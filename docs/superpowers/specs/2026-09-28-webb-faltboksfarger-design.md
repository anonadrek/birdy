# Webben i fältbokens färger: espresso, persika och handstil

_Spec, godkänd design 2026-09-28 (brainstorm med Albin, visuell jämförelse i webbläsaren). Gäller birdy.community (`website/`)._

## 1. Bakgrund och mål

1.3-lyftet (live sedan 2026-09-25) gav webben en ny layout som Albin är nöjd med, men också appens mossgröna mörka partier. Albin saknar fältbokskänslan från tidigare versioner: den varma orange/persika färgskalan och handstilen. Mossgrönt ihop med de varma färgerna "skär sig".

**Mål:** exakt samma layout och innehåll som i dag, men:

1. **Inget grönt** i webbens egen yta. De mörka partierna blir espresso (mycket mörk varm brun).
2. **Persika** bakom telefonkarusellen.
3. **Mer handstil:** accentordet i varje rubrik handskrivet, marginalanteckningar under rubrikerna och några handskrivna bildtexter.
4. **Rivna papperskanter** där ett mörkt eller persikofärgat band börjar och slutar.

Artsidorna (fas 2 i artsidorsplanen, ej påbörjad) byggs i samma utseende.

## 2. Beslut från brainstormen

| Fråga | Beslut |
|---|---|
| Layout | Oförändrad. Bara färger, typsnitt och kanter ändras. |
| Grönt | Bort helt från webbens yta. |
| Mörk färg | **Espresso** (valdes före rostbrunt som 24 sep-versionen hade). |
| Persika | Bakom telefonkarusellen (från 24 sep-versionen). |
| Handstil | **Nivå 2:** accentord + marginalanteckningar + bildtexter. Listrubriker, FAQ-frågor och kickers förblir tryckstil (nivå 3 valdes bort för läsbarheten). |
| Rivna kanter | Ja (julis `DeckleEdge`). |
| Snedställda kort, hörnmarkeringar, stämpelnummer (julis detaljer) | Nej. |
| Telefonerna i karusellen | Visar appen som den är, alltså appens mossgröna Lyssna-skärm blir kvar. Webben ska inte visa en app som inte finns. |

Godkänd rendering (riktiga startsidan med färgerna utbytta): `assets/2026-09-28-webb-faltboksfarger/godkand-espresso-marginalnoter.jpg`. Jämförelse: `fore-1-3-mossa.jpg` (i dag) och `referens-juli-faltbok.jpg` (juli). Anteckningarnas ordalydelse i renderingen var ett första utkast; texterna i avsnitt 5.2 gäller.

## 3. Färger

### 3.1 Tokens (`website/src/styles/tokens.css`)

De mossgröna tokens byter namn till neutrala namn så att nästa färgbyte blir en ändring i en fil och inga namn ljuger.

| Före | Efter | Värde |
|---|---|---|
| `--moss` `#1F2A19` | `--dark` | `#2A1D17` |
| `--moss-2` `#2B3A23` | `--dark-2` | `#3B2A21` |
| `--moss-deep` `#172013` | `--dark-deep` | `#1E1410` |
| (ny) | `--dark-rgb` | `42, 29, 23` (för `rgba(var(--dark-rgb), .8)` i fototoningar) |
| (ny) | `--peach` | `#FDE5CB` |
| `--ink` `#26301F` (oliv) | `--ink` | `#302019` |
| `--muted` `#5B6350` (oliv) | `--muted` | `#6E584B` |

Oförändrade: `--paper`, `--card`, `--line`, `--rust`, `--rust-deep`, `--apricot`, `--brass`, `--brass-hi`, `--brass-ink`, `--cream`, `--navy`.

### 3.2 Hårdkodade gröna nyanser som blir tokens

