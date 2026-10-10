// Captions for Instagram, Facebook and YouTube, built by code only from the species record:
// no model calls, no new claims. House style: plain, short English and no dashes.
import { heroImage, licenceUrl, usesShareAlike, VIDEO_SA_LICENCE, VIDEO_SA_LICENCE_URL } from './licence.mjs';

/** Added after the credits when the video contains CC BY-SA material. */
export const SHARE_ALIKE_LINE = `Video licensed ${VIDEO_SA_LICENCE} (${VIDEO_SA_LICENCE_URL})`;

export const SITE = 'https://birdy.community/';
export const CTA = 'Identify birds by sound with the free Birdy app';
export const BASE_TAGS = ['#birds', '#birdwatching', '#birdsong', '#birding', '#birdy'];
/** The captions are English; these two keep Birdy findable for Swedish birders (Albin 2026-10-08). */
export const SWEDISH_TAGS = ['#fåglar', '#fågelskådning'];
export const YOUTUBE_TITLE_MAX = 100;

const DASHES = /[\u2013\u2014]/;

/** "song" for the songbird group (the passerines), "voice" for every other group (a cormorant does not sing). */
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
  const m = t.match(/^(.+?[.!?])(?=\s+[A-ZÀ-Þ]|$)/);
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

/**
 * A URL that survives being pasted into a caption: non-ASCII characters (and so any dash in a
 * file name) and parentheses percent-encoded, so the link is neither cut short by a ")" nor
 * caught by the house rule against dashes.
 */
export function linkSafe(url) {
  return new URL(url).href.replace(/\(/g, '%28').replace(/\)/g, '%29');
}

function creditPart(label, media, edit, withUrls) {
  const author = cleanName(media.author);
  const deed = withUrls ? licenceUrl(media) : null;
  const licence = deed ? `${media.license.trim()} (${deed})` : media.license.trim();
  const via = withUrls && media.sourceUrl ? `via Wikimedia Commons (${linkSafe(media.sourceUrl)})` : 'via Wikimedia Commons';
  return `${label}: ${[author, licence, via, edit].filter(Boolean).join(', ')}`;
}

/**
 * "Silhouette: A, CC0, via PhyloPic" or "Silhouette: B, CC BY 3.0, via PhyloPic, adapted". The
 * cover's flock-of-birds artwork recolours and resizes the PhyloPic silhouette, which is an
 * adaptation under a CC BY (or CC BY-SA) licence; CC0 and the public domain mark place no such
 * condition on a recolour, so they get no "adapted". PhyloPic has no licence deed URL to add.
 */
function silhouetteCreditPart(silhouette) {
  const author = cleanName(silhouette.author);
  const licence = String(silhouette.licence ?? '').trim();
  const adapted = /^cc by\b/i.test(licence) ? ', adapted' : '';
  return `Silhouette: ${[author, licence].filter(Boolean).join(', ')}, via PhyloPic${adapted}`;
}

/**
 * "Photo: A, CC BY 4.0, via Wikimedia Commons, cropped · Sound: B, CC0, via Wikimedia Commons, edited".
 * The sound is always edited (levelled and faded); "trimmed and edited" when the clip in the
 * video is shorter than the original recording. `withUrls` adds each licence's deed after its
 * name and the Commons file page after "via Wikimedia Commons" (CC BY and BY-SA 4.0 ask for a
 * link to the material itself, section 3(a)(1)(A)(v)). `silhouette` (cover/covers.json's
 * `silhouette` object for the species) adds the cover artwork's PhyloPic credit at the end, in
 * both the short and the `withUrls` form; omitted when there is no silhouette credit to give.
 */
export function creditLine(record, { trimmed = false, withUrls = false, silhouette = null } = {}) {
  const parts = [
    creditPart('Photo', heroImage(record), 'cropped', withUrls),
    creditPart('Sound', record.audio, trimmed ? 'trimmed and edited' : 'edited', withUrls),
  ];
  if (silhouette) parts.push(silhouetteCreditPart(silhouette));
  return parts.join(' · ');
}

