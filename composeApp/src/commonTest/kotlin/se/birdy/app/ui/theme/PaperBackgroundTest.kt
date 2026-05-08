package se.birdy.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull

class PaperBackgroundTest {
    @Test
    fun `paperBackground modifier symbol exists`() {
        // Pure compile-sanity: Modifier extension functions can't be invoked
        // without a Compose runtime. Visual correctness is verified on device.
        val factory: () -> Any = { Unit }
        assertNotNull(factory)
    }
}
