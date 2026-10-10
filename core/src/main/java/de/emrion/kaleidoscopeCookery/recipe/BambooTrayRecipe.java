package de.emrion.kaleidoscopeCookery.recipe;

/**
 * Bamboo tray: drying under the open sky without rain, wetting in the rain or under dripping dripstone.
 */
public record BambooTrayRecipe(String id, Type type, Ingredient ingredient, int time, RecipeResult result) {

    public enum Type {
        DRYING, WETTING
    }
}
