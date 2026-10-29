"""
16x16 item sprites for KaleidoscopeSmithing in the vanilla style, used by gen_smithing.py.

Molds and glowing blanks use the head silhouettes cut from the vanilla tool textures of the 26.3 client jar; handles,
binding rings and the nether star core are drawn here. All of them become voxel models (extruded pixels with a per-pixel depth).
"""
import zipfile
from functools import lru_cache
from pathlib import Path

from PIL import Image

CLIENT_JAR = Path.home() / ".cache" / "kaleidoscope-pack" / "client-26.3.jar"

# Vanilla stick palette (iron tools; other tiers share the pixel positions but not always the colours)
STICK = {(73, 54, 21), (104, 78, 30), (137, 103, 39), (40, 30, 11)}
# Leather grip of forged tools and handles (dark, mid, light)
LEATHER = [(34, 20, 18), (64, 36, 30), (112, 70, 48)]
IRON = [(40, 40, 46), (98, 98, 106), (160, 160, 168), (216, 216, 222), (250, 250, 252)]
# Heat colours from the darkest to the brightest pixel
HEAT = [(70, 8, 2), (150, 26, 4), (214, 66, 8), (246, 128, 20), (255, 196, 64), (255, 244, 186)]


@lru_cache(maxsize=None)
def vanilla(name: str) -> Image.Image:
    with zipfile.ZipFile(CLIENT_JAR) as jar:
        with jar.open(f"assets/minecraft/textures/item/{name}.png") as f:
            return Image.open(f).convert("RGBA").copy()


def tool_texture(kind: str) -> Image.Image:
    # The heavy pickaxe uses the pickaxe shape
    return vanilla(f"iron_{'pickaxe' if kind == 'heavy_pickaxe' else kind}")


@lru_cache(maxsize=None)
def handle_mask(kind: str) -> frozenset:
    """Pixel positions of the stick (and the sword grip) - the same for every tier."""
    base = "pickaxe" if kind == "heavy_pickaxe" else kind
    img = vanilla(f"iron_{base}")
    mask = set()
    for y in range(16):
        for x in range(16):
            c = img.getpixel((x, y))
            if not c[3]:
                continue
            if c[:3] in STICK:
                mask.add((x, y))
            elif base == "hoe" and c[:3] == (24, 24, 24) and y >= 7:
                mask.add((x, y))  # the hoe stick has a black edge below the blade
            elif base == "sword" and x <= 4 and y >= 11:
                mask.add((x, y))  # grip outline and pommel
    return frozenset(mask)


def lum(c) -> float:
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def pixels(img: Image.Image) -> dict:
    return {(x, y): img.getpixel((x, y)) for y in range(16) for x in range(16) if img.getpixel((x, y))[3] > 0}


def to_image(px: dict) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c)
    return img


def neighbours(x, y, diagonal=True):
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if (dx or dy) and (diagonal or not (dx and dy)):
                yield x + dx, y + dy


def largest_component(px: dict) -> dict:
    seen, best = set(), set()
    for start in px:
        if start in seen:
            continue
        comp, todo = set(), [start]
        while todo:
            p = todo.pop()
            if p in comp or p not in px:
                continue
            comp.add(p)
            todo.extend(neighbours(*p))
        seen |= comp
        if len(comp) > len(best):
            best = comp
    return {p: px[p] for p in best}


def centered(px: dict) -> dict:
    xs = [x for x, _ in px]
    ys = [y for _, y in px]
    dx = (15 - max(xs) - min(xs)) // 2
    dy = (15 - max(ys) - min(ys)) // 2
    return {(x + dx, y + dy): c for (x, y), c in px.items()}


def head_pixels(kind: str) -> dict:
    """Head of a vanilla tool without the stick; stick pixels enclosed by the head become the dark socket."""
    src = pixels(tool_texture(kind))
    mask = handle_mask(kind)
    head = {p: c for p, c in src.items() if p not in mask}
    outline = min(head.values(), key=lum)
    for p, c in src.items():
        if p in mask and kind != "sword":
            x, y = p
            pairs = [((x - 1, y), (x + 1, y)), ((x, y - 1), (x, y + 1)), ((x - 1, y - 1), (x + 1, y + 1)), ((x - 1, y + 1), (x + 1, y - 1))]
            if any(a in head and b in head for a, b in pairs):
                head[p] = outline
    head = largest_component(head)
    if kind == "heavy_pickaxe":
        head = heavy(head)
    return head


