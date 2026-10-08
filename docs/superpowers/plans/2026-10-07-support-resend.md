# Support via Resend: implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Post till `support@birdy.community` når Albins Gmail med en ämnesetikett och `Reply-To` till avsändaren, avsändaren får ett kvitto i Birdys namn, och ingenting sparas av Birdy.

**Architecture:** En Vercel-funktion (`services/support/api/inbound.mjs`, eget Vercel-projekt `birdy-support`) tar emot Resends webbhändelse `email.received`, kontrollerar signaturen, hämtar meddelandet och bilagorna från Resend, vidarebefordrar och skickar kvittot. All logik ligger i små rena moduler under `services/support/lib/` med en tunn adapter mot Resends SDK, så att allt testas med `node --test` utan nätverk.

**Tech Stack:** Node 22 (ESM, `.mjs`), `resend` 6.32.1 (SDK: `emails.receiving.get`, `emails.receiving.attachments.list`, `emails.list`, `emails.send(payload, { idempotencyKey })`, `webhooks.verify`), `node:test`, Vercel Functions (Web-signaturen `export async function POST(request)`).

**Spec:** `docs/superpowers/specs/2026-10-07-support-resend-design.md` (samma gren). **Gren:** `feature/support-resend` i worktree `C:/w/birdy-support`, från `main`. Sammanslagning till `main` gör huvudagenten.

---

## Filer

| Fil | Ansvar |
|---|---|
| `services/support/package.json` | Paketet: ESM, `resend` exakt version, `npm test` |
| `services/support/.gitignore` | `node_modules/` |
| `services/support/lib/address.mjs` | `addressOf(from)`: e-postadressen ur "Namn <adress>", gemener |
| `services/support/lib/labels.mjs` | `labelFor({ subject, text })`: `[App]`, `[Köp]`, `[Fel]`, `[Birdy]` |
| `services/support/lib/receipt.mjs` | `isAutomated`, `mailedRecently`, `parseTime`, `receiptMessage` |
| `services/support/lib/forward.mjs` | `buildForward(...)`: payloaden till `emails.send` |
| `services/support/lib/client.mjs` | `resendClient(resend)`: adapter mot SDK:t, kastar vid fel |
| `services/support/lib/handler.mjs` | `handleInbound(...)`: hela flödet, returnerar `{ status }` |
| `services/support/api/inbound.mjs` | Vercel-ingången |
| `services/support/vercel.json` | `maxDuration` |
| `services/support/README.md` | Uppsättning och drift |
| `services/support/test/*.test.mjs` | Tester per modul + `sign.mjs` (signerar testhändelser) |
| `.github/workflows/ci.yml` | Nytt jobb `support` |

---

### Task 1: Paketet, adresser och etiketter

**Files:**
- Create: `services/support/package.json`, `services/support/.gitignore`, `services/support/lib/address.mjs`, `services/support/lib/labels.mjs`
- Test: `services/support/test/labels.test.mjs`

- [ ] **Step 1: Paketet**

`services/support/package.json`:
```json
{
  "name": "birdy-support",
  "private": true,
  "type": "module",
  "engines": { "node": ">=22" },
  "scripts": { "test": "node --test test/" },
  "dependencies": { "resend": "6.32.1" }
}
```
`services/support/.gitignore`:
```
node_modules/
.vercel/
```
Run: `cd services/support && npm install` (skapar `package-lock.json`, committas).

- [ ] **Step 2: Skriv de fallerande testerna**

`services/support/test/labels.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { addressOf } from '../lib/address.mjs';
import { labelFor } from '../lib/labels.mjs';

test('addressOf takes the address out of a display name, lower case', () => {
  assert.equal(addressOf('Anna Andersson <Anna@Example.se>'), 'anna@example.se');
  assert.equal(addressOf(' anna@example.se '), 'anna@example.se');
});

test('the app feedback subject gets [App], in both of the app subject forms', () => {
  assert.equal(labelFor({ subject: 'Birdy v1.3.0 — feedback', text: '' }), '[App]');
  assert.equal(labelFor({ subject: 'Birdy v1.2.0-rc3 - feedback', text: 'Hej' }), '[App]');
});

test('purchase words give [Köp], in Swedish and English', () => {
  assert.equal(labelFor({ subject: 'Köpet syns inte', text: '' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Hello', text: 'I want a refund for Premium' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Livstid', text: '' }), '[Köp]');
});

test('bug words give [Fel], and an apostrophe of any kind matches', () => {
  assert.equal(labelFor({ subject: 'Appen kraschar', text: '' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Hi', text: 'The map doesn’t work' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Hi', text: 'It freezes on start' }), '[Fel]');
});

test('purchase wins over bug, and the app label comes first', () => {
  assert.equal(labelFor({ subject: 'Köpet kraschade appen', text: '' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Birdy v1.3.0 — feedback', text: 'Appen kraschar' }), '[App][Fel]');
});

test('words only match at the start of a word', () => {
  // "fel" inside "helfel" or "sköp" must not count; "felaktig" and "köpte" do.
  assert.equal(labelFor({ subject: 'Ett helfel', text: '' }), '[Birdy]');
  assert.equal(labelFor({ subject: 'Felaktig art', text: '' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Jag köpte Premium', text: '' }), '[Köp]');
});

test('nothing matching gives [Birdy]', () => {
  assert.equal(labelFor({ subject: 'Tack för appen', text: 'Fin fågelbok!' }), '[Birdy]');
  assert.equal(labelFor({}), '[Birdy]');
});
```

