package de.emrion.kaleidoscopeCookery.item;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Teig: Mehl im Wasser wird zu Teig (FlourItem#onEntityItemUpdate), Teig 1,5 s gedrückt halten wird zu Nudeln (RawDoughItem).
 */
public class DoughListener implements Listener {

    public static final Key FLOUR = Key.key("kaleidoscope_cookery", "flour");
    public static final Key RAW_DOUGH = Key.key("kaleidoscope_cookery", "raw_dough");
    public static final Key RAW_NOODLES = Key.key("kaleidoscope_cookery", "raw_noodles");
    /** RawDoughItem#MIN_USE_DURATION */
    private static final int MIN_USE_TICKS = 30;
    private static final Sound TRANSFORM = Sound.sound(Key.key("kaleidoscope_cookery", "item.dough_transform"), Sound.Source.PLAYER, 1, 1);

    private final de.emrion.kaleidoscopeCookery.KaleidoscopeCookery plugin;
    /** Mehl, das als Item in der Welt liegt */
    private final Set<Item> flour = new HashSet<>();

    public DoughListener(de.emrion.kaleidoscopeCookery.KaleidoscopeCookery plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkFlour, 10, 10);
    }

    // ---- Mehl im Wasser ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (FLOUR.equals(CookeryItems.idOf(event.getEntity().getItemStack()))) {
            flour.add(event.getEntity());
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item && FLOUR.equals(CookeryItems.idOf(item.getItemStack()))) {
                flour.add(item);
            }
        }
    }

    private void checkFlour() {
        flour.removeIf(item -> {
            if (!item.isValid() || !FLOUR.equals(CookeryItems.idOf(item.getItemStack()))) {
                return true;
            }
            if (!item.isInWater()) {
                return false;
            }
            ItemStack dough = plugin.getItems().create(RAW_DOUGH, item.getItemStack().getAmount());
            if (dough != null) {
                item.setItemStack(dough);
            }
            return true;
        });
    }

    // ---- Teig ziehen ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        if (!RAW_DOUGH.equals(CookeryItems.idOf(event.getItem())) || event.getTicksHeldFor() < MIN_USE_TICKS) {
            return;
        }
        Player player = event.getPlayer();
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            ItemStack held = player.getInventory().getItem(hand);
            if (RAW_DOUGH.equals(CookeryItems.idOf(held))) {
                ItemStack noodles = plugin.getItems().create(RAW_NOODLES, held.getAmount());
                player.getInventory().setItem(hand, null);
                if (noodles != null) {
                    for (ItemStack rest : player.getInventory().addItem(noodles).values()) {
                        player.getWorld().dropItem(player.getLocation(), rest);
                    }
                }
                player.playSound(TRANSFORM, Sound.Emitter.self());
                return;
            }
        }
    }
}
