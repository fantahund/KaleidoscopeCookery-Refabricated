package de.emrion.kaleidoscopeCookery.recipe;

import net.kyori.adventure.key.Key;

/**
 * Teapot: tea base (liquid) + ingredient, tea after {@code time} ticks.
 *
 * @param fluid minecraft:water, minecraft:milk ...
 */
public record TeapotRecipe(String id, Ingredient ingredient, int ingredientCount, Key fluid, int time, RecipeResult result) {

    public static final int DEFAULT_TIME = 2400;
}
