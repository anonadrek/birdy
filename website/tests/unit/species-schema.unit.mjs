// node --test "tests/unit/*.unit.mjs"
// The site's zod schemas (src/lib/species-schema.mjs): the full page contract only for records the site
// may build, an envelope for every other record, so a broken record that never gets a page cannot stop
// the build of every other page.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { comparisonRecord, speciesRecord } from '../../src/lib/species-schema.mjs';

const fixtures = resolve(dirname(fileURLToPath(import.meta.url)), '../fixtures');
const read = (dir, name) => JSON.parse(readFileSync(join(fixtures, dir, name), 'utf8'));
const species = (qid) => read('species', `${qid}.json`);
const messages = (result) => result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('\n');

// Page-only fields broken in three different ways.
const breakPageFields = (record) => ({
  ...record,
  images: 'inte en lista',
  data: { totalReports: -1 },
  audio: { file: 'fel.mp3' },
});

test('alla testposter och testjämförelser klarar schemat', () => {
  for (const name of readdirSync(join(fixtures, 'species')).filter((f) => f.endsWith('.json'))) {
    const result = speciesRecord.safeParse(read('species', name));
    assert.ok(result.success, `${name}\n${result.success ? '' : messages(result)}`);
  }
  for (const name of readdirSync(join(fixtures, 'comparisons')).filter((f) => f.endsWith('.json'))) {
    const result = comparisonRecord.safeParse(read('comparisons', name));
    assert.ok(result.success, `${name}\n${result.success ? '' : messages(result)}`);
  }
});

test('en väntande och en misslyckad post med trasiga sidfält klarar schemat, och sidfälten släpps', () => {
  for (const qid of ['Q143284', 'Q166171']) {
    const result = speciesRecord.safeParse(breakPageFields(species(qid)));
    assert.ok(result.success, `${qid}\n${result.success ? '' : messages(result)}`);
    assert.equal(result.data.qid, qid);
    assert.equal(result.data.names.sv.length > 0, true);
    for (const key of ['images', 'data', 'audio', 'text', 'facts', 'generated']) assert.equal(key in result.data, false, `${qid}: ${key} ska släppas`);
  }
});

test('en skriven men okontrollerad post (text, ingen verification) är ett kuvert', () => {
  const { verification, ...unverified } = species('Q25485');
  const result = speciesRecord.safeParse({ ...breakPageFields(unverified), publish: false });
  assert.ok(result.success, result.success ? '' : messages(result));
  assert.equal('text' in result.data, false);
});

test('samma trasiga sidfält stoppar en byggbar post, publicerad eller inte', () => {
  for (const qid of ['Q25485', 'Q26209']) {
    const result = speciesRecord.safeParse(breakPageFields(species(qid)));
    assert.equal(result.success, false, qid);
    const text = messages(result);
    for (const path of ['images', 'data.totalReports', 'audio.file']) assert.match(text, new RegExp(`^${path.replace('.', '\.')}: `, 'm'), `${qid}: ${path}\n${text}`);
  }
});

test('publish på en post som inte får byggas är fortfarande ett fel', () => {
  for (const qid of ['Q143284', 'Q166171']) {
    const result = speciesRecord.safeParse({ ...species(qid), publish: true });
    assert.equal(result.success, false, qid);
    assert.match(messages(result), new RegExp(`^publish: ${qid}: publish kräver status ok`, 'm'));
  }
  const { verification, ...unverified } = species('Q25485');
  assert.equal(speciesRecord.safeParse(unverified).success, false, 'publicerad men okontrollerad');
});

test('kuvertet kontrolleras: namn, adresser och text: null för väntande poster', () => {
  const pending = species('Q143284');
  const { names, ...noNames } = pending;
  assert.match(messages(speciesRecord.safeParse(noNames)), /^names: /m);
  assert.match(messages(speciesRecord.safeParse({ ...pending, slug: { sv: 'spillkraka' } })), /^slug\.en: /m);
  assert.match(messages(speciesRecord.safeParse({ ...pending, text: species('Q25485').text })), /^text: Q143284: status pending ska ha text: null/m);
});

test('marginalia kan sakna ett språk (null), och wikipedia kan sakna vilken artikel som helst', () => {
  const record = species('Q25485');
  const result = speciesRecord.safeParse({ ...record, marginalia: { sv: 'Bara på svenska.', en: null }, wikipedia: { de: record.wikipedia.de } });
  assert.ok(result.success, result.success ? '' : messages(result));
  assert.deepEqual(result.data.marginalia, { sv: 'Bara på svenska.', en: null });
});
