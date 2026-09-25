// Share images (1200×630) for the homepages in the 1.3 palette: the robin photo under a moss shade.
// Run: npm run assets:og
import sharp from 'sharp';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const photo = resolve(root, 'src/assets/hero-robin.webp');
const variants = [
  { locale: 'en', line1: 'Know the bird.', line2: 'Keep the moment.', note: 'IDENTIFY · EXPLORE · REMEMBER' },
  { locale: 'sv', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', note: 'IDENTIFIERA · UTFORSKA · BEVARA' },
];

for (const item of variants) {
  const overlay = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630">
    <defs><linearGradient id="shade"><stop offset="0" stop-color="#1F2A19" stop-opacity=".96"/><stop offset=".48" stop-color="#1F2A19" stop-opacity=".8"/><stop offset="1" stop-color="#1F2A19" stop-opacity=".06"/></linearGradient></defs>
    <rect width="1200" height="630" fill="url(#shade)"/>
    <text x="72" y="85" fill="#FFF8EE" font-family="Georgia,serif" font-style="italic" font-size="42">Birdy.</text>
    <text x="72" y="182" fill="#F2B27A" font-family="Arial,sans-serif" font-size="17" letter-spacing="3">${item.note}</text>
    <text x="72" y="320" fill="#FFF8EE" font-family="Georgia,serif" font-size="72">${item.line1}</text>
    <text x="72" y="410" fill="#F2B27A" font-family="Georgia,serif" font-style="italic" font-size="72">${item.line2}</text>
    <line x1="72" y1="546" x2="1128" y2="546" stroke="#FFF8EE" stroke-opacity=".45"/>
    <text x="72" y="584" fill="#FFF8EE" font-family="Arial,sans-serif" font-size="19">birdy.community</text>
  </svg>`);
  await sharp(photo)
    .resize(1200, 630, { fit: 'cover', position: 'centre' })
    .composite([{ input: overlay }])
    .png({ compressionLevel: 9 })
    .toFile(resolve(root, `public/og-field-${item.locale}.png`));
  console.log(`og-field-${item.locale}.png`);
}
