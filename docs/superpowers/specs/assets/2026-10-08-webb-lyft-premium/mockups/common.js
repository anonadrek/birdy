// Shared helpers for the mockups (built with DOM methods, constant content only).
const NS = 'http://www.w3.org/2000/svg';
const svgEl = (tag, attrs) => {
  const n = document.createElementNS(NS, tag);
  Object.entries(attrs).forEach(([k, v]) => n.setAttribute(k, v));
  return n;
};
// Torn paper edges: <div data-deckle="#2A1D17" data-side="top|bottom"></div> becomes the site's DeckleEdge.
const DECKLE = 'M0 0 H1440 V8 L1404 14 L1370 6 L1342 16 L1310 9 L1272 18 L1236 8 L1206 15 L1170 5 L1132 17 L1098 10 L1060 20 L1024 9 L988 16 L952 6 L918 14 L884 8 L846 19 L810 10 L774 16 L738 7 L700 18 L664 9 L630 15 L596 6 L558 17 L524 10 L488 19 L452 8 L418 15 L382 6 L346 16 L312 9 L276 18 L240 8 L206 14 L172 5 L136 16 L100 9 L64 17 L30 7 L0 13 Z';
document.querySelectorAll('[data-deckle]').forEach((el) => {
  el.classList.add('deckle', el.dataset.side || 'top');
  el.setAttribute('aria-hidden', 'true');
  const s = svgEl('svg', { viewBox: '0 0 1440 26', preserveAspectRatio: 'none' });
  s.append(svgEl('path', { fill: el.dataset.deckle, d: DECKLE }));
  el.append(s);
});
// The App Store badge ("Snart på App Store"), unlinked as on the site today.
const APPLE = 'M14.1 10.6c0-2.6 2.1-3.8 2.2-3.9-1.2-1.8-3.1-2-3.7-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.3-.9C3.7 4.8 2.1 5.8 1.3 7.3c-1.8 3.1-.5 7.7 1.3 10.2.8 1.2 1.8 2.6 3.1 2.5 1.3-.1 1.7-.8 3.2-.8s1.9.8 3.2.8c1.3 0 2.2-1.2 3-2.4.9-1.4 1.3-2.7 1.3-2.8 0 0-2.6-1-2.6-4.2zM11.6 3.1c.7-.8 1.1-1.9 1-3.1-1 0-2.2.7-2.9 1.5-.6.7-1.2 1.9-1 3 1.1.1 2.2-.6 2.9-1.4z';
document.querySelectorAll('[data-appstore]').forEach((el) => {
  const en = el.dataset.appstore === 'en';
  el.classList.add('appstore');
  const s = svgEl('svg', { viewBox: '0 0 17 20', 'aria-hidden': 'true' });
  s.append(svgEl('path', { d: APPLE }));
  const span = document.createElement('span');
  span.append(en ? 'Coming soon to' : 'Snart på');
  const b = document.createElement('b');
  b.textContent = 'App Store';
  span.append(b);
  el.append(s, span);
});
// Month bars from Artportalen shares: <div data-months="31,33,..." data-hi="9"></div>
document.querySelectorAll('[data-months]').forEach((el) => {
  const vals = el.dataset.months.split(',').map(Number);
  const hi = Number(el.dataset.hi ?? -1);
  el.classList.add('months');
  vals.forEach((v, i) => {
    const bar = document.createElement('i');
    bar.style.height = `${Math.max(6, v)}%`;
    if (i === hi) bar.className = 'hi';
    el.append(bar);
  });
});
