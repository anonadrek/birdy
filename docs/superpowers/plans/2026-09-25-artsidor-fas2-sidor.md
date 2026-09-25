# Artsidor fas 2: sidorna på birdy.community – implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingångssida, 15 gruppsidor och en artsida per granskad art (svenska och engelska) på birdy.community, byggda ur fas 1:s datafiler. Dessutom ny meny- och sidfotsnavigering och SEO-reglerna som ett skript som stoppar bygget vid fel.

**Architecture:** En innehållssamling `species` (Astro content collection) läser `website/src/data/species/*.json`, och `src/lib/species.ts` samlar all logik (adresser, grupper, titlar, relaterade arter, bilder). Sidorna är komponenter under `src/components/species/`, och en dynamisk route per språk renderar artsidor och gruppsidor. Artsidan har layout B: ett CSS-rutnät på dator med sticky vänsterspalt, och i mobilen `display: contents` med `order`, så att ordningen blir den som specen kräver. `scripts/check-seo.mjs` kontrollerar den byggda sajten.

**Tech Stack:** Astro 5 (content layer, `astro:assets`), TypeScript, `@astrojs/sitemap`, Playwright, Node-skript utan nya beroenden.

**Spec:** `docs/superpowers/specs/2026-09-25-artsidor-design.md` (avsnitt 4 till 6 och 9 till 12, bilaga A). Mockup: `docs/superpowers/specs/assets/2026-09-25-artsidor/helheten.html`. Fas 1: `docs/superpowers/plans/2026-09-25-artsidor-fas1-pipeline.md`.

---

## Avvikelser från specen (medvetna, små)

1. **Skriptet heter `npm run verify`**, eftersom `npm run check` redan är `astro check`. Det kör build, `check-seo`, `test:i18n`, `test:no-dashes` och `test:contrast`.
2. **Appruta och textcredit nämner inte artens namn.** "Birdy känner igen talgoxe på foto" blir fel böjning på svenska och datan har inte bestämd form. Ny text: "Birdy känner igen arten på foto eller läte, direkt i telefonen och utan täckning." Textcrediten blir: "Texten bygger på Wikipedia och får delas under CC BY-SA 4.0. Källor: svenska artikeln, engelska artikeln", med länkar till respektive revision.
3. **Sökfältet i kategoriraden skickar `q` till ingångssidan**, som gör filtreringen. I mobilen visas bara ikonen, och ett tryck öppnar ingångssidan med sökfältet i fokus.
4. **Artkorten har tom alt-text.** Namnet står som text i samma länk, så en alt-text hade upprepat det för skärmläsare. Artsidans foton har full alt-text.

## Förutsättningar

- **Fas 1 är klar:** `website/src/data/species/*.json` och `website/src/assets/species/` finns på `main`, och talgoxen (Q25485) har `status: "ok"`. Testerna i den här planen använder talgoxen, blåmesen och gruppen Ugglor.
- **1.3-webben är live** (sedan 2026-09-25). Planen bygger på den koden.
- **Gren:** från `main`, i en egen worktree med kort sökväg (långa sökvägar failar på Windows):
  ```bash
  git worktree add C:/w/birdy-artsidor -b website/artsidor
  cd C:/w/birdy-artsidor/website && npm ci
  ```
  Alla kommandon nedan körs i `C:/w/birdy-artsidor/website`.
- **Testkommando:** `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test <fil>` (egen port så att en annan dev-server inte krockar, som i 1.3-arbetet).

## Filstruktur

**Skapas:**

| Fil | Ansvar |
|---|---|
| `src/lib/species.ts` | Typer, laddning, adresser, grupper, titlar, relaterade arter, bilder, UTM, JSON-LD för brödsmulor. |
| `src/lib/species-sitemap.mjs` | Samma data i ren JS för `astro.config.mjs`: `lastmod` och vilka gruppsidor som har `noindex`. |
| `src/styles/species.css` | Delade stilar för brödsmulor, rubriker, kortrutnät och approta. |
| `src/components/species/CategoryBar.astro` | Kategoriraden med chips och sökfält. |
| `src/components/species/SpeciesCard.astro` | Artkort (foto, namn, vetenskapligt namn). |
| `src/components/species/SpeciesHub.astro` | Ingångssidan. |
| `src/components/species/GroupPage.astro` | Gruppsidan. |
| `src/components/species/SpeciesArticle.astro` | Artsidan (layout B). |
| `src/components/species/Credits.astro` | Foto- och textcredits samt raden "Hittade du ett fel?". |
| `src/pages/species/index.astro`, `src/pages/species/[slug].astro` | Engelska routes. |
| `src/pages/sv/arter/index.astro`, `src/pages/sv/arter/[slug].astro` | Svenska routes. |
| `scripts/check-seo.mjs` | SEO-reglerna på `dist/`. |
| `tests/species.spec.ts` | Playwright för ingång, grupp, art, meny och sidfot. |

**Ändras:** `src/content.config.ts`, `src/layouts/Layout.astro`, `src/components/Nav.astro`, `src/components/Footer.astro`, `src/components/Guide.astro`, `src/components/ui/Icon.astro`, `src/content/copy.{en,sv}.json`, `astro.config.mjs`, `scripts/check-no-dashes.mjs`, `package.json`, `tests/home.spec.ts`.

---

### Task 1: Layout får språkpar, noindex och extra JSON-LD

**Files:**
- Modify: `website/src/layouts/Layout.astro`

- [ ] **Step 1: Utöka `Props`**

I `src/layouts/Layout.astro`, ersätt `interface Props { ... }` och raden `const { locale, pathname, ... } = Astro.props;` med:

```ts
interface Props {
  locale: Locale;
  pathname: string;
  noAlternateLocale?: boolean;
  /** The other language's path when the two languages use different slugs (species pages). */
  alternatePath?: string;
  /** Adds `<meta name="robots" content="noindex, follow">` (small group pages, spec §4). */
  noindex?: boolean;
  /** Extra schema.org nodes appended to the page's @graph. */
  jsonLd?: Record<string, unknown>[];
  title?: string;
  description?: string;
  articleDate?: string;
  /** Site-relative or absolute URL of a 1200×630 share image. Defaults to the locale's og-field image. */
  ogImage?: string;
  ogImageAlt?: string;
}

const {
  locale, pathname, noAlternateLocale = false, alternatePath, noindex = false, jsonLd: extraJsonLd = [],
  title, description, articleDate, ogImage, ogImageAlt,
} = Astro.props;
```

- [ ] **Step 2: Använd dem**

Ersätt `const altPath = alternateHref(locale, pathname);` med:

```ts
const altPath = alternatePath ?? alternateHref(locale, pathname);
```

Lägg till `...extraJsonLd,` som sista element i `'@graph': [ ... ]` (efter FAQPage-grenen), så att det står:

```ts
    }] : []),
    ...extraJsonLd,
  ],
};
```

Lägg till direkt efter `<meta name="description" content={metaDescription} />`:

```astro
    {noindex && <meta name="robots" content="noindex, follow" />}
```

- [ ] **Step 3: Kontrollera att inget befintligt ändrats**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/smoke.spec.ts tests/home.spec.ts`
Expected: samma resultat som på `main` (alla gröna). Ingen sida skickar de nya fälten än.

- [ ] **Step 4: Commit**

```bash
git add src/layouts/Layout.astro
git commit -m "feat(website): Layout tar språkpar, noindex och extra JSON-LD"
```

---

### Task 2: Innehållssamlingen och `lib/species.ts`

**Files:**
- Modify: `website/src/content.config.ts`
- Create: `website/src/lib/species.ts`

- [ ] **Step 1: Lägg till samlingen**

I `src/content.config.ts`, lägg till före `export const collections`:

```ts
const localized = z.object({ sv: z.string().min(1), en: z.string().min(1) });
const statuses = ['resident', 'breeding_migrant', 'passage', 'winter_visitor', 'rare_visitor', 'absent'] as const;
const langText = z.object({
  lead: z.string().min(1),
  fieldMarks: z.array(z.string().min(1)).min(3).max(4),
  voice: z.string().min(1),
  whereWhen: z.string().min(1),
  metaDescription: z.string().min(120).max(155),
  facts: z.object({
    size: z.object({ value: z.string().min(1), quote: z.string() }).nullable(),
    swedenStatus: z.object({ value: z.enum(statuses), quote: z.string() }).nullable(),
  }),
});
const wikiRef = z.object({ title: z.string(), revision: z.string() }).nullable();

// One file per species, written by `birdy-fetcher web` (spec 2026-09-25 appendix C).
const species = defineCollection({
  loader: glob({ pattern: '*.json', base: './src/data/species' }),
  schema: z
    .object({
      qid: z.string().regex(/^Q\d+$/),
      status: z.enum(['ok', 'failed']),
      review: z.enum(['unreviewed', 'approved']),
      slug: localized,
      names: z.object({ sv: z.string().min(1), en: z.string().min(1), scientific: z.string().min(1) }),
      family: z.object({ latin: z.string().min(1), sv: z.string().min(1) }),
      group: z.string().min(1),
      iucn: z.string(),
      marginalia: localized.nullable(),
      images: z.array(
        z.object({
          role: z.enum(['hero', 'extra']),
          file: z.string().regex(/^Q\d+\/(hero|extra)\.webp$/),
          width: z.number().int().positive(),
          height: z.number().int().positive(),
          author: z.string().nullable(),
          license: z.string().min(1),
          licenseUrl: z.string().url().nullable(),
          sourceUrl: z.string().url(),
        }),
      ),
      wikipedia: z.object({ sv: wikiRef, en: wikiRef }),
      text: z.object({ sv: langText, en: langText }).nullable(),
      generated: z.object({ model: z.string(), prompt: z.string(), at: z.string() }),
      errors: z.array(z.string()),
    })
    .superRefine((d, ctx) => {
      if (d.status !== 'ok') return;
      if (!d.text) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver text` });
      if (!d.images.some((i) => i.role === 'hero')) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver huvudfoto` });
    }),
});
```

Ändra sista raden till:

```ts
export const collections = { fieldNotes, species };
```

- [ ] **Step 2: Skriv `src/lib/species.ts`**

```ts
import { getCollection, type CollectionEntry } from 'astro:content';
import type { ImageMetadata } from 'astro';
import groupData from '../data/species-groups.json';
import type { Copy, Locale } from './i18n';

export type Species = CollectionEntry<'species'>['data'];
export type SpeciesImage = Species['images'][number];
export type SpeciesText = NonNullable<Species['text']>['sv'];

export interface Group {
  key: string;
  slug: Record<Locale, string>;
  name: Record<Locale, string>;
  photo: string;
  intro: Record<Locale, string>;
}

