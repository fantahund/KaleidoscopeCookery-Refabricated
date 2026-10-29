#!/usr/bin/env python3
"""
Generator for KaleidoscopeSmithing (addon for KaleidoscopeCookery):
- Generates 16x16 pixel-art textures for stations, molds, blanks, tool heads, handles, bindings,
  forged tools & weapons, and 1x2 heavy pickaxes.
- Generates 3D JSON block/part models for the 4 stations (Forge Crucible, Smithing Anvil, Quenching Basin,
  Tinker Workbench) and their dynamic parts (molten metal, glowing blanks, liquid, grindstone wheel).
- Generates item models & 26.3 item definitions (assets/kaleidoscope_smithing/items/*.json).
- Generates de_de.json and en_us.json translations.
- Generates src/main/resources/items.yml, recipes.yml, and generated/creative_tabs.yml.

Usage: python3 -I tools/gen_smithing.py
"""
import json
import math
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import smithing_sprites as sprites  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SMITHING_ROOT = ROOT.parent / "KaleidoscopeSmithing"
ASSETS = SMITHING_ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope_smithing"
MC_ASSETS = SMITHING_ROOT / "resourcepack" / "custom" / "assets" / "minecraft"
RESOURCES = SMITHING_ROOT / "src" / "main" / "resources"
NS = "kaleidoscope_smithing"

# Material palettes: 5 shades from darkest outline (0) to brightest highlight (4)
PALETTES = {
    "copper": [
        (88, 42, 28),
        (138, 66, 44),
        (184, 94, 62),
        (220, 126, 86),
        (246, 168, 128),
    ],
    "iron": [
        (58, 62, 68),
        (104, 110, 118),
        (156, 164, 172),
        (204, 212, 220),
        (242, 246, 250),
    ],
    "gold": [
        (110, 74, 18),
        (168, 118, 26),
        (222, 174, 44),
        (248, 216, 78),
        (255, 246, 154),
    ],
    "diamond": [
        (24, 92, 98),
        (38, 142, 148),
        (62, 198, 200),
        (116, 234, 230),
        (198, 252, 248),
    ],
    "netherite": [
        (28, 22, 30),
        (56, 44, 58),
        (86, 72, 90),
        (124, 108, 128),
        (176, 160, 178),
    ],
    "glowing": [
        (124, 28, 14),
        (186, 52, 18),
        (236, 104, 24),
        (252, 174, 46),
        (255, 238, 132),
    ],
    "clay": [
        (76, 80, 92),
        (108, 114, 128),
        (142, 148, 164),
        (172, 178, 194),
        (202, 208, 222),
    ],
    "fired_mold": [
        (78, 42, 32),
        (114, 62, 46),
        (148, 84, 64),
        (178, 108, 84),
        (204, 134, 108),
    ],
    "wood": [
        (56, 38, 20),
        (88, 60, 32),
        (122, 86, 46),
        (152, 110, 62),
        (178, 132, 78),
    ],
    "leather_grip": [
        (46, 24, 16),
        (82, 42, 26),
        (118, 64, 38),
        (150, 88, 52),
        (178, 114, 70),
    ],
    # Black leather wrapped with gold wire (gold tier and up, tongs)
    "reinforced_grip": [
        (18, 14, 18),
        (34, 26, 32),
        (52, 42, 50),
        (196, 146, 38),
        (250, 214, 96),
    ],
    # Designs (cosmetic, from loot): body, trim and grip of the tool take these colours
    "end_metal": [(40, 22, 50), (86, 54, 102), (136, 92, 152), (182, 140, 196), (228, 204, 234)],
    "end_accent": [(130, 126, 84), (176, 172, 118), (212, 210, 150), (236, 236, 184), (252, 252, 222)],
    "end_grip": [(14, 8, 18), (28, 18, 34), (46, 32, 54), (150, 90, 190), (214, 170, 240)],
    "nether_metal": [(16, 12, 16), (34, 28, 34), (56, 48, 56), (86, 76, 86), (126, 114, 124)],
    "nether_grip": [(30, 6, 10), (76, 14, 22), (120, 26, 34), (214, 160, 40), (250, 214, 96)],
    "sculk_metal": [(4, 16, 22), (10, 34, 44), (18, 58, 72), (36, 96, 112), (74, 150, 160)],
    "sculk_accent": [(0, 84, 104), (0, 134, 154), (36, 196, 206), (116, 236, 236), (216, 255, 255)],
    "sculk_grip": [(4, 12, 16), (10, 22, 28), (18, 36, 44), (40, 170, 180), (150, 240, 240)],
    "sculk_gem": [(0, 60, 74), (0, 120, 140), (36, 196, 206), (130, 244, 244), (230, 255, 255)],
    # Vote design (only from the vote case): royal blue with gold trim and a golden topaz
    "vote_metal": [(10, 18, 58), (22, 40, 112), (38, 70, 170), (78, 118, 220), (160, 196, 250)],
    "vote_grip": [(8, 12, 36), (16, 24, 66), (28, 40, 104), (214, 170, 40), (252, 226, 120)],
    "vote_gem": [(120, 70, 4), (196, 132, 10), (244, 196, 40), (255, 232, 130), (255, 252, 226)],
    # Gems set into the better tools: ruby (gold), emerald (diamond), glowing amethyst (netherite)
    "ruby": [
        (74, 6, 16),
        (140, 16, 34),
        (206, 34, 54),
        (246, 92, 104),
        (255, 206, 210),
    ],
    "emerald": [
        (6, 66, 32),
        (14, 124, 58),
        (34, 186, 92),
        (110, 236, 150),
        (214, 255, 226),
    ],
    "amethyst": [
        (54, 18, 92),
        (100, 40, 166),
        (156, 84, 230),
        (204, 150, 255),
        (244, 226, 255),
    ],
}

TIERS = [
    ("copper", "Kupfer", "Copper", "COPPER", "COPPER_INGOT"),
    ("iron", "Eisen", "Iron", "IRON", "IRON_INGOT"),
    ("gold", "Gold", "Golden", "GOLDEN", "GOLD_INGOT"),
    ("diamond", "Diamant", "Diamond", "DIAMOND", "DIAMOND"),
    ("netherite", "Netherit", "Netherite", "NETHERITE", "NETHERITE_INGOT"),
]

# The 1x2 heavy pickaxe only exists in these tiers; each needs a binding ring of its tier
HEAVY_TIERS = ("diamond", "netherite")
# Cast parts that are not tools: (kind, German name, English name)
PART_KINDS = [
    ("ring", "Verstärkungsring", "Binding Ring"),
    ("star_core", "Netherstern-Kern", "Nether Star Core"),
]

TOOL_KINDS = [
    ("sword", "Schwert", "Sword", "Schwertklinge", "Sword Blade", 2),
    ("pickaxe", "Spitzhacke", "Pickaxe", "Spitzhackenkopf", "Pickaxe Head", 3),
    ("axe", "Axt", "Axe", "Axtkopf", "Axe Head", 3),
    ("shovel", "Schaufel", "Shovel", "Schaufelblatt", "Shovel Head", 1),
    ("hoe", "Hacke", "Hoe", "Hackenblatt", "Hoe Head", 2),
    ("spear", "Speer", "Spear", "Speerspitze", "Spear Head", 1),
    ("heavy_pickaxe", "Stollenhammer (1x2)", "Heavy Pickaxe (1x2)", "Schwerer Bohrkopf", "Heavy Drill Head", 6),
    # Armor: cast plates, assembled on an armor padding (see ARMOR_KINDS)
    ("helmet", "Helm", "Helmet", "Helmplatte", "Helmet Plate", 5),
    ("chestplate", "Brustpanzer", "Chestplate", "Brustplatte", "Breastplate", 8),
    ("leggings", "Beinschutz", "Leggings", "Beinplatten", "Leg Plates", 7),
    ("boots", "Stiefel", "Boots", "Stiefelplatten", "Boot Plates", 4),
]
ARMOR_KINDS = ("helmet", "chestplate", "leggings", "boots")
# Cookware cast for KaleidoscopeCookery: (kind, German part, English part, tiers). The knife blade is assembled with a
# handle into the Cookery kitchen knife; wok and stockpot come out of the basin as the finished Cookery station.
COOKWARE_KINDS = [
    ("knife", "Küchenmesser-Klinge", "Kitchen Knife Blade", ("iron", "gold", "diamond", "netherite")),
    ("wok", "Wok", "Wok", ("iron",)),
    ("stockpot", "Suppentopf", "Stockpot", ("iron",)),
]
ARMOR_SLOTS = {"helmet": "head", "chestplate": "chest", "leggings": "legs", "boots": "feet"}


def new_img() -> Image.Image:
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img: Image.Image, x: int, y: int, color: tuple):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color[:3] + (color[3] if len(color) > 3 else 255,))


def draw_pixels(img: Image.Image, coords: list[tuple[int, int, int]], palette: list[tuple]):
    for x, y, shade in coords:
        put(img, x, y, palette[max(0, min(len(palette) - 1, shade))])


# ---- 16x16 Shaded Material Textures for 3D Weapon & Tool Models ----

def draw_material_texture(pal: list[tuple], variant: str = "mid", mat_name: str = "") -> Image.Image:
    """16x16 material texture ('mid', 'edge', 'dark'). Every model face samples it from (2, 2) on, so row and column 2
    carry a highlight that reads as a polished bevel on each part; the rest is a smooth, clean gradient."""
    img = new_img()
    for y in range(16):
        for x in range(16):
            lit = x == 2 or y == 2
            if mat_name == "wood":
                # Fine straight grain with a lighter top edge
                shade = 3 if lit else (1 if x % 4 == 0 else 2)
            elif mat_name == "leather_grip":
                # Diagonal leather wraps: dark seam, body, light ridge
                band = (x + y) % 4
                shade = 0 if band == 0 else (3 if band == 2 else 2)
            elif mat_name.endswith("_grip"):
                # Black leather with a thin gold wire spiral
                shade = 3 if (x + y) % 6 == 0 else (2 if (x + y) % 6 == 3 else 1)
            elif mat_name in ("ruby", "emerald", "amethyst", "sculk_gem", "vote_gem"):
                # Cut gem: one sparkle, saturated body (small faces only show the first pixels from (2, 2))
                shade = 4 if (x, y) == (2, 2) else (3 if (x, y) in ((3, 2), (2, 3)) else (2 if x + y < 10 else 1))
            elif mat_name == "glowing":
                shade = 3 if lit else (2 if (x + y) % 4 else 1)
            elif variant == "edge":
                # Honed edge: almost white highlight, bright body
                shade = 4 if lit or (x + y) % 9 == 0 else 3
            elif variant == "dark":
                shade = 2 if lit else 1
            else:
                # Polished body: highlight bevel, clean body, slightly darker towards the far end
                shade = 4 if lit and (x + y) < 12 else (3 if lit else (2 if y < 11 else 1 if y > 13 else 2))
            put(img, x, y, pal[shade])
    return img


def generate_material_textures():
    tex_dir = ASSETS / "textures" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, pal in PALETTES.items():
        draw_material_texture(pal, "mid", name).save(tex_dir / f"mat_{name}.png")
        draw_material_texture(pal, "edge", name).save(tex_dir / f"mat_{name}_edge.png")
        draw_material_texture(pal, "dark", name).save(tex_dir / f"mat_{name}_dark.png")


# ---- Shape definitions (16x16 coordinates, (x, y, shade 0..4)) ----

def handle_pixels(diagonal_len: int = 10) -> list[tuple[int, int, int]]:
    pts = []
    for i in range(diagonal_len):
        x = 1 + i
        y = 14 - i
        pts.append((x, y, 3 if i % 2 == 0 else 4))
        pts.append((x - 1, y, 2))
        pts.append((x, y + 1, 1))
        pts.append((x - 1, y - 1, 0))
        pts.append((x + 1, y + 1, 0))
    # Heavy Pommel at bottom-left (0..2, 13..15)
    pts.extend([
        (0, 15, 0), (1, 15, 2), (2, 15, 0),
        (0, 14, 2), (1, 14, 4), (2, 14, 1),
        (0, 13, 0), (1, 13, 1),
    ])
    return pts


def head_shape(kind: str) -> list[tuple[int, int, int]]:
    if kind == "sword":
        pts = []
        # Broad greatsword blade from (4, 11) all the way to (15, 0)
        for i in range(11):
            bx, by = 5 + i, 10 - i
            pts.append((bx, by, 4 if i > 3 else 3))       # Bright central ridge
            pts.append((bx - 1, by, 4))                    # Upper bevel edge
            pts.append((bx, by + 1, 2))                    # Lower bevel
            pts.append((bx - 2, by, 3))                    # Wide upper wing
            pts.append((bx, by + 2, 1))                    # Wide lower wing
            pts.append((bx - 2, by - 1, 0))                # Dark outline top
            pts.append((bx + 1, by + 2, 0))                # Dark outline bottom
        pts.extend([(15, 0, 4), (14, 0, 4), (15, 1, 3)])
        # Sweeping winged crossguard from (1, 6) to (9, 14)
        pts.extend([
            (2, 7, 3), (3, 8, 4), (4, 9, 4), (5, 10, 4), (6, 11, 4), (7, 12, 3), (8, 13, 2),
            (1, 6, 1), (2, 8, 2), (3, 9, 3), (5, 11, 3), (6, 12, 2), (7, 13, 1), (9, 14, 1),
        ])
        return pts
    if kind == "pickaxe":
        return [
            # Heavy socket & top spike
            (8, 4, 2), (9, 4, 3), (10, 4, 4), (11, 4, 3), (12, 3, 4), (13, 2, 4),
            (8, 5, 2), (9, 5, 4), (10, 5, 4), (11, 5, 3), (12, 4, 3),
            (9, 6, 2), (10, 6, 3), (11, 6, 2),
            # Long sweeping upper-left prong (from (9,4) out to (2,2))
            (7, 3, 4), (6, 2, 4), (5, 2, 4), (4, 2, 3), (3, 3, 3), (2, 4, 2),
            (7, 4, 3), (6, 3, 3), (5, 3, 3), (4, 3, 2), (3, 4, 1), (2, 5, 0),
            (6, 1, 1), (5, 1, 1), (4, 1, 0),
            # Long sweeping lower-right prong (from (11,6) out to (13,13))
            (12, 7, 4), (13, 8, 4), (13, 9, 4), (13, 10, 3), (12, 11, 3), (11, 12, 2),
            (11, 7, 3), (12, 8, 3), (12, 9, 3), (12, 10, 2), (11, 11, 1), (10, 12, 0),
            (14, 8, 1), (14, 9, 1), (14, 10, 0),
        ]
    if kind == "heavy_pickaxe":
        return [
            # Colossal warhammer & drill core
            (7, 4, 2), (8, 4, 3), (9, 4, 4), (10, 4, 4), (11, 4, 3), (12, 3, 4), (13, 2, 4),
            (7, 5, 2), (8, 5, 4), (9, 5, 4), (10, 5, 4), (11, 5, 3), (12, 4, 3),
            (8, 6, 2), (9, 6, 3), (10, 6, 4), (11, 6, 3),
            (9, 7, 1), (10, 7, 2), (11, 7, 2),
            # Massive flanged hammer head (top-left (2..7, 1..6))
            (3, 1, 2), (4, 1, 4), (5, 1, 4), (6, 2, 4), (7, 3, 3),
            (2, 2, 3), (3, 2, 4), (4, 2, 4), (5, 2, 3), (6, 3, 3), (7, 4, 2),
            (2, 3, 4), (3, 3, 4), (4, 3, 3), (5, 3, 2), (6, 4, 2),
            (1, 3, 2), (2, 4, 3), (3, 4, 3), (4, 4, 2), (5, 4, 1),
            (2, 5, 1), (3, 5, 2), (4, 5, 1),
            # Rear heavy earthbreaker wedge (bottom-right (11..14, 7..13))
            (12, 7, 4), (13, 8, 4), (14, 9, 4), (14, 10, 3), (13, 11, 3), (12, 12, 2),
            (11, 8, 3), (12, 8, 3), (13, 9, 3), (13, 10, 2), (12, 11, 2), (11, 12, 1),
            (11, 9, 1), (12, 9, 2), (12, 10, 1),
        ]
    if kind == "axe":
        return [
            # Socket & rear poll spike
            (8, 4, 2), (9, 4, 3), (10, 4, 4), (11, 3, 4), (12, 4, 3), (13, 5, 2),
            (8, 5, 2), (9, 5, 4), (10, 5, 4), (11, 4, 3), (12, 5, 2), (13, 6, 1),
            (9, 6, 2), (10, 6, 3), (11, 5, 2),
            # Massive bearded battleaxe crescent blade (2..8, 1..8)
            (4, 1, 3), (5, 1, 4), (6, 1, 4), (7, 2, 3), (8, 3, 3),
            (3, 2, 4), (4, 2, 4), (5, 2, 4), (6, 2, 3), (7, 3, 3), (8, 4, 2),
            (2, 3, 4), (3, 3, 4), (4, 3, 3), (5, 3, 3), (6, 3, 2), (7, 4, 2),
            (2, 4, 4), (3, 4, 4), (4, 4, 3), (5, 4, 2), (6, 4, 2), (7, 5, 1),
            (2, 5, 4), (3, 5, 3), (4, 5, 3), (5, 5, 2), (6, 5, 1),
            (3, 6, 4), (4, 6, 3), (5, 6, 2), (6, 6, 1),
            (4, 7, 3), (5, 7, 2), (6, 7, 0),
        ]
    if kind == "shovel":
        return [
            # Broad ribbed war spade head (8..15, 1..8)
            (10, 1, 3), (11, 1, 4), (12, 1, 4), (13, 1, 4), (14, 2, 3),
            (9, 2, 3), (10, 2, 4), (11, 2, 4), (12, 2, 4), (13, 2, 4), (14, 3, 3),
            (8, 3, 2), (9, 3, 3), (10, 3, 4), (11, 3, 3), (12, 3, 4), (13, 3, 3), (14, 4, 2),
            (8, 4, 2), (9, 4, 3), (10, 4, 3), (11, 4, 4), (12, 4, 3), (13, 4, 2), (14, 5, 1),
            (7, 5, 1), (8, 5, 2), (9, 5, 3), (10, 5, 3), (11, 5, 3), (12, 5, 2), (13, 5, 1),
            (8, 6, 1), (9, 6, 2), (10, 6, 3), (11, 6, 2), (12, 6, 1),
            (9, 7, 1), (10, 7, 2), (11, 7, 1),
        ]
    if kind == "hoe":
        return [
            # Long sweeping scythe / reaper blade
            (9, 4, 2), (10, 4, 4), (11, 4, 4), (12, 4, 3), (13, 5, 2),
            (9, 5, 2), (10, 5, 3), (11, 5, 4), (12, 5, 2),
            (5, 1, 3), (6, 1, 4), (7, 2, 4), (8, 3, 4), (9, 3, 3),
            (3, 2, 4), (4, 2, 4), (5, 2, 4), (6, 2, 3), (7, 3, 3), (8, 4, 2),
            (2, 3, 4), (3, 3, 4), (4, 3, 3), (5, 3, 2), (6, 3, 2), (7, 4, 1),
            (1, 4, 3), (2, 4, 3), (3, 4, 2), (4, 4, 1),
            (1, 5, 2), (2, 5, 1),
        ]
    return []


def draw_tool_item(kind: str, tier: str) -> Image.Image:
    img = new_img()
    grip_pal = PALETTES["reinforced_grip"] if kind == "heavy_pickaxe" or tier in ("diamond", "netherite") else PALETTES["leather_grip"]
    hbar_len = 5 if kind == "sword" else 10
    draw_pixels(img, handle_pixels(hbar_len), grip_pal)
    draw_pixels(img, head_shape(kind), PALETTES[tier])
    accent = PALETTES["gold"][3] if tier in ("diamond", "netherite", "iron") else PALETTES["iron"][3]
    if kind == "sword":
        put(img, 5, 10, accent)
        put(img, 4, 9, accent)
    elif kind == "heavy_pickaxe":
        put(img, 9, 5, accent)
        put(img, 10, 5, accent)
    else:
        put(img, 9, 5, accent)
    return img


def draw_head_item(kind: str, pal: list[tuple]) -> Image.Image:
    img = new_img()
    for x, y, shade in head_shape(kind):
        put(img, max(1, min(14, x - 1)), max(1, min(14, y + 2)), pal[shade])
    return img


def draw_smithing_hammer() -> Image.Image:
    img = new_img()
    draw_pixels(img, handle_pixels(10), PALETTES["wood"])
    head = [
        (5, 1, 2), (6, 1, 4), (7, 2, 4), (8, 3, 4), (9, 4, 3), (10, 5, 3), (11, 6, 4), (12, 7, 3), (13, 8, 2),
        (4, 2, 3), (5, 2, 4), (6, 2, 4), (7, 3, 3), (8, 4, 3), (9, 5, 3), (10, 6, 3), (11, 7, 3), (12, 8, 2), (13, 9, 1),
        (3, 3, 4), (4, 3, 4), (5, 3, 3), (6, 3, 3), (7, 4, 2), (8, 5, 2), (9, 6, 2), (10, 7, 2), (11, 8, 2), (12, 9, 1),
        (3, 4, 2), (4, 4, 3), (5, 4, 2), (6, 4, 2), (7, 5, 1), (8, 6, 1), (9, 7, 1), (10, 8, 1), (11, 9, 0),
    ]
    draw_pixels(img, head, PALETTES["iron"])
    put(img, 8, 4, PALETTES["gold"][3])
    put(img, 9, 5, PALETTES["gold"][3])
    return img


