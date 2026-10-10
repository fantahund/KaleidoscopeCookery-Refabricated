package de.emrion.kaleidoscopeCookery.api;

import de.emrion.kaleidoscopeCookery.station.Station;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * A finished dish is taken out of a station (wok, stockpot), right before the player receives it.
 * The item may be modified.
 */
public class DishServedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Station station;
    private final ItemStack dish;

    public DishServedEvent(Player player, Station station, ItemStack dish) {
        this.player = player;
        this.station = station;
        this.dish = dish;
    }

    public Player getPlayer() {
        return player;
    }

    public Station getStation() {
        return station;
    }

    /**
     * Type of the station ("pot", "stockpot" ...).
     */
    public String getStationType() {
        return station.context().type().id();
    }

    public ItemStack getDish() {
        return dish;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
