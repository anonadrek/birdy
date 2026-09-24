# Birdy Field Notes

Add each article as two Markdown files in `src/content/field-notes/en/` and `src/content/field-notes/sv/`. Use the same `slug` in both frontmatters; the build checks that every article has both translations.

Required frontmatter: `locale`, `slug`, `title`, `description`, `date` (`YYYY-MM-DD`), and `category`. The file name can match the slug. The article body goes below the frontmatter and may use Markdown headings, lists, and links.

New posts appear automatically at `/blog/` and `/sv/blog/`, newest first, with their own article URLs. The latest post appears on both homepages. Titles, descriptions, canonical URLs, language alternatives, and article metadata are generated from the frontmatter. Use a specific title and description for each language, and check all links and product claims before publishing.

Use the Swedish and English writing on `albit.se` as the tone reference. Write directly about what Birdy does, who it helps, and why a feature matters. Avoid dash punctuation in public copy in both languages.
