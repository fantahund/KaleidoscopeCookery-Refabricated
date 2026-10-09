package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.util.ItemIcons;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Seiten des Kochbuchs: eine je Station.
 *
 * @param nameKey Übersetzungs-Schlüssel des Namens
 * @param bigImage Schriftzeichen des großen Bildes (Schrift kaleidoscope:cookbook, tools/gen_cookbook_icons.py), 0 = keins
 * @param guide   Anleitungs-Texte aus den Ponder-Szenen des Mods: Szene -> Anzahl Zeilen
 */
public enum Category {
    POT("block.kaleidoscope_cookery.pot", 0xE200, Map.of("pot", 6)),
    STOCKPOT("block.kaleidoscope_cookery.stockpot", 0xE201, Map.of("stockpot", 7)),
    STEAMER("block.kaleidoscope_cookery.steamer", 0xE202, Map.of("steamer", 5, "steamer_1", 6)),
    CHOPPING_BOARD("block.kaleidoscope_cookery.chopping_board", 0xE203, Map.of()),
    TEAPOT("block.kaleidoscope_cookery.teapot", 0xE204, Map.of()),
    MILLSTONE("block.kaleidoscope_cookery.millstone", 0xE205, Map.of("millstone", 5)),
    BAMBOO_TRAY("block.kaleidoscope_cookery.bamboo_tray", 0xE206, Map.of()),
    SHAWARMA_SPIT("block.kaleidoscope_cookery.shawarma_spit", 0, Map.of("shawarma_spit", 8)),
    CRAFTING("block.minecraft.crafting_table", 0, Map.of()),
    FURNACE("block.minecraft.furnace", 0, Map.of());

    private static final Key FONT = Key.key("kaleidoscope", "cookbook");

    private final String nameKey;
    private final int bigImage;
    private final Map<String, Integer> guide;

    Category(String nameKey, int bigImage, Map<String, Integer> guide) {
        this.nameKey = nameKey;
        this.bigImage = bigImage;
        this.guide = guide;
    }

    public Component title() {
        return Lang.raw(nameKey);
    }

    /**
     * Kleines Bild für Knöpfe.
     */
    public Component icon() {
        return switch (this) {
            case SHAWARMA_SPIT -> ItemIcons.item(Key.key("kaleidoscope_cookery", "item/shawarma_spit"));
            case CRAFTING -> ItemIcons.block(Key.key("minecraft", "block/crafting_table_front"));
            case FURNACE -> ItemIcons.block(Key.key("minecraft", "block/furnace_front"));
            default -> ItemIcons.gui(Key.key("kaleidoscope", "cookbook/" + name().toLowerCase(java.util.Locale.ROOT)));
        };
    }

    /**
     * Bild der Station im Rezept-Diagramm (32 Pixel, in der mittleren Reihe), null wenn es keins gibt.
     */
    public @Nullable Component diagramImage() {
        return switch (this) {
            case SHAWARMA_SPIT -> ItemIcons.diagram(Key.key("kaleidoscope_cookery", "shawarma_spit"));
            case CRAFTING -> ItemIcons.diagram(Key.key("minecraft", "crafting_table"));
            case FURNACE -> ItemIcons.diagram(Key.key("minecraft", "furnace"));
            default -> Component.text(Character.toString(0xE220 + ordinal())).font(FONT).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);
        };
    }

    /**
     * Großes Bild als Schriftzeichen, null wenn es keins gibt.
     */
    public @Nullable Component bigImage() {
        return bigImage == 0 ? null : Component.text(Character.toString(bigImage)).font(FONT);
    }

    /**
     * Ponder-Texte: Überschrift und Zeilen je Szene (in der Reihenfolge des Mods).
     */
    public List<Component> guide() {
        List<Component> lines = new java.util.ArrayList<>();
        guide.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            lines.add(Lang.raw("kaleidoscope_cookery.ponder." + entry.getKey() + ".header"));
            for (int i = 1; i <= entry.getValue(); i++) {
                lines.add(Lang.raw("kaleidoscope_cookery.ponder." + entry.getKey() + ".text_" + i));
            }
        });
        return lines;
    }
}
