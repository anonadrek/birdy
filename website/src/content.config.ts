import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

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

export const collections = { fieldNotes };
