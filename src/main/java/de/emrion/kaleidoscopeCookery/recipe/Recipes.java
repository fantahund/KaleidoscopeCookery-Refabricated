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
 * Alle Rezepte aus recipes.yml. Unveränderlich – /kc reload erzeugt eine neue Instanz.
 */
public class Recipes {

    private final Map<String, List<String>> tags;
    private final List<ChoppingBoardRecipe> choppingBoard;
    private final List<VanillaRecipe> vanilla;
    private final List<PotRecipe> pot;
    private final List<StockpotRecipe> stockpot;
    private final List<SteamerRecipe> steamer;
    private final int dataVersion;

    private Recipes(Map<String, List<String>> tags, List<ChoppingBoardRecipe> choppingBoard, List<VanillaRecipe> vanilla, List<PotRecipe> pot,
                    List<StockpotRecipe> stockpot, List<SteamerRecipe> steamer, int dataVersion) {
        this.tags = Collections.unmodifiableMap(tags);
        this.choppingBoard = List.copyOf(choppingBoard);
        this.vanilla = List.copyOf(vanilla);
        this.pot = List.copyOf(pot);
        this.stockpot = List.copyOf(stockpot);
        this.steamer = List.copyOf(steamer);
        this.dataVersion = dataVersion;
    }

    public static Recipes load(YamlConfiguration config, Logger logger) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        ConfigurationSection tagSection = config.getConfigurationSection("tags");
        if (tagSection != null) {
            // Tag-Namen enthalten ":" und "/", deshalb nicht über Pfade auflösen
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
        return new Recipes(tags, chopping, vanilla, pot, stockpot, steamer, config.getInt("data-version", 0));
    }

    /**
     * Unterabschnitte, deren Namen ":" enthalten (Rezept-IDs).
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
     * Erst exakte, dann Flex-Rezepte (wie StockpotBlockEntity#setRecipe).
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

    public List<SteamerRecipe> steamer() {
        return steamer;
    }

    public int dataVersion() {
        return dataVersion;
    }

    /**
     * Erst exakte Wok-Rezepte, dann Flex-Rezepte (wie PotBlockEntity#startCooking).
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
