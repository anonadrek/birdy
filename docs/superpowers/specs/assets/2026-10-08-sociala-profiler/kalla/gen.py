"""Data for Birdy's social profile artwork (2026-10-08, direction C "Flocken").

Reads the traced bird mark (birdy-bird.svg), rasterises it and writes data.js for brand-kit.html:
the mark's path, its smallest enclosing circle (to fit the mark in round crops) and the flocks: one small
bird for each species in Birdy's field guide (SPECIES, today 839; the artwork never prints the number).
Each flock has an edge of birds along the mark's outline (so the shape holds at 32 px), an even fill inside
it and, on the covers, a stream flying in plus a few birds out in front.

Run: python gen.py   (Python 3, numpy, Pillow). Deterministic: fixed seeds.
"""
import json
import math
import random
import re
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).parent
SVG = (HERE / 'birdy-bird.svg').read_text(encoding='utf-8')
PATH = re.search(r' d="([^"]+)"', SVG).group(1)
VB = [float(v) for v in re.search(r'viewBox="([^"]+)"', SVG).group(1).split()]
BW, BH = VB[2], VB[3]
SPECIES = 839  # species in Birdy's field guide today: one bird each


def flatten(d, steps=14):
    """Path (M, L, C, Z with absolute coordinates) to a polygon."""
    toks = re.findall(r'[MLCZ]|-?\d+\.?\d*', d)
    pts, i, cur, cmd = [], 0, (0.0, 0.0), None
    while i < len(toks):
        t = toks[i]
        if t in 'MLCZ':
            cmd = t
            i += 1
            if cmd == 'Z':
                continue
        if cmd in ('M', 'L'):
            cur = (float(toks[i]), float(toks[i + 1]))
            pts.append(cur)
            i += 2
        elif cmd == 'C':
            c1 = (float(toks[i]), float(toks[i + 1]))
            c2 = (float(toks[i + 2]), float(toks[i + 3]))
            e = (float(toks[i + 4]), float(toks[i + 5]))
            for s in range(1, steps + 1):
                u = s / steps
                a, b, c, k = (1 - u) ** 3, 3 * (1 - u) ** 2 * u, 3 * (1 - u) * u * u, u ** 3
                pts.append((a * cur[0] + b * c1[0] + c * c2[0] + k * e[0], a * cur[1] + b * c1[1] + c * c2[1] + k * e[1]))
            cur = e
            i += 6
    return pts


POLY = flatten(PATH)
SCALE = 2  # mask pixels per path unit
mask_img = Image.new('L', (int(BW * SCALE) + 2, int(BH * SCALE) + 2), 0)
ImageDraw.Draw(mask_img).polygon([(x * SCALE, y * SCALE) for x, y in POLY], fill=255)
MASK = np.array(mask_img) > 127


def inside_mask(mask, x, y):
    px, py = int(x * SCALE), int(y * SCALE)
    return 0 <= px < mask.shape[1] and 0 <= py < mask.shape[0] and bool(mask[py, px])


def inside(x, y):
    """Is the path point (x, y) inside the mark?"""
    return inside_mask(MASK, x, y)


def erode(mask, r):
    """Shrink the mark by about r mask pixels (alternating 4- and 8-neighbour steps, close to a disc)."""
    m = mask.copy()
    for i in range(int(r)):
        n = m & np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1)
        if i % 2:
            for dy in (1, -1):
                for dx in (1, -1):
                    n &= np.roll(np.roll(m, dy, 0), dx, 1)
        m = n
    return m


def hull(points):
    pts = sorted(set(points))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def enclosing_circle(points):
    """Smallest enclosing circle (Welzl, iterative), returns (cx, cy, r)."""
    pts = points[:]
    random.Random(7).shuffle(pts)

    def circle2(a, b):
        cx, cy = (a[0] + b[0]) / 2, (a[1] + b[1]) / 2
        return cx, cy, math.dist(a, (cx, cy))

    def circle3(a, b, c):
        ax, ay = a
        bx, by = b
        cx, cy = c
        d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        if abs(d) < 1e-9:
            return None
        ux = ((ax * ax + ay * ay) * (by - cy) + (bx * bx + by * by) * (cy - ay) + (cx * cx + cy * cy) * (ay - by)) / d
        uy = ((ax * ax + ay * ay) * (cx - bx) + (bx * bx + by * by) * (ax - cx) + (cx * cx + cy * cy) * (bx - ax)) / d
        return ux, uy, math.dist(a, (ux, uy))

    def ok(c, p):
        return c is not None and math.dist((c[0], c[1]), p) <= c[2] + 1e-7

    c = None
    for i, p in enumerate(pts):
        if ok(c, p):
            continue
        c = (p[0], p[1], 0.0)
        for j in range(i):
            q = pts[j]
            if ok(c, q):
                continue
            c = circle2(p, q)
            for k in range(j):
                s = pts[k]
                if ok(c, s):
                    continue
                c = circle3(p, q, s) or c
    return c