def draw_smithing_tongs() -> Image.Image:
    img = new_img()
    pal = PALETTES["iron"]
    pts = [
        (1, 13, 1), (2, 12, 2), (3, 11, 3), (4, 10, 3), (5, 9, 3), (6, 8, 3), (7, 7, 3),
        (3, 15, 1), (4, 14, 2), (5, 13, 3), (6, 12, 3), (7, 11, 3), (7, 9, 3), (8, 8, 3),
        (7, 8, 4), (8, 7, 4),
        (8, 6, 3), (9, 5, 4), (10, 4, 4), (11, 3, 4), (12, 2, 3), (13, 2, 2), (13, 3, 1),
        (9, 7, 3), (10, 6, 3), (11, 6, 3), (12, 5, 4), (13, 4, 3), (14, 3, 2), (13, 2, 1),
    ]
    draw_pixels(img, pts, pal)
    put(img, 7, 8, PALETTES["gold"][3])
    return img


# ---- 3D Volumetric Weapon, Tool & Tool Head Model Builders (Tier-Progressive & Sleek) ----

TIER_RANK = {"copper": 0, "iron": 1, "gold": 2, "diamond": 3, "netherite": 4, "glowing": 2}


def get_tool_3d_display(kind: str, tier: str) -> dict:
    rank = TIER_RANK.get(tier, 1)
    if kind in ("sword", "spear"):
        # Perfectly center each sword tier's [y_min..y_max] midpoint on the 45° GUI diagonal so neither pommel nor tip clips!
        gui_params = {
            0: (-2.2, -2.0, 0.85),  # Copper (Y=1.0..24.8)
            1: (-2.9, -2.6, 0.78),  # Iron (Y=0.4..28.4)
            2: (-3.2, -2.9, 0.75),  # Gold (Y=0.0..29.8)
            3: (-3.4, -3.1, 0.72),  # Diamond (Y=0.0..31.2)
            4: (-3.5, -3.2, 0.70),  # Netherite (Y=0.0..31.95)
        }
        gx, gy, g_scale = gui_params.get(rank, (-2.9, -2.6, 0.78))
        # In-hand scale: since the 3D sword is built vertically up to Y=25..31.95, a scale of ~0.58..0.62 makes its
        # world length only minimally larger than a vanilla sword!
        tp_s = round(0.64 - rank * 0.015, 3)
        fp_s = round(0.50 - rank * 0.01, 3)
        return {
            "gui": {"rotation": [18, -24, -45], "translation": [gx, gy, 0], "scale": [g_scale, g_scale, g_scale]},
            "ground": {"rotation": [0, 0, -45], "translation": [0, 2.2, 0], "scale": [0.42, 0.42, 0.42]},
            "fixed": {"rotation": [0, 0, -45], "translation": [gx, gy, 0], "scale": [g_scale, g_scale, g_scale]},
            "thirdperson_righthand": {"rotation": [0, -90, 20], "translation": [0, 2.6, 0.8], "scale": [tp_s, tp_s, tp_s]},
            "thirdperson_lefthand": {"rotation": [0, 90, -20], "translation": [0, 2.6, 0.8], "scale": [tp_s, tp_s, tp_s]},
            "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 2.2, 1.13], "scale": [fp_s, fp_s, fp_s]},
            "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 2.2, 1.13], "scale": [fp_s, fp_s, fp_s]},
        }
    return {
        "gui": {"rotation": [20, -28, -45], "translation": [-1.3, -1.5, 0], "scale": [0.74, 0.74, 0.74]},
        "ground": {"rotation": [0, 0, -45], "translation": [0, 2.8, 0], "scale": [0.52, 0.52, 0.52]},
        "fixed": {"rotation": [0, 0, -45], "translation": [-1.2, -1.3, 0], "scale": [0.76, 0.76, 0.76]},
        "thirdperson_righthand": {"rotation": [0, -90, 15], "translation": [0, 3.2, 0.9], "scale": [0.78, 0.78, 0.78]},
        "thirdperson_lefthand": {"rotation": [0, 90, -15], "translation": [0, 3.2, 0.9], "scale": [0.78, 0.78, 0.78]},
        "firstperson_righthand": {"rotation": [0, -90, -15], "translation": [1.13, 2.6, 1.13], "scale": [0.64, 0.64, 0.64]},
        "firstperson_lefthand": {"rotation": [0, 90, 15], "translation": [1.13, 2.6, 1.13], "scale": [0.64, 0.64, 0.64]},
    }


HEAD_3D_DISPLAY = {
    "gui": {"rotation": [20, -28, -35], "translation": [0, -5.2, 0], "scale": [0.90, 0.90, 0.90]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.52, 0.52, 0.52]},
    "fixed": {"rotation": [0, 0, -35], "translation": [0, -5.0, 0], "scale": [0.88, 0.88, 0.88]},
    "thirdperson_righthand": {"rotation": [45, -45, 0], "translation": [0, -2.0, 0], "scale": [0.58, 0.58, 0.58]},
    "firstperson_righthand": {"rotation": [0, -45, 0], "translation": [0, -2.5, 0], "scale": [0.52, 0.52, 0.52]},
}


