package de.emrion.kaleidoscopeCookery.station.crop;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Pflanzen aus Kaleidoscope Cookery (BaseCropBlock, ChiliCropBlock, LettuceCropBlock, RiceCropBlock, TeaTreeBlock).
 *
 * @param seeds        Items, mit denen man die Pflanze setzt
 * @param result       Ernte (Rechtsklick bzw. Abbauen in der letzten Stufe)
 * @param lootSeed     Samen in der Beute
 * @param model        Modell-Präfix im Resourcepack (Stufe wird angehängt)
 * @param harvestAge   Stufe nach dem Ernten per Rechtsklick; -1 = keine Rechtsklick-Ernte
 */
public enum CropKind {
    TOMATO("tomato", List.of(key("tomato_seed")), key("tomato"), key("tomato_seed"), 7, 5, Soil.FARMLAND),
    CHILI("chili", List.of(key("chili_seed")), key("red_chili"), key("chili_seed"), 7, 5, Soil.FARMLAND),
    LETTUCE("lettuce", List.of(key("lettuce_seed")), key("lettuce"), key("lettuce_seed"), 7, -1, Soil.FARMLAND),
    RICE("rice", List.of(key("rice"), key("wild_rice")), key("rice_panicle"), key("wild_rice"), 7, -1, Soil.WATER),
    TEA("tea_tree", List.of(key("tea_seed")), key("fresh_tea_leaves"), key("tea_seed"), 5, 3, Soil.DIRT);

    public enum Soil {
        FARMLAND, DIRT, WATER
    }

    private final String name;
    private final List<Key> seeds;
    private final Key result;
    private final Key lootSeed;
    private final int maxAge;
    private final int harvestAge;
    private final Soil soil;

    CropKind(String name, List<Key> seeds, Key result, Key lootSeed, int maxAge, int harvestAge, Soil soil) {
        this.name = name;
        this.seeds = seeds;
        this.result = result;
        this.lootSeed = lootSeed;
        this.maxAge = maxAge;
        this.harvestAge = harvestAge;
        this.soil = soil;
    }

    private static Key key(String value) {
        return Key.key("kaleidoscope_cookery", value);
    }

    public String id() {
        return name;
    }

    public String typeId() {
        return "crop_" + name;
    }

    public List<Key> seeds() {
        return seeds;
    }

    public Key result() {
        return result;
    }

    public Key lootSeed() {
        return lootSeed;
    }

    public int maxAge() {
        return maxAge;
    }

    public int harvestAge() {
        return harvestAge;
    }

    public Soil soil() {
        return soil;
    }

    /**
     * Item-Model-Definition der Stufe (bei Reis mit Teil down/middle/up).
     */
    public Key model(int age, @Nullable String part) {
        String suffix = part == null ? "" : "_" + part;
        return Key.key("kaleidoscope_cookery", "block/crop/" + name + "/stage" + age + suffix);
    }

    public static @Nullable CropKind bySeed(@Nullable Key item) {
        if (item == null) {
            return null;
        }
        for (CropKind kind : values()) {
            if (kind.seeds.contains(item)) {
                return kind;
            }
        }
        return null;
    }
}