Alla förekomster av `rgba(31, 42, 25, …)`, `#18200F` och `#1F2A19` utanför telefonerna ersätts med `--dark*`/`--dark-rgb`. Kända ställen: `Hero.astro`, `hero/HeroScene.astro`, `Nav.astro`, `AppTour.astro`, `Premium.astro`, `FinalCta.astro`, `Footer.astro`, `FieldNotesIndex.astro`, `FieldNoteArticle.astro`, `JournalSection.astro`, `NoteCard.astro`, `global.css` (`--color-moss`, `::selection`), `article-prose.css` (`#3B4434` blir en varm brun), `CoverageMap.astro` (`INK` och `MOSS` för fågeln i kartnålarna blir ink respektive espresso), `layouts/Layout.astro` (`theme-color`). Efter bytet ska `grep -rniE "1f2a19|31, ?42, ?25|18200f|--moss" website/src` bara träffa telefonerna (3.3).

### 3.3 Telefonerna behåller appens färger

`website/src/styles/phone.css` och `phone/screens/*` visar appen. `.ph` får egna, lokala tokens med appens värden (`--moss`, `--moss-2`, `--moss-deep`, `--ink: #26301F`, `--muted: #5B6350`) så att skärmarna ser ut som appen oavsett webbens tokens. Mossgrönt finns alltså kvar bara inuti telefonerna. Naturens grönska i fotona och LoopLeads logga (`#4d7f58`) räknas inte.

## 4. Var färgerna hamnar

| Del | I dag | Efter |
|---|---|---|
| Menyn (solid läge, mobilplattan) | mossa | espresso |
| Hero (foto) | mossa + mossgrön toning | espresso + espressotoning |
| Tre sätt att fånga | papper | papper |
| Fältboken | kort | kort |
| Appkarusellen | mossa (radiell) | **persika**, mörk text, rost accent, rostprickar |
| Uppslagsverket + karta | papper | papper |
| Premium | mossa | espresso (mässingssigill och mässingsaccent kvar) |
| Integritet, Fältanteckningar | papper | papper |
| Frågor | kort | kort |
| Ta med Birdy (foto) | mossa + toning | espresso + espressotoning |
| Sidfot | mossa djup | espresso djup |
| Bloggens rubrikband, artikelns hero och slutruta | mossa | espresso |
| Juridiksidorna | (menyn) | (menyn) espresso |

Karusellen på persika: rubrik och bildtext `--ink`, ingress och bildtextens brödtext `--muted`, kicker och accent `--rust`, pilknappar med mörk kant, förloppsstreck i rost.

## 5. Handstil

Typsnittet är Caveat, som redan är självhostat (`/fonts/caveat-*.woff2`, `--font-script`).

### 5.1 Accentord

`JournalHeadline` `.accent` och heroens `h1 em` sätts i Caveat bold, normal lutning, ungefär `1.18em`, färg som i dag via `--jh-accent` (rost på ljust, aprikos på mörkt). Gäller hela webben: startsidan på båda språken, bloggen, artiklar och juridiksidornas rubriker där accent används. `Accent.astro` (bara telefonskärmar) ändras inte.

### 5.2 Marginalanteckningar (bara startsidan)

En ny komponent `ui/MarginNote.astro` renderar en kort handskriven rad direkt under rubriken. Riktig text (inte `aria-hidden`), Caveat bold `clamp(20px, 2vw, 24px)`, rost på ljust och aprikos på mörkt, lätt lutning (`rotate(-1.4deg)`, ingen animation). Ny valfri nyckel `note` i copy-filerna; saknas den renderas inget.

| Nyckel | Svenska | Engelska |
|---|---|---|
| `hero.note` | Se. Lyssna. Spara. | See. Listen. Keep. |
| `howItWorks.note` | kamera, foto eller läte | camera, photo or song |
| `tour.note` | så här ser det ut i fält | this is how it looks in the field |
| `guide.note` | slå upp fågeln du just såg | look up the bird you just saw |
| `premium.note` | helt valfritt, att känna igen fåglar är gratis | optional, identifying birds is free |
| `privacy.note` | dina bilder stannar i telefonen | your photos stay on your phone |
| `fieldNotes.note` | anteckningar från oss som bygger Birdy | notes from the people who build Birdy |
| `faq.note` | det folk brukar undra | what people usually ask |
| `download.note` | vi ses i fält | see you out there |

