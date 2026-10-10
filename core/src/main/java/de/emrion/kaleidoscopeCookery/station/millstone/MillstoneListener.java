package de.emrion.kaleidoscopeCookery.station.millstone;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * MillstoneEntityEvent: a harnessed animal that takes damage is released.
 */
public class MillstoneListener implements Listener {

    private final StationManager stations;

    public MillstoneListener(StationManager stations) {
        this.stations = stations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        for (Station station : stations.loaded()) {
            if (station instanceof MillstoneStation millstone && millstone.hasEntity() && millstone.isBoundTo(event.getEntity())) {
                millstone.unbind();
                return;
            }
        }
    }
}
