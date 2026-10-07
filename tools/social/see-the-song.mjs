#!/usr/bin/env node
// Birdy social media: "See the song" videos, covers, captions and a posting schedule.
// Reads the species records (read only) and never posts anything anywhere.
//
//   node tools/social/see-the-song.mjs --list [--data <website dir>] [--share-alike]
//   node tools/social/see-the-song.mjs --species Q25307,Q26026 [--out <dir>] [--start 2026-10-09]
//   node tools/social/see-the-song.mjs --all [--captions-only] [--share-alike]
//
// Output per species: <out>/<en-slug>/see-the-song.mp4, cover.jpg, caption.json, render-info.json;
// for the batch: <out>/schedule.csv (one post a day at 08:00 Europe/Stockholm).
import { parseArgs } from 'node:util';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { DEFAULT_DATA, loadRecords, mediaPath, qualifiesWithFiles } from './lib/species.mjs';
import { heroImage, usesShareAlike, VIDEO_SA_LICENCE } from './lib/licence.mjs';
import { buildCaptions, voiceWord, SHARE_ALIKE_LINE } from './lib/captions.mjs';
import { orderForSchedule, scheduleRows, toCsv, isValidIsoDate } from './lib/schedule.mjs';
import { prepareClip, buildTrack, measureLoudness, MAX_CLIP_SEC } from './lib/audio.mjs';
import { probeDuration, run } from './lib/proc.mjs';
import { decodeMono, SAMPLE_RATE } from './lib/spectrogram.mjs';
import { styleData, STYLE_LETTERS, STYLES } from './lib/style-config.mjs';
import { timeline, launchBrowser, openStage, encodeVideo, writeCover, frameAt, FPS } from './lib/render.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..', '..');
const FONTS = join(repo, 'website', 'public', 'fonts');
const MARK = join(repo, 'website', 'public', 'brand', 'birdy-bird.png');

const HELP = `See the song: Birdy's social media videos.

  --list               list the species that qualify (licence rule) and stop
  --species Q1,Q2      render these species (QIDs)
  --all                render every qualifying species
  --data <dir>         website directory with src/data/species (default ${DEFAULT_DATA})
  --out <dir>          output directory (default tools/social/out)
  --start YYYY-MM-DD   first posting day in schedule.csv (default 2026-10-09)
  --share-alike        also admit CC BY-SA photos and recordings (the video is then CC BY-SA 4.0)
  --captions-only      write caption.json and schedule.csv without rendering video
  --preview            write key frames (preview-*.png) instead of the video, for a quick look
  --style <name>       sound animation: glow (first version), sonagram, waveform, halo or notes
  --clip-start <s>     start the clip this far into the recording (previews)
  --clip-seconds <s>   use at most this much of the recording (previews)
  --preview-video      a short, light preview clip and still in <out>/previews/ (no schedule)
`;

