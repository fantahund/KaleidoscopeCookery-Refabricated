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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enamel basin (EnamelBasinBlock): up to 32 oil, lid via right-click with an empty hand.
 * Oil goes in; the spatula takes one oil or returns it. Knocking with a stick only makes noise.
 */
public class EnamelBasinStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "enamel_basin");
    private static final int MAX_OIL = 32;
    private static final NamespacedKey OIL = Keys.key("oil");
    private static final NamespacedKey LID = Keys.key("lid");

    private int oil;
    private boolean lid;

    public EnamelBasinStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("enamel_basin", ITEM, false, 0.7f, 0.4f, true, EnamelBasinStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        oil = state.getOrDefault(OIL, PersistentDataType.INTEGER, 0);
        lid = state.getOrDefault(LID, PersistentDataType.BOOLEAN, false);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(OIL, PersistentDataType.INTEGER, oil);
        state.set(LID, PersistentDataType.BOOLEAN, lid);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.STICK) {
            world().playSound(center(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.BLOCKS, 2, 0.6f + ThreadLocalRandom.current().nextFloat() * 0.2f);
            return true;
        }
        if (lid) {
            lid = false;
            world().playSound(center(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.BLOCKS, 0.8f, 0.8f);
            changed();
            return true;
        }
        if (held.isEmpty()) {
            lid = true;
            world().playSound(center(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.BLOCKS, 0.8f, 0.4f);
            changed();
            return true;
        }
        if (OilItems.isOil(held)) {
            int add = Math.min(MAX_OIL - oil, held.getAmount());
            if (add <= 0) {
                return false;
            }
            oil += add;
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract(add);
            }
            world().playSound(center(), Sound.BLOCK_HONEY_BLOCK_BREAK, SoundCategory.BLOCKS, 0.8f, 0.8f);
            changed();
            return true;
        }
        if (OilItems.isShovel(held)) {
            if (OilItems.shovelHasOil(held)) {
                if (oil >= MAX_OIL) {
                    return false;
                }
                OilItems.setShovelOil(held, false);
                oil++;
                world().playSound(center(), Sound.BLOCK_HONEY_BLOCK_BREAK, SoundCategory.BLOCKS, 0.8f, 0.8f);
            } else {
                if (oil == 0) {
                    return false;
                }
                OilItems.setShovelOil(held, true);
                oil--;
                world().playSound(center(), Sound.BLOCK_HONEY_BLOCK_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);
            }
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
    public List<ItemStack> contents() {
        List<ItemStack> drops = new ArrayList<>();
        if (oil > 0) {
            ItemStack stack = items().create(OilItems.OIL, oil);
            if (stack != null) {
                drops.add(stack);
            }
        }
        return drops;
    }

    @Override
    public void updateDisplay() {
        String model = lid ? "base" : oil == 0 ? "empty" : oil <= 10 ? "low" : oil <= 20 ? "middle" : "high";
        showPart("body", Key.key("kaleidoscope_cookery", "block/enamel_basin/" + model), blockTransform(0));
    }

    @Override
    public String describe() {
        return "Fetttopf (" + oil + " Öl, Deckel " + lid + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        return Lang.raw("block.kaleidoscope_cookery.enamel_basin").color(NamedTextColor.WHITE).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(oil + "/" + MAX_OIL, NamedTextColor.YELLOW));
    }
}
