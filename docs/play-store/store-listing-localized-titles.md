# Birdy — Localized app title + short description (Play-length-safe)

> Play Console limits: **app title ≤ 30 chars**, **short description ≤ 80 chars** (per language).
> The **long description** (≤4000) almost never overflows in translation — only title + short are the problem field.
> Every row below verified ≤30 / ≤80 (counted as Unicode characters, 2026-06-18).
> These are length-compliant, keyword-aware drafts — fine-tune wording with a native term if one reads better, just keep within the limits.

## Length-safe pattern (for any language not in the table)
- **Title:** `Birdy: <short functional tag>` — keep "Birdy" + colon as the brand anchor and translate ONLY the tag (the bird-ID phrase). Budget ~22 chars for the tag. The title does NOT need a full sentence.
- **Short desc:** lead with "identify birds by photo & sound" + "offline, private"; drop the "guide & journal" tail if a language runs long. Keep ≤80.

## Per-language (verified ≤30 / ≤80)

| Lang | App title (≤30) | Short description (≤80) |
|------|-----------------|--------------------------|
| en | `Birdy: Bird ID & Field Guide` | `Identify birds by photo & sound. Offline, private field guide & journal.` |
| sv | `Birdy: Fågel-ID & Guide` | `Identifiera fåglar på foto & ljud. Offline, privat fågelguide & dagbok.` |
| no | `Birdy: Fugle-ID & Guide` | `Gjenkjenn fugler på foto & lyd. Offline, privat fugleguide & dagbok.` |
| da | `Birdy: Fugle-ID & Guide` | `Genkend fugle på foto & lyd. Offline, privat fuglebog & dagbog.` |
| fi | `Birdy: Lintutunnistus` | `Tunnista linnut kuvasta ja äänestä. Offline, yksityinen lintuopas.` |
| de | `Birdy: Vögel bestimmen` | `Vögel per Foto & Ruf erkennen. Offline, privat. Vogelführer & Tagebuch.` |
| nl | `Birdy: Vogels herkennen` | `Vogels herkennen op foto & geluid. Offline, privé gids & dagboek.` |
| fr | `Birdy: ID des oiseaux` | `Identifiez les oiseaux par photo et chant. Hors-ligne et privé.` |
| es | `Birdy: Identificar aves` | `Identifica aves por foto y sonido. Sin conexión, guía privada.` |
| it | `Birdy: ID uccelli & guida` | `Identifica uccelli con foto e suono. Offline, guida privata.` |
| pt | `Birdy: Identificar aves` | `Identifique aves por foto e som. Offline, guia privado.` |
| pl | `Birdy: Rozpoznaj ptaki` | `Rozpoznawaj ptaki po zdjęciu i dźwięku. Offline, prywatnie.` |

Need another language (Czech, Hungarian, Greek, Romanian, Turkish, Russian, …)? Ask and I'll add it, length-verified.
