"""Cuts grid/grid-row.png (3240 x 1440, rendered by render.mjs) into the three 1080 x 1440 tiles for the
Instagram and TikTok profile grid. A plain crop, so the tiles are pixel for pixel the same picture.

Run: node render.mjs .. grid-row   then   python split-grid.py
"""
from pathlib import Path

from PIL import Image

GRID = Path(__file__).parent.parent / 'grid'
row = Image.open(GRID / 'grid-row.png').convert('RGB')
assert row.size == (3240, 1440), row.size
for i, name in enumerate(['tile-1-left', 'tile-2-middle', 'tile-3-right']):
    tile = row.crop((i * 1080, 0, (i + 1) * 1080, 1440))
    tile.save(GRID / f'{name}.png', optimize=True)
    print(f'{name}.png {tile.size[0]} x {tile.size[1]}')
