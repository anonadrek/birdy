package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BirdNetLabelMapperTest {
    // Fixture using actual index→qid pairs from the generated birdnet_lite_to_qid.json.
    // Indices 7, 33, 45 confirmed stable from T1 generation run (2026-05-20).
    private val fixture =
        """
        {
          "_meta": {
            "generated_for_model_version": "birdnet_lite_6k_global",
            "coverage_pct": 74.7,
            "total_birdnet_classes": 6362,
            "mapped_to_qid": 627,
            "total_species_in_list": 839
          },
          "mapping": {
            "7": "Q20754771",
            "33": "Q25380",
            "45": "Q715819"
          }
        }
        """.trimIndent()

    @Test
    fun lookup_returnsQid_forMappedIndex() {
        val mapper = BirdNetLabelMapper.parse(fixture)
        assertEquals("Q20754771", mapper.lookup(7), "index 7 should map to Q20754771")
        assertEquals("Q25380", mapper.lookup(33), "index 33 should map to Q25380")
        assertEquals("Q715819", mapper.lookup(45), "index 45 should map to Q715819")
    }

    @Test
    fun lookup_returnsNull_forUnmappedIndex() {
        val mapper = BirdNetLabelMapper.parse(fixture)
        assertNull(mapper.lookup(999_999))
    }

    @Test
    fun load_exposesMetaFields() {
        val mapper = BirdNetLabelMapper.parse(fixture)
        assertEquals("birdnet_lite_6k_global", mapper.modelVersion)
        assertTrue(mapper.coveragePct >= 70.0, "coverage was ${mapper.coveragePct}, expected ≥70")
    }

    @Test
    fun rejects_malformed_json() {
        assertFailsWith<IllegalArgumentException> {
            BirdNetLabelMapper.parse("not json at all {{{")
        }
    }
}