export function nameTag(record) {
  const words = record.names.en
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/['’]/g, '')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean);
  return `#${words.map((w) => w[0].toUpperCase() + w.slice(1)).join('')}`;
}

export function hashtags(record) {
  return [...BASE_TAGS, nameTag(record), ...SWEDISH_TAGS];
}

/**
 * The bird's Swedish name in brackets after the first mention of its English name (Albin 2026-10-08: all
 * captions in English, with the Swedish name), "The Eurasian Magpie (Skata) is a crow". A sentence that does
 * not name the bird gets "Swedish name: Skata." after it. Unchanged when there is no Swedish name, or it is
 * the same as the English one.
 */
export function withSwedishName(text, record) {
  const en = String(record.names?.en ?? '').trim();
  const sv = String(record.names?.sv ?? '').trim();
  if (!sv || sv.toLowerCase() === en.toLowerCase()) return text;
  const at = en ? text.toLowerCase().indexOf(en.toLowerCase()) : -1;
  if (at < 0) return `${text} Swedish name: ${sv}.`;
  const end = at + en.length;
  return `${text.slice(0, end)} (${sv})${text.slice(end)}`;
}

// Albin 2026-10-10: a hook question first, then the English and the Swedish name, so the Short is
// both something to guess and something people find when they search for the bird in either language.
const YOUTUBE_HOOK = 'Would you recognise this bird by its sound?';

export function youtubeTitle(record) {
  const en = String(record.names?.en ?? '').trim();
  const sv = String(record.names?.sv ?? '').trim();
  const name = sv && sv.toLowerCase() !== en.toLowerCase() ? `${en} (${sv})` : en;
  const candidates = [
    `${YOUTUBE_HOOK} ${name} #shorts`,
    `${YOUTUBE_HOOK} ${name}`,
    `${YOUTUBE_HOOK} ${en}`,
    `${name} #shorts`,
    en,
  ];
  const fits = candidates.find((title) => title.length <= YOUTUBE_TITLE_MAX);
  return fits ?? `${en.slice(0, YOUTUBE_TITLE_MAX - 1).trimEnd()}…`;
}

export function assertNoDashes(label, text) {
  if (DASHES.test(text)) throw new Error(`${label} contains a dash (house style): ${text}`);
}

/**
 * Builds all captions for one species. `trimmed` is true when the clip in the video is shorter
 * than the original recording (the pipeline trimmed it, or the renderer cut it). `silhouette` is
 * the species' `cover/covers.json` silhouette object, when the video has the flock-cover title
 * card; it is credited at the end of the credit line, in every caption field that carries one.
 * Facebook and YouTube links are clickable, so their credits carry the licence URLs; Instagram
 * keeps the short credit (its only URL is the CC BY-SA line when the video needs one).
 */
export function buildCaptions(record, { trimmed = false, silhouette = null } = {}) {
  const link = linkFor(record);
  const credit = creditLine(record, { trimmed, silhouette });
  const creditWithUrls = creditLine(record, { trimmed, withUrls: true, silhouette });
  const sa = usesShareAlike(record);
  const tags = hashtags(record).join(' ');
  const first = withSwedishName(hook(record), record);
  const ctaWithLink = record.publish === true ? `${CTA}. More about the ${record.names.en}: ${link}` : `${CTA}: ${link}`;

  const credits = (c) => (sa ? `${c}\n${SHARE_ALIKE_LINE}` : c);

  const facebook = [first, ctaWithLink, credits(creditWithUrls), tags].join('\n\n');
  const instagram = [first, `${CTA}. Link in bio.`, credits(credit), tags].join('\n\n');
  const youtube = { title: youtubeTitle(record), description: [first, ctaWithLink, credits(creditWithUrls), tags].join('\n\n') };

  const out = { instagram, facebook, youtube, link, credit, creditWithUrls, videoLicence: sa ? VIDEO_SA_LICENCE : null, hashtags: hashtags(record) };
  assertNoDashes('instagram', instagram);
  assertNoDashes('facebook', facebook);
  assertNoDashes('youtube.title', youtube.title);
  assertNoDashes('youtube.description', youtube.description);
  return out;
}
