package de.emrion.kaleidoscopeCookery.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Verzauberung "Schnelles Messer" (kaleidoscope_cookery:quick_knife, registriert in KaleidoscopeCookeryBootstrap).
 * Außerdem Regeln für die Küchenmesser selbst: kein Schwungangriff, kein Wissensbuch-Rechtsklick.
 */
public class QuickKnifeListener implements Listener {

    public static final Key KEY = Key.key("kaleidoscope_cookery", "quick_knife");

    public static @Nullable Enchantment enchantment() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(KEY);
    }

    /**
     * Stufe von "Schnelles Messer" auf dem Item (0 = keine).
     */
    public static int level(@Nullable ItemStack stack) {
        Enchantment enchantment = enchantment();
        return stack == null || enchantment == null ? 0 : stack.getEnchantmentLevel(enchantment);
    }

    /**
     * Küchenmesser = Mod-Item, dessen ID auf _kitchen_knife endet.
     */
    public static boolean isKnife(@Nullable ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null && id.value().endsWith("kitchen_knife");
    }

    /**
     * Mod: "菜刀不能横扫之刃" – Küchenmesser machen keinen Schwungangriff.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && event.getDamager() instanceof LivingEntity attacker
                && attacker.getEquipment() != null && isKnife(attacker.getEquipment().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    /**
     * Basis der Messer ist das Wissensbuch: dessen Rechtsklick (verbraucht sich) verhindern.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && isKnife(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
}
