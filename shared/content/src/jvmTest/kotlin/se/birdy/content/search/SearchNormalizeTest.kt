package se.birdy.content.search

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchNormalizeTest {
    @Test fun `ascii apostrophe stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora's Falcon"))

    @Test fun `typographic apostrophe U2019 stripped`() =
        assertEquals("eleonoras falcon", normalizeSearch("Eleonora’s Falcon"))

    @Test fun `diacritics stripped`() =
        assertEquals("ruppells vulture", normalizeSearch("Rüppell’s Vulture"))

    @Test fun `lowercased and whitespace collapsed`() =
        assertEquals("falco eleonorae", normalizeSearch("  Falco   eleonorae "))

    @Test fun `empty stays empty`() =
        assertEquals("", normalizeSearch(""))
}
