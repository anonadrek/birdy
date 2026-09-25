"""Klipper ut rödhaken ur hero-bilden: robin-layer (full duk, alfa) + bakgrundsplatta (rödhaken borttagen)."""
import sys
import numpy as np
from PIL import Image
from rembg import remove, new_session

src, out = sys.argv[1], sys.argv[2]
model = sys.argv[3] if len(sys.argv) > 3 else 'isnet-general-use'
im = Image.open(src).convert('RGB')
W, H = im.size

# Beskär runt fågeln så modellen fokuserar på den
box = (980, 100, 1520, 700)
crop = im.crop(box)
sess = new_session(model)
cut = remove(crop, session=sess, post_process_mask=True)
a = np.array(cut)[:, :, 3]

full = np.zeros((H, W), np.uint8)
full[box[1]:box[3], box[0]:box[2]] = a
Image.fromarray(full).save(f'{out}/mask-{model}.png')
print('mask saved', model, 'coverage', (full > 128).sum())
