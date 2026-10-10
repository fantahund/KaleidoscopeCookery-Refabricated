#!/usr/bin/env python3
"""
Generates the player wiki as plain static HTML (no API calls, no fetch at runtime): wiki/ can be copied into any
folder of a web server (Apache) and also works when opened from the file system.

Every page contains German and English: the settings in the top bar switch the wiki language and, separately, the
language of item, block, entity and effect names (stored in the browser). Texts are written as (German, English)
pairs; names inside texts are placeholders like {item:ns:id}, {entity:minecraft:pig} or {effect:ns:id}.

Data comes straight from both plugins: items.yml, recipes.yml (KaleidoscopeGrilling merged in like registerAddon does),
disabled-recipes from the default config.yml, generated/skewers.yml, the language files and the rendered item images
of the built packs (run `mvn package` in both repos first). Vanilla names, tags and campfire recipes come from the
26.3 client/server jars and the German vanilla language file (downloaded into the cache once).

Usage: python3 -I tools/gen_wiki.py [--out wiki] [--cache ~/.cache/kaleidoscope-pack]
"""
import argparse
import html
import io
import json
import re
import shutil
import urllib.request
import zipfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
GRILLING = ROOT.parent / "KaleidoscopeGrilling"
WIKI_SRC = Path(__file__).resolve().parent / "wiki"
MONOCRAFT = "https://github.com/IdreesInc/Monocraft/releases/download/v4.2.1/Monocraft-ttf.zip"

# ---------------------------------------------------------------- Sources


def download(url: str, target: Path) -> Path:
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        print(f"Downloading {url}")
        with urllib.request.urlopen(url) as response:
            target.write_bytes(response.read())
    return target


class Vanilla:
    """Client jar (textures, models), server data (tags, campfire recipes) and the German language file."""

    def __init__(self, cache: Path):
        meta = json.loads((cache / "26.3.json").read_text())
        self.client = zipfile.ZipFile(cache / "client-26.3.jar")
        bundler = zipfile.ZipFile(download(meta["downloads"]["server"]["url"], cache / "server-26.3.jar"))
        inner = next(n for n in bundler.namelist() if n.startswith("META-INF/versions/") and n.endswith(".jar"))
        self.server = zipfile.ZipFile(io.BytesIO(bundler.read(inner)))
        index = json.loads(download(meta["assetIndex"]["url"], cache / "assets-index-26.3.json").read_text())
        lang_hash = index["objects"]["minecraft/lang/de_de.json"]["hash"]
        lang_file = download(f"https://resources.download.minecraft.net/{lang_hash[:2]}/{lang_hash}", cache / "vanilla-de_de.json")
        self.lang_de = json.loads(lang_file.read_text(encoding="utf-8"))
        self.lang_en = json.loads(self.client.read("assets/minecraft/lang/en_us.json"))

    def tag(self, key: str) -> list:
        ns, path = key.split(":", 1)
        name = f"data/{ns}/tags/item/{path}.json"
        if name not in self.server.namelist():
            return []
        return [v["id"] if isinstance(v, dict) else v for v in json.loads(self.server.read(name))["values"]]

    def campfire_recipes(self) -> list:
        recipes = []
        for name in self.server.namelist():
            if name.startswith("data/minecraft/recipe/") and name.endswith(".json"):
                data = json.loads(self.server.read(name))
                if data.get("type") == "minecraft:campfire_cooking":
                    recipes.append((name.split("/")[-1][:-5], data))
        return recipes


def yaml_load(path: Path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))


class Data:
    def __init__(self, vanilla: Vanilla):
        self.vanilla = vanilla
        self.items = dict(yaml_load(ROOT / "core/src/main/resources/items.yml")["items"])
        self.items.update(yaml_load(GRILLING / "src/main/resources/items.yml")["items"])
        self.recipes = yaml_load(ROOT / "core/src/main/resources/recipes.yml")
        for section, values in yaml_load(GRILLING / "src/main/resources/recipes.yml").items():
            if isinstance(values, dict):
                self.recipes.setdefault(section, {}).update(values)
        self.config = yaml_load(ROOT / "core/src/main/resources/config.yml")
        self.disabled = self.config.get("disabled-recipes", [])
        self.skewers = yaml_load(GRILLING / "src/main/resources/generated/skewers.yml")
        self.tabs = {}
        for path in (ROOT / "core/src/main/resources/generated/creative_tabs.yml", GRILLING / "src/main/resources/generated/creative_tabs.yml"):
            for tab, value in yaml_load(path)["tabs"].items():
                self.tabs[tab] = value["items"]
        self.packs = [zipfile.ZipFile(ROOT / "core/target/classes/KaleidoscopeCookery-Pack.zip"),
                      zipfile.ZipFile(GRILLING / "target/classes/KaleidoscopeGrilling-Pack.zip")]
        self.lang = {"en": dict(vanilla.lang_en), "de": dict(vanilla.lang_de)}
        for pack in self.packs:
            for name in pack.namelist():
                for code, file in (("en", "/lang/en_us.json"), ("de", "/lang/de_de.json")):
                    if name.endswith(file):
                        self.lang[code].update(json.loads(pack.read(name)))
        for key, value in self.lang["en"].items():
            self.lang["de"].setdefault(key, value)
        self.icon_files = self.load_icons()

    def disabled_recipe(self, recipe_id: str) -> bool:
        return any(recipe_id.startswith(p[:-1]) if p.endswith("*") else recipe_id == p for p in self.disabled)

    def load_icons(self) -> dict:
        """Item -> path of its cookbook image: rendered 3D view or the flat texture (item_icons.txt + icon font)."""
        icons = {}
        sources = [(ROOT / "core/target/classes/item_icons.txt", self.packs[0]), (GRILLING / "target/classes/item_icons.txt", self.packs[1])]
        for path, pack in sources:
            glyphs = {}
            for line in path.read_text(encoding="utf-8").split("\n"):
                if line.startswith("@font|"):
                    ns, font_path = line[6:].strip().split(":")
                    providers = json.loads(pack.read(f"assets/{ns}/font/{font_path}.json"))["providers"]
                    glyphs = {ord(p["chars"][0]): p["file"] for p in providers}
                    continue
                if "=" not in line or line.startswith("@"):
                    continue
                item, rest = line.split("=", 1)
                fields = rest.split("|")
                if len(fields) > 2 and fields[2]:
                    file = glyphs.get(int(fields[2].split(":")[0], 16))
                    if file:
                        ns, file_path = file.split(":")
                        icons[item] = f"assets/{ns}/textures/{file_path}"
                        continue
                ns, sprite = fields[1].split(":")
                icons[item] = f"assets/{ns}/textures/{sprite}.png"
        return icons

    def read_asset(self, path: str):
        for pack in self.packs:
            if path in pack.namelist():
                return pack.read(path)
        if path in self.vanilla.client.namelist():
            return self.vanilla.client.read(path)
        return None

    # ---- Names ----

    def name(self, item: str, code: str) -> str:
        lang = self.lang[code]
        if item in self.items:
            return lang.get(self.items[item].get("name", ""), item)
        ns, path = item.split(":", 1)
        for prefix in ("item", "block"):
            if f"{prefix}.{ns}.{path}" in lang:
                return lang[f"{prefix}.{ns}.{path}"]
        return path.replace("_", " ").title()

    def keyed(self, kind: str, key: str, code: str) -> str:
        ns, path = key.split(":", 1)
        return self.lang[code].get(f"{kind}.{ns}.{path}", path.replace("_", " ").title())

    def resolve(self, ingredient) -> list:
        """Ingredient (id, "#tag" or list of alternatives) -> list of item ids."""
        if isinstance(ingredient, list):
            out = []
            for value in ingredient:
                out += [v for v in self.resolve(value) if v not in out]
            return out
        if isinstance(ingredient, dict):
            return self.resolve(ingredient.get("item") or ingredient.get("tag"))
        if not isinstance(ingredient, str):
            return []
        if ingredient.startswith("#"):
            key = ingredient[1:]
            return self.resolve(list(self.recipes.get("tags", {}).get(key) or self.vanilla.tag(key)))
        if ingredient.split(":")[0] not in ("minecraft", "kaleidoscope", "kaleidoscope_cookery", "kaleidoscope_grilling"):
            return []  # optional entries of other mods (e.g. kaleidoscope_tavern) never match on the server
        return [ingredient]


# ---------------------------------------------------------------- Recipes

# Liquids (stockpot broth, teapot tea base) shown as their bucket
LIQUIDS = {"minecraft:water": "minecraft:water_bucket", "minecraft:lava": "minecraft:lava_bucket",
           "minecraft:milk": "minecraft:milk_bucket", "kaleidoscope_cookery:milk": "minecraft:milk_bucket"}

FLEX = ("Flex-Rezept: jede Zutat mindestens einmal, die Mengen bestimmen die Qualität",
        "Flex recipe: every ingredient at least once, the amounts decide the quality")


def result_of(value) -> tuple:
    if isinstance(value, dict):
        return value.get("item"), int(value.get("count", 1))
    return value, 1


class Recipe:
    def __init__(self, station: str, rid: str, inputs: list, outputs: list, notes=None, grid=None, extra=None):
        self.station = station
        self.id = rid
        self.inputs = inputs      # list of (alternatives, count)
        self.outputs = outputs    # list of (item, count, chance)
        self.notes = notes or []  # list of (German, English) with placeholders
        self.grid = grid          # 9 slots (alternatives or None) for shaped crafting
        self.extra = extra        # ((German, English) label, item) shown next to the station, e.g. broth

    def items_in(self) -> set:
        found = set()
        for alts, _ in self.inputs:
            found |= set(alts)
        for alts in self.grid or []:
            found |= set(alts or [])
        if self.extra:
            found.add(self.extra[1])
        return found


def seconds(ticks) -> str:
    return f"{int(ticks) / 20:g}"


def de_num(value) -> str:
    return str(value).replace(".", ",")


def count_inputs(data: Data, ingredients: list) -> list:
    merged = []
    for ingredient in ingredients:
        alts = data.resolve(ingredient)
        for entry in merged:
            if entry[0] == alts:
                entry[1] += 1
                break
        else:
            merged.append([alts, 1])
    return [(alts, n) for alts, n in merged]


