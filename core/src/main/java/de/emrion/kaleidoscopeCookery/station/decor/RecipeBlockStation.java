package de.emrion.kaleidoscopeCookery.station.decor;

import de.emrion.kaleidoscopeCookery.item.RecipeItems;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Recipe note (RecipeBlock in the mod): a recipe placed on the floor, on a wall or below the ceiling.
 * Right-click with an empty hand picks it up again.
 */
public class RecipeBlockStation extends Station {

    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/recipe_block");
    private static final NamespacedKey ITEM_DATA = Keys.key("recipe");
    private static final NamespacedKey FACE = Keys.key("face");
    private static final NamespacedKey DIRECTION = Keys.key("direction");

    /** AttachFace: 0 = floor, 1 = wall, 2 = ceiling */
    private int face;
    /** Blockstate direction (facing) as a 2D value */
    private int direction = Facing.NORTH.dataValue();
    private @Nullable ItemStack recipe;

    public RecipeBlockStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("recipe_block", RecipeItems.ITEM, false, 1f, 1f, false, RecipeBlockStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        face = Math.clamp(state.getOrDefault(FACE, PersistentDataType.INTEGER, 0), 0, 2);
        direction = state.getOrDefault(DIRECTION, PersistentDataType.INTEGER, Facing.NORTH.dataValue());
        byte[] bytes = state.get(ITEM_DATA, PersistentDataType.BYTE_ARRAY);
        recipe = bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(FACE, PersistentDataType.INTEGER, face);
        state.set(DIRECTION, PersistentDataType.INTEGER, direction);
        if (recipe != null) {
            state.set(ITEM_DATA, PersistentDataType.BYTE_ARRAY, recipe.serializeAsBytes());
        }
    }

    /**
     * FaceAttachedHorizontalDirectionalBlock#getStateForPlacement: floor/ceiling in view direction, wall facing away from the wall.
     *
     * @param clickedFace clicked face of the block the note is attached to
     */
    public void initFrom(ItemStack item, BlockFace clickedFace) {
        recipe = item.asOne();
        Facing look = Facing.byDataValue((facing().dataValue() + 2) % 4);
        switch (clickedFace) {
            case UP -> {
                face = 0;
                direction = look.dataValue();
            }
            case DOWN -> {
                face = 2;
                direction = look.dataValue();
            }
            default -> {
                face = 1;
                for (Facing candidate : Facing.values()) {
                    if (candidate.face() == clickedFace) {
                        direction = candidate.dataValue();
                    }
                }
            }
        }
    }

    private static int yaw(Facing facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
    }

    @Override
    public void updateDisplay() {
        // Blockstate: floor y; wall x 90; ceiling x 180, y + 180
        Facing facing = Facing.byDataValue(direction);
        int x = switch (face) {
            case 1 -> 90;
            case 2 -> 180;
            default -> 0;
        };
        int y = yaw(facing) + (face == 2 ? 180 : 0);
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(-y)).rotateX((float) Math.toRadians(-x));
        showPart("body", MODEL, new Transformation(new Vector3f(), rotation, new Vector3f(1), new Quaternionf()));
    }

    @Override
    public String describe() {
        return "Rezept-Zettel";
    }

    /**
     * Only holds on a solid block (floor, wall behind it or ceiling).
     */
    @Override
    public boolean canSurvive() {
        Block block = world().getBlockAt(position().x(), position().y(), position().z());
        Block support = switch (face) {
            case 0 -> block.getRelative(BlockFace.DOWN);
            case 2 -> block.getRelative(BlockFace.UP);
            default -> block.getRelative(Facing.byDataValue(direction).face().getOppositeFace());
        };
        return support.getType().isSolid();
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        if (!player.getInventory().getItemInMainHand().isEmpty() || recipe == null) {
            return false;
        }
        player.getInventory().setItemInMainHand(recipe);
        recipe = null;
        world().playSound(center(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1, 1);
        context.manager().remove(this);
        return true;
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        return null;
    }

    @Override
    public List<ItemStack> contents() {
        return recipe == null ? List.of() : List.of(recipe);
    }

    @Override
    public boolean breaksOnAttack() {
        return true;
    }
}
