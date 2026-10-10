package de.emrion.kaleidoscopeCookery.recipe;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafting, furnace, smithing and stonecutter recipes of the mods (section "vanilla" in recipes.yml).
 *
 * @param key         crafting table (shaped): symbol -> ingredient
 * @param ingredients crafting table (shapeless), otherwise exactly one ingredient (furnace, stonecutter) or template/base/addition (smithing)
 */
public record VanillaRecipe(String id, Type type, List<String> pattern, Map<Character, Ingredient> key, List<Ingredient> ingredients,
                            int time, float experience, RecipeResult result) {

    public enum Type {
        CRAFTING_SHAPED, CRAFTING_SHAPELESS, SMELTING, SMOKING, CAMPFIRE_COOKING, BLASTING, SMITHING_TRANSFORM, STONECUTTING
    }

    static VanillaRecipe parse(String id, ConfigurationSection section, Map<String, List<String>> tags) {
        Type type;
        try {
            type = Type.valueOf(section.getString("type", "").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unbekannter Typ " + section.getString("type"));
        }
        RecipeResult result = RecipeResult.parse(section.get("result"));
        List<String> pattern = List.of();
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        List<Ingredient> ingredients = new ArrayList<>();
        switch (type) {
            case CRAFTING_SHAPED -> {
                pattern = List.copyOf(section.getStringList("pattern"));
                ConfigurationSection keys = section.getConfigurationSection("key");
                if (pattern.isEmpty() || keys == null) {
                    throw new IllegalArgumentException("pattern/key fehlt");
                }
                for (String symbol : keys.getKeys(false)) {
                    key.put(symbol.charAt(0), Ingredient.parse(keys.get(symbol), tags));
                }
            }
            case CRAFTING_SHAPELESS -> {
                for (Object value : section.getList("ingredients", List.of())) {
                    ingredients.add(Ingredient.parse(value, tags));
                }
            }
            case SMITHING_TRANSFORM -> {
                ingredients.add(Ingredient.parse(section.get("template"), tags));
                ingredients.add(Ingredient.parse(section.get("base"), tags));
                ingredients.add(Ingredient.parse(section.get("addition"), tags));
            }
            default -> ingredients.add(Ingredient.parse(section.get("ingredient"), tags));
        }
        int defaultTime = switch (type) {
            case SMELTING -> 200;
            case SMOKING, BLASTING -> 100;
            case CAMPFIRE_COOKING -> 600;
            default -> 0;
        };
        return new VanillaRecipe(id, type, pattern, key, ingredients, section.getInt("time", defaultTime), (float) section.getDouble("experience", 0),
                result);
    }
}
