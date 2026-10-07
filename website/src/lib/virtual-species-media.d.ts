// The virtual module astro.config.mjs builds (plugin birdy-species-media) from builtSpeciesMedia() in
// src/lib/species-source.mjs: the species that get a page in this build.
declare module 'virtual:birdy-species-media' {
  /** Photos keyed by the record's `file` ("Q25485/hero.webp"). */
  export const images: ReadonlyMap<string, import('astro').ImageMetadata>;
  /** Recording links keyed by QID ("/audio/species/Q25485.3f9c0a1b2d.mp3"); the build copies the files there. */
  export const audio: ReadonlyMap<string, string>;
}
