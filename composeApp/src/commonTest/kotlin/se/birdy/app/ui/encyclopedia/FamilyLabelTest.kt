package se.birdy.app.ui.encyclopedia

import se.birdy.content.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FamilyLabelTest {
    @Test
    fun sv_uses_swedish_common_name() {
        assertEquals("Mesar", localizedFamilyLabel(Locale.SV, family = "Paridae", familySv = "Mesar"))
    }

    @Test
    fun en_uses_scientific_family_and_never_swedish() {
        assertEquals("Paridae", localizedFamilyLabel(Locale.EN, family = "Paridae", familySv = "Mesar"))
        assertEquals("Accipitridae", localizedFamilyLabel(Locale.EN, family = "Accipitridae", familySv = "Hökar"))
    }

    @Test
    fun sv_falls_back_to_scientific_when_swedish_missing() {
        assertEquals("Paridae", localizedFamilyLabel(Locale.SV, family = "Paridae", familySv = ""))
        assertEquals("Paridae", localizedFamilyLabel(Locale.SV, family = "Paridae", familySv = null))
    }
}
