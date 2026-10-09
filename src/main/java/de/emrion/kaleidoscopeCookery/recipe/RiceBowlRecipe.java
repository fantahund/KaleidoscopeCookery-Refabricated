package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Reisschale (RiceBowlRecipe im Mod): formlos eine Zutat und gekochter Reis; das Ergebnis bekommt die beste Qualität der beiden.
 */
public record RiceBowlRecipe(String id, Ingredient ingredient, RecipeResult result) {

    public static final String COOKED_RICE = "#c:foods/cooked_rice";
}
