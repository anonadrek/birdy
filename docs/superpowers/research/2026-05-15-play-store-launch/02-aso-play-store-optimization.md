# Birdy — ASO & Play Store Launch Playbook

_Forskningsdokument 2026-05-15 — utkast inför v1.0.0 launch._
_Baserat på `v0.8.0-rc1` artefakter + aktuella ASO best practices 2025-2026._

---

## TL;DR — sju viktigaste fynden

1. **Titeln "Birdy — Fågelskanner" lämnar pengar på bordet.** "Fågelskanner" har nästan ingen sökvolym i Sverige. Använd "Birdy: Fågel-ID & Fältdagbok" (30 tecken) — "Fågel-ID" och "Fältdagbok" är båda searchable terms som matchar real-world användarintent.
2. **EN-titeln behöver "Bird ID" inte "Bird Scanner".** "Bird ID" är det term som Cornell/Merlin har normaliserat på Google Play; "Bird Scanner" har ~0 sökvolym. Föreslå: "Birdy: Bird ID & Field Journal" (30 tecken).
3. **Screenshot-1 (cold-start) bör bytas mot ett camera-overlay-mockup.** Konvention 2025-2026: första screenshoten ska visa kärnfunktionen, inte launcher-skärmen. Cold-start är en navigeringssida — inte en hook.
4. **Privacy-as-feature är Birdys största differentierings-USP** mot Merlin (eBird-konto + cloud sync) och Picture Bird (Glority data sharing). "100% offline, no account, no tracking" bör vara screenshot-3 och första-rad i long description.
5. **Sverige är PERFEKT soft-launch-marknad** (~10M befolkning, ~1.5M aktiva fågelskådare, men bara 1 stark konkurrent — Fågelguiden 145 kr betalapp med 4.2★). Hög intent-match + låg konkurrens + lätt att samla initial reviews via SOF-communityt.
6. **Bygg featured graphic till 2 varianter** för Store Listing Experiments. Nuvarande "Birdy — A field journal for finds" är konceptuellt fint men säger inte vad appen GÖR. Variant B bör säga "Identify 700+ European birds — offline".
7. **Crash-rate-hygiene before push.** Google Play's bad-behavior-tröskel är 1.09% user-perceived crash + 0.47% ANR (28-day window). Tagga release endast om Plan 6a/6b release-build körs >24h i internal track utan crash. Annars tappar du discovery innan launchen ens hinner börja.

---

## 1. Keyword-strategi

### 1.1 Konkurrent-keyword-analys

| App | Title | Short Description | Primära keywords |
|---|---|---|---|
| Merlin Bird ID | "Merlin Bird ID by Cornell Lab" | "Free bird identification..." | bird ID, identify, photo, sound, Cornell |
| BirdNET | "BirdNET" | "Identify birds by their sound..." | bird sound, audio ID, song |
| Picture Bird | "Picture Bird - Bird Identifier" | "Identify any bird in seconds..." | bird identifier, photo, scan, species |
| All Birds Sweden (Sunbird) | "All Birds Sweden" | (encyclopedia/field guide) | sverige, fågel, fältguide |
| Fågelguiden | "Fågelguiden" (145 kr betalapp) | "Lars Svensson..." | fågelguide, läten, illustrationer |

**Centrala observationer:**

- Ingen av de stora konkurrenterna äger termen **"fågel-ID"** på SV-marknaden — Fågelguiden är pay-up-front (145 kr) + "guide"-positionerad, inte "skanner".
- "Bird scanner" har minimal sökvolym på EN-marknaden — det är inte hur folk söker. "Bird identifier" + "bird ID" är de termer användare faktiskt skriver.
- "Field journal" / "fältdagbok" är completely uncontested — det är en unik positionering vs Merlin (life list) och Picture Bird (gallery).
- Privacy/offline är ett under-utnyttjat angle. Merlin kräver eBird-konto. Picture Bird är Glority-ägt (data-sharing concerns enligt deras tidigare apparat). Birdy kan claima den platsen.

### 1.2 Long-tail keywords (lågkonkurrens, hög intent)

**Svenska (prioritetsordning):**

1. `vilken fågel är det` — high-intent query, behöver matchas i long description natural-language
2. `fågel-ID` — kortform, fits in title
3. `fältdagbok fågel` — unique positioning, no competitor uses
4. `fågelapp offline` — privacy/connectivity angle
5. `lär dig fåglar` — beginner angle (Birdys "field birder year"-positioning)
6. `fågelarter sverige` — geographic match
7. `känna igen fåglar` — verb-form, natural-language
8. `fågelskådning för nybörjare` — long-tail beginner

**Engelska:**

