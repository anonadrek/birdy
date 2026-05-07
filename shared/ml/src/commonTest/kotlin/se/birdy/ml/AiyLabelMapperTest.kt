package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiyLabelMapperTest {
    private val fixture =
        """
        {
          "_meta": { "generated_for_model_version": "aiy_birds_v1", "coverage_pct": 78.5,
                     "mapped_classes": 757, "total_classes": 964 },
          "mappings": { "0": "Q1226346", "1": "Q913049", "12": "Q25485" }
        }
        """.trimIndent()

    @Test
    fun lookup_returns_qid_for_known_class_index() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertEquals("Q1226346", mapper.lookup(0))
        assertEquals("Q913049", mapper.lookup(1))
        assertEquals("Q25485", mapper.lookup(12))
    }

    @Test
    fun lookup_returns_null_for_unmapped_class_index() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertNull(mapper.lookup(99))
    }

    @Test
    fun lookup_returns_null_for_background_class() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertNull(mapper.lookup(964))
    }

    @Test
    fun coverage_pct_exposed_for_factory() {
        val mapper = AiyLabelMapper.parse(fixture)
        assertEquals(78.5, mapper.coveragePct)
    }
}
