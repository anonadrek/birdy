package se.birdy.app.ui.stats

import androidx.compose.ui.graphics.Color
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.StampNavy

/**
 * 1.3.0 T12b: the one shared winter/spring/summer/autumn palette, used by both
 * [se.birdy.app.ui.stats.charts.JournalDonutChart]'s own defaults and this package's
 * `SeasonLegend` — previously the four colors were declared twice (donut defaults +
 * legend call sites), guarded only by a "MUST match" comment. A single source means the
 * arcs and the swatches can never drift apart again.
 *
 * Summer is [Brass], not `BrassText` (T12's choice, reverted in review): [se.birdy.app.ui.theme.BrassText]
 * reads almost identically to [AccentCopper] (autumn) for deuteranopes — the two colors are
 * ~1.10:1 apart in luminance and simulate to a ΔE of ~2 (indistinguishable), because both are
 * fairly dark, desaturated browns. [Brass] keeps a real gap from [AccentCopper] (≈2.05:1 in
 * luminance, ΔE 22–34 across every CVD simulation) while still clearing WCAG 1.4.11's 3:1
 * graphics-object floor on the surface this palette is actually drawn on: both
 * [se.birdy.app.ui.stats.charts.JournalDonutChart] and the legend only ever render inside a
 * [se.birdy.app.ui.components.SectionCard] (i.e. on `CardPaper`, ≈3.02:1) — never directly on
 * `MossCreme` (≈2.75:1, which would fail). See `SeasonPaletteContrastTest`.
 */
internal object SeasonPalette {
    val winter: Color = StampNavy
    val spring: Color = HeroMossLight
    val summer: Color = Brass
    val autumn: Color = AccentCopper
}