/** The app's 15 groups in the app's order (src/data/species-groups.json, shared with the pipeline). */
export const GROUPS: Group[] = groupData.groups;
/** Groups with fewer species get noindex (spec §4). Same rule in src/lib/species-sitemap.mjs. */
export const MIN_GROUP_SIZE = 3;
export const SITE = 'https://birdy.community';
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=se.birdy.android';

const images = import.meta.glob<{ default: ImageMetadata }>('../assets/species/*/*.webp', { eager: true });

export function speciesImage(file: string): ImageMetadata {
  const hit = images[`../assets/species/${file}`];
  if (!hit) throw new Error(`Artbilden saknas: src/assets/species/${file}`);
  return hit.default;
}

export function heroOf(s: Species): SpeciesImage {
  const hero = s.images.find((i) => i.role === 'hero');
  if (!hero) throw new Error(`${s.qid} saknar huvudfoto`);
  return hero;
}

let cached: Species[] | undefined;
/** Every species with status ok. Failed species get no page. */
export async function getAllSpecies(): Promise<Species[]> {
  cached ??= (await getCollection('species')).map((e) => e.data).filter((s) => s.status === 'ok');
  return cached;
}

export const hubHref = (locale: Locale): string => (locale === 'sv' ? '/sv/arter/' : '/species/');
export const speciesHref = (s: Species, locale: Locale): string => `${hubHref(locale)}${s.slug[locale]}/`;
export const groupHref = (g: Group, locale: Locale): string => `${hubHref(locale)}${g.slug[locale]}/`;

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

export function isGroupIndexed(group: Group, list: Species[]): boolean {
  return (groupSizes(list).get(group.key) ?? 0) >= MIN_GROUP_SIZE;
}

/** The `n` groups with the most species, ties in the app's order (footer column). */
export function largestGroups(list: Species[], n: number): Group[] {
  const sizes = groupSizes(list);
  return [...GROUPS].sort((a, b) => (sizes.get(b.key) ?? 0) - (sizes.get(a.key) ?? 0)).slice(0, n);
}

/** The footer's "Common species" row. A listed species without a page stops the build. */
export function commonSpecies(list: Species[]): Species[] {
  return groupData.common.map((qid) => {
    const hit = list.find((s) => s.qid === qid);
    if (!hit) throw new Error(`Vanliga arter: ${qid} saknar sida (status ok krävs). Byt art i src/data/species-groups.json.`);
    return hit;
  });
}

export function groupPhoto(group: Group, list: Species[]): ImageMetadata | undefined {
  const members = list.filter((s) => s.group === group.key);
  const pick = members.find((s) => s.qid === group.photo) ?? members[0];
  return pick ? speciesImage(heroOf(pick).file) : undefined;
}

/** Up to four other species in the same family, or in the same group when the family is too small. */
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

/** Play link with UTM tags, readable in Play Console's acquisition report (spec §12). */
export function playHref(campaign: string, medium: 'species' | 'group' | 'hub'): string {
  const referrer = `utm_source=birdy.community&utm_medium=${medium}&utm_campaign=${campaign}`;
  return `${PLAY_URL}&referrer=${encodeURIComponent(referrer)}`;
}

