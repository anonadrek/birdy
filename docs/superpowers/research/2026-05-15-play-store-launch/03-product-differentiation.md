# Birdy Bird Scanner — produktdifferentiering inför Play Store-launch

**Datum:** 2026-05-15
**Källor:** CLAUDE.md, memory-filer, composeApp/src/commonMain/kotlin/se/birdy/, screenshots, spec 2026-04-30-birdy-bird-scanner-v1-design.md.
**Status:** v0.8.0-rc1 (Plan 6a klar). Plan 6b återstår före v1.0.0.

## 200-ords sammanfattning

Birdy:s starkaste differentiator är **Field Journal-estetiken** — paper-bg + DM Serif Display Italic + Caveat handwriting + StampSeal + PlateFrame + OrnamentRule är ett komplett designsystem (inte styling) som ingen konkurrent matchar. Konkret kod: `theme/Color.kt`, `theme/PaperBackground.kt`, `components/StampSeal.kt`, `components/PlateFrame.kt`, `components/JournalHeadline.kt`. Den näst starkaste är **ärlig osäkerhetshantering**: threshold-routing till tre olika skärmar (Match ≥0.50 / Disambig 0.35–0.50 / NoBird <0.35) där NoBird säger `awaiting your signature` med Caveat-italic-tips istället för att forcera gissning. Tredje är **on-device + offline + ingen molnuppladdning** (AIY V1 i AAB, ~14 ms inferens på S23 Ultra). Gamification (25 stamps), KMP, marginalia är förstärkningar. Sälj INTE AI-precision (72% top-3 är inte konkurrenskraftigt mot Merlin's 90%) — sälj `förtroende` istället. Play Store-tagline (SV): "Identifiera fåglar med kameran. En fältdagbok som ser ut som fältdagböcker. Inga foton lämnar din telefon." Personas: Eva (58, biolog) + Joar (34, designer, viral seed) + Lena (45, familjenatur). Svagheter: TalkBack är partial, marginalia inkonsistent över 273 arter, fyra premium-teasers innan premium-leverans, audio saknas helt.

---

## 1. Sammanfattande tes

Birdy konkurrerar inte med Merlin Bird ID på modellprecision. Det skulle vara ett förlorat krig — Cornell Lab har 30 års data, expertvalidering och en backend som Birdy avsiktligt inte har. Birdys verkliga differentiator är **visuell identitet och tonalitet**: en konsekvent gjord 1800-talsfältdagboks-estetik (paper-bg + DM Serif Display Italic + Caveat handwriting + stamp-seals + plate-frames + marginalia) som varken Merlin, BirdNET, Picture Bird eller Seek försöker sig på. Det finns i appbutiken just nu noll AI-fågelidentifierings-appar som inte ser ut som tonad Material You eller White-iOS-form-design. Det är USP:n som syns på 1 sekund i Play Store-screenshots.

Näst starkast är **integriteten i osäkerhetshanteringen** — threshold-routing till tre olika skärmar (Match / Disambig / NoBird) med olika visuell tonalitet, istället för att alltid presentera top-1 med ett confidence-tal som ändå är meningslöst för användaren. Tredje är **on-device + offline by default** (AIY V1 bundlad i AAB, ingen backend), vilket är ett genuint privacy-USP i en kategori där de flesta gratis-apparna är annonsfinansierade och skickar foton till moln.

Allt annat — gamification, KMP, marginalia — är förstärkningar, inte huvud-USPs.

## 2. Visual identity — djupanalys

### 2.1 Vad som faktiskt finns i koden

- `theme/Color.kt` definierar två separata token-set: "Mossbädd" från Plan 1 + "Field Journal" från Plan 7c (PaperTop #F0E7D0, MarginaliaInk #3F4F30).
- `theme/PaperBackground.kt` ritar vertikalt gradient + sex deterministiskt placerade "bläck-fläckar" (drawCircle color 0x14000000) på varje skärm.
- `components/StampSeal.kt` (242 rader) implementerar tre tillstånd, tre fonter, custom dashed-circular-border via PathEffect (18 segment), -3° rotation för unlocked. Plus a11y-semantics.
- `components/PlateFrame.kt` ramar in fotot med kopparborder + "Pl. {nr} — {scientific name}, in nature" caption i Caveat — exakt 1800-talsplankor.
- `components/OrnamentRule.kt` ritar ─ ❦ ─ med horisontalt gradient och U+2766 FLORAL HEART.
- `JournalHeadline.kt` parsar *ord*-syntax → Caveat-italic ovanpå DM Serif Display Italic med 1.15× fontsize + -3° rotation.

### 2.2 Vad det är "stulet" från

- **Roger Tory Peterson Field Guides (1934–):** Plate-formatet "Pl. N — species name". PlateFrame är direkt referens.
- **Crossley ID Guide (2011):** marginalia-anteckningar bredvid huvudbilden.
- **Cornell Lab Birds of the World:** ornament-rulers, serif-rubriker.
- **Audubon-plankor (1700–1800-tal):** stamp-seals med romerska siffror.
- **Field Notes Brand:** Caveat-handwriting + kraftpapper.
- **iA Writer:** principen "två fonter med tydlig kontrast".

Estetiken är "stulen" från 200 år av prior art — det är dess styrka. Användare känner igen utan att kunna peka var ifrån.

### 2.3 Konkurrent-jämförelse

| App | Visuell stil | Differentierande |
|---|---|---|
| Merlin Bird ID | Modern Material/iOS, vita kort, blå CTA | Foto-fokus |
| BirdNET | Funktionell Material, mörk topbar | Ljud-spektogram |
| Picture Bird | Stockfoto-tung, aggressiva CTA | Freemium-funnel |
| Seek (iNaturalist) | Material You, gröna accenter | Cross-species |
| Birda | Social-feed-stil | Community |
| **Birdy** | Paper-bg + serif italic + handwriting + stamps + plates | Ingen direkt konkurrent |

### 2.4 Viral potential (rangordnad)

1. **StampSeal unlock-animation** — ghost→solid via animateFloatAsState + bottom-sheet glow-loop. TikTok #birdtok-värdigt.
2. **PlateFrame screenshot** från Species Profile — ser ut som Audubon-plansch.
3. **NoBird-skärmen** — tilted thumbnail + Caveat-italic "awaiting your signature" + tre tips. Empatisk där konkurrenter är robotaktiga.
4. **JournalHeadline-mixin** — typografi-Twitter delar gärna.

### 2.5 Anti-aesthetic risk

Låg risk för core demografin (40+). De är uppvuxna med Lars Jonsson, Peterson, Killian Mullarney, Collins Bird Guide. Pl. 1 — Cyanistes caeruleus är familjärt.

Verklig risk: 18–24-årig demografi som förväntar sig Material You. Men de är inte huvudmålgrupp.

Verklig tillgänglighets-risk: Caveat-Bold 12–13sp på paper-bg är på gränsen för presbyopi. MarginaliaInk bumpades till #3F4F30 för WCAG AA (~6.7:1) i Plan 6a T9 — bra fix. TalkBack-täckning är partial — verkligt hål inför launch.

## 3. Feature differentiation

### 3.1 Stamps vs Merlin Life List

Merlin: flat lista, funktionell.
Birdy: 25 badges (3 progression + 4 weekly + 3 monthly + 4 season + 8 family + 3 rare) + StampSeal 5×5-grid + UnlockQueue + BadgeBackfillOnAppStart.

Är det differentiator eller gimmick? Differentiator pga "tonad sport"-ton (opt-in streaks, ingen Duolingo-pressure). Screenshot `07-badges-all-locked.png` med "0 found. 25 waiting in the field." ser ut som genuin samlarhobby.

**Risk:** Lifelist-fliken heter "Lifelist" (etablerat ornitolog-term) men ser ut som stamp-collection. Förvirring.
**Rekommendation:** behåll badges på Badges-flik, positionera Lifelist tydligt som "alla arter du sett, A-Z".

### 3.2 Marginalia-fält

`species_text(species_id, locale, kind, text)` med kind = "marginalia" är ett dolt feature. Caveat-italic + AccentCopper vänster-border.

Inget konkurrent har detta. Men content-skull: 273 godkända arter, om bara 5–10 har marginalia blir det inkonsistent. Plan 2b-runbook bör inkludera marginalia i godkännande-checklistan.

### 3.3 Threshold-routing (Match/Disambig/NoBird)

`MatchThresholds.kt`:
- ≥ 0.50 → MATCH
- 0.35–0.50 → DISAMBIG (2-3 candidate-cards)
- < 0.35 → NOBIRD (tilted thumbnail + tips)

Konkurrent-beteende: Merlin visar alltid top-3 med procent; BirdNET visar confidence 0.0–1.0 utan kalibrering; Picture Bird forcerar alltid gissning + premium-popup.

Birdy admitterar osäkerhet på tre nivåer. NoBird säger "awaiting your signature" i Caveat-italic. **Detta är näst starkaste differentiatorn efter visuell identitet.** Förtroende > confidence.

### 3.4 On-device ML privacy

AIY Birds V1 (3.5 MB MobileNetV2 quantized) i AAB. ~14ms inferens på S23 Ultra. Inget skickas till moln.

Konkurrenter:
- Merlin: anonym upload (opt-in default)
- Picture Bird: AWS-API
- Seek: on-device (samma som Birdy)
- BirdNET: ljud till Cornell-servrar

Inte UNIKT (Seek har det) men differentierande mot Merlin/Picture Bird. Växande sales-argument 2026.

**Caveat:** 72% top-3 vs Merlin's ~90% är svaghet. Driv INTE den siffran. Sälj "AI vi äger transparent" istället.

### 3.5 Per-tab Premium teasers

PremiumTeaserCard (Archive) + LockedStatsPreview (Lifelist) + PremiumBadgesRow (Badges) + cold-start modal.

Subtilt och estetiskt konsistent. MEN fyra touch-points + cold-start modal innan premium-features faktiskt levererats (Plan 6b) är overkill. Skala ner till Archive + Settings tills Plan 6b shippar.

## 4. Teknisk differentiering

### 4.1 KMP + Compose Multiplatform

Inte synligt för användaren idag. iOS-skelett inte byggt. v1 = Android-only. Inte ett USP idag, möjligen post-v1.0 när iOS shippas.

### 4.2 Real TFLite 14ms p95, top-3=72%

Plan 4b accuracy_report_2026-05-08.md: top-1=52%, top-3=72% på 25 Wikimedia-bilder. Ärlig prediktion: real-world 10–25pp lägre.

Merlin ~90% (top-1 på handpickade). BirdNET ~80% audio.

Inte konkurrenskraftigt som primary selling-point. Men:
- För blåmes/talgoxe/koltrast i 5m: fullt tillräckligt.
- För sällsynt vadare på 50m: inte heller Merlin levererar.

**Strategi:** sälj "kapabel + ärlig", inte "bäst".

### 4.3 Solo-dev + Claude Code som story

Pro: "Solo dev + AI assist" är 2026-trendig (Wired/Verge/HN älskar dessa). Indie-koden är hantverksmässig.

Risk: Användare bryr sig om att appen fungerar, inte hur den är byggd. "AI-byggd" kan vara liability för anti-AI segment.

**Rekommendation:** håll i About-Settings + PR-vinkel POST launch. Inte i Play Store-första-rad.

## 5. Personas

### Träffar bra:

**Eva, 58, biologilärare, Trollhättan.** Lars Jonsson hemma. Hatar Merlin's iOS-look. Pl. 1 — Cyanistes caeruleus får henne att le. Betalar 99 SEK/år efter en vecka. Field Journal är HENNES estetik.

**Joar, 34, produktdesigner, Stockholm.** Typografintresserad. Fastnar på icon. Laddar ner för det är vackert. Delar screenshot på Mastodon. Viral seed.

**Lena, 45, familjenatur, Göteborg, två barn (8+11).** Vill att barnen lär sig naturen. Stamp-mekaniken hookar barnen. Hon betalar för Premium eftersom barnen kräver fler stamps.

### Faller utanför:

- **Avancerade entusiaster** som vill ha eBird-integration: ingen molnsynk i v1. Hamnar i v1.5.
- **Total nybörjare** som vill ha video-tutorials: Pl. 1 förutsätter förståelse.
- **Audio-ID-användare:** v1.0 har ingen audio-pipeline. BirdNET äger nichen.
- **TalkBack-användare:** partial täckning. Bör adresseras eller bli explicit "Known Limitations".

## 6. Top-USPs rangordnade

### USP 1 — Field Journal-estetiken
Konsistent designsystem. Filer: theme/, components/StampSeal.kt, PlateFrame.kt, JournalHeadline.kt.
- SV: "Som en fältbok. Som en kamera. Som en app."
- EN: "A field journal that knows what you saw."

### USP 2 — Ärlig osäkerhetshantering
Match/Disambig/NoBird. Files: ui/match/.
- SV: "Vet när den inte vet."
- EN: "Honest about what it doesn't know."

### USP 3 — On-device + offline
AIY Birds V1 i AAB. ~14ms.
- SV: "Inga foton till moln. Bara du och fågeln."
- EN: "Stays on your phone. Always."

### USP 4 — Samlarspelet
25 badges, opt-in streaks, stamps.
- SV: "Samla stämplar för varje art."
- EN: "A stamp for every species."

### USP 5 — Marginalia per art
Caveat-italic anteckningar med kind="marginalia".
- SV: "Med en anteckning du läser, inte bara fakta du minns."
- EN: "With handwritten notes worth re-reading."

### USP 6 — Tvåspråkig från dag ett
SV + EN i UI och content.
- SV: "Svenska + engelska från start."
- EN: "Bilingual, by design."

### USP 7 — Indie-craft (PR-story)
Story för bloggar/poddar EFTER launch. Inte Play Store-listing.

## 7. Play Store-prioritering

### App-icon
Field Journal från `docs/superpowers/icon-concepts/final/` (Plan 6a T3). Användarens eget set efter att ha avvisat auto-generated. Rätt val.

### Screenshots (rangordnade)
1. Match-skärmen — essensen
2. Species Profile med PlateFrame — djupet
3. Badges-grid med stamps — samlarspelet
4. Encyclopedia loaded — bredden (273+ arter)
5. NoBird-skärmen (om kapturbar Plan 6b) — ärligheten
6. Listen launcher cold-start — tonen

### Första line of description
- SV: "Identifiera fåglar med kameran. En fältdagbok som ser ut som fältdagböcker. Inga foton lämnar din telefon."
- EN: "Identify birds with your camera. A field journal that looks like a field journal. Photos stay on your phone."

### Vad som INTE ska figurera
- AI-precision-siffror
- "AI-powered" som primär headline (commodity 2026)
- KMP / Compose som user-facing feature

## 8. Ärliga svagheter att adressera före v1.0

1. **TalkBack-täckning är partial** (Plan 6a T15). Push till körbar core-flow med skärmläsare.
2. **Marginalia-content inkonsistent** över 273 arter. Inkludera i Plan 2b-checklistan.
3. **Premium-teasers är 4 ställen utan premium-leverans** (Plan 6b). Skala ner till Archive + Settings.
4. **Lifelist-positioneringen otydlig.** Heter Lifelist men ser ut som stamps. Antingen flytta stamps eller renämna.
5. **Audio-ID saknas helt.** Listen-launcher suggererar audio. "coming soon"-formulering är ärlig men förväntningsbrytande.

## 9. Slutsats

Birdys mest underskattade tillgång är inte AI:n, gamification, eller KMP. Det är att appen ser ut som något en människa designat med omsorg för en specifik målgrupp. Sällsynt 2026. Den syns i screenshots och håller över sessions. Tre konkurrenter (Merlin, Picture Bird, BirdNET) skulle behöva 6–18 månaders designarbete för att replikera Field Journal-estetiken, och de skulle inte göra det för det inte är deras strategi.

Mest överskattade tillgång: AI-precisionen. 72% top-3 är inte konkurrenskraftigt. Att INTE sälja den siffran är klokt; sälja "ärlighet om osäkerhet" (NoBird) är bättre.

Inför launch: prioritera att Play Store-screenshots säljer estetiken på 3 sekunder, att svensk-första copy talar till Eva och Lena, att TalkBack täcker core-flow. Premium-features kan vänta — bättre shippa v1.0 utan billing och addera v1.1 med billing när premium-värdet faktiskt levererats.
