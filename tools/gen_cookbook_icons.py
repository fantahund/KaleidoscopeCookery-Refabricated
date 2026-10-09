#!/usr/bin/env python3
"""
Stations-Bilder fürs Kochbuch aus den JEI-Hintergründen des Mods (textures/gui/jei/*.png) ausschneiden.

Ausgabe:
- resourcepack/custom/assets/kaleidoscope/textures/gui/sprites/cookbook/<station>.png
  -> Sprite "kaleidoscope:cookbook/<station>" im Atlas minecraft:gui (kleine Bilder im Text und auf Knöpfen)
- resourcepack/custom/assets/kaleidoscope/font/cookbook.json
  -> große Bilder als Schriftzeichen U+E200 ... (Überschrift der Stations-Seiten)
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
JEI = Path.home() / ".cache" / "kaleidoscope-pack" / "cookery" / "src" / "main" / "resources" / "assets" / "kaleidoscope_cookery" / "textures" / "gui" / "jei"
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope"

# Bereich des Stations-Bildes im JEI-Hintergrund (x1, y1, x2, y2 inklusive), per Zusammenhangs-Analyse ermittelt
CROPS = {
    "pot": ("pot", (77, 23, 120, 77)),
    "stockpot": ("stockpot", (77, 15, 119, 77)),
    "steamer": ("steamer", (21, 19, 70, 61)),
    "chopping_board": ("chopping_board", (18, 21, 80, 54)),
    "teapot": ("teapot", (18, 4, 75, 65)),
    "millstone": ("millstone", (21, 12, 104, 85)),
    "bamboo_tray": ("bamboo_tray", (14, 15, 85, 55)),
}
# Erstes Schriftzeichen der großen Bilder; Reihenfolge = CROPS
FIRST_CHAR = 0xE200
BIG_HEIGHT = 32


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
        square.save(sprites / f"{name}.png")
        providers.append({"type": "bitmap", "file": f"kaleidoscope:gui/sprites/cookbook/{name}.png",
                          "height": BIG_HEIGHT, "ascent": BIG_HEIGHT - 8, "chars": [chr(FIRST_CHAR + index)]})
        print(f"{name}: {square.size[0]} px, Zeichen U+{FIRST_CHAR + index:04X}")
    (OUT / "font").mkdir(parents=True, exist_ok=True)
    (OUT / "font" / "cookbook.json").write_text(json.dumps({"providers": providers}, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
