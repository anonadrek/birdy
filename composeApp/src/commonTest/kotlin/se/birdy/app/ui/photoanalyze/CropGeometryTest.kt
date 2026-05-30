package se.birdy.app.ui.photoanalyze

import kotlin.test.Test
import kotlin.test.assertEquals

class CropGeometryTest {
    @Test
    fun full_rect_covers_whole_image() {
        assertEquals(CropRect(0, 0, 1000, 800), CropGeometry.fullRect(1000, 800))
    }

    @Test
    fun resize_corner_respects_min_side() {
        // Dra TOP_LEFT nästan in i motsatt hörn → stoppas av minSide=224.
        val rect = CropRect(0, 0, 1000, 1000)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.TOP_LEFT,
                x = 995,
                y = 995,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(776, 776, 1000, 1000), out)
    }

    @Test
    fun resize_corner_clamps_to_image_bounds() {
        val rect = CropRect(100, 100, 900, 900)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.TOP_LEFT,
                x = -50,
                y = -50,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(0, 0, 900, 900), out)
    }

    @Test
    fun resize_bottom_right_grows_within_bounds() {
        val rect = CropRect(0, 0, 400, 400)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.BOTTOM_RIGHT,
                x = 5000,
                y = 5000,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(0, 0, 1000, 1000), out)
    }

    @Test
    fun move_translates_and_clamps_to_bounds() {
        val rect = CropRect(800, 800, 1000, 1000) // 200×200 i nedre högra hörnet
        val out = CropGeometry.move(rect, dx = 500, dy = 500, width = 1000, height = 1000)
        // Kan inte flyttas ut → klampas så rect ligger kvar mot kanten.
        assertEquals(CropRect(800, 800, 1000, 1000), out)
    }

    @Test
    fun move_within_bounds_shifts_by_delta() {
        val rect = CropRect(0, 0, 200, 200)
        val out = CropGeometry.move(rect, dx = 50, dy = 30, width = 1000, height = 1000)
        assertEquals(CropRect(50, 30, 250, 230), out)
    }
}
