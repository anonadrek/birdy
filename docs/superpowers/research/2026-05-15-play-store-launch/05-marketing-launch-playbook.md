# Birdy v1.0 — Go-to-market-playbook

**Datum:** 2026-05-15
**Författare:** Launch-strategi-research för anonadrek
**Mål-tag:** v1.0.0 (Play Store live ca 2026-05-29)
**Format:** Solo-indie, ingen marknadsbudget, Sverige-först, freemium

Den här playbooken är skriven för en specifik situation: en ensam utvecklare som har en distinkt, polerad Android-app (Field Journal-estetik, on-device AI, ~700 europeiska arter) och som ska in på Google Play Store på ca två veckor. Inga pengar för UA, men full produkt- och tidskontroll. Den långa konkurrenten heter Merlin (Cornell Lab, gratis, 10 miljoner användare globalt) — Birdys vinkel är nordiskt fokus, papper-estetik, och premium-funktioner som inte finns hos Merlin (PDF-export, stamp-collector, fält-märken).

Numreringen följer briefen.

---

## 1. Pre-launch (T-14 till T-1 dagar)

### 1.1 Beta-test-strategin: Internal Testing först, Closed sedan

Google Play har tre testspår med tydligt olika syften (uppdaterat policy december 2024):

- **Internal Testing** — upp till 100 testare, byggen ute på sekunder, ingen 14-dagars-låsning. Det är här du börjar dag 1.
- **Closed Testing** — krävs 14 dagar och minst 12 testare opt-in:ade samtidigt för nya **personliga** developer-konton skapade efter 13 november 2023. Detta är gate:n till production. Org-konton är exempterade, men eftersom anonadrek startar AB först nu är kontot troligen personligt.
- **Open Testing** — public beta, anyone-can-join. Hoppa över i v1.0 — du vill inte ha okontrollerad reach innan produkten är klar.

**Konkret rekommendation för Birdy:**
- **T-14 till T-10:** Internal Testing igång omedelbart. Bjud in dig själv + 3-5 nära vänner som äger Android-telefoner. Mål: hitta crash:er och regression i locale-switch + permission-flow på minst 2 olika tillverkare (Samsung + Pixel/OnePlus).
- **T-10:** Skapa Closed Testing-spår. Värva minst 15 testare (buffer mot opt-outs). Du behöver 12 *opt-in:ade i minst 14 dagar continous* innan production unlocks.
- **T+0 (launch-dag) blir alltså egentligen T+24 totalt** — du kan inte gå till production på dag 14 om Closed Testing inte är startat på dag 0. Räkna baklänges: vill du launcha 2026-05-29 måste Closed Testing-spåret ha startat med 12 testare senast 2026-05-15. Det är **idag**. Detta är den enskilt viktigaste insikten i hela planen.

**Var värvar man 12-15 testare?**
1. Direkta vänner och familj som äger Android (5-8 personer). Snabbaste vägen.
2. Posta i **r/AndroidAppsTesting** (~45k medlemmar, dedikerat till just detta) — flair:ar för "looking for testers" är OK.
3. **r/TestMyApp** — mindre, men birdy-specifik content (nordic birding) skiljer ut.
4. **TestersCommunity.com** och **PrimeTestLab** — community-tjänster som matcher testare mot dev:s (gratis bartering — du testar deras app, de testar din). Reciprocity-modellen är vanlig men kan ge zombie-testare som inte engagerar.
5. **Discord:** Kotlin/KMP-Discord, Android-dev Discords. Bjud in 3-4 dev-kollegor — de är vana att testa och rapportera buggar.
6. **Internt nätverk:** Naturskyddsföreningens lokalavdelningar, ornitologiska föreningar (kommunalt). En vänlig mejl till BirdLife Sverige Skåne med "jag bygger en svensk fågel-app, vill ni testa innan launch?" kan ge 3-5 entusiaster.

**Vad mäter du under beta:**
- Crash-free sessions (>99%)
- Cold-start time (mål: ≤1.5s, redan verifierat T15)
- Permission denial → recovery flow (känd känslig path)
- Locale-switch utan kill (verifierat T15, men test på fler enheter)
- Match-flow i naturen — *enbart riktiga fåglar utomhus* — eftersom AIY V1 gav 72% top-3 i eval-corpus, men real-world på okänd belysning/distans är oprövat på fler arter än talgoxe/koltrast/blåmes.

### 1.2 Pre-registration: skippa för Birdy

Pre-registration via Play Console låter dig publicera en store-listing 3-6 veckor före launch så folk kan klicka "Notify me / Install on launch". För **stora IP:n** (spel med trailers, kända varumärken) ger det ett download-spike på dag 0 som boostar ASO-ranking.

För indie-apps utan etablerat varumärke är detta tomt. Du har inte 50k Twitter-followers som klickar bara-för-att. Pre-registration är **inte värt det** för Birdy v1.0 — det stjäl bara fokus från Internal/Closed Testing-arbetet och du har inte trafiken som behövs för att toppa "Coming Soon"-listan.

**Undantag:** Om du har en email-lista på minst 500 personer eller en Twitter/X-following över 2000 som engagerar — då gör pre-registration. Annars: skippa.

### 1.3 Hype-byggande: när och var

Solo-indie-launch utan budget = du måste börja prata om Birdy minst 7 dagar före launch. Inte tidigare — uppmärksamhet är färskvara och du har bara en chans att vara "ny".

