package de.emrion.kaleidoscopeCookery.station.teapot;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import net.kyori.adventure.key.Key;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Placed clay pot milk tea (ClayPotMilkTeaBlock): pure decoration, right-click picks it up again.
 * The item is what you drink (removes harmful effects, see FoodService).
 */
public class MilkTeaStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "clay_pot_milk_tea");

    public MilkTeaStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("clay_pot_milk_tea", ITEM, false, 0.65f, 0.65f, false, MilkTeaStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
    }

    @Override
    protected void save(PersistentDataContainer state) {
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack tea = items().create(ITEM, 1);
        if (tea == null) {
            return false;
        }
        for (ItemStack leftover : player.getInventory().addItem(tea).values()) {
            world().dropItem(player.getLocation(), leftover);
        }
        world().playSound(center(), Sound.BLOCK_DECORATED_POT_BREAK, SoundCategory.BLOCKS, 1, 1);
        context.manager().remove(this);
        return true;
    }

    @Override
    public void updateDisplay() {
        showPart("pot", Key.key("kaleidoscope_cookery", "block/clay_pot_milk_tea"), blockTransform(0));
    }
}
