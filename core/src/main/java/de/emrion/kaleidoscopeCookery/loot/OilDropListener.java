package de.emrion.kaleidoscopeCookery.loot;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import net.kyori.adventure.key.Key;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Oil from pigs &amp; co. when they are killed with a cutting tool
 * (mod: loot tables kaleidoscope_cookery:pig etc. with a kitchen knife condition), and donkey meat from donkeys
 * (Fabric fork: minecraft:entities/donkey, 2 rolls of 1-2 each with a kitchen knife).
 */
public class OilDropListener implements Listener {

    private static final Key OIL = Key.key("kaleidoscope_cookery", "oil");
    private static final Key RAW_DONKEY_MEAT = Key.key("kaleidoscope_cookery", "raw_donkey_meat");

    private final KaleidoscopeCookery plugin;

    public OilDropListener(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        CookeryConfig config = plugin.getCookeryConfig();
        CookeryConfig.OilDrops drops = config.oilDrops();
        Player killer = event.getEntity().getKiller();
        if (killer == null || !config.cutting().isTool(killer.getInventory().getItemInMainHand())) {
            return;
        }
        ItemStack tool = killer.getInventory().getItemInMainHand();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (event.getEntityType() == org.bukkit.entity.EntityType.DONKEY) {
            int looting = tool.getEnchantmentLevel(Enchantment.LOOTING);
            int meat = 0;
            for (int roll = 0; roll < 2; roll++) {
                meat += random.nextInt(1, 3) + Math.round(looting * random.nextFloat());
            }
            ItemStack stack = plugin.getItems().create(RAW_DONKEY_MEAT, meat);
            if (stack != null) {
                event.getDrops().add(stack);
            }
            return;
        }
        if (!drops.enabled() || !drops.entities().contains(event.getEntityType())) {
            return;
        }
        int count = random.nextInt(drops.min(), drops.max() + 1);
        // enchanted_count_increase: 0..1 extra per level of Looting
        int looting = tool.getEnchantmentLevel(Enchantment.LOOTING);
        count += Math.round(looting * random.nextFloat());
        if (count <= 0) {
            return;
        }
        ItemStack oil = plugin.getItems().create(OIL, count);
        if (oil != null) {
            event.getDrops().add(oil);
        }
    }
}
