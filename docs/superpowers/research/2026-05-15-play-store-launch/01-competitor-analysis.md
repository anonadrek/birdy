# Konkurrentanalys — Birdy Bird Scanner (Play Store-lansering)

> Forskning gjord 2026-05-15 inför `v1.0.0`-launch. Källa: agent-driven web-research mot Play Store, app-stats-tjänster och birding-publikationer.

## 200-ords sammanfattning

Marknaden domineras av **Merlin Bird ID** (Cornell Lab): 10M nedladdningar, 4,91/5 rating, helt gratis, Swedish-stöd sedan 2025, Scandinavia bird pack med 400+ arter inkl. Sound ID. Att slå Merlin på accuracy eller storlek är orealistiskt år 1. **Picture Bird** (Glority, 3,6M nedladdningar) är "anti-modellen" — aggressiva subscription-traps på 40 USD/år har genererat massa klagomål om bedrägeri-känsla. **BirdNET** (5,4M) är gratis och ljud-only. **Birda** (100k) är konceptuellt närmast Birdy med gamification + freemium, men kräver community-skala. Svenska aktörer som **Fågelguiden** (145 kr engångsköp, 700+ arter) och **All Birds Sweden** (555 arter) saknar AI-ID — de är digitala fältböcker, inte scanners.

**Vita fläckar:** (1) ingen aktör har "field journal"-estetik trots tydlig journaling-renässans-trend, (2) ingen svensk-native AI-app finns, (3) ingen erbjuder gamification utan social-network-tyngd, (4) "hederlig freemium utan trial-trap" är en trovärdig position eftersom Picture Bird har förorenat begreppet. Marknaden växer 14,7% CAGR; freemium är dominanta affärsmodellen. Birdys troliga #1-konkurrent vid Play Store-sökning är **Merlin** — strid om "feel" och svensk-native-upplevelse, inte accuracy.

---

## 1. Marknadens form

- Marknadsstorlek **182,6 MUSD (2024)** → **547,3 MUSD (2033)**, **14,7% CAGR**. Källa: Growth Market Reports.
- Fyra läger:
  - **Gratis-NGO:** Merlin, BirdNET, Audubon, Seek, eBird
  - **Freemium-trap:** Picture Bird, Smart Bird ID
  - **Community-freemium:** Birda
  - **Premium-fältguider:** Collins/Fågelguiden, Sibley, All Birds Sweden
  - **Hardware-bundle:** Bird Buddy

## 2. Konkurrent-data

| App | Pris | Downloads | Rating | SV-stöd | Foto-ID | Ljud-ID | Gamification |
|---|---|---|---|---|---|---|---|
| **Merlin** | Gratis | 10M (~17k/dag) | 4,91 | Artnamn på SV; Scandinavia pack 400+ arter | Ja | Ja (best in class) | Nej |
| **BirdNET** | Gratis | 5,4M (~2,7k/dag) | hög | SV artnamn-toggle | Nej | Ja (6000+ arter) | Nej |
| **Picture Bird** | 3,99 USD/mån, 40 USD/år | 3,6M (~72k/dag) | 4,58 | EN | Ja (svag) | Ja | Nej |
| **Smart Bird ID** | 29,99 USD/år eller 1 ID/dag | låg | medel | EN | Ja | Ja | Quiz |
| **ChirpOMatic** | Paid | låg | medel | EN | Nej | Ja | Nej |
| **Seek (iNat)** | Gratis | 1M+ | medel | flera språk | Ja (svag på fågel) | Nej | Nej |
| **eBird** | Gratis | 1,2M | medel | flera | Nej (är logger) | Nej | Life list |
| **Birda** | Freemium (Birda+) | 100k | 4,2 | EN | Ja | Nej | **Ja** |
| **Audubon** | Gratis | 2M+ | hög | EN | Nej (manuell) | Nej | Life list |
| **Bird Buddy** | App gratis; Premium 49 USD/år | hög | medel | EN | Ja | Ja | Postcards |
| **Fågelguiden** | 145 kr engångsköp | medel | hög | **SV nativ** | Nej | Nej | Nej |
| **All Birds Sweden** | Paid | låg | hög | **SV nativ** | Nej | Nej | Nej |
| **Artportalen** | Gratis | medel | medel | **SV nativ** | Nej | Nej | Nej |
| **Naturblick** | Gratis | 120k | medel | DE | Ja (urban) | Ja | Nej |

