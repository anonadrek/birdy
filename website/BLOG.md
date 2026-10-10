# Birdy Field Notes

Add each article as two Markdown files in `src/content/field-notes/en/` and `src/content/field-notes/sv/`. Use the same `slug` in both frontmatters; the build checks that every article has both translations.

Required frontmatter: `locale`, `slug`, `title`, `description`, `date` (`YYYY-MM-DD`), `category`, `image` and `imageAlt`. Optional: `imageCaption` (a short handwritten caption under the picture, for example the species name), `imagePosition` (CSS `object-position` for the picture, for example `30% 35%`) and `updated` (`YYYY-MM-DD`, when the note was rewritten).

Keep `title` under about 60 characters. A longer one still works, but it wraps to more lines under the picture and on the cards.

Put the picture in `src/assets/photos/` and point to it with a path relative to the Markdown file, for example `image: ../../../assets/photos/why-birdy-flock-q25334-en.webp`. Add its source and licence to `src/assets/photos/SOURCES.md`. The picture must be at least 1200×630 pixels, since the same file is cropped to exactly that size for the share image. The build stops if `image` is missing or smaller than that, or if `imageAlt` is missing. The same picture is the plate at the top of the article (whole, nothing laid over it, the title on the paper below), the card on the list page and on the homepage, and the share image when the article is posted on social media. Plates and cards show it at 1600×840, so a picture in that shape is shown uncropped; `imagePosition` moves the crop for any other shape.

The notes' own pictures in the Flock look are drawn with `node tools/render-note-art.mjs` (from `website/`): the flock forms the note's bird on the peach paper, with a few words beside it, one file per language (`-en.webp`, `-sv.webp`), and each language's note points at its own file. Add a new picture to `PICTURES` in that script, with the bird's QID (its silhouette must be in `tools/social/cover/sil/`) and the words in both languages.

The body goes below the frontmatter and may use headings, lists, links and one quote (`> ...`), which is shown as a large pull quote. The first paragraph is shown as the lead. Reading time is calculated from the text.

Links to species pages (`/species/<slug>/`, `/sv/arter/<slug>/`) are fine: in a build that doesn't have that page, the link is shown as plain text instead (`src/lib/note-links.mjs`). A vertical video goes in `public/video/` and into the body as raw HTML, `<figure class="note-video">` with a `<video controls playsinline preload="none" poster="...">` and its credit in `<figcaption>`, one `<span>` per source (see `see-the-song.md`); keep the figure free of blank lines, or Markdown ends the HTML block early. Never embed a third-party player.

New posts appear automatically at `/blog/` and `/sv/blog/`, newest first. The three newest posts appear on both homepages, side by side on wide screens. Titles, descriptions, canonical URLs, language alternatives and article metadata come from the frontmatter. Use a specific title and description for each language, and check all links and product claims before publishing.

Use the Swedish and English writing on `albit.se` as the tone reference. Write directly about what Birdy does, who it helps and why a feature matters. Avoid dash punctuation in public copy in both languages (`npm run test:no-dashes` checks it).
