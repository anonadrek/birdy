---
description: Birdys north star + långsiktiga roadmap + var vi står just nu
---

Detta är **Birdys mål och långsiktiga roadmap** (vår north star). När jag kör `/goal`: påminn om målet, sammanfatta kort var vi står, och peka ut nästa steg mot målet. Uppdatera "Var vi står nu"-sektionen när vi passerar en milstolpe.

# 🎯 Birdy — mål & roadmap

## North star
En vacker, privat, **on-device** fältdagbok för fågelskådare — i hela världen. Identifiera vilken fågel som helst med kamera eller ljud, lär dig den, och behåll varje fynd som ett uppslag du äger. **Privacy-first:** nästan inget samlas in, datan stannar på telefonen. Tvålagers-användare: nybörjare som vill lära sig + entusiaster i fält.

## Det slutliga målet (Albin 2026-10-10)
**Ett riktigt Birdy-forum:** en plats där människor som tycker om fåglar träffas, pratar, delar sina fynd och diskuterar det de har sett, där en nybörjare kan fråga och få svar av någon som skådat i många år. Allt annat i roadmapen leder hit. **Ramar för forumet:** det är något man själv väljer att gå med i (konto och server bara för den som vill), fältdagboken stannar i telefonen tills användaren själv delar ett fynd, och privacy-löftet nedan gäller fortfarande för alla som inte går med. Forumet kräver moderering och en backend, så det byggs ovanpå spåren "Karta & moln" (konton, molnsynk) och "Community" (delning, kommentarer, flöde) nedan. Berättat publikt i fältanteckningen "Birdy × AlbIT" på birdy.community.

## Var vi står nu (framsteg, 2026-10-10)
- **v1.0–v1.2 — Norden/Europa (839 arter):** on-device foto- + ljud-ID, uppslagsverk, fältdagbok, märken, Dagens fågel, privat fynd-karta, Premium. **v1.2 live på Google Play sedan 2026-06-17.**
- **1.3.1 (gratis, Premium öppet för alla):** byggd och emulatortestad (vC130), nya butiksbilder i Flock-looken i Play-utkastet (en-US + sv-SE), väntar på att Albin drar in AAB:n; sedan inskick med 100 % utrullning. **Den betalda releasen** (betalning, Märken 1b, PDF 1, pop-up, intro, månadspris 49 kr) kommer efter: väntar på BirdNET:s svar, köptestet, MapTiler Flex, Resend och beslutet om löftet till tidiga användare.
- **Webben (birdy.community):** Flock-looken live i båda språken (ljus meny och ljust galleri sedan 2026-10-10), 100 av 180 artsidor live (pausat till tidigast 15 okt, sedan cirka 3 om dagen), blogg med See the song; Birdy × AlbIT och den omskrivna "Varför Birdy finns" väntar på Albins OK.
- **Sociala kanaler:** en video om dagen schemalagd till 7 nov på Instagram, Facebook, TikTok och YouTube.
- **v2 iOS-spåret:** all kod klar från samma KMP-kodbas (i0–i4: uppslagsverk, dagbok, foto-ID, live-kamera, ljud-ID, karta, notiser, PDF). Kvar: tester på en fysisk iPhone → i5 StoreKit 2 (kräver Apple Developer-enrollment) → i6 App Store. Checklista: `docs/ios-release-checklist.md`.

## Långsiktiga mål (från planen)

### Geografisk expansion — huvudtracken
ML-modellerna är redan globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000) — vi har bara filtrerat till EU. Expansionsjobbet sitter i **content-pipeline** (en YAML + plate-foto per art), **regional migrations-/säsongsdata**, **on-demand asset packs** (APK växer bortom v1.0:s 136 MB-base) och **fler språk**.
- **Inom ett år:** 1 000 artsidor på webben, på svenska och engelska.
- **v2 — "Asien + hela Europa" + iOS-launch:** utöka content till delar av Asien (Östasien/Indien först) **och** släpp på App Store. Webb: ny `/regions/`-sida med coverage-status.
- **v3 — "Hela världen":** alla återstående kontinenter; full content-skalning + språkstöd.

### Parallella feature-spår (inte version-bundna)
Kan landa när som helst längs geografi-tracken.
- **"Karta & moln":** konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. *(Personlig fynd-karta = första biten, levererad i v1.2. `Observation`-schemat har redan nullable `latitude`/`longitude`/`location_label` från Plan 5a.)*
- **"Community" → Birdy-forumet (slutmålet ovan):** delning av fynd, kommentarer, flöde, diskussioner, moderering. Opt-in.
- **Övrigt:** quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

## Ramar (icke förhandlingsbart)
- **Privacy-löftet:** "nästan inget samlas in, datan stannar på telefonen" — bryt inte utan diskussion. Forumet är opt-in och ändrar inte löftet för den som inte går med.
- **On-device AI:** ingen backend för inference.
- Solo-utvecklare bygger via Claude Code; granskning sker mellan tasks.
