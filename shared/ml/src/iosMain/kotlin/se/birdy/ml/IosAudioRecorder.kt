package se.birdy.ml

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterInputStatus_NoDataNow
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioEngineConfigurationChangeNotification
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlin.math.ceil

/**
 * iOS-spegel av [AndroidAudioRecorder]: öppen 48 kHz mono PCM_16-capture via
 * AVAudioEngine-tap + AVAudioConverter (hårdvaruformat → 48k/mono/Int16).
 * Identiskt callback-kontrakt (se [AndroidAudioRecorder] + AudioRecorderApi-KDoc):
 * onChunk på recorderns egen tråd (~33 ms-chunks), onCapReached vid [maxDurationMs],
 * onError HÖGST EN GÅNG (session-avbrott via [AVAudioSessionInterruptionNotification],
 * mic-förlust/engine-ombyggnad via [AVAudioEngineConfigurationChangeNotification] — t.ex.
 * BT-öronsnäckor ansluter mitt i inspelning) och aldrig efter stop/cancel. Start-fel
 * kastas SYNKRONT ur [start] (try/catch → teardown → rethrow) — går INTE via onError.
 * AVAudioSession .record + .measurement ≈ Androids UNPROCESSED.
 *
 * OBS failable-init-trapen (CLAUDE.md): AVAudioFormat/AVAudioConverter är `init?` —
 * konstruktoranropen wrappas i [orNullOnNpe], aldrig elvis direkt på konstruktorn.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAudioRecorder(
    val sampleRate: Int = 48_000,
) {
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        onCapReached: () -> Unit,
        onError: (Throwable) -> Unit = {},
        maxDurationMs: Long = 60_000L,
    ): IosRecorderHandle {
        val maxSamples = (sampleRate * maxDurationMs / 1000L).toInt()
        val session = AVAudioSession.sharedInstance()
        val engine = AVAudioEngine()
        val handle = IosRecorderHandle(engine, session)

        val chunker =
            PcmChunker(
                chunkSize = sampleRate / 30, // ~33 ms — samma kadens som Android
                maxSamples = maxSamples,
                onChunk = { s, r, t -> if (!handle.terminated) onChunk(s, r, t) },
                onCapReached = {
                    // Spegel av Android: capture slutar vid cap; VM:en kör stopAndFlush.
                    // Uppskjuten teardown (main queue) — denna lambda körs inifrån
                    // tap-callbackens egen call stack, och removeTapOnBus/engine.stop()
                    // reentrant därifrån är en AVAudioEngine-fälla (se stopCaptureOnlyDeferred-
                    // KDoc). Själva konsument-callbacken fyras direkt, kontraktsenligt.
                    handle.stopCaptureOnlyDeferred()
                    onCapReached()
                },
            )
        handle.chunker = chunker
        handle.onErrorOnce = { t -> onError(t) }

        try {
            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                check(
                    session.setCategory(
                        AVAudioSessionCategoryRecord,
                        mode = AVAudioSessionModeMeasurement,
                        options = 0u,
                        error = err.ptr,
                    ),
                ) { "AVAudioSession.setCategory failed: ${err.value?.localizedDescription}" }
                check(session.setActive(true, error = err.ptr)) {
                    "AVAudioSession.setActive(true) failed: ${err.value?.localizedDescription}"
                }
            }

            val input = engine.inputNode
            val hwFormat = input.inputFormatForBus(0u)
            check(hwFormat.sampleRate > 0.0) { "Ingen mikrofon-input tillgänglig (sampleRate=0)" }

            val targetFormat =
                orNullOnNpe {
                    AVAudioFormat(
                        commonFormat = AVAudioPCMFormatInt16,
                        sampleRate = sampleRate.toDouble(),
                        channels = 1u,
                        interleaved = true,
                    )
                } ?: error("AVAudioFormat(Int16/48k/mono) kunde inte skapas")
            val converter =
                orNullOnNpe { AVAudioConverter(fromFormat = hwFormat, toFormat = targetFormat) }
                    ?: error("AVAudioConverter $hwFormat -> $targetFormat kunde inte skapas")

            // Avbrott (samtal/Siri) = mic stulen → onError en gång (Androids read<=0-motsvarighet).
            // Guard på .Began (Fix #2): en avslutad (.Ended) notis får inte fela en session
            // som redan lever vidare (t.ex. samtalet tar slut och OS postar .Ended efteråt).
            handle.interruptionObserver =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVAudioSessionInterruptionNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { notification ->
                    if (isInterruptionBegan(notification)) {
                        handle.fireError(IllegalStateException("AVAudioSession interrupted"))
                    }
                }
            // Registrerings-fönster-guard: om handle redan hunnit terminate:as (fireError/
            // teardown/cancel körde på en ANNAN tråd mellan handle-konstruktion och denna
            // assignment) innan vi hann tilldela fältet, river vi observern direkt istället
            // för att låta den och dess retainade handle-referens läcka tyst vidare.
            if (handle.terminated) {
                handle.interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
                handle.interruptionObserver = null
            }

            // Engine-konfigurationsändring (Fix #1, slutreview Important): input-hårdvaran
            // byts mitt i inspelning (t.ex. BT-öronsnäckor ansluter) → AVAudioEngine stoppar
            // SIG SJÄLV och tap-callbacks upphör tyst — AVAudioSessionInterruptionNotification
            // täcker BARA samtal/Siri-avbrott, inte det här. Utan denna observer fastnar UI:t
            // i en frusen timer (mic-indikatorn lever kvar, inga nya chunks kommer). object =
            // engine (INTE null) — en gammal sessions engine-instans får inte träffa en ny
            // sessions observer (nästa start() skapar en FÄRSK AVAudioEngine).
            handle.engineConfigObserver =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVAudioEngineConfigurationChangeNotification,
                    `object` = engine,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    handle.fireError(IllegalStateException("AVAudioEngine configuration changed"))
                }
            if (handle.terminated) {
                handle.engineConfigObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
                handle.engineConfigObserver = null
            }

            input.installTapOnBus(0u, bufferSize = 4800u, format = hwFormat) { buffer, _ ->
                val inBuf = buffer ?: return@installTapOnBus
                if (handle.terminated) return@installTapOnBus
                try {
                    val inFrames = inBuf.frameLength.toInt()
                    if (inFrames == 0) return@installTapOnBus
                    val capacity =
                        ceil(inFrames * sampleRate.toDouble() / hwFormat.sampleRate).toInt() + 16
                    val outBuf =
                        orNullOnNpe { AVAudioPCMBuffer(pCMFormat = targetFormat, frameCapacity = capacity.toUInt()) }
                            ?: error("AVAudioPCMBuffer kunde inte skapas")
                    var consumed = false
                    memScoped {
                        val convErr = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                        val status =
                            converter.convertToBuffer(
                                outBuf,
                                error = convErr.ptr,
                                withInputFromBlock = { _, outStatus ->
                                    if (consumed) {
                                        outStatus?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                                        null
                                    } else {
                                        consumed = true
                                        outStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                                        inBuf
                                    }
                                },
                            )
                        check(status != AVAudioConverterOutputStatus_Error) {
                            "AVAudioConverter failed: ${convErr.value?.localizedDescription}"
                        }
                    }
                    val outFrames = outBuf.frameLength.toInt()
                    if (outFrames > 0) {
                        val channel =
                            outBuf.int16ChannelData?.get(0)
                                ?: error("int16ChannelData was null")
                        val shorts = ShortArray(outFrames) { channel[it] }
                        handle.withLock { chunker.accept(shorts) }
                    }
                } catch (t: Throwable) {
                    // Denna catch körs inifrån tap-callbacken själv → deferTeardown=true
                    // (se stopCaptureOnlyDeferred-KDoc). Avbrotts-observern nedan körs på
                    // main queue, INTE inifrån tap-stacken, och behåller default (synkron).
                    handle.fireError(t, deferTeardown = true)
                }
            }

            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                check(engine.startAndReturnError(err.ptr)) {
                    "AVAudioEngine.start failed: ${err.value?.localizedDescription}"
                }
            }
        } catch (t: Throwable) {
            handle.teardown()
            throw t
        }
        return handle
    }

    private inline fun <T : Any> orNullOnNpe(block: () -> T): T? =
        try {
            block()
        } catch (_: NullPointerException) {
            // K/N mappar failable ObjC-init till konstruktor som kastar NPE vid nil.
            null
        }

    /**
     * Fix #2: [AVAudioSessionInterruptionNotification] fyras BÅDE när avbrottet börjar
     * och när det slutar — userInfo[[AVAudioSessionInterruptionTypeKey]] (en boxad
     * NSNumber) skiljer dem åt. Vi ska bara fela på .Began; en .Ended-notis betyder att
     * OS just gav tillbaka mic:en, inte att den försvann. Saknad userInfo/typ hanteras
     * konservativt som .Began (fireError) — hellre en falsk RecordingFailed än en tyst
     * frusen inspelning om Apple någon gång skulle posta en typlös notis.
     */
    private fun isInterruptionBegan(notification: NSNotification?): Boolean {
        val typeNumber = notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber
        return typeNumber == null || typeNumber.unsignedLongValue == AVAudioSessionInterruptionTypeBegan.toULong()
    }
}

