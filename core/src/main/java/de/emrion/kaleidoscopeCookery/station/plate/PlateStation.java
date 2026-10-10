package de.emrion.kaleidoscopeCookery.station.plate;

import de.emrion.kaleidoscopeCookery.food.FoodService;
import de.emrion.kaleidoscopeCookery.food.Quality;
import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.station.BlockKey;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Placed dish (FoodBiteBlock in the mod): right-click eats one bite, after the last bite
 * the dishware remains (bowl, bone ...). Broken untouched, it returns the dish.
 * 1x2 dishes also occupy the block to the left (seen from the dish), 3x3 dishes
 * (FoodBiteThreeByThreeBlock, cold cut ham slices) the eight blocks around it.
 */
public class PlateStation extends Station {

    public static final String TYPE_ID = "plate";
    private static final NamespacedKey DISH = Keys.key("dish");
    private static final NamespacedKey BITES = Keys.key("bites");
    private static final NamespacedKey QUALITY = Keys.key("quality");

    private @Nullable Key dish;
    private int bites;
    private @Nullable Quality quality;

    public PlateStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        // The item of the type is only a placeholder: every dish with plate data can be placed
        return new StationType(TYPE_ID, Key.key("kaleidoscope", "plate"), false, 0.8f, 0.4f, true, PlateStation::new);
    }

    /**
     * Second block of a 1x2 dish: to the left as seen from the dish (mod: pos.relative(facing.getClockWise())).
     */
    public static BlockFace secondBlock(Facing facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
        };
    }

    public void initFrom(ItemStack item, Key dishId) {
        List<BlockKey> before = occupied();
        dish = dishId;
        bites = 0;
        quality = FoodService.quality(item);
        markDirty();
        context.manager().updateOccupied(this, before);
    }

    private @Nullable CookeryItem item() {
        return dish == null ? null : items().get(dish);
    }

    private CookeryItem.@Nullable Plate plate() {
        CookeryItem item = item();
        return item == null ? null : item.plate();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        String id = state.get(DISH, PersistentDataType.STRING);
        dish = id != null && Key.parseable(id) ? Key.key(id) : null;
        bites = state.getOrDefault(BITES, PersistentDataType.INTEGER, 0);
        quality = Quality.byId(state.get(QUALITY, PersistentDataType.STRING));
    }

    @Override
    protected void save(PersistentDataContainer state) {
        if (dish != null) {
            state.set(DISH, PersistentDataType.STRING, dish.asString());
        }
        state.set(BITES, PersistentDataType.INTEGER, bites);
        if (quality != null) {
            state.set(QUALITY, PersistentDataType.STRING, quality.id());
        }
    }

    @Override
    public List<BlockKey> occupied() {
        CookeryItem.Plate plate = plate();
        if (plate != null && plate.wide()) {
            BlockFace side = secondBlock(facing());
            return List.of(position(), position().relative(side.getModX(), 0, side.getModZ()));
        }
        if (plate != null && plate.large()) {
            List<BlockKey> blocks = new ArrayList<>(9);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    blocks.add(position().relative(dx, 0, dz));
                }
            }
            return blocks;
        }
        return List.of(position());
    }

    @Override
    public void updateDisplay() {
        CookeryItem.Plate plate = plate();
        if (plate == null) {
            return;
        }
        if (plate.wide()) {
            // Part "right" stands on the block of the station, "left" next to it
            showStage("right", plate, blockTransform(0));
            BlockFace side = secondBlock(facing());
            org.bukkit.util.Transformation left = blockTransform(0);
            left.getTranslation().add(side.getModX(), 0, side.getModZ());
            showStage("left", plate, left);
            showHitbox("left", new Vector(side.getModX(), 0, side.getModZ()), 0.8f, 0.4f);
        } else {
            showStage("main", plate, blockTransform(0));
            if (plate.large()) {
                // Whole platter clickable: flat hitboxes on the eight neighbouring blocks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            showHitbox("plate_" + (dx + 1) + "_" + (dz + 1), new Vector(dx, 0, dz), 1f, 0.25f);
                        }
                    }
                }
            }
        }
    }

    private void showStage(String part, CookeryItem.Plate plate, org.bukkit.util.Transformation transformation) {
        List<Key> stages = plate.models().get(part);
        if (stages == null || stages.isEmpty()) {
            return;
        }
        showPart(part, stages.get(Math.min(bites, stages.size() - 1)), transformation);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        CookeryItem.Plate plate = plate();
        if (plate == null) {
            return false;
        }
        if (bites >= plate.maxBites()) {
            context.manager().breakStation(this, player);
            return true;
        }
        ItemStack dishStack = dish == null ? null : items().create(dish, 1);
        if (dishStack != null && quality != null) {
            plugin().getFoodService().setQuality(dishStack, quality);
        }
        if (!plugin().getFoodService().eatBite(player, plate.food(), quality == null ? 1 : quality.ratio(),
                dishStack == null ? ItemStack.empty() : dishStack)) {
            return false;
        }
        bites++;
        markDirty();
        updateDisplay();
        return true;
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        if (dish == null || bites > 0) {
            return null;
        }
        ItemStack stack = items().create(dish, 1);
        if (stack != null && quality != null) {
            plugin().getFoodService().setQuality(stack, quality);
        }
        return stack;
    }

    @Override
    public List<ItemStack> contents() {
        CookeryItem.Plate plate = plate();
        if (plate == null || bites == 0) {
            return List.of();
        }
        List<ItemStack> loot = new ArrayList<>();
        for (Key key : plate.loot()) {
            ItemStack stack = items().create(key, 1);
            if (stack != null) {
                loot.add(stack);
            }
        }
        return loot;
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        CookeryItem item = item();
        CookeryItem.Plate plate = plate();
        if (item == null || plate == null) {
            return null;
        }
        Component line = Lang.raw(item.nameKey()).color(NamedTextColor.WHITE)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Lang.get("hud.plate.bites", plate.maxBites() - bites, plate.maxBites()).color(NamedTextColor.YELLOW));
        if (quality != null) {
            line = line.append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(Lang.raw(quality.translationKey()).color(quality.color()));
        }
        return line;
    }

    @Override
    public String describe() {
        return "Teller (" + dish + ", Biss " + bites + ")";
    }
}
