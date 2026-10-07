// The forward to Albin's inbox (spec section 3.3.4): labelled subject, Reply-To the sender, a short header.
import { addressOf } from './address.mjs';

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

const escapeHtml = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const stripTags = (html) => html.replace(/<style[\s\S]*?<\/style>/gi, '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
const megabytes = (bytes) => (bytes / (1024 * 1024)).toFixed(1);
// Resend's content_id may come wrapped in angle brackets (the raw MIME Content-ID form), but the
// cid: reference in html never has them.
const stripAngles = (s) => s.replace(/^<|>$/g, '');
// Only treat an attachment as inline when the html actually references it — a content_id present
// but unused would otherwise wrongly hide a real, downloadable attachment as "inline" content.
const isInlineReferenced = (html, contentId) => typeof html === 'string' && html.includes(`cid:${stripAngles(contentId)}`);

/** The plain-text body: the message's own text, or its html stripped of tags when there is no text. */
export function plainBody(email) {
  return email.text ?? stripTags(email.html ?? '');
}

/**
 * The payload for resend.emails.send. `attachments` are Resend's attachment records with
 * download_url. `hasMore` (from the paginated attachments list) means Resend holds even more
 * attachments than were fetched — noted in the header rather than silently dropped.
 */
export function buildForward({ email, attachments, hasMore = false, label, supportAddress, forwardTo }) {
  const total = attachments.reduce((sum, a) => sum + a.size, 0);
  const fits = total <= MAX_ATTACHMENT_BYTES;
  // Resend-id is always present, so the message can be found in Resend even when the subject or
  // sender gets mangled on the way through a mail client.
  const header = [`Från: ${email.from}`, `Datum: ${email.created_at}`, `Till: ${(email.to ?? []).join(', ')}`, `Resend-id: ${email.id}`];
  if (attachments.length > 0 && !fits) {
    header.push(`Bilagor (${attachments.length} st, ${megabytes(total)} MB) skickas inte vidare, inklusive eventuella infogade bilder i texten; de finns kvar i Resend i 30 dagar (${email.id}).`);
  }
  if (hasMore) {
    header.push(`Fler bilagor finns kvar i Resend (${email.id}); bara de första listade hämtades.`);
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
        ? attachments.map((a) => ({
            filename: a.filename ?? `bilaga-${a.id}`,
            path: a.download_url,
            contentType: a.content_type,
            // contentId makes this attachment inline, resolving a matching cid: reference in html
            // (from html_format: 'cid') instead of a base64 duplicate. Omitted, not undefined, when
            // there is none, or when the html doesn't actually reference it — a content_id present
            // but unused must still be a normal, visible, downloadable attachment.
            ...(a.content_id && isInlineReferenced(email.html, a.content_id) ? { contentId: stripAngles(a.content_id) } : {}),
          }))
        : undefined,
  };
}

/**
 * A minimal fallback for when the normal forward fails validation (e.g. a broken attachment URL):
 * text only, no attachments, no Reply-To — just the id and the plain body, so the message is not
 * lost. Its own idempotency key (forward-fallback-<id>) is separate from the normal forward's.
 */
export function buildFallbackForward({ email, supportAddress, forwardTo }) {
  // Plain lines only — Från/Datum/Ämne/Resend-id can never themselves fail validation, unlike the
  // full forward's html or attachments.
  const header = [`Från: ${email.from}`, `Datum: ${email.created_at}`, `Ämne: ${email.subject?.trim() || '(inget ämne)'}`, `Resend-id: ${email.id}`];
  return {
    from: `Birdy support <${supportAddress}>`,
    to: [forwardTo],
    subject: `[Birdy] (kunde inte vidarebefordras som vanligt) ${email.id}`,
    text: `${header.join('\n')}\n\n${plainBody(email)}`,
  };
}
