"""
Generates the data and assets of KaleidoscopeTanning (own module, no mod template):

- src/main/resources/items.yml, recipes.yml, generated/creative_tabs.yml
- resourcepack/custom/assets/kaleidoscope_tanning: item textures (recoloured vanilla sprites), the tanning frame
  (frame model + one hide model per state), leather variants for the quality item models, language files

Run after changes: python3 tools/gen_tanning.py (from KaleidoscopeCookery), then build KaleidoscopeTanning.
"""
import json
import zipfile
from functools import lru_cache
from pathlib import Path

from PIL import Image

import smithing_sprites as sprites

ROOT = Path(__file__).resolve().parent.parent
TANNING = ROOT.parent / "KaleidoscopeTanning"
NS = "kaleidoscope_tanning"
OUT = TANNING / "resourcepack" / "custom" / "assets" / NS
RES = TANNING / "src" / "main" / "resources"
CLIENT_JAR = Path.home() / ".cache" / "kaleidoscope-pack" / "client-26.3.jar"

# Leather qualities (crude/fine change the item model of minecraft:leather; normal is plain vanilla leather)
QUALITIES = ("crude", "normal", "fine")


def k(value: str) -> str:
    return f"{NS}:{value}"


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def yaml_str(value) -> str:
    return json.dumps(value, ensure_ascii=False)


@lru_cache(maxsize=None)
def vanilla_block(name: str) -> Image.Image:
    with zipfile.ZipFile(CLIENT_JAR) as jar:
        with jar.open(f"assets/minecraft/textures/block/{name}.png") as f:
            return Image.open(f).convert("RGBA").copy()


def noise(x, y, seed=0) -> int:
    return ((x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)) & 0xff


# ---- item sprites ----

RAW = [(70, 34, 30), (128, 66, 58), (176, 104, 92), (214, 150, 136), (236, 190, 176)]
FUR = [(62, 42, 26), (92, 64, 38)]
SCRAPED = [(120, 96, 70), (176, 148, 112), (212, 188, 150), (232, 214, 182), (246, 236, 214)]
TALLOW = [(150, 134, 84), (200, 186, 132), (230, 220, 170), (246, 240, 204), (255, 252, 232)]
SOAP = [(120, 104, 140), (176, 160, 196), (212, 200, 228), (236, 228, 246), (255, 255, 255)]
CRUDE = [(40, 28, 20), (72, 52, 36), (98, 74, 52), (124, 98, 72), (146, 120, 90)]
FINE = [(54, 20, 10), (110, 46, 22), (160, 78, 36), (204, 118, 58), (246, 196, 128)]
GRIP = [(30, 18, 10), (70, 40, 22), (112, 68, 38), (150, 98, 58), (190, 140, 92)]


def leather_px() -> dict:
    return sprites.pixels(sprites.vanilla("leather"))


def raw_hide() -> Image.Image:
    px = sprites.recolor(leather_px(), RAW)
    # tufts of fur on the outside
    for (x, y) in list(px):
        if noise(x, y, 7) % 5 == 0:
            px[(x, y)] = FUR[noise(x, y, 8) % 2] + (255,)
    return sprites.to_image(px)


def bark(log: str, dark: bool) -> Image.Image:
    """A chip of bark: an irregular slab cut from the log side texture with a dark rim."""
    tex = vanilla_block(log)
    px = {}
    for y in range(4, 13):
        for x in range(2, 14):
            jag = noise(x, y, 3) % 3
            if (y in (4, 12) and jag == 0) or (x in (2, 13) and jag == 1):
                continue
            c = tex.getpixel((x, y))[:3]
            if dark:
                c = tuple(int(v * 0.8) for v in c)
            px[(x, y)] = c + (255,)
    for (x, y) in list(px):
        if any(n not in px for n in sprites.neighbours(x, y, diagonal=False)):
            r, g, b, a = px[(x, y)]
            px[(x, y)] = (int(r * 0.55), int(g * 0.55), int(b * 0.55), a)
    return sprites.to_image(px)


