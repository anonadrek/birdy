// App tour carousel (ported from the approved mockup): scroll-snap track with depth (neighbours
// scale, tilt and fade), a caption that follows the centred phone, arrows, arrow keys,
// click-to-centre and mouse drag with momentum. Touch uses native scrolling.
const root = document.querySelector<HTMLElement>('[data-tour]');
const track = root?.querySelector<HTMLElement>('[data-track]');

if (root && track) {
  const slides = [...track.querySelectorAll<HTMLElement>('.slide')];
  const rail = root.querySelector<HTMLElement>('[data-rail]');
  const capInner = root.querySelector<HTMLElement>('[data-cap]');
  const ck = root.querySelector<HTMLElement>('[data-ck]');
  const ch = root.querySelector<HTMLElement>('[data-ch]');
  const cpp = root.querySelector<HTMLElement>('[data-cpp]');
  const cp = root.querySelector<HTMLElement>('[data-cp]');
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let active = 0;
  let raf = 0;
  let moved = false;

  const centerOf = (s: HTMLElement) => s.offsetLeft + s.offsetWidth / 2;

  const setCaption = (i: number) => {
    const s = slides[i];
    const apply = () => {
      if (ck) ck.textContent = s.dataset.k ?? '';
      if (ch) ch.textContent = s.dataset.h ?? '';
      if (cpp) cpp.textContent = s.dataset.p ?? '';
      if (cp) cp.hidden = !s.dataset.premium;
      capInner?.classList.remove('out');
    };
    if (reduce) { apply(); return; }
    capInner?.classList.add('out');
    setTimeout(apply, 160);
  };

  const frame = () => {
    raf = 0;
    const mid = track.scrollLeft + track.clientWidth / 2;
    const step = slides.length > 1 ? centerOf(slides[1]) - centerOf(slides[0]) : 1;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => {
      const d = (centerOf(s) - mid) / step;
      const a = Math.min(Math.abs(d), 1.4);
      if (!reduce) {
        s.style.transform = `translate3d(0, ${(a * 22).toFixed(2)}px, 0) scale(${(1 - a * 0.13).toFixed(4)}) rotate(${(Math.max(-1.4, Math.min(1.4, d)) * -2.2).toFixed(2)}deg)`;
        s.style.opacity = (1 - Math.min(a, 1) * 0.55).toFixed(3);
      }
      if (Math.abs(d) < bestDist) { bestDist = Math.abs(d); best = i; }
    });
    const progress = track.scrollLeft / Math.max(1, track.scrollWidth - track.clientWidth);
    rail?.style.setProperty('--p', (progress * (slides.length - 1)).toFixed(4));
    if (best !== active) { active = best; setCaption(best); }
  };
  const schedule = () => { if (!raf) raf = requestAnimationFrame(frame); };

  const go = (i: number) => {
    const target = Math.max(0, Math.min(slides.length - 1, i));
    track.scrollTo({ left: centerOf(slides[target]) - track.clientWidth / 2, behavior: reduce ? 'auto' : 'smooth' });
  };

  track.addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule, { passive: true });
  root.querySelector('[data-prev]')?.addEventListener('click', () => go(active - 1));
  root.querySelector('[data-next]')?.addEventListener('click', () => go(active + 1));
  track.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowRight') { e.preventDefault(); go(active + 1); }
    if (e.key === 'ArrowLeft') { e.preventDefault(); go(active - 1); }
  });
  slides.forEach((s, i) => s.addEventListener('click', () => { if (!moved && i !== active) go(i); }));

  let down = false;
  let startX = 0;
  let startLeft = 0;
  let lastX = 0;
  let lastT = 0;
  let velocity = 0;
  track.addEventListener('pointerdown', (e) => {
    if (e.pointerType !== 'mouse' || e.button !== 0) return;
    down = true; moved = false; startX = lastX = e.clientX; startLeft = track.scrollLeft; lastT = performance.now(); velocity = 0;
    track.setPointerCapture(e.pointerId);
  });
  track.addEventListener('pointermove', (e) => {
    if (!down) return;
    const dx = e.clientX - startX;
    if (!moved && Math.abs(dx) > 4) { moved = true; track.classList.add('dragging'); }
    if (!moved) return;
    track.scrollLeft = startLeft - dx;
    const now = performance.now();
    velocity = (e.clientX - lastX) / Math.max(1, now - lastT);
    lastX = e.clientX; lastT = now;
  });
  const end = () => {
    if (!down) return;
    down = false;
    if (!moved) return;
    track.classList.remove('dragging');
    const mid = track.scrollLeft + track.clientWidth / 2 - velocity * 180;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => { const d = Math.abs(centerOf(s) - mid); if (d < bestDist) { bestDist = d; best = i; } });
    go(best);
    setTimeout(() => { moved = false; }, 50);
  };
  track.addEventListener('pointerup', end);
  track.addEventListener('pointercancel', end);

  // Load every phone's photos together as the section approaches, so nothing pops in while browsing
  // (lazy images clipped by the horizontal scroller would otherwise wait until each is scrolled to).
  const lazyImages = [...track.querySelectorAll<HTMLImageElement>('img[loading="lazy"]')];
  if ('IntersectionObserver' in window) {
    const preload = new IntersectionObserver((entries) => {
      if (!entries.some((e) => e.isIntersecting)) return;
      preload.disconnect();
      for (const img of lazyImages) img.loading = 'eager';
    }, { rootMargin: '800px 0px' });
    preload.observe(root);
  }

  frame();
}

export {};
