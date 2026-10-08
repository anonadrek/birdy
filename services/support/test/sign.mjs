import { createHmac } from 'node:crypto';

export const SECRET = `whsec_${Buffer.from('birdy-support-test-secret-0123456').toString('base64')}`;

/** Headers for `payload` as Resend sends them; `timestamp` in seconds. */
export function sign(payload, { id = 'msg_1', timestamp = Math.floor(Date.now() / 1000), secret = SECRET } = {}) {
  const key = Buffer.from(secret.slice('whsec_'.length), 'base64');
  const signature = createHmac('sha256', key).update(`${id}.${timestamp}.${payload}`).digest('base64');
  return new Headers({ 'svix-id': id, 'svix-timestamp': String(timestamp), 'svix-signature': `v1,${signature}` });
}
