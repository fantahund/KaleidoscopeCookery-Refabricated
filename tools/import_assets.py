#!/usr/bin/env python3
"""
Importiert Texturen, Modelle, Sounds und die englischen Sprachdateien aus
Kaleidoscope Cookery (Branch 1.21.1-neoforge) nach resourcepack/imported bzw. mit
--project grilling --dest ../KaleidoscopeGrilling Kaleidoscope Grilling (neoforge-1.21.1 + common)
in dessen resourcepack/imported und konvertiert sie auf das Format von Minecraft 26.3.

Konvertierung:
  * NeoForge-Felder entfernen (render_type, neoforge_data, ...)
  * neoforge:separate_transforms -> Item-Model-Definition mit select auf display_context
  * Item-Overrides (predicate) -> range_dispatch auf custom_model_data (Index pro Property,
    siehe core/src/main/resources/generated/model_properties.yml)
  * Fuer jedes Modell eine Item-Model-Definition unter assets/<ns>/items/
  * sounds.json pro Namespace zusammenfuehren

Aufruf: python3 -I tools/import_assets.py [--cache ~/.cache/kaleidoscope-pack]
Die Mod-Repos werden bei Bedarf in den Cache geklont (feste Commits, siehe SOURCES).
"""
import argparse
import itertools
import re
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROPERTIES_FILE = Path("src") / "main" / "resources" / "generated" / "model_properties.yml"

SOURCES = {
    # Fabric-Fork (Refabricated, 26.x): Grundlage seit 2026-10-10
    "refab": ("https://github.com/NightEpiphany/KaleidoscopeCookery-Refabricated.git", "26.4-fabric",
              "796f94dd7eb00b85988366ae0f623d46148b7612"),
    "grilling": ("https://github.com/breezeth-CN/KaleidoscopeGrilling.git", "main",
                 "9a1acdab27698457bec16c9362678e574895a28c"),
}

# Reihenfolge = Prioritaet (spaetere Ebenen ueberschreiben fruehere). Grilling ist ein eigenes Plugin mit eigenem Pack
# (Repo KaleidoscopeGrilling); seine Overrides im Cookery-Namespace (Oelkanne) landen in dessen Pack.
LAYERS = {
    # Ess-Animationen: eingebautes Pack des Forks (nur Vanilla-Properties using_item/use_duration)
    "cookery": [("refab", "src/main/resources/assets"), ("refab", "src/main/resources/resourcepacks/kaleidoscope_eating_animation/assets")],
    "grilling": [("grilling", "common/src/main/resources/assets"), ("grilling", "neoforge-1.21.1/src/main/resources/assets")],
}
NAMESPACES = {"kaleidoscope_cookery", "kaleidoscope_grilling", "minecraft"}
COPY_DIRS = {"models", "textures", "sounds", "equipment"}
# Nur fuer Mod-Code relevante Modelle
SKIP_FILES = {"models/item_render_replacer.json"}
REMOVE_MODEL_KEYS = {"render_type", "neoforge_data", "forge_data", "loader", "visibility", "renderTypes"}


