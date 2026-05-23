# Birdy v1.0 — strategisk launch-roadmap

> **Skapat:** 2026-05-15 — direkt efter att 5 forskningsagenter levererat (filer 01–05 i denna katalog).
> **Syfte:** Binda ihop research-fynden med Plan 6b-scope och nuvarande release-planering till en prioriterad arbetsordning från `v0.8.0-rc1` → `v1.0.0` Play Store.
> **Källor:** Filer 01–05 i denna katalog + `CLAUDE.md` + memory (`project_release_v1.md`, `project_plan_scheduling.md`).

---

## TL;DR — den korta vägen

**Två launch-blockerare som flyttar in i Plan 6b-scopet från forskningen:**

1. **Google Play Billing v8** — INTE v6 som CLAUDE.md säger. v6 är deprecated 2026-08-31; v8 är mandatory för nya appar efter det datumet. Vid v1.0-launch är vi mindre än 4 månader från cutoffen. Plan 6b ska skrivas direkt mot v8.
2. **"Spara 60%"-stämpeln måste bort** — bryter EU Omnibus-direktivet (Art. 6a, kräver legitim referens-månadspris som vi inte har) + Google Play 2025 dark-pattern-policy. App-rejection-risk om vi släpper med den.

**En tidskritisk extern process som måste startas idag:**

3. **Closed Testing-spåret för nya personliga developer-konton** — Google kräver **14 dagars continuous testing med 12 testare** innan production access. Om launch är 2026-05-29 är dagens datum 2026-05-15 = exakt deadline. Måste startas i dag.

**En kritisk risk som forskningen inte adresserade men som syns i memory:**

4. **ML hit-rate ~10% i fält** trots top-3=72% i eval (per `project_plan_scheduling.md`). Beslut hittills: defer post-v1.0 + sänk threshold till 0.10 + fixa mean/std. Forskningen säger sälj "ärlig osäkerhetshantering" som USP — men det funkar bara om Match-skärmen faktiskt får rätt fågel **ibland**. Om verklig hit-rate är 10% blir Birdy 1-stjärniga reviews på dag 1.

---

## Strategisk prioritering — i ordning

### Tier 0 — Måste-besluta innan annat (denna vecka)

| # | Action | Varför | Ref |
|---|---|---|---|
| 0.1 | **Starta Closed Testing-spåret idag** med 12 testare opt-in | 14-dagars Google-krav blockerar Production-publishing | 05 §1 |
| 0.2 | **Bilda AB** — pågående enligt memory | Krävs för Play Console-konto och Privacy Policy "Birdy AB, org.nr…" | 04 §6 |
| 0.3 | **Beslut: fix ML preprocessing-bug PRE-launch eller leva med 10% hit-rate?** | Forskningens USP-narrativ kräver att Match ibland träffar rätt; 10% = launch-failure-risk | memory `project_plan_scheduling.md` Phase 1 |
| 0.4 | **Beslut: skala ner Premium-teasers innan billing levererats?** | Forskning säger 4 teasers + cold-start-modal är overkill innan Premium-features finns | 03 §3.5 |

### Tier 1 — Plan 6b scope (måste levereras före v1.0)

Plan 6b ska brainstormas + skrivas med fynden integrerade. Föreslaget scope:

**A. Billing & purchase-flow**
- [ ] Wire Google Play Billing **v8** (inte v6) — replace `purchase()`-mock i `PremiumViewModel`
- [ ] Implementera **Restore Purchases** i Settings (Play Policy-krav)
- [ ] On-device receipt validation via Licensing public key (~50 LOC, ingen backend behövs)
- [ ] Konfigurera **7-dagars grace period** + account hold i Play Console
- [ ] Polla `queryPurchasesAsync` vid cold-start + 1×/dygn (Google triggar inte `REVOKED` automatiskt vid refund)

