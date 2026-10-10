package de.emrion.kaleidoscopeCookery.recipe;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Result of a recipe. In recipes.yml as an id or { item: ID, count: n }.
 */
public record RecipeResult(Key item, int count) {

    public @Nullable ItemStack create(CookeryItems items) {
        return items.create(item, count);
    }

    public static RecipeResult parse(Object value) {
        if (value instanceof ConfigurationSection section) {
            return parse(section.getValues(false));
        }
        if (value == null) {
            throw new IllegalArgumentException("Ergebnis fehlt");
        }
        if (value instanceof Map<?, ?> map) {
            Object count = map.get("count");
            return new RecipeResult(Key.key(String.valueOf(map.get("item"))), count instanceof Number number ? Math.max(1, number.intValue()) : 1);
        }
        return new RecipeResult(Key.key(String.valueOf(value)), 1);
    }
}
