import { getCollection, type CollectionEntry } from 'astro:content';
import type { ImageMetadata } from 'astro';
import type { z } from 'astro/zod';
import groupData from '../data/species-groups.json';
import type { Copy, Locale } from './i18n';
import { audioPublicPath, hasPageContract, isComparisonBuilt, isPreview, isSpeciesBuilt, useFixtures } from './species-source.mjs';
import type { speciesPage } from './species-schema.mjs';
import speciesImages from 'virtual:birdy-species-images';

/** Any species file: the full page data for written and verified records, only the envelope for the rest. */
export type SpeciesRecord = CollectionEntry<'species'>['data'];
/** A species the site may build (status ok and verified), held to the full page contract. */
export type Species = z.output<typeof speciesPage>;
export type SpeciesImage = Species['images'][number];
export type SpeciesText = NonNullable<Species['text']>['sv'];
export type Sentence = SpeciesText['lead'][number];
export type LookAlike = SpeciesText['lookAlikes'][number];
export type Comparison = CollectionEntry<'comparisons'>['data'];
export type WikiLang = 'sv' | 'en' | 'de';
export interface WikiRef { title: string; revision: string }

export interface Group {
  key: string;
  slug: Record<Locale, string>;
  name: Record<Locale, string>;
  photo: string;
  intro: Record<Locale, string>;
}

/** The app's 15 groups in the app's order (src/data/species-groups.json, shared with the pipeline). */
export const GROUPS: Group[] = groupData.groups;
/** Groups with fewer built species get noindex (spec §2). Same rule in src/lib/species-sitemap.mjs. */
export const MIN_GROUP_SIZE = 3;
export const SITE = 'https://birdy.community';
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=se.birdy.android';
export const ABOUT_SLUG: Record<Locale, string> = { sv: 'om-artsidorna', en: 'about-these-pages' };

// Photos: src/assets/species/<QID>/*.webp, or the test images under tests/fixtures/ (SPECIES_FIXTURES=1),
// keyed by the record's `file` ("Q25485/hero.webp"). The virtual module comes from astro.config.mjs and
// holds only the photos of the species built in this build, so no other photo ends up in dist (an
// import.meta.glob would emit every one of them, unpublished species and test photos included).
const images: ReadonlyMap<string, ImageMetadata> = speciesImages;

export function speciesImage(file: string): ImageMetadata {
  const hit = images.get(file);
  if (!hit) {
    const dir = useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species';
    throw new Error(`Artbilden saknas: ${file} (${dir}; bara arter som får en sida i det här bygget har foton)`);
  }
  return hit;
}

export function heroOf(s: Species): SpeciesImage {
  const hero = s.images.find((i) => i.role === 'hero');
  if (!hero) throw new Error(`${s.qid} saknar huvudfoto`);
  return hero;
}

/** Where the species' recording is served (astro.config.mjs copies it there), or undefined. */
export function audioHref(s: Species): string | undefined {
  return s.audio ? audioPublicPath(process.cwd(), s) : undefined;
}

let records: SpeciesRecord[] | undefined;
/** Every species file, whatever its status. Used for names (look-alikes), never for pages. */
export async function getAllRecords(): Promise<SpeciesRecord[]> {
  records ??= (await getCollection('species')).map((e) => e.data);
  return records;
}

let built: Species[] | undefined;
/** The species that get a page in this build (spec §14): written, reviewed, and published or previewed. */
export async function getAllSpecies(): Promise<Species[]> {
  // species-schema.mjs holds exactly the records with hasPageContract to the page contract, so these have every page field.
  built ??= (await getAllRecords()).filter((s): s is Species => hasPageContract(s) && isSpeciesBuilt(s));
  return built;
}

let builtComparisons: Comparison[] | undefined;
/** The comparisons that get a page: written, published or previewed, and both species built. */
export async function getComparisons(): Promise<Comparison[]> {
  if (!builtComparisons) {
    const qids = new Set((await getAllSpecies()).map((s) => s.qid));
    builtComparisons = (await getCollection('comparisons')).map((e) => e.data).filter((c) => isComparisonBuilt(c, qids));
  }
  return builtComparisons;
}

/** Built only because this is a preview build: noindex and the preview banner. */
export const isUnpublished = (item: { publish: boolean }): boolean => isPreview() && !item.publish;

export const hubHref = (locale: Locale): string => (locale === 'sv' ? '/sv/arter/' : '/species/');
export const speciesHref = (s: Species, locale: Locale): string => `${hubHref(locale)}${s.slug[locale]}/`;
export const groupHref = (g: Group, locale: Locale): string => `${hubHref(locale)}${g.slug[locale]}/`;
export const comparisonHref = (c: Comparison, locale: Locale): string => `${hubHref(locale)}${c.slug[locale]}/`;
export const aboutHref = (locale: Locale): string => `${hubHref(locale)}${ABOUT_SLUG[locale]}/`;

export function groupByKey(key: string): Group {
  const group = GROUPS.find((g) => g.key === key);
  if (!group) throw new Error(`Okänd grupp: ${key}`);
  return group;
}

