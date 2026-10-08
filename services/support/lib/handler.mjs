// The whole flow (spec section 3). Returns { status }; the Vercel entry turns it into a Response.
// Logs one JSON line per outcome with the email id, never addresses, subjects or content (spec section 6).
import { addressOf } from './address.mjs';
import { buildFallbackForward, buildForward } from './forward.mjs';
import { labelFor } from './labels.mjs';
import { isAutomated, isThreadReply, mailedRecently, parseTime, receiptMessage } from './receipt.mjs';

const REQUIRED = ['RESEND_API_KEY', 'RESEND_WEBHOOK_SECRET', 'SUPPORT_ADDRESS', 'FORWARD_TO'];
// Only these qualify the full forward's failure for the text-only fallback: a real validation
// problem on the content itself. A 409 concurrent_idempotent_requests (attempt 1 may still complete
// — falling back would then double-send), 401/403 (API key trouble), 429 (rate limited — falling
// back would permanently degrade the forward with a 200, so Svix never retries the full version),
// and 5xx are real, retryable failures instead.
const FALLBACK_NAMES = new Set(['validation_error', 'invalid_attachment', 'invalid_parameter', 'missing_required_field']);

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
  let forwardPayload;
  try {
    email = await resolvedClient.getEmail(id);
    const { attachments, hasMore } = email.attachments?.length > 0 ? await resolvedClient.listAttachments(id) : { attachments: [], hasMore: false };
    const label = labelFor({ subject: email.subject ?? '', text: email.text ?? '' });
    forwardPayload = buildForward({ email, attachments, hasMore, label, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO });
    await resolvedClient.send(forwardPayload, `forward-${id}`);
    say({ outcome: 'forwarded', id, label, attachments: attachments.length });
  } catch (error) {
    // A validation error (e.g. a broken attachment URL) on the full forward — try once more with a
    // minimal, text-only version so the message is not lost outright.
    const sendFallback = async () => {
      try {
        await resolvedClient.send(buildFallbackForward({ email, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO }), `forward-fallback-${id}`);
        say({ outcome: 'forward-fallback', id, statusCode: error.statusCode });
        return true;
      } catch (fallbackError) {
        say({ outcome: 'forward-failed', id, error: fallbackError.name, statusCode: fallbackError.statusCode });
        return false;
      }
    };

    if (email && error.statusCode === 409 && error.name === 'invalid_idempotent_request') {
      // Resend re-fetches attachment download_url on every listAttachments() call, so a retry
      // (Svix, after a prior 500) can send a different payload under the same forward-<id>
      // idempotency key, and normally that 409 just means the first attempt already went out. But
      // Resend may (undocumented) bind the key even on a FAILED attempt — blindly trusting the 409
      // could then lose the message while the sender still gets a receipt. Confirm it actually
      // reached FORWARD_TO before believing it.
      let alreadySent = false;
      try {
        const sent = await resolvedClient.listSent();
        const forwardToAddress = addressOf(env.FORWARD_TO);
        alreadySent = sent.some(
          (m) =>
            (m.to ?? []).some((t) => addressOf(t) === forwardToAddress) &&
            (m.subject ?? '') === forwardPayload.subject &&
            parseTime(m.created_at) >= parseTime(email.created_at),
        );
      } catch {
        // listSent itself failed — can't confirm either way; fall through to the safer fallback.
      }
      if (alreadySent) {
        say({ outcome: 'forward-replayed', id });
      } else if (!(await sendFallback())) {
        return { status: 500 };
      }
    } else if (email && (error.statusCode === 400 || error.statusCode === 422 || FALLBACK_NAMES.has(error.name))) {
      if (!(await sendFallback())) {
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
    const dkim = email.authentication?.dkim ?? null;
    const dmarc = email.authentication?.dmarc ?? null;
    const authenticated = dkim === 'pass' || dmarc === 'pass';
    if (isAutomated(email, support)) {
      say({ outcome: 'no-receipt', id, reason: 'automated', dkim, dmarc });
    } else if (isThreadReply(email)) {
      say({ outcome: 'no-receipt', id, reason: 'thread', dkim, dmarc });
    } else if (!authenticated) {
      say({ outcome: 'no-receipt', id, reason: 'unauthenticated', dkim, dmarc });
    } else if (mailedRecently(await resolvedClient.listSent(), sender, now, env.FORWARD_TO)) {
      say({ outcome: 'no-receipt', id, reason: 'recent', dkim, dmarc });
    } else {
      // DKIM alone is enough to send a receipt at all, but echoing the original subject back is
      // held to the stricter DMARC pass (domain-aligned) — otherwise a fixed "Re: Birdy".
      const { subject, text } = receiptMessage(dmarc === 'pass' ? email.subject : '');
      const thread = email.message_id ? { 'In-Reply-To': email.message_id, References: email.message_id } : {};
      await resolvedClient.send({ from: `Birdy <${env.SUPPORT_ADDRESS}>`, to: [sender], subject, text, headers: { 'Auto-Submitted': 'auto-replied', ...thread } }, `receipt-${id}`);
      say({ outcome: 'receipt-sent', id, dkim, dmarc });
    }
  } catch (error) {
    say({ outcome: 'receipt-failed', id, error: error.name, statusCode: error.statusCode });
  }
  return { status: 200 };
}
