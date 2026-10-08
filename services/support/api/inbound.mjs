// Vercel Function for Resend's email.received webhook (spec 2026-10-07-support-resend-design.md).
import { Resend } from 'resend';
import { resendClient } from '../lib/client.mjs';
import { handleInbound } from '../lib/handler.mjs';

export async function POST(request) {
  // Lazy: `new Resend(...)` throws synchronously when RESEND_API_KEY is missing. Deferring
  // construction to a factory means handleInbound's own config check (REQUIRED) catches a missing
  // key and logs 'config' instead of this throwing before that check ever runs.
  const client = () => resendClient(new Resend(process.env.RESEND_API_KEY));
  const { status } = await handleInbound({ rawBody: await request.text(), headers: request.headers, env: process.env, client });
  return new Response(null, { status });
}
