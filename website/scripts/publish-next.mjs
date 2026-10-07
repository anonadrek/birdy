#!/usr/bin/env node
// Publishes at most ONE species or comparison (spec 2026-09-25 §14, plan Task 16). Runs from website/ in a
// DEDICATED worktree that follows origin/main (never the main clone, which other sessions share):
//   0. preflight: clean tree, fast-forward to origin/main, nothing unpushed, a free port, no reports/STOP;
//   1. `uv run birdy-fetcher web publish --next` in ../tools/content-pipeline picks the next ready record in
//      queue order and sets `publish: true` (stdout: "species QID", "comparison STEM" or "none");
//   2. the dash guard, the production build (npm run build:prod), check-seo.mjs over the whole site, the
//      page's own tests (tests/published-page.spec.ts) and axe (tests/a11y.spec.ts) on both languages, the
//      hub and the group page;
//   3. all green: commit only that record's files, `git push origin HEAD:main`, and wait until both pages
//      answer 200 on birdy.community.
// A failed record is put back (from HEAD, index and working tree), excluded for the rest of the session and
// written to reports/publish-loop-<date>.md. Exit codes: 0 published (with --dry-run: everything green),
// 1 this record failed (the loop goes on with the next), 3 nothing ready, 4 stop the loop (unsafe state,
// a restore that did not take, push or rebase trouble, a page that never went live, Ctrl+C).
// scripts/publish-loop.sh runs it in a loop. Flags:
//   --dry-run   everything but commit and push, then put the record back and exclude it for the session
//   --no-push   commit, but do not push (no worktree or upstream requirements), for testing
//   --port N    the preview server's port for the tests (default 46327, or PUBLISH_PORT)
import { spawn, spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { connect } from 'node:net';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { COMPARISONS_ENABLED } from '../src/lib/species-source.mjs';

export const EXIT = { published: 0, failed: 1, none: 3, stop: 4 };
export const EXCLUDED_FILE = 'reports/publish-loop-excluded.txt';
export const STOP_FILE = 'reports/STOP';
export const DEFAULT_PORT = 46327;
export const SITE = 'https://birdy.community';
export const TRAILER = 'Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>';
/** Git never asks for a password or opens a credential window: a prompt nobody answers would hang the loop. */
export const GIT_ENV = { GIT_TERMINAL_PROMPT: '0', GCM_INTERACTIVE: 'never' };
const SPECIES_DIR = 'src/data/species';
const COMPARISONS_DIR = 'src/data/comparisons';
const ASSETS_DIR = 'src/assets/species';

/** @param {string[]} argv */
export function parseArgs(argv) {
  const out = { dryRun: false, push: true, port: Number(process.env.PUBLISH_PORT ?? DEFAULT_PORT) };
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

/** The record's JSON file (relative to website/). */
export function recordFile(pick) {
  return pick.kind === 'species' ? `${SPECIES_DIR}/${pick.id}.json` : `${COMPARISONS_DIR}/${pick.id}.json`;
}

/** The photos and recording a species record points at (relative to website/); a comparison has none. */
export function assetFiles(pick, record) {
  if (pick.kind !== 'species') return [];
  const files = [...(record.images ?? []).map((i) => i.file), ...(record.audio ? [record.audio.file] : [])];
  return files.map((f) => `${ASSETS_DIR}/${f}`);
}

/** The page's address in both languages, without the leading slash (as the tests take them). */
export function pagePaths(record) {
  return [`sv/arter/${record.slug.sv}/`, `species/${record.slug.en}/`];
}

/**
 * The addresses axe checks: the page on both languages, plus the hub and (for a species) its group page,
 * which change with every publication and turn indexable along the way.
 */
export function axePaths(pick, record, groups) {
  const group = pick.kind === 'species' ? groups.find((g) => g.key === record.group) : undefined;
  return [...pagePaths(record), 'sv/arter/', 'species/', ...(group ? [`sv/arter/${group.slug.sv}/`, `species/${group.slug.en}/`] : [])];
}

/** The live addresses to poll after the push; the token keeps a cache from answering with an old 404. */
export function liveUrls(site, paths, token) {
  return paths.map((p) => `${site}/${p}?live=${token}`);
}

/** "data(artsidor): Talgoxe (Q25485)" or "data(artsidor): Blåmes eller talgoxe (Q25404+Q25485)". */
export function commitMessage(pick, names) {
  const subject = pick.kind === 'species'
    ? `data(artsidor): ${names[0]} (${pick.id})`
    : `data(artsidor): ${names[0]} eller ${names[1].toLocaleLowerCase('sv')} (${pick.id.replace('_', '+')})`;
  return `${subject}\n\n${TRAILER}\n`;
}

/**
 * Puts files back as they are in HEAD, index AND working tree (a plain `git checkout -- file` restores from
 * the index, so after a `git add` it restored nothing), then checks that nothing is left.
 * @returns {{ ok: boolean, detail: string }}
 */
export function restoreFiles(cwd, files, env = process.env) {
  if (!files.length) return { ok: true, detail: '' };
  const restored = spawnSync('git', ['restore', '--source=HEAD', '--staged', '--worktree', '--', ...files], { cwd, env, encoding: 'utf8' });
  const left = spawnSync('git', ['status', '--porcelain', '--', ...files], { cwd, env, encoding: 'utf8' });
  const ok = restored.status === 0 && left.status === 0 && left.stdout.trim() === '';
  return { ok, detail: [restored.stderr, left.stdout, left.stderr].filter(Boolean).join('\n').trim() };
}

/** Nothing changed, staged or untracked anywhere in the checkout, the loop's own reports/ aside. */
export function treeIsClean(repoRoot, env = process.env) {
  const status = spawnSync('git', ['status', '--porcelain', '--untracked-files=all', '--', '.', ':(exclude)website/reports'], { cwd: repoRoot, env, encoding: 'utf8' });
  return { clean: status.status === 0 && status.stdout.trim() === '', detail: `${status.stdout}${status.stderr}`.trim() };
}

/** Local date and time, for the report's file name and headings. */
const stamp = () => {
  const now = new Date();
  return { date: now.toLocaleDateString('sv-SE'), time: now.toLocaleTimeString('sv-SE', { hour: '2-digit', minute: '2-digit' }) };
};
// The last lines of a command's output, without the terminal's colour and cursor codes.
// eslint-disable-next-line no-control-regex
const tail = (text, lines = 60) => (text ?? '').replace(/\x1b\[[0-9;?]*[A-Za-z]/g, '').trimEnd().split(/\r?\n/).slice(-lines).join('\n').trim();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
    await sleep(500);
  }
  return false;
}

/**
 * How main() ends: thrown, caught at the bottom of the file, and turned into process.exitCode, so Node exits
 * by itself once its handles are closed. process.exit() with a fetch socket or a child's pipe still closing
 * aborts Node on Windows ("Assertion failed: ... UV_HANDLE_CLOSING", seen in the trial run), and the loop
 * then reads a crash code instead of 0, 1, 3 or 4.
 */
export class Exit extends Error {
  constructor(code) {
    super(`exit ${code}`);
    this.code = code;
  }
}

/** Set by main(): puts a picked record back and reports, for errors that escape main(). Never throws. */
let onFatal = null;

// Child processes, so a signal or a timeout can stop the whole tree (a shell's children too, on Windows).
const children = new Set();
function killTree(child) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  if (process.platform === 'win32') spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
  else child.kill('SIGTERM');
}

