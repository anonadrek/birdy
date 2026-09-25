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
    image: image(),
    imageAlt: z.string().min(1),
    imageCaption: z.string().optional(),
    imagePosition: z.string().optional(),
  }),
});

export const collections = { fieldNotes };
