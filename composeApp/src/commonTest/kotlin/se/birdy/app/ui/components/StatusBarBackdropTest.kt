package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusBarBackdropTest {
    @Test
    fun `nothing reported means no dark backdrop`() {
        assertFalse(StatusBarBackdrop().anyDark)
    }

    @Test
    fun `a dark report makes the backdrop dark`() {
        val backdrop = StatusBarBackdrop()
        backdrop.report("hero", isDark = true)
        assertTrue(backdrop.anyDark)
    }

    @Test
    fun `a hero scrolled away no longer counts`() {
        val backdrop = StatusBarBackdrop()
        backdrop.report("hero", isDark = true)
        backdrop.report("hero", isDark = false)
        assertFalse(backdrop.anyDark)
    }

    @Test
    fun `two heroes during a transition stay dark until both are gone`() {
        val backdrop = StatusBarBackdrop()
        backdrop.report("old", isDark = true)
        backdrop.report("new", isDark = true)
        backdrop.remove("old")
        assertTrue(backdrop.anyDark)
        backdrop.remove("new")
        assertFalse(backdrop.anyDark)
    }
}
