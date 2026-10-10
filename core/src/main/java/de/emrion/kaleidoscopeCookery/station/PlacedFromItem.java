package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * Station that takes data from the item when placed (for addons; Cookery stations are handled in the StationListener).
 */
public interface PlacedFromItem {

    /**
     * @param item the placed unit (amount 1)
     * @param face clicked face
     */
    void initFrom(ItemStack item, BlockFace face);
}
