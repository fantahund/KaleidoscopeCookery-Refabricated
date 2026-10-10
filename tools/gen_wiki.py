#!/usr/bin/env python3
"""
Generates the player wiki as plain static HTML (no API calls, no fetch at runtime): wiki/ can be copied into any
folder of a web server (Apache) and also works when opened from the file system.

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
        self.cache = cache
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
        values = []
        for value in json.loads(self.server.read(name))["values"]:
            values.append(value["id"] if isinstance(value, dict) else value)
        return values

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
        addon = yaml_load(GRILLING / "src/main/resources/recipes.yml")
        for section, values in addon.items():
            if isinstance(values, dict):
                self.recipes.setdefault(section, {}).update(values)
        config = yaml_load(ROOT / "core/src/main/resources/config.yml")
        self.disabled = config.get("disabled-recipes", [])
        self.skewers = yaml_load(GRILLING / "src/main/resources/generated/skewers.yml")
        self.tabs = {}
        for path in (ROOT / "core/src/main/resources/generated/creative_tabs.yml", GRILLING / "src/main/resources/generated/creative_tabs.yml"):
            for tab, value in yaml_load(path)["tabs"].items():
                self.tabs[tab] = value["items"]
        self.packs = [zipfile.ZipFile(ROOT / "core/target/classes/KaleidoscopeCookery-Pack.zip"),
                      zipfile.ZipFile(GRILLING / "target/classes/KaleidoscopeGrilling-Pack.zip")]
        self.lang = dict(vanilla.lang_en)
        self.lang.update(vanilla.lang_de)
        for pack in self.packs:
            for name in pack.namelist():
                if name.endswith("/lang/en_us.json"):
                    for key, value in json.loads(pack.read(name)).items():
                        self.lang.setdefault(key, value)
            for name in pack.namelist():
                if name.endswith("/lang/de_de.json"):
                    self.lang.update(json.loads(pack.read(name)))
        self.icon_files = self.load_icons()

    def disabled_recipe(self, recipe_id: str) -> bool:
        for pattern in self.disabled:
            if pattern.endswith("*") and recipe_id.startswith(pattern[:-1]) or recipe_id == pattern:
                return True
        return False

    def load_icons(self) -> dict:
        """Item -> (zip, path) of its cookbook image: rendered 3D view or the flat texture (item_icons.txt + icon font)."""
        icons = {}
        sources = [(ROOT / "core/target/classes/item_icons.txt", self.packs[0]), (GRILLING / "target/classes/item_icons.txt", self.packs[1])]
        for path, pack in sources:
            font = None
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

    # ---- Names and texts ----

    def name(self, item: str) -> str:
        if item in self.items:
            return self.lang.get(self.items[item].get("name", ""), item)
        ns, path = item.split(":", 1)
        for prefix in ("item", "block"):
            key = f"{prefix}.{ns}.{path}"
            if key in self.lang:
                return self.lang[key]
        return path.replace("_", " ").title()

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
            values = self.recipes.get("tags", {}).get(key) or self.vanilla.tag(key)
            return self.resolve(list(values))
        ns = ingredient.split(":")[0]
        if ns not in ("minecraft", "kaleidoscope", "kaleidoscope_cookery", "kaleidoscope_grilling"):
            return []  # optional entries of other mods (e.g. kaleidoscope_tavern) never match on the server
        return [ingredient]


# ---------------------------------------------------------------- Recipes


# Liquids (stockpot broth, teapot tea base) shown as their bucket
LIQUIDS = {"minecraft:water": "minecraft:water_bucket", "minecraft:lava": "minecraft:lava_bucket",
           "minecraft:milk": "minecraft:milk_bucket", "kaleidoscope_cookery:milk": "minecraft:milk_bucket"}


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
        self.notes = notes or []
        self.grid = grid          # 9 slots (alternatives or None) for shaped crafting
        self.extra = extra        # (label, item) shown next to the station, e.g. broth

    def items_in(self) -> set:
        found = set()
        for alts, _ in self.inputs:
            found |= set(alts)
        if self.grid:
            for alts in self.grid:
                found |= set(alts or [])
        if self.extra:
            found.add(self.extra[1])
        return found


def seconds(ticks) -> str:
    value = int(ticks) / 20
    return f"{value:g} s".replace(".", ",")


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
            out.append(Recipe("chopping_board", rid, count_inputs(data, [v["ingredient"]]),
                              [(*result_of(x), 1.0) for x in v["results"]], [f"{v['cuts']} Schnitte mit dem Küchenmesser"]))
    for section, flex in (("pot", False), ("flex-pot", True)):
        for rid, v in r.get(section, {}).items():
            if not keep(rid):
                continue
            item, count = result_of(v["result"])
            notes = []
            if v.get("per-item"):
                notes.append("1–9 gleiche Zutaten ergeben entsprechend viele Portionen")
            if flex:
                notes.append("Flex-Rezept: jede Zutat mindestens einmal, die Mengen bestimmen die Qualität")
            notes.append(f"Kochzeit {seconds(v.get('time', 200))}, {v.get('stir-fry', 3)}× rühren")
            carrier = v.get("carrier")
            notes.append(f"Herausnehmen mit: {data.name(carrier)}" if carrier else "Herausnehmen mit dem Pfannenwender")
            out.append(Recipe("pot", rid, count_inputs(data, v["ingredients"]), [(item, count, 1.0)], notes))
    for section, flex in (("stockpot", False), ("flex-stockpot", True)):
        for rid, v in r.get(section, {}).items():
            if not keep(rid):
                continue
            item, count = result_of(v["result"])
            notes = []
            if flex:
                notes.append("Flex-Rezept: jede Zutat mindestens einmal, die Mengen bestimmen die Qualität")
            notes.append(f"Kochzeit {seconds(v.get('time', 300))}")
            carrier = v.get("carrier")
            if carrier:
                notes.append(f"Portionen nehmen mit: {data.name(carrier)}")
            base = v.get("soup-base", "minecraft:water")
            out.append(Recipe("stockpot", rid, count_inputs(data, v["ingredients"]), [(item, count, 1.0)], notes,
                              extra=("Brühe", LIQUIDS.get(base, base))))
    for rid, v in r.get("steamer", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            out.append(Recipe("steamer", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [f"Dämpfzeit {seconds(v.get('time', 1200))}"]))
    for rid, v in r.get("teapot", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            out.append(Recipe("teapot", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [f"Ziehzeit {seconds(v.get('time', 2400))}", "Eine Kanne ergibt 12 Tassen"],
                              extra=("Teebasis", LIQUIDS[v.get("tea-fluid", "minecraft:water")]) if v.get("tea-fluid", "minecraft:water") in LIQUIDS else None))
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
            kind = "Trocknen (freier Himmel, kein Regen)" if v.get("type") == "drying" else "Befeuchten (Regen oder tropfender Tropfstein)"
            out.append(Recipe("bamboo_tray", rid, count_inputs(data, [v["ingredient"]]), [(item, count, 1.0)],
                              [kind, f"Dauer {seconds(v.get('time', 1800))}"]))
    for rid, v in r.get("rice-bowl", {}).items():
        if keep(rid):
            item, count = result_of(v["result"])
            out.append(Recipe("crafting", rid, count_inputs(data, [v["ingredient"], "#c:foods/cooked_rice"]), [(item, count, 1.0)],
                              ["Formlos in der Werkbank, das Ergebnis bekommt die beste Qualität der Zutaten"]))
    campfire_seen = set()
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
            ingredients = [x for x in grid if x]
            merged = []
            for alts in ingredients:
                for entry in merged:
                    if entry[0] == alts:
                        entry[1] += 1
                        break
                else:
                    merged.append([alts, 1])
            out.append(Recipe("crafting", rid, [(a, n) for a, n in merged], outputs, grid=grid))
        elif kind == "crafting_shapeless":
            out.append(Recipe("crafting", rid, count_inputs(data, v["ingredients"]), outputs, ["Formlos"]))
        elif kind in ("smelting", "smoking", "campfire_cooking", "blasting"):
            label = {"smelting": "Ofen", "smoking": "Räucherofen", "campfire_cooking": "Lagerfeuer", "blasting": "Schmelzofen"}[kind]
            notes = [f"{label}, {seconds(v.get('time', 200))}"]
            station = "shawarma_spit" if kind == "campfire_cooking" else "furnace"
            if kind == "campfire_cooking":
                campfire_seen.add(item)
                out.append(Recipe("furnace", rid, count_inputs(data, [v["ingredient"]]), outputs, notes))
            out.append(Recipe(station, rid, count_inputs(data, [v["ingredient"]]), outputs, notes))
        elif kind == "smithing_transform":
            out.append(Recipe("crafting", rid, count_inputs(data, [v["template"], v["base"], v["addition"]]), outputs,
                              ["Schmiedetisch: Vorlage, Basis, Zusatz"]))
    for name, value in data.vanilla.campfire_recipes():
        item = value["result"]["id"] if isinstance(value["result"], dict) else value["result"]
        out.append(Recipe("shawarma_spit", f"minecraft:{name}", count_inputs(data, [value["ingredient"]]), [(item, 1, 1.0)],
                          ["Vanilla-Lagerfeuer-Rezept"]))
    # Grilling: skewering and grill
    for raw, value in data.skewers["recipes"].items():
        if data.disabled_recipe(raw):
            continue
        slots = [(data.resolve(list(slot)), 1) for slot in value["ingredients"]]
        out.append(Recipe("skewering", raw, [(["minecraft:stick"], 1)] + slots, [(raw, 1, 1.0)],
                          ["Stock in die Nebenhand, Zutaten der Reihe nach mit Rechtsklick aufspießen"]))
        if value.get("cooked"):
            out.append(Recipe("grill", raw + "/grill", [([raw], 1)], [(value["cooked"], 1, 1.0)],
                              ["Auflegen, mit der Ölkanne bestreichen, 4× wenden"]))
    out.append(Recipe("skewering", "kaleidoscope_grilling:secret_skewer", [(["minecraft:stick"], 1),
                      (["kaleidoscope_grilling:unfinished_skewer"], 1)], [("kaleidoscope_grilling:secret_skewer", 1, 1.0)],
                      ["Drei beliebige essbare Zutaten, die kein festes Rezept ergeben, werden zum Geheimspieß"]))
    out.append(Recipe("grill", "kaleidoscope_grilling:secret_skewer/grill", [(["kaleidoscope_grilling:secret_skewer"], 1)],
                      [("kaleidoscope_grilling:secret_skewer", 1, 1.0)], ["Wird gegart und wirkt dann wie seine Zutaten"]))
    out.append(Recipe("oil_press", "kaleidoscope_grilling:oil_press/canola", [(["kaleidoscope_grilling:oil_cake"], 4)],
                      [("kaleidoscope_grilling:canola_oil_bucket", 4, 1.0), ("kaleidoscope_grilling:oil_residue", 4, 1.0)],
                      ["16× pressen (Amboss +4, Pressstein +1)", "Das Rapsöl fließt in ein großes Fass in der Nähe"]))
    out.append(Recipe("seasoning", "kaleidoscope_grilling:special_seasoning",
                      [(["kaleidoscope_grilling:empty_seasoning_bottle"], 1), (["kaleidoscope_grilling:green_chili_powder"], 1),
                       (["kaleidoscope_grilling:sichuan_pepper"], 1), (["kaleidoscope_grilling:onion_powder"], 1)],
                      [("kaleidoscope_grilling:special_seasoning", 1, 1.0)],
                      ["Zutaten in die hingestellte Flasche geben (bis 8)", "Die unfertige Mischung 4 s schütteln: 16 Anwendungen"]))
    out.append(Recipe("crafting", "kaleidoscope_grilling:cold_houttuynia", [(["kaleidoscope_grilling:houttuynia"], 3),
                      (["kaleidoscope_cookery:oil_pot"], 1)], [("kaleidoscope_grilling:cold_houttuynia", 1, 1.0)],
                      ["Ölkanne mit mindestens 2 Magma-Chiliöl, sie bleibt mit 2 Öl weniger im Raster"]))
    return out


# ---------------------------------------------------------------- Stations and guides

# id, title, icon item, intro paragraphs, steps
STATIONS = [
    ("stove", "Herd", "kaleidoscope_cookery:stove", "Kochen",
     ["Der Herd ist die Hitzequelle für Wok und Suppentopf. Alternativ funktionieren auch Lagerfeuer, Feuer, Lava, Magma und andere brennende Blöcke darunter."],
     ["Anzünden mit Feuerzeug, Feuerkugel oder einem brennenden Projektil (z. B. Flammenpfeil).",
      "Löschen mit einer Schaufel (auch dem Pfannenwender), durch Regen oder Wasser darüber.",
      "Ein brennender Herd leuchtet und raucht."]),
    ("pot", "Wok", "kaleidoscope_cookery:pot", "Kochen",
     ["Im Wok entstehen die meisten Pfannengerichte. Er braucht Hitze von unten, z. B. einen brennenden Herd."],
     ["Öl hineingeben (Öl, Ölkanne oder Pfannenwender mit Öl). Danach bleiben 60 Sekunden Zeit für bis zu 9 Zutaten.",
      "Mit dem Pfannenwender rühren startet das Kochen; jedes weitere Rühren zählt die nötigen Rührvorgänge herunter.",
      "Nicht genug gerührt ergibt ein verdächtiges Pfannengericht, sonst das Gericht.",
      "Herausnehmen mit dem passenden Gefäß (z. B. Schüssel) oder Schleichen + Pfannenwender. Nach 40 s brennt das Gericht an, nach weiteren 20 s wird es zu Holzkohle.",
      "Mit der bloßen Hand in den heißen Wok greifen kostet ein halbes Herz."]),
    ("stockpot", "Suppentopf", "kaleidoscope_cookery:stockpot", "Kochen",
     ["Im Suppentopf werden Suppen und Eintöpfe gekocht."],
     ["Brühe hineingeben: Wasser-, Lava-, Milch- oder Fischeimer.", "Bis zu 9 Zutaten hineinlegen.",
      "Deckel drauf: auf einer Hitzequelle kocht die Suppe (Standard 15 s).",
      "Deckel ab und portionsweise herausnehmen (je nach Rezept mit Schüssel), bis zu 9 Portionen.",
      "Ohne passendes Rezept entsteht ein verdächtiger Eintopf."]),
    ("steamer", "Dampfgarer", "kaleidoscope_cookery:steamer", "Kochen",
     ["Der Dampfgarer gart Teigwaren und Gebäck. Er steht auf einem Wok oder Suppentopf über Hitze oder auf einer Hitzequelle."],
     ["Eine halbe Lage hat 4 Plätze, eine volle Lage (zwei Körbe) 8. Mit einem Dampfgarer in der Hand stapelst du weitere Lagen.",
      "Gegart wird nur mit Deckel oben (Schleichen + Rechtsklick mit leerer Hand).",
      "Hitze direkt darunter ergibt Stufe 4, jede volle Lage darüber eine Stufe weniger.",
      "Ohne Hitze kühlt der Fortschritt langsam wieder ab. Rechtsklick mit leerer Hand nimmt alles heraus."]),
    ("teapot", "Teekanne", "kaleidoscope_cookery:teapot", "Getränke",
     ["In der Teekanne zieht Tee, der anschließend in hingestellte Tassen gegossen wird."],
     ["Teebasis einfüllen: Wasser- oder Milcheimer. Eine leere Kanne in der Hand schöpft auch direkt aus Wasser oder Lava.",
      "Zutat (Teebeutel, Blumen …) hineinlegen und auf einer Hitzequelle ziehen lassen.",
      "Fertig: die Kanne aufnehmen und auf hingestellte leere Tassen gießen. Eine Kanne reicht für 12 Tassen.",
      "Tassen und Tee stellst du schleichend hin, bis zu 4 Tassen pro Block."]),
    ("chopping_board", "Schneidebrett", "kaleidoscope_cookery:chopping_board", "Vorbereitung",
     ["Auf dem Schneidebrett zerteilst du Zutaten mit dem Küchenmesser."],
     ["Rechtsklick mit einer Zutat legt ein Stück auf.", "Linksklick mit einem Küchenmesser schneidet; das Modell ändert sich bei jedem Schnitt.",
      "Sind alle Schnitte gemacht, lässt ein Klick die Ergebnisse fallen.", "Schleichen + Rechtsklick vor dem ersten Schnitt nimmt die Zutat zurück.",
      "Die Verzauberung „Schnelles Messer“ verdoppelt bzw. vervierfacht die Schnitte pro Hieb."]),
    ("millstone", "Mühlstein", "kaleidoscope_cookery:millstone", "Vorbereitung",
     ["Der Mühlstein mahlt Getreide, Samen und Gewürze. Er wird von einem Tier angetrieben und belegt 2×2 Blöcke."],
     ["Ein Tier (kein Spieler, nicht geritten), das auf den Mühlstein tritt, wird eingespannt und läuft im Kreis.",
      "Rechtsklick mit einer Zutat legt bis zu 8 Stück ein; auch auf den Stein geworfene Items werden eingelegt.",
      "Nach jeder Runde fallen die Ergebnisse vorne heraus.",
      "Das Tier wird gelöst, wenn es Schaden nimmt, stirbt, fällt oder sich zu weit entfernt."]),
    ("bamboo_tray", "Bambustablett", "kaleidoscope_cookery:bamboo_tray", "Vorbereitung",
     ["Auf dem Bambustablett werden Zutaten getrocknet oder befeuchtet. Es hat 4 Plätze und lässt sich stapeln."],
     ["Trocknen: freier Himmel ohne Regen (Glas und weitere Tabletts darüber sind erlaubt).",
      "Befeuchten: Regen auf dem Tablett oder ein Tropfstein mit Wasser darüber.",
      "Rechtsklick auf ein Viertel legt den ganzen Stapel ab, mit leerer Hand nimmst du ein Stück (Schleichen: alles)."]),
    ("shawarma_spit", "Dönerspieß", "kaleidoscope_cookery:shawarma_spit", "Kochen",
     ["Der Dönerspieß ist 2 Blöcke hoch und gart alles, was auch auf einem Lagerfeuer gart."],
     ["Jede der beiden Ebenen nimmt bis zu 8 gleiche Zutaten.",
      "Gegart wird nur mit Redstone-Signal an einem der beiden Blöcke, dann dreht sich der Spieß.",
      "Rechtsklick ohne passende Zutat nimmt das Fertige heraus. Ist der Spieß an, verbrennst du dich dabei leicht."]),
    ("crafting", "Werkbank & Schmiedetisch", "minecraft:crafting_table", "Herstellung",
     ["Viele Werkzeuge, Stationen und einfache Gerichte werden in der Werkbank hergestellt. Reisschalen entstehen formlos aus gekochtem Reis und einer Zutat."],
     []),
    ("furnace", "Ofen, Räucherofen & Lagerfeuer", "minecraft:furnace", "Herstellung",
     ["Diese Rezepte funktionieren in den Vanilla-Öfen bzw. auf dem Lagerfeuer. Öl brennt wie Kohle, der Ölblock wie ein Kohleblock."],
     []),
    ("grill", "Grill", "kaleidoscope_grilling:grill", "Grillen",
     ["Auf dem Grill werden rohe Spieße gegart."],
     ["Anzünden mit dem Feuerzeug, löschen mit einer Schaufel.",
      "Bis zu 3 rohe Spieße (oder Geheimspieße) auf den brennenden Grill legen.",
      "Mit der Ölkanne bestreichen (1 Öl pro Spieß), dann 4× mit leerer Hand wenden (jeweils 1 s Pause).",
      "Gar: optional mit Spezialgewürz würzen, dann mit leerer Hand herunternehmen (Schleichen: alle).",
      "800 Ticks ohne Fortschritt: angebrannt, nach weiteren 400 Ticks wird der Spieß zu Holzkohle."]),
    ("skewering", "Aufspießen", "minecraft:stick", "Grillen",
     ["Spieße entstehen ohne Station direkt in der Hand."],
     ["Einen Stock in die Nebenhand nehmen.",
      "Mit einer Zutat in der Haupthand Rechtsklick: Sie wird aufgespießt. Passt die Reihenfolge zu einem Rezept, entsteht der feste rohe Spieß.",
      "Nach drei Zutaten ohne passendes Rezept entsteht ein Geheimspieß.",
      "Schleichen + Rechtsklick nimmt den Spieß in der Nebenhand wieder auseinander.",
      "Das Spieß-Rezeptbuch merkt sich einen Spieß und spießt ihn später aus dem Inventar auf; es lässt sich auch an die Wand hängen."]),
    ("oil_press", "Ölpresse & großes Fass", "kaleidoscope_grilling:oil_press", "Grillen",
     ["Aus Ölkuchen wird Rapsöl gepresst, das in einem großen Fass gesammelt wird."],
     ["4 Ölkuchen in die Presse legen und mit einem Amboss (+4) oder Pressstein (+1) bis 16 pressen.",
      "Fertig: 4 Eimer Rapsöl laufen in ein großes Fass in der Nähe (±4 Blöcke), 4 Ölrückstände fallen vorne heraus.",
      "Das Fass fasst 8 Eimer einer Flüssigkeit. Ölkannen werden daran mit 8 Öl pro Eimer gefüllt.",
      "Ölsorten: Rapsöl, Chiliöl, Magma-Chiliöl. Eine Ölkanne mit Ölsorte fasst 64, mit Fett 256."]),
    ("seasoning", "Gewürze", "kaleidoscope_grilling:empty_seasoning_bottle", "Grillen",
     ["Spezialgewürz verfeinert Spieße auf dem Grill sowie Gerichte aus Wok und Suppentopf."],
     ["Gewürzflaschen hinstellen (bis 4 pro Block) und mit Gewürz-Zutaten befüllen (bis 8 pro Flasche).",
      "Sind grünes Chilipulver, Sichuanpfeffer und Zwiebelpulver drin, wird daraus eine unfertige Mischung.",
      "Die Mischung 4 s schütteln (gedrückt halten): Spezialgewürz mit 16 Anwendungen.",
      "Im Wok oder Suppentopf würzt ein Klick alle Gerichte dieser Runde; jede Zutat im Gewürz bringt eine eigene Wirkung."]),
]

OTHER_GUIDES = [
    ("Ölkanne, Fetttopf & Ölblock", "kaleidoscope_cookery:oil_pot",
     ["Öl erhältst du, wenn du Schweine, Hoglins, Piglins oder Zoglins mit einem Küchenmesser tötest, oder aus der Mühle.",
      "Die Ölkanne speichert bis zu 256 Öl; hingestellt füllst du sie mit Öl nach und nimmst mit leerer Hand bis zu 64 heraus.",
      "Der Fetttopf (Emaillebecken) fasst 32 Öl und hat einen Deckel; der Pfannenwender nimmt dort ein Öl auf."]),
    ("Teller-Gerichte", "kaleidoscope_cookery:braised_beef",
     ["Viele Gerichte lassen sich hinstellen und dann bissweise essen; nach dem letzten Biss bleibt das Geschirr übrig.",
      "Große Gerichte belegen 2 Blöcke, der Aufschnitt-Schinken sogar 3×3 Blöcke.",
      "Unangetastet abgebaut erhältst du das Gericht zurück."]),
    ("Qualität", "kaleidoscope:cookbook",
     ["Flex-Rezepte (Wok und Suppentopf) bewerten die Mengen der Zutaten: Für jedes Rezept gibt es auf jedem Server ein verstecktes Mengenverhältnis.",
      "Je näher du daran liegst, desto besser die Qualität – und desto mehr Nährwert und längere Effekte hat das Gericht."]),
    ("Weitere Blöcke", "kaleidoscope_cookery:trash_can",
     ["Küchengeräteregal: hängt an der Wand und hält zwei Werkzeuge.",
      "Obstkorb: 8 Plätze, behält seinen Inhalt beim Abbauen.",
      "Mülleimer: wirft Stapel endgültig weg (die letzten 3 bleiben zurückholbar); wer aus über einem Block hineinfällt, versteckt sich darin.",
      "Rezept-Zettel: zeichnet die Zutaten von Wok oder Suppentopf auf und legt sie später aus dem Inventar ein.",
      "Verwandlungs-Lunchbeutel: fasst 24 Stapel Essen und isst so lange, bis du satt bist.",
      "Vogelscheuche: schützt Ackerland in 16 Blöcken vor dem Zertrampeln; Laterne, Werkzeug und Köpfe lassen sich anlegen.",
      "Milchtee im Tontopf: trinken entfernt alle schädlichen Effekte; er lässt sich auch als Deko hinstellen.",
      "Grilling: Spießteller (bis 5 Spieße) und erweitertes Küchenregal (9 Fächer)."]),
]

# Crops: (name, seed, harvest, soil, details) - CropKind (Cookery) and GrillingCrops
CROPS = [
    ("Tomate", "kaleidoscope_cookery:tomato_seed", ["kaleidoscope_cookery:tomato"], "Ackerland",
     "8 Stufen. Reif per Rechtsklick ernten, die Pflanze wächst nach."),
    ("Chili", "kaleidoscope_cookery:chili_seed", ["kaleidoscope_cookery:red_chili", "kaleidoscope_cookery:green_chili"], "Ackerland",
     "8 Stufen. Reif per Rechtsklick ernten (20 % grüne statt rote Chili) oder abbauen (20 % zusätzlich eine grüne)."),
    ("Salat", "kaleidoscope_cookery:lettuce_seed", ["kaleidoscope_cookery:lettuce", "kaleidoscope_cookery:caterpillar"], "Ackerland",
     "8 Stufen. Nur durch Abbauen ernten; mit 10 % Chance sitzt eine Raupe darin."),
    ("Reis", "kaleidoscope_cookery:wild_rice", ["kaleidoscope_cookery:rice_panicle"], "Ackerland unter einer Wasserquelle",
     "Wächst im Wasser und wird 3 Blöcke hoch. Ergibt 2–4 Reisrispen; gepflanzt wird mit Wildreis oder Reis. Die Hacke funktioniert auch unter Wasser."),
    ("Teestrauch", "kaleidoscope_cookery:tea_seed", ["kaleidoscope_cookery:fresh_tea_leaves"], "Erde, Gras, Podsol, Moos u. Ä.",
     "6 Stufen. Reif per Rechtsklick ernten, der Strauch wächst nach."),
    ("Raps", "kaleidoscope_grilling:canola_seeds", ["kaleidoscope_grilling:canola_seeds"], "Ackerland",
     "8 Stufen. Die Samen werden zu Rapspulver gemahlen oder zu Ölkuchen verarbeitet."),
    ("Zwiebel", "kaleidoscope_grilling:onion", ["kaleidoscope_grilling:onion"], "Ackerland", "8 Stufen, Ernte durch Abbauen."),
    ("Süßkartoffel", "kaleidoscope_grilling:sweet_potato", ["kaleidoscope_grilling:sweet_potato"], "Ackerland",
     "8 Stufen, reif gibt es mindestens 2 zusätzliche Süßkartoffeln."),
    ("Fischminze", "kaleidoscope_grilling:houttuynia", ["kaleidoscope_grilling:houttuynia"], "Ackerland oder Seelensand",
     "8 Stufen. Auf Seelensand (sonst zu 30 %) färbt sie sich ab Stufe 5 rot."),
    ("Sichuanpfeffer", "kaleidoscope_grilling:sichuan_pepper_seeds", ["kaleidoscope_grilling:sichuan_pepper"], "Ackerland",
     "Strauch mit 6 Stufen. Reif per Rechtsklick 1–2 Pfeffer ernten, danach blüht er und reift erneut."),
]

GRASS_SEEDS = ["kaleidoscope_cookery:tomato_seed", "kaleidoscope_cookery:chili_seed", "kaleidoscope_cookery:lettuce_seed",
               "kaleidoscope_cookery:wild_rice", "kaleidoscope_cookery:tea_seed"]
GRILLING_GRASS = ["kaleidoscope_grilling:canola_seeds", "kaleidoscope_grilling:sweet_potato", "kaleidoscope_grilling:onion",
                  "kaleidoscope_grilling:sichuan_pepper_seeds"]


def sources(config: dict) -> dict:
    """Item -> where it is found (drops, loot, harvest). From OilDropListener, CropListener, GrillingDrops, CropStation."""
    found = {}

    def add(item, text):
        found.setdefault(item, []).append(text)

    oil = config.get("oil-drops", {})
    if oil.get("enabled", True):
        mobs = {"PIG": "Schwein", "HOGLIN": "Hoglin", "PIGLIN": "Piglin", "PIGLIN_BRUTE": "Piglin-Barbar", "ZOGLIN": "Zoglin",
                "ZOMBIFIED_PIGLIN": "Zombifizierter Piglin"}
        names = ", ".join(mobs.get(m, m.title()) for m in oil.get("entities", []))
        add("kaleidoscope_cookery:oil", f"Mit einem Küchenmesser töten: {names} – {oil.get('min', 1)}–{oil.get('max', 2)} Öl (Plünderung erhöht).")
    add("kaleidoscope_cookery:raw_donkey_meat", "Einen Esel mit einem Küchenmesser töten: 2–4 Stück (Plünderung erhöht).")
    add("kaleidoscope_grilling:chicken_wing", "Ein Huhn mit einem Küchenmesser töten: 1–2 Hähnchenflügel.")
    add("kaleidoscope_cookery:raw_cow_offal", "Eine Kuh mit einem Küchenmesser töten: 1–2 Stück.")
    add("kaleidoscope_grilling:squid_tentacle", "Einen Tintenfisch mit einem Küchenmesser töten: zu 50 % 2–3 Tentakel.")
    for seed in GRASS_SEEDS:
        add(seed, "Gras abbauen, während du einen Strohhut trägst (12,5 % je Samenart).")
    for seed in GRILLING_GRASS:
        add(seed, "Gras abbauen, während du einen Strohhut trägst (je 12,5 %).")
    add("kaleidoscope_grilling:houttuynia", "Truhen in Netherfestungen (65 %, 1–3 Stück).")
    add("kaleidoscope_grilling:houttuynia", "Wächst in neu erzeugten Netherfestungen an einem Viertel der Netherwarzen-Stellen.")
    add("kaleidoscope_grilling:sichuan_pepper", "Dorf-Truhen (40 %, 3–10 Stück).")
    add("kaleidoscope_grilling:sichuan_pepper_seeds", "Dorf-Truhen (20 %).")
    for name, seed, harvest, soil, _ in CROPS:
        for item in harvest:
            add(item, f"Ernte von {name} (auf {soil}).")
    add("kaleidoscope_cookery:red_chili", "Von einer hängenden Chili-Kette ernten.")
    add("kaleidoscope_grilling:chicken_skin", "Rohes Hähnchen auf dem Schneidebrett zerteilen: zusätzlich 1–3 Hühnerhaut.")
    add("kaleidoscope_grilling:dragon_egg_powder", "Rechtsklick mit einem Küchenmesser auf ein Drachenei (1 Stück, Plünderung erhöht; kostet 1 Haltbarkeit).")
    add("kaleidoscope_cookery:suspicious_stir_fry", "Wok: Zutaten ohne passendes Rezept oder nicht genug gerührt.")
    add("kaleidoscope_cookery:dark_cuisine", "Wok: ein fertiges Gericht nicht rechtzeitig herausgenommen – es brennt an.")
    add("kaleidoscope_cookery:mystery_tea", "Teekanne: eine Zutat ohne passendes Rezept ergibt 4 Tassen davon.")
    add("kaleidoscope_grilling:mysterious_skewer", "Grill: ein Spieß ohne gegrillte Form.")
    add("kaleidoscope_grilling:dark_grilling", "Grill: einen Spieß zu lange liegen lassen – er brennt an.")
    add("kaleidoscope_grilling:unfinished_skewer", "Entsteht beim Aufspießen, solange noch nicht alle Zutaten auf dem Stock sind.")
    add("kaleidoscope_grilling:pending_seasoning", "Gewürzflasche mit grünem Chilipulver, Sichuanpfeffer und Zwiebelpulver; 4 s schütteln ergibt Spezialgewürz.")
    add("kaleidoscope_grilling:skewer_plate", "Schleichen + Rechtsklick mit einem Spieß auf die Oberseite eines festen Blocks stellt einen Spießteller hin.")
    add("kaleidoscope_grilling:skewer_recipe_book", "Werkbank: leeres Rezept + roher Spieß (oder Geheimspieß); der Spieß bleibt im Raster.")
    for brush in ("canola_oil_brush", "secret_chili_oil_brush", "premium_chili_oil_brush"):
        add(f"kaleidoscope_grilling:{brush}", "Nur Darstellung: so sieht die Ölkanne beim Bestreichen am Grill aus. Nicht als Item erhältlich.")
    add("kaleidoscope_grilling:wedding_candy", "Event-Item des Mods (September 2026), auf dem Server nicht erhältlich.")
    add("kaleidoscope_cookery:suspicious_stir_fry_rice_bowl", "Im Mod nur über den Koch-Dorfbewohner erhältlich, den es auf dem Server (noch) nicht gibt.")
    for block in ("oil_block", "straw_block"):
        add(f"kaleidoscope_cookery:{block}", "Das Rezept ist auf diesem Server abgeschaltet (disabled-recipes).")
    return found


COMMANDS = [
    ("/kc recipes [Suche]", "Kochbuch öffnen bzw. durchsuchen (auch /kc rezepte)", "kaleidoscope.use"),
    ("/kc recipe", "Rezepte für das Item in der Hand (auch /kc rezept)", "kaleidoscope.use"),
    ("/kc pack", "Resourcepack erneut laden", "kaleidoscope.use"),
    ("/kc give <item> [spieler] [anzahl]", "Item vergeben", "kaleidoscope.admin.give"),
    ("/kc menu", "Kreativ-Menü mit allen Items", "kaleidoscope.admin.give"),
    ("/kc place <typ> <x> <y> <z> [welt] [ausrichtung]", "Station ohne Item setzen", "kaleidoscope.admin.place"),
    ("/kc info", "Zustand der angeschauten Station", "kaleidoscope.admin.info"),
    ("/kc list", "Alle geladenen Stationen", "kaleidoscope.admin.info"),
    ("/kc repair", "Stationen in geladenen Chunks prüfen und reparieren", "kaleidoscope.admin.repair"),
    ("/kc reload", "config.yml und recipes.yml neu laden", "kaleidoscope.admin.reload"),
]

TAB_NAMES = {"kitchen": "Küche & Werkzeuge", "food": "Essen & Zutaten", "furniture": "Möbel & Deko", "grilling": "Grillen"}

# ---------------------------------------------------------------- HTML


def e(text) -> str:
    return html.escape(str(text), quote=True)


def slug(item: str) -> str:
    return item.replace(":", "/")


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
        self.sources = sources(yaml_load(ROOT / "core/src/main/resources/config.yml"))

    # ---- Helpers ----

    def icon(self, item: str, root: str, size: int = 32, cls: str = "icon") -> str:
        path = f"assets/icons/{slug(item)}.png"
        if item not in self.icons_written:
            source = self.data.icon_files.get(item)
            raw = self.data.read_asset(source) if source else None
            if raw is None and item.startswith("minecraft:"):
                raw = self.data.read_asset(f"assets/minecraft/textures/item/{item[10:]}.png") or \
                    self.data.read_asset(f"assets/minecraft/textures/block/{item[10:]}.png")
            if raw is not None:
                target = self.out / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(first_frame(raw))
                self.icons_written.add(item)
            else:
                self.icons_written.add(item)
                path = None
        elif not (self.out / path).exists():
            path = None
        if path is None:
            return f'<span class="{cls} missing" style="width:{size}px;height:{size}px"></span>'
        return f'<img class="{cls}" src="{root}{path}" width="{size}" height="{size}" alt="" loading="lazy">'

    def item_link(self, item: str, root: str) -> str:
        return f"{root}items/{slug(item)}.html" if item in self.data.items else None

    def slot(self, alts, count: int, root: str, big: bool = False) -> str:
        if not alts:
            return '<span class="slot empty"></span>'
        item = alts[0]
        names = [self.data.name(a) for a in alts]
        tip = names[0] if len(alts) == 1 else "Eines von: " + ", ".join(names[:12]) + (" …" if len(names) > 12 else "")
        link = self.item_link(item, root)
        inner = self.icon(item, root, 32)
        if len(alts) > 1:
            inner += '<span class="slot-tag">#</span>'
        if count > 1:
            inner += f'<span class="slot-count">{count}</span>'
        cls = "slot big" if big else "slot"
        if link:
            return f'<a class="{cls}" href="{e(link)}" data-tip="{e(tip)}">{inner}</a>'
        return f'<span class="{cls}" data-tip="{e(tip)}">{inner}</span>'

    def recipe_card(self, recipe: Recipe, root: str) -> str:
        station = self.stations.get(recipe.station)
        if recipe.grid:
            rows = "".join(f'<div class="grid-row">{"".join(self.slot(a or [], 1, root) for a in recipe.grid[i * 3:i * 3 + 3])}</div>'
                           for i in range(3))
            left = f'<div class="grid3">{rows}</div>'
        else:
            left = '<div class="inputs">' + "".join(self.slot(a, n, root) for a, n in recipe.inputs) + "</div>"
        middle = '<div class="arrow"><span></span></div>'
        if recipe.extra:
            middle = f'<div class="via">{self.slot([recipe.extra[1]], 1, root)}<small>{e(recipe.extra[0])}</small></div>' + middle
        outputs = ""
        for item, count, chance in recipe.outputs:
            outputs += self.slot([item], count, root, big=True)
            if chance < 1:
                outputs += f'<small class="chance">{chance * 100:g} %</small>'
        notes = "".join(f"<li>{e(n)}</li>" for n in recipe.notes)
        station_html = ""
        if station:
            station_html = (f'<a class="card-station" href="{root}stations/{station[0]}.html">'
                            f'{self.icon(station[2], root, 16, "icon-sm")}{e(station[1])}</a>')
        return (f'<div class="recipe panel">{station_html}<div class="recipe-body">{left}{middle}'
                f'<div class="outputs">{outputs}</div></div>{f"<ul class=notes>{notes}</ul>" if notes else ""}</div>')

    def page(self, path: str, title: str, body: str, depth: int, active: str = ""):
        root = "../" * depth
        nav = [("index.html", "Start", "home"), ("einstieg.html", "Einstieg", "start"), ("stations.html", "Stationen", "stations"),
               ("items.html", "Items", "items"), ("anbau.html", "Anbau", "farming"),
               ("effects.html", "Effekte", "effects"), ("guides.html", "Anleitungen", "guides"), ("commands.html", "Befehle", "commands")]
        links = "".join(f'<a class="nav-link{" active" if key == active else ""}" href="{root}{href}">{label}</a>' for href, label, key in nav)
        doc = f"""<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(title)} – Kaleidoscope Wiki</title>
