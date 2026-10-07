import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { stagedOutputs, writeFileAtomic, partialPath } from '../lib/atomic.mjs';

async function withDir(fn) {
  const dir = await mkdtemp(join(tmpdir(), 'birdy-atomic-'));
  try {
    await fn(dir);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test('staged outputs appear together on commit', () =>
  withDir(async (dir) => {
    const out = stagedOutputs(dir);
    await out.write('caption.json', '{}');
    await writeFile(out.path('see-the-song.mp4'), 'video');
    assert.deepEqual((await readdir(dir)).sort(), ['.caption.json.partial', '.see-the-song.mp4.partial']);
    await out.commit();
    assert.deepEqual((await readdir(dir)).sort(), ['caption.json', 'see-the-song.mp4']);
    assert.equal(await readFile(join(dir, 'see-the-song.mp4'), 'utf8'), 'video');
  }));

test('a failed render leaves the previous files untouched and no partial files', () =>
  withDir(async (dir) => {
    await writeFile(join(dir, 'see-the-song.mp4'), 'old video');
    await writeFile(join(dir, 'caption.json'), 'old caption');
    const out = stagedOutputs(dir);
    await out.write('caption.json', 'new caption');
    await writeFile(out.path('see-the-song.mp4'), 'half a vid');
    await out.discard();
    assert.deepEqual((await readdir(dir)).sort(), ['caption.json', 'see-the-song.mp4']);
    assert.equal(await readFile(join(dir, 'see-the-song.mp4'), 'utf8'), 'old video');
    assert.equal(await readFile(join(dir, 'caption.json'), 'utf8'), 'old caption');
  }));

test('commit replaces existing files', () =>
  withDir(async (dir) => {
    await writeFile(join(dir, 'cover.jpg'), 'old');
    const out = stagedOutputs(dir);
    await out.write('cover.jpg', 'new');
    await out.commit();
    assert.equal(await readFile(join(dir, 'cover.jpg'), 'utf8'), 'new');
  }));

test('writeFileAtomic: the file is complete or not there', () =>
  withDir(async (dir) => {
    const file = join(dir, 'schedule.csv');
    await writeFileAtomic(file, 'a,b\r\n');
    assert.equal(await readFile(file, 'utf8'), 'a,b\r\n');
    assert.deepEqual(await readdir(dir), ['schedule.csv']);
    assert.equal(partialPath(file), join(dir, '.schedule.csv.partial'));
  }));