- [ ] **Step 3: Kör och se dem falla**

Run: `cd services/support && npm test`
Expected: FAIL, `Cannot find module '../lib/address.mjs'`.

- [ ] **Step 4: Implementera**

`services/support/lib/address.mjs`:
```js
/** The bare address in a From/To value ("Anna <anna@example.se>" -> "anna@example.se"), lower case. */
export function addressOf(value) {
  const text = String(value ?? '');
  const inAngles = text.match(/<([^>]+)>/);
  return (inAngles ? inAngles[1] : text).trim().toLowerCase();
}
```
`services/support/lib/labels.mjs`:
```js
// Subject labels (spec section 4): a source label for mail from the app's Feedback button and a
// topic label from plain word rules, Swedish and English. No model reads the messages.

// The app's settings_feedback_subject, "Birdy v%1$s — feedback" in both locales.
const APP_SUBJECT = /^birdy v\d[\w.-]*\s*[—–-]?\s*feedback\b/i;

const PURCHASE = ['köp', 'kvitto', 'återbetal', 'refund', 'premium', 'prenumeration', 'subscription', 'purchase', 'payment', 'betalning', 'livstid', 'lifetime'];
const BUG = ['krasch', 'fel', 'bugg', 'crash', 'bug', 'error', 'fungerar inte', "doesn't work", 'does not work', 'broken', 'hänger sig', 'freezes'];

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// A word counts at the start of a word: "köpte" matches "köp", "sköp" does not.
const startsAWord = (words) => new RegExp(`(?<![\\p{L}\\p{N}])(?:${words.map(escape).join('|')})`, 'iu');
const PURCHASE_RE = startsAWord(PURCHASE);
const BUG_RE = startsAWord(BUG);

const normalize = (s) => s.replace(/[’‘]/g, "'").toLowerCase();

/** "[App]", "[Köp]", "[Fel]", "[App][Fel]" or "[Birdy]". */
export function labelFor({ subject = '', text = '' } = {}) {
  const source = APP_SUBJECT.test(subject.trim()) ? '[App]' : '';
  const haystack = normalize(`${subject}\n${text}`);
  const topic = PURCHASE_RE.test(haystack) ? '[Köp]' : BUG_RE.test(haystack) ? '[Fel]' : '';
  return source || topic ? `${source}${topic}` : '[Birdy]';
}
```

- [ ] **Step 5: Kör testerna**

Run: `cd services/support && npm test`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add services/support/package.json services/support/package-lock.json services/support/.gitignore services/support/lib/address.mjs services/support/lib/labels.mjs services/support/test/labels.test.mjs
git commit -m "feat(support): paketet och ämnesetiketterna

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Kvittot

**Files:**
- Create: `services/support/lib/receipt.mjs`
- Test: `services/support/test/receipt.test.mjs`

- [ ] **Step 1: Skriv de fallerande testerna**

`services/support/test/receipt.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isAutomated, mailedRecently, parseTime, receiptMessage } from '../lib/receipt.mjs';

const SUPPORT = 'support@birdy.community';
const person = { from: 'Anna <anna@example.se>', headers: { 'Content-Type': 'text/plain' } };

test('a person writing is not automated', () => {
  assert.equal(isAutomated(person, SUPPORT), false);
  assert.equal(isAutomated({ ...person, headers: { 'Auto-Submitted': 'no' } }, SUPPORT), false);
  assert.equal(isAutomated({ ...person, headers: null }, SUPPORT), false);
});

test('automatic mail never gets a receipt (RFC 3834), whatever the header case', () => {
  for (const headers of [
    { 'Auto-Submitted': 'auto-replied' },
    { 'auto-submitted': 'auto-generated' },
    { Precedence: 'bulk' },
    { precedence: 'list' },
    { Precedence: 'junk' },
    { 'List-Id': '<birds.lists.example.org>' },
    { 'List-Unsubscribe': '<mailto:u@example.org>' },
  ]) {
    assert.equal(isAutomated({ ...person, headers }, SUPPORT), true, JSON.stringify(headers));
  }
});

test('system and no-reply senders, and Birdy itself, are automated', () => {
  for (const from of [
    'MAILER-DAEMON@mx.example.org',
    'postmaster@example.org',
    'noreply@example.org',
    'no-reply@example.org',
    'do-not-reply@example.org',
    'donotreply@example.org',
    'no-reply+abc@example.org',
    'Birdy <support@birdy.community>',
  ]) {
    assert.equal(isAutomated({ from, headers: {} }, SUPPORT), true, from);
  }
});

test("parseTime reads both of Resend's timestamp forms", () => {
  assert.equal(parseTime('2026-10-07T20:00:00.000Z'), Date.UTC(2026, 9, 7, 20));
  assert.equal(parseTime('2026-10-07 20:00:00.123456+00'), Date.UTC(2026, 9, 7, 20, 0, 0, 123));
});

test('mailedRecently looks at mail sent to the address in the last 24 hours', () => {
  const now = Date.UTC(2026, 9, 8, 12);
  const sent = [
    { to: ['Anna <anna@example.se>'], created_at: '2026-10-08T01:00:00.000Z' },
    { to: ['bo@example.se'], created_at: '2026-10-06T12:00:00.000Z' },
  ];
  assert.equal(mailedRecently(sent, 'anna@example.se', now), true);
  assert.equal(mailedRecently(sent, 'bo@example.se', now), false);
  assert.equal(mailedRecently(sent, 'cecilia@example.se', now), false);
  assert.equal(mailedRecently([], 'anna@example.se', now), false);
});

test("the receipt is in Birdy's name, Swedish first, and never names a person", () => {
  const r = receiptMessage('Appen kraschar');
  assert.equal(r.subject, 'Re: Appen kraschar');
  assert.match(r.text, /^Tack, ditt meddelande har kommit fram till Birdy\./);
  assert.match(r.text, /Thanks, your message reached Birdy\. We read every message and usually answer within a few days\./);
  assert.match(r.text, /\nBirdy\n/);
  assert.doesNotMatch(r.text, /Albin/);
  assert.equal(receiptMessage('Re: Fråga').subject, 'Re: Fråga');
  assert.equal(receiptMessage('').subject, 'Re: Birdy');
});
```