Fältboken-sektionen har redan handstil (`journal.marginalia`, plåtens bildtext) och får ingen ny anteckning.

### 5.3 Bildtexter och småanteckningar

- Under siffrorna 839 / 34 / 0: ny nyckel `guide.stats[].note`. SV: "från vanliga till sällsynta", "tjänas in när du hittar fåglar", "din dagbok stannar hos dig". EN: "from common to rare", "earned by finding birds", "your journal stays with you". Caveat ungefär 19 px, rost.
- Kartans bildtext (`.mapcap`) i Caveat, rost.
- Sidfotens tagline (`.tag`) i Caveat, aprikos.

### 5.4 Textregler

Alla nya texter följer husreglerna och vakterna: inga tankstreck (`test:no-dashes`), inga precisionspåståenden (`test:no-accuracy`), samma nycklar på båda språken (`test:i18n`), inget löfte om att allt fungerar utan nät (kartan kräver nät). Albin kan justera ordalydelsen i förhandsvisningen.

## 6. Rivna papperskanter

`ui/DeckleEdge.astro` återinförs från juliversionen (`git show 138d3941:website/src/components/ui/DeckleEdge.astro`), med `color`-prop och `side="top" | "bottom"`. Höjd `clamp(14px, 2vw, 26px)`, `aria-hidden`. Regel: kanten ligger överst i det undre bandet och har det övre bandets färg, så att det övre "river ner" i det undre (så som den godkända renderingen gör). Placering:

- hero → Tre sätt att fånga: espresso river ner i papperet
- Fältboken → karusellen: kortets ljusa färg river ner i persikan
- karusellen → Uppslagsverket: persika river ner i papperet
- Uppslagsverket → Premium: papper river ner i espresso
- Premium → Integritet: espresso river ner i papperet
- Frågor → Ta med Birdy: kortets ljusa färg river ner i espresso
- Ta med Birdy → sidfot: espresso river ner i den djupare espresson

Premiums mässingssigill ligger ovanför kanten (högre `z-index` än kanten). På bloggens rubrikband och artikelns hero används samma kant mot papperet under.

## 7. Oförändrat

Layout, avstånd, bilder, texter (utöver de nya anteckningarna), komponentstruktur, typsnitt i brödtext och rubriker (DM Serif Display, Inter), appens egna färger, kartans stil utöver fågeln i nålarna.

## 8. Delningsbilder och metadata

- `tools/generate-og.mjs`: toningen `#1F2A19` blir `#2A1D17`; kör `npm run assets:og` och committa `public/og-field-{sv,en}.png`.
- `alt.panorama` i båda copy-filerna: "i mossgrönt ljus" / "in moss green light" blir "i varmt ljus" / "in warm light".
- `theme-color` blir `#2A1D17`.

## 9. Artsidorna

Före fas 2 av artsidorna (`docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md`) uppdateras:

- specen `2026-09-25-artsidor-design.md` avsnitt om sidlayouten: "Appruta (mossgrön)" blir espresso; artsidornas rubriker använder handskrivna accentord (via `JournalHeadline`) och marginalanteckningen (`marginalia`) följer `MarginNote`-stilen;
- planen: `.sp-app { background: var(--moss) }` blir `var(--dark)`, och andra `--moss`-referenser byts mot `--dark*`.

Artsidorna ärver annars tokens och komponenter automatiskt.

## 10. Kvalitet och vakter

