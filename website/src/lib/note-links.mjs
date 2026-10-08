// Links from a field note to the species pages (plain JS, so it can be unit tested: tests/unit/note-links.unit.mjs).
//
// A field note is Markdown and links to a species page by its address. Species are published one at a time, and a
// build can have none of them (npm run build:empty) or only the test data's (npm run build:fixtures), so a link to a
// species page that this build doesn't have would be dead (scripts/check-seo.mjs fails the build on it). The note's
// HTML goes through unwrapUnbuiltSpeciesLinks before it is shown (components/ui/BuiltSpeciesLinks.astro): such a
// link becomes its plain text, every other link stays as it is.

const SPECIES_PATH = /^\/(?:sv\/arter|species)\//;

/**
 * @param {string} html the rendered note
 * @param {ReadonlySet<string>} built every page this build has under /species/ and /sv/arter/, with a trailing slash
 * @returns {string}
 */
export function unwrapUnbuiltSpeciesLinks(html, built) {
  return html.replace(/<a\b([^>]*)>([\s\S]*?)<\/a>/g, (whole, attrs, inner) => {
    const href = attrs.match(/\shref="([^"]*)"/)?.[1];
    if (!href) return whole;
    const path = href.split(/[?#]/)[0];
    if (!SPECIES_PATH.test(path)) return whole;
    return built.has(path.endsWith('/') ? path : `${path}/`) ? whole : inner;
  });
}
