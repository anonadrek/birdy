#!/usr/bin/env node
// Adds the flock cover as a title card to the first second AND the last fraction of a second of
// every "See the song" video (Albin, 2026-10-08: "Go for it" for the opening; 2026-10-09: close
// the loop so the end flows back into the title card). Opening: cover-flock.jpg, opaque from 0 to
// 0.9 s, fades out (alpha only, the video dissolving into view underneath) from 0.9 to 1.3 s.
// Closing: the same cover fades back in (alpha only) from duration-0.6 s to duration-0.1 s, then
// stays fully opaque for the final 0.1 s, so the very last frame is the cover again, matching the
// first. In between, the original video is unchanged. This also makes the cover the video's first
// frame, which is what YouTube Shorts and TikTok use as the thumbnail, since neither takes an
// uploaded cover image from us. The matching first/last frame is what makes the loop seamless:
// whichever platform repeats the clip (Instagram, TikTok, YouTube, Facebook), the cut from last
// frame to first frame lands on the same image instead of a visible jump.
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
// byte-identical to the original's (md5 of the decoded stream, not the container); frame 0 AND
// the very last frame both close to cover.jpg; the frame at 2.0 s AND at duration-1.0 s both close
// to the original's frame at that same instant (every video here runs well past those points, and
// duration-1.0 s sits comfortably before the closing fade starts, so this also proves the closing
// fade doesn't start early). "Close" is a small mean absolute difference over a downscaled frame,
// which absorbs JPEG/H.264 recompression noise without hiding an actual mismatch.
import { readFile, rename, rm, access, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseCsv } from '../lib/schedule.mjs';
import { run, probeDuration } from '../lib/proc.mjs';
import { writeFileAtomic, partialPath } from '../lib/atomic.mjs';
import { FPS, VIDEO_ENCODE_ARGS, MAX_VIDEO_SEC } from '../lib/render.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const social = join(here, '..');

