import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile, access, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { deflateSync, crc32 } from 'node:zlib';
import { randomBytes } from 'node:crypto';
import { timeline, layoutProblems, encodeVideo, MAX_CLIP_SEC, MAX_VIDEO_SEC, FPS, SAFE_BOTTOM, CREDITS_ALLOWANCE, LEAD_SEC, SETTLE_SEC, PAUSE_SEC } from '../lib/render.mjs';

test('the whole video stays within 30 s, also for the longest clip', () => {
  const t = timeline(MAX_CLIP_SEC);
  assert.ok(t.T <= MAX_VIDEO_SEC, `${t.T}`);
  assert.equal(t.frames, Math.round(t.T * FPS));
  assert.ok(timeline(20).T < MAX_VIDEO_SEC);
  assert.throws(() => timeline(MAX_VIDEO_SEC - LEAD_SEC), /over 30 s/);
});

test('after the song the ring settles, then one breath of stillness, then the reveal', () => {
  const t = timeline(12);
  assert.equal(t.A0, LEAD_SEC);
  assert.equal(t.E, LEAD_SEC + 12);
  assert.ok(Math.abs(t.settled - (t.E + SETTLE_SEC)) < 1e-9);
  assert.ok(Math.abs(t.R - (t.settled + PAUSE_SEC)) < 1e-9);
  assert.ok(PAUSE_SEC >= 0.8 && PAUSE_SEC <= 1.2, 'a breath, not a wait');
  assert.ok(t.T - t.R >= 5, 'time for the name and the end card after the reveal');
});

test('layout: anything to read under the Reels overlay or the buttons is a problem', () => {
  const fine = { textBottom: { brand: 1236, hint: 1530, sub: 1534, endLink: 1385 }, endContentBottom: 1540, textRight: { question: 900 } };
  assert.deepEqual(layoutProblems(fine), []);
  assert.match(layoutProblems({ ...fine, textBottom: { brand: 1808 } }).join(), /brand ends at y 1808/);
  assert.match(layoutProblems({ ...fine, textBottom: { sub: SAFE_BOTTOM + 1 } }).join(), /sub ends at y 1537/);
  assert.deepEqual(layoutProblems({ ...fine, endContentBottom: SAFE_BOTTOM + CREDITS_ALLOWANCE }), [], 'credits get a small allowance');
  assert.match(layoutProblems({ ...fine, endContentBottom: SAFE_BOTTOM + CREDITS_ALLOWANCE + 1 }).join(), /credits end at y/);
  assert.match(layoutProblems({ ...fine, textRight: { name: 990 } }).join(), /name reaches x 990/);
});

// A small solid PNG, built by hand so the test needs no browser.
function png(w, h) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // RGB
  const rows = Buffer.alloc(h * (1 + 3 * w), 0x80);
  for (let y = 0; y < h; y++) rows[y * (1 + 3 * w)] = 0; // filter byte
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(rows)), chunk('IEND', Buffer.alloc(0))]);
}

function wav(seconds, sr = 48000) {
  const n = Math.round(seconds * sr);
  const b = Buffer.alloc(44 + n * 2);
  b.write('RIFF', 0);
  b.writeUInt32LE(36 + n * 2, 4);
  b.write('WAVEfmt ', 8);
  b.writeUInt32LE(16, 16);
  b.writeUInt16LE(1, 20);
  b.writeUInt16LE(1, 22);
  b.writeUInt32LE(sr, 24);
  b.writeUInt32LE(sr * 2, 28);
  b.writeUInt16LE(2, 32);
  b.writeUInt16LE(16, 34);
  b.write('data', 36);
  b.writeUInt32LE(n * 2, 40);
  return b;
}

const frame = png(64, 64);

// A frame as large as the real ones (1080 x 1920, noisy, several MB), so a write is still in
// flight when ffmpeg dies.
function bigPng() {
  const w = 1080;
  const h = 1920;
  const rows = randomBytes(h * (1 + 3 * w));
  for (let y = 0; y < h; y++) rows[y * (1 + 3 * w)] = 0;
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(rows, { level: 1 })), chunk('IEND', Buffer.alloc(0))]);
}

test('an ffmpeg failure rejects with ffmpeg\'s message instead of crashing the batch', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'birdy-social-test-'));
  try {
    // Frames come slowly, as from the browser, so ffmpeg dies while frames are still being
    // written (the old renderer crashed the whole process here: an unhandled rejection).
    const slow = () => new Promise((r) => setTimeout(() => r(frame), 15));
    const run = encodeVideo(null, { frames: 400, trackWav: join(dir, 'missing.wav'), outMp4: join(dir, 'out.mp4'), frame: slow });
    await assert.rejects(run, /ffmpeg exited with/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('with full-size frames, a dying ffmpeg is reported by its own reason, not as a write error', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'birdy-social-test-'));
  try {
    const big = bigPng();
    assert.ok(big.length > 4 * 1024 * 1024, `${big.length} bytes`);
    const run = encodeVideo(null, { frames: 40, trackWav: join(dir, 'missing.wav'), outMp4: join(dir, 'out.mp4'), frame: async () => big });
    await assert.rejects(run, (e) => {
      assert.match(String(e.message), /ffmpeg exited with/);
      return true;
    });
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('a failing page stops ffmpeg and rejects with the page\'s error', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'birdy-social-test-'));
  try {
    await writeFile(join(dir, 'track.wav'), wav(1));
    let n = 0;
    const run = encodeVideo(null, {
      frames: 30,
      trackWav: join(dir, 'track.wav'),
      outMp4: join(dir, 'out.mp4'),
      frame: async () => {
        if (++n === 4) throw new Error('page crashed');
        return frame;
      },
    });
    await assert.rejects(run, /page crashed/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('a complete run writes the video', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'birdy-social-test-'));
  try {
    await writeFile(join(dir, 'track.wav'), wav(1));
    const out = join(dir, '.see-the-song.mp4.partial');
    await encodeVideo(null, { frames: 12, trackWav: join(dir, 'track.wav'), outMp4: out, frame: async () => frame });
    await access(out);
    assert.ok((await stat(out)).size > 0);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});