**B. Premium-prissättning & UI-fixar**
- [ ] **Ta bort "spara 60%"-stämpeln** från `PremiumScreen.kt` + tillhörande strängar (EU Omnibus + dark-pattern-policy)
- [ ] **Bumpa priser:** Yearly 199→299 kr, Lifetime 499→699 kr (i Play Console + strängar)
- [ ] **Fixa EN-bug:** `values-en/strings.xml` säger "199 kr / year" — ska vara `$29 / year` eller `299 SEK / year`
- [ ] **Lägg till "Auto-renews yearly at 299 kr · Cancel anytime"** som Caveat-sub under Yearly TierCard
- [ ] **Re-ordna premium-features:** Audio överst, sen PDF, sen Statistics, sen Badges
- [ ] **Throttla cold-start-modal:** från 1×/dag → 1×/3 dagar + 7-dagars first-install-grace
- [ ] **Skala ner per-tab teasers:** Archive + Settings först; aktivera Lifelist + Badges + SpeciesProfile när Premium-features faktiskt levererats

**C. Premium-feature-leverans**
- [ ] **Audio-ID** (högst FOMO; Listen-launchern lovar redan något vi inte levererar)
- [ ] **PDF-export** (pappersbok-känsla för 60+-målgruppen)
- [ ] **Säsongs-statistik** (Lifelist `LockedStatsPreview` ska få verkligt innehåll)
- [ ] **10 premium-badges (field marks)** — fyll `premium_badges.yaml` med riktig content + ikoner

**D. Test-infrastruktur**
- [ ] `test_species.txt`-style mekanism för Match/Disambig/NoBird → deterministisk screenshot-pipeline (Plan 7d hade 2/8 screenshots saknade pga avsaknad av detta)

**E. Carry-over från Plan 6a §5 partial**
- [ ] Aktivera **GitHub Pages** i repo-settings → verifiera `https://anonadrek.github.io/birdy/privacy.html` returnerar 200
- [ ] **TalkBack-walkthrough** på SM-S918B: scan → match → save → archive → species profile → badges → settings

### Tier 2 — Bör levereras före v1.0 men inte hård blocker

- [ ] **Title-byte i Play Console:** `Birdy: Fågel-ID & Fältdagbok` (SV) / `Birdy: Bird ID & Field Journal` (EN) per ASO-research
- [ ] **Tre saknade screenshots** för storefront: live-scan med camera-feed, match-resultat, fylld diary (5–10 obs)
- [ ] **Featured graphic** (1024×500) i Field Journal-stil
- [ ] **Granska & skriva om `docs/play-store/store-listing-{sv,en}.md`** med USP-narrativet från fil 03
- [ ] **Privacy-as-feature copy** i description (utan att namnge konkurrenter — Play-policy förbjuder)
- [ ] **Tier 2-lokalisering förberedd:** DE + NL strängar (faktisk push först 4-8 veckor post-launch)

### Tier 3 — Launch-vecka (T-7 → T-0)

- [ ] Soft launch i bara Sverige först → samla initial reviews → utvidga
- [ ] **Show HN** tisdag-torsdag 15:00 svensk tid, KMP-vinkeln
- [ ] **Reddit r/birding + r/SideProject** showcase-format (90/10-regeln)
- [ ] **`Fåglar inpå knuten` Facebook-grupp** (200k+ medlemmar) efter mod-approval
- [ ] Pressrelease-paket redo: skärmavbilder, ikon-PNG, en-pager-text (SV + EN), credits
- [ ] Crash-rate-tröskel + ANR-tröskel innan global push (verifiera i Play Console Vitals)

### Tier 4 — Post-launch dag 1–90

- [ ] Mäta D7-retention > 12%
- [ ] Mäta obs/aktiv/vecka > 2
- [ ] Mäta premium-conversion 3–5%
- [ ] Vid vecka 16 om < 1 000 downloads + 0 conversions → fixa ASO först (panik-sänk INTE priset)
- [ ] **Phase 1 ML-diagnos** — bygg debug-screen som dumpar 224×224 input-tensor; jämför device-vs-desktop topK
- [ ] **Phase 2 ML** — fine-tune AIY V1 på iNaturalist research-grade Aves
- [ ] **Plan 2b content-backfill** kan köras parallellt med launchen (273 → 700 arter; pure-data)

