// "Samma fågel som i appen i dag" is only true on the day the page was built (data-date, Europe/Stockholm). The nightly
// build (.github/workflows/daily-site-build.yml) moves the site on to the next day at 00:05; if that build did not run,
// the page still shows yesterday's bird, so the line is hidden instead of claiming it is today's (review 2026-10-08).
const hero = document.querySelector<HTMLElement>('[data-hero][data-date]');
const line = hero?.querySelector<HTMLElement>('[data-same-as-app]');

if (hero && line) {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Stockholm', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  if (today !== hero.dataset.date) line.hidden = true;
}

export {};
