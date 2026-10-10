package de.emrion.kaleidoscopeCookery.recipe;

import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Chopping board: one ingredient, {@code cuts} cuts, then the results.
 *
 * @param model base of the cutting stage models: &lt;ns&gt;:chopping_board/&lt;path&gt;/&lt;stage&gt;
 */
public record ChoppingBoardRecipe(String id, Ingredient ingredient, int cuts, Key model, List<RecipeResult> results) {

    /**
     * Item model definition of the stage (0 = uncut, cuts = done).
     */
    public Key stageModel(int stage) {
        return stageModel(model, Math.clamp(stage, 0, cuts));
    }

    /**
     * Cookery (Fabric-Fork): models/item/chopping_board/…; Grilling: models/chopping_board/….
     */
    public static Key stageModel(Key model, int stage) {
        String folder = model.namespace().equals("kaleidoscope_cookery") ? "item/chopping_board/" : "chopping_board/";
        return Key.key(model.namespace(), folder + model.value() + "/" + stage);
    }
}
