#!/usr/bin/env python3
"""
Rebuilds the mod's Java entity models (createBodyLayer) as item models:
- Millstone (MillstoneModel): base (fixed), wheel with shaft (rotates around the block centre)
  and roller (additionally rolls around its own axis)
- Trash can (TrashCanModel): body, lid, eye and pupils separately for the animations
- Scarecrow (ScarecrowModel, entity 2.375 blocks tall): body and head (hidden when a head is put on),
  scaled down to 1/2; texture copied to block/scarecrow_entity (entity textures are not in any item atlas)
- Cold cut ham slices (ColdCutHamSlicesModel, 3x3 blocks): one model per bite stage 0-8 from the mod's Blockbench item model
  (cold_cut_ham_slices_block), bite k disappears from bites >= 9 - k like in updateBites

Coordinates: the mod's renderers place the models in the block with translate(0.5, 1.5, 0.5) and ZN(180)
(facing north). A model point (x, y, z) in pixels is therefore at (8 - x, 24 - y, 8 + z)
relative to the block corner. The millstone parts are 3 blocks large, but block models only allow -16..32 -
so they are scaled down to 1/4 around the anchor point and scaled by a factor of 4 in the display.

Output to resourcepack/custom/assets/kaleidoscope_cookery/{models,items}/block/*.json
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope_cookery"


def cube(tex, box, size, grow=0.0):
    return {"tex": tex, "box": box, "size": size, "grow": grow}


# Parts as in the mod models: (name, pivot relative to the parent, rotation in degrees (x, y, z), cubes, children)
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

# ScarecrowModel#createBodyLayer (radians converted to degrees, mirror() does not affect the shape)
SCARECROW = [
    ("group", (0, 23.0278, 0.0278), (0, 0, 0), [
        cube((85, 82), (-4, -26.0278, -2.0278), (8, 10, 4)),
        cube((54, 69), (-4, -17.0278, -2.0278), (8, 0, 4)),
        cube((73, 60), (8, -26.0278, -2.0278), (0, 4, 4)),
        cube((73, 60), (-8, -26.0278, -2.0278), (0, 4, 4)),
        cube((10, 107), (-14, -25.7778, -1.0278), (28, 2, 2)),
        cube((101, 65), (4, -26.0278, -2.0278), (5, 4, 4)),
        cube((82, 65), (-9, -26.0278, -2.0278), (5, 4, 4)),
        cube((28, 70), (-1, -18.0278, -1.0278), (2, 19, 2)),
    ], [
        ("head", (0, -2.8, 0.1), (0, 0, 0), [cube((13, 3), (-4.5, -32.2278, -4.3778), (9, 9, 9))], [
            ("hat", (0.1198, -30.4846, 0.5972), (0, 0, 0), [], [
                ("headwear_r1", (0, -0.25, -0.5), (0, 0, 12.5023), [
                    cube((66, 8), (-5, -3.775, -5.025), (10, 5, 10)),
                    cube((77, 25), (-8, 1.275, -7.975), (16, 0, 16)),
                ], []),
            ]),
        ]),
        ("rightArm", (-12, -24.8, -0.05), (0, 0, 0), [cube((0, 0), (-1, -1, -1), (2, 2, 2))], []),
        ("leftArm", (12, -24.8, -0.05), (0, 0, 0), [cube((0, 0), (-1, -1, -1), (2, 2, 2))], []),
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
    Box UV of ModelPart.Cube onto block model faces. Because of ZN(180) the entity's west side (minX)
    is in the east and the entity's bottom (minY) at the top.
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
    # Flat cubes (width, height or depth 0): only the two large faces
    if d == 0:
        result = {k: result[k] for k in ("north", "south")}
    elif h == 0:
        result = {k: result[k] for k in ("up", "down")}
    elif w == 0:
        result = {k: result[k] for k in ("east", "west")}
    return result


def collect(parts, offset, path, out):
    """Collect cubes with an absolute pivot. Parents are unrotated at rest, only leaves have a rotation."""
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
        # Rz(180) conjugation: x and y rotations flip their sign
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


# Anchor in world pixels (north): block centre for base and wheel, roller centre for the roller
BLOCK_CENTER = (8, 8, 8)
ROLL_CENTER = to_world((0, 3, -8))


def main():
    # Millstone: three parts, scaled down to 1/4
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

    # Trash can: four animated parts in original size, each around its pivot (TrashCanAnimation)
    cubes = []
    collect(TRASH_CAN, (0, 0, 0), [], cubes)
    pivots = {"bone": (0, 18, 0), "bone2": (0, 9.25, 0), "eye": (0, 15.5, 0), "bone3": (0, 17, -0.1)}
    parts = {"bone": "trash_can_body", "bone2": "trash_can_lid", "eye": "trash_can_eye", "bone3": "trash_can_pupils"}
    groups = {name: [] for name in parts.values()}
    for path, pivot, rotation, c in cubes:
        part = next(p for p in reversed(path) if p in parts)
        groups[parts[part]].append(element(pivot, rotation, c, to_world(pivots[part]), 1, 64))
    for name, elements in groups.items():
        write_model(name, elements, "kaleidoscope_cookery:block/trash_can")
        print(f"{name}: {len(elements)} Elemente")
    for part, pivot in pivots.items():
        print(f"Drehpunkt {part} (Welt-Pixel, Norden): {to_world(pivot)}")

    # Scarecrow: scaled down to 1/2 around the block centre (factor 2 in the display)
    texture_file = ROOT / "resourcepack" / "imported" / "assets" / "kaleidoscope_cookery" / "textures" / "entity" / "scarecrow.png"
    (OUT / "textures" / "block").mkdir(parents=True, exist_ok=True)
    (OUT / "textures" / "block" / "scarecrow_entity.png").write_bytes(texture_file.read_bytes())
    cubes = []
    collect(SCARECROW, (0, 0, 0), [], cubes)
    groups = {"scarecrow_body": [], "scarecrow_head": []}
    for path, pivot, rotation, c in cubes:
        groups["scarecrow_head" if "head" in path else "scarecrow_body"].append(element(pivot, rotation, c, BLOCK_CENTER, 2, 128))
    for name, elements in groups.items():
        write_model(name, elements, "kaleidoscope_cookery:block/scarecrow_entity")
        print(f"{name}: {len(elements)} Elemente")
    for arm in ("rightArm", "leftArm"):
        print(f"Arm {arm} (Welt-Pixel, Norden): {to_world((12 if arm == 'leftArm' else -12, 23.0278 - 24.8, 0.0278 - 0.05))}")

    ham_stages()


def ham_stages():
    source = ROOT / "resourcepack" / "imported" / "assets" / "kaleidoscope_cookery" / "models" / "item" / "cold_cut_ham_slices_block.json"
    model = json.loads(source.read_text(encoding="utf-8"))
    for bites in range(9):
        hidden = {f"bite{k}" for k in range(1, 9) if bites >= 9 - k}
        stage = {"texture_size": model["texture_size"], "textures": model["textures"],
                 "elements": [e for e in model["elements"] if e.get("name") not in hidden]}
        name = f"food/cold_cut_ham_slices/cold_cut_ham_slices_{bites}"
        (OUT / "models" / "block" / name).parent.mkdir(parents=True, exist_ok=True)
        (OUT / "models" / "block" / f"{name}.json").write_text(json.dumps(stage, indent=2) + "\n", encoding="utf-8")
        (OUT / "items" / "block" / name).parent.mkdir(parents=True, exist_ok=True)
        (OUT / "items" / "block" / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"kaleidoscope_cookery:block/{name}"}}, indent=2) + "\n", encoding="utf-8")
    print("Aufschnitt-Schinken: 9 Bissstufen")


if __name__ == "__main__":
    main()
