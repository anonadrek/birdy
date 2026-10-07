import { defineCollection } from 'astro:content';
import { z } from 'astro/zod';
import { glob } from 'astro/loaders';
import { comparisonsDir, speciesDir } from './lib/species-source.mjs';

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
  }),
});

// Species pages (spec 2026-09-25 appendix C and D). The pipeline writes src/data/species/*.json and
// src/data/comparisons/*.json; SPECIES_FIXTURES=1 reads the test data in tests/fixtures/ instead.
// Not .strict(): the files carry more than the pages read (raw counts, sources, generation details).
//
// The entry id is the file name (Q25485, Q25404_Q25485). The glob loader's default id is the data's
// `slug` field when it has one, and here `slug` is an object ({ sv, en }): every file would get the
// id "[object Object]" and all but one entry would be lost.
const idFromFileName = ({ entry }: { entry: string }) => entry.replace(/\.json$/, '');

const qid = z.string().regex(/^Q\d+$/);
const localized = z.object({ sv: z.string().min(1), en: z.string().min(1) });
const sentence = z.object({ text: z.string().min(1), factIds: z.array(z.string().min(1)).min(1) });
const sentences = z.array(sentence).min(1);
const statuses = ['resident', 'breeding_migrant', 'passage', 'winter_visitor', 'rare_visitor', 'absent'] as const;
const recordStatus = z.enum(['pending', 'ok', 'failed']);
const licensed = {
  author: z.string().nullable(),
  license: z.string().min(1),
  licenseUrl: z.url().nullable(),
  sourceUrl: z.url(),
};
const wikiRef = z.object({ title: z.string().min(1), revision: z.string().min(1) });
const langText = z.object({
  lead: sentences,
  fieldMarks: z.array(sentence).min(3).max(4),
  voice: sentences,
  whereWhen: sentences,
  // The pipeline always writes the list, empty when nothing about behaviour passed the checks.
  behaviour: z.array(sentence).optional(),
  lookAlikes: z.array(z.object({ other: z.string().min(1), text: sentences })).max(3).default([]),
  metaDescription: z.string().min(120).max(155),
  facts: z.object({
    size: z.object({ value: z.string().min(1), factIds: z.array(z.string()) }).nullable(),
    swedenStatus: z.object({ value: z.enum(statuses), factIds: z.array(z.string()) }).nullable(),
  }),
});

const species = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${speciesDir()}`, generateId: idFromFileName }),
  schema: z
    .object({
      qid,
      status: recordStatus,
      publish: z.boolean(),
      slug: localized,
      names: z.object({ sv: z.string().min(1), en: z.string().min(1), scientific: z.string().min(1) }),
      family: z.object({ latin: z.string().min(1), sv: z.string().min(1) }),
      group: z.string().min(1),
      iucn: z.string(),
      swedishRedList: z.enum(['RE', 'CR', 'EN', 'VU', 'NT', 'DD', 'not_listed']).optional(),
      identifiable: z.object({ photo: z.boolean(), sound: z.boolean() }).optional(),
      marginalia: localized.optional(),
      images: z.array(z.object({
        role: z.enum(['hero', 'extra']),
        file: z.string().regex(/^Q\d+\/(hero|extra)\.webp$/),
        width: z.number().int().positive(),
        height: z.number().int().positive(),
        ...licensed,
      })).default([]),
      audio: z.object({
        file: z.string().regex(/^Q\d+\/voice\.mp3$/),
        durationSec: z.number().positive(),
        trimmed: z.boolean(),
        ...licensed,
      }).optional(),
      // Any of the three can be missing: the pipeline keeps whichever articles exist (a species with
      // only a Swedish article is possible), so the schema does not require `en` either.
      wikipedia: z.object({ sv: wikiRef.optional(), en: wikiRef.optional(), de: wikiRef.optional() }).default({}),
      data: z.object({
        totalReports: z.number().int().nonnegative(),
        months: z.array(z.number().int().min(0).max(100)).length(12).optional(),
        counties: z.record(z.string().regex(/^SE-[A-Z]{1,2}$/), z.number().int().min(0).max(100)).optional(),
        sentences: z.object({ sv: z.array(z.string()), en: z.array(z.string()) }),
      }).optional(),
      // Only to name a look-alike that has no species file (deviation 7 in the plan).
      facts: z.array(z.object({
        id: z.string(),
        topic: z.string(),
        other: z.object({ scientific: z.string().min(1), qid: qid.optional() }).optional(),
      })).default([]),
      review: z.object({
        wave: z.number().int().min(1).optional(),
      }),
      // Revision 2026-10-05: replaces the old review.facts. Only `at` is read by the pages
      // (the credit line and lastReviewed); method, model and spotChecked exist so the
      // pipeline's own records are self-explaining, the site never branches on them.
      verification: z.object({
        method: z.literal('auto'),
        at: z.string().regex(/^\d{4}-\d{2}-\d{2}/),
        model: z.string().min(1),
        spotChecked: z.boolean(),
      }).optional(),
      text: z.object({ sv: langText, en: langText }).nullable(),
      generated: z.object({ text: z.object({ at: z.string() }).optional() }).optional(),
    })
    .superRefine((d, ctx) => {
      if (d.status === 'ok') {
        if (!d.text) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver text` });
        if (!d.images.some((i) => i.role === 'hero')) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver huvudfoto` });
        if (!d.identifiable) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver identifiable` });
      } else if (d.text) {
        ctx.addIssue({ code: 'custom', message: `${d.qid}: status ${d.status} ska ha text: null` });
      }
      if (d.publish && (d.status !== 'ok' || !d.verification)) {
        ctx.addIssue({ code: 'custom', message: `${d.qid}: publish kräver status ok och kontrollerade fakta` });
      }
    }),
});

const compareText = z.object({
  shortAnswer: sentences,
  rows: z.array(z.object({ feature: z.string().min(1), a: sentence, b: sentence })).min(3).max(5),
  metaDescription: z.string().min(120).max(155),
});

const comparisons = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${comparisonsDir()}`, generateId: idFromFileName }),
  schema: z
    .object({
      a: qid,
      b: qid,
      status: recordStatus,
      publish: z.boolean(),
      slug: localized,
      text: z.object({ sv: compareText, en: compareText }).nullable(),
      generated: z.object({ at: z.string() }).optional(),
    })
    .superRefine((d, ctx) => {
      if (d.status === 'ok' && !d.text) ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: status ok kräver text` });
      if (d.status !== 'ok' && d.text) ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: status ${d.status} ska ha text: null` });
      if (d.publish && d.status !== 'ok') ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: publish kräver status ok` });
    }),
});

export const collections = { fieldNotes, species, comparisons };
