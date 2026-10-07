// Atomic output: every file is first written next to its final name with a ".partial" suffix
// and renamed into place only when everything for that species (or the batch) is complete.
// A failed or interrupted render therefore never leaves a half-written video, or a video
// whose cover, caption or render info belongs to another run.
//
// Known limitation (left as it is): on Windows a rename fails while another program holds the
// final file open (a video player, Explorer's preview). commit() then throws part way: the
// files already renamed are the new ones, discard() drops the rest, and the species is
// reported as failed with a mix of new and old files; close the file and run it again.
import { rename, rm, writeFile } from 'node:fs/promises';
import { basename, dirname, join } from 'node:path';

export function partialPath(finalPath) {
  return join(dirname(finalPath), `.${basename(finalPath)}.partial`);
}

/** Writes one file atomically (a rename on the same disk replaces the old file in one step). */
export async function writeFileAtomic(finalPath, data) {
  const tmp = partialPath(finalPath);
  try {
    await writeFile(tmp, data);
    await rename(tmp, finalPath);
  } catch (e) {
    await rm(tmp, { force: true });
    throw e;
  }
}

/**
 * A set of outputs in one directory that appear together or not at all:
 *   const out = stagedOutputs(dir);
 *   await out.write('caption.json', text);  // or: render to out.path('see-the-song.mp4')
 *   await out.commit();                     // renames every file into place
 *   await out.discard();                    // after a failure: removes the partial files
 */
export function stagedOutputs(dir) {
  const files = new Map();
  const api = {
    /** The partial path to write `name` to (the final file is untouched until commit). */
    path(name) {
      const final = join(dir, name);
      const tmp = partialPath(final);
      files.set(final, tmp);
      return tmp;
    },
    async write(name, data) {
      await writeFile(api.path(name), data);
    },
    async commit() {
      for (const [final, tmp] of files) await rename(tmp, final);
      files.clear();
    },
    async discard() {
      for (const tmp of files.values()) await rm(tmp, { force: true });
      files.clear();
    },
  };
  return api;
}
