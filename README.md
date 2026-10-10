# KaleidoscopeCookery

Paper plugin (26.3) with a server resource pack: cooking stations, dishes and crops based on the mod
[Kaleidoscope Cookery](https://github.com/KaleidoscopeMods/KaleidoscopeCookery), following its Fabric port
[KaleidoscopeCookery-Refabricated](https://github.com/NightEpiphany/KaleidoscopeCookery-Refabricated). Players need
no mods, only the resource pack, which the plugin delivers itself.

[Kaleidoscope Grilling](https://github.com/breezeth-CN/KaleidoscopeGrilling) is a separate plugin (repo `KaleidoscopeGrilling`
next to this one) with its own pack. It registers itself via `KaleidoscopeCookery#registerAddon`: its items, recipes, pack,
translations, cookbook images and creative menu tabs are added, and the packs are sent together.

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

1. **CubesideUtils** must be installed, **CubesideNMSUtils** is optional.
2. Run `mvn package` (needs `python3`; downloads the 26.3 client once for checking the pack) and copy
   `core/target/KaleidoscopeCookery-*.jar` into `plugins/`.
3. Start the server. The plugin creates `config.yml`, `recipes.yml` and the pack zip and starts a small web server
   (default port 8163) from which players download the pack. For a public server set `resource-pack.public-host`
   to the reachable address, or upload the pack and use `resource-pack.external-url`.

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
| `/kc recipes [search]`, `/kc recipe` (also `rezepte`/`rezept`), `/kc pack` | `kaleidoscope.use` |
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
