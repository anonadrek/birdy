#!/usr/bin/env node
// Birdy social media: "See the song" videos, covers, captions and a posting schedule.
// Reads the species records (read only) and never posts anything anywhere.
//
//   node tools/social/see-the-song.mjs --list [--data <website dir>]
//   node tools/social/see-the-song.mjs --species Q25307,Q26026 [--out <dir>] [--start 2026-10-09]
//   node tools/social/see-the-song.mjs --all [--captions-only]
//
// Output per species: <out>/<en-slug>/see-the-song.mp4, cover.jpg, caption.json, render-info.json;
// for the batch: <out>/schedule.csv (one post a day at 08:00 Europe/Stockholm). Every file is
// written under a ".partial" name and renamed into place only when the species is complete,
// and a species that fails is reported and skipped: the batch goes on with the next one.
// Posting dates are fixed per species: schedule.csv is merged by QID, never rebuilt, so a
// re-run of one species keeps everyone's day and a failed species leaves its day empty.
import { parseArgs } from 'node:util';
import { mkdir, mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DEFAULT_DATA, loadRecords, mediaPath, qualifiesWithFiles } from './lib/species.mjs';
import { heroImage, usesShareAlike, VIDEO_SA_LICENCE } from './lib/licence.mjs';
import { buildCaptions, voiceWord, SHARE_ALIKE_LINE } from './lib/captions.mjs';
import { orderForSchedule, rowFor, planDates, mergeSchedule, parseCsv, toCsv, isValidIsoDate } from './lib/schedule.mjs';
import { prepareClip, buildTrack, measureLoudness, isCut } from './lib/audio.mjs';
import { probeDuration, run } from './lib/proc.mjs';
import { decodeMono, SAMPLE_RATE } from './lib/spectrogram.mjs';
import { haloData, fullestFrame } from './lib/halo.mjs';
import { stagedOutputs, writeFileAtomic } from './lib/atomic.mjs';
import { timeline, launchBrowser, openStage, encodeVideo, frameAt, FPS, MAX_CLIP_SEC } from './lib/render.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..', '..');
const FONTS = join(repo, 'website', 'public', 'fonts');
const MARK = join(repo, 'website', 'public', 'brand', 'birdy-bird.png');

const HELP = `See the song: Birdy's social media videos.

  --list               list the species that qualify (licence rule) and stop
  --species Q1,Q2      render these species (QIDs), posted in this order
  --all                render every qualifying species (published first, then the most reported)
  --data <dir>         website directory with src/data/species (default ${DEFAULT_DATA})
  --out <dir>          output directory (default tools/social/out)
  --start YYYY-MM-DD   first posting day for species not yet in schedule.csv (default 2026-10-09);
                       a species already in schedule.csv keeps its day (delete the file to plan again)
  --captions-only      write caption.json and schedule.csv without rendering video
  --preview            write key frames (preview-*.png) instead of the video, for a quick look
`;

const { values: opt } = parseArgs({
  options: {
    data: { type: 'string', default: DEFAULT_DATA },
    out: { type: 'string', default: join(here, 'out') },
    species: { type: 'string' },
    all: { type: 'boolean', default: false },
    list: { type: 'boolean', default: false },
    start: { type: 'string', default: '2026-10-09' },
    'captions-only': { type: 'boolean', default: false },
    preview: { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false },
  },
});

if (opt.help || (!opt.list && !opt.all && !opt.species)) {
  process.stdout.write(HELP);
  process.exit(opt.help ? 0 : 2);
}
if (!isValidIsoDate(opt.start)) {
  console.error(`--start must be YYYY-MM-DD, got "${opt.start}"`);
  process.exit(2);
}

const overrides = JSON.parse(await readFile(join(here, 'overrides.json'), 'utf8'));
const records = await loadRecords(opt.data);
const byQid = new Map(records.map((r) => [r.qid, r]));

