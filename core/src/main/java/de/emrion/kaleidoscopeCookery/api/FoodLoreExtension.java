package de.emrion.kaleidoscopeCookery.api;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Additional lines in the description of mod items (after description, maxim and quality).
 * Register via FoodService#addLoreExtension.
 */
@FunctionalInterface
public interface FoodLoreExtension {

    /**
     * @param world the player's world, null if unknown
     * @param lore  append lines here
     */
    void extend(ItemStack stack, @Nullable World world, List<Component> lore);
}
