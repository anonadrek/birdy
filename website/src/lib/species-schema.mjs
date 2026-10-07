// The data contract between the pipeline and the species pages (spec 2026-09-25 appendix C and D),
// as zod schemas. Plain JS so src/content.config.ts and the unit tests (node --test) share one definition.
// Not .strict(): the files carry more than the pages read (raw counts, sources, generation details).
//
// Only a species record the site may build (written and verified, `hasPageContract`) is held to the full
// page contract, whether it is published or not (a preview build shows it). Every other record (pending,
// failed, or written but not verified) is checked as an envelope: what the pages need to name and link it
// (qid, status, slug, names, family, group, review, verification), and its page-only fields are dropped.
// A broken field in a record that never gets a page must not stop the build of every other page.
// Publishing such a record is still an error.
import { z } from 'astro/zod';
import { hasPageContract } from './species-source.mjs';

const qid = z.string().regex(/^Q\d+$/);
const localized = z.object({ sv: z.string().min(1), en: z.string().min(1) });
const sentence = z.object({ text: z.string().min(1), factIds: z.array(z.string().min(1)).min(1) });
const sentences = z.array(sentence).min(1);
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
    swedenStatus: z.object({
      value: z.enum(['resident', 'breeding_migrant', 'passage', 'winter_visitor', 'rare_visitor', 'absent']),
      factIds: z.array(z.string()),
    }).nullable(),
  }),
});
// Revision 2026-10-05: replaces the old review.facts. Only `at` is read by the pages (the credit line and
// lastReviewed); method, model and spotChecked exist so the pipeline's own records are self-explaining,
// the site never branches on them.
const verification = z.object({
  method: z.literal('auto'),
  at: z.string().regex(/^\d{4}-\d{2}-\d{2}/),
  model: z.string().min(1),
  spotChecked: z.boolean(),
});

/** What every species record must have, built or not: enough to name it and link to it. */
const envelopeFields = {
  qid,
  status: recordStatus,
  publish: z.boolean(),
  slug: localized,
  names: z.object({ sv: z.string().min(1), en: z.string().min(1), scientific: z.string().min(1) }),
  family: z.object({ latin: z.string().min(1), sv: z.string().min(1) }),
  group: z.string().min(1),
  review: z.object({
    wave: z.number().int().min(1).optional(),
  }),
  verification: verification.optional(),
};

/** The full contract for a species the site may build (status ok and verified). */
export const speciesPage = z
  .object({
    ...envelopeFields,
    status: z.literal('ok'),
    verification,
    iucn: z.string(),
    swedishRedList: z.enum(['RE', 'CR', 'EN', 'VU', 'NT', 'DD', 'not_listed']).optional(),
    identifiable: z.object({ photo: z.boolean(), sound: z.boolean() }),
    // The app's marginalia can exist in one language only; the pipeline then writes null for the other.
    marginalia: z.object({ sv: z.string().nullish(), en: z.string().nullish() }).optional(),
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
    // Any of the three can be missing: the pipeline keeps whichever articles exist (a species with only
    // a Swedish article is possible), so the schema does not require `en` either.
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
    text: z.object({ sv: langText, en: langText }),
    generated: z.object({ text: z.object({ at: z.string() }).optional() }).optional(),
  })
  .superRefine((d, ctx) => {
    if (!d.images.some((i) => i.role === 'hero')) {
      ctx.addIssue({ code: 'custom', path: ['images'], message: `${d.qid}: status ok kräver huvudfoto` });
    }
  });

/** Every other species record: the envelope fields, checked, and nothing else kept. */
export const speciesEnvelope = z
  .object({ ...envelopeFields, text: z.unknown() })
  .superRefine((d, ctx) => {
    if (d.publish) {
      ctx.addIssue({ code: 'custom', path: ['publish'], message: `${d.qid}: publish kräver status ok och kontrollerade fakta (verification)` });
    }
    if (d.status !== 'ok' && d.text != null) {
      ctx.addIssue({ code: 'custom', path: ['text'], message: `${d.qid}: status ${d.status} ska ha text: null` });
    }
  })
  .transform(({ text: _text, ...envelope }) => envelope);

/**
 * One species file: the page contract when the site may build the record, the envelope otherwise. The
 * issues of whichever schema applies are passed on with their paths, so Astro's error names the file
 * (the entry id) and the field.
 */
export const speciesRecord = z.unknown().transform((input, ctx) => {
  const result = hasPageContract(input) ? speciesPage.safeParse(input) : speciesEnvelope.safeParse(input);
  if (result.success) return result.data;
  for (const issue of result.error.issues) {
    ctx.addIssue({ code: 'custom', path: issue.path, message: issue.message, input: issue.input });
  }
  return z.NEVER;
});

const compareText = z.object({
  shortAnswer: sentences,
  rows: z.array(z.object({ feature: z.string().min(1), a: sentence, b: sentence })).min(3).max(5),
  metaDescription: z.string().min(120).max(155),
});

/** One comparison file (appendix D). */
export const comparisonRecord = z
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
  });