## 3. USPs och svagheter

### Merlin Bird ID (Cornell Lab)
- **USPs:** Cornell-brand, Sound ID-kvalitet (state-of-the-art), gratis, integration med eBird-data, Scandinavia bird pack 400+ arter sedan 2025.
- **Svagheter:** "Only suggests common species in your area, which is frustrating because it is the uncommon birds users need help identifying" (Audubon-citat). UI känns "slightly out-dated". Ingen gamification. Felidentifieringar förorenar eBird-data — citat: "Even moderately skilled birders can outperform Merlin."

### Picture Bird (Glority)
- **USPs:** Foto + ljud, snabb identifiering.
- **Svagheter:** **Trial-trap** är det dominerande klagomålet. 5–10% accuracy >3 m avstånd. Generisk tech-design. Banköverdrag-rapporter från arga användare: "Some users reported being charged even after canceling their subscription, resulting in overdraft fees from their banks."

### Birda
- **USPs:** Social plattform + gamification + HI Bird ID (people teach people).
- **Svaghet:** 100k users → community-skala saknas än.

### BirdNET
- **USPs:** 6000+ arter globalt, vetenskaplig trovärdighet.
- **Svaghet:** Ljud-only, spartansk UX.

### Fågelguiden / Collins
- **USPs:** SV-nativ, auktoritativa illustrationer (Mullarney/Zetterström), Lars Svensson-texter.
- **Svaghet:** Ingen AI-ID, prissatt vid version-uppdateringar (engångsköp + paid upgrades).

### Seek by iNaturalist
- **USPs:** Bred natur, kid-friendly, NatGeo-brand.
- **Svaghet:** Usel på fåglar specifikt — "Even when having a clean close-up pic of a bird, Seek is unable to identify its species."

## 4. UX / visuell identitet — konkurrenters generiska look

- **Merlin:** pop-art (Charley Harper) + grön accent, men "daterad".
- **Picture Bird:** generisk tech-vit/grön, "kunde vara plant-app".
- **Birda:** Instagram-social-feed-look.
- **BirdNET:** spartansk material-grön.
- **Fågelguiden / Collins:** klassisk fältbok-layout, ingen "feel".
- **All Birds Sweden:** foto-galleri.

**Birdys "Field Journal"-estetik (paper-bg, DM Serif Italic, Caveat, marginalia, stamp-seals, drop-cap) är kategori-distinkt.** Trenden inom analog fågeljournaling 2025 går mot stamps/fonts/pressed-leaves-renässans — Birdy är **i fas med kulturströmningen, inte emot**.

## 5. Vita fläckar i marknaden

1. **Field-journal-estetik** — ingen aktör.
2. **Hederlig freemium utan trial-trap** — Picture Bird har förstört begreppet, vilket öppnar för en motpol.
3. **Svensk-native AI-app** — Fågelguiden/All Birds Sweden är SV men inte AI; Merlin är AI men inte fullt SV-native.
4. **Single-player gamification** — Birda kräver community-engagemang; ingen erbjuder badges/stamps utan socialt nätverk.
5. **Premium = fördjupning, inte feature-gating** — de flesta gate:ar kärnan; Birdys plan att gate audio/PDF/stats är mjukare.

## 6. Tablestakes (måste-matcha)

- Foto-ID med konfidensgrad — Birdy OK
- Gratis kärna, ej trial-trap — Birdys plan OK
- ~500+ arter — Birdy **273/700 just nu** (varning: kan vara för litet vid launch jämfört med Merlin Scandinavia 400+, Fågelguiden 700+, All Birds Sweden 555)
- Svenska språk — Birdy OK
- Snabb inference — Birdy 14 ms OK
- Offline — Birdy OK (on-device TFLite)
- 4,3+ rating — kräver tidiga seed-reviews

## 7. Trolig #1-konkurrent vid Play Store-sökning

**Merlin Bird ID** garanterat — högsta rating + Cornell-brand + gratis + nu Swedish-stöd. Sekundära: Picture Bird (volym + ASO), Fågelguiden (lokal relevans), BirdNET (Cornell-brand). Birdy hamnar realistiskt på plats 5–15 vid launch.

