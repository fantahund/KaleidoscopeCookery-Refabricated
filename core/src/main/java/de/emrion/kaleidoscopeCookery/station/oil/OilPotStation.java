package de.emrion.kaleidoscopeCookery.station.oil;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hingestellte Ölkanne (OilPotBlock): speichert bis zu 256 Öl.
 * Rechtsklick mit Öl füllt nach, mit leerer Hand nimmt man bis zu 64 Öl heraus.
 * Abgebaut behält die Kanne ihren Inhalt.
 */
public class OilPotStation extends Station {

    private static final NamespacedKey OIL = Keys.key("oil");
    private int oil;

    public OilPotStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("oil_pot", OilItems.OIL_POT, false, 0.4f, 0.65f, true, OilPotStation::new);
    }

    public void initFrom(ItemStack item) {
        oil = OilItems.oilCount(item);
        markDirty();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        oil = state.getOrDefault(OIL, PersistentDataType.INTEGER, 0);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(OIL, PersistentDataType.INTEGER, oil);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (held.isEmpty()) {
            if (oil <= 0) {
                return false;
            }
            int amount = Math.min(oil, 64);
            ItemStack oilStack = items().create(OilItems.OIL, amount);
            if (oilStack == null) {
                return false;
            }
            player.getInventory().setItemInMainHand(oilStack);
            oil -= amount;
            world().playSound(center(), Sound.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, 1, random.nextFloat() * 0.2f + 0.8f);
            changed();
            return true;
        }
        if (OilItems.isOil(held)) {
            int add = Math.min(OilItems.OIL_POT_MAX - oil, held.getAmount());
            if (add <= 0) {
                return false;
            }
            oil += add;
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract(add);
            }
            world().playSound(center(), Sound.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, 1, random.nextFloat() * 0.2f + 0.4f);
            changed();
            return true;
        }
        return false;
    }

    private void changed() {
        markDirty();
        updateDisplay();
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        ItemStack pot = items().create(OilItems.OIL_POT, 1);
        if (pot != null) {
            OilItems.setOilCount(pot, oil);
        }
        return pot;
    }

    @Override
    public void updateDisplay() {
        showPart("body", Key.key("kaleidoscope_cookery", oil > 0 ? "block/oil_pot_with_oil" : "block/oil_pot"), blockTransform(0));
    }

    @Override
    public String describe() {
        return "Ölkanne (" + oil + " Öl)";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        return Lang.raw("block.kaleidoscope_cookery.oil_pot").color(NamedTextColor.WHITE).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Lang.raw("tooltip.kaleidoscope_cookery.oil_pot.count", oil).color(NamedTextColor.YELLOW));
    }
}
