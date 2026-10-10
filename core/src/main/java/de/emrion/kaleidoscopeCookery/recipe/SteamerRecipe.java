package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Steamer: one ingredient turns into the result after {@code time} ticks of steam (mod default 60 s).
 */
public record SteamerRecipe(String id, Ingredient ingredient, int time, RecipeResult result) {

    public static final int DEFAULT_TIME = 60 * 20;
}