def soap() -> Image.Image:
    """A rounded bar of soap with a few bubbles above it."""
    art = ["................",
           "..........ww....",
           ".........w..w...",
           "....ww...w..w...",
           "...w..w...ww....",
           "...w..w.........",
           "....ww..........",
           "................",
           "...OOOOOOOOOO...",
           "..OHHHHHHHHHPO..",
           "..OHPPPPPPPPDO..",
           "..OHPPPHHPPPDO..",
           "..OPPPPPPPPDDO..",
           "..ODDDDDDDDDDO..",
           "...OOOOOOOOOO...",
           "................"]
    return sprites.draw(art, {"O": (112, 92, 142), "H": (244, 238, 252), "P": (216, 200, 236), "D": (174, 154, 202),
                              "w": (214, 236, 255, 220)})


def item_sprites() -> dict:
    return {
        "raw_hide": raw_hide(),
        "scraped_hide": sprites.to_image(sprites.recolor(leather_px(), SCRAPED)),
        "tallow": sprites.to_image(sprites.recolor(sprites.pixels(sprites.vanilla("slime_ball")), TALLOW)),
        "bark": bark("spruce_log", True),
        "oak_bark": bark("oak_log", False),
        "soap": soap(),
        "leather_grip": sprites.to_image(sprites.recolor(sprites.pixels(sprites.vanilla("lead")), GRIP)),
        "crude_leather": sprites.to_image(sprites.recolor(leather_px(), CRUDE)),
        "fine_leather": sprites.to_image(sprites.recolor(leather_px(), FINE)),
    }


# ---- tanning frame ----

def hide_texture(palette: list, seed: int) -> Image.Image:
    """Stretched hide: mottled surface, darker rim."""
    import random
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            # mostly the middle shades, a few dark pores and light spots
            i = 0 if edge else min(len(palette) - 1, max(1, round(rnd.gauss(2.4, 0.8))))
            img.putpixel((x, y), palette[i] + (255,))
    return img


HIDE_STATES = {
    "scraped": SCRAPED,
    "tanning": [(70, 50, 30), (118, 88, 58), (140, 108, 74), (160, 126, 88), (176, 140, 100)],
    "crude": CRUDE,
    "normal": [(62, 36, 22), (122, 72, 44), (150, 92, 56), (172, 110, 68), (190, 128, 82)],
    "fine": FINE,
}


def box(frm, to, texture, uv_faces=None):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        faces[face] = {"texture": texture}
    return {"from": frm, "to": to, "faces": faces}


def frame_models():
    log = "minecraft:block/stripped_spruce_log"
    elements = [
        # posts and feet
        box([0, 0, 7], [2, 16, 9], "#wood"), box([14, 0, 7], [16, 16, 9], "#wood"),
        box([0, 0, 4], [2, 1.5, 12], "#wood"), box([14, 0, 4], [16, 1.5, 12], "#wood"),
        # bars
        box([2, 14, 7.25], [14, 15.5, 8.75], "#wood"), box([2, 2, 7.25], [14, 3.5, 8.75], "#wood"),
    ]
    # lacing between the hide and the frame
    for x in (3, 6, 9.5, 12.5):
        elements.append(box([x, 13, 7.9], [x + 0.5, 14, 8.1], "#lace"))
        elements.append(box([x, 3.5, 7.9], [x + 0.5, 4.5, 8.1], "#lace"))
    write_json(OUT / "models" / "block" / "tanning_frame.json", {
        "parent": "minecraft:block/block",
        "textures": {"wood": log, "lace": "minecraft:block/white_wool", "particle": log},
        "elements": elements,
    })
    write_json(OUT / "items" / "tanning_frame.json", {"model": {"type": "minecraft:model", "model": k("block/tanning_frame")}})
    write_json(OUT / "items" / "block" / "tanning_frame.json", {"model": {"type": "minecraft:model", "model": k("block/tanning_frame")}})
    out = OUT / "textures" / "block"
    out.mkdir(parents=True, exist_ok=True)
    for seed, (state, palette) in enumerate(HIDE_STATES.items()):
        hide_texture(palette, seed + 11).save(out / f"hide_{state}.png")
        face = {"uv": [0, 0, 16, 16], "texture": "#hide"}
        write_json(OUT / "models" / "block" / "frame_hide" / f"{state}.json", {
            "textures": {"hide": k(f"block/hide_{state}"), "particle": k(f"block/hide_{state}")},
            "elements": [{"from": [3, 4, 7.9], "to": [13, 13, 8.1], "faces": {"north": face, "south": dict(face)}}],
        })
        write_json(OUT / "items" / "block" / "frame_hide" / f"{state}.json",
                   {"model": {"type": "minecraft:model", "model": k(f"block/frame_hide/{state}")}})