async function qualifying() {
  const ok = [];
  for (const r of records) if ((await qualifiesWithFiles(r, opt.data)).ok) ok.push(r);
  return orderForSchedule(ok);
}

if (opt.list) {
  const shown = await qualifying();
  console.log(`${shown.length} of ${records.length} species qualify (photo and recording CC0, public domain, CC BY or CC BY-SA), in posting order\n`);
  const pad = (s, n) => String(s).padEnd(n);
  console.log(`${pad('QID', 12)}${pad('en-slug', 28)}${pad('publish', 9)}${pad('photo', 16)}${pad('recording', 16)}${pad('sec', 5)}${pad('video', 14)}name`);
  for (const r of shown) {
    const hero = heroImage(r);
    console.log(`${pad(r.qid, 12)}${pad(r.slug.en, 28)}${pad(r.publish === true, 9)}${pad(hero.license, 16)}${pad(r.audio.license, 16)}${pad(r.audio.durationSec ?? '', 5)}${pad(usesShareAlike(r) ? VIDEO_SA_LICENCE : '', 14)}${r.names.en} (${r.names.sv})`);
    if (overrides[r.qid]?.note) console.log(`${' '.repeat(12)}note: ${overrides[r.qid].note}`);
  }
  process.exit(0);
}

// Selection: --species keeps the given order (the posting order); --all orders by the schedule rule.
let selection = [];
let failed = 0;
if (opt.all) selection = await qualifying();
else {
  for (const qid of opt.species.split(',').map((s) => s.trim()).filter(Boolean)) {
    const rec = byQid.get(qid);
    if (!rec) {
      console.error(`${qid}: no record in ${opt.data}`);
      failed++;
      continue;
    }
    const q = await qualifiesWithFiles(rec, opt.data);
    if (!q.ok) {
      console.error(`${qid} ${rec.names.en}: does not qualify: ${q.reasons.join('; ')}`);
      failed++;
      continue;
    }
    selection.push(rec);
  }
}

// Posting days, fixed per species, planned before anything is rendered.
const SCHEDULE = join(opt.out, 'schedule.csv');
let existingRows = [];
try {
  existingRows = parseCsv(await readFile(SCHEDULE, 'utf8'));
} catch (e) {
  if (e.code !== 'ENOENT') throw e;
}
let plan;
try {
  plan = planDates(selection.map((r) => r.qid), existingRows, { start: opt.start });
} catch (e) {
  console.error(e.message);
  process.exit(2);
}

