package de.emrion.kaleidoscopeCookery.station.trash;

import de.emrion.kaleidoscopeCookery.station.StationManager;
import de.emrion.kaleidoscopeCookery.station.seat.Seats;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TrashCanBlock#fallOn: if a player lands on the trash can after falling more than one block, they climb in.
 * The fall distance is reset on landing, so we remember it from the previous movement.
 */
public class TrashCanListener implements Listener {

    private final StationManager stations;
    private final Seats seats;
    private final Map<UUID, Float> fallDistance = new HashMap<>();

    public TrashCanListener(StationManager stations, Seats seats) {
        this.stations = stations;
        this.seats = seats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        float before = fallDistance.getOrDefault(player.getUniqueId(), 0f);
        float now = player.getFallDistance();
        if (now > 0) {
            fallDistance.put(player.getUniqueId(), now);
        } else {
            fallDistance.remove(player.getUniqueId());
        }
        if (Math.max(before, now) <= 1 || event.getTo().getY() >= event.getFrom().getY() || player.isInsideVehicle() || player.isFlying()) {
            return;
        }
        double y = event.getTo().getY();
        Block below = event.getTo().clone().subtract(0, 0.05, 0).getBlock();
        // Feet on the top of the can
        if (y - below.getY() > 1.05 || !(stations.at(below) instanceof TrashCanStation trashCan)) {
            return;
        }
        fallDistance.remove(player.getUniqueId());
        trashCan.onFallOn(player, seats);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fallDistance.remove(event.getPlayer().getUniqueId());
    }
}
