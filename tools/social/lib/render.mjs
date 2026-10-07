// Frame-accurate rendering: the stage page is a pure function of time, so frame f is the
// stage at t = f / FPS, screenshotted by Playwright and piped as PNG into ffmpeg (no screen
// recording). The audio track is muxed in the same ffmpeg run.
import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
export const STAGE = join(here, '..', 'stage', 'see-the-song.html');
export const FPS = 30;
export const LEAD_SEC = 0.5; // the question is on screen this long before the bird starts
export const TAIL_SEC = 6.6; // reveal (photo, handwritten name) and the end card after the song

/** All times in seconds. The video is exactly `frames` frames long. */
export function timeline(clipSec) {
  const A0 = LEAD_SEC;
  const D = clipSec;
  const E = A0 + D;
  const frames = Math.round((E + TAIL_SEC) * FPS);
  // Cover: the song fully drawn, the bird faint behind it, the question still on the paper.
  return { A0, D, E, frames, T: frames / FPS, cover: E + 0.05, reveal: E + 2.6 };
}

export async function launchBrowser() {
  const channel = process.env.BIRDY_SOCIAL_CHANNEL || undefined; // e.g. "chrome" to use the installed Chrome
  return chromium.launch({ channel });
}

export async function openStage(browser, config) {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto(pathToFileURL(STAGE).href);
  const layout = await page.evaluate((cfg) => window.setupStage(cfg), config);
  if (errors.length) throw new Error(`stage errors: ${errors.join('; ')}`);
  return { page, layout };
}

export async function frameAt(page, t, type = 'png', quality) {
  await page.evaluate((time) => {
    window.renderAt(time);
    return new Promise((r) => requestAnimationFrame(() => r()));
  }, t);
  return page.screenshot(type === 'jpeg' ? { type, quality } : { type });
}

/** Final quality by default; `preview` trades quality for small files (about 3 MB for 8 s). */
function x264Args({ frames, trackWav, outMp4, preview = false }) {
  const q = preview ? ['-crf', '26', '-maxrate', '2400k', '-bufsize', '4800k'] : ['-crf', '18'];
  return [
    '-hide_banner', '-nostats', '-loglevel', 'error', '-y',
    '-f', 'image2pipe', '-framerate', String(FPS), '-c:v', 'png', '-i', '-',
    '-i', trackWav,
    '-map', '0:v:0', '-map', '1:a:0',
    '-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
    '-c:v', 'libx264', '-preset', 'medium', ...q, '-profile:v', 'high', '-g', '60', '-r', String(FPS),
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-c:a', 'aac', '-b:a', preview ? '128k' : '192k', '-ar', '48000',
    '-frames:v', String(frames), '-t', (frames / FPS).toFixed(4),
    '-movflags', '+faststart', outMp4,
  ];
}

/** Renders every frame into ffmpeg and writes the MP4. */
export async function encodeVideo(page, { frames, trackWav, outMp4, onProgress, preview = false }) {
  const ff = spawn('ffmpeg', x264Args({ frames, trackWav, outMp4, preview }), { stdio: ['pipe', 'ignore', 'pipe'], windowsHide: true });
  let err = '';
  ff.stderr.on('data', (d) => {
    err += d.toString();
  });
  const done = new Promise((resolve, reject) => {
    ff.on('error', reject);
    ff.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited with ${code}\n${err.slice(-2000)}`))));
  });
  try {
    for (let f = 0; f < frames; f++) {
      const png = await frameAt(page, f / FPS);
      if (!ff.stdin.write(png)) await once(ff.stdin, 'drain');
      if (onProgress && (f % 60 === 0 || f === frames - 1)) onProgress(f + 1, frames);
    }
  } finally {
    ff.stdin.end();
  }
  await done;
}

export async function writeCover(page, t, outJpg) {
  await writeFile(outJpg, await frameAt(page, t, 'jpeg', 92));
}
