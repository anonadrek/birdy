// Captions for Instagram, Facebook and YouTube, built by code only from the species record:
// no model calls, no new claims. House style: plain, short English and no dashes.
import { heroImage, usesShareAlike, VIDEO_SA_LICENCE, VIDEO_SA_LICENCE_URL } from './licence.mjs';

/** Added after the credits when the video contains CC BY-SA material (only possible with --share-alike). */
export const SHARE_ALIKE_LINE = `Video licensed ${VIDEO_SA_LICENCE} (${VIDEO_SA_LICENCE_URL})`;

export const SITE = 'https://birdy.community/';
export const CTA = 'Identify birds by sound with the free Birdy app';
export const BASE_TAGS = ['#birds', '#birdwatching', '#birdsong', '#birding', '#birdy'];
export const YOUTUBE_TITLE_MAX = 100;

const DASHES = /[\u2013\u2014]/;

/** "song" for the songbird group, "voice" for every other group (a cormorant does not sing). */
export function voiceWord(record) {
  return record.group === 'songbirds' ? 'song' : 'voice';
}

/** The species page only when the record is published, otherwise the home page. */
export function linkFor(record) {
  return record.publish === true ? `${SITE}species/${record.slug.en}/` : SITE;
}

/** Text counts as approved when it passed the pipeline's checks: status ok and a verification stamp. */
export function hasApprovedText(record) {
  const lead = record?.text?.en?.lead;
  return record.status === 'ok' && !!record.verification && Array.isArray(lead) && !!lead[0]?.text?.trim();
}

export function firstSentence(text) {
  const t = String(text).trim().replace(/\s+/g, ' ');
  const m = t.match(/^(.+?[.!?])(?=\s+[A-Z\u00C0-\u00DE]|$)/);
  return m ? m[1] : t;
}

/** First sentence of the English lead, or a neutral line when there is no approved text. */
export function hook(record) {
  const neutral = `Listen to the ${voiceWord(record)} of the ${record.names.en}.`;
  if (!hasApprovedText(record)) return neutral;
  const sentence = firstSentence(record.text.en.lead[0].text);
  // The checked text should never contain a dash; if one slipped through, do not rewrite it, use the neutral line.
  return DASHES.test(sentence) ? neutral : sentence;
}

function cleanName(name) {
  return String(name ?? '').trim().replace(/[\u2013\u2014]/g, '-');
}

function creditPart(label, media, edit) {
  const author = cleanName(media.author);
  const parts = [author, media.license.trim(), 'via Wikimedia Commons'].filter(Boolean);
  if (edit) parts.push(edit);
  return `${label}: ${parts.join(', ')}`;
}

/** "Photo: A, CC BY 4.0, via Wikimedia Commons, cropped · Sound: B, CC0, via Wikimedia Commons[, trimmed]" */
export function creditLine(record, { trimmed = false } = {}) {
  return [creditPart('Photo', heroImage(record), 'cropped'), creditPart('Sound', record.audio, trimmed ? 'trimmed' : '')].join(' · ');
}

export function nameTag(record) {
  const words = record.names.en
    .normalize('NFKD')
    .replace(/[\u0300-\u036F]/g, '')
    .replace(/['\u2019]/g, '')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean);
  return `#${words.map((w) => w[0].toUpperCase() + w.slice(1)).join('')}`;
}

export function hashtags(record) {
  return [...BASE_TAGS, nameTag(record)];
}

export function youtubeTitle(record) {
  const full = `What does the ${record.names.en} sound like? #shorts`;
  if (full.length <= YOUTUBE_TITLE_MAX) return full;
  const short = `What does the ${record.names.en} sound like?`;
  if (short.length <= YOUTUBE_TITLE_MAX) return short;
  return `${record.names.en.slice(0, YOUTUBE_TITLE_MAX - 1).trimEnd()}\u2026`;
}

export function assertNoDashes(label, text) {
  if (DASHES.test(text)) throw new Error(`${label} contains a dash (house style): ${text}`);
}

/**
 * Builds all captions for one species. `trimmed` is true when the clip in the video is shorter
 * than the original recording (the pipeline trimmed it, or the renderer cut it at 30 s).
 */
export function buildCaptions(record, { trimmed = false } = {}) {
  const link = linkFor(record);
  const credit = creditLine(record, { trimmed });
  const tags = hashtags(record).join(' ');
  const first = hook(record);
  const ctaWithLink = record.publish === true ? `${CTA}. More about the ${record.names.en}: ${link}` : `${CTA}: ${link}`;

  // The licence line follows the credits on every platform; on Instagram it is the only URL.
  const credits = usesShareAlike(record) ? `${credit}\n${SHARE_ALIKE_LINE}` : credit;

  const facebook = [first, ctaWithLink, credits, tags].join('\n\n');
  const instagram = [first, `${CTA}. Link in bio.`, credits, tags].join('\n\n');
  const youtube = { title: youtubeTitle(record), description: [first, ctaWithLink, credits, tags].join('\n\n') };

  const out = { instagram, facebook, youtube, link, credit, videoLicence: usesShareAlike(record) ? VIDEO_SA_LICENCE : null, hashtags: hashtags(record) };
  assertNoDashes('instagram', instagram);
  assertNoDashes('facebook', facebook);
  assertNoDashes('youtube.title', youtube.title);
  assertNoDashes('youtube.description', youtube.description);
  return out;
}