/** Lowercase, accents removed: "Gök" and "gok" both match. The page script normalises the same way. */
export function searchKey(s: Species): string {
  return [s.names.sv, s.names.en, s.names.scientific].join(' ').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export function wikiUrl(lang: Locale, ref: { title: string; revision: string }): string {
  const title = encodeURIComponent(ref.title.replace(/ /g, '_'));
  return `https://${lang}.wikipedia.org/w/index.php?title=${title}&oldid=${ref.revision}`;
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

/** Throws when two pages in one language would get the same address (spec §4). */
export function assertUniqueSlugs(slugs: string[], locale: Locale): void {
  const seen = new Set<string>();
  for (const slug of slugs) {
    if (seen.has(slug)) throw new Error(`Samma adress två gånger (${locale}): ${slug}`);
    seen.add(slug);
  }
}
```

- [ ] **Step 3: Typkontroll och bygge**

Run: `npx astro check && npm run build`
Expected: `astro check` utan nya fel (ett känt Vite/Tailwind-typfel i `astro.config.mjs` finns sedan tidigare och räknas inte). Bygget går igenom och läser in alla artfiler. Failar schemat på en artfil, rätta filen eller kör om arten i fas 1. Ändra inte schemat för att släppa igenom felet.

Om `copy`-typen saknar `species`-nycklarna klagar `astro check` på `t.species` här. Det åtgärdas i Task 3. Kör i så fall bara `npm run build` nu.

- [ ] **Step 4: Commit**

```bash
git add src/content.config.ts src/lib/species.ts
git commit -m "feat(website): innehållssamlingen species och lib/species.ts"
```

---

### Task 3: Texterna (SV och EN)

**Files:**
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json`

- [ ] **Step 1: Lägg till nycklarna i `copy.sv.json`**

I objektet `nav`, lägg till `"species": "Arter",`. I `footer`, lägg till `"species": "Arter", "allSpecies": "Alla arter från A till Ö", "commonSpecies": "Vanliga arter",`. I `guide`, lägg till `"browse": "Bläddra bland arterna",`. Lägg till ett nytt toppnivåobjekt `species` (före `footer`):

```json
  "species": {
    "allChip": "Alla arter",
    "groupsLabel": "Grupper",
    "searchLabel": "Sök art",
    "searchPlaceholder": "Sök art",
    "searchSubmit": "Sök",
    "kicker": "Uppslagsverket",
    "hubHeadline": "Fåglar i Sverige och *Europa*",
    "hubLead": "{n} vanliga fåglar med foton, kännetecken och läten. Samma uppslagsverk som i appen, där du också kan känna igen fågeln på plats.",
    "hubGroups": "Grupperna",
    "hubAll": "Alla arter från A till Ö",
    "noResults": "Ingen art matchar sökningen.",
    "groupSpecies": "Arterna",
    "countOne": "1 art",
    "countMany": "{n} arter",
    "titleSpecies": "{name}: kännetecken, läte och foton | Birdy",
    "titleSpeciesShort": "{name}: kännetecken och läte | Birdy",
    "titleGroup": "{group}: {count} med foton och kännetecken | Birdy",
    "titleGroupShort": "{group}: arter och kännetecken | Birdy",
    "titleHub": "Fåglar i Sverige och Europa: {count} med foton | Birdy",
    "descGroup": "{group}: {count} med foton, kännetecken och läten. Lär dig skilja dem åt i fält, och känn igen dem på plats med appen Birdy.",
    "descHub": "Bläddra bland {n} vanliga fåglar i Sverige och Europa. Foton, kännetecken och läten, sorterade i samma grupper som i appen Birdy.",
    "crumbLabel": "Brödsmulor",
    "crumbHome": "Birdy",
    "crumbHub": "Arter",
    "facts": {
      "scientific": "Vetenskapligt namn",
      "family": "Familj",
      "sweden": "I Sverige",
      "size": "Storlek",
      "iucn": "Global rödlista (IUCN)"
    },
    "statusLabels": {
      "resident": "Stannfågel",
      "breeding_migrant": "Flyttfågel, häckar här",
      "passage": "Ses under flyttningen",
      "winter_visitor": "Vintergäst",
      "rare_visitor": "Sällsynt gäst",
      "absent": "Förekommer inte"
    },
    "iucnLabels": {
      "LC": "Livskraftig",
      "NT": "Nära hotad",
      "VU": "Sårbar",
      "EN": "Starkt hotad",
      "CR": "Akut hotad",
      "EW": "Utdöd i vilt tillstånd",
      "EX": "Utdöd",
      "DD": "Kunskapsbrist"
    },
    "headMarks": "Så känner du igen den",
    "headVoice": "Läte",
    "headWhere": "Var och när",
    "moreFamily": "Fler {family}",
    "moreGroup": "Fler {group}",
    "appHeadline": "Osäker på vad du ser?",
    "appText": "Birdy känner igen arten på foto eller läte, direkt i telefonen och utan täckning.",
    "plate": "Pl. {n}",
    "photoCredit": "Foto:",
    "via": "via",
    "unknownAuthor": "okänd fotograf",
    "textCredit": "Texten bygger på Wikipedia och får delas under",
    "sources": "Källor",
    "articleSv": "svenska artikeln",
    "articleEn": "engelska artikeln",
    "reportError": "Hittade du ett fel? Skriv till oss.",
    "reportSubject": "Fel på artsidan: {name}",
    "altHero": "{name} ({scientific})",
    "altExtra": "{name}, ytterligare foto"
  },
```

- [ ] **Step 2: Samma nycklar i `copy.en.json`**

`nav.species`: `"Species"`. `footer`: `"species": "Species", "allSpecies": "All species A to Z", "commonSpecies": "Common species"`. `guide.browse`: `"Browse the species"`. Objektet `species`:

```json
  "species": {
    "allChip": "All species",
    "groupsLabel": "Groups",
    "searchLabel": "Search species",
    "searchPlaceholder": "Search species",
    "searchSubmit": "Search",
    "kicker": "Field guide",
    "hubHeadline": "Birds of Sweden and *Europe*",
    "hubLead": "{n} common birds with photos, field marks and calls. The same field guide as in the app, where you can also identify the bird on the spot.",
    "hubGroups": "The groups",
    "hubAll": "All species A to Z",
    "noResults": "No species match your search.",
    "groupSpecies": "The species",
    "countOne": "1 species",
    "countMany": "{n} species",
    "titleSpecies": "{name}: identification, song and photos | Birdy",
    "titleSpeciesShort": "{name}: identification | Birdy",
    "titleGroup": "{group}: {count} with photos and ID tips | Birdy",
    "titleGroupShort": "{group}: species and ID tips | Birdy",
    "titleHub": "Birds of Sweden and Europe: {count} with photos | Birdy",
    "descGroup": "{group}: {count} with photos, field marks and calls. Learn to tell them apart, and identify them on the spot with the Birdy app.",
    "descHub": "Browse {n} common birds of Sweden and Europe. Photos, field marks and calls, sorted in the same groups as in the Birdy app.",
    "crumbLabel": "Breadcrumb",
    "crumbHome": "Birdy",
    "crumbHub": "Species",
    "facts": {
      "scientific": "Scientific name",
      "family": "Family",
      "sweden": "In Sweden",
      "size": "Size",
      "iucn": "Global Red List (IUCN)"
    },
    "statusLabels": {
      "resident": "Resident all year",
      "breeding_migrant": "Summer visitor, breeds here",
      "passage": "Seen on migration",
      "winter_visitor": "Winter visitor",
      "rare_visitor": "Rare visitor",
      "absent": "Does not occur"
    },
    "iucnLabels": {
      "LC": "Least concern",
      "NT": "Near threatened",
      "VU": "Vulnerable",
      "EN": "Endangered",
      "CR": "Critically endangered",
      "EW": "Extinct in the wild",
      "EX": "Extinct",
      "DD": "Data deficient"
    },
    "headMarks": "How to recognise it",
    "headVoice": "Call and song",
    "headWhere": "Where and when",
    "moreFamily": "More in the {family} family",
    "moreGroup": "More {group}",
    "appHeadline": "Not sure what you are seeing?",
    "appText": "Birdy identifies the species from a photo or its song, right on your phone and without signal.",
    "plate": "Pl. {n}",
    "photoCredit": "Photo:",
    "via": "via",
    "unknownAuthor": "unknown photographer",
    "textCredit": "The text is based on Wikipedia and may be shared under",
    "sources": "Sources",
    "articleSv": "Swedish article",
    "articleEn": "English article",
    "reportError": "Found a mistake? Write to us.",
    "reportSubject": "Mistake on the species page: {name}",
    "altHero": "{name} ({scientific})",
    "altExtra": "{name}, another photo"
  },
```

- [ ] **Step 3: Kör textvakterna**

Run: `npm run test:i18n && npm run test:no-dashes && npm run test:no-accuracy && npx astro check`
Expected: paritet OK, inga streck, ingen noggrannhetssiffra och inga nya typfel (nu känner `Copy` till `species`).

- [ ] **Step 4: Commit**

```bash
git add src/content/copy.sv.json src/content/copy.en.json
git commit -m "feat(website): texter för artsidorna (SV och EN)"
```

---

### Task 4: Kategoriraden, artkortet och de delade stilarna

**Files:**
- Modify: `website/src/components/ui/Icon.astro` (ny ikon `search`)
- Create: `website/src/styles/species.css`
- Create: `website/src/components/species/CategoryBar.astro`
- Create: `website/src/components/species/SpeciesCard.astro`

- [ ] **Step 1: Sökikonen**

I `src/components/ui/Icon.astro`, lägg till i `paths` efter `menu`:

```ts
  search: '<circle cx="11" cy="11" r="6"/><path d="m20 20-4.5-4.5"/>',
```

- [ ] **Step 2: Delade stilar**

`src/styles/species.css`:

```css
/* Shared by the species hub, group pages and species pages (spec 2026-09-25 §5 and §6). */
.sp-crumbs ol { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin: 0 0 16px; padding: 0; font-size: 12.5px; color: var(--muted); }
.sp-crumbs li + li::before { content: '›'; margin-right: 6px; }
.sp-crumbs a { color: var(--muted); border-bottom: 1px solid transparent; }
.sp-crumbs a:hover { color: var(--rust); border-bottom-color: currentColor; }
.sp-h2 { font-size: clamp(24px, 2.6vw, 30px); margin: 44px 0 16px; }
.sp-cards { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 14px; }
.sp-app { background: var(--moss); color: var(--cream); border-radius: 14px; padding: 18px 20px; --jh-ink: var(--cream); }
.sp-app .sp-app-h { margin: 0 0 6px; font-family: var(--font-serif); font-style: italic; font-size: 21px; color: var(--apricot); }
.sp-app p { margin: 0 0 12px; font-size: 14px; line-height: 1.55; color: var(--cream); }
.sp-app :global(:focus-visible), .sp-app :focus-visible { outline-color: var(--apricot); }
@media (max-width: 760px) {
  .sp-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
}
```

- [ ] **Step 3: Artkortet**

`src/components/species/SpeciesCard.astro`:

```astro
---
import { Image } from 'astro:assets';
import type { Locale } from '../../lib/i18n';
import { heroOf, speciesHref, speciesImage, type Species } from '../../lib/species';

interface Props { species: Species; locale: Locale }
const { species: s, locale } = Astro.props;
---

<a class="scard" href={speciesHref(s, locale)}>
  <Image src={speciesImage(heroOf(s).file)} alt="" widths={[320, 480]} sizes="(max-width: 760px) 45vw, 220px" loading="lazy" decoding="async" />
  <span class="scard-name">{s.names[locale]}</span>
  <span class="scard-latin">{s.names.scientific}</span>
</a>

<style>
  .scard { display: block; height: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper), box-shadow .25s var(--ease-paper); }
  .scard:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(31, 42, 25, .12); }
  .scard :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 8px; }
  .scard-name { display: block; margin: 8px 4px 0; font-weight: 600; font-size: 14px; color: var(--ink); }
  .scard-latin { display: block; margin: 0 4px 4px; font-family: var(--font-script); font-size: 17px; color: var(--muted); }
</style>
```

- [ ] **Step 4: Kategoriraden**

`src/components/species/CategoryBar.astro`:

```astro
---
import Icon from '../ui/Icon.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import { GROUPS, getAllSpecies, groupHref, groupSizes, hubHref } from '../../lib/species';

interface Props {
  locale: Locale;
  /** 'all' on the hub, otherwise the group key. */
  active: string;
  /** The hub has its own search field, so it hides this one. */
  search?: boolean;
}
const { locale, active, search = true } = Astro.props;
const t = getCopy(locale);
const all = await getAllSpecies();
const sizes = groupSizes(all);
const chips = [
  { key: 'all', href: hubHref(locale), label: t.species.allChip, n: all.length },
  ...GROUPS.map((g) => ({ key: g.key, href: groupHref(g, locale), label: g.name[locale], n: sizes.get(g.key) ?? 0 })),
];
---

<div class="catbar" data-catbar>
  <div class="catbar-inner">
    <nav class="chips" aria-label={t.species.groupsLabel} data-chips>
      {chips.map((c) => (
        <a class:list={['chip', { 'is-active': c.key === active }]} href={c.href} aria-current={c.key === active ? 'page' : undefined}>
          {c.label} <span class="n">{c.n}</span>
        </a>
      ))}
    </nav>
    {search && (
      <form class="search" action={hubHref(locale)} method="get" role="search">
        <label class="sr-only" for="cat-q">{t.species.searchLabel}</label>
        <input id="cat-q" name="q" type="search" placeholder={t.species.searchPlaceholder} autocomplete="off" />
        <button type="submit" class="search-btn" aria-label={t.species.searchSubmit}><Icon name="search" size={18} /></button>
      </form>
    )}
  </div>
</div>

<script>
  // Bring the active chip into view on narrow screens, without scrolling the page itself.
  const chips = document.querySelector<HTMLElement>('[data-chips]');
  const current = chips?.querySelector<HTMLElement>('.chip.is-active');
  if (chips && current) chips.scrollLeft = current.offsetLeft - (chips.clientWidth - current.offsetWidth) / 2;
</script>

<style>
  .catbar { position: sticky; top: 76px; z-index: 90; background: var(--card); border-bottom: 1px solid var(--line); }
  .catbar-inner { max-width: 1320px; margin: 0 auto; display: flex; align-items: center; gap: 16px; padding: 10px 44px; }
  .chips { display: flex; gap: 8px; overflow-x: auto; scrollbar-width: none; flex: 1; min-width: 0; }
  .chips::-webkit-scrollbar { display: none; }
  .chip { flex: none; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); font-size: 13px; font-weight: 600; color: var(--ink); white-space: nowrap; transition: border-color .2s; }
  .chip:hover { border-color: var(--rust); }
  .chip .n { font-weight: 400; color: var(--muted); }
  .chip.is-active { background: var(--rust); border-color: var(--rust); color: var(--cream); }
  .chip.is-active .n { color: var(--cream); }
  .search { flex: none; display: flex; align-items: center; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); padding: 0 4px 0 14px; }
  .search:focus-within { outline: 3px solid var(--rust); outline-offset: 2px; }
  .search input { border: 0; background: none; font: inherit; font-size: 13px; width: 150px; padding: 7px 0; color: var(--ink); outline: none; }
  .search-btn { border: 0; background: none; color: var(--muted); width: 32px; height: 32px; display: grid; place-items: center; cursor: pointer; }
  @media (max-width: 1023px) {
    .catbar { top: 64px; }
    .catbar-inner { padding: 8px 20px; gap: 10px; }
  }
  @media (max-width: 760px) {
    .search { padding: 0; border-color: transparent; background: none; }
    .search input { width: 0; padding: 0; }
  }
</style>
```

- [ ] **Step 5: Bygg**

Run: `npm run build`
Expected: bygget går igenom (komponenterna används inte än).

- [ ] **Step 6: Commit**

```bash
git add src/components/ui/Icon.astro src/styles/species.css src/components/species/CategoryBar.astro src/components/species/SpeciesCard.astro
git commit -m "feat(website): kategoriraden, artkortet och delade stilar för artsidorna"
```

---

### Task 5: Ingångssidan

**Files:**
- Create: `website/tests/species.spec.ts`
- Create: `website/src/components/species/SpeciesHub.astro`
- Create: `website/src/pages/sv/arter/index.astro`, `website/src/pages/species/index.astro`

- [ ] **Step 1: Skriv testerna för ingångssidan**

`tests/species.spec.ts`:

```ts
import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

async function noSideScroll(page: Page): Promise<void> {
  const [scroll, client] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(scroll).toBeLessThanOrEqual(client);
}

test.describe('ingångssidan', () => {
  for (const [path, h1, other] of [['/sv/arter/', 'Europa', '/species/'], ['/species/', 'Europe', '/sv/arter/']] as const) {
    test(`${path} visar grupper och hela listan`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toContainText(h1);
      expect(await page.locator('.groups a').count()).toBe(15);
      expect(await page.locator('[data-item]').count()).toBeGreaterThan(150);
      await expect(page.locator('link[rel="alternate"][hreflang="' + (path.startsWith('/sv') ? 'en' : 'sv') + '"]')).toHaveAttribute('href', `https://birdy.community${other}`);
      expect(errors).toEqual([]);
    });
  }

  test('sökningen filtrerar och klarar å, ä och ö', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Talgoxe');
    await page.locator('#species-search').fill('blames');
    await expect(page.locator('[data-item]:visible').first()).toContainText('Blåmes');
    await page.locator('#species-search').fill('zzzz');
    await expect(page.locator('[data-no-results]')).toBeVisible();
  });

  test('?q= fyller i sökfältet', async ({ page }) => {
    await page.goto('/sv/arter/?q=talg');
    await expect(page.locator('#species-search')).toHaveValue('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
  });

  test('390 px utan sidledsscroll', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/');
    await noSideScroll(page);
  });
});

