#!/usr/bin/env python3
"""
Creates the plugin data from the mod sources:

  core/src/main/resources/items.yml     all items (model, language key, stack size, food values)
  core/src/main/resources/recipes.yml   recipes of all stations (adjustable by admins, is copied into the
                                   plugin folder)
  resourcepack/custom/assets/<ns>/lang/de_de.json   German names (tools/lang/de_de_*.json)

The wok's vanilla "1x-9x" duplicate recipes are merged into one recipe with "per-item: true".

Usage: python3 -I tools/gen_data.py   (run tools/import_assets.py first)
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / ".cache" / "kaleidoscope-pack"
# The Fabric fork (Refabricated) is the base since 2026-10-10
COOKERY = CACHE / "refab"
GRILLING = CACHE / "grilling"
# Grilling is a separate plugin (repo KaleidoscopeGrilling next to this one); its data and language files go there
GRILLING_ROOT = ROOT.parent / "KaleidoscopeGrilling"


def project_root(ns: str) -> Path:
    return GRILLING_ROOT if ns == "kaleidoscope_grilling" else ROOT


def imported(ns: str) -> Path:
    return project_root(ns) / "resourcepack" / "imported" / "assets" / ns


def custom(ns: str) -> Path:
    return project_root(ns) / "resourcepack" / "custom" / "assets" / ns


RESOURCES = ROOT / "core" / "src" / "main" / "resources"
COOKERY_JAVA = COOKERY / "src/main/java/com/github/ysbbbbbb/kaleidoscopecookery"

RECIPE_DIRS = [
    COOKERY / "src/main/resources/data/kaleidoscope_cookery/recipe",
    GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/recipe",
    GRILLING / "common/src/main/resources/data/kaleidoscope_cookery/recipe",
]
TAG_DIRS = [
    COOKERY / "src/main/resources/data",
    GRILLING / "common/src/main/resources/data",
]

# Vanilla effects in Mojang mappings 1.21.1 -> registry key
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


# ---------------------------------------------------------------- YAML output

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


# ---------------------------------------------------------------- Food (Cookery)

def eval_int(expression: str) -> int:
    if not re.fullmatch(r"[0-9 *+()-]+", expression):
        raise ValueError(expression)
    return int(eval(expression))  # only digits and operators, see the check


def mod_effects() -> set:
    return set(re.findall(r"Holder<MobEffect>\s+(\w+)\s*=", (COOKERY_JAVA / "init/ModEffects.java").read_text(encoding="utf-8")))


MOD_EFFECTS = mod_effects()


def effect_key(name: str) -> str:
    name = name.split(".")[-1]
    if name in MOD_EFFECTS:
        return "kaleidoscope_cookery:" + name.lower()
    return "minecraft:" + VANILLA_EFFECTS.get(name, name.lower())


def parse_effect_instances(body: str) -> list:
    effects = []
    for e in re.finditer(r"MobEffectInstance\(\s*([\w.]+)\s*,\s*([^,)]+?)\s*(?:,\s*(\d+))?\s*\)", body):
        effect = {"effect": effect_key(e.group(1)), "duration": eval_int(e.group(2))}
        if e.group(3) and int(e.group(3)) > 0:
            effect["amplifier"] = int(e.group(3))
        effects.append(effect)
    return effects


def balanced(text: str, start: int) -> str:
    """Contents of the parenthesis that starts at text[start] == '('."""
    depth = 0
    for i in range(start, len(text)):
        depth += {"(": 1, ")": -1}.get(text[i], 0)
        if depth == 0:
            return text[start + 1:i]
    raise ValueError("Klammer nicht geschlossen")


def parse_cookery_foods() -> dict:
    """ModFoods (nutrition, saturation): constant -> values."""
    source = (COOKERY_JAVA / "init/ModFoods.java").read_text(encoding="utf-8")
    foods = {}
    for match in re.finditer(r"FoodProperties\s+(\w+)\s*=\s*\(?new FoodProperties\.Builder\(\)\)?(.*?)\.build\(\);", source, re.S):
        name, body = match.group(1), match.group(2)
        food = {}
        if m := re.search(r"\.nutrition\((\d+)\)", body):
            food["nutrition"] = int(m.group(1))
        if m := re.search(r"\.saturationModifier\(([\d.]+)[fF]?\)", body):
            # Minecraft: saturation = nutrition * modifier * 2
            food["saturation"] = round(food.get("nutrition", 0) * float(m.group(1)) * 2, 2)
        if ".alwaysEdible()" in body:
            food["always-edible"] = True
        foods[name] = food
    return foods


def parse_consumables() -> dict:
    """ModConsumables (26.x): eating time and effects on eating, constant -> values."""
    source = (COOKERY_JAVA / "init/ModConsumables.java").read_text(encoding="utf-8")
    consumables = {}
    for match in re.finditer(r"Consumable\s+(\w+)\s*=\s*(Consumables\.\w+\(\).*?)\.build\(\);", source, re.S):
        name, body = match.group(1), match.group(2)
        entry = {}
        if m := re.search(r"\.consumeSeconds\(([\d.]+)[fF]?\)", body):
            entry["eat-seconds"] = float(m.group(1))
        effects = []
        for m in re.finditer(r"ApplyStatusEffectsConsumeEffect\(", body):
            inner = balanced(body, m.end() - 1)
            probability = re.search(r",\s*([\d.]+)[fF]?\s*$", inner.strip())
            for effect in parse_effect_instances(inner):
                if probability and float(probability.group(1)) < 1:
                    effect["probability"] = float(probability.group(1))
                effects.append(effect)
        if effects:
            entry["effects"] = effects
        consumables[name] = entry
    return consumables


def food_of(foods: dict, consumables: dict, food: str, consumable: str | None) -> dict:
    result = dict(foods.get(food, {}))
    result.update(consumables.get(consumable, {}) if consumable else {})
    return result


def parse_cookery_item_foods(foods: dict) -> dict:
    """ModItems: registerItem("id", ... ModFoods.X, ModConsumables.Y ...)."""
    consumables = parse_consumables()
    result = {}
    source = (COOKERY_JAVA / "init/ModItems.java").read_text(encoding="utf-8")
    for match in re.finditer(r'registerItem\("(\w+)"(.*?)\);\s*$', source, re.M | re.S):
        item_id, rest = match.group(1), match.group(2)
        if m := re.search(r"ModFoods\.(\w+)", rest):
            c = re.search(r"ModConsumables\.(\w+)", rest)
            food = food_of(foods, consumables, m.group(1), c.group(1) if c else None)
            if "BowlFood" in rest:
                food["remainder"] = "minecraft:bowl"
            result.setdefault(item_id, food)
    return result


# ---------------------------------------------------------------- Plated dishes

VANILLA_ITEM_CONSTANTS = {"FLOWER_POT": "minecraft:flower_pot"}


def java_item(name: str) -> str:
    return VANILLA_ITEM_CONSTANTS.get(name, "minecraft:" + name.lower())


def parse_food_bites(foods: dict) -> dict:
    source = (COOKERY_JAVA / "init/registry/FoodBiteRegistry.java").read_text(encoding="utf-8")
    consumables = parse_consumables()
    blockstates = COOKERY / "src/main/resources/assets/kaleidoscope_cookery/blockstates"
    bites = {}
    for match in re.finditer(r'registerFoodData\("(\w+)",\s*FoodData\s*\.(create|createOneByTwo)\((\d+),\s*(\w+),\s*(\w+),\s*(?:ModConsumables\.)?(\w+),\s*(?:ModConsumables\.)?(\w+)\)(.*?)\);', source, re.S):
        name, kind, max_bites, block_food, item_food, block_consumable, item_consumable, rest = match.groups()
        loot = ["minecraft:bowl"]
        if m := re.search(r"setLootItem\(Items\.(\w+)\)", rest):
            loot = [java_item(m.group(1))]
        for m in re.finditer(r"addLootItems\(([^)]*)\)", rest):
            loot += [java_item(x.strip().split(".")[-1]) for x in m.group(1).split(",") if x.strip()]
        entry = {"max-bites": int(max_bites), "food": food_of(foods, consumables, block_food, block_consumable),
                 "item-food": food_of(foods, consumables, item_food, item_consumable), "loot": loot}
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
    # FoodBiteThreeByThreeBlock (not in the FoodBiteRegistry): 3x3 blocks, 8 bites, models from tools/gen_entity_models.py
    bites["kaleidoscope_cookery:cold_cut_ham_slices"] = {
        "max-bites": 8, "food": food_of(foods, consumables, "COLD_CUT_HAM_SLICES_BLOCK", "COLD_CUT_HAM_SLICES_BLOCK"),
        "item-food": None, "loot": ["minecraft:bowl"], "size": "3x3",
        "models": {"main": [f"kaleidoscope_cookery:block/food/cold_cut_ham_slices/cold_cut_ham_slices_{i}" for i in range(9)]}}
    return bites


# ---------------------------------------------------------------- Tea

def parse_teacups() -> dict:
    """TeacupRegistry: tea drinks with effects (drinking 32 ticks, the empty cup remains)."""
    source = (COOKERY_JAVA / "init/registry/TeacupRegistry.java").read_text(encoding="utf-8")
    ids = dict(re.findall(r'(\w+)\s*=\s*id\("(\w+)"\)', source))
    teas = {}
    for match in re.finditer(r'registerTeacupData\((\w+),\s*TeacupData\.create\((\d+)\)(.*?)\);\s*$', source, re.S | re.M):
        name, body = ids.get(match.group(1), match.group(1).lower()), match.group(3)
        effects = parse_effect_instances(body)
        teas[f"kaleidoscope_cookery:{name}"] = {"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True,
                                                "eat-seconds": 1.6, "remainder": "kaleidoscope_cookery:empty_cup", "effects": effects}
    return teas


# ---------------------------------------------------------------- Items

# Items with a vanilla function: base material instead of paper
BASE_MATERIALS = {
    "kaleidoscope_cookery:kitchen_shovel": "IRON_SHOVEL",
    # Stockpot lid: a shield in the mod (ShieldItem, 120 durability)
    "kaleidoscope_cookery:stockpot_lid": "SHIELD",
    # Kitchen knives: jigsaw block (not obtainable in survival, not placeable without op, no right-click function)
    # with all components of the matching sword. The plugin adds it to #minecraft:swords (like the knives in the mod).
    "kaleidoscope_cookery:copper_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:iron_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:gold_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:diamond_kitchen_knife": "JIGSAW",
    "kaleidoscope_cookery:netherite_kitchen_knife": "JIGSAW",
    # Sickle: structure block (also not obtainable and not placeable without op), own "Sweeping" enchantment
    "kaleidoscope_cookery:sickle": "STRUCTURE_BLOCK",
}
MAX_DAMAGE = {"kaleidoscope_cookery:stockpot_lid": 120, "kaleidoscope_cookery:sickle": 2000}
# SickleItem: SwordItem without attack attributes, repaired with flint (SICKLE_TIER)
EXTRA_ITEM_DATA = {"kaleidoscope_cookery:sickle": {"repair": "FLINT", "attributes": False}}
# Clothes (ArmorMaterial FARMER in the mod): leather armour as base (enchantments), armour values as in the mod, no durability.
# Straw hats without an equipment asset: the client then shows the 3D item model on the head.
WEARABLES = {
    "kaleidoscope_cookery:straw_hat": ("LEATHER_HELMET", "head", None, 3),
    "kaleidoscope_cookery:straw_hat_flower": ("LEATHER_HELMET", "head", None, 3),
    "kaleidoscope_cookery:farmer_chest_plate": ("LEATHER_CHESTPLATE", "chest", "kaleidoscope_cookery:cookery_farmer", 4),
    "kaleidoscope_cookery:farmer_leggings": ("LEATHER_LEGGINGS", "legs", "kaleidoscope_cookery:cookery_farmer", 5),
    "kaleidoscope_cookery:farmer_boots": ("LEATHER_BOOTS", "feet", "kaleidoscope_cookery:cookery_farmer", 2),
}
PROTOTYPES = {
    "kaleidoscope_cookery:copper_kitchen_knife": "COPPER_SWORD",
    "kaleidoscope_cookery:iron_kitchen_knife": "IRON_SWORD",
    "kaleidoscope_cookery:gold_kitchen_knife": "GOLDEN_SWORD",
    "kaleidoscope_cookery:diamond_kitchen_knife": "DIAMOND_SWORD",
    "kaleidoscope_cookery:netherite_kitchen_knife": "NETHERITE_SWORD",
    "kaleidoscope_cookery:sickle": "STONE_SWORD",
}


def parse_stack_sizes() -> dict:
    sizes = {}
    source = (COOKERY_JAVA / "init/ModItems.java").read_text(encoding="utf-8")
    for match in re.finditer(r'registerItem\("(\w+)"(.*?)\);\s*$', source, re.M):
        if m := re.search(r"stacksTo\((\d+)\)", match.group(2)):
            sizes["kaleidoscope_cookery:" + match.group(1)] = int(m.group(1))
    return sizes


# Not adopted: straw bed (not wanted), model preview (debug item), poplar furniture (poplar does not exist in 26.3)
# Grilling: a pepper bush crop instead of the pepper tree (log, leaves, sapling, world generation)
SKIP_ITEM = re.compile(r"kaleidoscope_cookery:(straw_bed|model_display|\w+_poplar)$|kaleidoscope_grilling:pepper_(log|leaves|sapling)$")


def without_skipped(recipes: dict) -> dict:
    """Remove recipes that need or produce an item that was not adopted, or poplar wood."""
    out = {}
    for section, values in recipes.items():
        if not isinstance(values, dict) or section == "tags":
            out[section] = values
            continue
        out[section] = {k: v for k, v in values.items()
                        if not re.search(r"kaleidoscope_cookery:(straw_bed|model_display|\w+_poplar)\b|minecraft:poplar|kaleidoscope_grilling:pepper_(log|leaves|sapling)\b",
                                         json.dumps(v) + k)}
    return out


# Skewer eating animations (MultiBiteSkewerItem.AnimationProfile): duration in ticks and bites in seconds
SKEWER_PROFILES = {"ONE": (90, [1.16667, 3.08333]), "TWO": (90, [0.95833, 4.0]),
                   "THREE": (100, [0.95833, 2.33333, 3.54167]), "THREE_ALT": (90, [0.95833, 2.16667, 3.5]),
                   "THREE_RANDOM": (100, [0.95833, 2.33333, 3.54167]), "FOUR": (90, [0.95833, 2.33333, 3.45833, 4.08333])}


def skewer_profile(item_id: str) -> str:
    """ModItems#animationProfile"""
    name = item_id.replace("raw_", "").replace("grilled_", "")
    if name in ("fish_skewer", "caterpillar_skewer"):
        return "ONE"
    if name in ("bun_slice_skewer", "fried_egg_skewer", "slime_skewer", "sweet_potato_sheet_skewer"):
        return "TWO"
    if name in ("beef_skewer", "chicken_skin_skewer", "pork_belly_skewer"):
        return "FOUR"
    if name == "ender_pearl_skewer":
        return "THREE"
    return "THREE_RANDOM"


