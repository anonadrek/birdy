# Birdy Field Notes

Add each article as two Markdown files in `src/content/field-notes/en/` and `src/content/field-notes/sv/`. Use the same `slug` in both frontmatters; the build checks that every article has both translations.

Required frontmatter: `locale`, `slug`, `title`, `description`, `date` (`YYYY-MM-DD`), `category`, `image` and `imageAlt`. Optional: `imageCaption` (a short handwritten caption shown on the photo on desktop, for example the species name) and `imagePosition` (CSS `object-position` for the photo, for example `30% 35%`).

Keep `title` under about 60 characters. A longer one still works, but it can push the hero text lower on wide screens and wraps to more lines everywhere else.

Put the photo in `src/assets/photos/` and point to it with a path relative to the Markdown file, for example `image: ../../../assets/photos/rodhake-q25334.webp`. Add the photo's source and licence to `src/assets/photos/SOURCES.md`. The photo must be at least 1200×630 pixels, since the same file is cropped to exactly that size for the share image. The build stops if `image` is missing or smaller than that, or if `imageAlt` is missing. The photo is also used on the list page, on the homepage card and as the share image when the article is posted on social media.

The body goes below the frontmatter and may use headings, lists, links and one quote (`> ...`), which is shown as a large pull quote. The first paragraph is shown as the lead. Reading time is calculated from the text.

New posts appear automatically at `/blog/` and `/sv/blog/`, newest first. The latest post appears on both homepages. Titles, descriptions, canonical URLs, language alternatives and article metadata come from the frontmatter. Use a specific title and description for each language, and check all links and product claims before publishing.

Use the Swedish and English writing on `albit.se` as the tone reference. Write directly about what Birdy does, who it helps and why a feature matters. Avoid dash punctuation in public copy in both languages (`npm run test:no-dashes` checks it).
