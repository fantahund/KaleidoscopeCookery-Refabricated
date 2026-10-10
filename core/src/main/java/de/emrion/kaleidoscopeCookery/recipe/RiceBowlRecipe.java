package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Rice bowl (RiceBowlRecipe in the mod): shapeless, one ingredient and cooked rice; the result gets the better quality of the two.
 */
public record RiceBowlRecipe(String id, Ingredient ingredient, RecipeResult result) {

    public static final String COOKED_RICE = "#c:foods/cooked_rice";
}
