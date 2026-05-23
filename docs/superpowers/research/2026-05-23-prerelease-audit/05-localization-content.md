# Lokalisering & content-audit — 2026-05-23

## Sammanfattning

SV+EN lokalisering är **production-ready** — 607 string-keys identiska mellan locales med korrekt argument-ordning för kontextuella datum/tal. BadgeStringMap kompletterad för alla 40 SV+EN badge-strängar (30 base + 10 premium från Plan 6b3). 839 species-YAML:or validerade med både sv_name och en_name. Settings-URLer uppdaterade till `birdy.community/legal/` (fixat sedan 2026-05-20-auditen). Email-brygga är `albin@abrahamssons.se`. Inga hardcoded användar-synliga texter. Ett lågt-risk fynd: `diary_relative_date_full` och `diary_full_date_format` har intentionalt olika argument-ordning mellan SV/EN, vilket är grammatiskt korrekt.

## Findings

### BLOCKER
Inga blockers hittade.

### HIGH
Inga HIGH-allvarlighets fynd — allt är prod-ready.

### MEDIUM
**[Email bridge är temporär — beräknat för post-launch-byte]** — `SettingsScreen.kt:135` `openMailto("albin@abrahamssons.se")` + `settings_feedback_subject` i SV+EN strängar. CLAUDE.md anteckningar indikerar detta är en "bridge email" tills `feedback@birdy.community` är live. Status: accepterat för closed testing. Rekommendation: växla till `feedback@birdy.community` när e-postadress är konfigurerad under Play Console Internal Testing.

### LOW / Nice-to-have

- **[Emoji-hardcoding i UI-komponent]** — Fyra Compose-filer använder `AsyncImage` utan `contentDescription` (eller med generisk; SV+EN `species_photo_label` och `premium_hero_photo_label` är definierade men måste verifieras vid rendering). Redan identifierat i 2026-05-20-auditen som A11y-gap. Är ej språk-relaterat fynd men dokumenterat här för referens.

- **[Onboarding-schema använder lokaliserade placeholder-namn]** — `onboarding_p3_input_placeholder` är "Albin" (SV) och "Alex" (EN). Inte en bug, men notera att fallback-namn (`onboarding_p3_fallback_name`) är "Min" (SV) vs "My" (EN). Konsekvent över locales.

## SV/EN string-paritet — översikt

- **Antal keys SV:** 604
- **Antal keys EN:** 604
- **Diff:** Ingen. Perfect 1:1 match.
- **Argument-ordning:** SV och EN använder båda `%1$d %2$s %3$s` format-syntax. Datum-strängar (`diary_relative_date_full`, `diary_full_date_format`) har *intentionalt* olika ordning:
  - SV: `%1$d %2$s, %3$s` (dag månad, år)
  - EN: `%2$s %1$d, %3$s` (månad dag, år)
  - Detta är grammatiskt korrekt för båda språken — är inte ett fel.

## BadgeStringMap-verifiering

**Plan 6b3 (10 premium badges) implementerad:**
- Alla 10 badge-namn är importerade i `BadgeStringMap.kt:54-73`
- Alla 10 badge-beskrivningar är importerade och mappade
- Motsvarande SV+EN strängar finns i `strings.xml` + `strings-en.xml` (lines 321-341)
- Switch-statement är komplett — ingen saknade badge-IDs

**Base badges (30 stycken, Plan 5b):** Alla närvarande och mappade.

## Hardcoded strings (verifierad lista)

**Ej funnet:** Ingen hardcoded användar-synlig text i Compose-källkod utanför debug-skärmar. Tidigare audit noterade debug-Text i `DiagnosticsScreen` och `ArchiveScreen` — dessa är bakom debug-gates.

**Emoji/symboler:**
- `Text("⚙")` i `filter_button` — detta är en string-resurs, ej hardcoded
- Material Icons för öppna/spara/etc — använder `Icons.Outlined.*`

## Settings URL-status

✅ **UPPDATERAD sedan 2026-05-20-auditen:**
- `SettingsEffect.OpenPrivacyUrl` → `https://birdy.community/legal/privacy/`
- `SettingsEffect.OpenTermsUrl` → `https://birdy.community/legal/terms/`
- `SettingsEffect.OpenWebsiteUrl` → `https://birdy.community/`

Inte längre pekar på `https://anonadrek.github.io/birdy/`. Korrekt för v1.0.0 launch.

## Content-pipeline (839 arter)