### Tier 5 — v1.1+ (deferrade)

- [ ] **Månads-tier** (49–59 kr/mån) om data säger det efter 3–6 månader
- [ ] **Free trial** (7 dagar) när audio + PDF har verifierats live
- [ ] **Push-notiser** (Plan v1.5)
- [ ] **iOS-build** (KMP-skelett finns)
- [ ] **eBird-integration** / cloud-sync (v1.5+)

---

## Konvergens-punkter (där flera fynd är överens)

Forskningsagenterna landade självständigt i samma slutsatser — dessa är robusta beslut:

1. **Sluta sälja AI-precision; sälj förtroende** (03 + 01 + 02) — 72% top-3 är inte konkurrenskraftigt mot Merlins ~90%; "honest about uncertainty" är.
2. **Privacy-as-feature är största USP mot Merlin** (02 + 03 + 04) — Merlin uppladdar foton, Picture Bird kör via Glority-cloud; Birdy stannar på telefonen.
3. **Sverige först som soft-launch** (01 + 02 + 04 + 05) — Fågelguiden är enda starka konkurrent och har ingen AI; bygg reviews här före global push.
4. **Ta bort "spara 60%"-stämpeln före launch** (04 + 02) — rejection-risk + EU-regulation.
5. **Audio är högsta-FOMO premium-feature** (04 + 03 + 01) — Listen-launchern lovar redan något du inte levererar; Plan 6b måste skicka audio.
6. **Stamp-mekaniken är retention-loop** (05 + 03) — push-notiser saknas till v1.5, så stamps är enda mekanik som drar tillbaka användaren dag-2+.

## Stora oberoende risker

### Risk A — ML hit-rate i fält ≠ ML hit-rate i eval

`project_plan_scheduling.md` (memory) säger: "User reported ~10% top-3 hit-rate in field testing despite eval showing 72%." Diagnos är deferred post-v1.0. Mitigation hittills: threshold sänkt 0.35 → 0.10 + mean/std fix.

**Implikationer för forskningens narrativ:**
- USP "ärlig osäkerhetshantering" (Match/Disambig/NoBird) bryts om Match-skärmen aldrig får rätt fågel
- Folk lämnar 1-stjärnor om appen aldrig identifierar deras blåmes
- "Privacy-as-feature" är inte tillräckligt om kärnfunktionen inte fungerar

**Föreslagen åtgärd:** Lägg in Phase 1 ML-diagnos (1–2 dagar) som ny task i Plan 6b. Hypoteser i ordning: (a) `ImageProxy.rotationDegrees` ej applicerad, (b) ImagePreprocessor stretchar i st f center-crop, (c) RGB/BGR swap i YUV→Bitmap. 1–2 dagars förseningskostnad är försvinnande liten jämfört med launch-fiasko-risk.

**Behöver beslut från användaren.**

### Risk B — Audio är launch-kritiskt men inte byggt

Forskningen säger:
- Audio är högst FOMO (04 §3.3)
- Listen-launchern (gear-button, Plan 7e) lovar audio redan i UI
- "Coming soon"-formulering är ärlig men förväntningsbrytande (03 §8.5)

Det betyder Plan 6b måste leverera audio. Men audio är ett betydande tekniskt arbete (BirdNET-modell? AIY-audio? Real-time spectrogram-visning?). Detta är inte småplock.

**Föreslagen åtgärd:** Brainstorma Plan 6b INNAN den skrivs — låsa scope: levererar vi audio i v1.0, eller "coming soon"-banner + audio i v1.1? Båda är okej men måste beslutas explicit.

### Risk C — Premium-teasers innan Premium-features

Per fil 03 §3.5: fyra premium-teasers + cold-start-modal är overkill när Premium-features fortfarande är mock. Användarna ser teasers, klickar, ser PremiumScreen, betalar, får ingenting nytt.

**Föreslagen åtgärd:** Stäng av teasers i Lifelist + SpeciesProfile + Badges tills audio/PDF/stats/badges levererats. Behåll Archive + Settings.