def rbox(from_xyz, to_xyz, tex: str, angle: float = 0.0, origin=None, axis: str = "z", top_tex: str | None = None):
    elem = box(from_xyz, to_xyz, tex, top_tex)
    x1, y1, z1 = from_xyz
    x2, y2, z2 = to_xyz
    dx = min(16.0, max(1.0, round(abs(x2 - x1), 2)))
    dy = min(16.0, max(1.0, round(abs(y2 - y1), 2)))
    dz = min(16.0, max(1.0, round(abs(z2 - z1), 2)))
    for side, fobj in elem["faces"].items():
        if side in ("north", "south"):
            fobj["uv"] = [2, 2, min(16.0, 2 + dx), min(16.0, 2 + dy)]
        elif side in ("west", "east"):
            fobj["uv"] = [2, 2, min(16.0, 2 + dz), min(16.0, 2 + dy)]
        else:
            fobj["uv"] = [2, 2, min(16.0, 2 + dx), min(16.0, 2 + dz)]
    if angle != 0.0 and origin is not None:
        elem["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return elem


def build_3d_handle_elements(kind: str, tier: str = "iron", top_y: float | None = None) -> list[dict]:
    rank = TIER_RANK.get(tier, 1)
    if kind == "sword":
        if rank == 0:
            # Copper: Simple, humble wooden pommel & plain leather-wrapped hilt (no gold/gems)
            return [
                rbox([6.9, 1.0, 7.2], [9.1, 2.2, 8.8], "wood"),
                rbox([7.3, 2.2, 7.45], [8.7, 6.6, 8.55], "grip"),
            ]
        if rank == 1:
            # Iron: Classic knight's wheel pommel + leather grip with 2 subtle iron bands
            return [
                rbox([6.6, 0.4, 7.0], [9.4, 2.2, 9.0], "metal"),
                rbox([7.0, 0.7, 6.8], [9.0, 1.9, 9.2], "dark"),
                rbox([7.25, 2.2, 7.4], [8.75, 6.6, 8.6], "grip"),
                rbox([7.1, 3.2, 7.3], [8.9, 3.6, 8.7], "metal"),
                rbox([7.1, 5.2, 7.3], [8.9, 5.6, 8.7], "metal"),
            ]
        # Gold / Diamond / Netherite: Sculpted ornate pommel with jewel/gold core & ribbed grip
        elems = [
            rbox([6.4, 0.2, 6.9], [9.6, 2.2, 9.1], "dark"),
            rbox([6.9, 0.5, 6.7], [9.1, 1.9, 9.3], "accent"),
            rbox([7.3, 0.0, 7.3], [8.7, 0.4, 8.7], "edge"),
            rbox([7.2, 2.2, 7.35], [8.8, 6.6, 8.65], "grip"),
            rbox([7.0, 2.8, 7.25], [9.0, 3.2, 8.75], "accent"),
            rbox([7.0, 4.2, 7.25], [9.0, 4.6, 8.75], "accent"),
            rbox([7.0, 5.6, 7.25], [9.0, 6.0, 8.75], "accent"),
        ]
        if rank >= 4:
            # Netherite: Menacing side pommel claws
            elems.append(rbox([5.7, 0.6, 7.3], [6.5, 2.0, 8.7], "edge", angle=22.5, origin=[6.4, 1.2, 8.0]))
            elems.append(rbox([9.5, 0.6, 7.3], [10.3, 2.0, 8.7], "edge", angle=-22.5, origin=[9.6, 1.2, 8.0]))
        return elems

    if kind == "spear":
        # Long spear shaft like vanilla: wrapped grip in the lower middle, butt cap at the end
        elems = [
            rbox([7.2, -14.0, 7.2], [8.8, 16.8, 8.8], "wood" if rank <= 2 else "grip"),
            rbox([7.0, -4.0, 7.0], [9.0, 2.0, 9.0], "grip"),
            rbox([6.9, -14.8, 6.9], [9.1, -13.6, 9.1], "dark" if rank == 0 else "accent"),
        ]
        if rank >= 1:
            elems.append(rbox([6.9, -4.4, 6.9], [9.1, -3.8, 9.1], "accent"))
            elems.append(rbox([6.9, 1.8, 6.9], [9.1, 2.4, 9.1], "accent"))
        return elems

    # Tool shafts: Sturdier, thicker shafts (1.70 units main shaft, 2.04 grip wrap) extending down to Y = -2.2!
    if top_y is None:
        top_y = 14.4 if kind == "shovel" else 16.8  # ends inside the collar, never flush with its top
    if rank == 0:
        # Copper: Sturdy wooden stick shaft (1.64 thick, extended down to Y = -1.8) with a leather wrap (1.92 thick)
        return [
            rbox([7.18, -1.8, 7.18], [8.82, top_y, 8.82], "wood"),
            rbox([7.04, 0.8, 7.04], [8.96, 5.8, 8.96], "grip"),
        ]
    elems = [
        # Sturdy metal pommel cap at bottom (2.50 thick, Y = -2.2..-0.6)
        rbox([6.75, -2.2, 6.75], [9.25, -0.7, 9.25], "dark"),
        rbox([6.92, -0.7, 6.92], [9.08, -0.2, 9.08], "accent"),
        # Sturdy main shaft (1.70 units thick, from Y = -0.3 up to top_y)
        rbox([7.15, -0.3, 7.15], [8.85, top_y, 8.85], "wood" if rank <= 2 else "grip"),
        # Ergonomic lower grip wrap (2.04 units thick) + accent ferrules (2.28 units thick)
        rbox([6.98, 0.8, 6.98], [9.02, 7.2, 9.02], "grip"),
        rbox([6.86, 0.6, 6.86], [9.14, 1.1, 9.14], "accent"),
        rbox([6.86, 6.9, 6.86], [9.14, 7.4, 9.14], "accent"),
    ]
    if kind == "heavy_pickaxe" or rank >= 3:
        elems.append(rbox([6.92, 10.9, 6.92], [9.08, 11.5, 9.08], "accent"))
    if kind == "shovel" and rank >= 3:
        # High-tier shovel gets a reinforced D-ring pommel grip
        elems.append(rbox([6.2, -2.4, 6.9], [9.8, -0.5, 9.1], "accent"))
    return elems


def build_3d_head_elements(kind: str, tier: str = "iron") -> list[dict]:
    rank = TIER_RANK.get(tier, 1)

    if kind == "spear":
        # Leaf-shaped spear head on a socket; longer, broader and more ornate with the tier. Every layer has its own
        # thickness (blade 1.6, edge 1.3, tip 1.0, point 0.7, wings 1.4, lugs 1.0) so no faces share a plane.
        # iron gets side lugs and a fuller, gold swept wings and a gold socket, diamond a gold ridge, netherite gold bands
        tip = 22.0 + rank * 1.6
        hw = 1.6 + rank * 0.18
        elems = [
            rbox([6.8, 14.0, 6.9], [9.2, 17.0, 9.1], "accent" if rank >= 2 else "dark"),
            rbox([7.2, 17.0, 7.15], [8.8, 17.6, 8.85], "dark"),
            rbox([round(8 - hw, 2), 17.6, 7.2], [round(8 + hw, 2), round(tip - 3.0, 2), 8.8], "metal"),
            rbox([round(8 - hw + 0.5, 2), round(tip - 3.0, 2), 7.35], [round(8 + hw - 0.5, 2), round(tip - 1.4, 2), 8.65], "edge"),
            rbox([7.3, round(tip - 1.4, 2), 7.5], [8.7, round(tip - 0.3, 2), 8.5], "edge"),
            rbox([7.6, round(tip - 0.3, 2), 7.65], [8.4, round(tip + 0.5, 2), 8.35], "edge"),
        ]
        if rank >= 1:
            elems.append(rbox([7.5, 17.8, 7.05], [8.5, round(tip - 3.4, 2), 8.95], "accent" if rank >= 3 else "dark"))
            elems.append(rbox([round(8 - hw - 1.2, 2), 17.0, 7.5], [round(8 - hw, 2), 17.8, 8.5], "edge"))
            elems.append(rbox([round(8 + hw, 2), 17.0, 7.5], [round(8 + hw + 1.2, 2), 17.8, 8.5], "edge"))
        if rank >= 2:
            # Swept-up wings below the blade
            elems.append(rbox([round(8 - hw - 2.6, 2), 16.4, 7.3], [round(8 - hw, 2), 17.2, 8.7], "metal", angle=-22.5, origin=[round(8 - hw, 2), 16.8, 8.0]))
            elems.append(rbox([round(8 + hw, 2), 16.4, 7.3], [round(8 + hw + 2.6, 2), 17.2, 8.7], "metal", angle=22.5, origin=[round(8 + hw, 2), 16.8, 8.0]))
        if rank >= 4:
            elems.append(rbox([round(8 - hw - 0.1, 2), 17.7, 7.1], [round(8 + hw + 0.1, 2), 18.3, 8.9], "accent"))
            elems.append(rbox([round(8 - hw + 0.3, 2), round(tip - 3.2, 2), 7.1], [round(8 + hw - 0.3, 2), round(tip - 2.7, 2), 8.9], "accent"))
        return elems

    if kind == "sword":
        if rank == 0:
            # Tier 0 (Copper): Simple, humble Shortsword / Gladius (Y up to 24.8, narrow & unadorned)
            return [
                rbox([4.8, 6.5, 7.15], [11.2, 7.6, 8.85], "dark"),
                rbox([5.6, 6.7, 7.05], [10.4, 7.4, 8.95], "metal"),
                # Matching Y-bands (7.6..14.0, 14.0..19.5) for spine & blade so 3D sorting is 100% exact
                rbox([7.25, 7.6, 7.56], [8.75, 14.0, 8.44], "metal"),
                rbox([6.35, 7.6, 7.82], [9.65, 14.0, 8.18], "edge"),
                rbox([7.25, 14.0, 7.56], [8.75, 19.5, 8.44], "metal"),
                rbox([6.35, 14.0, 7.82], [9.65, 19.5, 8.18], "edge"),
                rbox([6.65, 19.5, 7.82], [9.35, 21.6, 8.18], "edge"),
                rbox([7.05, 21.6, 7.82], [8.95, 23.4, 8.18], "edge"),
                rbox([7.5, 23.4, 7.84], [8.5, 24.8, 8.16], "edge"),
            ]
        if rank == 1:
            # Tier 1 (Iron): Sleek Knight's Longsword (Y up to 28.4, cruciform guard + dark fuller + fine taper)
            return [
                rbox([6.7, 6.3, 6.95], [9.3, 7.9, 9.05], "dark"),
                rbox([3.2, 6.6, 7.25], [12.8, 7.5, 8.75], "metal"),
                rbox([2.6, 6.4, 7.15], [3.6, 7.7, 8.85], "edge"),
                rbox([12.4, 6.4, 7.15], [13.4, 7.7, 8.85], "edge"),
                # Matching Y-bands (7.7..14.5, 14.5..21.5)
                rbox([7.35, 7.7, 7.52], [8.65, 14.5, 8.48], "dark"),
                rbox([6.0, 7.7, 7.82], [10.0, 14.5, 8.18], "edge"),
                rbox([7.35, 14.5, 7.52], [8.65, 21.5, 8.48], "dark"),
                rbox([6.0, 14.5, 7.82], [10.0, 21.5, 8.18], "edge"),
                rbox([6.3, 21.5, 7.82], [9.7, 23.8, 8.18], "edge"),
                rbox([6.7, 23.8, 7.82], [9.3, 25.8, 8.18], "edge"),
                rbox([7.15, 25.8, 7.82], [8.85, 27.3, 8.18], "edge"),
                rbox([7.55, 27.3, 7.84], [8.45, 28.4, 8.16], "edge"),
            ]
        if rank == 2:
            # Tier 2 (Gold): Ornate Royal Broadsword (Y up to 29.8, swept winged guard + parrying prongs)
            return [
                rbox([6.5, 6.2, 6.85], [9.5, 8.2, 9.15], "dark"),
                rbox([2.8, 6.6, 7.25], [7.2, 7.6, 8.75], "metal", angle=-22.5, origin=[7.0, 7.1, 8.0]),
                rbox([8.8, 6.6, 7.25], [13.2, 7.6, 8.75], "metal", angle=22.5, origin=[9.0, 7.1, 8.0]),
                rbox([5.4, 7.4, 7.45], [6.1, 10.4, 8.55], "edge"),
                rbox([9.9, 7.4, 7.45], [10.6, 10.4, 8.55], "edge"),
                # Matching Y-bands (7.8..15.2, 15.2..22.5)
                rbox([7.35, 7.8, 7.52], [8.65, 15.2, 8.48], "dark"),
                rbox([5.85, 7.8, 7.82], [10.15, 15.2, 8.18], "edge"),
                rbox([7.35, 15.2, 7.52], [8.65, 22.5, 8.48], "dark"),
                rbox([5.85, 15.2, 7.82], [10.15, 22.5, 8.18], "edge"),
                rbox([6.2, 22.5, 7.82], [9.8, 25.0, 8.18], "edge"),
                rbox([6.65, 25.0, 7.82], [9.35, 27.2, 8.18], "edge"),
                rbox([7.1, 27.2, 7.82], [8.9, 28.7, 8.18], "edge"),
                rbox([7.55, 28.7, 7.84], [8.45, 29.8, 8.16], "edge"),
            ]
        if rank == 3:
            # Tier 3 (Diamond): Mighty Crystalline Greatsword (Y up to 31.2, crystal wings + ricasso spikes)
            return [
                rbox([6.4, 6.1, 6.8], [9.6, 8.4, 9.2], "accent"),
                rbox([1.6, 6.6, 7.2], [7.2, 7.7, 8.8], "dark", angle=-22.5, origin=[7.0, 7.1, 8.0]),
                rbox([8.8, 6.6, 7.2], [14.4, 7.7, 8.8], "dark", angle=22.5, origin=[9.0, 7.1, 8.0]),
                rbox([0.8, 7.2, 7.45], [3.6, 8.2, 8.55], "edge", angle=-22.5, origin=[7.0, 7.1, 8.0]),
                rbox([12.4, 7.2, 7.45], [15.2, 8.2, 8.55], "edge", angle=22.5, origin=[9.0, 7.1, 8.0]),
                # Ricasso parrying lugs (angled crystal side spikes at Y = 10.5)
                rbox([4.2, 10.2, 7.6], [6.8, 11.2, 8.4], "edge", angle=-22.5, origin=[6.5, 10.7, 8.0]),
                rbox([9.2, 10.2, 7.6], [11.8, 11.2, 8.4], "edge", angle=22.5, origin=[9.5, 10.7, 8.0]),
                # Matching Y-bands (8.0..15.5, 15.5..23.2) for dark spine, gold ridge, and crystal blade
                rbox([7.5, 8.0, 7.36], [8.5, 15.5, 8.64], "accent"),
                rbox([7.0, 8.0, 7.52], [9.0, 15.5, 8.48], "dark"),
                rbox([5.45, 8.0, 7.82], [10.55, 15.5, 8.18], "edge"),
                rbox([7.5, 15.5, 7.36], [8.5, 23.2, 8.64], "accent"),
                rbox([7.0, 15.5, 7.52], [9.0, 23.2, 8.48], "dark"),
                rbox([5.45, 15.5, 7.82], [10.55, 23.2, 8.18], "edge"),
                rbox([5.85, 23.2, 7.82], [10.15, 25.8, 8.18], "edge"),
                rbox([6.3, 25.8, 7.82], [9.7, 27.8, 8.18], "edge"),
                rbox([6.8, 27.8, 7.82], [9.2, 29.4, 8.18], "edge"),
                rbox([7.25, 29.4, 7.82], [8.75, 30.5, 8.18], "edge"),
                rbox([7.6, 30.5, 7.84], [8.4, 31.2, 8.16], "edge"),
            ]
        # Tier 4 (Netherite): Colossal Endgame Doom-Greatsword (Y up to 31.95, horned guard + double sawtooth fangs + gold blood-groove)
        return [
            rbox([6.2, 6.0, 6.7], [9.8, 8.6, 9.3], "dark"),
            rbox([6.8, 6.4, 6.55], [9.2, 8.2, 9.45], "accent"),
            rbox([0.8, 6.6, 7.15], [7.0, 7.8, 8.85], "dark", angle=-22.5, origin=[6.8, 7.2, 8.0]),
            rbox([9.0, 6.6, 7.15], [15.2, 7.8, 8.85], "dark", angle=22.5, origin=[9.2, 7.2, 8.0]),
            rbox([0.2, 7.3, 7.45], [3.2, 8.3, 8.55], "accent", angle=-22.5, origin=[6.8, 7.2, 8.0]),
            rbox([12.8, 7.3, 7.45], [15.8, 8.3, 8.55], "accent", angle=22.5, origin=[9.2, 7.2, 8.0]),
            # Twin serrated ricasso fangs (Y = 10.0 and Y = 12.6)
            rbox([3.8, 9.8, 7.6], [6.6, 10.9, 8.4], "accent", angle=-22.5, origin=[6.4, 10.3, 8.0]),
            rbox([9.4, 9.8, 7.6], [12.2, 10.9, 8.4], "accent", angle=22.5, origin=[9.6, 10.3, 8.0]),
            rbox([4.2, 12.4, 7.6], [6.6, 13.4, 8.4], "edge", angle=-22.5, origin=[6.4, 12.9, 8.0]),
            rbox([9.4, 12.4, 7.6], [11.8, 13.4, 8.4], "edge", angle=22.5, origin=[9.6, 12.9, 8.0]),
            # Matching Y-bands (8.2..16.5, 16.5..24.0) for golden blood-groove, dark spine, and broad razor blade
            rbox([7.5, 8.2, 7.34], [8.5, 16.5, 8.66], "accent"),
            rbox([6.8, 8.2, 7.50], [9.2, 16.5, 8.50], "dark"),
            rbox([5.1, 8.2, 7.82], [10.9, 16.5, 8.18], "edge"),
            rbox([7.5, 16.5, 7.34], [8.5, 24.0, 8.66], "accent"),
            rbox([6.8, 16.5, 7.50], [9.2, 24.0, 8.50], "dark"),
            rbox([5.35, 16.5, 7.82], [10.65, 24.0, 8.18], "edge"),
            rbox([5.75, 24.0, 7.82], [10.25, 26.6, 8.18], "edge"),
            rbox([6.25, 26.6, 7.82], [9.75, 28.6, 8.18], "edge"),
            rbox([6.75, 28.6, 7.82], [9.25, 30.2, 8.18], "edge"),
            rbox([7.2, 30.2, 7.82], [8.8, 31.2, 8.18], "edge"),
            rbox([7.6, 31.2, 7.84], [8.4, 31.95, 8.16], "edge"),
        ]

    if kind == "pickaxe":
        if rank == 0:
            # Copper: Simple narrow prospector's pick (plain collar, no top spike, short tines)
            return [
                rbox([6.6, 14.8, 7.0], [9.4, 17.2, 9.0], "dark"),
                rbox([3.2, 15.3, 7.35], [6.8, 16.8, 8.65], "metal"),
                rbox([9.2, 15.3, 7.35], [12.8, 16.8, 8.65], "metal"),
                rbox([1.2, 15.0, 7.6], [3.8, 16.3, 8.4], "edge", angle=22.5, origin=[3.5, 15.8, 8.0]),
                rbox([12.2, 15.0, 7.6], [14.8, 16.3, 8.4], "edge", angle=-22.5, origin=[12.5, 15.8, 8.0]),
            ]
        # Iron / Gold / Diamond / Netherite: Sleek curved pickaxe with tier-scaled reach & spikes
        reach = 1.6 + rank * 0.65
        elems = [
            # Sleek socket collar + accent strap
            rbox([6.4, 14.4, 6.85], [9.6, 17.4, 9.15], "dark"),
            rbox([6.2, 15.4, 6.75], [9.8, 16.4, 9.25], "accent"),
            # Inner shoulders
            rbox([3.4, 15.1, 7.25], [6.6, 16.9, 8.75], "metal"),
            rbox([9.4, 15.1, 7.25], [12.6, 16.9, 8.75], "metal"),
            # Curved main tines (sleek Z = 7.4..8.6 body + razor Z = 7.75..8.25 chisel tip)
            rbox([round(1.0 - reach, 2), 15.0, 7.4], [4.0, 16.6, 8.6], "metal", angle=22.5, origin=[3.8, 15.8, 8.0]),
            rbox([round(-0.4 - reach, 2), 15.1, 7.75], [round(1.6 - reach, 2), 16.2, 8.25], "edge", angle=22.5, origin=[3.8, 15.8, 8.0]),
            rbox([12.0, 15.0, 7.4], [round(15.0 + reach, 2), 16.6, 8.6], "metal", angle=-22.5, origin=[12.2, 15.8, 8.0]),
            rbox([round(14.4 + reach, 2), 15.1, 7.75], [round(16.4 + reach, 2), 16.2, 8.25], "edge", angle=-22.5, origin=[12.2, 15.8, 8.0]),
        ]
        if rank >= 1:
            # Top finial / spike grows with tier
            spike_top = 18.2 + rank * 0.95
            elems.append(rbox([7.3, 17.4, 7.3], [8.7, round(spike_top, 2), 8.7], "metal"))
            if rank >= 3:
                elems.append(rbox([7.6, round(spike_top - 0.2, 2), 7.65], [8.4, round(spike_top + 1.2, 2), 8.35], "edge"))
        if rank >= 3:
            # Diamond & Netherite: Serrated upper prong barbs
            elems.append(rbox([1.2, 16.5, 7.65], [3.2, 17.5, 8.35], "edge", angle=22.5, origin=[3.8, 15.8, 8.0]))
            elems.append(rbox([12.8, 16.5, 7.65], [14.8, 17.5, 8.35], "edge", angle=-22.5, origin=[12.2, 15.8, 8.0]))
        if rank >= 4:
            # Netherite: Golden rune bands on the prongs
            elems.append(rbox([2.2, 14.9, 7.15], [3.2, 16.9, 8.85], "accent", angle=22.5, origin=[3.8, 15.8, 8.0]))
            elems.append(rbox([12.8, 14.9, 7.15], [13.8, 16.9, 8.85], "accent", angle=-22.5, origin=[12.2, 15.8, 8.0]))
        return elems

    if kind == "heavy_pickaxe":
        # Copper..Netherite: Warhammer / Earthbreaker with heavy striking face on -X and armor-piercing pick on +X
        face_half_y = 2.8 + rank * 0.45
        elems = [
            # Sleek reinforced core collar + accent binding
            rbox([5.4, 13.4, 6.4], [10.6, 17.8, 9.6], "dark"),
            rbox([5.2, 14.6, 6.25], [10.8, 16.6, 9.75], "accent"),
            # Left (-X) Warhammer neck & beveled impact plate
            rbox([2.0, 14.0, 6.7], [5.5, 17.2, 9.3], "metal"),
            rbox([0.2, round(15.6 - face_half_y, 2), 6.0], [2.2, round(15.6 + face_half_y, 2), 10.0], "dark"),
            rbox([-0.5, round(15.6 - face_half_y + 0.4, 2), 6.3], [0.3, round(15.6 + face_half_y - 0.4, 2), 9.7], "edge"),
            # Right (+X) Rear armor-piercing pick (longer & sharper with tier)
            rbox([10.5, 14.4, 7.1], [13.8, 16.8, 8.9], "metal"),
            rbox([12.8, 14.6, 7.45], [round(15.4 + rank * 0.85, 2), 16.4, 8.55], "edge", angle=-22.5, origin=[13.2, 15.6, 8.0]),
        ]
        if rank >= 2:
            # Top breaker spike
            top_h = 18.8 + rank * 0.9
            elems.append(rbox([7.2, 17.8, 7.2], [8.8, round(top_h, 2), 8.8], "metal"))
            elems.append(rbox([7.55, round(top_h - 0.2, 2), 7.6], [8.45, round(top_h + 1.3, 2), 8.4], "edge"))
        if rank >= 4:
            # Netherite: Golden impact trim & upper hook barb
            elems.append(rbox([1.8, round(15.6 - face_half_y - 0.2, 2), 5.85], [2.5, round(15.6 + face_half_y + 0.2, 2), 10.15], "accent"))
            elems.append(rbox([13.2, 16.3, 7.6], [15.5, 17.4, 8.4], "accent", angle=-22.5, origin=[13.2, 15.6, 8.0]))
        return elems

    if kind == "axe":
        if rank == 0:
            # Copper: Humble small woodcutter's hatchet (compact blade, flat back, no beard)
            return [
                rbox([6.6, 13.6, 7.0], [9.2, 17.2, 9.0], "dark"),
                rbox([4.2, 13.4, 7.45], [6.8, 17.4, 8.55], "metal"),
                rbox([2.8, 13.0, 7.78], [4.4, 17.8, 8.22], "edge"),
            ]
        if rank == 1:
            # Iron: Classic bearded Viking axe with sleek curved edge & small back poll
            return [
                rbox([6.4, 13.2, 6.9], [9.6, 17.6, 9.1], "dark"),
                rbox([9.5, 14.4, 7.2], [11.2, 16.6, 8.8], "metal"),
                rbox([3.2, 13.6, 7.4], [6.5, 17.8, 8.6], "metal"),
                rbox([2.2, 11.0, 7.55], [4.5, 18.6, 8.45], "metal"),
                rbox([1.0, 10.4, 7.8], [2.5, 19.0, 8.2], "edge"),
                rbox([0.4, 11.4, 7.82], [1.2, 18.0, 8.18], "edge"),
            ]
        # Gold / Diamond / Netherite: Sweeping crescent Battleaxe / Executioner's Halberd-Axe
        beard_low = 9.8 - (rank - 2) * 1.1
        horn_high = 19.8 + (rank - 2) * 1.0
        elems = [
            # Ornate socket collar + top spear finial
            rbox([6.3, 12.8, 6.8], [9.7, 18.0, 9.2], "dark"),
            rbox([6.1, 14.6, 6.65], [9.9, 16.2, 9.35], "accent"),
            rbox([7.2, 18.0, 7.2], [8.8, round(19.6 + rank * 0.7, 2), 8.8], "metal"),
            rbox([7.55, round(19.4 + rank * 0.7, 2), 7.65], [8.45, round(20.8 + rank * 0.85, 2), 8.35], "edge"),
            # Sweeping cheek & inner cutout (sleek Z = 7.4..8.6)
            rbox([3.0, 13.4, 7.35], [6.5, 18.0, 8.65], "metal"),
            rbox([1.2, round(beard_low + 1.2, 2), 7.52], [3.4, round(horn_high - 0.8, 2), 8.48], "metal"),
            # Multi-stepped razor crescent cutting edge (Z = 7.82..8.18)
            rbox([0.0, round(beard_low, 2), 7.8], [1.6, round(horn_high, 2), 8.2], "edge"),
            rbox([-0.9, round(beard_low + 1.0, 2), 7.82], [0.2, round(horn_high - 1.0, 2), 8.18], "edge"),
            # Rear armor-piercing spike / cleaver hook on +X
            rbox([9.6, 14.2, 7.25], [12.6, 16.8, 8.75], "metal"),
            rbox([11.8, 14.4, 7.6], [round(13.8 + rank * 0.65, 2), 16.2, 8.4], "edge", angle=-22.5, origin=[12.0, 15.4, 8.0]),
        ]
        if rank >= 4:
            # Netherite: Extra outer razor crescent & golden cheek trim
            elems.append(rbox([-1.6, round(beard_low + 2.2, 2), 7.84], [-0.7, round(horn_high - 2.2, 2), 8.16], "edge"))
            elems.append(rbox([2.6, 13.8, 7.15], [3.6, 17.6, 8.85], "accent"))
        return elems

    if kind == "shovel":
        if rank == 0:
            # Copper: Small simple flat trowel/spade
            return [
                rbox([6.8, 13.6, 7.1], [9.2, 15.2, 8.9], "dark"),
                rbox([5.4, 15.0, 7.72], [10.6, 19.6, 8.28], "metal"),
                rbox([6.2, 19.6, 7.78], [9.8, 21.0, 8.22], "edge"),
            ]
        # Iron..Netherite: Sleek dished war-spade with central rib & stepped pointed tip
        w_half = 3.2 + rank * 0.35
        tip_y = 21.8 + rank * 0.85
        elems = [
            # Socket ferrule & foot-step bar
            rbox([6.5, 13.0, 6.9], [9.5, 15.2, 9.1], "accent" if rank >= 2 else "dark"),
            rbox([round(8.0 - w_half + 0.4, 2), 14.4, 7.3], [round(8.0 + w_half - 0.4, 2), 15.4, 8.7], "dark"),
            # Central reinforcing spine (Z = 7.42..8.58)
            rbox([7.15, 14.4, 7.42], [8.85, round(tip_y - 2.2, 2), 8.58], "dark" if rank <= 2 else "accent"),
            # Sleek dished blade body & honed cutting edges (Z = 7.78..8.22)
            rbox([round(8.0 - w_half, 2), 15.2, 7.76], [round(8.0 + w_half, 2), round(tip_y - 3.0, 2), 8.24], "metal"),
            rbox([round(8.0 - w_half + 0.7, 2), round(tip_y - 3.0, 2), 7.8], [round(8.0 + w_half - 0.7, 2), round(tip_y - 1.4, 2), 8.2], "edge"),
            rbox([round(8.0 - w_half + 1.6, 2), round(tip_y - 1.4, 2), 7.82], [round(8.0 + w_half - 1.6, 2), round(tip_y - 0.3, 2), 8.18], "edge"),
            rbox([7.3, round(tip_y - 0.3, 2), 7.84], [8.7, round(tip_y + 0.6, 2), 8.16], "edge"),
        ]
        if rank >= 3:
            # Diamond & Netherite: Winged side bevel edges
            elems.append(rbox([round(8.0 - w_half - 0.5, 2), 15.8, 7.82], [round(8.0 - w_half + 0.2, 2), round(tip_y - 3.2, 2), 8.18], "edge"))
            elems.append(rbox([round(8.0 + w_half - 0.2, 2), 15.8, 7.82], [round(8.0 + w_half + 0.5, 2), round(tip_y - 3.2, 2), 8.18], "edge"))
        return elems

    if kind == "hoe":
        if rank == 0:
            # Copper: Short simple garden hoe — single continuous blade emerging directly from the collar
            return [
                rbox([6.6, 14.8, 7.0], [9.2, 17.2, 9.0], "dark"),
                rbox([2.2, 15.4, 7.42], [7.2, 16.7, 8.58], "metal", angle=22.5, origin=[6.8, 16.1, 8.0]),
                rbox([1.6, 14.8, 7.78], [6.6, 15.6, 8.22], "edge", angle=22.5, origin=[6.8, 16.1, 8.0]),
            ]
        # Iron..Netherite: Sleek continuous Reaper Scythe / Hoe blade sharing a single origin [6.8, 16.2, 8.0] inside the collar!
        blade_len = 5.4 + rank * 0.85
        x_tip = round(6.8 - blade_len, 2)
        orig = [6.8, 16.2, 8.0]
        elems = [
            # Socket collar & accent ring
            rbox([6.4, 14.6, 6.85], [9.6, 17.4, 9.15], "dark"),
            rbox([6.2, 15.5, 6.75], [9.8, 16.5, 9.25], "accent"),
            # Continuous upper blade spine (one piece from collar X=7.2 to tip -> zero kink, zero vertical seams!)
            rbox([round(x_tip + 0.8, 2), 15.5, 7.42], [7.2, 16.9, 8.58], "dark" if rank >= 3 else "metal", angle=22.5, origin=orig),
            # Continuous honed lower cutting edge along the entire underside of the blade
            rbox([x_tip, 14.7, 7.80], [6.6, 15.7, 8.20], "edge", angle=22.5, origin=orig),
            # Tapered pointed reaper tip at the end of the blade
            rbox([round(x_tip - 0.9, 2), 14.5, 7.82], [round(x_tip + 0.6, 2), 15.4, 8.18], "edge", angle=22.5, origin=orig),
        ]
        if rank >= 2:
            # Top finial & compact rear poll
            elems.append(rbox([7.3, 17.4, 7.3], [8.7, round(18.4 + rank * 0.5, 2), 8.7], "edge"))
            elems.append(rbox([9.5, 15.4, 7.35], [round(10.6 + rank * 0.4, 2), 16.8, 8.65], "metal"))
        if rank >= 3:
            # Diamond & Netherite: Golden spine ridge along the top of the scythe blade
            elems.append(rbox([round(x_tip + 2.0, 2), 16.6, 7.34], [6.8, 17.2, 8.66], "accent", angle=22.5, origin=orig))
        return elems
    return []


GEM_BY_TIER = {"gold": "ruby", "diamond": "emerald", "netherite": "amethyst"}
# Accent metal of the armor trim, like the tool accents (gold on iron, diamond and netherite, iron on copper and gold)
ARMOR_ACCENT = {"copper": "iron", "iron": "gold", "gold": "iron", "diamond": "gold", "netherite": "gold"}
# Height (y from, y to) of the gem in the collar of each tool kind (sword: centre of the cross guard)
GEM_HEIGHT = {"sword": (6.5, 7.9), "spear": (14.8, 16.2), "pickaxe": (15.2, 16.6), "axe": (14.7, 16.1), "shovel": (13.4, 14.8),
              "hoe": (15.3, 16.7), "heavy_pickaxe": (14.9, 16.3)}


def gem_elements(kind: str, tier: str, with_pommel: bool = True) -> list[dict]:
    """Gold tier and up: a cut gem set into the collar (front and back); diamond and netherite also crown the pommel.
    The netherite amethyst glows."""
    if tier not in GEM_BY_TIER:
        return []
    y1, y2 = GEM_HEIGHT[kind]
    depth = (6.0, 10.0) if kind == "heavy_pickaxe" else (6.3, 9.7)
    elems = [rbox([7.3, y1, depth[0]], [8.7, y2, depth[1]], "gem")]
    if with_pommel and TIER_RANK[tier] >= 3:
        elems.append(rbox([7.35, 0.5, 6.6], [8.65, 1.8, 9.4], "gem") if kind == "sword"
                     else rbox([7.4, -2.7, 7.4], [8.6, -2.0, 8.6], "gem"))
    if tier == "netherite":
        for e in elems:
            e["light_emission"] = 10
    return elems


# Design -> (body palette, trim texture, grip palette, gem); the blade ("edge") keeps the tier's colour
DESIGNS = {
    "end": ("end_metal", "mat_end_accent", "end_grip", "amethyst"),
    "nether": ("nether_metal", "mat_gold", "nether_grip", "ruby"),
    "sculk": ("sculk_metal", "mat_sculk_accent", "sculk_grip", "sculk_gem"),
    "vote": ("vote_metal", "mat_gold", "vote_grip", "vote_gem"),
}
DESIGN_NAMES = (("end", "End", "End"), ("nether", "Nether", "Nether"), ("sculk", "Sculk", "Sculk"), ("vote", "Vote", "Vote"))


def separate_planes(elements: list[dict]) -> list[dict]:
    """Overlapping parts whose faces lie in the same plane flicker (z-fighting). Pull the smaller part's face in by
    0.04 until no two overlapping, equally rotated parts share a face plane."""
    def same_rotation(a, b):
        return a.get("rotation") == b.get("rotation")

    def volume(e):
        return (e["to"][0] - e["from"][0]) * (e["to"][1] - e["from"][1]) * (e["to"][2] - e["from"][2])

    for _ in range(8):
        changed = False
        for i, a in enumerate(elements):
            for b in elements[i + 1:]:
                if not same_rotation(a, b) or not all(a["from"][k] < b["to"][k] and b["from"][k] < a["to"][k] for k in range(3)):
                    continue
                small = a if volume(a) <= volume(b) else b
                big = b if small is a else a
                for k in range(3):
                    for side, step in (("from", 0.04), ("to", -0.04)):
                        if any(abs(small[side][k] - p) < 1e-6 for p in (big["from"][k], big["to"][k])):
                            small[side][k] = round(small[side][k] + step, 3)
                            changed = True
        if not changed:
            break
    return elements


def register_3d_tool(item_id: str, kind: str, tier: str, img_fallback: Image.Image, design: str | None = None):
    """Saves the 2D texture for particle/palette lookup AND registers a tier-progressive 3D JSON model."""
    tex_path = ASSETS / "textures" / "item" / f"{item_id}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img_fallback.save(tex_path)

    grip_tex = "mat_reinforced_grip" if (kind == "heavy_pickaxe" or tier in ("gold", "diamond", "netherite")) else "mat_leather_grip"
    accent_tex = "mat_gold" if tier in ("gold", "diamond", "netherite") else ("mat_iron" if tier == "iron" else "mat_copper_dark")
    elements = separate_planes(build_3d_handle_elements(kind, tier) + build_3d_head_elements(kind, tier) + gem_elements(kind, tier))

    write_json(ASSETS / "models" / "item" / f"{item_id}.json", {
        "textures": {
            "particle": f"{NS}:item/{item_id}",
            "metal": f"{NS}:item/mat_{tier}",
            "edge": f"{NS}:item/mat_{tier}_edge",
            "dark": f"{NS}:item/mat_{tier}_dark",
            "accent": f"{NS}:item/{accent_tex}",
            "grip": f"{NS}:item/{grip_tex}",
            "wood": f"{NS}:item/mat_wood",
            "gem": f"{NS}:item/mat_{GEM_BY_TIER.get(tier, 'ruby')}",
        } | ({} if design is None else {
            "metal": f"{NS}:item/mat_{DESIGNS[design][0]}",
            "dark": f"{NS}:item/mat_{DESIGNS[design][0]}_dark",
            "accent": f"{NS}:item/{DESIGNS[design][1]}",
            "grip": f"{NS}:item/mat_{DESIGNS[design][2]}",
            "wood": f"{NS}:item/mat_{DESIGNS[design][2]}",
            "gem": f"{NS}:item/mat_{DESIGNS[design][3]}",
        }),
        "display": spear_display(elements) if kind == "spear" else get_tool_3d_display(kind, tier),
        "elements": elements,
    })
    write_json(ASSETS / "items" / f"{item_id}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
    } | ({"swap_animation_scale": 1.95} if kind == "spear" else {}))


