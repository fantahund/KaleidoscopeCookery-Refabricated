#!/usr/bin/env python3
"""
Data for the KaleidoscopeTavern plugin (repo KaleidoscopeTavern next to this one) from the mod Kaleidoscope Tavern
(Fabric fork, branch 26.3-fabric, cloned into the cache by tools/import_assets.py --project tavern):

- src/main/resources/items.yml        all items the plugin uses (no pure decoration blocks)
- src/main/resources/recipes.yml      crafting recipes (merged into Cookery's vanilla recipes)
- src/main/resources/generated/tavern.yml
                                      barrel, pressing tub and shaker recipes, drink effects per brew level,
                                      cocktail colours and rack block lists
- src/main/resources/generated/creative_tabs.yml
- resourcepack/custom/...             barrel models (rebuilt from the mod's BarrelModel), fluid surfaces, effect icon
                                      font, plugin texts (en_us + German, merged with the German mod translation)

Usage: python3 -I tools/gen_tavern.py
"""
import json
import math
import sys
import yaml
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_entity_models as entity  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TAVERN = ROOT.parent / "KaleidoscopeTavern"
MOD = Path.home() / ".cache" / "kaleidoscope-pack" / "tavern" / "src" / "main" / "resources"
DATA = MOD / "data" / "kaleidoscope_tavern"
NS = "kaleidoscope_tavern"
OUT = TAVERN / "resourcepack" / "custom" / "assets" / NS
RES = TAVERN / "src" / "main" / "resources"

LANG = json.loads((MOD / "assets" / NS / "lang" / "en_us.json").read_text(encoding="utf-8"))


def k(value: str) -> str:
    return f"{NS}:{value}"


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


# Own additions without a name in the mod (washing up): item -> (English, German)
OWN_ITEMS = {
    "dirty_glassware": ("Dirty Glassware", "Dreckiges Glas"),
    "dish_sponge": ("Dish Sponge", "Spülschwamm"),
    "sink": ("Sink", "Spülbecken"),
    "mulled_wine": ("Mulled Wine", "Glühwein"),
    "wineskin": ("Wineskin", "Weinschlauch"),
    "full_wineskin": ("Wineskin", "Weinschlauch"),
}

# Broths for Cookery's stockpot (registered by the plugin): item -> surface texture
STOCKPOT_BASES = {
    "grape_bucket": "block/grape_juice_still",
    "ice_grape_bucket": "block/ice_grape_juice_still",
    "gold_grape_bucket": "block/gold_grape_juice_still",
    "green_grape_bucket": "block/green_grape_juice_still",
    "sweet_berries_bucket": "block/sweet_berries_juice_still",
    "glow_berries_bucket": "block/glow_berries_juice_still",
    "wine": "block/grape_juice_still",
}


def name_key(item: str) -> str:
    if item in OWN_ITEMS:
        return f"item.{NS}.{item}"
    for prefix in ("item", "block"):
        if f"{prefix}.{NS}.{item}" in LANG:
            return f"{prefix}.{NS}.{item}"
    raise SystemExit(f"Kein Name für {item}")


def short(value: str) -> str:
    """minecraft:apple -> minecraft:apple, #c:x -> #c:x (ingredient strings as in Cookery's recipes.yml)"""
    return value


def yaml_str(value) -> str:
    return json.dumps(value, ensure_ascii=False)


# ---- recipes and data from the mod ----

def barrel_recipes():
    recipes = []
    for file in sorted((DATA / "recipe" / "barrel").glob("*.json")):
        r = read(file)
        recipes.append({"id": k(file.stem), "fluid": r["fluid"], "carrier": r["carrier"],
                        "ingredients": [i if isinstance(i, str) else i.get("item", i.get("tag")) for i in r.get("ingredients", [])],
                        "result": r["result"]["id"], "unit-time": r.get("unit_time", 2400)})
    return recipes


def pressing_recipes():
    recipes = []
    for file in sorted((DATA / "recipe" / "pressing_tub").glob("*.json")):
        r = read(file)
        ingredient = r["ingredient"]
        if ingredient == "#c:fruits/grapes":
            ingredient = k("grape")
        recipes.append({"id": k(file.stem), "ingredient": ingredient, "fluid": r["fluid"], "amount": r.get("fluid_amount", 125)})
    return recipes


COLOURS = ["black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
           "blue", "green", "aqua", "red", "light_purple", "yellow", "white"]


def colour_tags():
    tags = {}
    for colour in COLOURS:
        file = DATA / "tags" / "item" / f"cocktail_ingredient_{colour}.json"
        values = read(file)["values"] if file.exists() else []
        tags[colour] = [v if isinstance(v, str) else v["id"] for v in values]
    return tags


def shaker_recipes(tags):
    recipes = []
    for file in sorted((DATA / "recipe" / "shaker").glob("*.json")):
        r = read(file)
        slots = []
        for ingredient in r["ingredients"]:
            if ingredient.startswith(f"#{NS}:cocktail_ingredient_"):
                slots.append(ingredient[len(f"#{NS}:cocktail_ingredient_"):])
            else:
                raise SystemExit(f"Unbekannte Shaker-Zutat {ingredient}")
        recipes.append({"id": k(file.stem), "colours": slots, "result": r["result"]["id"]})
    return recipes


def drink_effects():
    effects = {}
    for file in sorted((DATA / "datamap" / "drink_effect").glob("*.json")):
        d = read(file)
        effects[d["item"]] = d["effects"]
    return effects


def tag_values(name):
    return [v if isinstance(v, str) else v["id"] for v in read(DATA / "tags" / "item" / f"{name}.json")["values"]]


# ---- items ----

