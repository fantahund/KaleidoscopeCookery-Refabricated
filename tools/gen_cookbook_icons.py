#!/usr/bin/env python3
"""
Stations-Bilder fürs Kochbuch aus den JEI-Hintergründen des Mods (textures/gui/jei/*.png) ausschneiden.

Ausgabe:
- resourcepack/custom/assets/kaleidoscope/textures/gui/sprites/cookbook/<station>.png
  -> Sprite "kaleidoscope:cookbook/<station>" im Atlas minecraft:gui (kleine Bilder im Text und auf Knöpfen)
- resourcepack/custom/assets/kaleidoscope/font/cookbook.json
  -> große Bilder als Schriftzeichen U+E200 ... (Überschrift der Stations-Seiten),
     Slot-Kästchen U+E210 und Abstände U+E211/U+E212 fürs Werkbank-Raster
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
# Erstes Schriftzeichen der großen Bilder; Reihenfolge = CROPS. Ascent 7: das Bild beginnt oben an der Zeile
# und reicht nach unten (Dialoge schneiden alles über der ersten Zeile ab), darunter Leerzeilen freihalten
FIRST_CHAR = 0xE200
BIG_HEIGHT = 32
# Werkbank-Raster (siehe Cookbook#craftingGrid): Slot, 18 zurück, 1 Abstand, 4 Abstand, Pfeil, leerer Pfeil, leerer Slot
SLOT_CHAR = 0xE210
BACK_CHAR = 0xE211
GAP_CHAR = 0xE212
SPACE_CHAR = 0xE213
ARROW_CHAR = 0xE214
BLANK_ARROW_CHAR = 0xE215
BLANK_SLOT_CHAR = 0xE216
BLANK_DIAGRAM_CHAR = 0xE217
# Stations-Bilder im Rezept-Diagramm (Reihenfolge = CROPS)
DIAGRAM_CHAR = 0xE220
# Ziffern 0-9 für Mengen im Slot und 6/12/18 Pixel zurück
DIGIT_CHAR = 0xE240
DIGIT_BACK_CHAR = 0xE250
# Abstände +n / -n Pixel
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
        # Fast unsichtbarer Pixel unten rechts: der Client kürzt Schrift-Bilder sonst auf die sichtbare Breite
        square.putpixel((size - 1, size - 1), (0, 0, 0, 1))
        square.save(sprites / f"{name}.png")
        providers.append({"type": "bitmap", "file": f"kaleidoscope:gui/sprites/cookbook/{name}.png",
                          "height": BIG_HEIGHT, "ascent": 7, "chars": [chr(FIRST_CHAR + index)]})
        # Im Rezept-Diagramm: in der mittleren Reihe des Rasters, mittig über drei Slot-Reihen
        providers.append({"type": "bitmap", "file": f"kaleidoscope:gui/sprites/cookbook/{name}.png",
                          "height": BIG_HEIGHT, "ascent": 14, "chars": [chr(DIAGRAM_CHAR + index)]})
        print(f"{name}: {square.size[0]} px, Zeichen U+{FIRST_CHAR + index:04X}")
    # Werkbank-Raster: Slot (18x18 wie im Inventar), Pfeil, Abstände (siehe Cookbook#craftingGrid)
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
    # Mengen in den Slots wie im Inventar: Ziffern aus der Vanilla-Schrift (minecraft:font/ascii.png, nicht kopiert),
    # unten rechts im Slot; dazu Rückwärts-Abstände für 1 bis 3 Ziffern (je 6 Pixel)
    grid = []
    for row in range(16):
        line = ""
        for column in range(16):
            code = row * 16 + column
            line += chr(DIGIT_CHAR + code - 0x30) if 0x30 <= code <= 0x39 else "\u0000"
        grid.append(line)
    providers.append({"type": "bitmap", "file": "minecraft:font/ascii.png", "height": 8, "ascent": -2, "chars": grid})
    providers.append({"type": "space", "advances": {chr(DIGIT_BACK_CHAR + i): -6 * (i + 1) for i in range(3)}})
    # Ausgleich für Bilder mit anderer Breite: +n und -n Pixel (n = 0..63)
    providers.append({"type": "space", "advances": {**{chr(PAD_CHAR + n): n for n in range(64)},
                                                    **{chr(PAD_NEG_CHAR + n): -n for n in range(64)}}})
    (OUT / "font").mkdir(parents=True, exist_ok=True)
    (OUT / "font" / "cookbook.json").write_text(json.dumps({"providers": providers}, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
