#!/usr/bin/env python3
"""
Erzeugt aus den Mod-Quellen die Daten des Plugins:

  src/main/resources/items.yml     alle Items (Modell, Sprach-Key, Stapelgroesse, Nahrungswerte)
  src/main/resources/recipes.yml   Rezepte aller Stationen (fuer Admins anpassbar, wird ins
                                   Plugin-Verzeichnis kopiert)
  resourcepack/custom/assets/<ns>/lang/de_de.json   deutsche Namen (tools/lang/de_de_*.json)

Vanilla-"1x-9x"-Duplikatrezepte des Woks werden zu einem Rezept mit "per-item: true" zusammengefasst.

Aufruf: python3 -I tools/gen_data.py   (vorher tools/import_assets.py)
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / ".cache" / "kaleidoscope-pack"
COOKERY = CACHE / "cookery"
GRILLING = CACHE / "grilling"
IMPORTED = ROOT / "resourcepack" / "imported" / "assets"
CUSTOM = ROOT / "resourcepack" / "custom" / "assets"
RESOURCES = ROOT / "src" / "main" / "resources"
COOKERY_JAVA = COOKERY / "src/main/java/com/github/ysbbbbbb/kaleidoscopecookery"

RECIPE_DIRS = [
    COOKERY / "src/generated/resources/data/kaleidoscope_cookery/recipe",
    GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/recipe",
    GRILLING / "common/src/main/resources/data/kaleidoscope_cookery/recipe",
]
TAG_DIRS = [
    COOKERY / "src/generated/resources/data",
    COOKERY / "src/main/resources/data",
    GRILLING / "common/src/main/resources/data",
]

# Vanilla-Effekte in Mojang-Mappings 1.21.1 -> Registry-Key
VANILLA_EFFECTS = {
    "ABSORPTION": "absorption", "BLINDNESS": "blindness", "CONFUSION": "nausea",
    "DAMAGE_RESISTANCE": "resistance", "DIG_SLOWDOWN": "mining_fatigue", "DIG_SPEED": "haste",
    "FIRE_RESISTANCE": "fire_resistance", "JUMP": "jump_boost", "LUCK": "luck",
    "MOVEMENT_SPEED": "speed", "POISON": "poison", "REGENERATION": "regeneration",
    "UNLUCK": "unluck", "WATER_BREATHING": "water_breathing", "HUNGER": "hunger",
    "NIGHT_VISION": "night_vision", "SATURATION": "saturation", "SLOW_FALLING": "slow_falling",
    "HEAL": "instant_health", "WEAKNESS": "weakness", "MOVEMENT_SLOWDOWN": "slowness",
    "DAMAGE_BOOST": "strength", "INVISIBILITY": "invisibility", "GLOWING": "glowing",
    "LEVITATION": "levitation", "WITHER": "wither", "HERO_OF_THE_VILLAGE": "hero_of_the_village",
}


# ---------------------------------------------------------------- YAML-Ausgabe

def yaml_scalar(value):
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value)
    text = str(value)
    if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_./-]*", text) and text not in ("true", "false", "null", "yes", "no", "on", "off"):
        return text
    return json.dumps(text, ensure_ascii=False)


def to_yaml(value, indent=0) -> str:
    pad = "  " * indent
    if isinstance(value, dict):
        if not value:
            return "{}"
        lines = []
        for key, item in value.items():
            if isinstance(item, (dict, list)) and item:
                lines.append(f"{pad}{yaml_scalar(key)}:\n{to_yaml(item, indent + 1)}")
            else:
                lines.append(f"{pad}{yaml_scalar(key)}: {to_yaml(item, 0) if isinstance(item, (dict, list)) else yaml_scalar(item)}")
        return "\n".join(lines)
    if isinstance(value, list):
        if not value:
            return "[]"
        if all(not isinstance(v, (dict, list)) for v in value):
            return pad + "- " + ("\n" + pad + "- ").join(yaml_scalar(v) for v in value) if indent else \
                "[" + ", ".join(yaml_scalar(v) for v in value) + "]"
        lines = []
        for item in value:
            body = to_yaml(item, indent + 1)
            lines.append(f"{pad}- {body.lstrip()}")
        return "\n".join(lines)
    return yaml_scalar(value)


def read_json(path: Path):
    with open(path, encoding="utf-8-sig") as f:
        return json.load(f)


# ---------------------------------------------------------------- Nahrung (Cookery)

def eval_int(expression: str) -> int:
    if not re.fullmatch(r"[0-9 *+()-]+", expression):
        raise ValueError(expression)
    return int(eval(expression))  # nur Ziffern und Operatoren, siehe Pruefung


def parse_cookery_foods() -> dict:
    source = (COOKERY_JAVA / "init/ModFoods.java").read_text(encoding="utf-8")
    foods = {}
    for match in re.finditer(r"FoodProperties\s+(\w+)\s*=\s*\(?new FoodProperties\.Builder\(\)\)?(.*?)\.build\(\);", source, re.S):
        name, body = match.group(1), match.group(2)
        food = {}
        if m := re.search(r"\.nutrition\((\d+)\)", body):
            food["nutrition"] = int(m.group(1))
        if m := re.search(r"\.saturationModifier\(([\d.]+)[fF]?\)", body):
            # Minecraft: Saettigung = Naehrwert * Modifier * 2
            food["saturation"] = round(food.get("nutrition", 0) * float(m.group(1)) * 2, 2)
        if ".alwaysEdible()" in body:
            food["always-edible"] = True
        if ".fast()" in body:
            food["eat-seconds"] = 0.8
        effects = []
        for e in re.finditer(r"MobEffectInstance\(\s*([\w.]+)\s*,\s*([^,)]+?)\s*(?:,\s*(\d+))?\s*\)\s*,\s*([\d.]+)F?\s*\)", body):
            effect_name = e.group(1).split(".")[-1]
            key = "minecraft:" + VANILLA_EFFECTS[effect_name] if effect_name in VANILLA_EFFECTS else "kaleidoscope_cookery:" + effect_name.lower()
            effect = {"effect": key, "duration": eval_int(e.group(2))}
            if e.group(3) and int(e.group(3)) > 0:
                effect["amplifier"] = int(e.group(3))
            if float(e.group(4)) < 1:
                effect["probability"] = float(e.group(4))
            effects.append(effect)
        if effects:
            food["effects"] = effects
        foods[name] = food
    return foods


def parse_cookery_item_foods(foods: dict) -> dict:
    result = {}
    for file in ("init/ModItems.java", "init/ModBlocks.java"):
        source = (COOKERY_JAVA / file).read_text(encoding="utf-8")
        for match in re.finditer(r'register\("(\w+)"(.*?)\);\s*$', source, re.M):
            item_id, rest = match.group(1), match.group(2)
            if m := re.search(r"ModFoods\.(\w+)", rest):
                food = dict(foods.get(m.group(1), {}))
                if "BowlFoodOnlyItem" in rest or "BowlFood" in rest:
                    food["remainder"] = "minecraft:bowl"
                result.setdefault(item_id, food)
    return result


# ---------------------------------------------------------------- Teller-Gerichte

VANILLA_ITEM_CONSTANTS = {"FLOWER_POT": "minecraft:flower_pot"}


def java_item(name: str) -> str:
    return VANILLA_ITEM_CONSTANTS.get(name, "minecraft:" + name.lower())


def parse_food_bites(foods: dict) -> dict:
    source = (COOKERY_JAVA / "init/registry/FoodBiteRegistry.java").read_text(encoding="utf-8")
    blockstates = COOKERY / "src/generated/resources/assets/kaleidoscope_cookery/blockstates"
    bites = {}
    for match in re.finditer(r'registerFoodData\("(\w+)",\s*FoodData\s*\.(create|createOneByTwo)\((\d+),\s*(\w+),\s*(\w+)\)(.*?)\);', source, re.S):
        name, kind, max_bites, block_food, item_food, rest = match.groups()
        loot = ["minecraft:bowl"]
        if m := re.search(r"setLootItem\(Items\.(\w+)\)", rest):
            loot = [java_item(m.group(1))]
        for m in re.finditer(r"addLootItems\(([^)]*)\)", rest):
            loot += [java_item(x.strip().split(".")[-1]) for x in m.group(1).split(",") if x.strip()]
        entry = {"max-bites": int(max_bites), "food": foods.get(block_food, {}), "item-food": foods.get(item_food, {}), "loot": loot}
        if kind == "createOneByTwo":
            entry["size"] = "1x2"
        state_file = blockstates / f"{name}.json"
        if state_file.exists():
            models = {}
            for key, variant in read_json(state_file)["variants"].items():
                props = dict(p.split("=") for p in key.split(","))
                if props.get("facing") != "north" or props.get("quality", "0") != "0":
                    continue
                part = {"0": "left", "1": "right"}.get(props.get("position"), "main")
                models.setdefault(part, {})[int(props["bites"])] = variant["model"]
            entry["models"] = {part: [stages[i] for i in sorted(stages)] for part, stages in models.items()}
        bites[f"kaleidoscope_cookery:{name}"] = entry
    return bites


# ---------------------------------------------------------------- Tee

def parse_teacups() -> dict:
    """TeacupRegistry: Tee-Getränke mit Effekten (Trinken 32 Ticks, leere Tasse bleibt)."""
    source = (COOKERY_JAVA / "init/registry/TeacupRegistry.java").read_text(encoding="utf-8")
    teas = {}
    for match in re.finditer(r'registerTeacupData\("(\w+)",\s*TeacupData\.create\((\d+)\)(.*?)\);', source, re.S):
        name, _, body = match.groups()
        effects = []
        for e in re.finditer(r"new MobEffectInstance\(\s*([\w.]+)\s*,\s*([^,)]+?)\s*(?:,\s*(\d+))?\s*\)", body):
            effect_name = e.group(1).split(".")[-1]
            key = "minecraft:" + VANILLA_EFFECTS[effect_name] if effect_name in VANILLA_EFFECTS else "kaleidoscope_cookery:" + effect_name.lower()
            effect = {"effect": key, "duration": eval_int(e.group(2))}
            if e.group(3) and int(e.group(3)) > 0:
                effect["amplifier"] = int(e.group(3))
            effects.append(effect)
        teas[f"kaleidoscope_cookery:{name}"] = {"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True,
                                                "eat-seconds": 1.6, "remainder": "kaleidoscope_cookery:empty_cup", "effects": effects}
    return teas


# ---------------------------------------------------------------- Items

# Items mit Vanilla-Funktion: Basis-Material statt Papier
BASE_MATERIALS = {
    "kaleidoscope_cookery:kitchen_shovel": "IRON_SHOVEL",
    # Suppentopfdeckel: im Mod ein Schild (ShieldItem, 120 Haltbarkeit)
    "kaleidoscope_cookery:stockpot_lid": "SHIELD",
    # Küchenmesser: Verbundblock (im Survival nicht erhältlich, ohne OP nicht platzierbar, keine Rechtsklick-Funktion)
    # mit allen Komponenten des passenden Schwerts. Das Plugin trägt ihn in #minecraft:swords ein (wie die Messer im Mod).
    "kaleidoscope_cookery:iron_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:gold_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:diamond_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:netherite_kitchen_knife": "JIGSAW",
}
MAX_DAMAGE = {"kaleidoscope_cookery:stockpot_lid": 120}
# Kleidung (ArmorMaterial FARMER im Mod): Basis Lederrüstung (Verzauberungen), Rüstungswerte wie im Mod, ohne Haltbarkeit.
# Strohhüte ohne Equipment-Asset: der Client zeigt dann das 3D-Itemmodell auf dem Kopf.
WEARABLES = {
    "kaleidoscope_cookery:straw_hat": ("LEATHER_HELMET", "head", None, 3),
    "kaleidoscope_cookery:straw_hat_flower": ("LEATHER_HELMET", "head", None, 3),
    "kaleidoscope_cookery:farmer_chest_plate": ("LEATHER_CHESTPLATE", "chest", "kaleidoscope_cookery:cookery_farmer", 4),
    "kaleidoscope_cookery:farmer_leggings": ("LEATHER_LEGGINGS", "legs", "kaleidoscope_cookery:cookery_farmer", 5),
    "kaleidoscope_cookery:farmer_boots": ("LEATHER_BOOTS", "feet", "kaleidoscope_cookery:cookery_farmer", 2),
}
PROTOTYPES = {
    "kaleidoscope_cookery:iron_kitchen_knife": "IRON_SWORD",
    "kaleidoscope_cookery:gold_kitchen_knife": "GOLDEN_SWORD",
    "kaleidoscope_cookery:diamond_kitchen_knife": "DIAMOND_SWORD",
    "kaleidoscope_cookery:netherite_kitchen_knife": "NETHERITE_SWORD",
}


def parse_stack_sizes() -> dict:
    sizes = {}
    source = (COOKERY_JAVA / "init/ModItems.java").read_text(encoding="utf-8")
    for match in re.finditer(r'register\("(\w+)"(.*?)\);\s*$', source, re.M):
        if m := re.search(r"stacksTo\((\d+)\)", match.group(2)):
            sizes["kaleidoscope_cookery:" + match.group(1)] = int(m.group(1))
    return sizes


def collect_items(foods: dict, bites: dict) -> dict:
    items = {}
    teas = parse_teacups()
    stack_sizes = parse_stack_sizes()
    for ns in ("kaleidoscope_cookery", "kaleidoscope_grilling"):
        lang = read_json(IMPORTED / ns / "lang" / "en_us.json")
        for key in lang:
            parts = key.split(".")
            if len(parts) != 3 or parts[0] not in ("item", "block") or parts[1] != ns:
                continue
            item_id = parts[2]
            if not (IMPORTED / ns / "items" / f"{item_id}.json").exists():
                continue  # reine Bloecke ohne Item (Pflanzen-Stufen, ...)
            full_id = f"{ns}:{item_id}"
            if full_id in items and parts[0] == "block":
                continue
            entry = {"model": full_id, "name": key}
            tooltip_key = f"tooltip.{ns}.{item_id}"
            if tooltip_key in lang:
                entry["tooltip"] = tooltip_key
            if full_id in BASE_MATERIALS:
                entry["base"] = BASE_MATERIALS[full_id]
                if full_id in PROTOTYPES:
                    entry["prototype"] = PROTOTYPES[full_id]
                if full_id in MAX_DAMAGE:
                    entry["max-damage"] = MAX_DAMAGE[full_id]
            elif full_id in stack_sizes:
                entry["max-stack"] = stack_sizes[full_id]
            if ns == "kaleidoscope_cookery" and item_id in foods:
                entry["food"] = foods[item_id]
            if full_id in WEARABLES:
                base, slot, asset, armor = WEARABLES[full_id]
                entry["base"] = base
                entry["prototype"] = base
                entry["equippable"] = {"slot": slot, "asset": asset} if asset else {"slot": slot}
                entry["armor"] = armor
                entry["unbreakable"] = True
                entry.pop("max-stack", None)
            if full_id == "kaleidoscope_cookery:oil_pot":
                entry["max-stack"] = 1  # OilPotItem: stacksTo(1)
            if full_id == "kaleidoscope_cookery:teapot":
                entry["sneak-place"] = True  # TeapotItem#useOn: nur beim Schleichen platzieren
                entry["max-stack"] = 1
            if full_id in teas:
                entry["food"] = teas[full_id]
                entry["max-stack"] = 16
                entry["sneak-place"] = True
            if full_id in bites:
                bite = dict(bites[full_id])
                entry["food"] = bite.pop("item-food")
                entry["plate"] = bite
            items[full_id] = entry
    for full_id, name_key in EXTRA_ITEMS.items():
        items.setdefault(full_id, {"model": full_id, "name": name_key})
    return dict(sorted(items.items()))


# ---------------------------------------------------------------- Tags + Rezepte

def load_tags() -> dict:
    tags = {}
    for base in TAG_DIRS:
        for ns_dir in base.iterdir():
            for folder in ("tags/item", "tags/items"):
                tag_root = ns_dir / folder
                if not tag_root.exists():
                    continue
                for file in tag_root.rglob("*.json"):
                    name = f"{ns_dir.name}:{file.relative_to(tag_root).with_suffix('').as_posix()}"
                    values = tags.setdefault(name, [])
                    for value in read_json(file).get("values", []):
                        if isinstance(value, dict):
                            if value.get("required", True) is False:
                                value = value["id"]
                                if not value.startswith("#") and not value.startswith(("minecraft:", "kaleidoscope_")):
                                    continue  # optionale Eintraege anderer Mods
                            else:
                                value = value["id"]
                        if value not in values:
                            values.append(value)
    return tags


def resolve_tag(name: str, tags: dict, seen=None) -> list:
    seen = seen or set()
    if name in seen:
        return []
    seen.add(name)
    result = []
    for value in tags.get(name, []):
        if value.startswith("#"):
            result += resolve_tag(value[1:], tags, seen)
        elif value.startswith(("minecraft:", "kaleidoscope_cookery:", "kaleidoscope_grilling:")):
            result.append(value)
    return list(dict.fromkeys(result))


# In 26.x umbenannte Vanilla-Items
RENAMED_ITEMS = {"minecraft:chain": "minecraft:iron_chain"}
# Items ohne eigenen Namen in der Sprachdatei (Item-ID -> Sprach-Key)
EXTRA_ITEMS = {"kaleidoscope_cookery:recipe_item": "block.kaleidoscope_cookery.recipe_block"}


def vanilla_item_ids() -> set:
    import zipfile
    jar = CACHE / "client-26.3.jar"
    with zipfile.ZipFile(jar) as zf:
        return {"minecraft:" + n[len("assets/minecraft/items/"):-5] for n in zf.namelist()
                if n.startswith("assets/minecraft/items/") and n.endswith(".json")}


VANILLA_ITEMS = vanilla_item_ids()


def item_id(value: str) -> str:
    value = RENAMED_ITEMS.get(value, value)
    if value.startswith("minecraft:") and value not in VANILLA_ITEMS:
        print(f"WARNUNG: {value} gibt es in 26.3 nicht")
    return value


def ingredient(value, used_tags: set) -> str | list:
    if isinstance(value, list):
        options = [ingredient(v, used_tags) for v in value]
        return options[0] if len(options) == 1 else options
    if "tag" in value:
        used_tags.add(value["tag"])
        return "#" + value["tag"]
    return item_id(value["item"])


def result_list(recipe: dict) -> list:
    results = recipe.get("results") or recipe.get("result")
    if isinstance(results, dict):
        results = [results]
    out = []
    for r in results:
        count = r.get("count", 1)
        rid = item_id(r["id"])
        out.append(rid if count == 1 else {"item": rid, "count": count})
    return out


def collect_recipes(tags: dict) -> dict:
    used_tags = set()
    chopping = {}
    for base in RECIPE_DIRS:
        folder = base / "chopping_board"
        if not folder.exists():
            continue
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            ns = base.parent.name
            recipe_id = f"{ns}:chopping_board/{file.stem}"
            # Grilling ersetzt einzelne Cookery-Rezepte (gleicher Pfad im Cookery-Namespace)
            chopping[recipe_id] = {
                "ingredient": ingredient(recipe["ingredient"], used_tags),
                "cuts": recipe.get("cut_count", 4),
                "model": recipe["model_id"],
                "results": result_list(recipe),
            }
    pot, flex_pot = collect_pot_recipes(used_tags)
    stockpot, flex_stockpot = collect_stockpot_recipes(used_tags)
    teapot = {}
    for ns, folder in pot_dirs("teapot"):
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            entry = {"ingredient": ingredient(recipe["ingredient"], used_tags), "tea-fluid": recipe.get("tea_fluid", "minecraft:empty")}
            if recipe.get("ingredient_count", 12) != 1:
                entry["ingredient-count"] = recipe.get("ingredient_count", 12)
            entry["time"] = recipe.get("time", 2400)
            entry["result"] = result_list({"result": recipe["result"]})[0]
            teapot[f"{ns}:teapot/{file.stem}"] = entry
    steamer = {}
    for ns, folder in pot_dirs("steamer"):
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            entry = {"ingredient": ingredient(recipe["ingredient"], used_tags)}
            if recipe.get("cook_tick", 1200) != 1200:
                entry["time"] = recipe["cook_tick"]
            entry["result"] = result_list({"result": recipe["result"]})[0]
            steamer[f"{ns}:steamer/{file.stem}"] = entry
    millstone = {}
    for ns, folder in pot_dirs("millstone"):
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            results = recipe.get("results") or recipe.get("result")
            if isinstance(results, dict):
                results = [results]
            out = []
            for r in results:
                rid = item_id(r["id"])
                count = r.get("count", 1)
                chance = r.get("chance", 1.0)
                if count == 1 and chance >= 1:
                    out.append(rid)
                else:
                    entry = {"item": rid, "count": count}
                    if chance < 1:
                        entry["chance"] = chance
                    out.append(entry)
            millstone[f"{ns}:millstone/{file.stem}"] = {"ingredient": ingredient(recipe["ingredient"], used_tags), "results": out}
    bamboo_tray = {}
    for ns, folder in pot_dirs("bamboo_tray"):
        for file in sorted(folder.glob("*/*.json")):
            recipe = read_json(file)
            bamboo_tray[f"{ns}:bamboo_tray/{file.parent.name}/{file.stem}"] = {
                "type": recipe.get("subtype", "drying"),
                "ingredient": ingredient(recipe["ingredient"], used_tags),
                "time": recipe.get("duration", 1800),
                "result": result_list({"result": recipe["result"]})[0]}
    vanilla = collect_vanilla_recipes(used_tags)
    tag_groups = {}
    for name in sorted(used_tags):
        if name.startswith("minecraft:"):
            continue  # Vanilla-Tags löst das Plugin zur Laufzeit über den Server auf
        values = resolve_tag(name, tags) or COMMON_TAGS.get(name, [])
        if not values:
            print(f"WARNUNG: Tag #{name} lässt sich nicht auflösen")
        tag_groups[name] = values
    return {"data-version": DATA_VERSION, "tags": tag_groups, "chopping-board": chopping, "pot": pot, "flex-pot": flex_pot,
            "stockpot": stockpot, "flex-stockpot": flex_stockpot, "steamer": steamer, "teapot": teapot,
            "millstone": millstone, "bamboo-tray": bamboo_tray, "vanilla": vanilla}


# Bei jeder inhaltlichen Aenderung der erzeugten recipes.yml erhoehen: das Plugin ersetzt dann alte Kopien (mit Backup)
DATA_VERSION = 6
STOCKPOT_DEFAULT_TIME = 300
POT_DEFAULT_TIME = 200
POT_DEFAULT_STIR_FRY = 3


def pot_dirs(kind: str):
    for base in RECIPE_DIRS:
        folder = base / kind
        if folder.exists():
            yield base.parent.name, folder


def collect_pot_recipes(used_tags: set):
    pot = {}
    groups = {}
    for ns, folder in pot_dirs("pot"):
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            entry = pot_entry(recipe, used_tags)
            m = re.fullmatch(r"(.+)_([1-9])", file.stem)
            ingredients = recipe["ingredients"]
            if m and len(ingredients) == int(m.group(2)) and all(i == ingredients[0] for i in ingredients) \
                    and recipe["result"].get("count", 1) == int(m.group(2)):
                groups.setdefault(f"{ns}:pot/{m.group(1)}", []).append((int(m.group(2)), entry))
                continue
            pot[f"{ns}:pot/{file.stem}"] = entry
    # Vanilla-"1x-9x"-Rezepte: ein Rezept, das mit 1-9 gleichen Zutaten funktioniert
    for recipe_id, variants in groups.items():
        if sorted(n for n, _ in variants) != list(range(1, 10)):
            for n, entry in variants:
                pot[f"{recipe_id}_{n}"] = entry
            continue
        entry = dict(next(e for n, e in variants if n == 1))
        entry["per-item"] = True
        pot[recipe_id] = entry
    flex = {}
    for ns, folder in pot_dirs("flex_pot"):
        for file in sorted(folder.glob("*.json")):
            flex[f"{ns}:flex_pot/{file.stem}"] = pot_entry(read_json(file), used_tags)
    return dict(sorted(pot.items())), dict(sorted(flex.items()))


def collect_stockpot_recipes(used_tags: set):
    result = {}
    for kind in ("stockpot", "flex_stockpot"):
        recipes = {}
        for ns, folder in pot_dirs(kind):
            for file in sorted(folder.glob("*.json")):
                recipe = read_json(file)
                entry = {"ingredients": [ingredient(i, used_tags) for i in recipe["ingredients"]]}
                if recipe.get("soup_base", "minecraft:water") != "minecraft:water":
                    entry["soup-base"] = recipe["soup_base"]
                if recipe.get("time", STOCKPOT_DEFAULT_TIME) != STOCKPOT_DEFAULT_TIME:
                    entry["time"] = recipe["time"]
                # Ohne Angabe: Schüssel; leere Liste: kein Gefäß nötig
                if "carrier" in recipe:
                    carrier = recipe["carrier"]
                    entry["carrier"] = "none" if carrier == [] else ingredient(carrier, used_tags)
                for key, out in (("cooking_texture", "cooking-texture"), ("finished_texture", "finished-texture"),
                                 ("cooking_bubble_color", "cooking-color"), ("finished_bubble_color", "finished-color")):
                    if key in recipe:
                        entry[out] = recipe[key]
                entry["result"] = result_list({"result": recipe["result"]})[0]
                recipes[f"{ns}:{kind}/{file.stem}"] = entry
        result[kind] = dict(sorted(recipes.items()))
    return result["stockpot"], result["flex_stockpot"]


def pot_entry(recipe: dict, used_tags: set) -> dict:
    entry = {"ingredients": [ingredient(i, used_tags) for i in recipe["ingredients"]]}
    if recipe.get("time", POT_DEFAULT_TIME) != POT_DEFAULT_TIME:
        entry["time"] = recipe["time"]
    if recipe.get("stir_fry_count", POT_DEFAULT_STIR_FRY) != POT_DEFAULT_STIR_FRY:
        entry["stir-fry"] = recipe["stir_fry_count"]
    if recipe.get("carrier"):
        entry["carrier"] = ingredient(recipe["carrier"], used_tags)
    entry["result"] = result_list({"result": recipe["result"]})[0]
    return entry


# NeoForge-Common-Tags, die nicht in den Mod-Daten stehen
COMMON_TAGS = {
    "c:gravels": ["minecraft:gravel"],
    "c:sands/colorless": ["minecraft:sand"],
    "c:sands/red": ["minecraft:red_sand"],
    "c:ingots/iron": ["minecraft:iron_ingot"],
    "c:ingots/gold": ["minecraft:gold_ingot"],
    "c:ingots/copper": ["minecraft:copper_ingot"],
    "c:ingots/netherite": ["minecraft:netherite_ingot"],
    "c:gems/diamond": ["minecraft:diamond"],
    "c:gems/emerald": ["minecraft:emerald"],
    "c:nuggets/iron": ["minecraft:iron_nugget"],
    "c:nuggets/gold": ["minecraft:gold_nugget"],
    "c:rods/wooden": ["minecraft:stick"],
    "c:strings": ["minecraft:string"],
    "c:leathers": ["minecraft:leather"],
    "c:eggs": ["minecraft:egg"],
    "c:slime_balls": ["minecraft:slime_ball"],
    "c:bones": ["minecraft:bone"],
    "c:dusts/redstone": ["minecraft:redstone"],
    "c:dusts/glowstone": ["minecraft:glowstone_dust"],
    "c:gunpowders": ["minecraft:gunpowder"],
    "c:ender_pearls": ["minecraft:ender_pearl"],
    "c:feathers": ["minecraft:feather"],
    "c:crops/wheat": ["minecraft:wheat"],
    "c:crops/carrot": ["minecraft:carrot"],
    "c:crops/potato": ["minecraft:potato"],
    "c:crops/beetroot": ["minecraft:beetroot"],
    "c:seeds/wheat": ["minecraft:wheat_seeds"],
    "c:foods/milk": ["minecraft:milk_bucket"],
    "c:buckets/milk": ["minecraft:milk_bucket"],
    "c:buckets/water": ["minecraft:water_bucket"],
    "c:buckets/empty": ["minecraft:bucket"],
    "c:cobblestones": ["minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:mossy_cobblestone"],
    "c:stones": ["minecraft:stone", "minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:deepslate", "minecraft:tuff"],
    "c:glass_blocks": ["minecraft:glass"],
    "c:glass_blocks/colorless": ["minecraft:glass"],
    "c:glass_panes/colorless": ["minecraft:glass_pane"],
    "c:chests/wooden": ["minecraft:chest", "minecraft:trapped_chest"],
    "c:bricks/normal": ["minecraft:brick"],
    "c:dyes/red": ["minecraft:red_dye"],
    "c:dyes/yellow": ["minecraft:yellow_dye"],
    "c:dyes/white": ["minecraft:white_dye"],
    "c:dyes/black": ["minecraft:black_dye"],
    "c:dyes/green": ["minecraft:green_dye"],
    "c:storage_blocks/iron": ["minecraft:iron_block"],
    "c:storage_blocks/wheat": ["minecraft:hay_block"],
    "c:fences/wooden": ["#minecraft:wooden_fences"],
    "c:mushrooms": ["minecraft:brown_mushroom", "minecraft:red_mushroom"],
    "c:foods/raw_beef": ["minecraft:beef"],
    "c:foods/raw_pork": ["minecraft:porkchop"],
    "c:foods/raw_chicken": ["minecraft:chicken"],
    "c:foods/raw_mutton": ["minecraft:mutton"],
    "c:foods/raw_fish": ["minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish", "minecraft:pufferfish"],
    "c:foods/cooked_beef": ["minecraft:cooked_beef"],
    "c:foods/bread": ["minecraft:bread"],
    "c:foods/berry": ["minecraft:sweet_berries", "minecraft:glow_berries"],
    "c:foods/fruit": ["minecraft:apple", "minecraft:melon_slice", "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:chorus_fruit"],
    "c:foods/vegetable": ["minecraft:carrot", "minecraft:potato", "minecraft:beetroot"],
    "c:crops": ["minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot"],
    "c:seeds": ["minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:melon_seeds", "minecraft:pumpkin_seeds", "minecraft:torchflower_seeds", "minecraft:pitcher_pod"],
}

VANILLA_RECIPE_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting", "minecraft:smoking",
                        "minecraft:campfire_cooking", "minecraft:blasting", "minecraft:smithing_transform", "minecraft:stonecutting"}
VANILLA_RECIPE_DIRS = [
    (COOKERY / "src/generated/resources/data/kaleidoscope_cookery/recipe", "kaleidoscope_cookery"),
    (COOKERY / "src/main/resources/data/kaleidoscope_cookery/recipe", "kaleidoscope_cookery"),
    (COOKERY / "src/generated/resources/data/minecraft/recipe", "kaleidoscope_cookery"),
    (GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/recipe", "kaleidoscope_grilling"),
]


def collect_vanilla_recipes(used_tags: set) -> dict:
    recipes = {}
    for folder, namespace in VANILLA_RECIPE_DIRS:
        if not folder.exists():
            continue
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            kind = recipe.get("type")
            if kind not in VANILLA_RECIPE_TYPES:
                continue
            if "components" in recipe.get("result", {}):
                continue  # z. B. vorgefüllter Dampfkorb: Inhalt als Block-Entity-Daten gibt es hier nicht
            entry = {"type": kind.split(":", 1)[1]}
            if kind == "minecraft:crafting_shaped":
                entry["pattern"] = recipe["pattern"]
                entry["key"] = {k: ingredient(v, used_tags) for k, v in recipe["key"].items()}
            elif kind == "minecraft:crafting_shapeless":
                entry["ingredients"] = [ingredient(v, used_tags) for v in recipe["ingredients"]]
            elif kind == "minecraft:smithing_transform":
                entry["template"] = ingredient(recipe["template"], used_tags)
                entry["base"] = ingredient(recipe["base"], used_tags)
                entry["addition"] = ingredient(recipe["addition"], used_tags)
            else:
                entry["ingredient"] = ingredient(recipe["ingredient"], used_tags)
                if "cookingtime" in recipe:
                    entry["time"] = recipe["cookingtime"]
                if "experience" in recipe:
                    entry["experience"] = recipe["experience"]
            result = recipe["result"]
            result["id"] = item_id(result["id"])
            entry["result"] = result["id"] if result.get("count", 1) == 1 else {"item": result["id"], "count": result["count"]}
            recipes[f"{namespace}:{file.stem}"] = entry
    return recipes


# ---------------------------------------------------------------- Sprachdateien

def write_german_lang():
    names = read_json(ROOT / "tools" / "lang" / "de_de_names.json")
    extra_path = ROOT / "tools" / "lang" / "de_de_extra.json"
    extra = read_json(extra_path) if extra_path.exists() else {}
    for ns in ("kaleidoscope_cookery", "kaleidoscope_grilling"):
        english = read_json(IMPORTED / ns / "lang" / "en_us.json")
        german = {}
        missing = []
        for key in english:
            parts = key.split(".")
            if len(parts) == 3 and parts[0] in ("item", "block") and parts[1] == ns:
                if parts[2] in names:
                    german[key] = names[parts[2]]
                else:
                    missing.append(key)
        german.update(extra.get(ns, {}))
        out = CUSTOM / ns / "lang" / "de_de.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(dict(sorted(german.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        if missing:
            print(f"Ohne deutsche Übersetzung ({ns}): {', '.join(missing)}")


def main():
    raw_foods = parse_cookery_foods()
    foods = parse_cookery_item_foods(raw_foods)
    items = collect_items(foods, parse_food_bites(raw_foods))
    header = "# Generiert von tools/gen_data.py aus den Mod-Quellen - nicht von Hand bearbeiten.\n"
    (RESOURCES / "items.yml").write_text(header + to_yaml({"items": items}) + "\n", encoding="utf-8")

    recipes = collect_recipes(load_tags())
    recipe_header = ("# Rezepte der Kochstationen, erzeugt aus den Mod-Daten (tools/gen_data.py).\n"
                     "# Diese Datei wird beim ersten Start nach plugins/KaleidoscopeCookery/recipes.yml kopiert\n"
                     "# und kann dort angepasst werden (/kc reload).\n"
                     "# Zutaten: Item-ID, \"#tag\" (siehe tags; minecraft:-Tags kommen vom Server) oder Liste von Alternativen.\n")
    (RESOURCES / "recipes.yml").write_text(recipe_header + to_yaml(recipes) + "\n", encoding="utf-8")
    write_german_lang()
    print(f"Items: {len(items)}, Teller: {sum(1 for i in items.values() if 'plate' in i)}, Wok: {len(recipes['pot'])}+{len(recipes['flex-pot'])} flex, Suppentopf: {len(recipes['stockpot'])}+{len(recipes['flex-stockpot'])} flex, Schneidebrett-Rezepte: {len(recipes['chopping-board'])}, Vanilla-Rezepte: {len(recipes['vanilla'])}, Tags: {len(recipes['tags'])}")


if __name__ == "__main__":
    main()