- [ ] **Step 2: Kör och se dem falla**

Run: `cd services/support && npm test`
Expected: FAIL, `Cannot find module '../lib/receipt.mjs'`.

- [ ] **Step 3: Implementera**

`services/support/lib/receipt.mjs`:
```js
// The receipt (spec section 5): never to automatic mail, at most one per sender a day, in Birdy's name.
import { addressOf } from './address.mjs';

const DAY_MS = 24 * 60 * 60 * 1000;
const AUTOMATED_LOCAL = /^(mailer-daemon|postmaster|no-?reply|do-?not-?reply|bounces?)([+._-].*)?$/i;

const lowerKeys = (headers) => Object.fromEntries(Object.entries(headers ?? {}).map(([k, v]) => [k.toLowerCase(), String(v)]));

/** True for mail a receipt must never answer (RFC 3834, system senders, Birdy's own address). */
export function isAutomated({ from, headers }, supportAddress) {
  const address = addressOf(from);
  if (address === supportAddress.toLowerCase()) return true;
  if (AUTOMATED_LOCAL.test(address.split('@')[0])) return true;
  const h = lowerKeys(headers);
  if ((h['auto-submitted'] ?? 'no').trim().toLowerCase() !== 'no') return true;
  if (/^(bulk|list|junk)$/i.test((h.precedence ?? '').trim())) return true;
  return 'list-id' in h || 'list-unsubscribe' in h;
}

/** Resend's timestamps, ISO ("…Z") or Postgres style ("2026-10-07 20:00:00.123456+00"). */
export function parseTime(value) {
  const iso = String(value).replace(' ', 'T').replace(/([+-]\d{2})$/, '$1:00').replace(/(\.\d{3})\d+/, '$1');
  return Date.parse(iso);
}

/** Whether Birdy (a receipt, or Albin's reply through Resend) mailed `address` in the last 24 hours. */
export function mailedRecently(sent, address, now) {
  return sent.some((email) => (email.to ?? []).some((to) => addressOf(to) === address) && now - parseTime(email.created_at) < DAY_MS);
}

/** The receipt's subject and plain text. */
export function receiptMessage(originalSubject) {
  const original = (originalSubject ?? '').trim();
  const subject = /^re:/i.test(original) ? original : `Re: ${original || 'Birdy'}`;
  const text = [
    'Tack, ditt meddelande har kommit fram till Birdy. Vi läser allt som kommer in och svarar oftast inom några dagar.',
    '',
    'Thanks, your message reached Birdy. We read every message and usually answer within a few days.',
    '',
    'Birdy',
    'https://birdy.community',
  ].join('\n');
  return { subject, text };
}
```

- [ ] **Step 4: Kör testerna**

Run: `cd services/support && npm test`
Expected: PASS (alla tester i båda filerna).

- [ ] **Step 5: Commit**

```bash
git add services/support/lib/receipt.mjs services/support/test/receipt.test.mjs
git commit -m "feat(support): kvittots regler och text i Birdys namn

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Vidarebefordran

**Files:**
- Create: `services/support/lib/forward.mjs`
- Test: `services/support/test/forward.test.mjs`

- [ ] **Step 1: Skriv de fallerande testerna**

`services/support/test/forward.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildForward, MAX_ATTACHMENT_BYTES } from '../lib/forward.mjs';

const email = {
  id: 'em_1',
  from: 'Anna <anna@example.se>',
  to: ['support@birdy.community'],
  reply_to: null,
  created_at: '2026-10-08T07:00:00.000Z',
  subject: 'Appen kraschar',
  text: 'Den kraschar när jag sparar.',
  html: '<p>Den kraschar när jag sparar.</p>',
};
const base = { email, attachments: [], label: '[Fel]', supportAddress: 'support@birdy.community', forwardTo: 'inbox@example.com' };