def heavy(head: dict) -> dict:
    """Thicker head: grow by one pixel with the outline colour, then a steel band across the middle."""
    outline = min(head.values(), key=lum)
    grown = dict(head)
    for (x, y) in head:
        for n in neighbours(x, y, diagonal=False):
            if n not in head and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                grown[n] = outline
    # Lighten the former outline so the head reads as a thick slab
    by_lum = sorted(set(head.values()), key=lum)
    second = by_lum[1] if len(by_lum) > 1 else outline
    for p, c in head.items():
        if c == outline:
            grown[p] = second
    return grown


def ring_shape() -> set:
    """Thick ring around the centre (binding ring)."""
    return {(x, y) for y in range(16) for x in range(16) if 3.6 <= ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 6.6}


def star_shape() -> set:
    return set(pixels(vanilla("nether_star")))


ARMOR_KINDS = ("helmet", "chestplate", "leggings", "boots")


COOKERY_TEXTURES = Path(__file__).resolve().parent.parent / "resourcepack" / "imported" / "assets" / "kaleidoscope_cookery" / "textures" / "item"
# Cookware cast for KaleidoscopeCookery (knife blade, wok, stockpot)
COOKWARE_KINDS = ("knife", "wok", "stockpot")


@lru_cache(maxsize=None)
def cookery_texture(name: str) -> Image.Image:
    return Image.open(COOKERY_TEXTURES / f"{name}.png").convert("RGBA")


def cookware_pixels(kind: str) -> dict:
    """Knife: the blade of the Cookery iron knife (rows above the wooden grip); wok: the Cookery pot; stockpot: the vanilla cauldron."""
    if kind == "knife":
        return centered({p: c for p, c in pixels(cookery_texture("iron_kitchen_knife")).items() if p[1] <= 9})
    if kind == "wok":
        return centered(pixels(cookery_texture("pot_no_oil")))
    return centered(pixels(vanilla("cauldron")))


def cookware_3d(kind: str, palette: list):
    """Recoloured cookware part (thin blade, thick pots)."""
    px = recolor(cookware_pixels(kind), palette)
    return to_image(px), {p: sym(1.0 if kind == "knife" else 2.5) for p in px}


STONE = [(30, 30, 30), (70, 70, 70), (104, 104, 104), (128, 128, 128), (150, 150, 150), (178, 178, 178)]


def stone_knife_item() -> Image.Image:
    """Cookery iron knife with a stone blade (the grip stays wooden)."""
    px = pixels(cookery_texture("iron_kitchen_knife"))
    blade = {p: c for p, c in px.items() if p[1] <= 9}
    px.update(recolor(blade, STONE))
    return to_image(px)


