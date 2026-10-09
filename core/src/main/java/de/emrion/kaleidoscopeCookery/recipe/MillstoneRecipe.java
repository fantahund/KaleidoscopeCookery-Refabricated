package de.emrion.kaleidoscopeCookery.recipe;

import java.util.List;

/**
 * Mühlstein: pro eingelegtem Item wird jede Ausgabe mit ihrer Chance erzeugt (MillstoneBlockEntity#tick).
 */
public record MillstoneRecipe(String id, Ingredient ingredient, List<Output> results) {

    public record Output(RecipeResult result, double chance) {
    }
}