BOARD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [66, 0, 0], "translation": [0, 0.5, -5], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [-175.58, -79.14, -172.05], "translation": [5.5, 1.75, 0], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, -2.75], "scale": [0.5, 0.5, 0.5]},
    "gui": {"translation": [0, -1.75, 0]},
    "fixed": {"rotation": [0, -180, 0], "translation": [0, -4.25, 6.5]},
}
SCRAPE_CUTS = 6


def board_models():
    """Raw hide on Cookery's chopping board (models chopping_board/raw_hide/<cut>): the fur fades with every cut and the
    scraped-off tallow piles up next to it."""
    import random
    out = OUT / "textures" / "chopping_board"
    out.mkdir(parents=True, exist_ok=True)
    for cut in range(SCRAPE_CUTS + 1):
        t = cut / SCRAPE_CUTS
        palette = [tuple(round(a + (b - a) * t) for a, b in zip(r, c)) for r, c in zip(RAW, SCRAPED)]
        rnd = random.Random(5)
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                edge = x in (0, 15) or y in (0, 15)
                i = 0 if edge else min(4, max(1, round(rnd.gauss(2.6, 0.8))))
                c = palette[i]
                # fur tufts that are scraped away cut by cut
                if not edge and rnd.random() < 0.35 * (1 - t):
                    c = FUR[rnd.randrange(2)]
                img.putpixel((x, y), c + (255,))
        img.save(out / f"raw_hide_{cut}.png")
        elements = [{"from": [3, 0, 4], "to": [13, 0.5, 12], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#hide"}, "north": {"uv": [0, 0, 16, 1], "texture": "#hide"},
            "south": {"uv": [0, 0, 16, 1], "texture": "#hide"}, "east": {"uv": [0, 0, 16, 1], "texture": "#hide"},
            "west": {"uv": [0, 0, 16, 1], "texture": "#hide"}}}]
        if cut >= 2:
            size = 1 + cut / SCRAPE_CUTS * 1.5
            faces = {f: {"uv": [5, 5, 11, 11], "texture": "#tallow"} for f in ("up", "north", "south", "east", "west")}
            elements.append({"from": [13.5, 0, 9], "to": [13.5 + size, size * 0.6, 9 + size], "faces": faces})
        write_json(OUT / "models" / "chopping_board" / "raw_hide" / f"{cut}.json", {
            "textures": {"hide": k(f"chopping_board/raw_hide_{cut}"), "tallow": k("item/tallow"),
                         "particle": k(f"chopping_board/raw_hide_{cut}")},
            "elements": elements, "display": BOARD_DISPLAY})
        write_json(OUT / "items" / "chopping_board" / "raw_hide" / f"{cut}.json",
                   {"model": {"type": "minecraft:model", "model": k(f"chopping_board/raw_hide/{cut}")}})