const { values: opt } = parseArgs({
  options: {
    data: { type: 'string', default: DEFAULT_DATA },
    out: { type: 'string', default: join(here, 'out') },
    species: { type: 'string' },
    all: { type: 'boolean', default: false },
    list: { type: 'boolean', default: false },
    start: { type: 'string', default: '2026-10-09' },
    'share-alike': { type: 'boolean', default: false },
    'captions-only': { type: 'boolean', default: false },
    preview: { type: 'boolean', default: false },
    style: { type: 'string', default: 'glow' },
    'clip-start': { type: 'string', default: '0' },
    'clip-seconds': { type: 'string' },
    'preview-video': { type: 'boolean', default: false },
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

const shareAlike = opt['share-alike'];
if (!STYLES.includes(opt.style)) {
  console.error(`--style must be one of ${STYLES.join(', ')}`);
  process.exit(2);
}
const clipStart = Number(opt['clip-start']);
const clipSeconds = opt['clip-seconds'] ? Number(opt['clip-seconds']) : MAX_CLIP_SEC;
const previewVideo = opt['preview-video'];
const overrides = JSON.parse(await readFile(join(here, 'overrides.json'), 'utf8'));
const records = await loadRecords(opt.data);
const byQid = new Map(records.map((r) => [r.qid, r]));

async function qualifying(mode) {
  const ok = [];
  for (const r of records) if ((await qualifiesWithFiles(r, opt.data, { shareAlike: mode })).ok) ok.push(r);
  return orderForSchedule(ok);
}

if (opt.list) {
  const strict = await qualifying(false);
  const sa = await qualifying(true);
  console.log(`Strict (CC0, public domain, CC BY): ${strict.length} of ${records.length} species qualify`);
  console.log(`Share-alike (also CC BY-SA, --share-alike): ${sa.length} of ${records.length} species qualify`);
  const shown = shareAlike ? sa : strict;
  console.log(`\nListed: ${shareAlike ? 'share-alike' : 'strict'} mode, in posting order\n`);
  const pad = (s, n) => String(s).padEnd(n);
  console.log(`${pad('QID', 12)}${pad('en-slug', 28)}${pad('publish', 9)}${pad('photo', 16)}${pad('recording', 16)}${pad('sec', 5)}name`);
  for (const r of shown) {
    const hero = heroImage(r);
    console.log(`${pad(r.qid, 12)}${pad(r.slug.en, 28)}${pad(r.publish === true, 9)}${pad(hero.license, 16)}${pad(r.audio.license, 16)}${pad(r.audio.durationSec ?? '', 5)}${r.names.en} (${r.names.sv})`);
    if (overrides[r.qid]?.note) console.log(`${' '.repeat(12)}note: ${overrides[r.qid].note}`);
  }
  process.exit(0);
}

// Selection
let selection = [];
let failed = 0;
if (opt.all) selection = await qualifying(shareAlike);
else {
  for (const qid of opt.species.split(',').map((s) => s.trim()).filter(Boolean)) {
    const rec = byQid.get(qid);
    if (!rec) {
      console.error(`${qid}: no record in ${opt.data}`);
      failed++;
      continue;
    }
    const q = await qualifiesWithFiles(rec, opt.data, { shareAlike });
    if (!q.ok) {
      console.error(`${qid} ${rec.names.en}: does not qualify: ${q.reasons.join('; ')}`);
      failed++;
      continue;
    }
    selection.push(rec);
  }
  selection = orderForSchedule(selection);
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

await mkdir(opt.out, { recursive: true });
const fontFaces = opt['captions-only'] ? null : await fonts();
const markSrc = opt['captions-only'] ? null : dataUri(MARK, await readFile(MARK));
const browser = opt['captions-only'] || selection.length === 0 ? null : await launchBrowser();
const done = [];

try {
  for (const rec of selection) {
    const label = `${rec.qid} ${rec.names.en}`;
    const dir = join(opt.out, rec.slug.en);
    await mkdir(dir, { recursive: true });
    const work = await mkdtemp(join(tmpdir(), `birdy-social-${rec.qid}-`));
    try {
      const audioSrc = mediaPath(opt.data, rec.audio.file);
      const clip = opt['captions-only'] ? { cut: (await probeDuration(audioSrc)) > MAX_CLIP_SEC + 0.05 } : await prepareClip(audioSrc, work, { start: clipStart, maxSec: clipSeconds });
      const trimmed = rec.audio.trimmed === true || clip.cut;
      const captions = buildCaptions(rec, { trimmed });
      const word = voiceWord(rec);
      await writeFile(
        join(dir, 'caption.json'),
        `${JSON.stringify({ qid: rec.qid, slug: rec.slug.en, names: rec.names, publish: rec.publish === true, instagram: captions.instagram, facebook: captions.facebook, youtube: captions.youtube, link: captions.link, credit: captions.credit, videoLicence: captions.videoLicence, hashtags: captions.hashtags }, null, 2)}\n`,
      );

      if (!opt['captions-only']) {
        const samples = await decodeMono(clip.clip);
        const D = samples.length / SAMPLE_RATE;
        const sd = styleData(opt.style, samples, { fps: FPS, D });
        const tl = timeline(D);
        if (previewVideo) {
          // A preview ends shortly after the name has been written.
          tl.frames = Math.round((tl.E + 3.0) * FPS);
          tl.T = tl.frames / FPS;
        }
        const track = await buildTrack(clip.clip, work, { leadSec: tl.A0, totalSec: tl.T });
        const hero = heroImage(rec);
        const heroFile = mediaPath(opt.data, hero.file);
        const creditLines = captions.credit.split(' · ');
        if (usesShareAlike(rec)) creditLines.push(SHARE_ALIKE_LINE);
        const config = {
          A0: tl.A0,
          D,
          photo: { src: dataUri(heroFile, await readFile(heroFile)), focus: overrides[rec.qid]?.focus ?? { x: 0.5, y: 0.5 } },
          markSrc,
          fonts: fontFaces,
          style: opt.style,
          [sd.key]: sd.data,
          names: rec.names,
          word,
          creditLines,
        };
        const previewDir = join(opt.out, 'previews');
        const previewBase = join(previewDir, `${STYLE_LETTERS[opt.style]}-${opt.style}-${rec.slug.en}`);
        if (previewVideo) await mkdir(previewDir, { recursive: true });
        const mp4 = previewVideo ? `${previewBase}.mp4` : join(dir, 'see-the-song.mp4');
        const { page, layout } = await openStage(browser, config);
        if (opt.preview) {
          const keys = { start: 0, 'mid-song': tl.A0 + D / 2, cover: tl.cover, writing: tl.E + 1.7, reveal: tl.reveal, end: tl.T - 1 / FPS };
          try {
            for (const [name, t] of Object.entries(keys)) await writeFile(join(dir, `preview-${name}.png`), await frameAt(page, t));
          } finally {
            await page.close();
          }
          console.log(`${label}: preview frames written, layout ${JSON.stringify(layout)}`);
          done.push({ record: rec, captions });
          continue;
        }
        const t0 = Date.now();
        try {
          await encodeVideo(page, {
            frames: tl.frames,
            trackWav: track,
            outMp4: mp4,
            preview: previewVideo,
            onProgress: (n, total) => process.stdout.write(`\r${label}: frame ${n}/${total}`),
          });
          process.stdout.write('\n');
          if (previewVideo) await writeCover(page, tl.A0 + sd.stillAt, `${previewBase}.jpg`);
          else await writeCover(page, tl.cover, join(dir, 'cover.jpg'));
        } finally {
          await page.close();
        }
        const loudness = await measureLoudness(mp4);
        const facts = await videoFacts(mp4);
        const info = {
          qid: rec.qid,
          timeline: tl,
          fps: FPS,
          clip: { seconds: D, sourceSeconds: clip.sourceDur, cutAt30s: clip.cut, trimmedCredit: trimmed, measuredInputLufs: clip.measuredInputLufs, gainDb: clip.gainDb, clipLoudness: clip.clipLoudness },
          style: opt.style,
          clipStart,
          spectrogram: sd.band,
          syllables: sd.syllables,
          stillAt: tl.A0 + sd.stillAt,
          layout,
          loudness,
          ffprobe: facts,
          videoLicence: usesShareAlike(rec) ? VIDEO_SA_LICENCE : null,
          renderSeconds: Math.round((Date.now() - t0) / 1000),
        };
        await writeFile(previewVideo ? `${previewBase}.json` : join(dir, 'render-info.json'), `${JSON.stringify(info, null, 2)}\n`);
        console.log(`${label}: ${tl.T.toFixed(2)} s, ${tl.frames} frames, ${loudness.integrated} LUFS, peak ${loudness.truePeak} dBFS, ${info.renderSeconds} s to render`);
      } else console.log(`${label}: captions written`);

      done.push({ record: rec, captions });
    } catch (e) {
      failed++;
      console.error(`\n${label}: FAILED: ${e.stack || e}`);
    } finally {
      await rm(work, { recursive: true, force: true });
    }
  }
} finally {
  if (browser) await browser.close();
}

const rows = scheduleRows(
  done.map(({ record: r, captions: c }) => ({
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
    video_licence: c.videoLicence ?? '',
    note: overrides[r.qid]?.note ?? '',
  })),
  { start: opt.start },
);
const columns = ['date', 'time', 'timezone', 'datetime', 'qid', 'slug', 'name_en', 'name_sv', 'publish', 'video', 'cover', 'caption_json', 'instagram', 'facebook', 'youtube_title', 'youtube_description', 'video_licence', 'note'];
if (rows.length && !opt.preview && !previewVideo) {
  await writeFile(join(opt.out, 'schedule.csv'), toCsv(rows, columns));
  console.log(`schedule.csv: ${rows.length} posts from ${rows[0].date} to ${rows.at(-1).date}`);
}
process.exit(failed ? 1 : 0);
