package de.emrion.kaleidoscopeCookery.recipe;

import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Schneidebrett: eine Zutat, {@code cuts} Schnitte, danach die Ergebnisse.
 *
 * @param model Basis der Schnittstufen-Modelle: &lt;ns&gt;:chopping_board/&lt;pfad&gt;/&lt;stufe&gt;
 */
public record ChoppingBoardRecipe(String id, Ingredient ingredient, int cuts, Key model, List<RecipeResult> results) {

    /**
     * Item-Model-Definition der Stufe (0 = ungeschnitten, cuts = fertig).
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
