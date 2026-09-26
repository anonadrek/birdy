// Every literal below IS the named palette token — MagicNumber has no signal to add here.
// (Historically these hex literals lived in the committed detekt-baseline.xml; the palette
// lift changed every value, so baseline signatures no longer match. Per house rule the
// baseline is never extended — suppress at file level instead, spec 2026-09-24 §4.1.)
@file:Suppress("MagicNumber")

package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color

// Field Journal palette, lifted 2026-09-24 for release 1.3.0 — "Mossa, rost & mässing".
// Spec: docs/superpowers/specs/2026-09-24-v1-3-release-design.md §4.1.
// Token NAMES are kept from the Mossbädd / Plan 7 eras so the ~70 call sites lift
// automatically; the VALUES are new. ColorContrastTest pins WCAG AA for every text token.

// ===== Paper =====
val MossCreme = Color(0xFFF6EFE2) // primary background
val SandCreme = Color(0xFFEDE3D1) // stat surface, a step darker than the background
val PaperTop = Color(0xFFF8F2E7) // paperBackground() gradient, top
val PaperBottom = Color(0xFFF2E9D8) // paperBackground() gradient, bottom
val CardPaper = Color(0xFFFFFAF1) // cards and sheets on paper
val Hairline = Color(0xFFDFD2BA) // 1dp rules and card outlines — never text
val PaperBottomBar = Color(0xFFF6EFE2) // bottom nav + system nav bar strip

// ===== Dark moss surfaces (photo scrims, Premium, hero gradients), light → deep =====
val HeroMossLight = Color(0xFF3A4A2E)
val HeroMossMid = Color(0xFF2C3A23)
val HeroMossDeep = Color(0xFF1F2A19)

// ===== Rust = "do something" (CTA, active tab, stat numbers, stamps) =====
val AccentCopper = Color(0xFF9A4526)
val AccentCopperDeep = Color(0xFF72301A) // end of the primary-button gradient

// Apricot: accent words and kickers on dark surfaces (8.1:1 on HeroMossDeep).
val AccentCopperLight = Color(0xFFF2B27A)

// ===== Brass = Premium. Fills and ornaments only — NEVER text on paper (2.8:1). =====
val Brass = Color(0xFFB8893A)
val BrassLight = Color(0xFFE2C07E) // brass text / icons on dark moss (8.6:1)
val BrassInk = Color(0xFF241B0C) // text on brass fills (5.4:1)
val BrassText = Color(0xFF805F28) // the text-safe brass: labels on paper (≥ 4.6:1 on every paper)

// ===== Ink =====
val TextOnCreme = Color(0xFF26301F) // primary text on paper (11.4:1 on PaperBottom)
val InkMuted = Color(0xFF5B6350) // secondary text on paper (≥ 4.9:1 on every paper)
val MarginaliaInk = Color(0xFF3F4A33) // marginalia / Caveat sub-lines (≥ 7.3:1)
val MarginaliaBorder = Color(0xFF9A4526) // = AccentCopper, 2dp left border on citations
val TextOnHero = Color(0xFFFFF8EE) // text on dark moss, photos and rust
val OffwhiteWarm = Color(0xFFFFF8EE)

// ===== Match-confidence grades (Lifelist stamp rows) — unchanged in 1.3.0 =====
val MatchHigh = Color(0xFF7CA868) // ≥80% confidence
val MatchMid = Color(0xFFD9B45A) // 60–79%
val MatchLow = Color(0xFFC07560) // <60%

// ===== Stamps =====
val StampLocked = Color(0xFFCDBB9C) // dashed outline + "?" on locked stamps (decorative)
val StampLockedBg = Color(0x00000000) // locked stamps are open circles on the paper
val StampUnlockedBg = Color(0x1F9A4526) // 12% rust behind in-progress stamps
val StampNavy = Color(0xFF1F3A5F) // rare / red-listed trophies
