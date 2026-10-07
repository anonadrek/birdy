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
