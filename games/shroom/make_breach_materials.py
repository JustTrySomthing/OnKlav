"""Generate the Breach's tileable ceramic-composite wall and floor maps."""
from pathlib import Path

import numpy as np
from PIL import Image

ASSETS = Path(__file__).with_name("assets")
SIZE = 256
rng = np.random.default_rng(73213)
y, x = np.mgrid[:SIZE, :SIZE]
tau = np.pi * 2
grain = rng.normal(0, 1, (SIZE, SIZE))
mottle = np.sin(x * tau / 64 + np.sin(y * tau / 128)) * np.cos(y * tau / 64)
pits = np.maximum(0, -grain - 1.45)


def maps(name, height, luminance, color):
    rgb = np.clip(np.asarray(color) + luminance[:, :, None], 0, 255).astype("uint8")
    Image.fromarray(rgb).save(ASSETS / f"breach-{name}.png")
    dx = (np.roll(height, -1, 1) - np.roll(height, 1, 1)) * .5
    dy = (np.roll(height, -1, 0) - np.roll(height, 1, 0)) * .5
    normal = np.stack([-dx, dy, np.ones_like(dx)], axis=-1)
    normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
    Image.fromarray(np.round((normal * .5 + .5) * 255).astype("uint8")).save(
        ASSETS / f"breach-{name}-normal.png"
    )


edge = np.minimum(x % 128, 127 - x % 128)
seam = np.minimum(edge, np.minimum(y % 128, 127 - y % 128))
groove = np.maximum(0, 4 - seam)
chips = (grain > 1.15) * (seam < 9) * (seam > 2)
maps("wall", -groove*.35 - pits*.22 + mottle*.17,
     mottle*13 + grain*5 - groove*13 + chips*30, (174, 170, 164))

floor_seam = np.minimum.reduce([x % 128, 127 - x % 128, y % 128, 127 - y % 128])
tread = np.maximum(0, 3.1 - np.abs((x+y) % 16-8) - .46*np.abs((x-y) % 16-8))
groove = np.maximum(0, 4-floor_seam)
scuff = np.maximum(0, np.sin(tau*(x+2*y)/32) - .92) * (mottle > .30)
maps("floor", tread*.25 - groove*.33 + grain*.058,
     tread*8 - groove*14 + mottle*12 + scuff*85 + grain*4, (149, 154, 154))
