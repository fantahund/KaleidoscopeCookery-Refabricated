#!/usr/bin/env python3
"""
Creates generated/creative_tabs.yml: categories and order of the creative menu (/kc menu) following the creative tabs
of the mods (ModCreativeTabs) - for Cookery in core/src/main/resources, for Grilling in the KaleidoscopeGrilling repo next to it.

Usage: python3 -I tools/gen_creative_tabs.py [--cache ~/.cache/kaleidoscope-pack]
The plugin filters out items it does not know itself; items without a tab end up under "Misc" there.
"""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "core" / "src" / "main" / "resources" / "generated" / "creative_tabs.yml"
GRILLING_OUT = ROOT.parent / "KaleidoscopeGrilling" / "src" / "main" / "resources" / "generated" / "creative_tabs.yml"

COOKERY_JAVA = "refab/src/main/java/com/github/ysbbbbbb/kaleidoscopecookery/init"  # Fabric-Fork
GRILLING_JAVA = "grilling/neoforge-1.21.1/src/main/java/cn/breezeth/kaleidoscope_grilling/registry"

# First furniture item in the Cookery main tab: from here on an own "Furniture" tab
FIRST_FURNITURE = "cook_stool_oak"


def accepted(block: str, ids: dict) -> list[str]:
    """Translate field names from output.accept(ModItems.X.get()) into registry ids (RICE_SEED -> rice)."""
    return [ids.get(m, m.lower()) for m in re.findall(r"output\.accept\(ModItems\.([A-Z0-9_]+)(?:\.get\(\))?\)", block)]


def field_ids(java: str) -> dict:
    return dict(re.findall(r"\b([A-Z0-9_]+)\s*=\s*(?:ITEMS\.register|registerItem|registerStrawHats)\(\s*\"([a-z0-9_]+)\"", java))


def registered(java: str) -> list[str]:
    return re.findall(r"(?:ITEMS\.register|registerItem)\(\s*\"([a-z0-9_]+)\"", java)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=str(Path.home() / ".cache" / "kaleidoscope-pack"))
    cache = Path(parser.parse_args().cache).expanduser()

    tabs_java = (cache / COOKERY_JAVA / "ModCreativeTabs.java").read_text(encoding="utf-8")
    ids = field_ids((cache / COOKERY_JAVA / "ModItems.java").read_text(encoding="utf-8"))
    main_block, food_block = tabs_java.split("CREATIVE_MODE_TAB, COOKERY_FOOD_TAB", 1)
    main_items = accepted(main_block, ids)
    split = main_items.index(FIRST_FURNITURE)
    kitchen = ["kaleidoscope:cookbook"] + ["kaleidoscope_cookery:" + i for i in main_items[:split]]
    furniture = ["kaleidoscope_cookery:" + i for i in main_items[split:]]

    # Food: fixed entries, then all remaining Cookery items in registration order
    # (the mod fills its registries for dishes, plates and cups dynamically)
    food = ["kaleidoscope_cookery:" + i for i in accepted(food_block, ids)]
    taken = set(kitchen) | set(furniture) | set(food)
    for item in registered((cache / COOKERY_JAVA / "ModItems.java").read_text(encoding="utf-8")):
        key = "kaleidoscope_cookery:" + item
        if key not in taken:
            food.append(key)
            taken.add(key)

    grilling_first = ["grill", "oil_press", "big_vat", "advanced_rack", "skewer_recipe_book", "empty_seasoning_bottle",
                      "special_seasoning", "canola_oil_bucket", "secret_chili_oil_bucket", "premium_chili_oil_bucket"]
    grilling_items = registered((cache / GRILLING_JAVA / "ModItems.java").read_text(encoding="utf-8"))
    grilling_items += registered((cache / GRILLING_JAVA / "ModBlocks.java").read_text(encoding="utf-8"))
    grilling_order = grilling_first + [i for i in grilling_items if i.startswith("raw_") and i.endswith("_skewer")]
    grilling_order += [i for i in grilling_items if i.endswith("_skewer")]
    grilling_order += grilling_items
    grilling = []
    for item in grilling_order:
        key = "kaleidoscope_grilling:" + item
        if key not in grilling:
            grilling.append(key)

    write(OUT, {
        "kitchen": ("kaleidoscope_cookery:iron_kitchen_knife", kitchen),
        "food": ("kaleidoscope_cookery:red_chili", food),
        "furniture": ("kaleidoscope_cookery:chair_oak", furniture),
    })
    write(GRILLING_OUT, {"grilling": ("kaleidoscope_grilling:grill", grilling)})


def write(out: Path, tabs: dict):
    lines = ["# Generiert von tools/gen_creative_tabs.py aus den Kreativ-Tabs der Mods - nicht von Hand bearbeiten.", "tabs:"]
    for name, (icon, items) in tabs.items():
        lines.append(f"  {name}:")
        lines.append(f"    icon: \"{icon}\"")
        lines.append("    items:")
        lines.extend(f"      - \"{i}\"" for i in items)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out.name + ": " + ", ".join(f"{n}: {len(v[1])}" for n, v in tabs.items()))


if __name__ == "__main__":
    main()
