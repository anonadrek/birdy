package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BirdClassifierModelInfoLoaderTest {
    @Test
    fun parses_valid_metadata_json() {
        val json =
            """
            {
              "modelVersion": "aiy_birds_v1",
              "distribution": "compose-resources",
              "input": {
                "shape": [1, 224, 224, 3],
                "dtype": "uint8",
                "normalization": {
                  "mean": [127.5, 127.5, 127.5],
                  "std":  [127.5, 127.5, 127.5]
                }
              },
              "output": {
                "shape": [1, 965],
                "dtype": "uint8",
                "labelFormat": "aiy_class_index",
                "outputClasses": 965,
                "backgroundClassIndex": 964,
                "quantization": {
                  "scale": 0.00390625,
                  "zeroPoint": 0
                }
              },
              "tfliteFileBytes": 3561598,
              "tfliteSha256": "95f59df4cff9053355abcd0b6e5e7e740f6eae19ed8cded32e2c3e8068195093"
            }
            """.trimIndent()

        val info = BirdClassifierModelInfoLoader.parseJson(json)

        assertEquals("aiy_birds_v1", info.modelVersion)
        assertEquals(ModelDistribution.COMPOSE_RESOURCES, info.distribution)
        assertEquals(224, info.inputWidthPx)
        assertEquals(224, info.inputHeightPx)
        assertEquals(3, info.inputChannels)
        assertEquals(TensorDtype.UINT8, info.inputDtype)
        assertEquals(listOf(127.5f, 127.5f, 127.5f), info.normalizationMean)
        assertEquals(listOf(127.5f, 127.5f, 127.5f), info.normalizationStd)
        assertEquals(965, info.outputClasses)
        assertEquals(964, info.backgroundClassIndex)
        assertEquals(TensorDtype.UINT8, info.outputDtype)
        assertEquals(0.00390625f, info.outputScale)
        assertEquals(0, info.outputZeroPoint)
        assertEquals(3561598L, info.tfliteFileBytes)
    }

    @Test
    fun rejects_input_shape_with_wrong_rank() {
        val json =
            """
            {
              "modelVersion": "x",
              "distribution": "compose-resources",
              "input": {
                "shape": [1, 224, 224],
                "dtype": "uint8",
                "normalization": { "mean": [0.0], "std": [1.0] }
              },
              "output": {
                "shape": [1, 965],
                "dtype": "uint8",
                "labelFormat": "aiy_class_index",
                "outputClasses": 965,
                "backgroundClassIndex": 964,
                "quantization": { "scale": 1.0, "zeroPoint": 0 }
              }
            }
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parseJson(json)
        }
    }

    @Test
    fun rejects_unknown_dtype() {
        val json =
            """
            {
              "modelVersion": "x",
              "distribution": "compose-resources",
              "input": {
                "shape": [1, 224, 224, 3],
                "dtype": "bfloat16",
                "normalization": { "mean": [0.0], "std": [1.0] }
              },
              "output": {
                "shape": [1, 965],
                "dtype": "uint8",
                "labelFormat": "aiy_class_index",
                "outputClasses": 965,
                "backgroundClassIndex": 964,
                "quantization": { "scale": 1.0, "zeroPoint": 0 }
              }
            }
            """.trimIndent()
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parseJson(json)
        }
    }

    @Test
    fun rejects_malformed_json() {
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parseJson("not json at all {{{")
        }
    }

    @Test
    fun rejects_missing_required_fields() {
        val json = """{"distribution": "compose-resources"}"""
        assertFailsWith<IllegalArgumentException> {
            BirdClassifierModelInfoLoader.parseJson(json)
        }
    }
}
