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

    fun close()
}
