# iOS-release-checklista — vägen till App Store

> **Levande dokument** (skapad 2026-08-16). Bocka av + committa vid varje passerad grind; den refereras från `.claude/commands/goal.md` och CLAUDE.md. Ägare: 🧑 = Albin (dina händer/beslut), 🤖 = Claude-arbetspass på Mac:en.
>
> **Läge just nu: all appkod för kärnupplevelsen OCH paritets-sveppet är klar.** i0–i4 (miljö, uppslagsverk+dagbok, foto-ID, live-kamera, ljud-ID, karta/notiser/PDF-paritet) är kodklara + fullt review:ade och pushade. Det som skiljer oss från en potentiell release är: dina verify-grindar, två återstående faser (i5 StoreKit, i6 release-mekanik) och Apples ledtider.

## Grov distansmätare

| Etapp | Status | Estimat kvar |
|---|---|---|
| Appkärna (i0–i3) | ✅ kodklar + review:ad | 0 |
| Verify-grindar (sim + iPhone) | ⬜ | ~1–2 h av din tid + iPhone-tillgång |
| i4 Paritets-svep (karta/notiser/PDF) | ✅ kodklar + review:ad | 0 |
| i5 StoreKit 2 | ⬜ | ~1–2 pass + Console-arbete |
| i6 TestFlight → App Store | ⬜ | ~1–2 pass + **1–2 v Apple-review** |
| **Totalt till potentiell release** | | **~2–4 Mac-arbetspass + externa ledtider** (enrollment-dagar + review-veckor) |

Med i3-tempot (en fas ≈ en dag) är **TestFlight realistiskt inom ett par veckors sessioner**; publik App Store-release därefter styrs mest av Apples review och enrollment.

---

## 🧑 DET HÄR BEHÖVER DU GÖRA (kritiska vägen)

### Nu direkt (ingen iPhone krävs)

- [ ] **1. Starta Apple Developer-enrollment** (developer.apple.com, 99 USD/år, personligt konto räcker). **Tar ofta några dagar — detta är den enda punkten som kan blockera i5/i6 på kalendertid, starta den FÖRST.** (Free Apple ID räcker för device-install fram tills dess.)
- [ ] **2. Sim-check-paketet (~15 min, allt uppdukat i iPhone 17-simulatorn på Mac:en):**
  - [ ] i2c: Scan-fliken → permission-panel → Allow → lokaliserad usage-sträng → preview + "searching…" utan krasch; ta-foto-knappen inert
  - [ ] i2c-regressionen: scan → back → scan igen → back → galleri-foto-ID (får INTE ge "Analyzer failed")
  - [ ] i2b: galleri→ID med den förladdade **48,8 MP-HEIC:en** + snabb dubbeltryck på rotera i crop-vyn
  - [ ] i3: Lyssna-fliken → mic-permission (SV/EN-sträng) → inspelnings-UI (chip/nedräkning/waveform) → **ärligt felstate** vid analys (sim kan aldrig köra riktig BirdNET — det är korrekt beteende; debug-bygge visar DEMO-banner) → Back mitt i släcker mic-indikatorn
  - [ ] **i4 (kräver MapTiler-nyckeln i `iosApp/Local.xcconfig`, se punkt 10):**
    - [ ] Karta: duotone-tiles renderar (visuell paritet mot Android — nu meningsfull tack vare gamma-fixen), vaxsigill-pin syns m. skugga, pin-tap → fynddetalj, `xcrun simctl location <UDID> set <lat>,<lon>` för pin-placering
    - [ ] Notiser: pre-prompt → systemdialog → devTriggers i Inställningar (**tappa EN i taget — de delar notis-id**) → notis efter ~2 s → tap → deep link navigerar
    - [ ] PDF: exportera från Arkiv → **share-sheeten VISAS** (slutreview-fixad väg) → öppna PDF:en → granska alla sidor visuellt (typsnitt DM Serif/Caveat + baseline-matten är analytiskt verifierad men aldrig pixelverifierad; titel/stats/arter/ev. märken/kolofon)
    - [ ] Inställningar: notis-toggles + "notiser av"-hjälplinjen
- [x] **3. Skaffa fram en fysisk iPhone** — ✅ PÅ VÄG: företagstelefon (senaste iPhone) levereras **~2026-08-26**. Plan: utvecklingen pausar INTE — i4 (och ev. i5-koden) byggs sim-verifierbart under väntetiden, och device-verifyn körs som EN samlad session när telefonen landar (alla grindar + i4-punkterna i ett svep). Kom ihåg första gången: slå på **Developer Mode** (Inställningar → Integritet & säkerhet) + lita på Mac:en; free Apple ID + Xcode räcker för install tills enrollment är klar.

### Vid iPhone-tillfället (device-verify — grinden som gör anspråken sanna)

