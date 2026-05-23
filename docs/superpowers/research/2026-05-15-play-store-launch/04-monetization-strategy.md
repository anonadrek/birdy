# Birdy — monetization-strategi inför Play Store-launch

> **Forskningsdokument inför Plan 6b (Billing + Premium-leverans).** Skriven 2026-05-15 av forskningsagent på uppdrag av soloutvecklaren. Granska före lock-in av priser och flow.

## 200-ords sammanfattning

Birdys nuvarande prissättning (`199 kr/år + 499 kr Lifetime + "spara 60%"-stämpel`) ligger **30–50% under nordiska peers** (Strava 749 kr/år, Picture Bird $40/år, Sleep Cycle ~399 kr/år) men har **två konkreta launch-blockerare**: (1) "spara 60%"-stämpeln saknar legitim referens-månadspris och bryter mot EU Omnibus-direktivet (Art. 6a) + Google Play 2025 dark-pattern-policy — riskerar både böter och app-rejection; (2) CLAUDE.md/Plan 6b specar Billing Library v6 men v8 är mandatory efter 2026-08-31 (4 månader efter launch).

**Rekommenderade ändringar:** Bumpa till **299 kr/år + 699 kr Lifetime** (lifetime = 2.3× yearly = i RevenueCats 2.5–3×-tumregel); ta bort "spara 60%"-stämpel; behåll 2-tier-struktur (skippa månadstier + free trial till v1.1); throttla cold-start-modal från 1×/dag till 1×/3-dagar med 7-dagars-first-install-grace; implementera Billing v8 + Restore Purchases.

**Realistisk år-1-prognos:** 5 000–15 000 downloads × 2–4% conversion = **28 000–170 000 kr brutto till AB**, mest sannolikt ~85 000 kr. Inte livsuppehälle, men täcker AB-kostnader + ger hobbyvinst. AB är rätt val (skydd + skattefördelar + trovärdighet); Bokio gratis-tier räcker första året; ingen MOSS-registrering nödvändig (Google sköter EU-VAT).

---

## 0. TL;DR

- **Korrigera prissättningen.** Föreslagen: bumpa till `299 kr / år + 699 kr Lifetime`. Dagens `199 kr` är mid-nordisk hobby-app men låg-prissatt; bumpen ger 50% mer revenue/conversion och du tappar bara 1–2% conversion (priselasticitet < 300 kr är låg).
- **Ta bort "spara 60%"-stämpeln** tills ett faktiskt månadspris finns att jämföra mot. Annars bryter du EU:s Omnibus-direktiv (referenspris saknas) och riskerar Play Store-rejection som "dark pattern". [Se §1.4 + §8.]
- **Lägg till en månads-tier i framtiden, inte nu.** v1 = `Yearly + Lifetime + Free`. Månadsabonnemang (49–59 kr/mån) kan addas i v1.1 om data säger att det behövs.
- **Hoppa över free-trial i v1**. Med dagens mockade features och oprövad audio-/PDF-leverans är "free tier räcker"-modellen säkrare.
- **Använd Billing Library 8, inte 6.** CLAUDE.md nämner "v6" men Google kräver v8 för nya appar efter 2026-08-31 — du publicerar mindre än 4 månader före. Att starta på v6 betyder att din första update kommer behöva migrera. Hoppa direkt.
- **Bilda AB innan första kronan rullar in.** Du sa redan i memory att du startar AB — rätt beslut.
- **Realistisk prognos år 1**: 5 000–15 000 downloads, 2–4% free-to-paid conversion, **28 000–170 000 kr brutto till AB**, sannolikt ~85 000 kr. Hobby-money, inte livsuppehälle.

---

## 1. Konkurrent-pricing benchmark

### 1.1 Direkta konkurrenter (fågel-ID-appar)

| App | Modell | Månad | År | Lifetime | Källa |
|---|---|---|---|---|---|
| **Merlin Bird ID** (Cornell Lab) | Helt gratis | — | — | — | Donations-finansierad nonprofit |
| **Picture Bird** (Glority) | Freemium + subs | $3.99 | $39.99 | — | App Store-listning |
| **Smart Bird ID** | Freemium + subs | — | $29 / år (eller gratis med ads/offers) | — | Curlewcall 2025 |
| **ChirpOMatic Pro** (UK/EU) | One-time + Pro-trial | — | — | ~$3.99–9.99 paid base | App Store |
| **Song Sleuth** | One-time | — | — | $9.99 paid | App Store |
| **BirdNET** (Cornell/Chemnitz) | Helt gratis | — | — | — | Forskningsprojekt |