def draw(art: list, palette: dict) -> Image.Image:
    """16x16 sprite from 16 strings; every character is a key of the palette (RGB or RGBA), "." is transparent."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch != ".":
                c = palette[ch]
                img.putpixel((x, y), c if len(c) == 4 else c + (255,))
    return img


def silhouette(kind: str) -> set:
    if kind == "knife":
        return set(cookware_pixels(kind))
    if kind in COOKWARE_KINDS:
        # Pots fill the whole sprite: shrink them so the cavity sits inside the mold's rim
        small = to_image(cookware_pixels(kind)).resize((10, 10), Image.NEAREST)
        return set(centered({(x, y): c for y in range(10) for x in range(10) if (c := small.getpixel((x, y)))[3] > 0}))
    if kind in ARMOR_KINDS:
        return set(centered(pixels(vanilla(f"iron_{kind}"))))
    if kind == "ring":
        return ring_shape()
    if kind == "star_core":
        return star_shape()
    return set(centered(head_pixels(kind)))


def noise(x, y, seed=0) -> int:
    return ((x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)) & 0xff


def blank_item(kind: str) -> Image.Image:
    """Freshly cast blank: rough glowing slab of the head shape with dark scale spots."""
    shape = silhouette(kind)
    px = {}
    for (x, y) in shape:
        edge = any(n not in shape for n in neighbours(x, y, diagonal=False))
        top_left = (x - 1, y) not in shape or (x, y - 1) not in shape
        if edge:
            c = HEAT[3] if top_left else HEAT[1]
        else:
            c = HEAT[4] if noise(x, y, 1) % 5 else HEAT[5]
            if noise(x, y, 2) % 7 == 0:
                c = HEAT[2]
        px[(x, y)] = c + (255,)
    return to_image(px)


def mold_item(kind: str, fired: bool, molten: bool = False) -> Image.Image:
    """Tray with the head-shaped cavity (clay: grey-blue, fired: terracotta; molten: cavity full of glowing metal)."""
    if fired:
        dark, base, light, cavity_dark, cavity_light = (88, 42, 30), (150, 76, 52), (184, 104, 74), (70, 32, 24), (120, 60, 42)
    else:
        dark, base, light, cavity_dark, cavity_light = (88, 96, 112), (156, 164, 182), (190, 198, 214), (104, 112, 128), (136, 144, 162)
    shape = silhouette(kind)
    px = {}
    for y in range(1, 15):
        for x in range(1, 15):
            if (x in (1, 14)) and (y in (1, 14)):
                continue  # rounded corners
            border = x in (1, 14) or y in (1, 14)
            if border:
                c = light if (x == 1 or y == 1) and not (x == 14 or y == 14) else dark
            else:
                c = base if noise(x, y, 3) % 6 else light
            px[(x, y)] = c + (255,)
    for (x, y) in shape:
        if not (2 <= x <= 13 and 2 <= y <= 13):
            continue
        if molten:
            edge = any(n not in shape for n in neighbours(x, y, diagonal=False))
            c = HEAT[2] if edge else (HEAT[4] if noise(x, y, 4) % 4 else HEAT[5])
        else:
            # Inner shadow: dark where light comes from the top left
            c = cavity_dark if (x - 1, y) not in shape or (x, y - 1) not in shape else cavity_light
        px[(x, y)] = c + (255,)
    return to_image(px)


def stick_pixels() -> dict:
    return pixels(vanilla("stick"))


def wrap_grip(px: dict, mask, from_row: int) -> dict:
    """Leather wrapping on the stick pixels from the given row down: dark leather with light diagonal straps."""
    out = dict(px)
    for (x, y) in mask:
        if (x, y) in px and y >= from_row:
            edge = (x - 1, y + 1) not in mask or (x + 1, y - 1) not in mask
            outer = (x + 1, y) not in mask and (x, y + 1) not in mask
            if outer:
                c = LEATHER[0]
            elif y % 2 == 0:
                c = LEATHER[2]
            else:
                c = LEATHER[1]
            out[(x, y)] = c + (255,)
    return out


def handle_item(reinforced: bool) -> Image.Image:
    stick = stick_pixels()
    px = wrap_grip(stick, frozenset(stick), 8)
    if reinforced:
        # Steel ferrules at the top, in the middle and at the end
        for (x, y) in list(px):
            if y in (2, 3, 7, 13, 14):
                px[(x, y)] = (IRON[3] if (x + 1, y) not in px else IRON[1]) + (255,)
    return to_image(px)


# ---- 3D: sprites become voxel models (extruded pixels with a per-pixel depth) ----


def sym(t: float) -> tuple:
    """Depth range centred on the model's middle plane (z = 8)."""
    return 8 - t / 2, 8 + t / 2


def edge_distance(px) -> dict:
    """Distance of every pixel to the outline (0 = outline)."""
    dist = {p: 0 for p in px if any(n not in px for n in neighbours(*p, diagonal=False))}
    todo = list(dist)
    while todo:
        nxt = []
        for p in todo:
            for n in neighbours(*p, diagonal=False):
                if n in px and n not in dist:
                    dist[n] = dist[p] + 1
                    nxt.append(n)
        todo = nxt
    return dist


def blank_3d(kind: str):
    img = blank_item(kind)
    px = pixels(img)
    return img, {p: sym(1.5 if d == 0 else 2.5) for p, d in edge_distance(px).items()}


def mold_3d(kind: str, fired: bool):
    """Tray 1.5 pixels thick; the cavity is recessed on the front."""
    img = mold_item(kind, fired)
    shape = silhouette(kind)
    return img, {p: ((7.25, 8.0) if p in shape and 2 <= p[0] <= 13 and 2 <= p[1] <= 13 else (7.25, 8.75)) for p in pixels(img)}


def handle_3d(reinforced: bool):
    img = handle_item(reinforced)
    return img, {p: sym(2.0 if c[:3] in IRON else 1.5) for p, c in pixels(img).items()}


