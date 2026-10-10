package de.emrion.kaleidoscopeCookery.station.crop;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Crop kind. Cookery: BaseCropBlock, ChiliCropBlock, LettuceCropBlock, RiceCropBlock, TeaTreeBlock; Addons (Grilling)
 * register more with {@link #register} before the station types are registered.
 *
 * @param name        name (station type crop_&lt;name&gt;)
 * @param seeds       items that plant the crop
 * @param result      harvest (right-click or breaking in the last stage)
 * @param lootSeed    seed in the loot
 * @param maxAge      last stage
 * @param harvestAge  stage after harvesting via right-click; -1 = no right-click harvest
 * @param soil        soil
 * @param seedTrials  rolls for extra seeds in the mature loot (binomial_with_bonus_count, p = 4/7)
 * @param matureExtra fixed number of extra seeds in the mature loot (sweet potato: set_count 2)
 * @param models      model per stage: "%d" = stage, "%s" = part (rice) or variant
 * @param variant     second look (houttuynia on soul sand), otherwise null
 * @param harvestMax  right-click harvest: 1 up to this many items
 */
public record CropKind(String name, List<Key> seeds, Key result, Key lootSeed, int maxAge, int harvestAge, Soil soil,
                       int seedTrials, int matureExtra, String models, @Nullable Variant variant, int harvestMax) {

    public enum Soil {
        FARMLAND, DIRT, WATER,
        /** Farmland or soul sand (houttuynia from Grilling) */
        FARMLAND_OR_SOUL_SAND
    }

    /**
     * Second look from a certain stage, e.g. red houttuynia.
     *
     * @param chance    chance when planting
     * @param soil      always on this soil
     * @param fromAge   only visible from this stage
     * @param suffix    suffix appended to the model name
     */
    public record Variant(double chance, Material soil, int fromAge, String suffix) {
    }

    /**
     * BlockTags.DIRT in 1.21.1 (in 26.x the tag only contains dirt, coarse dirt and rooted dirt).
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
        return new CropKind(name, seeds, result, lootSeed, maxAge, harvestAge, soil, 3, 0, COOKERY_MODELS.formatted(name), null, 1);
    }

    private static Key key(String value) {
        return Key.key("kaleidoscope_cookery", value);
    }

    /**
     * Another crop kind (addon). Register the station type {@link CropStation#type(CropKind)} afterwards.
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
     * Item model definition of the stage (for rice with part down/middle/up, otherwise possibly the variant suffix).
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
