// The whole flow (spec section 3). Returns { status }; the Vercel entry turns it into a Response.
// Logs one JSON line per outcome with the email id, never addresses, subjects or content (spec section 6).
import { addressOf } from './address.mjs';
import { buildForward } from './forward.mjs';
import { labelFor } from './labels.mjs';
import { isAutomated, mailedRecently, receiptMessage } from './receipt.mjs';

const REQUIRED = ['RESEND_WEBHOOK_SECRET', 'SUPPORT_ADDRESS', 'FORWARD_TO'];

export async function handleInbound({ rawBody, headers, env, client, now = Date.now(), log = console.log }) {
  const say = (fields) => log(JSON.stringify(fields));
  const missing = REQUIRED.filter((name) => !env[name]);
  if (missing.length > 0) {
    say({ outcome: 'config', missing });
    return { status: 500 };
  }

  let event;
  try {
    event = client.verify({
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
    email = await client.getEmail(id);
    const attachments = email.attachments?.length > 0 ? await client.listAttachments(id) : [];
    const label = labelFor({ subject: email.subject ?? '', text: email.text ?? '' });
    await client.send(buildForward({ email, attachments, label, supportAddress: env.SUPPORT_ADDRESS, forwardTo: env.FORWARD_TO }), `forward-${id}`);
    say({ outcome: 'forwarded', id, label, attachments: attachments.length });
  } catch (error) {
    // Resend re-fetches attachment download_url on every listAttachments() call, so a retry (Svix,
    // after a prior 500) can send a different payload under the same forward-<id> idempotency key.
    // That is a 409 invalid_idempotent_request, not a real failure: the first attempt already went
    // out, so treat it as forwarded and continue to the receipt.
    if (email && error.statusCode === 409 && error.name === 'invalid_idempotent_request') {
      say({ outcome: 'forward-replayed', id });
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
    } else if (!authenticated) {
      say({ outcome: 'no-receipt', id, reason: 'unauthenticated' });
    } else if (mailedRecently(await client.listSent(), sender, now, env.FORWARD_TO)) {
      say({ outcome: 'no-receipt', id, reason: 'recent' });
    } else {
      const { subject, text } = receiptMessage(email.subject);
      const thread = email.message_id ? { 'In-Reply-To': email.message_id, References: email.message_id } : {};
      await client.send({ from: `Birdy <${env.SUPPORT_ADDRESS}>`, to: [sender], subject, text, headers: { 'Auto-Submitted': 'auto-replied', ...thread } }, `receipt-${id}`);
      say({ outcome: 'receipt-sent', id });
    }
  } catch (error) {
    say({ outcome: 'receipt-failed', id, error: error.name, statusCode: error.statusCode });
  }
  return { status: 200 };
}