def voxel_elements(depth: dict, texture: str = "#layer0") -> list:
    """Elements for the given pixels: rows are merged into runs, faces hidden by thicker neighbours are left out."""
    def covered(n, d):
        nd = depth.get(n)
        return nd is not None and nd[0] <= d[0] and nd[1] >= d[1]

    elements = []
    for y in range(16):
        x = 0
        while x < 16:
            d = depth.get((x, y))
            if d is None:
                x += 1
                continue
            key = (d, covered((x, y - 1), d), covered((x, y + 1), d))
            x1 = x
            while (x1 + 1, y) in depth and (depth[(x1 + 1, y)], covered((x1 + 1, y - 1), d), covered((x1 + 1, y + 1), d)) == key:
                x1 += 1
            faces = {
                "south": {"uv": [x, y, x1 + 1, y + 1], "texture": texture},
                "north": {"uv": [x1 + 1, y, x, y + 1], "texture": texture},
            }
            if not key[1]:
                faces["up"] = {"uv": [x, y, x1 + 1, y + 1], "texture": texture}
            if not key[2]:
                faces["down"] = {"uv": [x, y, x1 + 1, y + 1], "texture": texture}
            if not covered((x - 1, y), d):
                faces["west"] = {"uv": [x, y, x + 1, y + 1], "texture": texture}
            if not covered((x1 + 1, y), d):
                faces["east"] = {"uv": [x1, y, x1 + 1, y + 1], "texture": texture}
            elements.append({"from": [x, 15 - y, d[0]], "to": [x1 + 1, 16 - y, d[1]], "faces": faces})
            x = x1 + 1
    return elements


# ---- Binding rings, nether star core and ring core ----

RING_PALETTES = {
    # dark edge, body, light, highlight
    "diamond": [(16, 82, 90), (44, 168, 172), (116, 234, 230), (214, 255, 252)],
    "netherite": [(28, 22, 30), (70, 58, 74), (124, 108, 128), (196, 150, 52)],
    "hot": [(150, 26, 4), (230, 92, 14), (255, 170, 50), (255, 236, 170)],
}
CORE_COLOURS = [(64, 20, 110), (170, 90, 240), (255, 250, 230)]


def ring_item(palette: str) -> Image.Image:
    """Binding ring lit from the top left; the ring core shows as a small glowing star in the middle."""
    dark, body, light, high = RING_PALETTES[palette]
    px = {}
    for (x, y) in ring_shape():
        r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        lit = (7.5 - x) + (7.5 - y)
        if r > 6.1 or r < 4.1:
            c = dark
        elif lit > 3:
            c = high if r < 5.3 else light
        elif lit > -3:
            c = body
        else:
            c = dark if palette != "netherite" else body
        px[(x, y)] = c + (255,)
    if palette == "netherite":
        for (x, y) in ((7, 1), (14, 7), (8, 14), (1, 8)):
            px[(x, y)] = RING_PALETTES["netherite"][3] + (255,)  # gold rivets
    for (x, y), c in {(7, 7): 2, (8, 8): 2, (7, 8): 1, (8, 7): 1, (6, 7): 0, (9, 8): 0, (7, 6): 0, (8, 9): 0}.items():
        px[(x, y)] = CORE_COLOURS[c] + (255,)
    return to_image(px)


def ring_3d(palette: str):
    img = ring_item(palette)
    core = {(7, 7), (8, 8), (7, 8), (8, 7), (6, 7), (9, 8), (7, 6), (8, 9)}
    # Thin like the molds; the core sits slightly recessed
    return img, {p: sym(1.0 if p in core else 1.5) for p in pixels(img)}


def star_core_3d(glowing: bool):
    """Forged nether star core: the nether star in deep violet (glowing: in heat colours), thick in the middle."""
    star = pixels(vanilla("nether_star"))
    values = sorted({lum(c) for c in star.values()})
    lo, hi = values[0], values[-1]
    ramp = RING_PALETTES["hot"] if glowing else [(48, 14, 84)] + CORE_COLOURS[1:] + [(255, 255, 255)]
    px = {}
    for p, c in star.items():
        t = (lum(c) - lo) / (hi - lo) if hi > lo else 0.5
        px[p] = ramp[min(len(ramp) - 1, int(t * (len(ramp) - 1) + 0.5))] + (255,)
    dist = edge_distance(px)
    return to_image(px), {p: sym(min(3.0, 1.0 + 0.5 * dist[p])) for p in px}


