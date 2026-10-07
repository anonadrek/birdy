import type { Locale } from './i18n';
import { ABOUT_SLUG, activeGroups, assertUniqueSlugs, getAllSpecies } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  const groups = activeGroups(all);
  assertUniqueSlugs([...groups.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  return groups.map((group) => ({ params: { slug: group.slug[locale] }, props: { group } }));
}