test('the forward comes from Birdy support, goes to the inbox and replies to the sender', () => {
  const f = buildForward(base);
  assert.equal(f.from, 'Birdy support <support@birdy.community>');
  assert.deepEqual(f.to, ['inbox@example.com']);
  assert.equal(f.replyTo, 'anna@example.se');
  assert.equal(f.subject, '[Fel] Appen kraschar');
});

test('the original Reply-To wins over From', () => {
  const f = buildForward({ ...base, email: { ...email, reply_to: ['Anna Privat <anna.p@example.se>'] } });
  assert.equal(f.replyTo, 'anna.p@example.se');
});

test('a short header goes first in text and html, then the message', () => {
  const f = buildForward(base);
  assert.match(f.text, /^Från: Anna <anna@example\.se>\nDatum: 2026-10-08T07:00:00\.000Z\nTill: support@birdy\.community\n\nDen kraschar/);
  assert.match(f.html, /Från: Anna &lt;anna@example\.se&gt;/);
  assert.match(f.html, /<p>Den kraschar när jag sparar\.<\/p>$/);
});

test('without text the html is used as text, without tags', () => {
  const f = buildForward({ ...base, email: { ...email, text: null } });
  assert.match(f.text, /\n\nDen kraschar när jag sparar\.$/);
  const g = buildForward({ ...base, email: { ...email, html: null } });
  assert.equal(g.html, undefined);
});

test('an empty subject still gets a label', () => {
  assert.equal(buildForward({ ...base, email: { ...email, subject: '' } }).subject, '[Fel] (inget ämne)');
});

test('attachments up to the limit go along by URL', () => {
  const attachments = [{ id: 'a1', filename: 'skärm.png', size: 1000, content_type: 'image/png', download_url: 'https://inbound-cdn.resend.com/a1' }];
  const f = buildForward({ ...base, attachments });
  assert.deepEqual(f.attachments, [{ filename: 'skärm.png', path: 'https://inbound-cdn.resend.com/a1', contentType: 'image/png' }]);
});

test('too large attachments stay in Resend and the header says so', () => {
  const attachments = [{ id: 'a1', filename: null, size: MAX_ATTACHMENT_BYTES + 1, content_type: 'video/mp4', download_url: 'https://x' }];
  const f = buildForward({ ...base, attachments });
  assert.equal(f.attachments, undefined);
  assert.match(f.text, /Bilagor \(1 st, 10\.0 MB\) skickas inte vidare; de finns kvar i Resend i 30 dagar \(em_1\)\./);
});
```

- [ ] **Step 2: Kör och se dem falla**

Run: `cd services/support && npm test`
Expected: FAIL, `Cannot find module '../lib/forward.mjs'`.

- [ ] **Step 3: Implementera**

`services/support/lib/forward.mjs`:
```js
// The forward to Albin's inbox (spec section 3.3.4): labelled subject, Reply-To the sender, a short header.
import { addressOf } from './address.mjs';

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const stripTags = (html) => html.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
const megabytes = (bytes) => (bytes / (1024 * 1024)).toFixed(1);

/** The payload for resend.emails.send. `attachments` are Resend's attachment records with download_url. */
export function buildForward({ email, attachments, label, supportAddress, forwardTo }) {
  const total = attachments.reduce((sum, a) => sum + a.size, 0);
  const fits = total <= MAX_ATTACHMENT_BYTES;
  const header = [`Från: ${email.from}`, `Datum: ${email.created_at}`, `Till: ${(email.to ?? []).join(', ')}`];
  if (attachments.length > 0 && !fits) {
    header.push(`Bilagor (${attachments.length} st, ${megabytes(total)} MB) skickas inte vidare; de finns kvar i Resend i 30 dagar (${email.id}).`);
  }
  const body = email.text ?? stripTags(email.html ?? '');
  return {
    from: `Birdy support <${supportAddress}>`,
    to: [forwardTo],
    replyTo: addressOf(email.reply_to?.[0] ?? email.from),
    subject: `${label} ${email.subject?.trim() || '(inget ämne)'}`,
    text: `${header.join('\n')}\n\n${body}`,
    html: email.html ? `<p style="color:#6E584B;font-size:13px">${header.map(escapeHtml).join('<br>')}</p><hr>${email.html}` : undefined,
    attachments:
      attachments.length > 0 && fits
        ? attachments.map((a) => ({ filename: a.filename ?? `bilaga-${a.id}`, path: a.download_url, contentType: a.content_type }))
        : undefined,
  };
}
```

- [ ] **Step 4: Kör testerna**

Run: `cd services/support && npm test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add services/support/lib/forward.mjs services/support/test/forward.test.mjs
git commit -m "feat(support): vidarebefordran med etikett, Reply-To och bilagor upp till 10 MB

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Adaptern och hanteraren

**Files:**
- Create: `services/support/lib/client.mjs`, `services/support/lib/handler.mjs`, `services/support/test/sign.mjs`
- Test: `services/support/test/handler.test.mjs`

- [ ] **Step 1: Signeringshjälpen för testerna**

