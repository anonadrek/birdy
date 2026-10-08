# Builds the image files for index.html (design preview 2026-10-08, the living app carousel).
#
# For each Swedish 1.3.0 screen (website/src/assets/screens/1.3.0/sv/) it writes:
#   img/<id>.webp        the screenshot, 720 x 1280
#   img/<id>-plate.webp  the same screenshot with the parts that move erased ("clean plate")
#   img/<id>-layer.webp  only where a moving part carries smaller moving parts (Match, Artprofil)
# and choreo.json, the piece list the page animates. Every moving piece is a slice of the real
# screenshot, so at rest the page shows the screenshot pixel for pixel. Coordinates are in the
# screenshot's own 1080 x 1920 pixels.
#
#   python make-assets.py            (from this folder; needs Pillow with WebP)
import json
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SRC = HERE.parents[4] / 'website' / 'src' / 'assets' / 'screens' / '1.3.0' / 'sv'
OUT = HERE / 'img'
DARK = (30, 30, 30)  # the app's dark header behind Match and Artprofil
NAV = {'id': 'nav', 'box': [0, 1668, 1080, 1920], 'anim': 'static'}

# anim: wipe (reveal left to right), write (soft-edged wipe, like ink), rise (dy up), slide (dx),
# fade, pop, grow (scaleX from origin), roll (rises inside its own box), stamp, sheet (slides up).
# erase: where the piece is erased: 'plate' (default) or 'layer'; how: v (fill between the rows
# above and below), above (repeat the row above), fill (DARK). src: 'o' screenshot, 'l' layer.
SCREENS = {
    '01-identifiera': [
        {'id': 'kick', 'box': [56, 1214, 350, 1252], 'anim': 'wipe', 'at': 0, 'dur': 520},
        {'id': 'h1', 'box': [58, 1280, 436, 1376], 'anim': 'rise', 'dy': 70, 'at': 140, 'dur': 640},
        {'id': 'h2', 'box': [436, 1276, 666, 1378], 'anim': 'write', 'at': 520, 'dur': 700},
        {'id': 'hand', 'box': [58, 1386, 882, 1446], 'anim': 'write', 'at': 900, 'dur': 1500},
        {'id': 'ornL', 'box': [56, 1484, 510, 1524], 'anim': 'grow', 'origin': 'right', 'at': 1500, 'dur': 600},
        {'id': 'flower', 'box': [512, 1484, 566, 1524], 'anim': 'pop', 'at': 1420, 'dur': 520},
        {'id': 'ornR', 'box': [566, 1484, 1024, 1524], 'anim': 'grow', 'origin': 'left', 'at': 1500, 'dur': 600},
        {'id': 'card', 'box': [18, 1600, 1062, 1668], 'anim': 'rise', 'dy': 66, 'at': 1750, 'dur': 700, 'how': 'above'},
        NAV,
    ],
    '07-lyssna': [
        {'id': 'top', 'box': [262, 684, 824, 744], 'anim': 'write', 'at': 100, 'dur': 1300},
        # The waveform is drawn by the page (canvas, the app's own WaveformBars formula), so it is
        # only erased here. The record button and the line under it are moved by the page while the
        # great tit plays ('hold': built as pieces, not part of the choreography).
        {'id': 'bars', 'box': [56, 876, 1024, 982], 'anim': 'none'},
        {'id': 'btn', 'box': [436, 1084, 644, 1292], 'anim': 'hold'},
        {'id': 'cta', 'box': [400, 1324, 690, 1378], 'anim': 'hold'},
    ],
    '02-match': [
        {'id': 'sheet', 'box': [0, 1021, 1080, 1668], 'src': 'l', 'anim': 'sheet', 'dy': 560, 'at': 0, 'dur': 900, 'how': 'fill'},
        {'id': 'bar', 'box': [54, 936, 250, 954], 'anim': 'wipe', 'at': 650, 'dur': 800, 'how': 'fill'},
        {'id': 'hand', 'box': [44, 1110, 214, 1150], 'anim': 'write', 'at': 820, 'dur': 700, 'erase': 'layer'},
        {'id': 'seal', 'box': [838, 1062, 1040, 1262], 'anim': 'stamp', 'at': 1300, 'dur': 560, 'erase': 'layer'},
        NAV,
    ],
    '03-mina-arter': [
        {'id': 'd1', 'box': [164, 224, 220, 298], 'anim': 'roll', 'at': 0, 'dur': 620},
        {'id': 'd2', 'box': [464, 224, 518, 298], 'anim': 'roll', 'at': 130, 'dur': 620},
        {'id': 'd3', 'box': [820, 224, 862, 298], 'anim': 'roll', 'at': 260, 'dur': 620},
        {'id': 'labels', 'box': [140, 322, 940, 354], 'anim': 'fade', 'at': 320, 'dur': 500},
        {'id': 'card1', 'box': [26, 386, 1054, 628], 'anim': 'slide', 'dx': -90, 'at': 440, 'dur': 700},
        {'id': 'card2', 'box': [34, 650, 1046, 892], 'anim': 'slide', 'dx': 90, 'at': 580, 'dur': 700},
        {'id': 'senaste', 'box': [34, 920, 1046, 1024], 'anim': 'fade', 'at': 780, 'dur': 500},
        {'id': 'r1', 'box': [20, 1150, 1070, 1316], 'anim': 'rise', 'dy': 50, 'at': 900, 'dur': 600},
        {'id': 'r2', 'box': [20, 1334, 1070, 1500], 'anim': 'rise', 'dy': 50, 'at': 1020, 'dur': 600},
        {'id': 'r3', 'box': [20, 1518, 1070, 1668], 'anim': 'rise', 'dy': 50, 'at': 1140, 'dur': 600, 'how': 'above'},
        NAV,
    ],
    '04-uppslagsverk': [
        {'id': 's1', 'box': [962, 1068, 1046, 1152], 'anim': 'stamp', 'at': 350, 'dur': 520},
        {'id': 's2', 'box': [962, 1246, 1046, 1330], 'anim': 'stamp', 'at': 640, 'dur': 520},
        {'id': 's3', 'box': [962, 1424, 1046, 1508], 'anim': 'stamp', 'at': 930, 'dur': 520},
        {'id': 's4', 'box': [962, 1602, 1046, 1668], 'anim': 'stamp', 'at': 1220, 'dur': 520, 'how': 'above'},
        NAV,
    ],
    '05-artprofil': [
        {'id': 'p1', 'box': [54, 1200, 218, 1262], 'anim': 'pop', 'at': 160, 'dur': 520, 'how': 'fill'},
        {'id': 'p2', 'box': [224, 1200, 464, 1262], 'anim': 'pop', 'at': 300, 'dur': 520, 'how': 'fill'},
        {'id': 'sheet', 'box': [0, 1306, 1080, 1668], 'src': 'l', 'anim': 'sheet', 'dy': 360, 'at': 420, 'dur': 820, 'how': 'fill'},
        {'id': 'kick2', 'box': [40, 1350, 318, 1386], 'anim': 'wipe', 'at': 1080, 'dur': 460, 'erase': 'layer'},
        {'id': 'l1', 'box': [44, 1438, 1026, 1540], 'anim': 'rise', 'dy': 30, 'at': 1220, 'dur': 520, 'erase': 'layer'},
        {'id': 'l2', 'box': [44, 1546, 956, 1600], 'anim': 'rise', 'dy': 30, 'at': 1360, 'dur': 520, 'erase': 'layer'},
        {'id': 'l3', 'box': [44, 1606, 982, 1658], 'anim': 'rise', 'dy': 30, 'at': 1500, 'dur': 520, 'erase': 'layer'},
        NAV,
    ],
}


