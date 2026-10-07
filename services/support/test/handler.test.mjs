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
  // DKIM/DMARC pass: a normal, authenticated sender. Not an SDK 6.32.1 type, but present at runtime.
  authentication: { dkim: 'pass', spf: 'pass', dmarc: 'pass' },
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

test('a replayed forward (409 invalid_idempotent_request, e.g. from changed attachment URLs on retry) counts as forwarded and continues to the receipt', async () => {
  const client = fakeClient();
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

test('a genuine 409 on a forward that was never truly forwarded (different name) is still a failure', async () => {
  const client = fakeClient();
  client.send = async () => {
    const err = new Error('conflict');
    err.name = 'concurrent_idempotent_requests';
    err.statusCode = 409;
    throw err;
  };
  assert.deepEqual(await run(client), { status: 500 });
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