`services/support/test/sign.mjs` (Resends SDK verifierar med paketet standardwebhooks: HMAC-SHA256 över `id.timestamp.payload` med den base64-avkodade hemligheten efter `whsec_`, tidsgräns 5 minuter):
```js
import { createHmac } from 'node:crypto';

export const SECRET = `whsec_${Buffer.from('birdy-support-test-secret-0123456').toString('base64')}`;

/** Headers for `payload` as Resend sends them; `timestamp` in seconds. */
export function sign(payload, { id = 'msg_1', timestamp = Math.floor(Date.now() / 1000), secret = SECRET } = {}) {
  const key = Buffer.from(secret.slice('whsec_'.length), 'base64');
  const signature = createHmac('sha256', key).update(`${id}.${timestamp}.${payload}`).digest('base64');
  return new Headers({ 'svix-id': id, 'svix-timestamp': String(timestamp), 'svix-signature': `v1,${signature}` });
}
```

- [ ] **Step 2: Skriv de fallerande testerna**

`services/support/test/handler.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Resend } from 'resend';
import { handleInbound } from '../lib/handler.mjs';
import { resendClient } from '../lib/client.mjs';
import { SECRET, sign } from './sign.mjs';

const env = { RESEND_WEBHOOK_SECRET: SECRET, SUPPORT_ADDRESS: 'support@birdy.community', FORWARD_TO: 'inbox@example.com' };
const NOW = Date.UTC(2026, 9, 8, 7, 5);
const realVerify = resendClient(new Resend('re_test')).verify;

const event = (data = {}) =>
  JSON.stringify({ type: 'email.received', created_at: '2026-10-08T07:00:01.000Z', data: { email_id: 'em_1', from: 'Anna <anna@example.se>', to: ['support@birdy.community'], subject: 'Appen kraschar', ...data } });

const email = {
  id: 'em_1', from: 'Anna <anna@example.se>', to: ['support@birdy.community'], reply_to: null,
  created_at: '2026-10-08T07:00:00.000Z', subject: 'Appen kraschar', text: 'Den kraschar.', html: null,
  headers: {}, message_id: '<m1@example.se>', attachments: [],
};

function fakeClient({ sent = [], failForward = false, failReceipt = false, mail = email } = {}) {
  const calls = { send: [], listAttachments: 0 };
  return {
    calls,
    verify: realVerify,
    getEmail: async (id) => (id === mail.id ? mail : Promise.reject(new Error('not found'))),
    listAttachments: async () => { calls.listAttachments += 1; return []; },
    listSent: async () => sent,
    send: async (payload, key) => {
      const isReceipt = key.startsWith('receipt-');
      if ((isReceipt && failReceipt) || (!isReceipt && failForward)) throw new Error('resend down');
      calls.send.push({ payload, key });
      return `sent_${calls.send.length}`;
    },
  };
}

const run = (client, body = event(), headers = sign(body), extra = {}) =>
  handleInbound({ rawBody: body, headers, env, client, now: NOW, log: () => {}, ...extra });

test('a bad signature is 401 and nothing is sent', async () => {
  const client = fakeClient();
  const body = event();
  const headers = sign(body, { secret: `whsec_${Buffer.from('another-secret-another-secret-0').toString('base64')}` });
  assert.deepEqual(await run(client, body, headers), { status: 401 });
  assert.equal(client.calls.send.length, 0);
});

test('an old timestamp is 401 (replay)', async () => {
  const client = fakeClient();
  const body = event();
  assert.deepEqual(await run(client, body, sign(body, { timestamp: Math.floor(Date.now() / 1000) - 3600 })), { status: 401 });
});

test('other events and other recipients are ignored with 200', async () => {
  const client = fakeClient();
  const other = JSON.stringify({ type: 'email.delivered', data: { email_id: 'x' } });
  assert.deepEqual(await run(client, other), { status: 200 });
  assert.deepEqual(await run(client, event({ to: ['hej@birdy.community'] })), { status: 200 });
  assert.equal(client.calls.send.length, 0);
});

test('mail from Birdy itself is not forwarded (no loops)', async () => {
  const client = fakeClient({ mail: { ...email, from: 'Birdy support <support@birdy.community>' } });
  assert.deepEqual(await run(client, event({ from: 'support@birdy.community' })), { status: 200 });
  assert.equal(client.calls.send.length, 0);
});

test('a message is forwarded with a label and gets one receipt, each with its idempotency key', async () => {
  const client = fakeClient();
  assert.deepEqual(await run(client), { status: 200 });
  const [forward, receipt] = client.calls.send;
  assert.equal(forward.key, 'forward-em_1');
  assert.equal(forward.payload.subject, '[Fel] Appen kraschar');
  assert.deepEqual(forward.payload.to, ['inbox@example.com']);
  assert.equal(receipt.key, 'receipt-em_1');
  assert.deepEqual(receipt.payload.to, ['anna@example.se']);
  assert.equal(receipt.payload.from, 'Birdy <support@birdy.community>');
  assert.equal(receipt.payload.headers['Auto-Submitted'], 'auto-replied');
  assert.equal(receipt.payload.headers['In-Reply-To'], '<m1@example.se>');
  assert.equal(client.calls.listAttachments, 0);
});

test('no receipt when Birdy mailed the sender in the last 24 hours', async () => {
  const client = fakeClient({ sent: [{ to: ['anna@example.se'], created_at: '2026-10-08T01:00:00.000Z' }] });
  assert.deepEqual(await run(client), { status: 200 });
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('no receipt to automatic mail', async () => {
  const client = fakeClient({ mail: { ...email, headers: { 'Auto-Submitted': 'auto-replied' } } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('a failed forward is 500 so Resend tries again; a failed receipt is still 200', async () => {
  assert.deepEqual(await run(fakeClient({ failForward: true })), { status: 500 });
  const client = fakeClient({ failReceipt: true });
  assert.deepEqual(await run(client), { status: 200 });
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('attachments are only fetched when the message has some', async () => {
  const client = fakeClient({ mail: { ...email, attachments: [{ id: 'a1', size: 10 }] } });
  await run(client);
  assert.equal(client.calls.listAttachments, 1);
});

test('a missing setting is 500 and logged, before anything is sent', async () => {
  const logs = [];
  const client = fakeClient();
  const body = event();
  const result = await handleInbound({ rawBody: body, headers: sign(body), env: { ...env, FORWARD_TO: '' }, client, now: NOW, log: (l) => logs.push(l) });
  assert.deepEqual(result, { status: 500 });
  assert.match(logs[0], /"outcome":"config"/);
  assert.equal(client.calls.send.length, 0);
});

test('logs carry the email id and outcome, never addresses or subjects', async () => {
  const logs = [];
  await run(fakeClient(), undefined, undefined, { log: (l) => logs.push(l) });
  assert.ok(logs.length >= 2);
  for (const line of logs) {
    assert.doesNotMatch(line, /anna|example\.se|kraschar/i);
    assert.match(line, /"id":"em_1"/);
  }
});
```

