package se.birdy.content.build

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ValidateModelMappingTest {
    private val validMapping =
        """
        {
          "_meta": {
            "generated_for_model_version": "aiy_birds_v1",
            "generated_at": "2026-05-07T00:00:00Z",
            "coverage_pct": 99.0,
            "mapped_classes": 954,
            "total_classes": 964
          },
          "mappings": {
            "0": "Q111",
            "1": "Q222"
          }
        }
        """.trimIndent()

    private val validMetadata =
        """
        {
          "modelVersion": "aiy_birds_v1"
        }
        """.trimIndent()

    @Test
    fun `valid mapping and metadata passes without error`() {
        // Should not throw
        ValidateModelMapping.validate(validMapping, validMetadata)
    }

    @Test
    fun `malformed mapping JSON throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            ValidateModelMapping.validate("{not valid json", validMetadata)
        }
    }

    @Test
    fun `version mismatch throws IllegalStateException`() {
        val mismatchedMetadata = """{ "modelVersion": "other_model_v2" }"""
        assertFailsWith<IllegalStateException> {
            ValidateModelMapping.validate(validMapping, mismatchedMetadata)
        }
    }

    @Test
    fun `coverage below 50 throws IllegalStateException`() {
        val lowCoverageMapping =
            """
            {
              "_meta": {
                "generated_for_model_version": "aiy_birds_v1",
                "generated_at": "2026-05-07T00:00:00Z",
                "coverage_pct": 30.0,
                "mapped_classes": 10,
                "total_classes": 964
              },
              "mappings": {
                "0": "Q111"
              }
            }
            """.trimIndent()
        assertFailsWith<IllegalStateException> {
            ValidateModelMapping.validate(lowCoverageMapping, validMetadata)
        }
    }

    @Test
    fun `duplicate class index throws RuntimeException`() {
        // Raw JSON with duplicate key "0" — kotlinx.serialization silently overwrites,
        // so we detect it via raw-text scan
        val duplicateMapping =
            """
            {
              "_meta": {
                "generated_for_model_version": "aiy_birds_v1",
                "generated_at": "2026-05-07T00:00:00Z",
                "coverage_pct": 99.0,
                "mapped_classes": 2,
                "total_classes": 964
              },
              "mappings": {
                "0": "Q111",
                "0": "Q222"
              }
            }
            """.trimIndent()
        assertFailsWith<RuntimeException> {
            ValidateModelMapping.validate(duplicateMapping, validMetadata)
        }
    }
}