**T-14 till T-8 (build-in-public-fas):**
- Posta 1-2 ggr/dag på X/Twitter med skärmdumpar från Field Journal-estetiken. Tagga `#buildinpublic`, `#indiedev`, `#androiddev`, `#KMP`.
- Visa stamp-collector unlock-animationen som GIF — det är det mest delningsbara visuella elementet i appen.
- Tråda en post om "Why I built an offline bird ID app in 2026 when Merlin exists" — den vinkeln (offline + svenska + paper-aesthetic) är defenderbar och drar dev-Twitter-engagement.

**T-7 (one week to launch):**
- Posta första gången till **r/SideProject** (1.5M+ medlemmar, dev-friendly, tillåter showcase). Format: "Launching next week: Birdy, a paper-journal-styled bird ID app for Nordic species (no backend, on-device AI)".
- Skicka teaser till `Fåglar inpå knuten` Facebook-grupp (200k+ medlemmar, drivs av BirdLife Sverige) — *men inte som spam*. Format: "Hej, jag har byggt en app för svenska fåglar och söker 5 tidiga testare innan launch nästa vecka — DM mig om du är intresserad." Tillåtet eftersom du explicit söker testare och inte länkar till en Play Store-page.

**T-3 till T-1:**
- Press-pitchar går ut (se §4).
- Klipp ihop en 30-sekunders demo-video (paper-bg + stamp-unlock + match-result) och lägg på TikTok, Instagram Reels, X. **Inte YouTube** — för långt format för pre-launch teaser.

### 1.4 Press-kit + press-release — dag-1-ready

Lägg detta i `docs/play-store/press-kit/` eller GitHub Pages-deployen så det är publikt accessible på launch-dag.

**Press-kit-mapp ska innehålla:**
- `press-release-sv.md` + `press-release-en.md` — 250 ord, en quote från utvecklaren, en huvudvinkel (se nedan), 3 sak-payouts ("works offline", "designed in Sweden", "no tracking").
- `screenshots/` — 8-10 högupplösta (1080×1920 minst) skärmdumpar från Play Store-listingen. Återanvänd `docs/superpowers/screenshots/2026-05-15-v0.8.0-rc1/`.
- `logo/` — app-ikonen i 512px + 1024px PNG, transparent bg-version, en variant på vit och en på paper-bg.
- `factsheet.md` — en sida: developer, release-date, platforms, price (free + premium SEK/USD), Play Store URL, support-email, social.
- `media-quotes.md` — tomt-template för att fylla på efter launch.

**Huvudvinkel — välj EN:**
1. "Solo-utvecklare i Sverige bygger Field-Journal-formad fågelapp utan backend eller tracking" — passar svensk natur/teknik-press.
2. "Birdy is a love letter to Nordic birding, drawn in paper and graphite" — passar internationell design/Android-press.
3. "On-device AI bird ID with a hand-illustrated field journal aesthetic" — passar HN/r/Android-läsare.

Använd vinkel 1 för svensk press, vinkel 2 för Android Authority / Android Police, vinkel 3 för Show HN.

### 1.5 Dag-för-dag-checklista (T-14 till T-1)

| Dag | Aktion |
|---|---|
| T-14 (2026-05-15) | Closed Testing-spår skapat. 12 testare opt-in. AB-formation slutförd om möjligt (annars personlig dev-konto). |
| T-13 | Plan 6b kick-off (Billing + Premium-leverans). 14-dagars-klockan tickar. |
| T-12 | First build i Closed Testing. Testare bjuds in via email-länk. |
| T-11 | Press-kit utkast skrivet (sv + en versioner). |
| T-10 | Skärmdumpar för Play Store finaliserade (8 st minst, sv + en). |
| T-9 | Plan 6b Billing wire-up klar. Test purchase-flow på Internal Testing. |
| T-8 | Build-in-public-tweets startar (1-2/dag). |
| T-7 | r/SideProject teaser-post. Mejla `Fåglar inpå knuten`-mods för posting-permission. |
| T-6 | Press-pitchar skickas (svensk media, se §4). |
| T-5 | Demo-video klippt (30s + 60s versioner). |
| T-4 | TalkBack-walkthrough på SM-S918B (Plan 6a partial — gör nu). |
| T-3 | Privacy-policy verified live på GitHub Pages (Plan 6a partial). |
| T-2 | Show HN-utkast skrivet, ej publicerat. Production-build (1.0.0) signed. |
| T-1 | Final sanity check: Crashlytics-strömmar, Settings → About → version visar 1.0.0, alla Play Store-URL:er fungerar. |
| T-0 | Production rollout 100%. Show HN-post går live 06:00 PT (15:00 svensk tid). |

---

## 2. Launch day (T-0)

### 2.1 Hur dagen ser ut, timme för timme

**07:00 svensk tid:** Production rollout startar i Play Console. Staged rollout 20% först (om något går fel kan du pausa innan halva Sverige fått det).

**08:00:** Tweet "It's live: birdy.se. After 6 months of solo dev, my Nordic bird ID app is on Google Play." Länk till Play Store + screenshot av app-ikonen.

**09:00:** Posta i `Fåglar inpå knuten` (om mods godkänt). Inte som annons — som "tack till alla som testade, nu är den ute." Mjukare ton.

**10:00:** Posta i r/birding (USA-publik vaknar). Format: showcase-post, inte "buy my app". Se §3 för exakt template.

