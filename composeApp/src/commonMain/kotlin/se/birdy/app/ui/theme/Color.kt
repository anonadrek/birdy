package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color

// Mossbädd palette — locked 2026-04-30. See CLAUDE.md and visual_language_birdy_v1 memory.

// Backgrounds
val MossCreme = Color(0xFFE8E2D2) // primary background
val SandCreme = Color(0xFFD8D0BC) // stat surface, slightly darker than bg

// Hero gradient (top → bottom)
val HeroMossLight = Color(0xFF5C6E48)
val HeroMossMid = Color(0xFF3F4F30)
val HeroMossDeep = Color(0xFF2A3520)

// Accent (warm copper — CTA, active tab, stat numbers)
val AccentCopper = Color(0xFF8C5A3C)

// Text
val TextOnCreme = Color(0xFF2A3525) // primary text on background
val TextOnHero = Color(0xFFF0EAD8) // text on hero zone or accent surfaces

// Hero pill labels (lighter than TextOnHero)
val LabelOnHero = Color(0xFFC5BC9F)

// Caption/label color on creme background (between TextOnCreme and bg)
val LabelOnCreme = Color(0xFF6B6F5C)

// ===== Plan 7 redesign tokens (locked 2026-05-08) =====

// Warmer offwhite for hero text — replaces TextOnHero on redesigned screens.
// Slightly more white-toned than #F0EAD8 for pop on mossgrön gradient.
val OffwhiteWarm = Color(0xFFFFFCF0)

// Lighter copper for italic accents within hero headlines (e.g. *fånga* in
// "Tre sätt att *fånga*."). Brighter than AccentCopper so italic segments pop.
val AccentCopperLight = Color(0xFFE0A47C)

// Premium-tier visual gold gradient — used only on the premium-sparkle marker
// in onboarding (Page 3) and Listen launcher (Audio-locked card). NOT a CTA.
val PremiumGoldLight = Color(0xFFFFE8B5)
val PremiumGoldMid = Color(0xFFE8C374)
val PremiumGoldDeep = Color(0xFFB88944)

// Match-confidence color grades — used in LifelistScreen stamp rows.
val MatchHigh = Color(0xFF7CA868) // ≥80% confidence
val MatchMid = Color(0xFFD9B45A) // 60–79%
val MatchLow = Color(0xFFC07560) // <60%

// ===== Plan 7c Field Journal tokens (locked 2026-05-09) =====

// Paper background gradient — replaces MossCreme as primary bg on all
// redesigned screens. Lighter top, darker bottom.
val PaperTop = Color(0xFFF0E7D0)
val PaperBottom = Color(0xFFE6D8B8)

// Stamp (sigill) state-tints
val StampLocked = Color(0x668C5A3C) // 40% AccentCopper for dashed border on locked stamps
val StampLockedBg = Color(0x99E8E2D2) // 60% MossCreme for locked stamp interior
val StampUnlockedBg = Color(0x1F8C5A3C) // 12% AccentCopper for unlocked stamp interior

// Marginalia — left border and text color for handwritten citations
val MarginaliaInk = Color(0xFF5C6E48) // mossgrön (same as HeroMossLight but explicit token for Caveat marginalia text)
val MarginaliaBorder = Color(0xFF8C5A3C) // AccentCopper for 2dp left border