const HOLD_SEC = 0.9; // the cover stays fully opaque until this moment
const FADE_SEC = 0.4; // then fades out (alpha only) over this long: gone by HOLD_SEC + FADE_SEC = 1.3 s
const CLOSE_FADE_SEC = 0.5; // the closing fade-in (alpha only) lasts this long
const CLOSE_HOLD_SEC = 0.1; // ...and then stays fully opaque for this long, through the very last frame
const LOOP_SEC = MAX_VIDEO_SEC + 1; // longer than any video here, so the looped cover input never ends first
const COVER_DIFF_MAX = 8; // mean abs diff (0..255 per channel), a frame vs cover-flock.jpg (frame 0, and the last frame)
const SAME_DIFF_MAX = 3; // mean abs diff, an unfaded frame (new) vs the same instant in the original (2.0 s, and duration-1.0 s)

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

  const duration = await probeDuration(v1Video);
  // The closing fade reaches full opacity at duration - CLOSE_HOLD_SEC, then holds to the end.
  const closeStart = duration - (CLOSE_FADE_SEC + CLOSE_HOLD_SEC);
  if (closeStart < HOLD_SEC + FADE_SEC) throw new Error(`${v1Video} is only ${duration.toFixed(3)}s, too short for the opening and closing fades not to overlap`);
  const closeStartStr = closeStart.toFixed(3);

  const tmp = partialPath(video);
  // One looped cover input (full video length + margin), split into an opening chain (fades out
  // alpha from HOLD_SEC) and a closing chain (fades in alpha from closeStart). Before its own
  // fade starts, each chain's alpha is the format filter's default — fully opaque — which is why
  // the closing overlay is `enable`d only from closeStart on: without that gate it would cover the
  // whole video, not just the end.
  const filter =
    `[1:v]scale=${width}:${height}:out_color_matrix=bt709:out_range=tv,format=yuva420p,split=2[cov_o][cov_c];` +
    `[cov_o]fade=t=out:st=${HOLD_SEC}:d=${FADE_SEC}:alpha=1,setpts=PTS-STARTPTS[ovl_o];` +
    `[cov_c]fade=t=in:st=${closeStartStr}:d=${CLOSE_FADE_SEC}:alpha=1,setpts=PTS-STARTPTS[ovl_c];` +
    `[0:v][ovl_o]overlay=x=0:y=0:eof_action=pass[stage1];` +
    `[stage1][ovl_c]overlay=x=0:y=0:enable='gte(t,${closeStartStr})':eof_action=pass,format=yuv420p[outv]`;
  try {
    await run('ffmpeg', [
      '-hide_banner', '-nostats', '-loglevel', 'error', '-y',
      '-i', v1Video,
      '-loop', '1', '-framerate', String(FPS), '-t', String(LOOP_SEC), '-i', flock,
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

  const lastT = (newFrames - 1) / FPS; // the exact presentation time of the last frame
  const earlyT = newDur - 1.0; // duration - 1.0s: well before closeStart (duration - 0.6s), must still be unfaded

  const qaDir = join(dir, 'qa');
  await mkdir(qaDir, { recursive: true });
  const f0 = join(qaDir, 'title-card-frame0.png');
  const f2New = join(qaDir, 'title-card-frame2s.png');
  const f2Old = join(qaDir, 'title-card-frame2s-original.png');
  const fEarlyNew = join(qaDir, 'title-card-frame-early.png');
  const fEarlyOld = join(qaDir, 'title-card-frame-early-original.png');
  const fLast = join(qaDir, 'title-card-frame-last.png');
  await Promise.all([
    frameAtTime(video, 0, f0),
    frameAtTime(video, 2.0, f2New),
    frameAtTime(v1Video, 2.0, f2Old),
    frameAtTime(video, earlyT, fEarlyNew),
    frameAtTime(v1Video, earlyT, fEarlyOld),
    frameAtTime(video, lastT, fLast),
  ]);

  const [rgbF0, rgbCover, rgbNew2, rgbOld2, rgbEarlyNew, rgbEarlyOld, rgbLast] = await Promise.all([
    smallRgb(f0), smallRgb(cover), smallRgb(f2New), smallRgb(f2Old), smallRgb(fEarlyNew), smallRgb(fEarlyOld), smallRgb(fLast),
  ]);
  const coverDiff = Number(meanAbsDiff(rgbF0, rgbCover).toFixed(3));
  const sameDiff = Number(meanAbsDiff(rgbNew2, rgbOld2).toFixed(3));
  const earlyDiff = Number(meanAbsDiff(rgbEarlyNew, rgbEarlyOld).toFixed(3));
  const lastDiff = Number(meanAbsDiff(rgbLast, rgbCover).toFixed(3));
  if (coverDiff > COVER_DIFF_MAX) problems.push(`frame 0 vs cover.jpg: mean abs diff ${coverDiff} (max ${COVER_DIFF_MAX})`);
  if (sameDiff > SAME_DIFF_MAX) problems.push(`frame at 2.0s vs original: mean abs diff ${sameDiff} (max ${SAME_DIFF_MAX})`);
  if (earlyDiff > SAME_DIFF_MAX) problems.push(`frame at duration-1.0s (${earlyT.toFixed(3)}s) vs original: mean abs diff ${earlyDiff} (max ${SAME_DIFF_MAX})`);
  if (lastDiff > COVER_DIFF_MAX) problems.push(`last frame vs cover.jpg: mean abs diff ${lastDiff} (max ${COVER_DIFF_MAX})`);

  return { ok: problems.length === 0, problems, coverDiff, sameDiff, earlyDiff, lastDiff, frames: newFrames, duration: newDur };
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
      console.log(`${report.ok ? 'ok  ' : 'FAIL'}  ${slug.padEnd(28)} frame0 diff ${report.coverDiff}  2s diff ${report.sameDiff}  early diff ${report.earlyDiff}  last diff ${report.lastDiff}  ${report.frames} frames  ${report.duration}s${report.ok ? '' : `\n      ${report.problems.join('\n      ')}`}`);
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