def grilling_effect(expression: str) -> str | None:
    if m := re.search(r'cookeryEffect\("(\w+)"\)', expression):
        return "kaleidoscope_cookery:" + m.group(1)
    if m := re.search(r'grillingEffect\("(\w+)"\)', expression):
        return "kaleidoscope_grilling:" + m.group(1)
    if m := re.search(r'withDefaultNamespace\("(\w+)"\)', expression):
        return "minecraft:" + m.group(1)
    return None


def parse_grilling_foods() -> dict:
    """Grilling ModItems: nutrition and effects (constructors and helper methods skewer/rawSkewer/dish ...)."""
    source = (GRILLING / "neoforge-1.21.1/src/main/java/cn/breezeth/kaleidoscope_grilling/registry/ModItems.java").read_text(encoding="utf-8")
    flat = re.sub(r"\s+", " ", source)
    foods = {}

    def food(nutrition, modifier, always=False):
        result = {"nutrition": int(nutrition), "saturation": round(int(nutrition) * float(modifier) * 2, 2)}
        if always:
            result["always-edible"] = True
        return result

    def ticks(expression):
        return eval_int(expression.replace(" ", ""))

    for chunk in flat.split("public static final")[1:]:
        m = re.search(r'(?:ITEMS\.register|ingredient)\( ?"(\w+)"', chunk)
        f = re.search(r"\.nutrition\((\d+)\) ?\.saturationModifier\(([\d.]+)F?\)", chunk)
        if not m or not f:
            continue
        item = food(f.group(1), f.group(2), ".alwaysEdible()" in chunk)
        tail = chunk[f.end():]
        effects = []
        # (effect, ticks) by properties: EffectFoodItem, DualEffectFoodItem, MultiBiteSkewerItem
        for e in re.finditer(r'((?:cookeryEffect|grillingEffect)\("\w+"\)|ResourceLocation\.withDefaultNamespace\("\w+"\)), ?([\d* ]+)', tail):
            effects.append({"effect": grilling_effect(e.group(1)), "duration": ticks(e.group(2))})
        if effects:
            item["effects"] = effects
        if "MultiBiteSkewerItem" in chunk or "SkewerItem" in chunk:
            item["always-edible"] = True
            profile = re.search(r"AnimationProfile\.(\w+)", chunk)
            item["eat-seconds"] = SKEWER_PROFILES[profile.group(1) if profile else skewer_profile(m.group(1))][0] / 20
            # The Grilling plugin plays its own eating sound once at the start (instead of the nibbling every 4 ticks)
            item["eat-sound"] = "minecraft:intentionally_empty"
        foods[m.group(1)] = item
    static = flat[flat.index("static {"):]
    for m in re.finditer(r'(skewer|multiBiteSkewer|rawSkewer|animatedRawSkewer)\( ?"(\w+)", ?(\d+), ?([\d.]+)F?, ?([^;]*?)\);', static):
        kind, item_id, nutrition, modifier, rest = m.groups()
        item = food(nutrition, modifier, True)
        if kind in ("skewer", "multiBiteSkewer"):
            parts = rest.split(",")
            effect = grilling_effect(rest)
            if effect:
                seconds = int(re.search(r"\), ?(\d+)", rest).group(1))
                item["effects"] = [{"effect": effect, "duration": seconds * 20}]
        elif rest.strip().startswith("true"):
            item["effects"] = [{"effect": "minecraft:nausea", "duration": 60}]
        profile = re.search(r"AnimationProfile\.(\w+)", rest)
        item["eat-seconds"] = SKEWER_PROFILES[profile.group(1) if profile else skewer_profile(item_id)][0] / 20
        item["eat-sound"] = "minecraft:intentionally_empty"
        foods[item_id] = item
    for m in re.finditer(r'dish\("(\w+)", ?(\d+), ?([\d.]+)F?\)', flat):
        foods[m.group(1)] = food(m.group(2), m.group(3))
    # SkewerItem#finishFoodAndEffect: the effect from grilling/skewers.json takes precedence (e.g. golden skewer: invulnerable)
    data = read_json(GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/grilling/skewers.json")
    for entry in data["skewers"]:
        item_id = entry["id"].split(":")[1]
        if "effect" in entry and item_id in foods:
            if entry["effect"]:
                foods[item_id]["effects"] = [{"effect": entry["effect"], "duration": entry["effect_seconds"] * 20}]
            else:
                foods[item_id].pop("effects", None)
    return foods


def collect_items(foods: dict, bites: dict) -> dict:
    items = {}
    teas = parse_teacups()
    grilling_foods = parse_grilling_foods()
    stack_sizes = parse_stack_sizes()
    for ns in ("kaleidoscope_cookery", "kaleidoscope_grilling"):
        lang = read_json(imported(ns) / "lang" / "en_us.json")
        for key in lang:
            parts = key.split(".")
            if len(parts) != 3 or parts[0] not in ("item", "block") or parts[1] != ns:
                continue
            item_id = parts[2]
            if not (imported(ns) / "items" / f"{item_id}.json").exists():
                continue  # pure blocks without an item (crop stages, ...)
            full_id = f"{ns}:{item_id}"
            if (full_id in items and parts[0] == "block") or SKIP_ITEM.match(full_id):
                continue
            entry = {"model": full_id, "name": key}
            tooltip_key = f"tooltip.{ns}.{item_id}"
            if tooltip_key in lang:
                entry["tooltip"] = tooltip_key
            maxim_key = f"tooltip.{ns}.{item_id}.maxim"
            if maxim_key in lang:
                # Number of lines: as many as English or German need (see write_german_lang)
                german_maxim = read_json(ROOT / "tools" / "lang" / "de_de_extra.json").get(ns, {}).get(maxim_key, lang[maxim_key])
                entry["maxim-lines"] = max(len(lang[maxim_key].split("\n")), len(german_maxim.split("\n")))
            if full_id in BASE_MATERIALS:
                entry["base"] = BASE_MATERIALS[full_id]
                if full_id in PROTOTYPES:
                    entry["prototype"] = PROTOTYPES[full_id]
                if full_id in MAX_DAMAGE:
                    entry["max-damage"] = MAX_DAMAGE[full_id]
                entry.update(EXTRA_ITEM_DATA.get(full_id, {}))
            elif full_id in stack_sizes:
                entry["max-stack"] = stack_sizes[full_id]
            if ns == "kaleidoscope_grilling" and item_id in grilling_foods:
                entry["food"] = grilling_foods[item_id]
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
            if full_id == "kaleidoscope_cookery:raw_dough":
                entry["use-animation"] = "bow"  # RawDoughItem: hold it like a bow, then it turns into noodles
            if full_id in ("kaleidoscope_cookery:oil_pot", "kaleidoscope_cookery:fruit_basket", "kaleidoscope_cookery:transmutation_lunch_bag"):
                entry["max-stack"] = 1  # OilPotItem, FruitBasketItem, TransmutationLunchBagItem: stacksTo(1)
            if full_id in ("kaleidoscope_grilling:empty_seasoning_bottle", "kaleidoscope_grilling:pending_seasoning",
                           "kaleidoscope_grilling:special_seasoning", "kaleidoscope_grilling:skewer_recipe_book"):
                entry["max-stack"] = 1  # stacksTo(1)
            if full_id == "kaleidoscope_grilling:skewer_plate":
                entry["max-stack"] = 1
                entry["sneak-place"] = True  # SkewerPlateItem#useOn: only placed while sneaking, otherwise eaten
            if full_id == "kaleidoscope_grilling:pending_seasoning":
                entry["sneak-place"] = True  # PendingSeasoningItem#useOn: without sneaking it is shaken
            if full_id == "kaleidoscope_cookery:teapot":
                # TeapotItem#useOn only places while sneaking (otherwise it scoops water) - here placed without sneaking like
                # all stations (user request); scooping still works because a station cannot be placed into water
                entry["max-stack"] = 1
            if full_id == "kaleidoscope_cookery:clay_pot_milk_tea":
                # ClayPotMilkTeaItem: drink (32 ticks, honey sound), removes harmful effects, a flower pot remains
                entry["food"] = {"nutrition": 0, "saturation": 0.0, "always-edible": True, "drink": True, "eat-seconds": 1.6,
                                 "remainder": "minecraft:flower_pot", "eat-sound": "minecraft:item.honey_bottle.drink"}
                entry["tooltip"] = "tooltip.kaleidoscope_cookery.clay_pot_milk_tea.effect"
                entry["max-stack"] = 16
            if full_id in teas:
                entry["food"] = teas[full_id]
                entry["max-stack"] = 16
                entry["sneak-place"] = True
            if full_id in bites:
                bite = dict(bites[full_id])
                if bite["item-food"] is not None:
                    entry["food"] = bite["item-food"]
                bite.pop("item-food")
                entry["plate"] = bite
            items[full_id] = entry
    # The plugin's own items (not from the mods)
    items["kaleidoscope:cookbook"] = {"model": "kaleidoscope:cookbook", "name": "kaleidoscope.cookbook.item",
                                      "tooltip": "kaleidoscope.cookbook.item.tooltip", "max-stack": 1}
    for full_id, name_key in EXTRA_ITEMS.items():
        ns, item_id = full_id.split(":")
        entry = {"model": full_id, "name": name_key, "tooltip": f"tooltip.{ns}.{item_id}"}
        if full_id == "kaleidoscope_cookery:recipe_item":
            entry["max-stack"] = 64
        items.setdefault(full_id, entry)
    return dict(sorted(items.items()))


# ---------------------------------------------------------------- Tags + recipes

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
                                    continue  # optional entries of other mods
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


# Vanilla items renamed in 26.x
RENAMED_ITEMS = {"minecraft:chain": "minecraft:iron_chain"}
# Items without their own name in the language file (item id -> language key)
EXTRA_ITEMS = {"kaleidoscope_cookery:recipe_item": "block.kaleidoscope_cookery.recipe_block",
               # Seeds of the pepper bush (tools/gen_pepper_bush.py) instead of the pepper tree
               "kaleidoscope_grilling:sichuan_pepper_seeds": "item.kaleidoscope_grilling.sichuan_pepper_seeds"}


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
    if isinstance(value, str):
        # 26.x format: "item" or "#tag"
        if value.startswith("#"):
            used_tags.add(value[1:])
            return value
        return item_id(value)
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
            # Grilling replaces individual Cookery recipes (same path in the Cookery namespace)
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
    # RiceBowlRecipe: shapeless ingredient + cooked rice (#c:foods/cooked_rice), the best quality goes onto the result
    rice_bowl = {}
    for ns, base in [(b.parent.name, b) for b in RECIPE_DIRS]:
        for file in sorted(base.glob("*.json")) if base.exists() else []:
            recipe = read_json(file)
            if recipe.get("type") == "kaleidoscope_cookery:rice_bowl":
                rice_bowl[f"{ns}:{file.stem}"] = {"ingredient": ingredient(recipe["ingredient"], used_tags),
                                                  "result": result_list({"result": recipe["result"]})[0]}
    if rice_bowl:
        used_tags.add("c:foods/cooked_rice")
    vanilla = collect_vanilla_recipes(used_tags)
    tag_groups = {}
    for name in sorted(used_tags):
        if name.startswith("minecraft:"):
            continue  # the plugin resolves vanilla tags at runtime via the server
        values = resolve_tag(name, tags) or COMMON_TAGS.get(name, [])
        if not values:
            print(f"WARNUNG: Tag #{name} lässt sich nicht auflösen")
        tag_groups[name] = values
    return {"data-version": DATA_VERSION, "tags": tag_groups, "chopping-board": chopping, "pot": pot, "flex-pot": flex_pot,
            "stockpot": stockpot, "flex-stockpot": flex_stockpot, "steamer": steamer, "teapot": teapot,
            "millstone": millstone, "bamboo-tray": bamboo_tray, "rice-bowl": rice_bowl, "vanilla": vanilla}


# Increase on every content change of the generated recipes.yml: the plugin then replaces old copies (with a backup)
DATA_VERSION = 13
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
    # Vanilla "1x-9x" recipes: one recipe that works with 1-9 identical ingredients
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
                # Not specified: bowl; empty list: no container needed
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


# NeoForge common tags that are not in the mod data
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
    (COOKERY / "src/main/resources/data/kaleidoscope_cookery/recipe", "kaleidoscope_cookery"),
    (COOKERY / "src/main/resources/data/minecraft/recipe", "kaleidoscope_cookery"),
    (GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/recipe", "kaleidoscope_grilling"),
]


def collect_vanilla_recipes(used_tags: set) -> dict:
    # The plugin's cookbook: book + any seeds (mod or vanilla)
    seeds = ["kaleidoscope_cookery:tomato_seed", "kaleidoscope_cookery:chili_seed", "kaleidoscope_cookery:lettuce_seed",
             "kaleidoscope_cookery:wild_rice", "kaleidoscope_cookery:rice", "kaleidoscope_cookery:tea_seed",
             "minecraft:wheat_seeds", "minecraft:beetroot_seeds", "minecraft:pumpkin_seeds", "minecraft:melon_seeds",
             "minecraft:torchflower_seeds", "minecraft:pitcher_pod"]
    recipes = {"kaleidoscope:cookbook": {"type": "crafting_shapeless",
                                         "ingredients": ["minecraft:book", seeds],
                                         "result": "kaleidoscope:cookbook"}}
    for folder, namespace in VANILLA_RECIPE_DIRS:
        if not folder.exists():
            continue
        for file in sorted(folder.glob("*.json")):
            recipe = read_json(file)
            kind = recipe.get("type")
            if kind not in VANILLA_RECIPE_TYPES:
                continue
            if "components" in recipe.get("result", {}):
                continue  # e.g. a pre-filled steamer: contents as block entity data do not exist here
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


# ---------------------------------------------------------------- Language files

def write_german_lang():
    names = read_json(ROOT / "tools" / "lang" / "de_de_names.json")
    extra_path = ROOT / "tools" / "lang" / "de_de_extra.json"
    extra = read_json(extra_path) if extra_path.exists() else {}
    for ns in ("kaleidoscope_cookery", "kaleidoscope_grilling"):
        english = read_json(imported(ns) / "lang" / "en_us.json")
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
        # Maxims (*.maxim) have several lines ("\n"); item descriptions cannot contain line breaks,
        # so every line gets its own key (*.maxim.line1 ...) for English and German
        english_lines = {}
        for key, value in english.items():
            if key.endswith(".maxim"):
                en = value.split("\n")
                de = german.get(key, value).split("\n")
                for i in range(max(len(en), len(de))):
                    english_lines[f"{key}.line{i + 1}"] = en[i] if i < len(en) else ""
                    german[f"{key}.line{i + 1}"] = de[i] if i < len(de) else ""
        if english_lines:
            out_en = custom(ns) / "lang" / "en_us.json"
            existing = read_json(out_en) if out_en.exists() else {}
            existing = {k: v for k, v in existing.items() if ".maxim.line" not in k}
            existing.update(english_lines)
            out_en.parent.mkdir(parents=True, exist_ok=True)
            out_en.write_text(json.dumps(dict(sorted(existing.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        out = custom(ns) / "lang" / "de_de.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(dict(sorted(german.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        if missing:
            print(f"Ohne deutsche Übersetzung ({ns}): {', '.join(missing)}")


GRILLING_RESOURCES = GRILLING_ROOT / "src" / "main" / "resources"
# Increase on every content change of the Grilling recipes.yml
GRILLING_DATA_VERSION = 2


def write_skewers():
    """Grilling: skewer recipes (grilling/skewers.json, tags resolved) and effects of the grilled skewers."""
    data = read_json(GRILLING / "common/src/main/resources/data/kaleidoscope_grilling/grilling/skewers.json")
    tags = load_tags()

    def options(slot):
        values = []
        for selector in slot:
            values += resolve_tag(selector[1:], tags) if selector.startswith("#") else [item_id(selector)]
        return list(dict.fromkeys(values))

    recipes, effects = {}, {}
    for entry in data["skewers"]:
        if entry.get("ingredients"):
            recipe = {"ingredients": [options(slot) for slot in entry["ingredients"]]}
            if entry.get("cooked_result"):
                recipe["cooked"] = entry["cooked_result"]
            recipes[entry["id"]] = recipe
        if entry.get("effect"):
            effects[entry["id"]] = {"effect": entry["effect"], "seconds": entry["effect_seconds"]}
    out = {"recipes": recipes, "effects": effects,
           "skewerable": resolve_tag("kaleidoscope_grilling:skewerable_ingredients", tags),
           "unskewerable": resolve_tag("kaleidoscope_grilling:unskewerable_ingredients", tags),
           "seasoning-effects": {e["ingredient"]: e["kind"] for e in data.get("seasoning_effects", [])}}
    target = GRILLING_RESOURCES / "generated" / "skewers.yml"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("# Generiert von tools/gen_data.py aus grilling/skewers.json - nicht von Hand bearbeiten.\n"
                      + to_yaml(out) + "\n", encoding="utf-8")
    return len(recipes)


def split_recipes(core: dict, full: dict) -> dict:
    """What Grilling adds or changes in Cookery recipes (same id = override)."""
    addon = {}
    for section, values in full.items():
        if not isinstance(values, dict):
            continue
        base = core.get(section, {})
        changed = {key: value for key, value in values.items() if base.get(key) != value}
        if changed:
            addon[section] = changed
    return addon


def main():
    global RECIPE_DIRS, VANILLA_RECIPE_DIRS, TAG_DIRS
    raw_foods = parse_cookery_foods()
    foods = parse_cookery_item_foods(raw_foods)
    items = collect_items(foods, parse_food_bites(raw_foods))
    core_items = {k: v for k, v in items.items() if not k.startswith("kaleidoscope_grilling:")}
    grilling_items = {k: v for k, v in items.items() if k.startswith("kaleidoscope_grilling:")}
    header = "# Generiert von tools/gen_data.py aus den Mod-Quellen - nicht von Hand bearbeiten.\n"
    (RESOURCES / "items.yml").write_text(header + to_yaml({"items": core_items}) + "\n", encoding="utf-8")
    GRILLING_RESOURCES.mkdir(parents=True, exist_ok=True)
    (GRILLING_RESOURCES / "items.yml").write_text(header + to_yaml({"items": grilling_items}) + "\n", encoding="utf-8")

    all_dirs, all_vanilla, all_tags = RECIPE_DIRS, VANILLA_RECIPE_DIRS, TAG_DIRS
    RECIPE_DIRS = [d for d in all_dirs if "grilling" not in str(d)]
    VANILLA_RECIPE_DIRS = [d for d in all_vanilla if "grilling" not in str(d[0])]
    TAG_DIRS = [d for d in all_tags if "grilling" not in str(d)]
    recipes = without_skipped(collect_recipes(load_tags()))
    RECIPE_DIRS, VANILLA_RECIPE_DIRS, TAG_DIRS = all_dirs, all_vanilla, all_tags
    grilling_recipes = split_recipes(recipes, without_skipped(collect_recipes(load_tags())))
    grilling_recipes["data-version"] = GRILLING_DATA_VERSION
    recipe_header = ("# Rezepte der Kochstationen, erzeugt aus den Mod-Daten (tools/gen_data.py).\n"
                     "# Diese Datei wird beim ersten Start nach plugins/KaleidoscopeCookery/recipes.yml kopiert\n"
                     "# und kann dort angepasst werden (/kc reload).\n"
                     "# Zutaten: Item-ID, \"#tag\" (siehe tags; minecraft:-Tags kommen vom Server) oder Liste von Alternativen.\n")
    (RESOURCES / "recipes.yml").write_text(recipe_header + to_yaml(recipes) + "\n", encoding="utf-8")
    grilling_header = ("# Rezepte aus Kaleidoscope Grilling (tools/gen_data.py): neue Rezepte und Aenderungen an Cookery-Rezepten.\n"
                       "# Wird nach plugins/KaleidoscopeGrilling/recipes.yml kopiert; KaleidoscopeCookery mischt sie in seine Rezepte\n"
                       "# (gleiche ID = ersetzt). Format wie die recipes.yml von KaleidoscopeCookery.\n")
    (GRILLING_RESOURCES / "recipes.yml").write_text(grilling_header + to_yaml(grilling_recipes) + "\n", encoding="utf-8")
    write_german_lang()
    skewers = write_skewers()
    print(f"Cookery: {len(core_items)} Items, Wok {len(recipes['pot'])}+{len(recipes['flex-pot'])}, Suppentopf {len(recipes['stockpot'])}+{len(recipes['flex-stockpot'])}, "
          f"Schneidebrett {len(recipes['chopping-board'])}, Vanilla {len(recipes['vanilla'])}, Tags {len(recipes['tags'])}")
    print(f"Grilling: {len(grilling_items)} Items, {skewers} Spieß-Rezepte, Rezepte: " + ", ".join(f"{k} {len(v)}" for k, v in grilling_recipes.items() if isinstance(v, dict)))


if __name__ == "__main__":
    main()
