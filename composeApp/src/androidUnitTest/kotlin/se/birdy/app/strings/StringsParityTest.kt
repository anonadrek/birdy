package se.birdy.app.strings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app ships in Swedish and English (spec 2026-09-24 D7). Every key must exist in both
 * languages with the same format placeholders, or one language shows a raw key / crashes.
 */
class StringsParityTest {
    private val sv = StringsXml.strings(StringsXml.swedish())
    private val en = StringsXml.strings(StringsXml.english())

    @Test
    fun `both languages define exactly the same string keys`() {
        assertTrue("Parsed zero strings — path or regex broken", sv.size > 100 && en.size > 100)
        assertEquals("Only in Swedish", emptySet<String>(), sv.keys - en.keys)
        assertEquals("Only in English", emptySet<String>(), en.keys - sv.keys)
    }

    @Test
    fun `placeholders match between languages`() {
        val mismatches =
            (sv.keys intersect en.keys).filter { key ->
                StringsXml.placeholderRegex
                    .findAll(sv.getValue(key))
                    .map { it.value }
                    .toSet() !=
                    StringsXml.placeholderRegex
                        .findAll(en.getValue(key))
                        .map { it.value }
                        .toSet()
            }
        assertEquals("Placeholder mismatch", emptyList<String>(), mismatches.sorted())
    }

    @Test
    fun `string arrays have the same keys and item counts`() {
        val svArrays = StringsXml.arrays(StringsXml.swedish())
        val enArrays = StringsXml.arrays(StringsXml.english())
        assertEquals(svArrays.keys, enArrays.keys)
        svArrays.forEach { (key, items) -> assertEquals("Array $key", items.size, enArrays.getValue(key).size) }
    }

    @Test
    fun `plural resources have the same keys, quantity forms and placeholders`() {
        val svPlurals = StringsXml.plurals(StringsXml.swedish())
        val enPlurals = StringsXml.plurals(StringsXml.english())
        assertTrue("Parsed zero plurals — regex broken?", svPlurals.isNotEmpty() && enPlurals.isNotEmpty())
        assertEquals("Only in Swedish", emptySet<String>(), svPlurals.keys - enPlurals.keys)
        assertEquals("Only in English", emptySet<String>(), enPlurals.keys - svPlurals.keys)
        svPlurals.forEach { (key, quantities) ->
            val enQuantities = enPlurals.getValue(key)
            assertEquals("Plural $key quantities", quantities.keys, enQuantities.keys)
            quantities.forEach { (quantity, svText) ->
                val svPlaceholders =
                    StringsXml.placeholderRegex
                        .findAll(svText)
                        .map { it.value }
                        .toSet()
                val enPlaceholders =
                    StringsXml.placeholderRegex
                        .findAll(enQuantities.getValue(quantity))
                        .map { it.value }
                        .toSet()
                assertEquals("Plural $key[$quantity] placeholders", svPlaceholders, enPlaceholders)
            }
        }
    }
}