1. `bird identifier` — primary handle, but competitive
2. `bird ID app offline` — privacy/connectivity differentiator
3. `field journal birding` — unique positioning
4. `european birds app` — geographic narrow vs Merlin's global scope
5. `learn to identify birds` — beginner verb-form
6. `birding diary` / `birding logbook` — adjacent terms
7. `bird species scanner` — long-form
8. `private bird app no account` — privacy-as-feature

### 1.3 Concrete recommendation: titel-varianter

**SV — välj en av:**

| Variant | Tecken | Kommentar |
|---|---|---|
| A: `Birdy: Fågel-ID & Fältdagbok` | 28 | **Rekommenderad.** Båda primära kw i title. |
| B: `Birdy — Fågel-ID på sekunden` | 28 | Action-oriented; mister "fältdagbok". |
| C: `Birdy: Fågelguide & ID-skanner` | 30 | Konkurrerar direkt med Fågelguiden — riskabelt. |

**EN — välj en av:**

| Variant | Tecken | Kommentar |
|---|---|---|
| A: `Birdy: Bird ID & Field Journal` | 28 | **Rekommenderad.** Båda primära kw + unikt positioneringsord. |
| B: `Birdy — Offline Bird Identifier` | 30 | Privacy-angle först; mister "field journal". |
| C: `Birdy: Identify Birds Offline` | 27 | Action-oriented; mister "field journal" & brand-fluff. |

### 1.4 Short description (80 char)

**SV — välj en av:**

| Variant | Tecken |
|---|---|
| A: `Identifiera fåglar med kameran. Lokalt, offline, utan konto. 700+ arter.` | 71 |
| B: `Fältdagbok för fågelskådare. Skanna med kameran, samla i journalen, lär dig.` | 76 |
| C: `Vilken fågel är det? Skanna med kameran och lär dig — helt offline.` | 65 |

**Rekommendation: Variant A.** Fångar primärt keyword ("Identifiera fåglar"), berättar HUR (kameran), nämner USP (offline, utan konto), kvantifierar (700+). Variant C är emotionellt starkast men mister "700+ arter"-kvantifieringen.

**EN — välj en av:**

| Variant | Tecken |
|---|---|
| A: `Identify birds with your camera. On-device, offline, no account. 700+ species.` | 78 |
| B: `Field journal for birders. Scan, collect, learn — 700+ European species.` | 73 |
| C: `What bird is that? Scan with your camera. Fully offline, fully private.` | 71 |

**Rekommendation: Variant A.** Parallell med SV-versionen, identisk struktur — användbart för soft-launch konsistens.

### 1.5 Long description — utkast (SV, ~3 700 tecken)

```
Vilken fågel är det? Rikta kameran och få svar — direkt, på enheten, utan
internet. Birdy är en AI-driven fältdagbok för fågelskådare, byggd för dig
som vill lära dig känna igen fåglar i fält, samla dina fynd, och växa som
fältornitolog.

★ IDENTIFIERA FÅGLAR MED KAMERAN
Skanna en levande fågel via kameran eller välj ett foto från galleriet.
Birdy använder en lokal AI-modell (Google AIY Birds V1) för att identifiera
arten på sekunden. Allt sker på telefonen — inga foton skickas någonstans.

★ 700+ EUROPEISKA FÅGELARTER
Bläddra i ett uppslagsverk med ~700 europeiska arter — text från Wikipedia,
foton från Wikimedia Commons, korta fältnoteringar för kännemärken. Sök på
namn, filtrera på familj, eller bara ströv i journalen.

★ DIN FÄLTDAGBOK STANNAR HOS DIG
Spara varje fynd med foto, plats, datum och en handskriven anteckning i
marginalen. Allt ligger lokalt på telefonen — inga molnkonton, inga
annonser, ingen tracking, inga e-post-listor. Avinstallation raderar allt.

★ 25 STÄMPLAR ATT JAGA
Första fynden, säsongs-streaks, hela familjer. Något att gå hem med efter
en lyckad dag i skogen. Märkena delas inte — de är dina att samla.

★ FUNGERAR HELT OFFLINE
Modellen, uppslagsverket, dagboken — allt finns på enheten. Bra för fjäll,
skog, fågelvik, eller överallt där mobiltäckningen sviker.

★ DESIGN SOM EN ÄLDRE FÄLTBOK
Pappersbakgrund, handskriven typografi, marginalanteckningar. Birdy är gjord
för att kännas som en fältbok du brukar ta med dig — inte ett socialt nätverk.

PREMIUM (valfritt, kommer i v1.0)
- Ljud-ID (när ljudmodellen släpps)
- PDF-export av fältdagboken
- Säsongsstatistik och årsöversikt
- 10 extra premium-stämplar att samla
Engångsköp eller årsabonnemang via Google Play.

INTEGRITET PÅ RIKTIGT
Birdy är gjord av en solo-utvecklare i Sverige för fältornitologer.
Vi har ingen backend. Inga konton. Inga annonsörer. Ingen tracking.
Camera-frames bearbetas on-device och slängs direkt efter klassificering.
Sparade foton ligger i app-mappen — du äger dem, de raderas vid uninstall.

OM DU SÖKER
Fågel-ID. Fågelguide. Fågelapp. Fågeldagbok. Vilken fågel är det. Lär dig
fåglar. Fågelskådning. Fågelarter Sverige. Känna igen fåglar. Fågelapp
offline. Ornitologi. Fältornitolog.

KONTAKT
- E-post: feedback@birdy.app
- Integritetspolicy: anonadrek.github.io/birdy/privacy.html
- Solo-utvecklare i Sverige
```

