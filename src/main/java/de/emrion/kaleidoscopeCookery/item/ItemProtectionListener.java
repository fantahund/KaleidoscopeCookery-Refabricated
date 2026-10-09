package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Mod-Items basieren auf Papier (bzw. Werkzeugen). Damit sie nicht in Vanilla-Rezepten (Karten, Bücher,
 * Reparatur ...) landen, blockiert dieser Listener alle fremden Ergebnisse mit Mod-Items.
 * Eigene Rezepte (Namespace kaleidoscope) und Umbenennen im Amboss bleiben erlaubt.
 */
public class ItemProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!VanillaRecipes.isOwn(event.getRecipe()) && containsCustom(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareInventoryResultEvent event) {
        Inventory inventory = event.getInventory();
        if (event instanceof PrepareAnvilEvent && inventory instanceof AnvilInventory anvil) {
            // Nur Umbenennen: zweiter Slot leer
            ItemStack second = anvil.getSecondItem();
            if (CookeryItems.isCustom(second) || (CookeryItems.isCustom(anvil.getFirstItem()) && second != null && !second.isEmpty())) {
                event.setResult(null);
            }
            return;
        }
        if (event instanceof PrepareSmithingEvent smithing && VanillaRecipes.isOwn(smithing.getInventory().getRecipe())) {
            return;
        }
        if (containsCustom(inventory.getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (!VanillaRecipes.isOwn(event.getRecipe()) && event.getBlock().getState(false) instanceof Crafter crafter
                && containsCustom(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    private static boolean containsCustom(ItemStack[] items) {
        for (ItemStack item : items) {
            if (CookeryItems.isCustom(item)) {
                return true;
            }
        }
        return false;
    }
}
