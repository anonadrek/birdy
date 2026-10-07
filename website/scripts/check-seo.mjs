#!/usr/bin/env node
// SEO rules as code (spec 2026-09-25 §12). Runs on the built site (dist/, or the folder given as the first
// argument) and lists every failure. New pages (/species/, /sv/arter/) get the full list; every page gets
// one h1, alt on images and no dead links.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, process.argv[2] ?? 'dist');
const SITE = 'https://birdy.community';
const NEW = ['/species/', '/sv/arter/'];

if (!existsSync(dist)) {
  console.error(`check-seo: ${dist} saknas, bygg först`);
  process.exit(1);
}

const decode = (s) => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;|&#x27;/g, "'");
const attr = (tag, name) => { const m = tag.match(new RegExp(`\\s${name}="([^"]*)"`)); return m ? decode(m[1]) : null; };
// Present at all, with or without a value: Astro writes an empty alt as a bare `alt` (compressHTML).
const hasAttr = (tag, name) => new RegExp(`\\s${name}(?=[\\s=/>])`).test(tag);
const text = (html) => decode(html.replace(/<[^>]+>/g, '')).replace(/\s+/g, ' ').trim();

const files = readdirSync(dist, { recursive: true, encoding: 'utf8' }).filter((f) => f.endsWith('.html')).map((f) => join(dist, f));
const pathOf = (file) => {
  const rel = relative(dist, file).split(sep).join('/');
  if (rel === 'index.html') return '/';
  return rel.endsWith('/index.html') ? `/${rel.slice(0, -'index.html'.length)}` : `/${rel}`;
};
const pages = files.map((file) => ({ path: pathOf(file), html: readFileSync(file, 'utf8') }));
const byPath = new Map(pages.map((p) => [p.path, p]));

const sitemap = new Set();
for (const f of readdirSync(dist).filter((f) => /^sitemap-\d+\.xml$/.test(f))) {
  for (const m of readFileSync(join(dist, f), 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)) sitemap.add(new URL(m[1]).pathname);
}

const errors = [];
const fail = (path, msg) => errors.push(`${path}: ${msg}`);
const seenTitles = new Map();
const seenDescs = new Map();

const internalTarget = (href) => {
  if (!href.startsWith('/') || href.startsWith('//')) return null;
  return href.split('#')[0].split('?')[0] || '/';
};
const exists = (target) => byPath.has(target) || byPath.has(`${target}/`) || existsSync(join(dist, target));

