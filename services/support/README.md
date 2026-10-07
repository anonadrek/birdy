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

En JSON-rad per utfall i Vercel: `forwarded`, `receipt-sent`, `no-receipt` (`automated`/`recent`), `ignored`, `own-mail`, `bad-signature`, `forward-failed` (500, Resend försöker igen), `receipt-failed`, `config`. Raderna har bara meddelandets id, aldrig adresser, ämnen eller innehåll.
