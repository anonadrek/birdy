#!/usr/bin/env node
// Prints one video's caption fields as JSON strings, ready to type into the platforms' editors: Facebook, Instagram,
// TikTok (Instagram's text split into the body and the hashtags, since TikTok's editor turns each tag into a chip) and
// YouTube's title and description. Replaces the local cap.py helper the scheduling runbook used to mention.
//
//   node tools/social/caption-fields.mjs <slug>        e.g. mute-swan; searched in out/*/<slug>/caption.json
import { readdir, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const slug = process.argv[2];
if (!slug) {
  console.error('usage: node caption-fields.mjs <slug>');
  process.exit(2);
}

const outDir = join(here, 'out');
const found = [];
for (const set of await readdir(outDir, { withFileTypes: true })) {
  if (!set.isDirectory()) continue;
  const file = join(outDir, set.name, slug, 'caption.json');
  try {
    found.push({ file, caption: JSON.parse(await readFile(file, 'utf8')) });
  } catch (err) {
    if (err.code !== 'ENOENT') throw err;
  }
}
if (found.length !== 1) {
  console.error(found.length === 0 ? `no out/*/${slug}/caption.json` : `${slug} is in more than one set: ${found.map((f) => f.file).join(', ')}`);
  process.exit(1);
}

const { file, caption } = found[0];
const ig = caption.instagram;
const tagsAt = ig.lastIndexOf('\n\n#');
const show = (label, value) => console.log(`${label}= ${JSON.stringify(value)}`);
show('DIR', dirname(file).replaceAll('\\', '/'));
show('FB', caption.facebook);
show('IG', ig);
show('TTBODY', tagsAt >= 0 ? ig.slice(0, tagsAt + 2) : ig);
show('TTTAGS', tagsAt >= 0 ? `${ig.slice(tagsAt + 2)} ` : '');
show('YTTITLE', caption.youtube.title);
show('YTDESC', caption.youtube.description);