**12:00:** Buffer-tid. Svara på första kommentarerna från Reddit + Twitter. Crash-monitor öppen i Play Console.

**15:00 svensk tid (06:00 PT, 09:00 ET):** Show HN-post går live. Titel: `Show HN: Birdy – Offline bird ID for Nordic species (Kotlin/Compose Multiplatform)`. Detta är dagens viktigaste enskilda kanal.

**16:00-22:00:** Engagera med HN-kommentarer. Reply på varenda en. HN dödar posts som inte engagerar i de första 2 timmarna. Svara hellre 30 gånger än inte alls.

**23:00:** Day-1-rapport till dig själv: downloads, crash-free %, top-3 frågor från community.

### 2.2 Soft launch endast Sverige — JA

För Birdy är detta inte ens en svår fråga: **soft launch i Sverige i 5-10 dagar, sedan utöka till Norge/Finland/Danmark/Tyskland, sedan global**.

Skälen:
- Innehållet är 273/700 arter, primärt nordiska. En tysk användare som söker "Schwarzkehlchen" får möjligen träff, men en spansk användare får mer luckor.
- Locale-stöd är sv + en. Du har inte resurser för tysk/spansk localization-feedback dag-1.
- En koncentrerad svensk launch ger dig dina första 50-200 reviews i ett kontrollerbart språk. Du kan svara, lära, iterera.
- Play Store-algoritmen prioriterar apps med hög velocity i tidiga geografier — ranka först i Sverige, expandera sedan.

Konkret: i Play Console Release → Countries/regions → välj endast Sverige för production. Dag T+7 utöka till Norden. Dag T+14 utöka till tysktalande + UK. Dag T+21 global rollout.

### 2.3 Vänner-och-familj-marketing: tillåtet eller inte?

Google Play-policyn är tydlig: **incentiviserade reviews är förbjudet**. Du får inte erbjuda något (rabatt, in-app-credit, ens en gratis-gåva utanför appen) i utbyte mot en review eller install. Pixelinflation är banbart.

Däremot är följande **OK enligt policy**:
- Be vänner ladda ner och *använda* appen (no review krav).
- Be om feedback (privat, via email/DM) utan att kräva en Play Store-review.
- In-app prompt med standard Google Play in-app review-API (Apple-style men för Android — `ReviewManager`). Det visar Play Stores egen dialog utan att hoppa ut, och det är policy-säkert.

**Etisk avvägning för 50 vänner-strategin:** Du kan be 50 vänner att ladda ner. Du kan **inte** be dem skriva 5-stjärniga reviews. Men: om du har 50 vänner som genuint provar appen, gillar paper-estetiken, och hade tänkt skriva en review ändå — då är det fine att de skriver. Gränsen går vid *direkt instruktion eller incitament*.

Realistisk effekt: av 50 vänner blir det kanske 8-12 reviews. Det räcker för att flytta dig från 0 reviews (där Play Store inte ens visar stjärnor) till en synlig snittstjärna.

### 2.4 Product Hunt: hoppa över för v1.0

PH är iOS-tungt. Android-only-launches dör ofta på PH eftersom kärnpubliken är västkust-iOS-baserade tech-folk. En PH-launch utan "Best of the Day" topp-5 ger nästan inga downloads och stjäl en hel dag av engagement-budget.

Skippa PH för v1.0. Om Plan 6b ger en imponerande Premium-paywall + audio-feature (post-v1.0), kör PH då — då har du en konkret hook.

---

## 3. Community-marketing (de kanaler som faktiskt drar)

Detta är kapitlet där dollar-per-effort är bäst för Birdy. Sortera efter ROI.

### 3.1 Reddit (top-prio)

**r/birding** (300k+ medlemmar): Tillåter showcase-posts med "I built" / "I made"-format om de följer 90/10-regeln (90% genuint deltagande, 10% self-promo). Läs subreddit-reglerna i sidebar innan posting — vissa subreddits kräver mod-godkännande för app-länkar.

Template:
> **Title:** I made an offline bird ID app inspired by Field Journals (Android, free, no tracking)
> **Body:** I'm a solo dev based in Sweden. Spent the last 6 months building Birdy — a bird identification app that runs entirely on-device (no internet, no analytics), styled like a hand-drawn naturalist's field journal. ~700 European species at launch. It's free; there's a premium tier later for audio + PDF export. Would love feedback from real birders — what's missing? Play Store link in comments per sub rules.
> **First comment:** Play Store link + GitHub if open source pieces exist (the content pipeline could be public).

**r/birdwatching** (~110k) och **r/whatsbirdisthis** (~250k): Mer ID-fokuserade. Posta inte som showcase — visa istället ett resultat från Birdy och be om verifiering ("My new bird ID app says this is X, can someone confirm?"). Subtilt men effektivt.

**r/Sweden** (~600k): Posta i svenska om appen, helst i thread-format ("Visa Sverige: Jag byggde en svensk fågelapp"). Sverige-subreddit är toleranta mot hemmagjord svensk teknik om språket är på svenska och tonen är ödmjuk.

**r/Naturphotography**, **r/NatureIsBeautiful**: Birdy är inte foto-app, men `PlateFrame`-komponenten med naturalist-foto-frames kan landa här som "look at the design".

**r/androidapps** (~150k): Mer showcase-vänligt än r/Android (där App-posts ofta tas bort). Lägg där dag-2 efter Reddit-launchen.

