#!/usr/bin/env python3
"""
Item image of the cookbook (own pixel art, 16x16): red linen book with golden corners, a ribbon bookmark and a
steaming bowl on the cover.
Output: resourcepack/custom/assets/kaleidoscope/{textures/item,models/item,items}/cookbook.*
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope"

# Colours
_ = None
O = (43, 20, 14, 255)       # outline
R = (150, 42, 33, 255)      # cover
r = (118, 30, 25, 255)      # cover dark (spine, shadow)
H = (185, 64, 48, 255)      # cover light (edge)
P = (240, 228, 196, 255)    # pages
p = (206, 190, 150, 255)    # pages shadow
G = (232, 184, 48, 255)     # Gold
g = (176, 128, 30, 255)     # gold dark
W = (236, 232, 226, 170)    # steam
L = (62, 120, 200, 255)     # ribbon bookmark

PIXELS = [
    [_, _, O, O, O, O, O, O, O, O, O, O, O, _, _, _],
    [_, O, G, H, H, H, H, H, H, H, H, G, O, O, _, _],
    [_, O, H, R, R, R, W, R, R, W, R, R, O, P, O, _],
    [_, O, r, R, R, W, R, R, W, R, R, R, O, P, O, _],
    [_, O, r, R, R, R, W, R, R, W, R, R, O, P, O, _],
    [_, O, r, R, G, G, G, G, G, G, R, R, O, P, O, _],
    [_, O, r, R, g, G, G, G, G, g, R, R, O, P, O, _],
    [_, O, r, R, R, g, G, G, g, R, R, R, O, P, O, _],
    [_, O, r, R, R, R, g, g, R, R, R, R, O, P, O, _],
    [_, O, r, R, R, R, R, R, R, R, R, R, O, P, O, _],
    [_, O, r, R, R, G, G, G, G, R, R, R, O, P, O, _],
    [_, O, r, R, R, R, R, R, R, R, R, R, O, P, O, _],
    [_, O, G, r, r, r, r, r, r, r, r, G, O, p, O, _],
    [_, _, O, O, O, O, O, L, O, O, O, O, O, p, O, _],
    [_, _, _, O, P, P, P, L, P, P, P, P, p, O, _, _],
    [_, _, _, _, O, O, O, L, O, O, O, O, O, _, _, _],
]


def main():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(PIXELS):
        for x, color in enumerate(row):
            if color:
                image.putpixel((x, y), color)
    (OUT / "textures" / "item").mkdir(parents=True, exist_ok=True)
    image.save(OUT / "textures" / "item" / "cookbook.png")
    (OUT / "models" / "item").mkdir(parents=True, exist_ok=True)
    (OUT / "models" / "item" / "cookbook.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "kaleidoscope:item/cookbook"}}, indent=2) + "\n", encoding="utf-8")
    (OUT / "items").mkdir(parents=True, exist_ok=True)
    (OUT / "items" / "cookbook.json").write_text(json.dumps(
        {"model": {"type": "minecraft:model", "model": "kaleidoscope:item/cookbook"}}, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
