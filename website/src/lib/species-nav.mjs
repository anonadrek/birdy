// What the menu, the home page and the footer show of the species pages (Task 13), in plain JS so the states
// can be unit tested (tests/unit/species-nav.unit.mjs). Species are published one at a time (spec §14).

/**
 * Whether any species page is built: the one rule behind the menu's "Species" link, the home page's link under
 * the map and the footer's column (before the first page the hub only says the pages are on their way).
 * @param {readonly unknown[]} built the species that have a page in this build
 */
export function hasSpeciesPages(built) {
  return built.length > 0;
}

// The footer has three states: no species built (no column, no row), some built but none of the twelve common
// ones (the column with the groups, no "Common species" row), and at least one of the twelve (both).

/**
 * @template {{ qid: string }} T
 * @param {T[]} built the species that have a page in this build
 * @param {readonly string[]} commonQids the twelve common species, in the footer's order
 * @returns {{ column: boolean, common: T[] }}
 */
export function footerSpecies(built, commonQids) {
  return {
    column: hasSpeciesPages(built),
    common: commonQids.flatMap((qid) => built.filter((s) => s.qid === qid)),
  };
}
