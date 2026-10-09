package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.station.Station;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Kochbuch-Item: Rechtsklick öffnet das Kochbuch, auf eine Station direkt deren Seite.
 */
public class CookbookListener implements Listener {

    public static final Key ITEM = Key.key("kaleidoscope", "cookbook");

    private final KaleidoscopeCookery plugin;

    public CookbookListener(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !Cookbook.isCookbook(event.getItem())
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable() && !event.getPlayer().isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().hasPermission(Permissions.USE)) {
            plugin.getCookbook().open(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseStation(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !Cookbook.isCookbook(player.getInventory().getItemInMainHand())) {
            return;
        }
        Station station = plugin.getStations().byEntity(event.getRightClicked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission(Permissions.USE)) {
            return;
        }
        Category category = Cookbook.categoryOf(station.context().type().item());
        if (category != null) {
            plugin.getCookbook().open(player, category);
        } else {
            plugin.getCookbook().open(player);
        }
    }
}