test.describe('ingångssidan utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });
  test('hela listan syns', async ({ page }) => {
    await page.goto('/sv/arter/');
    const total = await page.locator('[data-item]').count();
    await expect(page.locator('[data-item]:visible')).toHaveCount(total);
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: FAIL, `/sv/arter/` ger 404.

- [ ] **Step 3: Skriv komponenten**

`src/components/species/SpeciesHub.astro`:

```astro
---
import { Image } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import JournalHeadline from '../ui/JournalHeadline.astro';
import CategoryBar from './CategoryBar.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  GROUPS, breadcrumbJsonLd, countLabel, getAllSpecies, groupHref, groupPhoto, groupSizes, hubHref,
  itemListJsonLd, searchKey, sortByName, speciesHref,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const all = await getAllSpecies();
const sizes = groupSizes(all);
const sorted = sortByName(all, locale);
const firstLetter = (name: string) => name.charAt(0).toLocaleUpperCase(locale);
const letters = [...new Set(sorted.map((s) => firstLetter(s.names[locale])))];
const pathname = hubHref(locale);
const n = all.length;
const title = t.species.titleHub.replace('{count}', countLabel(n, t));
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: pathname },
];
const jsonLd = [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, title.replace(/ \| Birdy$/, ''), locale, sorted)];
---

<Layout locale={locale} pathname={pathname} alternatePath={hubHref(other)} title={title} description={t.species.descHub.replace('{n}', String(n))} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={hubHref(other)} />
  <CategoryBar locale={locale} active="all" search={false} />
  <main class="hub wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[1].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <JournalHeadline text={t.species.hubHeadline} level="h1" align="left" size="clamp(38px, 5vw, 60px)" />
    <p class="lead">{t.species.hubLead.replace('{n}', String(n))}</p>
    <div class="hub-search">
      <label class="sr-only" for="species-search">{t.species.searchLabel}</label>
      <input id="species-search" name="q" type="search" placeholder={t.species.searchPlaceholder} autocomplete="off" />
    </div>

    <h2 class="sp-h2">{t.species.hubGroups}</h2>
    <ul class="groups" role="list">
      {GROUPS.map((g) => {
        const photo = groupPhoto(g, all);
        return (
          <li>
            <a class="gcard" href={groupHref(g, locale)}>
              {photo && <Image src={photo} alt="" widths={[320, 480]} sizes="(max-width: 760px) 45vw, 200px" loading="lazy" decoding="async" />}
              <span class="gcard-name">{g.name[locale]}</span>
              <span class="gcard-n">{countLabel(sizes.get(g.key) ?? 0, t)}</span>
            </a>
          </li>
        );
      })}
    </ul>

    <h2 class="sp-h2" id="a-o">{t.species.hubAll}</h2>
    <p class="no-results" data-no-results hidden>{t.species.noResults}</p>
    {letters.map((letter) => (
      <section class="letter" data-letter>
        <h3>{letter}</h3>
        <ul role="list">
          {sorted.filter((s) => firstLetter(s.names[locale]) === letter).map((s) => (
            <li data-item data-search={searchKey(s)}>
              <a href={speciesHref(s, locale)}><span>{s.names[locale]}</span> <i>{s.names.scientific}</i></a>
            </li>
          ))}
        </ul>
      </section>
    ))}
  </main>
  <Footer locale={locale} switchLangHref={hubHref(other)} />
</Layout>

<script>
  const input = document.querySelector<HTMLInputElement>('#species-search');
  const items = [...document.querySelectorAll<HTMLElement>('[data-search]')];
  const sections = [...document.querySelectorAll<HTMLElement>('[data-letter]')];
  const empty = document.querySelector<HTMLElement>('[data-no-results]');
  const norm = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
  const apply = () => {
    const q = norm(input?.value ?? '');
    let shown = 0;
    for (const item of items) {
      const hit = !q || (item.dataset.search ?? '').includes(q);
      item.hidden = !hit;
      if (hit) shown += 1;
    }
    for (const section of sections) section.hidden = !section.querySelector('[data-item]:not([hidden])');
    if (empty) empty.hidden = shown > 0;
  };
  const params = new URLSearchParams(location.search);
  if (input && params.has('q')) {
    input.value = params.get('q') ?? '';
    input.focus();
  }
  apply();
  input?.addEventListener('input', apply);
</script>

<style>
  .hub { padding-top: 26px; padding-bottom: 88px; }
  .hub-search { margin: 22px 0 0; max-width: 420px; }
  .hub-search input { width: 100%; box-sizing: border-box; font: inherit; font-size: 15px; padding: 12px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: var(--ink); }
  .hub-search input:focus-visible { outline: 3px solid var(--rust); outline-offset: 2px; }
  .groups { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
  .gcard { display: block; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper); }
  .gcard:hover { transform: translateY(-2px); }
  .gcard :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 8px; }
  .gcard-name { display: block; margin: 8px 4px 0; font-weight: 600; font-size: 14px; }
  .gcard-n { display: block; margin: 0 4px 4px; font-size: 12.5px; color: var(--muted); }
  .letter { margin-top: 22px; }
  .letter h3 { font-size: 26px; color: var(--rust); margin: 0 0 8px; }
  .letter ul { list-style: none; margin: 0; padding: 0; columns: 3 220px; column-gap: 28px; }
  .letter li { break-inside: avoid; padding: 5px 0; border-bottom: 1px dotted var(--line); font-size: 14.5px; }
  .letter li[hidden], .letter[hidden] { display: none; }
  .letter a:hover span { color: var(--rust); }
  .letter i { font-family: var(--font-script); font-style: normal; font-size: 16px; color: var(--muted); margin-left: 4px; }
  .no-results { color: var(--muted); }
  @media (max-width: 760px) {
    .groups { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  }
</style>
```

- [ ] **Step 4: Routes**

`src/pages/sv/arter/index.astro`:

```astro
---
import SpeciesHub from '../../../components/species/SpeciesHub.astro';
---
<SpeciesHub locale="sv" />
```

`src/pages/species/index.astro`:

```astro
---
import SpeciesHub from '../../components/species/SpeciesHub.astro';
---
<SpeciesHub locale="en" />
```

- [ ] **Step 5: Kör testerna igen**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (7 tester)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/SpeciesHub.astro src/pages/sv/arter/index.astro src/pages/species/index.astro tests/species.spec.ts
git commit -m "feat(website): ingångssidan för arterna med sökning"
```

---

### Task 6: Gruppsidorna

**Files:**
- Create: `website/src/components/species/GroupPage.astro`
- Create: `website/src/pages/sv/arter/[slug].astro`, `website/src/pages/species/[slug].astro`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Skriv testerna**

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('gruppsidorna', () => {
  test('/sv/arter/ugglor/ har aktiv chip, arter och approta', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/ugglor/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Ugglor');
    await expect(page.locator('.catbar .chip[aria-current="page"]')).toContainText('Ugglor');
    expect(await page.locator('[data-item]').count()).toBeGreaterThanOrEqual(3);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    await expect(page.locator('a[href*="utm_medium%3Dgroup"]')).toHaveCount(1);
    expect(errors).toEqual([]);
  });

  test('Tättingar delas upp i familjer', async ({ page }) => {
    await page.goto('/sv/arter/tattingar/');
    expect(await page.locator('.family h3').count()).toBeGreaterThan(5);
  });

  test('små grupper har noindex', async ({ page }) => {
    await page.goto('/sv/arter/havsfaglar/');
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, follow');
  });

  test('kategoriraden sveps i sidled på 390 px utan att sidan gör det', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/ugglor/');
    const [scroll, client] = await page.locator('[data-chips]').evaluate((el) => [el.scrollWidth, el.clientWidth]);
    expect(scroll).toBeGreaterThan(client);
    await noSideScroll(page);
  });

  test('engelska gruppsidan och språkbytet', async ({ page }) => {
    const res = await page.goto('/species/owls/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('link[rel="alternate"][hreflang="sv"]')).toHaveAttribute('href', 'https://birdy.community/sv/arter/ugglor/');
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g gruppsidorna`
Expected: FAIL (404)

- [ ] **Step 3: Skriv gruppsidan**

`src/components/species/GroupPage.astro`:

```astro
---
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import SpeciesCard from './SpeciesCard.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  breadcrumbJsonLd, countLabel, getAllSpecies, groupHref, groupTitle, hubHref, isGroupIndexed,
  itemListJsonLd, playHref, sortByName, type Group, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { group: Group; locale: Locale }
const { group, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const all = await getAllSpecies();
const members = sortByName(all.filter((s) => s.group === group.key), locale);
const pathname = groupHref(group, locale);
const title = groupTitle(group, members.length, locale, t);
const description = t.species.descGroup.replace('{group}', group.name[locale]).replace('{count}', countLabel(members.length, t));
const familyName = (s: Species) => (locale === 'sv' ? s.family.sv : s.family.latin);
const byFamily = group.key === 'songbirds'
  ? [...new Set(members.map(familyName))].sort((a, b) => a.localeCompare(b, locale)).map((name) => ({ name, items: members.filter((s) => familyName(s) === name) }))
  : [];
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: group.name[locale], href: pathname },
];
const jsonLd = [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, group.name[locale], locale, byFamily.length ? byFamily.flatMap((f) => f.items) : members)];
---

<Layout locale={locale} pathname={pathname} alternatePath={groupHref(group, other)} title={title} description={description} noindex={!isGroupIndexed(group, all)} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={groupHref(group, other)} />
  <CategoryBar locale={locale} active={group.key} />
  <main class="group wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><a href={crumbs[1].href} data-crumb>{crumbs[1].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[2].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <h1>{group.name[locale]}</h1>
    <p class="lead">{group.intro[locale]}</p>

    <h2 class="sp-h2">{t.species.groupSpecies} <span class="count">{countLabel(members.length, t)}</span></h2>
    {byFamily.length > 0 ? (
      byFamily.map((f) => (
        <section class="family">
          <h3>{f.name}</h3>
          <ul class="sp-cards" role="list">
            {f.items.map((s) => <li data-item><SpeciesCard species={s} locale={locale} /></li>)}
          </ul>
        </section>
      ))
    ) : (
      <ul class="sp-cards" role="list">
        {members.map((s) => <li data-item><SpeciesCard species={s} locale={locale} /></li>)}
      </ul>
    )}

    <aside class="sp-app group-app">
      <p class="sp-app-h">{t.species.appHeadline}</p>
      <p>{t.species.appText}</p>
      <PlayStoreBadge locale={locale} href={playHref(group.slug[locale], 'group')} alt={t.alt.playStoreBadge} size="small" />
    </aside>
  </main>
  <Footer locale={locale} switchLangHref={groupHref(group, other)} />
</Layout>

<style>
  .group { padding-top: 26px; padding-bottom: 88px; }
  h1 { font-size: clamp(38px, 5vw, 60px); line-height: 1.04; }
  .count { font-family: var(--font-sans); font-size: 14px; color: var(--muted); margin-left: 8px; }
  .family h3 { font-size: 22px; margin: 30px 0 12px; }
  .group-app { margin-top: 48px; max-width: 520px; }
</style>
```

- [ ] **Step 4: Routes (grupper nu, arter i Task 7)**

`src/pages/sv/arter/[slug].astro`:

```astro
---
import GroupPage from '../../../components/species/GroupPage.astro';
import { GROUPS, assertUniqueSlugs, type Group } from '../../../lib/species';

export async function getStaticPaths() {
  assertUniqueSlugs(GROUPS.map((g) => g.slug.sv), 'sv');
  return GROUPS.map((group) => ({ params: { slug: group.slug.sv }, props: { group } }));
}

interface Props { group: Group }
const { group } = Astro.props;
---
<GroupPage group={group} locale="sv" />
```

`src/pages/species/[slug].astro`:

```astro
---
import GroupPage from '../../components/species/GroupPage.astro';
import { GROUPS, assertUniqueSlugs, type Group } from '../../lib/species';

export async function getStaticPaths() {
  assertUniqueSlugs(GROUPS.map((g) => g.slug.en), 'en');
  return GROUPS.map((group) => ({ params: { slug: group.slug.en }, props: { group } }));
}

interface Props { group: Group }
const { group } = Astro.props;
---
<GroupPage group={group} locale="en" />
```

- [ ] **Step 5: Kör testerna igen**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (alla, 12 tester)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/GroupPage.astro src/pages/sv/arter/[slug].astro src/pages/species/[slug].astro tests/species.spec.ts
git commit -m "feat(website): gruppsidorna med familjer, noindex för små grupper och approta"
```

---

### Task 7: Artsidan (layout B)

**Files:**
- Create: `website/src/components/species/Credits.astro`
- Create: `website/src/components/species/SpeciesArticle.astro`
- Modify: `website/src/pages/sv/arter/[slug].astro`, `website/src/pages/species/[slug].astro` (arter läggs till)
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Skriv testerna**

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('artsidan', () => {
  test('talgoxe: rubrik, fakta, texter, credits och språkbyte', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/talgoxe/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Talgoxe');
    await expect(page.locator('.latin')).toHaveText('Parus major');
    await expect(page.locator('.facts')).toContainText('Vetenskapligt namn');
    await expect(page.locator('.facts')).toContainText('Mesar');
    await expect(page.locator('h2')).toContainText(['Så känner du igen den', 'Läte', 'Var och när']);
    const photos = await page.locator('[data-photo]').count();
    await expect(page.locator('[data-credit-for]')).toHaveCount(photos);
    await expect(page.locator('[data-wiki-credit]')).toContainText('CC BY-SA 4.0');
    await expect(page.locator('a[href*="utm_campaign%3Dtalgoxe"]')).toHaveCount(1);
    await expect(page.locator('#site-nav .links a[lang="en"]')).toHaveAttribute('href', '/species/great-tit/');
    await expect(page.locator('#site-nav .links a[aria-current="page"]')).toHaveText('Arter');
    await expect(page.locator('.catbar .chip[aria-current="page"]')).toContainText('Tättingar');
    expect(errors).toEqual([]);
  });

  test('vänsterspalten följer med på dator', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/arter/talgoxe/');
    await page.evaluate(() => window.scrollTo({ top: 900, behavior: 'instant' }));
    const box = (await page.locator('.plate.hero').boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(76);
    expect(box.y).toBeLessThan(260);
  });

  test('mobilen: rubrik, foto, fakta och ingress i den ordningen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/talgoxe/');
    const y = async (sel: string) => (await page.locator(sel).first().boundingBox())!.y;
    const [h1, hero, facts, lead, app] = [await y('h1'), await y('.plate.hero'), await y('.facts'), await y('.sp-lead'), await y('.sp-app')];
    expect(h1).toBeLessThan(hero);
    expect(hero).toBeLessThan(facts);
    expect(facts).toBeLessThan(lead);
    expect(lead).toBeLessThan(app);
  });

  for (const width of [360, 390, 430]) {
    test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/arter/talgoxe/', '/species/great-tit/']) {
        await page.goto(path);
        await noSideScroll(page);
      }
    });
  }
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g artsidan`
Expected: FAIL (404 på `/sv/arter/talgoxe/`)

- [ ] **Step 3: Credits**

`src/components/species/Credits.astro`:

```astro
---
import { getCopy, type Locale } from '../../lib/i18n';
import { CONTACT_EMAIL } from '../../lib/links';
import { wikiUrl, type Species, type SpeciesImage } from '../../lib/species';

interface Props { species: Species; locale: Locale; images: SpeciesImage[] }
const { species: s, locale, images } = Astro.props;
const t = getCopy(locale);
const sources = (['sv', 'en'] as const).flatMap((lang) => {
  const ref = s.wikipedia[lang];
  return ref ? [{ href: wikiUrl(lang, ref), label: lang === 'sv' ? t.species.articleSv : t.species.articleEn }] : [];
});
const subject = encodeURIComponent(t.species.reportSubject.replace('{name}', s.names[locale]));
---

<div class="credits">
  {images.map((img) => (
    <p data-credit-for={img.role}>
      {t.species.photoCredit} {img.author ?? t.species.unknownAuthor},{' '}
      {img.licenseUrl ? <a href={img.licenseUrl} rel="license noopener">{img.license}</a> : img.license},{' '}
      {t.species.via} <a href={img.sourceUrl} rel="noopener">Wikimedia Commons</a>
    </p>
  ))}
  <p data-wiki-credit>
    {t.species.textCredit} <a href="https://creativecommons.org/licenses/by-sa/4.0/" rel="license noopener">CC BY-SA 4.0</a>.
    {' '}{t.species.sources}:{' '}
    {sources.map((src, i) => (
      <Fragment>{i > 0 && ', '}<a href={src.href} rel="noopener">{src.label}</a></Fragment>
    ))}
  </p>
  <p class="report"><a href={`mailto:${CONTACT_EMAIL}?subject=${subject}`}>{t.species.reportError}</a></p>
</div>

<style>
  .credits { margin-top: 40px; padding-top: 14px; border-top: 1px solid var(--line); font-size: 12.5px; line-height: 1.6; color: var(--muted); }
  .credits p { margin: 0 0 6px; }
  .credits a { color: var(--muted); text-decoration: underline; text-underline-offset: 2px; }
  .credits a:hover { color: var(--rust); }
  .report { margin-top: 12px !important; font-weight: 600; }
  .report a { color: var(--rust); }
</style>
```

- [ ] **Step 4: Artsidan**

`src/components/species/SpeciesArticle.astro`:

```astro
---
import { Image, getImage } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import SpeciesCard from './SpeciesCard.astro';
import Credits from './Credits.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  SITE, breadcrumbJsonLd, getAllSpecies, groupByKey, groupHref, heroOf, hubHref, playHref, related,
  speciesHref, speciesImage, speciesTitle, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { species: Species; locale: Locale }
const { species: s, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const text = s.text![locale];
const all = await getAllSpecies();
const group = groupByKey(s.group);
const name = s.names[locale];
const pathname = speciesHref(s, locale);
const otherPath = speciesHref(s, other);
const title = speciesTitle(s, locale, t);

const hero = heroOf(s);
const extra = s.images.find((i) => i.role === 'extra');
const heroImg = speciesImage(hero.file);
const extraImg = extra ? speciesImage(extra.file) : undefined;
const shown = extra ? [hero, extra] : [hero];
const altHero = t.species.altHero.replace('{name}', name).replace('{scientific}', s.names.scientific);
const share = await getImage({ src: heroImg, width: 1200, height: 630, fit: 'cover', format: 'jpg', quality: 82 });
const content = await getImage({ src: heroImg, width: 1200, format: 'webp' });

const status = text.facts.swedenStatus ? t.species.statusLabels[text.facts.swedenStatus.value] : undefined;
const iucnLabel = s.iucn !== 'NE' ? (t.species.iucnLabels as Record<string, string>)[s.iucn] : undefined;
const familyShown = locale === 'sv' ? s.family.sv : s.family.latin;
const rel = related(s, all, locale);
const moreHeading = rel.kind === 'family'
  ? t.species.moreFamily.replace('{family}', locale === 'sv' ? s.family.sv.toLocaleLowerCase('sv') : s.family.latin)
  : t.species.moreGroup.replace('{group}', group.name[locale].toLocaleLowerCase(locale));
const marginalia = s.marginalia?.[locale];

const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: group.name[locale], href: groupHref(group, locale) },
  { name, href: pathname },
];
const jsonLd = [
  breadcrumbJsonLd(crumbs),
  {
    '@type': 'WebPage',
    url: new URL(pathname, SITE).toString(),
    name: title.replace(/ \| Birdy$/, ''),
    inLanguage: locale,
    about: {
      '@type': 'Taxon',
      name: s.names.scientific,
      alternateName: [s.names.sv, s.names.en],
      taxonRank: 'species',
      sameAs: `https://www.wikidata.org/wiki/${s.qid}`,
    },
    primaryImageOfPage: {
      '@type': 'ImageObject',
      contentUrl: new URL(content.src, SITE).toString(),
      ...(hero.licenseUrl ? { license: hero.licenseUrl } : {}),
      acquireLicensePage: hero.sourceUrl,
      ...(hero.author ? { creator: { '@type': 'Person', name: hero.author }, creditText: hero.author } : {}),
    },
  },
];
---

