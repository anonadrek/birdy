// Frame-accurate rendering: the stage page is a pure function of time, so frame f is the
// stage at t = f / FPS, screenshotted by Playwright and piped as PNG into ffmpeg (no screen
// recording). The audio track is muxed in the same ffmpeg run.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
export const STAGE = join(here, '..', 'stage', 'see-the-song.html');
export const FPS = 30;

// The whole video, not just the sound, stays within 30 s.
export const MAX_VIDEO_SEC = 30;
export const LEAD_SEC = 0.5; // the question and the waiting ring before the bird starts
export const SETTLE_SEC = 0.7; // when the song ends the ring settles into a still circle
export const PAUSE_SEC = 1.0; // one breath of stillness before the reveal
export const AFTER_REVEAL_SEC = 6.0; // the photo sharpens, the circle opens, the name is written, end card
export const TAIL_SEC = SETTLE_SEC + PAUSE_SEC + AFTER_REVEAL_SEC;
/** The longest clip that keeps the video within MAX_VIDEO_SEC (0.1 s spare for rounding). */
export const MAX_CLIP_SEC = Math.floor((MAX_VIDEO_SEC - LEAD_SEC - TAIL_SEC - 0.1) * 10) / 10;

// Instagram Reels and YouTube Shorts lay their own buttons along the right edge and their
// own name, caption and sound rows over roughly the bottom fifth. Everything meant to be read
// ends above SAFE_BOTTOM and stays left of SAFE_RIGHT. The end card's small credit lines get
// a few pixels of allowance (they are in every caption as well).
export const SAFE_BOTTOM = 1536;
export const SAFE_RIGHT = 918;
export const CREDITS_ALLOWANCE = 8;

/**
 * All times in seconds; the video is exactly `frames` frames long.
 *   A0       the song starts
 *   E        the song ends
 *   settled  the ring is still
 *   R        the reveal starts: the photo sharpens, the circle opens, the name is written
 */
export function timeline(clipSec) {
  const A0 = LEAD_SEC;
  const D = clipSec;
  const E = A0 + D;
  const settled = E + SETTLE_SEC;
  const R = settled + PAUSE_SEC;
  const frames = Math.round((E + TAIL_SEC) * FPS);
  const T = frames / FPS;
  if (T > MAX_VIDEO_SEC + 1e-9) throw new Error(`a ${D.toFixed(2)} s clip makes a ${T.toFixed(2)} s video, over ${MAX_VIDEO_SEC} s (clips are cut at ${MAX_CLIP_SEC} s)`);
  return { A0, D, E, settled, R, frames, T };
}

/** Layout problems: text under the platforms' own overlays. `layout` comes from setupStage. */
export function layoutProblems(layout) {
  const problems = [];
  for (const [name, bottom] of Object.entries(layout.textBottom ?? {})) if (bottom > SAFE_BOTTOM) problems.push(`${name} ends at y ${bottom}, under the Reels overlay (y ${SAFE_BOTTOM})`);
  if (layout.endContentBottom > SAFE_BOTTOM + CREDITS_ALLOWANCE) problems.push(`the end card's credits end at y ${layout.endContentBottom}, under the Reels overlay (y ${SAFE_BOTTOM} + ${CREDITS_ALLOWANCE})`);
  for (const [name, right] of Object.entries(layout.textRight ?? {})) if (right > SAFE_RIGHT) problems.push(`${name} reaches x ${right}, under the buttons (x ${SAFE_RIGHT})`);
  return problems;
}

export async function launchBrowser() {
  const channel = process.env.BIRDY_SOCIAL_CHANNEL || undefined; // e.g. "chrome" to use the installed Chrome
  return chromium.launch({ channel });
}

export async function openStage(browser, config) {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  try {
    const errors = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.goto(pathToFileURL(STAGE).href);
    const layout = await page.evaluate((cfg) => window.setupStage(cfg), config);
    if (errors.length) throw new Error(`stage errors: ${errors.join('; ')}`);
    const problems = layoutProblems(layout);
    if (problems.length) throw new Error(`layout: ${problems.join('; ')}`);
    return { page, layout };
  } catch (e) {
    await page.close();
    throw e;
  }
}

