# Birdy — Launch community posts (ready to paste)

> Generated 2026-06-17 from the launch playbook (`docs/superpowers/research/2026-05-15-play-store-launch/05-marketing-launch-playbook.md`). App is LIVE in production. Positioning: privacy + field-journal, free during launch, Merlin = complementary, **never quote an accuracy %**. Value-first (90/10), disclose author, respect each community's rules.

---

## 1. Show HN

**Timing:** Tue/Wed/Thu ~06:00 PT (15:00 svensk tid) — avoid Mon/Fri. Reply to every comment within ~30 min for the first 4 hours.
**Rule:** No marketing language — factual, technical, humble. Lead with engineering.

**Title:**
```
Show HN: Birdy – Offline, on-device bird ID for European species (Kotlin Multiplatform)
```

**Body:**
```
Hi HN. I'm a solo dev in Sweden. Birdy is a bird identification app for European birds (839 species, Nordics first) that runs entirely on-device — no backend, no account, no analytics, no ads, no tracking. It works offline in the field with no signal.

Photo ID uses a quantized TensorFlow Lite model (AIY Birds V1, ~14ms inference on a Galaxy S23 Ultra); audio ID uses BirdNET-Lite. Both run locally on the phone. Everything you identify goes into a "Field Journal" — a diary styled like a hand-drawn 1800s naturalist's notebook, where each find is a page you own. There's also an encyclopedia of all 839 species, a private on-device map of your own finds, and a collectible wax-seal "stamp" system for gamification.

Built with Kotlin Multiplatform + Compose Multiplatform; the shared module compiles to JVM, Android, and iOS (Android shipped first, iOS later). The content pipeline is Python (Wikidata + Wikipedia + Commons + Claude).

The deliberate constraint was: nothing leaves the phone. No login, no cloud sync, no telemetry. That forced some interesting trade-offs (on-device-only models, no server-side correction loop, asset delivery via Play Asset Delivery to keep the base install reasonable despite ~2000 species plates + two ML models).

What I'd love feedback on: (1) the strict on-device-only stance and what it costs you, (2) the Field Journal aesthetic — does it land or feel gimmicky, (3) the KMP + Compose Multiplatform experience for something this asset-heavy.

It's free (a premium tier is open to everyone during launch). Play Store and privacy policy in the first comment.
```

**Prepared first founder comment:**
```
Author here, happy to answer anything.

Play Store: https://play.google.com/store/apps/details?id=se.birdy.android
Site + privacy policy: https://birdy.community  (legal at https://birdy.community/legal/privacy/)

A few notes I expect questions on:

- Why on-device only? It started as a privacy stance ("almost nothing collected, data stays on the phone") and turned into a fun engineering constraint. No server means no per-user correction loop, so I lean on a good encyclopedia + the journal so the app is useful even when an ID is uncertain.
- Merlin (Cornell Lab) is the giant here and it's excellent — I see Birdy as complementary, not a competitor to beat. My angles are the offline/no-tracking stance, the Nordic-first content, and the journal aesthetic, not "more accurate".
- Stack: shared Kotlin module (domain + ML wrappers) compiles to JVM/Android/iOS, UI in Compose Multiplatform, SQLDelight for the diary, CameraX for live scan, TFLite (photo) + BirdNET-Lite via TF Select ops (audio).
- It's a personal solo project; SV + EN localized, live in 10 countries.

Roadmap-ish: iOS, more regions (the models are globally trained, the bottleneck is content + plates per species), and eventually push/cloud sync as an *opt-in*, without breaking the no-tracking default.
```

---

## 2. r/birding (showcase)

**Timing:** ~10:00 svensk tid (US audience waking). Engage all day.
**Rule:** 90/10 — be a real participant. No Play link in title/body; put it in a comment per sub rules. Read the sidebar first.

**Title:**
```
I built an offline, no-tracking bird ID app styled like a hand-drawn field journal (Android, free)
```

**Body:**
```
I'm a solo dev in Sweden and a casual birder. I spent the last while building Birdy — a bird identification app that runs entirely on-device: no internet needed, no account, no analytics, no ads, nothing leaves your phone. It works offline in the field.

The part I care about most isn't the ID itself — it's that every bird you find becomes a page in a "Field Journal" you actually own, styled like an old hand-drawn naturalist's notebook. Photo + audio ID, an encyclopedia of 839 European species (Nordics first), and a private on-device map of your own finds.

I'm not trying to out-do Merlin — it's brilliant and I use it too. My angle is different: privacy-first, offline, and the journal feeling that a find is something you keep, not data you upload.

I'd genuinely love feedback from real birders: what's missing, what feels off, what would make you actually keep a digital journal? It's free (premium features are open to everyone right now). Link in the comments per sub rules.
```

**First comment (link):**
```
Play Store: https://play.google.com/store/apps/details?id=se.birdy.android
More + privacy policy: https://birdy.community

Happy to answer anything — it's a personal project so honest critique is welcome.
```

---

## 3. r/androidapps + r/SideProject ("I built this", honest indie)

**Timing:** r/androidapps day-2; r/SideProject launch-day. Don't cross-post within the same hour.
**Rule:** Honest indie disclosure — say you're the dev. r/SideProject loves the build story.

