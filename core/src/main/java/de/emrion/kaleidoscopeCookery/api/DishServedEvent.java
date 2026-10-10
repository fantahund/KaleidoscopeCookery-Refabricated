package de.emrion.kaleidoscopeCookery.api;

import de.emrion.kaleidoscopeCookery.station.Station;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Ein fertiges Gericht wird aus einer Station genommen (Wok, Suppentopf), kurz bevor der Spieler es bekommt.
 * Das Item darf verändert werden.
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
     * Typ der Station ("pot", "stockpot" ...).
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
