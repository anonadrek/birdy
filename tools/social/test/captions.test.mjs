import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildCaptions, creditLine, hook, linkFor, nameTag, voiceWord, youtubeTitle, firstSentence } from '../lib/captions.mjs';
import { record, withAudio, withHero, approved } from './fixtures.mjs';

const CREDIT = 'Photo: Julian Herzog, CC BY 4.0, via Wikimedia Commons, cropped · Sound: Oona Räisänen (Mysid), Public domain, via Wikimedia Commons, edited';
const CREDIT_TRIMMED = 'Photo: Julian Herzog, CC BY 4.0, via Wikimedia Commons, cropped · Sound: Oona Räisänen (Mysid), Public domain, via Wikimedia Commons, trimmed and edited';
const CREDIT_URLS =
  'Photo: Julian Herzog, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), via Wikimedia Commons (https://commons.wikimedia.org/wiki/File:x.jpg), cropped · Sound: Oona Räisänen (Mysid), Public domain, via Wikimedia Commons (https://commons.wikimedia.org/wiki/File:Pica_pica.ogg), edited';
const TAGS = '#birds #birdwatching #birdsong #birding #birdy #EurasianMagpie';

test('credit line, exact string: the sound is always "edited"', () => {
  assert.equal(creditLine(record()), CREDIT);
});

test('credit line says "trimmed and edited" when the clip was trimmed', () => {
  assert.equal(creditLine(record(), { trimmed: true }), CREDIT_TRIMMED);
});

test('credit line leaves out an empty author for CC0 and public domain', () => {
  const rec = withHero(withAudio(record(), { author: '', license: 'CC0' }), { author: '', license: 'Public domain' });
  assert.equal(creditLine(rec), 'Photo: Public domain, via Wikimedia Commons, cropped · Sound: CC0, via Wikimedia Commons, edited');
});

test('credit line with URLs: the licence deed after its name and the Commons file after "via Wikimedia Commons"', () => {
  assert.equal(creditLine(record(), { withUrls: true }), CREDIT_URLS);
  const rec = withAudio(record(), { author: 'Gavin Vella', license: 'CC BY-SA 3.0', licenseUrl: null, sourceUrl: 'https://commons.wikimedia.org/wiki/File:Parus_major_-_Great_Tit_XC129643.ogg' });
  assert.match(
    creditLine(rec, { withUrls: true, trimmed: true }),
    /Sound: Gavin Vella, CC BY-SA 3\.0 \(https:\/\/creativecommons\.org\/licenses\/by-sa\/3\.0\/\), via Wikimedia Commons \(https:\/\/commons\.wikimedia\.org\/wiki\/File:Parus_major_-_Great_Tit_XC129643\.ogg\), trimmed and edited$/,
  );
  const cc0 = withAudio(record(), { author: '', license: 'CC0', licenseUrl: null, sourceUrl: undefined });
  assert.match(creditLine(cc0, { withUrls: true }), /Sound: CC0 \(https:\/\/creativecommons\.org\/publicdomain\/zero\/1\.0\/\), via Wikimedia Commons, edited$/);
  // Instagram's short form has no links at all.
  assert.doesNotMatch(creditLine(record()), /https?:/);
});

test('Commons links are made safe for captions: parentheses and dashes in file names are encoded', () => {
  const rec = withAudio(record(), { sourceUrl: 'https://commons.wikimedia.org/wiki/File:Common_Gull_(Fiskem%C3%A5ke)_(Larus_canus)_\u2013_Tromsø.ogg' });
  const c = buildCaptions(rec);
  assert.match(c.facebook, /via Wikimedia Commons \(https:\/\/commons\.wikimedia\.org\/wiki\/File:Common_Gull_%28Fiskem%C3%A5ke%29_%28Larus_canus%29_%E2%80%93_Troms%C3%B8\.ogg\), edited/);
  for (const text of [c.facebook, c.youtube.description]) assert.doesNotMatch(text, /[\u2013\u2014]/);
});

test('link goes to the species page only when the record is published', () => {
  assert.equal(linkFor(record()), 'https://birdy.community/');
  assert.equal(linkFor(record({ publish: true })), 'https://birdy.community/species/eurasian-magpie/');
  assert.equal(linkFor(record({ publish: 'true' })), 'https://birdy.community/');
});

