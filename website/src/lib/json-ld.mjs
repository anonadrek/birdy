// Serializes a JSON-LD value for embedding in `<script type="application/ld+json">` via `set:html`.
//
// Security: later tasks feed Wikimedia Commons author names (editable by anyone; the pipeline's
// HTML-unescape step can turn an entity into a literal `<`) into the page's JSON-LD graph on
// auto-published species/comparison pages. A plain `JSON.stringify()` would let a value like
// `</script><script>alert(1)</script>` close the ld+json script element early and have the browser
// parse the rest as a new, attacker-controlled <script> — a stored XSS on a page nobody reviews by
// hand before it goes live. Escaping `<` as its JSON unicode escape closes that hole: `<` is
// valid inside a JSON string, so `JSON.parse` on the output still returns the original value
// unchanged, but the literal byte sequence `</script` can never appear in the HTML.
export function serializeJsonLd(value) {
  return JSON.stringify(value).replace(/</g, '\\u003c');
}