- `scripts/check-contrast.mjs`: par med `moss*` byts mot `dark*`; nya par `ink`/`muted`/`rust` på `peach` och `cream`/`apricot`/`brass-hi` på `dark`, `dark-2`, `dark-deep`; alfa-paren (sidfot, karusell, Premium) räknas mot de nya bakgrunderna, och karusellens alfa-par byts mot persikaparen. Alla ≥ 4.5:1. (Kontrollräknat: rost på papper ≈ 5,6:1, rost på persika ≈ 5,3:1, muted på persika ≈ 5,4:1, aprikos på espresso ≈ 8,9:1.)
- `tests/home.spec.ts`: menyfärgen `rgb(31, 42, 25)` blir `rgb(42, 29, 23)` och testnamnen "mossgrön" blir "espresso"; nytt test att marginalanteckningarna finns på båda språken och att karusellbandet är persika.
- Alla vakter gröna: `test:i18n`, `test:no-accuracy`, `test:contrast`, `test:no-dashes`, `build`, Playwright (`PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npm run test:smoke`), axe 0 fel.
- Visuell genomgång av varje sida på 1440 och 390 px, SV och EN: startsidan, bloggindex, inlägget, de tre juridiksidorna. Särskilt: anteckningarna bryts snyggt på mobil, kanterna glappar inte, sigillet ligger över kanten.
- Lighthouse mobil `/sv/` minst som i dag (93 / 100 / 96 / 100).

## 11. Genomförande och release

- Gren `website/faltboksfarger` i en worktree (t.ex. `C:/w/birdy-faltbok`), små commits.
- Förhandsvisning (Vercel-preview för grenen eller lokal build) till Albin. Godkänd ger snabbspolning in i `main`, Vercel bygger produktion.
- Live-kontroll mot en markör som bara finns i den nya versionen (t.ex. "Se. Lyssna. Spara." på `/sv/` och `#FDE5CB` i CSS:en), inte en text som även gamla sajten har (fällan från 1.3-lyftet).
- CLAUDE.md:s status uppdateras och pushas.

## 12. Utanför

Appens färger och 1.3.0-releasen (appen behåller "Mossa, rost & mässing"), nya foton, julis snedställda kort och hörnmarkeringar, handstil på listrubriker och FAQ-frågor, fler språk.

## 13. Avvikelser vid genomförandet (2026-09-28)

- `--dark-2` togs bort när karusellen blev persika; ingen annan del använde den.
- Telefonernas lokala tokens i `phone.css` är `--moss`, `--ink`, `--muted` och `--kick-color` (inte `--moss-2`/`--moss-deep`, som ingen telefonskärm använder). Karusellens telefoner får en kortare, varm skugga via `--ph-shadow` så att den ryms i karusellens nederkant.
- Karusellens kicker är rost (persikabandet är ljust); testet i `home.spec.ts` följer med.
- Artikelns hero har ingen riven kant: papperssidan ligger redan över heron med rundade hörn. Bloggens rubrikband har kanten i nederkant, och sidfotens kant följer sidan via `edgeColor` (espresso på startsidan, papper annars).
- Sektioner med riven kant får inte klippa överflöd (annars syns en söm vid 125/150 % skalning): `.final` har inte längre `overflow: hidden`, karusellen har `overflow-x: clip`. Fältbokens nedre linje togs bort eftersom den rivna kanten gör jobbet. Kanterna döljs i Windows högkontrastläge.
- Siffernoterna har luft till kolumnkanterna (`padding: 18px 6px`) och balanserade rader på mobil.
- Grep-kontrollen i §3.2 är en vakt: `npm run test:palette` (`website/scripts/check-palette.mjs`), som täcker `website/src/` och `website/tools/` (kod och `.svg`).
- Premiums marginalanteckning är aprikos som i de andra mörka partierna.
- Delningsbilden har `?v=2` i adressen så att sociala medier hämtar den nya bilden; App Store-märkets nästan svarta fyllning är nu `#000`.
