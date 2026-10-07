// The virtual module astro.config.mjs builds (plugin birdy-species-images): the photos of the species
// that get a page in this build, keyed by the record's `file` ("Q25485/hero.webp").
declare module 'virtual:birdy-species-images' {
  const images: ReadonlyMap<string, import('astro').ImageMetadata>;
  export default images;
}
