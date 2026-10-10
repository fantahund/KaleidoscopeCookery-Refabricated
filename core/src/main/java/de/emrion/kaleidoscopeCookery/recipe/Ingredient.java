package de.emrion.kaleidoscopeCookery.recipe;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ingredient of a recipe: a set of allowed item ids (mod items or minecraft:...).
 * In recipes.yml as an id, "#tag" or a list of alternatives. Tags from the "tags" section,
 * vanilla tags (#minecraft:planks ...) come from the server.
 */
public record Ingredient(Set<Key> items) {

    public boolean test(ItemStack stack) {
        return stack != null && !stack.isEmpty() && items.contains(CookeryItems.typeOf(stack));
    }

    /**
     * First allowed item (for display).
     */
    public Key first() {
        return items.iterator().next();
    }

    /**
     * @throws IllegalArgumentException for unknown tags or empty ingredients
     */
    public static Ingredient parse(Object value, Map<String, List<String>> tags) {
        Set<Key> items = new LinkedHashSet<>();
        if (value == null) {
            throw new IllegalArgumentException("Zutat fehlt");
        }
        List<Object> options = value instanceof List<?> list ? new ArrayList<>(list) : List.of(value);
        for (Object option : options) {
            add(String.valueOf(option), tags, items, 0);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("leere Zutat " + value);
        }
        return new Ingredient(Set.copyOf(items));
    }

    private static void add(String text, Map<String, List<String>> tags, Set<Key> items, int depth) {
        if (depth > 8) {
            throw new IllegalArgumentException("Tag-Schleife bei " + text);
        }
        if (!text.startsWith("#")) {
            if (!Key.parseable(text)) {
                throw new IllegalArgumentException("ungültige Item-ID " + text);
            }
            items.add(Key.key(text));
            return;
        }
        String name = text.substring(1);
        List<String> tag = tags.get(name);
        if (tag != null) {
            for (String entry : tag) {
                add(entry, tags, items, depth + 1);
            }
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(name);
        Tag<Material> vanilla = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
        if (vanilla == null) {
            throw new IllegalArgumentException("unbekannter Tag " + text);
        }
        for (Material material : vanilla.getValues()) {
            items.add(material.key());
        }
    }
}
