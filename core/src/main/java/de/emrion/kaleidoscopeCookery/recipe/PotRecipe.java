package de.emrion.kaleidoscopeCookery.recipe;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wok recipe (pot / flex_pot in the mod).
 * <ul>
 *     <li>normal: the ingredients must be in the wok exactly like this (same amount)</li>
 *     <li>per-item: 1-9 identical ingredients, the result comes that often (merges the vanilla "1x-9x" recipes)</li>
 *     <li>flex: every ingredient must be there at least once, nothing else; the amounts determine the quality</li>
 * </ul>
 *
 * @param carrier container for taking it out (e.g. bowl), null = with the spatula
 */
public record PotRecipe(String id, boolean flex, List<Ingredient> ingredients, boolean perItem, int time, int stirFryCount,
                        @Nullable Ingredient carrier, RecipeResult result) {

    public static final int SLOTS = 9;

    /**
     * @return number of results (for per-item the number of ingredients), 0 if the recipe does not match
     */
    public int match(List<ItemStack> inputs) {
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) {
                present.add(input);
            }
        }
        if (present.isEmpty()) {
            return 0;
        }
        if (perItem) {
            Ingredient ingredient = ingredients.getFirst();
            for (ItemStack input : present) {
                if (!ingredient.test(input)) {
                    return 0;
                }
            }
            return result.count() * present.size();
        }
        if (flex) {
            // Identical items count once (mod: grouped by item)
            Map<Key, ItemStack> distinct = new LinkedHashMap<>();
            for (ItemStack input : present) {
                distinct.putIfAbsent(CookeryItems.typeOf(input), input);
            }
            present = new ArrayList<>(distinct.values());
        }
        if (present.size() != ingredients.size()) {
            return 0;
        }
        return assign(present, 0, new boolean[ingredients.size()]) ? result.count() : 0;
    }

    /**
     * Assign exactly one item to every ingredient (like RecipeMatcher in the mod).
     */
    private boolean assign(List<ItemStack> inputs, int index, boolean[] used) {
        if (index == inputs.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(inputs.get(index))) {
                used[i] = true;
                if (assign(inputs, index + 1, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }
}
