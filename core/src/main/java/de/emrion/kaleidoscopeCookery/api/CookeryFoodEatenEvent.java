package de.emrion.kaleidoscopeCookery.api;

import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * A mod dish was eaten - from the hand, bite by bite from a plate or from the lunch bag.
 * Nutrition and effects have already been applied.
 */
public class CookeryFoodEatenEvent extends Event {

    public enum Source {
        HAND, PLATE_BITE, LUNCH_BAG
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack food;
    private final CookeryItem.Food values;
    private final double ratio;
    private final Source source;

    /**
     * @param food   eaten item (for plates the dish, as a copy)
     * @param values nutrition according to items.yml
     * @param ratio  factor from the quality (or bite size)
     */
    public CookeryFoodEatenEvent(Player player, ItemStack food, CookeryItem.Food values, double ratio, Source source) {
        this.player = player;
        this.food = food;
        this.values = values;
        this.ratio = ratio;
        this.source = source;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getFood() {
        return food;
    }

    public CookeryItem.Food getValues() {
        return values;
    }

    public double getRatio() {
        return ratio;
    }

    public Source getSource() {
        return source;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
