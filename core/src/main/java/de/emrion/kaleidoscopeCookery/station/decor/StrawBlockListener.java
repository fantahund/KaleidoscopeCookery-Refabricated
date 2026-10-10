package de.emrion.kaleidoscopeCookery.station.decor;

import de.emrion.kaleidoscopeCookery.station.StationManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * No fall damage on straw blocks (StrawBlocks#fallOn does not call super.fallOn).
 */
public class StrawBlockListener implements Listener {

    private final StationManager stations;

    public StrawBlockListener(StationManager stations) {
        this.stations = stations;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (stations.at(entity.getLocation().subtract(0, 0.2, 0).getBlock()) instanceof StrawBlockStation straw) {
            float fallDistance = entity.getFallDistance();
            event.setCancelled(true);
            straw.onFallOn(entity, fallDistance);
        }
    }
}
