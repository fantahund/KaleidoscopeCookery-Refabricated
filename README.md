# KaleidoscopeCookery

Paper plugin (26.3) with a server resource pack: cooking stations, dishes and crops based on the mod
[Kaleidoscope Cookery](https://github.com/KaleidoscopeMods/KaleidoscopeCookery), following its Fabric port
[KaleidoscopeCookery-Refabricated](https://github.com/NightEpiphany/KaleidoscopeCookery-Refabricated). Players need
no mods, only the resource packs, which the KaleidoscopePack plugin sends.

Cookery is the station engine of the Kaleidoscope plugins (all next to each other in one folder):

| Plugin | Role |
|---|---|
| `KaleidoscopeCore` | Addon registry and shared registries (cooking oils) |
| `KaleidoscopeCookery` | This plugin: stations, items, recipes, cookbook, pack build tools (`tools/`) |
| `KaleidoscopeGrilling`, `KaleidoscopeSmithing` | Addons with their own pack, registered via `KaleidoscopeCore#registerAddon` |
| `KaleidoscopePack` | Sends all packs (Paper plugin on a single server, Velocity plugin on a network) |

Addons register via `KaleidoscopeCore#registerAddon`: their items, recipes, translations, cookbook images and creative
menu tabs are added. Smithing registers with `includeInCookbook = false` and has its own guidebook.

Stations consist of an Interaction entity (clicks, state in the PDC), ItemDisplays (models) and, for some full blocks,
a barrier. All items are based on paper (tools such as the kitchen knife on a hidden vanilla item with the matching
components) and get their look from the `item_model` component.

## Features

- Stations: stove, wok, stockpot, steamer, teapot and cups, chopping board, millstone, bamboo tray, shawarma spit,
  oil pot, enamel basin, kitchenware rack, fruit basket, trash can, recipe notes, plated dishes (1x1, 1x2, 3x3)
- Crops (tomato, chili, lettuce, rice, tea bush) with random-tick growth like vanilla, straw hat seed drops, sickle
- Dish quality, the mod's food effects, a configurable nerf for vanilla food
- Cookbook as a dialog with search, recipe images and guides (`/kc recipes`, cookbook item)
- Scarecrow, clay pot milk tea, oil and oil block as furnace fuel, optional dispenser support
- Recipes can be disabled without deleting them (`disabled-recipes` in `config.yml`)
- Addon API for further plugins (items, recipes, crops, effects, cookbook pages, oil types)

## Installation

1. **CubesideUtils** and **KaleidoscopeCore** must be installed, **CubesideNMSUtils** is optional.
2. Build order: `mvn install` in `KaleidoscopeCore`, then `mvn install` here (needs `python3`; downloads the 26.3 client
   once for checking the pack), then the addons. Copy `core/target/KaleidoscopeCookery-*.jar` into `plugins/`.
3. Upload `core/target/generated-resources/pack/KaleidoscopeCookery-Pack.zip` (and the addon packs) to a web server
   and list the URLs in the `KaleidoscopePack` config.

## Usage

| Action | Effect |
|---|---|
| Right-click a block with a station item | Place the station (it faces the player) |
| Sneak + left-click a station | Break the station, contents and item drop |
| Right-click a chest, furnace, door ... while holding a station item or seeds | Uses the block; sneak to place instead |
| Chopping board: right-click with an ingredient | Put the ingredient on the board |
| Chopping board: left-click with a kitchen knife | Cut (1 cut per hit as in the mod, see `cutting.tools`) |
| Chopping board: click when done | The results drop |
| Chopping board: sneak + right-click before the first cut | Take the ingredient back |
| Kill a pig, hoglin, piglin or zoglin with a kitchen knife | Oil |

## Commands and permissions

Main command: `/kaleidoscope`, aliases `/kc`, `/kochen`

| Command | Permission |
|---|---|
| `/kc give <item> [player] [amount]` | `kaleidoscope.admin.give` |
| `/kc menu` (creative menu: click gives 1, shift-click a stack) | `kaleidoscope.admin.give` |
| `/kc place <type> <x> <y> <z> [world] [facing]` | `kaleidoscope.admin.place` |
| `/kc info`, `/kc list` | `kaleidoscope.admin.info` |
| `/kc repair` | `kaleidoscope.admin.repair` |
| `/kc reload` | `kaleidoscope.admin.reload` |
| `/kc recipes [search]`, `/kc recipe` (also `rezepte`/`rezept`) | `kaleidoscope.use` |
| Placing and using stations | `kaleidoscope.use` (default: everyone) |

`kaleidoscope.admin` includes all admin permissions.

## Development

| File | Purpose |
|---|---|
| `tools/import_assets.py` | Fetch the assets from the mod repos (fixed commits) into `resourcepack/imported` and convert them to 26.3 |
| `tools/gen_data.py` | Generate `items.yml`, `recipes.yml` and the German names from the mod sources |
| `tools/gen_entity_models.py` | Rebuild the mod's Java entity models (millstone, trash can, scarecrow, cold cut ham) as item models |
| `tools/gen_creative_tabs.py` | Generate the creative menu tabs from the mod's creative tabs |
| `tools/build_pack.py` | Assemble the pack, check all references, render cookbook images, zip, SHA1 (runs during `mvn package`) |
| `tools/lang/` | German translations of the mod texts |
| `resourcepack/custom/` | Own pack files (override `imported`) |

## Licenses

This project uses a dual license like the mods: the source code is licensed under the **BSD 3-Clause License**
(`LICENSE-CODE`), the resource pack under **CC BY-NC-SA 4.0** (`LICENSE-ASSETS`).

The assets in the resource pack (`resourcepack/`) come from Kaleidoscope Cookery (© 2025-2026 Kaleidoscope Official
Production Team, taken from the Fabric port KaleidoscopeCookery-Refabricated), licensed under **CC BY-NC-SA 4.0**, and were
modified for 26.3 (see `resourcepack/custom/CREDITS.txt`). Kaleidoscope Grilling (© 2026 breezeth) has its own plugin and pack. The pack is non-commercial and is distributed under the same license.
Game mechanics (e.g. the quality rating) are rebuilt following the source code of the mods (BSD 3-Clause, see `licenses/`).
The authors of the mods do not endorse or recommend this project.
