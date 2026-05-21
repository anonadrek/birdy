#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');

const en = JSON.parse(readFileSync(resolve(root, 'src/content/copy.en.json'), 'utf8'));
const sv = JSON.parse(readFileSync(resolve(root, 'src/content/copy.sv.json'), 'utf8'));

function collectKeys(obj, prefix = '') {
  const keys = [];
  for (const [k, v] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      keys.push(...collectKeys(v, path));
    } else if (Array.isArray(v)) {
      keys.push(`${path}[len=${v.length}]`);
      v.forEach((item, i) => {
        if (item && typeof item === 'object') {
          keys.push(...collectKeys(item, `${path}[${i}]`));
        }
      });
    } else {
      keys.push(path);
    }
  }
  return keys.sort();
}

const enKeys = collectKeys(en);
const svKeys = collectKeys(sv);
const enSet = new Set(enKeys);
const svSet = new Set(svKeys);

const missingInSv = enKeys.filter((k) => !svSet.has(k));
const missingInEn = svKeys.filter((k) => !enSet.has(k));

if (missingInSv.length === 0 && missingInEn.length === 0) {
  console.log(`i18n parity OK (${enKeys.length} keys)`);
  process.exit(0);
}

console.error('i18n parity FAILED');
if (missingInSv.length) {
  console.error('  Missing in sv:');
  missingInSv.forEach((k) => console.error(`    - ${k}`));
}
if (missingInEn.length) {
  console.error('  Missing in en:');
  missingInEn.forEach((k) => console.error(`    - ${k}`));
}
process.exit(1);
