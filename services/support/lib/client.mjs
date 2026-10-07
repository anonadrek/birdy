// The only file that knows Resend's SDK. Every call throws on an error, so the handler has one error path.

/** @param {import('resend').Resend} resend */
export function resendClient(resend) {
  const unwrap = (what) => ({ data, error }) => {
    if (error) throw new Error(`${what}: ${error.name ?? 'error'} ${error.message ?? ''}`.trim());
    return data;
  };
  return {
    verify: ({ payload, headers, secret }) => resend.webhooks.verify({ payload, headers, webhookSecret: secret }),
    getEmail: async (id) => unwrap('receiving.get')(await resend.emails.receiving.get(id)),
    listAttachments: async (id) => unwrap('receiving.attachments.list')(await resend.emails.receiving.attachments.list({ emailId: id })).data,
    listSent: async () => unwrap('emails.list')(await resend.emails.list({ limit: 100 })).data,
    send: async (payload, idempotencyKey) => unwrap('emails.send')(await resend.emails.send(payload, { idempotencyKey })).id,
  };
}
