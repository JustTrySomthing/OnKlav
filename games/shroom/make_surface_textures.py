"""Generate Shroom's tileable environment and fungal surface maps."""
from pathlib import Path
import math
import random

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent / "assets"
SIZE = 256
rng = random.Random(7204)


def surface(base, pattern):
    image = Image.new("RGB", (SIZE, SIZE))
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            shade = pattern(x, y) + rng.randint(-7, 7)
            pixels[x, y] = tuple(max(0, min(255, channel + shade)) for channel in base)
    return image


def plate(x, y):
    inset = min(x % 128, 127 - x % 128, y % 64, 63 - y % 64)
    seam = -66 if inset < 2 else (-29 if inset < 4 else (18 if inset == 4 else 0))
    rust = 12 * math.sin(x * math.tau / 64 + math.sin(y * math.tau / 32))
    return int(seam + rust + 5 * math.sin(y * math.tau / 16))


wall = surface((165, 157, 151), plate)
draw = ImageDraw.Draw(wall)
for y in range(8, SIZE, 64):
    for x in (10, 117, 138, 245):
        draw.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(84, 80, 78), outline=(179, 166, 151))
        draw.line((x - 1, y + 1, x + 1, y - 1), fill=(51, 53, 56))
wall.save(ASSETS / "foundry-panels.png")


def floor_plate(x, y):
    u, v = x % 128, y % 128
    inset = min(u, v, 127 - u, 127 - v)
    if inset < 5:
        return -65 if inset < 2 else (27 if inset == 3 else -18)
    rib = (u + (v // 16 % 2) * 8) % 16
    tread = abs(rib - 8) * 2 + abs(v % 16 - 8)
    return 28 if tread < 5 else (-17 if tread < 8 else int(8 * math.sin((x + y) * math.tau / 64)))


surface((147, 151, 153), floor_plate).save(ASSETS / "foundry-tread.png")


def fungal_skin(x, y):
    angle = x * math.tau / SIZE
    band = y * math.tau / SIZE
    veins = math.sin(15 * angle + 1.4 * math.sin(3 * band) + .6 * math.sin(6 * angle + band))
    speckle = math.sin(31 * angle + 12 * band) * math.sin(19 * band - 11 * angle)
    return int(19 * math.cos(3 * band) + 14 * veins + 11 * speckle)


surface((213, 199, 183), fungal_skin).save(ASSETS / "fungal-skin.png")