<Layout locale={locale} pathname={pathname} alternatePath={otherPath} title={title} description={text.metaDescription} ogImage={share.src} ogImageAlt={altHero} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={otherPath} />
  <CategoryBar locale={locale} active={s.group} />
  <main class="sp wrap" data-species-page>
    <div class="spread">
      <header class="head">
        <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
          <ol>
            {crumbs.slice(0, -1).map((c) => <li><a href={c.href} data-crumb>{c.name}</a></li>)}
            <li><span aria-current="page" data-crumb>{name}</span></li>
          </ol>
        </nav>
        <Kicker text={familyShown} />
        <h1>{name}</h1>
        <p class="latin">{s.names.scientific}</p>
      </header>

      <div class="left">
        <div class="left-inner">
          <figure class="plate hero" data-photo="hero">
            <Image src={heroImg} alt={altHero} widths={[480, 800, 1200]} sizes="(max-width: 1023px) 100vw, 440px" loading="eager" fetchpriority="high" decoding="async" />
            <figcaption><span>{t.species.plate.replace('{n}', '1')}, {name}</span><span>{t.species.photoCredit} {hero.author ?? t.species.unknownAuthor}</span></figcaption>
          </figure>
          <dl class="facts">
            <div><dt>{t.species.facts.scientific}</dt><dd><i>{s.names.scientific}</i></dd></div>
            <div><dt>{t.species.facts.family}</dt><dd>{familyShown}</dd></div>
            {status && <div><dt>{t.species.facts.sweden}</dt><dd>{status}</dd></div>}
            {text.facts.size && <div><dt>{t.species.facts.size}</dt><dd>{text.facts.size.value}</dd></div>}
            {iucnLabel && <div><dt>{t.species.facts.iucn}</dt><dd>{iucnLabel} ({s.iucn})</dd></div>}
          </dl>
          <aside class="sp-app">
            <p class="sp-app-h">{t.species.appHeadline}</p>
            <p>{t.species.appText}</p>
            <PlayStoreBadge locale={locale} href={playHref(s.slug[locale], 'species')} alt={t.alt.playStoreBadge} size="small" />
          </aside>
          {marginalia && <p class="note">{marginalia}</p>}
        </div>
      </div>

      <div class="body">
        <p class="sp-lead">{text.lead}</p>
        <div class="texts">
          <h2>{t.species.headMarks}</h2>
          <ul class="marks">{text.fieldMarks.map((m) => <li>{m}</li>)}</ul>
          <h2>{t.species.headVoice}</h2>
          <p>{text.voice}</p>
          <h2>{t.species.headWhere}</h2>
          <p>{text.whereWhen}</p>
        </div>
        {extra && extraImg && (
          <figure class="plate extra" data-photo="extra">
            <Image src={extraImg} alt={t.species.altExtra.replace('{name}', name)} widths={[480, 800, 1200]} sizes="(max-width: 1023px) 100vw, 640px" loading="lazy" decoding="async" />
            <figcaption><span>{t.species.plate.replace('{n}', '2')}</span><span>{t.species.photoCredit} {extra.author ?? t.species.unknownAuthor}</span></figcaption>
          </figure>
        )}
        {rel.items.length > 0 && (
          <section class="more">
            <h2>{moreHeading}</h2>
            <ul class="sp-cards" role="list">
              {rel.items.map((x) => <li><SpeciesCard species={x} locale={locale} /></li>)}
            </ul>
          </section>
        )}
        <div class="credits-wrap"><Credits species={s} locale={locale} images={shown} /></div>
      </div>
    </div>
  </main>
  <Footer locale={locale} switchLangHref={otherPath} />