def ensure_source(cache: Path, name: str) -> Path:
    url, branch, commit = SOURCES[name]
    path = cache / name
    if not path.exists():
        subprocess.run(["git", "clone", "-q", "--depth", "50", "-b", branch, url, str(path)], check=True)
    head = subprocess.run(["git", "-C", str(path), "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    if head != commit:
        print(f"WARNUNG: {name} steht auf {head}, erwartet {commit}", file=sys.stderr)
    return path


def read_json(path: Path):
    with open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=False)
        f.write("\n")


def ref(namespace: str, path: str) -> str:
    return f"{namespace}:{path}"


def clean_model(model: dict) -> dict:
    model = {k: v for k, v in model.items() if k not in REMOVE_MODEL_KEYS}
    for element in model.get("elements", []):
        for key in list(element):
            if key in REMOVE_MODEL_KEYS:
                del element[key]
        for face in element.get("faces", {}).values():
            for key in list(face):
                if key in REMOVE_MODEL_KEYS:
                    del face[key]
    return model


class Importer:
    def __init__(self, cache: Path, project: str, dest: Path, base_properties: dict):
        self.cache = cache
        self.project = project
        self.out = dest / "resourcepack" / "imported"
        self.properties_out = dest / ("core" if project == "cookery" else ".") / PROPERTIES_FILE
        self.files = {}          # Zielpfad relativ zu assets -> Quelldatei
        self.sounds = {}         # namespace -> dict
        self.lang = {}           # namespace -> dict (en_us)
        self.models = {}         # "ns:path" -> model dict (bereits bereinigt)
        self.item_defs = {}      # "ns/items/<path>.json" -> definition
        # predicate-Name -> custom_model_data-Index; Grilling zaehlt nach den Cookery-Indizes weiter (gleiche Items, z. B. Oelkanne)
        self.properties = dict(base_properties)
        self.base_properties = set(base_properties)
        self.notes = []
        self.fork_items = {}     # "ns/items/<path>.json" -> Definition aus dem Fork

    def collect(self):
        for repo, rel in LAYERS[self.project]:
            base = ensure_source(self.cache, repo) / rel
            for ns_dir in sorted(base.iterdir()):
                if ns_dir.name not in NAMESPACES:
                    continue
                ns = ns_dir.name
                for file in sorted(ns_dir.rglob("*")):
                    if not file.is_file():
                        continue
                    sub = file.relative_to(ns_dir).as_posix()
                    top = sub.split("/", 1)[0]
                    if sub == "sounds.json":
                        self.sounds.setdefault(ns, {}).update(read_json(file))
                    elif sub == "lang/en_us.json" and ns != "minecraft":
                        self.lang.setdefault(ns, {}).update(read_json(file))
                    elif top == "items" and sub.endswith(".json"):
                        # Fertige Item-Model-Definitionen (26.x) des Forks
                        self.fork_items[f"{ns}/{sub}"] = read_json(file)
                    elif top in COPY_DIRS and sub not in SKIP_FILES:
                        self.files[f"{ns}/{sub}"] = file

    def convert_models(self):
        for target in sorted(self.files):
            if not target.endswith(".json") or "/models/" not in target:
                continue
            ns, sub = target.split("/", 1)
            path = sub[len("models/"):-len(".json")]
            model = read_json(self.files[target])
            self.models[ref(ns, path)] = model
        for model_ref in list(self.models):
            ns, path = model_ref.split(":", 1)
            model = self.models[model_ref]
            definition = {"model": {"type": "minecraft:model", "model": model_ref}}

            if model.get("loader") == "neoforge:separate_transforms":
                base = model["base"]
                base_ref = self.add_inline_model(ns, path + "__base", base)
                cases = []
                for context, perspective in model.get("perspectives", {}).items():
                    perspective_ref = self.add_inline_model(ns, f"{path}__{context}", perspective)
                    cases.append({"when": context, "model": {"type": "minecraft:model", "model": perspective_ref}})
                # Das Modell selbst wird zur Basis (fuer Displays/Parents nutzbar)
                merged = dict(base)
                if "display" in model:
                    merged["display"] = model["display"]
                if "gui_light" in model:
                    merged["gui_light"] = model["gui_light"]
                self.models[model_ref] = clean_model(merged)
                definition = {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                                        "cases": cases, "fallback": {"type": "minecraft:model", "model": base_ref}}}
            else:
                self.models[model_ref] = clean_model(model)

            overrides = model.get("overrides")
            if overrides:
                definition = {"model": self.convert_overrides(model_ref, overrides)}
                self.models[model_ref].pop("overrides", None)

            if path.startswith("item/") and "/" not in path[len("item/"):]:
                item_def_path = f"{ns}/items/{path[len('item/'):]}.json"
            else:
                item_def_path = f"{ns}/items/{path}.json"
            self.item_defs[item_def_path] = definition

    # Mod-eigene Item-Properties der Fork-Definitionen -> Name in model_properties.yml (wie die alten Predicates)
    FORK_PROPERTIES = {
        "kaleidoscope_cookery:oil_pot/has_oil": "kaleidoscope_cookery:has_oil",
        "kaleidoscope_cookery:kitchen_shovel/has_oil": "kaleidoscope_cookery:has_oil",
        "kaleidoscope_cookery:recipe_item/has_recipe": "kaleidoscope_cookery:has_recipe",
        "kaleidoscope_cookery:steamer/has_item": "kaleidoscope_cookery:has_items",
        "kaleidoscope_cookery:transmutation_lunch_bag/has_food": "kaleidoscope_cookery:has_items",
        "kaleidoscope_cookery:ingredient/special_render": "kaleidoscope_cookery:special_render",
    }

    def convert_fork_node(self, node):
        """Mod-Conditions in Vanilla umwandeln: Bool-Properties -> custom_model_data-Index (>= 1 = wahr)."""
        if isinstance(node, list):
            return [self.convert_fork_node(n) for n in node]
        if not isinstance(node, dict):
            return node
        node = {k: self.convert_fork_node(v) for k, v in node.items()}
        if node.get("type") == "minecraft:condition" and node.get("property") == "kaleidoscope_cookery:stockpot_lid/using":
            node["property"] = "minecraft:using_item"
        elif node.get("type") == "minecraft:condition" and node.get("property") in self.FORK_PROPERTIES:
            index = self.property_index(self.FORK_PROPERTIES[node["property"]])
            return {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": index,
                    "entries": [{"threshold": 1.0, "model": node["on_true"]}], "fallback": node["on_false"]}
        return node

    def apply_fork_items(self):
        for path, definition in sorted(self.fork_items.items()):
            if "kaleidoscope_cookery:model_arguments" in json.dumps(definition):
                continue  # Modell-Vorschau (Debug-Item) braucht Mod-Code
            self.item_defs[path] = self.convert_fork_node(definition)
        unknown = {p for d in self.item_defs.values() for p in re.findall(r'"property": "(kaleidoscope_cookery:[^"]+)"', json.dumps(d))}
        if unknown:
            raise SystemExit(f"Unbekannte Item-Properties: {sorted(unknown)}")

    def max_tint(self, model_ref: str, depth: int = 0) -> int:
        model = self.models.get(model_ref)
        if model is None or depth > 16:
            return -1
        if "elements" in model:
            return max((face.get("tintindex", -1) for e in model["elements"] for face in e.get("faces", {}).values()), default=-1)
        parent = model.get("parent")
        return self.max_tint(parent if ":" in (parent or "") else f"minecraft:{parent}", depth + 1) if parent else -1

    def add_tints(self, node):
        """Spieße (Grilling): Zutaten-Farben kommen im Mod vom Client (SkewerColorProvider). Hier liest jedes
        tintindex die Farbe custom_model_data.colors[tintindex]; das Plugin schreibt die Farben in das Item."""
        if isinstance(node, list):
            for n in node:
                self.add_tints(n)
            return
        if not isinstance(node, dict):
            return
        if node.get("type") == "minecraft:model" and "tints" not in node:
            highest = self.max_tint(node.get("model", ""))
            if highest >= 0:
                node["tints"] = [{"type": "minecraft:custom_model_data", "index": i, "default": -1} for i in range(highest + 1)]
        for value in node.values():
            if isinstance(value, (dict, list)):
                self.add_tints(value)

    def add_inline_model(self, ns: str, path: str, model: dict) -> str:
        model_ref = ref(ns, path)
        self.models[model_ref] = clean_model(model)
        return model_ref

    def property_index(self, name: str) -> int:
        if name not in self.properties:
            self.properties[name] = len(self.properties)
        return self.properties[name]

    # Predicates, die es in 26.x als echte Item-Model-Bedingung gibt (statt custom_model_data)
    CONDITION_PROPERTIES = {"kaleidoscope_cookery:using": "minecraft:using_item"}
    # Predicates mit Vanilla-Gegenstück als Zahl: Teig ziehen = Benutzungsdauer / 10 (RawDoughItem#getTexture)
    RANGE_PROPERTIES = {"kaleidoscope_cookery:pull": {"property": "minecraft:use_duration", "scale": 0.1}}

    def convert_overrides(self, base_ref: str, overrides: list) -> dict:
        """Vanilla-1.21.1-Semantik: das letzte Override, dessen Predicates alle <= Wert sind, gewinnt."""
        names = {name for o in overrides for name in o["predicate"]}
        if len(names) == 1 and next(iter(names)) in self.CONDITION_PROPERTIES:
            name = next(iter(names))

            def pick(value):
                result = base_ref
                for o in overrides:
                    if value >= float(o["predicate"][name]):
                        result = o["model"]
                return {"type": "minecraft:model", "model": result}
            return {"type": "minecraft:condition", "property": self.CONDITION_PROPERTIES[name], "on_true": pick(1.0), "on_false": pick(0.0)}
        props = []
        for override in overrides:
            for name in override["predicate"]:
                if name not in props:
                    props.append(name)
        thresholds = {name: sorted({float(o["predicate"][name]) for o in overrides if name in o["predicate"]}) for name in props}

        def winner(values: dict) -> str:
            result = base_ref
            for override in overrides:
                if all(values[n] >= float(v) for n, v in override["predicate"].items()):
                    result = override["model"]
            return result

        def build(level: int, values: dict) -> dict:
            if level == len(props):
                return {"type": "minecraft:model", "model": winner(values)}
            name = props[level]
            # Wert unterhalb aller Schwellen (z. B. 0 bei Schwelle 0.25)
            fallback = build(level + 1, {**values, name: float("-inf")})
            entries = [{"threshold": t, "model": build(level + 1, {**values, name: t})} for t in thresholds[name]]
            if name in self.RANGE_PROPERTIES:
                return {"type": "minecraft:range_dispatch", **self.RANGE_PROPERTIES[name], "entries": entries, "fallback": fallback}
            return {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                    "index": self.property_index(name), "entries": entries, "fallback": fallback}

        return build(0, {})

    def write(self):
        if self.out.exists():
            shutil.rmtree(self.out)
        assets = self.out / "assets"
        for target, source in sorted(self.files.items()):
            if "/models/" in target and target.endswith(".json"):
                continue
            dest = assets / target
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, dest)
        for model_ref, model in sorted(self.models.items()):
            ns, path = model_ref.split(":", 1)
            write_json(assets / ns / "models" / f"{path}.json", model)
        for path, definition in sorted(self.item_defs.items()):
            write_json(assets / path, definition)
        for ns, sounds in sorted(self.sounds.items()):
            write_json(assets / ns / "sounds.json", dict(sorted(sounds.items())))
        for ns, lang in sorted(self.lang.items()):
            write_json(assets / ns / "lang" / "en_us.json", lang)

        self.properties_out.parent.mkdir(parents=True, exist_ok=True)
        with open(self.properties_out, "w", encoding="utf-8") as f:
            f.write("# Generiert von tools/import_assets.py - nicht von Hand bearbeiten.\n")
            f.write("# Item-Property (aus den Mod-Overrides) -> Index in custom_model_data.floats\n")
            for name, index in self.properties.items():
                if name in self.base_properties:
                    continue
                f.write(f"\"{name}\": {index}\n")

        sources = {name: SOURCES[name][2] for name in {repo for repo, _ in LAYERS[self.project]}}
        write_json(self.out / "SOURCES.json", sources)

    def run(self):
        self.collect()
        self.convert_models()
        self.apply_fork_items()
        if self.project == "grilling":
            for definition in self.item_defs.values():
                self.add_tints(definition)
        self.write()
        print(f"Dateien: {len(self.files)}, Modelle: {len(self.models)}, Item-Definitionen: {len(self.item_defs)}, "
              f"Properties: {len(self.properties)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=str(Path.home() / ".cache" / "kaleidoscope-pack"))
    parser.add_argument("--project", choices=sorted(LAYERS), default="cookery")
    parser.add_argument("--dest", help="Projekt-Wurzel (Standard: dieses Repo; fuer grilling das Repo KaleidoscopeGrilling)")
    args = parser.parse_args()
    cache = Path(args.cache).expanduser()
    cache.mkdir(parents=True, exist_ok=True)
    dest = Path(args.dest).resolve() if args.dest else ROOT
    base = {}
    if args.project != "cookery":
        base_file = ROOT / "core" / PROPERTIES_FILE
        for line in base_file.read_text(encoding="utf-8").splitlines():
            if line.startswith('"'):
                name, index = line.rsplit(":", 1)
                base[name.strip().strip('"')] = int(index)
    Importer(cache, args.project, dest, base).run()


if __name__ == "__main__":
    main()