- [ ] **Step 3: Kör och se dem falla**

Run: `cd services/support && npm test`
Expected: FAIL, `Cannot find module '../lib/handler.mjs'`.

- [ ] **Step 4: Implementera adaptern**

`services/support/lib/client.mjs`:
```js
// The only file that knows Resend's SDK. Every call throws on an error, so the handler has one error path.

/** @param {import('resend').Resend} resend */
export function resendClient(resend) {
  const unwrap = (what) => ({ data, error }) => {
    if (error) throw new Error(`${what}: ${error.name ?? 'error'} ${error.message ?? ''}`.trim());
    return data;
  };
  return {
    verify: ({ payload, headers, secret }) => resend.webhooks.verify({ payload, headers, webhookSecret: secret }),
    getEmail: async (id) => unwrap('receiving.get')(await resend.emails.receiving.get(id)),
    listAttachments: async (id) => unwrap('receiving.attachments.list')(await resend.emails.receiving.attachments.list({ emailId: id })).data,
    listSent: async () => unwrap('emails.list')(await resend.emails.list({ limit: 100 })).data,
    send: async (payload, idempotencyKey) => unwrap('emails.send')(await resend.emails.send(payload, { idempotencyKey })).id,
  };
}
```

- [ ] **Step 5: Implementera hanteraren**

`services/support/lib/handler.mjs`:
```js
// The whole flow (spec section 3). Returns { status }; the Vercel entry turns it into a Response.
// Logs one JSON line per outcome with the email id, never addresses, subjects or content (spec section 6).
import { addressOf } from './address.mjs';
import { buildForward } from './forward.mjs';
import { labelFor } from './labels.mjs';
import { isAutomated, mailedRecently, receiptMessage } from './receipt.mjs';

const REQUIRED = ['RESEND_WEBHOOK_SECRET', 'SUPPORT_ADDRESS', 'FORWARD_TO'];

export async function handleInbound({ rawBody, headers, env, client, now = Date.now(), log = console.log }) {
  const say = (fields) => log(JSON.stringify(fields));
  const missing = REQUIRED.filter((name) => !env[name]);
  if (missing.length > 0) {
    say({ outcome: 'config', missing });
    return { status: 500 };
  }

  let event;
  try {
    event = client.verify({
      payload: rawBody,
      headers: { id: headers.get('svix-id'), timestamp: headers.get('svix-timestamp'), signature: headers.get('svix-signature') },
      secret: env.RESEND_WEBHOOK_SECRET,
    });
  } catch {
    say({ outcome: 'bad-signature' });
    return { status: 401 };
  }

  const id = event?.data?.email_id ?? null;
  const support = env.SUPPORT_ADDRESS.toLowerCase();
  if (event.type !== 'email.received' || !(event.data.to ?? []).some((to) => addressOf(to) === support)) {
    say({ outcome: 'ignored', id, type: event.type });
    return { status: 200 };
  }
  if (addressOf(event.data.from) === support) {
    say({ outcome: 'own-mail', id });
    return { status: 200 };
  }

  let email;
  try {
    email = await client.getEmail(id);
    const attachments = email.attachments?.length > 0 ? await client.listAttachments(id) : [];
    const label = labelFor({ subject: email.subject ?? '', text: email.text ?? '' });
    await client.send(buildForward({ email, attachments, label, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO }), `forward-${id}`);
    say({ outcome: 'forwarded', id, label, attachments: attachments.length });
  } catch (error) {
    say({ outcome: 'forward-failed', id, error: error.message });
    return { status: 500 };
  }

  try {
    const sender = addressOf(email.reply_to?.[0] ?? email.from);
    if (isAutomated(email, support)) {
      say({ outcome: 'no-receipt', id, reason: 'automated' });
    } else if (mailedRecently(await client.listSent(), sender, now)) {
      say({ outcome: 'no-receipt', id, reason: 'recent' });
    } else {
      const { subject, text } = receiptMessage(email.subject);
      const thread = email.message_id ? { 'In-Reply-To': email.message_id, References: email.message_id } : {};
      await client.send({ from: `Birdy <${env.SUPPORT_ADDRESS}>`, to: [sender], subject, text, headers: { 'Auto-Submitted': 'auto-replied', ...thread } }, `receipt-${id}`);
      say({ outcome: 'receipt-sent', id });
    }
  } catch (error) {
    say({ outcome: 'receipt-failed', id, error: error.message });
  }
  return { status: 200 };
}
```

