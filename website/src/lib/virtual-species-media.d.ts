// The virtual module astro.config.mjs builds (plugin birdy-species-media) from builtSpeciesMedia() in
// src/lib/species-source.mjs: the species that get a page in this build.
declare module 'virtual:birdy-species-media' {
  /** Photos keyed by the record's `file` ("Q25485/hero.webp"). */
  export const images: ReadonlyMap<string, import('astro').ImageMetadata>;
  /** Recording links keyed by QID ("/audio/species/Q25485.3f9c0a1b2d.mp3"); the build copies the files there. */
  export const audio: ReadonlyMap<string, string>;
  /** Share image (og:image) links keyed by QID ("/og/species/Q25485.3f9c0a1b2d.jpg"); the build draws the files there. */
  export const share: ReadonlyMap<string, string>;
}

// The virtual module astro.config.mjs builds (plugin birdy-daily-bird) from src/lib/daily-bird.mjs: today's date in
// Europe/Stockholm when the site is built (or BIRDY_TODAY) and the app's Dagens fågel for it.
declare module 'virtual:birdy-daily-bird' {
  export const date: { year: number; month: number; day: number; iso: string; weekday: number; dayOfYear: number };
  /** The QID the app's DailyBirdSelector picks for `date`, or null when no species qualifies. */
  export const appQid: string | null;
}
