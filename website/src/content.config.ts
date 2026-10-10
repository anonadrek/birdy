import { defineCollection } from 'astro:content';
import { z } from 'astro/zod';
import { glob } from 'astro/loaders';
import { comparisonsDir, speciesDir } from './lib/species-source.mjs';
import { comparisonRecord, speciesRecord } from './lib/species-schema.mjs';

const fieldNotes = defineCollection({
  loader: glob({
    pattern: '**/*.md',
    base: './src/content/field-notes',
    generateId: ({ entry }) => entry.replace(/\.md$/, '').replace(/\\/g, '/'),
  }),
  schema: ({ image }) => z.object({
    locale: z.enum(['en', 'sv']),
    slug: z.string(),
    title: z.string(),
    description: z.string(),
    date: z.coerce.date(),
    category: z.string(),
    // Every note has its own photo (spec §6): header, cards and share image. The build fails without it.
    // It must be at least 1200×630 because the same file is cropped to exactly that for the share image.
    // That minimum is enforced in astro.config.mjs, not here with image().refine(): this collection's
    // `glob()` loader only ever hands refine() the unresolved "__ASTRO_IMAGE_<path>" placeholder string
    // (verified empirically against astro@5.18.2), never the real ImageMetadata with width/height — the
    // real file is resolved later, outside of schema validation. astro.config.mjs already walks every
    // note's frontmatter for the sitemap and has a real file path to probe with sharp.
    image: image(),
    imageAlt: z.string().trim().min(1),
    imageCaption: z.string().optional(),
    imagePosition: z.string().optional(),
    // When the note was last rewritten (2026-10-10, "Varför Birdy finns"): shown next to the date, used as the
    // sitemap's lastmod and the article's modified time.
    updated: z.coerce.date().optional(),
    // A note told with AlbIT (the company behind Birdy): the hero where the two brands meet instead of the photo
    // (CollabHero.astro). The photo is still the share image and the card image.
    collab: z.enum(['albit']).optional(),
  }),
});

// Species pages (spec 2026-09-25 appendix C and D). The pipeline writes src/data/species/*.json and
// src/data/comparisons/*.json; SPECIES_FIXTURES=1 reads the test data in tests/fixtures/ instead. The
// schemas live in src/lib/species-schema.mjs (plain JS, shared with the unit tests): the full page
// contract for records the site may build, an envelope for every other record.
//
// The entry id is the file name (Q25485, Q25404_Q25485). The glob loader's default id is the data's
// `slug` field when it has one, and here `slug` is an object ({ sv, en }): every file would get the
// id "[object Object]" and all but one entry would be lost.
const idFromFileName = ({ entry }: { entry: string }) => entry.replace(/\.json$/, '');

const species = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${speciesDir()}`, generateId: idFromFileName }),
  schema: speciesRecord,
});

const comparisons = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${comparisonsDir()}`, generateId: idFromFileName }),
  schema: comparisonRecord,
});

export const collections = { fieldNotes, species, comparisons };
