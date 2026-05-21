import enCopy from '../content/copy.en.json';
import svCopy from '../content/copy.sv.json';

export type Locale = 'en' | 'sv';
export type Copy = typeof enCopy;

const copyByLocale: Record<Locale, Copy> = {
  en: enCopy as Copy,
  sv: svCopy as Copy,
};

export function getCopy(locale: Locale): Copy {
  return copyByLocale[locale];
}

export function getLocaleFromUrl(url: URL): Locale {
  return url.pathname.startsWith('/sv') ? 'sv' : 'en';
}

export function alternateHref(currentLocale: Locale, currentPath: string): string {
  if (currentLocale === 'en') {
    return currentPath === '/' ? '/sv/' : `/sv${currentPath}`;
  }
  return currentPath.replace(/^\/sv/, '') || '/';
}
