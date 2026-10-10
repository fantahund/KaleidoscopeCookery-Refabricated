#!/usr/bin/env python3
"""
Sichuan pepper bush (own crop instead of the pepper tree from Grilling): 6 growth stages (16x16, cross model)
and a seed item, as pixel art in the style of the Grilling crops (colours from pepper_leaves_fruiting/sichuan_pepper).
Writes to ../KaleidoscopeGrilling/resourcepack/custom: textures, models, item definitions.

Usage: python3 -I tools/gen_pepper_bush.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT.parent / "KaleidoscopeGrilling" / "resourcepack" / "custom" / "assets" / "kaleidoscope_grilling"
STAGES = 6

STEM = [(66, 40, 26), (92, 58, 38), (112, 74, 46)]
LEAF = [(38, 70, 34), (52, 92, 40), (70, 120, 48), (100, 148, 58)]
FLOWER = [(232, 226, 196), (250, 246, 220), (214, 190, 120)]
UNRIPE = [(118, 150, 48), (150, 176, 62)]
RIPE = [(104, 22, 22), (160, 32, 32), (200, 52, 40), (232, 140, 82)]


def put(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color + (255,))


def stem(img, top: int, branches: list):
    """Main stem from the bottom up to top, branches as (y, direction, length)."""
    for y in range(15, top - 1, -1):
        put(img, 7, y, STEM[1] if y % 3 else STEM[2])
        if y > 12:
            put(img, 8, y, STEM[0])
    for y, direction, length in branches:
        for i in range(1, length + 1):
            put(img, 7 + direction * i, y - (i + 1) // 2, STEM[1])


def leaves(img, rng, centers: list, radius: float, density: float):
    """Irregular leaf clusters around the centres (pinnate pepper leaves: small patches, highlight at the top)."""
    for cx, cy in centers:
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + ((y - cy) * 1.15) ** 2) ** 0.5
                if d <= radius and rng.random() < density * (1.1 - d / (radius + 1)):
                    shade = 3 if y < cy - radius / 2 else 2 if y < cy else 1 if rng.random() < 0.6 else 0
                    put(img, x, y, LEAF[shade])


def dots(img, rng, centers: list, palette: list, count: int, cluster: bool):
    """Flowers or berries: single pixels or small clusters (2x2 with a highlight)."""
    spots = []
    for cx, cy in centers:
        for _ in range(count):
            spots.append((cx + rng.randint(-3, 3), cy + rng.randint(-3, 2)))
    for x, y in spots:
        if cluster:
            put(img, x, y, palette[1])
            put(img, x + 1, y, palette[2])
            put(img, x, y + 1, palette[0])
            put(img, x + 1, y + 1, palette[1])
            if rng.random() < 0.7:
                put(img, x + 1, y, palette[3])
        else:
            put(img, x, y, palette[rng.randint(0, len(palette) - 1)])


def stage(n: int) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rng = random.Random(1000 + min(n, 2))  # same bush from stage 2, only flowers/berries change
    if n == 0:
        stem(img, 12, [])
        for x, y in ((6, 12), (8, 12), (5, 11), (9, 11), (7, 11)):
            put(img, x, y, LEAF[2])
        put(img, 7, 10, LEAF[3])
        return img
    if n == 1:
        stem(img, 9, [(12, -1, 2), (11, 1, 2)])
        leaves(img, rng, [(7, 9), (5, 11), (10, 10)], 2.6, 0.9)
        return img
    stem(img, 4, [(13, -1, 4), (12, 1, 4), (9, -1, 3), (8, 1, 3)])
    centers = [(7, 5), (4, 8), (10, 7), (5, 11), (11, 11), (8, 9)]
    leaves(img, rng, centers, 3.9, 1.05)
    fruit = [(5, 7), (10, 6), (7, 10), (11, 10), (4, 11)]
    if n == 3:
        dots(img, random.Random(7), fruit, FLOWER, 3, False)
    elif n == 4:
        dots(img, random.Random(8), fruit, UNRIPE + [UNRIPE[0], UNRIPE[1]], 2, True)
    elif n >= 5:
        dots(img, random.Random(9), fruit, RIPE, 2, True)
    return img


def seeds() -> Image.Image:
    """Seeds: a few black, shiny peppercorns with burst red husks."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x, y in ((5, 6), (9, 5), (7, 9), (10, 10), (4, 10)):
        put(img, x, y, (34, 30, 30))
        put(img, x + 1, y, (52, 46, 44))
        put(img, x, y + 1, (24, 20, 20))
        put(img, x + 1, y + 1, (40, 34, 34))
        put(img, x + 1, y, (96, 90, 86))  # highlight
        put(img, x - 1, y + 1, RIPE[1])
        put(img, x + 2, y + 1, RIPE[2])
    return img


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    for n in range(STAGES):
        texture = OUT / "textures" / "block" / "crop" / "sichuan_pepper" / f"stage{n}.png"
        texture.parent.mkdir(parents=True, exist_ok=True)
        stage(n).save(texture)
        model = f"kaleidoscope_grilling:block/sichuan_pepper_crop_stage{n}"
        write_json(OUT / "models" / "block" / f"sichuan_pepper_crop_stage{n}.json",
                   {"parent": "minecraft:block/cross",
                    "textures": {"cross": f"kaleidoscope_grilling:block/crop/sichuan_pepper/stage{n}"}})
        write_json(OUT / "items" / "block" / f"sichuan_pepper_crop_stage{n}.json",
                   {"model": {"type": "minecraft:model", "model": model}})
    (OUT / "textures" / "item").mkdir(parents=True, exist_ok=True)
    seeds().save(OUT / "textures" / "item" / "sichuan_pepper_seeds.png")
    write_json(OUT / "models" / "item" / "sichuan_pepper_seeds.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "kaleidoscope_grilling:item/sichuan_pepper_seeds"}})
    write_json(OUT / "items" / "sichuan_pepper_seeds.json",
               {"model": {"type": "minecraft:model", "model": "kaleidoscope_grilling:item/sichuan_pepper_seeds"}})
    print(f"Pfefferstrauch: {STAGES} Stufen und Samen nach {OUT}")


if __name__ == "__main__":
    main()
