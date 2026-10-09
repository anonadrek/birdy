// Flocken lyfter (spec docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md): once per page view, when
// the hero comes into sight, the 839 birds fly in from below left, under the words, and land as Birdy's bird; today's
// bird lands last and lights up, and Dagens fågel's photo lifts out of it, develops like a polaroid and is taped down.
// Then nothing moves: no loop and no timer is left (the ResizeObserver only redraws the still frame after a resize).
// The motion is the approved prototype's (docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html,
// version 3), on a canvas with one bitmap of the mark per colour, at most devicePixelRatio 2. Reduced motion draws the
// landed flock at once; without JavaScript the hero's <noscript> SVG shows the same frame. data-flock on the hero says
// where it is (waiting, flying, landed, done); the hero's CSS hides the polaroid until the flock has landed (review
// fix: by opacity, not visibility, so it stays in the accessibility tree throughout), gated on [data-flock-live] (set
// by run() below as its first act) so a CSS-only fallback can reveal the card on its own after about 3 s if this
// module never runs at all (review fix: a dropped connection, a parse failure). A keydown anywhere, or a focus
// landing inside the hero (a keyboard user tabbing through while still hidden), skips straight to the end.
import { MARK } from './flock-data.mjs';
import { COLOURS, DISC_SCALE, LIT, LIT_SCALE, fitView, flightPlan } from './flock.mjs';

type Plan = ReturnType<typeof flightPlan>;
type Flyer = Plan['birds'][number];

/** The mark drawn at 12 % of its 1000-unit width (about 120 px): sharp at every bird size. */
const SPRITE_K = 0.12;
/** ms: the first frame already shows the river of birds arriving. */
const HEAD_START = 450;
/** ms: today's bird's ring pops once when it has landed. */
const RING_MS = 480;

const easeOut = (u: number) => 1 - Math.pow(1 - u, 3);
const easeOutBack = (k: number) => 1 + 2.70158 * Math.pow(k - 1, 3) + 1.70158 * Math.pow(k - 1, 2);