for (const { path, html } of pages) {
  const isNew = NEW.some((p) => path.startsWith(p));

  const h1 = (html.match(/<h1[\s>]/g) ?? []).length;
  if (h1 !== 1) fail(path, `ska ha exakt en h1 (har ${h1})`);
  for (const tag of html.match(/<img\b[^>]*>/g) ?? []) {
    if (!hasAttr(tag, 'alt')) fail(path, `bild utan alt: ${tag.slice(0, 90)}`);
    if (isNew && (!attr(tag, 'width') || !attr(tag, 'height'))) fail(path, `bild utan width/height: ${tag.slice(0, 90)}`);
  }
  for (const tag of html.match(/<a\b[^>]*>/g) ?? []) {
    const target = internalTarget(attr(tag, 'href') ?? '');
    if (target && !exists(target)) fail(path, `död länk till ${attr(tag, 'href')}`);
  }
  if (!isNew) continue;

  // Comparison titles may fall back to "{A} vs {B} | Birdy", which is under 40 for short names (spec §12).
  const isComparison = html.includes('data-comparison-page');
  const title = text(html.match(/<title>([\s\S]*?)<\/title>/)?.[1] ?? '');
  if ((!isComparison && title.length < 40) || title.length > 60) fail(path, `titeln är ${title.length} tecken: ${title}`);
  if (seenTitles.has(title)) fail(path, `samma titel som ${seenTitles.get(title)}`);
  seenTitles.set(title, path);

  const desc = attr(html.match(/<meta name="description"[^>]*>/)?.[0] ?? '', 'content') ?? '';
  if (desc.length < 120 || desc.length > 155) fail(path, `description är ${desc.length} tecken`);
  if (seenDescs.has(desc)) fail(path, `samma description som ${seenDescs.get(desc)}`);
  seenDescs.set(desc, path);

  const levels = [...html.matchAll(/<h([1-6])[\s>]/g)].map((m) => Number(m[1]));
  for (let i = 1; i < levels.length; i += 1) {
    if (levels[i] > levels[i - 1] + 1) fail(path, `rubriknivån hoppar från h${levels[i - 1]} till h${levels[i]}`);
  }

  // The share image is in the build (the species pages' own is drawn by astro.config.mjs, not by Astro).
  const og = attr(html.match(/<meta property="og:image"[^>]*>/)?.[0] ?? '', 'content');
  if (!og) fail(path, 'og:image saknas');
  else if (og.startsWith(SITE) && !exists(new URL(og).pathname)) fail(path, `og:image finns inte i bygget: ${og}`);

  const canonical = attr(html.match(/<link rel="canonical"[^>]*>/)?.[0] ?? '', 'href');
  if (canonical !== SITE + path) fail(path, `canonical är ${canonical}`);
  const alternates = Object.fromEntries([...html.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/g)].map((m) => [m[1], m[2]]));
  const lang = path.startsWith('/sv/') ? 'sv' : 'en';
  const other = lang === 'sv' ? 'en' : 'sv';
  if (alternates[lang] !== SITE + path) fail(path, `hreflang ${lang} pekar inte på sidan själv`);
  const otherPath = alternates[other] ? new URL(alternates[other]).pathname : null;
  const otherPage = otherPath ? byPath.get(otherPath) : undefined;
  if (!otherPage) fail(path, `hreflang ${other} pekar på en sida som inte finns: ${alternates[other]}`);
  else if (!otherPage.html.includes(`hreflang="${lang}" href="${SITE + path}"`)) fail(path, `${otherPath} pekar inte tillbaka med hreflang ${lang}`);
  const english = lang === 'en' ? SITE + path : alternates.en;
  if (alternates['x-default'] !== english) fail(path, 'x-default ska peka på den engelska sidan');

  const noindex = /<meta name="robots" content="noindex/.test(html);
  if (!noindex && !sitemap.has(path)) fail(path, 'saknas i sitemapen');
  if (noindex && sitemap.has(path)) fail(path, 'har noindex men finns i sitemapen');

  const graph = [];
  for (const m of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
    try {
      const data = JSON.parse(m[1]);
      graph.push(...(data['@graph'] ?? [data]));
    } catch {
      fail(path, 'JSON-LD går inte att tolka');
    }
  }
  const crumbs = [...html.matchAll(/data-crumb[^>]*>([^<]*)</g)].map((m) => text(m[1]));
  const breadcrumb = graph.find((n) => n['@type'] === 'BreadcrumbList');
  if (!breadcrumb) fail(path, 'BreadcrumbList saknas');
  else if (JSON.stringify(breadcrumb.itemListElement.map((i) => i.name)) !== JSON.stringify(crumbs)) {
    fail(path, `BreadcrumbList (${breadcrumb.itemListElement.map((i) => i.name).join(' › ')}) matchar inte brödsmulorna (${crumbs.join(' › ')})`);
  }
  const list = graph.find((n) => n['@type'] === 'CollectionPage')?.mainEntity;
  if (list) {
    const shown = (html.match(/data-item[\s>=]/g) ?? []).length;
    if (list.numberOfItems !== shown || list.itemListElement.length !== shown) fail(path, `ItemList har ${list.numberOfItems} poster men sidan visar ${shown}`);
  }

  // The date in JSON-LD must be the one the page shows (spec §11, Revision 2026-10-05:
  // no more reviewedBy, the page no longer names a reviewer, only a verification date).
  const webPage = graph.find((n) => n['@type'] === 'WebPage');
  const reviewedAt = attr(html.match(/<time\b[^>]*data-reviewed[^>]*>/)?.[0] ?? '', 'datetime');
  if (reviewedAt && webPage?.lastReviewed !== reviewedAt) {
    fail(path, `lastReviewed (${webPage?.lastReviewed}) är inte datumet på sidan (${reviewedAt})`);
  }
  if (webPage?.reviewedBy) fail(path, 'reviewedBy finns kvar i JSON-LD (borttaget 2026-10-05, bara lastReviewed ska finnas)');

  // Credits and media on species and comparison pages (spec §10, rule 5).
  if (html.includes('data-species-page') || isComparison) {
    if (!reviewedAt) fail(path, 'kontrollraden saknas');
    const keys = new Set([...html.matchAll(/data-(?:photo|audio)="([^"]+)"/g)].map((m) => m[1]));
    const credits = new Set([...html.matchAll(/data-credit-for="([^"]+)"/g)].map((m) => m[1]));
    for (const key of keys) if (!credits.has(key)) fail(path, `${key} saknar creditrad`);
    if (!/data-wiki-credit[\s\S]*?data-wiki="/.test(html)) fail(path, 'Wikipediaraden saknas eller har inga artiklar');
    if (/data-(?:chart|map|redlist)\b/.test(html) && !html.includes('data-data-credit')) fail(path, 'diagram, karta eller rödlista utan datakälla');
    const audios = [...html.matchAll(/<audio\b[^>]*>/g)].map((m) => attr(m[0], 'src'));
    for (const src of audios) if (!src || !exists(src)) fail(path, `inspelningen finns inte i bygget: ${src}`);
    for (const media of [webPage?.associatedMedia].flat().filter(Boolean)) {
      if (!audios.includes(new URL(media.contentUrl).pathname)) fail(path, `AudioObject pekar på ${media.contentUrl}, som inte spelas på sidan`);
    }
  }

  // Every chart and map has its sentences as text (spec §12, rule 6).
  for (const m of html.matchAll(/<svg\b[^>]*aria-describedby="([^"]+)"[^>]*>/g)) {
    const desc = text(html.match(new RegExp(`id="${m[1]}"[^>]*>([\\s\\S]*?)</p>`))?.[1] ?? '');
    if (!desc) fail(path, `diagrammet eller kartan har ingen mening som text (#${m[1]})`);
  }
}

// A warning, never an error (Task 4's fix wave): the footer lists those of the twelve common species that have
// a page, and species are published one at a time, so for a while some of them have none.
const common = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).common;
const builtQids = new Set(
  pages.filter((p) => p.html.includes('data-species-page')).flatMap((p) => [...p.html.matchAll(/wikidata\.org\/wiki\/(Q\d+)/g)].map((m) => m[1])),
);
const missingCommon = common.filter((qid) => !builtQids.has(qid));
if (missingCommon.length) console.warn(`check-seo: varning, ${missingCommon.length} av ${common.length} vanliga arter saknar sida: ${missingCommon.join(', ')}`);

if (errors.length) {
  console.error(`check-seo FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(`check-seo OK (${pages.length} sidor, ${pages.filter((p) => NEW.some((n) => p.path.startsWith(n))).length} artsidor, ${sitemap.size} adresser i sitemapen)`);