def item_assets():
    out = OUT / "textures" / "item"
    out.mkdir(parents=True, exist_ok=True)
    for name, img in item_sprites().items():
        img.save(out / f"{name}.png")
        write_json(OUT / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": k(f"item/{name}")}})
        write_json(OUT / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": k(f"item/{name}")}})


# ---- data ----

ITEMS = ["raw_hide", "scraped_hide", "tallow", "bark", "oak_bark", "soap", "leather_grip", "tanning_frame"]
# Name keys of the leather variants (item_name of minecraft:leather with a quality)
LEATHER_NAMES = {"crude_leather": ("Crude Leather", "Grobes Leder"), "fine_leather": ("Fine Leather", "Feines Leder")}

TEXTS = {
    # key: (English, German)
    f"item.{NS}.raw_hide": ("Raw Hide", "Rohhaut"),
    f"item.{NS}.scraped_hide": ("Scraped Hide", "Geschabte Haut"),
    f"item.{NS}.tallow": ("Tallow", "Talg"),
    f"item.{NS}.bark": ("Bark", "Rinde"),
    f"item.{NS}.oak_bark": ("Oak Bark", "Eichenrinde"),
    f"item.{NS}.soap": ("Soap", "Seife"),
    f"item.{NS}.leather_grip": ("Leather Grip", "Lederwicklung"),
    f"item.{NS}.tanning_frame": ("Tanning Frame", "Gerbrahmen"),
    f"item.{NS}.crude_leather": LEATHER_NAMES["crude_leather"],
    f"item.{NS}.fine_leather": LEATHER_NAMES["fine_leather"],
    f"tooltip.{NS}.raw_hide": ("Scrape it on a chopping board with a kitchen knife", "Auf einem Schneidebrett mit einem Küchenmesser abschaben"),
    f"tooltip.{NS}.scraped_hide": ("Stretch it on a tanning frame", "Auf einen Gerbrahmen spannen"),
    f"tooltip.{NS}.tallow": ("For candles and soap", "Für Kerzen und Seife"),
    f"tooltip.{NS}.bark": ("Tanning agent: normal leather. From stripping logs", "Gerbstoff: normales Leder. Fällt beim Entrinden von Stämmen"),
    f"tooltip.{NS}.oak_bark": ("Tanning agent: with salt fine leather. From stripping oak logs", "Gerbstoff: mit Salz feines Leder. Fällt beim Entrinden von Eichenstämmen"),
    f"tooltip.{NS}.soap": ("Put it into a sink: glasses get clean faster", "In die Spüle geben: Gläser werden schneller sauber"),
    f"tooltip.{NS}.leather_grip": ("Wrap a handle on the smithing assembly table: more durability", "Am Schmiede-Montagetisch um den Griff wickeln: mehr Haltbarkeit"),
    f"tooltip.{NS}.tanning_frame": ("Stretch a scraped hide on it, add a tanning agent and wet it with a water bottle",
                                     "Geschabte Haut aufspannen, Gerbstoff dazugeben und mit einer Wasserflasche anfeuchten"),
    f"skill.{NS}.tanning": ("Tanning", "Gerben"),
    "kaleidoscope.menu.tab.tanning": ("Tannery", "Gerberei"),
    f"{NS}.quality.crude": ("Quality: Crude", "Qualität: Grob"),
    f"{NS}.quality.normal": ("Quality: Normal", "Qualität: Normal"),
    f"{NS}.quality.fine": ("Quality: Fine", "Qualität: Fein"),
    f"{NS}.grip": ("Leather grip (%s)", "Ledergriff (%s)"),
    f"{NS}.quality_name.crude": ("crude", "grob"),
    f"{NS}.quality_name.normal": ("normal", "normal"),
    f"{NS}.quality_name.fine": ("fine", "fein"),
    f"{NS}.frame.empty": ("Tanning frame | stretch a scraped hide on it", "Gerbrahmen | geschabte Haut aufspannen"),
    f"{NS}.frame.ready": ("Tanning frame | agent: %s | water bottle: wet it and start tanning",
                          "Gerbrahmen | Gerbstoff: %s | Wasserflasche: anfeuchten und Gerben starten"),
    f"{NS}.frame.tanning": ("Tanning: %s%% | %s", "Gerben: %s%% | %s"),
    f"{NS}.frame.done": ("Leather ready (%s) - take it with an empty hand", "Leder fertig (%s) - mit leerer Hand abnehmen"),
    f"{NS}.frame.agent.none": ("none (crude)", "keiner (grob)"),
    f"{NS}.frame.agent.bark": ("bark (normal)", "Rinde (normal)"),
    f"{NS}.frame.agent.oak_bark": ("oak bark (normal, with salt fine)", "Eichenrinde (normal, mit Salz fein)"),
    f"{NS}.frame.agent.oak_bark_salt": ("oak bark + salt (fine)", "Eichenrinde + Salz (fein)"),
    f"{NS}.frame.agent_full": ("The frame already has its tanning agent", "Der Rahmen hat schon seinen Gerbstoff"),
    f"{NS}.frame.need_oak": ("Salt only works together with oak bark - add the oak bark first", "Salz wirkt nur mit Eichenrinde - zuerst Eichenrinde dazugeben"),
    f"{NS}.frame.too_low": ("Fine leather needs tanning level %s", "Feines Leder braucht Gerb-Stufe %s"),
    f"{NS}.frame.better": ("Well done - the leather turned out better!", "Gut gemacht - das Leder ist besser geworden!"),
    f"{NS}.frame.busy": ("The hide is tanning - wait until it is done", "Die Haut gerbt gerade - warte, bis sie fertig ist"),
    f"{NS}.sink.soap": ("Soap: %s glasses", "Seife: %s Gläser"),
}


def write_items():
    lines = ["# Generiert von tools/gen_tanning.py", "items:"]
    for item in ITEMS:
        lines.append(f"  {yaml_str(k(item))}:")
        lines.append(f"    model: {yaml_str(k(item))}")
        lines.append(f"    name: {yaml_str(f'item.{NS}.{item}')}")
        lines.append(f"    tooltip: {yaml_str(f'tooltip.{NS}.{item}')}")
        if item == "tanning_frame":
            continue
        lines.append(f"    max-stack: {16 if item == 'leather_grip' else 64}")
    (RES / "items.yml").parent.mkdir(parents=True, exist_ok=True)
    (RES / "items.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


RECIPES = f"""# Rezepte fuer KaleidoscopeTanning (generiert von tools/gen_tanning.py)
# Wird nach plugins/KaleidoscopeTanning/recipes.yml kopiert; KaleidoscopeCookery mischt sie in seine Rezepte.
chopping-board:
  "{NS}:chopping_board/scraped_hide":
    ingredient: "{NS}:raw_hide"
    cuts: {SCRAPE_CUTS}
    model: "{NS}:raw_hide"
    results:
      - "{NS}:scraped_hide"
      - "{NS}:tallow"
vanilla:
  "{NS}:tanning_frame":
    type: crafting_shaped
    pattern:
      - "SSS"
      - "T T"
      - "SSS"
    key:
      S: "minecraft:stick"
      T: "minecraft:string"
    result: "{NS}:tanning_frame"
  "{NS}:raw_hide_from_rabbit_hide":
    type: crafting_shapeless
    ingredients:
      - "minecraft:rabbit_hide"
      - "minecraft:rabbit_hide"
      - "minecraft:rabbit_hide"
      - "minecraft:rabbit_hide"
    result: "{NS}:raw_hide"
  "{NS}:soap":
    type: crafting_shapeless
    ingredients:
      - "{NS}:tallow"
      - "{NS}:tallow"
      - "minecraft:charcoal"
    result:
      item: "{NS}:soap"
      count: 2
  "{NS}:candle_from_tallow":
    type: crafting_shapeless
    ingredients:
      - "{NS}:tallow"
      - "minecraft:string"
    result: "minecraft:candle"
  "{NS}:leather_grip":
    type: crafting_shapeless
    ingredients:
      - "minecraft:leather"
      - "minecraft:leather"
      - "minecraft:string"
    result:
      item: "{NS}:leather_grip"
      count: 2
data-version: 1
"""


def write_tabs():
    items = [k(i) for i in ["tanning_frame", "raw_hide", "scraped_hide", "tallow", "bark", "oak_bark", "leather_grip", "soap"]]
    lines = ["# Generiert von tools/gen_tanning.py", "tabs:", "  tanning:", f"    icon: {yaml_str(k('tanning_frame'))}", "    items:"]
    lines += [f"      - {yaml_str(i)}" for i in items]
    (RES / "generated").mkdir(parents=True, exist_ok=True)
    (RES / "generated" / "creative_tabs.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_texts():
    for locale, index in (("en_us", 0), ("de_de", 1)):
        write_json(OUT / "lang" / f"{locale}.json", {key: value[index] for key, value in sorted(TEXTS.items())})


def pack_meta():
    root = OUT.parent.parent
    write_json(root / "pack.mcmeta", {"pack": {
        "description": [{"text": "Kaleidoscope Tanning", "color": "gold"},
                        {"text": "\nEigene Assets (Vanilla-Sprites umgefärbt)", "color": "gray"}],
        "min_format": 97, "max_format": 97}})
    item_sprites()["fine_leather"].resize((64, 64), Image.NEAREST).save(root / "pack.png")


def main():
    pack_meta()
    write_items()
    (RES / "recipes.yml").write_text(RECIPES, encoding="utf-8")
    write_tabs()
    item_assets()
    frame_models()
    board_models()
    write_texts()
    print(f"Tanning: {len(ITEMS)} Items, {len(TEXTS)} Texte")


if __name__ == "__main__":
    main()
