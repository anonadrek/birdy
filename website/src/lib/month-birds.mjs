// "Fåglarna i oktober" on the home page (plan 2026-10-08 Task 3): the species with a page that are reported more this
// month than over the year, from the species pages' own Artportalen data (data.months, the species' share of all bird
// reports per month, top month 100). Plain JS so node --test can check the pick.

/**
 * How much more the species is reported in `monthIndex` (0 = January) than in an average month: the month's value
 * divided by the mean of all twelve. Undefined without twelve months of data or when every month is 0.
 * @param {number[] | undefined} months
 * @param {number} monthIndex
 */
export function monthRatio(months, monthIndex) {
  if (!Array.isArray(months) || months.length !== 12) return undefined;
  const mean = months.reduce((sum, v) => sum + v, 0) / 12;
  if (mean <= 0) return undefined;
  return months[monthIndex] / mean;
}

/**
 * Up to `n` species reported more in `monthIndex` than the yearly average, highest ratio first, ties by QID. Only the
 * species passed in are considered: the caller passes the species that have a page and a photo the page may crop.
 * @template {{ qid: string, months?: number[] }} T
 * @param {T[]} species
 * @param {number} monthIndex 0 = January
 * @param {number} [n]
 * @returns {T[]}
 */
export function monthBirds(species, monthIndex, n = 4) {
  return species
    .map((s) => ({ s, ratio: monthRatio(s.months, monthIndex) }))
    .filter((x) => x.ratio !== undefined && x.ratio > 1)
    .sort((a, b) => (b.ratio - a.ratio) || (a.s.qid < b.s.qid ? -1 : a.s.qid > b.s.qid ? 1 : 0))
    .slice(0, n)
    .map((x) => x.s);
}
