import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resendClient } from '../lib/client.mjs';

function fakeResend() {
  return {
    webhooks: { verify: () => ({}) },
    emails: {
      send: async () => ({ data: null, error: { message: 'to: anna@example.se is invalid', statusCode: 422, name: 'validation_error' } }),
      list: async () => ({ data: { data: [] }, error: null }),
      receiving: {
        get: async () => ({ data: null, error: { message: 'not found: anna@example.se', statusCode: 404, name: 'not_found' } }),
        attachments: { list: async () => ({ data: { data: [] }, error: null }) },
      },
    },
  };
}

test('send throws an error carrying name and statusCode from Resend, never leaking the message text', async () => {
  const client = resendClient(fakeResend());
  await assert.rejects(client.send({ to: ['x'] }, 'key'), (err) => {
    assert.equal(err.name, 'validation_error');
    assert.equal(err.statusCode, 422);
    assert.doesNotMatch(err.message, /anna@example\.se/);
    return true;
  });
});

test('getEmail asks Resend to keep inline images as cid references, not base64 duplicates', async () => {
  let requestedId;
  let requestedOptions;
  const resend = fakeResend();
  resend.emails.receiving.get = async (id, options) => {
    requestedId = id;
    requestedOptions = options;
    return { data: { id, attachments: [] }, error: null };
  };
  const client = resendClient(resend);
  await client.getEmail('em_1');
  assert.equal(requestedId, 'em_1');
  assert.deepEqual(requestedOptions, { html_format: 'cid' });
});

test('listAttachments asks for up to 100 and reports whether more exist', async () => {
  let requestedOptions;
  const resend = fakeResend();
  resend.emails.receiving.attachments.list = async (options) => {
    requestedOptions = options;
    return { data: { object: 'list', has_more: true, data: [{ id: 'a1' }] }, error: null };
  };
  const client = resendClient(resend);
  const result = await client.listAttachments('em_1');
  assert.deepEqual(requestedOptions, { emailId: 'em_1', limit: 100 });
  assert.deepEqual(result, { attachments: [{ id: 'a1' }], hasMore: true });
});

test('getEmail throws an error carrying name and statusCode too', async () => {
  const client = resendClient(fakeResend());
  await assert.rejects(client.getEmail('em_1'), (err) => {
    assert.equal(err.name, 'not_found');
    assert.equal(err.statusCode, 404);
    assert.doesNotMatch(err.message, /anna@example\.se/);
    return true;
  });
});