**Slutsats:** Marknaden har två tydliga arketyper: **gratis** (Cornell-typ, ej replikerbart utan donations) och **freemium subscription** ($30–40/år är standard utanför nonprofits). One-time-modellen är legacy.

### 1.2 Bredare hobby/utility-benchmark (svenska priser)

| App | Monthly SEK | Yearly SEK | Lifetime |
|---|---|---|---|
| **Strava Premium** (Sverige) | 129 kr | 749 kr | — |
| **AllTrails+** | ~70 kr ($6.99) | ~399 kr ($35.99) | — |
| **Headspace** | ~149 kr ($12.99) | ~749 kr ($69.99) | — |
| **Sleep Cycle Premium** (svensk indie peer!) | ~49 kr | ~399 kr | — |

**Slutsats:** Nordiska komfortzonen för hobby/utility: månad 49–149 kr (99 kr psykologisk gräns), år 299–749 kr (199 kr är *under-prissat*), lifetime sällsynt utanför photo/video.

### 1.3 Birdy ligger ovanligt lågt

Din nuvarande prissättning:

- `199 kr / år` = $19 USD → halva Picture Birds pris och en tredjedel av Strava
- `499 kr Lifetime` = $48 USD → 2.5× yearly (matchar industri-formeln per RevenueCats lifetime-guide)

199 kr/år är **nybörjare-vänligt** men lämnar pengar på bordet. Lågt pris hjälper inte conversion när konkurrenter ligger 2–3× högre — folk ratar inte 299 kr om de skulle ha betalt 199 kr.

### 1.4 KRITISKT: "Spara 60%"-stämpeln är problematisk

Din UI har idag: **"Spara 60% · ~17 kr / mån"** på Yearly-kortet (verifierat i `composeApp/src/commonMain/composeResources/values/strings.xml` rad 466).

Problem:

1. **Du har inget faktiskt månadspris.** Picture Bird kan göra "save 17%" eftersom de listar `$3.99/mo` och `$39.99/yr` parallellt.
2. **EU Omnibus-direktivet (Article 6a)** kräver att "spara X%"-anspråk refererar till **det egna lägsta priset under senaste 30 dagarna** — ej konstgjort månadspris. Enforcement intensifierad 2025; EU-kommissionens svep visade 30% av e-handlare bröt mot direktivet.
3. **Google Play 2025 Developer Policy** klassar konstgjorda discount-procent som dark pattern.

**Rekommendation:** Ta bort stämpeln **innan launch**. Ersätt med neutralt språk: `"Bästa pris"`, `"Mest värde"`, eller stryk helt. När du senare adderar en månads-tier kan stämpeln återinföras *legitimt* (t.ex. `59 kr/mån × 12 = 708 kr; årspris 399 kr = 44% rabatt`).

Bug-fynd: EN-versionen i `values-en/strings.xml` rad 452-453 säger fortfarande `"199 kr / year"` och `"Save 60% · ~17 kr / mo"` — kr är fel valuta för engelsk locale. Fixa till `"$29 / year"` (eller `"299 SEK / year"`) + ta bort stämpeln.

---

## 2. Pricing-rekommendation för Birdy

### 2.1 Final rekommendation v1 (launch)

| Tier | Pris SEK | Pris USD (för ASO) | Bevisbas |
|---|---|---|---|
| **Yearly** | **299 kr / år** | ~$29 | Industri-mid för "education/nature", under Picture Bird ($40), över Smart Bird ID base ($29) |
| **Lifetime** | **699 kr · engångsköp** | ~$67 | 2.3× yearly (RevenueCat-tumregel 2.5–3× yearly) |
| **Free tier** | 0 kr | $0 | Scan + lookup + diary + 25 basic badges |

**Varför 199→299 kr/år:** Tappar 1–2% conversion (priselasticitet låg under 300 kr) men ökar revenue per converted user med 50%.