def _rot(point, axis, deg, origin):
    a = math.radians(deg)
    x, y, z = (point[i] - origin[i] for i in range(3))
    c, s_ = math.cos(a), math.sin(a)
    if axis == "x":
        y, z = y * c - z * s_, y * s_ + z * c
    elif axis == "y":
        x, z = x * c + z * s_, -x * s_ + z * c
    else:
        x, y = x * c - y * s_, x * s_ + y * c
    return [x + origin[0], y + origin[1], z + origin[2]]


def fit_gui(elements: list[dict], rotation: list[float], fill: float = 15.0, max_scale: float = 1.3) -> dict:
    """Inventory view that centres the model and fits it completely into the slot: corners of all elements (with their
    own rotation) are turned like the GUI does (X, Y, Z euler around the block centre) and the screen bounding box is
    scaled to `fill` model pixels (small models are not blown up beyond max_scale)."""
    pts = []
    for e in elements:
        (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
        for c in [(x, y, z) for x in (x1, x2) for y in (y1, y2) for z in (z1, z2)]:
            if "rotation" in e:
                c = _rot(c, e["rotation"]["axis"], e["rotation"]["angle"], e["rotation"]["origin"])
            c = [v - 8 for v in c]
            # Display rotation = Rx * Ry * Rz (Z applied first)
            for axis, deg in (("z", rotation[2]), ("y", rotation[1]), ("x", rotation[0])):
                c = _rot(c, axis, deg, [0, 0, 0])
            pts.append(c)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    scale = round(min(max_scale, fill / max(max(xs) - min(xs), max(ys) - min(ys))), 3)
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    return {"rotation": rotation, "translation": [round(-cx * scale, 2), round(-cy * scale, 2), 0],
            "scale": [scale, scale, scale]}


# Vanilla spear in hand (item/spear_in_hand): (rotation, translation, scale); its spear lies diagonally across the
# 16x16 plane (tip top left), so it is 16 * sqrt(2) long
VANILLA_SPEAR_HAND = {
    "firstperson_righthand": ([-20, 90, -35], [3.13, 2.0, 0.13], [1.36, 1.36, 0.68]),
    "firstperson_lefthand": ([-20, -90, 35], [3.13, 2.0, 0.13], [1.36, 1.36, 0.68]),
    "thirdperson_righthand": ([5, 270, -40], [0, 2, 2], [1.7, 1.7, 0.85]),
    "thirdperson_lefthand": ([5, -270, 40], [0, 2, 2], [1.7, 1.7, 0.85]),
    "ground": ([0, 0, 0], [0, 2, 0], [0.5, 0.5, 0.5]),
}


def spear_display(elements: list[dict]) -> dict:
    """Lay the upright 3D spear onto the vanilla diagonal (tip top left) inside the model - centred on the block
    centre and turned 45 degrees about Z - so the vanilla spear display works unchanged for both hands, only scaled
    to the vanilla length (16 * sqrt(2)). Mutates the elements."""
    ys = [c for e in elements for c in (e["from"][1], e["to"][1])]
    length = max(ys) - min(ys)
    dy = 8 - (max(ys) + min(ys)) / 2
    centre = [8, 8, 8]
    for e in elements:
        e["from"][1] = round(e["from"][1] + dy, 3)
        e["to"][1] = round(e["to"][1] + dy, 3)
        rotation = e.get("rotation")
        if rotation is None:
            e["rotation"] = {"origin": centre, "axis": "z", "angle": 45}
            continue
        # Own Z rotation about o, then 45 degrees about the centre = rotation by angle + 45 about o', element moved by o' - o
        origin = [rotation["origin"][0], rotation["origin"][1] + dy, rotation["origin"][2]]
        moved = _rot(origin, "z", 45, centre)
        shift = [moved[i] - origin[i] for i in range(3)]
        e["from"] = [round(e["from"][i] + shift[i], 3) for i in range(3)]
        e["to"] = [round(e["to"][i] + shift[i], 3) for i in range(3)]
        e["rotation"] = {"origin": [round(v, 3) for v in moved], "axis": "z", "angle": rotation["angle"] + 45}
    k = 16 * math.sqrt(2) / length
    # Vanilla halves the depth (its spear is a flat sprite); the 3D spear keeps its full depth
    display = {view: {"rotation": rotation, "translation": translation, "scale": [round(scale[0] * k, 3)] * 3}
               for view, (rotation, translation, scale) in VANILLA_SPEAR_HAND.items()}
    display["fixed"] = {"rotation": [0, 180, 0], "scale": [round(k, 3)] * 3}
    # Inventory: diagonal like the vanilla icon (tip top right), filling the slot
    display["gui"] = fit_gui(elements, [0, 0, -90], max_scale=4.0)
    return display


def register_3d_head(item_id: str, kind: str, mat_name: str, img_fallback: Image.Image, geom_tier: str = "iron"):
    """Registers a 3D forged tool head model (both glowing hot heads and cooled tempered heads)."""
    tex_path = ASSETS / "textures" / "item" / f"{item_id}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img_fallback.save(tex_path)

    accent_tex = "mat_glowing_edge" if mat_name == "glowing" else ("mat_gold" if geom_tier in ("gold", "diamond", "netherite") else f"mat_{mat_name}_dark")
    elements = separate_planes(build_3d_head_elements(kind, geom_tier))
    if mat_name == "glowing":
        for e in elements:
            e["light_emission"] = 12  # red-hot metal glows in the dark
    elif kind != "sword":
        elements += gem_elements(kind, geom_tier, with_pommel=False)

    write_json(ASSETS / "models" / "item" / f"{item_id}.json", {
        "textures": {
            "particle": f"{NS}:item/{item_id}",
            "metal": f"{NS}:item/mat_{mat_name}",
            "edge": f"{NS}:item/mat_{mat_name}_edge",
            "dark": f"{NS}:item/mat_{mat_name}_dark",
            "accent": f"{NS}:item/{accent_tex}",
            "gem": f"{NS}:item/mat_{GEM_BY_TIER.get(geom_tier, 'ruby')}",
        },
        "display": HEAD_3D_DISPLAY | {"gui": fit_gui(elements, HEAD_3D_DISPLAY["gui"]["rotation"])},
        "elements": elements,
    })
    write_json(ASSETS / "items" / f"{item_id}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
    })


def register_3d_smithing_hammer(img_fallback: Image.Image):
    tex_path = ASSETS / "textures" / "item" / "smithing_hammer.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img_fallback.save(tex_path)

    # Shaft ends inside the head (its top face would z-fight with the head top at 17.2)
    elements = build_3d_handle_elements("pickaxe", "iron", top_y=16.2) + [
        # Golden Collar & Heavy 3D Cross-Peen Blacksmith Hammer Head
        rbox([5.8, 12.2, 6.2], [10.2, 16.8, 9.8], "accent"),
        rbox([2.6, 13.0, 5.6], [13.4, 17.2, 10.4], "metal"),
        rbox([1.6, 12.6, 5.2], [2.8, 17.6, 10.8], "edge"),
        rbox([13.2, 13.8, 6.4], [14.8, 16.4, 9.6], "edge"),
    ]
    write_json(ASSETS / "models" / "item" / "smithing_hammer.json", {
        "textures": {
            "particle": f"{NS}:item/smithing_hammer",
            "metal": f"{NS}:item/mat_iron",
            "edge": f"{NS}:item/mat_iron_edge",
            "dark": f"{NS}:item/mat_iron_dark",
            "accent": f"{NS}:item/mat_gold",
            "grip": f"{NS}:item/mat_leather_grip",
            "wood": f"{NS}:item/mat_wood",
        },
        "display": get_tool_3d_display("pickaxe", "iron"),
        "elements": elements,
    })
    write_json(ASSETS / "items" / "smithing_hammer.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/smithing_hammer"},
    })


def register_3d_smithing_tongs(img_fallback: Image.Image):
    tex_path = ASSETS / "textures" / "item" / "smithing_tongs.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img_fallback.save(tex_path)

    # Blacksmith tongs, upright like the other tools (beak style as in tech mods): two reins side by side, a pivot
    # rivet, one jaw going straight on and one jaw angled off with an inward hook - an open beak
    elements = [
        # Reins (wrapped) with iron end caps
        rbox([6.8, 3.1, 7.45], [7.8, 10.6, 8.55], "grip"),
        rbox([8.2, 3.1, 7.45], [9.2, 10.6, 8.55], "grip"),
        rbox([6.6, 2.3, 7.35], [9.4, 3.1, 8.65], "metal"),
        # Pivot block and gold rivet (front and back)
        rbox([6.6, 10.6, 7.3], [9.4, 12.4, 8.7], "metal"),
        rbox([7.4, 10.9, 7.1], [8.6, 12.1, 8.9], "accent"),
        # Straight jaw with a bent-in tip
        rbox([6.8, 12.4, 7.45], [7.8, 16.2, 8.55], "metal"),
        rbox([6.8, 16.2, 7.5], [8.4, 17.0, 8.5], "edge"),
        # Angled jaw and its inward hook
        rbox([8.2, 12.2, 7.45], [9.2, 15.6, 8.55], "metal", angle=-22.5, origin=[8.7, 12.4, 8.0]),
        rbox([8.8, 14.9, 7.5], [10.4, 15.8, 8.5], "edge"),
    ]
    tongs_display = get_tool_3d_display("pickaxe", "iron")
    # The short reins sit 2 model pixels deeper in the hand (display translation is not scaled, so multiply by scale)
    for view in ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"):
        t = tongs_display[view]
        t["translation"] = [t["translation"][0], round(t["translation"][1] - 2 * t["scale"][1], 2), t["translation"][2]]
    write_json(ASSETS / "models" / "item" / "smithing_tongs.json", {
        "textures": {
            "particle": f"{NS}:item/smithing_tongs",
            "metal": f"{NS}:item/mat_iron",
            "edge": f"{NS}:item/mat_iron_edge",
            "accent": f"{NS}:item/mat_gold",
            "grip": f"{NS}:item/mat_reinforced_grip",
        },
        "display": tongs_display,
        "elements": elements,
    })
    write_json(ASSETS / "items" / "smithing_tongs.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/smithing_tongs"},
    })


def draw_smithing_book() -> Image.Image:
    """16x16 pixel-art blacksmith tome: dark crimson/charcoal leather cover, iron corner clasps, golden anvil emblem."""
    img = new_img()
    cover_dark = (48, 24, 22)
    cover_mid = (84, 34, 28)
    cover_light = (116, 46, 36)
    pages = (232, 220, 192)
    pages_shade = (194, 178, 148)
    iron = PALETTES["iron"]
    gold = PALETTES["gold"]

    # Book body (x: 3..12, y: 2..13)
    for y in range(2, 14):
        for x in range(3, 13):
            if x == 3:
                put(img, x, y, cover_dark)  # spine
            elif x == 12 or y == 2 or y == 13:
                put(img, x, y, cover_dark)  # border
            elif x == 11 and 3 <= y <= 12:
                put(img, x, y, pages if y % 2 == 0 else pages_shade)  # page edges on right
            else:
                put(img, x, y, cover_light if (x + y) % 3 == 0 else cover_mid)

    # Iron spine bands & corner clasps
    for y in (3, 7, 8, 12):
        put(img, 3, y, iron[2])
        put(img, 4, y, iron[3])
    for cx, cy in ((10, 2), (11, 2), (11, 3), (10, 13), (11, 13), (11, 12)):
        put(img, cx, cy, iron[3])

    # Golden Anvil Emblem in center (x: 5..9, y: 5..10)
    # Anvil top face (y=6, x=5..9) + horn (x=5, y=6)
    for x in range(5, 10):
        put(img, x, 6, gold[4] if x in (6, 7) else gold[3])
    put(img, 6, 7, gold[2])
    put(img, 7, 7, gold[3])
    put(img, 8, 7, gold[2])
    # Anvil waist (y=8, x=6..8)
    put(img, 7, 8, gold[2])
    # Anvil base (y=9, x=5..9)
    for x in range(6, 9):
        put(img, x, 9, gold[3])
    put(img, 5, 9, gold[1])
    put(img, 9, 9, gold[1])
    return img


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ---- 3D Block / Station Models ----

GUI_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def box(from_xyz, to_xyz, tex_all: str, top_tex: str | None = None, bottom_tex: str | None = None):
    x1, y1, z1 = from_xyz
    x2, y2, z2 = to_xyz
    dx = min(16.0, max(1.0, round(abs(x2 - x1), 3)))
    dy = min(16.0, max(1.0, round(abs(y2 - y1), 3)))
    dz = min(16.0, max(1.0, round(abs(z2 - z1), 3)))
    needs_uv = any(c < 0.0 or c > 16.0 for c in (x1, y1, z1, x2, y2, z2))
    faces = {}
    for side in ("north", "south", "west", "east", "up", "down"):
        t = top_tex if (side == "up" and top_tex) else (bottom_tex if (side == "down" and bottom_tex) else tex_all)
        face_obj = {"texture": f"#{t}"}
        if needs_uv:
            if side in ("north", "south"):
                face_obj["uv"] = [0, 0, dx, dy]
            elif side in ("west", "east"):
                face_obj["uv"] = [0, 0, dz, dy]
            else:
                face_obj["uv"] = [0, 0, dx, dz]
        faces[side] = face_obj
    return {"from": from_xyz, "to": to_xyz, "faces": faces}


def hollow_band(x1, y1, z1, x2, y2, z2, t, tex: str):
    """Four outer strips around a box perimeter so the interior remains 100% hollow."""
    strips = [
        box([x1, y1, z1], [x2, y2, z1 + t], tex),
        box([x1, y1, z2 - t], [x2, y2, z2], tex),
        box([x1, y1, z1 + t], [x1 + t, y2, z2 - t], tex),
        box([x2 - t, y1, z1 + t], [x2, y2, z2 - t], tex),
    ]
    for s in strips:
        for f in s["faces"].values():
            f["uv"] = [2, 2, 14, 4]
    return strips


SMELTERY_GUI_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.24, 0.24, 0.24]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.12, 0.12, 0.12]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.20, 0.20, 0.20]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.16, 0.16, 0.16]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.18, 0.18, 0.18]},
}


