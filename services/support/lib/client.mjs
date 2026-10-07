// The only file that knows Resend's SDK. Every call throws on an error, so the handler has one error path.
// The thrown error carries `.name` and `.statusCode` from Resend's error object, never its `.message`
// (which can embed the address or content that triggered it) — callers must log name/statusCode only.

/** @param {import('resend').Resend} resend */
export function resendClient(resend) {
  const unwrap = (what) => ({ data, error }) => {
    if (error) {
      const err = new Error(`${what} failed`);
      err.name = error.name ?? 'error';
      err.statusCode = error.statusCode ?? null;
      throw err;
    }
    return data;
  };
  return {
    verify: ({ payload, headers, secret }) => resend.webhooks.verify({ payload, headers, webhookSecret: secret }),
    // html_format: 'cid' keeps <img src="cid:..."> references in html instead of inlining every
    // image as a base64 data: URI (the default) — avoiding a duplicate of each inline image's
    // bytes once in html and again as a forwarded attachment.
    getEmail: async (id) => unwrap('receiving.get')(await resend.emails.receiving.get(id, { html_format: 'cid' })),
    listAttachments: async (id) => {
      // limit: 100 raises the default page of 20, which silently dropped attachments beyond it;
      // has_more still says whether even that was not everyone (forward.mjs notes it when so).
      const result = unwrap('receiving.attachments.list')(await resend.emails.receiving.attachments.list({ emailId: id, limit: 100 }));
      return { attachments: result.data, hasMore: result.has_more };
    },
    listSent: async () => unwrap('emails.list')(await resend.emails.list({ limit: 100 })).data,
    send: async (payload, idempotencyKey) => unwrap('emails.send')(await resend.emails.send(payload, { idempotencyKey })).id,
  };
}
