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

    private Recipes(Map<String, List<String>> tags, List<ChoppingBoardRecipe> choppingBoard, List<VanillaRecipe> vanilla) {
        this.tags = Collections.unmodifiableMap(tags);
        this.choppingBoard = List.copyOf(choppingBoard);
        this.vanilla = List.copyOf(vanilla);
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
        return new Recipes(tags, chopping, vanilla);
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
}
