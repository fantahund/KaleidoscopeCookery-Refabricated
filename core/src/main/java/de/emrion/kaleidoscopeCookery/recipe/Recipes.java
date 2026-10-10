package de.emrion.kaleidoscopeCookery.recipe;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * All recipes from recipes.yml. Immutable - /kc reload creates a new instance.
 */
public class Recipes {

    private final Map<String, List<String>> tags;
    private final List<ChoppingBoardRecipe> choppingBoard;
    private final List<VanillaRecipe> vanilla;
    private final List<PotRecipe> pot;
    private final List<StockpotRecipe> stockpot;
    private final List<SteamerRecipe> steamer;
    private final List<TeapotRecipe> teapot;
    private final List<MillstoneRecipe> millstone;
    private final List<BambooTrayRecipe> bambooTray;
    private final List<RiceBowlRecipe> riceBowl;
    private final Ingredient cookedRice;
    private final int dataVersion;

    private Recipes(Map<String, List<String>> tags, List<ChoppingBoardRecipe> choppingBoard, List<VanillaRecipe> vanilla, List<PotRecipe> pot,
                    List<StockpotRecipe> stockpot, List<SteamerRecipe> steamer, List<TeapotRecipe> teapot, List<MillstoneRecipe> millstone, List<BambooTrayRecipe> bambooTray, List<RiceBowlRecipe> riceBowl, int dataVersion) {
        this.tags = Collections.unmodifiableMap(tags);
        this.choppingBoard = List.copyOf(choppingBoard);
        this.vanilla = List.copyOf(vanilla);
        this.pot = List.copyOf(pot);
        this.stockpot = List.copyOf(stockpot);
        this.steamer = List.copyOf(steamer);
        this.teapot = List.copyOf(teapot);
        this.millstone = List.copyOf(millstone);
        this.bambooTray = List.copyOf(bambooTray);
        this.riceBowl = List.copyOf(riceBowl);
        Ingredient rice;
        try {
            rice = Ingredient.parse(RiceBowlRecipe.COOKED_RICE, tags);
        } catch (IllegalArgumentException e) {
            rice = Ingredient.parse("kaleidoscope_cookery:cooked_rice", tags);
        }
        this.cookedRice = rice;
        this.dataVersion = dataVersion;
    }

    public static Recipes load(YamlConfiguration config, Logger logger) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        ConfigurationSection tagSection = config.getConfigurationSection("tags");
        if (tagSection != null) {
            // Tag names contain ":" and "/", so do not resolve them via paths
            for (Map.Entry<String, Object> entry : tagSection.getValues(false).entrySet()) {
                List<String> values = new ArrayList<>();
                if (entry.getValue() instanceof List<?> list) {
                    for (Object value : list) {
                        values.add(String.valueOf(value));
                    }
                }
                tags.put(entry.getKey(), List.copyOf(values));
            }
        }

