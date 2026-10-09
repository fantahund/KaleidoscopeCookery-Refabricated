package de.emrion.kaleidoscopeCookery.station.stove;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Weather;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Herd (StoveBlock im Mod): Hitzequelle für Wok und Suppentopf.
 * <ul>
 *     <li>Anzünden mit Feuerzeug (1 Haltbarkeit) oder Feuerkugel (verbraucht) oder brennendem Projektil</li>
 *     <li>Löschen mit einer Schaufel (auch Pfannenwender), Regen oder Wasser darüber</li>
 * </ul>
 */
public class StoveStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "stove");
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/stove");
    private static final Key MODEL_LIT = Key.key("kaleidoscope_cookery", "block/stove_lit");
    private static final NamespacedKey LIT = Keys.key("lit");
    /** Mittlere Zeit bis zum zufälligen Block-Tick bei randomTickSpeed 3 (≈ 68 s): Chance pro Sekunde */
    private static final double RAIN_CHANCE_PER_SECOND = 1 / 68.0;

    private boolean lit;

    public StoveStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("stove", ITEM, true, 1f, 1f, false, StoveStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        lit = state.getOrDefault(LIT, PersistentDataType.BOOLEAN, false);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(LIT, PersistentDataType.BOOLEAN, lit);
    }

    public boolean isLit() {
        return lit;
    }

    @Override
    public void updateDisplay() {
        // Brennender Herd leuchtet (Mod: Lichtlevel 13)
        showPart("body", new PartLook(modelStack(lit ? MODEL_LIT : MODEL), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE,
                blockTransform(0), lit ? new Display.Brightness(13, 15) : null), 0);
    }

    @Override
    public String describe() {
        return "Herd (" + (lit ? "an" : "aus") + ")";
    }

    @Override
    public boolean isTicking() {
        return lit;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (!lit && (held.getType() == Material.FLINT_AND_STEEL || held.getType() == Material.FIRE_CHARGE)) {
            if (held.getType() == Material.FIRE_CHARGE) {
                world().playSound(center(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 1, ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                }
            } else {
                world().playSound(center(), Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1, ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    player.damageItemStack(hand, 1);
                }
            }
            setLit(true);
            return true;
        }
        if (lit && Tag.ITEMS_SHOVELS.isTagged(held.getType())) {
            if (OilItems.isShovel(held) && OilItems.shovelHasOil(held)) {
                OilItems.setShovelOil(held, false);
            }
            extinguish(0.5f);
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.damageItemStack(hand, 1);
            }
            return true;
        }
        return false;
    }

    /**
     * Brennendes Projektil trifft den Herd.
     */
    public void onBurningProjectile() {
        if (!lit) {
            setLit(true);
        }
    }

    public void setLit(boolean lit) {
        if (this.lit == lit) {
            return;
        }
        this.lit = lit;
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
    }

    private void extinguish(float volume) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, volume, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f);
        setLit(false);
    }

    @Override
    public void tick(long serverTick) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location center = center();
        // animateTick im Mod läuft clientseitig jeden Tick – hier etwas seltener, dafür für alle sichtbar
        if (serverTick % 4 == 0) {
            world().spawnParticle(Particle.SMOKE, center.clone().add(randomOffset(random), 0.5 + random.nextDouble() / 3, randomOffset(random)), 1, 0, 0.02, 0, 0.01);
            BlockFace front = facing().face();
            double side = random.nextDouble() * 0.6 - 0.3;
            double x = front.getModX() != 0 ? front.getModX() * 0.52 : side;
            double z = front.getModZ() != 0 ? front.getModZ() * 0.52 : side;
            world().spawnParticle(Particle.FLAME, center.getX() + x, position().y() + 0.25 + random.nextDouble() * 6 / 16, center.getZ() + z, 1, 0, 0, 0, 0);
        }
        if (random.nextInt(40) == 0) {
            world().playSound(center, Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS, 0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f);
        }
        if (serverTick % 20 == 0) {
            Block above = world().getBlockAt(position().x(), position().y() + 1, position().z());
            boolean water = above.getType() == Material.WATER || (above.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged());
            boolean rain = random.nextDouble() < RAIN_CHANCE_PER_SECOND && Weather.isRainingAt(above);
            if (water || rain) {
                extinguish(1);
            }
        }
    }

    private static double randomOffset(ThreadLocalRandom random) {
        return random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1);
    }
}