function run(hero: HTMLElement, canvas: HTMLCanvasElement, fitBox: HTMLElement, polaroid: HTMLElement, ctx: CanvasRenderingContext2D) {
  // Read before marking the hero live (next line): once [data-flock-live] is set, Hero.astro's CSS swaps from the
  // fallback animation to the live hide rule, which would force the computed opacity back to 0 and hide that the
  // fallback had already revealed the card (review fix). Marking the hero live is otherwise the first act of run(),
  // so the fallback's own CSS rule stops applying as soon as the module is actually in charge.
  const fallbackAlreadyRevealed = parseFloat(getComputedStyle(polaroid).opacity) > 0;
  hero.dataset.flockLive = '';
  const setState = (state: 'flying' | 'landed' | 'done') => {
    hero.dataset.flock = state;
    if (state === 'done') {
      document.removeEventListener('keydown', onKeydown);
      hero.removeEventListener('focusin', skipToEnd);
    }
  };
  const litIndex = Number(hero.dataset.flockIndex);
  const mark = new Path2D(MARK.path);
  // One small bitmap of the mark per colour; every bird is a rotated, scaled copy.
  const sprite = (colour: string): HTMLCanvasElement => {
    const c = document.createElement('canvas');
    c.width = Math.ceil(MARK.w * SPRITE_K) + 2;
    c.height = Math.ceil(MARK.h * SPRITE_K) + 2;
    const g = c.getContext('2d');
    if (g) {
      g.scale(SPRITE_K, SPRITE_K);
      g.fillStyle = colour;
      g.fill(mark);
    }
    return c;
  };
  const sprites = COLOURS.map(sprite);
  const litSprite = sprite(LIT.bird);

  let dpr = 1;
  let t0: number | null = null;
  let raf = 0;
  let io: IntersectionObserver | null = null;
  /** The reveal's own animations, while they are running (state 'landed'): a skip then jumps them to their end. */
  let revealRuns: Animation[] = [];
  // The canvas covers the hero; the flock fits the [data-flock-fit] box, the same box as the <noscript> SVG.
  const layout = (): Plan => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    const c = canvas.getBoundingClientRect();
    const f = fitBox.getBoundingClientRect();
    canvas.width = Math.round(c.width * dpr);
    canvas.height = Math.round(c.height * dpr);
    const fit = fitView({ left: f.left - c.left, top: f.top - c.top, width: f.width, height: f.height });
    return flightPlan({ fit, width: c.width, height: c.height, litIndex });
  };
  let plan = layout();

  const drawBird = (x: number, y: number, size: number, rot: number, img: HTMLCanvasElement, alpha: number) => {
    const k = (size / MARK.w / SPRITE_K) * dpr;
    const a = (rot * Math.PI) / 180;
    const cos = Math.cos(a) * k;
    const sin = Math.sin(a) * k;
    ctx.globalAlpha = alpha;
    ctx.setTransform(cos, sin, -sin, cos, x * dpr, y * dpr);
    ctx.drawImage(img, -MARK.cx * SPRITE_K, -MARK.cy * SPRITE_K);
  };

  const fly = (p: Flyer, t: number, scale: number, img: HTMLCanvasElement) => {
    const u = (t - p.delay) / p.dur;
    if (u <= 0) return;
    if (u >= 1) {
      drawBird(p.fx, p.fy, p.size * scale, p.rot, img, p.op);
      return;
    }
    const e = easeOut(u);
    const m = 1 - e;
    // A quadratic curve from the start, low under the words, to the landing place.
    let x = m * m * p.sx + 2 * m * e * p.cx + e * e * p.fx;
    let y = m * m * p.sy + 2 * m * e * p.cy + e * e * p.fy;
    const dx = 2 * m * (p.cx - p.sx) + 2 * e * (p.fx - p.cx);
    const dy = 2 * m * (p.cy - p.sy) + 2 * e * (p.fy - p.cy);
    const len = Math.hypot(dx, dy) || 1;
    // A shared wave across neighbours: the flock moves like one body, then calms as it lands.
    const wave = p.amp * Math.sin(2 * Math.PI * ((p.freq * t) / 1000 + p.phase)) * m * m;
    x += (-dy / len) * wave;
    y += (dx / len) * wave;
    const heading = (Math.atan2(dy, dx) * 180) / Math.PI;
    drawBird(x, y, p.size * scale * (0.55 + 0.45 * e), p.rot + m * heading * 0.35, img, Math.min(1, u * 6) * p.op);
  };

  const clear = () => {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  };
  const drawFlock = () => {
    for (const p of plan.birds) drawBird(p.fx, p.fy, p.size, p.rot, sprites[p.colour], p.op);
  };
  // Today's bird on its cream disc with the copper ring; `ring` grows the disc from 0 to 1 when it lands.
  const drawLit = (ring: number) => {
    const lit = plan.lit;
    ctx.globalAlpha = 1;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.beginPath();
    ctx.arc(lit.fx, lit.fy, lit.size * DISC_SCALE * ring, 0, Math.PI * 2);
    ctx.fillStyle = LIT.disc;
    ctx.fill();
    ctx.lineWidth = Math.max(1.8, lit.size * 0.14);
    ctx.strokeStyle = LIT.ring;
    ctx.stroke();
    drawBird(lit.fx, lit.fy, lit.size * LIT_SCALE, lit.rot, litSprite, 1);
  };
  const drawStill = () => {
    clear();
    drawFlock();
    drawLit(1);
  };

  // Dagens fågel arrives after the flock: the photo lifts out of the lit bird and flies to its place, develops like a
  // polaroid, and the tape lands on it.
  const reveal = () => {
    const img = polaroid.querySelector('img');
    const caption = polaroid.querySelector('figcaption');
    const tape = polaroid.querySelector<HTMLElement>('.tape');
    const c = canvas.getBoundingClientRect();
    const r = polaroid.getBoundingClientRect();
    const dx = plan.lit.fx - (r.left - c.left + r.width / 2);
    const dy = plan.lit.fy - (r.top - c.top + r.height / 2);
    const runs: Animation[] = [
      polaroid.animate([
        { transform: `translate(${dx.toFixed(1)}px, ${dy.toFixed(1)}px) scale(.06) rotate(-16deg)`, opacity: 0 },
        { opacity: 1, offset: 0.18 },
        { transform: 'translate(0px, 0px) scale(1.035) rotate(3.4deg)', offset: 0.76 },
        { transform: 'translate(0px, 0px) scale(1) rotate(2deg)', opacity: 1 },
      ], { duration: 1100, delay: 140, easing: 'cubic-bezier(.22, .9, .24, 1)', fill: 'both' }),
    ];
    if (img) {
      runs.push(img.animate([
        { filter: 'saturate(0) brightness(1.55) contrast(.7)', opacity: 0.3 },
        { filter: 'saturate(0) brightness(1.55) contrast(.7)', opacity: 0.3, offset: 0.3 },
        { filter: 'none', opacity: 1 },
      ], { duration: 2000, delay: 140, easing: 'ease-out', fill: 'both' }));
    }
    if (caption) runs.push(caption.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 1150, fill: 'both' }));
    // The tape is part of the card, which leans 2 degrees, so -8 here is -6 on the page.
    if (tape) {
      runs.push(tape.animate([
        { transform: 'translateY(-18px) rotate(-18deg) scale(1.25)', opacity: 0 },
        { transform: 'translateY(0px) rotate(-8deg) scale(1)', opacity: 0.94 },
      ], { duration: 360, delay: 1100, easing: 'cubic-bezier(.3, 1.5, .5, 1)', fill: 'both' }));
    }
    revealRuns = runs;
    // Landed: the animations let go (the CSS rest state is the same frame), so nothing is left running.
    const settle = () => {
      for (const a of runs) a.cancel();
      revealRuns = [];
      setState('done');
    };
    Promise.all(runs.map((a) => a.finished)).then(settle, settle);
  };

  // Draws the flock at elapsed time t (ms since the flight started); true once the ring has fully popped, so the
  // caller knows nothing more needs to be scheduled. Kept separate from frame() so a mid-flight resize can redraw this
  // exact instant at once (review fix: canvas.width/height resets and clears the canvas, so without an immediate
  // redraw in the same task there is one blank frame until the next natural tick).
  const renderFrame = (t: number): boolean => {
    if (t < plan.total) {
      clear();
      for (const p of plan.birds) fly(p, t, 1, sprites[p.colour]);
      fly(plan.lit, t, 1.25, litSprite);
      return false;
    }
    // Landed: the flock is still; today's bird pops its ring once, and its photo comes out of it.
    const k = Math.min(1, (t - plan.total) / RING_MS);
    clear();
    drawFlock();
    drawLit(Math.max(0, easeOutBack(k)));
    if (hero.dataset.flock === 'flying') {
      setState('landed');
      reveal();
    }
    return k >= 1;
  };

  const frame = (now: number) => {
    if (t0 === null) t0 = now - HEAD_START;
    const t = now - t0;
    raf = renderFrame(t) ? 0 : requestAnimationFrame(frame);
  };

  // A keyboard user (keydown anywhere, or focus landing inside the hero while tabbing through) skips straight to the
  // end: the polaroid stays in the accessibility tree throughout (opacity, not visibility, Hero.astro), so it can be
  // reached before the flight has finished; this jumps the visuals to match. Never wired to pointer, touch or wheel:
  // touch-scrolling past the hero on a phone must not cut the motion short.
  const skipToEnd = () => {
    const state = hero.dataset.flock;
    if (state === 'landed') {
      // The reveal is already under way: jump its animations to their end (each one's `finished` promise then
      // resolves, which runs the normal settle() above and reaches done) instead of cutting it off mid-reveal.
      for (const a of revealRuns) a.finish();
      return;
    }
    if (state !== 'waiting' && state !== 'flying') return;
    io?.disconnect();
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    drawStill();
    setState('done');
  };

  // Only a plain keypress counts as "the user is doing something": a bare modifier (Shift/Control/Alt/Meta) is not a
  // skip on its own, and a Ctrl/Alt/Meta combo is a browser or OS shortcut, not page navigation (Shift alone, as in
  // Shift+Tab, still skips). While waiting (the hero off-screen) a keydown is ignored outright: the same keys are
  // ordinary page-scrolling there, and a keyboard user actually tabbing into the hero early is already covered by the
  // focusin listener below.
  const onKeydown = (e: KeyboardEvent) => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key === 'Shift' || e.key === 'Control' || e.key === 'Alt' || e.key === 'Meta') return;
    if (hero.dataset.flock === 'waiting') return;
    skipToEnd();
  };

  // A new size moves the fit box: lay the flock out again. canvas.width/height resets (clears) the canvas, so the
  // current instant is redrawn at once, in the same task, rather than leaving a blank frame until the next natural
  // tick, but ONLY while the loop is actually still running (raf): once a skip has stopped it mid-flight, t0 is still
  // set (review fix), so recomputing "now - t0" would redraw a stray mid-air instant on top of the already-landed
  // still picture rather than keep it. Once the loop has stopped, landed/done redraws the finished still picture.
  if ('ResizeObserver' in window) {
    let pending = 0;
    new ResizeObserver(() => {
      if (pending) return;
      pending = requestAnimationFrame(() => {
        pending = 0;
        plan = layout();
        if (raf && t0 !== null) renderFrame(performance.now() - t0);
        else if (hero.dataset.flock === 'landed' || hero.dataset.flock === 'done') drawStill();
      });
    }).observe(hero);
  }

  // The fallback above (Hero.astro) may already have revealed the card if this module loaded slowly but not never:
  // run() still gets to run, just too late to matter, and the flight must not disappear the card and replay it.
  if (matchMedia('(prefers-reduced-motion: reduce)').matches || fallbackAlreadyRevealed) {
    drawStill();
    setState('done');
  } else {
    document.addEventListener('keydown', onKeydown);
    hero.addEventListener('focusin', skipToEnd);
    if ('IntersectionObserver' in window) {
      // Once, when the hero reaches the middle band of the viewport: a hero much taller than the viewport (a short,
      // wide window) never reaches a fixed share of its OWN area visible, so the threshold watches the root's shrunk
      // area instead (review fix: the old { threshold: 0.2 } never fired for a hero over five viewports tall). The
      // inset is capped in px (review fix): on a very tall viewport, 30 % of its height can exceed the hero's own
      // height, so a percentage-only inset would again never reach it.
      const m = Math.min(innerHeight * 0.3, 200);
      io = new IntersectionObserver((entries) => {
        if (!entries.some((e) => e.isIntersecting)) return;
        io?.disconnect();
        setState('flying');
        raf = requestAnimationFrame(frame);
      }, { rootMargin: `-${m}px 0px -${m}px 0px`, threshold: 0 });
      io.observe(hero);
    } else {
      setState('flying');
      raf = requestAnimationFrame(frame);
    }
  }
}

const hero = document.querySelector<HTMLElement>('[data-hero]');
const canvas = hero?.querySelector<HTMLCanvasElement>('[data-flock-canvas]');
const fitBox = hero?.querySelector<HTMLElement>('[data-flock-fit]');
const polaroid = hero?.querySelector<HTMLElement>('[data-polaroid]');
const ctx = canvas?.getContext('2d');
if (hero && canvas && fitBox && polaroid && ctx) {
  // The hero must never stay empty: anything that throws during setup falls back to the done state at once (review
  // fix), the same state the page would be in if the flight had already finished.
  try {
    run(hero, canvas, fitBox, polaroid, ctx);
  } catch {
    hero.dataset.flock = 'done';
  }
} else if (hero) hero.dataset.flock = 'done';
