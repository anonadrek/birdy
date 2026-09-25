import type { Locale } from './i18n';

// Links to AlbIT (the company behind Birdy) and its sister product LoopLead.
// Plain follow links with the brand as anchor text (agreed with albit.se).
export const albitProductHref = (locale: Locale): string =>
  locale === 'sv' ? 'https://www.albit.se/produkter/birdy/' : 'https://www.albit.se/en/products/birdy/';

export const LOOPLEAD_URL = 'https://looplead.se/';

/** Contact address on the site. A bridge until feedback@birdy.community exists (CLAUDE.md, follow-up #2). */
export const CONTACT_EMAIL = 'albin@abrahamssons.se';

/** AlbIT AB, the company behind Birdy, and its founder (schema.org publisher, author and creator). */
export const ALBIT_URL = 'https://www.albit.se/';
export const ALBIN_URL = 'https://www.albit.se/om-albin/';
