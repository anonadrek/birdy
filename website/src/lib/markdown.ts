import { marked } from 'marked';

// The legal texts live in the repo's docs/play-store/, outside the website. Vite reads them at build
// time, resolved from this source file. Resolving at runtime from import.meta.url broke with Astro 7:
// the bundled chunk sits at another depth, so '../../../docs' pointed inside website/.
const PLAY_STORE_DOCS = import.meta.glob<string>('../../../docs/play-store/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
});

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
    lastUpdated: '2026-10-05',
  },
  {
    slug: 'terms',
    filename: 'terms.md',
    title: 'Terms of Use',
    description: 'The straightforward rules for using Birdy.',
    lastUpdated: '2026-10-05',
  },
  {
    slug: 'data-safety',
    filename: 'data-safety-form.md',
    title: 'Data Safety',
    description: 'A complete record of what data Birdy collects, why, and how it is protected.',
    lastUpdated: '2026-10-05',
  },
] as const;

export function getLegalDoc(slug: string): LegalDoc | undefined {
  return LEGAL_DOCS.find((d) => d.slug === slug);
}

export function renderLegalDoc(filename: string): string {
  const md = PLAY_STORE_DOCS[`../../../docs/play-store/${filename}`];
  if (md === undefined) throw new Error(`Legal document not found in docs/play-store/: ${filename}`);
  const stripped = md.trimStart().replace(/^#\s+.*(?:\r?\n)+/, '');
  return marked.parse(stripped, { async: false }) as string;
}