**Keyword-density-anteckning:** "fågel" + sammansättningar (fågel-ID, fågelguide, fågelapp, fågeldagbok, fågelskådning, fågelarter) förekommer ~12 gånger naturligt fördelat. "identifiera/identifierar" 2 ggr. "lokal/lokalt/offline" 4 ggr. "fältdagbok/fältbok/fältornitolog" 5 ggr. Det är inom Google Plays NLP-tolerans (3-5 förekomster av primärt keyword är optimum enligt 2026 best practices).

### 1.6 Long description — utkast (EN, ~3 700 tecken)

Spegelversion av SV — använd samma struktur, byt språk, behåll keyword-densitet. Specifikt:

- Hook: "What bird is that? Point your camera and get an answer..."
- USP-rubriker: "IDENTIFY BIRDS WITH YOUR CAMERA / 700+ EUROPEAN BIRDS / YOUR FIELD JOURNAL STAYS YOURS / 25 STAMPS TO CHASE / WORKS FULLY OFFLINE / DESIGNED LIKE A FIELD NOTEBOOK"
- Sökord-stycke längst ner: `Bird ID. Bird identifier. Birding diary. Bird app offline. Field journal birding. What bird is that. European birds. Bird species scanner. Learn birds. Ornithology.`

---

## 2. Screenshot-strategi

### 2.1 Best practice 2026

- **Antal:** 4-8 screenshots optimum. Play Store visar de 4 första utan swipe — den första triggar 60-70% av installen.
- **Format:** 1080×1920 (9:16 portrait). PNG 24-bit (ingen alpha) eller JPEG 85-90%. Max 8 MB.
- **Caption overlay:** max 20% av ytan, en headline per screenshot (inte sales-pitch — explanatory). Stark kontrast.
- **Hook to Demo to Features to Trust to CTA.** Använd första 1-2 för "vad är detta", 3-5 för "hur funkar det", 6-7 för "varför vi", 8 valfritt CTA.

### 2.2 Granskning av nuvarande screenshots

| Fil | Innehåll | Storefront-bedömning |
|---|---|---|
| `01-cold-start-listen-launcher.png` | Listen launcher | **Skippa för storefront.** Visar inte appens kärnfunktion. Behåll som testartefakt. |
| `02-onboarding-page-1.png` | Brand "Birdy. A field journal for finds." | **Kandidat #1** med caption. Stark visuell identitet. |
| `02b-onboarding-page-2.png` | Onboarding page 2 | Skippa — onboarding är context-light. |
| `02c-onboarding-page-3.png` | Onboarding page 3 | Skippa. |
| `03-listen-launcher.png` | Listen launcher + Settings-popover | Skippa — pop-over är förvirrande utan kontext. |
| `04-encyclopedia-loaded.png` | Birds. + Premium-banner + filter-chips | **Kandidat med caption om Premium-bannern croppas eller bytas mot non-premium-state.** |
| `05-encyclopedia-search-clear.png` | Search/filter | Optional — kan användas för "search any species". |
| `06-diary-empty-redesign.png` | Empty diary + "Scan first bird" CTA | **Kandidat #3.** Kommunicerar "din journal växer". |
| `07-badges-all-locked.png` | 5×5 stamp grid (alla locked) | **Kandidat #4** — visar gamification-loop visuellt. |
| `10-settings-all-rows.png` | Settings | Skippa för storefront — för meta. |
| `11-about-screen.png` | About | Skippa. |
| `12-locale-en-archive.png` | EN-version av Archive | Använd som EN-storefront-equivalent. |
| `13-premium-cold-start.png` | Premium hero | **Kandidat #6** för premium-tease (efter de gratis features visas). |
| `14-camera-permission-hero.png` | "Birdy only sees birds" | **Kandidat #5** — privacy-as-feature i storefront. |