- [ ] **Step 6: Kör testerna**

Run: `cd services/support && npm test`
Expected: PASS, alla tester. Om "an old timestamp is 401" passerar utan ändring bekräftar det att SDK:t (standardwebhooks) har en tidsgräns; ta inte bort testet. Faller "logs carry the email id" för att ett Resend-felmeddelande innehåller en adress: ta bort `error.message` ur loggen och logga bara `error.name`.

- [ ] **Step 7: Commit**

```bash
git add services/support/lib/client.mjs services/support/lib/handler.mjs services/support/test/sign.mjs services/support/test/handler.test.mjs
git commit -m "feat(support): hanteraren (signatur, vidarebefordran, kvitto, idempotens, loggar utan innehåll)

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Vercel-ingången, README och CI

**Files:**
- Create: `services/support/api/inbound.mjs`, `services/support/vercel.json`, `services/support/README.md`
- Modify: `.github/workflows/ci.yml` (nytt jobb)

- [ ] **Step 1: Ingången**

`services/support/api/inbound.mjs`:
```js
// Vercel Function for Resend's email.received webhook (spec 2026-10-07-support-resend-design.md).
import { Resend } from 'resend';
import { resendClient } from '../lib/client.mjs';
import { handleInbound } from '../lib/handler.mjs';

export async function POST(request) {
  const client = resendClient(new Resend(process.env.RESEND_API_KEY));
  const { status } = await handleInbound({ rawBody: await request.text(), headers: request.headers, env: process.env, client });
  return new Response(null, { status });
}
```
`services/support/vercel.json`:
```json
{
  "functions": { "api/inbound.mjs": { "maxDuration": 30 } }
}
```

- [ ] **Step 2: Röktest av ingången utan nätverk**

Skapa `services/support/test/entry.test.mjs`:
```js
import { test } from 'node:test';
import assert from 'node:assert/strict';

test('the Vercel entry answers an unsigned request with 401', async () => {
  process.env.RESEND_API_KEY = 're_test';
  process.env.RESEND_WEBHOOK_SECRET = `whsec_${Buffer.from('entry-test-secret-entry-test-0').toString('base64')}`;
  process.env.SUPPORT_ADDRESS = 'support@birdy.community';
  process.env.FORWARD_TO = 'inbox@example.com';
  const { POST } = await import('../api/inbound.mjs');
  const response = await POST(new Request('http://localhost/api/inbound', { method: 'POST', body: '{}' }));
  assert.equal(response.status, 401);
});
```
Run: `cd services/support && npm test`
Expected: PASS (alla filer).

- [ ] **Step 3: README**

`services/support/README.md`:
````markdown
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
````

- [ ] **Step 4: CI-jobbet**

Lägg till under `jobs:` i `.github/workflows/ci.yml` (samma indrag som de andra jobben; använd samma versioner av `actions/checkout` och `actions/setup-node` som resten av filen om de skiljer sig från nedan):
```yaml
  support:
    name: Support function (services/support)
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: services/support
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
      - run: npm ci
      - run: npm test
