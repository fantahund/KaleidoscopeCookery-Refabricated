package de.emrion.kaleidoscopeCookery.station.seat;

import de.emrion.kaleidoscopeCookery.util.Keys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Sitzplätze (SitEntity im Mod): ein unsichtbares, nicht gespeichertes Display, auf dem der Spieler reitet.
 * Absteigen (Schleichen), Ausloggen oder Abbauen entfernt den Sitz.
 */
public class Seats implements Listener {

    private final Plugin plugin;

    public Seats(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Spieler hinsetzen.
     *
     * @param location Sitzhöhe und Blickrichtung
     * @param kind     Art des Sitzes (z. B. "trash_can"), steht im PDC
     */
    public void sit(Player player, Location location, String kind) {
        if (player.isInsideVehicle()) {
            return;
        }
        ItemDisplay seat = location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(Keys.SEAT, PersistentDataType.STRING, kind);
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
        }
    }

    public static boolean isSeat(@Nullable Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(Keys.SEAT, PersistentDataType.STRING);
    }

    /**
     * Gibt es an dieser Sitzposition schon einen Sitz?
     */
    public static boolean isOccupied(Location seat) {
        return !seat.getWorld().getNearbyEntities(seat, 0.3, 0.3, 0.3, Seats::isSeat).isEmpty();
    }

    /**
     * Sitze im Block entfernen (Station abgebaut).
     */
    public static void removeAt(World world, Location blockCorner) {
        world.getNearbyEntities(blockCorner.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, Seats::isSeat).forEach(Entity::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (isSeat(event.getDismounted())) {
            Entity seat = event.getDismounted();
            // Nach dem Absteigen etwas anheben, damit man nicht im Block steckt
            Entity rider = event.getEntity();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                seat.remove();
                if (rider.isValid()) {
                    rider.teleport(rider.getLocation().add(0, 0.5, 0));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (isSeat(vehicle)) {
            vehicle.eject();
            vehicle.remove();
        }
    }

    public void removeAll() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isSeat(display)) {
                    display.eject();
                    display.remove();
                }
            }
        }
    }
}
