import type { Locale } from './i18n';

// Links to AlbIT, the company behind Birdy.
// Plain follow links with the brand as anchor text (agreed with albit.se).
export const albitProductHref = (locale: Locale): string =>
  locale === 'sv' ? 'https://www.albit.se/produkter/birdy/' : 'https://www.albit.se/en/products/birdy/';

/** The Premium page (plan 2026-10-08 Task 6). */
export const premiumHref = (locale: Locale): string => (locale === 'sv' ? '/sv/premium/' : '/premium/');

/** Contact address on the site. A bridge until feedback@birdy.community exists (CLAUDE.md, follow-up #2). */
export const CONTACT_EMAIL = 'albin@abrahamssons.se';

/** AlbIT AB, the company behind Birdy, and its founder (schema.org publisher, author and creator). */
export const ALBIT_URL = 'https://www.albit.se/';
export const ALBIN_URL = 'https://www.albit.se/om-albin/';

/**
 * Birdy's own channels (2026-10-08), in the order the footer shows them. Used by the footer's follow row and as
 * `sameAs` on the app's JSON-LD (Layout.astro). Instagram is @app.birdy: @birdy.community on Instagram belongs to
 * an unrelated crypto project, so never link that one. The Facebook page has no username yet, so it is the number link.
 */
export const SOCIAL_PROFILES = [
  { network: 'Instagram', href: 'https://www.instagram.com/app.birdy/' },
  { network: 'Facebook', href: 'https://www.facebook.com/profile.php?id=61595339305266' },
  { network: 'YouTube', href: 'https://www.youtube.com/@birdy.community' },
  { network: 'TikTok', href: 'https://www.tiktok.com/@birdy.app' },
] as const;

export type SocialNetwork = (typeof SOCIAL_PROFILES)[number]['network'];
