#!/usr/bin/env node
// Runs a command with extra environment variables, the same way on Windows and macOS:
//   node scripts/env-run.mjs SPECIES_FIXTURES=1 SPECIES_PREVIEW=1 -- astro build --force
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const split = args.indexOf('--');
if (split < 1 || split === args.length - 1) {
  console.error('Användning: node scripts/env-run.mjs NAMN=värde [NAMN=värde ...] -- kommando [argument]');
  process.exit(2);
}
const env = { ...process.env };
for (const pair of args.slice(0, split)) {
  const eq = pair.indexOf('=');
  if (eq < 1) {
    console.error(`Ogiltig variabel: ${pair}`);
    process.exit(2);
  }
  env[pair.slice(0, eq)] = pair.slice(eq + 1);
}
const result = spawnSync(args.slice(split + 1).join(' '), { stdio: 'inherit', env, shell: true });
process.exit(result.status ?? 1);
