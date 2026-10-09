package de.emrion.kaleidoscopeCookery.food;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla-Essen abschwächen: Jedes Vanilla-Essen bekommt die food-Komponente mit den eingestellten Prozenten –
 * überall, wo es entsteht oder auftaucht (Drops, Werkbank, Ofen, Loot, Handel, Inventare). Weil alle Stapel dieselben
 * Werte bekommen, stapeln sie weiter normal, und Mods wie AppleSkin zeigen die echten Werte an.
 * Ändert sich die Konfiguration, werden die Werte beim nächsten Kontakt angepasst; 100 % = Vanilla-Komponente.
 */
@SuppressWarnings("UnstableApiUsage")
public class VanillaFoodAdjuster implements Listener {

    private static final int INVENTORY_INTERVAL = 100;

    private final KaleidoscopeCookery plugin;
    private BukkitTask task;

    public VanillaFoodAdjuster(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            adjust(player.getInventory());
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                adjust(player.getInventory());
            }
        }, INVENTORY_INTERVAL, INVENTORY_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Werte, die Vanilla diesem Item-Typ gibt (ohne Anpassungen am Stapel), null bei Nicht-Essen.
     */
    public static @Nullable FoodProperties vanillaFood(Material material) {
        ItemType type = material.asItemType();
        return type == null ? null : type.getDefaultData(DataComponentTypes.FOOD);
    }

    /**
     * food-Komponente des Stapels auf den eingestellten Wert bringen.
     *
     * @return true, wenn der Stapel geändert wurde
     */
    public boolean adjust(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || CookeryItems.isCustom(stack)) {
            return false;
        }
        FoodProperties vanilla = vanillaFood(stack.getType());
        if (vanilla == null) {
            return false;
        }
        CookeryConfig.Food config = plugin.getCookeryConfig().food();
        int percent = config.vanillaNerfEnabled() ? config.vanillaPercent(stack.getType()) : 100;
        FoodProperties current = stack.getData(DataComponentTypes.FOOD);
        if (percent >= 100) {
            if (stack.isDataOverridden(DataComponentTypes.FOOD)) {
                stack.resetData(DataComponentTypes.FOOD);
                return true;
            }
            return false;
        }
        FoodProperties target = FoodProperties.food()
                .nutrition((int) Math.round(vanilla.nutrition() * percent / 100.0))
                .saturation(vanilla.saturation() * percent / 100f)
                .canAlwaysEat(vanilla.canAlwaysEat())
                .build();
        if (current != null && current.nutrition() == target.nutrition() && Float.compare(current.saturation(), target.saturation()) == 0
                && current.canAlwaysEat() == target.canAlwaysEat()) {
            return false;
        }
        stack.setData(DataComponentTypes.FOOD, target);
        return true;
    }

    public void adjust(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (adjust(contents[slot])) {
                inventory.setItem(slot, contents[slot]);
            }
        }
    }

    // ---- Wo Essen entsteht oder auftaucht ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (adjust(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (adjust(stack)) {
            event.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (adjust(result)) {
            inventory.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        ItemStack result = event.getResult();
        if (adjust(result)) {
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        ItemStack result = event.getResult();
        if (adjust(result)) {
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        boolean changed = false;
        for (ItemStack stack : loot) {
            changed |= adjust(stack);
        }
        if (changed) {
            event.setLoot(loot);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        adjust(event.getInventory());
        if (event.getInventory().getHolder() instanceof org.bukkit.inventory.Merchant merchant) {
            adjustMerchant(merchant);
        } else if (event.getView().getTopInventory() instanceof org.bukkit.inventory.MerchantInventory merchantInventory) {
            adjustMerchant(merchantInventory.getMerchant());
        }
    }

    private void adjustMerchant(org.bukkit.inventory.Merchant merchant) {
        List<MerchantRecipe> recipes = new ArrayList<>(merchant.getRecipes());
        boolean changed = false;
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);
            ItemStack result = recipe.getResult().clone();
            if (adjust(result)) {
                MerchantRecipe copy = new MerchantRecipe(result, recipe.getUses(), recipe.getMaxUses(), recipe.hasExperienceReward(), recipe.getVillagerExperience(),
                        recipe.getPriceMultiplier(), recipe.getDemand(), recipe.getSpecialPrice(), recipe.shouldIgnoreDiscounts());
                copy.setIngredients(recipe.getIngredients());
                recipes.set(i, copy);
                changed = true;
            }
        }
        if (changed) {
            merchant.setRecipes(recipes);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        ItemStack cursor = event.getCursor();
        if (adjust(cursor)) {
            event.setCursor(cursor);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        adjust(event.getPlayer().getInventory());
        adjust(event.getPlayer().getEnderChest());
    }
}