def erase(img, box, how):
    x0, y0, x1, y1 = box
    px = img.load()
    for x in range(x0, x1):
        if how == 'fill':
            for y in range(y0, y1):
                px[x, y] = DARK
            continue
        top = px[x, y0 - 1]
        bot = px[x, min(y1, img.height - 1)] if how == 'v' else top
        n = y1 - y0
        for i, y in enumerate(range(y0, y1)):
            t = (i + 1) / (n + 1)
            px[x, y] = tuple(round(top[c] + (bot[c] - top[c]) * t) for c in range(3))


def save(img, name):
    img.resize((720, 1280), Image.LANCZOS).save(OUT / name, 'WEBP', quality=84, method=6)


def main():
    OUT.mkdir(exist_ok=True)
    choreo = {}
    for sid, pieces in SCREENS.items():
        orig = Image.open(SRC / f'{sid}.png').convert('RGB')
        plate = orig.copy()
        layer = orig.copy()
        for p in pieces:
            if p['anim'] == 'static':
                continue
            target = layer if p.get('erase') == 'layer' else plate
            erase(target, p['box'], p.get('how', 'v'))
        save(orig, f'{sid}.webp')
        save(plate, f'{sid}-plate.webp')
        if any(p.get('src') == 'l' for p in pieces):
            save(layer, f'{sid}-layer.webp')
        choreo[sid] = [{k: v for k, v in p.items() if k not in ('how', 'erase')} for p in pieces]
    data = json.dumps(choreo, separators=(',', ':'))
    (HERE / 'choreo.json').write_text(data, encoding='utf-8')
    page = HERE / 'index.html'
    if page.exists():
        html = page.read_text(encoding='utf-8')
        start, end = html.index('/*CHOREO*/') + len('/*CHOREO*/'), html.index('/*END*/')
        page.write_text(html[:start] + data + html[end:], encoding='utf-8')
        print('index.html: choreography updated')
    for f in sorted(OUT.iterdir()):
        print(f'{f.name:32} {f.stat().st_size // 1024:5d} kB')


if __name__ == '__main__':
    main()
