// Restores the original photo pixels around the robin's feet in the plate (the photo with the robin
// removed). layers.py dilates the cut-out by 8 px and blurs the fill, which also wiped the moss the
// toes grip, so the composited toes looked like they hovered over a dark smudge. The feet are the
// transform origin of every robin animation, so near them the layer moves under 2 px and the plate
// can keep the real pixels: the composite there equals the original photo exactly.
//
// Run from website/ (for sharp): node <this file> <original> <plate-in> <mask> <plate-out.webp> [debug-dir]
import { createRequire } from 'node:module';
const require = createRequire(`${process.cwd()}/package.json`);
const sharp = require('sharp');

const [, , origPath, platePath, maskPath, outPath, debugDir] = process.argv;

// Band above the lowest foot pixel where the original is restored (source px, 1672×941 photo).
const FULL = 45; // fully original from this many px above the feet downwards
const FEATHER = 25; // then fades to the filled plate over this many px
const SIDE = 40; // horizontal margin around the legs and toes
const SIDE_FEATHER = 20;

const raw = async (p, channels) => {
  const img = sharp(p).removeAlpha();
  const { data, info } = await (channels === 1 ? img.greyscale() : img).raw().toBuffer({ resolveWithObject: true });
  return { data, w: info.width, h: info.height, c: info.channels };
};

const orig = await raw(origPath, 3);
const plate = await raw(platePath, 3);
const mask = await raw(maskPath, 1);
if (orig.w !== plate.w || orig.h !== plate.h || orig.w !== mask.w || orig.h !== mask.h) throw new Error('size mismatch');
const { w: W, h: H } = orig;

let feetY = -1;
for (let y = H - 1; y >= 0 && feetY < 0; y--) for (let x = 0; x < W; x++) if (mask.data[y * W + x] > 8) { feetY = y; break; }
const top = feetY - FULL - FEATHER;
let lx = W, rx = -1;
for (let y = Math.max(0, top); y <= feetY; y++) for (let x = 0; x < W; x++) if (mask.data[y * W + x] > 8) { lx = Math.min(lx, x); rx = Math.max(rx, x); }

const smooth = (t) => (t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t));
const weight = (x, y) => {
  const wy = smooth((y - top) / FEATHER);
  const dx = x < lx - SIDE ? lx - SIDE - x : x > rx + SIDE ? x - rx - SIDE : 0;
  const wx = 1 - smooth(dx / SIDE_FEATHER);
  return wy * wx;
};

const out = Buffer.alloc(orig.data.length);
let changed = 0;
for (let y = 0; y < H; y++) {
  for (let x = 0; x < W; x++) {
    const a = weight(x, y);
    const i = (y * W + x) * 3;
    for (let k = 0; k < 3; k++) out[i + k] = Math.round(plate.data[i + k] * (1 - a) + orig.data[i + k] * a);
    if (a > 0) changed++;
  }
}
await sharp(out, { raw: { width: W, height: H, channels: 3 } }).webp({ quality: 95, effort: 6 }).toFile(outPath);
console.log(JSON.stringify({ feetY, band: [top, H - 1], legs: [lx, rx], restoredPx: changed }));

if (debugDir) {
  const { mkdirSync } = await import('node:fs');
  mkdirSync(debugDir, { recursive: true });
  // Composites: layer at rest, and at the bob's squash (scale 1.012, .962 around the feet origin).
  const layerPath = platePath.replace(/robin-plate\.webp$/, 'robin-layer.png');
  const box = { x: 1054, y: 145, w: 419, h: 502 };
  const ox = 0.3393 * box.w, oy = 0.9861 * box.h;
  const comp = async (plateBuf, sx, sy, name) => {
    const lw = Math.round(box.w * sx), lh = Math.round(box.h * sy);
    const layer = await sharp(layerPath).resize(lw, lh, { fit: 'fill' }).png().toBuffer();
    const left = Math.round(box.x + ox - ox * sx), topY = Math.round(box.y + oy - oy * sy);
    const base = sharp(plateBuf, plateBuf.length === W * H * 3 ? { raw: { width: W, height: H, channels: 3 } } : undefined);
    const full = await base.composite([{ input: layer, left, top: topY }]).png().toBuffer();
    // Feet crop at 3x, and a head/back crop at 2x.
    await sharp(full).extract({ left: 1080, top: feetY - 100, width: 260, height: 110 }).resize(780, 330, { kernel: 'nearest' }).toFile(`${debugDir}/${name}-feet-x3.png`);
    await sharp(full).extract({ left: 1000, top: 120, width: 520, height: 300 }).resize(1040, 600, { kernel: 'nearest' }).toFile(`${debugDir}/${name}-head-x2.png`);
    return full;
  };
  await sharp(orig.data, { raw: { width: W, height: H, channels: 3 } }).extract({ left: 1080, top: feetY - 100, width: 260, height: 110 }).resize(780, 330, { kernel: 'nearest' }).toFile(`${debugDir}/orig-feet-x3.png`);
  await comp(plate.data, 1, 1, 'old-rest');
  await comp(out, 1, 1, 'new-rest');
  await comp(out, 1.012, 0.962, 'new-squash');
  await comp(out, 0.994, 1.022, 'new-stretch');
  // The new plate alone around the feet (what shows through if the layer moved).
  await sharp(out, { raw: { width: W, height: H, channels: 3 } }).extract({ left: 1000, top: feetY - 160, width: 420, height: 200 }).resize(840, 400, { kernel: 'nearest' }).toFile(`${debugDir}/new-plate-alone-feet-x2.png`);
  // Mean abs difference of composite-at-rest vs original inside the band.
  const restNew = await sharp(await comp(out, 1, 1, 'tmp')).removeAlpha().raw().toBuffer();
  const restOld = await sharp(await comp(plate.data, 1, 1, 'tmp')).removeAlpha().raw().toBuffer();
  let dNew = 0, dOld = 0, n = 0;
  for (let y = Math.max(0, top); y < Math.min(H, feetY + 10); y++) for (let x = lx - SIDE; x <= rx + SIDE; x++) {
    const i = (y * W + x) * 3;
    for (let k = 0; k < 3; k++) { dNew += Math.abs(restNew[i + k] - orig.data[i + k]); dOld += Math.abs(restOld[i + k] - orig.data[i + k]); }
    n += 3;
  }
  console.log(JSON.stringify({ madBandOld: +(dOld / n).toFixed(2), madBandNew: +(dNew / n).toFixed(2) }));
}
