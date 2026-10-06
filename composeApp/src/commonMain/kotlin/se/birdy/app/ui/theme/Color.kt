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
val OutlineInk = Color(0xFF7D7766) // Material outline: interactive boundaries (≥ 3:1 on every paper)

// ===== Dark moss surfaces (photo scrims, Premium, hero gradients), light → deep =====
val HeroMossLight = Color(0xFF3A4A2E)
val HeroMossMid = Color(0xFF2C3A23)
val HeroMossDeep = Color(0xFF1F2A19)

// Dark glass behind icons drawn directly on a photo (Premium's close button; BackButton/
// GearButton's onDark variants reuse it via the shared GlassIconButton). A translucent WHITE
// glass was tried first and was invisible: both the icon and the glass are light, so lightening
// an already-bright photo further leaves almost no contrast between them.
//
// alpha is pinned to ≥3:1 for TextOnHero on a worst-case (blown-out white, no photo/scrim credit)
// backdrop — GlassOnPhotoContrastTest computes contrastRatio(TextOnHero, compositeOver(Black,
// alpha, White)). 0.30 (the original value, measured against one bundled photo's top-right
// region — ≈6.7:1 there, but that was one fixed bright-but-not-blown-out asset, not a worst
// case) only clears ≈2.0:1 against pure white with no credit for the hero's global scrim, or
// ≈2.85:1 with it — both below AA. 0.45 clears ≈3.2:1 with no scrim credit at all.
val GlassOnPhoto = Color.Black.copy(alpha = 0.45f)

// ===== Over and behind bird photos: neutral only (2026-10-06) =====
// Albin: "the green over the bird takes away from it, you can't see the species clearly". The
// moss scrims over the photos are gone; a bird photo now shows in its true colors. What text
// over a photo still needs is a NEUTRAL darkening (PhotoScrim at a partial alpha, confined to
// the text — see PhotoHero and the Mina arter recap card), and what shows behind a photo while
// it loads is a neutral dark gray, not a green flash. PhotoHeroContrastTest pins both as hue-free.
val PhotoScrim = Color(0xFF000000)
val PhotoLoading = Color(0xFF1E1E1E)

// The band the name sits on when the text is below the photo (PhotoHero's textBelowPhoto: Match,
// species profile). The same neutral gray the photo fades out into, so the photo needs no scrim
// at all there. PhotoHeroContrastTest proves every hero text line on it (kicker ≈ 9.1:1).
val PhotoBand = PhotoLoading

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

// ===== Match-confidence grades (Lifelist stamp rows) — text colors, AA on every paper =====
val MatchHigh = Color(0xFF4E6D3F) // ≥80% confidence
val MatchMid = Color(0xFF7D611D) // 60–79%
val MatchLow = Color(0xFF98503C) // <60% (darkened from 9B523D — was 4.50:1 on SandCreme, razor-thin AA)

// ===== Stamps =====
val StampLocked = Color(0xFFCDBB9C) // dashed outline + "?" on locked stamps (decorative)
val StampLockedBg = Color(0x00000000) // locked stamps are open circles on the paper
val StampUnlockedBg = Color(0x1F9A4526) // 12% rust behind in-progress stamps
val StampNavy = Color(0xFF1F3A5F) // rare / red-listed trophies

// 12% StampNavy tint — Archive red-listed tag pill (T10b, ≥7.4:1 for StampNavy text on every paper).
val RedListTagBg = Color(0x1F1F3A5F)