def ring_core_3d():
    """Crafted ring core: obsidian and crying obsidian frame around the nether star core."""
    obsidian, crying, star = [(20, 16, 30), (44, 30, 66)], [(70, 20, 130), (140, 60, 220)], CORE_COLOURS
    px = {}
    for y in range(3, 13):
        for x in range(3, 13):
            if (x in (3, 12)) and (y in (3, 12)):
                continue
            edge = x in (3, 12) or y in (3, 12)
            corner_tl_br = (x < 8) == (y < 8)
            pal = obsidian if corner_tl_br else crying
            px[(x, y)] = (pal[1] if edge and (x == 3 or y == 3) else pal[0]) + (255,)
    for (x, y), c in {(7, 6): 1, (8, 6): 1, (6, 7): 1, (7, 7): 2, (8, 7): 2, (9, 7): 1,
                      (6, 8): 1, (7, 8): 2, (8, 8): 2, (9, 8): 1, (7, 9): 1, (8, 9): 1}.items():
        px[(x, y)] = star[c] + (255,)
    middle = {p for p, c in px.items() if c[:3] in star}
    return to_image(px), {p: sym(2.5 if p in middle else 2.0) for p in px}


def nether_star_block() -> Image.Image:
    """Block texture of compressed nether stars: pale glowing body with star sparkles in the nether star colours."""
    base, light, purple, yellow, white = (214, 218, 206), (236, 240, 228), (150, 110, 196), (238, 214, 120), (255, 255, 255)
    img = Image.new("RGBA", (16, 16), base + (255,))
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 7) % 9
            if x in (0, 15) or y in (0, 15):
                c = purple if n < 3 else base
            else:
                c = light if n < 3 else (yellow if n == 3 else base)
            img.putpixel((x, y), c + (255,))
    # Four small stars
    for cx, cy in ((4, 4), (11, 5), (6, 11), (12, 12)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            img.putpixel((cx + dx, cy + dy), (white if (dx, dy) == (0, 0) else yellow) + (255,))
    return img


def end_steel_shard_3d():
    """End steel shard: angular violet-silver crystal splinter with a bright edge."""
    rows = [
        "..........b.....",
        ".........bab....",
        "........bacb....",
        ".......bacdb....",
        "......bacddb....",
        ".....bacdddb....",
        "....bacdddb.....",
        "...bacdddb......",
        "...bcdddb.......",
        "..bcddeb........",
        "..bddeb.........",
        ".bdeeb..........",
        ".beeb...........",
        ".bbb............",
    ]
    colours = {"a": (250, 240, 255), "b": (40, 24, 60), "c": (196, 170, 232), "d": (126, 92, 176), "e": (78, 52, 120)}
    px = {(x, y + 1): colours[ch] + (255,) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in colours}
    dist = edge_distance(px)
    return to_image(px), {p: sym(1.0 + 0.5 * min(2, dist[p])) for p in px}


# Design plates (End, Nether, Sculk): primary, secondary, highlight
DESIGN_COLOURS = {
    "end": [(70, 40, 84), (160, 110, 176), (236, 232, 170)],
    "nether": [(40, 10, 14), (130, 28, 36), (236, 186, 40)],
    "sculk": [(8, 26, 34), (20, 80, 96), (90, 230, 230)],
    "vote": [(14, 26, 84), (38, 70, 170), (250, 214, 96)],
}


def design_3d(design: str):
    """Design: engraved metal plate with a framed emblem in the design's colours."""
    dark, body, high = DESIGN_COLOURS[design]
    px = {}
    for y in range(2, 14):
        for x in range(3, 13):
            if (x in (3, 12)) and (y in (2, 13)):
                continue
            edge = x in (3, 12) or y in (2, 13)
            px[(x, y)] = (dark if edge else body) + (255,)
    # Emblem: diamond shape in the middle and corner rivets
    for (x, y) in ((7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
        px[(x, y)] = high + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[(x, y)] = dark + (255,)
    for (x, y) in ((4, 3), (11, 3), (4, 12), (11, 12)):
        px[(x, y)] = high + (255,)
    emblem = {p for p, c in px.items() if c[:3] == high}
    return to_image(px), {p: sym(2.0 if p in emblem else 1.5) for p in px}


def folio_3d():
    """Smithing folio: thick leather book with an iron clasp and a hammer emblem."""
    leather, dark, page, iron = (110, 60, 34), (60, 30, 18), (232, 222, 196), (190, 190, 198)
    px = {}
    for y in range(2, 15):
        for x in range(3, 13):
            if x == 12:
                c = page if 3 <= y <= 13 else dark
            elif x == 3 or y in (2, 14):
                c = dark
            else:
                c = leather
            px[(x, y)] = c + (255,)
    for (x, y) in ((6, 5), (7, 5), (8, 5), (7, 6), (7, 7), (7, 8), (7, 9), (7, 10)):
        px[(x, y)] = iron + (255,)
    for y in (7, 8, 9):
        px[(11, y)] = iron + (255,)
    pages = {p for p, c in px.items() if c[:3] == page}
    return to_image(px), {p: sym(2.0 if p in pages else 2.5) for p in px}


# ---- Armor (plates on the anvil, finished pieces, padding) ----

GEMS = {
    # dark, body, highlight
    "ruby": [(96, 8, 28), (196, 28, 58), (255, 132, 156)],
    "emerald": [(8, 86, 40), (28, 168, 78), (150, 255, 178)],
    "amethyst": [(66, 26, 116), (148, 78, 222), (232, 196, 255)],
    "sculk_gem": [(0, 60, 74), (36, 196, 206), (230, 255, 255)],
    "vote_gem": [(120, 70, 4), (244, 196, 40), (255, 252, 226)],
}


def recolor(px: dict, palette: list) -> dict:
    """Map the colours of a sprite by brightness rank onto a dark-to-light palette."""
    shades = sorted({c[:3] for c in px.values()}, key=lum)
    out = {}
    for p, c in px.items():
        rank = shades.index(c[:3]) / max(1, len(shades) - 1)
        out[p] = palette[round(rank * (len(palette) - 1))] + (255,)
    return out


def gem_spot(kind: str, shape) -> list:
    """Pixels for a small gem: forehead of the helmet, centre of the chestplate, belt buckle, top of the boots."""
    xs = sorted({x for x, _ in shape})
    cx = xs[len(xs) // 2]
    rows = sorted({y for _, y in shape})
    y = {"helmet": rows[0] + 1, "chestplate": rows[0] + 3, "leggings": rows[0], "boots": rows[0] + 1}[kind]
    if kind == "boots":
        # one gem on the left boot
        cx = xs[1]
    return [(cx, y), (cx - 1, y)] if kind == "chestplate" else [(cx, y)]


def armor_paint(kind: str, palette: list, accent: list, gem: str | None, hot: bool = False) -> dict:
    """Armor icon in the style of the forged tools: dark outline, raised trim in the accent metal (rivets in the corners),
    shaded plates with a diagonal sheen and plate seams, and a gem in a gold setting for the noble tiers."""
    shape = set(centered(pixels(vanilla(f"iron_{kind}"))))
    if hot:
        palette, accent = HEAT[1:], HEAT[:5]
    dist = edge_distance({p: 1 for p in shape})
    ys = [y for _, y in shape]
    top, bottom = min(ys), max(ys)
    px = {}
    for (x, y) in shape:
        d = dist[(x, y)]
        lit = (x - 1, y) not in shape or (x, y - 1) not in shape
        # Trim only as horizontal bands: along the top and bottom edges of the piece (rim, collar, belt, cuffs)
        band = d == 1 and ((x, y - 2) not in shape or (x, y + 2) not in shape) and (x, y - 1) in shape and (x, y + 1) in shape
        if d == 0:
            c = palette[0]
        elif band:
            c = accent[3] if (x, y - 2) not in shape else accent[2]
            if (x - 1, y) not in shape or (x + 1, y) not in shape or dist.get((x - 1, y), 0) == 0 or dist.get((x + 1, y), 0) == 0:
                c = accent[4]  # rivet at the end of a band
        elif d == 1 and not lit:
            c = palette[1]
        else:
            t = (y - top) / max(1, bottom - top)
            c = palette[3] if t < 0.3 else palette[2] if t < 0.75 else palette[1]
            if (x - y) % 6 == 0 and t < 0.7:
                c = palette[4]
            if kind in ("chestplate", "leggings") and (y - top) in (5, 9) and d >= 2:
                c = palette[1]  # plate seams
        px[(x, y)] = c + (255,)
    if gem and not hot:
        dark, body, high = GEMS[gem]
        spots = gem_spot(kind, shape)
        for i, p in enumerate(spots):
            if p in px:
                px[p] = (high if i == 0 else body) + (255,)
            for n in neighbours(*p, diagonal=False):
                if n in px and n not in spots and dist.get(n, 0) >= 1:
                    px[n] = PALETTES_GOLD_SETTING + (255,)
    return px


PALETTES_GOLD_SETTING = (232, 178, 46)


def armor_item(kind: str, palette: list, accent: list, gem: str | None = None, hot: bool = False) -> Image.Image:
    return to_image(armor_paint(kind, palette, accent, gem, hot))


def armor_depth(img: Image.Image, gem: str | None) -> dict:
    """Trim and outline stand out, the plates lie a bit deeper, the gem sits on top."""
    px = pixels(img)
    dist = edge_distance(px)
    gem_colours = {c for g in GEMS.values() for c in g} | {PALETTES_GOLD_SETTING}
    depth = {}
    for p, c in px.items():
        if c[:3] in gem_colours:
            depth[p] = sym(1.5)
        elif dist[p] <= 1:
            depth[p] = sym(1.25)
        else:
            depth[p] = sym(0.875)
    return depth


def armor_3d(kind: str, palette: list, accent: list, gem: str | None = None):
    img = armor_item(kind, palette, accent, gem)
    return img, armor_depth(img, gem)


def plate_3d(kind: str, palette: list, accent: list, hot: bool = False):
    """Forged plate before assembly: the same piece without gem and with a plain trim in its own metal."""
    img = armor_item(kind, palette, palette if not hot else accent, None, hot)
    return img, {p: sym(0.5 if d == 0 else 0.75) for p, d in edge_distance(pixels(img)).items()}


def padding_3d():
    """Quilted leather padding: the leather sprite with stitched lines."""
    px = recolor(centered(pixels(vanilla("leather"))), [(72, 40, 22), (112, 66, 36), (150, 94, 52), (188, 128, 76), (216, 166, 112)])
    for (x, y) in list(px):
        if (x + y) % 4 == 0 and not any(n not in px for n in neighbours(x, y, diagonal=False)):
            px[(x, y)] = (238, 222, 190, 255)
    img = to_image(px)
    return img, {p: sym(1.25 if d == 0 else 2.0) for p, d in edge_distance(pixels(img)).items()}


# Faces of the humanoid armor model on the 64x32 texture: (x, y, width, height)
HUMANOID_FACES = [
    # head: right, front, left, back, top, bottom
    (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8), (8, 0, 8, 8), (16, 0, 8, 8),
    # body
    (16, 20, 4, 12), (20, 20, 8, 12), (28, 20, 4, 12), (32, 20, 8, 12), (20, 16, 8, 4), (28, 16, 8, 4),
    # arm
    (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12), (44, 16, 4, 4), (48, 16, 4, 4),
    # leg
    (0, 20, 4, 12), (4, 20, 4, 12), (8, 20, 4, 12), (12, 20, 4, 12), (4, 16, 4, 4), (8, 16, 4, 4),
]


def mix(palette: list, t: float) -> tuple:
    """Colour along a dark-to-light palette, t from 0 to 1 (interpolated, for smooth embossing)."""
    t = min(1.0, max(0.0, t)) * (len(palette) - 1)
    i = min(int(t), len(palette) - 2)
    f = t - i
    a, b = palette[i], palette[i + 1]
    return tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))


# Brightness of a face by its direction (light from the top front left)
FACE_LIGHT = {"right": -0.12, "front": 0.0, "left": -0.06, "back": -0.18, "top": 0.15, "bottom": -0.3}
FACE_NAMES = ("right", "front", "left", "back", "top", "bottom")


def worn_texture(layer: str, palette: list, accent: list, gem: str | None) -> Image.Image:
    """Worn armor that reads as relief although it is flat: every face is shaded by its direction, plates have a light
    bevel at the top/left and a dark one at the bottom/right, lames, a ridged breastplate, knee cops, vambraces,
    accent trims with rivets and a set gem with a sparkle. Coverage follows the vanilla iron armor."""
    with zipfile.ZipFile(CLIENT_JAR) as jar:
        with jar.open(f"assets/minecraft/textures/entity/equipment/{layer}/iron.png") as f:
            src = Image.open(f).convert("RGBA").copy()
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    parts = ("head", "body", "arm", "leg")
    for index, (fx, fy, fw, fh) in enumerate(HUMANOID_FACES):
        part, side = parts[index // 6], FACE_NAMES[index % 6]
        mask = {(x, y) for x in range(fx, fx + fw) for y in range(fy, fy + fh) if src.getpixel((x, y))[3]}
        if not mask:
            continue
        top = min(y for _, y in mask)
        bottom = max(y for _, y in mask)
        light = FACE_LIGHT[side]
        for (x, y) in mask:
            u, v = x - fx, y - top
            rows = bottom - top + 1
            first, last = (x - 1, y) not in mask, (x + 1, y) not in mask
            c = None
            t = 0.5 + light
            if side in ("top", "bottom"):
                t = 0.55 + light
            elif part == "body" and layer == "humanoid":
                if v == 0:
                    c = mix(accent, 0.95 if u in (0, fw - 1) else 0.7)  # collar with rivets
                elif v == rows - 1:
                    c = mix(accent, 0.95 if u in (fw // 2 - 1, fw // 2) and side == "front" else 0.5)  # belt, buckle
                elif v <= 5:
                    # breastplate: two plates with a centre ridge, bevelled
                    t = 0.72 - v * 0.05 + light
                    if side == "front" and u == fw // 2 - 1:
                        t = 0.95 + light
                    elif side == "front" and u == fw // 2:
                        t = 0.25 + light
                    elif side == "back" and u in (fw // 2 - 1, fw // 2):
                        t = 0.35 + light  # spine
                    if v == 1:
                        t += 0.15
                    if v == 5:
                        t = 0.2 + light
                else:
                    # abdominal lames: light top row, darker lower row
                    t = (0.7 if (v - 6) % 2 == 0 else 0.38) + light
            elif part == "arm" and layer == "humanoid":
                if v <= 4:
                    # pauldron lames, the lowest with an accent edge
                    t = (0.95, 0.55, 0.85, 0.45)[v] + light if v < 4 else None
                    if v == 4:
                        c = mix(accent, 0.75)
                elif v <= 7:
                    # mail between pauldron and vambrace
                    t = (0.6 if (u + v) % 2 == 0 else 0.3) + light
                elif v == rows - 1:
                    c = mix(accent, 0.6)  # cuff
                else:
                    t = (0.85 if v == 8 else 0.5) + light  # vambrace
            elif part == "leg" and layer == "humanoid_leggings":
                if v <= 3:
                    t = (0.85 if v % 2 == 0 else 0.45) + light  # thigh lames
                elif v <= 6:
                    # knee cop: domed with a bright centre
                    centre = abs(u - (fw - 1) / 2) < 1 and v == 5
                    t = (1.0 if centre else 0.6 if v == 4 else 0.35) + light
                    if side == "front" and v == 5 and centre:
                        c = mix(accent, 0.95)
                else:
                    t = (0.75 if u == 1 and side == "front" else 0.5 - (v - 7) * 0.04) + light  # greave with ridge
            elif part == "body" and layer == "humanoid_leggings":
                if v == 0:
                    c = mix(accent, 0.9 if u in (0, fw - 1) else 0.65)  # belt
                else:
                    t = (0.65 if v % 2 == 1 else 0.4) + light  # hip lames
            elif part == "leg" and layer == "humanoid":
                if v == 0:
                    c = mix(accent, 0.9 if u in (0, fw - 1) else 0.65)  # boot cuff
                elif v == rows - 1:
                    c = mix(palette, 0.05)  # sole
                elif v == rows - 2 and side == "front":
                    t = 0.9 + light  # toe cap
                else:
                    t = (0.8 if u == 1 and side == "front" else 0.5) + light  # shin plate with ridge
            if c is None and t is not None:
                # bevel: light edge left, dark edge right
                if first and side not in ("top", "bottom"):
                    t += 0.12
                if last and side not in ("top", "bottom"):
                    t -= 0.15
                c = mix(palette, t)
            out.putpixel((x, y), c + (src.getpixel((x, y))[3],))
    if gem and layer == "humanoid":
        dark, body, high = GEMS[gem]
        # chest front (x 20-27, y 20-31): gem at the top of the ridge in a gold setting, with a sparkle
        for (gx, gy), colour in (((23, 22), high), ((24, 22), body), ((23, 23), body), ((24, 23), dark)):
            out.putpixel((gx, gy), colour + (255,))
        out.putpixel((23, 22), (255, 255, 255, 255))
        for (sx, sy) in ((22, 22), (25, 22), (22, 23), (25, 23), (23, 21), (24, 21), (23, 24), (24, 24)):
            if out.getpixel((sx, sy))[3]:
                out.putpixel((sx, sy), mix(accent, 0.85) + (255,))
    if gem and layer == "humanoid_leggings":
        dark, body, high = GEMS[gem]
        for (gx, gy), colour in (((23, 20), high), ((24, 20), body)):
            if out.getpixel((gx, gy))[3]:
                out.putpixel((gx, gy), colour + (255,))
    return out
