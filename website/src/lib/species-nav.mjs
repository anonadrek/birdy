// What the footer shows of the species pages (Task 13), in plain JS so the three states can be unit tested
// (tests/unit/species-nav.unit.mjs). Species are published one at a time (spec §14), so there are three:
// no species built (no column, no row), some built but none of the twelve common ones (the column with the
// groups, no "Common species" row), and at least one of the twelve (both).

/**
 * @template {{ qid: string }} T
 * @param {T[]} built the species that have a page in this build
 * @param {readonly string[]} commonQids the twelve common species, in the footer's order
 * @returns {{ column: boolean, common: T[] }}
 */
export function footerSpecies(built, commonQids) {
  return {
    column: built.length > 0,
    common: commonQids.flatMap((qid) => built.filter((s) => s.qid === qid)),
  };
}
