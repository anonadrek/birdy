#!/usr/bin/env node
// Publishes at most ONE species or comparison (spec 2026-09-25 §14, plan Task 16), from website/ on `main`:
//   1. `uv run birdy-fetcher web publish --next` in ../tools/content-pipeline picks the next ready record in
//      queue order and sets `publish: true` (stdout: "species QID", "comparison STEM" or "none");
//   2. the production build (npm run build:prod), check-seo.mjs over the whole site, the page's own tests
//      (tests/published-page.spec.ts) and axe (tests/a11y.spec.ts) on both language versions;
//   3. all green: commit only that record's files and push; anything red: put the record back, write the
//      reason to reports/publish-loop-<date>.md and exclude the record for the rest of the session.
// Exit codes: 0 published (or, with --dry-run, everything green), 1 failure, 3 nothing ready.
// scripts/publish-loop.sh runs it in a loop. Flags:
//   --dry-run   do everything but commit and push, then put the record back (leaves no trace but reports/)
//   --no-push   commit, but do not push (and allow a branch other than main), for testing
//   --port N    the preview server's port for the tests (default 4327, or PUBLISH_PORT)
import { spawn, spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { connect } from 'node:net';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { COMPARISONS_ENABLED } from '../src/lib/species-source.mjs';

export const EXIT = { published: 0, failed: 1, none: 3 };
export const EXCLUDED_FILE = 'reports/publish-loop-excluded.txt';
export const TRAILER = 'Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>';
const SPECIES_DIR = 'src/data/species';
const COMPARISONS_DIR = 'src/data/comparisons';
const ASSETS_DIR = 'src/assets/species';

/** @param {string[]} argv */
export function parseArgs(argv) {
  const out = { dryRun: false, push: true, port: Number(process.env.PUBLISH_PORT ?? 4327) };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a === '--dry-run') out.dryRun = true;
    else if (a === '--no-push') out.push = false;
    else if (a === '--port') out.port = Number(argv[(i += 1)]);
    else throw new Error(`okänd flagga: ${a}`);
  }
  if (!Number.isInteger(out.port) || out.port < 1024 || out.port > 65535) throw new Error(`ogiltig port: ${out.port}`);
  return out;
}

