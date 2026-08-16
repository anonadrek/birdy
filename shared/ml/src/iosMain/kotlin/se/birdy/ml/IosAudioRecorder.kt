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
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import kotlin.concurrent.Volatile
import kotlin.math.ceil

/**
 * iOS-spegel av [AndroidAudioRecorder]: öppen 48 kHz mono PCM_16-capture via
 * AVAudioEngine-tap + AVAudioConverter (hårdvaruformat → 48k/mono/Int16).
 * Identiskt callback-kontrakt (se [AndroidAudioRecorder] + AudioRecorderApi-KDoc):
 * onChunk på recorderns egen tråd (~33 ms-chunks), onCapReached vid [maxDurationMs],
 * onError HÖGST EN GÅNG (session-avbrott/mic-förlust/start-fel) och aldrig efter
 * stop/cancel. AVAudioSession .record + .measurement ≈ Androids UNPROCESSED.
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
                    handle.stopCaptureOnly()
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
            handle.interruptionObserver =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVAudioSessionInterruptionNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    handle.fireError(IllegalStateException("AVAudioSession interrupted"))
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
                    handle.fireError(t)
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
}

@OptIn(ExperimentalForeignApi::class)
class IosRecorderHandle internal constructor(
    private val engine: AVAudioEngine,
    private val session: AVAudioSession,
) {
    internal lateinit var chunker: PcmChunker
    internal var onErrorOnce: ((Throwable) -> Unit)? = null
    internal var interruptionObserver: Any? = null

    private val lock = NSLock()

    @Volatile internal var terminated = false
        private set

    @Volatile private var cancelled = false

    @Volatile private var errorFired = false

    internal fun withLock(block: () -> Unit) {
        lock.lock()
        try {
            block()
        } finally {
            lock.unlock()
        }
    }

    internal fun fireError(t: Throwable) {
        if (terminated || errorFired) return
        errorFired = true
        stopCaptureOnly()
        onErrorOnce?.invoke(t)
    }

    /** Stoppar tap + engine (mic-indikatorn släcks) utan att markera handle som stängd för flush. */
    internal fun stopCaptureOnly() {
        runCatching { engine.inputNode.removeTapOnBus(0u) }
        runCatching { engine.stop() }
        runCatching { interruptionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
        interruptionObserver = null
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
            if (cancelled) ShortArray(0) else withLockReturning { chunker.snapshot() }
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
