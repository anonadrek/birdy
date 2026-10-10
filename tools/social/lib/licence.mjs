// Licence rule for "See the song" (Albin, 2026-10-07): putting sound on a picture is an
// adaptation. The hero photo and the recording must each be CC0, public domain, CC BY or
// CC BY-SA (2.0, 3.0 or 4.0). A video that uses any CC BY-SA material is itself published under
// CC BY-SA 4.0 and the caption says so. NC and ND are never allowed. CC BY and CC BY-SA need a
// named author.

const ALLOWED = [/^cc0(?: 1\.0)?$/i, /^public domain$/i, /^cc by [234]\.0$/i, /^cc by-sa [234]\.0$/i];
const SHARE_ALIKE = /^cc by-sa [234]\.0$/i;

export const VIDEO_SA_LICENCE = 'CC BY-SA 4.0';
export const VIDEO_SA_LICENCE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/';
export const CC0_URL = 'https://creativecommons.org/publicdomain/zero/1.0/';

function normalise(licence) {
  return String(licence ?? '').trim().replace(/\s+/g, ' ');
}

export function isShareAlike(licence) {
  return SHARE_ALIKE.test(normalise(licence));
}

export function licenceAllowed(licence) {
  const l = normalise(licence);
  return ALLOWED.some((re) => re.test(l));
}

export function needsAuthor(licence) {
  return /^cc by/i.test(normalise(licence));
}

/**
 * The licence deed for a photo or recording: the record's own licenseUrl, else the deed for
 * CC0 or a CC BY / CC BY-SA version. Public domain has no deed and gives null.
 */
export function licenceUrl(media) {
  if (media?.licenseUrl) return media.licenseUrl;
  const l = normalise(media?.license);
  if (/^cc0/i.test(l)) return CC0_URL;
  const m = l.match(/^cc (by(?:-sa)?) ([234]\.0)$/i);
  return m ? `https://creativecommons.org/licenses/${m[1].toLowerCase()}/${m[2]}/` : null;
}

export function heroImage(record) {
  return (record.images ?? []).find((img) => img.role === 'hero') ?? null;
}

/** True when the photo or the recording is CC BY-SA, so the video must be shared under CC BY-SA 4.0. */
export function usesShareAlike(record) {
  return isShareAlike(heroImage(record)?.license) || isShareAlike(record.audio?.license);
}

function mediaReasons(media, kind) {
  if (!media) return [`no ${kind}`];
  const reasons = [];
  if (!media.file) reasons.push(`${kind} has no file`);
  if (!licenceAllowed(media.license)) reasons.push(`${kind} licence "${media.license ?? ''}" is not CC0, public domain, CC BY or CC BY-SA`);
  else if (needsAuthor(media.license) && !String(media.author ?? '').trim()) reasons.push(`${kind} is ${media.license} but has no author`);
  return reasons;
}

/** Returns { ok, reasons } for one species record. */
export function qualifies(record) {
  const reasons = [...mediaReasons(heroImage(record), 'photo'), ...mediaReasons(record.audio, 'recording')];
  return { ok: reasons.length === 0, reasons };
}
