// Audio for the video: the recording (cut so that the whole video stays within 30 s, see
// lib/render.mjs), short fades, about -16 LUFS, delayed by the lead-in and padded with
// silence to the exact video length.
import { join } from 'node:path';
import { run, probeDuration } from './proc.mjs';

export const TARGET_LUFS = -16;
export const LIMIT_DBFS = -2;
export const MAX_EXTRA_GAIN_DB = 2;
const LIMIT_LINEAR = (10 ** (LIMIT_DBFS / 20)).toFixed(4);
const FADE_IN = 0.15;

function fades(dur) {
  const out = Math.min(0.5, dur / 4);
  return `afade=t=in:st=0:d=${FADE_IN},afade=t=out:st=${(dur - out).toFixed(3)}:d=${out.toFixed(3)}`;
}

function parseEbur128(stderr) {
  const summary = stderr.slice(stderr.lastIndexOf('Summary:'));
  const num = (re) => {
    const m = summary.match(re);
    return m ? Number(m[1]) : NaN;
  };
  return { integrated: num(/I:\s+(-?[\d.]+) LUFS/), truePeak: num(/True peak:\s+Peak:\s+(-?[\d.]+) dBFS/), lra: num(/LRA:\s+(-?[\d.]+) LU/) };
}

/** Integrated loudness, true peak and loudness range of a file's audio, measured with ebur128. */
export async function measureLoudness(file) {
  const { stderr } = await run('ffmpeg', ['-hide_banner', '-nostats', '-i', file, '-vn', '-af', 'ebur128=peak=true', '-f', 'null', '-']);
  return parseEbur128(stderr);
}

/** True when a recording of `sourceSec` is longer than the clip may be (it will be cut). */
export function isCut(sourceSec, maxSec) {
  return sourceSec > maxSec + 0.05;
}

/**
 * Writes <workDir>/clip.wav (48 kHz mono, faded, normalised; the ring is drawn from it) and
 * returns its length and whether it was cut at `maxSec`. Plain gain to -16 LUFS plus a peak
 * limiter at -2 dBFS, so the bird's own dynamics stay as recorded (no compressor).
 */
export async function prepareClip(src, workDir, { maxSec }) {
  if (!(maxSec > 0)) throw new Error(`prepareClip needs maxSec, got ${maxSec}`);
  const full = await probeDuration(src);
  const dur = Math.min(full, maxSec);
  const cut = isCut(full, maxSec);
  const clip = join(workDir, 'clip.wav');
  const t = ['-t', dur.toFixed(3)];
  const { stderr } = await run('ffmpeg', ['-hide_banner', '-nostats', ...t, '-i', src, '-af', `${fades(dur)},ebur128`, '-f', 'null', '-']);
  const input = parseEbur128(stderr).integrated;
  if (!Number.isFinite(input)) throw new Error(`could not measure the loudness of ${src}`);
  // A very peaky clip would need the limiter to work hard; it may end up a little under -16
  // rather than be squashed, so the extra gain on top of the plain gain is capped.
  const maxGain = TARGET_LUFS - input + MAX_EXTRA_GAIN_DB;
  let gain = TARGET_LUFS - input;
  let result = null;
  for (let pass = 0; pass < 3; pass++) {
    const chain = `${fades(dur)},volume=${gain.toFixed(2)}dB,alimiter=limit=${LIMIT_LINEAR}:level=false:attack=5:release=50,aresample=48000`;
    await run('ffmpeg', ['-hide_banner', '-nostats', '-y', ...t, '-i', src, '-af', chain, '-ac', '1', '-c:a', 'pcm_s16le', clip]);
    result = await measureLoudness(clip);
    const next = Math.min(maxGain, gain + (TARGET_LUFS - result.integrated)); // the limiter took a little off; ask for it back
    if (Math.abs(result.integrated - TARGET_LUFS) <= 0.3 || next - gain < 0.1) break;
    gain = next;
  }
  return { clip, dur: await probeDuration(clip), sourceDur: full, cut, measuredInputLufs: input, gainDb: Number(gain.toFixed(2)), clipLoudness: result };
}

/** Delays the clip by `leadSec` and pads it with silence to exactly `totalSec` (stereo, 48 kHz). */
export async function buildTrack(clip, workDir, { leadSec, totalSec }) {
  const track = join(workDir, 'track.wav');
  const ms = Math.round(leadSec * 1000);
  await run('ffmpeg', ['-hide_banner', '-nostats', '-y', '-i', clip, '-af', `adelay=${ms}:all=1,apad=whole_dur=${totalSec.toFixed(4)}`, '-t', totalSec.toFixed(4), '-ac', '2', '-ar', '48000', '-c:a', 'pcm_s16le', track]);
  return track;
}