export function sortByName(list: Species[], locale: Locale): Species[] {
  return [...list].sort((a, b) => a.names[locale].localeCompare(b.names[locale], locale));
}

export function groupSizes(list: Species[]): Map<string, number> {
  const sizes = new Map<string, number>();
  for (const s of list) sizes.set(s.group, (sizes.get(s.group) ?? 0) + 1);
  return sizes;
}

/** Groups with at least one page in this build, in the app's order. Empty groups get no page, chip or card. */
export function activeGroups(list: Species[]): Group[] {
  const sizes = groupSizes(list);
  return GROUPS.filter((g) => (sizes.get(g.key) ?? 0) > 0);
}

export function isGroupIndexed(group: Group, list: Species[]): boolean {
  return (groupSizes(list).get(group.key) ?? 0) >= MIN_GROUP_SIZE;
}

/** The `n` groups with the most built species, ties in the app's order (footer column). */
export function largestGroups(list: Species[], n: number): Group[] {
  const sizes = groupSizes(list);
  return activeGroups(list).sort((a, b) => (sizes.get(b.key) ?? 0) - (sizes.get(a.key) ?? 0)).slice(0, n);
}

/**
 * The footer's "Common species" row: those of the twelve that have a page in this build, in the list's
 * order. Species are published one at a time (spec §14), so the list is often incomplete and empty
 * before the first publication; the footer hides the row when it is empty (Task 13).
 */
export function commonSpecies(list: Species[]): Species[] {
  return groupData.common.flatMap((qid) => list.filter((s) => s.qid === qid));
}

/** The group's fixed photo species, or its first built species in Swedish alphabetical order (spec §6). */
export function groupPhoto(group: Group, list: Species[]): ImageMetadata | undefined {
  const members = sortByName(list.filter((s) => s.group === group.key), 'sv');
  const pick = members.find((s) => s.qid === group.photo) ?? members[0];
  return pick ? speciesImage(heroOf(pick).file) : undefined;
}

/** Up to four other built species in the same family, or in the same group when the family is too small. */
export function related(s: Species, list: Species[], locale: Locale): { kind: 'family' | 'group'; items: Species[] } {
  const others = list.filter((x) => x.qid !== s.qid);
  const family = sortByName(others.filter((x) => x.family.latin === s.family.latin), locale);
  if (family.length >= 2) return { kind: 'family', items: family.slice(0, 4) };
  return { kind: 'group', items: sortByName(others.filter((x) => x.group === s.group), locale).slice(0, 4) };
}

export function countLabel(n: number, t: Copy): string {
  return n === 1 ? t.species.countOne : t.species.countMany.replace('{n}', String(n));
}

const fits = (title: string): boolean => title.length <= 60;

export function speciesTitle(s: Species, locale: Locale, t: Copy): string {
  const long = t.species.titleSpecies.replace('{name}', s.names[locale]);
  return fits(long) ? long : t.species.titleSpeciesShort.replace('{name}', s.names[locale]);
}

export function groupTitle(g: Group, n: number, locale: Locale, t: Copy): string {
  const long = t.species.titleGroup.replace('{group}', g.name[locale]).replace('{count}', countLabel(n, t));
  return fits(long) ? long : t.species.titleGroupShort.replace('{group}', g.name[locale]);
}

export interface PairSide { side: 'a' | 'b'; species: Species }

/** The comparison's two species in this language's slug order (spec §4), each with its side in the record. */
export function comparisonPair(c: Comparison, locale: Locale, list: Species[]): [PairSide, PairSide] {
  const find = (qid: string): Species => {
    const hit = list.find((s) => s.qid === qid);
    if (!hit) throw new Error(`Jämförelsen ${c.slug.sv} saknar arten ${qid}`);
    return hit;
  };
  const a: PairSide = { side: 'a', species: find(c.a) };
  const b: PairSide = { side: 'b', species: find(c.b) };
  return a.species.slug[locale] <= b.species.slug[locale] ? [a, b] : [b, a];
}

/** "Blåmes" and "talgoxe": in Swedish the second name is in lower case, as in running text. */
export function pairNames(pair: [PairSide, PairSide], locale: Locale): [string, string] {
  const first = pair[0].species.names[locale];
  const second = pair[1].species.names[locale];
  return [first, locale === 'sv' ? second.toLocaleLowerCase('sv') : second];
}

export function comparisonTitle(names: [string, string], t: Copy): string {
  const fill = (tpl: string) => tpl.replace('{a}', names[0]).replace('{b}', names[1]);
  const long = fill(t.species.titleCompare);
  return fits(long) ? long : fill(t.species.titleCompareShort);
}

export function joinSentences(list?: Sentence[]): string {
  return (list ?? []).map((x) => x.text).join(' ');
}

export interface LookAlikeView {
  name: string;
  /** No species file: the name is the scientific one and is shown in italics. */
  scientificOnly: boolean;
  /** The other species when it has a page in this build. */
  species?: Species;
  text: string;
  comparison?: Comparison;
}

