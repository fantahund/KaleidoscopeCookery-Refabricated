package de.emrion.kaleidoscopeCookery.item;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An item from the mods (definition from items.yml).
 *
 * @param id       z. B. kaleidoscope_cookery:tomato
 * @param base     base material (paper; spatula: iron shovel; kitchen knives: jigsaw block)
 * @param prototype vanilla item whose components (damage, durability ...) are copied, otherwise null
 * @param model    item model definition in the resource pack (assets/&lt;ns&gt;/items/&lt;path&gt;.json)
 * @param nameKey  translation key of the name
 * @param maxStack stack size
 * @param maxDamage durability, 0 = default of the base item
 * @param tooltipKey description from the mod's language file, otherwise null
 * @param food     food values, null if not edible
 * @param plate    dish that can be placed as a plate and eaten bite by bite, otherwise null
 * @param sneakToPlace only place while sneaking (tea cups, skewer plate - otherwise drink or eat)
 * @param wearable clothing, otherwise null
 * @param repair   repair material in the anvil instead of the prototype's, otherwise null
 * @param attributes keep the prototype's attack attributes (sickle: no)
 * @param useAnimation hold with this animation (dough: bow), otherwise null
 * @param maximLines lines of the maxim (tooltip.*.maxim.lineN), 0 = none
 */
public record CookeryItem(Key id, Material base, @Nullable Material prototype, Key model, String nameKey, @Nullable String tooltipKey, int maxStack, int maxDamage, @Nullable Food food,
                          @Nullable Plate plate, boolean sneakToPlace, @Nullable Wearable wearable, @Nullable Material repair, boolean attributes,
                          @Nullable String useAnimation, int maximLines) {

    /**
     * Clothing (straw hat, farmer clothes).
     *
     * @param asset equipment asset for the armour layer; null = item model on the head (straw hat)
     */
    public record Wearable(org.bukkit.inventory.EquipmentSlot slot, @Nullable Key asset, int armor, boolean unbreakable) {
    }

    /**
     * Plated dish (FoodBiteBlock in the mod).
     *
     * @param food   nutrition per bite
     * @param loot   remains after the last bite (bowl, bone ...)
     * @param wide   1x2 dish (second half to the left)
     * @param models models per part ("main" or "left"/"right") and bite stage
     */
    public record Plate(int maxBites, Food food, List<Key> loot, boolean wide, boolean large, java.util.Map<String, List<Key>> models) {
    }

    /**
     * Food values as in the mod.
     *
     * @param remainder item that remains after eating (e.g. bowl), otherwise null
     * @param drink     drink (drinking animation; without nutrition no food component)
     * @param sound     sound when eating instead of the normal one (e.g. skewers from Grilling), otherwise null
     */
    public record Food(int nutrition, float saturation, boolean alwaysEdible, float eatSeconds, @Nullable Key remainder, List<FoodEffect> effects,
                       boolean drink, @Nullable Key sound) {

        /** Nutrition and saturation scaled to percent (config food.dishes) */
        public Food scaled(int percent) {
            return new Food(Math.round(nutrition * percent / 100f), saturation * percent / 100f, alwaysEdible, eatSeconds, remainder, effects, drink, sound);
        }
    }

    /**
     * Copy with food values (also per bite of a plate) scaled to percent.
     */
    public CookeryItem withFoodPercent(int percent) {
        Plate scaledPlate = plate == null ? null
                : new Plate(plate.maxBites(), plate.food().scaled(percent), plate.loot(), plate.wide(), plate.large(), plate.models());
        return new CookeryItem(id, base, prototype, model, nameKey, tooltipKey, maxStack, maxDamage, food == null ? null : food.scaled(percent),
                scaledPlate, sneakToPlace, wearable, repair, attributes, useAnimation, maximLines);
    }

    /**
     * Effect when eating. The plugin implements effects from the mods (namespace kaleidoscope_*) itself.
     */
    public record FoodEffect(Key effect, int duration, int amplifier, float probability) {
    }
}