def generate_station_models():
    models_dir = ASSETS / "models" / "block"
    items_dir = ASSETS / "items"

    # 1. 3x3 Tinkers' Construct Smeltery (Schmelze) + Front Casting Table (Gießtisch)
    # Centered at the middle of the 3x3 Smeltery: X in [-15..31], Z in [-15..31], Y in [0..26].
    # Hollow interior pit: X in [-11..27], Z in [-11..27], Y in [4..26].
    crucible_elements = []

    # 3x3 Tiled Stone Base & Pit Floor (Y = 0..4)
    x_segs = [(-15, 0), (0, 16), (16, 31)]
    z_segs = [(-15, 0), (0, 16), (16, 31)]
    for x0, x1 in x_segs:
        for z0, z1 in z_segs:
            crucible_elements.append(box([x0, 0, z0], [x1, 4, z1], "base", "inner", "base"))

    # Four Heavy Corner Pillars (Y = 0..27)
    for px0, px1 in ((-15.5, -11), (27, 31.5)):
        for pz0, pz1 in ((-15.5, -11), (27, 31.5)):
            crucible_elements.append(box([px0, 0, pz0], [px1, 14, pz1], "rim", "band", "base"))
            crucible_elements.append(box([px0, 14, pz0], [px1, 27, pz1], "rim", "band", "base"))

    # Back Wall (+Z: Z = 27..31) & Left/Right Side Walls (X = -15..-11 and X = 27..31), segmented for 1:1 brick UVs
    for y0, y1 in ((4, 15), (15, 26)):
        for s0, s1 in ((-11, 0), (0, 16), (16, 27)):
            # Back wall (+Z)
            crucible_elements.append(box([s0, y0, 27], [s1, y1, 31], "wall", "rim"))
            # West wall (-X)
            crucible_elements.append(box([-15, y0, s0], [-11, y1, s1], "wall", "rim"))
            # East wall (+X)
            crucible_elements.append(box([27, y0, s0], [31, y1, s1], "wall", "rim"))
        # Front wall (-Z: Z = -15..-11) center block (Controller & Drain)
        crucible_elements.append(box([0, y0, -15], [16, y1, -11], "wall", "rim"))

    # Front-Left (X = -11..0) & Front-Right (X = 16..27) Seared Lava Tank Walls with Level Gauge Windows!
    for gx0, gx1 in ((-11, 0), (16, 27)):
        # Inner backing wall of the Lava Tank (Z = -13.5..-11, Y = 4..26) so the Smeltery pit stays sealed
        crucible_elements.append(box([gx0, 4, -13.5], [gx1, 15, -11], "inner", "rim"))
        crucible_elements.append(box([gx0, 15, -13.5], [gx1, 26, -11], "inner", "rim"))
        # Side pillars framing the Lava Gauge Window
        crucible_elements.append(box([gx0, 4, -15.2], [gx0 + 2, 26, -13.5], "wall", "rim"))
        crucible_elements.append(box([gx1 - 2, 4, -15.2], [gx1, 26, -13.5], "wall", "rim"))
        # Top header above the Gauge Window (Y = 24..26)
        crucible_elements.append(box([gx0 + 2, 24, -15.2], [gx1 - 2, 26, -13.5], "rim", "rim"))
        # Tinted Glass Window Pane (X = gx0+2..gx1-2, Y = 4..24, Z = -15.1..-14.7)
        crucible_elements.append(box([gx0 + 2, 4, -15.1], [gx1 - 2, 24, -14.7], "glass"))
        # 4 Metal Measurement Scale Bars (Füllstandsanzeige-Markierungen at 25%, 50%, 75%, 100%: Y = 9, 14, 19, 23.5)
        for mark_y in (8.6, 13.6, 18.6, 23.4):
            crucible_elements.append(box([gx0 + 1.5, mark_y, -15.4], [gx1 - 1.5, mark_y + 0.6, -14.9], "band"))

    # Three Heavy Iron Reinforcement Bands around the entire 3x3 Smeltery perimeter
    for band_y in (5.5, 14.5, 22.5):
        crucible_elements.extend(hollow_band(-15.6, band_y, -15.6, 31.6, band_y + 1.5, 31.6, 0.5, "band"))

    # Tinkers-style Smeltery Drain & Pouring Faucet higher up at Front-Center (-Z = -16.0..-14.0, Y = 12.5..18.5, pouring down onto the Casting Table)
    crucible_elements.extend([
        box([5.0, 12.5, -15.5], [11.0, 18.5, -14.0], "rim"),
        box([6.2, 14.0, -16.0], [9.8, 17.0, -14.5], "band"),
        box([6.2, 12.2, -16.0], [9.8, 14.5, -15.0], "band"),
    ])

    write_json(models_dir / "forge_crucible.json", {
        "textures": {
            "particle": "minecraft:block/polished_blackstone_bricks",
            "base": "minecraft:block/polished_blackstone",
            "top": "minecraft:block/polished_blackstone",
            "wall": "minecraft:block/polished_blackstone_bricks",
            "rim": "minecraft:block/chiseled_polished_blackstone",
            "band": "minecraft:block/anvil",
            "inner": "minecraft:block/blackstone",
            "glass": "minecraft:block/orange_stained_glass",
        },
        "display": SMELTERY_GUI_DISPLAY,
        "elements": crucible_elements,
    })
    write_json(items_dir / "forge_crucible.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/forge_crucible"}})

    # Dynamic Lava Level Gauge Bars (Füllstandsanzeige inside the left & right front Seared Tanks)
    # Base Y spans [4..20] (height 16 = 1.0 block, bottom at -0.25f). Scaled dynamically in ForgeCrucibleStation!
    write_json(models_dir / "crucible_lava_gauge.json", {
        "textures": {
            "particle": "minecraft:block/lava_still",
            "lava": "minecraft:block/lava_still",
        },
        "elements": [
            box([-8.8, 4, -14.9], [-2.2, 20, -13.5], "lava"),
            box([18.2, 4, -14.9], [24.8, 20, -13.5], "lava"),
        ],
    })
    write_json(items_dir / "block" / "crucible_lava_gauge.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/crucible_lava_gauge"}})

    # Casting Table (Gießtisch) placed automatically in front of the 3x3 Smeltery Faucet
    casting_table_elements = [
        # Four sturdy stone/iron legs
        box([1, 0, 1], [4, 9, 4], "leg"),
        box([12, 0, 1], [15, 9, 4], "leg"),
        box([1, 0, 12], [4, 9, 15], "leg"),
        box([12, 0, 12], [15, 9, 15], "leg"),
        # Crossbars
        box([2, 3, 2], [14, 4.5, 14], "base"),
        # Table bed (Y = 9..11)
        box([1, 9, 1], [15, 11, 15], "top", "bed"),
        # Raised casting rim around the mold tray (hollow inside [3..13, 11..12.5, 3..13] so the mold sits clearly visible!)
        box([1, 11, 1], [15, 12.5, 3], "rim", "band"),
        box([1, 11, 13], [15, 12.5, 15], "rim", "band"),
        box([1, 11, 3], [3, 12.5, 13], "rim", "band"),
        box([13, 11, 3], [15, 12.5, 13], "rim", "band"),
        # Receiving channel towards the furnace faucet (+Z back side)
        box([6, 11, 13], [10, 12.5, 16], "band"),
    ]
    write_json(models_dir / "casting_table.json", {
        "textures": {
            "particle": "minecraft:block/polished_blackstone_bricks",
            "leg": "minecraft:block/polished_blackstone",
            "base": "minecraft:block/polished_blackstone_bricks",
            "top": "minecraft:block/chiseled_polished_blackstone",
            "bed": "minecraft:block/smooth_stone",
            "rim": "minecraft:block/polished_blackstone_bricks",
            "band": "minecraft:block/anvil",
        },
        "display": GUI_DISPLAY,
        "elements": casting_table_elements,
    })
    write_json(items_dir / "block" / "casting_table.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/casting_table"}})

    # Molten metal pouring stream from the raised Faucet into the Casting Table
    write_json(models_dir / "casting_stream.json", {
        "textures": {
            "particle": "minecraft:block/lava_still",
            "lava": "minecraft:block/lava_still",
        },
        "elements": [
            # Stream pouring from the raised Smeltery faucet (+Z side of the Casting Table) down into the mold tray
            box([7, 14.2, 14], [9, 16.2, 18], "lava"),
            box([7, 11.2, 13], [9, 15.5, 15.5], "lava"),
            box([7, 11.2, 8], [9, 12.8, 15], "lava"),
            box([3.5, 11.1, 3.5], [12.5, 11.9, 12.5], "lava"),
        ],
    })
    write_json(items_dir / "block" / "casting_stream.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/casting_stream"}})

    # 3x3 Crucible liquid / molten metal pit (tiled 3x3 across [-11..27, 4..20, -11..27])
    pit_segs = [(-11, 1), (1, 15), (15, 27)]
    molten_elements = [box([x0, 4, z0], [x1, 20, z1], "lava") for x0, x1 in pit_segs for z0, z1 in pit_segs]
    write_json(models_dir / "crucible_molten.json", {
        "textures": {
            "particle": "minecraft:block/lava_still",
            "lava": "minecraft:block/lava_still",
        },
        "elements": molten_elements,
    })
    write_json(items_dir / "block" / "crucible_molten.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/crucible_molten"}})

    # 3x3 Crucible solid ore charge pit (tiled 3x3 across [-10..26, 4..20, -10..26])
    solid_segs = [(-10, 2), (2, 14), (14, 26)]
    solid_elements = [box([x0, 4, z0], [x1, 20, z1], "ore") for x0, x1 in solid_segs for z0, z1 in solid_segs]
    write_json(models_dir / "crucible_solid.json", {
        "textures": {
            "particle": "minecraft:block/raw_iron_block",
            "ore": "minecraft:block/raw_iron_block",
        },
        "elements": solid_elements,
    })
    write_json(items_dir / "block" / "crucible_solid.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/crucible_solid"}})

    # Nether stars in the pit: glowing nether star block (same tiling as the solid ore charge)
    (ASSETS / "textures" / "block").mkdir(parents=True, exist_ok=True)
    sprites.nether_star_block().save(ASSETS / "textures" / "block" / "nether_star_block.png")
    star_elements = [box([x0, 4, z0], [x1, 20, z1], "star") for x0, x1 in solid_segs for z0, z1 in solid_segs]
    for e in star_elements:
        e["light_emission"] = 12
    write_json(models_dir / "crucible_star.json", {
        "textures": {
            "particle": f"{NS}:block/nether_star_block",
            "star": f"{NS}:block/nether_star_block",
        },
        "elements": star_elements,
    })
    write_json(items_dir / "block" / "crucible_star.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/crucible_star"}})

    # 2. Smithing Anvil (Schmiedeamboss) - oriented crosswise along X axis (like vanilla anvil)
    anvil_elements = [
        # Heavy wooden block stump + hollow iron base band
        box([2, 0, 2], [14, 5, 14], "stump", "stump_top"),
        *hollow_band(1.75, 1, 1.75, 14.25, 2.5, 14.25, 0.5, "band"),
        # Anvil foot & waist (along X)
        box([3, 5, 4], [13, 7, 12], "anvil"),
        box([5, 7, 5], [11, 10, 11], "anvil"),
        # Main working face (smooth steel top along X)
        box([2, 10, 4], [13, 14, 12], "anvil", "face"),
        # Horn (west/left taper along X)
        box([0, 11, 5.5], [2, 13.5, 10.5], "anvil", "face"),
        # Heel (east/right hardy hole step along X)
        box([13, 11, 5], [15.5, 13.5, 11], "anvil", "face"),
    ]
    write_json(models_dir / "smithing_anvil.json", {
        "textures": {
            "particle": "minecraft:block/anvil",
            "stump": "minecraft:block/dark_oak_log",
            "stump_top": "minecraft:block/dark_oak_log_top",
            "band": "minecraft:block/cauldron_side",
            "anvil": "minecraft:block/anvil",
            "face": "minecraft:block/iron_block",
        },
        "display": GUI_DISPLAY,
        "elements": anvil_elements,
    })
    write_json(items_dir / "smithing_anvil.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/smithing_anvil"}})

    # 3. Quenching Basin & Grindstone (Löschbecken & Wetzstein)
    basin_elements = [
        # Left side (x: 1..10): Wooden tub with hollow outer iron hoops (interior [2.5..8.5, 2..11, 3.5..12.5] is 100% hollow)
        box([1, 0, 2], [10, 2, 14], "wood"),
        box([1, 2, 2], [10, 11, 3.5], "wood"),
        box([1, 2, 12.5], [10, 11, 14], "wood"),
        box([1, 2, 3.5], [2.5, 11, 12.5], "wood"),
        box([8.5, 2, 3.5], [10, 11, 12.5], "wood"),
        *hollow_band(0.75, 3, 1.75, 10.25, 4.5, 14.25, 0.35, "hoop"),
        *hollow_band(0.75, 8, 1.75, 10.25, 9.5, 14.25, 0.35, "hoop"),
        # Right side (x: 10.5..15): Grindstone frame + sharpening wheel (center X=12.75, top Y=12)
        box([10.5, 0, 4], [12, 9, 6], "frame"),
        box([13.5, 0, 4], [15, 9, 6], "frame"),
        box([10.5, 0, 10], [12, 9, 12], "frame"),
        box([13.5, 0, 10], [15, 9, 12], "frame"),
        box([10, 7.5, 7.25], [15.5, 8.75, 8.75], "hoop"),
        box([11.8, 4.5, 4.5], [13.7, 12, 11.5], "stone", "stone_side"),
    ]
    write_json(models_dir / "quenching_basin.json", {
        "textures": {
            "particle": "minecraft:block/spruce_planks",
            "wood": "minecraft:block/barrel_side",
            "hoop": "minecraft:block/anvil",
            "frame": "minecraft:block/dark_oak_planks",
            "stone": "minecraft:block/polished_andesite",
            "stone_side": "minecraft:block/smooth_stone",
        },
        "display": GUI_DISPLAY,
        "elements": basin_elements,
    })
    write_json(items_dir / "quenching_basin.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/quenching_basin"}})

    # Basin water / oil levels (inside hollow tub [2.5..8.5, 2..9.2, 3.5..12.5])
    write_json(models_dir / "basin_water.json", {
        "textures": {
            "particle": "minecraft:block/water_still",
            "liquid": "minecraft:block/light_blue_stained_glass",
        },
        "elements": [box([2.5, 2.1, 3.5], [8.5, 9.2, 12.5], "liquid")],
    })
    write_json(items_dir / "block" / "basin_water.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/basin_water"}})

    write_json(models_dir / "basin_oil.json", {
        "textures": {
            "particle": "minecraft:block/honey_block_top",
            "liquid": "minecraft:block/honey_block_top",
        },
        "elements": [box([2.5, 2.1, 3.5], [8.5, 9.2, 12.5], "liquid")],
    })
    write_json(items_dir / "block" / "basin_oil.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/basin_oil"}})

    # 4. Tinker Workbench / Montagetisch (smithing_table_station)
    table_elements = [
        # Four sturdy legs
        box([1, 0, 1], [4, 11, 4], "leg"),
        box([12, 0, 1], [15, 11, 4], "leg"),
        box([1, 0, 12], [4, 11, 15], "leg"),
        box([12, 0, 12], [15, 11, 15], "leg"),
        # Lower tool shelf
        box([2, 3, 2], [14, 4.5, 14], "shelf"),
        # Heavy tabletop with metal vise on the side
        box([0, 11, 0], [16, 15, 16], "side", "top", "bottom"),
        # Iron vise jaw at front-left
        box([2, 12, 15], [7, 16, 16.75], "vise"),
    ]
    write_json(models_dir / "smithing_table_station.json", {
        "textures": {
            "particle": "minecraft:block/smithing_table_top",
            "leg": "minecraft:block/dark_oak_log",
            "shelf": "minecraft:block/spruce_planks",
            "side": "minecraft:block/smithing_table_side",
            "top": "minecraft:block/smithing_table_top",
            "bottom": "minecraft:block/smithing_table_bottom",
            "vise": "minecraft:block/anvil",
        },
        "display": GUI_DISPLAY,
        "elements": table_elements,
    })
    write_json(items_dir / "smithing_table_station.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/smithing_table_station"}})
    for st in ("forge_crucible", "smithing_anvil", "quenching_basin", "smithing_table_station"):
        write_json(items_dir / "block" / f"{st}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{st}"}})


SPRITE_3D_DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [12, -24, 0], "scale": [0.95, 0.95, 0.95]},
}


def register_3d_sprite(item_id: str, sprite: tuple, glow: int = 0):
    """Voxel model from a sprite and its per-pixel depth (smithing_sprites: molds, blanks, handles, binding ring)."""
    img, depth = sprite
    tex_path = ASSETS / "textures" / "item" / f"{item_id}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex_path)
    elements = sprites.voxel_elements(depth)
    if glow:
        for e in elements:
            e["light_emission"] = glow
    write_json(ASSETS / "models" / "item" / f"{item_id}.json", {
        "textures": {"layer0": f"{NS}:item/{item_id}", "particle": f"{NS}:item/{item_id}"},
        "gui_light": "side",
        "display": SPRITE_3D_DISPLAY,
        "elements": elements,
    })
    write_json(ASSETS / "items" / f"{item_id}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
    })


def register_2d_item(item_id: str, img: Image.Image, handheld: bool = False):
    tex_path = ASSETS / "textures" / "item" / f"{item_id}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex_path)
    write_json(ASSETS / "models" / "item" / f"{item_id}.json", {
        "parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
        "textures": {"layer0": f"{NS}:item/{item_id}"},
    })
    write_json(ASSETS / "items" / f"{item_id}.json", {
        "model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"},
    })


def build_3d_armor_elements(kind: str, tier: str) -> list[dict]:
    """Ornate armor pieces from boxes like the forged tools (front = north). Richer with the tier: copper plain, iron
    with a nose guard and tassets, gold with a crest and gem, diamond with wings, spurs and gold filigree, netherite
    with horns and a glowing amethyst."""
    rank = TIER_RANK[tier]
    e = []
    if kind == "helmet":
        # The player's head fills about 1.6-14.4 here: the shell stays outside of it everywhere, the dome sits on top.
        # Open face with a narrow brow plate and slim cheek guards.
        def mirror(frm, to):
            return [16 - to[0], frm[1], frm[2]], [16 - frm[0], to[1], to[2]]
        e += [rbox([1.0, 1.4, 1.0], [1.8, 13.0, 15.0], "metal"),
              rbox([14.2, 1.4, 1.0], [15.0, 13.0, 15.0], "metal"),
              rbox([1.8, 1.4, 14.2], [14.2, 13.0, 15.0], "metal"),
              rbox([0.8, 13.0, 0.8], [15.2, 14.8, 15.2], "metal"),
              rbox([1.2, 14.8, 1.2], [14.8, 15.8, 14.8], "metal"),
              rbox([2.6, 15.8, 2.6], [13.4, 16.8, 13.4], "metal"),
              rbox([4.4, 16.8, 4.4], [11.6, 17.4, 11.6], "metal"),
              rbox([1.8, 11.6, 0.8], [14.2, 13.0, 1.4], "metal"),
              rbox([1.8, 5.0, 0.8], [3.2, 11.6, 1.4], "metal"),
              rbox([12.8, 5.0, 0.8], [14.2, 11.6, 1.4], "metal"),
              rbox([1.4, 5.4, 0.5], [2.8, 11.0, 0.8], "edge"),
              rbox([13.2, 5.4, 0.5], [14.6, 11.0, 0.8], "edge"),
              rbox([0.6, 1.0, 0.6], [1.8, 1.8, 15.4], "accent"),
              rbox([14.2, 1.0, 0.6], [15.4, 1.8, 15.4], "accent"),
              rbox([1.8, 1.0, 14.2], [14.2, 1.8, 15.4], "accent"),
              rbox([0.6, 13.6, 0.6], [15.4, 14.2, 15.4], "accent")]
        if rank >= 1:
            # nose guard from the brow
            e.append(rbox([7.5, 8.6, 0.5], [8.5, 13.0, 0.8], "edge"))
        if rank >= 2:
            e.append(rbox([7.3, 17.0, 2.8], [8.7, 18.8, 13.2], "accent"))
            e.append(rbox([7.5, 18.8, 4.2], [8.5, 19.6, 11.8], "edge"))
        if rank >= 3:
            for frm, to, tex in (([-0.6, 11.0, 3.6], [1.0, 15.6, 9.4], "edge"), ([-0.8, 10.8, 3.4], [0.0, 15.8, 9.6], "accent")):
                e.append(rbox(frm, to, tex, angle=-22.5, origin=[1.0, 13.4, 6.4]))
                mf, mt = mirror(frm, to)
                e.append(rbox(mf, mt, tex, angle=22.5, origin=[15.0, 13.4, 6.4]))
        if rank >= 4:
            for frm, to, tex, angle, origin in (([-2.4, 14.0, 6.6], [1.0, 16.0, 9.4], "dark", -22.5, [1.0, 15.0, 8.0]),
                                                ([-3.8, 15.6, 7.0], [-1.8, 17.6, 9.0], "dark", -45, [-1.8, 16.2, 8.0]),
                                                ([-4.2, 17.4, 7.3], [-2.8, 19.0, 8.7], "edge", -45, [-1.8, 16.2, 8.0])):
                e.append(rbox(frm, to, tex, angle=angle, origin=origin))
                mf, mt = mirror(frm, to)
                e.append(rbox(mf, mt, tex, angle=-angle, origin=[16 - origin[0], origin[1], origin[2]]))
        if tier in GEM_BY_TIER:
            e.append(rbox([6.6, 11.8, 0.4], [9.4, 13.4, 0.8], "accent"))
            e.append(rbox([7.1, 12.1, 0.1], [8.9, 13.1, 0.5], "gem"))
    elif kind == "chestplate":
        # Breastplate with two raised chest plates, abdominal lames, layered pauldrons and a high gorget
        e += [rbox([3.0, 2.0, 6.0], [13.0, 12.6, 10.0], "metal"),
              rbox([3.4, 8.0, 5.3], [7.8, 12.2, 6.0], "metal"),
              rbox([8.2, 8.0, 5.3], [12.6, 12.2, 6.0], "metal"),
              rbox([7.6, 2.4, 5.1], [8.4, 12.4, 5.5], "edge"),
              rbox([3.4, 6.0, 5.5], [12.6, 7.4, 6.0], "metal"),
              rbox([3.6, 4.2, 5.5], [12.4, 5.6, 6.0], "metal"),
              rbox([3.4, 5.6, 5.7], [12.6, 6.0, 6.0], "dark"),
              rbox([3.4, 7.4, 5.7], [12.6, 7.8, 6.0], "dark"),
              rbox([4.2, 12.4, 5.2], [11.8, 14.4, 10.8], "accent"),
              rbox([5.0, 14.4, 5.8], [11.0, 14.9, 10.2], "dark"),
              rbox([2.8, 2.0, 5.6], [13.2, 3.4, 10.4], "accent"),
              # pauldrons: two lames each
              rbox([0.4, 10.6, 5.0], [5.0, 13.8, 11.0], "metal", angle=22.5, origin=[4.2, 12.4, 8.0]),
              rbox([0.0, 9.0, 5.2], [4.2, 10.8, 10.8], "metal", angle=22.5, origin=[4.2, 12.4, 8.0]),
              rbox([11.0, 10.6, 5.0], [15.6, 13.8, 11.0], "metal", angle=-22.5, origin=[11.8, 12.4, 8.0]),
              rbox([11.8, 9.0, 5.2], [16.0, 10.8, 10.8], "metal", angle=-22.5, origin=[11.8, 12.4, 8.0]),
              rbox([-0.2, 8.6, 5.0], [4.4, 9.2, 11.0], "accent", angle=22.5, origin=[4.2, 12.4, 8.0]),
              rbox([11.6, 8.6, 5.0], [16.2, 9.2, 11.0], "accent", angle=-22.5, origin=[11.8, 12.4, 8.0])]
        if rank >= 1:
            e.append(rbox([3.0, 1.0, 6.4], [13.0, 2.0, 9.6], "dark"))
        if rank >= 3:
            e.append(rbox([3.2, 8.0, 5.1], [3.8, 12.2, 5.6], "accent"))
            e.append(rbox([12.2, 8.0, 5.1], [12.8, 12.2, 5.6], "accent"))
            e.append(rbox([4.0, 12.0, 5.1], [12.0, 12.4, 5.5], "accent"))
        if tier in GEM_BY_TIER:
            e.append(rbox([6.6, 8.6, 4.7], [9.4, 11.4, 5.1], "accent"))
            e.append(rbox([7.1, 9.1, 4.3], [8.9, 10.9, 4.8], "gem"))
    elif kind == "leggings":
        e += [rbox([3.0, 12.0, 5.8], [13.0, 14.0, 10.2], "accent"),
              rbox([3.2, 10.0, 6.2], [12.8, 12.0, 9.8], "metal"),
              rbox([3.2, 1.0, 6.4], [7.6, 10.0, 9.6], "metal"),
              rbox([8.4, 1.0, 6.4], [12.8, 10.0, 9.6], "metal"),
              # knee cops with side wings
              rbox([3.2, 4.4, 5.8], [7.6, 6.6, 6.4], "edge"),
              rbox([8.4, 4.4, 5.8], [12.8, 6.6, 6.4], "edge"),
              rbox([2.4, 4.6, 6.2], [3.2, 6.4, 8.4], "edge"),
              rbox([12.8, 4.6, 6.2], [13.6, 6.4, 8.4], "edge"),
              rbox([4.6, 4.9, 5.5], [6.2, 6.1, 5.8], "accent"),
              rbox([9.8, 4.9, 5.5], [11.4, 6.1, 5.8], "accent"),
              rbox([7.0, 12.3, 5.4], [9.0, 13.7, 5.8], "edge")]
        if rank >= 1:
            # tassets: two lames over each thigh
            e.append(rbox([3.2, 9.4, 5.8], [7.6, 11.8, 6.2], "metal"))
            e.append(rbox([8.4, 9.4, 5.8], [12.8, 11.8, 6.2], "metal"))
            e.append(rbox([3.4, 7.6, 5.9], [7.4, 9.6, 6.3], "metal"))
            e.append(rbox([8.6, 7.6, 5.9], [12.6, 9.6, 6.3], "metal"))
        if rank >= 3:
            e.append(rbox([3.3, 7.4, 5.7], [7.5, 7.9, 6.4], "accent"))
            e.append(rbox([8.5, 7.4, 5.7], [12.7, 7.9, 6.4], "accent"))
            e.append(rbox([3.0, 1.0, 6.2], [7.8, 1.6, 9.8], "accent"))
            e.append(rbox([8.2, 1.0, 6.2], [13.0, 1.6, 9.8], "accent"))
        if tier in GEM_BY_TIER:
            e.append(rbox([7.4, 12.6, 5.0], [8.6, 13.4, 5.4], "gem"))
    elif kind == "boots":
        for dx in (0.0, 6.4):
            e += [rbox([2.6 + dx, 4.0, 6.0], [7.0 + dx, 10.4, 10.0], "metal"),
                  rbox([3.0 + dx, 4.4, 5.5], [6.6 + dx, 9.8, 6.0], "edge"),
                  rbox([2.6 + dx, 1.2, 4.2], [7.0 + dx, 4.0, 10.0], "metal"),
                  # sabaton lames towards the toe
                  rbox([2.7 + dx, 1.2, 3.0], [6.9 + dx, 3.4, 4.2], "metal"),
                  rbox([2.9 + dx, 1.2, 2.0], [6.7 + dx, 2.8, 3.0], "edge"),
                  rbox([2.6 + dx, 3.2, 3.8], [7.0 + dx, 3.6, 4.4], "dark"),
                  rbox([2.4 + dx, 0.4, 1.8], [7.2 + dx, 1.2, 10.2], "dark"),
                  rbox([2.2 + dx, 10.0, 5.6], [7.4 + dx, 11.8, 10.4], "accent")]
            if rank >= 1:
                e.append(rbox([2.4 + dx, 6.2, 5.8], [7.2 + dx, 6.8, 10.2], "accent"))
            if rank >= 3:
                e.append(rbox([4.4 + dx, 1.8, 10.0], [5.2 + dx, 2.6, 11.6], "accent"))
                e.append(rbox([4.2 + dx, 1.6, 11.4], [5.4 + dx, 2.8, 12.0], "edge"))
            if tier in GEM_BY_TIER:
                e.append(rbox([4.1 + dx, 10.4, 5.1], [5.5 + dx, 11.4, 5.6], "gem"))
    if tier == "netherite":
        for el in e:
            if el["faces"]["north"]["texture"] == "#gem":
                el["light_emission"] = 10
    return separate_planes(e)