/** Runs a command without blocking the event loop (so Ctrl+C is handled), with a timeout. */
function run(command, args, { cwd, env, timeoutMs, shell = false }) {
  return new Promise((done) => {
    const child = shell ? spawn(command, { cwd, env, shell: true, windowsHide: true }) : spawn(command, args, { cwd, env, windowsHide: true });
    children.add(child);
    let stdout = '';
    let stderr = '';
    let timedOut = false;
    child.stdout.on('data', (d) => { stdout += d; });
    child.stderr.on('data', (d) => { stderr += d; });
    const timer = setTimeout(() => { timedOut = true; killTree(child); }, timeoutMs);
    const finish = (status, error) => {
      clearTimeout(timer);
      children.delete(child);
      done({ status: timedOut ? null : status, stdout, stderr: `${stderr}${error ? `\n${error.message}` : ''}${timedOut ? `\navbruten efter ${timeoutMs / 1000} s` : ''}` });
    };
    child.once('error', (e) => finish(null, e));
    child.once('close', (code) => finish(code));
  });
}

async function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const repoRoot = resolve(root, '..');
  const pipeline = resolve(repoRoot, 'tools/content-pipeline');
  const opts = parseArgs(process.argv.slice(2));
  // Never let an inherited variable change the production build or the tests (build:prod pins the first two).
  const env = { ...process.env, ...GIT_ENV };
  for (const name of ['SPECIES_FIXTURES', 'SPECIES_PREVIEW', 'SPECIES_EMPTY', 'CI']) delete env[name];

  const say = (line) => console.log(`publish-next: ${line}`);
  const git = (args) => spawnSync('git', args, { cwd: root, env, encoding: 'utf8' });
  const gitNet = (args) => run('git', args, { cwd: root, env, timeoutMs: 60_000 });
  const sh = (command, extraEnv = {}, timeoutMs = 15 * 60_000) => run(command, [], { cwd: root, env: { ...env, ...extraEnv }, timeoutMs, shell: true });

  mkdirSync(resolve(root, 'reports'), { recursive: true });
  const report = resolve(root, `reports/publish-loop-${stamp().date}.md`);
  const writeReport = (heading, body = '') => appendFileSync(report, `## ${stamp().time} ${heading}\n\n${body ? `\`\`\`\n${body}\n\`\`\`\n\n` : ''}`);
  const excludedPath = resolve(root, EXCLUDED_FILE);

  // What a failure or a signal must put back once a record is picked (cleared after the commit).
  let pick = null;
  let restoreList = [];
  // Photos or a recording that HEAD doesn't have yet are only unstaged on a failure, never deleted.
  let unstageList = [];
  const putBack = () => {
    if (unstageList.length) git(['reset', '-q', '--', ...unstageList]);
    const result = restoreFiles(root, restoreList, env);
    if (result.ok) {
      restoreList = [];
      unstageList = [];
    }
    return result;
  };
  /** Puts the record back first and reports the stop; a restore that does not take is reported loudly. */
  const halt = (heading, details = '') => {
    const back = putBack();
    const restoreNote = back.ok ? '' : `\n\nÅTERSTÄLLNINGEN MISSLYCKADES, kontrollera för hand: ${back.detail}`;
    writeReport(`STOPP: ${heading}${pick ? ` (${pick.kind} ${pick.id})` : ''}`, `${details}${restoreNote}`.trim());
    console.error(`publish-next: STOPP: ${heading}${back.ok ? '' : ` OCH ÅTERSTÄLLNINGEN MISSLYCKADES (${back.detail})`}; se ${report}`);
  };
  /** Stops the loop. */
  const stop = (heading, details = '') => {
    halt(heading, details);
    throw new Exit(EXIT.stop);
  };
  /** This record failed: put it back, exclude it for the session, let the loop go on. */
  const fail = (step, details) => {
    const back = putBack();
    if (!back.ok) stop(`${pick.id} föll i steget ${step} och gick inte att återställa`, `${details}\n\n${back.detail}`);
    appendFileSync(excludedPath, `${pick.id}\n`);
    writeReport(`${pick.kind} ${pick.id} FAILED i steget ${step} (återställd, utesluten i sessionen)`, details);
    console.error(`publish-next: ${pick.kind} ${pick.id} föll i steget ${step}; posten är återställd och utesluten, se ${report}`);
    throw new Exit(EXIT.failed);
  };
  // Ctrl+C, a kill, or an exception outside main()'s own flow: put the record back and stop the loop at once.
  const abort = (heading, details = '') => {
    for (const child of children) killTree(child);
    halt(heading, details);
    process.exit(EXIT.stop);
  };
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => abort(`avbruten med ${signal}`));
  process.on('uncaughtException', (e) => abort('oväntat fel', e?.stack ?? String(e)));
  onFatal = (e) => {
    for (const child of children) killTree(child);
    halt('oväntat fel', e?.stack ?? String(e));
  };

  // -- 0. Preflight: nothing is changed before every check passes ------------------------------------------
  const problems = [];
  if (existsSync(resolve(root, STOP_FILE))) problems.push(`${STOP_FILE} finns (ta bort den för att publicera igen)`);
  if (!existsSync(pipeline)) problems.push(`pipelinen saknas: ${pipeline}`);
  if (await portInUse(opts.port)) problems.push(`porten ${opts.port} är upptagen (en annan server?), välj en annan med --port eller PUBLISH_PORT`);
  const live = opts.push && !opts.dryRun;
  if (live) {
    // The main clone is shared with other sessions: the loop only publishes from a worktree of its own.
    const gitDir = resolve(root, git(['rev-parse', '--git-dir']).stdout.trim());
    const commonDir = resolve(root, git(['rev-parse', '--git-common-dir']).stdout.trim());
    if (gitDir === commonDir) problems.push('det här är huvudklonen; loopen pushar bara från en egen worktree (planens Task 16, "Uppsättning")');
    const upstream = git(['rev-parse', '--abbrev-ref', '--symbolic-full-name', '@{u}']);
    const detached = git(['symbolic-ref', '-q', 'HEAD']).status !== 0;
    if (!detached && upstream.stdout.trim() !== 'origin/main') problems.push(`grenen följer ${upstream.stdout.trim() || 'ingenting'}, inte origin/main`);
    const tree = treeIsClean(repoRoot, env);
    if (!tree.clean) problems.push(`utcheckningen är inte ren:\n${tree.detail}`);
  }
  if (problems.length) {
    writeReport('förkontroll: STOPP (ingen post vald)', problems.join('\n'));
    for (const p of problems) console.error(`publish-next: ${p}`);
    throw new Exit(EXIT.stop);
  }
  if (live) {
    const fetched = await gitNet(['fetch', 'origin', 'main']);
    const merged = fetched.status === 0 ? git(['merge', '--ff-only', 'origin/main']) : fetched;
    const ahead = merged.status === 0 ? git(['rev-list', '--count', 'origin/main..HEAD']).stdout.trim() : '';
    if (merged.status !== 0 || ahead !== '0') {
      const why = merged.status !== 0 ? `fetch eller merge --ff-only misslyckades:\n${merged.stdout}${merged.stderr}` : `${ahead} lokala commits är inte pushade`;
      writeReport('förkontroll: STOPP (ingen post vald)', why);
      console.error(`publish-next: STOPP: ${why}`);
      throw new Exit(EXIT.stop);
    }
  }

  const excluded = existsSync(excludedPath) ? readExcluded(readFileSync(excludedPath, 'utf8')) : [];
  // Outside a live run a record with uncommitted changes (somebody's spot check or import) is never picked.
  const dirtyBefore = dirtyRecordIds(git(['status', '--porcelain', '--', SPECIES_DIR, COMPARISONS_DIR]).stdout);
  const stems = existsSync(resolve(root, COMPARISONS_DIR))
    ? readdirSync(resolve(root, COMPARISONS_DIR)).filter((f) => /^Q\d+_Q\d+\.json$/.test(f)).map((f) => f.slice(0, -5))
    : [];

  // -- 1. The pick -------------------------------------------------------------------------------------
  const picked = await run('uv', nextArgs([...excluded, ...dirtyBefore], stems, COMPARISONS_ENABLED), { cwd: pipeline, env, timeoutMs: 10 * 60_000 });
  // Whatever --next wrote is put back on any failure from here, even when its answer can't be read.
  const writtenByNext = dirtyRecordIds(git(['status', '--porcelain', '--', SPECIES_DIR, COMPARISONS_DIR]).stdout).filter((id) => !dirtyBefore.includes(id));
  restoreList = writtenByNext.map((id) => recordFile({ kind: id.includes('_') ? 'comparison' : 'species', id }));
  if (picked.status !== 0) stop('web publish --next misslyckades', `${tail(picked.stderr)}\n${tail(picked.stdout)}`);
  try {
    pick = parsePick(picked.stdout);
  } catch (e) {
    stop('web publish --next gav ett oväntat svar', `${e.message}\n${tail(picked.stderr)}`);
  }
  if (!pick) {
    if (restoreList.length) stop('web publish --next svarade none men ändrade filer', restoreList.join('\n'));
    say(`inget att publicera just nu (${excluded.length} uteslutna i sessionen, ${dirtyBefore.length} med ocommittade ändringar${COMPARISONS_ENABLED ? '' : ', jämförelserna avstängda till Task 11'})`);
    throw new Exit(EXIT.none);
  }
  const json = recordFile(pick);
  if (restoreList.length !== 1 || restoreList[0] !== json) stop(`web publish --next valde ${pick.id} men ändrade ${restoreList.join(', ') || 'ingenting'}`);

  let record;
  let names;
  let paths;
  let axe;
  try {
    record = JSON.parse(readFileSync(resolve(root, json), 'utf8'));
    if (pick.kind === 'comparison' && !COMPARISONS_ENABLED) throw new Error('en jämförelse valdes fast jämförelserna är avstängda (COMPARISONS_ENABLED)');
    names = pick.kind === 'species'
      ? [record.names.sv]
      : [record.a, record.b].map((qid) => JSON.parse(readFileSync(resolve(root, SPECIES_DIR, `${qid}.json`), 'utf8')).names.sv);
    paths = pagePaths(record);
    axe = axePaths(pick, record, JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).groups);
  } catch (e) {
    fail('läsa posten', e.message);
  }
  say(`${pick.kind} ${pick.id} (${names.join(' / ')}): bygger och testar /${paths.join(' och /')}`);

  // -- 2. Build and check (again after a rebase) -----------------------------------------------------------
  /** @returns {Promise<string | null>} the failed step and its output, or null when everything is green */
  const verify = async () => {
    const dashes = await sh('node scripts/check-no-dashes.mjs', {}, 120_000);
    if (dashes.status !== 0) return `check-no-dashes\n${tail(dashes.stdout)}\n${tail(dashes.stderr)}`;
    const build = await sh('npm run build:prod');
    if (build.status !== 0) return `build:prod\n${tail(build.stdout)}\n${tail(build.stderr)}`;
    const seo = await sh('node scripts/check-seo.mjs', {}, 300_000);
    if (seo.status !== 0) return `check-seo\n${tail(seo.stdout)}\n${tail(seo.stderr)}`;
    for (const p of paths) if (!existsSync(resolve(root, 'dist', p, 'index.html'))) return `bygget\n/${p} finns inte i dist/`;
    // The tests get a preview server of their own (foreground, outside Astro's preview lock, which another
    // session's server may hold); Playwright reuses it instead of starting one.
    const server = spawn(process.execPath, [resolve(root, 'node_modules/astro/bin/astro.mjs'), 'preview', '--port', String(opts.port), '--strictPort', '--ignore-lock'], { cwd: root, env, stdio: 'ignore', windowsHide: true });
    children.add(server);
    try {
      const up = await waitForServer(`http://localhost:${opts.port}/`, 60_000);
      // Something else answering on the port while ours died would test the wrong site.
      if (!up || server.exitCode !== null) return `förhandsvisningsservern\nastro preview ${up ? 'avslutades, en annan server svarar' : 'svarade inte'} på port ${opts.port}`;
      const tests = await sh(
        `"${process.execPath}" node_modules/@playwright/test/cli.js test tests/published-page.spec.ts tests/a11y.spec.ts --workers=2 --reporter=line`,
        { PAGE_PATHS: paths.join(','), AXE_PATH: axe.join(','), PLAYWRIGHT_PORT: String(opts.port) },
        10 * 60_000,
      );
      if (tests.status !== 0) return `Playwright och axe\n${tail(tests.stdout, 120)}\n${tail(tests.stderr)}`;
      say(`testerna gröna: ${tail(tests.stdout, 1)}`);
      return null;
    } finally {
      killTree(server);
      children.delete(server);
    }
  };
  const red = await verify();
  if (red) {
    const [step, ...rest] = red.split('\n');
    fail(step, rest.join('\n'));
  }

  // -- 3. Publish ---------------------------------------------------------------------------------------
  if (opts.dryRun) {
    const back = putBack();
    if (!back.ok) stop(`torrkörningen av ${pick.id} gick inte att återställa`, back.detail);
    // Excluded, so a loop of dry runs goes on with the next record instead of picking this one again.
    appendFileSync(excludedPath, `${pick.id}\n`);
    writeReport(`${pick.kind} ${pick.id}: torrkörning grön (återställd, utesluten i sessionen, inget committat)`);
    say(`torrkörning: allt grönt för ${pick.id}, posten är återställd och inget är committat`);
    throw new Exit(EXIT.published);
  }
  // Only what the record points at, deletions under its folder included.
  const assets = assetFiles(pick, record).filter((f) => existsSync(resolve(root, f)));
  const deleted = pick.kind === 'species' ? git(['ls-files', '--deleted', '--', `${ASSETS_DIR}/${pick.id}`]).stdout.split(/\r?\n/).filter(Boolean) : [];
  const commitPaths = [json, ...assets, ...deleted];
  const inHead = new Set(git(['ls-tree', '-r', '--name-only', 'HEAD', '--', ...commitPaths]).stdout.split(/\r?\n/).filter(Boolean));
  restoreList = commitPaths.filter((f) => inHead.has(f));
  unstageList = commitPaths.filter((f) => !inHead.has(f));
  const added = git(['add', '-A', '--', ...commitPaths]);
  const committed = added.status === 0 ? git(['commit', '-m', commitMessage(pick, names), '--', ...commitPaths]) : added;
  if (committed.status !== 0) fail('git commit', `${committed.stdout}\n${committed.stderr}`);
  restoreList = [];
  unstageList = [];
  const hash = git(['rev-parse', '--short', 'HEAD']).stdout.trim();
  if (!opts.push) {
    writeReport(`${pick.kind} ${pick.id} (${names.join(' / ')}): committad ${hash}, inte pushad (--no-push)`);
    say(`${names.join(' / ')} (${pick.id}) committad som ${hash} (inte pushad)`);
    throw new Exit(EXIT.published);
  }

  let pushed = await gitNet(['push', 'origin', 'HEAD:main']);
  if (pushed.status !== 0) {
    // Someone else pushed in the meantime: rebase once (no autostash, the tree is clean), test the new tree
    // again, push again. Anything else stops the loop with the commit kept locally for a person to look at.
    say(`pushen avvisades (någon annan pushade under tiden): rebase på origin/main och en ny kontroll`);
    writeReport(`${pick.kind} ${pick.id}: push avvisad, rebase på origin/main och ny kontroll`, tail(pushed.stderr, 10));
    const fetched = await gitNet(['fetch', 'origin', 'main']);
    const rebased = fetched.status === 0 ? git(['rebase', 'origin/main']) : fetched;
    if (rebased.status !== 0) {
      git(['rebase', '--abort']);
      stop(`push avvisad och rebase misslyckades; ${hash} ligger kvar lokalt`, `${pushed.stderr}\n${rebased.stdout}${rebased.stderr}`);
    }
    const again = await verify();
    if (again) stop(`efter rebase på origin/main föll ${again.split('\n')[0]}; commiten ligger kvar lokalt`, again);
    pushed = await gitNet(['push', 'origin', 'HEAD:main']);
    if (pushed.status !== 0) stop(`push avvisad två gånger; commiten ligger kvar lokalt`, `${pushed.stdout}${pushed.stderr}`);
  }
  const pushedHash = git(['rev-parse', '--short', 'HEAD']).stdout.trim();
  say(`${names.join(' / ')} (${pick.id}) pushad som ${pushedHash}, väntar på att sidan blir live`);

  // -- 4. Live on birdy.community ----------------------------------------------------------------------
  const site = process.env.PUBLISH_SITE ?? SITE;
  const seconds = Number(process.env.PUBLISH_LIVE_TIMEOUT ?? 600);
  const until = Date.now() + (Number.isFinite(seconds) && seconds > 0 ? seconds : 600) * 1000;
  for (;;) {
    const statuses = await Promise.all(liveUrls(site, paths, pushedHash).map(async (url) => {
      try {
        return (await fetch(url, { headers: { 'cache-control': 'no-cache' } })).status;
      } catch {
        return 0;
      }
    }));
    if (statuses.every((code) => code === 200)) break;
    if (Date.now() >= until) {
      stop(`${pick.id} är pushad (${pushedHash}) men inte live på ${site} i tid; kontrollera Vercel`, paths.map((path, i) => `/${path}: ${statuses[i]}`).join('\n'));
    }
    await sleep(15_000);
  }
  writeReport(`${pick.kind} ${pick.id} (${names.join(' / ')}): publicerad ${pushedHash}, live`);
  say(`${names.join(' / ')} (${pick.id}) är live: ${site}/${paths[0]}`);
  throw new Exit(EXIT.published);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((e) => {
    if (e instanceof Exit) {
      process.exitCode = e.code;
      return;
    }
    if (onFatal) onFatal(e);
    else console.error(`publish-next: ${e?.stack ?? e}`);
    process.exitCode = EXIT.stop;
  });
}
