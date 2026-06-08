package se.birdy.app.ui.match

import se.birdy.ml.Classification
import se.birdy.ml.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachLocationTest {
    private val cls = Classification(results = emptyList())

    @Test
    fun imageAttaches() = assertEquals(true, shouldAttachLocation(ScanSource.Image("/f.jpg", cls)))

    @Test
    fun audioAttaches() = assertEquals(true, shouldAttachLocation(ScanSource.Audio("/f.jpg", cls, "/a.wav")))
}