<link rel="icon" href="{root}assets/icons/kaleidoscope/cookbook.png">
<link rel="stylesheet" href="{root}assets/style.css">
</head>
<body>
<header class="topbar">
  <a class="brand" href="{root}index.html">{self.icon("kaleidoscope:cookbook", root, 32, "brand-icon")}<span>Kaleidoscope<b>Wiki</b></span></a>
  <nav class="nav">{links}</nav>
  <div class="search"><input id="search" type="search" placeholder="Item oder Station suchen …" autocomplete="off"><div id="search-results" class="panel"></div></div>
</header>
<main class="content">
{body}
</main>
<footer class="footer">Kaleidoscope Cookery &amp; Grilling – Paper-Plugin nach den Mods von Kaleidoscope Official Production Team und breezeth.
Assets unter CC BY-NC-SA 4.0. Keine offizielle Seite der Mod-Autoren.</footer>
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
            name = self.data.name(item)
            lang = self.data.lang
            lore = []
            if value.get("tooltip") and value["tooltip"] in lang:
                lore += [l for l in lang[value["tooltip"]].split("\n") if l.strip()]
            maxims = [lang.get(f"tooltip.{item.replace(':', '.')}.maxim.line{i}", "") for i in range(1, int(value.get("maxim-lines", 0)) + 1)]
            maxims = [m for m in maxims if m]
            stats = []
            food = value.get("food")
            if food:
                if food.get("nutrition"):
                    stats.append(("Hunger", f'{food["nutrition"]} ({food["nutrition"] / 2:g} Keulen)'.replace(".", ",")))
                if food.get("saturation"):
                    stats.append(("Sättigung", f'{food["saturation"]:g}'.replace(".", ",")))
                if food.get("drink"):
                    stats.append(("Art", "Getränk"))
                if food.get("eat-seconds"):
                    stats.append(("Essdauer", f'{food["eat-seconds"]:g} s'.replace(".", ",")))
                if food.get("remainder"):
                    stats.append(("Rest", self.data.name(food["remainder"])))
            plate = value.get("plate")
            if plate:
                stats.append(("Hinstellbar", f'{plate.get("max-bites", 3)} Bisse' + {"1x2": ", 2 Blöcke", "3x3": ", 3×3 Blöcke"}.get(plate.get("size"), "")))
                pf = plate.get("food", {})
                if pf.get("nutrition"):
                    stats.append(("Pro Biss", f'{pf["nutrition"]} Hunger, {pf.get("saturation", 0):g} Sättigung'.replace(".", ",")))
            stats.append(("Stapel", str(value.get("max-stack", 64))))
            stats.append(("ID", f"<code>{e(item)}</code>"))
            effects = self.effect_list(food.get("effects", []) if food else [], root)
            made = self.by_output.get(item, [])
            used = [r for r in self.by_input.get(item, []) if r not in made]
            found = self.sources.get(item, [])
            if not made:
                hint = "Dieses Item lässt sich nicht herstellen."
                if any(f"kaleidoscope_cookery:{p}" in item for p in ("chair", "table", "cook_stool", "long_bench", "eight_immortals", "red_lantern", "tea_banner")):
                    hint = "Möbel und Deko sind noch ohne Funktion und derzeit nicht herstellbar."
                made_html = "" if found else f'<p class="muted">{hint}</p>'
            else:
                made_html = "".join(self.recipe_card(r, root) for r in made[:40])
            found_html = "".join(f"<li>{e(t)}</li>" for t in found)
            used_html = "".join(self.recipe_card(r, root) for r in used[:60]) or '<p class="muted">Wird in keinem Rezept verwendet.</p>'
            stat_rows = "".join(f"<tr><th>{e(k)}</th><td>{v if k == 'ID' else e(v)}</td></tr>" for k, v in stats)
            tooltip = "".join(f'<div class="tt-lore">{e(l)}</div>' for l in lore) + "".join(f'<div class="tt-maxim">{e(m)}</div>' for m in maxims)
            body = f"""
<nav class="crumbs"><a href="{root}items.html">Items</a> › {e(name)}</nav>
<section class="item-hero panel">
  <div class="item-hero-icon">{self.icon(item, root, 96, "icon-xl")}</div>
  <div class="item-hero-text">
    <h1>{e(name)}</h1>
    <div class="mc-tooltip static"><div class="tt-name">{e(name)}</div>{tooltip}</div>
  </div>
  <table class="stats">{stat_rows}</table>
</section>
{f'<section><h2>Effekte beim Essen</h2>{effects}</section>' if effects else ''}
{f'<section><h2>Fundorte</h2><ul class="panel sources">{found_html}</ul></section>' if found_html else ''}
{f'<section><h2>Herstellung</h2><div class="recipes">{made_html}</div></section>' if made_html else ''}
<section><h2>Verwendung</h2><div class="recipes">{used_html}</div></section>
"""
            self.page(f"items/{slug(item)}.html", name, body, 2, "items")
            self.search.append({"n": name, "u": f"items/{slug(item)}.html", "i": f"assets/icons/{slug(item)}.png", "t": "Item"})

    def effect_list(self, effects: list, root: str) -> str:
        rows = []
        for effect in effects:
            key = effect["effect"]
            ns, path = key.split(":")
            name = self.data.lang.get(f"effect.{ns}.{path}", path.replace("_", " ").title())
            level = int(effect.get("amplifier", 0))
            roman = ["", " II", " III", " IV", " V"][level] if level < 5 else f" {level + 1}"
            chance = float(effect.get("probability", 1))
            anchor = f'{root}effects.html#{ns}-{path}' if ns.startswith("kaleidoscope") else None
            label = f'<a href="{anchor}">{e(name)}{roman}</a>' if anchor else f"{e(name)}{roman}"
            extra = f" · {chance * 100:g} %" if chance < 1 else ""
            rows.append(f'<li><span class="effect-name">{label}</span><span class="muted">{seconds(effect.get("duration", 0))}{extra}</span></li>')
        return f'<ul class="effects panel">{"".join(rows)}</ul>' if rows else ""

    def station_pages(self):
        cards = []
        for sid, title, icon, group, intro, steps in STATIONS:
            root = "../"
            recipes = [r for r in self.recipes if r.station == sid]
            seen, unique = set(), []
            for r in recipes:
                key = (tuple(o[0] for o in r.outputs), tuple(tuple(a) for a, _ in r.inputs))
                if key not in seen:
                    seen.add(key)
                    unique.append(r)
            intro_html = "".join(f"<p>{e(p)}</p>" for p in intro)
            steps_html = f'<ol class="steps">{"".join(f"<li>{e(s)}</li>" for s in steps)}</ol>' if steps else ""
            recipe_html = "".join(self.recipe_card(r, root) for r in unique)
            body = f"""
<nav class="crumbs"><a href="{root}stations.html">Stationen</a> › {e(title)}</nav>
<section class="station-hero panel">{self.icon(icon, root, 64, "icon-lg")}<div><span class="badge">{e(group)}</span><h1>{e(title)}</h1>{intro_html}</div></section>
{f'<section><h2>So funktioniert es</h2><div class="panel guide">{steps_html}</div></section>' if steps else ''}
"""
            if unique:
                body += f"""<section><h2>Rezepte <span class="count">{len(unique)}</span></h2>
<input class="filter" type="search" placeholder="Rezepte filtern …" data-filter=".recipes > .recipe">
<div class="recipes">{recipe_html}</div></section>
"""
            self.page(f"stations/{sid}.html", title, body, 1, "stations")
            self.search.append({"n": title, "u": f"stations/{sid}.html", "i": f"assets/icons/{slug(icon)}.png", "t": "Station"})
            cards.append((group, f'<a class="card panel" href="stations/{sid}.html">{self.icon(icon, "", 48, "icon-lg")}'
                                 f'<span class="card-title">{e(title)}</span><span class="muted">{f"{len(unique)} Rezepte" if unique else "Hitzequelle"}</span></a>'))
        groups = {}
        for group, card in cards:
            groups.setdefault(group, []).append(card)
        body = '<h1>Stationen</h1><p class="lead">Alle Kochstationen mit Anleitung und sämtlichen Rezepten.</p>'
        for group, items in groups.items():
            body += f'<section><h2>{e(group)}</h2><div class="cards">{"".join(items)}</div></section>'
        self.page("stations.html", "Stationen", body, 0, "stations")
        return groups

    def items_page(self):
        listed = set()
        sections = []
        for tab, items in self.data.tabs.items():
            entries = [i for i in items if i in self.data.items]
            listed |= set(entries)
            sections.append((TAB_NAMES.get(tab, tab), entries))
        rest = sorted(i for i in self.data.items if i not in listed)
        if rest:
            sections.append(("Sonstiges", rest))
        body = '<h1>Items</h1><p class="lead">Alle Items beider Plugins. Klicke ein Item für Werte, Herstellung und Verwendung.</p>'
        body += '<input class="filter big" type="search" placeholder="Items filtern …" data-filter=".item-grid > a">'
        for title, entries in sections:
            tiles = "".join(f'<a class="item-tile" href="items/{slug(i)}.html" data-tip="{e(self.data.name(i))}" data-name="{e(self.data.name(i).lower())}">'
                            f'{self.icon(i, "", 32)}<span>{e(self.data.name(i))}</span></a>' for i in entries)
            body += f'<section><h2>{e(title)} <span class="count">{len(entries)}</span></h2><div class="item-grid">{tiles}</div></section>'
        self.page("items.html", "Items", body, 0, "items")

    def effects_page(self):
        lang = self.data.lang
        users = {}
        for item, value in self.data.items.items():
            for effect in (value.get("food") or {}).get("effects", []):
                users.setdefault(effect["effect"], []).append(item)
        keys = sorted({k for k in lang if re.match(r"effect\.kaleidoscope_(cookery|grilling)\.[a-z_]+$", k)})
        rows = ""
        for key in keys:
            _, ns, path = key.split(".")
            effect_id = f"{ns}:{path}"
            desc = lang.get(f"{key}.description", "")
            foods = users.get(effect_id, [])
            food_html = "".join(f'<a class="mini" href="items/{slug(i)}.html" data-tip="{e(self.data.name(i))}">{self.icon(i, "", 24, "icon-sm")}</a>' for i in foods)
            rows += (f'<div class="effect-card panel" id="{ns}-{path}"><h3>{e(lang[key])}</h3><p>{e(desc) or "–"}</p>'
                     f'{f"<div class=minis>{food_html}</div>" if food_html else ""}</div>')
        body = ('<h1>Effekte</h1><p class="lead">Eigene Effekte der Mods. Sie stehen während der Wirkung in der Seitenleiste '
                'rechts, weil Minecraft eigene Effekte nicht im Inventar anzeigen kann.</p>' f'<div class="effect-grid">{rows}</div>')
        self.page("effects.html", "Effekte", body, 0, "effects")

    def guides_page(self):
        body = '<h1>Anleitungen</h1><p class="lead">Grundlagen, Anbau und alles, was keine eigene Station ist.</p>'
        body += '<p>Neu? Starte mit dem <a href="einstieg.html">Einstieg</a>; alles zu Pflanzen steht unter <a href="anbau.html">Anbau</a>.</p>'
        for title, icon, lines in OTHER_GUIDES:
            body += (f'<section class="panel guide"><h2>{self.icon(icon, "", 32, "icon-sm")} {e(title)}</h2>'
                     f'<ul>{"".join(f"<li>{e(l)}</li>" for l in lines)}</ul></section>')
        self.page("guides.html", "Anleitungen", body, 0, "guides")

    def recipe_by_id(self, rid: str):
        return next((r for r in self.recipes if r.id == rid), None)

    def start_page(self):
        root = ""

        def cards(*ids):
            found = [self.recipe_by_id(i) for i in ids]
            return '<div class="recipes">' + "".join(self.recipe_card(r, root) for r in found if r) + "</div>"

        def link(item):
            return f'<a href="items/{slug(item)}.html">{e(self.data.name(item))}</a>'

        steps = [
            ("Strohhut und erste Samen",
             f"<p>Stelle einen {link('kaleidoscope_cookery:straw_hat')} aus Weizen und Faden her und setze ihn auf. Baust du jetzt Gras ab, "
             "findest du Samen für Tomaten, Chilis, Salat, Wildreis und Tee (mit Grilling auch Raps, Zwiebeln, Süßkartoffeln und Pfeffer).</p>"
             + cards("kaleidoscope_cookery:straw_hat") +
             '<p>Pflanze die Samen auf Ackerland. Was wo wächst, steht auf der Seite <a href="anbau.html">Anbau</a>.</p>'),
            ("Küchenmesser",
             "<p>Ein Küchenmesser ist das wichtigste Werkzeug: Nur mit einem Küchenmesser schneidest du auf dem Schneidebrett, "
             "und nur damit lassen Tiere Öl und besondere Zutaten fallen. Es gibt sie aus Kupfer, Eisen, Gold und Diamant; "
             f"das Netherit-Messer entsteht am Schmiedetisch. Für den Anfang reicht das {link('kaleidoscope_cookery:copper_kitchen_knife')}.</p>"
             + cards("kaleidoscope_cookery:copper_kitchen_knife", "kaleidoscope_cookery:iron_kitchen_knife")),
            ("Öl besorgen",
             f"<p>Töte ein Schwein (oder Hoglin, Piglin …) mit dem Küchenmesser – es lässt {link('kaleidoscope_cookery:oil')} fallen. "
             f"Öl lagerst du in der {link('kaleidoscope_cookery:oil_pot')}, die bis zu 256 Öl fasst.</p>"),
            ("Herd, Wok und Pfannenwender",
             "<p>Stelle einen Herd auf, zünde ihn mit einem Feuerzeug an und setze den Wok darauf. Zum Rühren brauchst du den Pfannenwender.</p>"
             + cards("kaleidoscope_cookery:stove_campfire", "kaleidoscope_cookery:pot", "kaleidoscope_cookery:kitchen_shovel")),
            ("Das erste Gericht",
             "<ol class=\"steps\"><li>Öl in den Wok geben – jetzt hast du 60 Sekunden für die Zutaten.</li>"
             "<li>Zutaten hineinlegen, z. B. vier Kartoffeln und zwei Zucker.</li>"
             "<li>Mit dem Pfannenwender rühren, bis das Gericht fertig ist (meist 3× rühren, 10 Sekunden).</li>"
             "<li>Mit einer Schüssel herausnehmen – bevor es nach 40 Sekunden anbrennt.</li></ol>"
             + cards("kaleidoscope_cookery:pot/candied_potato")),
            ("Weiter geht's",
             '<ul><li>Das <a href="items/kaleidoscope/cookbook.html">Kochbuch</a> (Buch + beliebige Samen) zeigt alle Rezepte im Spiel; '
             "Rechtsklick damit auf eine Station öffnet direkt deren Seite.</li>"
             '<li><a href="stations/stockpot.html">Suppentopf</a> für Suppen, <a href="stations/chopping_board.html">Schneidebrett</a> '
             'für Zutaten, <a href="stations/millstone.html">Mühlstein</a> für Mehl und Gewürze.</li>'
             '<li>Mit Grilling: <a href="stations/skewering.html">Spieße aufspießen</a> und auf dem <a href="stations/grill.html">Grill</a> garen.</li>'
             '<li>Flex-Rezepte belohnen das richtige Mengenverhältnis mit besserer Qualität – siehe <a href="guides.html">Anleitungen</a>.</li></ul>'),
        ]
        body = ('<h1>Einstieg</h1><p class="lead">Vom ersten Samen bis zum ersten Gericht – Schritt für Schritt. '
                "Beim Betreten des Servers lädt das Resourcepack automatisch; klappt das nicht, hilft <code>/kc pack</code>.</p>")
        for i, (title, content) in enumerate(steps, 1):
            body += f'<section class="panel guide start-step"><h2><span class="step-no">{i}</span>{e(title)}</h2>{content}</section>'
        body += """<section class="panel guide"><h2>Bedienung</h2><ul>
<li>Stationen setzt du mit Rechtsklick auf einen Block; sie schauen dann zu dir.</li>
<li>Klickst du dabei eine Kiste, einen Ofen oder eine Tür an, wird der Block benutzt – schleiche, um trotzdem zu setzen.</li>
<li>Schleichen + Linksklick baut eine Station ab; Inhalt und Item fallen heraus.</li>
<li>Schaust du eine Station an, zeigt die Leiste über der Hotbar ihren Zustand.</li></ul></section>"""
        self.page("einstieg.html", "Einstieg", body, 0, "start")

    def farming_page(self):
        rows = ""
        for name, seed, harvest, soil, details in CROPS:
            seed_link = f'<a class="mini" href="items/{slug(seed)}.html" data-tip="{e(self.data.name(seed))}">{self.icon(seed, "", 32)}</a>'
            harvest_html = "".join(f'<a class="mini" href="items/{slug(h)}.html" data-tip="{e(self.data.name(h))}">{self.icon(h, "", 32)}</a>' for h in harvest)
            rows += (f'<div class="crop panel"><h3>{e(name)}</h3><div class="crop-row"><div><small>Samen</small>{seed_link}</div>'
                     f'<div class="arrow"><span></span></div><div><small>Ernte</small><div class="minis">{harvest_html}</div></div></div>'
                     f'<p><b>Boden:</b> {e(soil)}</p><p class="muted">{e(details)}</p></div>')
        body = f"""<h1>Anbau</h1>
<p class="lead">Alle Pflanzen wachsen wie Vanilla-Pflanzen über zufällige Ticks, nur in der Simulationsdistanz eines Spielers und
mit genug Licht. Knochenmehl beschleunigt das Wachstum, feuchtes Ackerland drumherum ebenfalls.</p>
<section class="panel guide"><h2>Samen finden</h2><ul>
<li>Setze einen Strohhut auf und baue Gras ab: Mit etwas Glück fallen Samen heraus (je 12,5 % pro Samenart).</li>
<li>Fischminze findest du in Netherfestungen (Truhen und anstelle mancher Netherwarzen), Sichuanpfeffer in Dorf-Truhen.</li>
<li>Die Sichel erntet reife Pflanzen in einem großen Bereich auf einmal und räumt dabei Gras und Blumen ab.</li></ul></section>
<section><h2>Pflanzen</h2><div class="crop-grid">{rows}</div></section>"""
        self.page("anbau.html", "Anbau", body, 0, "farming")

    def commands_page(self):
        rows = "".join(f"<tr><td><code>{e(c)}</code></td><td>{e(d)}</td><td><code>{e(p)}</code></td></tr>" for c, d, p in COMMANDS)
        body = f"""<h1>Befehle &amp; Rechte</h1>
<p class="lead">Hauptbefehl <code>/kaleidoscope</code>, Kurzformen <code>/kc</code> und <code>/kochen</code>.</p>
<div class="panel"><table class="table"><tr><th>Befehl</th><th>Beschreibung</th><th>Recht</th></tr>{rows}</table></div>
<p class="muted">Stationen benutzen und setzen erfordert <code>kaleidoscope.use</code> (Standard: alle). <code>kaleidoscope.admin</code> enthält alle Admin-Rechte.</p>"""
        self.page("commands.html", "Befehle", body, 0, "commands")

    def index_page(self, groups):
        food_count = sum(1 for v in self.data.items.values() if v.get("food"))
        stats = [("Items", len(self.data.items)), ("Rezepte", len(self.recipes)), ("Gerichte & Getränke", food_count), ("Stationen", len(STATIONS))]
        stat_html = "".join(f'<div class="stat panel"><b>{n}</b><span>{e(t)}</span></div>' for t, n in stats)
        featured = "".join(card for items in groups.values() for card in items[:3])
        body = f"""
<section class="hero">
  <div class="hero-text">
    <h1>Kaleidoscope <span>Cookery &amp; Grilling</span></h1>
    <p>Kochen wie in den Mods – auf einem normalen Paper-Server, ganz ohne Client-Mods. Wok, Suppentopf, Grill, Mühle und
    über 300 Items; das Resourcepack lädt der Server automatisch.</p>
    <div class="actions"><a class="btn" href="einstieg.html">Erste Schritte</a><a class="btn alt" href="stations.html">Stationen</a><a class="btn alt" href="items.html">Alle Items</a></div>
  </div>
  <div class="hero-art">{self.icon("kaleidoscope_cookery:pot", "", 96, "icon-xl")}{self.icon("kaleidoscope_grilling:grilled_beef_skewer", "", 96, "icon-xl")}{self.icon("kaleidoscope_cookery:braised_beef", "", 96, "icon-xl")}</div>
</section>
<div class="stats-row">{stat_html}</div>
<section><h2>Beliebte Stationen</h2><div class="cards">{featured}</div></section>
<section class="panel guide"><h2>Schnellstart</h2><p>Neu hier? Die <a href="einstieg.html">Einstiegs-Anleitung</a> führt dich Schritt für Schritt vom ersten Samen zum ersten Gericht.</p><ol class="steps">
<li>Herd setzen und mit einem Feuerzeug anzünden, darauf einen Wok stellen.</li>
<li>Öl in den Wok geben – Öl bekommst du, wenn du ein Schwein mit einem Küchenmesser tötest.</li>
<li>Zutaten hineinlegen und mit dem Pfannenwender rühren, bis das Gericht fertig ist.</li>
<li>Mit einer Schüssel herausnehmen. Welche Zutaten zu welchem Gericht führen, steht bei den <a href="stations/pot.html">Wok-Rezepten</a>.</li>
</ol></section>
"""
        self.page("index.html", "Start", body, 0, "home")

    def assets(self):
        assets = self.out / "assets"
        assets.mkdir(parents=True, exist_ok=True)
        shutil.copy(WIKI_SRC / "style.css", assets / "style.css")
        shutil.copy(WIKI_SRC / "wiki.js", assets / "wiki.js")
        index = json.dumps(sorted(self.search, key=lambda s: s["n"].lower()), ensure_ascii=False, separators=(",", ":"))
        (assets / "search-index.js").write_text(f"window.WIKI_INDEX={index};\n", encoding="utf-8")


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
    vanilla = Vanilla(cache)
    data = Data(vanilla)
    recipes = build_recipes(data)
    site = Site(data, recipes, out)
    groups = site.station_pages()
    site.item_pages()
    site.items_page()
    site.effects_page()
    site.guides_page()
    site.start_page()
    site.farming_page()
    site.commands_page()
    site.index_page(groups)
    site.assets()
    install_font(cache, out)
    pages = len(list(out.rglob("*.html")))
    print(f"Wiki: {pages} pages, {len(recipes)} recipes, {len(site.icons_written)} icons -> {out}")


if __name__ == "__main__":
    main()