</Layout>

<style>
  .sp { padding-top: 26px; padding-bottom: 88px; }
  .spread { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); grid-template-rows: auto 1fr; grid-template-areas: 'left head' 'left body'; }
  .head { grid-area: head; padding-left: 36px; }
  .left { grid-area: left; padding-right: 28px; border-right: 1px dashed var(--line); }
  .left-inner { position: sticky; top: 150px; display: flex; flex-direction: column; gap: 18px; }
  .body { grid-area: body; padding-left: 36px; }
  h1 { font-size: clamp(40px, 4.6vw, 58px); line-height: 1.02; margin: 4px 0 0; overflow-wrap: anywhere; }
  .latin { margin: 2px 0 0; font-family: var(--font-script); font-size: 24px; color: var(--muted); }
  .plate { margin: 0; background: var(--card); border: 1px solid var(--line); padding: 9px 9px 5px; box-shadow: 0 2px 0 var(--line); }
  .plate :global(img) { display: block; width: 100%; height: auto; }
  .plate figcaption { display: flex; justify-content: space-between; gap: 12px; padding-top: 4px; font-family: var(--font-script); font-size: 17px; color: var(--muted); }
  .facts { margin: 0; }
  .facts div { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
  .facts dt { color: var(--muted); }
  .facts dd { margin: 0; font-weight: 600; text-align: right; }
  .note { margin: 4px 0 0; font-family: var(--font-script); font-size: 22px; line-height: 1.2; color: var(--rust); transform: rotate(-2deg); }
  .sp-lead { font-size: 17px; line-height: 1.6; margin: 18px 0 0; max-width: 40rem; }
  .texts h2, .more h2 { font-size: 24px; margin: 30px 0 8px; }
  .texts p, .marks { font-size: 15.5px; line-height: 1.65; max-width: 40rem; margin: 0; }
  .marks { padding-left: 20px; }
  .marks li { margin: 3px 0; }
  .plate.extra { margin-top: 30px; max-width: 640px; }
  @media (max-width: 1023px) {
    .spread { display: flex; flex-direction: column; gap: 18px; }
    .left, .left-inner, .body { display: contents; }
    .head { order: 1; padding-left: 0; }
    .plate.hero { order: 2; }
    .facts { order: 3; }
    .sp-lead { order: 4; margin-top: 0; }
    .texts { order: 5; }
    .plate.extra { order: 6; margin-top: 0; }
    .sp-app { order: 7; }
    .note { order: 8; }
    .more { order: 9; }
    .credits-wrap { order: 10; }
    .texts h2, .more h2 { margin-top: 18px; }
  }
</style>
```

- [ ] **Step 5: Routes med arter**

Ersätt `src/pages/sv/arter/[slug].astro` med:

```astro
---
import SpeciesArticle from '../../../components/species/SpeciesArticle.astro';
import GroupPage from '../../../components/species/GroupPage.astro';
import { GROUPS, assertUniqueSlugs, getAllSpecies, type Group, type Species } from '../../../lib/species';

export async function getStaticPaths() {
  const all = await getAllSpecies();
  assertUniqueSlugs([...all.map((s) => s.slug.sv), ...GROUPS.map((g) => g.slug.sv)], 'sv');
  return [
    ...all.map((species) => ({ params: { slug: species.slug.sv }, props: { species } })),
    ...GROUPS.map((group) => ({ params: { slug: group.slug.sv }, props: { group } })),
  ];
}

interface Props { species?: Species; group?: Group }
const { species, group } = Astro.props;
---
{species && <SpeciesArticle species={species} locale="sv" />}
{group && <GroupPage group={group} locale="sv" />}
```

Ersätt `src/pages/species/[slug].astro` med samma innehåll, men med importvägarna `../../components/...` och `../../lib/species`, `slug.en` på alla fyra ställen, `'en'` i `assertUniqueSlugs` och `locale="en"` i de två komponenterna.

- [ ] **Step 6: Kör testerna**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (alla). Om "Arter" inte är markerad i menyn failar det testet tills Task 8. Kör i så fall `-g "artsidan"` utan den raden och gå vidare, testet går igenom efter Task 8.

- [ ] **Step 7: Commit**

```bash
git add src/components/species/Credits.astro src/components/species/SpeciesArticle.astro src/pages/sv/arter/[slug].astro src/pages/species/[slug].astro tests/species.spec.ts
git commit -m "feat(website): artsidan i layout B med credits, relaterade arter och JSON-LD"
```

---

### Task 8: Menyn, sidfoten och startsidans länk

**Files:**
- Modify: `website/src/components/Nav.astro`
- Modify: `website/src/components/Footer.astro`
- Modify: `website/src/components/Guide.astro`
- Modify: `website/tests/home.spec.ts`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Uppdatera de befintliga testerna**

I `tests/home.spec.ts`:
- I `for (const [path, label, getApp] of [['/sv/', 'Så funkar det', 'Hämta appen'], ['/', 'How it works', 'Get the app']] as const)`: byt `'Så funkar det'` mot `'Arter'` och `'How it works'` mot `'Species'`.
- I testet `sidfoten har kolumnerna`: byt `['Utforska', 'Läs', 'Information']` mot `['Arter', 'Utforska', 'Läs', 'Information']`.

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('meny och sidfot för arterna', () => {
  for (const path of ['/sv/', '/sv/blog/', '/sv/arter/talgoxe/']) {
    test(`sidfoten på ${path} har Arter och tolv vanliga arter`, async ({ page, request }) => {
      await page.goto(path);
      const footer = page.locator('footer.footer');
      await expect(footer.locator('.fh').first()).toHaveText('Arter');
      const common = footer.locator('.fpop a');
      await expect(common).toHaveCount(12);
      for (const href of await common.evaluateAll((els) => els.map((e) => e.getAttribute('href')!))) {
        expect((await request.get(href)).status(), href).toBe(200);
      }
    });
  }

  test('startsidans uppslagsverk länkar till arterna', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('#guide a[href="/sv/arter/"]')).toBeVisible();
  });

  test('mobilmenyn har Arter först', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await page.locator('#site-nav .menu-toggle').click();
    await expect(page.locator('#mobile-menu a').first()).toHaveText('Arter');
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test tests/home.spec.ts tests/species.spec.ts -g "meny|sidfot"`
Expected: FAIL (ingen länk "Arter" än)

- [ ] **Step 3: Menyn**

I `src/components/Nav.astro`, lägg till importen `import { hubHref } from '../lib/species';` och ersätt `const links = [ ... ];` med:

```ts
const speciesHub = hubHref(locale);
const links = [
  { href: speciesHub, label: t.nav.species },
  { href: `${home}#how-it-works`, label: t.nav.howItWorks },
  { href: `${home}#app`, label: t.nav.app },
  { href: `${home}#premium`, label: t.nav.premium },
  { href: fieldNotesHref(locale), label: t.nav.fieldNotes },
];
// "Species" is current on the hub, group pages and species pages.
const isCurrent = (href: string) => href === here || (href === speciesHub && here.startsWith(speciesHub));
```

Byt båda förekomsterna av `aria-current={l.href === here ? 'page' : undefined}` mot `aria-current={isCurrent(l.href) ? 'page' : undefined}`.

Kontrollera i 1024 px att menyraden fortfarande får plats (sex länkar plus knappen). Om den bryter: sänk `gap` i `.links` från `26px` till `20px` i `@media (max-width: 1180px)`.

- [ ] **Step 4: Sidfoten**

I `src/components/Footer.astro`, lägg till importen `import { commonSpecies, getAllSpecies, groupHref, hubHref, largestGroups, speciesHref } from '../lib/species';` och efter `const blogPrefix = ...`:

```ts
const allSpecies = await getAllSpecies();
const topGroups = largestGroups(allSpecies, 5);
const common = commonSpecies(allSpecies);
```

Lägg till en ny kolumn före `<div class="col">` med `t.footer.explore`:

```astro
      <div class="col">
        <h2 class="fh">{t.footer.species}</h2>
        {topGroups.map((g) => <a href={groupHref(g, locale)}>{g.name[locale]}</a>)}
        <a href={hubHref(locale)}>{t.footer.allSpecies}</a>
      </div>
