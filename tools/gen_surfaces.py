"""
Soup surface models (kaleidoscope:surface/<texture>) for every stockpot texture of the mod: the stockpot shows its
cooking and finished soups with them (StockpotStation#textureSurface). Hand-made surfaces (water, lava, milk) are kept.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "resourcepack" / "imported" / "assets" / "kaleidoscope_cookery" / "textures" / "stockpot"
OUT = ROOT / "resourcepack" / "custom" / "assets" / "kaleidoscope"


def main():
    count = 0
    for png in sorted(TEXTURES.glob("*.png")):
        name = png.stem
        model = OUT / "models" / "surface" / f"{name}.json"
        if model.exists():
            continue
        texture = f"kaleidoscope_cookery:stockpot/{name}"
        face = {"uv": [0, 0, 10, 10], "texture": "#surface"}
        model.write_text(json.dumps({
            "textures": {"surface": texture, "particle": texture},
            "elements": [{"from": [3, 0, 3], "to": [13, 0, 13], "shade": False, "faces": {"up": face}}],
        }, indent=2) + "\n", encoding="utf-8")
        (OUT / "items" / "surface" / f"{name}.json").write_text(json.dumps(
            {"model": {"type": "minecraft:model", "model": f"kaleidoscope:surface/{name}"}}, indent=2) + "\n", encoding="utf-8")
        count += 1
    print(f"{count} Suppen-Oberflächen erzeugt")


if __name__ == "__main__":
    main()