MEC = enclosing_circle(hull(POLY))
# The mark's flight line, tail tip to head: the small birds of a stream turn by (stream angle - this).
HEADING = math.degrees(math.atan2(230 - 955, 960 - 530))


def poisson(n_target, seed, mask):
    """Exactly n_target points spread evenly inside mask (path units): Bridson's disc sampling with a
    slightly small radius, then the most crowded points are removed one by one."""
    rng = random.Random(seed)
    area = mask.sum() / SCALE ** 2
    r = math.sqrt(area / n_target) * 0.76
    cell = r / math.sqrt(2)
    grid = {}
    pts, active = [], []
    while True:
        x, y = rng.uniform(0, BW), rng.uniform(0, BH)
        if inside_mask(mask, x, y):
            break
    pts.append((x, y))
    active.append(0)
    grid[(int(x / cell), int(y / cell))] = 0
    while active:
        idx = active[rng.randrange(len(active))]
        px, py = pts[idx]
        placed = False
        for _ in range(30):
            a = rng.uniform(0, 2 * math.pi)
            rr = rng.uniform(r, 2 * r)
            x, y = px + rr * math.cos(a), py + rr * math.sin(a)
            if not (0 <= x < BW and 0 <= y < BH) or not inside_mask(mask, x, y):
                continue
            gx, gy = int(x / cell), int(y / cell)
            near = any(
                (j := grid.get((ix, iy))) is not None and math.dist(pts[j], (x, y)) < r
                for ix in range(gx - 2, gx + 3) for iy in range(gy - 2, gy + 3)
            )
            if near:
                continue
            pts.append((x, y))
            active.append(len(pts) - 1)
            grid[(gx, gy)] = len(pts) - 1
            placed = True
            break
        if not placed:
            active.remove(idx)
    p = np.array(pts)
    if len(p) < n_target:
        raise SystemExit(f'only {len(p)} points for {n_target}; lower the radius factor')
    dm = np.sqrt(((p[:, None, :] - p[None, :, :]) ** 2).sum(-1))
    np.fill_diagonal(dm, np.inf)
    alive = np.ones(len(p), bool)
    while alive.sum() > n_target:
        nn = np.where(alive, dm.min(1), np.inf)
        k = int(nn.argmin())
        alive[k] = False
        dm[k, :] = np.inf
        dm[:, k] = np.inf
    return [tuple(v) for v in p[alive]]


def perimeter():
    return sum(math.dist(POLY[i], POLY[(i + 1) % len(POLY)]) for i in range(len(POLY)))


def outline(n, inset, seed):
    """n points evenly along the mark's edge, moved `inset` path units inwards: the birds that draw the edge."""
    rng = random.Random(seed)
    seg = [(POLY[i], POLY[(i + 1) % len(POLY)]) for i in range(len(POLY))]
    lens = [math.dist(a, b) for a, b in seg]
    total = sum(lens)
    step = total / n
    s, i, acc, out = rng.uniform(0, step), 0, 0.0, []
    while len(out) < n and s < total:
        while acc + lens[i] < s:
            acc += lens[i]
            i += 1
        (ax, ay), (bx, by) = seg[i]
        u = (s - acc) / lens[i] if lens[i] else 0
        x, y = ax + (bx - ax) * u, ay + (by - ay) * u
        tx, ty = (bx - ax) / (lens[i] or 1), (by - ay) / (lens[i] or 1)
        nx, ny = -ty, tx
        if not inside(x + nx * 3, y + ny * 3):
            nx, ny = -nx, -ny
        for f in (1, .75, .5, .3):
            qx, qy = x + nx * inset * f, y + ny * inset * f
            if inside(qx, qy):
                out.append((qx, qy))
                break
        s += step
    return out


def place(cx, cy, radius):
    """Path units to artboard pixels: the mark's enclosing circle lands on (cx, cy) with this radius."""
    k = radius / MEC[2]
    return lambda x, y: (cx + (x - MEC[0]) * k, cy + (y - MEC[1]) * k), k


def shade(x, y, rng):
    """0 = lit (brass), 1 = deep shadow: light from the upper left, with a little noise."""
    u = (x - MEC[0]) / MEC[2]
    v = (y - MEC[1]) / MEC[2]
    s = 0.48 + 0.32 * (0.55 * u + 0.85 * v) + rng.gauss(0, 0.16)
    return min(1.0, max(0.0, s))


def colour(s):
    # 0 brass, 1 copper, 2 rust-deep, 3 darkest rust (brand-kit.html maps them)
    return 0 if s < 0.14 else 1 if s < 0.6 else 2 if s < 0.9 else 3


