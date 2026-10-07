/** The bare address in a From/To value ("Anna <anna@example.se>" -> "anna@example.se"), lower case. */
export function addressOf(value) {
  const text = String(value ?? '');
  const inAngles = text.match(/<([^>]+)>/);
  return (inAngles ? inAngles[1] : text).trim().toLowerCase();
}
