package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MapTilerUrlsTest {
    @Test
    fun builds_same_shape_as_android_xy_tile_source() {
        assertEquals(
            "https://api.maptiler.com/maps/landscape/13/4400/2686@2x.png?key=abc123",
            mapTilerTileUrl(styleId = "landscape", z = 13, x = 4400, y = 2686, apiKey = "abc123"),
        )
    }

    @Test
    fun a_custom_style_id_goes_into_the_path() {
        val style = "0199b2c4-7d1e-7a3b-9c55-1f2e3d4c5b6a"
        assertEquals(
            "https://api.maptiler.com/maps/$style/5/17/9@2x.png?key=k",
            mapTilerTileUrl(styleId = style, z = 5, x = 17, y = 9, apiKey = "k"),
        )
        assertEquals("https://api.maptiler.com/maps/$style/", mapTilerTileBaseUrl(style))
    }

    @Test
    fun the_tile_url_starts_with_the_base_url() {
        val url = mapTilerTileUrl(styleId = "landscape", z = 1, x = 0, y = 1, apiKey = "k")
        assertTrue(url.startsWith(mapTilerTileBaseUrl("landscape")))
    }

    @Test
    fun unset_or_blank_style_uses_the_stock_default() {
        assertEquals(DEFAULT_MAPTILER_STYLE_ID, mapTilerStyleId(null))
        assertEquals(DEFAULT_MAPTILER_STYLE_ID, mapTilerStyleId(""))
        assertEquals(DEFAULT_MAPTILER_STYLE_ID, mapTilerStyleId("   "))
        assertEquals("landscape", DEFAULT_MAPTILER_STYLE_ID)
    }

    @Test
    fun a_configured_style_id_is_used_trimmed() {
        assertEquals("0199b2c4-7d1e", mapTilerStyleId(" 0199b2c4-7d1e "))
        assertEquals("toner-v2", mapTilerStyleId("toner-v2"))
    }

    @Test
    fun anything_but_a_bare_style_id_falls_back_to_the_default() {
        // A pasted style URL, a path or a query would break every tile request.
        listOf(
            "https://api.maptiler.com/maps/abc/style.json?key=x",
            "abc/256",
            "../landscape",
            "abc?key=x",
            "abc def",
            "$(MAPTILER_STYLE_ID)",
        ).forEach { assertEquals(DEFAULT_MAPTILER_STYLE_ID, mapTilerStyleId(it), it) }
    }

    @Test
    fun the_tile_cache_name_is_per_style() {
        // osmdroid caches tiles per tile source name: two styles must never share cached tiles.
        assertNotEquals(mapTilerTileSourceName("landscape"), mapTilerTileSourceName("0199b2c4-7d1e"))
        assertTrue("0199b2c4-7d1e" in mapTilerTileSourceName("0199b2c4-7d1e"))
    }
}
