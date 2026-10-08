// Scroll-driven motion for the first view: the Birdy bird that landed on the Dagens fågel plate takes off as the page
// scrolls, and comes back when you scroll up again. Progressive enhancement only; nothing here is needed to read the page.
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const hero = document.querySelector<HTMLElement>('[data-hero]');
const bird = hero?.querySelector<HTMLElement>('[data-birdy]');

if (hero && bird && !reduce) {
  const FLIGHT = 300; // px of scroll over which the bird flies off
  let raf = 0;

  const update = () => {
    raf = 0;
    const y = Math.max(0, Math.min(scrollY, hero.offsetHeight));
    const p = Math.min(y, FLIGHT) / FLIGHT;
    const dx = Math.min(innerWidth * 0.5, 520) * Math.pow(p, 0.7);
    bird.style.transform = p === 0 ? '' : `translate(${dx.toFixed(1)}px, ${(-p * 150).toFixed(1)}px) rotate(${(-p * 20).toFixed(1)}deg) scale(${(1 - p * 0.45).toFixed(3)})`;
    bird.style.opacity = p === 0 ? '' : String(1 - p * p);
  };

  const schedule = () => { if (!raf) raf = requestAnimationFrame(update); };
  addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule, { passive: true });
  update();
}

export {};