**r/SideProject** (~1.5M, dev-fokus): Posta dag T-7 som teaser och dag T+0 som launch. Tillåts en gång var.

**Bännings-trick att undvika:**
- Ladda inte upp flera subreddits samma timme (cross-posting-spam-flagg).
- Använd inte Play Store-shortlink i title — länk i kommentar.
- Bygg karma 7-30 dagar innan launch om dina huvudkonton har <100 karma.
- Ha en separat Reddit-konto för Birdy-arbete om du normalt postar politik/etc — det renar associationen.

### 3.2 Facebook-grupper (svensk publik = guld)

**`Fåglar inpå knuten`** (BirdLife Sveriges grupp, 200k+ medlemmar): Mod-approved post är värt mer än 100 random posts. Mejla BirdLife Sverige (`info@birdlife.se`) två veckor i förväg, presentera dig + appen, fråga om de vill att du postar (eller om de vill posta åt dig som "vi har sett detta initiativ"). De är vänliga och kommer troligen att engagera om appen är professionell och inte spammar deras community.

**Övriga svenska grupper att hitta via FB-sökning:**
- `Fåglar och fågelskådning` (allmänt)
- Regionala BirdLife Sverige-grupper (25 regionala föreningar — Skåne, Stockholm, Västerbotten osv)
- `Vilka fåglar är det` / liknande ID-grupper
- `Norra Skånes fågelklubb` och liknande lokala klubbar

Tonen i svenska FB-fågelgrupper är *vänlig och kunskaps-orienterad*. Posta inte som annonsör — posta som birder som råkar ha byggt något. "Hej, jag har lagt 6 månader på det här, vill ni testa?"

### 3.3 Discord

- **r/Kotlin Discord** + **Kotlin Multiplatform Slack** — dev-publik, ger feedback på arkitektur. Inte stora download-numbers, men quality leads för Show HN-amplifiering.
- **Bird Buddy Discord** — community kring smart bird-feeders. ~10k+ medlemmar, alla birders. Var försiktig: Bird Buddy är en kommersiell aktör och kan se Birdy som konkurrens. Posta i "community-projects"-kanal om en sådan finns.
- **eBird-relaterade Discord:s** — söker av birding-Discord listings. Cornell Lab driver inte officiella Discords men entusiast-communities finns.

### 3.4 X/Twitter

Sverige-fokus: tagga `#fågelskådning` (~små men engagerad), `#fåglar`. Svenska birders finns på X men volym är låg jämfört med FB.

Engelsk-fokus: `#birdwatching`, `#birding`, `#birdsofX`, `#birdphotography`. Volym hög men brus också. Bättre signal: **rikta replies till stora birding-accounts** (@CornellBirds, @BirdLifeInt, @AudubonSociety) när relevant — inte spam, utan genuin reply-engagement under deras posts.

Dev-Twitter: `#KMP`, `#JetpackCompose`, `#androiddev`, `#buildinpublic`. Detta är publiken som kommer dela "look at this Kotlin Multiplatform indie app"-vibe.

### 3.5 Instagram + TikTok

Birding är extremt visuellt. Tre format som funkar för indie-apps 2025-2026:

1. **Före/efter-clip** (5-10s): Fågel-foto fade-in → Birdy-skärm med ID + stamp-unlock. Tagga `#birdwatching #birdid #naturetech`. Reach-potential: hög på TikTok For You-feed eftersom watch-time blir hög.
2. **Stamp-unlock-animation** (3-5s, looping): MiniStamp som fyller in från ghost → solid. Pure aesthetic, ingen voiceover. Funkar bättre på Instagram Reels än TikTok eftersom IG-publiken gillar designat content.
3. **Paper-aesthetic mood-video** (15s): Macro-shot på papper + bläck → cut till app → cut till fågel i naturen → cut till app igen. Försök hitta en folktune-soundtrack utan upphovsrätt (TikTok Sounds-bibliotek).

TikTok 2025-2026: organisk reach är fortfarande den bästa platform-en för zero-budget apps. Posta minst 3 ggr/vecka, ej alla samtidigt. Konsistens > kvalitet på enskild post.

Instagram organisk reach föll 40% under 2025 — det är inte var du lägger din primära energi. Använd det som spegling av TikTok-content.

### 3.6 YouTube

Långsiktigt > launch. För launch-dag är YouTube för långsamt — videos behöver veckor att indexeras.

**Post-launch (vecka 2-4):** 
- Skicka **gratis premium-licenser** till birding-YouTubers med 5k-50k subs. Exempel-kanaler att leta efter: "Lesley the Bird Nerd", "BirdNation", svenska "Naturmorgon"-liknande. Erbjud no-strings-attached licens + "om du gillar appen, nämn gärna". Konvertering: ca 1 av 10 nämner.
- Egna videos: 5-min "How Birdy works under the hood" — visa Kotlin Multiplatform + on-device TFLite. Den drar dev-publik och blir long-tail content.

### 3.7 HackerNews / Lobste.rs

**Show HN** är den enskilt största "free traffic"-källan en indie dev kan trigga. För Birdy:

**Titel:** `Show HN: Birdy – Offline bird ID for Nordic species (Kotlin Multiplatform)`

