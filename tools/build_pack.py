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
"""
import argparse
import hashlib
import io
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAYERS = [ROOT / "resourcepack" / "imported", ROOT / "resourcepack" / "custom"]
MC_VERSION = "26.3"
ZIP_NAME = "KaleidoscopeCookery-Pack.zip"
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
    def __init__(self, vanilla: Vanilla):
        self.vanilla = vanilla
        self.files: dict[str, bytes] = {}
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.texture_dirs: set[str] = set()

    def collect(self):
        for layer in LAYERS:
            if not layer.exists():
                continue
            for file in sorted(layer.rglob("*")):
                if not file.is_file() or file.name == "SOURCES.json":
                    continue
                rel = file.relative_to(layer).as_posix()
                data = file.read_bytes()
                if rel in self.files and rel.endswith(".json") and any(m in rel for m in MERGE_JSON):
                    merged = load_json(self.files[rel], rel)
                    merged.update(load_json(data, rel))
                    data = json.dumps(merged, ensure_ascii=False).encode("utf-8")
                self.files[rel] = data

    # ---- Pruefungen ----

    def exists(self, path: str) -> bool:
        return path in self.files or self.vanilla.has(path)

    def read(self, path: str):
        if path in self.files:
            return load_json(self.files[path], path)
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
                    if path not in self.files:
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
        (out / ZIP_NAME).write_bytes(content)
        (out / (ZIP_NAME + ".sha1")).write_text(sha1 + "\n", encoding="ascii")
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
        sha1 = self.write_zip(out)
        size = (out / ZIP_NAME).stat().st_size
        print(f"Resourcepack OK: {len(self.files)} Dateien, {size / 1024 / 1024:.2f} MiB")
        print(f"SHA1: {sha1}")
        return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(ROOT / "target" / "generated-resources" / "pack"))
    parser.add_argument("--cache", default=str(Path.home() / ".cache" / "kaleidoscope-pack"))
    args = parser.parse_args()
    vanilla = Vanilla(Path(args.cache).expanduser())
    sys.exit(PackBuilder(vanilla).run(Path(args.out)))


if __name__ == "__main__":
    main()
