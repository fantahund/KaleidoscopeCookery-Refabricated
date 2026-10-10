package de.emrion.kaleidoscopeCookery.station.crop;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pflanzenart. Cookery: BaseCropBlock, ChiliCropBlock, LettuceCropBlock, RiceCropBlock, TeaTreeBlock; Addons (Grilling)
 * melden weitere mit {@link #register} an, bevor die Stationstypen registriert werden.
 *
 * @param name        Name (Stationstyp crop_&lt;name&gt;)
 * @param seeds       Items, mit denen man die Pflanze setzt
 * @param result      Ernte (Rechtsklick bzw. Abbauen in der letzten Stufe)
 * @param lootSeed    Samen in der Beute
 * @param maxAge      letzte Stufe
 * @param harvestAge  Stufe nach dem Ernten per Rechtsklick; -1 = keine Rechtsklick-Ernte
 * @param soil        Boden
 * @param seedTrials  Würfe für zusätzliche Samen in der reifen Beute (binomial_with_bonus_count, p = 4/7)
 * @param matureExtra feste Zahl zusätzlicher Samen in der reifen Beute (Süßkartoffel: set_count 2)
 * @param models      Modell je Stufe: "%d" = Stufe, "%s" = Teil (Reis) bzw. Variante
 * @param variant     zweites Aussehen (Fischminze auf Seelensand), sonst null
 */
public record CropKind(String name, List<Key> seeds, Key result, Key lootSeed, int maxAge, int harvestAge, Soil soil,
                       int seedTrials, int matureExtra, String models, @Nullable Variant variant) {

    public enum Soil {
        FARMLAND, DIRT, WATER,
        /** Ackerboden oder Seelensand (Fischminze aus Grilling) */
        FARMLAND_OR_SOUL_SAND
    }

    /**
     * Zweites Aussehen ab einer Stufe, z. B. rote Fischminze.
     *
     * @param chance    Zufall beim Pflanzen
     * @param soil      auf diesem Boden immer
     * @param fromAge   erst ab dieser Stufe sichtbar
     * @param suffix    Anhang an den Modellnamen
     */
    public record Variant(double chance, Material soil, int fromAge, String suffix) {
    }

    /**
     * BlockTags.DIRT in 1.21.1 (in 26.x enthält der Tag nur noch Erde, Grobe Erde und Wurzelerde).
     */
    public static final java.util.Set<Material> DIRT_121 = java.util.EnumSet.of(Material.DIRT, Material.GRASS_BLOCK,
            Material.PODZOL, Material.COARSE_DIRT, Material.MYCELIUM, Material.ROOTED_DIRT,
            Material.MOSS_BLOCK, Material.MUD, Material.MUDDY_MANGROVE_ROOTS);

    private static final String COOKERY_MODELS = "kaleidoscope_cookery:block/crop/%s/stage%%d%%s";

    public static final CropKind TOMATO = cookery("tomato", List.of(key("tomato_seed")), key("tomato"), key("tomato_seed"), 7, 5, Soil.FARMLAND);
    public static final CropKind CHILI = cookery("chili", List.of(key("chili_seed")), key("red_chili"), key("chili_seed"), 7, 5, Soil.FARMLAND);
    public static final CropKind LETTUCE = cookery("lettuce", List.of(key("lettuce_seed")), key("lettuce"), key("lettuce_seed"), 7, -1, Soil.FARMLAND);
    public static final CropKind RICE = cookery("rice", List.of(key("rice"), key("wild_rice")), key("rice_panicle"), key("wild_rice"), 7, -1, Soil.WATER);
    public static final CropKind TEA = cookery("tea_tree", List.of(key("tea_seed")), key("fresh_tea_leaves"), key("tea_seed"), 5, 3, Soil.DIRT);

    private static final List<CropKind> KINDS = new ArrayList<>(List.of(TOMATO, CHILI, LETTUCE, RICE, TEA));

    private static CropKind cookery(String name, List<Key> seeds, Key result, Key lootSeed, int maxAge, int harvestAge, Soil soil) {
        return new CropKind(name, seeds, result, lootSeed, maxAge, harvestAge, soil, 3, 0, COOKERY_MODELS.formatted(name), null);
    }

    private static Key key(String value) {
        return Key.key("kaleidoscope_cookery", value);
    }

    /**
     * Weitere Pflanzenart (Addon). Danach den Stationstyp {@link CropStation#type(CropKind)} registrieren.
     */
    public static void register(CropKind kind) {
        if (KINDS.stream().noneMatch(k -> k.name.equals(kind.name))) {
            KINDS.add(kind);
        }
    }

    public static List<CropKind> values() {
        return Collections.unmodifiableList(KINDS);
    }

    public String id() {
        return name;
    }

    public String typeId() {
        return "crop_" + name;
    }

    /**
     * Item-Model-Definition der Stufe (bei Reis mit Teil down/middle/up, sonst ggf. Varianten-Anhang).
     */
    public Key model(int age, @Nullable String part) {
        return Key.key(models.formatted(age, part == null ? "" : (part.startsWith("_") ? part : "_" + part)));
    }

    public static @Nullable CropKind bySeed(@Nullable Key item) {
        if (item == null) {
            return null;
        }
        for (CropKind kind : KINDS) {
            if (kind.seeds.contains(item)) {
                return kind;
            }
        }
        return null;
    }
}