**Body:**
> Hi HN. I'm a solo dev in Sweden. Birdy is a Field-Journal-styled bird identification app for European birds (~700 species at launch, mostly Nordic). It runs entirely on-device using a quantized TensorFlow Lite model (AIY Birds V1, ~14ms inference on a Galaxy S23 Ultra). No backend, no analytics, no tracking.
> Built with Kotlin Multiplatform + Compose Multiplatform; the shared module compiles to JVM, Android, and iOS (iOS shipping later). The content pipeline is Python with Wikidata + Wikipedia + Claude + Commons.
> What I'd love feedback on: (1) the on-device-only stance vs. accuracy trade-offs, (2) the freemium model (free ID + diary, premium for audio + PDF + 10 field marks), (3) the Field Journal aesthetic — does it land or feel gimmicky?
> Play Store: [URL]. Privacy policy: [URL]. Repo (parts of it): [URL if applicable].

**Tider:** Posta 06:00 PT vardag (alltså Tisdag/Onsdag/Torsdag — undvik mån/fre). Det är 15:00 svensk tid.

**Engagemang:** Svara på *varenda* kommentar inom 30 minuter de första 4 timmarna. HN-algoritmen straffar tråd-tystnad.

**Lobste.rs:** Inbjudningsbaserat. Hoppa över om du inte har konto. Om du har — posta dagen efter HN, taggad `mobile` + `release`.

### 3.8 Indie Hackers + buildinpublic

Det är **inte för sent** att starta build-in-public-narrativet nu. Två veckor pre-launch är acceptabelt — du kan posta retro-tråd:

> "I built a Kotlin Multiplatform bird ID app in 6 months as a solo dev. Here's what worked, what didn't, and what's launching next week."

Posta på:
- **indiehackers.com** Milestones-section (gratis, ger discoverability).
- X/Twitter som tråd med 5-7 posts, var och en med screenshot.
- **r/SideProject** och **r/EntrepreneurRideAlong** som showcase.

Indie Hackers-launch-strategi 2025-data visar att IH ofta konverterar 3-8x bättre än Product Hunt för dev-tools — men för konsumentappar är skillnaden mindre. Lägg 30 min på en IH-post, inte 4 timmar.

---

## 4. PR + earned media

### 4.1 Svensk media — vem och hur

**Mest relevanta journalister/redaktioner:**
- **Mobil.se** — Sveriges största app/mobil-fokus. Kontakt via tipsa@mobil.se, eller leta upp redaktör Daniel Hessel / Christofer Andersson på LinkedIn. De gillar svensk indie + tydlig vinkel.
- **Computer Sweden / IDG.se** — IT-fokus, mindre konsument. Pitcha "Solo-dev bygger AI-app utan moln" — den vinkeln passar deras publik.
- **DN/SvD tech-sektioner** — svår nöt, men en bra story om svensk natur + AI kan landa hos Linus Larsson (DN) eller Jacob Henriksson (SvD).
- **P3 Tech med Andreas Ekström** — radioformat, gillar svensk indie-vinkel. Pitcha via SR:s tipsa-formulär.
- **Natursidan.se** — natur-fokuserat, mindre publik men exakt rätt målgrupp. Mejla redaktion@natursidan.se.
- **Vår Fågelvärld** (BirdLife Sveriges medlemstidning) — om appen presenteras väl kan de skriva en notis. Lång lead-time (kvartalsvis), men det blir evergreen content.

**Format för svensk pitch (max 200 ord):**
> Ämnesrad: "Solo-dev släpper svensk fågelapp utan moln eller tracking — Birdy lanseras 29 maj"
>
> Hej [namn],
>
> Jag har under sex månader byggt Birdy, en Android-app för fågelidentifiering som kör helt på telefonen — ingen molntjänst, ingen tracking. Appen är gjord för svenska och nordiska fåglar med en estetik som liknar handritad fältdagbok från 1800-talet.
>
> Tre saker som skiljer Birdy från Merlin (Cornells gratisapp):
> 1. Designad i Sverige, sv + en i grunden, nordiskt artfokus.
> 2. Helt offline — fungerar i fält utan täckning.
> 3. Freemium med en hederlig premium-tier (PDF-export, fält-märken) — ingen reklam, inga köpknappar i den fria versionen.
>
> Jag jobbar som ensam utvecklare och har dokumenterat resan publikt. Lansering 29 maj på Google Play Store. Skulle Birdy passa för en notis eller djupare reportage hos [redaktion]?
>
> Press-kit + skärmdumpar: [URL till GitHub Pages press-page]
> Bästa, [namn]

Skicka **personliga** mejl, en åt gången. INTE BCC. Förvänta dig 20% response, 5% publication.

### 4.2 Engelsk media

**Android-publikationer:**
- **Android Police** (tips@androidpolice.com) — gillar polerade indie-apps, har "App of the Day"-spot.
- **Android Authority** (tips@androidauthority.com) — bredare publik, mer kritisk.
- **9to5Google** (tips@9to5mac.com / via formulär) — Google-fokus.

**The Verge** är osannolikt för v1.0 — de skriver om indie-apps men kräver något *Verge-y* (designerbart eller kulturellt signifikant). Vänta tills Premium-launch eller iOS-launch.

**Natur-publikationer:**
- **Audubon Magazine** — USA-fokus, troligen ointresserade av nordisk app utan US-fokus.
- **Bird Watching Magazine (UK)** — bättre fit. Pitcha "Nordic indie dev launches paper-styled bird ID app" — UK-publiken gillar både fågelskådning och hantverk-estetik.

