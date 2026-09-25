import { getCollection, type CollectionEntry } from 'astro:content';
import type { Locale } from './i18n';

export type FieldNote = CollectionEntry<'fieldNotes'>;

export function fieldNoteHref(note: FieldNote): string {
  return `${note.data.locale === 'sv' ? '/sv' : ''}/blog/${note.data.slug}/`;
}

export function fieldNotesHref(locale: Locale): string {
  return locale === 'sv' ? '/sv/blog/' : '/blog/';
}

export async function getFieldNotes(locale: Locale): Promise<FieldNote[]> {
  const all = await getCollection('fieldNotes');
  const translations = new Map<string, Set<Locale>>();
  for (const note of all) {
    const locales = translations.get(note.data.slug) ?? new Set<Locale>();
    if (locales.has(note.data.locale)) throw new Error(`Duplicate field note: ${note.data.locale}/${note.data.slug}`);
    locales.add(note.data.locale);
    translations.set(note.data.slug, locales);
  }
  for (const [slug, locales] of translations) {
    if (!locales.has('en') || !locales.has('sv')) throw new Error(`Field note needs both EN and SV versions: ${slug}`);
  }
  return all.filter((note) => note.data.locale === locale)
    .sort((a, b) => b.data.date.valueOf() - a.data.date.valueOf());
}

/** Minutes to read a note's markdown body at 200 words per minute, at least 1. */
export function readingMinutes(body: string | undefined): number {
  const words = (body ?? '').replace(/[#>*_`[\]()!]/g, ' ').split(/\s+/).filter(Boolean).length;
  return Math.max(1, Math.round(words / 200));
}

export function formatNoteDate(date: Date, locale: Locale): string {
  return date.toLocaleDateString(locale === 'sv' ? 'sv-SE' : 'en-GB', { year: 'numeric', month: 'long', day: 'numeric' });
}