```

- [ ] **Step 5: Kör allt**

Run: `cd services/support && npm ci && npm test`
Expected: PASS.

- [ ] **Step 6: Commit och push**

```bash
git add services/support/api/inbound.mjs services/support/vercel.json services/support/README.md services/support/test/entry.test.mjs .github/workflows/ci.yml
git commit -m "feat(support): Vercel-ingången, README och CI-jobbet

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
git push
```

---

### Task 6: Driftsättning (kräver Albins Resend-konto; huvudagenten)

Delade resurser (DNS, Vercel, Resend): görs av huvudagenten. Albin har godkänt DNS-posterna och projektet (beslutssidan, spec avsnitt 7).

- [ ] **Step 1 (Albin):** skapar Resend-kontot, lägger till domänen `birdy.community` (EU-regionen om den går att välja), slår på mottagning för domänen (Domains → birdy.community → Receiving) och skapar en API-nyckel med full åtkomst, som han lämnar till agenten eller lägger direkt i Vercel.
- [ ] **Step 2: DNS.** Läs domänens poster ur Resend (`GET https://api.resend.com/domains` och `GET https://api.resend.com/domains/{id}` med nyckeln; fälten `records[]` med `record`, `name`, `type`, `value`, `priority`) och lägg in dem i Vercel (`POST https://api.vercel.com/v2/domains/birdy.community/records?slug=albtab`, token ur `~/AppData/Roaming/com.vercel.cli/Data/auth.json`; body `{"type","name","value","mxPriority","ttl":3600}`, aldrig token i loggar). Lägg till `_dmarc` TXT `v=DMARC1; p=none;` om den saknas. Kontrollera med `nslookup -type=mx birdy.community 8.8.8.8` och vänta tills Resend visar domänen som verifierad för sändning och mottagning.
- [ ] **Step 3: Vercel-projektet.** `POST https://api.vercel.com/v11/projects?slug=albtab` med `{"name":"birdy-support","rootDirectory":"services/support","framework":null,"gitRepository":{"type":"github","repo":"anonadrek/birdy"},"commandForIgnoringBuildStep":"git diff --quiet HEAD^ HEAD -- ."}`. Miljövariablerna (Production) via `POST /v10/projects/birdy-support/env?slug=albtab`: `RESEND_API_KEY`, `SUPPORT_ADDRESS`, `FORWARD_TO` (Albins Gmail), `RESEND_WEBHOOK_SECRET` (steg 4). Produktionsadressen blir `https://birdy-support.vercel.app`.
- [ ] **Step 4: Webbhändelsen.** Med SDK:t eller `POST https://api.resend.com/webhooks` `{"endpoint":"https://birdy-support.vercel.app/api/inbound","events":["email.received"]}` → `signing_secret` in som `RESEND_WEBHOOK_SECRET` i Vercel, sedan en ny produktionsdeploy (en commit i `services/support/` eller en redeploy i Vercel).
- [ ] **Step 5 (Albin, agenten ger värdena): Gmail "Skicka som".** Inställningar → Konton och import → Skicka e-post som → Lägg till: namn `Birdy`, adress `support@birdy.community`, SMTP `smtp.resend.com`, port 465 (SSL), användarnamn `resend`, lösenord = en Resend-nyckel med bara sändningsrätt för `birdy.community`. Gmails bekräftelsekod kommer via den nya vägen.

---

### Task 7: Hela vägen (grinden före adressbytet)

- [ ] **Step 1:** Albin skickar från sin Gmail till `support@birdy.community` med ämnet "Test köp". Förväntat i Gmail: `[Köp] Test köp` från `Birdy support`, `Reply-To` = hans adress; ett kvitto i Birdys namn till honom; Vercel-loggen `forwarded` + `receipt-sent`.
- [ ] **Step 2:** Ett andra mejl samma dygn från samma adress: vidarebefordras, inget nytt kvitto (`no-receipt`, `recent`).
- [ ] **Step 3:** Ett mejl från en annan adress med en bilaga under 10 MB: bilagan följer med.
- [ ] **Step 4:** Albin svarar från Gmail som `support@birdy.community`; mottagaren ser `support@birdy.community`, och "Visa original" visar SPF och DKIM `PASS`.
- [ ] **Step 5:** Skriv resultatet (datum och utfall) i `services/support/README.md` och committa. Grön senast måndag 12 oktober → Task 8 i 1.3.0; annars Task 8 för webben och butikssidan nu och för appen i 1.3.1.

---

### Task 8: Adressbytet och integritetspolicyn

**Files (`release/1.3.0`, worktree `C:/w/birdy-130`):**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt:171` (`openMailto("albin@abrahamssons.se", …)` → `openMailto("support@birdy.community", …)`)
- Modify: `docs/play-store/privacy-policy.md`, `docs/play-store/terms.md`, `docs/play-store/store-listing-en.md`, `docs/play-store/store-listing-sv.md`, `docs/play-store/closed-testing-tester-instructions.md`, `docs/play-store/growth/press-kit.md`
**Files (`main`):**
- Modify: `website/src/lib/links.ts:11` (`CONTACT_EMAIL`)

- [ ] **Step 1:** Byt adressen överallt i listan ovan (`git grep -n "albin@abrahamssons.se"`; träffar i gamla planer och forskningsdokument lämnas som historik).
- [ ] **Step 2:** Integritetspolicyn (`docs/play-store/privacy-policy.md`), nytt stycke före "Questions?":
```markdown
## Contacting us

If you email support@birdy.community, the message is received by our email
provider Resend and forwarded to us. We use it only to answer you and keep it
as long as your question needs; Resend deletes its copy after 30 days. The app
itself sends nothing: writing to us is always your choice.
```
och "Last updated" till dagens datum.
- [ ] **Step 3:** Grind på `release/1.3.0` (huvudagenten): `--no-configuration-cache`-grinden enligt CLAUDE.md; på `main`: webbens vakter (`test:no-dashes`, `test:i18n`, `build`).
- [ ] **Step 4:** Play Console → Butiksnärvaro → kontaktuppgifter: e-post `support@birdy.community` (Albin, eller agenten via Chrome med hans godkännande).
- [ ] **Step 5:** Pending follow-up 2 i `CLAUDE.md` markeras klar.
