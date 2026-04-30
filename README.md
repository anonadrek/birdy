# Birdy Bird Scanner

AI-driven Android-app för fågelidentifiering — realtidsskanning via kamera, foto-upload, och ett rikt uppslagsverk över ~700 europeiska arter.

> **Status:** v1 under utveckling. Fas 1 av 6 (Foundation) — projektet bygger och testar grönt, men ingen funktionalitet finns ännu.

## Arkitektur

Kotlin Multiplatform-app där affärslogik och UI är delad via Compose Multiplatform. v1 är Android-only; iOS-skelettet finns men aktiveras först i en senare fas.

| Modul | Innehåll |
|---|---|
| `composeApp` | Compose Multiplatform UI (delad mellan Android och framtida iOS) |
| `shared/domain` | Use cases, domänmodeller, business rules (ren Kotlin) |
| `shared/data` | SQLDelight-queries, repositories, content providers |
| `shared/ml` | `BirdClassifier` expect/actual, bildpreprocessing |
| `shared/content` | Artdatabas-loading, gamification-regler |
| `androidApp` | Android entry point, MainActivity, plattforms-actuals |
| `iosApp` | iOS-skelett (aktiveras i v2) |

Se design-specen för detaljer: [`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`](docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md)

## Komma igång

**Krav:**
- JDK 17 (Temurin/Adoptium rekommenderat)
- Android Studio Iguana (eller senare) eller IntelliJ IDEA 2024.2+
- Android SDK 35 + build-tools

**Bygga och köra:**

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug   # om enhet/emulator är ansluten
```

**Köra tester:**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest
```

**Linting:**

```bash
./gradlew ktlintCheck detekt
./gradlew ktlintFormat               # autofix formatting
```

## Vägkarta (high-level)

| Fas | Innehåll | Status |
|---|---|---|
| 1 | Foundation — KMP, Compose, CI | **klar (denna)** |
| 2 | Content pipeline (species data → species.db) | nästa |
| 3 | Encyclopedia (browse + species profile) | |
| 4 | ML & Camera (TFLite + CameraX) | |
| 5 | Diary & Gamification | |
| 6 | i18n, polish, Play Store-release | |

## Bidragande

Specs och plans i `docs/superpowers/` är källan till sanning. Brainstorming-sessioner använder Markdown-spec → implementation plan → kod.

## Licens

TBD (lägg till `LICENSE`-fil innan första publika release).