def bez(p0, p1, p2, p3, t):
    a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
    return (a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1])


def flock(name, cx, cy, radius, seed, size_k, n_stream=0, curve=None, lead=()):
    """SPECIES birds: an edge along the mark's outline, an even fill inside it and optionally a stream flying in
    along a curve plus a few birds out in front. Each bird is [x, y, width, turn, colour, opacity]."""
    rng = random.Random(seed)
    to_px, k = place(cx, cy, radius)
    n_body = SPECIES - n_stream - len(lead)
    area = MASK.sum() / SCALE ** 2
    unit = math.sqrt(area / n_body)            # spacing inside the mark, path units
    base = unit * k * size_k                    # bird width, pixels
    edge = outline(int(perimeter() / (unit * 0.9)), unit * 0.4, seed)
    inner = poisson(n_body - len(edge), seed, erode(MASK, unit * 0.62 * SCALE))
    birds = []
    for (x, y) in inner:
        X, Y = to_px(x, y)
        birds.append([round(X, 1), round(Y, 1), round(base * rng.uniform(0.82, 1.1), 1), round(rng.gauss(0, 6), 1), colour(shade(x, y, rng)), 1])
    for (x, y) in edge:
        X, Y = to_px(x, y)
        birds.append([round(X, 1), round(Y, 1), round(base * rng.uniform(0.98, 1.14), 1), round(rng.gauss(0, 5), 1), colour(min(1, shade(x, y, rng) + 0.15)), 1])
    if n_stream:
        p0, p1, p2 = curve
        p3 = to_px(520, 760)
        phase = rng.uniform(0, math.pi)
        for _ in range(n_stream):
            r = rng.random()
            # Most of the stream bunches up where it reaches the bird; two looser pulses further back.
            if r < 0.58:
                t = rng.random() ** 0.55
            else:
                t = min(1.0, max(0.0, rng.gauss(0.4, 0.06) if r < 0.82 else rng.gauss(0.7, 0.045)))
            X, Y = bez(p0, p1, p2, p3, t)
            X2, Y2 = bez(p0, p1, p2, p3, min(1.0, t + 0.01))
            ang = math.atan2(Y2 - Y, X2 - X) if t < 0.99 else math.atan2(p3[1] - p2[1], p3[0] - p2[0])
            width = radius * (0.04 + 0.2 * math.sin(math.pi * min(1.0, t * 1.1))) * (1 - 0.55 * t ** 6)
            off = rng.gauss(0, width * 0.5) + radius * 0.05 * math.sin(t * math.pi * 3 + phase)
            X += -math.sin(ang) * off
            Y += math.cos(ang) * off
            rot = math.degrees(ang) - HEADING + rng.gauss(0, 8)
            size = base * (0.42 + 0.6 * t) * rng.uniform(0.88, 1.08)
            c = (0 if rng.random() < 0.4 else 1) if t < 0.45 else (1 if rng.random() < 0.7 else 2)
            birds.append([round(X, 1), round(Y, 1), round(size, 1), round(rot, 1), c, round(0.5 + 0.5 * t, 2)])
    for (x, y) in lead:
        X, Y = to_px(x, y)
        birds.append([round(X + rng.gauss(0, 5), 1), round(Y + rng.gauss(0, 5), 1), round(base * rng.uniform(1.05, 1.22), 1), round(rng.gauss(0, 5), 1), 1 if rng.random() < 0.7 else 2, 1])
    assert len(birds) == SPECIES, (name, len(birds))
    return {'birds': birds, 'big': {'cx': cx, 'cy': cy, 'r': radius}, 'edge': len(edge)}


LEAD = [(1150, 130), (1240, 60), (1215, 215), (1305, 150)]
FLOCKS = {
    'profile-flock': flock('profile', 540, 540, 505, 11, 1.42),
    'facebook-cover-flock': flock('facebook', 1162, 344, 300, 23, 1.42, 150, [(-80, 800), (380, 760), (760, 600)], LEAD),
    'youtube-banner-flock': flock('youtube', 846, 716, 214, 37, 1.34, 260, [(-140, 1340), (110, 1030), (440, 915)], LEAD[:3]),
}

out = {
    'path': PATH,
    'w': BW,
    'h': BH,
    'mec': {'cx': round(MEC[0], 2), 'cy': round(MEC[1], 2), 'r': round(MEC[2], 2)},
    'flocks': FLOCKS,
}
(HERE / 'data.js').write_text('window.BIRDY = ' + json.dumps(out, separators=(',', ':')) + ';\n', encoding='utf-8')
print('MEC', out['mec'], 'heading', round(HEADING, 1))
for k, v in FLOCKS.items():
    print(k, len(v['birds']), 'edge', v['edge'])
