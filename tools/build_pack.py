#!/usr/bin/env python3
"""
Baut das Server-Resourcepack: resourcepack/imported + resourcepack/custom (custom gewinnt,
Sprachdateien und sounds.json werden schluesselweise zusammengefuehrt), prueft alle Referenzen
und schreibt ein deterministisches Zip samt SHA1.

Geprueft wird:
  * jede JSON-Datei ist gueltig
  * Item-Model-Definitionen zeigen nur auf vorhandene Modelle
  * Modelle: Parent-Kette und alle Texturen existieren (im Pack oder in Vanilla 26.3)
  * sounds.json: alle Sound-Dateien existieren
  * Atlases fuer Textur-Ordner ausserhalb von block/ und item/ werden automatisch erzeugt

Vanilla-Assets kommen aus dem 26.3-Client-Jar (wird einmalig in den Cache geladen).

Aufruf: python3 -I tools/build_pack.py --out target/generated-resources/pack
Addon-Pack (eigenes Plugin, z. B. KaleidoscopeGrilling): --root <Addon-Repo> --base <dieses Repo> --zip-name ...
--icon-font ...; das Basis-Pack wird nur zum Pruefen der Referenzen gelesen, nicht mit gezippt.
"""
import argparse
import hashlib
import io
import math
import json
import sys
import urllib.request
import zipfile

from PIL import Image
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MC_VERSION = "26.3"
# Große Item-Bilder fürs Kochbuch (Schrift kaleidoscope:icons): Zeichen ab U+F0000, 16 Pixel hoch, oben an der Zeile
ICON_FIRST_CHAR = 0xF0000
ICON_HEIGHT = 16
ICON_ASCENT = 7
# Stations-Bilder im Rezept-Diagramm (32 Pixel, in der mittleren Reihe des Rasters, siehe Cookbook#diagram)
DIAGRAM_ITEMS = {"minecraft:crafting_table", "minecraft:furnace", "kaleidoscope_cookery:shawarma_spit"}
# Items, deren Block aus mehreren Modellen übereinander besteht: (Modell, y-Versatz in Modell-Pixeln)
MULTIBLOCK_MODELS = {"kaleidoscope_cookery:shawarma_spit": [("kaleidoscope_cookery:block/shawarma_spit_lower", 0),
                                                            ("kaleidoscope_cookery:block/shawarma_spit_upper", 16)]}
ZIP_TIME = (2026, 1, 1, 0, 0, 0)
MERGE_JSON = ("/lang/", "/sounds.json")
BUILTIN_PARENTS = {"builtin/generated", "minecraft:builtin/generated"}


