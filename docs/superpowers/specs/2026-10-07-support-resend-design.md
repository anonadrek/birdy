# Support via Resend: support@birdy.community

**Datum:** 2026-10-07. **Status:** designen godkänd av Albin 2026-10-07 (valen nedan), specen väntar på hans genomläsning.
**Underlag:** Albins önskemål 2026-10-07 kväll ("ta emot och automatisera kundtjänst med Resend"), beslutssidan https://claude.ai/artifact/QL5zLVdRhEhnYsjC813N9o, Pending follow-up 2 i `CLAUDE.md`.

## 1. Mål

Alla som skriver till Birdy gör det på en adress, `support@birdy.community`. Varje meddelande hamnar i Albins inkorg med en ämnesetikett, avsändaren får ett kort kvitto i Birdys namn, och Albin svarar från Gmail som `support@birdy.community` i stället för från sin privata adress. Birdy sparar ingenting själv.

## 2. Albins val (2026-10-07)

| # | Fråga | Val |
|---|---|---|
| 1 | Adress | `support@birdy.community` |
| 2 | Kvitto till avsändaren | Ja, kort på svenska och engelska, högst ett per avsändare och dygn, aldrig till automatisk post. **I Birdys namn ("vi på Birdy"), aldrig Albins** (Albins tillägg). |
| 3 | Sortering | En etikett i ämnesraden ur enkla ordregler. Ingen AI (meddelandena skickas inte till någon modell). |
| 4 | Var koden körs | Ett eget litet Vercel-projekt i samma repo, `services/support/`. Webbplatsens bygge rörs inte. |
| 5 | Adressen i appen 1.3.0 | Byts i 1.3.0 bara om ett testmeddelande gått hela vägen senast måndag 12 oktober; annars allt i 1.3.1. |
| 6 | Inkorg | Albins Gmail (adressen ligger som miljövariabel i Vercel, aldrig i repot). |

## 3. Flödet

1. Någon skickar till `support@birdy.community` (appens Feedback-knapp, webben, butikssidan, juridiktexterna).
2. **Resend** tar emot posten via en MX-post på `birdy.community` (domänen har inga MX-poster i dag, DNS hos Vercel, så inget annat påverkas) och skickar webbhändelsen `email.received` till funktionen. Händelsen innehåller bara metadata (avsändare, mottagare, ämne, `email_id`), inte innehållet.
3. **Funktionen** `services/support/api/inbound` (Node, Vercel Functions):
   1. kontrollerar signaturen (Svix-huvudena `svix-id`, `svix-timestamp`, `svix-signature` med `RESEND_WEBHOOK_SECRET`); fel signatur ger 401 och ingenting skickas;
   2. struntar i allt som inte är `email.received` till `SUPPORT_ADDRESS` — i To, Cc, Bcc **eller** ett alias/list (`received_for`), vilken av de fyra som helst räknas (svarar 200);
   3. hämtar meddelandet (text, html, huvuden, bilagor) från Resends API med `email_id` (html hämtas med `html_format: 'cid'` så inline-bilder refereras som `cid:` i stället för att dubbleras som base64 i html);
   4. sätter etiketten (avsnitt 4) och vidarebefordrar till `FORWARD_TO`: från `Birdy support <support@birdy.community>`, ämne `[Etikett] ursprungligt ämne`, `Reply-To` = den ursprungliga avsändaren, ett kort huvud överst (Från, Datum, Till, **Resend-id** — alltid med, så meddelandet går att hitta i Resend även om avsändare eller ämne trasas av en mejlklient) och sedan meddelandet; bilagor följer med upp till 10 MB totalt (max 100 hämtade per meddelande; finns fler kvar i Resend noteras det i huvudet), annars en rad om att de finns kvar i Resend i 30 dagar; misslyckas hela vidarebefordran med ett valideringsfel (4xx, t.ex. en trasig bilaga) skickas en minimal textversion utan bilagor och utan Reply-To i stället, under sin egen idempotensnyckel;
   5. skickar kvittot (avsnitt 5) om avsändaren ska ha ett.
