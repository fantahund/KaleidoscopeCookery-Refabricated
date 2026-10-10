package de.emrion.kaleidoscopeCookery.station;

import net.kyori.adventure.key.Key;

import java.util.Map;

/**
 * Ingredients shown with a different model in pots (item_render_replacer.json in the mod).
 */
public final class DisplayModels {

    private static final Map<Key, Key> REPLACEMENTS = Map.of(
            Key.key("kaleidoscope_cookery", "cooked_rice"), Key.key("kaleidoscope_cookery", "rice"),
            Key.key("minecraft", "honey_bottle"), Key.key("kaleidoscope_cookery", "honey"),
            Key.key("minecraft", "egg"), Key.key("kaleidoscope_cookery", "egg"));

    private DisplayModels() {
    }

    public static Key replace(Key item, Key model) {
        return REPLACEMENTS.getOrDefault(item, model);
    }
}
