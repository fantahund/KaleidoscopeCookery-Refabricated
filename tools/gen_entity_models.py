#!/usr/bin/env python3
"""
Baut Java-Entity-Modelle des Mods (createBodyLayer) als Item-Modelle nach:
- Mühlstein (MillstoneModel): Sockel (fest), Rad mit Deichsel (dreht sich um die Blockmitte)
  und Walze (rollt zusätzlich um ihre eigene Achse)
- Mülleimer (TrashCanModel): ein Modell in Ruhestellung

Koordinaten: Die Mod-Renderer legen die Modelle mit translate(0.5, 1.5, 0.5) und ZN(180) in den Block
(Ausrichtung Norden). Ein Modellpunkt (x, y, z) in Pixeln liegt damit bei (8 - x, 24 - y, 8 + z)
relativ zur Blockecke. Die Mühlstein-Teile sind 3 Blöcke groß, Blockmodelle erlauben aber nur -16..32 –
deshalb werden sie um den Ankerpunkt auf 1/4 verkleinert und im Display mit Faktor 4 skaliert.

Ausgabe nach resourcepack/custom/assets/kaleidoscope_cookery/{models,items}/block/*.json
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope_cookery"


def cube(tex, box, size, grow=0.0):
    return {"tex": tex, "box": box, "size": size, "grow": grow}


# Teile wie in den Mod-Modellen: (Name, Pivot relativ zum Elternteil, Rotation in Grad (x, y, z), Würfel, Kinder)
MILLSTONE = [
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

TRASH_CAN = [
    ("root", (0, 24, 0), (0, 0, 0), [], [
        ("bone", (0, -6, 0), (0, 0, 0), [cube((16, 40), (-6, -6, -6), (12, 12, 12))], []),
        ("bone2", (0, -14.75, 0), (0, 0, 0), [cube((8, 0), (-7, -0.25, -7), (14, 3, 14))], [
            ("cube_r1", (0, -1.75, 0), (0, -90, 0), [
                cube((6, 26), (-1, -0.5, -3), (2, 2, 0)),
                cube((0, 20), (-1, -0.5, -3), (2, 0, 6)),
                cube((6, 26), (-1, -0.5, 3), (2, 2, 0)),
            ], []),
        ]),
        ("eye", (0, -8.5, 0), (0, 0, 0), [cube((24, 22), (-5, -1.5, -5), (10, 3, 10))], [
            ("bone3", (0, 1.5, -0.1), (0, 0, 0), [cube((34, 36), (-5, -3, -5), (10, 3, 0))], []),
        ]),
    ]),
]


def to_world(point):
    x, y, z = point
    return (8 - x, 24 - y, 8 + z)


def uv(u0, v0, u1, v1, texture_size):
    factor = 16 / texture_size
    return [round(u0 * factor, 4), round(v0 * factor, 4), round(u1 * factor, 4), round(v1 * factor, 4)]


def faces(u, v, w, h, d, texture_size):
    """
    Box-UV von ModelPart.Cube auf Blockmodell-Flächen. Durch ZN(180) liegt die Entity-Westseite (minX)
    im Osten und die Entity-Unterseite (minY) oben.
    """
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
    f10, f11, f12 = v, v + d, v + d + h
    tex = "#texture"
    result = {
        "north": {"uv": uv(f5, f11, f6, f12, texture_size), "texture": tex},
        "south": {"uv": uv(f8, f11, f9, f12, texture_size), "texture": tex},
        "east": {"uv": uv(f4, f11, f5, f12, texture_size), "texture": tex},
        "west": {"uv": uv(f6, f11, f8, f12, texture_size), "texture": tex},
        "up": {"uv": uv(f6, f11, f5, f10, texture_size), "texture": tex},
        "down": {"uv": uv(f7, f10, f6, f11, texture_size), "texture": tex},
    }
    # Flache Würfel (Breite, Höhe oder Tiefe 0): nur die beiden großen Flächen
    if d == 0:
        result = {k: result[k] for k in ("north", "south")}
    elif h == 0:
        result = {k: result[k] for k in ("up", "down")}
    elif w == 0:
        result = {k: result[k] for k in ("east", "west")}
    return result


def collect(parts, offset, path, out):
    """Würfel mit absolutem Pivot sammeln. Elternteile sind in Ruhe unverdreht, nur Blätter haben Rotation."""
    for name, pivot, rotation, cubes, children in parts:
        absolute = tuple(o + p for o, p in zip(offset, pivot))
        for c in cubes:
            out.append((path + [name], absolute, rotation, c))
        collect(children, absolute, path + [name], out)


def element(pivot, rotation, c, anchor, scale, texture_size):
    def scaled(point):
        return [round(8 + (a - b) / scale, 4) for a, b in zip(point, anchor)]

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
        "faces": faces(c["tex"][0], c["tex"][1], w, h, d, texture_size),
    }
    rx, ry, rz = rotation
    if rx or ry or rz:
        # Rz(180)-Konjugation: x- und y-Drehungen kehren ihr Vorzeichen um
        result["rotation"] = {"origin": scaled(to_world(pivot)), "x": -rx, "y": -ry, "z": rz}
    return result


def write_model(name, elements, texture):
    for e in elements:
        for value in e["from"] + e["to"]:
            if not -16 <= value <= 32:
                raise SystemExit(f"{name}: Koordinate {value} außerhalb -16..32")
    model = {"textures": {"texture": texture, "particle": texture}, "elements": elements}
    (OUT / "models" / "block").mkdir(parents=True, exist_ok=True)
    (OUT / "items" / "block").mkdir(parents=True, exist_ok=True)
    (OUT / "models" / "block" / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": f"kaleidoscope_cookery:block/{name}"}}
    (OUT / "items" / "block" / f"{name}.json").write_text(json.dumps(item, indent=2) + "\n", encoding="utf-8")


# Anker in Welt-Pixeln (Norden): Blockmitte für Sockel und Rad, Walzenmitte für die Walze
BLOCK_CENTER = (8, 8, 8)
ROLL_CENTER = to_world((0, 3, -8))


def main():
    # Mühlstein: drei Teile, auf 1/4 verkleinert
    texture = "kaleidoscope_cookery:block/millstone"
    cubes = []
    collect(MILLSTONE, (0, 0, 0), [], cubes)
    groups = {"millstone_base": [], "millstone_wheel": [], "millstone_roll": []}
    for path, pivot, rotation, c in cubes:
        if path[0] == "base":
            groups["millstone_base"].append(element(pivot, rotation, c, BLOCK_CENTER, 4, 128))
        elif "roll" in path:
            groups["millstone_roll"].append(element(pivot, rotation, c, ROLL_CENTER, 4, 128))
        else:
            groups["millstone_wheel"].append(element(pivot, rotation, c, BLOCK_CENTER, 4, 128))
    for name, elements in groups.items():
        write_model(name, elements, texture)
        print(f"{name}: {len(elements)} Elemente")

    # Mülleimer: ein Modell in Originalgröße
    cubes = []
    collect(TRASH_CAN, (0, 0, 0), [], cubes)
    elements = [element(pivot, rotation, c, BLOCK_CENTER, 1, 64) for path, pivot, rotation, c in cubes]
    write_model("trash_can_model", elements, "kaleidoscope_cookery:block/trash_can")
    print(f"trash_can_model: {len(elements)} Elemente")


if __name__ == "__main__":
    main()
