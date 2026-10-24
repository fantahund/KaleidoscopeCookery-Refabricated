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
 * Mod items are based on paper (or tools). So that they do not end up in vanilla recipes (maps, books,
 * repairing ...), this listener blocks all foreign results containing mod items.
 * Own recipes (namespace kaleidoscope) and renaming in the anvil stay allowed.
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
            ItemStack first = anvil.getFirstItem();
            ItemStack second = anvil.getSecondItem();
            if (isTool(first)) {
                // Tools (kitchen knives, spatula): books, repairing and the same tool allowed, other mod items not
                if (CookeryItems.isCustom(second) && !CookeryItems.typeOf(second).equals(CookeryItems.typeOf(first))) {
                    event.setResult(null);
                }
                return;
            }
            // Otherwise only renaming: second slot empty
            if (CookeryItems.isCustom(second) || (CookeryItems.isCustom(first) && second != null && !second.isEmpty())) {
                event.setResult(null);
            }
            return;
        }
        if (event instanceof PrepareSmithingEvent smithing && VanillaRecipes.isOwn(smithing.getInventory().getRecipe())) {
            return;
        }
        if (inventory instanceof org.bukkit.inventory.GrindstoneInventory grindstone && grindstoneAllowed(grindstone)) {
            return; // disenchanting a kitchen knife or sickle (or two of the same kind) like any vanilla tool
        }
        if (containsCustom(inventory.getContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        // The placeholder recipes (any_items) only exist for the crafting table's written recipe; in a crafter they would
        // turn mod items into plain paper
        if (VanillaRecipes.isAnyItemsRecipe(event.getRecipe())) {
            event.setCancelled(true);
            return;
        }
        if (!VanillaRecipes.isOwn(event.getRecipe()) && event.getBlock().getState(false) instanceof Crafter crafter
                && containsCustom(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    /**
     * Mod item with a vanilla tool as base (not paper).
     */
    private static boolean isTool(ItemStack stack) {
        return CookeryItems.isCustom(stack) && stack.getType() != CookeryItems.BASE_MATERIAL;
    }

    /**
     * Villager trades only check the item type: mod items (paper) would count as paper (24 paper = 1 emerald at the
     * librarian). Taking the result with a mod item in a payment slot is blocked.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getInventory() instanceof org.bukkit.inventory.MerchantInventory merchant && event.getRawSlot() == 2
                && (CookeryItems.isCustom(merchant.getItem(0)) || CookeryItems.isCustom(merchant.getItem(1)))) {
            event.setCancelled(true);
        }
    }

    /** Only mod tools in the grindstone, and two of them only of the same kind */
    private static boolean grindstoneAllowed(org.bukkit.inventory.GrindstoneInventory grindstone) {
        ItemStack upper = grindstone.getUpperItem();
        ItemStack lower = grindstone.getLowerItem();
        boolean upperEmpty = upper == null || upper.isEmpty();
        boolean lowerEmpty = lower == null || lower.isEmpty();
        if (!upperEmpty && CookeryItems.isCustom(upper) && !isTool(upper) || !lowerEmpty && CookeryItems.isCustom(lower) && !isTool(lower)) {
            return false;
        }
        if (upperEmpty || lowerEmpty) {
            return true;
        }
        return CookeryItems.isCustom(upper) == CookeryItems.isCustom(lower)
                && (!CookeryItems.isCustom(upper) || CookeryItems.typeOf(upper).equals(CookeryItems.typeOf(lower)));
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
