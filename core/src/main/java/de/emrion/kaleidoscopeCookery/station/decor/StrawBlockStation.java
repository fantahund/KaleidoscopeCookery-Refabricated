package de.emrion.kaleidoscopeCookery.station.decor;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Straw block (StrawBlocks in the mod): pillar block in three axes. No fall damage at all on it; from a fall height of
 * 3 blocks the block can fall apart (chance fall height / 30) and gives 5 rice panicles and 4 rice.
 */
public class StrawBlockStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "straw_block");
    private static final NamespacedKey AXIS = Keys.key("axis");

    /** 0 = y, 1 = x, 2 = z */
    private int axis;

    public StrawBlockStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("straw_block", ITEM, true, 1f, 1f, false, StrawBlockStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        axis = Math.clamp(state.getOrDefault(AXIS, PersistentDataType.INTEGER, 0), 0, 2);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(AXIS, PersistentDataType.INTEGER, axis);
    }

    /**
     * RotatedPillarBlock#getStateForPlacement: axis of the clicked face.
     */
    public void initAxis(BlockFace face) {
        axis = switch (face) {
            case EAST, WEST -> 1;
            case NORTH, SOUTH -> 2;
            default -> 0;
        };
    }

    @Override
    public void updateDisplay() {
        // Blockstate: axis=x -> x 90, y 90; axis=z -> x 90
        Quaternionf rotation = switch (axis) {
            case 1 -> new Quaternionf().rotationY((float) Math.toRadians(-90)).rotateX((float) Math.toRadians(-90));
            case 2 -> new Quaternionf().rotationX((float) Math.toRadians(-90));
            default -> new Quaternionf();
        };
        Key model = Key.key("kaleidoscope_cookery", axis == 0 ? "block/straw_block" : "block/straw_block_horizontal");
        showPart("body", model, new Transformation(new Vector3f(), rotation, new Vector3f(1), new Quaternionf()));
    }

    @Override
    public String describe() {
        return "Strohballen";
    }

    /**
     * StrawBlocks#fallOn
     */
    public void onFallOn(LivingEntity entity, float fallDistance) {
        world().playSound(center(), Sound.BLOCK_GRASS_FALL, SoundCategory.BLOCKS, 1, 1);
        if (fallDistance < 3) {
            return;
        }
        float chance = Math.clamp(fallDistance / 30f, 0, 1);
        if (ThreadLocalRandom.current().nextFloat() < chance) {
            context.manager().remove(this);
            for (ItemStack drop : new ItemStack[]{items().create(Key.key("kaleidoscope_cookery", "rice_panicle"), 5),
                    items().create(Key.key("kaleidoscope_cookery", "rice"), 4)}) {
                if (drop != null) {
                    world().dropItemNaturally(center(), drop);
                }
            }
            world().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center(), 10, 0.1, 0.1, 0.1, 0.05);
        }
    }
}
