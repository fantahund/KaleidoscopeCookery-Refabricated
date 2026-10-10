package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * Station, die beim Setzen Daten aus dem Item übernimmt (für Addons; Cookery-Stationen stehen im StationListener).
 */
public interface PlacedFromItem {

    /**
     * @param item die gesetzte Einheit (Menge 1)
     * @param face angeklickte Fläche
     */
    void initFrom(ItemStack item, BlockFace face);
}