def build_recipes(data: Data) -> list:
    r = data.recipes
    out = []

    def keep(rid):
        return not data.disabled_recipe(rid)

    for rid, v in r.get("chopping-board", {}).items():
        if keep(rid):
            out.append(Recipe("chopping_board", rid, count_inputs(data, [v["ingredient"]]), [(*result_of(x), 1.0) for x in v["results"]],
                              [(f"{v['cuts']} Schnitte mit dem Küchenmesser", f"{v['cuts']} cuts with a kitchen knife")]))
    for section, flex in (("pot", False), ("flex-pot", True)):
        for rid, v in r.get(section, {}).items():
            if not keep(rid):
                continue
            item, count = result_of(v["result"])
            notes = []
            if v.get("per-item"):
                notes.append(("1–9 gleiche Zutaten ergeben entsprechend viele Portionen", "1–9 identical ingredients give as many servings"))
            if flex:
                notes.append(FLEX)
            t, stir = seconds(v.get("time", 200)), v.get("stir-fry", 3)
            notes.append((f"Kochzeit {de_num(t)} s, {stir}× rühren", f"Cooking time {t} s, stir {stir}×"))
            carrier = v.get("carrier")
            notes.append((f"Herausnehmen mit: {{item:{carrier}}}", f"Take out with: {{item:{carrier}}}") if carrier else
                         ("Herausnehmen mit dem {item:kaleidoscope_cookery:kitchen_shovel}", "Take out with the {item:kaleidoscope_cookery:kitchen_shovel}"))
            out.append(Recipe("pot", rid, count_inputs(data, v["ingredients"]), [(item, count, 1.0)], notes))
    for section, flex in (("stockpot", False), ("flex-stockpot", True)):
        for rid, v in r.get(section, {}).items():
            if not keep(rid):
                continue
            item, count = result_of(v["result"])
            notes = [FLEX] if flex else []
            t = seconds(v.get("time", 300))
            notes.append((f"Kochzeit {de_num(t)} s", f"Cooking time {t} s"))
            carrier = v.get("carrier")
            if carrier:
                notes.append((f"Portionen nehmen mit: {{item:{carrier}}}", f"Take servings with: {{item:{carrier}}}"))
            base = v.get("soup-base", "minecraft:water")
            out.append(Recipe("stockpot", rid, count_inputs(data, v["ingredients"]), [(item, count, 1.0)], notes,
                              extra=(("Brühe", "Broth"), LIQUIDS.get(base, base))))
    for rid, v in r.get("steamer", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            t = seconds(v.get("time", 1200))
            out.append(Recipe("steamer", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [(f"Dämpfzeit {de_num(t)} s", f"Steaming time {t} s")]))
    for rid, v in r.get("teapot", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            fluid = v.get("tea-fluid", "minecraft:water")
            t = seconds(v.get("time", 2400))
            out.append(Recipe("teapot", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [(f"Ziehzeit {de_num(t)} s", f"Steeping time {t} s"), ("Eine Kanne ergibt 12 Tassen", "One pot fills 12 cups")],
                              extra=(("Teebasis", "Tea base"), LIQUIDS[fluid]) if fluid in LIQUIDS else None))
    for rid, v in r.get("millstone", {}).items():
        if keep(rid):
            outputs = []
            for x in v["results"]:
                item, count = result_of(x)
                outputs.append((item, count, float(x.get("chance", 1.0)) if isinstance(x, dict) else 1.0))
            out.append(Recipe("millstone", rid, count_inputs(data, [v["ingredient"]]), outputs))
    for rid, v in r.get("bamboo-tray", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            kind = (("Trocknen (freier Himmel, kein Regen)", "Drying (open sky, no rain)") if v.get("type") == "drying" else
                    ("Befeuchten (Regen oder tropfender Tropfstein)", "Wetting (rain or dripping dripstone)"))
            t = seconds(v.get("time", 1800))
            out.append(Recipe("bamboo_tray", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [kind, (f"Dauer {de_num(t)} s", f"Duration {t} s")]))
    for rid, v in r.get("rice-bowl", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            out.append(Recipe("crafting", rid, count_inputs(data, [v["ingredient"], "#c:foods/cooked_rice"]), [(item, count, 1.0)],
                              [("Formlos in der Werkbank, das Ergebnis bekommt die beste Qualität der Zutaten",
                                "Shapeless in the crafting table, the result gets the best quality of the ingredients")]))
    for rid, v in r.get("vanilla", {}).items():
        if not keep(rid):
            continue
        kind = v.get("type")
        item, count = result_of(v["result"])
        outputs = [(item, count, 1.0)]
        if kind == "crafting_shaped":
            keys = {k: data.resolve(val) for k, val in v["key"].items()}
            grid = []
            for row in (v["pattern"] + ["", "", ""])[:3]:
                for ch in (row + "   ")[:3]:
                    grid.append(keys.get(ch) if ch != " " else None)
            merged = []
            for alts in (x for x in grid if x):
                for entry in merged:
                    if entry[0] == alts:
                        entry[1] += 1
                        break
                else:
                    merged.append([alts, 1])
            out.append(Recipe("crafting", rid, [(a, n) for a, n in merged], outputs, grid=grid))
        elif kind == "crafting_shapeless":
            out.append(Recipe("crafting", rid, count_inputs(data, v["ingredients"]), outputs, [("Formlos", "Shapeless")]))
        elif kind in ("smelting", "smoking", "campfire_cooking", "blasting"):
            label = {"smelting": ("{item:minecraft:furnace}", "{item:minecraft:furnace}"),
                     "smoking": ("{item:minecraft:smoker}", "{item:minecraft:smoker}"),
                     "campfire_cooking": ("{item:minecraft:campfire}", "{item:minecraft:campfire}"),
                     "blasting": ("{item:minecraft:blast_furnace}", "{item:minecraft:blast_furnace}")}[kind]
            t = seconds(v.get("time", 200))
            notes = [(f"{label[0]}, {de_num(t)} s", f"{label[1]}, {t} s")]
            if kind == "campfire_cooking":
                out.append(Recipe("shawarma_spit", rid, count_inputs(data, [v["ingredient"]]), outputs, notes))
            out.append(Recipe("furnace", rid, count_inputs(data, [v["ingredient"]]), outputs, notes))
        elif kind == "smithing_transform":
            out.append(Recipe("crafting", rid, count_inputs(data, [v["template"], v["base"], v["addition"]]), outputs,
                              [("{item:minecraft:smithing_table}: Vorlage, Basis, Zusatz", "{item:minecraft:smithing_table}: template, base, addition")]))
    for name, value in data.vanilla.campfire_recipes():
        item = value["result"]["id"] if isinstance(value["result"], dict) else value["result"]
        out.append(Recipe("shawarma_spit", f"minecraft:{name}", count_inputs(data, [value["ingredient"]]), [(item, 1, 1.0)],
                          [("Vanilla-Rezept vom {item:minecraft:campfire}", "Vanilla {item:minecraft:campfire} recipe")]))
    for raw, value in data.skewers["recipes"].items():
        if data.disabled_recipe(raw):
            continue
        slots = [(data.resolve(list(slot)), 1) for slot in value["ingredients"]]
        out.append(Recipe("skewering", raw, [(["minecraft:stick"], 1)] + slots, [(raw, 1, 1.0)],
                          [("{item:minecraft:stick} in die Nebenhand, Zutaten der Reihe nach mit Rechtsklick aufspießen",
                            "{item:minecraft:stick} in the off hand, skewer the ingredients in order with right-click")]))
        if value.get("cooked"):
            out.append(Recipe("grill", raw + "/grill", [([raw], 1)], [(value["cooked"], 1, 1.0)],
                              [("Auflegen, mit der {item:kaleidoscope_cookery:oil_pot} bestreichen, 4× wenden",
                                "Put it on, brush with the {item:kaleidoscope_cookery:oil_pot}, turn 4×")]))
    out.append(Recipe("skewering", "kaleidoscope_grilling:secret_skewer", [(["minecraft:stick"], 1), (["kaleidoscope_grilling:unfinished_skewer"], 1)],
                      [("kaleidoscope_grilling:secret_skewer", 1, 1.0)],
                      [("Drei beliebige essbare Zutaten, die kein festes Rezept ergeben", "Any three edible ingredients that do not match a fixed recipe")]))
    out.append(Recipe("grill", "kaleidoscope_grilling:secret_skewer/grill", [(["kaleidoscope_grilling:secret_skewer"], 1)],
                      [("kaleidoscope_grilling:secret_skewer", 1, 1.0)],
                      [("Wird gegart und wirkt dann wie seine Zutaten", "Gets cooked and then acts like its ingredients")]))
    out.append(Recipe("oil_press", "kaleidoscope_grilling:oil_press/canola", [(["kaleidoscope_grilling:oil_cake"], 4)],
                      [("kaleidoscope_grilling:canola_oil_bucket", 4, 1.0), ("kaleidoscope_grilling:oil_residue", 4, 1.0)],
                      [("16× pressen ({item:minecraft:anvil} +4, Pressstein +1)", "Press 16× ({item:minecraft:anvil} +4, press stone +1)"),
                       ("Das Rapsöl fließt in ein {item:kaleidoscope_grilling:big_vat} in der Nähe",
                        "The canola oil flows into a nearby {item:kaleidoscope_grilling:big_vat}")]))
    out.append(Recipe("seasoning", "kaleidoscope_grilling:special_seasoning",
                      [(["kaleidoscope_grilling:empty_seasoning_bottle"], 1), (["kaleidoscope_grilling:green_chili_powder"], 1),
                       (["kaleidoscope_grilling:sichuan_pepper"], 1), (["kaleidoscope_grilling:onion_powder"], 1)],
                      [("kaleidoscope_grilling:special_seasoning", 1, 1.0)],
                      [("Zutaten in die hingestellte Flasche geben (bis 8)", "Put the ingredients into the placed bottle (up to 8)"),
                       ("Die unfertige Mischung 4 s schütteln: 16 Anwendungen", "Shake the unfinished mix for 4 s: 16 uses")]))
    out.append(Recipe("crafting", "kaleidoscope_grilling:cold_houttuynia", [(["kaleidoscope_grilling:houttuynia"], 3), (["kaleidoscope_cookery:oil_pot"], 1)],
                      [("kaleidoscope_grilling:cold_houttuynia", 1, 1.0)],
                      [("{item:kaleidoscope_cookery:oil_pot} mit mindestens 2 Magma-Chiliöl; sie bleibt mit 2 Öl weniger im Raster",
                        "{item:kaleidoscope_cookery:oil_pot} with at least 2 magma chili oil; it stays in the grid with 2 oil less")]))
    return out


# ---------------------------------------------------------------- Texts (German, English)

# id, title (None = name of the icon item), icon item, group, intro paragraphs, steps
STATIONS = [
    ("stove", None, "kaleidoscope_cookery:stove", ("Kochen", "Cooking"),
     [("Der Herd ist die Hitzequelle für {item:kaleidoscope_cookery:pot} und {item:kaleidoscope_cookery:stockpot}. Alternativ funktionieren auch "
       "{item:minecraft:campfire}, Feuer, Lava, Magma und andere brennende Blöcke darunter.",
       "The stove is the heat source for the {item:kaleidoscope_cookery:pot} and {item:kaleidoscope_cookery:stockpot}. A {item:minecraft:campfire}, "
       "fire, lava, magma and other burning blocks below work as well.")],
     [("Anzünden mit {item:minecraft:flint_and_steel}, {item:minecraft:fire_charge} oder einem brennenden Projektil (z. B. Flammenpfeil).",
       "Light it with {item:minecraft:flint_and_steel}, a {item:minecraft:fire_charge} or a burning projectile (e.g. a Flame arrow)."),
      ("Löschen mit einer Schaufel (auch dem {item:kaleidoscope_cookery:kitchen_shovel}), durch Regen oder Wasser darüber.",
       "Put it out with a shovel (also the {item:kaleidoscope_cookery:kitchen_shovel}), by rain or water above it."),
      ("Ein brennender Herd leuchtet und raucht.", "A burning stove glows and smokes.")]),
    ("pot", None, "kaleidoscope_cookery:pot", ("Kochen", "Cooking"),
     [("Im Wok entstehen die meisten Pfannengerichte. Er braucht Hitze von unten, z. B. einen brennenden {item:kaleidoscope_cookery:stove}.",
       "Most stir-fries are made in the wok. It needs heat from below, e.g. a burning {item:kaleidoscope_cookery:stove}.")],
     [("Öl hineingeben ({item:kaleidoscope_cookery:oil}, {item:kaleidoscope_cookery:oil_pot} oder {item:kaleidoscope_cookery:kitchen_shovel} mit Öl). "
       "Danach bleiben 60 Sekunden Zeit für bis zu 9 Zutaten.",
       "Add oil ({item:kaleidoscope_cookery:oil}, {item:kaleidoscope_cookery:oil_pot} or {item:kaleidoscope_cookery:kitchen_shovel} with oil). "
       "You then have 60 seconds for up to 9 ingredients."),
      ("Mit dem {item:kaleidoscope_cookery:kitchen_shovel} rühren startet das Kochen; jedes weitere Rühren zählt die nötigen Rührvorgänge herunter.",
       "Stirring with the {item:kaleidoscope_cookery:kitchen_shovel} starts cooking; every further stir counts down the required stirs."),
      ("Nicht genug gerührt ergibt ein {item:kaleidoscope_cookery:suspicious_stir_fry}, sonst das Gericht.",
       "Not stirred enough gives a {item:kaleidoscope_cookery:suspicious_stir_fry}, otherwise the dish."),
      ("Herausnehmen mit dem passenden Gefäß (z. B. {item:minecraft:bowl}) oder Schleichen + Pfannenwender. Nach 40 s brennt das Gericht an "
       "und wird zu {item:kaleidoscope_cookery:dark_cuisine}.",
       "Take it out with the matching container (e.g. {item:minecraft:bowl}) or sneak + spatula. After 40 s the dish burns and becomes "
       "{item:kaleidoscope_cookery:dark_cuisine}."),
      ("Mit der bloßen Hand in den heißen Wok greifen kostet ein halbes Herz.", "Reaching into the hot wok with your bare hand costs half a heart.")]),
    ("stockpot", None, "kaleidoscope_cookery:stockpot", ("Kochen", "Cooking"),
     [("Im Suppentopf werden Suppen und Eintöpfe gekocht.", "Soups and stews are cooked in the stockpot.")],
     [("Brühe hineingeben: {item:minecraft:water_bucket}, {item:minecraft:lava_bucket}, {item:minecraft:milk_bucket} oder ein Fischeimer.",
       "Add broth: {item:minecraft:water_bucket}, {item:minecraft:lava_bucket}, {item:minecraft:milk_bucket} or a fish bucket."),
      ("Bis zu 9 Zutaten hineinlegen.", "Put in up to 9 ingredients."),
      ("{item:kaleidoscope_cookery:stockpot_lid} drauf: auf einer Hitzequelle kocht die Suppe (Standard 15 s).",
       "Put the {item:kaleidoscope_cookery:stockpot_lid} on: on a heat source the soup cooks (default 15 s)."),
      ("Deckel ab und portionsweise herausnehmen (je nach Rezept mit {item:minecraft:bowl}), bis zu 9 Portionen.",
       "Take the lid off and take it out serving by serving (with a {item:minecraft:bowl} depending on the recipe), up to 9 servings."),
      ("Ohne passendes Rezept entsteht {item:minecraft:suspicious_stew}.", "Without a matching recipe you get {item:minecraft:suspicious_stew}.")]),
    ("steamer", None, "kaleidoscope_cookery:steamer", ("Kochen", "Cooking"),
     [("Der Dampfgarer gart Teigwaren und Gebäck. Er steht auf einem {item:kaleidoscope_cookery:pot} oder {item:kaleidoscope_cookery:stockpot} "
       "über Hitze oder direkt auf einer Hitzequelle.",
       "The steamer cooks dough and pastries. It stands on a {item:kaleidoscope_cookery:pot} or {item:kaleidoscope_cookery:stockpot} "
       "above heat or directly on a heat source.")],
     [("Eine halbe Lage hat 4 Plätze, eine volle Lage (zwei Körbe) 8. Mit einem weiteren Dampfgarer in der Hand stapelst du Lagen.",
       "A half layer has 4 slots, a full layer (two baskets) 8. With another steamer in hand you stack layers."),
      ("Gegart wird nur mit Deckel oben (Schleichen + Rechtsklick mit leerer Hand).", "It only cooks with a lid on top (sneak + right-click with an empty hand)."),
      ("Hitze direkt darunter ergibt Stufe 4, jede volle Lage darüber eine Stufe weniger.", "Heat directly below gives level 4, every full layer above one level less."),
      ("Ohne Hitze kühlt der Fortschritt langsam ab. Rechtsklick mit leerer Hand nimmt alles heraus.",
       "Without heat the progress slowly cools down. Right-click with an empty hand takes everything out.")]),
    ("teapot", None, "kaleidoscope_cookery:teapot", ("Getränke", "Drinks"),
     [("In der Teekanne zieht Tee, der anschließend in hingestellte Tassen gegossen wird.", "Tea steeps in the teapot and is then poured into placed cups.")],
     [("Teebasis einfüllen: {item:minecraft:water_bucket} oder {item:minecraft:milk_bucket}. Eine leere Kanne in der Hand schöpft auch direkt aus Wasser oder Lava.",
       "Fill in a tea base: {item:minecraft:water_bucket} or {item:minecraft:milk_bucket}. An empty pot in hand also scoops directly from water or lava."),
      ("Zutat (Teebeutel, Blumen …) hineinlegen und auf einer Hitzequelle ziehen lassen.", "Add an ingredient (tea bag, flowers …) and let it steep on a heat source."),
      ("Fertig: die Kanne aufnehmen und auf hingestellte {item:kaleidoscope_cookery:empty_cup} gießen. Eine Kanne reicht für 12 Tassen.",
       "Done: pick the pot up and pour it onto placed {item:kaleidoscope_cookery:empty_cup}. One pot fills 12 cups."),
      ("Tassen und Tee stellst du schleichend hin, bis zu 4 Tassen pro Block.", "Cups and tea are placed while sneaking, up to 4 cups per block."),
      ("Eine Zutat ohne passendes Rezept ergibt {item:kaleidoscope_cookery:mystery_tea}.", "An ingredient without a matching recipe gives {item:kaleidoscope_cookery:mystery_tea}.")]),
    ("chopping_board", None, "kaleidoscope_cookery:chopping_board", ("Vorbereitung", "Preparation"),
     [("Auf dem Schneidebrett zerteilst du Zutaten mit einem Küchenmesser.", "On the chopping board you cut ingredients with a kitchen knife.")],
     [("Rechtsklick mit einer Zutat legt ein Stück auf.", "Right-click with an ingredient puts one piece on it."),
      ("Linksklick mit einem Küchenmesser schneidet; das Modell ändert sich bei jedem Schnitt.", "Left-click with a kitchen knife cuts; the model changes with every cut."),
      ("Sind alle Schnitte gemacht, lässt ein Klick die Ergebnisse fallen.", "Once all cuts are done, a click drops the results."),
      ("Schleichen + Rechtsklick vor dem ersten Schnitt nimmt die Zutat zurück.", "Sneak + right-click before the first cut takes the ingredient back."),
      ("Die Verzauberung „Schnelles Messer“ verdoppelt bzw. vervierfacht die Schnitte pro Hieb.", "The Quick Knife enchantment doubles or quadruples the cuts per hit.")]),
    ("millstone", None, "kaleidoscope_cookery:millstone", ("Vorbereitung", "Preparation"),
     [("Der Mühlstein mahlt Getreide, Samen und Gewürze. Er wird von einem Tier angetrieben und belegt 2×2 Blöcke.",
       "The millstone grinds grain, seeds and spices. It is driven by an animal and occupies 2×2 blocks.")],
     [("Ein Tier (kein Spieler, nicht geritten), das auf den Mühlstein tritt, wird eingespannt und läuft im Kreis.",
       "An animal (not a player, not ridden) that steps onto the millstone is harnessed and walks in a circle."),
      ("Rechtsklick mit einer Zutat legt bis zu 8 Stück ein; auch auf den Stein geworfene Items werden eingelegt.",
       "Right-click with an ingredient adds up to 8 items; items thrown onto the stone are added too."),
      ("Nach jeder Runde fallen die Ergebnisse vorne heraus.", "After every round the results drop out at the front."),
      ("Das Tier wird gelöst, wenn es Schaden nimmt, stirbt, fällt oder sich zu weit entfernt.",
       "The animal is released when it takes damage, dies, falls or gets too far away.")]),
    ("bamboo_tray", None, "kaleidoscope_cookery:bamboo_tray", ("Vorbereitung", "Preparation"),
     [("Auf dem Bambustablett werden Zutaten getrocknet oder befeuchtet. Es hat 4 Plätze und lässt sich stapeln.",
       "On the bamboo tray ingredients are dried or wetted. It has 4 slots and can be stacked.")],
     [("Trocknen: freier Himmel ohne Regen (Glas und weitere Tabletts darüber sind erlaubt).", "Drying: open sky without rain (glass and further trays above are allowed)."),
      ("Befeuchten: Regen auf dem Tablett oder ein Tropfstein mit Wasser darüber.", "Wetting: rain on the tray or a dripstone with water above it."),
      ("Rechtsklick auf ein Viertel legt den ganzen Stapel ab, mit leerer Hand nimmst du ein Stück (Schleichen: alles).",
       "Right-click on a quarter puts the whole stack down, with an empty hand you take one item (sneaking: everything).")]),
    ("shawarma_spit", None, "kaleidoscope_cookery:shawarma_spit", ("Kochen", "Cooking"),
     [("Der Dönerspieß ist 2 Blöcke hoch und gart alles, was auch auf einem {item:minecraft:campfire} gart.",
       "The shawarma spit is 2 blocks tall and cooks everything that also cooks on a {item:minecraft:campfire}.")],
     [("Jede der beiden Ebenen nimmt bis zu 8 gleiche Zutaten.", "Each of the two levels takes up to 8 identical ingredients."),
      ("Gegart wird nur mit Redstone-Signal an einem der beiden Blöcke, dann dreht sich der Spieß.",
       "It only cooks with a redstone signal on one of the two blocks, then the spit rotates."),
      ("Rechtsklick ohne passende Zutat nimmt das Fertige heraus. Ist der Spieß an, verbrennst du dich dabei leicht.",
       "Right-click without a matching ingredient takes out the finished food. If the spit is on, you burn yourself slightly.")]),
    ("crafting", ("Werkbank & Schmiedetisch", "Crafting & smithing"), "minecraft:crafting_table", ("Herstellung", "Crafting"),
     [("Viele Werkzeuge, Stationen und einfache Gerichte werden in der {item:minecraft:crafting_table} hergestellt. Reisschalen entstehen formlos "
       "aus {item:kaleidoscope_cookery:cooked_rice} und einer Zutat.",
       "Many tools, stations and simple dishes are made in the {item:minecraft:crafting_table}. Rice bowls are made shapeless from "
       "{item:kaleidoscope_cookery:cooked_rice} and one ingredient.")], []),
    ("furnace", ("Ofen, Räucherofen & Lagerfeuer", "Furnace, smoker & campfire"), "minecraft:furnace", ("Herstellung", "Crafting"),
     [("Diese Rezepte funktionieren in den Vanilla-Öfen bzw. auf dem {item:minecraft:campfire}. {item:kaleidoscope_cookery:oil} brennt wie Kohle, "
       "der {item:kaleidoscope_cookery:oil_block} wie ein Kohleblock.",
       "These recipes work in the vanilla furnaces or on the {item:minecraft:campfire}. {item:kaleidoscope_cookery:oil} burns like coal, "
       "the {item:kaleidoscope_cookery:oil_block} like a coal block.")], []),
    ("grill", None, "kaleidoscope_grilling:grill", ("Grillen", "Grilling"),
     [("Auf dem Grill werden rohe Spieße gegart.", "Raw skewers are cooked on the grill.")],
     [("Anzünden mit {item:minecraft:flint_and_steel}, löschen mit einer Schaufel.", "Light it with {item:minecraft:flint_and_steel}, put it out with a shovel."),
      ("Bis zu 3 rohe Spieße (oder Geheimspieße) auf den brennenden Grill legen.", "Put up to 3 raw skewers (or secret skewers) on the burning grill."),
      ("Mit der {item:kaleidoscope_cookery:oil_pot} bestreichen (1 Öl pro Spieß), dann 4× mit leerer Hand wenden (jeweils 1 s Pause).",
       "Brush them with the {item:kaleidoscope_cookery:oil_pot} (1 oil per skewer), then turn them 4× with an empty hand (1 s pause each)."),
      ("Gar: optional mit {item:kaleidoscope_grilling:special_seasoning} würzen, dann mit leerer Hand herunternehmen (Schleichen: alle).",
       "Cooked: optionally season with {item:kaleidoscope_grilling:special_seasoning}, then take them off with an empty hand (sneaking: all)."),
      ("800 Ticks ohne Fortschritt: angebrannt ({item:kaleidoscope_grilling:dark_grilling}), nach weiteren 400 Ticks wird der Spieß zu Holzkohle.",
       "800 ticks without progress: burnt ({item:kaleidoscope_grilling:dark_grilling}), after another 400 ticks the skewer turns into charcoal.")]),
    ("skewering", ("Aufspießen", "Skewering"), "minecraft:stick", ("Grillen", "Grilling"),
     [("Spieße entstehen ohne Station direkt in der Hand.", "Skewers are made directly in your hands without a station.")],
     [("Einen {item:minecraft:stick} in die Nebenhand nehmen.", "Take a {item:minecraft:stick} into your off hand."),
      ("Mit einer Zutat in der Haupthand Rechtsklick: Sie wird aufgespießt. Passt die Reihenfolge zu einem Rezept, entsteht der feste rohe Spieß.",
       "Right-click with an ingredient in the main hand: it gets skewered. If the order matches a recipe, the fixed raw skewer is made."),
      ("Nach drei Zutaten ohne passendes Rezept entsteht ein {item:kaleidoscope_grilling:secret_skewer}.",
       "After three ingredients without a matching recipe you get a {item:kaleidoscope_grilling:secret_skewer}."),
      ("Schleichen + Rechtsklick nimmt den Spieß in der Nebenhand wieder auseinander.", "Sneak + right-click takes the skewer in your off hand apart again."),
      ("Das {item:kaleidoscope_grilling:skewer_recipe_book} merkt sich einen Spieß und spießt ihn später aus dem Inventar auf; es lässt sich auch an die Wand hängen.",
       "The {item:kaleidoscope_grilling:skewer_recipe_book} remembers a skewer and skewers it later from the inventory; it can also be hung on a wall.")]),
    ("oil_press", None, "kaleidoscope_grilling:oil_press", ("Grillen", "Grilling"),
     [("Aus {item:kaleidoscope_grilling:oil_cake} wird Rapsöl gepresst, das im {item:kaleidoscope_grilling:big_vat} gesammelt wird.",
       "Canola oil is pressed from {item:kaleidoscope_grilling:oil_cake} and collected in the {item:kaleidoscope_grilling:big_vat}.")],
     [("4 Ölkuchen in die Presse legen und mit einem {item:minecraft:anvil} (+4) oder Pressstein (+1) bis 16 pressen.",
       "Put 4 oil cakes into the press and press up to 16 with an {item:minecraft:anvil} (+4) or press stone (+1)."),
      ("Fertig: 4 Eimer Rapsöl laufen in ein großes Fass in der Nähe (±4 Blöcke), 4 {item:kaleidoscope_grilling:oil_residue} fallen vorne heraus.",
       "Done: 4 buckets of canola oil run into a big vat nearby (±4 blocks), 4 {item:kaleidoscope_grilling:oil_residue} drop out at the front."),
      ("Das Fass fasst 8 Eimer einer Flüssigkeit. Ölkannen werden daran mit 8 Öl pro Eimer gefüllt.",
       "The vat holds 8 buckets of one liquid. Oil pots are filled there with 8 oil per bucket."),
      ("Ölsorten: Rapsöl, Chiliöl, Magma-Chiliöl. Eine Ölkanne mit Ölsorte fasst 64, mit Fett 256.",
       "Oil types: canola oil, chili oil, magma chili oil. An oil pot with an oil type holds 64, with fat 256.")]),
    ("seasoning", ("Gewürze", "Seasoning"), "kaleidoscope_grilling:empty_seasoning_bottle", ("Grillen", "Grilling"),
     [("{item:kaleidoscope_grilling:special_seasoning} verfeinert Spieße auf dem Grill sowie Gerichte aus Wok und Suppentopf.",
       "{item:kaleidoscope_grilling:special_seasoning} refines skewers on the grill as well as dishes from the wok and stockpot.")],
     [("Gewürzflaschen hinstellen (bis 4 pro Block) und mit Gewürz-Zutaten befüllen (bis 8 pro Flasche).",
       "Place seasoning bottles (up to 4 per block) and fill them with seasoning ingredients (up to 8 per bottle)."),
      ("Sind {item:kaleidoscope_grilling:green_chili_powder}, {item:kaleidoscope_grilling:sichuan_pepper} und {item:kaleidoscope_grilling:onion_powder} drin, "
       "wird daraus eine {item:kaleidoscope_grilling:pending_seasoning}.",
       "Once {item:kaleidoscope_grilling:green_chili_powder}, {item:kaleidoscope_grilling:sichuan_pepper} and {item:kaleidoscope_grilling:onion_powder} "
       "are in, it becomes a {item:kaleidoscope_grilling:pending_seasoning}."),
      ("Die Mischung 4 s schütteln (gedrückt halten): Spezialgewürz mit 16 Anwendungen.", "Shake the mix for 4 s (hold right-click): special seasoning with 16 uses."),
      ("Im Wok oder Suppentopf würzt ein Klick alle Gerichte dieser Runde; jede Zutat im Gewürz bringt eine eigene Wirkung.",
       "In the wok or stockpot one click seasons all dishes of that round; every ingredient in the seasoning adds its own effect.")]),
]

OTHER_GUIDES = [
    (("Ölkanne, Fetttopf & Ölblock", "Oil pot, enamel basin & oil block"), "kaleidoscope_cookery:oil_pot",
     [("{item:kaleidoscope_cookery:oil} erhältst du, wenn du Schweine und Piglins mit einem Küchenmesser tötest.",
       "You get {item:kaleidoscope_cookery:oil} by killing pigs and piglins with a kitchen knife."),
      ("Die {item:kaleidoscope_cookery:oil_pot} speichert bis zu 256 Öl; hingestellt füllst du sie mit Öl nach und nimmst mit leerer Hand bis zu 64 heraus.",
       "The {item:kaleidoscope_cookery:oil_pot} stores up to 256 oil; when placed you refill it with oil and take out up to 64 with an empty hand."),
      ("Das {item:kaleidoscope_cookery:enamel_basin} fasst 32 Öl und hat einen Deckel; der {item:kaleidoscope_cookery:kitchen_shovel} nimmt dort ein Öl auf.",
       "The {item:kaleidoscope_cookery:enamel_basin} holds 32 oil and has a lid; the {item:kaleidoscope_cookery:kitchen_shovel} picks up one oil there.")]),
    (("Teller-Gerichte", "Plated dishes"), "kaleidoscope_cookery:braised_beef",
     [("Viele Gerichte lassen sich hinstellen und dann bissweise essen; nach dem letzten Biss bleibt das Geschirr übrig.",
       "Many dishes can be placed and then eaten bite by bite; after the last bite the dishware remains."),
      ("Große Gerichte belegen 2 Blöcke, der {item:kaleidoscope_cookery:cold_cut_ham_slices} sogar 3×3 Blöcke.",
       "Large dishes occupy 2 blocks, the {item:kaleidoscope_cookery:cold_cut_ham_slices} even 3×3 blocks."),
      ("Unangetastet abgebaut erhältst du das Gericht zurück.", "Broken untouched, you get the dish back.")]),
    (("Qualität", "Quality"), "kaleidoscope:cookbook",
     [("Flex-Rezepte (Wok und Suppentopf) bewerten die Mengen der Zutaten: Für jedes Rezept gibt es auf jedem Server ein verstecktes Mengenverhältnis.",
       "Flex recipes (wok and stockpot) rate the ingredient amounts: every recipe has a hidden ratio on every server."),
      ("Je näher du daran liegst, desto besser die Qualität – und desto mehr Nährwert und längere Effekte hat das Gericht.",
       "The closer you get, the better the quality – and the more nutrition and the longer effects the dish has.")]),
    (("Weitere Blöcke", "More blocks"), "kaleidoscope_cookery:trash_can",
     [("{item:kaleidoscope_cookery:kitchenware_racks}: hängt an der Wand und hält zwei Werkzeuge.", "{item:kaleidoscope_cookery:kitchenware_racks}: hangs on a wall and holds two tools."),
      ("{item:kaleidoscope_cookery:fruit_basket}: 8 Plätze, behält seinen Inhalt beim Abbauen.", "{item:kaleidoscope_cookery:fruit_basket}: 8 slots, keeps its contents when broken."),
      ("{item:kaleidoscope_cookery:trash_can}: wirft Stapel endgültig weg (die letzten 3 bleiben zurückholbar); wer aus über einem Block hineinfällt, versteckt sich darin.",
       "{item:kaleidoscope_cookery:trash_can}: throws stacks away for good (the last 3 can be taken back); whoever falls in from more than one block hides inside."),
      ("{item:kaleidoscope_cookery:recipe_item}: zeichnet die Zutaten von Wok oder Suppentopf auf und legt sie später aus dem Inventar ein.",
       "{item:kaleidoscope_cookery:recipe_item}: records the ingredients of a wok or stockpot and adds them later from your inventory."),
      ("{item:kaleidoscope_cookery:transmutation_lunch_bag}: fasst 24 Stapel Essen und isst so lange, bis du satt bist.",
       "{item:kaleidoscope_cookery:transmutation_lunch_bag}: holds 24 stacks of food and eats until you are full."),
      ("{item:kaleidoscope_cookery:scarecrow}: schützt Ackerland in 16 Blöcken vor dem Zertrampeln; Laterne, Werkzeug und Köpfe lassen sich anlegen.",
       "{item:kaleidoscope_cookery:scarecrow}: protects farmland within 16 blocks from trampling; lanterns, tools and heads can be equipped."),
      ("{item:kaleidoscope_cookery:clay_pot_milk_tea}: trinken entfernt alle schädlichen Effekte; er lässt sich auch als Deko hinstellen.",
       "{item:kaleidoscope_cookery:clay_pot_milk_tea}: drinking it removes all harmful effects; it can also be placed as decoration."),
      ("Grilling: {item:kaleidoscope_grilling:skewer_plate} (bis 5 Spieße) und {item:kaleidoscope_grilling:advanced_rack} (9 Fächer).",
       "Grilling: {item:kaleidoscope_grilling:skewer_plate} (up to 5 skewers) and {item:kaleidoscope_grilling:advanced_rack} (9 slots).")]),
]

# Crops: (title, seed, harvest, soil, details) - CropKind (Cookery) and GrillingCrops
CROPS = [
    (("Tomate", "Tomato"), "kaleidoscope_cookery:tomato_seed", ["kaleidoscope_cookery:tomato"], ("Ackerland", "Farmland"),
     ("8 Stufen. Reif per Rechtsklick ernten, die Pflanze wächst nach.", "8 stages. Harvest with right-click when ripe, the plant grows back.")),
    (("Chili", "Chili"), "kaleidoscope_cookery:chili_seed", ["kaleidoscope_cookery:red_chili", "kaleidoscope_cookery:green_chili"], ("Ackerland", "Farmland"),
     ("8 Stufen. Reif per Rechtsklick ernten (20 % grüne statt rote Chili) oder abbauen (20 % zusätzlich eine grüne).",
      "8 stages. Harvest with right-click when ripe (20% green instead of red chili) or break it (20% an extra green one).")),
    (("Salat", "Lettuce"), "kaleidoscope_cookery:lettuce_seed", ["kaleidoscope_cookery:lettuce", "kaleidoscope_cookery:caterpillar"], ("Ackerland", "Farmland"),
     ("8 Stufen. Nur durch Abbauen ernten; mit 10 % Chance sitzt eine Raupe darin.", "8 stages. Harvest only by breaking; with a 10% chance a caterpillar sits inside.")),
    (("Reis", "Rice"), "kaleidoscope_cookery:wild_rice", ["kaleidoscope_cookery:rice_panicle"], ("Ackerland unter einer Wasserquelle", "Farmland below a water source"),
     ("Wächst im Wasser und wird 3 Blöcke hoch. Ergibt 2–4 Reisrispen; gepflanzt wird mit Wildreis oder Reis. Die Hacke funktioniert auch unter Wasser.",
      "Grows in water and becomes 3 blocks tall. Gives 2–4 rice panicles; plant it with wild rice or rice. The hoe also works under water.")),
    (("Teestrauch", "Tea bush"), "kaleidoscope_cookery:tea_seed", ["kaleidoscope_cookery:fresh_tea_leaves"], ("Erde, Gras, Podsol, Moos u. Ä.", "Dirt, grass, podzol, moss etc."),
     ("6 Stufen. Reif per Rechtsklick ernten, der Strauch wächst nach.", "6 stages. Harvest with right-click when ripe, the bush grows back.")),
    (("Raps", "Canola"), "kaleidoscope_grilling:canola_seeds", ["kaleidoscope_grilling:canola_seeds"], ("Ackerland", "Farmland"),
     ("8 Stufen. Die Samen werden zu Rapspulver gemahlen oder zu Ölkuchen verarbeitet.", "8 stages. The seeds are ground into canola powder or made into oil cake.")),
    (("Zwiebel", "Onion"), "kaleidoscope_grilling:onion", ["kaleidoscope_grilling:onion"], ("Ackerland", "Farmland"),
     ("8 Stufen, Ernte durch Abbauen.", "8 stages, harvest by breaking.")),
    (("Süßkartoffel", "Sweet potato"), "kaleidoscope_grilling:sweet_potato", ["kaleidoscope_grilling:sweet_potato"], ("Ackerland", "Farmland"),
     ("8 Stufen, reif gibt es mindestens 2 zusätzliche Süßkartoffeln.", "8 stages, ripe it gives at least 2 extra sweet potatoes.")),
    (("Fischminze", "Houttuynia"), "kaleidoscope_grilling:houttuynia", ["kaleidoscope_grilling:houttuynia"], ("Ackerland oder Seelensand", "Farmland or soul sand"),
     ("8 Stufen. Auf Seelensand (sonst zu 30 %) färbt sie sich ab Stufe 5 rot.", "8 stages. On soul sand (otherwise with 30%) it turns red from stage 5.")),
    (("Sichuanpfeffer", "Sichuan pepper"), "kaleidoscope_grilling:sichuan_pepper_seeds", ["kaleidoscope_grilling:sichuan_pepper"], ("Ackerland", "Farmland"),
     ("Strauch mit 6 Stufen. Reif per Rechtsklick 1–2 Pfeffer ernten, danach blüht er und reift erneut.",
      "Bush with 6 stages. Harvest 1–2 peppers with right-click when ripe, then it flowers and ripens again.")),
]

GRASS_SEEDS = ["kaleidoscope_cookery:tomato_seed", "kaleidoscope_cookery:chili_seed", "kaleidoscope_cookery:lettuce_seed",
               "kaleidoscope_cookery:wild_rice", "kaleidoscope_cookery:tea_seed", "kaleidoscope_grilling:canola_seeds",
               "kaleidoscope_grilling:sweet_potato", "kaleidoscope_grilling:onion", "kaleidoscope_grilling:sichuan_pepper_seeds"]


def sources(config: dict) -> dict:
    """Item -> where it is found (drops, loot, harvest). From OilDropListener, CropListener, GrillingDrops, CropStation."""
    found = {}

    def add(item, de, en):
        found.setdefault(item, []).append((de, en))

    oil = config.get("oil-drops", {})
    if oil.get("enabled", True):
        mobs = ", ".join(f"{{entity:minecraft:{m.lower()}}}" for m in oil.get("entities", []))
        amount = f"{oil.get('min', 1)}–{oil.get('max', 2)} {{item:kaleidoscope_cookery:oil}}"
        add("kaleidoscope_cookery:oil", f"Mit einem Küchenmesser töten: {mobs} – {amount} (Plünderung erhöht).",
            f"Kill with a kitchen knife: {mobs} – {amount} (Looting increases it).")
    add("kaleidoscope_cookery:raw_donkey_meat", "Einen {entity:minecraft:donkey} mit einem Küchenmesser töten: 2–4 Stück (Plünderung erhöht).",
        "Kill a {entity:minecraft:donkey} with a kitchen knife: 2–4 pieces (Looting increases it).")
    add("kaleidoscope_grilling:chicken_wing", "Ein {entity:minecraft:chicken} mit einem Küchenmesser töten: 1–2 Stück.",
        "Kill a {entity:minecraft:chicken} with a kitchen knife: 1–2 pieces.")
    add("kaleidoscope_cookery:raw_cow_offal", "Eine {entity:minecraft:cow} mit einem Küchenmesser töten: 1–2 Stück.",
        "Kill a {entity:minecraft:cow} with a kitchen knife: 1–2 pieces.")
    add("kaleidoscope_grilling:squid_tentacle", "Einen {entity:minecraft:squid} mit einem Küchenmesser töten: zu 50 % 2–3 Stück.",
        "Kill a {entity:minecraft:squid} with a kitchen knife: 50% chance for 2–3 pieces.")
    for seed in GRASS_SEEDS:
        add(seed, "{item:minecraft:short_grass} abbauen, während du einen {item:kaleidoscope_cookery:straw_hat} trägst (12,5 % je Samenart).",
            "Break {item:minecraft:short_grass} while wearing a {item:kaleidoscope_cookery:straw_hat} (12.5% per seed type).")
    add("kaleidoscope_grilling:houttuynia", "Truhen in Netherfestungen (65 %, 1–3 Stück).", "Nether fortress chests (65%, 1–3 pieces).")
    add("kaleidoscope_grilling:houttuynia", "Wächst in neu erzeugten Netherfestungen an einem Viertel der Netherwarzen-Stellen.",
        "Grows in newly generated Nether fortresses at a quarter of the nether wart spots.")
    add("kaleidoscope_grilling:sichuan_pepper", "Dorf-Truhen (40 %, 3–10 Stück).", "Village chests (40%, 3–10 pieces).")
    add("kaleidoscope_grilling:sichuan_pepper_seeds", "Dorf-Truhen (20 %).", "Village chests (20%).")
    for (title_de, title_en), seed, harvest, (soil_de, soil_en), _ in CROPS:
        for item in harvest:
            add(item, f"Ernte von {title_de} (auf {soil_de}).", f"Harvest of {title_en} (on {soil_en.lower()}).")
    add("kaleidoscope_cookery:red_chili", "Von einer hängenden {item:kaleidoscope_cookery:chili_ristra} ernten.",
        "Harvest it from a hanging {item:kaleidoscope_cookery:chili_ristra}.")
    add("kaleidoscope_grilling:chicken_skin", "{item:minecraft:chicken} auf dem {item:kaleidoscope_cookery:chopping_board} zerteilen: zusätzlich 1–3 Stück.",
        "Cut {item:minecraft:chicken} on the {item:kaleidoscope_cookery:chopping_board}: 1–3 extra pieces.")
    add("kaleidoscope_grilling:dragon_egg_powder", "Rechtsklick mit einem Küchenmesser auf ein {item:minecraft:dragon_egg} (1 Stück, Plünderung erhöht; kostet 1 Haltbarkeit).",
        "Right-click a {item:minecraft:dragon_egg} with a kitchen knife (1 piece, Looting increases it; costs 1 durability).")
    add("kaleidoscope_cookery:suspicious_stir_fry", "{item:kaleidoscope_cookery:pot}: Zutaten ohne passendes Rezept oder nicht genug gerührt.",
        "{item:kaleidoscope_cookery:pot}: ingredients without a matching recipe, or not stirred enough.")
    add("kaleidoscope_cookery:dark_cuisine", "{item:kaleidoscope_cookery:pot}: ein fertiges Gericht nicht rechtzeitig herausgenommen – es brennt an.",
        "{item:kaleidoscope_cookery:pot}: a finished dish not taken out in time – it burns.")
    add("kaleidoscope_cookery:mystery_tea", "{item:kaleidoscope_cookery:teapot}: eine Zutat ohne passendes Rezept ergibt 4 Tassen davon.",
        "{item:kaleidoscope_cookery:teapot}: an ingredient without a matching recipe gives 4 cups of it.")
    add("kaleidoscope_grilling:mysterious_skewer", "{item:kaleidoscope_grilling:grill}: ein Spieß ohne gegrillte Form.",
        "{item:kaleidoscope_grilling:grill}: a skewer without a grilled form.")
    add("kaleidoscope_grilling:dark_grilling", "{item:kaleidoscope_grilling:grill}: einen Spieß zu lange liegen lassen – er brennt an.",
        "{item:kaleidoscope_grilling:grill}: leaving a skewer on too long – it burns.")
    add("kaleidoscope_grilling:unfinished_skewer", "Entsteht beim Aufspießen, solange noch nicht alle Zutaten auf dem Stock sind.",
        "Made while skewering, as long as not all ingredients are on the stick yet.")
    add("kaleidoscope_grilling:pending_seasoning", "Gewürzflasche mit grünem Chilipulver, Sichuanpfeffer und Zwiebelpulver; 4 s schütteln ergibt Spezialgewürz.",
        "Seasoning bottle with green chili powder, Sichuan pepper and onion powder; shaking it for 4 s gives special seasoning.")
    add("kaleidoscope_grilling:skewer_plate", "Schleichen + Rechtsklick mit einem Spieß auf die Oberseite eines festen Blocks.",
        "Sneak + right-click with a skewer on the top of a solid block.")
    add("kaleidoscope_grilling:skewer_recipe_book", "{item:minecraft:crafting_table}: {item:kaleidoscope_cookery:recipe_item} + roher Spieß (oder Geheimspieß); der Spieß bleibt im Raster.",
        "{item:minecraft:crafting_table}: {item:kaleidoscope_cookery:recipe_item} + raw skewer (or secret skewer); the skewer stays in the grid.")
    for brush in ("canola_oil_brush", "secret_chili_oil_brush", "premium_chili_oil_brush"):
        add(f"kaleidoscope_grilling:{brush}", "Nur Darstellung: so sieht die Ölkanne beim Bestreichen am Grill aus. Nicht als Item erhältlich.",
            "Display only: this is how the oil pot looks while brushing on the grill. Not obtainable as an item.")
    add("kaleidoscope_grilling:wedding_candy", "Event-Item des Mods (September 2026), auf dem Server nicht erhältlich.",
        "Event item of the mod (September 2026), not obtainable on the server.")
    add("kaleidoscope_cookery:suspicious_stir_fry_rice_bowl", "Im Mod nur über den Koch-Dorfbewohner erhältlich, den es auf dem Server (noch) nicht gibt.",
        "In the mod only obtainable from the chef villager, who does not exist on the server (yet).")
    for block in ("oil_block", "straw_block"):
        add(f"kaleidoscope_cookery:{block}", "Das Rezept ist auf diesem Server abgeschaltet.", "The recipe is disabled on this server.")
    return found


# Effect descriptions missing in the language files (behaviour as implemented in EffectManager / GrillingEffects)
EFFECT_DESCRIPTIONS = {
    "kaleidoscope_cookery:hinder": ("Deine Treffer verlangsamen das Ziel (Langsamkeit II für 5 Sekunden).", "Your hits slow the target (Slowness II for 5 seconds)."),
    "kaleidoscope_cookery:instant_smelting": ("Abgebaute Erze lassen ihre Drops gleich geschmolzen fallen – je Stufe ein Drop mehr.",
                                              "Mined ores drop their drops already smelted – one more drop per level."),
    "kaleidoscope_cookery:projectile_dodge": ("Pfeile und andere Geschosse verfehlen dich; du weichst mit einem kurzen Teleport aus. Jedes Ausweichen kostet 10 Sekunden Effektdauer.",
                                              "Arrows and other projectiles miss you; you dodge with a short teleport. Every dodge costs 10 seconds of effect duration."),
    "kaleidoscope_cookery:vitality": ("Erwachsene Tiere, die du tötest, hinterlassen ein Jungtier; Zombies einen Baby-Zombie (zu 5 % ein Dorfbewohner-Kind).",
                                      "Adult animals you kill leave a baby; zombies a baby zombie (5% a villager child)."),
    "kaleidoscope_grilling:dragon_blood": ("Mehr maximale Gesundheit: +3 Herzen, auf Stufe II +5 Herzen.", "More max health: +3 hearts, +5 hearts at level II."),
    "kaleidoscope_grilling:heavy_metal": ("Verhindert einmal den Tod: tödlicher Schaden lässt dich mit einem halben Herz überleben. Danach folgt Schwermetallvergiftung.",
                                          "Prevents death once: fatal damage leaves you with half a heart. Heavy metal poisoning follows."),
    "kaleidoscope_grilling:heavy_metal_poisoning": ("Abklingzeit von Schwermetall (10 Minuten): solange schützt Schwermetall nicht erneut vor dem Tod.",
                                                    "Cooldown of heavy metal (10 minutes): meanwhile heavy metal does not protect you from death again."),
    "kaleidoscope_grilling:invincible": ("Du nimmst keinerlei Schaden (außer durch /kill); Funken zeigen den Schutz an.", "You take no damage at all (except from /kill); sparks show the protection."),
    "kaleidoscope_grilling:numb": ("Scharfer Sichuanpfeffer betäubt die Zunge. Im Mod wackelt das Fadenkreuz; auf dem Server wird der Effekt nur angezeigt.",
                                   "Spicy Sichuan pepper numbs your tongue. In the mod the crosshair wobbles; on the server the effect is only displayed."),
}

TAB_NAMES = {"kitchen": ("Küche & Werkzeuge", "Kitchen & tools"), "food": ("Essen & Zutaten", "Food & ingredients"),
             "furniture": ("Möbel & Deko", "Furniture & decoration"), "grilling": ("Grillen", "Grilling")}

FURNITURE = ("chair", "table", "cook_stool", "long_bench", "eight_immortals", "red_lantern", "tea_banner")

# ---------------------------------------------------------------- HTML


def e(text) -> str:
    return html.escape(str(text), quote=True)


def slug(item: str) -> str:
    return item.replace(":", "/")


def L(de: str, en: str) -> str:
    """Wiki text in both languages (already HTML)."""
    if de == en:
        return de
    return f'<span class="l-de">{de}</span><span class="l-en">{en}</span>'


def N(de: str, en: str) -> str:
    """Name in both languages (already escaped)."""
    if de == en:
        return de
    return f'<span class="n-de">{de}</span><span class="n-en">{en}</span>'


class Site:
    def __init__(self, data: Data, recipes: list, out: Path):
        self.data = data
        self.recipes = recipes
        self.out = out
        self.icons_written = set()
        self.by_output = {}
        self.by_input = {}
        for recipe in recipes:
            for item, _, _ in recipe.outputs:
                self.by_output.setdefault(item, []).append(recipe)
            for item in recipe.items_in():
                self.by_input.setdefault(item, []).append(recipe)
        self.stations = {s[0]: s for s in STATIONS}
        self.search = []
        self.sources = sources(data.config)

    # ---- Helpers ----

    def name(self, item: str) -> str:
        return N(e(self.data.name(item, "de")), e(self.data.name(item, "en")))

    def tip(self, item: str) -> str:
        return f'data-tip-de="{e(self.data.name(item, "de"))}" data-tip-en="{e(self.data.name(item, "en"))}"'

    def rich(self, pair, root: str) -> str:
        """(German, English) text with {item:..}/{entity:..}/{effect:..} placeholders -> bilingual HTML."""
        def render(text):
            def repl(match):
                kind, key = match.group(1), match.group(2)
                if kind == "item":
                    link = self.item_link(key, root)
                    label = self.name(key)
                    return f'<a href="{e(link)}">{label}</a>' if link else label
                return N(e(self.data.keyed(kind, key, "de")), e(self.data.keyed(kind, key, "en")))
            return re.sub(r"\{(item|entity|effect):([a-z_]+:[a-z_0-9/]+)\}", repl, e(text))
        return L(render(pair[0]), render(pair[1]))

    def station_title(self, station) -> str:
        sid, title, icon = station[0], station[1], station[2]
        return self.name(icon) if title is None else L(e(title[0]), e(title[1]))

    def station_title_text(self, station, code: str) -> str:
        title = station[1]
        return self.data.name(station[2], code) if title is None else title[0 if code == "de" else 1]

    def icon(self, item: str, root: str, size: int = 32, cls: str = "icon") -> str:
        path = f"assets/icons/{slug(item)}.png"
        if item not in self.icons_written:
            self.icons_written.add(item)
            source = self.data.icon_files.get(item)
            raw = self.data.read_asset(source) if source else None
            if raw is None and item.startswith("minecraft:"):
                raw = self.data.read_asset(f"assets/minecraft/textures/item/{item[10:]}.png") or \
                    self.data.read_asset(f"assets/minecraft/textures/block/{item[10:]}.png")
            if raw is not None:
                target = self.out / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(first_frame(raw))
        if not (self.out / path).exists():
            return f'<span class="{cls} missing" style="width:{size}px;height:{size}px"></span>'
        return f'<img class="{cls}" src="{root}{path}" width="{size}" height="{size}" alt="" loading="lazy">'

    def item_link(self, item: str, root: str):
        return f"{root}items/{slug(item)}.html" if item in self.data.items else None

    def slot(self, alts, count: int, root: str, big: bool = False) -> str:
        if not alts:
            return '<span class="slot empty"></span>'
        item = alts[0]
        if len(alts) == 1:
            tip = self.tip(item)
        else:
            names = {code: [self.data.name(a, code) for a in alts[:12]] for code in ("de", "en")}
            more = " …" if len(alts) > 12 else ""
            tip = (f'data-tip-de="{e("Eines von: " + ", ".join(names["de"]) + more)}" '
                   f'data-tip-en="{e("One of: " + ", ".join(names["en"]) + more)}"')
        link = self.item_link(item, root)
        inner = self.icon(item, root, 32)
        if len(alts) > 1:
            inner += '<span class="slot-tag">#</span>'
        if count > 1:
            inner += f'<span class="slot-count">{count}</span>'
        cls = "slot big" if big else "slot"
        if link:
            return f'<a class="{cls}" href="{e(link)}" {tip}>{inner}</a>'
        return f'<span class="{cls}" {tip}>{inner}</span>'

    def recipe_card(self, recipe: Recipe, root: str) -> str:
        station = self.stations.get(recipe.station)
        if recipe.grid:
            rows = "".join(f'<div class="grid-row">{"".join(self.slot(a or [], 1, root) for a in recipe.grid[i * 3:i * 3 + 3])}</div>' for i in range(3))
            left = f'<div class="grid3">{rows}</div>'
        else:
            left = '<div class="inputs">' + "".join(self.slot(a, n, root) for a, n in recipe.inputs) + "</div>"
        middle = '<div class="arrow"><span></span></div>'
        if recipe.extra:
            label = L(e(recipe.extra[0][0]), e(recipe.extra[0][1]))
            middle = f'<div class="via">{self.slot([recipe.extra[1]], 1, root)}<small>{label}</small></div>' + middle
        outputs = ""
        for item, count, chance in recipe.outputs:
            outputs += self.slot([item], count, root, big=True)
            if chance < 1:
                outputs += f'<small class="chance">{chance * 100:g} %</small>'
        notes = "".join(f"<li>{self.rich(n, root)}</li>" for n in recipe.notes)
        station_html = ""
        if station:
            station_html = f'<a class="card-station" href="{root}stations/{station[0]}.html">{self.icon(station[2], root, 16, "icon-sm")}{self.station_title(station)}</a>'
        names = " ".join(self.data.name(o[0], c) for o in recipe.outputs for c in ("de", "en"))
        return (f'<div class="recipe panel" data-name="{e(names.lower())}">{station_html}<div class="recipe-body">{left}{middle}'
                f'<div class="outputs">{outputs}</div></div>{f"<ul class=notes>{notes}</ul>" if notes else ""}</div>')

    def page(self, path: str, title: tuple, body: str, depth: int, active: str = ""):
        root = "../" * depth
        nav = [("index.html", ("Start", "Home"), "home"), ("einstieg.html", ("Einstieg", "Getting started"), "start"),
               ("stations.html", ("Stationen", "Stations"), "stations"), ("items.html", ("Items", "Items"), "items"),
               ("anbau.html", ("Anbau", "Farming"), "farming"), ("effects.html", ("Effekte", "Effects"), "effects"),
               ("guides.html", ("Anleitungen", "Guides"), "guides")]
        links = "".join(f'<a class="nav-link{" active" if key == active else ""}" href="{root}{href}">{L(*label)}</a>' for href, label, key in nav)
        doc = f"""<!DOCTYPE html>
<html lang="de" data-title-de="{e(title[0])} – Kaleidoscope Wiki" data-title-en="{e(title[1])} – Kaleidoscope Wiki">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(title[0])} – Kaleidoscope Wiki</title>
<script>{SETTINGS_BOOT}</script>
<link rel="icon" href="{root}assets/icons/kaleidoscope/cookbook.png">
<link rel="stylesheet" href="{root}assets/style.css">
</head>
<body>
<header class="topbar">
  <a class="brand" href="{root}index.html">{self.icon("kaleidoscope:cookbook", root, 32, "brand-icon")}<span>Kaleidoscope<b>Wiki</b></span></a>
  <nav class="nav">{links}</nav>
  <div class="search"><input id="search" type="search" data-ph-de="Item oder Station suchen …" data-ph-en="Search item or station …" autocomplete="off"><div id="search-results" class="panel"></div></div>
  <div class="settings">
    <button id="settings-button" class="settings-button" aria-label="Einstellungen / Settings">{L("Sprache", "Language")} ▾</button>
    <div id="settings-panel" class="panel settings-panel">
      <div class="setting"><span>{L("Sprache des Wikis", "Wiki language")}</span>
        <div class="toggle" data-setting="ui"><button data-value="de">Deutsch</button><button data-value="en">English</button></div></div>
      <div class="setting"><span>{L("Namen von Items, Blöcken &amp; Effekten", "Names of items, blocks &amp; effects")}</span>
        <div class="toggle" data-setting="names"><button data-value="de">Deutsch</button><button data-value="en">English</button></div></div>
      <p class="muted small">{L("Wird in diesem Browser gespeichert.", "Saved in this browser.")}</p>
    </div>
  </div>
</header>
<main class="content">
{body}
</main>
<footer class="footer">{L("Kaleidoscope Cookery &amp; Grilling – Paper-Plugin nach den Mods von Kaleidoscope Official Production Team und breezeth. Assets unter CC BY-NC-SA 4.0. Keine offizielle Seite der Mod-Autoren.",
                          "Kaleidoscope Cookery &amp; Grilling – Paper plugin based on the mods by Kaleidoscope Official Production Team and breezeth. Assets under CC BY-NC-SA 4.0. Not an official page of the mod authors.")}</footer>
<div id="tooltip" class="mc-tooltip"></div>
<script>window.WIKI_ROOT = "{root}";</script>
<script src="{root}assets/search-index.js"></script>
<script src="{root}assets/wiki.js"></script>
</body>
</html>
"""
        target = self.out / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(doc, encoding="utf-8")

    # ---- Pages ----

    def item_pages(self):
        for item, value in sorted(self.data.items.items()):
            root = "../../"
            names = (self.data.name(item, "de"), self.data.name(item, "en"))
            tooltip = {}
            for code in ("de", "en"):
                lang = self.data.lang[code]
                lore = [l for l in lang.get(value.get("tooltip", ""), "").split("\n") if l.strip()] if value.get("tooltip") else []
                maxims = [lang.get(f"tooltip.{item.replace(':', '.')}.maxim.line{i}", "") for i in range(1, int(value.get("maxim-lines", 0)) + 1)]
                tooltip[code] = (f'<div class="tt-name">{e(names[0 if code == "de" else 1])}</div>' + "".join(f'<div class="tt-lore">{e(l)}</div>' for l in lore)
                                 + "".join(f'<div class="tt-maxim">{e(m)}</div>' for m in maxims if m))
            stats = []
            food = value.get("food")
            if food:
                n = food.get("nutrition", 0)
                if n:
                    stats.append((("Hunger", "Hunger"), L(f"{n} ({de_num(f'{n / 2:g}')} Keulen)", f"{n} ({n / 2:g} drumsticks)")))
                if food.get("saturation"):
                    stats.append((("Sättigung", "Saturation"), L(de_num(f"{food['saturation']:g}"), f"{food['saturation']:g}")))
                if food.get("drink"):
                    stats.append((("Art", "Type"), L("Getränk", "Drink")))
                if food.get("eat-seconds"):
                    s = f"{food['eat-seconds']:g}"
                    stats.append((("Essdauer", "Eating time"), L(f"{de_num(s)} s", f"{s} s")))
                if food.get("remainder"):
                    stats.append((("Rest", "Leftover"), self.name(food["remainder"])))
            plate = value.get("plate")
            if plate:
                size = {"1x2": (", 2 Blöcke", ", 2 blocks"), "3x3": (", 3×3 Blöcke", ", 3×3 blocks")}.get(plate.get("size"), ("", ""))
                bites = plate.get("max-bites", 3)
                stats.append((("Hinstellbar", "Placeable"), L(f"{bites} Bisse{size[0]}", f"{bites} bites{size[1]}")))
                pf = plate.get("food", {})
                if pf.get("nutrition"):
                    sat = f"{pf.get('saturation', 0):g}"
                    stats.append((("Pro Biss", "Per bite"), L(f"{pf['nutrition']} Hunger, {de_num(sat)} Sättigung", f"{pf['nutrition']} hunger, {sat} saturation")))
            stats.append((("Stapel", "Stack"), str(value.get("max-stack", 64))))
            stats.append((("ID", "ID"), f"<code>{e(item)}</code>"))
            effects = self.effect_list(food.get("effects", []) if food else [], root)
            made = self.by_output.get(item, [])
            used = [r for r in self.by_input.get(item, []) if r not in made]
            found = self.sources.get(item, [])
            if made:
                made_html = "".join(self.recipe_card(r, root) for r in made[:40])
            elif found:
                made_html = ""
            elif any(f"kaleidoscope_cookery:{p}" in item for p in FURNITURE):
                made_html = f'<p class="muted">{L("Möbel und Deko sind noch ohne Funktion und derzeit nicht herstellbar.", "Furniture and decoration have no function yet and cannot be crafted at the moment.")}</p>'
            else:
                made_html = f'<p class="muted">{L("Dieses Item lässt sich nicht herstellen.", "This item cannot be crafted.")}</p>'
            used_html = "".join(self.recipe_card(r, root) for r in used[:60]) or f'<p class="muted">{L("Wird in keinem Rezept verwendet.", "Not used in any recipe.")}</p>'
            stat_rows = "".join(f"<tr><th>{L(*k)}</th><td>{v}</td></tr>" for k, v in stats)
            found_html = "".join(f"<li>{self.rich(t, root)}</li>" for t in found)
            body = f"""
<nav class="crumbs"><a href="{root}items.html">{L("Items", "Items")}</a> › {self.name(item)}</nav>
<section class="item-hero panel">
  <div class="item-hero-icon">{self.icon(item, root, 96, "icon-xl")}</div>
  <div class="item-hero-text">
    <h1>{self.name(item)}</h1>
    <div class="mc-tooltip static"><div class="n-de">{tooltip["de"]}</div><div class="n-en">{tooltip["en"]}</div></div>
  </div>
  <table class="stats">{stat_rows}</table>
</section>
{f'<section><h2>{L("Effekte beim Essen", "Effects when eaten")}</h2>{effects}</section>' if effects else ''}
{f'<section><h2>{L("Fundorte", "Where to find")}</h2><ul class="panel sources">{found_html}</ul></section>' if found_html else ''}
{f'<section><h2>{L("Herstellung", "How to make")}</h2><div class="recipes">{made_html}</div></section>' if made_html else ''}
<section><h2>{L("Verwendung", "Used in")}</h2><div class="recipes">{used_html}</div></section>
"""
            self.page(f"items/{slug(item)}.html", names, body, 2, "items")
            self.search.append({"d": names[0], "e": names[1], "u": f"items/{slug(item)}.html", "i": f"assets/icons/{slug(item)}.png", "k": "item"})

    def effect_list(self, effects: list, root: str) -> str:
        rows = []
        for effect in effects:
            key = effect["effect"]
            ns, path = key.split(":")
            level = int(effect.get("amplifier", 0))
            roman = ["", " II", " III", " IV", " V"][level] if level < 5 else f" {level + 1}"
            name = N(e(self.data.keyed("effect", key, "de")), e(self.data.keyed("effect", key, "en"))) + roman
            label = f'<a href="{root}effects.html#{ns}-{path}">{name}</a>' if ns.startswith("kaleidoscope") else name
            chance = float(effect.get("probability", 1))
            s = seconds(effect.get("duration", 0))
            extra = f" · {chance * 100:g} %" if chance < 1 else ""
            rows.append(f'<li><span class="effect-name">{label}</span><span class="muted">{L(de_num(s), s)} s{extra}</span></li>')
        return f'<ul class="effects panel">{"".join(rows)}</ul>' if rows else ""

    def station_pages(self):
        cards = []
        for station in STATIONS:
            sid, _, icon, group, intro, steps = station
            root = "../"
            seen, unique = set(), []
            for r in (r for r in self.recipes if r.station == sid):
                key = (tuple(o[0] for o in r.outputs), tuple(tuple(a) for a, _ in r.inputs))
                if key not in seen:
                    seen.add(key)
                    unique.append(r)
            title = self.station_title(station)
            intro_html = "".join(f"<p>{self.rich(p, root)}</p>" for p in intro)
            steps_html = f'<ol class="steps">{"".join(f"<li>{self.rich(s, root)}</li>" for s in steps)}</ol>' if steps else ""
            body = f"""
<nav class="crumbs"><a href="{root}stations.html">{L("Stationen", "Stations")}</a> › {title}</nav>
<section class="station-hero panel">{self.icon(icon, root, 64, "icon-lg")}<div><span class="badge">{L(*group)}</span><h1>{title}</h1>{intro_html}</div></section>
{f'<section><h2>{L("So funktioniert es", "How it works")}</h2><div class="panel guide">{steps_html}</div></section>' if steps else ''}
"""
            if unique:
                body += f"""<section><h2>{L("Rezepte", "Recipes")} <span class="count">{len(unique)}</span></h2>
<input class="filter" type="search" data-ph-de="Rezepte filtern …" data-ph-en="Filter recipes …" data-filter=".recipes > .recipe">
<div class="recipes">{"".join(self.recipe_card(r, root) for r in unique)}</div></section>
"""
            titles = (self.station_title_text(station, "de"), self.station_title_text(station, "en"))
            self.page(f"stations/{sid}.html", titles, body, 1, "stations")
            self.search.append({"d": titles[0], "e": titles[1], "u": f"stations/{sid}.html", "i": f"assets/icons/{slug(icon)}.png", "k": "station"})
            count = L(f"{len(unique)} Rezepte", f"{len(unique)} recipes") if unique else L("Hitzequelle", "Heat source")
            cards.append((group, f'<a class="card panel" href="stations/{sid}.html">{self.icon(icon, "", 48, "icon-lg")}'
                                 f'<span class="card-title">{title}</span><span class="muted">{count}</span></a>'))
        groups = {}
        for group, card in cards:
            groups.setdefault(group, []).append(card)
        body = f'<h1>{L("Stationen", "Stations")}</h1><p class="lead">{L("Alle Kochstationen mit Anleitung und sämtlichen Rezepten.", "All cooking stations with instructions and every recipe.")}</p>'
        for group, items in groups.items():
            body += f'<section><h2>{L(*group)}</h2><div class="cards">{"".join(items)}</div></section>'
        self.page("stations.html", ("Stationen", "Stations"), body, 0, "stations")
        return groups

    def items_page(self):
        listed = set()
        sections = []
        for tab, items in self.data.tabs.items():
            entries = [i for i in items if i in self.data.items]
            listed |= set(entries)
            sections.append((TAB_NAMES.get(tab, (tab, tab)), entries))
        rest = sorted(i for i in self.data.items if i not in listed)
        if rest:
            sections.append((("Sonstiges", "Other"), rest))
        body = (f'<h1>{L("Items", "Items")}</h1><p class="lead">{L("Alle Items beider Plugins. Klicke ein Item für Werte, Herstellung und Verwendung.", "All items of both plugins. Click an item for its values, recipes and uses.")}</p>'
                '<input class="filter big" type="search" data-ph-de="Items filtern …" data-ph-en="Filter items …" data-filter=".item-grid > a">')
        for title, entries in sections:
            tiles = "".join(f'<a class="item-tile" href="items/{slug(i)}.html" {self.tip(i)} '
                            f'data-name="{e((self.data.name(i, "de") + " " + self.data.name(i, "en")).lower())}">{self.icon(i, "", 32)}<span>{self.name(i)}</span></a>'
                            for i in entries)
            body += f'<section><h2>{L(*title)} <span class="count">{len(entries)}</span></h2><div class="item-grid">{tiles}</div></section>'
        self.page("items.html", ("Items", "Items"), body, 0, "items")

    def effects_page(self):
        users = {}
        for item, value in self.data.items.items():
            for effect in (value.get("food") or {}).get("effects", []):
                users.setdefault(effect["effect"], []).append(item)
        keys = sorted({k for k in self.data.lang["en"] if re.match(r"effect\.kaleidoscope_(cookery|grilling)\.[a-z_]+$", k)})
        rows = ""
        for key in keys:
            _, ns, path = key.split(".")
            effect_id = f"{ns}:{path}"
            if f"{key}.description" in self.data.lang["en"]:
                desc = (self.data.lang["de"][f"{key}.description"], self.data.lang["en"][f"{key}.description"])
            else:
                desc = EFFECT_DESCRIPTIONS.get(effect_id, ("–", "–"))
            foods = "".join(f'<a class="mini" href="items/{slug(i)}.html" {self.tip(i)}>{self.icon(i, "", 24, "icon-sm")}</a>' for i in users.get(effect_id, []))
            name = N(e(self.data.lang["de"].get(key, path)), e(self.data.lang["en"].get(key, path)))
            rows += (f'<div class="effect-card panel" id="{ns}-{path}"><h3>{name}</h3><p>{L(e(desc[0]), e(desc[1]))}</p>'
                     f'{f"<div class=minis>{foods}</div>" if foods else ""}</div>')
        body = (f'<h1>{L("Effekte", "Effects")}</h1><p class="lead">'
                + L("Eigene Effekte der Mods. Sie stehen während der Wirkung in der Seitenleiste rechts, weil Minecraft eigene Effekte nicht im Inventar anzeigen kann.",
                    "Custom effects of the mods. While active they are listed in the sidebar on the right, because Minecraft cannot show custom effects in the inventory.")
                + f'</p><div class="effect-grid">{rows}</div>')
        self.page("effects.html", ("Effekte", "Effects"), body, 0, "effects")

    def guides_page(self):
        body = (f'<h1>{L("Anleitungen", "Guides")}</h1><p class="lead">'
                + L('Neu? Starte mit dem <a href="einstieg.html">Einstieg</a>; alles zu Pflanzen steht unter <a href="anbau.html">Anbau</a>.',
                    'New here? Start with <a href="einstieg.html">Getting started</a>; everything about plants is under <a href="anbau.html">Farming</a>.') + "</p>")
        for title, icon, lines in OTHER_GUIDES:
            body += (f'<section class="panel guide"><h2>{self.icon(icon, "", 32, "icon-sm")} {L(e(title[0]), e(title[1]))}</h2>'
                     f'<ul>{"".join(f"<li>{self.rich(l, "")}</li>" for l in lines)}</ul></section>')
        self.page("guides.html", ("Anleitungen", "Guides"), body, 0, "guides")

    def recipe_by_id(self, rid: str):
        return next((r for r in self.recipes if r.id == rid), None)

    def start_page(self):
        def cards(*ids):
            return '<div class="recipes">' + "".join(self.recipe_card(r, "") for r in (self.recipe_by_id(i) for i in ids) if r) + "</div>"

        def p(de, en):
            return f"<p>{self.rich((de, en), '')}</p>"

        steps = [
            (("Strohhut und erste Samen", "Straw hat and first seeds"),
             p("Stelle einen {item:kaleidoscope_cookery:straw_hat} aus Weizen und Faden her und setze ihn auf. Baust du jetzt Gras ab, findest du Samen für "
               "Tomaten, Chilis, Salat, Wildreis und Tee (mit Grilling auch Raps, Zwiebeln, Süßkartoffeln und Pfeffer).",
               "Craft a {item:kaleidoscope_cookery:straw_hat} from wheat and string and put it on. If you break grass now, you find seeds for tomatoes, "
               "chilis, lettuce, wild rice and tea (with Grilling also canola, onions, sweet potatoes and pepper).")
             + cards("kaleidoscope_cookery:straw_hat")
             + f'<p>{L("Pflanze die Samen auf Ackerland. Was wo wächst, steht auf der Seite <a href=anbau.html>Anbau</a>.", "Plant the seeds on farmland. What grows where is on the <a href=anbau.html>Farming</a> page.")}</p>'),
            (("Küchenmesser", "Kitchen knife"),
             p("Ein Küchenmesser ist das wichtigste Werkzeug: Nur mit einem Küchenmesser schneidest du auf dem Schneidebrett, und nur damit lassen Tiere Öl "
               "und besondere Zutaten fallen. Es gibt sie aus Kupfer, Eisen, Gold und Diamant; das Netherit-Messer entsteht am Schmiedetisch. Für den Anfang "
               "reicht das {item:kaleidoscope_cookery:copper_kitchen_knife}.",
               "A kitchen knife is the most important tool: only with a kitchen knife can you cut on the chopping board, and only then do animals drop oil "
               "and special ingredients. There are copper, iron, gold and diamond knives; the netherite knife is made at the smithing table. For the start "
               "the {item:kaleidoscope_cookery:copper_kitchen_knife} is enough.")
             + cards("kaleidoscope_cookery:copper_kitchen_knife", "kaleidoscope_cookery:iron_kitchen_knife")),
            (("Öl besorgen", "Getting oil"),
             p("Töte ein {entity:minecraft:pig} (oder einen Hoglin, Piglin …) mit dem Küchenmesser – es lässt {item:kaleidoscope_cookery:oil} fallen. "
               "Öl lagerst du in der {item:kaleidoscope_cookery:oil_pot}, die bis zu 256 Öl fasst.",
               "Kill a {entity:minecraft:pig} (or a hoglin, piglin …) with the kitchen knife – it drops {item:kaleidoscope_cookery:oil}. "
               "Store oil in the {item:kaleidoscope_cookery:oil_pot}, which holds up to 256 oil.")),
            (("Herd, Wok und Pfannenwender", "Stove, wok and spatula"),
             p("Stelle einen {item:kaleidoscope_cookery:stove} auf, zünde ihn mit einem Feuerzeug an und setze den {item:kaleidoscope_cookery:pot} darauf. "
               "Zum Rühren brauchst du den {item:kaleidoscope_cookery:kitchen_shovel}.",
               "Place a {item:kaleidoscope_cookery:stove}, light it with flint and steel and put the {item:kaleidoscope_cookery:pot} on top. "
               "You stir with the {item:kaleidoscope_cookery:kitchen_shovel}.")
             + cards("kaleidoscope_cookery:stove_campfire", "kaleidoscope_cookery:pot", "kaleidoscope_cookery:kitchen_shovel")),
            (("Das erste Gericht", "Your first dish"),
             "<ol class=\"steps\">" + "".join(f"<li>{self.rich(s, '')}</li>" for s in [
                 ("Öl in den Wok geben – jetzt hast du 60 Sekunden für die Zutaten.", "Put oil into the wok – now you have 60 seconds for the ingredients."),
                 ("Zutaten hineinlegen, z. B. vier {item:minecraft:potato} und zwei {item:minecraft:sugar}.", "Add the ingredients, e.g. four {item:minecraft:potato} and two {item:minecraft:sugar}."),
                 ("Mit dem Pfannenwender rühren, bis das Gericht fertig ist (meist 3× rühren, 10 Sekunden).", "Stir with the spatula until the dish is done (usually 3 stirs, 10 seconds)."),
                 ("Mit einer {item:minecraft:bowl} herausnehmen – bevor es nach 40 Sekunden anbrennt.", "Take it out with a {item:minecraft:bowl} – before it burns after 40 seconds.")])
             + "</ol>" + cards("kaleidoscope_cookery:pot/candied_potato")),
            (("Weiter geht's", "What's next"),
             "<ul>" + "".join(f"<li>{x}</li>" for x in [
                 self.rich(("Das {item:kaleidoscope:cookbook} (Buch + beliebige Samen) zeigt alle Rezepte im Spiel; Rechtsklick damit auf eine Station öffnet direkt deren Seite.",
                            "The {item:kaleidoscope:cookbook} (book + any seeds) shows all recipes in game; right-clicking a station with it opens that station's page directly."), ""),
                 L('<a href="stations/stockpot.html">Suppentopf</a> für Suppen, <a href="stations/chopping_board.html">Schneidebrett</a> für Zutaten, <a href="stations/millstone.html">Mühlstein</a> für Mehl und Gewürze.',
                   '<a href="stations/stockpot.html">Stockpot</a> for soups, <a href="stations/chopping_board.html">chopping board</a> for ingredients, <a href="stations/millstone.html">millstone</a> for flour and spices.'),
                 L('Mit Grilling: <a href="stations/skewering.html">Spieße aufspießen</a> und auf dem <a href="stations/grill.html">Grill</a> garen.',
                   'With Grilling: <a href="stations/skewering.html">make skewers</a> and cook them on the <a href="stations/grill.html">grill</a>.'),
                 L('Flex-Rezepte belohnen das richtige Mengenverhältnis mit besserer Qualität – siehe <a href="guides.html">Anleitungen</a>.',
                   'Flex recipes reward the right ratio with better quality – see <a href="guides.html">Guides</a>.')]) + "</ul>"),
        ]
        body = (f'<h1>{L("Einstieg", "Getting started")}</h1><p class="lead">'
                + L("Vom ersten Samen bis zum ersten Gericht – Schritt für Schritt. Beim Betreten des Servers lädt das Resourcepack automatisch; klappt das nicht, hilft <code>/kc pack</code>.",
                    "From the first seed to the first dish – step by step. The resource pack loads automatically when you join; if it fails, <code>/kc pack</code> helps.") + "</p>")
        for i, (title, content) in enumerate(steps, 1):
            body += f'<section class="panel guide start-step"><h2><span class="step-no">{i}</span>{L(*title)}</h2>{content}</section>'
        controls = [("Stationen setzt du mit Rechtsklick auf einen Block; sie schauen dann zu dir.", "You place stations with right-click on a block; they then face you."),
                    ("Klickst du dabei eine Kiste, einen Ofen oder eine Tür an, wird der Block benutzt – schleiche, um trotzdem zu setzen.",
                     "If you click a chest, furnace or door, the block is used instead – sneak to place anyway."),
                    ("Schleichen + Linksklick baut eine Station ab; Inhalt und Item fallen heraus.", "Sneak + left-click breaks a station; its contents and item drop."),
                    ("Schaust du eine Station an, zeigt die Leiste über der Hotbar ihren Zustand.", "Looking at a station shows its state above the hotbar.")]
        body += f'<section class="panel guide"><h2>{L("Bedienung", "Controls")}</h2><ul>{"".join(f"<li>{L(e(a), e(b))}</li>" for a, b in controls)}</ul></section>'
        self.page("einstieg.html", ("Einstieg", "Getting started"), body, 0, "start")

    def farming_page(self):
        rows = ""
        for title, seed, harvest, soil, details in CROPS:
            seed_link = f'<a class="mini" href="items/{slug(seed)}.html" {self.tip(seed)}>{self.icon(seed, "", 32)}</a>'
            harvest_html = "".join(f'<a class="mini" href="items/{slug(h)}.html" {self.tip(h)}>{self.icon(h, "", 32)}</a>' for h in harvest)
            rows += (f'<div class="crop panel"><h3>{L(*title)}</h3><div class="crop-row"><div><small>{L("Samen", "Seed")}</small>{seed_link}</div>'
                     f'<div class="arrow"><span></span></div><div><small>{L("Ernte", "Harvest")}</small><div class="minis">{harvest_html}</div></div></div>'
                     f'<p><b>{L("Boden", "Soil")}:</b> {L(*soil)}</p><p class="muted">{L(*details)}</p></div>')
        tips = [("Setze einen {item:kaleidoscope_cookery:straw_hat} auf und baue Gras ab: Mit etwas Glück fallen Samen heraus (je 12,5 % pro Samenart).",
                 "Put on a {item:kaleidoscope_cookery:straw_hat} and break grass: with some luck seeds drop (12.5% per seed type)."),
                ("Fischminze findest du in Netherfestungen (Truhen und anstelle mancher Netherwarzen), Sichuanpfeffer in Dorf-Truhen.",
                 "Houttuynia is found in Nether fortresses (chests and instead of some nether wart), Sichuan pepper in village chests."),
                ("Die {item:kaleidoscope_cookery:sickle} erntet reife Pflanzen in einem großen Bereich auf einmal und räumt dabei Gras und Blumen ab.",
                 "The {item:kaleidoscope_cookery:sickle} harvests ripe plants in a large area at once and clears grass and flowers.")]
        body = (f'<h1>{L("Anbau", "Farming")}</h1><p class="lead">'
                + L("Alle Pflanzen wachsen wie Vanilla-Pflanzen über zufällige Ticks, nur in der Simulationsdistanz eines Spielers und mit genug Licht. "
                    "Knochenmehl beschleunigt das Wachstum, feuchtes Ackerland drumherum ebenfalls.",
                    "All plants grow like vanilla crops via random ticks, only within a player's simulation distance and with enough light. "
                    "Bone meal speeds up growth, and so does moist farmland around them.")
                + f'</p><section class="panel guide"><h2>{L("Samen finden", "Finding seeds")}</h2><ul>{"".join(f"<li>{self.rich(t, "")}</li>" for t in tips)}</ul></section>'
                f'<section><h2>{L("Pflanzen", "Plants")}</h2><div class="crop-grid">{rows}</div></section>')
        self.page("anbau.html", ("Anbau", "Farming"), body, 0, "farming")

    def index_page(self, groups):
        food_count = sum(1 for v in self.data.items.values() if v.get("food"))
        stats = [(("Items", "Items"), len(self.data.items)), (("Rezepte", "Recipes"), len(self.recipes)),
                 (("Gerichte & Getränke", "Dishes & drinks"), food_count), (("Stationen", "Stations"), len(STATIONS))]
        stat_html = "".join(f'<div class="stat panel"><b>{n}</b><span>{L(e(t[0]), e(t[1]))}</span></div>' for t, n in stats)
        featured = "".join(card for items in groups.values() for card in items[:3])
        quick = [("Herd setzen und mit einem Feuerzeug anzünden, darauf einen Wok stellen.", "Place a stove and light it with flint and steel, put a wok on top."),
                 ("Öl in den Wok geben – Öl bekommst du, wenn du ein Schwein mit einem Küchenmesser tötest.", "Put oil into the wok – you get oil by killing a pig with a kitchen knife."),
                 ("Zutaten hineinlegen und mit dem Pfannenwender rühren, bis das Gericht fertig ist.", "Add ingredients and stir with the spatula until the dish is done."),
                 ("Mit einer Schüssel herausnehmen.", "Take it out with a bowl.")]
        body = f"""
<section class="hero">
  <div class="hero-text">
    <h1>Kaleidoscope <span>Cookery &amp; Grilling</span></h1>
    <p>{L("Kochen wie in den Mods – auf einem normalen Paper-Server, ganz ohne Client-Mods. Wok, Suppentopf, Grill, Mühle und über 300 Items; das Resourcepack lädt der Server automatisch.",
          "Cooking like in the mods – on a normal Paper server, without any client mods. Wok, stockpot, grill, millstone and more than 300 items; the server loads the resource pack automatically.")}</p>
    <div class="actions"><a class="btn" href="einstieg.html">{L("Erste Schritte", "Getting started")}</a><a class="btn alt" href="stations.html">{L("Stationen", "Stations")}</a><a class="btn alt" href="items.html">{L("Alle Items", "All items")}</a></div>
  </div>
  <div class="hero-art">{self.icon("kaleidoscope_cookery:pot", "", 96, "icon-xl")}{self.icon("kaleidoscope_grilling:grilled_beef_skewer", "", 96, "icon-xl")}{self.icon("kaleidoscope_cookery:braised_beef", "", 96, "icon-xl")}</div>
</section>
<div class="stats-row">{stat_html}</div>
<section><h2>{L("Beliebte Stationen", "Popular stations")}</h2><div class="cards">{featured}</div></section>
<section class="panel guide"><h2>{L("Schnellstart", "Quick start")}</h2><p>{L('Neu hier? Die <a href="einstieg.html">Einstiegs-Anleitung</a> führt dich Schritt für Schritt vom ersten Samen zum ersten Gericht.',
                                                                              'New here? The <a href="einstieg.html">getting started guide</a> takes you step by step from the first seed to the first dish.')}</p>
<ol class="steps">{"".join(f"<li>{L(e(a), e(b))}</li>" for a, b in quick)}</ol></section>
"""
        self.page("index.html", ("Start", "Home"), body, 0, "home")

    def assets(self):
        assets = self.out / "assets"
        assets.mkdir(parents=True, exist_ok=True)
        shutil.copy(WIKI_SRC / "style.css", assets / "style.css")
        shutil.copy(WIKI_SRC / "wiki.js", assets / "wiki.js")
        index = json.dumps(sorted(self.search, key=lambda s: s["e"].lower()), ensure_ascii=False, separators=(",", ":"))
        (assets / "search-index.js").write_text(f"window.WIKI_INDEX={index};\n", encoding="utf-8")


# Applied in <head> before rendering: sets the language classes from the saved settings (no flicker)
SETTINGS_BOOT = ("(function(){var d=document.documentElement,s={};try{s=JSON.parse(localStorage.getItem('kc-wiki')||'{}')}catch(x){}"
                 "var b=(navigator.language||'de').toLowerCase().indexOf('de')===0?'de':'en';var ui=s.ui||b,n=s.names||ui;"
                 "d.classList.add('ui-'+ui,'names-'+n);d.lang=ui;})();")


def first_frame(raw: bytes) -> bytes:
    """Animated textures are vertical strips: keep the first square frame."""
    from PIL import Image
    image = Image.open(io.BytesIO(raw))
    if image.height > image.width:
        image = image.crop((0, 0, image.width, image.width))
        buffer = io.BytesIO()
        image.save(buffer, "PNG")
        return buffer.getvalue()
    return raw


def install_font(cache: Path, out: Path):
    archive = download(MONOCRAFT, cache / "Monocraft-ttf.zip")
    with zipfile.ZipFile(archive) as zf:
        name = next(n for n in zf.namelist() if n.endswith("Monocraft.ttf"))
        target = out / "assets/font/Monocraft.ttf"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(zf.read(name))
    shutil.copy(WIKI_SRC / "Monocraft-OFL.txt", out / "assets/font/OFL.txt")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ROOT / "wiki"))
    parser.add_argument("--cache", default=str(Path.home() / ".cache" / "kaleidoscope-pack"))
    args = parser.parse_args()
    out = Path(args.out)
    cache = Path(args.cache)
    if out.exists():
        shutil.rmtree(out)
    data = Data(Vanilla(cache))
    recipes = build_recipes(data)
    site = Site(data, recipes, out)
    groups = site.station_pages()
    site.item_pages()
    site.items_page()
    site.effects_page()
    site.guides_page()
    site.start_page()
    site.farming_page()
    site.index_page(groups)
    site.assets()
    install_font(cache, out)
    pages = len(list(out.rglob("*.html")))
    print(f"Wiki: {pages} pages, {len(recipes)} recipes, {len(site.icons_written)} icons -> {out}")


if __name__ == "__main__":
    main()
