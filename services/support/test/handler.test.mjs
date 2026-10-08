import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Resend } from 'resend';
import { handleInbound } from '../lib/handler.mjs';
import { resendClient } from '../lib/client.mjs';
import { SECRET, sign } from './sign.mjs';

const env = { RESEND_API_KEY: 're_test', RESEND_WEBHOOK_SECRET: SECRET, SUPPORT_ADDRESS: 'support@birdy.community', FORWARD_TO: 'inbox@example.com' };
const NOW = Date.UTC(2026, 9, 8, 7, 5);
const realVerify = resendClient(new Resend('re_test')).verify;

const event = (data = {}) =>
  JSON.stringify({ type: 'email.received', created_at: '2026-10-08T07:00:01.000Z', data: { email_id: 'em_1', from: 'Anna <anna@example.se>', to: ['support@birdy.community'], subject: 'Appen kraschar', ...data } });

const email = {
  id: 'em_1', from: 'Anna <anna@example.se>', to: ['support@birdy.community'], reply_to: null,
  created_at: '2026-10-08T07:00:00.000Z', subject: 'Appen kraschar', text: 'Den kraschar.', html: null,
  headers: {}, message_id: '<m1@example.se>', attachments: [],
  // DKIM/DMARC pass: a normal, authenticated sender. Not an SDK 6.32.1 type, but present at runtime.
  authentication: { dkim: 'pass', spf: 'pass', dmarc: 'pass' },
};

