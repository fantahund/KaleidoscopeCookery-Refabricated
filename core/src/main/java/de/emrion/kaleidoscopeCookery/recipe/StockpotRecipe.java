package de.emrion.kaleidoscopeCookery.recipe;

import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Stockpot recipe (stockpot / flex_stockpot in the mod).
 *
 * @param soupBase        broth (minecraft:water, minecraft:lava, minecraft:milk, minecraft:cod_bucket ...)
 * @param carrier         container for taking it out, null = none needed
 * @param cookingTexture  surface while cooking (item model definition kaleidoscope:surface/...)
 * @param finishedTexture surface when done
 */
public record StockpotRecipe(String id, boolean flex, List<Ingredient> ingredients, Key soupBase, int time, @Nullable Ingredient carrier,
                             RecipeResult result, Key cookingTexture, Key finishedTexture, int cookingColor, int finishedColor) {

    public static final int SLOTS = 9;
    public static final int DEFAULT_TIME = 300;
    public static final Key DEFAULT_COOKING_TEXTURE = Key.key("kaleidoscope_cookery", "stockpot/default_cooking");
    public static final Key DEFAULT_FINISHED_TEXTURE = Key.key("kaleidoscope_cookery", "stockpot/default_finished");
    public static final int DEFAULT_COOKING_COLOR = 0xFFECC3;
    public static final int DEFAULT_FINISHED_COLOR = 0xF4AA8B;

    public boolean matches(List<ItemStack> inputs, Key currentSoupBase) {
        if (!soupBase.equals(currentSoupBase)) {
            return false;
        }
        List<ItemStack> present = IngredientMatcher.present(inputs);
        return !present.isEmpty() && (flex ? IngredientMatcher.flex(present, ingredients) : IngredientMatcher.exact(present, ingredients));
    }
}