@OptIn(ExperimentalForeignApi::class)
class IosRecorderHandle internal constructor(
    private val engine: AVAudioEngine,
    private val session: AVAudioSession,
) {
    // Fix #4: publicerade av start() (main/caller-tråd), lästa från tap-tråden,
    // main-queue-notisobservrarna och Dispatchers.Default (stopAndFlush) — @Volatile
    // ger korrekt cross-thread-synlighet utan att gå vägen om `lock` för varje läsning.
    @Volatile internal var chunker: PcmChunker? = null

    @Volatile internal var onErrorOnce: ((Throwable) -> Unit)? = null

    @Volatile internal var interruptionObserver: Any? = null

    @Volatile internal var engineConfigObserver: Any? = null

    private val lock = NSLock()

    @Volatile internal var terminated = false
        private set

    @Volatile private var cancelled = false

    // Atomisk engångs-grind (inte en olåst check-and-set): tap-tråden (in-tap-fel) och
    // main-queue-avbrottsobservern kan anropa fireError samtidigt från OLIKA trådar —
    // en Boolean-"if (!errorFired) { errorFired = true }" har ett race-fönster där båda
    // hinner läsa false innan någon skriver true, vilket skulle bryta "onError högst en
    // gång". compareAndSet(0, 1) gör övergången atomär: exakt en anropare vinner.
    private val errorFired = AtomicInt(0)

    internal fun withLock(block: () -> Unit) {
        lock.lock()
        try {
            block()
        } finally {
            lock.unlock()
        }
    }

    /**
     * @param deferTeardown true när anroparen själv kör INIFRÅN tap-callbackens call stack
     *   (se [stopCaptureOnlyDeferred]) — main-queue-avbrottsobservern använder default
     *   (false) eftersom den redan kör utanför tap-stacken och kan riva synkront.
     */
    internal fun fireError(
        t: Throwable,
        deferTeardown: Boolean = false,
    ) {
        if (terminated) return
        if (!errorFired.compareAndSet(0, 1)) return
        if (deferTeardown) stopCaptureOnlyDeferred() else stopCaptureOnly()
        onErrorOnce?.invoke(t)
    }

    /** Stoppar tap + engine (mic-indikatorn släcks) utan att markera handle som stängd för flush. */
    internal fun stopCaptureOnly() {
        runCatching { engine.inputNode.removeTapOnBus(0u) }
        runCatching { engine.stop() }
        runCatching { interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
        interruptionObserver = null
        runCatching { engineConfigObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
        engineConfigObserver = null
    }

    /**
     * Skjuter upp [stopCaptureOnly] till main queue. Vägar som initieras INIFRÅN
     * tap-callbackens egen call stack (cap-reached via [PcmChunker]s onCapReached, in-tap-fel
     * via [fireError]) får INTE riva `removeTapOnBus`/`engine.stop()` synkront där — det är
     * reentrant in i tap-callbackens egen exekvering, en känd AVAudioEngine-fälla (Apples
     * rekommendation är att trigga tap-borttagning utanför render-callbacken). Konsument-
     * callbacken (onCapReached/onError) fyras ändå direkt från tap-tråden — det är
     * kontraktsenligt (se AudioRecorderApi-KDoc: "Fyras på recorderns egen tråd/kö").
     *
     * Idempotent mot en mellanliggande [stopAndFlush]/[cancel]: om [teardown] redan hunnit
     * köra [stopCaptureOnly] synkront innan denna uppskjutna körning triggas, är varje steg
     * i [stopCaptureOnly] redan självt no-op-säkert (`runCatching` + `interruptionObserver`
     * null-check) — ofarligt att köra en andra gång.
     */
    internal fun stopCaptureOnlyDeferred() {
        dispatch_async(dispatch_get_main_queue()) {
            stopCaptureOnly()
        }
    }

    internal fun teardown() {
        terminated = true
        stopCaptureOnly()
        runCatching {
            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                session.setActive(false, error = err.ptr)
            }
        }
    }

    suspend fun stopAndFlush(): ShortArray =
        withContext(Dispatchers.Default) {
            teardown()
            if (cancelled) {
                ShortArray(0)
            } else {
                // start() always assigns chunker before returning this handle to the
                // caller — null here would mean stopAndFlush() was reached without going
                // through start(), a caller contract violation. Fail loud, not silent
                // (CLAUDE.md trap-katalogen: tysta fallbacks i produktion är förbjudna).
                withLockReturning {
                    checkNotNull(chunker) { "stopAndFlush() called before start() assigned chunker" }
                        .snapshot()
                }
            }
        }

    fun cancel() {
        cancelled = true
        teardown()
    }

    private fun <T> withLockReturning(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
