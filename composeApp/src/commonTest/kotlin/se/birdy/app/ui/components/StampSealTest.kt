package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class StampSealTest {
    @Test
    fun `locked state has dashed border by spec`() {
        val s = StampSealState.Locked(name = "Novice")
        assertEquals(StampStyle.Dashed, s.borderStyle())
        assertEquals(0f, s.rotationDegrees())
    }

    @Test
    fun `unlocked state has solid border and rotation`() {
        val s = StampSealState.Unlocked(number = 5, glyph = "★", name = "Novice")
        assertEquals(StampStyle.Solid, s.borderStyle())
        assertEquals(-3f, s.rotationDegrees())
    }

    @Test
    fun `in-progress state is solid copper without rotation`() {
        val s = StampSealState.InProgress(number = 6, name = "Birder", progressLabel = "3/5")
        assertEquals(StampStyle.Solid, s.borderStyle())
        assertEquals(0f, s.rotationDegrees())
    }
}
