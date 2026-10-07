// Licence rule for "See the song" (decided 2026-10-06): putting sound on a picture is an
// adaptation. Strict mode (the default): BOTH the hero photo and the recording must be CC0,
// public domain or CC BY 2.0/3.0/4.0. Share-alike mode (--share-alike, Albin to decide)
// also admits CC BY-SA 2.0/3.0/4.0; the video is then itself licensed CC BY-SA 4.0.
// NC and ND are never allowed. CC BY and CC BY-SA need a named author.

const STRICT = [/^cc0(?: 1\.0)?$/i, /^public domain$/i, /^cc by [234]\.0$/i];
const SHARE_ALIKE = [/^cc by-sa [234]\.0$/i];

export const VIDEO_SA_LICENCE = 'CC BY-SA 4.0';
export const VIDEO_SA_LICENCE_URL = 'https://creativecommons.org/licenses/by-sa/4.0/';

function normalise(licence) {
  return String(licence ?? '').trim().replace(/\s+/g, ' ');
}

export function isShareAlike(licence) {
  return SHARE_ALIKE.some((re) => re.test(normalise(licence)));
}

export function licenceAllowed(licence, { shareAlike = false } = {}) {
  const l = normalise(licence);
  return STRICT.some((re) => re.test(l)) || (shareAlike && isShareAlike(l));
}

export function needsAuthor(licence) {
  return /^cc by/i.test(normalise(licence));
}

export function heroImage(record) {
  return (record.images ?? []).find((img) => img.role === 'hero') ?? null;
}

/** True when the photo or the recording is CC BY-SA, so the video must be shared under CC BY-SA 4.0. */
export function usesShareAlike(record) {
  return isShareAlike(heroImage(record)?.license) || isShareAlike(record.audio?.license);
}

function mediaReasons(media, kind, opts) {
  if (!media) return [`no ${kind}`];
  const reasons = [];
  if (!media.file) reasons.push(`${kind} has no file`);
  if (!licenceAllowed(media.license, opts)) {
    const allowed = opts.shareAlike ? 'CC0, public domain, CC BY or CC BY-SA' : 'CC0, public domain or CC BY';
    reasons.push(`${kind} licence "${media.license ?? ''}" is not ${allowed}`);
  } else if (needsAuthor(media.license) && !String(media.author ?? '').trim()) reasons.push(`${kind} is ${media.license} but has no author`);
  return reasons;
}

/** Returns { ok, reasons } for one species record. */
export function qualifies(record, { shareAlike = false } = {}) {
  const opts = { shareAlike };
  const reasons = [...mediaReasons(heroImage(record), 'photo', opts), ...mediaReasons(record.audio, 'recording', opts)];
  return { ok: reasons.length === 0, reasons };
}
