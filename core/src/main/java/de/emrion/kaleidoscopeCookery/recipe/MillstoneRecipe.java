package de.emrion.kaleidoscopeCookery.recipe;

import java.util.List;

/**
 * Millstone: for each inserted item every output is produced with its chance (MillstoneBlockEntity#tick).
 */
public record MillstoneRecipe(String id, Ingredient ingredient, List<Output> results) {

    public record Output(RecipeResult result, double chance) {
    }
}
