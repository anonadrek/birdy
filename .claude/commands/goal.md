---
description: Birdys north star + långsiktiga roadmap + var vi står just nu
---

Detta är **Birdys mål och långsiktiga roadmap** (vår north star). När jag kör `/goal`: påminn om målet, sammanfatta kort var vi står, och peka ut nästa steg mot målet. Uppdatera "Var vi står nu"-sektionen när vi passerar en milstolpe.

# 🎯 Birdy — mål & roadmap

## North star
En vacker, privat, **on-device** fältdagbok för fågelskådare — i hela världen. Identifiera vilken fågel som helst med kamera eller ljud, lär dig den, och behåll varje fynd som ett uppslag du äger. **Privacy-first:** nästan inget samlas in, datan stannar på telefonen. Tvålagers-användare: nybörjare som vill lära sig + entusiaster i fält.

## Var vi står nu (framsteg)
- **v1.0 — Norden/Europa (839 arter):** on-device foto- + ljud-ID, uppslagsverk, fältdagbok, gamification, premium-tier. I closed testing.
- **v1.1-batchen:** sök/uppslagsverk/märken-polish (DP A–E), onboarding v2, kamera-zoom + crop, website v2, Veckans uppslag, Troférum, jaga Dagens fågel.
- **v1.2 (NU — vC123 / 1.2.0-rc1, på `main` 2026-06-08):** **Personlig fynd-karta** — privat on-device-karta över egna fynd (opt-in platsfångst gratis, kart-vy Premium), Field Journal-kartstil + vax-sigill-pins, foto-uppladdningar geotaggar. **Första biten av "Karta & moln"-spåret.** → Nästa konkreta steg: bygg + ladda upp AAB:n.

## Långsiktiga mål (från planen)

### Geografisk expansion — huvudtracken
ML-modellerna är redan globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000) — vi har bara filtrerat till EU. Expansionsjobbet sitter i **content-pipeline** (en YAML + plate-foto per art), **regional migrations-/säsongsdata**, **on-demand asset packs** (APK växer bortom v1.0:s 136 MB-base) och **fler språk**.
- **v2 — "Asien + hela Europa" + iOS-launch:** utöka content till delar av Asien (Östasien/Indien först) **och** släpp samtidigt på App Store. KMP-skelettet finns; iOS = Compose Multiplatform-iOS-target + SwiftUI-shim för plattforms-API:er (kamera, audio, billing → StoreKit, share-sheet, file-export). Webb: ny `/regions/`-sida med coverage-status.
- **v3 — "Hela världen":** alla återstående kontinenter; full content-skalning + språkstöd.

### Parallella feature-spår (inte version-bundna)
Kan landa när som helst längs geografi-tracken.
- **"Karta & moln":** konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. *(Personlig fynd-karta = första biten, levererad i v1.2. `Observation`-schemat har redan nullable `latitude`/`longitude`/`location_label` från Plan 5a.)*
- **"Community":** delning av fynd, kommentarer, flöde, moderering.
- **Övrigt:** quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

## Ramar (icke förhandlingsbart)
- **Privacy-löftet:** "nästan inget samlas in, datan stannar på telefonen" — bryt inte utan diskussion.
- **On-device AI:** ingen backend för inference.
- Solo-utvecklare bygger via Claude Code; granskning sker mellan tasks.
