import { marked } from 'marked';
import { readFileSync } from 'node:fs';

const PLAY_STORE_DIR = new URL('../../../docs/play-store/', import.meta.url);

export interface LegalDoc {
  slug: string;
  filename: string;
  title: string;
  description: string;
  lastUpdated: string;
}

export const LEGAL_DOCS: readonly LegalDoc[] = [
  {
    slug: 'privacy',
    filename: 'privacy-policy.md',
    title: 'Privacy Policy',
    description: 'What Birdy collects (almost nothing) and where your data lives (your phone).',
    lastUpdated: '2026-05-15',
  },
  {
    slug: 'terms',
    filename: 'terms.md',
    title: 'Terms of Use',
    description: 'The straightforward rules for using Birdy.',
    lastUpdated: '2026-05-15',
  },
  {
    slug: 'data-safety',
    filename: 'data-safety-form.md',
    title: 'Data Safety',
    description: 'A complete record of what data Birdy collects, why, and how it is protected.',
    lastUpdated: '2026-05-17',
  },
] as const;

export function getLegalDoc(slug: string): LegalDoc | undefined {
  return LEGAL_DOCS.find((d) => d.slug === slug);
}

export function renderLegalDoc(filename: string): string {
  const path = new URL(filename, PLAY_STORE_DIR);
  const md = readFileSync(path, 'utf-8');
  const stripped = md.trimStart().replace(/^#\s+.*(?:\r?\n)+/, '');
  return marked.parse(stripped, { async: false }) as string;
}
