# Play Store launch-research — 2026-05-15

Forskning utförd parallellt av fem agenter inför `v1.0.0`-launch (Plan 6b återstår). Varje fil är fristående med egen 200-ords-sammanfattning på toppen + djupanalys + källor.

## Filer

| # | Fil | Domän | Storlek |
|---|---|---|---|
| 01 | [Konkurrentanalys](./01-competitor-analysis.md) | Merlin, BirdNET, Picture Bird, Birda, Fågelguiden + nordiska aktörer; vita fläckar i marknaden | ~10 KB |
| 02 | [ASO + Play Store-listning](./02-aso-play-store-optimization.md) | Keywords, title, screenshots, featured graphic, data-safety, localization | ~33 KB |
| 03 | [Produkt-USP-djupdykning](./03-product-differentiation.md) | Vad är genuint unikt med Birdy (kodbas + Field Journal-estetik); USP-rangordning + Play Store-prioritering | ~14 KB |
| 04 | [Monetisering & Premium-strategi](./04-monetization-strategy.md) | Pricing, Billing v8, free vs premium, AB-bolag, EU-regulation, revenue-prognos | ~24 KB |
| 05 | [Marketing & launch-playbook](./05-marketing-launch-playbook.md) | Pre-launch → 90-dagars-roadmap, community-kanaler, PR, KPIs | ~38 KB |

## Snabba TL;DR per fil (för triage)

**01 — Konkurrent:** Merlin (Cornell, gratis) dominerar (10M downloads, 4.91/5). Picture Bird är "anti-modellen" med trial-traps. Birdys vita fläck: Field Journal-estetik + privacy-as-feature + svensk-native AI — ingen aktör äger detta.

**02 — ASO:** Byt title till `Birdy: Fågel-ID & Fältdagbok` (SV) / `Birdy: Bird ID & Field Journal` (EN). Befintliga screenshots saknar live-scan, match-result och fylld diary. Sverige är optimal soft-launch-marknad. Tier 2-lokalisering: DE + NL inom 4-8 veckor post-launch.

**03 — USP:** Top-3 differentiators i ordning: (1) Field Journal-estetik, (2) ärlig osäkerhetshantering (Match/Disambig/NoBird), (3) on-device + offline. Sälj INTE AI-precision (72% top-3 är inte konkurrenskraftigt mot Merlin's ~90%). Tagline (SV): "Identifiera fåglar med kameran. En fältdagbok som ser ut som fältdagböcker. Inga foton lämnar din telefon."

**04 — Monetisering:** Bumpa till **299 kr/år + 699 kr Lifetime**. **TA BORT "spara 60%"-stämpeln** — bryter EU Omnibus-direktivet + Google Play dark-pattern-policy. Använd **Billing v8, inte v6** (CLAUDE.md är outdated; v6 är deprecated 2026-08-31). Realistisk prognos år 1: ~85 000 kr brutto.

**05 — Marketing:** **Closed Testing-spåret måste startas idag** (Google kräver 14 dagars test innan production för nya konton). Soft-launch Sverige först, sedan Norden → DE/UK → global. Top-3 kanaler dag 1: Show HN (tis/tor 15:00 svensk tid, KMP-vinkeln), Reddit r/birding + r/SideProject, `Fåglar inpå knuten` Facebook-grupp (200k+). KPIs: D7-retention >12%, obs/aktiv/vecka >2, premium-conversion 3-5%.

## Tvärgående konvergens-punkter (där flera dokument är överens)

Flera agenter landade självständigt i samma slutsatser — dessa är de mest robusta rekommendationerna:

1. **Sluta sälja AI-precision; sälj förtroende** (03 + 01 + 02) — 72% top-3 är inte konkurrenskraftigt; "honest about uncertainty" är.
2. **Privacy-as-feature är ditt största USP mot Merlin** (02 + 03 + 04) — Merlin uploadar foton, Picture Bird kör via Glority-cloud; Birdy stannar på telefonen.
3. **Sverige först som soft-launch** (01 + 02 + 04 + 05) — Fågelguiden är enda starka konkurrent och har ingen AI; bygg recensioner här före global push.
4. **Ta bort "spara 60%"-stämpeln före launch** (04 + 02) — rejection-risk + EU-regulation.
5. **Audio är högsta-FOMO premium-feature** (04 + 03 + 01) — Listen-launchern lovar redan något du inte levererar; Plan 6b måste skicka audio.
6. **Stamp-mekaniken är retention-loop** (05 + 03) — push-notiser saknas till v1.5, så stamps är enda mekanik som drar tillbaka användaren dag-2+.

## Föreslagen läsordning för dig

1. **05** (Marketing) — för du har en tidskritisk Closed Testing-deadline. Läs först.
2. **03** (Produkt) — för att låsa USP-narrativet innan du skriver store-listing.
3. **02** (ASO) — för att applicera USP-narrativet på listningen.
4. **04** (Monetization) — för konkreta åtgärds-items till Plan 6b.
5. **01** (Konkurrent) — som referens när du skriver pitch eller pressrelease.
