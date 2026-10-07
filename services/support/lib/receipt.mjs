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

/** True when the incoming mail already references a prior message (an ongoing thread) — no receipt needed, the sender already knows Birdy has it. A present-but-blank header does not count. */
export function isThreadReply({ headers }) {
  return Boolean(lowerKeys(headers)['in-reply-to']?.trim());
}

/** Resend's timestamps, ISO ("…Z") or Postgres style ("2026-10-07 20:00:00.123456+00"). */
export function parseTime(value) {
  const iso = String(value).replace(' ', 'T').replace(/([+-]\d{2})$/, '$1:00').replace(/(\.\d{3})\d+/, '$1');
  return Date.parse(iso);
}

/**
 * Whether Birdy (a receipt, or Albin's reply through Resend) mailed `address` in the last 24 hours.
 * Mail sent to `excludeAddress` (FORWARD_TO) is ignored: it is the forward to Albin's inbox, not a
 * receipt, and must not be mistaken for one when the sender happens to be that same inbox (Albin
 * testing from his own Gmail, which is also FORWARD_TO).
 */
export function mailedRecently(sent, address, now, excludeAddress) {
  const exclude = excludeAddress ? addressOf(excludeAddress) : null;
  return sent.some((email) => {
    const to = email.to ?? [];
    if (exclude && to.some((t) => addressOf(t) === exclude)) return false;
    return to.some((t) => addressOf(t) === address) && now - parseTime(email.created_at) < DAY_MS;
  });
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