**Title:**
```
I built Birdy — an offline, no-tracking bird ID app with a hand-drawn field-journal design (solo dev, Kotlin Multiplatform)
```

**Body:**
```
Disclosure: I'm the developer. Solo dev in Sweden, this is a personal project.

Birdy is a bird identification app for European birds (839 species, Nordics first) that runs entirely on-device — no backend, no account, no analytics, no ads, no tracking. It works fully offline.

What it does:
- Photo ID (live camera scan + gallery/upload) and audio ID (BirdNET) — both run locally on the phone.
- A "Field Journal" diary styled like a hand-drawn 1800s naturalist's notebook — every find is a page you own, not data you upload.
- An encyclopedia of all 839 species, a private on-device map of your own finds, and a collectible wax-seal "stamp" system.

Tech: Kotlin Multiplatform + Compose Multiplatform (shared module → JVM/Android/iOS, Android first), TensorFlow Lite for photo, BirdNET-Lite for audio, SQLDelight for the journal, CameraX for the live scan. The hardest part was keeping it strictly on-device while still shipping ~2000 species plates + two ML models — solved with Play Asset Delivery.

It's free; the premium tier is open to everyone during launch. Would love feedback — especially on the journal concept and whether the on-device-only stance matters to you.

Play Store: https://play.google.com/store/apps/details?id=se.birdy.android
Site: https://birdy.community
```

---

## 4. "Fåglar inpå knuten" (Facebook, 200k+, BirdLife) — SVENSKA

**Timing:** Posta först EFTER 2–4 veckor som genuin, hjälpsam deltagare (svara på "vilken fågel är det?"-trådar). Dagtid mitt i veckan.
**Regel:** BirdLife Sveriges grupp — mejla mods/`info@birdlife.se` i förväg om posting-tillstånd. Värde först, ingen säljton.

**Inlägg:**
```
Hej allihop! Jag har hängt med här ett tag och lärt mig massor av era trådar.

En liten sak jag velat dela: jag är ensam utvecklare och har på fritiden byggt en gratis liten svensk app som svarar på "vad är det här för fågel?" — både via foto och läte. Det jag är mest stolt över är inte ID:t i sig, utan att allt körs direkt i telefonen: ingen inloggning, ingen reklam, ingen spårning, inget skickas till någon molntjänst. Den funkar offline ute i fält utan täckning. "Nästan ingenting samlas in, allt stannar i telefonen."

Varje fågel man hittar blir en sida i en liten fältdagbok i telefonen — formgiven som en handritad gammaldags naturdagbok, så det känns som att man samlar på något eget snarare än matar in data nånstans. Det finns också ett uppslagsverk över 839 europeiska arter och en privat karta över ens egna fynd som bara ligger på telefonen.

Jag försöker inte konkurrera med Merlin — den är fantastisk och jag använder den själv. Det här är mer ett litet svenskt fritidsprojekt med en annan känsla.

Den är helt gratis just nu. Jag delar gärna länk i en kommentar om någon är nyfiken — men mest ville jag bara höra: vad skulle ni vilja att en sån här dagbok kunde göra? Tar tacksamt emot all ärlig feedback från folk som kan det här bättre än jag.
```

**Kommentar (länk, om mods tillåter):**
```
Länk för den som vill kika: https://play.google.com/store/apps/details?id=se.birdy.android
Mer info + integritetspolicy: https://birdy.community
```

---

## 5. r/Sweden — SVENSKA

**Timing:** Dagtid på en vardag. Thread-format ("Visa Sverige") funkar.
**Regel:** Ärlig dev-disclosure, ödmjuk ton, inget säljsnack.

**Titel:**
```
Visa Sverige: Jag byggde en svensk fågel-app som kör helt i telefonen — ingen spårning, inget moln, fungerar offline
```

**Brödtext:**
```
Hej r/Sweden! Jag är ensam utvecklare och har på fritiden byggt en gratis app för att identifiera fåglar — både via foto och läte.

Grejen jag är mest stolt över: allt körs direkt på telefonen. Ingen inloggning, ingen reklam, ingen spårning, ingenting skickas till något moln. Den funkar offline ute i skogen utan täckning. Tanken är "nästan ingenting samlas in, allt stannar i telefonen" — i en tid när varenda app vill ha ett konto och dina data kändes det skönt att bygga tvärtom.

Varje fågel man hittar blir en sida i en liten fältdagbok i telefonen, formgiven som en handritad gammaldags naturdagbok, så det känns som att man samlar på något eget. Det finns ett uppslagsverk över 839 europeiska arter och en privat karta över ens egna fynd som bara ligger lokalt.

Den är byggd i Kotlin Multiplatform + Compose, och AI:n (foto + ljud) kör lokalt med TensorFlow Lite respektive BirdNET. Jag försöker inte slå Merlin — den är grym — utan ville göra något svenskt med en annan känsla och en hård integritets-linje.

Helt gratis just nu. Jag länkar gärna i en kommentar om någon vill testa, men jag är mest nyfiken på vad ni tycker om idén — och om "allt stannar i telefonen" är något ni faktiskt bryr er om eller om jag överskattar det.
```

**Kommentar (länk):**
```
För den nyfikne: https://play.google.com/store/apps/details?id=se.birdy.android
Mer + integritetspolicy: https://birdy.community
```
