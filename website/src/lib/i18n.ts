/**
 * i18n infrastructure for the landing page.
 *
 * Copy strings in copy.{en,sv}.json may contain `*word*` syntax that the
 * T8 JournalHeadline parser converts to Caveat-italic accent spans.
 * Literal asterisks in copy are not currently supported — if future copy
 * needs a literal *, add an escape mechanism in headline.ts before using it.
 */
import enCopy from '../content/copy.en.json';
import svCopy from '../content/copy.sv.json';

export type Locale = 'en' | 'sv';
export type Copy = typeof enCopy;

const copyByLocale: Record<Locale, Copy> = {
  en: enCopy satisfies Copy,
  sv: svCopy satisfies Copy,
};

export function getCopy(locale: Locale): Copy {
  return copyByLocale[locale];
}

export function getLocaleFromUrl(url: URL): Locale {
  return url.pathname === '/sv' || url.pathname.startsWith('/sv/') ? 'sv' : 'en';
}

export function alternateHref(currentLocale: Locale, currentPath: string): string {
  if (currentLocale === 'en') {
    return currentPath === '/' ? '/sv/' : `/sv${currentPath}`;
  }
  return currentPath.replace(/^\/sv(?=\/|$)/, '') || '/';
}