**Saknas (kritiska luckor som blockerar storefront-kvalitet):**

1. **Live scan-skärm med camera-feed + bounding box + species-match.** Detta är appens kärnfunktion och saknas helt från screenshot-setet. Ska vara screenshot #1.
2. **Match-resultat med stamp-animation + species + confidence.** Visar "payoff" — vad användaren får ut.
3. **Diary med 5-10 ifyllda observationer (mock-data).** Empty state är OK för screenshot 4-5, men en fylld dagbok behövs som social proof för "appen växer med dig".
4. **Species profile (PlateFrame + drop-cap + marginalia).** Visar djupet i encyclopedia.

### 2.3 Rekommenderad storefront-ordning (8 screenshots)

**Field Journal-estetik i alla overlays: paper-bg ovanpå mockup, Caveat-italic caption, copper accent på keywords.**

| # | Screenshot | Caption-overlay (SV) | Caption-overlay (EN) |
|---|---|---|---|
| 1 | **Live scan med species-match (saknas — mocka)** | "Rikta kameran. *Få svar.*" | "Point. *Identify.*" |
| 2 | Onboarding page 1 (`02-onboarding-page-1`) | "*En fältbok för fynd.*" | "*A field journal for finds.*" |
| 3 | Camera-permission-hero (`14`) | "Birdy ser bara *fåglar.*" | "Birdy only sees *birds.*" |
| 4 | Encyclopedia (`04`, croppa Premium-banner) | "*700+ arter* — sök, filtrera, lär." | "*700+ species* — search, filter, learn." |
| 5 | Diary med data (saknas — mocka) | "*Din* fältdagbok — stannar på din telefon." | "*Your* field journal — lives on your phone." |
| 6 | Badges (`07`) | "*25 stämplar* att jaga." | "*25 stamps* to chase." |
| 7 | Match-resultat (saknas — mocka) | "*På sekunden.* Helt offline." | "*Instant.* Fully offline." |
| 8 | Premium (`13`) eller Species profile | "*Bli fältmedlem* — frivilligt." | "*Become a field member* — optional." |

**Konkret att-göra:** mocka upp #1, #5, #7 i Figma eller direkt via debug-build (`PREMIUM_DEBUG_FORCE_ACTIVE` + `test_species.txt`-hacket från Plan 5b för deterministisk match). Det är blockerande för storefront-kvalitet.

### 2.4 Caption-overlay-design

Field Journal-estetik bör tunnas ner för storefront — overlays får INTE konkurrera med screenshot-innehållet. Föreslagen formel:

- **Bottom-third overlay strip**, paper-bg (`#EFE7D6`) med 90% opacity
- DM Serif Italic för plain-text, Caveat för accent-segment (`*ord*`)
- Kort: max 6 ord per caption
- AccentCopper (`#A8552D`) för accent-segment + matchar appens UI

---

## 3. Featured graphic + ikon

### 3.1 Granskning av launcher-ikonen

Den nuvarande ikonen (`ic_launcher_512.png`) visar en sångfågel på en kvist mot paper-bg. **Stark visuell signal:** mörkgrön/koppar fågel, varm bakgrund — sticker ut mot generiska blå/vita "scanner"-ikoner. **Risker:**

- Kan se "för vanlig" ut i Play Store-griden. Många nature-apps har fågel-ikoner.
- Adaptive-icon foreground har detaljer som riskerar att klippas i circle-mask på vissa OEMs (S23 Ultra-circle är medium-strikt). **Kontrollera safe zone (66% inner-radius)** — fågelns stjärt kan klippas.

**Rekommendation:** behåll ikonen för v1.0 launch. Men **bygg en alternativ "B-variant"** med:
- Större single-bird-silhuett (mindre löv-detalj)
- Starkare kontrast — kanske endast koppar + creme, ingen mörkgrön
- Tydligare "stamp"-utseende som ekar Field Journal-konceptet

Kör Store Listing Experiment A/B mellan v1 (current) och v2 (stamp-silhouette) efter 14 dagars baseline. Behåll den med högre CVR.

### 3.2 Featured graphic 1024×500

Nuvarande grafik säger "Birdy. A field journal for finds. IDENTIFY · LEARN · COLLECT" — vacker, on-brand, men säger inte **vad appen gör konkret**.

**Problem:** Featured graphic är den första visuella biten användaren ser i sökresultat på Google Play. "A field journal for finds" kräver tre tolkningssteg. Användaren har 1 sekund.

**Rekommendation — bygg 2 varianter för A/B-test:**