/**
 * The stage at time t as PNG (or JPEG). `cover: true` renders the cover: the same moment with
 * the photo hidden (a question mark in the empty circle), so the thumbnail never gives the bird away.
 */
export async function frameAt(page, t, { type = 'png', quality, cover = false } = {}) {
  const photoOpacity = await page.evaluate(
    ({ time, asCover }) => {
      window.renderAt(time, { cover: asCover });
      return new Promise((r) => requestAnimationFrame(() => r(getComputedStyle(document.getElementById('photoWrap')).opacity)));
    },
    { time: t, asCover: cover },
  );
  if (cover && Number(photoOpacity) !== 0) throw new Error(`the cover shows the photo (opacity ${photoOpacity})`);
  return page.screenshot(type === 'jpeg' ? { type, quality } : { type });
}

export function x264Args({ frames, trackWav, outMp4 }) {
  return [
    '-hide_banner', '-nostats', '-loglevel', 'error', '-y',
    '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'png', '-i', '-',
    '-i', trackWav,
    '-map', '0:v:0', '-map', '1:a:0',
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
    '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-profile:v', 'high', '-g', '60', '-r', String(FPS),
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
    '-frames:v', String(frames), '-t', (frames / FPS).toFixed(4),
    // -f mp4: the output is written to a ".partial" name first (lib/atomic.mjs)
    '-movflags', '+faststart', '-f', 'mp4', outMp4,
  ];
}

/**
 * Renders every frame into ffmpeg and writes the MP4 to `outMp4`. If ffmpeg fails (or exits
 * early), this rejects with ffmpeg's own message instead of crashing the process: the write
 * error on its closed stdin is caught and the frame loop stops.
 */
export async function encodeVideo(page, { frames, trackWav, outMp4, onProgress, frame = frameAt }) {
  const ff = spawn('ffmpeg', x264Args({ frames, trackWav, outMp4 }), { stdio: ['pipe', 'ignore', 'pipe'], windowsHide: true });
  let err = '';
  ff.stderr.on('data', (d) => {
    err = (err + d.toString()).slice(-8000);
  });
  let exited = false;
  const done = new Promise((resolve, reject) => {
    ff.on('error', (e) => {
      exited = true;
      reject(e);
    });
    ff.on('close', (code, signal) => {
      exited = true;
      if (code === 0) resolve();
      else reject(new Error(`ffmpeg exited with ${code ?? signal}\n${err.slice(-2000)}`));
    });
  });
  done.catch(() => {}); // awaited below; until then a failure must not be an unhandled rejection
  ff.stdin.on('error', () => {}); // EPIPE when ffmpeg has died; `done` carries the reason
  let written = 0;
  let pageError = null;
  try {
    for (let f = 0; f < frames && !exited; f++) {
      let png;
      try {
        png = await frame(page, f / FPS);
      } catch (e) {
        pageError = e;
        throw e;
      }
      if (exited) break;
      if (!ff.stdin.write(png)) await Promise.race([once(ff.stdin, 'drain'), done]);
      written++;
      if (onProgress && (f % 60 === 0 || f === frames - 1)) onProgress(f + 1, frames);
    }
  } catch (e) {
    if (pageError) {
      // The page failed: stop ffmpeg and report the page's error.
      ff.kill();
      await done.catch(() => {});
      throw pageError;
    }
    // A write failed ("write EOF", EPIPE): ffmpeg has stopped or is stopping, and its exit
    // says why. Wait for it (a few seconds at most) and report that instead.
    const reason = await Promise.race([done.then(() => null, (x) => x), new Promise((r) => setTimeout(() => r(null), 5000))]);
    if (!exited) ff.kill();
    throw reason ?? e;
  } finally {
    ff.stdin.end();
  }
  await done;
  if (written < frames) throw new Error(`ffmpeg stopped after ${written} of ${frames} frames\n${err.slice(-2000)}`);
}
