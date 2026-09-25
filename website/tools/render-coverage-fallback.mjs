// Renders public/coverage/coverage-fallback.webp from coverage-europe.geojson in the field journal
// colours (spec §5.6). Shown before the live map loads, without JavaScript, and wherever the map key
// is missing (CI, previews). No map service is needed. Run: npm run assets:coverage
import sharp from 'sharp';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const gj = JSON.parse(readFileSync(resolve(root, 'public/coverage/coverage-europe.geojson'), 'utf8'));

const W = 1600;
const H = 700;
const PAD = 24;
const WATER = '#D9CCAF';
const FILL = '#9A4526';
const STROKE = '#72301A';
const PAPER = '#F1E8D6';
const BOUNDS = { west: -25, east: 42, south: 34, north: 71.5 };

// Web Mercator, fitted to BOUNDS and centred in the canvas.
const mx = (lon) => (lon * Math.PI) / 180;
const my = (lat) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360));
const x0 = mx(BOUNDS.west);
const x1 = mx(BOUNDS.east);
const y0 = my(BOUNDS.north);
const y1 = my(BOUNDS.south);
const scale = Math.min((W - 2 * PAD) / (x1 - x0), (H - 2 * PAD) / (y0 - y1));
const offX = (W - scale * (x1 - x0)) / 2;
const offY = (H - scale * (y0 - y1)) / 2;
const project = ([lon, lat]) => [offX + (mx(lon) - x0) * scale, offY + (y0 - my(lat)) * scale];

const ringPath = (ring) =>
  ring.map((pt, i) => { const [x, y] = project(pt); return `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; }).join('') + 'Z';
const polygons = gj.features.flatMap((f) => (f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates));
const d = polygons.map((poly) => poly.map(ringPath).join('')).join('');

// Decorative sample pins, the same five cities as the live map (not real sightings).
const pins = [[18.07, 59.33], [13.4, 52.52], [2.35, 48.85], [12.49, 41.9], [-3.7, 40.42]].map(project);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="${WATER}"/>
  <path d="${d}" fill="${FILL}" fill-opacity="0.55" fill-rule="evenodd" stroke="${STROKE}" stroke-width="1.2" stroke-linejoin="round"/>
  ${pins.map(([x, y]) => `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="11" fill="${PAPER}" stroke="${FILL}" stroke-width="4"/>`).join('')}
</svg>`;

await sharp(Buffer.from(svg)).webp({ quality: 82 }).toFile(resolve(root, 'public/coverage/coverage-fallback.webp'));
console.log(`coverage-fallback.webp ${W}x${H}`);