/** What the "Can be confused with" section shows for one look-alike (spec §5, deviation 7). */
export function lookAlikeView(s: Species, item: LookAlike, locale: Locale, builtList: Species[], records: SpeciesRecord[], comps: Comparison[]): LookAlikeView | undefined {
  const isQid = /^Q\d+$/.test(item.other);
  const record = isQid ? records.find((x) => x.qid === item.other) : undefined;
  const page = isQid ? builtList.find((x) => x.qid === item.other) : undefined;
  const fact = s.facts.find((f) => f.topic === 'lookalike' && (f.other?.qid === item.other || f.other?.scientific === item.other));
  const name = record ? record.names[locale] : (fact?.other?.scientific ?? (isQid ? undefined : item.other));
  if (!name) return undefined;
  const comparison = page
    ? comps.find((c) => (c.a === s.qid && c.b === page.qid) || (c.b === s.qid && c.a === page.qid))
    : undefined;
  return { name, scientificOnly: !record, species: page, text: joinSentences(item.text), comparison };
}

/** The date the species' facts were verified, YYYY-MM-DD (spec Revision 2026-10-05). Built species always have one. */
export const reviewDate = (s: Species): string => (s.verification?.at ?? '').slice(0, 10);
export const laterDate = (a: string, b: string): string => (a > b ? a : b);

export function formatDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale === 'sv' ? 'sv-SE' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${iso.slice(0, 10)}T00:00:00Z`));
}

/** The app box says only what the app can do for this species (spec §5); a group or comparison gets the general line. */
export function appText(s: Species | undefined, t: Copy): string {
  const id = s?.identifiable;
  if (id?.photo && id.sound) return t.species.appTextBoth;
  if (id?.sound) return t.species.appTextSound;
  if (id?.photo) return t.species.appTextPhoto;
  return t.species.appTextNone;
}

/** Play link with UTM tags, readable in Play Console's acquisition report (spec §15). */
export function playHref(campaign: string, medium: 'species' | 'group' | 'hub' | 'compare'): string {
  const referrer = `utm_source=birdy.community&utm_medium=${medium}&utm_campaign=${campaign}`;
  return `${PLAY_URL}&referrer=${encodeURIComponent(referrer)}`;
}

/** Lowercase, accents removed: "Gök" and "gok" both match. The page script normalises the same way. */
export function searchKey(s: Species): string {
  return [s.names.sv, s.names.en, s.names.scientific].join(' ').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export function wikiUrl(lang: WikiLang, ref: WikiRef): string {
  const title = encodeURIComponent(ref.title.replace(/ /g, '_'));
  return `https://${lang}.wikipedia.org/w/index.php?title=${title}&oldid=${ref.revision}`;
}

/** The Wikipedia articles a species' text is based on, in the order Swedish, English, German. */
export function wikiSources(s: Species): { lang: WikiLang; ref: WikiRef }[] {
  return (['sv', 'en', 'de'] as const).flatMap((lang) => {
    const ref = s.wikipedia[lang];
    return ref ? [{ lang, ref }] : [];
  });
}

export interface Crumb { name: string; href: string }

export function breadcrumbJsonLd(crumbs: Crumb[]): Record<string, unknown> {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map((c, i) => ({ '@type': 'ListItem', position: i + 1, name: c.name, item: new URL(c.href, SITE).toString() })),
  };
}

export function itemListJsonLd(pathname: string, name: string, locale: Locale, items: Species[]): Record<string, unknown> {
  return {
    '@type': 'CollectionPage',
    url: new URL(pathname, SITE).toString(),
    name,
    inLanguage: locale,
    mainEntity: {
      '@type': 'ItemList',
      numberOfItems: items.length,
      itemListElement: items.map((s, i) => ({
        '@type': 'ListItem', position: i + 1, name: s.names[locale], url: new URL(speciesHref(s, locale), SITE).toString(),
      })),
    },
  };
}

export function taxonJsonLd(s: Species): Record<string, unknown> {
  return {
    '@type': 'Taxon',
    name: s.names.scientific,
    alternateName: [s.names.sv, s.names.en],
    taxonRank: 'species',
    sameAs: `https://www.wikidata.org/wiki/${s.qid}`,
  };
}

export function audioJsonLd(s: Species): Record<string, unknown> | undefined {
  const href = audioHref(s);
  if (!s.audio || !href) return undefined;
  return {
    '@type': 'AudioObject',
    contentUrl: new URL(href, SITE).toString(),
    encodingFormat: 'audio/mpeg',
    ...(s.audio.licenseUrl ? { license: s.audio.licenseUrl } : {}),
    acquireLicensePage: s.audio.sourceUrl,
    ...(s.audio.author ? { creator: { '@type': 'Person', name: s.audio.author }, creditText: s.audio.author } : {}),
  };
}

/** Throws when two pages in one language would get the same address (spec §4). */
export function assertUniqueSlugs(slugs: string[], locale: Locale): void {
  const seen = new Set<string>();
  for (const slug of slugs) {
    if (seen.has(slug)) throw new Error(`Samma adress två gånger (${locale}): ${slug}`);
    seen.add(slug);
  }
}
