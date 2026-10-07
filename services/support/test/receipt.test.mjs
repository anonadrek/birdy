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
