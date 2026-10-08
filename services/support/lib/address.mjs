/**
 * The bare address in a From/To value ("Anna <anna@example.se>" -> "anna@example.se"), lower case.
 * When there is more than one angle-bracket group (a quoted display name that itself contains one,
 * e.g. `"Anna <x>" <anna@ex.se>`), the LAST one is the real address.
 */
export function addressOf(value) {
  const text = String(value ?? '');
  const matches = [...text.matchAll(/<([^>]+)>/g)];
  const last = matches.at(-1);
  return (last ? last[1] : text).trim().toLowerCase();
}
