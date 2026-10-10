#!/usr/bin/env node
// The field notes' own pictures in the Flock look (2026-10-10): the same peach paper and the same 839-bird flock as
// the home page and the See the song covers (tools/social/cover/flock-cover.html), drawn wide at 1600 × 840 (files at
// twice that) so each picture works as the article's band, the card on the blog and the home page, and the share image (1200 × 630).
//
//   node tools/render-note-art.mjs                    the notes' pictures into src/assets/photos/
//   node tools/render-note-art.mjs see-the-song       one picture (a proposal only when named)
//   node tools/render-note-art.mjs --out <dir> ...    somewhere else (previews), as PNG
//
// The silhouettes are the covers' own (tools/social/cover/sil, from PhyloPic, CC0). Seeded, so a run gives the same
// birds every time.
import { existsSync, mkdirSync, readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';
import sharp from 'sharp';
import { MARK } from '../src/components/hero/flock-data.mjs';
import { COLOURS } from '../src/components/hero/flock.mjs';

const root = resolve(import.meta.dirname, '..');
const repo = resolve(root, '..');
const W = 1600;
const H = 840;
/** Drawn at twice the size, so the article's plate stays sharp on high-density screens (3200 × 1680 files). */
const DPR = 2;

const font = (file) => pathToFileURL(join(root, 'public/fonts', file)).href;
const dataUrl = (file, type) => `data:${type};base64,${readFileSync(file).toString('base64')}`;
const silhouette = (qid) => dataUrl(join(repo, 'tools/social/cover/sil', `${qid}.svg`), 'image/svg+xml');

/**
 * The pictures, one file per language (the words in a picture follow its page). `flock` fills a species' silhouette
 * with the whole flock (one bird per species in Birdy, 839), as on the See the song covers; `polaroid` is a photo
 * taped onto the paper, as Dagens fågel on the home page.
 */
const ROBIN_PHOTO = {
  photo: join(root, 'src/assets/photos/rodhake-q25334.webp'),
  // The robin's part of the 1600 × 1200 photo (Rob Hille, public domain), a 5:4 crop around the bird.
  crop: { x: 420, y: 250, w: 1060, h: 848 },
};
export const PICTURES = {
  // See the song: the series' new cover (Albin 2026-10-10, "the new minityr picture"), wide. The blue tit is the
  // series' first video, the one in the note.
  'see-the-song': {
    file: 'see-the-song-flock-q25404',
    flock: { qid: 'Q25404', box: { x: 780, y: 80, w: 740, h: 680 }, scale: 0.86, leaveSide: 1, litNear: { x: 1250, y: 400 } },
    words: {
      x: 128, w: 600,
      en: [
        { cls: 'kick', text: 'sound on' },
        { cls: 'clue', text: 'Often hangs upside down to find food.' },
        { cls: 'ask', text: 'Whose song is this?' },
      ],
      sv: [
        { cls: 'kick', text: 'ljud på' },
        { cls: 'clue', text: 'Hänger ofta upp och ner för att hitta mat.' },
        { cls: 'ask', text: 'Vems sång är det här?' },
      ],
    },
  },
  // Why Birdy exists, three directions for Albin's choice (2026-10-10). A: the flock forms the note's own bird, the
  // robin, like the See the song cover, mirrored, with Birdy's line beside it.
  'why-birdy-flock': {
    file: 'why-birdy-flock-q25334',
    // The robin's own silhouette (PhyloPic) faces left; mirrored here so it faces right, the same way as every
    // small bird that fills it (and the same way as the home hero flock and See the song), so the flock and the
    // bird it forms fly together instead of past each other.
    flock: { qid: 'Q25334', box: { x: 150, y: 70, w: 560, h: 700 }, scale: 0.84, mirror: true, leaveSide: 1, litNear: { x: 300, y: 420 } },
    words: {
      x: 860, w: 620,
      en: [
        { cls: 'kick', text: 'why Birdy exists' },
        { cls: 'clue', text: 'Know the bird. Keep the moment.' },
        { cls: 'ask', text: 'European Robin' },
      ],
      sv: [
        { cls: 'kick', text: 'varför Birdy finns' },
        { cls: 'clue', text: 'Känn igen fågeln. Bevara stunden.' },
        { cls: 'ask', text: 'Rödhake' },
      ],
    },
  },
  // B: the robin's photo taped into the journal, a few birds of the flock on their way. B and C are proposals, drawn
  // only when named, and go once Albin has picked (the choices page of 2026-10-10).
  'why-birdy-plate': {
    proposal: true,
    file: 'why-birdy-plate-q25334',
    polaroid: { ...ROBIN_PHOTO, x: 600, y: 92, w: 480, rot: -3.5, caption: { en: 'European Robin', sv: 'Rödhake' } },
    trail: { from: { x: 600, y: 520 }, to: { x: 140, y: 770 }, n: 70 },
  },
  // C: the photo and the flock that forms the same bird.
  'why-birdy-both': {
    proposal: true,
    file: 'why-birdy-both-q25334',
    flock: { qid: 'Q25334', box: { x: 860, y: 60, w: 520, h: 700 }, scale: 0.82, leaveSide: 1 },
    polaroid: { ...ROBIN_PHOTO, x: 330, y: 170, w: 400, rot: -4, caption: { en: 'European Robin', sv: 'Rödhake' } },
  },
};

function page(pic) {
  const flock = pic.flock ? { ...pic.flock, sil: silhouette(pic.flock.qid) } : null;
  const polaroid = pic.polaroid ? { ...pic.polaroid, src: null } : null;
  return `<!doctype html><meta charset="utf-8"><style>
@font-face { font-family: Caveat; src: url('${font('caveat-bold.woff2')}') format('woff2'); font-weight: 700; }
@font-face { font-family: Inter; src: url('${font('inter-semibold.woff2')}') format('woff2'); font-weight: 600; }
* { box-sizing: border-box; margin: 0; }
html, body { width: ${W}px; height: ${H}px; overflow: hidden; }
#art { position: relative; width: ${W}px; height: ${H}px; overflow: hidden; color: #302019;
  background:
    radial-gradient(circle at 16% 14%, rgba(255, 255, 255, .55), transparent 44%),
    radial-gradient(circle at 88% 90%, rgba(232, 176, 128, .26), transparent 48%),
    linear-gradient(168deg, #FEEBD6, #FDE5CB 50%, #F8D6B4); }
canvas { position: absolute; inset: 0; width: ${W}px; height: ${H}px; }
.col { position: absolute; display: flex; flex-direction: column; align-items: flex-start; }
.kick { font: 700 58px/1 Caveat; color: #9A4526; transform: rotate(-3deg); transform-origin: left center; }
.clue { margin-top: 18px; font: 700 86px/1.0 Caveat; color: #302019; text-wrap: balance; }
.ask { margin-top: 44px; margin-left: 4px; font: 600 25px/1 Inter; letter-spacing: .16em; text-transform: uppercase; color: #9A4526; }
.cap { font: 700 46px/1 Caveat; color: #9A4526; text-align: center; transform: rotate(-2deg); }
.pol { position: absolute; background: #FFFAF1; padding: 22px 22px 0; box-shadow: 0 2px 3px rgba(42, 29, 23, .12), 0 28px 50px rgba(42, 29, 23, .22); }
.pol canvas { position: static; display: block; width: 100%; height: auto; }
.pol .pc { font: 700 44px/1 Caveat; color: #302019; padding: 20px 4px 8px; }
.pol .ps { font: 700 30px/1 Caveat; color: #9A4526; padding: 0 4px 26px; }
.tape { position: absolute; left: 50%; top: -22px; width: 150px; height: 44px; margin-left: -75px; background: rgba(242, 178, 122, .55); box-shadow: 0 1px 2px rgba(42, 29, 23, .08); transform: rotate(-4deg); }
</style>
<div id="art"><canvas id="c" width="${W * DPR}" height="${H * DPR}"></canvas></div>
<script>
const W = ${W}, H = ${H};
const MARK = ${JSON.stringify({ path: MARK.path, w: MARK.w, cx: MARK.cx, cy: MARK.cy })};
const COLOURS = ${JSON.stringify(COLOURS)};
const PIC = ${JSON.stringify({ ...pic, flock, polaroid })};
const mark = new Path2D(MARK.path);
const art = document.getElementById('art');
const ctx = document.getElementById('c').getContext('2d');
ctx.scale(${DPR}, ${DPR});

function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s + 0x6D2B79F5) >>> 0; let t = s; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
function keyIndex(key, n) { let h = 0; for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0; return n > 0 ? h % n : h; }
function clamp(v, lo, hi) { return Math.min(Math.max(v, lo), hi); }
function bird(g, x, y, size, rot, colour, alpha) {
  const k = size / MARK.w;
  g.save(); g.globalAlpha = alpha; g.translate(x, y); g.rotate(rot * Math.PI / 180); g.scale(k, k); g.translate(-MARK.cx, -MARK.cy);
  g.fillStyle = colour; g.fill(mark); g.restore();
}
function load(src) { return new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = fail; i.src = src; }); }

// The covers' silhouette flock (tools/social/cover/flock-cover.html, direction D), on a wide canvas: exactly 839
// birds, a darker outline of birds along the edge, the rest inside, a few leaving, and the species' own bird lit.
async function silhouetteFlock(f) {
  const img = await load(f.sil);
  const ar = img.naturalWidth / img.naturalHeight;
  let w = f.box.w, h = w / ar;
  if (h > f.box.h) { h = f.box.h; w = h * ar; }
  const x0 = f.box.x + (f.box.w - w) / 2, y0 = f.box.y + (f.box.h - h) / 2;
  const m = document.createElement('canvas'); m.width = W; m.height = H;
  const mg = m.getContext('2d');
  mg.save();
  // Some PhyloPic silhouettes face left; mirrored in place (same box) so the big bird faces the way the flock flies.
  if (f.mirror) { mg.translate(x0 + w, y0); mg.scale(-1, 1); mg.drawImage(img, 0, 0, w, h); }
  else mg.drawImage(img, x0, y0, w, h);
  mg.restore();
  const px = mg.getImageData(0, 0, W, H).data;
  const inside = (x, y) => { x = Math.round(x); y = Math.round(y); return x >= 0 && y >= 0 && x < W && y < H && px[(y * W + x) * 4 + 3] > 128; };
  const r = rng(7919 ^ keyIndex(f.qid, 0));
  const k = f.scale;
  const CELL = 8, hash = {};
  const near = (x, y, d) => {
    const cx = Math.floor(x / CELL), cy = Math.floor(y / CELL), n = Math.ceil(d / CELL);
    for (let i = -n; i <= n; i++) for (let j = -n; j <= n; j++) {
      const list = hash[(cx + i) + ',' + (cy + j)]; if (!list) continue;
      for (const p of list) if ((p[0] - x) ** 2 + (p[1] - y) ** 2 < d * d) return true;
    }
    return false;
  };
  const add = (x, y) => { const key = Math.floor(x / CELL) + ',' + Math.floor(y / CELL); (hash[key] = hash[key] || []).push([x, y]); };
  const cand = [];
  for (let y = Math.floor(y0); y < y0 + h; y += 2) for (let x = Math.floor(x0); x < x0 + w; x += 2) {
    if (!inside(x, y)) continue;
    const e = 5 * k;
    if (!inside(x - e, y) || !inside(x + e, y) || !inside(x, y - e) || !inside(x, y + e)) cand.push([x, y]);
  }
  for (let s = cand.length - 1; s > 0; s--) { const t = Math.floor(r() * (s + 1)); [cand[s], cand[t]] = [cand[t], cand[s]]; }
  const edge = [];
  for (const p of cand) if (!near(p[0], p[1], 15 * k)) { edge.push(p); add(p[0], p[1]); }
  const LEAVE = 7, TOTAL = 839;
  let inner = [];
  for (let cell = 30 * k; cell > 6 * k; cell -= 0.5) {
    inner = [];
    for (let gy = y0; gy < y0 + h; gy += cell) for (let gx = x0; gx < x0 + w; gx += cell) {
      const jx = gx + (r() - 0.5) * cell * 0.8, jy = gy + (r() - 0.5) * cell * 0.8;
      if (inside(jx, jy) && inside(jx - 4 * k, jy) && inside(jx + 4 * k, jy) && !near(jx, jy, 11 * k)) inner.push([jx, jy]);
    }
    if (edge.length + inner.length + LEAVE >= TOTAL) break;
  }
  while (edge.length + inner.length + LEAVE > TOTAL) { if (inner.length) inner.splice(Math.floor(r() * inner.length), 1); else edge.pop(); }
  // litNear is written for the unmirrored silhouette; mirror it too, so the lit bird still lands on the breast.
  const litNear = f.litNear && f.mirror ? { x: 2 * x0 + w - f.litNear.x, y: f.litNear.y } : f.litNear;
  let at = keyIndex(f.qid, inner.length);
  if (litNear && inner.length) {
    let best = Infinity;
    inner.forEach((p, i) => { const d = (p[0] - litNear.x) ** 2 + (p[1] - litNear.y) ** 2; if (d < best) { best = d; at = i; } });
  }
  const lit = inner.length ? inner.splice(at, 1)[0] : edge.pop();
  const pick = (wts) => { const u = r(); let a = 0; for (let i = 0; i < wts.length; i++) { a += wts[i]; if (u < a) return COLOURS[i]; } return COLOURS[1]; };
  // Every bird in the flock points the way the big bird faces: the shared heading of -8 degrees (a slight climb),
  // at most about +-4 degrees off it, so the mass reads as one body in flight, not a jittery swarm.
  for (const p of inner) bird(ctx, p[0], p[1], (13 + r() * 12) * k, -8 + (r() - 0.5) * 8, pick([0.25, 0.4, 0.25, 0.1]), 0.72 + r() * 0.28);
  for (const p of edge) bird(ctx, p[0], p[1], (19 + r() * 7) * k, -8 + (r() - 0.5) * 8, pick([0.05, 0.2, 0.5, 0.25]), 0.9 + r() * 0.1);
  // A few leave from the front of the head (the side the bird faces, near its top), in a gentle rising arc ahead
  // of the flock. Each one sits at its own point t along the arc and is turned to the arc's tangent there, so its
  // heading starts close to the flock's own -8 degrees and climbs the further it pulls away, reading as the
  // flock's own leaders heading the same way rather than a separate, randomly aimed stream. Smaller and fainter
  // with distance (increasing n), as they recede ahead of the flock.
  const side = f.leaveSide >= 0 ? edge.filter((p) => p[0] > x0 + w * 0.45) : edge.filter((p) => p[0] < x0 + w * 0.55);
  const from = side.length ? side : edge;
  const head = from.reduce((a, p) => (p[1] < a[1] ? p : a), from[0]);
  const way = f.leaveSide >= 0 ? 1 : -1;
  const EDGE_PAD = 18;
  let lastLeave = null;
  for (let n = 0; n < LEAVE; n++) {
    const t = n + r() * 0.4 - 0.2;
    const lx = clamp(head[0] + way * (26 + 20 * t) * k, EDGE_PAD, W - EDGE_PAD);
    const ly = clamp(head[1] - (18 + 3 * t + 1.3 * t * t) * k, EDGE_PAD, H - EDGE_PAD);
    const heading = Math.atan2(-(3 + 2.6 * t), way * 28) * 180 / Math.PI + (r() - 0.5) * 8;
    const size = Math.max(7, 17 - 1.3 * n + r() * 4) * k, alpha = Math.min(1, Math.max(0.3, 0.85 - 0.06 * n + r() * 0.15));
    bird(ctx, lx, ly, size, heading, COLOURS[1 + Math.floor(r() * 2)], alpha);
    lastLeave = { x: lx, y: ly };
  }
  const size = 44 * k;
  ctx.save(); ctx.beginPath(); ctx.arc(lit[0], lit[1], size * 0.95, 0, Math.PI * 2);
  ctx.fillStyle = '#FFFAF1'; ctx.fill(); ctx.lineWidth = 5 * k; ctx.strokeStyle = '#9A4526'; ctx.stroke(); ctx.restore();
  bird(ctx, lit[0], lit[1], size, -8, '#9A4526', 1);
  const aim = { x: x0 + w * 0.45, y: y0 + h * 0.35 };
  const landing = inner.reduce((a, p) => ((p[0] - aim.x) ** 2 + (p[1] - aim.y) ** 2 < (a[0] - aim.x) ** 2 + (a[1] - aim.y) ** 2 ? p : a), inner[0] ?? lit);
  return { count: edge.length + inner.length + 1 + LEAVE, exit: { x: lastLeave.x / W, y: lastLeave.y / H }, land: { x: landing[0] / W, y: landing[1] / H } };
}

// A short stream of birds between two points, like the home hero's trail (seeded, thinning towards its end).
function trail(t) {
  const r = rng(4049);
  for (let i = 0; i < t.n; i++) {
    const u = i / t.n;
    const x = t.from.x + (t.to.x - t.from.x) * u + (r() - 0.5) * 70 * (0.4 + u);
    const y = t.from.y + (t.to.y - t.from.y) * u + Math.sin(u * Math.PI * 1.4) * -60 + (r() - 0.5) * 60 * (0.4 + u);
    bird(ctx, x, y, 10 + r() * 9, -150 + (r() - 0.5) * 50, COLOURS[[0, 1, 1, 2][Math.floor(r() * 4)]], 0.95 - u * 0.55);
  }
}

async function polaroid(p) {
  const img = await load(p.src);
  const inner = p.w - 44;
  const ih = Math.round(inner * p.crop.h / p.crop.w);
  const box = document.createElement('div');
  box.className = 'pol';
  box.style.cssText = 'left:' + p.x + 'px;top:' + p.y + 'px;width:' + p.w + 'px;transform:rotate(' + p.rot + 'deg)';
  const c = document.createElement('canvas'); c.width = inner * 2; c.height = ih * 2;
  c.getContext('2d').drawImage(img, p.crop.x, p.crop.y, p.crop.w, p.crop.h, 0, 0, c.width, c.height);
  c.style.width = inner + 'px'; c.style.height = ih + 'px';
  const tape = document.createElement('span'); tape.className = 'tape';
  const cap = document.createElement('p'); cap.className = 'pc'; cap.textContent = p.caption;
  box.append(tape, c, cap);
  if (p.sub) { const s = document.createElement('p'); s.className = 'ps'; s.textContent = p.sub; box.append(s); }
  art.append(box);
}

window.__ready = (async () => {
  await document.fonts.load('700 40px Caveat');
  await document.fonts.load('600 20px Inter');
  let flight = null;
  let avoid = [];
  if (PIC.trail) trail(PIC.trail);
  if (PIC.flock) flight = await silhouetteFlock(PIC.flock);
  if (PIC.polaroid) await polaroid(PIC.polaroid);
  if (PIC.words && PIC.words.items.length) {
    const col = document.createElement('div');
    col.className = 'col';
    col.style.left = PIC.words.x + 'px'; col.style.width = PIC.words.w + 'px';
    for (const w of PIC.words.items) {
      const el = document.createElement('div');
      el.className = w.cls; el.textContent = w.text;
      col.append(el);
    }
    art.append(col);
    await document.fonts.ready;
    col.style.top = Math.round((H - col.offsetHeight) / 2) + 'px';
    const b = col.getBoundingClientRect();
    avoid = [{ x: (b.left - 16) / W, y: (b.top - 16) / H, w: (b.width + 32) / W, h: (b.height + 32) / H }];
  }
  await document.fonts.ready;
  return { count: flight?.count ?? null, exit: flight?.exit ?? null, land: flight?.land ?? null, avoid };
})();
</script>`;
}

const LOCALES = ['en', 'sv'];
const args = process.argv.slice(2);
const outAt = args.indexOf('--out');
const outDir = outAt >= 0 ? resolve(args[outAt + 1]) : null;
const names = args.filter((a, i) => !(outAt >= 0 && (i === outAt || i === outAt + 1)));
const which = names.length ? names : Object.keys(PICTURES).filter((name) => !PICTURES[name].proposal);

/** One language's version of a picture: its own words and its own caption on the photo. */
function inLocale(pic, locale) {
  return {
    ...pic,
    words: pic.words ? { x: pic.words.x, w: pic.words.w, items: pic.words[locale] } : null,
    polaroid: pic.polaroid ? { ...pic.polaroid, caption: pic.polaroid.caption[locale] } : undefined,
  };
}

// The pictures' flight data (exit, land, avoid), read back in by src/components/NoteCard.astro via artKey. Only
// written in the normal (non-preview) run: a --out run is a proposal, not the data other components read.
const artFile = join(root, 'src/data/note-art.json');
const art = existsSync(artFile) ? JSON.parse(readFileSync(artFile, 'utf8')) : {};
const r4 = (n) => Math.round(n * 1e4) / 1e4;
const pt = (p) => p && { x: r4(p.x), y: r4(p.y) };

const dir = mkdtempSync(join(tmpdir(), 'note-art-'));
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
try {
  const tab = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: DPR });
  const errors = [];
  tab.on('pageerror', (e) => errors.push(String(e)));
  for (const name of which) {
    if (!PICTURES[name]) throw new Error(`no picture called ${name} (${Object.keys(PICTURES).join(', ')})`);
    for (const locale of LOCALES) {
      const pic = inLocale(PICTURES[name], locale);
      const base = `${pic.file}-${locale}`;
      const photo = pic.polaroid ? JSON.stringify(dataUrl(pic.polaroid.photo, 'image/webp')) : '""';
      const html = page(pic).replace('"src":null', `"src":${photo}`);
      const file = join(dir, `${name}-${locale}.html`);
      writeFileSync(file, html);
      await tab.goto(pathToFileURL(file).href, { waitUntil: 'load' });
      const result = await tab.evaluate(() => window.__ready);
      if (errors.length) throw new Error(`${name} (${locale}): ${errors.join('; ')}`);
      if (pic.flock && result.count !== 839) throw new Error(`${name} (${locale}): drew ${result.count} birds, expected 839`);
      if (result.exit && !outDir) art[base] = { exit: pt(result.exit), land: pt(result.land), avoid: result.avoid.map((a) => ({ x: r4(a.x), y: r4(a.y), w: r4(a.w), h: r4(a.h) })) };
      const png = await tab.locator('#art').screenshot();
      if (outDir) {
        mkdirSync(outDir, { recursive: true });
        const out = join(outDir, `${base}.png`);
        writeFileSync(out, png);
        console.log(out);
      } else {
        const out = join(root, 'src/assets/photos', `${base}.webp`);
        // A running `astro preview` keeps these files open on Windows (writes fail with "Invalid argument"): stop it first.
        await sharp(png).webp({ quality: 90 }).toFile(out);
        const meta = await sharp(out).metadata();
        console.log(`${out} ${meta.width}x${meta.height}`);
      }
    }
  }
  if (!outDir) writeFileSync(artFile, JSON.stringify(art, null, 2) + '\n');
} finally {
  await browser.close();
  rmSync(dir, { recursive: true, force: true });
}
