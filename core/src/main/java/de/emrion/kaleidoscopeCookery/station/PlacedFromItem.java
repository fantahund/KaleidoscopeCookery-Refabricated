package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Station that takes data from the item when placed (for addons; Cookery stations are handled in the StationListener).
 */
public interface PlacedFromItem {

    /**
     * @param item the placed unit (amount 1)
     * @param face clicked face
     */
    void initFrom(ItemStack item, BlockFace face);

    /**
     * Like {@link #initFrom(ItemStack, BlockFace)} with the placing player (e.g. for a free rotation from the yaw).
     */
    default void initFrom(ItemStack item, BlockFace face, @Nullable Player placer) {
        initFrom(item, face);
    }
}
