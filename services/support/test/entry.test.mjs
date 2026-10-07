import { test } from 'node:test';
import assert from 'node:assert/strict';

test('the Vercel entry answers an unsigned request with 401', async () => {
  process.env.RESEND_API_KEY = 're_test';
  process.env.RESEND_WEBHOOK_SECRET = `whsec_${Buffer.from('entry-test-secret-entry-test-0').toString('base64')}`;
  process.env.SUPPORT_ADDRESS = 'support@birdy.community';
  process.env.FORWARD_TO = 'inbox@example.com';
  const { POST } = await import('../api/inbound.mjs');
  const response = await POST(new Request('http://localhost/api/inbound', { method: 'POST', body: '{}' }));
  assert.equal(response.status, 401);
});
