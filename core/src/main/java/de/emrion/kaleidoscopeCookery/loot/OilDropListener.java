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
 * Öl von Schweinen &amp; Co., wenn sie mit einem Schneidewerkzeug getötet werden
 * (Mod: Loot-Tabellen kaleidoscope_cookery:pig usw. mit Küchenmesser-Bedingung).
 */
public class OilDropListener implements Listener {

    private static final Key OIL = Key.key("kaleidoscope_cookery", "oil");

    private final KaleidoscopeCookery plugin;

    public OilDropListener(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        CookeryConfig config = plugin.getCookeryConfig();
        CookeryConfig.OilDrops drops = config.oilDrops();
        Player killer = event.getEntity().getKiller();
        if (!drops.enabled() || killer == null || !drops.entities().contains(event.getEntityType())) {
            return;
        }
        ItemStack tool = killer.getInventory().getItemInMainHand();
        if (!config.cutting().isTool(tool)) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = random.nextInt(drops.min(), drops.max() + 1);
        // enchanted_count_increase: pro Stufe Plünderung 0..1 zusätzlich
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
