package se.birdy.content.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchNormalizeTest {
    @Test fun `ascii apostrophe stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora's Falcon"))

    // ’ = U+2019 RIGHT SINGLE QUOTATION MARK (how names are stored in the YAML).
    // It is a literal U+2019 (bytes e2 80 99) — distinct from the ASCII case above.
    @Test fun `typographic apostrophe U2019 stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora’s Falcon"))

    @Test fun `diacritics stripped`() =
        assertEquals("ruppells vulture", normalizeSearch("Rüppell’s Vulture"))

    @Test fun `lowercased and whitespace collapsed`() =
        assertEquals("falco eleonorae", normalizeSearch("  Falco   eleonorae "))

    @Test fun `empty stays empty`() =
        assertEquals("", normalizeSearch(""))
}