```

Lägg till före `<div class="fbot">`:

```astro
    <p class="fpop"><span class="fpop-h">{t.footer.commonSpecies}</span>{common.map((s) => <a href={speciesHref(s, locale)}>{s.names[locale]}</a>)}</p>
```

I `<style>`: ändra `.fgrid { ... grid-template-columns: 1.6fr 1fr 1fr 1fr; ... }` till `grid-template-columns: 1.6fr repeat(4, 1fr);` och lägg till:

```css
  .fpop { margin: 40px 0 0; display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 16px; font-size: 13.5px; }
  .fpop-h { font-size: 11px; letter-spacing: .16em; text-transform: uppercase; color: var(--apricot); font-weight: 600; margin-right: 4px; }
  /* same colour as .col a (alpha checked in scripts/check-contrast.mjs) */
  .fpop a { color: rgba(233, 226, 210, .82); text-decoration: underline; text-decoration-color: rgba(233, 226, 210, .3); text-underline-offset: 3px; transition: color .2s; }
  .fpop a:hover { color: var(--apricot); }
```

- [ ] **Step 5: Startsidans länk**

I `src/components/Guide.astro`, lägg till importen `import { hubHref } from '../lib/species';` och efter `<CoverageMap locale={locale} />`:

```astro
    <p class="browse"><a href={hubHref(locale)}>{t.guide.browse} <span aria-hidden="true">→</span></a></p>
```

Och i komponentens `<style>`:

```css
  .browse { margin: 28px 0 0; font-weight: 600; }
  .browse a { color: var(--rust); border-bottom: 1px solid currentColor; padding-bottom: 2px; }
```

- [ ] **Step 6: Kör alla webbtester**

Run: `npm run build && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS (hela sviten, även de gamla testerna och artsidans menytest från Task 7)

- [ ] **Step 7: Commit**

```bash
git add src/components/Nav.astro src/components/Footer.astro src/components/Guide.astro tests/home.spec.ts tests/species.spec.ts
git commit -m "feat(website): Arter i menyn, sidfoten och startsidans uppslagsverk"
```

---

### Task 9: Sitemap och SEO-reglerna som kod

**Files:**
- Create: `website/src/lib/species-sitemap.mjs`
- Modify: `website/astro.config.mjs`
- Create: `website/scripts/check-seo.mjs`
- Modify: `website/scripts/check-no-dashes.mjs`
- Modify: `website/package.json`

- [ ] **Step 1: Sitemap-data**

`src/lib/species-sitemap.mjs`:

```js
// Species data for astro.config.mjs (sitemap lastmod and noindex), in plain JS so the config can load it.
// The noindex rule must match MIN_GROUP_SIZE in src/lib/species.ts; scripts/check-seo.mjs fails if they drift.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const MIN_GROUP_SIZE = 3;
const BASES = [['sv', '/sv/arter/'], ['en', '/species/']];

/** @param {string} root the website folder */
export function readSpeciesSitemapInfo(root) {
  const dir = resolve(root, 'src/data/species');
  const groups = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).groups;
  const records = existsSync(dir)
    ? readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => JSON.parse(readFileSync(resolve(dir, f), 'utf8'))).filter((r) => r.status === 'ok')
    : [];
  /** @type {Map<string, string>} */
  const lastmod = new Map();
  /** @type {Map<string, number>} */
  const sizes = new Map();
  let newest = '';
  for (const r of records) {
    for (const [lang, base] of BASES) lastmod.set(`${base}${r.slug[lang]}/`, r.generated.at);
    sizes.set(r.group, (sizes.get(r.group) ?? 0) + 1);
    if (r.generated.at > newest) newest = r.generated.at;
  }
  /** @type {Set<string>} */
  const noindex = new Set();
  for (const g of groups) {
    const small = (sizes.get(g.key) ?? 0) < MIN_GROUP_SIZE;
    for (const [lang, base] of BASES) {
      const path = `${base}${g.slug[lang]}/`;
      if (small) noindex.add(path);
      else if (newest) lastmod.set(path, newest);
    }
  }
  if (newest) for (const [, base] of BASES) lastmod.set(base, newest);
  return { lastmod, noindex };
}
```

- [ ] **Step 2: Koppla in i `astro.config.mjs`**

Lägg till importen `import { readSpeciesSitemapInfo } from './src/lib/species-sitemap.mjs';` och efter `for`-loopen som fyller `noteDates`:

```js
// Species pages: lastmod from each species' generated date, and small group pages left out (spec §10).
const speciesInfo = readSpeciesSitemapInfo(root);
```

Ersätt `integrations: [sitemap({ ... })],` med:

```js
  integrations: [sitemap({
    filter: (page) => !speciesInfo.noindex.has(new URL(page).pathname),
    serialize(item) {
      const path = new URL(item.url).pathname;
      const d = noteDates.get(path) ?? speciesInfo.lastmod.get(path);
      if (d) item.lastmod = new Date(d).toISOString();
      return item;
    },
  })],
```

- [ ] **Step 3: SEO-skriptet**

`scripts/check-seo.mjs`:

```js
#!/usr/bin/env node
// SEO rules as code (spec 2026-09-25 §10). Runs on the built site in dist/ and lists every failure.
// New pages (/species/, /sv/arter/) get the full list; every page gets one h1, alt on images and no dead links.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist');
const SITE = 'https://birdy.community';
const NEW = ['/species/', '/sv/arter/'];

if (!existsSync(dist)) {
  console.error('check-seo: dist/ saknas, kör npm run build först');
  process.exit(1);
}

const decode = (s) => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;|&#x27;/g, "'");
const attr = (tag, name) => { const m = tag.match(new RegExp(`\\s${name}="([^"]*)"`)); return m ? decode(m[1]) : null; };
const text = (html) => decode(html.replace(/<[^>]+>/g, '')).replace(/\s+/g, ' ').trim();

const files = readdirSync(dist, { recursive: true, encoding: 'utf8' }).filter((f) => f.endsWith('.html')).map((f) => join(dist, f));
const pathOf = (file) => {
  const rel = relative(dist, file).split(sep).join('/');
  if (rel === 'index.html') return '/';
  return rel.endsWith('/index.html') ? `/${rel.slice(0, -'index.html'.length)}` : `/${rel}`;
};
const pages = files.map((file) => ({ path: pathOf(file), html: readFileSync(file, 'utf8') }));
const byPath = new Map(pages.map((p) => [p.path, p]));

const sitemap = new Set();
for (const f of readdirSync(dist).filter((f) => /^sitemap-\d+\.xml$/.test(f))) {
  for (const m of readFileSync(join(dist, f), 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)) sitemap.add(new URL(m[1]).pathname);
}

const errors = [];
const fail = (path, msg) => errors.push(`${path}: ${msg}`);
const seenTitles = new Map();
const seenDescs = new Map();

const internalTarget = (href) => {
  if (!href.startsWith('/') || href.startsWith('//')) return null;
  return href.split('#')[0].split('?')[0] || '/';
};
const exists = (target) => byPath.has(target) || byPath.has(`${target}/`) || existsSync(join(dist, target));

