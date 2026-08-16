package se.birdy.ml

import cnames.structs.TfLiteInterpreter
import cnames.structs.TfLiteInterpreterOptions
import cnames.structs.TfLiteModel
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import tflitec.TfLiteInterpreterAllocateTensors
import tflitec.TfLiteInterpreterCreate
import tflitec.TfLiteInterpreterDelete
import tflitec.TfLiteInterpreterGetInputTensor
import tflitec.TfLiteInterpreterGetInputTensorCount
import tflitec.TfLiteInterpreterGetOutputTensor
import tflitec.TfLiteInterpreterInvoke
import tflitec.TfLiteInterpreterOptionsCreate
import tflitec.TfLiteInterpreterOptionsDelete
import tflitec.TfLiteInterpreterOptionsSetNumThreads
import tflitec.TfLiteModelCreate
import tflitec.TfLiteModelDelete
import tflitec.TfLiteTensorByteSize
import tflitec.TfLiteTensorCopyFromBuffer
import tflitec.TfLiteTensorCopyToBuffer
import tflitec.TfLiteTensorDim
import tflitec.TfLiteTensorNumDims
import tflitec.kTfLiteOk
import kotlin.time.TimeSource

/**
 * iOS-spegel av [AndroidTfliteAudioRunner] för BirdNET-Lite (float32 in/ut, INGEN
 * kvantisering — till skillnad från [IosTfliteRunner]/AIY). Kör på den vendrade
 * TensorFlowLiteC-cinteropen (i2b); FlexRFFT-op:en (node 29) löses av den
 * force_load:ade SelectTfOps-arkiveringen på DEVICE (i3 T1). På SIMULATOR saknas
 * Flex-slicen → create/allocate/invoke failar → kastat fel → ärligt felstate/DEMO
 * via [AudioClassifierFactory] (i3-spec B6).
 *
 * Paritetsregler mot Android-runnern:
 * - Adaptiv [expectedSamples] från inputShape `[1, N]` (guard mot fel modellfil).
 * - Output-guard mot [BirdNetLabelMapper.totalBirdnetClasses] (model/mapping-mismatch).
 * - [flatSigmoid] + [rankMappedScores] ur commonMain — identisk postprocess.
 * - METADATA_INPUT (tensor 1) nollfylls EXPLICIT (i3-spec B4): Android lämnar den
 *   omatad via Interpreter.run(input, output); C-API:t garanterar inte arena-innehåll,
 *   så determinismen görs synlig här. Ingen beteendeskillnad avsedd.
 * - Modellbytes LIFETIME-pinnas ([pinnedModel]-fält, unpin i [close]) —
 *   TfLiteModelCreate kopierar inte FlatBuffern (trap-katalogen).
 * - **Fix #1** (spegel av `AndroidTfliteAudioRunner.load()`s "wrap post-construction
 *   steps in try/catch so the native TFLite handle is always closed if anything throws
 *   before ownership transfers"): hela native-uppbyggnaden (pin → model → options →
 *   interpreter → allocate → introspektion → nollfyll) körs i EN try/catch. En kastande
 *   konstruktor lämnar aldrig ifrån sig en instans — [close] kan därför ALDRIG anropas på
 *   det som redan hunnit skapas. Spec B6 gör en misslyckad load till DEN FÖRVÄNTADE vägen
 *   på simulator (Flex saknas där, felet landar typiskt på AllocateTensors) — utan denna
 *   städning läcker varje försök det pinnade modell-minnet + alla native handles som
 *   redan skapats. Catch-blocket river ner exakt det som lyckades, i omvänd
 *   (skapande-)ordning, innan det återkastar.
 * - Mutex-serialisering + idempotent [close], som Android.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTfliteAudioRunner(
    modelBytes: ByteArray,
    private val mapper: BirdNetLabelMapper,
) : BirdAudioClassifier {
    init {
        require(modelBytes.isNotEmpty()) { "Empty model bytes — model file failed to load" }
    }

    private val pinnedModel = modelBytes.pin()

    private val model: CPointer<TfLiteModel>
    private val options: CPointer<TfLiteInterpreterOptions>
    private val interpreter: CPointer<TfLiteInterpreter>
    private val expectedSamples: Int
    private val outputClasses: Int
    override val info: AudioModelInfo

    init {
        // Spårar bara vad som FAKTISKT hunnit skapas, så catch-blocket kan städa exakt
        // det — i omvänd ordning — istället för att gissa. Se Fix #1 i KDoc ovan.
        var createdModel: CPointer<TfLiteModel>? = null
        var createdOptions: CPointer<TfLiteInterpreterOptions>? = null
        var createdInterpreter: CPointer<TfLiteInterpreter>? = null
        try {
            val model =
                checkNotNull(TfLiteModelCreate(pinnedModel.addressOf(0), modelBytes.size.convert())) {
                    "TfLiteModelCreate returned null — korrupt/ogiltig modellfil"
                }
            createdModel = model

            val options =
                checkNotNull(TfLiteInterpreterOptionsCreate()) {
                    "TfLiteInterpreterOptionsCreate returned null"
                }.also { TfLiteInterpreterOptionsSetNumThreads(it, NUM_THREADS) }
            createdOptions = options

            val interpreter =
                checkNotNull(TfLiteInterpreterCreate(model, options)) {
                    "TfLiteInterpreterCreate returned null"
                }
            createdInterpreter = interpreter
            check(TfLiteInterpreterAllocateTensors(interpreter) == kTfLiteOk) {
                "TfLiteInterpreterAllocateTensors failed"
            }

            val inputTensor =
                checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0)) { "input tensor was null" }
            val numDims = TfLiteTensorNumDims(inputTensor)
            val inputShape = List(numDims) { TfLiteTensorDim(inputTensor, it) }
            check(numDims == 2 && inputShape[0] == 1) {
                "Unexpected inputShape $inputShape — expected [1, N] waveform tensor. " +
                    "This may indicate the wrong model file was bundled (T1 regression)."
            }

            val outputTensor =
                checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0)) { "output tensor was null" }
            val outDims = TfLiteTensorNumDims(outputTensor)
            val outputShape = List(outDims) { TfLiteTensorDim(outputTensor, it) }
            val outputClassCount = outputShape.last()
            check(outputClassCount == mapper.totalBirdnetClasses) {
                "Model emits $outputClassCount classes but birdnet_lite_to_qid.json " +
                    "maps ${mapper.totalBirdnetClasses} — model/mapping mismatch would mis-index species."
            }

            // Nollfyll METADATA_INPUT (tensor 1) en gång — se KDoc.
            if (TfLiteInterpreterGetInputTensorCount(interpreter) >= 2) {
                val meta =
                    checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 1)) { "metadata tensor was null" }
                val byteSize = TfLiteTensorByteSize(meta).toInt()
                if (byteSize > 0) {
                    ByteArray(byteSize).usePinned { pinned ->
                        check(
                            TfLiteTensorCopyFromBuffer(meta, pinned.addressOf(0), byteSize.convert()) == kTfLiteOk,
                        ) { "zero-fill of METADATA_INPUT failed" }
                    }
                }
            }

            // Allt validerade OK — commit:a fälten atomiskt. Inget nedanför kan kasta.
            this.model = model
            this.options = options
            this.interpreter = interpreter
            this.expectedSamples = inputShape[1]
            this.outputClasses = outputClassCount
            this.info =
                AudioModelInfo(
                    modelVersion = mapper.modelVersion,
                    inputShape = inputShape,
                    outputShape = outputShape,
                    coveragePct = mapper.coveragePct,
                )
        } catch (t: Throwable) {
            createdInterpreter?.let { TfLiteInterpreterDelete(it) }
            createdOptions?.let { TfLiteInterpreterOptionsDelete(it) }
            createdModel?.let { TfLiteModelDelete(it) }
            pinnedModel.unpin()
            throw t
        }
    }

    private val mutex = Mutex()
    private var closed = false
    private val logits = FloatArray(outputClasses)

    override suspend fun classify(input: AudioInput): AudioClassification =
        mutex.withLock {
            check(!closed) { "IosTfliteAudioRunner closed" }
            require(input.waveform.size == expectedSamples) {
                "Expected $expectedSamples samples (from model inputShape), got ${input.waveform.size}"
            }

            val t0 = TimeSource.Monotonic.markNow()
            val inputTensor =
                checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0)) { "input tensor was null" }
            input.waveform.usePinned { pinned ->
                check(
                    TfLiteTensorCopyFromBuffer(
                        inputTensor,
                        pinned.addressOf(0),
                        (input.waveform.size * Float.SIZE_BYTES).convert(),
                    ) == kTfLiteOk,
                ) { "TfLiteTensorCopyFromBuffer failed" }
            }

            check(TfLiteInterpreterInvoke(interpreter) == kTfLiteOk) { "TfLiteInterpreterInvoke failed" }

            val outputTensor =
                checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0)) { "output tensor was null" }
            logits.usePinned { pinned ->
                check(
                    TfLiteTensorCopyToBuffer(
                        outputTensor,
                        pinned.addressOf(0),
                        (logits.size * Float.SIZE_BYTES).convert(),
                    ) == kTfLiteOk,
                ) { "TfLiteTensorCopyToBuffer failed" }
            }
            val inferenceMs = t0.elapsedNow().inWholeMilliseconds

            val scores = FloatArray(outputClasses) { flatSigmoid(logits[it]) }
            AudioClassification(
                results = rankMappedScores(scores, mapper::lookup),
                inferenceMs = inferenceMs,
                modelVersion = info.modelVersion,
            )
        }

    override fun close() {
        if (closed) return
        closed = true
        TfLiteInterpreterDelete(interpreter)
        TfLiteInterpreterOptionsDelete(options)
        TfLiteModelDelete(model)
        pinnedModel.unpin()
    }

    companion object {
        private const val NUM_THREADS = 4

        /** Läser modellen från [modelPath] (NSBundle-path, wire:as i IosAppGraph i3 T7). */
        suspend fun load(modelPath: String): IosTfliteAudioRunner {
            val bytes = readFileBytes(modelPath)
            val mapper = loadBirdNetLabelMapper()
            return IosTfliteAudioRunner(bytes, mapper)
        }

        private fun readFileBytes(path: String): ByteArray {
            val data =
                NSData.dataWithContentsOfFile(path)
                    ?: error("Kunde inte läsa modellfil: $path")
            val size = data.length.toInt()
            check(size > 0) { "Modellfilen är tom: $path" }
            val bytes = ByteArray(size)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            return bytes
        }
    }
}
