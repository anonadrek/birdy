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
  const prevBtn = root.querySelector<HTMLButtonElement>('[data-prev]');
  const nextBtn = root.querySelector<HTMLButtonElement>('[data-next]');
  const rm = matchMedia('(prefers-reduced-motion: reduce)');
  let active = 0;
  let pending: number | null = null;
  let raf = 0;
  let moved = false;
  let capTimer = 0;
  let settleTimer = 0;

  const centerOf = (s: HTMLElement) => s.offsetLeft + s.offsetWidth / 2;

  // The real, current resting slide, read fresh from the scroll position rather than from `active`
  // (which only updates via rAF and can lag under load) or `pending` (which only reflects our own
  // last `go()` call, not a native touch swipe that never went through it).
  const nearest = (): number => {
    const mid = track.scrollLeft + track.clientWidth / 2;
    const step = slides.length > 1 ? centerOf(slides[1]) - centerOf(slides[0]) : 1;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => {
      const d = Math.abs((centerOf(s) - mid) / step);
      if (d < bestDist) { bestDist = d; best = i; }
    });
    return best;
  };

  const setCaption = (i: number) => {
    const s = slides[i];
    const apply = () => {
      if (ck) ck.textContent = s.dataset.k ?? '';
      if (ch) ch.textContent = s.dataset.h ?? '';
      if (cpp) cpp.textContent = s.dataset.p ?? '';
      if (cp) cp.hidden = !s.dataset.premium;
      capInner?.classList.remove('out');
    };
    clearTimeout(capTimer);
    if (rm.matches) { apply(); return; }
    capInner?.classList.add('out');
    capTimer = window.setTimeout(apply, 160);
  };

  const setArrows = (i: number) => {
    prevBtn?.setAttribute('aria-disabled', i <= 0 ? 'true' : 'false');
    nextBtn?.setAttribute('aria-disabled', i >= slides.length - 1 ? 'true' : 'false');
  };

  const frame = () => {
    raf = 0;
    // Read phase: every slide's distance from centre, plus the scroll progress. No writes yet,
    // so this never forces a synchronous layout in between reads.
    const mid = track.scrollLeft + track.clientWidth / 2;
    const step = slides.length > 1 ? centerOf(slides[1]) - centerOf(slides[0]) : 1;
    const ds: number[] = [];
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => {
      const d = (centerOf(s) - mid) / step;
      ds.push(d);
      if (Math.abs(d) < bestDist) { bestDist = Math.abs(d); best = i; }
    });
    const progress = track.scrollLeft / Math.max(1, track.scrollWidth - track.clientWidth);

    // Write phase.
    if (!rm.matches) {
      slides.forEach((s, i) => {
        const d = ds[i];
        const a = Math.min(Math.abs(d), 1.4);
        s.style.transform = `translate3d(0, ${(a * 22).toFixed(2)}px, 0) scale(${(1 - a * 0.13).toFixed(4)}) rotate(${(Math.max(-1.4, Math.min(1.4, d)) * -2.2).toFixed(2)}deg)`;
        s.style.opacity = (1 - Math.min(a, 1) * 0.55).toFixed(3);
      });
    }
    rail?.style.setProperty('--p', (progress * (slides.length - 1)).toFixed(4));
    if (best !== active) { active = best; setCaption(best); }
  };
  const schedule = () => { if (!raf) raf = requestAnimationFrame(frame); };

  const go = (i: number) => {
    pending = Math.max(0, Math.min(slides.length - 1, i));
    setArrows(pending);
    track.scrollTo({ left: centerOf(slides[pending]) - track.clientWidth / 2, behavior: rm.matches ? 'auto' : 'smooth' });
  };

  // Once scrolling has genuinely finished (including any CSS scroll-snap settling, or a native
  // touch swipe that never went through `go()` at all), drop `pending` and resync the arrows to
  // wherever the track actually landed. `nearest()` reads the real position at that moment, so it
  // is correct however long settling actually took, on Safari as much as anywhere else.
  const settle = () => { pending = null; setArrows(nearest()); };

  track.addEventListener('scroll', schedule, { passive: true });
  if ('onscrollend' in window) {
    track.addEventListener('scrollend', settle);
  } else {
    // Safari (all of it, including iOS) never fires `scrollend`, so fall back to a debounce.
    // Unlike a resync that trusts a stored index, `settle` re-reads the real scroll position, so a
    // debounce firing early or late under load still lands on the correct slide.
    track.addEventListener('scroll', () => {
      clearTimeout(settleTimer);
      settleTimer = window.setTimeout(settle, 180);
    }, { passive: true });
  }
  addEventListener('resize', schedule, { passive: true });
  prevBtn?.addEventListener('click', () => go((pending ?? nearest()) - 1));
  nextBtn?.addEventListener('click', () => go((pending ?? nearest()) + 1));
  track.addEventListener('keydown', (e) => {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    if (e.key === 'ArrowRight') { e.preventDefault(); go((pending ?? nearest()) + 1); }
    else if (e.key === 'ArrowLeft') { e.preventDefault(); go((pending ?? nearest()) - 1); }
    else if (e.key === 'Home') { e.preventDefault(); go(0); }
    else if (e.key === 'End') { e.preventDefault(); go(slides.length - 1); }
  });
  // Pointer capture during a mouse drag sends the click to the track, so find the slide under the pointer.
  track.addEventListener('click', (e) => {
    if (moved) return;
    const el = document.elementFromPoint(e.clientX, e.clientY)?.closest<HTMLElement>('.slide');
    const i = el ? slides.indexOf(el) : -1;
    if (i >= 0 && i !== active) go(i);
  });

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
    const mid = track.scrollLeft + track.clientWidth / 2 - velocity * 180;
    let best = 0;
    let bestDist = Infinity;
    slides.forEach((s, i) => { const d = Math.abs(centerOf(s) - mid); if (d < bestDist) { bestDist = d; best = i; } });
    go(best);
    track.classList.remove('dragging');
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

  setArrows(0);
  frame();
}

export {};