function dataUri(file, buf) {
  const ext = extname(file).toLowerCase();
  const mime = { '.webp': 'image/webp', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png' }[ext];
  if (!mime) throw new Error(`unknown image type: ${file}`);
  return `data:${mime};base64,${buf.toString('base64')}`;
}

async function fonts() {
  const faces = [
    ['Caveat', '700', 'normal', 'caveat-bold.woff2'],
    ['DM Serif Display', '400', 'normal', 'dm-serif-display-regular.woff2'],
    ['DM Serif Display', '400', 'italic', 'dm-serif-display-italic.woff2'],
    ['Inter', '400', 'normal', 'inter-regular.woff2'],
    ['Inter', '600', 'normal', 'inter-semibold.woff2'],
  ];
  const out = [];
  for (const [family, weight, style, file] of faces) out.push({ family, weight, style, b64: (await readFile(join(FONTS, file))).toString('base64') });
  return out;
}

async function videoFacts(mp4) {
  const { stdout } = await run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration:stream=codec_type,codec_name,width,height,r_frame_rate,nb_frames,sample_rate,channels', '-of', 'json', mp4]);
  return JSON.parse(stdout.toString());
}

/** Where to take a YouTube Shorts thumbnail if cover.jpg cannot be uploaded. */
function thumbnailNote(info) {
  if (!info?.coverAt || !info?.timeline?.R) return '';
  return `If YouTube will not take cover.jpg, choose the frame at ${info.coverAt.toFixed(1)} s: the ring at its fullest, before the reveal at ${info.timeline.R.toFixed(1)} s (the bird is a blur there; the frame at 0 s shows no bird at all).`;
}

async function readInfo(dir) {
  try {
    return JSON.parse(await readFile(join(dir, 'render-info.json'), 'utf8'));
  } catch {
    return null;
  }
}

const f32b64 = (a) => Buffer.from(a.buffer, a.byteOffset, a.byteLength).toString('base64');

function captionJson(rec, captions) {
  const { instagram, facebook, youtube, link, credit, creditWithUrls, videoLicence, hashtags } = captions;
  return `${JSON.stringify({ qid: rec.qid, slug: rec.slug.en, names: rec.names, publish: rec.publish === true, instagram, facebook, youtube, link, credit, creditWithUrls, videoLicence, hashtags }, null, 2)}\n`;
}

/** Renders one species into `out` (staged); returns a line for the log. */
async function renderSpecies(rec, out, work, assets) {
  const label = `${rec.qid} ${rec.names.en}`;
  const audioSrc = mediaPath(opt.data, rec.audio.file);
  if (opt['captions-only']) {
    const trimmed = rec.audio.trimmed === true || isCut(await probeDuration(audioSrc), MAX_CLIP_SEC);
    const captions = buildCaptions(rec, { trimmed });
    await out.write('caption.json', captionJson(rec, captions));
    return { captions, info: await readInfo(join(opt.out, rec.slug.en)), line: `${label}: captions written` };
  }

  const clip = await prepareClip(audioSrc, work, { maxSec: MAX_CLIP_SEC });
  const trimmed = rec.audio.trimmed === true || clip.cut;
  const captions = buildCaptions(rec, { trimmed });
  const samples = await decodeMono(clip.clip);
  const D = samples.length / SAMPLE_RATE;
  const tl = timeline(D);
  const halo = haloData(samples, { fps: FPS });
  const coverAt = tl.A0 + fullestFrame(halo) / FPS;
  const track = await buildTrack(clip.clip, work, { leadSec: tl.A0, totalSec: tl.T });
  const heroFile = mediaPath(opt.data, heroImage(rec).file);
  const creditLines = captions.credit.split(' · ');
  if (usesShareAlike(rec)) creditLines.push(SHARE_ALIKE_LINE);
  const config = {
    times: { A0: tl.A0, E: tl.E, settled: tl.settled, R: tl.R },
    photo: { src: dataUri(heroFile, await readFile(heroFile)), focus: overrides[rec.qid]?.focus ?? { x: 0.5, y: 0.5 } },
    halo: { fps: halo.fps, bands: halo.bands, frames: halo.frames, levelB64: f32b64(halo.level) },
    markSrc: assets.markSrc,
    fonts: assets.fonts,
    names: rec.names,
    word: voiceWord(rec),
    creditLines,
  };

  const { page, layout } = await openStage(assets.browser, config);
  const t0 = Date.now();
  try {
    if (opt.preview) {
      const keys = { start: 0, 'mid-song': tl.A0 + D / 2, settled: tl.settled + 0.05, reveal: tl.R + 1.0, name: tl.R + 2.4, end: tl.T - 1 / FPS };
      for (const [name, t] of Object.entries(keys)) await out.write(`preview-${name}.png`, await frameAt(page, t));
      await out.write('preview-cover.jpg', await frameAt(page, coverAt, { type: 'jpeg', quality: 92, cover: true }));
      return { captions, info: null, line: `${label}: preview frames written, layout ${JSON.stringify(layout)}` };
    }
    const mp4 = out.path('see-the-song.mp4');
    await encodeVideo(page, { frames: tl.frames, trackWav: track, outMp4: mp4, onProgress: (n, total) => process.stdout.write(`\r${label}: frame ${n}/${total}`) });
    process.stdout.write('\n');
    await out.write('cover.jpg', await frameAt(page, coverAt, { type: 'jpeg', quality: 92, cover: true }));
    const loudness = await measureLoudness(mp4);
    const info = {
      qid: rec.qid,
      style: 'halo',
      timeline: tl,
      fps: FPS,
      coverAt,
      clip: { seconds: D, sourceSeconds: clip.sourceDur, cut: clip.cut, trimmedCredit: trimmed, measuredInputLufs: clip.measuredInputLufs, gainDb: clip.gainDb, clipLoudness: clip.clipLoudness },
      halo: { fmin: halo.fmin, fmax: halo.fmax, bands: halo.bands },
      layout,
      loudness,
      ffprobe: await videoFacts(mp4),
      videoLicence: usesShareAlike(rec) ? VIDEO_SA_LICENCE : null,
      renderSeconds: Math.round((Date.now() - t0) / 1000),
    };
    await out.write('render-info.json', `${JSON.stringify(info, null, 2)}\n`);
    await out.write('caption.json', captionJson(rec, captions));
    return { captions, info, line: `${label}: ${tl.T.toFixed(2)} s, ${tl.frames} frames, ${loudness.integrated} LUFS, peak ${loudness.truePeak} dBFS, ${info.renderSeconds} s to render` };
  } finally {
    await page.close().catch(() => {});
  }
}

await mkdir(opt.out, { recursive: true });
const needBrowser = !opt['captions-only'] && selection.length > 0;
const assets = {
  fonts: needBrowser ? await fonts() : null,
  markSrc: needBrowser ? dataUri(MARK, await readFile(MARK)) : null,
  browser: needBrowser ? await launchBrowser() : null,
};
const done = [];

try {
  for (const rec of selection) {
    const dir = join(opt.out, rec.slug.en);
    await mkdir(dir, { recursive: true });
    const out = stagedOutputs(dir);
    const work = await mkdtemp(join(tmpdir(), `birdy-social-${rec.qid}-`));
    try {
      if (assets.browser && !assets.browser.isConnected()) assets.browser = await launchBrowser();
      const { captions, info, line } = await renderSpecies(rec, out, work, assets);
      await out.commit();
      console.log(line);
      done.push({ record: rec, captions, info });
    } catch (e) {
      failed++;
      await out.discard();
      console.error(`\n${rec.qid} ${rec.names.en}: FAILED, skipped: ${e.stack || e}`);
    } finally {
      await rm(work, { recursive: true, force: true });
    }
  }
} finally {
  if (assets.browser) await assets.browser.close().catch(() => {});
}

if (!opt.preview && done.length) {
  const rows = done.map(({ record: r, captions: c, info }) =>
    rowFor(plan.get(r.qid), {
      qid: r.qid,
      slug: r.slug.en,
      name_en: r.names.en,
      name_sv: r.names.sv,
      publish: r.publish === true,
      video: `${r.slug.en}/see-the-song.mp4`,
      cover: `${r.slug.en}/cover.jpg`,
      caption_json: `${r.slug.en}/caption.json`,
      instagram: c.instagram,
      facebook: c.facebook,
      youtube_title: c.youtube.title,
      youtube_description: c.youtube.description,
      youtube_thumbnail: thumbnailNote(info),
      video_licence: c.videoLicence ?? '',
      note: overrides[r.qid]?.note ?? '',
    }),
  );
  const merged = mergeSchedule(existingRows, rows);
  const columns = ['date', 'time', 'timezone', 'datetime', 'qid', 'slug', 'name_en', 'name_sv', 'publish', 'video', 'cover', 'caption_json', 'instagram', 'facebook', 'youtube_title', 'youtube_description', 'youtube_thumbnail', 'video_licence', 'note'];
  await writeFileAtomic(SCHEDULE, toCsv(merged, columns));
  console.log(`schedule.csv: ${rows.length} updated, ${merged.length} posts from ${merged[0].date} to ${merged.at(-1).date}`);
}
if (failed) console.error(`${failed} species failed or did not qualify; see above.`);
process.exit(failed ? 1 : 0);