**Engelska pitch-template:** Samma 200-ord-struktur, byt språk + tona ned svensk-vinkeln om publiken är global.

### 4.3 Awards och listor

- **Google Play Best of**-listan kommer i december. För att hamna där behövs: launch före oktober, minst 50k downloads, hög rating, *uppmärksammad estetik eller teknisk innovation*. Birdys Field Journal-design är ett bra Best Of-snack.
- **Indie Game Festival** / **A' Design Award** — inte gaming, men design-awards finns för apps. Värt att överväga 2027 efter att appen är polerad och har userbase.
- **"Best Android apps May/June 2026"**-listor (CNET, TechRadar) — pitcha till dessa redaktioner separat tre veckor pre-launch. De curerar runt månadsslut.

---

## 5. SEO + content marketing

### 5.1 Hemsidan: ja, men inte stor

Plan 6a-T14 deployar `docs/play-store/` till GitHub Pages — privacy, terms, store-listing. Det är *minimum legal*. Du bör utöka det till en **landing page med 5 sidor**:

1. `index.html` — hero med app-mockup, "Download on Google Play"-knapp, en sektion per huvudfeature, en testimonials-sektion (post-launch).
2. `privacy.html` (finns)
3. `terms.html` (finns)
4. `blog/` — content marketing, se nedan.
5. `press.html` — press-kit, factsheet, screenshots, contact.

Designspråket på sajten ska matcha appens Field Journal-estetik — papper-bg, DM Serif Display Italic, Caveat. Återanvänd komponenterna konceptuellt. Använd Eleventy eller Astro för enkel statisk-site-build i GitHub Pages.

### 5.2 Content marketing: 5-10 evergreen-artiklar

Skriv på svenska. Long-tail search är där indie-apps faktiskt får organisk trafik utan annonser.

**Artikel-koncept (titel + målsökning):**
1. "Vilken fågel är det? 10 vanliga svenska fåglar och hur du känner igen dem" — söks 1000+ ggr/mån.
2. "Skillnaden mellan talgoxe och blåmes — en nybörjarguide"
3. "Vad är det för fågel som låter så här? Identifiera fågelläten utan internet"
4. "De 5 bästa fågelappar 2026 — jämförelse Merlin vs Birdy vs Picture Bird" (komparativ, du kan vara objektiv eftersom du har en USP)
5. "Så här fungerar AI-fågelidentifiering — en pedagogisk guide"
6. "Fågelskådning för nybörjare i Sverige — utrustning, appar, första turen"
7. "Bygg din egen fältdagbok — analog och digital"
8. "Sveriges 20 sällsyntaste fåglar 2026 (och var du kan se dem)"
9. "Häckande fåglar i Sverige — månadskalender för fågelskådare"
10. "Open source bird ID — så är Birdys content-pipeline byggd" (dev-publik, drar tech-trafik)

Skriv 1 artikel per 2 veckor post-launch. Mål: 5 artiklar publicerade till T+90. Använd dem för att backlink:a internt i Play Store-beskrivningen + i sociala posts.

**Ranka för "Birdy app" omedelbart genom att ha en `index.html`-page med exakt det namnet i title-tag. Konkurrensen om termen är obefintlig.**

### 5.3 Wikipedia: avstå

Att lägga till Birdy under "External Links" på t.ex. "Bird identification" eller "Talgoxe" är direkt mot Wikipedia:s external-links-policy. Promotional linking räknas som spam och tas bort av editors inom timmar — i värsta fall hamnar din domän på en global blacklist som blockerar all framtida länkning.

Det enda legitima sättet Birdy kan dyka upp på Wikipedia är om en oberoende editor anser att appen har encyklopedisk relevans (t.ex. har täckts av etablerad media som DN eller Wired). Det är `WP:GNG`-tröskeln. Vänta tills du har press-coverage; försök inte forcera.

---

## 6. Influencer + partnership

### 6.1 Mikro-influencers i svensk birding

5k-50k followers är sweet spot. Sök på Instagram:
- Hashtag-sök `#fågelskådning #sverigesfåglar #fågelskådareisverige`. Filter efter accounts med 5-50k followers. Lista 20 accounts på en spreadsheet.
- Studera senaste 10 posts: är de aktiva? Engagerar de? Skulle deras publik tagga vänner i en bird ID-app-post?
- DM:a 5 åt gången: "Hej, jag har byggt en app som passar dig och din publik. Vill du ha en gratis premium-licens utan strings attached? Om du gillar den får du gärna nämna, annars no worries."
- Förväntad konvertering: 2-3 av 20 nämner appen. Inga garantier.

### 6.2 BirdLife Sverige

Officiellt partnerskap kräver typiskt: skriftligt avtal, BirdLife-policy-alignment, ev. ekonomisk kompensation, juridisk granskning. För indie pre-launch är detta **för tidigt och för dyrt**.

Däremot: **fågelinformell relation**. Mejla `info@birdlife.se` och presentera dig + appen. Säg uttryckligen "jag söker inte partnerskap, jag ville bara att ni vet att appen finns och om ni ser någon felaktighet i artbeskrivningar så är jag mottaglig för korrigeringar." Det öppnar dörrar utan att tvinga dem ta beslut.

Post-launch + post-AB-formation kan ett formellt partnerskap diskuteras. Värdet: BirdLife:s 30k+ medlemmar är hardcore birders, de talar med varandra, en BirdLife-blessning är värd 1000 organisk reach.