function fakeClient({ sent = [], failForward = false, failReceipt = false, mail = email } = {}) {
  const calls = { send: [], listAttachments: 0 };
  return {
    calls,
    verify: realVerify,
    getEmail: async (id) => (id === mail.id ? mail : Promise.reject(new Error('not found'))),
    listAttachments: async () => { calls.listAttachments += 1; return { attachments: [], hasMore: false }; },
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

test('support@ in Cc, Bcc, or received_for (alias/list delivery) also forwards, not just To', async () => {
  const client = fakeClient();
  await run(client, event({ to: ['other@example.se'], cc: ['support@birdy.community'] }));
  assert.equal(client.calls.send[0]?.key, 'forward-em_1');
});

test('support@ in Bcc also forwards', async () => {
  const client = fakeClient();
  await run(client, event({ to: ['other@example.se'], bcc: ['support@birdy.community'] }));
  assert.equal(client.calls.send[0]?.key, 'forward-em_1');
});

test('support@ in received_for also forwards', async () => {
  const client = fakeClient();
  await run(client, event({ to: ['other@example.se'], received_for: ['support@birdy.community'] }));
  assert.equal(client.calls.send[0]?.key, 'forward-em_1');
});

test('an event with no data object at all is ignored with 200, not thrown', async () => {
  const client = fakeClient();
  const body = JSON.stringify({ type: 'email.received', created_at: '2026-10-08T07:00:01.000Z' });
  assert.deepEqual(await run(client, body), { status: 200 });
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
  // The base fixture's authentication has dmarc: 'pass', so the receipt may echo the real subject.
  assert.equal(receipt.payload.subject, 'Re: Appen kraschar');
  assert.equal(receipt.payload.headers['Auto-Submitted'], 'auto-replied');
  assert.equal(receipt.payload.headers['In-Reply-To'], '<m1@example.se>');
  assert.equal(client.calls.listAttachments, 0);
});

test('the receipt goes only to From, never to Reply-To (anti-backscatter)', async () => {
  // A forged or merely different Reply-To must not redirect the receipt — only the forward does that.
  const client = fakeClient({ mail: { ...email, reply_to: ['Attacker <attacker@evil.example>'] } });
  await run(client);
  const receipt = client.calls.send.find((c) => c.key === 'receipt-em_1');
  assert.deepEqual(receipt.payload.to, ['anna@example.se']);
});

test('no receipt without DKIM or DMARC pass (anti-backscatter), logged as unauthenticated', async () => {
  const client = fakeClient({ mail: { ...email, authentication: { dkim: 'fail', dmarc: 'fail' } } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('no receipt when the authentication field is missing entirely', async () => {
  const client = fakeClient({ mail: { ...email, authentication: undefined } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('a receipt is sent when DKIM passes even if DMARC is only gray (p=none, e.g. gmail.com)', async () => {
  const client = fakeClient({ mail: { ...email, authentication: { dkim: 'pass', dmarc: 'gray' } } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1', 'receipt-em_1']);
});

test('a receipt is sent when DMARC passes even if DKIM does not', async () => {
  const client = fakeClient({ mail: { ...email, authentication: { dkim: 'fail', dmarc: 'pass' } } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1', 'receipt-em_1']);
});

test('SPF alone is not enough for a receipt', async () => {
  const client = fakeClient({ mail: { ...email, authentication: { spf: 'pass', dkim: 'fail', dmarc: 'fail' } } });
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('the receipt subject echoes the original only when DMARC passes; otherwise a fixed "Re: Birdy" (DKIM alone is enough to send one, but not to echo the subject)', async () => {
  const client = fakeClient({ mail: { ...email, authentication: { dkim: 'pass', dmarc: 'gray' } } });
  await run(client);
  const receipt = client.calls.send.find((c) => c.key === 'receipt-em_1');
  assert.equal(receipt.payload.subject, 'Re: Birdy');
});

test('receipt log lines carry the dkim and dmarc result strings, which are not personal data', async () => {
  const logs = [];
  await run(fakeClient(), undefined, undefined, { log: (l) => logs.push(l) });
  const receiptLine = logs.find((l) => l.includes('receipt-sent'));
  assert.match(receiptLine, /"dkim":"pass"/);
  assert.match(receiptLine, /"dmarc":"pass"/);

  const unauthClient = fakeClient({ mail: { ...email, authentication: { dkim: 'fail', dmarc: 'fail' } } });
  const unauthLogs = [];
  await run(unauthClient, undefined, undefined, { log: (l) => unauthLogs.push(l) });
  const noReceiptLine = unauthLogs.find((l) => l.includes('no-receipt'));
  assert.match(noReceiptLine, /"dkim":"fail"/);
  assert.match(noReceiptLine, /"dmarc":"fail"/);
});

test('no receipt when Birdy mailed the sender in the last 24 hours', async () => {
  const client = fakeClient({ sent: [{ to: ['anna@example.se'], created_at: '2026-10-08T01:00:00.000Z' }] });
  assert.deepEqual(await run(client), { status: 200 });
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-em_1']);
});

test('no receipt when the incoming mail already has an In-Reply-To header (an ongoing thread)', async () => {
  const client = fakeClient({ mail: { ...email, headers: { 'In-Reply-To': '<previous@example.se>' } } });
  await run(client);
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

test('a replayed forward (409 invalid_idempotent_request), CONFIRMED via listSent (a matching forward already reached FORWARD_TO), counts as forwarded and continues to the receipt', async () => {
  const client = fakeClient({ sent: [{ to: ['inbox@example.com'], subject: '[Fel] Appen kraschar', created_at: '2026-10-08T07:00:01.000Z' }] });
  let calls = 0;
  client.send = async (payload, key) => {
    calls += 1;
    if (key.startsWith('forward-') && calls === 1) {
      const err = new Error('conflict');
      err.name = 'invalid_idempotent_request';
      err.statusCode = 409;
      throw err;
    }
    client.calls.send.push({ payload, key });
    return `sent_${client.calls.send.length}`;
  };
  const logs = [];
  const result = await run(client, undefined, undefined, { log: (l) => logs.push(l) });
  assert.deepEqual(result, { status: 200 });
  assert.ok(logs.some((l) => l.includes('forward-replayed')));
  assert.deepEqual(client.calls.send.map((c) => c.key), ['receipt-em_1']);
});

test('a 409 invalid_idempotent_request NOT confirmed by listSent (Resend may bind the key on a failed attempt, undocumented) sends the fallback instead of trusting it blindly', async () => {
  const client = fakeClient(); // sent: [] — no record that the forward ever reached FORWARD_TO
  client.send = async (payload, key) => {
    if (key === 'forward-em_1') {
      const err = new Error('conflict');
      err.name = 'invalid_idempotent_request';
      err.statusCode = 409;
      throw err;
    }
    client.calls.send.push({ payload, key });
    return `sent_${client.calls.send.length}`;
  };
  const logs = [];
  const result = await run(client, undefined, undefined, { log: (l) => logs.push(l) });
  assert.deepEqual(result, { status: 200 });
  assert.ok(logs.some((l) => l.includes('forward-fallback')));
  assert.equal(logs.some((l) => l.includes('forward-replayed')), false);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-fallback-em_1', 'receipt-em_1']);
});

test('a 409 invalid_idempotent_request is not confirmed by an unrelated sent mail (wrong subject, or sent before the message even arrived) and falls back', async () => {
  const client = fakeClient({
    sent: [
      { to: ['inbox@example.com'], subject: 'Something else entirely', created_at: '2026-10-08T07:00:01.000Z' },
      { to: ['inbox@example.com'], subject: '[Fel] Appen kraschar', created_at: '2026-10-08T06:59:00.000Z' }, // before email.created_at — can't be this forward
    ],
  });
  client.send = async (payload, key) => {
    if (key === 'forward-em_1') {
      const err = new Error('conflict');
      err.name = 'invalid_idempotent_request';
      err.statusCode = 409;
      throw err;
    }
    client.calls.send.push({ payload, key });
    return `sent_${client.calls.send.length}`;
  };
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-fallback-em_1', 'receipt-em_1']);
});

test('a failed listSent while confirming a 409 replay falls back instead of crashing', async () => {
  const client = fakeClient();
  let listSentCalls = 0;
  client.listSent = async () => {
    listSentCalls += 1;
    if (listSentCalls === 1) throw new Error('resend down'); // the 409-confirmation call fails
    return []; // the later recency check (for the receipt) succeeds normally
  };
  client.send = async (payload, key) => {
    if (key === 'forward-em_1') {
      const err = new Error('conflict');
      err.name = 'invalid_idempotent_request';
      err.statusCode = 409;
      throw err;
    }
    client.calls.send.push({ payload, key });
    return `sent_${client.calls.send.length}`;
  };
  await run(client);
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-fallback-em_1', 'receipt-em_1']);
});

test('non-fallback-worthy errors (409 concurrent, 401, 403, 429, 5xx) are real failures, never a fallback — even though the fallback would succeed', async () => {
  for (const { name, statusCode } of [
    { name: 'concurrent_idempotent_requests', statusCode: 409 }, // a Svix retry while attempt 1 is still in flight — attempt 1 may still complete
    { name: 'invalid_api_key', statusCode: 401 },
    { name: 'restricted_api_key', statusCode: 403 },
    { name: 'rate_limit_exceeded', statusCode: 429 }, // falling back here would permanently degrade the forward with a 200, so Svix never retries the full version
    { name: 'internal_server_error', statusCode: 500 },
  ]) {
    const client = fakeClient();
    client.send = async (payload, key) => {
      if (key === 'forward-em_1') {
        const err = new Error('nope');
        err.name = name;
        err.statusCode = statusCode;
        throw err;
      }
      // Any other key (the fallback, the receipt) would succeed — proving the fallback was never attempted.
      client.calls.send.push({ payload, key });
      return 'sent';
    };
    const result = await run(client);
    assert.deepEqual(result, { status: 500 }, `${name} ${statusCode}`);
    assert.equal(client.calls.send.some((c) => c.key.startsWith('forward-fallback-')), false, `${name} ${statusCode}`);
  }
});

test('a fallback-worthy error name (invalid_parameter) triggers the fallback even without statusCode exactly 400/422', async () => {
  const client = fakeClient();
  client.send = async (payload, key) => {
    if (key === 'forward-em_1') {
      const err = new Error('bad parameter');
      err.name = 'invalid_parameter';
      // No statusCode set at all — the name alone must be enough to qualify for the fallback.
      throw err;
    }
    client.calls.send.push({ payload, key });
    return 'sent';
  };
  await run(client);
  assert.ok(client.calls.send.some((c) => c.key === 'forward-fallback-em_1'));
});

test('a 4xx validation error on the forward (e.g. a broken attachment) sends one text-only fallback forward, then the receipt', async () => {
  const client = fakeClient();
  client.send = async (payload, key) => {
    if (key === 'forward-em_1') {
      const err = new Error('invalid attachment');
      err.name = 'invalid_attachment';
      err.statusCode = 422;
      throw err;
    }
    client.calls.send.push({ payload, key });
    return `sent_${client.calls.send.length}`;
  };
  const logs = [];
  const result = await run(client, undefined, undefined, { log: (l) => logs.push(l) });
  assert.deepEqual(result, { status: 200 });
  assert.ok(logs.some((l) => l.includes('forward-fallback')));
  assert.deepEqual(client.calls.send.map((c) => c.key), ['forward-fallback-em_1', 'receipt-em_1']);
});

test('a failed fallback forward is still a 500', async () => {
  const client = fakeClient();
  client.send = async (_payload, key) => {
    const err = new Error('nope');
    if (key === 'forward-em_1') {
      err.name = 'invalid_attachment';
      err.statusCode = 422;
    } else {
      err.name = 'internal_server_error';
      err.statusCode = 500;
    }
    throw err;
  };
  assert.deepEqual(await run(client), { status: 500 });
});

test('attachments are only fetched when the message has some', async () => {
  const client = fakeClient({ mail: { ...email, attachments: [{ id: 'a1', size: 10 }] } });
  await run(client);
  assert.equal(client.calls.listAttachments, 1);
});

test('a hasMore attachments page adds a header line noting more exist in Resend', async () => {
  const client = fakeClient({ mail: { ...email, attachments: [{ id: 'a1', size: 10 }] } });
  client.listAttachments = async () => {
    client.calls.listAttachments += 1;
    return { attachments: [{ id: 'a1', filename: 'x.png', size: 10, content_type: 'image/png', download_url: 'https://x' }], hasMore: true };
  };
  await run(client);
  const forward = client.calls.send.find((c) => c.key === 'forward-em_1');
  assert.match(forward.payload.text, /Fler bilagor finns kvar i Resend/);
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

test('forward-failed and receipt-failed log only error.name and statusCode, never error.message', async () => {
  const logs = [];
  const client = fakeClient();
  client.send = async () => {
    const err = new Error('to: anna@example.se is invalid');
    err.name = 'validation_error';
    err.statusCode = 422;
    throw err;
  };
  await run(client, undefined, undefined, { log: (l) => logs.push(l) });
  const line = logs.find((l) => l.includes('forward-failed'));
  assert.ok(line);
  assert.doesNotMatch(line, /anna@example\.se/);
  assert.match(line, /"error":"validation_error"/);
  assert.match(line, /"statusCode":422/);
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