def load_json(data: bytes, name: str):
    try:
        return json.loads(data.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise ValueError(f"{name}: ungueltiges JSON ({e})")


def norm(reference: str) -> tuple[str, str]:
    if ":" in reference:
        ns, path = reference.split(":", 1)
    else:
        ns, path = "minecraft", reference
    return ns, path


class Vanilla:
    def __init__(self, cache: Path):
        self.jar_path = cache / f"client-{MC_VERSION}.jar"
        if not self.jar_path.exists():
            self.download()
        self.names = set(zipfile.ZipFile(self.jar_path).namelist())

    def download(self):
        print(f"Lade Vanilla-Client {MC_VERSION} fuer die Validierung ...")
        self.jar_path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json") as r:
            manifest = json.load(r)
        url = next(v["url"] for v in manifest["versions"] if v["id"] == MC_VERSION)
        with urllib.request.urlopen(url) as r:
            client = json.load(r)["downloads"]["client"]["url"]
        tmp = self.jar_path.with_suffix(".part")
        urllib.request.urlretrieve(client, tmp)
        tmp.rename(self.jar_path)

    def has(self, path: str) -> bool:
        return path in self.names

    def read(self, path: str) -> bytes:
        with zipfile.ZipFile(self.jar_path) as jar:
            return jar.read(path)


class PackBuilder:
    def __init__(self, vanilla: Vanilla, root: Path, base: Path | None, zip_name: str, icon_font: str):
        self.vanilla = vanilla
        self.root = root
        self.zip_name = zip_name
        self.icon_font = icon_font
        self.files: dict[str, bytes] = {}
        # Dateien des Basis-Packs (nur lesen: der Client hat es zusätzlich geladen)
        self.base: dict[str, bytes] = self.read_layers(base) if base else {}
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.texture_dirs: set[str] = set()

    def collect(self):
        self.files = self.read_layers(self.root)

    @staticmethod
    def read_layers(root: Path) -> dict[str, bytes]:
        files = {}
        for layer in (root / "resourcepack" / "imported", root / "resourcepack" / "custom"):
            if not layer.exists():
                continue
            for file in sorted(layer.rglob("*")):
                if not file.is_file() or file.name == "SOURCES.json":
                    continue
                rel = file.relative_to(layer).as_posix()
                data = file.read_bytes()
                if rel in files and rel.endswith(".json") and any(m in rel for m in MERGE_JSON):
                    merged = load_json(files[rel], rel)
                    merged.update(load_json(data, rel))
                    data = json.dumps(merged, ensure_ascii=False).encode("utf-8")
                files[rel] = data
        return files

    # ---- Pruefungen ----

    def exists(self, path: str) -> bool:
        return path in self.files or path in self.base or self.vanilla.has(path)

    def read(self, path: str):
        if path in self.files or path in self.base:
            return load_json(self.files.get(path) or self.base[path], path)
        return load_json(self.vanilla.read(path), path)

    def model_path(self, reference: str) -> str:
        ns, path = norm(reference)
        return f"assets/{ns}/models/{path}.json"

    def check_json(self):
        for name, data in self.files.items():
            if name.endswith((".json", ".mcmeta")):
                try:
                    load_json(data, name)
                except ValueError as e:
                    self.errors.append(str(e))

    def check_models(self):
        checked = set()
        for name in list(self.files):
            if "/models/" in name and name.endswith(".json"):
                self.check_model(name, checked, [])

    def check_model(self, path: str, checked: set, chain: list):
        if path in checked:
            return
        checked.add(path)
        if path in chain:
            self.errors.append(f"Parent-Schleife: {' -> '.join(chain + [path])}")
            return
        if path not in self.files:
            return  # Vanilla-Modell
        model = self.read(path)
        parent = model.get("parent")
        if parent:
            if parent in BUILTIN_PARENTS:
                pass
            elif norm(parent)[1].startswith("builtin/"):
                self.errors.append(f"{path}: Parent {parent} gibt es ohne Mod-Code nicht")
            else:
                parent_path = self.model_path(parent)
                if not self.exists(parent_path):
                    self.errors.append(f"{path}: Parent {parent} fehlt")
                else:
                    self.check_model(parent_path, checked, chain + [path])
        for key, value in model.get("textures", {}).items():
            if isinstance(value, dict):
                value = value.get("sprite", "")
            if not value or value.startswith("#"):
                continue
            ns, tex = norm(value)
            tex_path = f"assets/{ns}/textures/{tex}.png"
            if not self.exists(tex_path):
                self.errors.append(f"{path}: Textur {value} ({key}) fehlt")
            elif tex_path in self.files:
                top = tex.split("/", 1)[0]
                if top not in ("block", "item"):
                    self.texture_dirs.add(top)

    def check_item_definitions(self):
        for name in self.files:
            parts = name.split("/")
            if len(parts) > 3 and parts[0] == "assets" and parts[2] == "items" and name.endswith(".json"):
                self.walk_item_model(name, self.read(name).get("model"))

    def walk_item_model(self, origin: str, node):
        if isinstance(node, list):
            for child in node:
                self.walk_item_model(origin, child)
            return
        if not isinstance(node, dict):
            return
        if node.get("type") in ("minecraft:model", "model") and "model" in node:
            if not self.exists(self.model_path(node["model"])):
                self.errors.append(f"{origin}: Modell {node['model']} fehlt")
        for key, value in node.items():
            if key != "model" or isinstance(value, (dict, list)):
                self.walk_item_model(origin, value)

    def check_fonts(self):
        for name in self.files:
            parts = name.split("/")
            if len(parts) > 3 and parts[2] == "font" and name.endswith(".json"):
                for provider in self.read(name).get("providers", []):
                    if provider.get("type") == "bitmap":
                        ns, path = norm(provider["file"])
                        if not self.exists(f"assets/{ns}/textures/{path}"):
                            self.errors.append(f"{name}: Textur {provider['file']} fehlt")

    def check_sounds(self):
        for name in self.files:
            if not name.endswith("/sounds.json"):
                continue
            ns = name.split("/")[1]
            for event, entry in self.read(name).items():
                for sound in entry.get("sounds", []):
                    if isinstance(sound, dict):
                        if sound.get("type") == "event":
                            continue
                        sound = sound.get("name", "")
                    sound_ns, sound_path = norm(sound) if ":" in sound else (ns, sound)
                    path = f"assets/{sound_ns}/sounds/{sound_path}.ogg"
                    if path not in self.files and path not in self.base:
                        if sound_ns == "minecraft":
                            continue  # Vanilla-Sounds liegen im Asset-Index, nicht im Jar
                        self.errors.append(f"{name}: Sound {sound} ({event}) fehlt")

    def add_atlases(self):
        if not self.texture_dirs:
            return
        sources = [{"type": "minecraft:directory", "source": d, "prefix": f"{d}/"} for d in sorted(self.texture_dirs)]
        for atlas in ("blocks", "items"):
            path = f"assets/minecraft/atlases/{atlas}.json"
            data = load_json(self.files[path], path) if path in self.files else {"sources": []}
            known = {json.dumps(s, sort_keys=True) for s in data["sources"]}
            data["sources"] += [s for s in sources if json.dumps(s, sort_keys=True) not in known]
            self.files[path] = json.dumps(data).encode("utf-8")

    # ---- Item-Bilder fürs Kochbuch ----

    ICON_TEXTURE_KEYS = ("layer0", "all", "side", "front", "top", "texture", "cross", "plant", "particle")

    def write_item_icons(self, out: Path):
        """
        Für jedes Item (Vanilla aus dem Client-Jar und die Mod-Items) eine flache Textur bestimmen, die als Sprite
        im Text angezeigt werden kann: item_icons.txt mit Zeilen "item=atlas|sprite".
        """
        jar = zipfile.ZipFile(self.vanilla.jar_path)
        cache = {}

        def read(path):
            if path in cache:
                return cache[path]
            if path in self.files or path in self.base:
                data = load_json(self.files.get(path) or self.base[path], path)
            elif path in self.vanilla.names:
                data = json.loads(jar.read(path))
            else:
                data = None
            cache[path] = data
            return data

        def first_model(node):
            """Modell, das der Client im Inventar ohne besondere Daten zeigt (custom_model_data 0, nicht benutzt)."""
            if not isinstance(node, dict):
                return None
            kind = node.get("type", "").removeprefix("minecraft:")
            if kind == "model":
                return node.get("model")
            if kind == "special":
                return node.get("base")
            if kind == "range_dispatch":
                chosen = None
                for entry in sorted(node.get("entries", []), key=lambda e: e.get("threshold", 0)):
                    if entry.get("threshold", 0) <= 0:
                        chosen = entry.get("model")
                return first_model(chosen) if chosen else first_model(node.get("fallback"))
            if kind == "select":
                for case in node.get("cases", []):
                    when = case.get("when")
                    if when == "gui" or (isinstance(when, list) and "gui" in when):
                        return first_model(case.get("model"))
                return first_model(node.get("fallback")) or (first_model(node["cases"][0].get("model")) if node.get("cases") else None)
            if kind == "condition":
                return first_model(node.get("on_false"))
            if kind == "composite":
                for model in node.get("models", []):
                    found = first_model(model)
                    if found:
                        return found
            return first_model(node.get("fallback"))

        def textures_of(reference, chain=None):
            textures = {}
            seen = 0
            while reference and seen < 16:
                seen += 1
                if chain is not None:
                    chain.append(norm(reference))
                model = read(self.model_path(reference))
                if model is None:
                    break
                for key, value in model.get("textures", {}).items():
                    textures.setdefault(key, value)
                reference = model.get("parent")
            return textures

        def resolve(textures, value, depth=0):
            if isinstance(value, dict):
                value = value.get("sprite")  # 26.x: {"sprite": ..., "force_translucent": true}
            while isinstance(value, str) and value.startswith("#") and depth < 8:
                value = textures.get(value[1:])
                if isinstance(value, dict):
                    value = value.get("sprite")
                depth += 1
            return value if isinstance(value, str) and not value.startswith("#") else None

        def atlas_of(texture):
            ns, path = norm(texture)
            top = path.split("/", 1)[0]
            if top == "item":
                return "minecraft:items"
            if top == "block" or top in self.texture_dirs:
                return "minecraft:blocks"
            return None

        def load_texture(texture):
            """Textur in Originalgröße; animierte (Streifen) nur das erste Bild."""
            tns, tpath = norm(texture)
            png = f"assets/{tns}/textures/{tpath}.png"
            if png in self.files or png in self.base:
                data = self.files.get(png) or self.base[png]
            elif png in self.vanilla.names:
                data = jar.read(png)
            else:
                return None
            image = Image.open(io.BytesIO(data)).convert("RGBA")
            if image.height > image.width and image.height % image.width == 0:
                image = image.crop((0, 0, image.width, image.width))
            return image

        def is_animated(texture):
            tns, tpath = norm(texture)
            meta = f"assets/{tns}/textures/{tpath}.png.mcmeta"
            data = read(meta) if (meta in self.files or meta in self.base or meta in self.vanilla.names) else None
            return bool(data and "animation" in data)

        def shade(image, factor, tint=None):
            r, g, b, a = image.split()
            channels = [r, g, b]
            if tint:
                channels = [c.point(lambda v, t=t: v * t // 255) for c, t in zip(channels, tint)]
            return Image.merge("RGBA", [c.point(lambda v: int(v * factor)) for c in channels] + [a])

        def model_parts(reference):
            """Texturen, Elemente (vom ersten Modell der Kette, das welche hat) und Inventar-Ansicht (display.gui)."""
            textures = {}
            elements = None
            gui = None
            seen = 0
            while reference and seen < 16:
                seen += 1
                model = read(self.model_path(reference))
                if model is None:
                    break
                for key, value in model.get("textures", {}).items():
                    textures.setdefault(key, value)
                if elements is None and "elements" in model:
                    elements = model["elements"]
                if gui is None and "gui" in model.get("display", {}):
                    gui = model["display"]["gui"]
                reference = model.get("parent")
            return textures, elements, gui or {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}

        FOLIAGE = (124, 189, 107)

        def rotate(point, rx, ry, rz):
            """Quaternionf.rotationXYZ: erst um z, dann y, dann x (Grad)."""
            x, y, z = point
            for axis, angle in (("z", rz), ("y", ry), ("x", rx)):
                if not angle:
                    continue
                c, s_ = math.cos(math.radians(angle)), math.sin(math.radians(angle))
                if axis == "x":
                    y, z = y * c - z * s_, y * s_ + z * c
                elif axis == "y":
                    x, z = x * c + z * s_, -x * s_ + z * c
                else:
                    x, y = x * c - y * s_, x * s_ + y * c
            return x, y, z

        def element_rotation(element, point):
            rotation = element.get("rotation")
            if not rotation:
                return point
            origin = rotation.get("origin", [8, 8, 8])
            local = [point[i] - origin[i] for i in range(3)]
            if "axis" in rotation:
                angles = {"x": 0, "y": 0, "z": 0}
                angles[rotation["axis"]] = rotation.get("angle", 0)
                rx, ry, rz = angles["x"], angles["y"], angles["z"]
            else:
                rx, ry, rz = rotation.get("x", 0), rotation.get("y", 0), rotation.get("z", 0)
            local = rotate(local, rx, ry, rz)
            return tuple(local[i] + origin[i] for i in range(3))

        # Ecken je Fläche: A = Textur-Ursprung (u, v = 0), B = Ende in u-Richtung, C = Ende in v-Richtung;
        # Standard-UV aus den Abmessungen; Helligkeit wie Minecraft (oben hell, Seiten dunkler)
        FACES = {
            "up": (lambda a, b: ((a[0], b[1], a[2]), (b[0], b[1], a[2]), (a[0], b[1], b[2])), lambda a, b: [a[0], a[2], b[0], b[2]], 1.0),
            "down": (lambda a, b: ((a[0], a[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], a[2])), lambda a, b: [a[0], 16 - b[2], b[0], 16 - a[2]], 0.5),
            "north": (lambda a, b: ((b[0], b[1], a[2]), (a[0], b[1], a[2]), (b[0], a[1], a[2])), lambda a, b: [16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1]], 0.8),
            "south": (lambda a, b: ((a[0], b[1], b[2]), (b[0], b[1], b[2]), (a[0], a[1], b[2])), lambda a, b: [a[0], 16 - b[1], b[0], 16 - a[1]], 0.8),
            "west": (lambda a, b: ((a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], a[2])), lambda a, b: [a[2], 16 - b[1], b[2], 16 - a[1]], 0.6),
            "east": (lambda a, b: ((b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], b[2])), lambda a, b: [16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1]], 0.6),
        }

        def render3d(parts):
            """
            Modell so zeichnen, wie es im Inventar erscheint: Inventar-Ansicht (display.gui: Drehung, Größe,
            Verschiebung), gedrehte Elemente, sichtbare Flächen von hinten nach vorne. 2 Pixel je Modell-Pixel,
            4-fach groß gezeichnet und glatt verkleinert.
            parts: Liste aus (Texturen, Elemente, Inventar-Ansicht, y-Versatz in Modell-Pixeln).
            """
            extra = max(offset for *_, offset in parts)
            supersample = 8
            gui = parts[0][2]
            rx, ry, rz = gui.get("rotation", [0, 0, 0])
            tx, ty, tz = gui.get("translation", [0, 0, 0])
            sx_, sy_, sz_ = gui.get("scale", [1, 1, 1])
            # Bei hohen Mehrblock-Modellen (Dönerspieß) die gemeinsame Mitte nehmen und die Fläche nach oben verlängern
            center = (8, 8 + extra / 2, 8)
            width = 32 * supersample
            height = round((32 + extra * sy_ * 2)) * supersample if extra else width

            def screen(point):
                p = ((point[0] - center[0]) * sx_, (point[1] - center[1]) * sy_, (point[2] - center[2]) * sz_)
                x, y, z = rotate(p, rx, ry, rz)
                x, y, z = x + tx, y + ty, z + tz
                return width / 2 + x * 2 * supersample, height / 2 - y * 2 * supersample, z

            faces = []
            for textures, elements, _, offset in parts:
                for element in elements:
                    a = (element["from"][0], element["from"][1] + offset, element["from"][2])
                    b = (element["to"][0], element["to"][1] + offset, element["to"][2])
                    for direction, face in element.get("faces", {}).items():
                        if direction not in FACES:
                            continue
                        texture = resolve(textures, face.get("texture"))
                        if not texture:
                            continue
                        corners_of, default_uv, light = FACES[direction]
                        uv = face.get("uv")
                        if uv is None:
                            raw = default_uv((a[0], a[1] - offset, a[2]), (b[0], b[1] - offset, b[2]))
                            uv = raw
                        corners = [screen(element_rotation(element, c)) for c in corners_of(a, b)]
                        (ax, ay, az), (bx, by, bz), (cx, cy, cz) = corners
                        # Rückseiten weglassen (gegen den Uhrzeigersinn auf dem Bildschirm)
                        if (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) <= 0:
                            continue
                        depth = (az + bz + cz) / 3
                        faces.append((depth, texture, uv, (ax, ay), (bx, by), (cx, cy), light, "tintindex" in face, face.get("rotation", 0)))
            out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
            for _, texture, uv, (ax, ay), (bx, by), (cx, cy), light, tinted, uv_rotation in sorted(faces, key=lambda f: f[0]):
                image = load_texture(texture)
                if image is None:
                    continue
                scale = image.width / 16
                u1, v1, u2, v2 = uv
                box = (min(u1, u2) * scale, min(v1, v2) * scale, max(u1, u2) * scale, max(v1, v2) * scale)
                if box[2] - box[0] < 0.5 or box[3] - box[1] < 0.5:
                    continue
                part = image.crop(tuple(round(v) for v in box))
                if u2 < u1:
                    part = part.transpose(Image.FLIP_LEFT_RIGHT)
                if v2 < v1:
                    part = part.transpose(Image.FLIP_TOP_BOTTOM)
                if uv_rotation:
                    part = part.rotate(-uv_rotation, expand=True)
                part = shade(part, light, FOLIAGE if tinted else None)
                w, h = part.size
                m11, m12, m21, m22 = (bx - ax) / w, (cx - ax) / h, (by - ay) / w, (cy - ay) / h
                det = m11 * m22 - m12 * m21
                if abs(det) < 1e-9:
                    continue
                i11, i12, i21, i22 = m22 / det, -m12 / det, -m21 / det, m11 / det
                data = (i11, i12, -(i11 * ax + i12 * ay), i21, i22, -(i21 * ax + i22 * ay))
                out.alpha_composite(part.transform(out.size, Image.AFFINE, data, Image.NEAREST))
            if not out.getbbox():
                return None
            # 4-fache Auflösung (128 Pixel für 16 Modell-Pixel): auch bei großer GUI scharf
            return out.resize((width // 2, height // 2), Image.BOX)

        def glyph_advance(file, height):
            """Vorschub wie BitmapProvider: Breite bis zur letzten nicht ganz durchsichtigen Spalte, skaliert, + 1."""
            fns, fpath = norm(file)
            png = f"assets/{fns}/textures/{fpath}"
            data = self.files.get(png) or (jar.read(png) if png in self.vanilla.names else None)
            if data is None:
                return height + 1
            image = Image.open(io.BytesIO(data)).convert("RGBA")
            alpha = image.getchannel("A")
            width = 0
            for x in range(image.width - 1, -1, -1):
                if any(alpha.getpixel((x, y)) for y in range(image.height)):
                    width = x + 1
                    break
            return int(0.5 + width * height / image.height) + 1

        def add_glyph(file, height, ascent):
            char = chr(ICON_FIRST_CHAR + len(providers))
            providers.append({"type": "bitmap", "file": file, "height": height, "ascent": ascent, "chars": [char]})
            # Hex des Zeichens und sein Vorschub (der Client kürzt Bilder rechts auf den letzten sichtbaren Pixel)
            return f"{ord(char):X}:{glyph_advance(file, height)}"

        def save_icon(ns, item, image):
            buffer = io.BytesIO()
            image.save(buffer, "PNG")
            self.files[f"assets/{font_ns}/textures/icon/{ns}/{item}.png"] = buffer.getvalue()
            return f"{font_ns}:icon/{ns}/{item}.png"

        font_ns, font_path = norm(self.icon_font)
        # Addon-Pack: nur die eigenen Items (Vanilla und Basis-Items stehen schon in der item_icons.txt des Basis-Packs)
        candidates = set(self.files) if self.base else set(self.files) | self.vanilla.names
        definitions = [n for n in candidates if "/items/" in n and n.startswith("assets/") and n.endswith(".json")
                       and n.count("/") == 3]
        lines = []
        providers = []
        diagrams = []
        for name in sorted(definitions):
            ns = name.split("/")[1]
            item = name.split("/")[3][:-5]
            if ns not in ("minecraft", "kaleidoscope", "kaleidoscope_cookery", "kaleidoscope_grilling"):
                continue
            definition = read(name)
            model = first_model(definition.get("model")) if definition else None
            if not model:
                continue
            chain = []
            textures = textures_of(model, chain)
            # Blockmodelle (nicht item/generated): 3D-Ansicht wie im Inventar
            generated = any(path in ("item/generated", "item/handheld", "builtin/generated") for _, path in chain)
            if not generated:
                if f"{ns}:{item}" in MULTIBLOCK_MODELS:
                    parts = [model_parts(ref) + (offset,) for ref, offset in MULTIBLOCK_MODELS[f"{ns}:{item}"]]
                else:
                    parts = [model_parts(model) + (0,)]
                parts = [(t, e, g, o) for t, e, g, o in parts if e]
                image = render3d(parts) if parts else None
                if image is not None:
                    # Slot-Bild in 64 Pixel (16 Pixel im Spiel, also 4-fach), Stations-Bild in 128 Pixel (32 im Spiel)
                    file = save_icon(ns, item, image.resize((image.width // 2, image.height // 2), Image.BOX))
                    glyph = add_glyph(file, ICON_HEIGHT * image.height // image.width, ICON_ASCENT)
                    if f"{ns}:{item}" in DIAGRAM_ITEMS:
                        large = save_icon(ns, item + "_large", image)
                        shown = image.height // 4
                        # mittig über den drei Slot-Reihen (54 Pixel)
                        diagrams.append(f"@diagram|{ns}:{item}={add_glyph(large, shown, 7 + 18 - (54 - shown) // 2)}")
                    first = resolve(textures, textures.get("particle")) or next((resolve(textures, v) for v in textures.values() if resolve(textures, v)), None)
                    if first and atlas_of(first):
                        tns, tpath = norm(first)
                        lines.append(f"{ns}:{item}={atlas_of(first)}|{tns}:{tpath}|{glyph}")
                    else:
                        lines.append(f"{ns}:{item}=minecraft:blocks|minecraft:block/stone|{glyph}")
                    continue
            for key in self.ICON_TEXTURE_KEYS + tuple(textures):
                texture = resolve(textures, textures.get(key))
                if texture and atlas_of(texture):
                    tns, tpath = norm(texture)
                    png = f"assets/{tns}/textures/{tpath}.png"
                    # Große Variante als Schriftzeichen (16 Pixel), nicht für animierte Texturen (Streifen mit mehreren Bildern)
                    glyph = ""
                    if png in self.files or png in self.vanilla.names:
                        # Animierte Texturen: erstes Bild als eigene Datei (die Schrift würde sonst den ganzen Streifen zeigen)
                        file = save_icon(ns, item, load_texture(texture)) if is_animated(texture) else f"{tns}:{tpath}.png"
                        glyph = add_glyph(file, ICON_HEIGHT, ICON_ASCENT)
                        if f"{ns}:{item}" in DIAGRAM_ITEMS:
                            diagrams.append(f"@diagram|{ns}:{item}={add_glyph(file, 32, 14)}")
                    lines.append(f"{ns}:{item}={atlas_of(texture)}|{tns}:{tpath}|{glyph}")
                    break
        self.files[f"assets/{font_ns}/font/{font_path}.json"] = json.dumps({"providers": providers}, ensure_ascii=True).encode("utf-8")
        out.mkdir(parents=True, exist_ok=True)
        (out / "item_icons.txt").write_text("\n".join([f"@font|{self.icon_font}"] + lines + diagrams) + "\n", encoding="utf-8")
        print(f"Item-Bilder: {len(lines)}, davon groß: {len(providers)}")

    # ---- Ausgabe ----

    def write_zip(self, out: Path) -> str:
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
            for name in sorted(self.files):
                data = self.files[name]
                if name.endswith((".json", ".mcmeta")):
                    data = json.dumps(load_json(data, name), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                info = zipfile.ZipInfo(name, ZIP_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                zf.writestr(info, data)
        content = buffer.getvalue()
        sha1 = hashlib.sha1(content).hexdigest()
        out.mkdir(parents=True, exist_ok=True)
        (out / self.zip_name).write_bytes(content)
        (out / (self.zip_name + ".sha1")).write_text(sha1 + "\n", encoding="ascii")
        return sha1

    def run(self, out: Path) -> int:
        self.collect()
        for required in ("pack.mcmeta",):
            if required not in self.files:
                self.errors.append(f"{required} fehlt")
        self.check_json()
        if not self.errors:
            self.check_models()
            self.check_item_definitions()
            self.check_sounds()
            self.check_fonts()
        for warning in self.warnings:
            print("WARNUNG: " + warning)
        if self.errors:
            for error in sorted(set(self.errors)):
                print("FEHLER: " + error, file=sys.stderr)
            print(f"Resourcepack ungueltig: {len(set(self.errors))} Fehler", file=sys.stderr)
            return 1
        self.add_atlases()
        self.write_item_icons(out)
        sha1 = self.write_zip(out)
        size = (out / self.zip_name).stat().st_size
        print(f"Resourcepack OK: {len(self.files)} Dateien, {size / 1024 / 1024:.2f} MiB")
        print(f"SHA1: {sha1}")
        return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ROOT / "target" / "generated-resources" / "pack"))
    parser.add_argument("--cache", default=str(Path.home() / ".cache" / "kaleidoscope-pack"))
    parser.add_argument("--root", default=str(ROOT), help="Projekt mit resourcepack/imported und resourcepack/custom")
    parser.add_argument("--base", help="Basis-Projekt, dessen Pack der Client zusätzlich hat (nur für die Prüfung)")
    parser.add_argument("--zip-name", default="KaleidoscopeCookery-Pack.zip")
    parser.add_argument("--icon-font", default="kaleidoscope:icons", help="Schrift für die großen Item-Bilder im Kochbuch")
    args = parser.parse_args()
    vanilla = Vanilla(Path(args.cache).expanduser())
    builder = PackBuilder(vanilla, Path(args.root).resolve(), Path(args.base).resolve() if args.base else None, args.zip_name, args.icon_font)
    sys.exit(builder.run(Path(args.out)))


if __name__ == "__main__":
    main()