test('unpublished: Facebook and YouTube link the home page, never the species page', () => {
  const c = buildCaptions(record());
  assert.match(c.facebook, /Identify birds by sound with the free Birdy app: https:\/\/birdy\.community\/\n/);
  assert.doesNotMatch(c.facebook + c.youtube.description, /\/species\//);
  assert.match(c.youtube.description, /https:\/\/birdy\.community\//);
});

test('published: Facebook and YouTube link the species page', () => {
  const c = buildCaptions(record({ publish: true }));
  assert.match(c.facebook, /More about the Eurasian Magpie: https:\/\/birdy\.community\/species\/eurasian-magpie\//);
  assert.match(c.youtube.description, /https:\/\/birdy\.community\/species\/eurasian-magpie\//);
});

test('Instagram says Link in bio and has no URL', () => {
  for (const rec of [record(), record({ publish: true })]) {
    const c = buildCaptions(rec);
    assert.match(c.instagram, /Identify birds by sound with the free Birdy app\. Link in bio\./);
    assert.doesNotMatch(c.instagram, /https?:\/\/|birdy\.community/);
  }
});

test('captions end with the credit and then the hashtags; Facebook and YouTube carry the licence URLs', () => {
  const c = buildCaptions(record(), { trimmed: true });
  assert.equal(c.instagram.split('\n\n').at(-2), CREDIT_TRIMMED);
  for (const text of [c.facebook, c.youtube.description]) assert.equal(text.split('\n\n').at(-2), CREDIT_URLS.replace(', edited', ', trimmed and edited'));
  for (const text of [c.instagram, c.facebook, c.youtube.description]) assert.equal(text.split('\n\n').at(-1), TAGS);
  assert.equal(c.credit, CREDIT_TRIMMED);
});

test('5 to 8 hashtags, the English name as one tag', () => {
  const c = buildCaptions(record());
  assert.ok(c.hashtags.length >= 5 && c.hashtags.length <= 8);
  assert.equal(nameTag(record({ names: { en: 'Common Wood Pigeon', sv: 'Ringduva', scientific: 'Columba palumbus' } })), '#CommonWoodPigeon');
  assert.equal(nameTag(record({ names: { en: "Montagu's Harrier", sv: 'Ängshök', scientific: 'Circus pygargus' } })), '#MontagusHarrier');
  assert.equal(nameTag(record({ names: { en: 'Black-tailed Godwit', sv: 'Rödspov', scientific: 'Limosa limosa' } })), '#BlackTailedGodwit');
});

test('"song" for the passerines (the songbird group, crows included), "voice" for every other group', () => {
  assert.equal(voiceWord(record()), 'song'); // the magpie is a passerine
  for (const group of ['waterfowl', 'gulls_terns', 'raptors', 'cranes_rails', 'woodpeckers', 'doves', 'other']) assert.equal(voiceWord(record({ group })), 'voice', group);
});

test('hook: neutral line without approved text, "voice" outside the songbird group', () => {
  assert.equal(hook(record()), 'Listen to the song of the Eurasian Magpie.');
  assert.equal(hook(record({ group: 'seabirds', names: { en: 'Great Cormorant', sv: 'Storskarv', scientific: 'Phalacrocorax carbo' } })), 'Listen to the voice of the Great Cormorant.');
  // Text without a verification stamp is not approved.
  const unverified = { ...approved(record(), 'The Eurasian Magpie is a black and white crow.'), verification: undefined };
  assert.equal(hook(unverified), 'Listen to the song of the Eurasian Magpie.');
});

test('hook: first sentence of the English lead when the text is approved', () => {
  assert.equal(hook(approved(record(), 'The Eurasian Magpie is a black and white crow. It has a long tail.')), 'The Eurasian Magpie is a black and white crow.');
  assert.equal(firstSentence('It is about 14 cm long. It sings.'), 'It is about 14 cm long.');
});

test('hook falls back to the neutral line if the lead has a dash', () => {
  assert.equal(hook(approved(record(), 'It is 44\u201346 cm long.')), 'Listen to the song of the Eurasian Magpie.');
});

test('no dashes anywhere in generated text, even when a name has one', () => {
  const rec = withAudio(record(), { author: 'Jean\u2013Pierre Dupont' });
  const c = buildCaptions(rec);
  for (const text of [c.instagram, c.facebook, c.youtube.title, c.youtube.description]) assert.doesNotMatch(text, /[\u2013\u2014]/);
  assert.match(c.facebook, /Jean-Pierre Dupont/);
});

test('without CC BY-SA material there is no video licence line', () => {
  const c = buildCaptions(record());
  assert.equal(c.videoLicence, null);
  for (const text of [c.instagram, c.facebook, c.youtube.description]) assert.doesNotMatch(text, /Video licensed/);
});

test('a video with CC BY-SA material gets the CC BY-SA 4.0 line right after the credits', () => {
  const rec = withAudio(record(), { license: 'CC BY-SA 3.0', author: 'José Carlos Sires', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/' });
  const c = buildCaptions(rec, { trimmed: true });
  assert.equal(c.videoLicence, 'CC BY-SA 4.0');
  const sa = 'Video licensed CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/)';
  assert.equal(c.instagram.split('\n\n').at(-2), `Photo: Julian Herzog, CC BY 4.0, via Wikimedia Commons, cropped · Sound: José Carlos Sires, CC BY-SA 3.0, via Wikimedia Commons, trimmed and edited\n${sa}`);
  for (const text of [c.facebook, c.youtube.description]) {
    assert.equal(
      text.split('\n\n').at(-2),
      `Photo: Julian Herzog, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), via Wikimedia Commons (https://commons.wikimedia.org/wiki/File:x.jpg), cropped · Sound: José Carlos Sires, CC BY-SA 3.0 (https://creativecommons.org/licenses/by-sa/3.0/), via Wikimedia Commons (https://commons.wikimedia.org/wiki/File:Pica_pica.ogg), trimmed and edited\n${sa}`,
    );
  }
  for (const text of [c.instagram, c.facebook, c.youtube.description]) assert.equal(text.split('\n\n').at(-1), TAGS);
  // On Instagram the licence URL is the only URL; the app link is still "Link in bio".
  assert.deepEqual(c.instagram.match(/https?:\/\/\S+/g), ['https://creativecommons.org/licenses/by-sa/4.0/)']);
  assert.match(c.instagram, /Link in bio\./);
});

test('YouTube title is at most 100 characters', () => {
  assert.equal(youtubeTitle(record()), 'What does the Eurasian Magpie sound like? #shorts');
  const long = record({ names: { en: 'A'.repeat(70), sv: 'x', scientific: 'y' } });
  assert.ok(youtubeTitle(long).length <= 100);
  assert.equal(youtubeTitle(long), `What does the ${'A'.repeat(70)} sound like?`);
  const huge = record({ names: { en: 'B'.repeat(140), sv: 'x', scientific: 'y' } });
  assert.ok(youtubeTitle(huge).length <= 100);
  for (const r of [record(), long, huge]) assert.ok(buildCaptions(r).youtube.title.length <= 100);
});