## 8. Notable user-citat (insikter direkt från reviews)

- Merlin positivt: *"I love it so much. it's so fun and incredibly accurate."*
- Merlin kritiskt: *"It only suggests common species in your area, which is frustrating because it is the uncommon birds users need help identifying."*
- Picture Bird kritiskt: *"Some users reported being charged even after canceling their subscription, resulting in overdraft fees from their banks."*
- Seek kritiskt: *"Even when having a clean close-up pic of a bird, Seek is unable to identify its species."*
- Bird Buddy kritiskt: *"Missing 75% of bird visits as frustrating."*

## Källor

- [Merlin Bird ID on Google Play](https://play.google.com/store/apps/details?id=com.labs.merlinbirdid.app&hl=en_US)
- [BirdNET on Google Play](https://play.google.com/store/apps/details?id=de.tu_chemnitz.mi.kahst.birdnet)
- [Picture Bird Reviews — justuseapp.com](https://justuseapp.com/en/app/1474586978/picture-bird-birds-identifier/reviews)
- [Picture Bird AppBrain stats](https://www.appbrain.com/app/picture-bird-bird-identifier/com.glority.picturebird)
- [Picture Bird pricing — Qonversion](https://qonversion.io/apps/ios/picture-bird-birds-identifier/1474586978)
- [Smart Bird ID on Google Play](https://play.google.com/store/apps/details?id=com.smartbirdid.na&hl=en_US)
- [Seek by iNaturalist on Google Play](https://play.google.com/store/apps/details?id=org.inaturalist.seek&hl=en_US)
- [eBird on Google Play](https://play.google.com/store/apps/details?id=edu.cornell.birds.ebird&hl=en_US)
- [Birda on Google Play](https://play.google.com/store/apps/details?id=com.chirpbirding.birda&hl=en_US)
- [Audubon Bird Guide on Google Play](https://play.google.com/store/apps/details?id=com.audubon.mobile.android)
- [Birdbuddy on Google Play](https://play.google.com/store/apps/details?id=com.birdbuddy.app&hl=en_US)
- [Collins Bird Guide / Fågelguiden on Google Play](https://play.google.com/store/apps/details?id=com.natureguides.birdguide&hl=en_US)
- [All Birds Sweden on Google Play](https://play.google.com/store/apps/details?id=com.sunbirdimages.allbirdsse&hl=en_US)
- [Merlin Is Magical, but It Still Makes Mistakes — Audubon](https://www.audubon.org/magazine/merlin-magical-it-still-makes-mistakes)
- [Merlin Bird Packs / Regions Merlin Covers](https://merlin.allaboutbirds.org/bird-packs/)
- [Merlin: A Comprehensive App Review — Birda blog](https://birda.org/merlin-bird-id-a-comprehensive-app-review/)
- [BirdNET App — Cornell](https://birdnet.cornell.edu/app/)
- [Best Birding Apps 2026 — Bird Watching HQ](https://birdwatchinghq.com/best-birding-apps/)
- [Which is the best birdsong ID app? — Flying Lessons](https://flyinglessons.us/2020/12/22/which-is-the-best-birdsong-id-app-we-tested-them-and-have-a-winner/)
- [Bird Song Recognition App Market Research Report 2033](https://growthmarketreports.com/report/bird-song-recognition-app-market)
- [Bird Buddy Premium pricing](https://bigbird.alibaba.com/question/how-much-is-bird-buddy-premium)
- [Naturblick — Museum für Naturkunde Berlin](https://www.museumfuernaturkunde.berlin/en/discover-urban-nature-naturblick-app)
- [BirdLife Sverige — Merlin och BirdNET PDF](https://cdn.birdlife.se/wp-content/uploads/sites/53/2024/09/Merlin-o-BirdNET-1.pdf)
- [Ideas for Creative Birdwatching Journals and Sketches](https://realitypathing.com/ideas-for-creative-birdwatching-journals-and-sketches/)
- [Sibley Birds v2 — Sibley Guides](https://www.sibleyguides.com/product/sibley-birds-v2-app/)
- [Artportalen — SLU Artdatabanken](https://artportalen.se/)
