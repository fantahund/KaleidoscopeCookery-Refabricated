package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Ein Rezept, wie es im Kochbuch angezeigt wird – unabhängig von der Station.
 *
 * @param category Station
 * @param id       Rezept-ID (für die Reihenfolge und zum Wiederfinden)
 * @param inputs   Zutaten mit Anzahl
 * @param outputs  Ergebnisse (erstes = Hauptergebnis)
 * @param info     Zeilen unter den Zutaten (Zeit, Umrühren, Behälter ...)
 */
public record CookbookEntry(Category category, String id, List<Input> inputs, List<Output> outputs, List<Component> info) {

    public record Input(Ingredient ingredient, int amount) {
    }

    /**
     * @param chance 0..1, 1 = immer
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