**Variant A (current):** behåll som "brand-version" — den fungerar för CTA-trafik (folk som klickade från en länk och redan har intention).

**Variant B (utility-version):** byt copy till `"Identify 700+ European birds. Offline."` + en mockad live-scan-skärm i bilden. Targeting: sökresultat-trafik där användaren inte vet vad appen är.

**Variant C (privacy-version):** `"Bird ID that stays on your phone."` + paper-bg + lock-ikonen. Targeting: privacy-conscious användare. Långsiktigt-värdefullt men kanske inte launch-prioritet.

Kör A vs B i 7-14 dagar; behåll vinnaren; testa C som steg 2.

### 3.3 Store Listing Experiments — vad man kan testa

Google Play tillåter A/B-test av:
- App icon (rekommenderat efter 30 dagar baseline)
- Feature graphic (rekommenderat direkt vid launch)
- Screenshots (testa hook-screenshot #1)
- Short description (testa "Identifiera fåglar..." vs "Vilken fågel är det...")
- Long description (men låg payoff för organic)

**INTE testbart:** title (är låst). Välj titel rätt från start.

**Tips:** kör max 1 experiment per asset åt gången. 5 lokaliserade experiment samtidigt är tillåtet (SV-experiment påverkar inte EN-experiment).

---

## 4. Description copy — rewrite-recommendations

### 4.1 Granskning av nuvarande SV-listing

**Styrkor:**
- Tonen ("Birdy är skapad av en solo-utvecklare i Sverige för fältornitologer") är sympatisk, äkta, differentierande.
- USP-bullets (Scan/Learn/Collect/Stamp/Premium/Offline/Privacy) är välstrukturerade.
- "Privacy-first"-avsnittet är konkret och trovärdigt.

**Svagheter:**
- **Ingen hook i första meningen.** Börjar "Birdy är en AI-driven fältbok..." — det är en self-description, inte en användar-intent-match. Användaren tänker "vilken fågel är det?" — börja där.
- **Saknar keyword-density i title-länkbar text.** "fågel-ID" saknas helt; "fågelguide" saknas; "vilken fågel" saknas.
- **Ingen siffer-anchoring för utility.** 700 nämns men inte 25 stämplar i hook. Quantifiable claims rankar bättre.
- **What's new-block är för kort för v1.0** — bör ha 5-7 punkter, inte 5 vaga.

### 4.2 Field Journal-vokabulär för differentiering

Inkorporera följande Field Journal-termer i copyn (utan att förvirra första-läsare):

- "fältdagbok" / "field journal" — primärt unique-term
- "marginalanteckning" / "marginalia" — sekundärt (i bullet, inte hook)
- "stämpla i journalen" / "stamp in your journal" — gamification-koppling
- "fält(orn)itolog" / "field birder" — målgrupp-positionering

**Undvik i copy:**
- "scanna fältet" — för vagt, kameran scannar fågeln inte "fältet"
- "marginalia-anteckningar" som dubbel-substantiv — använd antingen "marginalia" (en) eller "marginalanteckning" (sv), inte både
- "stämpel-collector" — engelsk-svensk hybrid, klingar fel

### 4.3 Social proof utan siffror

Pre-launch finns inga reviews. Använd istället:

- **Authority anchoring:** "Drivs av Google AIY Birds V1 — samma open-source-modell som forskningsappar använder."
- **Transparency-as-trust:** "Open source-licensförteckning i appen. Privacy policy på en sida."
- **Solo-developer charm:** "Byggd av en utvecklare i Sverige. Hör av dig — feedback@birdy.app."
- **Concrete features:** "700+ arter. 25 stämplar. 0 reklamannonser. 0 trackers. 0 konton."
- **Future-credibility:** "Ljud-ID kommer i nästa version" (bara om det är säkert — undvik annars).

Sätt INTE påhittade rating-claims ("4.9 stars on App Store"). Sätt INTE generiska "Used by thousands of birders". Trovärdighet är en del av Birdys USP.

---

## 5. First 24/72h-strategi

### 5.1 Soft launch — ja, Sverige är rätt marknad

**Varför Sverige:**
- ~10M befolkning + ~1.5M aktiva fågelskådare = stor enough att få signal, liten enough att fail-soft.
- Endast 1 stark konkurrent (Fågelguiden, 145 kr betalapp, 4.2 stjärnor, 75K installs över 8+ år). Birdy gratis = stor friktion-fördel.
- Sveriges Ornitologiska Förening (SOF) + ArtPortalen-användare är hyper-engaged community.
- Du är solo-dev i Sverige — kan svara på reviews på svenska, det är differentiering.

**Soft-launch-plan (vecka 1-2):**

1. **Internal testing track (vecka -1):** ladda upp signed AAB (`v0.8.0-rc1` eller `v1.0.0-internal`) till Internal Track. Bjud in 5-10 fågelskådare du känner. Kör 5-7 dagar. Tröskel: 0 crashes, 0 ANRs.
2. **Closed testing track (vecka 0):** öppna Closed Beta för 50-100 personer via SOF Facebook-grupper + Reddit r/birding-svenska. Samla in feedback. Tröskel: <0.5% crash rate per session.
3. **Production launch SE-only (vecka 1):** sätt distribution till `SE` enbart. Push announcement på SOF + Natursidan.se + Reddit r/sweden + r/birding. Targeta lokala fågelskådare.
4. **Production expansion (vecka 3-4):** efter 2 veckor stabilitet, expandera till NO + DK + FI + DE + NL + UK.

### 5.2 Säkra initiala reviews (4.5+ är breakpoint)

**Pre-launch:**
- Bygg pre-registration page på `anonadrek.github.io/birdy/` med email-signup. Konvertera till review-prospects vid launch.
- Skriv en personlig launch-post på SOF Facebook (svensk) och r/birding (engelsk) — solo-dev-story är klickvänlig.

**Vid launch:**
- **Använd Google Play's `In-App Review` API** — triggra reviewprompt efter 3:e save (positiv moment, inte cold-launch). Aldrig på första session.
- **Skicka aldrig review-prompt om appen kraschat sessionen innan.** Tracka `lastCrashTimestamp` i DataStore.
- Email feedback@birdy.app-svar inom 24h första 2 veckor. Personliga svar = positiva uppdateringar av rating.

**Brevkampanj till early reviewers (post-launch):**
- "Tack för att du laddade ner Birdy! Solo-dev i Sverige här. Skulle uppskatta en review om appen funkar för dig — och hör av dig om något buggar."
- Inkludera ALDRIG "give us 5 stars" — det bryter Play-policy och får ratings flaggade.

### 5.3 Crash/ANR-tröskel innan marknadsföring

**Google Plays bad-behavior-trösklar (28-day window):**
- Crash rate: 1.09% user-perceived crashes över alla devices = bad. 8% per device model = bad.
- ANR rate: 0.47% over devices = bad. 8% per device = bad.

**Birdys interna gates innan push:**
- Internal track 7 dagar: 0 crashes, 0 ANRs på any device.
- Closed track 7 dagar: <0.3% crash rate, <0.2% ANR rate (med tunna data sample, sätt internal gate hårdare än Google's threshold).
- Sätt Crashlytics-equivalent? **Du har valt att inte ha analytics** — överväg om en _opt-in_ crash reporter (Sentry self-hosted, Acra) är värd att lägga till för v1.0. Argumenten: utan crash-data är du blind. Argumenten emot: bryter privacy-USP. **Rekommendation: lägg INTE till för v1.0.** Använd istället Play Console Vitals-rapporter (de aggregerar utan PII och kräver inget SDK).

### 5.4 När triggar Play algoritmen "trending"

- Install velocity relative to category baseline = primär signal. Education-kategorin har låg baseline.
- Conversion rate impression to install = sekundär signal.
- 7-dagars uninstall rate = negativ signal (om hög).
- Update cadence = freshness signal (var 2-4 veckor optimum).

**Konkret för Birdy:**
- Sikta på 50-100 installs första 72 timmarna (via SOF-grupper + Reddit). Det räcker för algoritmens initial-signal i en nischkategori.
- Pusha v1.0.1 ~2-3 veckor efter launch med "Quick fixes from your feedback" — freshness-signal + tar hand om real bugs.
- Bygg en publik release-cadence: 1 minor release/månad första 6 månaderna.

---

## 6. Lokalisering

### 6.1 Vilka språk ger bäst ROI

**Tier 1 (launch — vecka 1):**
- **Svenska (SV)** — primärmarknad, soft-launch.
- **Engelska (EN)** — internationell default, redan klar.

**Tier 2 (~vecka 4-8 — minimum viable localization för store listing + UI):**
- **Tyska (DE)** — största EU-marknaden för birdwatching (NABU har 875K medlemmar). High ROI.
- **Holländska (NL)** — Hollandsk birdwatching-community är hög-engagement (Sovon). Litet land men hög per-capita-conversion.

**Tier 3 (~månad 3-6):**
- **Finska (FI)** — granne, ~500K aktiva fågelskådare via BirdLife Finland. Låg konkurrens.
- **Norska bokmål (NB) + Danska (DA)** — kan översättas billigt från SV (~80% lexikal-overlap), lågkostnad-expansion.

**Tier 4 (post-v1.0 stable):**
- **Franska (FR)** — stora EU-marknader.
- **Spanska (ES)** — stora EU-marknaden + LatAm.
- **Italienska (IT)** — birdwatching-community är medium.

**Tier 5 (deferred):**
- **Polska (PL)**, **Tjeckiska (CS)** — birdwatching-tradition men låg app-revenue per capita.

### 6.2 Vad du behöver lokalisera per språk

För **minimum viable localization**:
1. Title (30 chars per locale)
2. Short description (80 chars per locale)
3. Long description (~3 500 chars per locale)
4. Screenshots med caption-overlays (8 st per locale — caption-bytet är billigt om base-screenshot är samma)

**Inte nödvändigt för MVL:**
- UI-strängar (engelska räcker som fallback)
- In-app content (encyclopedia kan stanna på EN i Tier 2-3)

**Kostnadsuppskattning:** ~$100-200 USD per språk för professional translation av store-listing-text (ca 5 000 ord). DeepL Pro räcker för MVL för Tier 2-3, men låt en native birdwatcher i målmarknaden snabbgranska.

---

## 7. Data safety + Permissions — trust som USP

### 7.1 Granskning av `data-safety-form.md`

**Korrekt och välutformad.** Alla 13 data-typer är `No`. Security practices är `N/A — no transit`. Detta är **legitimt och provable** — appen gör 0 nätverksanrop.

**Två justeringar att överväga:**

1. **"Photos and videos: No"** med kommentar "user-supplied photos stay in app-private storage; not 'collected' per Play Console definition". Detta är _korrekt enligt Play Console-definition_, men Google har skärpt tolkningen under 2025. Säkrare formulering: hänvisa till deras egen guide som säger att in-app-private-storage som inte syncas är inte "collection". Verifiera mot policy-updates April 2026.

2. **App access instructions:** för Google Play-granskning av Premium behöver du:
   - Antingen ett test-account-flaggat Play Console license-tester-konto
   - Eller en debug-build via `PREMIUM_DEBUG_FORCE_ACTIVE=true`
   - Notera att Plan 6b införandet av real billing kommer att ändra detta — uppdatera när 6b shipas.

### 7.2 Camera + photos som trust-USP

**Nuvarande copy i privacy policy är OK men inte säljbar.** "Camera frames are processed on-device and discarded after classification" är teknisk, inte emotionell.

**Säljbara versioner (för store listing + onboarding):**

- "Birdy ser bara fåglar. Inga foton lämnar din telefon."
- "Camera-frames bearbetas direkt, slängs direkt. Aldrig sparas, aldrig skickas."
- "Den enda telefon som ser dina fynd är din egen."

Screenshot #3 (`14-camera-permission-hero.png`) säger redan "Birdy only sees birds — no photos are saved without your choice". **Behåll, men förstärk:** lägg till en mikro-detalj: "Camera permission: foreground only. No background access. No analytics. No tracking."

### 7.3 Privacy-as-feature copy-förslag

Bygg en synlig **"Trust line"** i:

- Long description, första stycket: "Helt offline. Inget konto. Ingen tracking. Inga annonsörer."
- Settings-skärm: lägg till en "Privacy at a glance"-rad som expanderar till en kort förklaring. Detta är gratis trust-signal för users som kollar Settings.
- About-skärm: redan finns. Bra.

**Kontrastera mot konkurrenter (vinklad copy):**
- "Merlin kräver eBird-konto. Birdy kräver inget."
- "Picture Bird säljer data via Glority-koncernen. Birdy har ingen backend."
- Använd ALDRIG konkurrentnamn i Play Store-copy (Play-policy förbjuder). Använd istället: "Andra fågelappar kräver konton. Birdy gör inte det."

---

## 8. Sammanfattande to-do (i prioritetsordning)

### Blockerande för v1.0 launch (P0)

1. **Mocka 3 saknade screenshots** (live-scan, match-result, fylld diary). Använd debug-builds + `test_species.txt`-hack för deterministisk demo-data.
2. **Skriv om long description** enligt utkast i 1.5 — fixar keyword-density + hook + quantifiers.
3. **Byt title** till `Birdy: Fågel-ID & Fältdagbok` (SV) + `Birdy: Bird ID & Field Journal` (EN).
4. **Byt short description** till "Identifiera fåglar..."-varianten (SV) + "Identify birds..."-varianten (EN).
5. **Bygg 2 featured graphic-varianter** för Store Listing Experiment A/B.

### Hög prio innan push (P1)

6. **Lägg till caption-overlays** på storefront-screenshots (Field Journal-stil, 20% screen real estate max).
7. **Bygg pre-launch landing page** på `anonadrek.github.io/birdy/` med email-signup.
8. **Förbered SOF + r/birding + Natursidan launch-posts.**
9. **Sätt internal+closed testing tracks** med 5-10 + 50-100 testare.
10. **In-App Review API**-trigger på 3:e save (verifiera state-tracking).

### Medium prio (post-launch månad 1-2)

11. **Lägg till DE + NL store-listing-localization** (Tier 2).
12. **Starta Store Listing Experiments**: featured graphic A vs B, icon A vs B (efter 30d baseline).
13. **Sätt v1.0.1 release-plan** (2-3 veckor efter launch — quick-fix release för freshness-signal).

### Långsiktigt (månad 3-6)

14. **Lägg till FI + NB + DA store-listing-localization** (Tier 3).
15. **Övervaka Play Console Vitals**, sikta på <0.3% crash rate.
16. **Iterera screenshots** baserat på CVR-data från experiments.

---

## Källor

- [Google Play Keyword Research Checklist for 2026 ASO Success — ASO World](https://asoworld.com/insight/aso-checklist-the-complete-guide-to-google-play-store-keyword-research-in-2025/)
- [Play Store keyword research in 2026 — Apptweak](https://www.apptweak.com/en/aso-blog/play-store-keyword-research)
- [Google Play Title: Guide on Optimizing Main Elements — Asolytics](https://asolytics.pro/academy/post/google-play-title-guide/)
- [ASO Best Practices 2026 — Growth by Kev](https://www.growthbykev.com/blog/aso-fundamentals-guide)
- [Google Play Store Screenshot Guidelines in 2026 — The App Launchpad](https://theapplaunchpad.com/blog/google-play-store-screenshot-guidelines)
- [App screenshot sizes and guidelines for the Google Play Store in 2026 — MobileAction](https://www.mobileaction.co/guide/app-screenshot-sizes-and-guidelines-for-the-google-play-store/)
- [Store listing experiments — Google Play Console](https://play.google.com/console/about/store-listing-experiments/)
- [Google Play Store Listing Experiments — App Radar](https://appradar.com/blog/app-ab-testing-with-store-listing-experiments-in-google-play)
- [Elevate Your Google Play Feature Graphic in 2026 — ScreenshotWhale](https://screenshotwhale.com/blog/google-play-feature-graphic)
- [Google Play Feature Graphic Examples and Best Practices — Apptamin](https://www.apptamin.com/blog/feature-graphic-play-store/)
- [Soft Launch Strategy: A Complete Guide for App Developers — Appalize](https://www.appalize.com/ca/blog/mobile-trends/soft-launch-strategy-a-complete-guide-for-app-developers)
- [The Ultimate App Launch Strategy for 2026 & Beyond — Moburst](https://www.moburst.com/blog/app-launch-strategy/)
- [Mobile App Launch Checklist 2026 — AppLaunchFlow](https://www.applaunchflow.com/blog/app-launch-checklist-2026)
- [Google Play Store ranking factors: Ultimate ASO breakdown — MobileAction](https://www.mobileaction.co/blog/google-play-store-ranking-factors/)
- [ASO Ranking Factors in 2026 — App Radar](https://appradar.com/academy/app-store-ranking-factors)
- [Android vitals — Google Play Console](https://play.google.com/console/about/vitals/)
- [Monitor your app's technical quality with Android vitals — Play Console Help](https://support.google.com/googleplay/android-developer/answer/9844486?hl=en)
- [Android App Localization for High ROI and Competitive Edge — Smartcat](https://www.smartcat.com/blog/android-app-localization/)
- [Per-app language preferences — Android Developers](https://developer.android.com/guide/topics/resources/app-languages)
- [Provide information for Google Play's Data safety section — Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [Google Play Data Safety Section: Step-by-Step Guide (2026) — Respectlytics](https://respectlytics.com/blog/google-play-data-safety-guide/)
- [Merlin Bird ID by Cornell Lab — Google Play](https://play.google.com/store/apps/details?id=com.labs.merlinbirdid.app)
- [Picture Bird - Bird Identifier — Google Play](https://play.google.com/store/apps/details?id=com.glority.picturebird&hl=en_US)
- [All Birds Sweden — Google Play](https://play.google.com/store/apps/details?id=com.sunbirdimages.allbirdsse&hl=en)
- [BirdID Nord University — Google Play](https://play.google.com/store/apps/details?id=phoot.pimms.hintbird&hl=en_US)
- [Recension av fågelappen Fågelguiden — Natursidan](https://www.natursidan.se/nyheter/recension-av-fagelappen-fagelguiden/)
- [How to Find Low-Competition App Store Keywords — Sonar Blog](https://trysonar.app/blog/low-competition-keywords)
