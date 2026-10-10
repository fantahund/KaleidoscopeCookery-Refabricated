package de.emrion.kaleidoscopeCookery.station.oil;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Öl in Items: Ölkanne (OilPotItem, bis 256 Öl) und Pfannenwender mit Öl (KitchenShovelItem#hasOil).
 */
@SuppressWarnings("UnstableApiUsage")
public final class OilItems {

    public static final Key OIL = Key.key("kaleidoscope_cookery", "oil");
    public static final Key OIL_POT = Key.key("kaleidoscope_cookery", "oil_pot");
    public static final Key KITCHEN_SHOVEL = Key.key("kaleidoscope_cookery", "kitchen_shovel");
    public static final int OIL_POT_MAX = 256;
    private static final NamespacedKey OIL_COUNT = Keys.key("oil_count");
    private static final NamespacedKey HAS_OIL = Keys.key("has_oil");
    private static final String HAS_OIL_PROPERTY = "kaleidoscope_cookery:has_oil";

    private OilItems() {
    }

    public static boolean isOil(@Nullable ItemStack stack) {
        return OIL.equals(CookeryItems.idOf(stack));
    }

    public static boolean isOilPot(@Nullable ItemStack stack) {
        return OIL_POT.equals(CookeryItems.idOf(stack));
    }

    public static boolean isShovel(@Nullable ItemStack stack) {
        return KITCHEN_SHOVEL.equals(CookeryItems.idOf(stack));
    }

    // ---- Ölkanne ----

    public static int oilCount(ItemStack oilPot) {
        return oilPot.getPersistentDataContainer().getOrDefault(OIL_COUNT, PersistentDataType.INTEGER, 0);
    }

    public static void setOilCount(ItemStack oilPot, int count) {
        int clamped = Math.clamp(count, 0, OIL_POT_MAX);
        oilPot.editPersistentDataContainer(pdc -> pdc.set(OIL_COUNT, PersistentDataType.INTEGER, clamped));
        ModelProperties.set(oilPot, HAS_OIL_PROPERTY, clamped > 0 ? 1 : 0);
        String key = clamped > 0 ? "tooltip.kaleidoscope_cookery.oil_pot.count" : "tooltip.kaleidoscope_cookery.oil_pot.empty";
        Component line = Lang.raw(key, clamped);
        for (java.util.function.BiFunction<ItemStack, Integer, @Nullable Component> provider : LORE_PROVIDERS) {
            Component replaced = provider.apply(oilPot, clamped);
            if (replaced != null) {
                line = replaced;
                break;
            }
        }
        oilPot.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(Text.noItalic(line.color(NamedTextColor.GRAY)))));
    }

    private static final List<java.util.function.BiFunction<ItemStack, Integer, @Nullable Component>> LORE_PROVIDERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Addons (Grilling: Ölsorte) ersetzen die Mengen-Zeile der Ölkanne: (Kanne, Menge) -> Zeile oder null.
     */
    public static void addLoreProvider(java.util.function.BiFunction<ItemStack, Integer, @Nullable Component> provider) {
        LORE_PROVIDERS.add(provider);
    }

    // ---- Pfannenwender ----

    public static boolean shovelHasOil(ItemStack shovel) {
        return shovel.getPersistentDataContainer().getOrDefault(HAS_OIL, PersistentDataType.BOOLEAN, false);
    }

    public static void setShovelOil(ItemStack shovel, boolean hasOil) {
        shovel.editPersistentDataContainer(pdc -> pdc.set(HAS_OIL, PersistentDataType.BOOLEAN, hasOil));
        ModelProperties.set(shovel, HAS_OIL_PROPERTY, hasOil ? 1 : 0);
    }

    /**
     * Ein Öl aus Öl-Item, Ölkanne oder Pfannenwender nehmen (PotBlockEntity#onPlaceOil).
     *
     * @return true, wenn Öl da war
     */
    public static boolean takeOne(ItemStack stack, boolean creative) {
        if (isOil(stack)) {
            if (!creative) {
                stack.subtract();
            }
            return true;
        }
        if (isShovel(stack) && shovelHasOil(stack)) {
            setShovelOil(stack, false);
            return true;
        }
        if (isOilPot(stack) && oilCount(stack) > 0) {
            setOilCount(stack, oilCount(stack) - 1);
            return true;
        }
        return false;
    }
}
