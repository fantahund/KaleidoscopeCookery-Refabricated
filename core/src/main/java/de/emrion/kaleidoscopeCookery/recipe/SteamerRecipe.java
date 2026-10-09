package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Dampfkorb: eine Zutat wird nach {@code time} Ticks Dampf zum Ergebnis (Mod-Standard 60 s).
 */
public record SteamerRecipe(String id, Ingredient ingredient, int time, RecipeResult result) {

    public static final int DEFAULT_TIME = 60 * 20;
}
