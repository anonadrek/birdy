# Birdy support (support@birdy.community)

Resend tar emot post till `support@birdy.community` och skickar webbhändelsen `email.received` till `api/inbound.mjs`. Funktionen vidarebefordrar meddelandet med en etikett i ämnet till inkorgen i `FORWARD_TO` och skickar ett kvitto i Birdys namn. Spec: `docs/superpowers/specs/2026-10-07-support-resend-design.md`.

## Miljövariabler (Vercel-projektet `birdy-support`)

| Namn | Värde |
|---|---|
| `RESEND_API_KEY` | Resend-nyckel med full åtkomst (läser mottagen post, listar och skickar) |
| `RESEND_WEBHOOK_SECRET` | `signing_secret` från webbhändelsen (`whsec_…`) |
| `SUPPORT_ADDRESS` | `support@birdy.community` |
| `FORWARD_TO` | Inkorgen som får meddelandena (aldrig i repot) |

## Test

```bash
npm ci && npm test
```

## Loggar

En JSON-rad per utfall i Vercel: `forwarded`, `forward-replayed` (409 `invalid_idempotent_request` på en omförsökt vidarebefordran, bekräftad mot `listSent()` innan den litas på), `forward-fallback` (en valideringsvärdig 4xx gav en minimal textversion i stället — aldrig för en konkurrerande idempotent förfrågan, 401/403, 429 eller 5xx, som alla är 500 så Resend/Svix försöker igen), `receipt-sent`/`no-receipt` (`automated`/`thread`/`unauthenticated`/`recent`, med `dkim`/`dmarc`-resultatsträngarna), `ignored`, `own-mail`, `bad-signature`, `forward-failed` (500), `receipt-failed`, `config`. Raderna har bara meddelandets id och den typen av fält (`error.name`/`statusCode`, `dkim`/`dmarc`), aldrig adresser, ämnen eller innehåll.

## Drift

- **Gmail blockerar `.apk`/`.exe`-bilagor** och studsar hela vidarebefordran om en sådan följer med — bilagan stannar ändå kvar i Resend i 30 dagar (se `Resend-id`-raden i den misslyckade vidarebefordringen, eller Resends egen logg).
- **Sätt ingen semesterautosvarare på `FORWARD_TO`-inkorgen för `support@birdy.community`-post.** Gmails autosvar går ut från Albins privata adress, inte från `support@birdy.community` — det bryter avsändarlöftet och kan starta en autosvar-mot-autosvar-loop med en avsändares egen frånvarosvarare.
- **Kontrollera att Vercel-projektet `birdy-support` inte har Vercel Authentication eller annat deployment protection på produktionsmiljön.** Är det på svarar `/api/inbound` med en inloggningssida i stället för att köra funktionen, och Resends webbhändelse ser ut att "fungera" (200) utan att något forwardas.
- **`vercel dev` och annan körning utan `NODE_ENV=production` loggar Resend-SDK:ts fullständiga felobjekt till konsolen** (`resend.ts`s egen `logError`, oberoende av vår `error.name`/`statusCode`-disciplin i `client.mjs`/`handler.mjs`) — kan innehålla adresser eller annat innehåll som orsakade felet. Vercels produktionsmiljö sätter `NODE_ENV=production` automatiskt, så detta gäller bara lokal felsökning, aldrig produktionsloggarna.