---

## Beslut tagna 2026-05-15 (efter forskningen)

1. **Launch-datum: 2026-06-01** (~17 dagar). Closed Testing-spåret måste starta senast 2026-05-18 för 14-dagars Google-krav.
2. **Audio-scope: levereras i v1.0.** Föreslagen lösning: BirdNET-Lite TFLite-modell (Cornell, MIT-style licens) som sekundär `BirdAudioClassifier` parallellt med AIY V1 för foto. Återanvänd Plan 4b-mönster.
3. **ML preprocessing-fix: pre-launch.** Phase 1 diagnos (1-2 dagar) körs i Plan 6b. Hypoteser i ordning: (a) `ImageProxy.rotationDegrees` ej applicerad, (b) `ImagePreprocessor` stretchar i st f center-crop, (c) RGB/BGR-swap i YUV→Bitmap.
4. **Pris: behåll 199 kr/år + 499 kr Lifetime.** Forskningens rekommendation 299/699 avvisas — användaren behåller dem som "introduction price". Stämpel-fix (ta bort "spara 60%") och EN-bug fortfarande in scope.
5. **Premium-teasers: BYGG features istället för att skala ner.** Eftersom audio + PDF + stats + 10 fält-märken alla levereras i v1.0 är teasers ärliga från dag 1.

**Tidsbudget för Plan 6b (17 dagar):**

| Task | Estimat | Risk |
|---|---|---|
| Google Play Billing v8 + Restore Purchases | 2-3 d | Låg |
| ML preprocessing Phase 1 diagnos + fix | 1-2 d | Låg-medel |
| Premium-screen fixar (60%-stämpel, EN-bug, disclosure, re-order, modal-throttle) | 1 d | Låg |
| Test-image-infra för Match/Disambig | 1 d | Låg |
| **Audio-ID (BirdNET-Lite)** | **5-7 d** | **HÖG — wildcard** |
| PDF-export | 2 d | Låg-medel |
| Säsongs-statistik (`LockedStatsPreview` → riktig) | 1-2 d | Låg |
| 10 fält-märken (content + ikoner + evaluator) | 2-3 d | Medel (kräver illustration) |
| Carry-overs (Pages + TalkBack) | 0.5 d | Låg |
| **Summa** | **15-21 d** | Tight men görbart |

**Audio-risk:** Om audio-ID går över 7 dagar är 1 juni i fara. Decision-point efter dag 7 i Plan 6b: om audio är &lt;50% klar → flytta launch till 2026-06-08.

När de fem besluten är låsta kan Plan 6b skrivas på 30 min via `superpowers:brainstorming` + `superpowers:writing-plans`.

---

## Vad som INTE har ändrats

- Plan 2b (content backfill 273/700) — kör parallellt
- Field Journal-estetiken — låst, sälj-narrativ
- SQLDelight + KMP + Compose Multiplatform — låst
- AIY Birds V1 som klassifierare — låst för v1.0 (Phase 2 fine-tune är post-launch)
- Mossbädd → Field Journal-migrationen — slutförd i Plan 7c

---

## Referens-filer

| Fil | Källa |
|---|---|
| `01-competitor-analysis.md` | Konkurrentlandskap + vita fläckar |
| `02-aso-play-store-optimization.md` | Title, screenshots, copy |
| `03-product-differentiation.md` | USP-rangordning + personas |
| `04-monetization-strategy.md` | Pricing, billing, AB, regulation |
| `05-marketing-launch-playbook.md` | Pre-launch → 90-dagars roadmap |
| `README.md` | Index + TL;DR per fil |
| `00-launch-roadmap.md` | **Den här filen.** Binder ihop allt strategiskt. |

| Memory-fil | Hänvisar till |
|---|---|
| `project_release_v1.md` | Release-status + carry-overs |
| `project_plan_scheduling.md` | Plan 6b-scope + ML-deferral-beslut |
| `project_play_store_launch_research.md` | (Skapas nu) — pointer till research-katalogen |
