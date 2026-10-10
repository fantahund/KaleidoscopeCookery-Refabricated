package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * A recipe as it is shown in the cookbook - independent of the station.
 *
 * @param category Station
 * @param id       recipe id (for ordering and finding it again)
 * @param inputs   ingredients with amount
 * @param outputs  results (first = main result)
 * @param info     lines below the ingredients (time, stirring, container ...)
 * @param grid     crafting table: 9 slots (row by row, null = empty), otherwise null
 */
public record CookbookEntry(Category category, String id, List<Input> inputs, List<Output> outputs, List<Component> info,
                            @org.jspecify.annotations.Nullable List<@org.jspecify.annotations.Nullable Ingredient> grid) {

    public CookbookEntry(Category category, String id, List<Input> inputs, List<Output> outputs, List<Component> info) {
        this(category, id, inputs, outputs, info, null);
    }

    public record Input(Ingredient ingredient, int amount) {
    }

    /**
     * @param chance 0..1, 1 = always
     */
    public record Output(Key item, int amount, double chance) {
    }

    public Output main() {
        return outputs.getFirst();
    }

    public boolean produces(Key item) {
        return outputs.stream().anyMatch(output -> output.item().equals(item));
    }

    public boolean uses(Key item) {
        return inputs.stream().anyMatch(input -> input.ingredient().items().contains(item));
    }
}
