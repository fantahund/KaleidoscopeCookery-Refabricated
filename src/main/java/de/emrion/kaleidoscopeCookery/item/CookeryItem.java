package de.emrion.kaleidoscopeCookery.item;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ein Item aus den Mods (Definition aus items.yml).
 *
 * @param id       z. B. kaleidoscope_cookery:tomato
 * @param base     Basis-Material (Papier, bei Werkzeugen wie dem Küchenmesser das passende Vanilla-Werkzeug)
 * @param model    Item-Model-Definition im Resourcepack (assets/&lt;ns&gt;/items/&lt;pfad&gt;.json)
 * @param nameKey  Übersetzungs-Key des Namens
 * @param maxStack Stapelgröße
 * @param food     Nahrungswerte, null wenn nicht essbar
 */
public record CookeryItem(Key id, Material base, Key model, String nameKey, int maxStack, @Nullable Food food) {

    /**
     * Nahrungswerte wie im Mod.
     *
     * @param remainder Item, das nach dem Essen zurückbleibt (z. B. Schüssel), sonst null
     */
    public record Food(int nutrition, float saturation, boolean alwaysEdible, float eatSeconds, @Nullable Key remainder, List<FoodEffect> effects) {
    }

    /**
     * Effekt beim Essen. Effekte aus den Mods (Namespace kaleidoscope_*) setzt das Plugin selbst um.
     */
    public record FoodEffect(Key effect, int duration, int amplifier, float probability) {
    }
}