4. Albin svarar i Gmail med "Skicka som" `support@birdy.community` via `smtp.resend.com`. Svaret går direkt till avsändaren tack vare `Reply-To`.

**Dubbletter:** Svix försöker igen när funktionen svarar fel. Varje sändning har en idempotensnyckel i Resend (`forward-<email_id>`, `receipt-<email_id>`, giltig 24 h), så ett nytt försök skickar aldrig samma vidarebefordran eller kvitto två gånger. Resend hämtar en ny `download_url` för bilagor vid varje listning, så ett omförsök kan skicka en annan payload under samma nyckel och få ett 409 `invalid_idempotent_request` tillbaka — det räknas som redan skickat, inte som ett fel. Misslyckas vidarebefordran på riktigt svarar funktionen 500 så att Svix försöker igen; misslyckas bara kvittot loggas det och funktionen svarar 200 (vidarebefordran är det viktiga).

## 4. Etiketter

Reglerna läser ämne och brödtext, gemener, svenska och engelska. Ett meddelande kan få en källetikett och en ämnesetikett.

| Etikett | När |
|---|---|
| `[App]` | ämnet börjar som appens feedbackämne (`settings_feedback_subject`, "Birdy v{version} … feedback", båda språken) |
| `[Köp]` | köp, kvitto, återbetal, refund, premium, prenumeration, subscription, purchase, payment, betalning, livstid, lifetime |
| `[Fel]` | krasch, kraschar, fel, bugg, crash, bug, error, fungerar inte, doesn't work, does not work, broken, hänger sig, freezes |
| `[Birdy]` | inget av ovan |

Ordning när flera matchar: `[Köp]` före `[Fel]`. Exempel: `[App][Fel] Birdy v1.3.0 feedback`. Gmail-filter kan sedan sätta Albins egna etiketter.

## 5. Kvittot

- **Text** (svenska först, sedan engelska, i Birdys namn): "Tack, ditt meddelande har kommit fram till Birdy. Vi läser allt som kommer in och svarar oftast inom några dagar. / Thanks, your message reached Birdy. We read every message and usually answer within a few days." Signatur: "Birdy". Ämne: `Re: <ursprungligt ämne>`. Inga löften om tid utöver "oftast".
- **Bara till avsändarens From-adress, aldrig Reply-To** (skydd mot backscatter): en förfalskad From/Reply-To ska inte kunna få Birdy att skicka kvitton till tredje part. Reply-To styr fortfarande vidarebefordran till Albin, bara kvittot är From-only.
- **Bara när avsändaren är autentiserad:** inget kvitto om meddelandet inte har `dkim: pass` eller `dmarc: pass` (Resends `authentication`-fält på meddelandet; finns i API-svaret men inte i SDK:ns 6.32.1-typer). SPF ensamt räcker inte; `dmarc` ensamt krävs inte heller, eftersom Resend rapporterar `gray` för avsändare med `p=none` (t.ex. gmail.com) snarare än `pass`. Utan autentisering loggas `no-receipt`/`unauthenticated`.
- **Bara när** avsändaren inte är automatisk: inget kvitto om meddelandet har `Auto-Submitted` (annat än `no`), `Precedence: bulk|list|junk`, `List-Id` eller `List-Unsubscribe`, om avsändaren är `mailer-daemon`, `postmaster`, `noreply`/`no-reply`/`do-not-reply` eller `support@birdy.community` själv (RFC 3834). Kvittot har själv `Auto-Submitted: auto-replied`, så att en annan autosvarare inte svarar på det.
- **Bara när tråden är ny:** har det inkommande meddelandet redan ett `In-Reply-To`-huvud (avsändaren svarar i en pågående tråd) skickas inget nytt kvitto — avsändaren vet redan att Birdy har mejlet.
- **Högst ett per avsändare och dygn:** före sändning frågar funktionen Resends lista över skickad post om ett kvitto gått till samma adress de senaste 24 timmarna. Post till `FORWARD_TO` (själva vidarebefordran till Albin) räknas aldrig som ett kvitto i den kontrollen — annars skulle en avsändare som testar från samma inkorg som `FORWARD_TO` (Albin själv) alltid se ut att redan ha fått ett kvitto. Ingen egen databas; Resend har redan posten i 30 dagar.

