// The whole flow (spec section 3). Returns { status }; the Vercel entry turns it into a Response.
// Logs one JSON line per outcome with the email id, never addresses, subjects or content (spec section 6).
import { addressOf } from './address.mjs';
import { buildFallbackForward, buildForward } from './forward.mjs';
import { labelFor } from './labels.mjs';
import { isAutomated, isThreadReply, mailedRecently, receiptMessage } from './receipt.mjs';

const REQUIRED = ['RESEND_API_KEY', 'RESEND_WEBHOOK_SECRET', 'SUPPORT_ADDRESS', 'FORWARD_TO'];

export async function handleInbound({ rawBody, headers, env, client, now = Date.now(), log = console.log }) {
  const say = (fields) => log(JSON.stringify(fields));
  const missing = REQUIRED.filter((name) => !env[name]);
  if (missing.length > 0) {
    say({ outcome: 'config', missing });
    return { status: 500 };
  }
  // `client` may be a factory (the Vercel entry defers constructing the Resend SDK client until
  // here, so a missing RESEND_API_KEY is caught by the check above instead of throwing on import).
  const resolvedClient = typeof client === 'function' ? client() : client;

  let event;
  try {
    event = resolvedClient.verify({
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
  // support@ can be reached via To, Cc, Bcc, or an alias/list (received_for) — any of them counts.
  const recipients = [...(event.data?.to ?? []), ...(event.data?.cc ?? []), ...(event.data?.bcc ?? []), ...(event.data?.received_for ?? [])];
  if (event.type !== 'email.received' || !recipients.some((to) => addressOf(to) === support)) {
    say({ outcome: 'ignored', id, type: event.type });
    return { status: 200 };
  }
  if (addressOf(event.data?.from) === support) {
    say({ outcome: 'own-mail', id });
    return { status: 200 };
  }

  let email;
  try {
    email = await resolvedClient.getEmail(id);
    const { attachments, hasMore } = email.attachments?.length > 0 ? await resolvedClient.listAttachments(id) : { attachments: [], hasMore: false };
    const label = labelFor({ subject: email.subject ?? '', text: email.text ?? '' });
    await resolvedClient.send(buildForward({ email, attachments, hasMore, label, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO }), `forward-${id}`);
    say({ outcome: 'forwarded', id, label, attachments: attachments.length });
  } catch (error) {
    // Resend re-fetches attachment download_url on every listAttachments() call, so a retry (Svix,
    // after a prior 500) can send a different payload under the same forward-<id> idempotency key.
    // That is a 409 invalid_idempotent_request, not a real failure: the first attempt already went
    // out, so treat it as forwarded and continue to the receipt.
    if (email && error.statusCode === 409 && error.name === 'invalid_idempotent_request') {
      say({ outcome: 'forward-replayed', id });
    } else if (email && error.statusCode >= 400 && error.statusCode < 500) {
      // A validation error (e.g. a broken attachment URL) on the full forward — try once more with
      // a minimal, text-only version so the message is not lost outright.
      try {
        await resolvedClient.send(buildFallbackForward({ email, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO }), `forward-fallback-${id}`);
        say({ outcome: 'forward-fallback', id, statusCode: error.statusCode });
      } catch (fallbackError) {
        say({ outcome: 'forward-failed', id, error: fallbackError.name, statusCode: fallbackError.statusCode });
        return { status: 500 };
      }
    } else {
      say({ outcome: 'forward-failed', id, error: error.name, statusCode: error.statusCode });
      return { status: 500 };
    }
  }

  try {
    // Anti-backscatter: the receipt goes only to the (authenticated) From, never to Reply-To — a
    // forged From/Reply-To with an unauthenticated message must not turn Birdy into a relay.
    const sender = addressOf(email.from);
    const authenticated = email.authentication?.dkim === 'pass' || email.authentication?.dmarc === 'pass';
    if (isAutomated(email, support)) {
      say({ outcome: 'no-receipt', id, reason: 'automated' });
    } else if (isThreadReply(email)) {
      say({ outcome: 'no-receipt', id, reason: 'thread' });
    } else if (!authenticated) {
      say({ outcome: 'no-receipt', id, reason: 'unauthenticated' });
    } else if (mailedRecently(await resolvedClient.listSent(), sender, now, env.FORWARD_TO)) {
      say({ outcome: 'no-receipt', id, reason: 'recent' });
    } else {
      const { subject, text } = receiptMessage(email.subject);
      const thread = email.message_id ? { 'In-Reply-To': email.message_id, References: email.message_id } : {};
      await resolvedClient.send({ from: `Birdy <${env.SUPPORT_ADDRESS}>`, to: [sender], subject, text, headers: { 'Auto-Submitted': 'auto-replied', ...thread } }, `receipt-${id}`);
      say({ outcome: 'receipt-sent', id });
    }
  } catch (error) {
    say({ outcome: 'receipt-failed', id, error: error.name, statusCode: error.statusCode });
  }
  return { status: 200 };
}
