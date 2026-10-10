package de.emrion.kaleidoscopeCookery.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Enchantment "Quick Knife" (kaleidoscope_cookery:quick_knife, registered in KaleidoscopeCookeryBootstrap).
 * Also rules for the kitchen knives themselves: no sweep attack, not placeable as a block.
 */
public class QuickKnifeListener implements Listener {

    public static final Key KEY = Key.key("kaleidoscope_cookery", "quick_knife");

    public static @Nullable Enchantment enchantment() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(KEY);
    }

    /**
     * Level of "Quick Knife" on the item (0 = none).
     */
    public static int level(@Nullable ItemStack stack) {
        Enchantment enchantment = enchantment();
        return stack == null || enchantment == null ? 0 : stack.getEnchantmentLevel(enchantment);
    }

    /**
     * Kitchen knife = mod item whose id ends with _kitchen_knife.
     */
    public static boolean isKnife(@Nullable ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null && id.value().endsWith("kitchen_knife");
    }

    /**
     * Mod: "菜刀不能横扫之刃" - kitchen knives do no sweep attack.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && event.getDamager() instanceof LivingEntity attacker
                && attacker.getEquipment() != null && isKnife(attacker.getEquipment().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * The knives are based on the jigsaw block: ops in creative mode could otherwise place it.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isKnife(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
