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
 * Wok-Rezept (pot / flex_pot im Mod).
 * <ul>
 *     <li>normal: die Zutaten müssen genau so (gleiche Anzahl) im Wok liegen</li>
 *     <li>per-item: 1–9 gleiche Zutaten, das Ergebnis gibt es so oft (fasst die Vanilla-"1x–9x"-Rezepte zusammen)</li>
 *     <li>flex: jede Zutat muss mindestens einmal dabei sein, sonst nichts; die Mengen bestimmen die Qualität</li>
 * </ul>
 *
 * @param carrier Gefäß zum Herausnehmen (z. B. Schüssel), null = mit dem Pfannenwender
 */
public record PotRecipe(String id, boolean flex, List<Ingredient> ingredients, boolean perItem, int time, int stirFryCount,
                        @Nullable Ingredient carrier, RecipeResult result) {

    public static final int SLOTS = 9;

    /**
     * @return Anzahl der Ergebnisse (bei per-item die Zahl der Zutaten), 0 wenn das Rezept nicht passt
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
            // Gleiche Items zählen einmal (Mod: nach Item zusammengefasst)
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
     * Jeder Zutat genau ein Item zuordnen (wie RecipeMatcher im Mod).
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