### 6.3 Naturskyddsföreningen

Större organisation, bredare scope (inte bara fåglar). Pitch-vinkel: "Appen lär fler människor om Sveriges fåglar och biologisk mångfald." Mejla deras kommunikationsavdelning. Långsamt response, men möjlig "We use it" eller "Reviewed in our member magazine" outcome.

### 6.4 Universitet och skolor

**Universitetskurser:** Zoologi/ornitologi-kurser vid SLU Uppsala, Lund, Göteborgs universitet. Mejla kursansvarig och erbjud gratis premium-licenser till alla studenter under kursen. Värde: studenter blir nästa generations birders + kanske akademiska citeringar.

**Skolor:** Birdy kan positioneras som "lärar-verktyg" för biologi-kurser i mellanstadiet/högstadiet. Naturskolornas Riksförbund har ett nätverk av naturskolor — pitcha en gratis premium-licens till alla naturskolor. Värde: lärare blir ambassadörer, barn drar med föräldrar.

Förvänta dig att detta är en **6-månaders-investering** — låg på prioritetslistan första 30 dagarna efter launch.

---

## 7. Retention + virality loops

### 7.1 Få användaren tillbaka dag 2

Birdy har en svaghet: **inga push-notiser i v1.0** (det är post-v1 / v1.5). Det är den enskilt största retention-hooken som saknas.

Mitigation:
- **Stamp-unlock-loopen** är din viktigaste retention-mekanik. Plan 5b's UnlockQueue + Plan 7c's StampSeal-animation skapar en dopamin-loop. Sett från användare-perspektiv: jag scannar en ny art, jag får en stämpel, jag vill se vilken nästa stämpel är. Det är "Pokémon för fåglar"-mekaniken.
- **Sätt mål synligt i Badges-skärmen.** "5 av 25 stämplar upplåsta. Nästa: 'Vårfågelskådare' (3 av 5)." Plan 5b har detta — säkerställ att texten är ledande, inte bara informativ.
- **Diary-recap.** I Plan 6b eller post-v1, lägg till en "Veckans observationer"-skärm som auto-genereras från diary. Det är något att öppna appen för även när man inte är ute och scannar.

### 7.2 Word-of-mouth-hooks i appen

- **Share an observation** (redan i Plan 5a?): När jag spara en obs, ge mig en share-button. Genererad bild = paper-card med fågel-foto + art-namn + datum + plats + Caveat-text "scannad med Birdy". Watermark är subtle Birdy-logga nere höger. **Värt att lägga till om det inte redan finns.**
- **Share a badge unlock**: När ny stämpel låses upp i `UnlockBottomSheet`, lägg en "dela"-knapp. Bilden är stamp-seal + "Jag låste upp 'Vårfågelskådare' i Birdy".
- **Watermark debate:** En subtle watermark är OK. En aggressiv "GET BIRDY" call-to-action är skum. Balansen: foto-attribution-style i nedre högra hörnet, 40% opacity.

### 7.3 Notifications som blocker

Är det en blocker? **Nej, men det är en signifikant retention-handikapp.** Day 7 retention för apps utan push är typiskt 30-50% lägre än med push (källa: app retention benchmarks 2025-2026).

Plan v1.5 introducerar push (per `project_release_v1.md`). Tills dess:
- Ladda in retention via stamp-mekaniken och weekly summary-skärmar.
- Mät utan att panic. Day 7 på 12-15% är acceptabelt för v1.0. Day 30 på 7-10% är target.

---

## 8. Mätning + KPIs

### 8.1 Tre viktigaste metrics första 30 dagarna

**Inte downloads.** Downloads är vanity. Tre faktiska:

1. **Day-7-retention.** Är den över 12%, är appen on track. Under 8%, något i onboarding/first-scan-loop är trasigt.
2. **Antal observationer per aktiv användare per vecka.** Det är primärfeatures (scan) faktiska användning. Mål: >2 obs/vecka för aktiva.
3. **Premium-conversion-rate** (efter Plan 6b). Freemium-snitt 2025 är 2.18% (RevenueCat). Birdy är nische — 3-5% är realistiskt om paywall är hederlig.

### 8.2 Privacy-as-feature vs behov av data

Birdy:s USP är *no tracking*. Att lägga till Google Analytics SDK eller Firebase Analytics bryter den loften. Men du behöver *någon* data.

**Lösningen är opt-in privacy-first analytics:**
- **Aptabase** (open source, AGPLv3, mobil-fokus, GDPR-compliant utan device IDs). Lägg en opt-in-checkbox i onboarding ("Hjälp till att förbättra Birdy genom anonym användningsdata") — default OFF. Track endast: app-open, scan-completed, badge-unlocked. Inga PII.
- **Alternativ: ingen analytics alls för v1.0.** Använd istället Play Console-data (gratis, aggregerad, ingen PII) + occasional in-app feedback prompts.

Privacy-USP är värt mer än finkornig data för Birdy. Lutar mot **ingen tracking i v1.0, möjlig opt-in Aptabase i v1.1**.

### 8.3 Play Console-mätvärden att kolla dagligen

