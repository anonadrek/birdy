// The virtual module astro.config.mjs builds (plugin birdy-clips) from src/lib/clips.mjs: the See the song clips posted
// on or before the build's date (Europe/Stockholm, BIRDY_TODAY in test builds), newest first, each with its flock cover.
declare module 'virtual:birdy-clips' {
  export const clips: {
    date: string;
    qid: string;
    slug: string;
    names: { sv: string; en: string; scientific: string };
    silhouette: { author: string; licence: string; url: string; adapted: boolean };
    cover: import('astro').ImageMetadata;
  }[];
}
