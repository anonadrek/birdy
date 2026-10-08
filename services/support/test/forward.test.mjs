import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildForward, buildFallbackForward, MAX_ATTACHMENT_BYTES } from '../lib/forward.mjs';

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
  assert.match(f.text, /^Från: Anna <anna@example\.se>\nDatum: 2026-10-08T07:00:00\.000Z\nTill: support@birdy\.community\nResend-id: em_1\n\nDen kraschar/);
  assert.match(f.html, /Från: Anna &lt;anna@example\.se&gt;/);
  assert.match(f.html, /<p>Den kraschar när jag sparar\.<\/p>$/);
});

test('the header always includes the Resend id, for finding the message in Resend later', () => {
  const f = buildForward(base);
  assert.match(f.text, /Resend-id: em_1/);
  assert.match(f.html, /Resend-id: em_1/);
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

test('an inline attachment (content_id) actually referenced via cid: in the html is forwarded with its contentId', () => {
  const attachments = [{ id: 'a1', filename: 'inline.png', size: 100, content_type: 'image/png', content_id: 'img1@resend', download_url: 'https://x' }];
  const f = buildForward({ ...base, email: { ...email, html: '<p><img src="cid:img1@resend"></p>' }, attachments });
  assert.deepEqual(f.attachments, [{ filename: 'inline.png', path: 'https://x', contentType: 'image/png', contentId: 'img1@resend' }]);
});

test('a content_id wrapped in angle brackets (as Resend may return it) is matched and stripped before use', () => {
  const attachments = [{ id: 'a1', filename: 'inline.png', size: 100, content_type: 'image/png', content_id: '<img1@resend>', download_url: 'https://x' }];
  const f = buildForward({ ...base, email: { ...email, html: '<p><img src="cid:img1@resend"></p>' }, attachments });
  assert.deepEqual(f.attachments, [{ filename: 'inline.png', path: 'https://x', contentType: 'image/png', contentId: 'img1@resend' }]);
});

test('a content_id NOT referenced anywhere in the html is forwarded as a regular attachment, not inline', () => {
  const attachments = [{ id: 'a1', filename: 'inline.png', size: 100, content_type: 'image/png', content_id: 'img1@resend', download_url: 'https://x' }];
  const f = buildForward({ ...base, email: { ...email, html: '<p>no image reference here</p>' }, attachments });
  assert.deepEqual(f.attachments, [{ filename: 'inline.png', path: 'https://x', contentType: 'image/png' }]);
});

test('a content_id is a regular attachment when there is no html at all', () => {
  const attachments = [{ id: 'a1', filename: 'inline.png', size: 100, content_type: 'image/png', content_id: 'img1@resend', download_url: 'https://x' }];
  const f = buildForward({ ...base, email: { ...email, html: null }, attachments });
  assert.deepEqual(f.attachments, [{ filename: 'inline.png', path: 'https://x', contentType: 'image/png' }]);
});

test('too large attachments stay in Resend and the header says so, including any inline images in the html', () => {
  const attachments = [{ id: 'a1', filename: null, size: MAX_ATTACHMENT_BYTES + 1, content_type: 'video/mp4', download_url: 'https://x' }];
  const f = buildForward({ ...base, attachments });
  assert.equal(f.attachments, undefined);
  assert.match(f.text, /Bilagor \(1 st, 10\.0 MB\) skickas inte vidare, inklusive eventuella infogade bilder i texten; de finns kvar i Resend i 30 dagar \(em_1\)\./);
});

test('a header line notes when more attachments exist in Resend beyond what was fetched', () => {
  const f = buildForward({ ...base, hasMore: true });
  assert.match(f.text, /Fler bilagor finns kvar i Resend/);
});

test('no extra header line when there are no more attachments', () => {
  const f = buildForward(base);
  assert.doesNotMatch(f.text, /Fler bilagor/);
});

test('buildFallbackForward is text-only, with no attachments or Reply-To, and a fixed subject carrying the id', () => {
  const f = buildFallbackForward({ email, supportAddress: 'support@birdy.community', forwardTo: 'inbox@example.com' });
  assert.equal(f.from, 'Birdy support <support@birdy.community>');
  assert.deepEqual(f.to, ['inbox@example.com']);
  assert.equal(f.subject, '[Birdy] (kunde inte vidarebefordras som vanligt) em_1');
  assert.equal(f.replyTo, undefined);
  assert.equal(f.attachments, undefined);
  assert.equal(f.html, undefined);
});

test('buildFallbackForward includes Från, Datum and the original subject as plain text lines — none of which can fail validation', () => {
  const f = buildFallbackForward({ email, supportAddress: 'support@birdy.community', forwardTo: 'inbox@example.com' });
  assert.match(f.text, /^Från: Anna <anna@example\.se>\nDatum: 2026-10-08T07:00:00\.000Z\nÄmne: Appen kraschar\nResend-id: em_1\n\nDen kraschar när jag sparar\.$/);
});

test('buildFallbackForward notes a missing original subject the same way the normal forward does', () => {
  const f = buildFallbackForward({ email: { ...email, subject: '' }, supportAddress: 'support@birdy.community', forwardTo: 'inbox@example.com' });
  assert.match(f.text, /Ämne: \(inget ämne\)/);
});

test('buildFallbackForward uses the stripped html when there is no text', () => {
  const f = buildFallbackForward({ email: { ...email, text: null }, supportAddress: 'support@birdy.community', forwardTo: 'inbox@example.com' });
  assert.match(f.text, /\n\nDen kraschar när jag sparar\.$/);
});
