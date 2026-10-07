// Reads the species records (read only) from a website directory:
//   <data>/src/data/species/<QID>.json and media under <data>/src/assets/species/.
import { readdir, readFile, access } from 'node:fs/promises';
import { join } from 'node:path';
import { heroImage, qualifies } from './licence.mjs';

export const DEFAULT_DATA = 'C:/w/birdy-artdata/website';

export function recordsDir(dataDir) {
  return join(dataDir, 'src', 'data', 'species');
}

export function mediaPath(dataDir, file) {
  return join(dataDir, 'src', 'assets', 'species', ...file.split('/'));
}

export async function loadRecords(dataDir) {
  const dir = recordsDir(dataDir);
  const files = (await readdir(dir)).filter((f) => /^Q\d+\.json$/.test(f));
  const records = [];
  for (const f of files) records.push(JSON.parse(await readFile(join(dir, f), 'utf8')));
  return records;
}

async function exists(p) {
  try {
    await access(p);
    return true;
  } catch {
    return false;
  }
}

/** Licence rule plus a check that the photo and recording files are on disk. */
export async function qualifiesWithFiles(record, dataDir) {
  const result = qualifies(record);
  if (!result.ok) return result;
  const reasons = [];
  const hero = heroImage(record);
  if (!(await exists(mediaPath(dataDir, hero.file)))) reasons.push(`photo file missing: ${hero.file}`);
  if (!(await exists(mediaPath(dataDir, record.audio.file)))) reasons.push(`recording file missing: ${record.audio.file}`);
  return { ok: reasons.length === 0, reasons };
}
