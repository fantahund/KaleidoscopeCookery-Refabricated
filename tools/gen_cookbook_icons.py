#!/usr/bin/env python3
"""
Cut the station images for the cookbook out of the mod's JEI backgrounds (textures/gui/jei/*.png).

Output:
- resourcepack/custom/assets/kaleidoscope/textures/gui/sprites/cookbook/<station>.png
  -> sprite "kaleidoscope:cookbook/<station>" in the atlas minecraft:gui (small images in text and on buttons)
- resourcepack/custom/assets/kaleidoscope/font/cookbook.json
  -> large images as font characters U+E200 ... (heading of the station pages),
     slot box U+E210 and spaces U+E211/U+E212 for the crafting grid
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
JEI = Path.home() / ".cache" / "kaleidoscope-pack" / "cookery" / "src" / "main" / "resources" / "assets" / "kaleidoscope_cookery" / "textures" / "gui" / "jei"
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope"

# Area of the station image in the JEI background (x1, y1, x2, y2 inclusive), found via connected component analysis
CROPS = {
    "pot": ("pot", (77, 23, 120, 77)),
    "stockpot": ("stockpot", (77, 15, 119, 77)),
    "steamer": ("steamer", (21, 19, 70, 61)),
    "chopping_board": ("chopping_board", (18, 21, 80, 54)),
    "teapot": ("teapot", (18, 4, 75, 65)),
    "millstone": ("millstone", (21, 12, 104, 85)),
    "bamboo_tray": ("bamboo_tray", (14, 15, 85, 55)),
}
# First font character of the large images; order = CROPS. Ascent 7: the image starts at the top of the line
# and extends downwards (dialogs cut off everything above the first line), keep empty lines free below it
FIRST_CHAR = 0xE200
BIG_HEIGHT = 32
# Crafting grid (see Cookbook#craftingGrid): slot, 18 back, 1 space, 4 space, arrow, empty arrow, empty slot
SLOT_CHAR = 0xE210
BACK_CHAR = 0xE211
GAP_CHAR = 0xE212
SPACE_CHAR = 0xE213
ARROW_CHAR = 0xE214
BLANK_ARROW_CHAR = 0xE215
BLANK_SLOT_CHAR = 0xE216
BLANK_DIAGRAM_CHAR = 0xE217
# Station images in the recipe diagram (order = CROPS)
DIAGRAM_CHAR = 0xE220
# Digits 0-9 for amounts in the slot and 6/12/18 pixels back
DIGIT_CHAR = 0xE240
DIGIT_BACK_CHAR = 0xE250
# Spaces +n / -n pixels
PAD_CHAR = 0xE300
PAD_NEG_CHAR = 0xE340


def main():
    sprites = OUT / "textures" / "gui" / "sprites" / "cookbook"
    sprites.mkdir(parents=True, exist_ok=True)
    providers = []
    for index, (name, (source, box)) in enumerate(CROPS.items()):
        image = Image.open(JEI / f"{source}.png").convert("RGBA")
        x1, y1, x2, y2 = box
        part = image.crop((x1, y1, x2 + 1, y2 + 1))
        size = max(part.size)
        square = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        square.paste(part, ((size - part.width) // 2, (size - part.height) // 2))
        # Almost invisible pixel at the bottom right: otherwise the client trims font images to the visible width
        square.putpixel((size - 1, size - 1), (0, 0, 0, 1))
        square.save(sprites / f"{name}.png")
        providers.append({"type": "bitmap", "file": f"kaleidoscope:gui/sprites/cookbook/{name}.png",
                          "height": BIG_HEIGHT, "ascent": 7, "chars": [chr(FIRST_CHAR + index)]})
        # In the recipe diagram: in the middle row of the grid, centred over three slot rows
        providers.append({"type": "bitmap", "file": f"kaleidoscope:gui/sprites/cookbook/{name}.png",
                          "height": BIG_HEIGHT, "ascent": 14, "chars": [chr(DIAGRAM_CHAR + index)]})
        print(f"{name}: {square.size[0]} px, Zeichen U+{FIRST_CHAR + index:04X}")
    # Crafting grid: slot (18x18 like in the inventory), arrow, spaces (see Cookbook#craftingGrid)
    slot = Image.new("RGBA", (18, 18), (139, 139, 139, 255))
    for i in range(18):
        slot.putpixel((i, 0), (55, 55, 55, 255))
        slot.putpixel((0, i), (55, 55, 55, 255))
        slot.putpixel((i, 17), (255, 255, 255, 255))
        slot.putpixel((17, i), (255, 255, 255, 255))
    slot.putpixel((0, 17), (139, 139, 139, 255))
    slot.putpixel((17, 0), (139, 139, 139, 255))
    slot.save(sprites / "slot.png")
    arrow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    gray = (139, 139, 139, 255)
    for x in range(1, 11):
        for y in range(7, 10):
            arrow.putpixel((x, y), gray)
    for i in range(5):
        for y in range(8 - i, 9 + i):
            arrow.putpixel((14 - i, y), gray)
    arrow.putpixel((15, 15), (0, 0, 0, 1))
    arrow.save(sprites / "arrow.png")
    providers.append({"type": "bitmap", "file": "kaleidoscope:gui/sprites/cookbook/slot.png", "height": 18, "ascent": 8, "chars": [chr(SLOT_CHAR)]})
    providers.append({"type": "bitmap", "file": "kaleidoscope:gui/sprites/cookbook/arrow.png", "height": 16, "ascent": 7, "chars": [chr(ARROW_CHAR)]})
    providers.append({"type": "space", "advances": {chr(BACK_CHAR): -18, chr(GAP_CHAR): 1, chr(SPACE_CHAR): 4,
                                                    chr(BLANK_ARROW_CHAR): 17, chr(BLANK_SLOT_CHAR): 19,
                                                    chr(BLANK_DIAGRAM_CHAR): 33}})
    # Amounts in the slots like in the inventory: digits from the vanilla font (minecraft:font/ascii.png, not copied),
    # at the bottom right of the slot; plus backward spaces for 1 to 3 digits (6 pixels each)
    grid = []
    for row in range(16):
        line = ""
        for column in range(16):
            code = row * 16 + column
            line += chr(DIGIT_CHAR + code - 0x30) if 0x30 <= code <= 0x39 else "\u0000"
        grid.append(line)
    providers.append({"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 8, "ascent": -2, "chars": grid})
    providers.append({"type": "space", "advances": {chr(DIGIT_BACK_CHAR + i): -6 * (i + 1) for i in range(3)}})
    # Compensation for images with a different width: +n and -n pixels (n = 0..63)
    providers.append({"type": "space", "advances": {**{chr(PAD_CHAR + n): n for n in range(64)},
                                                    **{chr(PAD_NEG_CHAR + n): -n for n in range(64)}}})
    (OUT / "font").mkdir(parents=True, exist_ok=True)
    (OUT / "font" / "cookbook.json").write_text(json.dumps({"providers": providers}, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
