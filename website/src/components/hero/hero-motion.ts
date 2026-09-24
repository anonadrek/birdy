// Scroll-driven motion for the first view: photo parallax, the Birdy bird's flight towards the
// robin, and the robin's answering bob. Progressive enhancement only; nothing here is needed to read the page.
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const hero = document.querySelector<HTMLElement>('[data-hero]');
const scene = document.querySelector<HTMLElement>('[data-scene]');
const bird = document.querySelector<HTMLElement>('[data-birdy]');
const bob = document.querySelector<HTMLElement>('[data-robin-bob]');

if (hero && !reduce) {
  const FLIGHT = 320; // px of scroll over which the bird flies away
  let raf = 0;
  let armed = true;

  const update = () => {
    raf = 0;
    const y = Math.max(0, Math.min(scrollY, hero.offsetHeight));
    if (scene) scene.style.translate = `0 ${(y * 0.14).toFixed(1)}px`;
    if (!bird) return;
    const p = Math.min(y, FLIGHT) / FLIGHT;
    const dx = innerWidth * 0.55 * Math.pow(p, 0.6);
    bird.style.transform = `translate(${dx.toFixed(1)}px, ${(-p * 90).toFixed(1)}px) rotate(${(-p * 24).toFixed(1)}deg) scale(${(1 - p * 0.55).toFixed(3)})`;
    bird.style.opacity = String(1 - p * p);
    if (bob && armed && p > 0.35) {
      armed = false;
      bob.animate(
        [{ transform: 'none' }, { transform: 'scale(1.012, .962)', offset: 0.3 }, { transform: 'scale(.994, 1.022)', offset: 0.6 }, { transform: 'none' }],
        { duration: 520, easing: 'ease-out', composite: 'add' },
      );
    }
    if (p < 0.1) armed = true;
  };

  const schedule = () => { if (!raf) raf = requestAnimationFrame(update); };
  addEventListener('scroll', schedule, { passive: true });
  addEventListener('resize', schedule, { passive: true });
  update();
}

export {};
