# KaleidoscopeCookery

Paper-Plugin (26.3) mit Server-Resourcepack: Kochstationen, Gerichte und Pflanzen nach dem Mod
[Kaleidoscope Cookery](https://github.com/KaleidoscopeMods/KaleidoscopeCookery) (Branch `1.21.1-neoforge`). Spieler brauchen
keine Mods, nur das Resourcepack, das das Plugin selbst ausliefert.

[Kaleidoscope Grilling](https://github.com/breezeth-CN/KaleidoscopeGrilling) ist ein eigenes Plugin (Repo `KaleidoscopeGrilling`
neben diesem) mit eigenem Pack. Es meldet sich über `KaleidoscopeCookery#registerAddon` an: Items, Rezepte, Pack, Übersetzungen,
Kochbuch-Bilder und Kreativ-Menü-Tabs kommen dazu, die Packs werden zusammen verschickt.

Stationen bestehen aus einer Interaction-Entity (Klicks, Zustand im PDC), ItemDisplays (Modelle) und bei vollen Blöcken
einer Barriere. Alle Items basieren auf Papier (Werkzeuge wie das Küchenmesser auf dem passenden Schwert) und bekommen
ihr Aussehen über die `item_model`-Komponente.

## Stand

Phase 1: Projekt, Resourcepack-Pipeline, Station-Framework, Schneidebrett, Öl-Drops, Werkbank-/Ofen-Rezepte der Mods.

## Installation

1. **CubesideUtils** muss installiert sein, **CubesideNMSUtils** ist optional.
2. `mvn package` (braucht `python3`, lädt beim ersten Mal den 26.3-Client für die Pack-Prüfung) und
   `core/target/KaleidoscopeCookery-*.jar` nach `plugins/` kopieren.
3. Server starten. Das Plugin legt `config.yml`, `recipes.yml` und das Pack-Zip an und startet einen kleinen Webserver
   (Standard: Port 8163), über den die Spieler das Pack laden. Für einen öffentlichen Server `resource-pack.public-host`
   auf die erreichbare Adresse setzen oder `resource-pack.external-url` nutzen.

## Bedienung

| Aktion | Wirkung |
|---|---|
| Rechtsklick mit Stations-Item auf einen Block | Station platzieren (zeigt zum Spieler) |
| Schleichen + Linksklick auf eine Station | Station abbauen, Inhalt und Item fallen heraus |
| Schneidebrett: Rechtsklick mit Zutat | Zutat auflegen |
| Schneidebrett: Linksklick mit Küchenmesser | Schneiden (1 Schnitt pro Hieb wie im Mod, siehe `cutting.tools`) |
| Schneidebrett: Klick, wenn fertig | Ergebnisse fallen heraus |
| Schneidebrett: Schleichen + Rechtsklick vor dem ersten Schnitt | Zutat zurücknehmen |
| Schwein, Hoglin, Piglin, Zoglin mit Küchenmesser töten | Öl |

## Befehle und Rechte

Hauptbefehl: `/kaleidoscope`, Aliase `/kc`, `/kochen`

| Befehl | Recht |
|---|---|
| `/kc give <item> [spieler] [anzahl]` | `kaleidoscope.admin.give` |
| `/kc menu` (Kreativ-Menü: Klick 1 Stück, Shift-Klick ein Stapel) | `kaleidoscope.admin.give` |
| `/kc place <typ> <x> <y> <z> [welt] [ausrichtung]` | `kaleidoscope.admin.place` |
| `/kc info`, `/kc list` | `kaleidoscope.admin.info` |
| `/kc repair` | `kaleidoscope.admin.repair` |
| `/kc reload` | `kaleidoscope.admin.reload` |
| `/kc pack` | `kaleidoscope.use` |
| Stationen platzieren und benutzen | `kaleidoscope.use` (Standard: alle) |

## Entwicklung

| Datei | Zweck |
|---|---|
| `tools/import_assets.py` | Assets aus den Mod-Repos (feste Commits) nach `resourcepack/imported` holen und auf 26.3 konvertieren |
| `tools/gen_data.py` | `items.yml`, `recipes.yml` und die deutschen Namen aus den Mod-Quellen erzeugen |
| `tools/build_pack.py` | Pack zusammenbauen, alle Referenzen prüfen, zippen, SHA1 (läuft bei `mvn package`) |
| `tools/lang/` | Deutsche Übersetzungen der Mod-Texte |
| `resourcepack/custom/` | Eigene Pack-Dateien (überschreiben `imported`) |

## Lizenzen

Die Assets im Resourcepack (`resourcepack/`) stammen aus Kaleidoscope Cookery (© 2025 Kaleidoscope Official Production Team)
und Kaleidoscope Grilling (© 2026 breezeth), lizenziert unter **CC BY-NC-SA 4.0**, und wurden für 26.3 verändert
(siehe `resourcepack/custom/CREDITS.txt`). Das Pack ist nicht-kommerziell und wird unter derselben Lizenz weitergegeben.
Spiel-Mechaniken (z. B. die Qualitätsbewertung) sind nach dem Quellcode der Mods (BSD 3-Clause, siehe `licenses/`)
nachgebaut. Die Urheber der Mods unterstützen oder empfehlen dieses Projekt nicht.