        List<ChoppingBoardRecipe> chopping = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "chopping-board").entrySet()) {
            ConfigurationSection section = entry.getValue();
            try {
                List<RecipeResult> results = new ArrayList<>();
                for (Object result : section.getList("results", List.of())) {
                    results.add(RecipeResult.parse(result));
                }
                if (results.isEmpty()) {
                    throw new IllegalArgumentException("keine Ergebnisse");
                }
                chopping.add(new ChoppingBoardRecipe(entry.getKey(), Ingredient.parse(section.get("ingredient"), tags),
                        Math.max(1, section.getInt("cuts", 4)), Key.key(section.getString("model", "")), results));
            } catch (RuntimeException e) {
                logger.warning("Schneidebrett-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }

        List<VanillaRecipe> vanilla = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "vanilla").entrySet()) {
            try {
                vanilla.add(VanillaRecipe.parse(entry.getKey(), entry.getValue(), tags));
            } catch (RuntimeException e) {
                logger.warning("Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        List<PotRecipe> pot = new ArrayList<>();
        for (String kind : List.of("pot", "flex-pot")) {
            for (Map.Entry<String, ConfigurationSection> entry : sections(config, kind).entrySet()) {
                ConfigurationSection section = entry.getValue();
                try {
                    List<Ingredient> ingredients = new ArrayList<>();
                    for (Object value : section.getList("ingredients", List.of())) {
                        ingredients.add(Ingredient.parse(value, tags));
                    }
                    if (ingredients.isEmpty() || ingredients.size() > PotRecipe.SLOTS) {
                        throw new IllegalArgumentException("1 bis " + PotRecipe.SLOTS + " Zutaten nötig");
                    }
                    Object carrier = section.get("carrier");
                    pot.add(new PotRecipe(entry.getKey(), kind.equals("flex-pot"), List.copyOf(ingredients), section.getBoolean("per-item"),
                            Math.max(1, section.getInt("time", 200)), Math.max(0, section.getInt("stir-fry", 3)),
                            carrier == null ? null : Ingredient.parse(carrier, tags), RecipeResult.parse(section.get("result"))));
                } catch (RuntimeException e) {
                    logger.warning("Wok-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
                }
            }
        }
        List<StockpotRecipe> stockpot = new ArrayList<>();
        for (String kind : List.of("stockpot", "flex-stockpot")) {
            for (Map.Entry<String, ConfigurationSection> entry : sections(config, kind).entrySet()) {
                ConfigurationSection section = entry.getValue();
                try {
                    List<Ingredient> ingredients = new ArrayList<>();
                    for (Object value : section.getList("ingredients", List.of())) {
                        ingredients.add(Ingredient.parse(value, tags));
                    }
                    if (ingredients.isEmpty() || ingredients.size() > StockpotRecipe.SLOTS) {
                        throw new IllegalArgumentException("1 bis " + StockpotRecipe.SLOTS + " Zutaten nötig");
                    }
                    Object carrierValue = section.get("carrier", "minecraft:bowl");
                    Ingredient carrier = "none".equals(carrierValue) ? null : Ingredient.parse(carrierValue, tags);
                    stockpot.add(new StockpotRecipe(entry.getKey(), kind.equals("flex-stockpot"), List.copyOf(ingredients),
                            Key.key(section.getString("soup-base", "minecraft:water")), Math.max(1, section.getInt("time", StockpotRecipe.DEFAULT_TIME)), carrier,
                            RecipeResult.parse(section.get("result")),
                            Key.key(section.getString("cooking-texture", StockpotRecipe.DEFAULT_COOKING_TEXTURE.asString())),
                            Key.key(section.getString("finished-texture", StockpotRecipe.DEFAULT_FINISHED_TEXTURE.asString())),
                            section.getInt("cooking-color", StockpotRecipe.DEFAULT_COOKING_COLOR), section.getInt("finished-color", StockpotRecipe.DEFAULT_FINISHED_COLOR)));
                } catch (RuntimeException e) {
                    logger.warning("Suppentopf-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
                }
            }
        }
        List<SteamerRecipe> steamer = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "steamer").entrySet()) {
            try {
                steamer.add(new SteamerRecipe(entry.getKey(), Ingredient.parse(entry.getValue().get("ingredient"), tags),
                        Math.max(1, entry.getValue().getInt("time", SteamerRecipe.DEFAULT_TIME)), RecipeResult.parse(entry.getValue().get("result"))));
            } catch (RuntimeException e) {
                logger.warning("Dampfkorb-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        List<TeapotRecipe> teapot = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "teapot").entrySet()) {
            ConfigurationSection section = entry.getValue();
            try {
                teapot.add(new TeapotRecipe(entry.getKey(), Ingredient.parse(section.get("ingredient"), tags), Math.max(1, section.getInt("ingredient-count", 1)),
                        Key.key(section.getString("tea-fluid", "minecraft:water")), Math.max(1, section.getInt("time", TeapotRecipe.DEFAULT_TIME)),
                        RecipeResult.parse(section.get("result"))));
            } catch (RuntimeException e) {
                logger.warning("Teekannen-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        List<MillstoneRecipe> millstone = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "millstone").entrySet()) {
            try {
                List<MillstoneRecipe.Output> results = new ArrayList<>();
                for (Object value : entry.getValue().getList("results", List.of())) {
                    double chance = value instanceof Map<?, ?> map && map.get("chance") instanceof Number number ? number.doubleValue() : 1;
                    results.add(new MillstoneRecipe.Output(RecipeResult.parse(value), Math.clamp(chance, 0, 1)));
                }
                if (results.isEmpty()) {
                    throw new IllegalArgumentException("keine Ergebnisse");
                }
                millstone.add(new MillstoneRecipe(entry.getKey(), Ingredient.parse(entry.getValue().get("ingredient"), tags), List.copyOf(results)));
            } catch (RuntimeException e) {
                logger.warning("Mühlstein-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        List<BambooTrayRecipe> bambooTray = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "bamboo-tray").entrySet()) {
            ConfigurationSection section = entry.getValue();
            try {
                bambooTray.add(new BambooTrayRecipe(entry.getKey(), BambooTrayRecipe.Type.valueOf(section.getString("type", "drying").toUpperCase(java.util.Locale.ROOT)),
                        Ingredient.parse(section.get("ingredient"), tags), Math.max(1, section.getInt("time", 1800)), RecipeResult.parse(section.get("result"))));
            } catch (RuntimeException e) {
                logger.warning("Bambustablett-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        List<RiceBowlRecipe> riceBowl = new ArrayList<>();
        for (Map.Entry<String, ConfigurationSection> entry : sections(config, "rice-bowl").entrySet()) {
            try {
                riceBowl.add(new RiceBowlRecipe(entry.getKey(), Ingredient.parse(entry.getValue().get("ingredient"), tags), RecipeResult.parse(entry.getValue().get("result"))));
            } catch (RuntimeException e) {
                logger.warning("Reisschalen-Rezept " + entry.getKey() + " ungültig: " + e.getMessage());
            }
        }
        return new Recipes(tags, chopping, vanilla, pot, stockpot, steamer, teapot, millstone, bambooTray, riceBowl, config.getInt("data-version", 0));
    }

    /**
     * Subsections whose names contain ":" (recipe ids).
     */
    private static Map<String, ConfigurationSection> sections(ConfigurationSection config, String path) {
        Map<String, ConfigurationSection> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection child) {
                    result.put(entry.getKey(), child);
                }
            }
        }
        return result;
    }

    public @Nullable ChoppingBoardRecipe findChoppingBoard(ItemStack input) {
        for (ChoppingBoardRecipe recipe : choppingBoard) {
            if (recipe.ingredient().test(input)) {
                return recipe;
            }
        }
        return null;
    }

    public @Nullable ChoppingBoardRecipe choppingBoardById(String id) {
        for (ChoppingBoardRecipe recipe : choppingBoard) {
            if (recipe.id().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    public List<ChoppingBoardRecipe> choppingBoard() {
        return choppingBoard;
    }

    public List<VanillaRecipe> vanilla() {
        return vanilla;
    }

    public List<PotRecipe> pot() {
        return pot;
    }

    public List<StockpotRecipe> stockpot() {
        return stockpot;
    }

    /**
     * Exact recipes first, then flex recipes (like StockpotBlockEntity#setRecipe).
     */
    public @Nullable StockpotRecipe findStockpot(List<ItemStack> inputs, Key soupBase) {
        for (boolean flex : new boolean[]{false, true}) {
            for (StockpotRecipe recipe : stockpot) {
                if (recipe.flex() == flex && recipe.matches(inputs, soupBase)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    public @Nullable SteamerRecipe findSteamer(ItemStack input) {
        for (SteamerRecipe recipe : steamer) {
            if (recipe.ingredient().test(input)) {
                return recipe;
            }
        }
        return null;
    }

    public @Nullable TeapotRecipe findTeapot(ItemStack input, Key fluid) {
        for (TeapotRecipe recipe : teapot) {
            if (recipe.fluid().equals(fluid) && recipe.ingredient().test(input) && input.getAmount() >= recipe.ingredientCount()) {
                return recipe;
            }
        }
        return null;
    }

    public @Nullable MillstoneRecipe findMillstone(ItemStack input) {
        for (MillstoneRecipe recipe : millstone) {
            if (recipe.ingredient().test(input)) {
                return recipe;
            }
        }
        return null;
    }

    public @Nullable BambooTrayRecipe findBambooTray(ItemStack input, BambooTrayRecipe.Type type) {
        for (BambooTrayRecipe recipe : bambooTray) {
            if (recipe.type() == type && recipe.ingredient().test(input)) {
                return recipe;
            }
        }
        return null;
    }

    public List<RiceBowlRecipe> riceBowl() {
        return riceBowl;
    }

    public Ingredient cookedRice() {
        return cookedRice;
    }

    public List<MillstoneRecipe> millstone() {
        return millstone;
    }

    public List<BambooTrayRecipe> bambooTray() {
        return bambooTray;
    }

    public List<TeapotRecipe> teapot() {
        return teapot;
    }

    public List<SteamerRecipe> steamer() {
        return steamer;
    }

    public int dataVersion() {
        return dataVersion;
    }

    /**
     * Exact wok recipes first, then flex recipes (like PotBlockEntity#startCooking).
     */
    public @Nullable PotRecipe findPot(List<ItemStack> inputs) {
        for (boolean flex : new boolean[]{false, true}) {
            for (PotRecipe recipe : pot) {
                if (recipe.flex() == flex && recipe.match(inputs) > 0) {
                    return recipe;
                }
            }
        }
        return null;
    }
}