/** The session's exclusions: one QID or comparison stem per line; blank lines and # comments are skipped. */
export function readExcluded(text) {
  return [...new Set(text.split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#')))];
}

/** The record ids behind a list of changed paths (`git status --porcelain`): src/data JSON files only. */
export function dirtyRecordIds(porcelain) {
  const ids = new Set();
  for (const line of porcelain.split(/\r?\n/)) {
    const m = line.match(/src\/data\/(?:species|comparisons)\/(Q\d+(?:_Q\d+)?)\.json/);
    if (m) ids.add(m[1]);
  }
  return [...ids];
}

/**
 * The arguments for `uv` (run in ../tools/content-pipeline). Comparison pages come with Task 11: while
 * COMPARISONS_ENABLED is false every comparison is excluded, so --next never publishes one that has no page.
 */
export function nextArgs(excluded, comparisonStems, comparisonsEnabled) {
  const skip = [...new Set([...excluded, ...(comparisonsEnabled ? [] : comparisonStems)])];
  return ['run', 'birdy-fetcher', 'web', 'publish', '--next', ...skip.flatMap((id) => ['--exclude', id])];
}

/** --next's stdout contract: exactly one line, "species QID", "comparison STEM" or "none". */
export function parsePick(stdout) {
  const lines = stdout.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  if (lines.length !== 1) throw new Error(`web publish --next skrev ${lines.length} rader, väntade en: ${JSON.stringify(stdout.slice(0, 300))}`);
  if (lines[0] === 'none') return null;
  const species = lines[0].match(/^species (Q\d+)$/);
  if (species) return { kind: 'species', id: species[1] };
  const comparison = lines[0].match(/^comparison (Q\d+_Q\d+)$/);
  if (comparison) return { kind: 'comparison', id: comparison[1] };
  throw new Error(`web publish --next: oväntad rad ${JSON.stringify(lines[0])}`);
}

/** The files a published record may change: its JSON, and for a species its photos and recording. */
export function pickFiles(pick) {
  return pick.kind === 'species'
    ? { json: `${SPECIES_DIR}/${pick.id}.json`, extra: [`${ASSETS_DIR}/${pick.id}`] }
    : { json: `${COMPARISONS_DIR}/${pick.id}.json`, extra: [] };
}

/** The page's address in both languages, without the leading slash (as the tests take them). */
export function pagePaths(record) {
  return [`sv/arter/${record.slug.sv}/`, `species/${record.slug.en}/`];
}

/** "data(artsidor): Talgoxe (Q25485)" or "data(artsidor): Blåmes eller talgoxe (Q25404+Q25485)". */
export function commitMessage(pick, names) {
  const subject = pick.kind === 'species'
    ? `data(artsidor): ${names[0]} (${pick.id})`
    : `data(artsidor): ${names[0]} eller ${names[1].toLocaleLowerCase('sv')} (${pick.id.replace('_', '+')})`;
  return `${subject}\n\n${TRAILER}\n`;
}

/** Local date and time, for the report's file name and headings. */
const stamp = () => {
  const now = new Date();
  return { date: now.toLocaleDateString('sv-SE'), time: now.toLocaleTimeString('sv-SE', { hour: '2-digit', minute: '2-digit' }) };
};
// The last lines of a command's output, without the terminal's colour and cursor codes.
// eslint-disable-next-line no-control-regex
const tail = (text, lines = 60) => (text ?? '').replace(/\x1b\[[0-9;?]*[A-Za-z]/g, '').trimEnd().split(/\r?\n/).slice(-lines).join('\n').trim();

/** True when something answers on the port, on IPv4 or IPv6 localhost (astro preview binds "localhost"). */
async function portInUse(port) {
  const answers = (host) => new Promise((done) => {
    const socket = connect({ host, port });
    socket.setTimeout(1000);
    socket.once('connect', () => { socket.destroy(); done(true); });
    socket.once('timeout', () => { socket.destroy(); done(false); });
    socket.once('error', () => done(false));
  });
  return (await Promise.all(['127.0.0.1', '::1'].map(answers))).some(Boolean);
}

async function waitForServer(url, ms) {
  const until = Date.now() + ms;
  while (Date.now() < until) {
    try {
      if ((await fetch(url)).ok) return true;
    } catch {
      // not up yet
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return false;
}

async function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const pipeline = resolve(root, '../tools/content-pipeline');
  const opts = parseArgs(process.argv.slice(2));
  // Never let an inherited variable change the production build or the tests (build:prod pins the first two).
  const env = { ...process.env };
  for (const name of ['SPECIES_FIXTURES', 'SPECIES_PREVIEW', 'SPECIES_EMPTY', 'CI']) delete env[name];

  const sh = (cmd, cwd = root, extraEnv = {}, timeout = 15 * 60_000) =>
    spawnSync(cmd, { cwd, env: { ...env, ...extraEnv }, shell: true, encoding: 'utf8', timeout, maxBuffer: 64 * 1024 * 1024 });
  const git = (args) => spawnSync('git', args, { cwd: root, env, encoding: 'utf8' });
  const say = (line) => console.log(`publish-next: ${line}`);

  mkdirSync(resolve(root, 'reports'), { recursive: true });
  const { date } = stamp();
  const report = resolve(root, `reports/publish-loop-${date}.md`);
  const writeReport = (heading, body = '') => appendFileSync(report, `## ${stamp().time} ${heading}\n\n${body ? `\`\`\`\n${body}\n\`\`\`\n\n` : ''}`);

  // -- Before anything changes ---------------------------------------------------------------------------
  const preflight = [];
  if (!existsSync(pipeline)) preflight.push(`pipelinen saknas: ${pipeline}`);
  const branch = git(['rev-parse', '--abbrev-ref', 'HEAD']).stdout.trim();
  if (opts.push && !opts.dryRun && branch !== 'main') preflight.push(`grenen är ${branch}, publiceringen pushar bara från main (testa med --dry-run eller --no-push)`);
  if (await portInUse(opts.port)) preflight.push(`porten ${opts.port} är upptagen (en annan server?), välj en annan med --port eller PUBLISH_PORT`);
  if (preflight.length) {
    writeReport('förkontroll FAILED (ingen post vald)', preflight.join('\n'));
    for (const p of preflight) console.error(`publish-next: ${p}`);
    process.exit(EXIT.failed);
  }

  const excludedPath = resolve(root, EXCLUDED_FILE);
  const excluded = existsSync(excludedPath) ? readExcluded(readFileSync(excludedPath, 'utf8')) : [];
  // A record with uncommitted changes (a spot check or an import in progress) is somebody else's work in
  // progress: never pick it, so putting a failed record back can never throw those changes away.
  const dirty = dirtyRecordIds(git(['status', '--porcelain', '--', SPECIES_DIR, COMPARISONS_DIR]).stdout);
  const stems = existsSync(resolve(root, COMPARISONS_DIR))
    ? readdirSync(resolve(root, COMPARISONS_DIR)).filter((f) => /^Q\d+_Q\d+\.json$/.test(f)).map((f) => f.slice(0, -5))
    : [];

  // -- 1. The pick -------------------------------------------------------------------------------------
  const args = nextArgs([...excluded, ...dirty], stems, COMPARISONS_ENABLED);
  const picked = spawnSync('uv', args, { cwd: pipeline, env, encoding: 'utf8', timeout: 10 * 60_000 });
  if (picked.error || picked.status !== 0) {
    writeReport('web publish --next FAILED (ingen post vald)', `${picked.error?.message ?? ''}\n${tail(picked.stderr)}\n${tail(picked.stdout)}`.trim());
    console.error(`publish-next: web publish --next misslyckades (${picked.error?.message ?? `kod ${picked.status}`}), se ${report}`);
    process.exit(EXIT.failed);
  }
  let pick;
  try {
    pick = parsePick(picked.stdout);
  } catch (e) {
    writeReport('web publish --next gav ett oväntat svar', `${e.message}\n${tail(picked.stderr)}`);
    console.error(`publish-next: ${e.message}`);
    process.exit(EXIT.failed);
  }
  if (!pick) {
    say(`inget att publicera just nu (${excluded.length} uteslutna i sessionen, ${dirty.length} med ocommittade ändringar${COMPARISONS_ENABLED ? '' : ', jämförelserna avstängda till Task 11'})`);
    process.exit(EXIT.none);
  }

  const files = pickFiles(pick);
  const restore = () => git(['checkout', '--', files.json]);
  const fail = (step, details) => {
    restore();
    appendFileSync(excludedPath, `${pick.id}\n`);
    writeReport(`${pick.kind} ${pick.id} FAILED i steget ${step} (återställd, utesluten i sessionen)`, details);
    console.error(`publish-next: ${pick.kind} ${pick.id} föll i steget ${step}; posten är återställd och utesluten, se ${report}`);
    process.exit(EXIT.failed);
  };

  let record;
  let names;
  try {
    record = JSON.parse(readFileSync(resolve(root, files.json), 'utf8'));
    if (pick.kind === 'comparison' && !COMPARISONS_ENABLED) throw new Error('en jämförelse valdes fast jämförelserna är avstängda (COMPARISONS_ENABLED)');
    names = pick.kind === 'species'
      ? [record.names.sv]
      : [record.a, record.b].map((qid) => JSON.parse(readFileSync(resolve(root, SPECIES_DIR, `${qid}.json`), 'utf8')).names.sv);
  } catch (e) {
    fail('läsa posten', e.message);
  }
  const paths = pagePaths(record);
  say(`${pick.kind} ${pick.id} (${names.join(' / ')}): bygger och testar /${paths.join(' och /')}`);

  // -- 2. Build and check --------------------------------------------------------------------------------
  const build = sh('npm run build:prod');
  if (build.status !== 0) fail('build:prod', `${tail(build.stdout)}\n${tail(build.stderr)}`);
  const seo = sh('node scripts/check-seo.mjs');
  if (seo.status !== 0) fail('check-seo', `${tail(seo.stdout)}\n${tail(seo.stderr)}`);
  for (const p of paths) {
    if (!existsSync(resolve(root, 'dist', p, 'index.html'))) fail('bygget', `/${p} finns inte i dist/ efter bygget`);
  }

  // The tests get a preview server of their own (foreground, outside Astro's preview lock, which another
  // session's server may hold); Playwright reuses it instead of starting one.
  const server = spawn(process.execPath, [resolve(root, 'node_modules/astro/bin/astro.mjs'), 'preview', '--port', String(opts.port), '--strictPort', '--ignore-lock'], { cwd: root, env, stdio: 'ignore' });
  let tests;
  try {
    if (!(await waitForServer(`http://localhost:${opts.port}/`, 60_000))) {
      server.kill();
      fail('förhandsvisningsservern', `astro preview svarade inte på port ${opts.port} inom 60 s`);
    }
    tests = sh(
      `"${process.execPath}" node_modules/@playwright/test/cli.js test tests/published-page.spec.ts tests/a11y.spec.ts --workers=2 --reporter=line`,
      root,
      { PAGE_PATHS: paths.join(','), AXE_PATH: paths.join(','), PLAYWRIGHT_PORT: String(opts.port) },
      10 * 60_000,
    );
  } finally {
    server.kill();
  }
  if (tests.status !== 0) fail('Playwright och axe', `${tail(tests.stdout, 120)}\n${tail(tests.stderr)}`);
  say(`testerna gröna: ${tail(tests.stdout, 1)}`);

  // -- 3. Publish ---------------------------------------------------------------------------------------
  if (opts.dryRun) {
    restore();
    writeReport(`${pick.kind} ${pick.id}: torrkörning grön (återställd, inget committat)`);
    say(`torrkörning: allt grönt för ${pick.id}, posten är återställd och inget är committat`);
    process.exit(EXIT.published);
  }
  const commitPaths = [files.json, ...files.extra.filter((p) => existsSync(resolve(root, p)))];
  const added = git(['add', '--', ...commitPaths]);
  const committed = added.status === 0 ? git(['commit', '-m', commitMessage(pick, names), '--', ...commitPaths]) : added;
  if (committed.status !== 0) fail('git commit', `${committed.stdout}\n${committed.stderr}`);
  const hash = git(['rev-parse', '--short', 'HEAD']).stdout.trim();
  if (opts.push) {
    let pushed = git(['push']);
    if (pushed.status !== 0) {
      // Someone else pushed meanwhile (the pipeline's data, a spot check): rebase once and try again.
      const pulled = git(['pull', '--rebase', '--autostash']);
      pushed = pulled.status === 0 ? git(['push']) : pulled;
    }
    if (pushed.status !== 0) {
      // Committed but not pushed: the record is published locally, so --next will not pick it again and the
      // next successful push takes it along. Not excluded, nothing to put back.
      writeReport(`${pick.kind} ${pick.id}: committad (${hash}) men push FAILED`, `${pushed.stdout}\n${pushed.stderr}`);
      console.error(`publish-next: ${pick.id} är committad (${hash}) men gick inte att pusha, se ${report}`);
      process.exit(EXIT.failed);
    }
  }
  writeReport(`${pick.kind} ${pick.id} (${names.join(' / ')}): publicerad ${hash}${opts.push ? ', pushad' : ', inte pushad (--no-push)'}`);
  say(`${names.join(' / ')} (${pick.id}) publicerad som ${hash}${opts.push ? ' och pushad' : ' (inte pushad)'}`);
  process.exit(EXIT.published);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((e) => {
    console.error(`publish-next: ${e.stack ?? e}`);
    process.exit(EXIT.failed);
  });
}