- **Crashes** (Android Vitals → crash rate). >1% är dåligt, >2% akut.
- **ANR** (App Not Responding). Cold-start-fix från Plan 6a T5 ska hålla dig under 0.5%.
- **Rating-trend** (Reviews tab). Spotta negativa reviews tidigt och svara.
- **Acquisition by source** (Acquisition → Acquisition reports). Vilka kanaler levererar installer? Justera marketing-effort därefter.
- **Country breakdown** för att se om soft-launch + utvidgning fungerar.

### 8.4 Crashlytics / Sentry — för eller emot

**För:** Bra crash-data, stack traces, real-time alerts.

**Emot:** Sentry skickar data till tredje part. Crashlytics är Firebase, Google = tracking. Bryter privacy-USP.

**Kompromiss:** **Använd Play Console Vitals** (gratis, ingen extra SDK, ingen PII). Den ger crash-statistik aggregerat. Du saknar fina stack traces för minor crashes — men för en solo-dev med 1000 användare det första halvåret räcker det.

Om du *måste* ha bättre crash-data: **Sentry self-hosted**. Du driftar en Sentry-instans på Hetzner/etc. Användarens data lämnar aldrig din egen kontroll. Privacy-USP intakt.

---

## 9. 90-dagars-roadmap

### Dag 0-30 (Launch sprint)

**Vecka 1:**
- Production rollout till Sverige.
- Svara på alla reviews inom 24h. Skapa svar-mallar för 5 vanligaste klagomål.
- Bevaka HN-thread, Reddit-trådar.
- Crashes-zero priority — fix:a kritiska inom 48h, ship hotfix till Play Store.

**Vecka 2:**
- Utöka till Norden + Tyskland + UK.
- Klipp 5 fler TikTok/Reels-clips från real-world användning (om du har testare som delar foton).
- Mejla svensk press uppföljning ("Birdy är nu live + här är download-numbers").

**Vecka 3:**
- Plan 6b uppdatering 1.1.0 om Billing är klar. Premium-launch.
- Outreach till birding-YouTubers med gratis premium-licenser.
- Skriv blog-artikel 1 ("10 vanliga svenska fåglar").

**Vecka 4:**
- Iteration baserat på data. Vad öppnar folk appen för? Vad ignorerar de?
- Första monthly recap-tweet ("Birdy hit X downloads in first month — here's what I learned").

### Dag 31-60 (Reviewer outreach + content)

- Publicera blog-artiklar 2-4. Backlink:a internt och från sociala posts.
- Skicka 10 fler influencer-DMs med gratis premium-licenser.
- Pitcha "Best Android apps June 2026"-listor på TechRadar, CNET, AndroidCentral.
- Börja konversationer med BirdLife Sverige om informellt samarbete (notis i Vår Fågelvärld 2026 Q3-nummer?).
- Soft-launch iOS-skelett *om* det är klart (annars vänta).
- Mät Premium-conversion. Om under 1%, justera paywall (för aggressiv? För dold? Fel pris?).

### Dag 61-90 (Iteration baserat på data)

- Plan 2b restart om data visar att artbristen är största friktionen ("Birdy hittade inte min fågel"-feedback).
- Push-notiser-prototyp om retention är låg (mål 1.5.0-release post-90).
- Festival/event-närvaro — Sveriges Stora Fågeldag (september). Bord, demo, premium-licenser till intresserade.
- Annual subscription-pivot om månadsabonnemang inte konverterar.

### Decision-trees

**Om Day 7-retention < 8%:**
- Är det onboarding? Kolla drop-off i Aptabase (om opt-in) eller hör av sig till 5 första-vecka-användare. Onboarding-flow rebuild prio 1.
- Är det första-scan-friction? Permission-flow eller camera-latency.
- Är det "appen är fin men jag har inget att göra här"? Bygg weekly summary post-v1.0.

**Om Premium-conversion < 1%:**
- Är paywallen synlig? Audit varje tab — visar Premium teasers konkret värde eller bara abstrakta "Unlock 10 field marks"-strings?
- Är priset fel? A/B-testa via Play Console (yearly bara, eller yearly + lifetime + monthly).
- Är features mock/halvfärdiga? Plan 6b-checklist: audio, PDF, stats, fält-märken alla *faktiskt* levererade?

**Om churn-signal (negative reviews om bug X):**
- Hotfix samma dag. Replicate, fix, ship via staged rollout, mäta 24h innan global.
- Svara på alla reviews relaterade till bug:en med "Fixed in version Y, please update."

**Om en kanal överraskande converterar:**
- Dubbla ner. Om TikTok-clips drar 10x mer än Twitter — sluta tweeta dagligen och börja klippa videos dagligen.

---

## Sammanfattning och nästa konkreta steg

Birdy:s launch-formel: **(1) soft-launch Sverige först, (2) Reddit + Facebook + HN som dag-1-kanaler, (3) press-pitch som vecka-2-amplifiering, (4) blog-content för long-tail SEO, (5) stamp-mekaniken som retention-motor, (6) ingen analytics i v1.0 — privacy är USP**.

**Tre saker att göra idag (T-14):**
1. Starta Closed Testing-spår och värva 12 testare. Den 14-dagars-klockan är hård.
2. Mejla BirdLife Sverige + `Fåglar inpå knuten`-mods om posting-permission om 7-10 dagar.
3. Skriv press-release och press-kit-utkast (2 timmar arbete, ej blockerat av Plan 6b).

**En sak att inte göra:** Försök inte lansera globalt dag 1. Sverige först. Det är den enskilt största spaknigen för att kontrollera initial review-kvalitet och rank-velocity.
