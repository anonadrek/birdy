package se.birdy.app.ui.onboarding.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.ui.onboarding.components.IntroSceneScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.datastore.AppLanguage
import se.birdy.content.Locale as ContentLocale

/**
 * Scen 0 — språkval. AVSIKTLIGT tvåspråkig med hårdkodade literaler: skärmen
 * visas INNAN användaren valt språk, så båda språken renderas samtidigt
 * (etablerad konvention för språkväljare). Dokumenterat undantag från
 * stringResource-regeln i trap-katalogen.
 *
 * Val persisteras direkt (task 2) → AppCompat recreatar aktiviteten → pagern
 * nollställs till sida 0 = den här scenen, nu på valt språk. Flyttas scenen
 * någonsin från index 0 måste recreate-beteendet omprövas (state-förlust).
 */
@Composable
fun SceneLanguage(
    pageOffset: Float,
    selected: AppLanguage?,
    onSelect: (AppLanguage) -> Unit,
) {
    val fallback =
        when (LocaleResolver.resolve(override = null, systemTag = Locale.current.toLanguageTag())) {
            ContentLocale.SV -> AppLanguage.SV
            ContentLocale.EN -> AppLanguage.EN
        }
    val effective = selected ?: fallback

    IntroSceneScaffold(
        eyebrow = "SPRÅK · LANGUAGE · NO 0",
        headline = "Välj språk · *Choose language*",
        sub = "Kan ändras när som helst i Inställningar · Change anytime in Settings",
        pageOffset = pageOffset,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LanguageChoice(
                label = "Svenska",
                isSelected = effective == AppLanguage.SV,
                onClick = { onSelect(AppLanguage.SV) },
            )
            Spacer(Modifier.height(12.dp))
            LanguageChoice(
                label = "English",
                isSelected = effective == AppLanguage.EN,
                onClick = { onSelect(AppLanguage.EN) },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LanguageChoice(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) AccentCopper else MarginaliaInk.copy(alpha = 0.3f)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .width(240.dp)
                .clip(shape)
                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = shape)
                .background(if (isSelected) AccentCopper.copy(alpha = 0.08f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 18.sp, color = MarginaliaInk, modifier = Modifier.weight(1f))
        if (isSelected) {
            Text(text = "✓", fontSize = 18.sp, color = AccentCopper)
        }
    }
}
