#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const manifestPath = resolve(__dirname, 'asset-manifest.json');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

for (const file of manifest.files) {
  const [rootKey, ...rest] = file.from.split('/');
  const sourceRoot = manifest.sources[rootKey];
  if (!sourceRoot) {
    console.error(`Unknown source root: ${rootKey}`);
    process.exit(1);
  }
  const sourceRel = rest.join('/');
  process.stdout.write(`${sourceRel}\t${file.to}\t${sourceRoot}\n`);
}
