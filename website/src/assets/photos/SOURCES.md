# Bildkällor

Fotona är appens egna planschfoton (`asset-pack/src/main/assets/images/<QID>/hero.webp`), nedskalade till 1600 px. Metadata finns i `shared/content/species/**/<QID>.yaml`. Alla är CC0 eller public domain, så ingen namngivning krävs, men källan ska stå här för varje bild som läggs till (även bilder till blogginlägg).

| Fil | Art | QID | Fotograf | Licens | Används i |
|---|---|---|---|---|---|
| `talgoxe-q25485.webp` | Talgoxe | Q25485 | Hobbyfotowiki | CC0 | startsidans plansch när ingen artsida har ett fritt foto (bara testbygget utan arter) |
| `ladusvala-q25429.webp` | Ladusvala | Q25429 | Аимаина хикари | CC0 | Fältboken (planschen) |
| `skaggmes-q192817.webp` | Skäggmes | Q192817 | Hobbyfotowiki | CC0 | Ta med Birdy ut i fält |
| `rodhake-q25334.webp` | Rödhake | Q25334 | Rob Hille | Public domain | blogginlägget "Varför Birdy finns" (inläggets foto, korten och delningsbilden); delningsbilderna `public/og-field-{sv,en}.jpg` (tools/generate-og.mjs) |

Startsidans plansch (Dagens fågel) visar artsidans eget foto helt, utan något ritat över det, och får därför visa foton under CC0, public domain, CC BY och CC BY-SA. Korten Fåglarna i månaden beskär fotot och visar därför bara CC0, public domain och CC BY, aldrig CC BY-SA (Albins fotoregler, `src/lib/daily-bird.mjs`). Fotograf, licens (länkad) och källa står under planschen och korten med samma rad som på artsidan (`src/components/species/PhotoCredit.astro`). Den AI-genererade rödhaken som heron byggde på före 2026-10-08 är borttagen.

Birdy-fågeln (`public/brand/birdy-bird.png`, `public/coverage/seal-bird.png`, favikonerna) och appikonen står i `docs/legal/image-sources.md`, som listar alla bilder i repot.
