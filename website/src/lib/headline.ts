export type Token =
  | { kind: 'plain'; text: string }
  | { kind: 'accent'; text: string };

/**
 * Parse "A *word* and more" into tokens.
 * - `*word*` becomes an accent token (Caveat-italic, rotated)
 * - Plain text becomes plain tokens
 * - Asymmetric asterisks are kept as literal text
 *
 * Mirrors the Kotlin JournalHeadline parser in
 * composeApp/.../ui/components/JournalHeadline.kt
 */
export function parseHeadline(input: string): Token[] {
  const tokens: Token[] = [];
  const re = /\*([^*]+)\*/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(input)) !== null) {
    if (match.index > lastIndex) {
      tokens.push({ kind: 'plain', text: input.slice(lastIndex, match.index) });
    }
    tokens.push({ kind: 'accent', text: match[1] });
    lastIndex = re.lastIndex;
  }
  if (lastIndex < input.length) {
    tokens.push({ kind: 'plain', text: input.slice(lastIndex) });
  }
  if (tokens.length === 0) {
    tokens.push({ kind: 'plain', text: input });
  }
  return tokens;
}
