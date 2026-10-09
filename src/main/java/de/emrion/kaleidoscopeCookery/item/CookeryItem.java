package de.emrion.kaleidoscopeCookery.item;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Ein Item aus den Mods (Definition aus items.yml).
 *
 * @param id       z. B. kaleidoscope_cookery:tomato
 * @param base     Basis-Material (Papier; Pfannenwender: Eisenschaufel; Küchenmesser: Verbundblock)
 * @param prototype Vanilla-Item, dessen Komponenten (Schaden, Haltbarkeit ...) übernommen werden, sonst null
 * @param model    Item-Model-Definition im Resourcepack (assets/&lt;ns&gt;/items/&lt;pfad&gt;.json)
 * @param nameKey  Übersetzungs-Key des Namens
 * @param maxStack Stapelgröße
 * @param maxDamage Haltbarkeit, 0 = Standard des Basis-Items
 * @param tooltipKey Beschreibung aus der Mod-Sprachdatei, sonst null
 * @param food     Nahrungswerte, null wenn nicht essbar
 * @param plate    Gericht, das man als Teller hinstellen und bissweise essen kann, sonst null
 */
public record CookeryItem(Key id, Material base, @Nullable Material prototype, Key model, String nameKey, @Nullable String tooltipKey, int maxStack, int maxDamage, @Nullable Food food,
                          @Nullable Plate plate) {

    /**
     * Teller-Gericht (FoodBiteBlock im Mod).
     *
     * @param food   Nährwert pro Biss
     * @param loot   bleibt nach dem letzten Biss übrig (Schüssel, Knochen ...)
     * @param wide   1x2-Gericht (zweite Hälfte links daneben)
     * @param models Modelle je Teil ("main" bzw. "left"/"right") und Biss-Stufe
     */
    public record Plate(int maxBites, Food food, List<Key> loot, boolean wide, java.util.Map<String, List<Key>> models) {
    }

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
