#!/usr/bin/env node
// Adds the flock cover as a title card to the first second of every "See the song" video (Albin,
// 2026-10-08: "Go for it"): cover-flock.jpg, opaque from 0 to 0.9 s, fades out (alpha only, the
// video dissolving into view underneath) from 0.9 to 1.3 s, and from 1.3 s on the original video
// is unchanged. This makes the cover the video's first frame, which is what YouTube Shorts and
// TikTok use as the thumbnail, since neither takes an uploaded cover image from us.
//
// The audio is a stream copy throughout, never touched. cover-flock.jpg also becomes cover.jpg,
// so every path the scheduling runbook already uses keeps working.
//
//   node cover/title-card.mjs week1
//   node cover/title-card.mjs week1-reserves
//   node cover/title-card.mjs oct19-nov7
//
// Idempotent: the first run backs up the untouched original to see-the-song-v1.mp4 / cover-v1.jpg
// (once; a backup that already exists is left alone) and every run, including the first, rebuilds
// see-the-song.mp4 and cover.jpg from that backup plus the current cover-flock.jpg. Re-running
// never stacks a second title card on an already-titled video.
//
// QA per video, printed and left in <slug>/qa/: duration within one frame of the original; video
// width/height/pix_fmt/frame rate equal to the original; frame count equal; the audio stream
// byte-identical to the original's (md5 of the decoded stream, not the container); frame 0 close
// to cover.jpg; the frame at 2.0 s close to the original's frame at 2.0 s (every video here runs
// well past 2.0 s). "Close" is a small mean absolute difference over a downscaled frame, which
// absorbs JPEG/H.264 recompression noise without hiding an actual mismatch.
import { readFile, rename, rm, access, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseCsv } from '../lib/schedule.mjs';
import { run, probeDuration } from '../lib/proc.mjs';
import { writeFileAtomic, partialPath } from '../lib/atomic.mjs';
import { FPS, VIDEO_ENCODE_ARGS } from '../lib/render.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const social = join(here, '..');

const HOLD_SEC = 0.9; // the cover stays fully opaque until this moment
const FADE_SEC = 0.4; // then fades out (alpha only) over this long: gone by HOLD_SEC + FADE_SEC = 1.3 s
const OVERLAY_INPUT_SEC = 1.4; // > HOLD_SEC + FADE_SEC, margin so the fade finishes before this input ends
const COVER_DIFF_MAX = 8; // mean abs diff (0..255 per channel), frame 0 vs cover-flock.jpg
const SAME_DIFF_MAX = 3; // mean abs diff, frame at 2.0 s (new) vs frame at 2.0 s (original)

const set = process.argv[2];
if (!set) {
  process.stderr.write('usage: node cover/title-card.mjs <set>   (e.g. week1, week1-reserves, oct19-nov7)\n');
  process.exit(2);
}
const outDir = join(social, 'out', set);

async function exists(p) {
  try {
    await access(p);
    return true;
  } catch {
    return false;
  }
}

async function probeVideo(file) {
  const { stdout } = await run('ffprobe', ['-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=width,height,pix_fmt,r_frame_rate,profile,level,color_range,color_space', '-of', 'json', file]);
  return JSON.parse(stdout.toString()).streams[0];
}

async function countFrames(file) {
  const { stdout } = await run('ffprobe', ['-v', 'error', '-select_streams', 'v:0', '-count_frames', '-show_entries', 'stream=nb_read_frames', '-of', 'default=nw=1:nk=1', file]);
  return Number(stdout.toString().trim());
}

async function audioMd5(file) {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', file, '-map', '0:a', '-c', 'copy', '-f', 'md5', '-']);
  return stdout.toString().trim();
}

async function frameAtTime(file, t, outPath) {
  await run('ffmpeg', ['-hide_banner', '-v', 'error', '-y', '-ss', String(t), '-i', file, '-frames:v', '1', outPath]);
}

/** Raw RGB of `file`, downscaled to w x h, for a forgiving pixel comparison. */
async function smallRgb(file, w = 108, h = 192) {
  const { stdout } = await run('ffmpeg', ['-hide_banner', '-v', 'error', '-i', file, '-vf', `scale=${w}:${h}:flags=area`, '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']);
  if (stdout.length !== w * h * 3) throw new Error(`${file}: got ${stdout.length} bytes, expected ${w * h * 3}`);
  return stdout;
}

function meanAbsDiff(a, b) {
  let s = 0;
  for (let i = 0; i < a.length; i++) s += Math.abs(a[i] - b[i]);
  return s / a.length;
}

/**
 * Backs up the untouched original (once) and rebuilds see-the-song.mp4 + cover.jpg from that
 * backup plus the current cover-flock.jpg. Throws (and leaves the backup alone) if anything fails.
 */
