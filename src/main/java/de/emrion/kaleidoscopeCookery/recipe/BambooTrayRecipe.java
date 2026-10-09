package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Bambustablett: Trocknen unter freiem Himmel ohne Regen, Befeuchten im Regen oder unter tropfendem Tropfstein.
 */
public record BambooTrayRecipe(String id, Type type, Ingredient ingredient, int time, RecipeResult result) {

    public enum Type {
        DRYING, WETTING
    }
}
