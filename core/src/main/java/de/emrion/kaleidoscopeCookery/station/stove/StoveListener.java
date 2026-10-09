package de.emrion.kaleidoscopeCookery.station.stove;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Brennende Projektile (z. B. Pfeile mit Flamme) zünden den Herd an (StoveBlock#onProjectileHit).
 */
public class StoveListener implements Listener {

    private final StationManager stations;

    public StoveListener(StationManager stations) {
        this.stations = stations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Station station = null;
        if (event.getHitBlock() != null) {
            station = stations.at(event.getHitBlock());
        } else if (event.getHitEntity() != null) {
            station = stations.byEntity(event.getHitEntity());
        }
        if (station instanceof StoveStation stove && event.getEntity().getFireTicks() > 0) {
            stove.onBurningProjectile();
        }
    }
}
