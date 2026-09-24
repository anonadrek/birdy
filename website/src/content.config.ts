import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

const fieldNotes = defineCollection({
  loader: glob({
    pattern: '**/*.md',
    base: './src/content/field-notes',
    generateId: ({ entry }) => entry.replace(/\.md$/, '').replace(/\\/g, '/'),
  }),
  schema: z.object({
    locale: z.enum(['en', 'sv']),
    slug: z.string(),
    title: z.string(),
    description: z.string(),
    date: z.coerce.date(),
    category: z.string(),
  }),
});

export const collections = { fieldNotes };
