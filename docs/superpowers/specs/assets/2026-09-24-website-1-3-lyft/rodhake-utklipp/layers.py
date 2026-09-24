"""Bygger lagren: rodhake (beskuren, alfa) + platta (rodhaken ifylld) + matt for position."""
import sys, json
import numpy as np
import cv2
from PIL import Image, ImageFilter

src, maskp, out = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert('RGB')
W, H = im.size
m = np.array(Image.open(maskp).convert('L'))

# Rodhakelagret: beskuret till fagelns ruta + marginal
ys, xs = np.where(m > 8)
pad = 6
x0, y0, x1, y1 = xs.min() - pad, ys.min() - pad, xs.max() + pad + 1, ys.max() + pad + 1
rgba = np.dstack([np.array(im), m])
Image.fromarray(rgba[y0:y1, x0:x1]).save(f'{out}/robin-layer.webp', quality=88, method=6)
Image.fromarray(rgba[y0:y1, x0:x1]).save(f'{out}/robin-layer.png')

# Plattan: fyll i dar fageln satt (maske utvidgad), mjuka upp ifyllnaden sa den liknar bokeh
dil = cv2.dilate((m > 8).astype(np.uint8) * 255, np.ones((17, 17), np.uint8))
bgr = cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)
filled = cv2.inpaint(bgr, dil, 9, cv2.INPAINT_TELEA)
blur = cv2.GaussianBlur(filled, (0, 0), 14)
soft = cv2.GaussianBlur(dil, (0, 0), 6).astype(np.float32)[..., None] / 255
plate = (filled * (1 - soft) + blur * soft).astype(np.uint8)
plate_rgb = cv2.cvtColor(plate, cv2.COLOR_BGR2RGB)
Image.fromarray(plate_rgb).save(f'{out}/robin-plate.jpg', quality=86, optimize=True, progressive=True)
Image.fromarray(plate_rgb).save(f'{out}/robin-plate.webp', quality=95, method=6)

# Fotterna: lagsta punkten dar benen moter grenen (for transform-origin)
feet_y = ys.max()
feet_x = xs[ys > feet_y - 30].mean()
info = {
    'W': W, 'H': H,
    'box': [int(x0), int(y0), int(x1), int(y1)],
    'box_pct': [round(x0 / W * 100, 3), round(y0 / H * 100, 3), round((x1 - x0) / W * 100, 3), round((y1 - y0) / H * 100, 3)],
    'origin_pct_in_box': [round((feet_x - x0) / (x1 - x0) * 100, 2), round((feet_y - y0) / (y1 - y0) * 100, 2)],
}
json.dump(info, open(f'{out}/layers.json', 'w'), indent=1)
print(json.dumps(info))
