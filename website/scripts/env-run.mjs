#!/usr/bin/env node
// Runs a command with extra environment variables, the same way on Windows and macOS:
//   node scripts/env-run.mjs SPECIES_FIXTURES=1 SPECIES_PREVIEW=1 -- astro build --force
//   node scripts/env-run.mjs -- astro build --force          (zero variables is allowed)
//
// The command and its arguments are joined with a space and run through the shell (so that both
// Windows cmd.exe and POSIX shells resolve `astro`/`npx`/etc the same way). That means NONE of the
// arguments after `--` may contain a space or a shell metacharacter (quotes, `|`, `&&`, `>`, `$`, ...)
// — such characters would be split or reinterpreted by the shell instead of passed through literally.
// Keep call sites to plain words and flags, same as the existing build:fixtures/build:prod scripts.
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const split = args.indexOf('--');
if (split < 0 || split === args.length - 1) {
  console.error('Användning: node scripts/env-run.mjs [NAMN=värde ...] -- kommando [argument]');
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
if (result.error) {
  console.error(`env-run: kommandot kunde inte startas: ${result.error.message}`);
  process.exit(1);
}
if (result.signal) {
  console.error(`env-run: kommandot avbröts av signalen ${result.signal}`);
  process.exit(1);
}
process.exit(result.status ?? 1);
