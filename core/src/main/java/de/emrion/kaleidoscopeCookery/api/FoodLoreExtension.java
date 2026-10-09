package de.emrion.kaleidoscopeCookery.api;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Zusätzliche Zeilen in der Beschreibung von Mod-Items (nach Beschreibung, Spruch und Qualität), z. B. "noch 0:45 heiß".
 * Anmelden über FoodService#addLoreExtension.
 */
@FunctionalInterface
public interface FoodLoreExtension {

    /**
     * @param world Welt des Spielers, null wenn unbekannt
     * @param lore  hier Zeilen anhängen
     */
    void extend(ItemStack stack, @Nullable World world, List<Component> lore);
}
