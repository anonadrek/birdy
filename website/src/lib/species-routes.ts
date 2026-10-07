import type { Locale } from './i18n';
import { ABOUT_SLUG, GROUPS, activeGroups, assertUniqueSlugs, getAllRecords, getAllSpecies } from './species';
import { hasPageContract } from './species-source.mjs';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  // Checked against all 15 GROUPS, not just the active ones (controller review, Task 8 fix wave): a slug
  // collision must fail the very first build, not wait for the publish that happens to activate the
  // colliding group, by which point the build has looked clean for however long the group sat empty.
  // Every species that has the page contract, built in this build or not (controller review, Task 10): the
  // same reasoning, a collision with a verified but unpublished species must not wait for its publication.
  const contract = (await getAllRecords()).filter((r) => hasPageContract(r));
  assertUniqueSlugs([...contract.map((s) => s.slug[locale]), ...GROUPS.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  const groups = activeGroups(all);
  return [
    ...all.map((species) => ({ params: { slug: species.slug[locale] }, props: { species } })),
    ...groups.map((group) => ({ params: { slug: group.slug[locale] }, props: { group } })),
  ];
}
