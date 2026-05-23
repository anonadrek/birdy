# Vercel Migration + Legal Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate hosting from GitHub Pages (`birdy.app`) to Vercel (`birdy.community`) and add a Field-Journal-themed legal-documents section at `/legal/`.

**Architecture:** Astro static build deployed by Vercel auto-deploy on push to `main`. Legal source markdown stays in `docs/play-store/`, rendered via `marked` at build time inside a shared `LegalLayout`. Pandoc pipeline + GitHub Pages workflow removed entirely.

**Tech Stack:** Astro 5.x (static), marked 12.x (markdown→HTML), Vercel hosting (zero-config Astro detection), TypeScript strict, Playwright (smoke tests).

**Resolved scope decisions:**
- **D1** Pandoc pages dropped entirely (store-listing-{en,sv}, tester-instructions, plus the 3 legal docs we're replacing). `docs/play-store/*.md` remains canonical source.
- **A** Legal docs are EN-only. SV-locale footer links cross-locale to `/legal/...`. No SV mirror routes.
- **B** GitHub Pages workflow deleted; Vercel auto-deploys from `main`.
- **C** Email `feedback@birdy.app` kept as-is (separate DNS/MX concern; can swap to `feedback@birdy.community` later if user sets up MX).

---

## Task 1: Migrate site config + robots.txt to birdy.community

**Files:**
- Modify: `website/astro.config.mjs:7`
- Modify: `website/public/robots.txt:4`

- [ ] **Step 1: Update astro.config.mjs site URL**

Change line 7 from `site: 'https://birdy.app',` to `site: 'https://birdy.community',`.

`Astro.site` is used throughout `Layout.astro` (canonical, hreflang, og:url, og:image) and `@astrojs/sitemap` for sitemap URLs — they all auto-update from this single line.

- [ ] **Step 2: Update robots.txt sitemap URL**

Change line 4 from `Sitemap: https://birdy.app/sitemap-index.xml` to `Sitemap: https://birdy.community/sitemap-index.xml`.

- [ ] **Step 3: Build to verify no other birdy.app refs in dist/**

```bash
cd website && npm run build
grep -r 'birdy\.app' dist/ | grep -v 'play.google\|feedback@' | head -5
```
Expected: no output (only `feedback@birdy.app` mailto links and `play.google.com` URLs remain — both intentional).

- [ ] **Step 4: Commit**

```bash
git add website/astro.config.mjs website/public/robots.txt
git commit -m "feat(website/t1): switch site URL birdy.app -> birdy.community

- astro.config.mjs site config drives canonical, hreflang, og:url, og:image, sitemap
- robots.txt sitemap URL updated
- feedback@birdy.app email kept as-is (separate DNS concern)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: Delete GitHub Pages workflow + CNAME

**Files:**
- Delete: `.github/workflows/pages.yml`
- Delete: `website/public/CNAME`

- [ ] **Step 1: Remove the workflow**

```bash
git rm .github/workflows/pages.yml
```

- [ ] **Step 2: Remove the CNAME**

```bash
git rm website/public/CNAME
```

- [ ] **Step 3: Verify nothing else references these files**

```bash
grep -r 'pages\.yml\|CNAME' . --include='*.md' --include='*.yml' --include='*.json' 2>/dev/null | grep -v node_modules | head -5
```
Expected: no business-critical references (markdown docs referencing the workflow are OK to leave; they describe history).

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(website/t2): drop GitHub Pages workflow + CNAME

- Vercel auto-deploys from main on push; no workflow needed
- CNAME (birdy.app) deleted; Vercel manages birdy.community via Dashboard
- Pandoc-rendered store-listing/tester-instructions/legal docs dropped
  (legal docs replaced by Astro pages in T3-T6; store-listing/tester-instructions
  remain in docs/play-store/ for internal Play Console reference, no web publish)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: Install marked + write markdown helper

**Files:**
- Modify: `website/package.json` (add `marked` dep)
- Create: `website/src/lib/markdown.ts`

- [ ] **Step 1: Install marked**

```bash
cd website && npm install marked
```

`marked` is a small (~30 KB), zero-deps Markdown parser. Synchronous API. No runtime — we use it at build time inside Astro page frontmatter.

- [ ] **Step 2: Create `website/src/lib/markdown.ts`**

```ts
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
    description: 'The full Google Play Data Safety declarations.',
    lastUpdated: '2026-05-17',
  },
] as const;

export function getLegalDoc(slug: string): LegalDoc | undefined {
  return LEGAL_DOCS.find((d) => d.slug === slug);
}

export function renderLegalDoc(filename: string): string {
  const path = new URL(filename, PLAY_STORE_DIR);
  const md = readFileSync(path, 'utf-8');
  const stripped = md.replace(/^#\s+.*\n+/, '');
  return marked.parse(stripped, { async: false }) as string;
}
```

The `stripped` line removes the first `# Heading` line from each markdown file because `LegalLayout` renders its own `<h1>` from `LegalDoc.title` — we don't want it twice.

- [ ] **Step 3: Smoke-test the helper compiles**

```bash
cd website && npx tsc --noEmit src/lib/markdown.ts
```
Expected: no errors. (If tsc complains about JSON imports or top-level URL, this is fine — Astro's build will handle them; only flag errors that are actual type bugs.)

- [ ] **Step 4: Commit**

```bash
git add website/package.json website/package-lock.json website/src/lib/markdown.ts
git commit -m "feat(website/t3): markdown helper + marked dep for legal pages

- LEGAL_DOCS registry: slug, filename, title, description, lastUpdated
- renderLegalDoc(filename) reads docs/play-store/<file>.md via fs + marked.parse
- Strips first H1 from source markdown (LegalLayout renders its own H1)
- Zero-runtime: marked + fs.readFileSync run at Astro build time only

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: LegalLayout.astro shared wrapper

**Files:**
- Create: `website/src/layouts/LegalLayout.astro`
- Create: `website/src/styles/legal-prose.css`

**Context:** The shared layout component used by `/legal/` index AND `/legal/[slug]/` pages. Renders:
1. Standard `Layout.astro` (head/meta/Nav/Footer/global styles).
2. A small `Hero`-like header inside the page (eyebrow + JournalHeadline + `Last updated YYYY-MM-DD`).
3. A `<main>` content slot that renders the markdown HTML.
4. A "More legal documents" footer with cross-links to the OTHER two docs.

- [ ] **Step 1: Create `website/src/styles/legal-prose.css`**

```css
.legal-prose {
  font-family: var(--font-sans);
  font-size: 1rem;
  line-height: 1.65;
  color: var(--color-text-primary);
  max-width: 60ch;
  margin: 0 auto;
}
.legal-prose h2 {
  font-family: var(--font-serif);
  font-style: italic;
  font-weight: 400;
  font-size: 1.5rem;
  margin: 2.5rem 0 0.75rem;
  color: var(--color-text-primary);
}
.legal-prose h3 {
  font-family: var(--font-serif);
  font-style: italic;
  font-weight: 400;
  font-size: 1.125rem;
  margin: 2rem 0 0.5rem;
}
.legal-prose p { margin: 0 0 1rem; }
.legal-prose ul, .legal-prose ol { margin: 0 0 1rem 1.5rem; padding: 0; }
.legal-prose li { margin: 0.25rem 0; }
.legal-prose a {
  color: var(--color-copper);
  text-decoration: underline;
  text-decoration-color: rgba(168, 85, 45, 0.4);
  text-underline-offset: 3px;
}
.legal-prose a:hover { text-decoration-color: var(--color-copper); }
.legal-prose strong { font-weight: 600; }
.legal-prose hr {
  border: none;
  border-top: 1px dashed var(--color-marginalia-ink);
  margin: 2.5rem 0;
  opacity: 0.5;
}
.legal-prose code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.875em;
  background: var(--color-paper-edge);
  padding: 0.1em 0.35em;
  border-radius: 3px;
}
```

- [ ] **Step 2: Create `website/src/layouts/LegalLayout.astro`**

```astro
---
import Layout from './Layout.astro';
import Nav from '../components/Nav.astro';
import Footer from '../components/Footer.astro';
import EyebrowLabel from '../components/ui/EyebrowLabel.astro';
import JournalHeadline from '../components/ui/JournalHeadline.astro';
import { LEGAL_DOCS, type LegalDoc } from '../lib/markdown';
import '../styles/legal-prose.css';

interface Props {
  doc: LegalDoc;
  pathname: string;
  bodyHtml: string;
}

const { doc, pathname, bodyHtml } = Astro.props;
const others = LEGAL_DOCS.filter((d) => d.slug !== doc.slug);
---

<Layout locale="en" pathname={pathname}>
  <Nav locale="en" />
  <main class="legal-page">
    <header class="hero">
      <EyebrowLabel text="LEGAL" />
      <JournalHeadline text={doc.title} level="h1" size="clamp(2.5rem, 6vw, 4rem)" />
      <p class="meta">Last updated {doc.lastUpdated}</p>
    </header>

    <article class="legal-prose" set:html={bodyHtml} />

    <aside class="more">
      <p class="more-label">More legal documents</p>
      <ul role="list">
        {others.map((o) => (
          <li>
            <a href={`/legal/${o.slug}/`}>{o.title}</a>
            <span class="dash"> — </span>
            <span class="desc">{o.description}</span>
          </li>
        ))}
        <li>
          <a href="/legal/">All legal documents</a>
        </li>
      </ul>
    </aside>
  </main>
  <Footer locale="en" />
</Layout>

<style>
  .legal-page {
    padding: 4rem 1.5rem 2rem;
    max-width: 64rem;
    margin: 0 auto;
  }
  .hero {
    text-align: center;
    margin-bottom: 3rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1rem;
  }
  .meta {
    font-family: var(--font-script);
    font-size: 1rem;
    color: var(--color-marginalia-ink);
    margin: 0;
  }
  .more {
    max-width: 60ch;
    margin: 4rem auto 0;
    padding-top: 2rem;
    border-top: 1px dashed var(--color-marginalia-ink);
  }
  .more-label {
    font-family: var(--font-sans);
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.12em;
    color: var(--color-marginalia-ink);
    margin: 0 0 1rem;
  }
  .more ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }
  .more a {
    color: var(--color-copper);
    font-family: var(--font-serif);
    font-style: italic;
    font-size: 1.125rem;
  }
  .more .dash, .more .desc {
    font-family: var(--font-sans);
    font-size: 0.9375rem;
    color: var(--color-text-primary);
  }
</style>
```

- [ ] **Step 3: Astro check**

```bash
cd website && npx astro check 2>&1 | tail -5
```
Expected: 0 errors related to LegalLayout (pre-existing Tailwind/Vite type warning is ignorable).

- [ ] **Step 4: Commit**

```bash
git add website/src/layouts/LegalLayout.astro website/src/styles/legal-prose.css
git commit -m "feat(website/t4): LegalLayout + prose styles

- Wraps Layout with Nav + Footer + paper-bg
- Hero: EyebrowLabel 'LEGAL' + JournalHeadline title + Caveat 'Last updated' meta
- Article slot accepts pre-rendered markdown HTML via set:html
- Bottom 'More legal documents' aside with cross-links to other docs + index
- legal-prose.css: serif italic h2/h3, copper links, dashed hr, max-width 60ch

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: `/legal/` index page

**Files:**
- Create: `website/src/pages/legal/index.astro`

- [ ] **Step 1: Create `website/src/pages/legal/index.astro`**

```astro
---
import Layout from '../../layouts/Layout.astro';
import Nav from '../../components/Nav.astro';
import Footer from '../../components/Footer.astro';
import EyebrowLabel from '../../components/ui/EyebrowLabel.astro';
import JournalHeadline from '../../components/ui/JournalHeadline.astro';
import WingLogo from '../../components/ui/WingLogo.astro';
import { LEGAL_DOCS } from '../../lib/markdown';

const pathname = '/legal/';
---

<Layout locale="en" pathname={pathname}>
  <Nav locale="en" />
  <main class="legal-index">
    <header class="hero">
      <WingLogo size="48px" />
      <EyebrowLabel text="LEGAL" />
      <JournalHeadline
        text="Field journal *fine print*"
        level="h1"
        size="clamp(2.5rem, 6vw, 4rem)"
      />
      <p class="sub">Everything Google asks for. In plain English.</p>
    </header>

    <ul class="docs" role="list">
      {LEGAL_DOCS.map((d) => (
        <li class="doc">
          <a href={`/legal/${d.slug}/`} class="doc-link">
            <h2 class="doc-title">{d.title}</h2>
            <p class="doc-desc">{d.description}</p>
            <span class="doc-cta">Read →</span>
          </a>
        </li>
      ))}
    </ul>
  </main>
  <Footer locale="en" />
</Layout>

<style>
  .legal-index {
    padding: 4rem 1.5rem 2rem;
    max-width: 60rem;
    margin: 0 auto;
  }
  .hero {
    text-align: center;
    margin-bottom: 3rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1rem;
  }
  .sub {
    font-family: var(--font-script);
    font-size: 1.25rem;
    color: var(--color-marginalia-ink);
    margin: 0;
  }
  .docs {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 1.5rem;
    max-width: 42rem;
    margin-inline: auto;
  }
  .doc-link {
    display: block;
    padding: 2rem;
    background: var(--color-paper-edge);
    border: 1px solid rgba(63, 79, 48, 0.1);
    border-radius: 12px;
    text-decoration: none;
    transition: transform 0.2s var(--ease-paper), box-shadow 0.2s var(--ease-paper);
  }
  .doc-link:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(63, 79, 48, 0.08);
  }
  .doc-title {
    font-family: var(--font-serif);
    font-style: italic;
    font-weight: 400;
    font-size: 1.75rem;
    color: var(--color-text-primary);
    margin: 0 0 0.5rem;
  }
  .doc-desc {
    font-family: var(--font-sans);
    font-size: 1rem;
    line-height: 1.5;
    color: var(--color-text-primary);
    margin: 0 0 1rem;
  }
  .doc-cta {
    font-family: var(--font-script);
    font-size: 1.125rem;
    color: var(--color-copper);
    font-weight: 700;
  }
</style>
```

- [ ] **Step 2: Build + verify the page renders**

```bash
cd website && npm run build && ls -la dist/legal/
```
Expected: `dist/legal/index.html` present.

- [ ] **Step 3: Commit**

```bash
git add website/src/pages/legal/index.astro
git commit -m "feat(website/t5): /legal/ index page

- WingLogo + EyebrowLabel 'LEGAL' + JournalHeadline 'Field journal fine print'
- 3 cards (Privacy / Terms / Data Safety) from LEGAL_DOCS registry
- Each card: doc title (DM Serif Italic), 1-line description, 'Read →' Caveat CTA
- Hover transform + soft shadow (matches Premium card pattern)

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: `/legal/[slug]/` dynamic page for the 3 docs

**Files:**
- Create: `website/src/pages/legal/[slug].astro`

- [ ] **Step 1: Create `website/src/pages/legal/[slug].astro`**

```astro
---
import LegalLayout from '../../layouts/LegalLayout.astro';
import { LEGAL_DOCS, renderLegalDoc, getLegalDoc } from '../../lib/markdown';

export function getStaticPaths() {
  return LEGAL_DOCS.map((doc) => ({
    params: { slug: doc.slug },
    props: { doc },
  }));
}

const { doc } = Astro.props;
const bodyHtml = renderLegalDoc(doc.filename);
const pathname = `/legal/${doc.slug}/`;
---

<LegalLayout doc={doc} pathname={pathname} bodyHtml={bodyHtml} />
```

- [ ] **Step 2: Build + verify all 3 dynamic routes**

```bash
cd website && npm run build
ls -la dist/legal/privacy/ dist/legal/terms/ dist/legal/data-safety/
```
Expected: `dist/legal/{privacy,terms,data-safety}/index.html` all present.

- [ ] **Step 3: Spot-check rendered content**

```bash
grep -c '<h2' dist/legal/privacy/index.html
grep -c '<h2' dist/legal/terms/index.html
grep -c '<h2' dist/legal/data-safety/index.html
```
Expected: each > 0 (the markdown source has multiple H2 sections each).

- [ ] **Step 4: Commit**

```bash
git add website/src/pages/legal/\[slug\].astro
git commit -m "feat(website/t6): /legal/[slug]/ dynamic page renders 3 legal docs

- getStaticPaths emits 3 routes (privacy, terms, data-safety)
- Reads docs/play-store/<filename>.md via renderLegalDoc, passes HTML to LegalLayout
- All 3 routes generated at build: dist/legal/{privacy,terms,data-safety}/

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: Update Footer copy + add Legal nav

**Files:**
- Modify: `website/src/content/copy.en.json` (footer.links)
- Modify: `website/src/content/copy.sv.json` (footer.links)

- [ ] **Step 1: Update `copy.en.json` footer.links**

Replace the existing `footer.links` array (lines ~84-89) with:

```json
    "links": [
      { "label": "Legal", "href": "/legal/" },
      { "label": "Privacy", "href": "/legal/privacy/" },
      { "label": "Terms", "href": "/legal/terms/" },
      { "label": "Email", "href": "mailto:feedback@birdy.app" },
      { "label": "SV", "href": "/sv/" }
    ],
```

- [ ] **Step 2: Update `copy.sv.json` footer.links**

Replace the existing `footer.links` array (same lines) with:

```json
    "links": [
      { "label": "Juridik", "href": "/legal/" },
      { "label": "Integritet", "href": "/legal/privacy/" },
      { "label": "Villkor", "href": "/legal/terms/" },
      { "label": "E-post", "href": "mailto:feedback@birdy.app" },
      { "label": "EN", "href": "/" }
    ],
```

(SV-locale footer links cross-locale to EN-only /legal/ routes — intentional per scope decision A.)

- [ ] **Step 3: Run i18n parity check**

```bash
cd website && npm run test:i18n
```
Expected: `i18n parity OK (NN keys)` — parity check verifies key structure is identical, value differences are fine.

- [ ] **Step 4: Build + verify footer renders correctly**

```bash
cd website && npm run build
grep -c 'href="/legal/' dist/index.html dist/sv/index.html
```
Expected: each file has at least 3 `/legal/` references (Legal index + Privacy + Terms).

- [ ] **Step 5: Commit**

```bash
git add website/src/content/copy.en.json website/src/content/copy.sv.json
git commit -m "feat(website/t7): footer links point to /legal/ (both locales)

- EN: Legal / Privacy / Terms / Email / SV-toggle
- SV: Juridik / Integritet / Villkor / E-post / EN-toggle
- SV cross-links to EN-only /legal/ routes (legal is EN-only per scope decision)
- 'Legal' / 'Juridik' index link added; old /privacy.html /terms.html paths gone

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 8: Add /legal/ smoke tests

**Files:**
- Modify: `website/tests/smoke.spec.ts`

- [ ] **Step 1: Append /legal/ tests to `website/tests/smoke.spec.ts`**

After the existing `test.describe('SV landing /sv/', ...)` block (around line 30), append:

```ts
test.describe('Legal section', () => {
  test('/legal/ index returns 200 + links to 3 docs', async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text());
    });

    const response = await page.goto('/legal/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('fine print');

    for (const slug of ['privacy', 'terms', 'data-safety']) {
      const link = page.locator(`a[href="/legal/${slug}/"]`);
      expect(await link.count()).toBeGreaterThan(0);
    }

    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });

  test('/legal/privacy/ renders markdown body + cross-links', async ({ page }) => {
    const response = await page.goto('/legal/privacy/');
    expect(response?.status()).toBe(200);

    await expect(page.locator('h1')).toContainText('Privacy');
    expect(await page.locator('.legal-prose h2').count()).toBeGreaterThan(0);

    const termsLink = page.locator('aside.more a[href="/legal/terms/"]');
    expect(await termsLink.count()).toBe(1);
  });

  test('/legal/terms/ renders', async ({ page }) => {
    const response = await page.goto('/legal/terms/');
    expect(response?.status()).toBe(200);
    await expect(page.locator('h1')).toContainText('Terms');
  });

  test('/legal/data-safety/ renders', async ({ page }) => {
    const response = await page.goto('/legal/data-safety/');
    expect(response?.status()).toBe(200);
    await expect(page.locator('h1')).toContainText('Data Safety');
  });
});
```

- [ ] **Step 2: Run all smoke tests**

```bash
cd website && npm run build && npm run test:smoke
```
Expected: 7 tests pass (3 original + 4 new).

- [ ] **Step 3: Commit**

```bash
git add website/tests/smoke.spec.ts
git commit -m "test(website/t8): smoke tests for /legal/ + 3 doc routes

- /legal/ index: 200 + h1 'fine print' + 3 doc card links + no console errors
- /legal/privacy/: 200 + h1 'Privacy' + .legal-prose h2 count > 0 + cross-link to terms
- /legal/terms/: 200 + h1 'Terms'
- /legal/data-safety/: 200 + h1 'Data Safety'
- Total smoke tests: 3 original + 4 new = 7 pass

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 9: Final build + push + verify Vercel deploy

**Files:**
- (none — verification + push only)

- [ ] **Step 1: Clean build + full test suite**

```bash
cd website
rm -rf dist
npm ci
npm run test:i18n
npm run build
npm run test:smoke
```
Expected: i18n parity OK, build succeeds, 7/7 smoke tests pass.

- [ ] **Step 2: Sanity-check dist/**

```bash
ls dist/legal/ dist/legal/privacy/ dist/legal/terms/ dist/legal/data-safety/
test -f dist/index.html && echo "EN landing OK"
test -f dist/sv/index.html && echo "SV landing OK"
test -f dist/sitemap-index.xml && echo "Sitemap OK"
test ! -f dist/CNAME && echo "CNAME removed OK"
```
Expected: all 4 statements print OK; legal/ subdirs each contain `index.html`.

- [ ] **Step 3: Check no birdy.app refs in dist (except intentional)**

```bash
cd website
grep -r 'birdy\.app' dist/ 2>/dev/null | grep -v 'mailto:feedback' | head -5
```
Expected: no output (only `mailto:feedback@birdy.app` allowed).

- [ ] **Step 4: Push branch**

```bash
git log --oneline -12
git push origin main
```

- [ ] **Step 5: Verify Vercel deploy**

Watch Vercel dashboard for the auto-deploy triggered by the push. After deploy finishes (~1-2 min for static Astro), verify these URLs return 200:

- `https://birdy.community/`
- `https://birdy.community/sv/`
- `https://birdy.community/legal/`
- `https://birdy.community/legal/privacy/`
- `https://birdy.community/legal/terms/`
- `https://birdy.community/legal/data-safety/`

If Vercel still serves 404 after deploy: check Vercel project settings → Build & Development → Root Directory = `website/`, Framework Preset = `Astro`, Build Command = `npm run build`, Output Directory = `dist`. These are the standard Astro-on-Vercel defaults.

---

## Post-launch follow-ups (track separately)

1. **Email migration** — `feedback@birdy.app` → `feedback@birdy.community` if/when user sets up MX records on birdy.community. Touches: copy.{en,sv}.json (FAQ + footer), all 5 markdown files in `docs/play-store/` that reference the email.
2. **GitHub Pages teardown** — In GitHub repo Settings → Pages, set Source to "None" so the old pages.yml deployment (now orphaned) is fully decommissioned.
3. **birdy.app domain disposition** — Keep, redirect to birdy.community via DNS, or let lapse. Out of scope.
4. **SV legal translations** — If product gains traction in Sweden and SV legal pages become a UX priority, add `/sv/legal/...` mirror routes with translated markdown.
5. **vercel.json** — Add only if redirects/rewrites/headers are needed. Not required for default static deploy.

---

## Self-Review Notes

- **Spec coverage:** All 3 scope decisions (D1 drop pandoc, A EN-only legal, B kill Pages workflow) covered. Email decision (C keep birdy.app) noted in T1 commit message + follow-up #1.
- **Placeholder scan:** No "TBD" in code blocks. All steps have exact paths, exact commands, and complete code.
- **Type consistency:** `LegalDoc` interface defined in T3; consumed by LegalLayout (T4), index page (T5), [slug].astro (T6). `LEGAL_DOCS` registry single source of truth for slug/filename/title/description.
- **Scope check:** Single repo, single deploy target, 9 small tasks. Each task ships independently testable changes.
- **Risk areas:** T9 Vercel-side configuration is dashboard-only (user-side); planned 404 fallback if Vercel root-dir not set to `website/`. T2 pandoc-pipeline removal is a one-way change (legal docs served only via Astro after this).