- [ ] **4. i1: install + browse** (uppslagsverk, artbilder, prefs överlever omstart)
- [ ] **5. i2c Milestone 1 (live-kamera):** riktiga frames end-to-end, FOV-zoom 1×/5×/10×, freeze→match→spara, ta-foto→crop→analyze, permission-rundor, background/resume, lågljus, snabb scan-ut/in, portrait-orientering
- [ ] **6. i2b foto-ID sammansatta vägen:** galleri→ID på riktiga foton (48 MP HEIC igen på device)
- [ ] **7. i3 ljud-ID (checklista i CLAUDE.md-posten):** riktig fågel → Match/Disambig (jämför `docs/superpowers/research/2026-08-16-i3-audio-reference-facit.md`), ambient 60 s → NO MATCH, **BT-öronsnäckor ansluter mitt i → RecordingFailed (inte frusen timer)**, samtal → felstate → retry efter samtalet, manuell ren stopp ger aldrig spurious fel, 60 s-cap, chip-kadens, bakgrundning
- [ ] **8. i4 (karta/notiser/PDF):** riktig GPS-plats → pin landar rätt på kartan; en riktig kalender-avfyrad notis (vänta in en verklig fire-tid, inte devTrigger) → tap → deep link navigerar till rätt skärm; PDF-export → dela till Filer/AirDrop → öppna där; musik-resume (spela musik → spela in ett läte → stoppa inspelningen → musiken återupptas, `NotifyOthersOnDeactivation`); reduced-accuracy-plats-grant (grov pin ~1–5 km — bedöm om det är acceptabelt för fynd-kartan); tile-tint-scrollprestanda (panorera/zooma kartan, leta efter hack)
- [ ] **9. Rapportera utfallen** i en session → CLAUDE.md-synk; rött ⇒ fixrunda före i5-start är billigast

### Senare beslut/handgrepp (vid respektive fas)

- [ ] **10. (i4) MapTiler-nyckeln för iOS-bygget:** `cp iosApp/Local.xcconfig.sample iosApp/Local.xcconfig` (gitignorerad fil) → fyll i `MAPTILER_API_KEY = <nyckel>` (samma nyckel/konto som Androids `gradle.properties`). Krävs innan punkt 2:s i4-sim-check kan köras (utan nyckel: tomma/403-tiles, ingen krasch — men inget att visuellt bedöma).
- [ ] **11. (i5) App Store Connect:** app-entry, in-app-produkter (spegla Plays YEARLY/LIFETIME + priser), StoreKit-verify på device — beslut från 2026-07-07 står: **paywall aktiv dag 1 på iOS**
- [ ] **12. (i6) Godkänn listing-texterna** (SV/EN återbrukas från Play), App Store-screenshots (nytt bildpaket — samma behov som follow-up #9 för Play), privacy labels (lätta givet privacy-löftet)
- [ ] **13. (i6) Trycka på "Submit for review"** + svara på ev. review-frågor

### Parallellt Android-spår (påverkar inte iOS, men ligger på dig)

- [ ] **14. Windows-passet:** vC127 emulator+Galaxy-verify → Play-upload av vC127 (fallback vC126) — beslutsträd i `docs/play-store/console-paste-v1.2.1-v1.2.2.md`

---

## 🤖 Claude-arbetspassen (körs när dina grindar ovan är gröna)

- [x] **i4 — Paritets-svep** — ✅ KODKLAR + FULLT REVIEW:AD (2026-08-17): karta på iOS (**MapKit + `MKTileOverlay`** via en tunn Swift-tile-bro `BirdyTileOverlay.swift` → Kotlin `IosTileFetcher` — INTE MapLibre; K/N kan inte subklassa `MKTileOverlay` (bevisat via klib-dump, båda tile-load-metoderna `final`), samma MapTiler-tiles + duotone-matris som Android; MapLibre kvarstår enbart som dokumenterad fallback), veckonotiser (`UNUserNotificationCenter` + `UNCalendarNotificationTrigger`, foreground-omschemaläggning), PDF-export-actual (`UIGraphicsPDFRenderer`), rest-städ (`NotifyOthersOnDeactivation`-polish klar; AAC-uppspelningsfil-beslutet står fast som v1-avvägning; i3:s deferred-minor-triage är INTE gjord här, re-deferrad — se CLAUDE.md). 14 tasks + NSLog-mikrofix + slutreview-fixvåg, alla Approved. **Kvar:** punkt 2:s i4-sim-check + punkt 8:s device-punkter.
- [ ] **i5 — StoreKit 2** (~1–2 pass): `PremiumBillingClient`-actual (köp/restore/entitlement), kräver punkt 1+11 ovan.
- [ ] **i6 — Release-mekanik** (~1–2 pass): appikoner i iOS-storlekar, TestFlight-bygge, listing-paket, privacy labels. **Bokförd kontrollpunkt vid FÖRSTA TestFlight-upload:** verifiera att `TensorFlowLiteSelectTfOps.bundle` (privacy-manifest utan egen Info.plist) passerar App Store-valideringen — flaggad i i3-slutreviewen.
- [ ] **Löpande:** grind-utfall → ev. fixrundor → CLAUDE.md-synk efter varje pass.

## Kända medvetna avvägningar (så de inte omprövas av misstag)

- **Flex-artefakten (+~88 MB shippad binär, uppmätt strippad Release-delta):** under 150 MB-gaten; **väg B** (selektiv bazel-build, endast RFFT — mindre + ger sim-slice) är dokumenterad fallback i `docs/superpowers/research/2026-08-15-ios-i3-flex-select-ops-research.md` om storleken skaver inför i6.
- **Simulatorn kör aldrig riktig audio-inferens** (ingen sim-slice existerar) — felstate/DEMO där är design, inte bugg.
- **Ingen uppspelningsfil för ljudfynd på iOS i v1** (Opus kan ändå inte spelas nativt; samma degrade som Android API<29). AAC + player = ev. follow-up.
- **iOS-launchen = Android v1.2-paritet.** Asien-content (andra halvan av "v2" i north star) är ett separat, opåbörjat spår och blockerar INTE App Store-releasen.
