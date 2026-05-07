package se.birdy.ml

interface TfliteRunner {
    /**
     * Runs inference. [input] is a flat FloatArray matching the model's input
     * tensor shape; [output] is pre-allocated to outputClasses and filled
     * in-place with logits/probabilities.
     */
    fun run(
        input: FloatArray,
        output: FloatArray,
    )

    /**
     * Releases native resources. Caller must guarantee no [run] call is in
     * flight when [close] fires — TFLite [Interpreter.close] while [run]
     * executes is undefined behavior. [TfLiteBirdClassifier] coordinates this
     * via its mutex; direct callers are responsible.
     */
    fun close()
}