for (const { path, html } of pages) {
  const isNew = NEW.some((p) => path.startsWith(p));

  const h1 = (html.match(/<h1[\s>]/g) ?? []).length;
  if (h1 !== 1) fail(path, `ska ha exakt en h1 (har ${h1})`);
  for (const tag of html.match(/<img\b[^>]*>/g) ?? []) {
    if (attr(tag, 'alt') === null) fail(path, `bild utan alt: ${tag.slice(0, 90)}`);
    if (isNew && (!attr(tag, 'width') || !attr(tag, 'height'))) fail(path, `bild utan width/height: ${tag.slice(0, 90)}`);
  }
  for (const tag of html.match(/<a\b[^>]*>/g) ?? []) {
    const target = internalTarget(attr(tag, 'href') ?? '');
    if (target && !exists(target)) fail(path, `död länk till ${attr(tag, 'href')}`);
  }
  if (!isNew) continue;

  const title = text(html.match(/<title>([\s\S]*?)<\/title>/)?.[1] ?? '');
  if (title.length < 40 || title.length > 60) fail(path, `titeln är ${title.length} tecken: ${title}`);
  if (seenTitles.has(title)) fail(path, `samma titel som ${seenTitles.get(title)}`);
  seenTitles.set(title, path);

  const desc = attr(html.match(/<meta name="description"[^>]*>/)?.[0] ?? '', 'content') ?? '';
  if (desc.length < 120 || desc.length > 155) fail(path, `description är ${desc.length} tecken`);
  if (seenDescs.has(desc)) fail(path, `samma description som ${seenDescs.get(desc)}`);
  seenDescs.set(desc, path);

  const levels = [...html.matchAll(/<h([1-6])[\s>]/g)].map((m) => Number(m[1]));
  for (let i = 1; i < levels.length; i += 1) {
    if (levels[i] > levels[i - 1] + 1) fail(path, `rubriknivån hoppar från h${levels[i - 1]} till h${levels[i]}`);
  }

  const canonical = attr(html.match(/<link rel="canonical"[^>]*>/)?.[0] ?? '', 'href');
  if (canonical !== SITE + path) fail(path, `canonical är ${canonical}`);
  const alternates = Object.fromEntries([...html.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/g)].map((m) => [m[1], m[2]]));
  const lang = path.startsWith('/sv/') ? 'sv' : 'en';
  const other = lang === 'sv' ? 'en' : 'sv';
  if (alternates[lang] !== SITE + path) fail(path, `hreflang ${lang} pekar inte på sidan själv`);
  const otherPath = alternates[other] ? new URL(alternates[other]).pathname : null;
  const otherPage = otherPath ? byPath.get(otherPath) : undefined;
  if (!otherPage) fail(path, `hreflang ${other} pekar på en sida som inte finns: ${alternates[other]}`);
  else if (!otherPage.html.includes(`hreflang="${lang}" href="${SITE + path}"`)) fail(path, `${otherPath} pekar inte tillbaka med hreflang ${lang}`);
  const english = lang === 'en' ? SITE + path : alternates.en;
  if (alternates['x-default'] !== english) fail(path, 'x-default ska peka på den engelska sidan');

  const noindex = /<meta name="robots" content="noindex/.test(html);
  if (!noindex && !sitemap.has(path)) fail(path, 'saknas i sitemapen');
  if (noindex && sitemap.has(path)) fail(path, 'har noindex men finns i sitemapen');

  const graph = [];
  for (const m of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
    try {
      const data = JSON.parse(m[1]);
      graph.push(...(data['@graph'] ?? [data]));
    } catch {
      fail(path, 'JSON-LD går inte att tolka');
    }
  }
  const crumbs = [...html.matchAll(/data-crumb[^>]*>([^<]*)</g)].map((m) => text(m[1]));
  const breadcrumb = graph.find((n) => n['@type'] === 'BreadcrumbList');
  if (!breadcrumb) fail(path, 'BreadcrumbList saknas');
  else if (JSON.stringify(breadcrumb.itemListElement.map((i) => i.name)) !== JSON.stringify(crumbs)) {
    fail(path, `BreadcrumbList (${breadcrumb.itemListElement.map((i) => i.name).join(' › ')}) matchar inte brödsmulorna (${crumbs.join(' › ')})`);
  }
  const list = graph.find((n) => n['@type'] === 'CollectionPage')?.mainEntity;
  if (list) {
    const shown = (html.match(/data-item[\s>=]/g) ?? []).length;
    if (list.numberOfItems !== shown || list.itemListElement.length !== shown) fail(path, `ItemList har ${list.numberOfItems} poster men sidan visar ${shown}`);
  }

  if (html.includes('data-species-page')) {
    const photos = new Set([...html.matchAll(/data-photo="([^"]+)"/g)].map((m) => m[1]));
    const credits = new Set([...html.matchAll(/data-credit-for="([^"]+)"/g)].map((m) => m[1]));
    for (const photo of photos) if (!credits.has(photo)) fail(path, `fotot ${photo} saknar creditrad`);
    if (!html.includes('data-wiki-credit')) fail(path, 'Wikipediaraden saknas');
  }
}

if (errors.length) {
  console.error(`check-seo FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(`check-seo OK (${pages.length} sidor, ${pages.filter((p) => NEW.some((n) => p.path.startsWith(n))).length} artsidor, ${sitemap.size} adresser i sitemapen)`);
```

- [ ] **Step 4: Streckvakten täcker artdatan**

I `scripts/check-no-dashes.mjs`, lägg till efter loopen över `deckFiles` (före loopen över `noteFiles`):

```js
// Species data (spec 2026-09-25): rendered text only. Quotes are Wikipedia's own words and are not shown.
const SKIP = new Set(['quote', 'sourceUrl', 'licenseUrl', 'file', 'revision', 'title', 'model', 'prompt', 'at', 'errors', 'qid', 'slug']);
const walkRendered = (value, path, cb) => {
  if (typeof value === 'string') cb(value, path);
  else if (Array.isArray(value)) value.forEach((v, i) => walkRendered(v, `${path}[${i}]`, cb));
  else if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) if (!SKIP.has(key)) walkRendered(value[key], path ? `${path}.${key}` : key, cb);
  }
};
const speciesDir = resolve(root, 'src/data/species');
const dataFiles = [
  'src/data/species-groups.json',
  ...(existsSync(speciesDir) ? readdirSync(speciesDir).filter((f) => f.endsWith('.json')).map((f) => join('src/data/species', f)) : []),
];
for (const file of dataFiles) {
  walkRendered(JSON.parse(readFileSync(resolve(root, file), 'utf8')), '', (text, path) => {
    if (emDashRe.test(text)) fail(`${file}:${path}`, `tankstreck (${EM_DASH})`);
    if (spacedEnDashRe.test(text)) fail(`${file}:${path}`, `tankstreck ( ${EN_DASH} )`);
  });
}
files.push(...dataFiles);
```

och ändra importraden högst upp till `import { existsSync, readFileSync, readdirSync } from 'node:fs';`. (Variabeln `files` används bara i slutraden `no-dashes OK (${files.length} filer)`.) Ändra `const files = [...]` till `let` om den är `const` och `push` inte godtas. En `const`-array går att pusha till, så normalt behövs ingen ändring.

- [ ] **Step 5: Skripten i `package.json`**

Lägg till i `"scripts"`:

```json
    "test:seo": "node scripts/check-seo.mjs",
    "verify": "npm run build && npm run test:seo && npm run test:i18n && npm run test:no-dashes && npm run test:contrast",
```

- [ ] **Step 6: Kör**

Run: `npm run verify`
Expected: `check-seo OK (... sidor, ... artsidor, ... adresser i sitemapen)` och de andra vakterna gröna.
Failar regel 9 eller 10 (en h1, alt, döda länkar) på en **befintlig** sida är det ett riktigt SEO-fel: rätta sidan i samma task och skriv i commit-meddelandet vilken sida det gällde. Failar en regel på de nya sidorna: rätta komponenten, inte skriptet.

- [ ] **Step 7: Kör hela Playwright-sviten igen**

Run: `PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add src/lib/species-sitemap.mjs astro.config.mjs scripts/check-seo.mjs scripts/check-no-dashes.mjs package.json
git commit -m "feat(website): sitemap för artsidorna och SEO-reglerna som kod (npm run verify)"
```

---

### Task 10: Full QA

- [ ] **Step 1: Alla vakter och tester**

Run: `npm run verify && npx astro check && npm run test:no-accuracy && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: allt grönt (utom det kända typfelet i `astro.config.mjs`).

- [ ] **Step 2: Skärmdumpar**

Starta `npm run preview -- --port 4327` i bakgrunden och ta skärmdumpar till `docs/superpowers/screenshots/artsidor/` (från repots rot, mappen skapas först):

```bash
mkdir -p ../docs/superpowers/screenshots/artsidor
for page in "sv/arter/" "sv/arter/ugglor/" "sv/arter/talgoxe/" "species/" "species/owls/" "species/great-tit/"; do
  name=$(echo "$page" | tr '/' '-' | sed 's/-$//')
  npx playwright screenshot --viewport-size=390,844 --full-page "http://localhost:4327/$page" "../docs/superpowers/screenshots/artsidor/$name-390.png"
  npx playwright screenshot --viewport-size=1440,900 --full-page "http://localhost:4327/$page" "../docs/superpowers/screenshots/artsidor/$name-1440.png"
done
```

Lägg till en art med marginalanteckning (talgoxen har en) och en art utan extrafoto: hitta en med `grep -L '"role": "extra"' src/data/species/*.json | head -1`, läs dess `slug.sv` och ta samma två skärmdumpar.

- [ ] **Step 3: Jämför med mockupen**

Öppna `docs/superpowers/specs/assets/2026-09-25-artsidor/helheten.html` bredvid skärmdumparna. Kontrollera:
- menyraden med Arter först
- kategoriraden med aktiv chip
- vänsterspalten (foto, fakta, approta, marginalanteckning)
- högerspalten med texterna i rätt ordning
- sidfotens kolumn Arter och raden Vanliga arter
- mobilordningen

Avvikelser som inte är listade under "Avvikelser från specen" rättas.

- [ ] **Step 4: Lighthouse**

```bash
npx lighthouse http://localhost:4327/sv/arter/talgoxe/ --form-factor=mobile --screenEmulation.mobile --only-categories=performance,accessibility,best-practices,seo --output=json --output=html --output-path=../docs/superpowers/screenshots/artsidor/lighthouse-talgoxe --chrome-flags="--headless=new"
npx lighthouse http://localhost:4327/sv/arter/ --form-factor=mobile --screenEmulation.mobile --only-categories=performance,accessibility,best-practices,seo --output=json --output=html --output-path=../docs/superpowers/screenshots/artsidor/lighthouse-arter --chrome-flags="--headless=new"
```

Mål: 90 eller mer i alla fyra kategorierna. Ligger Performance under 90: kontrollera att huvudfotot har `fetchpriority="high"` och att `sizes` stämmer, och sänk `widths` i artkorten. Resultaten sparas som underlag till AlbIT-caset.

- [ ] **Step 5: Commit**

```bash
git add ../docs/superpowers/screenshots/artsidor
git commit -m "docs: skärmdumpar och Lighthouse för artsidorna"
```

---

### Task 11: Förhandsvisning, baslinje och go-live

- [ ] **Step 1: Pusha grenen och hämta förhandsvisningen**

```bash
git push -u origin website/artsidor
```

Vercel bygger en förhandsvisning för grenen. Hämta länken (`npx vercel ls` eller Vercels kommentar på grenen) och kontrollera att `/sv/arter/talgoxe/` och `/species/` svarar 200 där.

- [ ] **Step 2: Albin godkänner förhandsvisningen (manuell grind)**

Skicka länken till Albin med tre adresser att titta på: `/sv/arter/`, `/sv/arter/talgoxe/` och `/sv/arter/ugglor/`. Vänta på hans ok. Rätta det han hittar på grenen.

- [ ] **Step 3: Baslinjen i Search Console (Albin, före sammanslagningen)**

Albin exporterar Search Console för birdy.community, de senaste 3 månaderna: klick, visningar och antal indexerade sidor. Skriv in siffrorna med dagens datum i `docs/superpowers/research/<datum>-artsidor-baslinje.md` (spec avsnitt 12) och committa filen på grenen.

- [ ] **Step 4: Slå ihop**

```bash
git fetch origin && git merge origin/main
npm ci && npm run verify && PLAYWRIGHT_PORT=4327 npx playwright test
git switch main && git pull && git merge --ff-only website/artsidor && git push
```

(Kör kommandona i huvudmappen för `main` om worktreen inte kan byta gren. Går `--ff-only` inte: ta in `main` i grenen igen, kör om verifieringen och försök på nytt.)

- [ ] **Step 5: Kontrollera live**

När Vercel har byggt produktion:

```bash
for p in /sv/arter/ /species/ /sv/arter/talgoxe/ /species/great-tit/ /sv/arter/ugglor/ /sitemap-0.xml; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' https://birdy.community$p)"
done
curl -s https://birdy.community/sitemap-0.xml | grep -c '/arter/'
```

Expected: 200 överallt, och sitemapen innehåller artsidorna (ungefär antalet `ok`-arter plus indexerade grupper plus ingångssidan).

- [ ] **Step 6: Search Console efter go-live (Albin eller agenten via Chrome)**

Skicka in `https://birdy.community/sitemap-index.xml` igen och begär indexering av `/sv/arter/`, `/species/` och de tolv vanliga arternas svenska sidor.

- [ ] **Step 7: Synka status**

Uppdatera 🔎-posten i CLAUDE.md:
- artsidorna live med datum
- antal sidor
- Lighthouse-resultaten
- baslinjefilen
- triggrarna: kontroll efter 6 veckor (datum) och 12 veckor (datum) enligt spec avsnitt 12

Committa och pusha. Ta bort worktreen: `git worktree remove C:/w/birdy-artsidor`.
