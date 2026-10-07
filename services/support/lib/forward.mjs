// The forward to Albin's inbox (spec section 3.3.4): labelled subject, Reply-To the sender, a short header.
import { addressOf } from './address.mjs';

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const stripTags = (html) => html.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
const megabytes = (bytes) => (bytes / (1024 * 1024)).toFixed(1);

/** The plain-text body: the message's own text, or its html stripped of tags when there is no text. */
export function plainBody(email) {
  return email.text ?? stripTags(email.html ?? '');
}

/** The payload for resend.emails.send. `attachments` are Resend's attachment records with download_url. */
export function buildForward({ email, attachments, label, supportAddress, forwardTo }) {
  const total = attachments.reduce((sum, a) => sum + a.size, 0);
  const fits = total <= MAX_ATTACHMENT_BYTES;
  // Resend-id is always present, so the message can be found in Resend even when the subject or
  // sender gets mangled on the way through a mail client.
  const header = [`Från: ${email.from}`, `Datum: ${email.created_at}`, `Till: ${(email.to ?? []).join(', ')}`, `Resend-id: ${email.id}`];
  if (attachments.length > 0 && !fits) {
    header.push(`Bilagor (${attachments.length} st, ${megabytes(total)} MB) skickas inte vidare; de finns kvar i Resend i 30 dagar (${email.id}).`);
  }
  const body = plainBody(email);
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

/**
 * A minimal fallback for when the normal forward fails validation (e.g. a broken attachment URL):
 * text only, no attachments, no Reply-To — just the id and the plain body, so the message is not
 * lost. Its own idempotency key (forward-fallback-<id>) is separate from the normal forward's.
 */
export function buildFallbackForward({ email, supportAddress, forwardTo }) {
  return {
    from: `Birdy support <${supportAddress}>`,
    to: [forwardTo],
    subject: `[Birdy] (kunde inte vidarebefordras som vanligt) ${email.id}`,
    text: `Resend-id: ${email.id}\n\n${plainBody(email)}`,
  };
}
