package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.station.oil.OilBlockStation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;

/**
 * Öl und Ölblock basieren auf Holzkohle und brennen deshalb schon wie Kohle. Der Ölblock brennt wie im Mod so lange
 * wie ein Kohleblock (zehnfache Dauer, Räucherofen und Schmelzofen halbieren sie wie bei Vanilla schon vorher).
 */
public class FuelListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (OilBlockStation.ITEM.equals(CookeryItems.idOf(event.getFuel()))) {
            event.setBurnTime(event.getBurnTime() * 10);
        }
    }
}