async function titleCard(dir) {
  const video = join(dir, 'see-the-song.mp4');
  const v1Video = join(dir, 'see-the-song-v1.mp4');
  const cover = join(dir, 'cover.jpg');
  const v1Cover = join(dir, 'cover-v1.jpg');
  const flock = join(dir, 'cover-flock.jpg');

  if (!(await exists(flock))) throw new Error('missing cover-flock.jpg');
  if (!(await exists(v1Video))) {
    if (!(await exists(video))) throw new Error('missing see-the-song.mp4 (and no see-the-song-v1.mp4 backup either)');
    await rename(video, v1Video);
  }
  if (!(await exists(v1Cover)) && (await exists(cover))) await rename(cover, v1Cover);

  const meta = await probeVideo(v1Video);
  const width = Number(meta.width);
  const height = Number(meta.height);
  if (!width || !height) throw new Error(`could not read the video size of ${v1Video}`);

  const tmp = partialPath(video);
  const filter =
    `[1:v]scale=${width}:${height}:out_color_matrix=bt709:out_range=tv,format=yuva420p,` +
    `fade=t=out:st=${HOLD_SEC}:d=${FADE_SEC}:alpha=1,setpts=PTS-STARTPTS[ovl];` +
    `[0:v][ovl]overlay=x=0:y=0:eof_action=pass,format=yuv420p[outv]`;
  try {
    await run('ffmpeg', [
      '-hide_banner', '-nostats', '-loglevel', 'error', '-y',
      '-i', v1Video,
      '-loop', '1', '-framerate', String(FPS), '-t', String(OVERLAY_INPUT_SEC), '-i', flock,
      '-filter_complex', filter,
      '-map', '[outv]', '-map', '0:a',
      ...VIDEO_ENCODE_ARGS,
      '-c:a', 'copy',
      '-movflags', '+faststart',
      '-f', 'mp4', tmp,
    ]);
  } catch (e) {
    await rm(tmp, { force: true });
    throw e;
  }
  await rename(tmp, video);
  await writeFileAtomic(cover, await readFile(flock));
}

/** Compares the rebuilt video against its -v1 backup and its frame 0 against the cover. */
async function qa(dir) {
  const video = join(dir, 'see-the-song.mp4');
  const v1Video = join(dir, 'see-the-song-v1.mp4');
  const cover = join(dir, 'cover.jpg');
  const problems = [];

  const [newDur, oldDur] = await Promise.all([probeDuration(video), probeDuration(v1Video)]);
  if (Math.abs(newDur - oldDur) > 1 / FPS + 1e-6) problems.push(`duration ${newDur}s vs original ${oldDur}s`);

  const [newMeta, oldMeta] = await Promise.all([probeVideo(video), probeVideo(v1Video)]);
  for (const key of ['width', 'height', 'pix_fmt', 'r_frame_rate']) {
    if (String(newMeta[key]) !== String(oldMeta[key])) problems.push(`${key} ${newMeta[key]} vs original ${oldMeta[key]}`);
  }

  const [newFrames, oldFrames] = await Promise.all([countFrames(video), countFrames(v1Video)]);
  if (newFrames !== oldFrames) problems.push(`frame count ${newFrames} vs original ${oldFrames}`);

  const [newAudio, oldAudio] = await Promise.all([audioMd5(video), audioMd5(v1Video)]);
  if (newAudio !== oldAudio) problems.push(`audio md5 ${newAudio} vs original ${oldAudio}`);

  const qaDir = join(dir, 'qa');
  await mkdir(qaDir, { recursive: true });
  const f0 = join(qaDir, 'title-card-frame0.png');
  const f2New = join(qaDir, 'title-card-frame2s.png');
  const f2Old = join(qaDir, 'title-card-frame2s-original.png');
  await frameAtTime(video, 0, f0);
  await frameAtTime(video, 2.0, f2New);
  await frameAtTime(v1Video, 2.0, f2Old);

  const [rgbF0, rgbCover, rgbNew2, rgbOld2] = await Promise.all([smallRgb(f0), smallRgb(cover), smallRgb(f2New), smallRgb(f2Old)]);
  const coverDiff = Number(meanAbsDiff(rgbF0, rgbCover).toFixed(3));
  const sameDiff = Number(meanAbsDiff(rgbNew2, rgbOld2).toFixed(3));
  if (coverDiff > COVER_DIFF_MAX) problems.push(`frame 0 vs cover.jpg: mean abs diff ${coverDiff} (max ${COVER_DIFF_MAX})`);
  if (sameDiff > SAME_DIFF_MAX) problems.push(`frame at 2.0s vs original: mean abs diff ${sameDiff} (max ${SAME_DIFF_MAX})`);

  return { ok: problems.length === 0, problems, coverDiff, sameDiff, frames: newFrames, duration: newDur };
}

async function main() {
  const csvText = await readFile(join(outDir, 'schedule.csv'), 'utf8');
  const rows = parseCsv(csvText).filter((r) => r.qid);
  if (!rows.length) throw new Error(`no rows with a qid in ${join(outDir, 'schedule.csv')}`);

  const results = [];
  for (const { slug } of rows) {
    const dir = join(outDir, slug);
    try {
      await titleCard(dir);
      const report = await qa(dir);
      results.push({ slug, ...report });
      console.log(`${report.ok ? 'ok  ' : 'FAIL'}  ${slug.padEnd(28)} frame0 diff ${report.coverDiff}  2s diff ${report.sameDiff}  ${report.frames} frames  ${report.duration}s${report.ok ? '' : `\n      ${report.problems.join('\n      ')}`}`);
    } catch (e) {
      results.push({ slug, ok: false, problems: [String(e.message || e)] });
      console.log(`FAIL  ${slug.padEnd(28)} ${String(e.message || e)}`);
    }
  }

  const ok = results.filter((r) => r.ok).length;
  console.log(`\n${ok}/${results.length} ok` + (ok < results.length ? `, ${results.length - ok} failed` : ''));
  if (ok < results.length) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