ARMOR_GUI_ROTATION = [18, 160, 0]


def register_3d_armor(item_id: str, kind: str, tier: str, img_fallback: Image.Image, design: str | None = None):
    """3D model of a finished armor piece; the helmet also shows this model when worn (head slot, no equipment asset).
    With a design, plates, trim and gem take the design's look (the edge keeps the tier's colour, like the tools)."""
    tex_path = ASSETS / "textures" / "item" / f"{item_id}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    img_fallback.save(tex_path)
    elements = build_3d_armor_elements(kind, tier)
    display = {
        "gui": fit_gui(elements, ARMOR_GUI_ROTATION, fill=14.5, max_scale=1.0),
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        # On the head a bit larger than the shell (1.4-14.6) so the skin's hat layer (1.125x the head) does not show through
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.16, 1.16, 1.16]},
    }
    accent = ARMOR_ACCENT[tier]
    write_json(ASSETS / "models" / "item" / f"{item_id}.json", {
        "textures": {
            "particle": f"{NS}:item/{item_id}",
            "metal": f"{NS}:item/mat_{tier}",
            "edge": f"{NS}:item/mat_{tier}_edge",
            "dark": f"{NS}:item/mat_{tier}_dark",
            "accent": f"{NS}:item/mat_{accent}",
            "gem": f"{NS}:item/mat_{GEM_BY_TIER.get(tier, 'ruby')}",
        } | ({} if design is None else {
            "metal": f"{NS}:item/mat_{DESIGNS[design][0]}",
            "dark": f"{NS}:item/mat_{DESIGNS[design][0]}_dark",
            "accent": f"{NS}:item/{DESIGNS[design][1]}",
            "gem": f"{NS}:item/mat_{DESIGNS[design][3]}",
        }),
        "display": display,
        "elements": elements,
    })
    write_json(ASSETS / "items" / f"{item_id}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{item_id}"}})


def register_armor_kind(items_yml, de_lang, en_lang, tier, de_tier, en_tier, proto_prefix, repair_mat, kind, de_tool, en_tool, de_part, en_part):
    """Hot plate, forged plate and the finished armor piece of one tier (the blank is registered by the caller)."""
    gem = GEM_BY_TIER.get(tier)
    hot_id, plate_id, armor_id = f"hot_{tier}_{kind}_plate", f"{tier}_{kind}_plate", f"forged_{tier}_{kind}"
    accent = PALETTES[ARMOR_ACCENT[tier]]
    register_3d_sprite(hot_id, sprites.plate_3d(kind, PALETTES[tier], accent, hot=True), glow=12)
    items_yml[f"{NS}:{hot_id}"] = {"model": f"{NS}:{hot_id}", "name": f"item.{NS}.{hot_id}", "tooltip": f"tooltip.{NS}.hot_head", "max-stack": 1}
    de_lang[f"item.{NS}.{hot_id}"] = f"Heiße {de_tier}-{de_part}"
    en_lang[f"item.{NS}.{hot_id}"] = f"Hot {en_tier} {en_part}"
    register_3d_sprite(plate_id, sprites.plate_3d(kind, PALETTES[tier], accent))
    items_yml[f"{NS}:{plate_id}"] = {"model": f"{NS}:{plate_id}", "name": f"item.{NS}.{plate_id}", "tooltip": f"tooltip.{NS}.armor_plate", "max-stack": 16}
    de_lang[f"item.{NS}.{plate_id}"] = f"Geschmiedete {de_tier}-{de_part}"
    en_lang[f"item.{NS}.{plate_id}"] = f"Forged {en_tier} {en_part}"
    register_3d_armor(armor_id, kind, tier, sprites.armor_item(kind, PALETTES[tier], accent, gem))
    # Model variants for the designs (chosen via the item_model component when a design is hammered on)
    for design, (metal, accent_tex, _, design_gem) in DESIGNS.items():
        register_3d_armor(f"{armor_id}_design_{design}", kind, tier,
                          sprites.armor_item(kind, PALETTES[metal], PALETTES[accent_tex.removeprefix("mat_")], design_gem if gem else None), design=design)
    prototype = f"{proto_prefix}_{kind.upper()}"
    items_yml[f"{NS}:{armor_id}"] = {"model": f"{NS}:{armor_id}", "name": f"item.{NS}.{armor_id}", "base": prototype, "prototype": prototype,
                                     "repair": repair_mat,
                                     # The helmet is worn as its 3D model (no equipment asset), the rest as forged textures
                                     "equippable": {"slot": ARMOR_SLOTS[kind], "asset": "none" if kind == "helmet" else f"{NS}:forged_{tier}"}}
    article = {"helmet": "Geschmiedeter", "chestplate": "Geschmiedeter", "leggings": "Geschmiedeter", "boots": "Geschmiedete"}[kind]
    de_lang[f"item.{NS}.{armor_id}"] = f"{article} {de_tier}-{de_tool}"
    en_lang[f"item.{NS}.{armor_id}"] = f"Forged {en_tier} {en_tool}"


def generate_armor_equipment():
    """Worn look of the forged armor: equipment asset + humanoid textures per tier."""
    for tier, *_ in TIERS:
        gem = GEM_BY_TIER.get(tier)
        for layer in ("humanoid", "humanoid_leggings"):
            path = ASSETS / "textures" / "entity" / "equipment" / layer / f"forged_{tier}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            sprites.worn_texture(layer, PALETTES[tier], PALETTES[ARMOR_ACCENT[tier]], gem).save(path)
        write_json(ASSETS / "equipment" / f"forged_{tier}.json", {"layers": {
            "humanoid": [{"texture": f"{NS}:forged_{tier}"}],
            "humanoid_leggings": [{"texture": f"{NS}:forged_{tier}"}]}})
    # Designs look the same on every tier when worn (the plugin swaps the equipment asset)
    for design, (metal, accent_tex, _, gem) in DESIGNS.items():
        for layer in ("humanoid", "humanoid_leggings"):
            path = ASSETS / "textures" / "entity" / "equipment" / layer / f"forged_design_{design}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            sprites.worn_texture(layer, PALETTES[metal], PALETTES[accent_tex.removeprefix("mat_")], gem).save(path)
        write_json(ASSETS / "equipment" / f"forged_design_{design}.json", {"layers": {
            "humanoid": [{"texture": f"{NS}:forged_design_{design}"}],
            "humanoid_leggings": [{"texture": f"{NS}:forged_design_{design}"}]}})