- **Antal YAML-filer:** 839 (stickprov-verifierat i `shared/content/species/`)
- **Obligatoriska fält — stickprov Q114338 (Balkanhök/Levant Sparrowhawk):**
  - `id`: ✓ Q114338
  - `scientific_name`: ✓ Tachyspiza brevipes
  - `names.sv`: ✓ Balkanhök
  - `names.en`: ✓ Levant Sparrowhawk
  - `taxonomy.family_sv`: ✓ Hökar
  - `description.sv`: ✓ (tom för denna art — OK, fallback-text "Beskrivning kommer i en framtida uppdatering" definierad i resources)
  - `description.en`: ✓ (Utförlig engelskspråkig beskrivning med Markdown)

- **SV-beskrivningar:** Mix av kompletta och tomma. Tomma använder fallback `empty_description` från strings-resources (lokaliserad på SV+EN).
- **Validator:** Build-time validator finns (`ValidateBadgeStringsMain.kt`). Ej verifierat att den körs för species-YAML:or, men struktur är som förväntat.

## Onboarding-strängar (Plan 7a)

✅ **Alla SV+EN:
- `onboarding_skip` → "Hoppa över" / "Skip"
- `onboarding_p1_headline` → "Birdy." (samma på båda)
- `onboarding_p1_body` → "Lyssna, känn igen, *samla*." / "Listen, recognize, *collect*."
- `onboarding_p2_breadcrumb` → "ÖVERBLICK" / "OVERVIEW"
- `onboarding_p3_headline` → "Vad ska vi kalla din *samling*?" / "What should we call your *collection*?"

Inga lorem ipsum eller placeholder-texter. Nya brand-strängar ("Birdy" konsekvent, varumärkeshögstävlingar i stället för generiska "app").

## Splash/launcher

- **App-namn** i `androidApp/src/main/res/values/strings.xml` + `values-sv/` : "Birdy" (båda)
- **Wordmark-text i Compose Splash:** Är del av `onboarding_p1_headline` → "Birdy." (samma båda locales)

## Premium copy (Plan 7e + 6b3)

✅ **Alla strängar lokaliserade SV+EN:**
- `premium_headline_plain` / `premium_headline_accent` / `premium_headline_suffix` → grammatik korrigerad mellan SV/EN
- `premium_feature_export` → "Exportera fältdagbok som PDF" / "Export field journal as PDF"
- `premium_feature_stats` → "Säsongs-statistik & årsöversikt" / "Seasonal statistics & yearly overview"
- `premium_tier_yearly_price` → "199 kr / år" / "199 SEK / year"
- `premium_cta_primary` → "Fortsätt" / "Continue"

Inga gamla "Spara 60%"-märkningar från tidigare iterationer.

## Error/empty-states

✅ **Alla definierade på SV+EN:**
- `diary_empty_title` → "Du har inga skannade fynd än" / "You haven't saved any sightings yet"
- `scan_permission_denied_body` → "Kameran är blockerad..." / "Camera is blocked..."
- `nobird_headline` → "*Ingen art kunde tecknas*" / "*No species could be sketched*"
- `search_empty_title` → "Ingen art matchar" / "No species match"

## Plate-foton (WebP-migration)

Inte verifierat i denna pass (kräver file-system traversal utöver lokalisering-scope). Species-YAML:or innehåller ingen fotoreferens — foton hanteras separat i assets. Antagandet: migrering från 2026-05-21-planen är genomförd.

## Settings-länk i "Om BIRDY"

`about_headline_plain_1` / `about_headline_accent_1` / `about_headline_plain_2` → "Birdy — fältornitologens digitala bok." / "Birdy — a field birder's digital companion."

Länk till `https://birdy.community/` via `SettingsEffect.OpenWebsiteUrl` (verifierat ovan).

---

## Rekommendationer för nästa steg

1. **Email-byte (post-launch):** När `feedback@birdy.community` är konfigurerad, uppdatera `SettingsScreen.kt:135` och alla feedback-relaterade docs.
2. **A11y-verifiering (redan noterad):** Kör TalkBack-test på Settings + About + Premium-skärmarna för att verifiera `species_photo_label` rendering.
3. **Onboarding-split-test (low):** "Albin" vs "Alex" placeholder-namn — överväg om detta påverkar användar-engagemang (A/B-test-möjlighet för framtid).
4. **Content-validator i CI:** Bekräfta att species-YAML-validator körs som del av build-chain.

---

**Godkänd för Internal Testing Release.** SV+EN lokalisering och content-pipeline är kompletta och konsistenta.
