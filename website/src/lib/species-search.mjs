// Shared search normalisation for the species hub (spec 2026-09-25 §6). Used server-side for each
// species' indexed key (species.ts searchKey, over names.sv / names.en / names.scientific) and
// client-side for the query typed into the hub's search field (SpeciesHub.astro), so the two always
// agree. Plain JS with no Astro-specific imports: safe to import from a browser <script> module too.
//
// Diacritics are stripped (NFD, drop combining marks) and any run of non-letter/non-digit characters
// (hyphens, apostrophes, punctuation, extra whitespace) collapses to a single space, so "Black-headed
// Gull" and "black headed gull" normalise to the same string, and "blames" still matches "Blåmes".

/** @param {string} s */
export function normalizeSearch(s) {
  return s
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim();
}

/**
 * True when every word of a normalised query is found somewhere in a normalised key, in any order
 * ("tit great" matches "talgoxe great tit parus major"). An empty query matches everything.
 * @param {string} key already run through normalizeSearch
 * @param {string} query already run through normalizeSearch
 */
export function matchesSearch(key, query) {
  if (!query) return true;
  return query.split(' ').filter(Boolean).every((word) => key.includes(word));
}