def main():
    generate_armor_equipment()
    generate_station_models()
    generate_material_textures()

    items_yml = {}
    de_lang = {
        f"block.{NS}.forge_crucible": "Schmelze (3x3) & Gießtisch",
        f"tooltip.{NS}.forge_crucible": "3x3-Multiblock: Verbraucht Lava (1 Eimer schmilzt 6 Barren), schmilzt 1 Erzsorte und gießt auf den Tisch.",
        f"block.{NS}.smithing_anvil": "Schmiedeamboss",
        f"tooltip.{NS}.smithing_anvil": "Glühende Rohlinge im richtigen Takt mit dem Schmiedehammer formen.",
        f"block.{NS}.quenching_basin": "Löschbecken & Wetzstein",
        f"tooltip.{NS}.quenching_basin": "Heiße Klingen in Wasser oder Öl abschrecken und am Wetzstein schärfen.",
        f"block.{NS}.smithing_table_station": "Schmiede-Montagetisch",
        f"tooltip.{NS}.smithing_table_station": "Griff, Werkzeugkopf und Verstärkungen ohne Menü zusammenfügen.",
        "kaleidoscope.menu.tab.smithing": "Schmiedekunst",
        # Station HUD & Actionbar messages
        f"{NS}.crucible.need_heat": "Zuerst Lava (Eimer) als Brennstoff einfüllen!",
        f"{NS}.crucible.lava_filled": "Lava eingefüllt (für %s/%s Barren)!",
        f"{NS}.crucible.need_mold": "Gebrannte Gussform auf den Gießtisch!",
        f"{NS}.crucible.no_mixing": "Schon ein anderes Erz drin - kein Mischen!",
        f"{NS}.crucible.added_metal": "Eingelegt (%s/%s) | Lava: %s/%s",
        f"{NS}.crucible.melting": "Schmilzt: %s%% (%s) | Lava: %s/%s",
        f"{NS}.crucible.ready": "Guss fertig! Mit Schmiedezange entnehmen",
        f"{NS}.crucible.need_tongs": "Schmiedezange nehmen - der Rohling ist heiß!",
        f"{NS}.anvil.need_blank": "Lege einen glühenden Rohling vom Gießtisch auf den Amboss!",
        f"{NS}.anvil.need_hammer": "Linksklick im Takt mit dem Schmiedehammer!",
        f"{NS}.anvil.progress": "Schmieden: %s/%s | Takt: %s | Präzision: %s%%",
        f"{NS}.anvil.ready": "Fertig! Heißen Kopf mit Schmiedezange nehmen",
        f"{NS}.basin.need_liquid": "Erst Wasser oder Speiseöl ins Becken füllen!",
        f"{NS}.basin.quenched": "Abgeschreckt! %s× Linksklick am Wetzstein",
        f"{NS}.basin.sharpening": "Schärfen am Wetzstein: %s/%s",
        f"{NS}.basin.done": "Werkzeugkopf fertiggestellt (%s)!",
        f"{NS}.table.put_handle": "Lege zuerst Griff oder Rüstungspolster auf!",
        f"{NS}.table.put_head": "Lege nun einen Werkzeugkopf auf den Griff!",
        f"{NS}.table.need_binding": "Fehlt: Verstärkungsring aus gleichem Material",
        f"{NS}.table.hammer_to_finish": "Noch %s× Linksklick mit dem Hammer",
        f"{NS}.table.done": "Werkzeug erfolgreich zusammengebaut!",
        # Quality names
        f"tooltip.{NS}.quality.crude": "Schmiede-Qualität: Grob (75% Haltbarkeit)",
        f"tooltip.{NS}.quality.sturdy": "Schmiede-Qualität: Solide (100% Standard)",
        f"tooltip.{NS}.quality.tempered": "Schmiede-Qualität: Gehärtet (120% Haltbarkeit, +10% Tempo)",
        f"tooltip.{NS}.quality.masterwork": "Schmiede-Qualität: Meisterwerk (140% Haltbarkeit, +15% Tempo, +0.5 Schaden)",
        f"tooltip.{NS}.heavy_pickaxe.ability": "Spezial: Baut 1x2 Blöcke gleichzeitig ab (Schleichen für 1x1).",
        # Cookbook categories & hints
        f"jei.{NS}.crucible": "Schmelze (3x3) & Gießtisch",
        f"jei.{NS}.anvil": "Schmiedeamboss",
        f"jei.{NS}.assembly": "Schmiede-Montagetisch",
        f"jei.{NS}.crucible.hint1": "1. Lavaeimer in die Schmelze füllen (1 Eimer schmilzt 6 Barren) & Gussform auf den Gießtisch legen",
        f"jei.{NS}.crucible.hint2": "2. Roherz, Erzblöcke oder Barren einer einzigen Sorte einwerfen (kein Mischen!)",
        f"jei.{NS}.crucible.hint3": "3. Nach dem Guss den glühenden Rohling mit der Schmiedezange vom Tisch nehmen",
        f"jei.{NS}.anvil.hint1": "1. Glühenden Rohling auf den Schmiedeamboss legen",
        f"jei.{NS}.anvil.hint2": "2. 12× mit dem Schmiedehammer im Takt [★] schmieden",
        f"jei.{NS}.anvil.hint3": "3. Im Löschbecken (Wasser/Öl) abschrecken & 6× am Wetzstein schärfen",
        f"jei.{NS}.assembly.hint1": "1. Werkzeuggriff + gehärteten Werkzeugkopf auflegen",
        f"jei.{NS}.assembly.hint2": "2. Mit 6 Hammerschlägen zum fertigen Werkzeug verbinden",
        f"item.{NS}.smithing_book": "Schmiedehandbuch",
        f"tooltip.{NS}.smithing_book": "Alle Rezepte und Anleitungen zur Schmiedekunst (/schmieden).",
        # Smithing Book Dialog UI translations
        f"{NS}.book.title": "Schmiedehandbuch",
        f"{NS}.book.intro": "Alle Stationen, Gussformen, Qualitätsstufen und Werkzeuge der Schmiedekunst im Überblick.",
        f"{NS}.book.section.crucible": "Schmelze (3x3) & Gießtisch",
        f"{NS}.book.section.crucible.header": "Die 3x3-Schmelze schmilzt Erze mit Lava und gießt sie auf den Gießtisch davor.",
        f"{NS}.book.section.crucible.line1": "Fülle die Schmelze mit einem Lavaeimer (1 Eimer reicht für 2 Gussvorgänge, Lava wird dabei verbraucht).",
        f"{NS}.book.book_mold_tip": "Lege vorne auf den Gießtisch eine gebrannte Gussform.",
        f"{NS}.book.section.crucible.line2": "Lege vorne auf den Gießtisch eine gebrannte Gussform und gib Roherz, Erz oder Barren einer Sorte in die Schmelze (kein Mischen!).",
        f"{NS}.book.section.crucible.line3": "Das flüssige Metall fließt auf den Tisch – nimm den glühenden Rohling mit der Schmiedezange ab (ohne Zange verbrennst du dich!).",
        f"{NS}.book.section.anvil": "Schmiedeamboss",
        f"{NS}.book.section.anvil.header": "Auf dem Schmiedeamboss formst du glühende Rohlinge im Rhythmus zum heißen Werkzeugkopf.",
        f"{NS}.book.section.anvil.line1": "Lege den glühenden Rohling auf den Amboss – in deiner Actionbar pendelt nun der Takt-Balken.",
        f"{NS}.book.section.anvil.line2": "Schlage 12× mit dem Schmiedehammer (Linksklick), genau wenn der Balken die grüne Mitte [★] trifft!",
        f"{NS}.book.section.anvil.line3": "Nimm den fertig geschmiedeten heißen Kopf mit der Schmiedezange (Rechtsklick) ab.",
        f"{NS}.book.section.basin": "Löschbecken & Wetzstein",
        f"{NS}.book.section.basin.header": "Im Löschbecken kühlst du heiße Werkzeugköpfe ab und schärfst sie am Wetzstein.",
        f"{NS}.book.section.basin.line1": "Fülle das Becken mit einem Wassereimer oder mit Speiseöl (aus KaleidoscopeCookery / KaleidoscopeGrilling).",
        f"{NS}.book.section.basin.line2": "Tipp: Das Abschrecken in Speiseöl verleiht +8% Präzisionsbonus für Meisterwerk-Qualität!",
        f"{NS}.book.section.basin.line3": "Schlage danach 6× mit Linksklick auf den Wetzstein, um den gehärteten Kopf fertigzustellen.",
        f"{NS}.book.section.assembly": "Schmiede-Montagetisch",
        f"{NS}.book.section.assembly.header": "Auf dem Montagetisch verbindest du Griffe und Werkzeugköpfe ohne Menü.",
        f"{NS}.book.section.assembly.line1": "1. Lege einen Holzgriff oder Verstärkten Schmiedegriff (+10% Haltbarkeit) mit Rechtsklick auf.",
        f"{NS}.book.section.assembly.line2": "2. Lege den geschmiedeten Werkzeugkopf auf (für den 1x2-Stollenhammer zusätzlich einen Verstärkungsring aus demselben Material).",
        f"{NS}.book.section.assembly.line3": "3. Schlage 6× mit dem Schmiedehammer (Linksklick), um die Waffe oder das Werkzeug fertigzustellen!",
        f"{NS}.book.section.crafting": "Werkbank (Stationen & Zubehör)",
        f"{NS}.book.section.crafting.header": "Herstellung der Schmiedestationen, Werkzeuge, Griffe und rohen Ton-Gussformen an der Werkbank.",
        f"{NS}.book.section.furnace": "Schmelzofen (Gussformen brennen)",
        f"{NS}.book.section.furnace.header": "Rohe Ton-Gussformen werden im Ofen zu hitzebeständigen Gussformen gebrannt.",
        f"jei.{NS}.basin.hint1": "1. Löschbecken mit Wassereimer oder Speiseöl füllen (Öl gibt +8% Qualitätsbonus)",
        f"jei.{NS}.basin.hint2": "2. Heißen Werkzeugkopf mit Rechtsklick im Bad abschrecken",
        f"jei.{NS}.basin.hint3": "3. Mit 6× Linksklick am Wetzstein schärfen",
    }

    en_lang = {
        f"block.{NS}.forge_crucible": "Smeltery (3x3) & Casting Table",
        f"tooltip.{NS}.forge_crucible": "3x3 Multiblock: Consumes Lava (1 bucket melts 6 ingots) to smelt 1 ore type and pours it onto the Casting Table.",
        f"block.{NS}.smithing_anvil": "Smithing Anvil",
        f"tooltip.{NS}.smithing_anvil": "Shape glowing blanks with the smithing hammer in rhythm.",
        f"block.{NS}.quenching_basin": "Quenching Basin & Grindstone",
        f"tooltip.{NS}.quenching_basin": "Quench hot blades in water or oil and sharpen them on the grindstone.",
        f"block.{NS}.smithing_table_station": "Tinker Assembly Table",
        f"tooltip.{NS}.smithing_table_station": "Assemble handles, tool heads and bindings without menus.",
        "kaleidoscope.menu.tab.smithing": "Smithing",
        f"{NS}.crucible.need_heat": "Fill in lava (bucket) as fuel first!",
        f"{NS}.crucible.lava_filled": "Lava added (enough for %s/%s ingots)!",
        f"{NS}.crucible.need_mold": "Put a fired mold on the Casting Table!",
        f"{NS}.crucible.no_mixing": "Another ore is already inside - no mixing!",
        f"{NS}.crucible.added_metal": "Added ore/metal (%s/%s) | Lava: %s/%s",
        f"{NS}.crucible.melting": "Smelting: %s%% (%s) | Lava: %s/%s",
        f"{NS}.crucible.ready": "Cast done! Take it out with Smithing Tongs",
        f"{NS}.crucible.need_tongs": "Use Smithing Tongs - the blank is too hot!",
        f"{NS}.anvil.need_blank": "Place a glowing blank from the Casting Table onto the anvil!",
        f"{NS}.anvil.need_hammer": "Left-click in rhythm with a Smithing Hammer!",
        f"{NS}.anvil.progress": "Forging: %s/%s | Rhythm: %s | Precision: %s%%",
        f"{NS}.anvil.ready": "Done! Take the hot head with Smithing Tongs",
        f"{NS}.basin.need_liquid": "Fill the basin with water or oil first!",
        f"{NS}.basin.quenched": "Quenched! Left-click %s× on the grindstone",
        f"{NS}.basin.sharpening": "Sharpening on grindstone: %s/%s",
        f"{NS}.basin.done": "Tool head finished (%s)!",
        f"{NS}.table.put_handle": "Place a tool handle or armor padding first!",
        f"{NS}.table.put_head": "Now place a forged tool head onto the handle!",
        f"{NS}.table.need_binding": "Needs a Binding Ring of the same material!",
        f"{NS}.table.hammer_to_finish": "Strike %s× with the hammer (left-click)",
        f"{NS}.table.done": "Tool assembled!",
        f"tooltip.{NS}.quality.crude": "Forged Quality: Crude (75% Durability)",
        f"tooltip.{NS}.quality.sturdy": "Forged Quality: Sturdy (100% Standard)",
        f"tooltip.{NS}.quality.tempered": "Forged Quality: Tempered (120% Durability, +10% Speed)",
        f"tooltip.{NS}.quality.masterwork": "Forged Quality: Masterwork (140% Durability, +15% Speed, +0.5 Damage)",
        f"tooltip.{NS}.heavy_pickaxe.ability": "Special: Mines 1x2 blocks at once (Sneak for 1x1).",
        f"jei.{NS}.crucible": "Smeltery (3x3) & Casting Table",
        f"jei.{NS}.anvil": "Smithing Anvil",
        f"jei.{NS}.assembly": "Tinker Assembly Table",
        f"jei.{NS}.crucible.hint1": "1. Fill Smeltery with Lava Bucket (1 bucket melts 6 ingots) & place mold on Casting Table",
        f"jei.{NS}.crucible.hint2": "2. Add Raw Ore, Ore blocks, or Ingots of a single metal (no mixing!)",
        f"jei.{NS}.crucible.hint3": "3. Once poured onto the Casting Table, extract the glowing blank with Smithing Tongs",
        f"jei.{NS}.anvil.hint1": "1. Place the glowing blank on the Smithing Anvil",
        f"jei.{NS}.anvil.hint2": "2. Strike 12× in rhythm [★] with the Smithing Hammer (left-click)",
        f"jei.{NS}.anvil.hint3": "3. Pick up the hot head with Smithing Tongs (right-click)",
        f"jei.{NS}.assembly.hint1": "1. Place tool handle + tempered tool head on the table",
        f"jei.{NS}.assembly.hint2": "2. Strike 6 times with the Smithing Hammer to assemble",
        f"item.{NS}.smithing_book": "Smithing Guidebook",
        f"tooltip.{NS}.smithing_book": "All smithing recipes and station guides (/smithing).",
        # Smithing Book Dialog UI translations
        f"{NS}.book.title": "Smithing Guidebook",
        f"{NS}.book.intro": "All smithing stations, molds, quality tiers, and forged tools at a glance.",
        f"{NS}.book.section.crucible": "Smeltery (3x3) & Casting Table",
        f"{NS}.book.section.crucible.header": "The 3x3 Smeltery consumes Lava to melt ores and pours them onto the Casting Table in front.",
        f"{NS}.book.section.crucible.line1": "Fill the Smeltery with a Lava Bucket (1 bucket fuels 2 casts; lava is consumed).",
        f"{NS}.book.section.crucible.line2": "Place a fired mold onto the front Casting Table, then add Raw Ore, Ore, or Ingots of a single metal (no mixing!).",
        f"{NS}.book.section.crucible.line3": "Molten metal pours onto the table – extract the glowing blank with Smithing Tongs (touching without tongs burns you!).",
        f"{NS}.book.section.anvil": "Smithing Anvil",
        f"{NS}.book.section.anvil.header": "Forge glowing blanks into hot tool heads in rhythm on the Smithing Anvil.",
        f"{NS}.book.section.anvil.line1": "Place the glowing blank on the anvil – the rhythm pendulum bar appears in your action bar.",
        f"{NS}.book.section.anvil.line2": "Strike 12× with the Smithing Hammer (left-click) right when the marker hits the green center [★]!",
        f"{NS}.book.section.anvil.line3": "Pick up the finished hot head using Smithing Tongs (right-click).",
        f"{NS}.book.section.basin": "Quenching Basin & Grindstone",
        f"{NS}.book.section.basin.header": "Quench hot tool heads in the basin and sharpen them on the grindstone.",
        f"{NS}.book.section.basin.line1": "Fill the basin with a Water Bucket or Cooking Oil (from KaleidoscopeCookery / KaleidoscopeGrilling).",
        f"{NS}.book.section.basin.line2": "Tip: Quenching in Cooking Oil grants a +8% precision bonus towards Masterwork quality!",
        f"{NS}.book.section.basin.line3": "Left-click 6× on the grindstone wheel to sharpen and finish the tempered tool head.",
        f"{NS}.book.section.assembly": "Tinker Assembly Table",
        f"{NS}.book.section.assembly.header": "Assemble handles, tool heads, and bindings without any menus.",
        f"{NS}.book.section.assembly.line1": "1. Right-click to place a Leather-Wrapped Handle or Reinforced Handle (+10% durability).",
        f"{NS}.book.section.assembly.line2": "2. Place the forged tool head on top (1x2 Heavy Pickaxes also require a Binding Ring of the same material).",
        f"{NS}.book.section.assembly.line3": "3. Strike 6× with the Smithing Hammer (left-click) to assemble the finished weapon or tool!",
        f"{NS}.book.section.crafting": "Crafting Table (Stations & Supplies)",
        f"{NS}.book.section.crafting.header": "Craft smithing stations, blacksmith tools, handles, and unfired clay molds at a Crafting Table.",
        f"{NS}.book.section.furnace": "Furnace (Firing Molds)",
        f"{NS}.book.section.furnace.header": "Smelt unfired clay molds in a Furnace to create heat-resistant fired molds.",
        f"jei.{NS}.basin.hint1": "1. Fill basin with Water Bucket or Cooking Oil (Oil grants +8% quality bonus)",
        f"jei.{NS}.basin.hint2": "2. Right-click with the hot tool head to quench it",
        f"jei.{NS}.basin.hint3": "3. Left-click 6× on the grindstone to sharpen",
    }

    # Smithing Guidebook with custom 16x16 pixel-art texture (dark iron-bound tome with golden anvil/hammer emblem)
    register_2d_item("smithing_book", draw_smithing_book(), handheld=False)
    items_yml[f"{NS}:smithing_book"] = {
        "model": f"{NS}:smithing_book",
        "name": f"item.{NS}.smithing_book",
        "tooltip": f"tooltip.{NS}.smithing_book",
        "max-stack": 1,
    }

    # Add the 4 stations to items_yml
    for station_id in ("forge_crucible", "smithing_anvil", "quenching_basin", "smithing_table_station"):
        items_yml[f"{NS}:{station_id}"] = {
            "model": f"{NS}:{station_id}",
            "name": f"block.{NS}.{station_id}",
            "tooltip": f"tooltip.{NS}.{station_id}",
        }

    # Blacksmith tools: 3D Smithing Hammer & 3D Smithing Tongs
    register_3d_smithing_hammer(draw_smithing_hammer())
    items_yml[f"{NS}:smithing_hammer"] = {
        "model": f"{NS}:smithing_hammer",
        "name": f"item.{NS}.smithing_hammer",
        "tooltip": f"tooltip.{NS}.smithing_hammer",
        "base": "JIGSAW",
        "prototype": "IRON_PICKAXE",
        "max-damage": 512,
        "repair": "IRON_INGOT",
    }
    de_lang[f"item.{NS}.smithing_hammer"] = "Schmiedehammer"
    de_lang[f"tooltip.{NS}.smithing_hammer"] = "Werkzeug zum Formen glühender Rohlinge und zur Endmontage."
    en_lang[f"item.{NS}.smithing_hammer"] = "Smithing Hammer"
    en_lang[f"tooltip.{NS}.smithing_hammer"] = "Used to forge glowing blanks on the anvil and assemble tools."

    register_3d_smithing_tongs(draw_smithing_tongs())
    items_yml[f"{NS}:smithing_tongs"] = {
        "model": f"{NS}:smithing_tongs",
        "name": f"item.{NS}.smithing_tongs",
        "tooltip": f"tooltip.{NS}.smithing_tongs",
        "base": "JIGSAW",
        "prototype": "SHEARS",
        "max-damage": 384,
        "repair": "IRON_INGOT",
    }
    de_lang[f"item.{NS}.smithing_tongs"] = "Schmiedezange"
    de_lang[f"tooltip.{NS}.smithing_tongs"] = "Unverzichtbar, um glühende Werkstücke sicher zu greifen."
    en_lang[f"item.{NS}.smithing_tongs"] = "Smithing Tongs"
    en_lang[f"tooltip.{NS}.smithing_tongs"] = "Essential for safely handling red-hot workpieces."

    # Handles & Bindings
    register_3d_sprite("wooden_handle", sprites.handle_3d(False))
    items_yml[f"{NS}:wooden_handle"] = {
        "model": f"{NS}:wooden_handle",
        "name": f"item.{NS}.wooden_handle",
        "tooltip": f"tooltip.{NS}.wooden_handle",
    }
    de_lang[f"item.{NS}.wooden_handle"] = "Lederumwickelter Holzgriff"
    de_lang[f"tooltip.{NS}.wooden_handle"] = "Handlicher Griff für normale geschmiedete Werkzeuge und Waffen."
    en_lang[f"item.{NS}.wooden_handle"] = "Leather-Wrapped Handle"
    en_lang[f"tooltip.{NS}.wooden_handle"] = "Ergonomic handle for standard forged tools and weapons."

    register_3d_sprite("reinforced_handle", sprites.handle_3d(True))
    items_yml[f"{NS}:reinforced_handle"] = {
        "model": f"{NS}:reinforced_handle",
        "name": f"item.{NS}.reinforced_handle",
        "tooltip": f"tooltip.{NS}.reinforced_handle",
    }
    de_lang[f"item.{NS}.reinforced_handle"] = "Verstärkter Schmiedegriff"
    de_lang[f"tooltip.{NS}.reinforced_handle"] = "Mit Eisen und Goldbändern verstärkter Griff (+10% Haltbarkeit, Pflicht für 1x2-Werkzeuge)."
    en_lang[f"item.{NS}.reinforced_handle"] = "Reinforced Handle"
    en_lang[f"tooltip.{NS}.reinforced_handle"] = "Iron-reinforced handle (+10% durability, required for 1x2 heavy tools)."

    register_3d_sprite("armor_padding", sprites.padding_3d())
    items_yml[f"{NS}:armor_padding"] = {"model": f"{NS}:armor_padding", "name": f"item.{NS}.armor_padding",
                                        "tooltip": f"tooltip.{NS}.armor_padding", "max-stack": 16}
    de_lang[f"item.{NS}.armor_padding"] = "Rüstungspolster"
    de_lang[f"tooltip.{NS}.armor_padding"] = "Gepolstertes Leder: kommt bei Rüstungen statt eines Griffs auf den Montagetisch."
    en_lang[f"item.{NS}.armor_padding"] = "Armor Padding"
    en_lang[f"tooltip.{NS}.armor_padding"] = "Quilted leather: goes onto the assembly table instead of a handle for armor."
    de_lang[f"tooltip.{NS}.armor_plate"] = "Auf dem Montagetisch mit einem Rüstungspolster verbinden."
    en_lang[f"tooltip.{NS}.armor_plate"] = "Combine with an armor padding on the assembly table."
    for quality, de_text, en_text in (("crude", "Grob (75% Haltbarkeit, -0.5 Rüstung)", "Crude (75% Durability, -0.5 Armor)"),
                                      ("sturdy", "Solide (100% Standard)", "Sturdy (100% Standard)"),
                                      ("tempered", "Gehärtet (120% Haltbarkeit)", "Tempered (120% Durability)"),
                                      ("masterwork", "Meisterwerk (140% Haltbarkeit, +0.5 Rüstung)", "Masterwork (140% Durability, +0.5 Armor)")):
        de_lang[f"tooltip.{NS}.quality_armor.{quality}"] = f"Schmiede-Qualität: {de_text}"
        en_lang[f"tooltip.{NS}.quality_armor.{quality}"] = f"Forged Quality: {en_text}"
    de_lang[f"{NS}.table.need_padding"] = "Platten brauchen Rüstungspolster statt Griff!"
    en_lang[f"{NS}.table.need_padding"] = "Armor plates need padding, not a handle!"
    de_lang[f"{NS}.table.padding_only_armor"] = "Aufs Polster passen nur Rüstungsplatten!"
    en_lang[f"{NS}.table.padding_only_armor"] = "Only armor plates fit onto the armor padding!"
    de_lang[f"skill.{NS}.armor"] = "Rüstung"
    en_lang[f"skill.{NS}.armor"] = "Armor"

    register_3d_sprite("ring_core", sprites.ring_core_3d(), glow=6)
    items_yml[f"{NS}:ring_core"] = {
        "model": f"{NS}:ring_core",
        "name": f"item.{NS}.ring_core",
        "tooltip": f"tooltip.{NS}.ring_core",
        "max-stack": 16,
    }
    de_lang[f"item.{NS}.ring_core"] = "Ringkern"
    de_lang[f"tooltip.{NS}.ring_core"] = "In die Ringform auf dem Gießtisch legen, dann Diamant oder Netherit darum gießen."
    en_lang[f"item.{NS}.ring_core"] = "Ring Core"
    en_lang[f"tooltip.{NS}.ring_core"] = "Lay it into the ring mold on the casting table, then cast diamond or netherite around it."

    # Molds (Clay & Fired) for each tool kind
    for kind, de_part, en_part in [(k, dp, ep) for k, _, _, dp, ep, _ in TOOL_KINDS] + PART_KINDS + [(k, dp, ep) for k, dp, ep, _ in COOKWARE_KINDS]:
        clay_id = f"clay_{kind}_mold"
        fired_id = f"{kind}_mold"
        register_3d_sprite(clay_id, sprites.mold_3d(kind, fired=False))
        register_3d_sprite(fired_id, sprites.mold_3d(kind, fired=True))

        items_yml[f"{NS}:{clay_id}"] = {
            "model": f"{NS}:{clay_id}",
            "name": f"item.{NS}.{clay_id}",
            "tooltip": f"tooltip.{NS}.clay_mold",
            "max-stack": 16,
        }
        items_yml[f"{NS}:{fired_id}"] = {
            "model": f"{NS}:{fired_id}",
            "name": f"item.{NS}.{fired_id}",
            "tooltip": f"tooltip.{NS}.fired_mold",
            "max-stack": 16,
        }
        de_lang[f"item.{NS}.{clay_id}"] = f"Rohe Gussform ({de_part})"
        en_lang[f"item.{NS}.{clay_id}"] = f"Unfired Mold ({en_part})"
        de_lang[f"item.{NS}.{fired_id}"] = f"Gussform ({de_part})"
        en_lang[f"item.{NS}.{fired_id}"] = f"Fired Mold ({en_part})"

    de_lang[f"tooltip.{NS}.clay_mold"] = "Im Ofen brennen, um eine hitzebeständige Gussform zu erhalten."
    en_lang[f"tooltip.{NS}.clay_mold"] = "Smelt in a furnace to create a heat-resistant mold."
    de_lang[f"tooltip.{NS}.fired_mold"] = "Auf den Gießtisch vor der Schmelze legen und mit Metall füllen. Zerbricht beim Guss (nur einmal nutzbar)."
    en_lang[f"tooltip.{NS}.fired_mold"] = "Place onto the Casting Table in front of the Smeltery and fill with metal. Breaks when cast (single use)."

    # For each tier & tool kind:
    # 1. Heated Blank (glühender Rohling)
    # 2. Hot Forged Head (3D heiß geschmiedeter Kopf, vor dem Löschen)
    # 3. Tempered Tool Head (3D abgekühlter & geschärfter Kopf)
    # 4. Finished Forged Tool / Weapon (3D Waffe / Werkzeug)
    for tier, de_tier, en_tier, proto_prefix, repair_mat in TIERS:
        for kind, de_tool, en_tool, de_part, en_part, cost in TOOL_KINDS:
            if kind == "heavy_pickaxe" and tier not in HEAVY_TIERS:
                continue
            blank_id = f"heated_{tier}_{kind}_blank"
            hot_head_id = f"hot_{tier}_{kind}_head"
            head_id = f"{tier}_{kind}_head"
            tool_id = f"forged_{tier}_{kind}" if kind != "heavy_pickaxe" else f"heavy_{tier}_pickaxe"

            # Blank texture: glowing mold/ingot shape
            register_3d_sprite(blank_id, sprites.blank_3d(kind), glow=12)
            items_yml[f"{NS}:{blank_id}"] = {
                "model": f"{NS}:{blank_id}",
                "name": f"item.{NS}.{blank_id}",
                "tooltip": f"tooltip.{NS}.heated_blank",
                "max-stack": 1,
            }
            de_lang[f"item.{NS}.{blank_id}"] = f"Glühender {de_tier}-{de_part}-Rohling"
            en_lang[f"item.{NS}.{blank_id}"] = f"Glowing {en_tier} {en_part} Blank"

            if kind in ARMOR_KINDS:
                register_armor_kind(items_yml, de_lang, en_lang, tier, de_tier, en_tier, proto_prefix, repair_mat, kind, de_tool, en_tool, de_part, en_part)
                continue
            # Hot forged head: 3D glowing orange shape of the head (tier-specific geometry!)
            register_3d_head(hot_head_id, kind, "glowing", draw_head_item(kind, PALETTES["glowing"]), geom_tier=tier)
            items_yml[f"{NS}:{hot_head_id}"] = {
                "model": f"{NS}:{hot_head_id}",
                "name": f"item.{NS}.{hot_head_id}",
                "tooltip": f"tooltip.{NS}.hot_head",
                "max-stack": 1,
            }
            de_lang[f"item.{NS}.{hot_head_id}"] = f"Heißer {de_tier}-{de_part}"
            en_lang[f"item.{NS}.{hot_head_id}"] = f"Hot {en_tier} {en_part}"

            # Finished cooled & sharpened head: 3D metallic head (tier-specific geometry!)
            register_3d_head(head_id, kind, tier, draw_head_item(kind, PALETTES[tier]), geom_tier=tier)
            items_yml[f"{NS}:{head_id}"] = {
                "model": f"{NS}:{head_id}",
                "name": f"item.{NS}.{head_id}",
                "tooltip": f"tooltip.{NS}.tool_head",
                "max-stack": 16,
            }
            de_lang[f"item.{NS}.{head_id}"] = f"Geschmiedeter {de_tier}-{de_part}"
            en_lang[f"item.{NS}.{head_id}"] = f"Forged {en_tier} {en_part}"

            # Finished tool / weapon: 3D weapon/tool model
            proto_kind = "PICKAXE" if kind == "heavy_pickaxe" else kind.upper()
            prototype = f"{proto_prefix}_{proto_kind}"
            register_3d_tool(tool_id, kind, tier, draw_tool_item(kind, tier))
            # Model variants for the designs (chosen via the item_model component when a design is hammered on)
            for design in DESIGNS:
                register_3d_tool(f"{tool_id}_design_{design}", kind, tier, draw_tool_item(kind, tier), design=design)
            tool_entry = {
                "model": f"{NS}:{tool_id}",
                "name": f"item.{NS}.{tool_id}",
                "base": prototype,
                "prototype": prototype,
                "repair": repair_mat,
            }
            if kind == "heavy_pickaxe":
                tool_entry["tooltip"] = f"tooltip.{NS}.heavy_pickaxe.ability"
            items_yml[f"{NS}:{tool_id}"] = tool_entry

            if kind == "heavy_pickaxe":
                de_lang[f"item.{NS}.{tool_id}"] = f"Geschmiedeter {de_tier}-Stollenhammer (1x2)"
                en_lang[f"item.{NS}.{tool_id}"] = f"Forged {en_tier} Heavy Pickaxe (1x2)"
            else:
                prefix_de = "Geschmiedetes" if kind == "schwert" or de_tool == "Schwert" else "Geschmiedete"
                de_lang[f"item.{NS}.{tool_id}"] = f"{prefix_de} {de_tier}-{de_tool}"
                en_lang[f"item.{NS}.{tool_id}"] = f"Forged {en_tier} {en_tool}"

    # Cookware: glowing blank, hot part and (knife only) the finished blade
    for kind, de_part, en_part, tiers in COOKWARE_KINDS:
        for tier, de_tier, en_tier, *_ in TIERS:
            if tier not in tiers:
                continue
            blank_id = f"heated_{tier}_{kind}_blank"
            hot_id = f"hot_{tier}_{kind}_head"
            register_3d_sprite(blank_id, sprites.blank_3d(kind), glow=12)
            register_3d_sprite(hot_id, sprites.cookware_3d(kind, sprites.HEAT), glow=12)
            parts = [(blank_id, "heated_blank", 1, f"Glühender {de_tier}-{de_part}-Rohling", f"Glowing {en_tier} {en_part} Blank"),
                     (hot_id, "hot_head", 1, f"Heiße {de_tier}-{de_part}", f"Hot {en_tier} {en_part}")]
            if kind == "knife":
                head_id = f"{tier}_knife_head"
                register_3d_sprite(head_id, sprites.cookware_3d(kind, PALETTES[tier]))
                parts.append((head_id, "knife_head", 16, f"Geschmiedete {de_tier}-{de_part}", f"Forged {en_tier} {en_part}"))
            for item_id, tooltip, stack, de_name, en_name in parts:
                items_yml[f"{NS}:{item_id}"] = {"model": f"{NS}:{item_id}", "name": f"item.{NS}.{item_id}",
                                                "tooltip": f"tooltip.{NS}.{tooltip}", "max-stack": stack}
                de_lang[f"item.{NS}.{item_id}"] = de_name
                en_lang[f"item.{NS}.{item_id}"] = en_name
    de_lang[f"tooltip.{NS}.knife_head"] = "Am Montagetisch mit einem Griff zum Küchenmesser zusammensetzen."
    en_lang[f"tooltip.{NS}.knife_head"] = "Assemble with a handle into a kitchen knife on the assembly table."

    # Binding rings (cast around a ring core) for the heavy pickaxe tiers
    for tier, de_tier, en_tier, *_ in TIERS:
        if tier not in HEAVY_TIERS:
            continue
        palette = "netherite" if tier == "netherite" else "diamond"
        for item_id, sprite, glow, de_name, en_name, tooltip, stack in (
                (f"heated_{tier}_ring_blank", sprites.blank_3d("ring"), 12, f"Glühender {de_tier}-Ring-Rohling",
                 f"Glowing {en_tier} Ring Blank", "heated_blank", 1),
                (f"hot_{tier}_ring", sprites.ring_3d("hot"), 12, f"Heißer {de_tier}-Verstärkungsring",
                 f"Hot {en_tier} Binding Ring", "hot_head", 1),
                (f"{tier}_binding_ring", sprites.ring_3d(palette), 0, f"{de_tier}-Verstärkungsring",
                 f"{en_tier} Binding Ring", "binding_ring", 16)):
            register_3d_sprite(item_id, sprite, glow=glow)
            items_yml[f"{NS}:{item_id}"] = {"model": f"{NS}:{item_id}", "name": f"item.{NS}.{item_id}",
                                            "tooltip": f"tooltip.{NS}.{tooltip}", "max-stack": stack}
            de_lang[f"item.{NS}.{item_id}"] = de_name
            en_lang[f"item.{NS}.{item_id}"] = en_name
    de_lang[f"tooltip.{NS}.binding_ring"] = "Pflicht für den 1x2-Stollenhammer aus demselben Material (Montagetisch)."
    en_lang[f"tooltip.{NS}.binding_ring"] = "Required for the 1x2 heavy pickaxe of the same material (assembly table)."

    # Nether star core: 9 nether stars cast in the core mold, forged and quenched
    for item_id, sprite, glow, de_name, en_name, tooltip, stack in (
            ("heated_star_core", sprites.blank_3d("star_core"), 12, "Glühender Netherstern-Rohling",
             "Glowing Nether Star Blank", "heated_blank", 1),
            ("glowing_star_core", sprites.star_core_3d(True), 14, "Glühender Netherstern-Kern",
             "Glowing Nether Star Core", "hot_head", 1),
            ("nether_star_core", sprites.star_core_3d(False), 10, "Netherstern-Kern", "Nether Star Core", "star_core", 16)):
        register_3d_sprite(item_id, sprite, glow=glow)
        items_yml[f"{NS}:{item_id}"] = {"model": f"{NS}:{item_id}", "name": f"item.{NS}.{item_id}",
                                        "tooltip": f"tooltip.{NS}.{tooltip}", "max-stack": stack}
        de_lang[f"item.{NS}.{item_id}"] = de_name
        en_lang[f"item.{NS}.{item_id}"] = en_name
    de_lang[f"tooltip.{NS}.star_core"] = "Herzstück des Ringkerns (Werkbank)."
    en_lang[f"tooltip.{NS}.star_core"] = "Heart of the ring core (crafting table)."
    de_lang[f"{NS}.crucible.wrong_metal"] = "Material passt nicht in diese Gussform!"
    en_lang[f"{NS}.crucible.wrong_metal"] = "This material does not fit this mold!"
    de_lang[f"{NS}.crucible.need_ring_core"] = "Lege zuerst einen Ringkern in die Ringform!"
    en_lang[f"{NS}.crucible.need_ring_core"] = "Lay a ring core into the ring mold first!"
    # Skills (level system in KaleidoscopeCore)
    for kind, de_name, en_name in (("sword", "Schwert", "Sword"), ("pickaxe", "Spitzhacke", "Pickaxe"), ("axe", "Axt", "Axe"),
                                   ("shovel", "Schaufel", "Shovel"), ("hoe", "Hacke", "Hoe"), ("spear", "Speer", "Spear")):
        de_lang[f"skill.{NS}.{kind}"] = de_name
        en_lang[f"skill.{NS}.{kind}"] = en_name
    for design, de_name, en_name in DESIGN_NAMES:
        register_3d_sprite(f"design_{design}", sprites.design_3d(design), glow=4 if design in ("sculk", "vote") else 0)
        items_yml[f"{NS}:design_{design}"] = {"model": f"{NS}:design_{design}", "name": f"item.{NS}.design_{design}",
                                              "tooltip": f"tooltip.{NS}.design_item", "max-stack": 16}
        de_lang[f"item.{NS}.design_{design}"] = f"Schmiede-Design: {de_name}"
        en_lang[f"item.{NS}.design_{design}"] = f"Smithing Design: {en_name}"
        de_lang[f"tooltip.{NS}.design.{design}"] = f"Design: {de_name}"
        en_lang[f"tooltip.{NS}.design.{design}"] = f"Design: {en_name}"
    de_lang[f"tooltip.{NS}.design_item"] = "Selten! Am Montagetisch auf ein fertig geschmiedetes Werkzeug, eine Waffe oder ein Rüstungsteil aufschmieden."
    en_lang[f"tooltip.{NS}.design_item"] = "Rare! Hammer onto a finished forged tool, weapon or armor piece on the assembly table."
    de_lang[f"{NS}.table.put_design"] = "Lege jetzt ein Schmiede-Design auf!"
    en_lang[f"{NS}.table.put_design"] = "Now lay a smithing design onto the tool!"
    de_lang[f"{NS}.table.has_design"] = "Dieses Werkzeug hat schon ein Design."
    en_lang[f"{NS}.table.has_design"] = "This tool already has a design."
    de_lang[f"{NS}.table.design_done"] = "Design aufgeschmiedet!"
    en_lang[f"{NS}.table.design_done"] = "Design hammered on!"
    for kind, de_name, en_name in (("sword", "Schwert", "Sword"), ("spear", "Speer", "Spear"), ("pickaxe", "Spitzhacke", "Pickaxe"),
                                   ("axe", "Axt", "Axe"), ("shovel", "Schaufel", "Shovel"), ("hoe", "Hacke", "Hoe")):
        register_3d_sprite(f"folio_{kind}", sprites.folio_3d())
        items_yml[f"{NS}:folio_{kind}"] = {"model": f"{NS}:folio_{kind}", "name": f"item.{NS}.folio_{kind}",
                                           "tooltip": f"tooltip.{NS}.folio", "max-stack": 1}
        de_lang[f"item.{NS}.folio_{kind}"] = f"Schmiede-Foliant: {de_name}"
        en_lang[f"item.{NS}.folio_{kind}"] = f"Smithing Folio: {en_name}"
    de_lang[f"tooltip.{NS}.folio"] = "Rechtsklick zum Lesen: XP für diese Werkzeugart."
    en_lang[f"tooltip.{NS}.folio"] = "Right-click to read: XP for this tool kind."
    de_lang[f"{NS}.folio.disabled"] = "Das Level-System ist auf diesem Server aus."
    en_lang[f"{NS}.folio.disabled"] = "The level system is off on this server."
    de_lang[f"{NS}.crucible.end_steel"] = "Endstahl dazu (%s/%s): mind. gehärtet"
    en_lang[f"{NS}.crucible.end_steel"] = "End steel (%s/%s): at least tempered"
    register_3d_sprite("end_steel_shard", sprites.end_steel_shard_3d(), glow=8)
    items_yml[f"{NS}:end_steel_shard"] = {"model": f"{NS}:end_steel_shard", "name": f"item.{NS}.end_steel_shard",
                                          "tooltip": f"tooltip.{NS}.end_steel_shard"}
    de_lang[f"item.{NS}.end_steel_shard"] = "Endstahl-Splitter"
    en_lang[f"item.{NS}.end_steel_shard"] = "End Steel Shard"
    de_lang[f"tooltip.{NS}.end_steel_shard"] = "Vor dem Guss in die Schmelze geben (1, beim Stollenhammer 2): Das Werkzeug wird mindestens gehärtet."
    en_lang[f"tooltip.{NS}.end_steel_shard"] = "Add to the smeltery before casting (1, heavy pickaxe 2): the tool will be at least tempered."
    de_lang[f"{NS}.skill.too_low_forge"] = "Dafür brauchst du %s Stufe %s."
    en_lang[f"{NS}.skill.too_low_forge"] = "You need %s level %s for that."
    de_lang[f"{NS}.skill.too_low_use"] = "Ab %s Stufe %s: doppelter Verschleiß!"
    en_lang[f"{NS}.skill.too_low_use"] = "Needs %s level %s - double wear!"
    de_lang[f"{NS}.basin.done_part"] = "Abgeschreckt und fertig!"
    en_lang[f"{NS}.basin.done_part"] = "Quenched and finished!"

    de_lang[f"tooltip.{NS}.heated_blank"] = "Auf dem Schmiedeamboss mit dem Schmiedehammer ausschmieden."
    en_lang[f"tooltip.{NS}.heated_blank"] = "Forge on the Smithing Anvil using a Smithing Hammer."
    de_lang[f"tooltip.{NS}.hot_head"] = "Im Löschbecken abschrecken und am Wetzstein schärfen!"
    en_lang[f"tooltip.{NS}.hot_head"] = "Quench in the Quenching Basin and sharpen on the grindstone!"
    de_lang[f"tooltip.{NS}.tool_head"] = "Auf dem Schmiede-Montagetisch mit einem Griff verbinden."
    en_lang[f"tooltip.{NS}.tool_head"] = "Combine with a handle on the Tinker Assembly Table."

    # Write language files into resourcepack/custom/assets/minecraft/lang/ so Lang.add() loads them
    write_json(MC_ASSETS / "lang" / "de_de.json", de_lang)
    write_json(MC_ASSETS / "lang" / "en_us.json", en_lang)

    # Write pack.mcmeta & pack.png
    write_json(SMITHING_ROOT / "resourcepack" / "custom" / "pack.mcmeta", {
        "pack": {
            "description": [
                {"text": "Kaleidoscope Smithing", "color": "gold"},
                {"text": "\nSchmiede-Addon für KaleidoscopeCookery", "color": "gray"},
            ],
            "min_format": 97,
            "max_format": 97,
        }
    })
    draw_smithing_hammer().resize((64, 64), Image.NEAREST).save(SMITHING_ROOT / "resourcepack" / "custom" / "pack.png")

    # Write items.yml
    items_lines = ["# Generiert von tools/gen_smithing.py", "items:"]
    for item_key, props in items_yml.items():
        items_lines.append(f'  "{item_key}":')
        for k, v in props.items():
            if isinstance(v, dict):
                items_lines.append(f'    {k}:')
                items_lines += [f'      {sk}: "{sv}"' for sk, sv in v.items()]
            elif isinstance(v, str):
                items_lines.append(f'    {k}: "{v}"')
            elif isinstance(v, bool):
                items_lines.append(f'    {k}: {"true" if v else "false"}')
            else:
                items_lines.append(f'    {k}: {v}')
    RESOURCES.mkdir(parents=True, exist_ok=True)
    (RESOURCES / "items.yml").write_text("\n".join(items_lines) + "\n", encoding="utf-8")

    # Write recipes.yml (crafting stations, molds, smelting clay molds, handles, bindings, hammer, tongs)
    recipes_yml = """# Rezepte fuer KaleidoscopeSmithing (generiert von tools/gen_smithing.py)
tags:
  "kaleidoscope_smithing:planks":
    - "minecraft:oak_planks"
    - "minecraft:spruce_planks"
    - "minecraft:birch_planks"
    - "minecraft:jungle_planks"
    - "minecraft:acacia_planks"
    - "minecraft:dark_oak_planks"
    - "minecraft:mangrove_planks"
    - "minecraft:cherry_planks"
    - "minecraft:bamboo_planks"
    - "minecraft:crimson_planks"
    - "minecraft:warped_planks"
    - "minecraft:pale_oak_planks"
  "kaleidoscope_smithing:logs":
    - "minecraft:oak_log"
    - "minecraft:spruce_log"
    - "minecraft:birch_log"
    - "minecraft:jungle_log"
    - "minecraft:acacia_log"
    - "minecraft:dark_oak_log"
    - "minecraft:mangrove_log"
    - "minecraft:cherry_log"
    - "minecraft:pale_oak_log"
    - "minecraft:crimson_stem"
    - "minecraft:warped_stem"
  "kaleidoscope_smithing:stone_bricks":
    - "minecraft:stone_bricks"
    - "minecraft:deepslate_bricks"
    - "minecraft:polished_blackstone_bricks"
    - "minecraft:bricks"
vanilla:
  "kaleidoscope_smithing:smithing_book":
    type: crafting_shapeless
    ingredients:
      - "minecraft:book"
      - "minecraft:iron_ingot"
    result: "kaleidoscope_smithing:smithing_book"
  "kaleidoscope_smithing:forge_crucible":
    type: crafting_shaped
    pattern:
      - "B B"
      - "BCB"
      - "BBB"
    key:
      B: "#kaleidoscope_smithing:stone_bricks"
      C: "minecraft:cauldron"
    result: "kaleidoscope_smithing:forge_crucible"
  "kaleidoscope_smithing:smithing_anvil":
    type: crafting_shaped
    pattern:
      - "III"
      - " I "
      - "LLL"
    key:
      I: "minecraft:iron_ingot"
      L: "#kaleidoscope_smithing:logs"
    result: "kaleidoscope_smithing:smithing_anvil"
  "kaleidoscope_smithing:quenching_basin":
    type: crafting_shaped
    pattern:
      - "P S"
      - "PBP"
      - "PPP"
    key:
      P: "#kaleidoscope_smithing:planks"
      B: "minecraft:bucket"
      S: "minecraft:smooth_stone"
    result: "kaleidoscope_smithing:quenching_basin"
  "kaleidoscope_smithing:smithing_table_station":
    type: crafting_shaped
    pattern:
      - "II "
      - "PP "
      - "LL "
    key:
      I: "minecraft:copper_ingot"
      P: "#kaleidoscope_smithing:planks"
      L: "#kaleidoscope_smithing:logs"
    result: "kaleidoscope_smithing:smithing_table_station"
  "kaleidoscope_smithing:smithing_hammer":
    type: crafting_shaped
    pattern:
      - "II "
      - "IS "
      - " S "
    key:
      I: "minecraft:iron_ingot"
      S: "minecraft:stick"
    result: "kaleidoscope_smithing:smithing_hammer"
  "kaleidoscope_smithing:smithing_tongs":
    type: crafting_shaped
    pattern:
      - " I "
      - "I I"
      - "S S"
    key:
      I: "minecraft:iron_ingot"
      S: "minecraft:stick"
    result: "kaleidoscope_smithing:smithing_tongs"
  "kaleidoscope_smithing:wooden_handle":
    type: crafting_shaped
    pattern:
      - "  S"
      - " L "
      - "S  "
    key:
      S: "minecraft:stick"
      L: "minecraft:leather"
    result: "kaleidoscope_smithing:wooden_handle"
  "kaleidoscope_smithing:reinforced_handle":
    type: crafting_shaped
    pattern:
      - " GI"
      - "IHI"
      - "IG "
    key:
      H: "kaleidoscope_smithing:wooden_handle"
      I: "minecraft:iron_ingot"
      G: "minecraft:gold_nugget"
    result: "kaleidoscope_smithing:reinforced_handle"
  # Ring core: the forged nether star core in netherite, obsidian and crying obsidian (one per binding ring)
  "kaleidoscope_smithing:ring_core":
    type: crafting_shaped
    pattern:
      - "ONC"
      - "NKN"
      - "CNO"
    key:
      O: "minecraft:obsidian"
      C: "minecraft:crying_obsidian"
      N: "minecraft:netherite_ingot"
      K: "kaleidoscope_smithing:nether_star_core"
    result: "kaleidoscope_smithing:ring_core"
  "kaleidoscope_smithing:clay_ring_mold":
    type: crafting_shaped
    pattern:
      - "CCC"
      - "C C"
      - "CCC"
    key:
      C: "minecraft:clay_ball"
    result: "kaleidoscope_smithing:clay_ring_mold"
  "kaleidoscope_smithing:clay_star_core_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CCC"
      - " C "
    key:
      C: "minecraft:clay_ball"
    result: "kaleidoscope_smithing:clay_star_core_mold"
  # Clay Molds (shaped around wooden/stone tool or clay + sticks)
  "kaleidoscope_smithing:clay_sword_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CSC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      S: "minecraft:wooden_sword"
    result: "kaleidoscope_smithing:clay_sword_mold"
  "kaleidoscope_smithing:clay_pickaxe_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CPC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      P: "minecraft:wooden_pickaxe"
    result: "kaleidoscope_smithing:clay_pickaxe_mold"
  "kaleidoscope_smithing:clay_axe_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CAC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      A: "minecraft:wooden_axe"
    result: "kaleidoscope_smithing:clay_axe_mold"
  "kaleidoscope_smithing:clay_shovel_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CSC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      S: "minecraft:wooden_shovel"
    result: "kaleidoscope_smithing:clay_shovel_mold"
  "kaleidoscope_smithing:clay_hoe_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CHC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      H: "minecraft:wooden_hoe"
    result: "kaleidoscope_smithing:clay_hoe_mold"
  "kaleidoscope_smithing:clay_spear_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "CSC"
      - " C "
    key:
      C: "minecraft:clay_ball"
      S: "minecraft:stone_spear"
    result: "kaleidoscope_smithing:clay_spear_mold"
  "kaleidoscope_smithing:clay_heavy_pickaxe_mold":
    type: crafting_shaped
    pattern:
      - "CCC"
      - "CPC"
      - "CCC"
    key:
      C: "minecraft:clay_ball"
      P: "minecraft:stone_pickaxe"
    result: "kaleidoscope_smithing:clay_heavy_pickaxe_mold"
  # Armor padding: quilted leather for the assembly table
  "kaleidoscope_smithing:armor_padding":
    type: crafting_shaped
    pattern:
      - "LSL"
      - "LWL"
    key:
      L: "minecraft:leather"
      S: "minecraft:string"
      W: "#minecraft:wool"
    result:
      item: "kaleidoscope_smithing:armor_padding"
      count: 2
"""
    # Cookware molds: clay around the Cookery stone knife, a bowl (wok) or a flower pot (stockpot)
    for kind, letter, template in (("knife", "K", "kaleidoscope_cookery:stone_kitchen_knife"), ("wok", "B", "minecraft:bowl"),
                                   ("stockpot", "P", "minecraft:flower_pot")):
        recipes_yml += f"""  "kaleidoscope_smithing:clay_{kind}_mold":
    type: crafting_shaped
    pattern:
      - " C "
      - "C{letter}C"
      - " C "
    key:
      C: "minecraft:clay_ball"
      {letter}: "{template}"
    result: "kaleidoscope_smithing:clay_{kind}_mold"
"""
    # Armor molds: clay around the leather piece of the same slot
    for kind, letter in (("helmet", "H"), ("chestplate", "C"), ("leggings", "L"), ("boots", "B")):
        recipes_yml += f"""  "kaleidoscope_smithing:clay_{kind}_mold":
    type: crafting_shaped
    pattern:
      - "KKK"
      - "K{letter}K"
      - "KKK"
    key:
      K: "minecraft:clay_ball"
      {letter}: "minecraft:leather_{kind}"
    result: "kaleidoscope_smithing:clay_{kind}_mold"
"""
    for kind in [k for k, *_ in TOOL_KINDS] + [k for k, *_ in PART_KINDS] + [k for k, *_ in COOKWARE_KINDS]:
        recipes_yml += f"""  "kaleidoscope_smithing:smelt_{kind}_mold":
    type: smelting
    ingredient: "kaleidoscope_smithing:clay_{kind}_mold"
    time: 160
    experience: 0.35
    result: "kaleidoscope_smithing:{kind}_mold"
"""
    recipes_yml += "data-version: 7\n"
    (RESOURCES / "recipes.yml").write_text(recipes_yml, encoding="utf-8")

    # Write generated/creative_tabs.yml
    tab_items = [
        f"{NS}:smithing_book",
        f"{NS}:forge_crucible",
        f"{NS}:smithing_anvil",
        f"{NS}:quenching_basin",
        f"{NS}:smithing_table_station",
        f"{NS}:smithing_hammer",
        f"{NS}:smithing_tongs",
        f"{NS}:wooden_handle",
        f"{NS}:reinforced_handle",
        f"{NS}:armor_padding",
        f"{NS}:nether_star_core",
        f"{NS}:ring_core",
    ] + [f"{NS}:{tier}_binding_ring" for tier in HEAVY_TIERS]
    for kind in [k for k, *_ in TOOL_KINDS] + [k for k, *_ in PART_KINDS]:
        tab_items.append(f"{NS}:{kind}_mold")
    for tier, *_ in TIERS:
        for kind, *_ in TOOL_KINDS:
            if kind == "heavy_pickaxe" and tier not in HEAVY_TIERS:
                continue
            tool_id = f"forged_{tier}_{kind}" if kind != "heavy_pickaxe" else f"heavy_{tier}_pickaxe"
            tab_items.append(f"{NS}:{tool_id}")
    for tier, *_ in TIERS:
        for kind, *_ in TOOL_KINDS:
            if kind == "heavy_pickaxe" and tier not in HEAVY_TIERS:
                continue
            tab_items.append(f"{NS}:{tier}_{kind}_plate" if kind in ARMOR_KINDS else f"{NS}:{tier}_{kind}_head")
    for kind, _, _, tiers in COOKWARE_KINDS:
        tab_items.append(f"{NS}:{kind}_mold")
    tab_items += [f"{NS}:{tier}_knife_head" for tier in COOKWARE_KINDS[0][3]]

    tabs_lines = [
        "# Generiert von tools/gen_smithing.py",
        "tabs:",
        "  smithing:",
        f'    icon: "{NS}:smithing_anvil"',
        "    items:",
    ]
    for it in tab_items:
        tabs_lines.append(f'      - "{it}"')
    (RESOURCES / "generated").mkdir(parents=True, exist_ok=True)
    (RESOURCES / "generated" / "creative_tabs.yml").write_text("\n".join(tabs_lines) + "\n", encoding="utf-8")
    print(f"KaleidoscopeSmithing generiert: {len(items_yml)} Items nach {SMITHING_ROOT}")


if __name__ == "__main__":
    main()