def items(barrel, cocktails):
    result = {}

    def add(item, **props):
        entry = {"model": k(item), "name": name_key(item)}
        entry.update({key: value for key, value in props.items() if value is not None})
        result[k(item)] = entry

    # Plants and fruit
    add("grapevine", tooltip=f"tooltip.{NS}.grapevine.2")
    add("trellis", tooltip=f"tooltip.{NS}.trellis.2")
    for grape in ("grape", "ice_grape", "gold_grape", "green_grape"):
        add(grape, tooltip=f"tooltip.{NS}.{grape}", food={"nutrition": 2, "saturation": 2.0, "always-edible": True})
    # Juice buckets: drinking removes all effects (like milk), bucket stays
    for juice in ("grape", "ice_grape", "gold_grape", "green_grape", "sweet_berries", "glow_berries"):
        add(f"{juice}_bucket", tooltip=f"tooltip.{NS}.{juice}_bucket", **{"max-stack": 16},
            food={"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True, "remainder": "minecraft:bucket"})
    # Bottles and drinks (brew level in the PDC, effects from the plugin)
    add("empty_bottle")
    # Watermelon juice: only from a tap on a melon, no effects
    drinks = sorted({r["result"] for r in barrel if r["result"] != k("molotov")} | {k("vinegar"), k("watermelon_juice")})
    for drink in drinks:
        add(drink.split(":")[1], **{"max-stack": 16},
            food={"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True, "remainder": k("empty_bottle")})
    # Molotov: thrown like a snowball
    add("molotov", base="SNOWBALL", **{"max-stack": 16})
    # Mixology
    # Shaker: long "use" while shaking, the plugin evaluates the release time
    add("shaker", **{"max-stack": 1, "use-animation": "drink", "sneak-place": True})
    add("empty_glassware", **{"max-stack": 16})
    for cocktail in cocktails:
        add(cocktail.split(":")[1], **{"max-stack": 16},
            food={"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True, "remainder": k("dirty_glassware")})
    # Washing up (own addition): drunk cocktails leave a dirty glass, washed in the sink with a sponge
    add("dirty_glassware", tooltip=f"tooltip.{NS}.dirty_glassware", **{"max-stack": 16})
    add("dish_sponge", tooltip=f"tooltip.{NS}.dish_sponge", **{"max-stack": 1, "max-damage": 384})
    add("sink", tooltip=f"tooltip.{NS}.sink")
    # Wineskin (own addition): 4 portions of one drink; filled by the plugin (Wineskins), a sip leaves the rest
    add("wineskin", tooltip=f"tooltip.{NS}.wineskin", **{"max-stack": 16})
    add("full_wineskin", **{"max-stack": 1},
        food={"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True})
    # Mulled wine: stockpot drink on a wine broth (own addition, values by level in Cookery's cooking-skill.dishes)
    add("mulled_wine", tooltip=f"tooltip.{NS}.mulled_wine", **{"max-stack": 16},
        food={"nutrition": 10, "saturation": 14.0, "always-edible": True, "drink": True, "remainder": "kaleidoscope_cookery:empty_cup",
              "effects": [{"effect": "kaleidoscope_cookery:warmth", "duration": 6000}, {"effect": "minecraft:regeneration", "duration": 400},
                          {"effect": "minecraft:resistance", "duration": 1200}]})
    # Stations, racks and cabinets
    for station in ("pressing_tub", "barrel", "tap", "bar_cabinet", "glass_bar_cabinet", "cellar_cabinet",
                    "tilted_rack", "circular_rack", "holder", "glassware_holder"):
        add(station)
    return result, drinks


def write_items(entries):
    lines = ["# Generiert von tools/gen_tavern.py", "items:"]
    for item, props in entries.items():
        lines.append(f"  {yaml_str(item)}:")
        for key, value in props.items():
            if isinstance(value, dict):
                lines.append(f"    {key}:")
                for sub, subvalue in value.items():
                    lines.append(f"      {sub}: {yaml_str(subvalue)}")
            else:
                lines.append(f"    {key}: {yaml_str(value)}")
    (RES / "items.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


# ---- crafting recipes ----

TAGS = {
    "c:ingots/iron": ["minecraft:iron_ingot"],
    "c:nuggets/iron": ["minecraft:iron_nugget"],
    "c:nuggets/gold": ["minecraft:gold_nugget"],
    "c:glass_panes": ["minecraft:glass_pane"] + [f"minecraft:{c}_stained_glass_pane" for c in
                      ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                       "cyan", "purple", "blue", "brown", "green", "red", "black")],
}
CRAFTING = ["barrel", "pressing_tub", "tap", "shaker", "empty_bottle", "empty_glassware", "trellis", "bar_cabinet",
            "glass_bar_cabinet", "cellar_cabinet", "circular_rack", "tilted_rack", "holder", "glassware_holder"]


# Wok dishes that need a drink (by result; every pot and flex-pot recipe of the dish gets the drink)
WOK_DRINKS = {
    "sweet_and_sour_pork": "vinegar",
    "fish_flavored_shredded_pork": "vinegar",
    "braised_beef": "wine",
    "braised_pork_ribs": "wine",
    "dongpo_pork": "wine",
    "braised_fish": "riesling_dry_white",
    "candied_potato": "rum",
}


def write_recipes():
    lines = ["# Rezepte fuer KaleidoscopeTavern (generiert von tools/gen_tavern.py)", "tags:"]
    for tag, values in TAGS.items():
        lines.append(f"  {yaml_str(tag)}:")
        lines += [f"    - {yaml_str(v)}" for v in values]
    lines.append("vanilla:")
    for name in CRAFTING:
        r = read(DATA / "recipe" / f"{name}.json")
        lines.append(f"  {yaml_str(k(name))}:")
        kind = r["type"].split(":")[1]
        lines.append(f"    type: {kind}")
        if kind == "crafting_shaped":
            lines.append("    pattern:")
            lines += [f"      - {yaml_str(row)}" for row in r["pattern"]]
            lines.append("    key:")
            for symbol, value in r["key"].items():
                lines.append(f"      {symbol}: {yaml_str(value if isinstance(value, str) else value.get('item', '#' + value.get('tag', '')))}")
        else:
            lines.append("    ingredients:")
            lines += [f"      - {yaml_str(v if isinstance(v, str) else v.get('item'))}" for v in r["ingredients"]]
        count = r["result"].get("count", 1)
        if count == 1:
            lines.append(f"    result: {yaml_str(r['result']['id'])}")
        else:
            lines.append("    result:")
            lines.append(f"      item: {yaml_str(r['result']['id'])}")
            lines.append(f"      count: {count}")
    # Own recipes (washing up)
    lines += [
        f"  {yaml_str(k('sink'))}:",
        "    type: crafting_shaped",
        "    pattern:",
        '      - "SCS"',
        '      - "PPP"',
        "    key:",
        '      S: "minecraft:smooth_stone_slab"',
        '      C: "minecraft:cauldron"',
        '      P: "#minecraft:planks"',
        f"    result: {yaml_str(k('sink'))}",
        f"  {yaml_str(k('wineskin'))}:",
        "    type: crafting_shaped",
        "    pattern:",
        '      - " S "',
        '      - "L L"',
        '      - " L "',
        "    key:",
        '      S: "minecraft:string"',
        '      L: "minecraft:leather"',
        f"    result: {yaml_str(k('wineskin'))}",
        f"  {yaml_str(k('dish_sponge'))}:",
        "    type: crafting_shapeless",
        "    ingredients:",
        '      - "minecraft:yellow_wool"',
        '      - "minecraft:string"',
        "    result:",
        f"      item: {yaml_str(k('dish_sponge'))}",
        "      count: 2",
    ]
    # Wok dishes that need a drink (overrides the Cookery recipe of the same id, the empty bottle comes back)
    cookery = yaml.safe_load((ROOT / "core" / "src" / "main" / "resources" / "recipes.yml").read_text(encoding="utf-8"))
    for section in ("pot", "flex-pot"):
        lines.append(f"{section}:")
        for recipe, r in cookery[section].items():
            res = r["result"]
            drink = WOK_DRINKS.get((res if isinstance(res, str) else res["item"]).split(":")[1])
            if drink is None:
                continue
            assert len(r["ingredients"]) < 9, recipe
            lines.append(f"  {yaml_str(recipe)}:")
            lines.append("    ingredients:")
            lines += [f"      - {yaml_str(v)}" for v in r["ingredients"] + [k(drink)]]
            for key in ("time", "stir-fry", "per-item"):
                if key in r:
                    lines.append(f"    {key}: {str(r[key]).lower()}")
            if "carrier" in r:
                lines.append(f"    carrier: {yaml_str(r['carrier'])}")
            if isinstance(res, str):
                lines.append(f"    result: {yaml_str(res)}")
            else:
                lines.append("    result:")
                lines += [f"      {key}: {yaml_str(v) if isinstance(v, str) else v}" for key, v in res.items()]
    # Stockpot dishes on a juice or wine broth
    lines += [
        "stockpot:",
        f"  {yaml_str(k('stockpot/fruit_compote'))}:",
        "    ingredients:",
        '      - "minecraft:apple"',
        '      - "minecraft:apple"',
        '      - "minecraft:sweet_berries"',
        '      - "minecraft:sweet_berries"',
        '      - "minecraft:sweet_berries"',
        f"    soup-base: {yaml_str(k('grape_bucket'))}",
        '    finished-texture: "kaleidoscope_cookery:stockpot/default_finished_berry"',
        '    result: "kaleidoscope_cookery:fruit_compote"',
        f"  {yaml_str(k('stockpot/mulled_wine'))}:",
        "    ingredients:",
        '      - "minecraft:sugar"',
        '      - "minecraft:sugar"',
        '      - "minecraft:glow_berries"',
        '      - "minecraft:glow_berries"',
        '      - "minecraft:glow_berries"',
        f"    soup-base: {yaml_str(k('wine'))}",
        '    finished-texture: "kaleidoscope_cookery:stockpot/default_finished_red"',
        '    carrier: "kaleidoscope_cookery:empty_cup"',
        f"    result: {yaml_str(k('mulled_wine'))}",
    ]
    lines.append("data-version: 6")
    (RES / "recipes.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_tavern_data(barrel, pressing, shaker, effects, tags):
    data = {
        "barrel": barrel,
        "pressing-tub": pressing,
        "shaker": shaker,
        "colours": {colour: values for colour, values in tags.items() if values},
        "effects": {item: [[{"effect": e["effect"], "duration": e.get("duration", 0), "amplifier": e.get("amplifier", 0),
                              "probability": e.get("probability", 1.0)} for e in level] for level in levels]
                    for item, levels in effects.items()},
        "blocklists": {name: tag_values(name) for name in ("bar_cabinet_irregular", "cellar_cabinet_blocklist",
                                                           "holder_blocklist", "tilted_rack_blocklist")},
    }
    out = RES / "generated" / "tavern.yml"
    out.parent.mkdir(parents=True, exist_ok=True)
    # JSON is valid YAML
    out.write_text("# Generiert von tools/gen_tavern.py\n" + json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def write_tabs(entries):
    lines = ["# Generiert von tools/gen_tavern.py", "tabs:", "  tavern:", f'    icon: "{k("barrel")}"', "    items:"]
    lines += [f"      - {yaml_str(item)}" for item in entries]
    (RES / "generated" / "creative_tabs.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


# ---- models ----

def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def model_item(name: str, tints=None):
    model = {"type": "minecraft:model", "model": k(name)}
    if tints:
        model["tints"] = tints
    write_json(OUT / "items" / f"{name}.json", {"model": model})


# BarrelModel (cubes as in createBodyLayer; root offset (8, 9, -9)). The open lid is approximated by one tilted box.
BARREL_BODY = [
    ("body", (8, 9, -9), (0, 0, 0), [
        entity.cube((28, 136), (6, 7, -10), (4, 8, 4)),
        entity.cube((28, 136), (-26, 7, -10), (4, 8, 4)),
        entity.cube((174, 118), (-22, 7, -8), (28, 4, 2)),
        entity.cube((174, 118), (-22, 7, 22), (28, 4, 2)),
        entity.cube((28, 136), (6, 7, 22), (4, 8, 4)),
        entity.cube((28, 136), (-26, 7, 22), (4, 8, 4)),
        entity.cube((0, 0), (-28, -33, -15), (40, 40, 48)),
        entity.cube((0, 160), (-16, -33, 1), (16, 21, 0)),
        entity.cube((0, 144), (0, -33, 1), (0, 24, 16)),
        entity.cube((0, 144), (-16, -33, 1), (0, 21, 16)),
        entity.cube((32, 160), (-16, -12, 1), (16, 0, 16)),
        entity.cube((0, 160), (-16, -33, 17), (16, 21, 0)),
    ], []),
]
BARREL_LID_CLOSED = [("close", (8, 9, -9), (0, 0, 0), [entity.cube((102, 113), (-16, -33, 1), (16, 2, 16))], [])]
# Open: the same lid swung up around its back edge (hinge at z = 17 relative to the root, top of the barrel)
BARREL_LID_OPEN = [("open", (8 - 8, 9 - 33, -9 + 17), (75, 0, 0), [entity.cube((102, 113), (-8, -2, -16), (16, 2, 16))], [])]


def barrel_models():
    texture_src = MOD / "assets" / NS / "textures" / "entity" / "brew" / "barrel.png"
    (OUT / "textures" / "block" / "brew").mkdir(parents=True, exist_ok=True)
    (OUT / "textures" / "block" / "brew" / "barrel_entity.png").write_bytes(texture_src.read_bytes())
    texture = k("block/brew/barrel_entity")
    for name, parts in (("barrel_body", BARREL_BODY), ("barrel_lid_closed", BARREL_LID_CLOSED), ("barrel_lid_open", BARREL_LID_OPEN)):
        cubes = []
        entity.collect(parts, (0, 0, 0), [], cubes)
        # Scaled down to 1/4 around the block centre (the barrel is 3 blocks large), factor 4 in the display
        elements = [entity.element(pivot, rotation, c, entity.BLOCK_CENTER, 4, 256) for _, pivot, rotation, c in cubes]
        for element in elements:
            # Flat inner walls/floor: the entity model draws only one side, the other UV is transparent. As item model
            # the transparent side faces into the opening, so both sides get the visible UV.
            if any(element["from"][axis] == element["to"][axis] for axis in range(3)):
                first = next(iter(element["faces"].values()))
                for face in element["faces"]:
                    element["faces"][face] = dict(first)
            # The entity model's Y axis points down: the open lid must swing up, not into the barrel
            if name == "barrel_lid_open" and "rotation" in element:
                element["rotation"]["x"] = -element["rotation"]["x"]
        write_json(OUT / "models" / "block" / "brew" / f"{name}.json",
                   {"textures": {"texture": texture, "particle": texture}, "elements": elements})
        model_item(f"block/brew/{name}")


FLUIDS = {
    # fluid id -> (texture, tint or None)
    "minecraft:water": ("minecraft:block/water_still", -12618012),
    "minecraft:lava": ("minecraft:block/lava_still", None),
    **{f"{NS}:{j}": (f"{NS}:block/{j}_still", None) for j in
       ("grape_juice", "ice_grape_juice", "gold_grape_juice", "green_grape_juice", "sweet_berries_juice", "glow_berries_juice")},
}


def fluid_models():
    """A flat 16x16 fluid surface per fluid (barrel and pressing tub show their liquid with it)."""
    for fluid, (texture, tint) in FLUIDS.items():
        name = "fluid/" + fluid.split(":")[1]
        face = {"uv": [0, 0, 16, 16], "texture": "#fluid"}
        if tint is not None:
            face["tintindex"] = 0
        write_json(OUT / "models" / "block" / f"{name}.json", {
            "textures": {"fluid": texture, "particle": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 0.01, 16], "faces": {"up": face, "down": dict(face)}}],
        })
        model_item(f"block/{name}", [{"type": "minecraft:constant", "value": tint}] if tint is not None else None)


def stockpot_surface_models():
    """Soup surfaces of the juice and wine broths, shaped like Cookery's kaleidoscope:surface models."""
    for item, texture in STOCKPOT_BASES.items():
        name = f"block/stockpot_surface/{item}"
        face = {"uv": [0, 0, 10, 10], "texture": "#surface"}
        write_json(OUT / "models" / f"{name}.json", {
            "textures": {"surface": k(texture), "particle": k(texture)},
            "elements": [{"from": [3, 0, 3], "to": [13, 0, 13], "shade": False, "faces": {"up": face}}],
        })
        model_item(name)




WS_LEATHER = [(78, 44, 22), (108, 64, 32), (140, 88, 46), (170, 114, 64), (200, 146, 90), (226, 180, 124)]
WS_OUTLINE = (44, 24, 12)
WS_SEAM = (238, 214, 160)
WS_RED, WS_WS_RED_D = (184, 40, 34), (116, 22, 20)
WS_HORN, WS_WS_HORN_H = (44, 36, 30), (98, 86, 72)
WS_CORD, WS_WS_CORD_D = (214, 190, 132), (146, 118, 70)


def wineskin_sprite(full: bool):
    """A bota bag (Spanish wineskin): pear-shaped leather pouch with a round belly and a short neck, red stitched rim,
    horn spout with a red collar and a carrying strap; shaded with light from the top left. The empty one is slimmer
    with a crease.

    Looks like the classic goatskin bota (pear shape, spout at the narrow tip)."""
    from PIL import Image
    bottom = (6.9, 10.4)
    neck = (10.0, 3.9)
    R = 5.1 if full else 4.7
    ax, ay = neck[0] - bottom[0], neck[1] - bottom[1]
    length = math.hypot(ax, ay)
    ux, uy = ax / length, ay / length

    def radius(t):
        # pear: full round belly, quickly narrowing to a slim neck
        rn = 0.9
        return rn + (R - rn) * max(0.0, 1 - min(1.0, t) ** 1.7) ** (0.55 if full else 0.75)

    light = (-0.6, -0.55, 0.58)
    ln = math.sqrt(sum(c * c for c in light)); light = tuple(c / ln for c in light)
    px, depth = {}, {}
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - bottom[0], y + 0.5 - bottom[1]
            t = (dx * ux + dy * uy) / length          # 0 = belly centre, 1 = neck
            side = dx * -uy + dy * ux                  # distance from the axis
            rr = radius(max(0.0, t)) if t >= 0 else math.sqrt(max(0.0, R * R - (t * length) ** 2))
            if t > 1.0 or rr <= 0 or abs(side) > rr:
                continue
            s = side / rr
            along = 0 if t >= 0 else t * length / R
            z = math.sqrt(max(0.0, 1 - s * s - along * along))
            nx = s * -uy + along * ux; ny = s * ux + along * uy
            n = (nx, ny, z * (1.2 if full else 0.75)); nl = math.sqrt(sum(c * c for c in n)) or 1
            i = max(0.0, sum(n[k] / nl * light[k] for k in range(3)))
            px[(x, y)] = WS_LEATHER[min(len(WS_LEATHER) - 1, int(i * 0.9 * len(WS_LEATHER)))]
            depth[(x, y)] = t
    if not full:
        # slack skin: a crease across the belly
        for x, y in [(5, 9), (6, 10), (7, 10), (8, 11)]:
            if (x, y) in px:
                px[(x, y)] = WS_LEATHER[1]
    inside = set(px)
    edge = {q for q in inside if any((q[0] + a, q[1] + b) not in inside for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    # stitched seam one pixel inside the outline, all around the belly
    seam = {q for q in inside - edge if any((q[0] + a, q[1] + b) in edge for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            and depth[q] < 0.72}
    for q in seam:
        if (q[0] + q[1]) % 2 == 0:
            px[q] = WS_RED
    for q in edge:
        px[q] = WS_OUTLINE
    # red collar, horn spout with a light tip
    for q, c in {(10, 3): WS_WS_RED_D, (11, 3): WS_RED, (10, 2): WS_RED, (11, 2): WS_WS_RED_D,
                 (11, 1): WS_HORN, (12, 1): WS_WS_HORN_H, (12, 2): WS_HORN}.items():
        px[q] = c
    # carrying cord: from the collar out to the left and down along the belly
    # carrying strap: tied at the collar, running up out of the picture
    cord = [(9, 2), (8, 1), (7, 0)]
    for k, q in enumerate(cord):
        if q not in inside:
            px[q] = WS_CORD if k % 2 == 0 else WS_WS_CORD_D
    img = Image.new("RGBA", (16, 16))
    for (x, y), c in px.items():
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), c + (255,))
    return img


def wineskin_textures():
    for item, full in (("wineskin", False), ("full_wineskin", True)):
        (OUT / "textures" / "item").mkdir(parents=True, exist_ok=True)
        wineskin_sprite(full).save(OUT / "textures" / "item" / f"{item}.png")
        write_json(OUT / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": k(f"item/{item}")}})
        write_json(OUT / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": k(f"item/{item}")}})


def mulled_wine_texture():
    """Mulled wine: Cookery's barley tea cup with red wine instead of tea."""
    from PIL import Image
    wine = Image.open(ROOT / "resourcepack" / "imported" / "assets" / "kaleidoscope_cookery" / "textures" / "item" / "barley_tea.png").convert("RGBA")
    tea = {(137, 133, 41): (96, 12, 30), (167, 170, 61): (138, 22, 44), (226, 223, 104): (196, 70, 70)}
    for y in range(16):
        for x in range(16):
            c = wine.getpixel((x, y))
            if c[3] and c[:3] in tea:
                wine.putpixel((x, y), tea[c[:3]] + (c[3],))
    (OUT / "textures" / "item").mkdir(parents=True, exist_ok=True)
    wine.save(OUT / "textures" / "item" / "mulled_wine.png")
    write_json(OUT / "models" / "item" / "mulled_wine.json", {"parent": "minecraft:item/generated", "textures": {"layer0": k("item/mulled_wine")}})
    write_json(OUT / "items" / "mulled_wine.json", {"model": {"type": "minecraft:model", "model": k("item/mulled_wine")}})


def young_vine_models():
    """A young vine (stages 0-2) inside a connected trellis: the bare trellis of that shape plus only the leaf planes of
    the single trellis stage, in one model (two overlapping displays flicker, and the stage's own post would lie flat
    on the trellis beams)."""
    imported = TAVERN / "resourcepack" / "imported" / "assets" / NS / "models" / "block" / "plant"
    shapes = ("east_west", "north_south", "cross_east_west", "cross_north_south", "cross_up_down", "six_direction")
    for kind in ("grapevine_trellis", "ice_grapevine_trellis", "gold_grapevine_trellis"):
        for stage in range(3):
            leaves = [e for e in read(imported / kind / f"single_stage{stage}.json")["elements"] if "rotation" in e and e["rotation"].get("angle")]
            for shape in shapes:
                trellis = read(imported / "trellis" / f"{shape}.json")
                elements = []
                for e in trellis["elements"]:
                    elements.append({**e, "faces": {f: {**v, "texture": "#trellis"} for f, v in e["faces"].items()}})
                for e in leaves:
                    elements.append({**e, "faces": {f: {**v, "texture": "#vine"} for f, v in e["faces"].items()}})
                name = f"block/plant/{kind}/{shape}_stage{stage}"
                model = {"textures": {"trellis": k("block/plant/trellis"), "vine": k(f"block/plant/{kind}"),
                                      "particle": k("block/plant/trellis")}, "elements": elements}
                write_json(OUT / "models" / f"{name}.json", model)
                model_item(name)


def washing_models():
    """Own assets for washing up: sink (vanilla textures), dish sponge and the dirty glass (smudged glassware)."""
    from PIL import Image, ImageDraw
    import random

    rng = random.Random(7)

    def smudge(src: Path, dst: Path, share: float):
        """Brownish residue on a share of the visible pixels, a bit stronger towards the bottom of the glass"""
        image = Image.open(src).convert("RGBA")
        w, h = image.size
        for y in range(h):
            for x in range(w):
                r, g, b, a = image.getpixel((x, y))
                if a == 0 or rng.random() >= share * (0.6 + 0.8 * y / h):
                    continue
                dirt = rng.choice(((112, 84, 52), (96, 78, 50), (150, 128, 84)))
                t = rng.uniform(0.45, 0.75)
                image.putpixel((x, y), (int(r + (dirt[0] - r) * t), int(g + (dirt[1] - g) * t), int(b + (dirt[2] - b) * t), max(a, 170)))
        dst.parent.mkdir(parents=True, exist_ok=True)
        image.save(dst)

    textures = MOD / "assets" / NS / "textures"
    smudge(textures / "block" / "mixology" / "empty_glassware.png", OUT / "textures" / "block" / "mixology" / "dirty_glassware.png", 0.3)
    smudge(textures / "item" / "empty_glassware.png", OUT / "textures" / "item" / "dirty_glassware.png", 0.35)
    write_json(OUT / "models" / "block" / "mixology" / "dirty_glassware.json", {
        "parent": k("block/mixology/empty_glassware"),
        "textures": {"2": k("block/mixology/dirty_glassware"), "particle": "minecraft:block/glass"},
    })
    model_item("block/mixology/dirty_glassware")
    write_json(OUT / "models" / "item" / "dirty_glassware.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": k("item/dirty_glassware")}})
    write_json(OUT / "items" / "dirty_glassware.json", {"model": {"type": "minecraft:model", "model": k("item/dirty_glassware")}})

    def box(frm, to, textures_by_face):
        return {"from": frm, "to": to, "faces": {face: {"texture": texture} for face, texture in textures_by_face.items()}}

    all6 = ("north", "south", "east", "west", "up", "down")
    deco = OUT / "textures" / "block" / "deco"
    deco.mkdir(parents=True, exist_ok=True)
    sink_textures()
    def faces(default, **special):
        return {f: special.get(f, default) for f in all6}

    # Sink: cabinet in the dark red wood of the mod's cabinets, stainless steel counter around the basin (front = north,
    # like blockstates). No faucet: the mod's tap goes on the wall above it
    sink = {
        "parent": "minecraft:block/block",
        "textures": {"side": k("block/deco/sink_side"), "front": k("block/deco/sink_front"), "steel": k("block/deco/sink_steel"),
                     "basin": k("block/deco/sink_basin"), "floor": k("block/deco/sink_basin_floor"),
                     "particle": k("block/deco/sink_steel")},
        "elements": [
            box([0, 0, 0], [16, 10, 16], faces("#side", north="#front", up="#floor")),
            box([0, 10, 0], [16, 14, 3], faces("#steel", south="#basin")),
            box([0, 10, 13], [16, 14, 16], faces("#steel", north="#basin")),
            box([0, 10, 3], [3, 14, 13], faces("#steel", east="#basin")),
            box([13, 10, 3], [16, 14, 13], faces("#steel", west="#basin")),
            # door handles
            box([6.5, 5, -0.5], [7, 8, 0], faces("#steel")),
            box([9, 5, -0.5], [9.5, 8, 0], faces("#steel")),
        ],
    }
    write_json(OUT / "models" / "block" / "deco" / "sink.json", sink)
    model_item("block/deco/sink")
    write_json(OUT / "items" / "sink.json", {"model": {"type": "minecraft:model", "model": k("block/deco/sink")}})

    # Dish sponge: yellow sponge with a green scouring layer. Block model stands on y = 0 (sink rim), the item model
    # is the same centred in the block so the icon sits in the middle of the slot
    sponge_textures = {"sponge": k("block/deco/dish_sponge"), "scrub": k("block/deco/dish_sponge_scrub"),
                       "particle": k("block/deco/dish_sponge")}

    def sponge_elements(y):
        return [box([5, y, 6.5], [11, y + 2, 9.5], faces("#sponge")),
                box([5, y + 2, 6.5], [11, y + 2.75, 9.5], faces("#scrub"))]

    write_json(OUT / "models" / "block" / "deco" / "dish_sponge.json", {"textures": sponge_textures, "elements": sponge_elements(0)})
    model_item("block/deco/dish_sponge")
    write_json(OUT / "models" / "item" / "dish_sponge.json", {
        "textures": sponge_textures,
        "elements": sponge_elements(6.625),
        "display": {
            "gui": {"rotation": [30, 225, 0], "scale": [1.7, 1.7, 1.7]},
            "ground": {"scale": [0.8, 0.8, 0.8]},
            "fixed": {"rotation": [90, 0, 0], "scale": [1.5, 1.5, 1.5]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [1, 1, 1]},
            "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.7, 0.7, 0.7]},
        },
    })
    write_json(OUT / "items" / "dish_sponge.json", {"model": {"type": "minecraft:model", "model": k("item/dish_sponge")}})


def sink_textures():
    """Own 16x16 textures: dark red wood panels (palette of the mod's bar cabinet), brushed stainless steel, sponge."""
    from PIL import Image
    import random

    rng = random.Random(11)
    out = OUT / "textures" / "block" / "deco"
    wood_dark, wood_mid, wood_base, wood_light, wood_highlight = (46, 29, 29), (65, 44, 44), (58, 37, 37), (78, 54, 54), (93, 66, 66)

    def wood(image, x0, y0, x1, y1):
        # panel: light frame, darker inner board with vertical grain
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                if edge:
                    colour = wood_light if (x == x0 or y == y0) else wood_dark
                else:
                    colour = wood_base if (x * 7 + rng.randrange(3)) % 5 else wood_mid
                image.putpixel((x, y), colour + (255,))

    side = Image.new("RGBA", (16, 16))
    wood(side, 0, 0, 16, 16)
    wood(side, 2, 2, 14, 14)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        side.putpixel((x, y), wood_highlight + (255,))
    side.save(out / "sink_side.png")
    # front: two doors, upper 6 px of the model are the top rail (the cabinet is 10 px high, UV from y 6)
    front = Image.new("RGBA", (16, 16))
    wood(front, 0, 0, 16, 16)
    wood(front, 1, 7, 8, 15)
    wood(front, 8, 7, 15, 15)
    for x in range(16):
        front.putpixel((x, 6), wood_dark + (255,))
    front.save(out / "sink_front.png")

    def steel(path, base, drain=False):
        image = Image.new("RGBA", (16, 16))
        for y in range(16):
            streak = rng.randint(-6, 6)
            for x in range(16):
                v = base + streak + rng.randint(-4, 4)
                if y == 0 or x == 0:
                    v += 28
                elif y == 15 or x == 15:
                    v -= 30
                image.putpixel((x, y), (v - 4, v, v + 6, 255))
        if drain:
            for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
                image.putpixel((x, y), (55, 58, 64, 255))
            for x, y in ((6, 7), (6, 8), (9, 7), (9, 8), (7, 6), (8, 6), (7, 9), (8, 9)):
                image.putpixel((x, y), (115, 120, 128, 255))
        image.save(path)

    steel(out / "sink_steel.png", 186)
    steel(out / "sink_basin.png", 150)
    steel(out / "sink_basin_floor.png", 140, drain=True)

    sponge = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            pore = rng.random() < 0.18
            colour = (196, 160, 40) if pore else rng.choice(((236, 204, 72), (228, 194, 60), (242, 214, 92)))
            sponge.putpixel((x, y), colour + (255,))
    sponge.save(out / "dish_sponge.png")
    scrub = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            scrub.putpixel((x, y), rng.choice(((46, 128, 62), (58, 148, 72), (36, 104, 50))) + (255,))
    scrub.save(out / "dish_sponge_scrub.png")

EFFECTS = ["slightly_tipsy", "high_heels", "grass_stealth", "vision", "bloody_mary", "ardent_heat", "long_reach",
           "tomb_raider", "xp_drain", "upside_down", "zenith", "shriek_attack"]


def effect_font():
    providers = [{"type": "bitmap", "file": f"{NS}:mob_effect/{effect}.png", "ascent": 8, "height": 9,
                  "chars": [chr(0xE100 + i)]} for i, effect in enumerate(EFFECTS)]
    write_json(OUT / "font" / "effects.json", {"providers": providers})


# ---- texts of the plugin ----

TEXTS = {
    # key: (English, German)
    "kaleidoscope.menu.tab.tavern": ("Tavern", "Taverne"),
    f"{NS}.cookbook.barrel": ("Barrel", "Fass"),
    f"{NS}.cookbook.pressing_tub": ("Pressing Tub", "Kelterbottich"),
    f"{NS}.cookbook.shaker": ("Shaker", "Shaker"),
    f"{NS}.cookbook.barrel.hint1": ("Open the lid, fill in 4 buckets of fluid, add 16 of each ingredient, close the lid",
                                    "Deckel öffnen, 4 Eimer Flüssigkeit einfüllen, je 16 Zutaten dazu, Deckel schließen"),
    f"{NS}.cookbook.barrel.hint2": ("It brews up to level 6 over time (about 30 minutes)",
                                    "Es reift mit der Zeit bis Stufe 6 (etwa 30 Minuten)"),
    f"{NS}.cookbook.barrel.hint3": ("Attach a tap to the front and right-click it with empty bottles",
                                    "Zapfhahn vorne anbringen und mit leeren Flaschen rechtsklicken"),
    f"{NS}.cookbook.pressing_tub.hint1": ("Put fruit in and jump into the tub: 8 fruit = 1 bucket of juice",
                                          "Früchte hineinlegen und in den Bottich springen: 8 Früchte = 1 Eimer Saft"),
    f"{NS}.cookbook.pressing_tub.hint2": ("Take the juice out with an empty bucket", "Den Saft mit einem leeren Eimer herausholen"),
    f"{NS}.cookbook.shaker.hint1": ("Place the shaker and add 3 drinks (at least level Fine)",
                                    "Shaker abstellen und 3 Getränke hineingeben (mindestens Stufe Fein)"),
    f"{NS}.cookbook.shaker.hint2": ("Pick it up, hold right-click and release in the green zone",
                                    "Aufnehmen, Rechtsklick halten und im grünen Bereich loslassen"),
    f"{NS}.cookbook.shaker.hint3": ("Right-click with an empty glass in the inventory to pour",
                                    "Mit einem leeren Glas im Inventar rechtsklicken zum Eingießen"),
    f"{NS}.barrel.status.open": ("Barrel open | %s | Ingredients: %s", "Fass offen | %s | Zutaten: %s"),
    f"{NS}.barrel.status.fluid": ("%s: %s/4 buckets", "%s: %s/4 Eimer"),
    f"{NS}.barrel.status.empty": ("empty", "leer"),
    f"{NS}.barrel.status.none": ("none", "keine"),
    f"{NS}.barrel.need_tap": ("Attach a tap to the front of the barrel first!", "Bringe zuerst vorne einen Zapfhahn am Fass an!"),
    f"{NS}.barrel.tap_hold_bottle": ("Right-click the tap with empty bottles!", "Rechtsklicke den Zapfhahn mit leeren Flaschen!"),
    f"{NS}.barrel.tap_attached": ("Tap attached", "Zapfhahn angebracht"),
    f"{NS}.barrel.lid_closed": ("Lid closed - brewing starts once the barrel is full",
                                "Deckel geschlossen - das Brauen beginnt, sobald das Fass voll ist"),
    f"{NS}.barrel.lid_opened": ("Lid opened", "Deckel geöffnet"),
    # Brewing level (own addition)
    f"skill.{NS}.brewing": ("Brewing", "Brauen"),
    f"{NS}.brewing.required": ("Requires brewing level %s", "Benötigt Brau-Stufe %s"),
    f"{NS}.brewing.too_low": ("Failed: %s needs brewing level %s", "Missglückt: %s braucht Brau-Stufe %s"),
    # Washing up (own addition)
    f"tooltip.{NS}.dirty_glassware": ("Wash it in a sink with a dish sponge", "Im Spülbecken mit einem Spülschwamm abwaschen"),
    f"tooltip.{NS}.dish_sponge": ("Scrub dirty glasses in the sink - in rhythm!", "Schrubbt dreckige Gläser im Spülbecken - im Takt!"),
    f"tooltip.{NS}.sink": ("Fill it with a water bucket or a tap on the wall above, the sponge can rest on the rim",
                           "Mit Wassereimer oder einem Zapfhahn an der Wand darüber füllen, der Schwamm kann auf dem Rand liegen"),
    f"{NS}.sink.need_water": ("The sink needs water - water bucket or a tap above it", "Das Spülbecken braucht Wasser - Wassereimer oder Zapfhahn darüber"),
    f"{NS}.sink.need_sponge": ("Scrub the glass with a dish sponge", "Schrubbe das Glas mit einem Spülschwamm"),
    f"{NS}.sink.need_glass": ("Put a dirty glass into the sink first", "Lege zuerst ein dreckiges Glas ins Becken"),
    f"{NS}.sink.scrubbing": ("Scrubbing", "Schrubben"),
    f"{NS}.sink.too_fast": ("Too hasty!", "Zu hastig!"),
    f"{NS}.sink.too_slow": ("Too slow - keep the rhythm!", "Zu langsam - bleib im Takt!"),
    f"{NS}.sink.clean": ("Sparkling clean!", "Blitzsauber!"),
    f"{NS}.sink.status": ("Sink | Water: %s/%s | %s", "Spülbecken | Wasser: %s/%s | %s"),
    f"{NS}.sink.status.empty": ("empty", "leer"),
    f"{NS}.sink.status.dirty": ("dirty glass", "dreckiges Glas"),
    f"{NS}.sink.status.clean": ("clean glass", "sauberes Glas"),
    f"{NS}.cookbook.sink": ("Washing up", "Abwasch"),
    f"{NS}.cookbook.sink.hint1": ("Fill the sink (water bucket or a tap on the wall above) and put a dirty glass in",
                                   "Spülbecken füllen (Wassereimer oder Zapfhahn an der Wand darüber) und ein dreckiges Glas hineinlegen"),
    f"{NS}.cookbook.sink.hint2": ("Right-click it with the sponge in rhythm: about once per second", "Mit dem Schwamm im Takt rechtsklicken: etwa einmal pro Sekunde"),
    f"{NS}.cookbook.sink.hint3": ("Sneak + right-click with the sponge puts it on the rim", "Schleichen + Rechtsklick mit dem Schwamm legt ihn auf den Rand"),
    f"{NS}.tub.status": ("%s | %s/8 pressed", "%s | %s/8 gepresst"),
    f"{NS}.tub.full": ("Juice ready - take it out with an empty bucket!", "Saft fertig - mit einem leeren Eimer herausholen!"),
    f"{NS}.shaker.status": ("Drinks: %s/3", "Getränke: %s/3"),
    f"{NS}.shaker.ready": ("Ready: %s - right-click with an empty glass in your inventory", "Fertig: %s - Rechtsklick mit leerem Glas im Inventar"),
    f"{NS}.shaker.need_glass": ("You need an empty glass in your inventory!", "Du brauchst ein leeres Cocktailglas im Inventar!"),
    f"{NS}.shaker.shaking": ("Shaking", "Schütteln"),
    f"{NS}.shaker.cleared": ("Shaker emptied", "Shaker geleert"),
    f"{NS}.trellis.waxed": ("Waxed - vines will not grow onto it", "Gewachst - Reben wachsen nicht darauf"),
    f"{NS}.trellis.wrong_ground": ("Grapes need dirt, ice/snow or netherrack/magma below the trellis",
                                   "Trauben brauchen Erde, Eis/Schnee oder Netherrack/Magma unter dem Rankgitter"),
    f"{NS}.rack.empty_bottle_only": ("Only empty glasses can be hung up here!", "Hier können nur leere Gläser aufgehängt werden!"),
    f"tooltip.{NS}.mulled_wine": ("Stockpot: wine with sugar and glow berries, served in a cup", "Suppentopf: Wein mit Zucker und Leuchtbeeren, im Becher serviert"),
    f"tooltip.{NS}.wineskin": ("Drink in the off hand + right-click: pour it in (4 portions). Or fill it at a barrel's tap",
                                "Getränk in die Nebenhand + Rechtsklick: hineingießen (4 Portionen). Oder am Zapfhahn eines Fasses füllen"),
    f"{NS}.wineskin.content": ("%s: %s/%s sips", "%s: %s/%s Schlucke"),
    f"{NS}.wineskin.full": ("The wineskin is full", "Der Weinschlauch ist voll"),
    f"{NS}.wineskin.mismatch": ("Only the same drink of the same brew level fits in", "Nur dasselbe Getränk in derselben Brau-Stufe passt hinein"),
    f"{NS}.pairing.perfect": ("Perfect pairing! Effects last 25% longer", "Perfekte Kombination! Effekte halten 25 % länger"),
    f"{NS}.pairing.goes_with": ("Goes well with: %s", "Passt zu: %s"),
    f"tooltip.{NS}.signature_effects": ("Mixed from your own drinks", "Aus deinen eigenen Getränken gemixt"),
}


def write_texts():
    texts = dict(TEXTS)
    texts.update({f"item.{NS}.{item}": names for item, names in OWN_ITEMS.items()})
    for locale, index in (("en_us", 0), ("de_de", 1)):
        path = OUT / "lang" / f"{locale}.json"
        current = read(path) if path.exists() else {}
        current.update({key: value[index] for key, value in texts.items()})
        write_json(path, current)


def main():
    barrel = barrel_recipes()
    pressing = pressing_recipes()
    tags = colour_tags()
    shaker = shaker_recipes(tags)
    effects = drink_effects()
    cocktails = sorted({r["result"] for r in shaker} | {k("mystery_cocktail"), k("signature_cocktail")})
    entries, drinks = items(barrel, cocktails)
    write_items(entries)
    write_recipes()
    write_tavern_data(barrel, pressing, shaker, effects, tags)
    write_tabs(entries)
    barrel_models()
    washing_models()
    young_vine_models()
    fluid_models()
    stockpot_surface_models()
    mulled_wine_texture()
    wineskin_textures()
    effect_font()
    write_texts()
    print(f"Tavern: {len(entries)} Items, {len(barrel)} Fass-, {len(pressing)} Kelter-, {len(shaker)} Shaker-Rezepte, "
          f"{len(effects)} Getränke-Effekte, {len(drinks)} Getränke")


if __name__ == "__main__":
    main()