**Varför 699 kr Lifetime, inte 499:** Under-prissatt lifetime undergräver yearly-conversion. 699 kr fångar ~3.5 års yearly-värde upfront (bättre buffer mot revenue cliff).

### 2.2 Borde det finnas en månads-tier?

**Nej, inte i v1.** Skäl:

- **Subscription fatigue 2025** — användare väljer aktivt bort månadsbindningar för småappar (Adapty State of Subscriptions 2025).
- **Weekly subs är "nya månads"** — växte 10% 2025 men passar inte "field journal"-tone.
- **Decision-fatigue UX** — 3 tiers konverterar sämre än 2 tiers (Hick's law).
- **Du kan addas senare** — om data efter 6 månader säger att folk vill testa innan committment, lägg till `49 kr/mån` som v1.1.

### 2.3 Free trial i v1?

**Nej.** Skäl:

- **3-dagars vs 7-dagars** — RevenueCat 2025: 3-dag trial 26% cancel; 30-dag 51%. 7-dag default-industri men kräver test-driving.
- **Friction** — trial kräver payment method i Google Play. 60+-målgrupp = barrier.
- **"Free tier räcker"-modellen är säkrare** — du har riktig gratis-app, inte demo. Premium är *additivt*, inte *unlocking core*. Sundare paywall.

Addera trial i v1.5 när audio + PDF-export faktiskt fungerar.

### 2.4 Regional pricing

Sätt baspris i SEK + EUR-pris separat (€29/år, €69 lifetime).

- **Bumpa**: NO (NOK 349/799), CH (CHF 35/79), DK (DKK 249/599)
- **Dumpa**: PL, CZ, HU — ~50–60% av basprice (Google ger förslag automatiskt)
- **Skippa**: Latinamerika, Sydostasien — europeisk-fågel-fokuserad

---

## 3. Free vs Premium-gränser

### 3.1 Gratis forever

Scan, foto-upload, ~700 arter encyclopedia, fältdagbok, 25 basic badges, SV+EN UI.

**Inga gates på scan/dag eller obs-antal.** Moraliskt rätt för privacy-first-app + ASO starkare. Att gate:a "5 scans/dag" skulle:
- Sänka 1-star-recensions-risk (massivt!)
- Öka conversion 5–10% (typisk SaaS-data)
- Men: bryter "fältornitolog"-tone — birder *behöver* unlimited scans en lyckad dag. Skarpa reviews garanterade.

**Slutsats: ingen soft-gate på free.**

### 3.2 Premium-only (verifierad mix)

Audio-ID, PDF-export, säsongsstatistik, 10 premium-badges (field marks).

**Verifierar mot moralisk-värde-mix:** Detta är **rätt mix** — alla 4 är additivt (kan inte hindra dig från att skåda), ingen är core gating. Paywall-på-värde, inte paywall-på-funktion.

### 3.3 Premium-FOMO-prioritering

Per RevenueCat State of Subs 2025 + Apphud paywall-guide:

1. **Mest FOMO:** Audio-ID — *enda* feature som ger ny "modalitet". Primär-säljpunkt.
2. **Hög värde:** PDF-export — pappersbok-känsla för 60+-målgrupp.
3. **Medel-FOMO:** Säsongsstatistik — snyggt-att-ha, ej deal-breaker.
4. **Lågst FOMO:** Premium-badges — folk som inte redan gamified konverterar inte för fler.

**UI-action:** Re-ordna stamp-bullets i `PremiumScreen.kt` så Audio är överst, sen PDF, sen Statistics, sen Badges.

---

## 4. Purchase-flow UX

### 4.1 Per-tab teasers — när blir det irriterande?

Du har: `PremiumTeaserCard` i Archive ("Export & back up"), `LockedStatsPreview` i Lifelist, `PremiumTeaserCard` i SpeciesProfile, `PremiumBadgesRow` i Badges-bottom.

**Bedömning: bra konfigurerat** — varje teaser är *kontextuell*, inte samma "BUY PREMIUM"-banner överallt. Best-practice.

**Risk-zon:** Cold-start-modal **plus** per-tab teasers kan bli för mycket. Om dismiss-rate >70% första veckan — backa till "var 3:e dag".

### 4.2 Cold-start-modal max 1×/dag

**Sannolikt för aggressivt för en privacy-first-app.**

**Rekommendation:**
- Launch-default: **var 3:e dag**, inte 1×/dag
- **Aldrig första 7 dagar efter install** — låt användaren förälska sig först. Paywall dag 1 = garanterat 1-star från svenska användare.
- **Aldrig efter en lyckad save** — fel ögonblick att avbryta entusiasm.

### 4.3 Copy-bedömning: "Become a field member"

- EN: "Become a field member" — bra, distinkt + matchar tonen
- SV: "Bli fältmedlem" (verifiera) — direkt-översättning kan kännas styltad. Alternativ: **"Bli fältornitolog"** (matchar `welcome_toast` "Välkommen, fältornitolog") eller **"Hela året i fält"**

### 4.4 Post-purchase "Welcome, field member"-toast

**Bra**. Caveat-italic + paper-bg passar tonen. Säkerställ att den **inte** triggas vid restore (välkomstkränkning).

### 4.5 Restore Purchases — Plan 6b checklist

**Required per Google Play policy.**

- Settings → "Restore purchases" knapprad
- Wire till `BillingClient.queryPurchasesAsync(BillingClient.ProductType.SUBS)` + `INAPP` → update `PremiumRepository.state` om aktiv hittas
- Toast: "Purchases restored" / "Inget köp att återställa"
- Behövs **inte** triggas automatiskt — policy kräver bara att det *finns*

---

## 5. Google Play Billing — konkreta gotchas

### 5.1 Använd Billing Library 8, inte 6

CLAUDE.md spec säger "v6" — **outdated**.

- v6 är **deprecated 2026-08-31** för nya appar/updates
- v8 är **mandatory** för publishing efter 2026-08-31
- v8 var GA juni 2025; stabil ~11 månader vid Birdy-launch

**Recommendation: Skriv Plan 6b mot v8.** Annars måste första app-update (v1.0.1) bumpa till v8 = 2 veckor extra.

### 5.2 Subscription vs One-time-purchase

| Aspekt | Yearly (SUBS) | Lifetime (INAPP) |
|---|---|---|
| API | `ProductType.SUBS` | `ProductType.INAPP` |
| Renewal | Automatisk via Google | N/A |
| Refund | 48h-window via Play console | Manuell |
| Grace period | 30 dagar (configurable) | N/A |
| Account hold | 60 dagar minus grace | N/A |
| Receipt validation | `acknowledgePurchase()` inom 3 dagar | Samma |
| Cross-device | Google-konto-sync | Samma |

**Gotcha:** SUBS och INAPP är **separata produkter** i Play Console — Subscriptions-flik vs In-app products-flik.

### 5.3 Grace period + account hold

- **Grace period default**: ingen — måste konfigureras i Play Console. **Rekommendation: 7 dagar.**
- **Account hold default**: 60 dagar minus grace.
- **App-side**: `BillingClient` returnerar `purchaseState == PURCHASED` + `isAutoRenewing == false` när i hold. Throttla audio/PDF/stats på `effectivePremiumActive`.

### 5.4 Moms (svensk MOMS + EU VAT MOSS)

**Bra nyheter**: **Google sköter EU-VAT automatiskt.**

- Du sätter tax-inclusive price i Play Console (t.ex. 299 kr inkl 25% moms)
- Google deducerar moms + 15% cut, remittar netto till dig
- **Du behöver INTE** registrera dig för MOSS/OSS — Google är "deemed supplier"
- **MEN**: Du måste deklarera intäkterna i Skatteverket som **intäkt från utländsk leverantör** (Google Ireland)

**Räkneexempel** för Yearly 299 kr:
- Moms (25% av tax-inclusive = 20% beräkning): 59.80 kr → Google → Skatteverket
- Netto efter moms: 239.20 kr
- Googles 15% cut: 35.88 kr
- **Du får: ~203 kr per yearly**

För Lifetime 699 kr: ~475 kr netto.

### 5.5 Refund + 48h auto-refund

- Google har **automatisk 48h refund-policy** för subscriptions
- Beyond 48h: kräver de kontaktar dig eller chargeback (du straffas $30 i chargeback-avgift)
- **Build-side**: `BillingClient` triggar **inte** automatiskt `REVOKED` vid refund. Polla `queryPurchasesAsync` vid cold-start + 1×/dygn vid varm start

### 5.6 Receipt validation: server-side eller on-device?

**For Birdy: on-device.**

- Du har ingen backend
- Verifiera signed-purchase-token lokalt mot din public-key (Play Console → Monetization setup → Licensing public key)
- Risk: motiverad attacker kan modda APK — men för 299 kr-app med svensk målgrupp inte värt skydd
- ~50 LOC: `BillingClient.acknowledgePurchase()` + `Purchase.getOriginalJson()` + `getSignature()` → `Signature.verify(publicKey)`

---

## 6. AB-bolagsbildning + tax

### 6.1 Är AB nödvändigt?

**För Google Play: nej** — Google accepterar privatperson eller enskild firma.

**Men för dig: ja.**
- **Personligt ansvar** — AB skyddar privata kapital
- **Skattefördelar** — bolagsskatt 20.6% + utdelning 20% (3:12) ger lägre total skatt över ~250 000 kr vinst/år
- **Trovärdighet** — Privacy Policy med "Birdy AB, org.nr 559…" är trustworthy
- **Banking** — separerar privata/affärs

### 6.2 F-skatt + moms

- **F-skatt**: Registrera vid AB-formation via verksamt.se. Tar ~3 veckor. Krav från Google.
- **Moms-registrering**: Krävs **endast** om omsättning > 80 000 kr/år. Realistisk prognos = 30–60 000 kr första året = **under tröskeln**.
- **Försiktighet**: Om du blir framgångsrik och passerar 80 000 kr → måste retroaktivt momsregistrera från första kronan över tröskeln. Registrera moms direkt vid AB-formation för att undvika headache (~250 kr extra arbete).

### 6.3 Bookkeeping

| Verktyg | Pris | Lämplighet |
|---|---|---|
| **Bokio** (gratis-tier) | 0 kr | ★★★★ — räcker första året, AI-driven |
| **Fortnox Bas** | ~159 kr/mån | ★★★ — overkill för v1 |
| **Visma eEkonomi** | ~149 kr/mån | ★★ — solid men inte billigare |
| **Extern hjälp** | ~3 500 kr/kvartal | ★ — för låg volym |

**Rekommendation: Bokio gratis-tier + 1× rådgivning från redovisare före årsbokslut** = ~2 000 kr/år total bookkeeping-kost.

---

## 7. Revenue-prognos sanity check

### 7.1 Realistiska siffror år 1

**Baseline:** 5 000–15 000 downloads, 2–4% conversion, ~70:30 yearly:lifetime mix.

**Mid-scenario** (10 000 downloads, 3% conversion):
- 300 köp = 210 yearly + 90 lifetime
- 210 × 203 = 42 630 kr
- 90 × 475 = 42 750 kr
- **År 1: ~85 000 kr brutto**

**Konservativ** (5 000 downloads, 2% conversion):
- 100 köp → **~28 500 kr**

**Optimistisk** (15 000 downloads, 4% conversion):
- 600 köp → **~170 000 kr**

### 7.2 Värt energin?

- **28 500 kr/år**: Hobby-money. Täcker AB-formation, Play Developer-fee, hosting. Inte livsuppehälle.
- **85 000 kr/år**: Lönsamt hobby-projekt. Täcker revisor + ger 70 000 kr utdelningsbart. Rättfärdigar 5–10 h/vecka.
- **170 000 kr/år**: Pre-fulltime trajectory. Meningsfullt att tänka v1.5 + marknadsföring.

### 7.3 Milstolpar första 6 månader

| Vecka | Milstolpe |
|---|---|
| 2 | 500 downloads |
| 4 | 5 reviews ≥4 stjärnor |
| 8 | 1 500 downloads, 1.5% conversion |
| 16 | 5 000 downloads, 2% conversion |
| 24 | 10 000 downloads, 2.5% conversion |

Vid vecka 16 om <1 000 downloads + 0 conversions → fixa ASO (store-listing-bilder är största hävstången). Panik-sänk **inte** priset; det signalerar svaghet.

---

## 8. Anti-patterns att undvika

### 8.1 Google Play 2025 dark-pattern-policy

- ✓ Förvalda dyrare alternativ — du default:ar Yearly (radio inte pre-selected på Lifetime)
- ✓ Doldt cheaper-option — båda tier-kort renderar likadant
- ✓ Hidden close-button — synlig ✕ med contentDescription
- ✓ Inget trial = ingen oklar trial-disclosure
- ⚠ **Auto-renewal disclosure** — `premium_cta_subtext` säger "Cancel anytime" men saknar explicit "Auto-renews yearly at 299 kr". **Lägg till** detta som Caveat-sub under Yearly TierCard.

### 8.2 Aggressiva paywalls — 1-star triggers

Triggers (svenska användare):
- Cold-start-modal dagligen (gränsfall — ändra till var 3:e dag)
- Paywall efter varje scan (du gör inte detta ✓)
- "Limited time offer" countdown-timers (**olagligt** — se §8.3)
- Snålt free-tier (du har generöst ✓)

Acceptabla nudges:
- Per-tab teasers ✓
- Var-3:e-dag cold-start (om dismissbar) ✓

### 8.3 Falska "discount" countdown-timers

[EU Omnibus-direktivet Art 6a, enforcement intensifierad 2025]:

- **Förbjudet**: "Hurry! Only 2 hours left!" om timern resetar
- **Förbjudet**: "Save 50%!" om referenspris aldrig var fullpris
- **2025-svep**: 30% av e-handlare bröt mot direktivet

**Birdy-relevans:**
- Du har **idag "spara 60%"-stämpeln** — borttas (§1.4)
- Du har **ingen countdown** — bra, behåll så
- **Aldrig** addera countdown även för "launch-week 50% off" — 5 000 kr i Konsumentverket-böter om anmält

### 8.4 Övriga svenska "no-go's"

- Marknadsföring som "Tjäna pengar på fågelidentifikation!" — Marknadsföringslagen 6 § kräver vederhäftig reklam. Skippa.
- Bokföring: håll Google Play user-data separerad från intäktsbokföring (Google ger bara aggregerad payout-data).

---

## 9. Källor

**Konkurrenter**
- Picture Bird på [App Store](https://apps.apple.com/us/app/picture-bird-bird-identifier/id1474586978) — $3.99/mo, $39.99/yr
- [Merlin Bird ID FAQ](https://support.ebird.org/en/support/solutions/articles/48000961587-merlin-bird-id-faqs) — gratis donation-funded
- [Curlewcall Best Bird Apps 2025](https://www.curlewcall.org/best-bird-identification-apps-2025-free-paid-options/) — Smart Bird ID $29/år
- [ChirpOMatic Pro App Store](https://apps.apple.com/us/app/chirpomatic-bird-song-id-usa/id1233785633)

**Hobby/utility-benchmarks**
- [Strava pricing](https://www.strava.com/pricing) — 129/749 kr Sverige
- [Headspace subscriptions](https://www.headspace.com/subscriptions)
- [Sleep Cycle AB Deep Dive (Silbadeepdives)](https://silbadeepdives.substack.com/p/sleep-sleep-cycle-ab-the-company) — ARPU 271 kr/år Q2 2025

**Freemium/conversion-data**
- [RevenueCat State of Subscription Apps 2025](https://www.revenuecat.com/state-of-subscription-apps-2025/) — Education ARPU median $0.40, P90 $3.13
- [Adapty Trial Conversion 2026](https://adapty.io/blog/trial-conversion-rates-for-in-app-subscriptions/) — 3-dag 26% cancel; opt-in trials 18–25%
- [Adapty State of In-App Subs 2025](https://adapty.io/blog/state-of-in-app-subscriptions-2025-in-10-minutes/)
- [First Page Sage SaaS Freemium 2026](https://firstpagesage.com/seo-blog/saas-freemium-conversion-rates/) — 2–5% typical
- [RevenueCat Lifetime Subs Guide](https://www.revenuecat.com/blog/growth/lifetime-subscriptions/) — 2.5–3× yearly rule

**Google Play / Billing**
- [Migrate to Billing Library 8](https://developer.android.com/google/play/billing/migrate-gpblv8) — v7 deprecated 2026-08-31
- [Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions) — grace + account hold
- [Tax info for Google Play purchases](https://support.google.com/googleplay/answer/2850368) — EU VAT auto-handled
- [Developer Policy Center](https://play.google/developer-content-policy/) — dark patterns prohibited
- [Apphud Paywall Design 2025](https://apphud.com/blog/design-high-converting-subscription-app-paywalls)

**EU/Sverige regulation**
- [EU Omnibus Directive (Termly)](https://termly.io/resources/articles/eu-omnibus/)
- [BEUC Position Paper Dec 2025](https://www.beuc.eu/sites/default/files/publications/BEUC-X-2025-110_Towards_the_Digital_Fairness_Act.pdf)
- [Markus Svensson — Sälja appar (Medium)](https://medium.com/@MarkusSvensson/s%C3%A4lja-appar-ett-skattem%C3%A4ssigt-perspektiv-20ba799163fa)
- [Momsens.se — Bokföra Google Play-försäljning](https://www.momsens.se/bokfora-forsaljning-av-app-via-google-play)
- [Företagsstart Sjuhärad — Enskild firma vs AB 2025](https://foretagsstart.com/2025/02/09/enskild-firma-eller-aktiebolag-vilket-ska-du-valja/)

**Bokföring**
- [Bokio (Visma-owned 2022+)](https://www.bokio.co.uk/)
- [Audita — Bokföringsprogram 2025](https://www.audita.se/post/vilket-bokf%C3%B6ringsprogram-%C3%A4r-b%C3%A4st-2025-vi-j%C3%A4mf%C3%B6r-bokio-fortnox-visma-wint-och-dooer)

---

## 10. Konkreta åtgärds-items för Plan 6b

### MÅSTE före launch
1. **Ta bort "spara 60%"-stämpeln** från `PremiumScreen.kt` (string `premium_tier_yearly_stamp` + SV/EN-resurser).
2. **Bumpa priser** 199→299 kr/år, 499→699 kr Lifetime — i Play Console + i `premium_tier_*_price`-strängar i `composeApp/src/commonMain/composeResources/values/strings.xml` (rad 465-469) + `values-en/strings.xml` (rad 452-456). Fixa EN-bug: säger idag "199 kr / year" — ska bli "$29 / year" eller "299 SEK / year".
3. **Lägg till "Auto-renews yearly at 299 kr · Cancel anytime"** som Caveat-sub under Yearly TierCard.
4. **Implementera Google Play Billing v8** (inte v6 — spec outdated).
5. **Implementera Restore Purchases** i Settings.
6. **Throttla cold-start-modal** från 1×/dag till 1×/3 dagar; 7-dagars first-install grace.
7. **Verifiera dark-pattern-checklist** mot Google Developer Policy (§8.1).

### BÖR före launch
8. **Re-ordna `premium_feature_*` så Audio är överst** (mest FOMO).
9. **Sätt upp Play Console-products**: 1× SUBS (yearly), 1× INAPP (lifetime). Regional pricing NO/CH/DK upp, PL/CZ/HU ner.
10. **Bokio gratis-tier**-uppsättning för AB.
11. **Privacy policy + Terms** måste täcka subscription auto-renewal explicit.

### KAN vänta till v1.1
12. Månads-tier (49–59 kr/mån) om data säger det efter 3–6 månader.
13. Free trial (7 dagar) när audio + PDF faktiskt fungerar.
14. In-App Messaging för grace-period-meddelanden.
15. Server-side receipt validation om piratism blir reellt problem.

---

## Filepaths som refereras (alla absoluta)

- `C:\Users\abbea\dev\birdy-bird-scanner\composeApp\src\commonMain\kotlin\se\birdy\app\ui\premium\PremiumScreen.kt`
- `C:\Users\abbea\dev\birdy-bird-scanner\composeApp\src\commonMain\kotlin\se\birdy\app\ui\premium\PremiumViewModel.kt`
- `C:\Users\abbea\dev\birdy-bird-scanner\composeApp\src\commonMain\composeResources\values\strings.xml` (rad 465-469)
- `C:\Users\abbea\dev\birdy-bird-scanner\composeApp\src\commonMain\composeResources\values-en\strings.xml` (rad 452-456)
- `C:\Users\abbea\dev\birdy-bird-scanner\docs\play-store\store-listing-sv.md`
- `C:\Users\abbea\dev\birdy-bird-scanner\docs\play-store\store-listing-en.md`
