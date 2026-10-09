#!/usr/bin/env python3
"""
Baut das Mühlstein-Modell aus dem Java-Entity-Modell des Mods (MillstoneModel#createBodyLayer)
als drei Item-Modelle nach: Sockel (fest), Rad mit Deichsel (dreht sich um die Blockmitte)
und Walze (rollt zusätzlich um ihre eigene Achse).

Koordinaten: Das Mod-Renderer legt das Modell mit translate(0.5, 1.5, 0.5) und ZN(180) in den Block
(Ausrichtung Norden). Ein Modellpunkt (x, y, z) in Pixeln liegt damit bei (8 - x, 24 - y, 8 + z)
relativ zur Blockecke. Die Teile sind 3 Blöcke groß, Blockmodelle erlauben aber nur -16..32 –
deshalb werden sie um den Ankerpunkt auf 1/4 verkleinert und im Display mit Faktor 4 skaliert.

Ausgabe nach resourcepack/custom/assets/kaleidoscope_cookery/{models,items}/block/millstone_*.json
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope_cookery"
SCALE = 4
TEXTURE_SIZE = 128
TEXTURE = "kaleidoscope_cookery:block/millstone"


def cube(tex, box, size, grow=0.0):
    return {"tex": tex, "box": box, "size": size, "grow": grow}


# Teile wie in MillstoneModel: (Name, Pivot relativ zum Elternteil, Rotation in Grad (x, y, z), Würfel, Kinder)
MODEL = [
    ("base", (8, 24, -8), (0, 0, 0), [
        cube((6, 8), (-13, -15, -10), (2, 3, 5)),
        cube((5, 8), (-5, -15, -10), (2, 3, 5)),
        cube((5, 21), (-13, -12, -10), (10, 2, 2)),
        cube((0, 87), (-24, -14, -8), (32, 8, 32)),
        cube((0, 31), (-21, -6, -5), (26, 6, 26)),
        cube((22, 64), (-18, -15, -2), (20, 1, 20)),
    ], []),
    ("wheel", (0, 3, 0), (0, 0, 0), [], [
        ("roll", (0, 0, -8), (0, 0, 0), [cube((76, 0), (-6, -6, -7), (12, 12, 14))], []),
        ("stick", (0, 0, 0), (0, 0, 0), [], [
            ("cube_r1", (0, -0.5, 1), (-90, 0, 0), [cube((83, 31), (-8, -2, -2), (16, 4, 5))], []),
            ("cube_r2", (0, 3, -36.5), (-90, 0, 0), [cube((114, 72), (-2, -21.5, -1), (4, 6, 2), 0.1)], []),
            ("cube_r3", (0, 0, -17), (-90, 0, 0), [cube((111, 84), (-2, 0, -2), (4, 29, 4))], []),
            ("cube_r4", (0, -1, -17), (-90, 0, 0), [cube((86, 32), (-7, -2, -2), (14, 3, 5))], []),
            ("rotStick", (0, 4, 0), (0, 0, 0), [], [
                ("cube_r5", (0, 3.5, 0), (0, -45, 0), [cube((111, 84), (-2, -17.5, -2), (4, 30, 4))], []),
            ]),
        ]),
    ]),
]


def to_world(point):
    x, y, z = point
    return (8 - x, 24 - y, 8 + z)


def uv(u0, v0, u1, v1):
    factor = 16 / TEXTURE_SIZE
    return [round(u0 * factor, 4), round(v0 * factor, 4), round(u1 * factor, 4), round(v1 * factor, 4)]


def faces(u, v, w, h, d):
    """
    Box-UV von ModelPart.Cube auf Blockmodell-Flächen. Durch ZN(180) liegt die Entity-Westseite (minX)
    im Osten und die Entity-Unterseite (minY) oben.
    """
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
    f10, f11, f12 = v, v + d, v + d + h
    tex = "#texture"
    return {
        "north": {"uv": uv(f5, f11, f6, f12), "texture": tex},
        "south": {"uv": uv(f8, f11, f9, f12), "texture": tex},
        "east": {"uv": uv(f4, f11, f5, f12), "texture": tex},
        "west": {"uv": uv(f6, f11, f8, f12), "texture": tex},
        "up": {"uv": uv(f6, f11, f5, f10), "texture": tex},
        "down": {"uv": uv(f7, f10, f6, f11), "texture": tex},
    }


def collect(parts, offset, path, out):
    """Würfel mit absolutem Pivot sammeln. Elternteile sind in Ruhe unverdreht, nur Blätter haben Rotation."""
    for name, pivot, rotation, cubes, children in parts:
        absolute = tuple(o + p for o, p in zip(offset, pivot))
        for c in cubes:
            out.append((path + [name], absolute, rotation, c))
        collect(children, absolute, path + [name], out)


def element(pivot, rotation, c, anchor):
    def scaled(point):
        return [round(8 + (a - b) / SCALE, 4) for a, b in zip(point, anchor)]

    bx, by, bz = c["box"]
    w, h, d = c["size"]
    g = c["grow"]
    px, py, pz = pivot
    lo = (px + bx - g, py + by - g, pz + bz - g)
    hi = (px + bx + w + g, py + by + h + g, pz + bz + d + g)
    a, b = to_world(lo), to_world(hi)
    result = {
        "from": scaled([min(a[i], b[i]) for i in range(3)]),
        "to": scaled([max(a[i], b[i]) for i in range(3)]),
        "faces": faces(c["tex"][0], c["tex"][1], w, h, d),
    }
    rx, ry, rz = rotation
    if rx or ry or rz:
        # Rz(180)-Konjugation: x- und y-Drehungen kehren ihr Vorzeichen um
        result["rotation"] = {"origin": scaled(to_world(pivot)), "x": -rx, "y": -ry, "z": rz}
    return result


def write_model(name, elements):
    model = {"textures": {"texture": TEXTURE, "particle": TEXTURE}, "elements": elements}
    (OUT / "models" / "block").mkdir(parents=True, exist_ok=True)
    (OUT / "items" / "block").mkdir(parents=True, exist_ok=True)
    (OUT / "models" / "block" / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": f"kaleidoscope_cookery:block/{name}"}}
    (OUT / "items" / "block" / f"{name}.json").write_text(json.dumps(item, indent=2) + "\n", encoding="utf-8")


# Anker in Welt-Pixeln (Norden): Blockmitte für Sockel und Rad, Walzenmitte für die Walze
BLOCK_CENTER = (8, 8, 8)
ROLL_CENTER = to_world((0, 3, -8))


def main():
    cubes = []
    collect(MODEL, (0, 0, 0), [], cubes)
    groups = {"millstone_base": [], "millstone_wheel": [], "millstone_roll": []}
    for path, pivot, rotation, c in cubes:
        if path[0] == "base":
            groups["millstone_base"].append(element(pivot, rotation, c, BLOCK_CENTER))
        elif "roll" in path:
            groups["millstone_roll"].append(element(pivot, rotation, c, ROLL_CENTER))
        else:
            groups["millstone_wheel"].append(element(pivot, rotation, c, BLOCK_CENTER))
    for name, elements in groups.items():
        for e in elements:
            for value in e["from"] + e["to"]:
                if not -16 <= value <= 32:
                    raise SystemExit(f"{name}: Koordinate {value} außerhalb -16..32")
        write_model(name, elements)
        print(f"{name}: {len(elements)} Elemente")
    print(f"Walzenmitte (Welt-Pixel, Norden): {ROLL_CENTER}")


if __name__ == "__main__":
    main()
