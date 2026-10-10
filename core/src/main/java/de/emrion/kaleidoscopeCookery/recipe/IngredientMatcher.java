package de.emrion.kaleidoscopeCookery.recipe;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns the added items to recipe ingredients (RecipeMatcher in the mod).
 */
public final class IngredientMatcher {

    private IngredientMatcher() {
    }

    /**
     * Non-empty items from the slots.
     */
    public static List<ItemStack> present(List<ItemStack> inputs) {
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) {
                present.add(input);
            }
        }
        return present;
    }

    /**
     * Exact: every ingredient used exactly once, no items left over.
     */
    public static boolean exact(List<ItemStack> present, List<Ingredient> ingredients) {
        return present.size() == ingredients.size() && assign(present, ingredients, 0, new boolean[ingredients.size()]);
    }

    /**
     * Flex: identical items count once, then exact.
     */
    public static boolean flex(List<ItemStack> present, List<Ingredient> ingredients) {
        Map<Key, ItemStack> distinct = new LinkedHashMap<>();
        for (ItemStack input : present) {
            distinct.putIfAbsent(CookeryItems.typeOf(input), input);
        }
        return exact(new ArrayList<>(distinct.values()), ingredients);
    }

    private static boolean assign(List<ItemStack> inputs, List<Ingredient> ingredients, int index, boolean[] used) {
        if (index == inputs.size()) {
            return true;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            if (!used[i] && ingredients.get(i).test(inputs.get(index))) {
                used[i] = true;
                if (assign(inputs, ingredients, index + 1, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }
}