## 6. Integritet

- Funktionen sparar ingenting och loggar bara `email_id`, etiketten och utfallet, aldrig adresser, ämnen eller innehåll.
- Resend håller mottagen och skickad post i 30 dagar (gratisplanen). Välj EU-regionen för domänen i Resend om den finns för mottagning.
- Integritetspolicyn får ett stycke "Kontakta oss": post till `support@birdy.community` tas emot av vår e-postleverantör Resend och vidarebefordras till oss; vi använder den bara för att svara och sparar den så länge ärendet kräver; Resend raderar sin kopia efter 30 dagar. Appens integritetslöfte ändras inte: appen skickar ingenting, det är användaren som skriver ett mejl.

## 7. Konton, nycklar och DNS

- **Albin:** skapar ett Resend-konto (gratis: 3 000 mejl i månaden, 100 om dagen) och lägger till domänen `birdy.community`; slår på Gmails "Skicka som" (värdena kommer från agenten).
- **Agenten** (med Albins godkännande, enligt beslutssidan): lägger in DNS-posterna som Resend visar via Vercels API (MX på roten för mottagning, SPF/MX på `send.birdy.community` och DKIM `resend._domainkey` för sändning, samt `_dmarc` med `p=none` om den saknas); skapar Vercel-projektet `birdy-support` (team albtab, rotkatalog `services/support`, bygger bara när `services/support/` ändras) och miljövariablerna `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`, `FORWARD_TO`, `SUPPORT_ADDRESS`; registrerar webbhändelsen i Resend. **Nyckelns behörighet:** Resend har bara två nivåer, `full_access` och `sending_access` — det finns ingen mellannivå som kan läsa mottagen post men inte allt annat. Att läsa mottagen post (`emails.receiving.get`/`attachments.list`) kräver `full_access`, så nyckeln måste ha den (README:ns beskrivning av nyckeln är korrekt; denna mening ersätter en tidigare version som antydde en begränsad kombinationsnivå).

## 8. Test

- **Enhetstester** (`node --test`): etikettreglerna (svenska, engelska, appens ämne i båda språken, flera träffar, "fel" matchar inte engelska felt/fell/fellow), kvittoreglerna (varje huvud i avsnitt 5, noreply-varianter, eget utskick, autentisering dkim/dmarc, pågående tråd via In-Reply-To), signaturkontrollen (rätt, fel, för gammal tidsstämpel), hanteraren med en fejkad Resend-klient (Cc/Bcc/received_for, idempotensnycklar, 409-omspel räknas som skickat, 4xx-fallback, 500 när vidarebefordran misslyckas, 200 när bara kvittot misslyckas, 24 h-regeln som bortser från `FORWARD_TO`), adaptern mot en fejkad Resend-SDK (`error.name`/`statusCode` utan `message`, `html_format: 'cid'`, `limit: 100` + `has_more`).
- **Hela vägen** innan något i appen eller texterna byts: ett mejl från Albins Gmail och ett från en annan adress når Gmail med rätt etikett och `Reply-To`, kvittot kommer en gång (ett andra mejl samma dygn ger inget nytt kvitto), Albins svar via "Skicka som" kommer fram från `support@birdy.community` och klarar SPF/DKIM (Gmails "Visa original").

## 9. Adressbytet (först när testet i 8 är grönt)

Gemensamt i en commit per gren: appens Feedback-knapp (`SettingsScreen.kt`, nu `albin@abrahamssons.se`), integritetspolicyn, villkoren, butikstexterna SV/EN (`docs/play-store/`), presskitet, `website/src/lib/links.ts` (`CONTACT_EMAIL`), testarinstruktionerna och Play Consoles kontaktadress (Albin eller agenten via Chrome med hans godkännande). Senast måndag 12 oktober för att hinna med vC130; annars i 1.3.1 för appen, medan webben och butikssidan kan bytas direkt.

## 10. Utanför

AI-sortering och svarsförslag, ärendesystem, automatiska svar utöver kvittot, flera inkorgar. Kan läggas till senare utan att ändra flödet.
