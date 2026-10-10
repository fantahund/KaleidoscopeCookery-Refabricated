package de.emrion.kaleidoscopeCookery.station.bambootray;

import de.emrion.kaleidoscopeCookery.recipe.BambooTrayRecipe;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Weather;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Bamboo tray (BambooTrayBlock/BambooTrayBlockEntity in the mod) with 4 slots.
 * <ul>
 *     <li>Right-clicking a quarter puts the whole stack there, with an empty hand you take 1 (sneaking: everything)</li>
 *     <li>Drying: no rain and open sky (glass and further trays above are allowed)</li>
 *     <li>Wetting: rain on the tray or a dripstone with water above it</li>
 *     <li>Stacked trays stand on feet</li>
 * </ul>
 */
public class BambooTrayStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "bamboo_tray");
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/bamboo_tray/base");
    private static final Key MODEL_STAND = Key.key("kaleidoscope_cookery", "block/bamboo_tray/stand");
    private static final int SLOTS = 4;
    private static final int TICK_INTERVAL = 19;
    /** The mod shows up to 5 copies per slot - at most 3 here to save entities */
    private static final int MAX_COPIES = 3;
    private static final float[][] SLOT_POSITIONS = {{0.25f, 0.25f}, {0.75f, 0.25f}, {0.25f, 0.75f}, {0.75f, 0.75f}};
    private static final Set<Material> TRANSPARENT = Set.of(Material.GLASS, Material.GLASS_PANE);

    private static final NamespacedKey ITEMS = Keys.key("items");
    private static final NamespacedKey PROGRESS = Keys.key("progress");
    private static final NamespacedKey COMPLETED = Keys.key("completed");

    /** CompletionState in the mod */
    private enum Completion {
        NONE, DRYING, WETTING
    }

    private final @Nullable ItemStack[] items = new ItemStack[SLOTS];
    private final int[] progress = new int[SLOTS];
    private final Completion[] completed = new Completion[SLOTS];
    private boolean stand;
    /** What is happening right now (for the display), null = nothing */
    private BambooTrayRecipe.@Nullable Type activeType;

    public BambooTrayStation(StationContext context) {
        super(context);
        Arrays.fill(completed, Completion.NONE);
    }

    public static StationType type() {
        return new StationType("bamboo_tray", ITEM, false, 1f, 0.2f, false, BambooTrayStation::new);
    }

    // ---- State ----

    @Override
    protected void load(PersistentDataContainer state) {
        Arrays.fill(items, null);
        List<byte[]> stored = state.getOrDefault(ITEMS, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < Math.min(stored.size(), SLOTS); i++) {
            items[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
        int[] storedProgress = state.getOrDefault(PROGRESS, PersistentDataType.INTEGER_ARRAY, new int[SLOTS]);
        System.arraycopy(storedProgress, 0, progress, 0, Math.min(SLOTS, storedProgress.length));
        byte[] storedCompleted = state.getOrDefault(COMPLETED, PersistentDataType.BYTE_ARRAY, new byte[SLOTS]);
        Arrays.fill(completed, Completion.NONE);
        for (int i = 0; i < Math.min(SLOTS, storedCompleted.length); i++) {
            completed[i] = Completion.values()[Math.clamp(storedCompleted[i], 0, Completion.values().length - 1)];
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item == null ? new byte[0] : item.serializeAsBytes());
        }
        state.set(ITEMS, PersistentDataType.LIST.byteArrays(), stored);
        state.set(PROGRESS, PersistentDataType.INTEGER_ARRAY, progress);
        byte[] storedCompleted = new byte[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            storedCompleted[i] = (byte) completed[i].ordinal();
        }
        state.set(COMPLETED, PersistentDataType.BYTE_ARRAY, storedCompleted);
    }

    // ---- Display ----

    @Override
    public void updateDisplay() {
        stand = context.manager().at(position().below()) instanceof BambooTrayStation;
        showPart("body", stand ? MODEL_STAND : MODEL, blockTransform(0));
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items[slot];
            int copies = stack == null ? 0 : Math.min(MAX_COPIES, stack.getAmount() == 1 ? 1 : Math.min(5, (stack.getAmount() - 1) / 16 + 2));
            Random random = new Random(position().hashCode() + slot * 31L);
            for (int copy = 0; copy < copies; copy++) {
                float x = SLOT_POSITIONS[slot][0] + (float) (random.nextDouble() - 0.5) * 0.1f - 0.5f;
                float z = SLOT_POSITIONS[slot][1] + (float) (random.nextDouble() - 0.5) * 0.1f - 0.5f;
                float y = 0.14f + copy * 0.01f + slot * 0.005f - 0.5f;
                Quaternionf rotation = new Quaternionf().rotateX((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(random.nextFloat() * 360));
                showItemPart("item_" + slot + "_" + copy, stack, new Transformation(new Vector3f(x, y, z), rotation, new Vector3f(0.5f), new Quaternionf()), 0, null);
            }
            for (int copy = copies; copy < MAX_COPIES; copy++) {
                hidePart("item_" + slot + "_" + copy);
            }
        }
    }

    @Override
    public String describe() {
        Block above = world().getBlockAt(position().x(), position().y() + 1, position().z());
        return "Bambustablett (Biom " + above.getBiome().key() + ", Temperatur " + above.getTemperature() + ", Sturm " + world().hasStorm()
                + ", Himmel " + Weather.canSeeSky(above) + ", Regen " + Weather.isRainingAt(above) + ", Modus " + activeType + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        boolean any = false;
        boolean allDone = true;
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] != null) {
                any = true;
                allDone &= completed[i] != Completion.NONE;
            }
        }
        if (!any) {
            state = Lang.get("hud.bamboo_tray.empty").color(NamedTextColor.GRAY);
        } else if (activeType != null) {
            int percent = 0;
            for (int i = 0; i < SLOTS; i++) {
                BambooTrayRecipe recipe = items[i] == null ? null : plugin().getRecipes().findBambooTray(items[i], activeType);
                if (recipe != null && completed[i] == Completion.NONE) {
                    percent = Math.max(percent, Math.min(100, progress[i] * 100 / recipe.time()));
                }
            }
            state = allDone ? Lang.get("hud.bamboo_tray.done").color(NamedTextColor.GREEN)
                    : Lang.get(activeType == BambooTrayRecipe.Type.DRYING ? "hud.bamboo_tray.drying" : "hud.bamboo_tray.wetting", percent).color(NamedTextColor.YELLOW);
        } else {
            state = allDone ? Lang.get("hud.bamboo_tray.done").color(NamedTextColor.GREEN) : Lang.get("hud.bamboo_tray.idle").color(NamedTextColor.GOLD);
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.bamboo_tray").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(state).build();
    }

    // ---- Interaction ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        int slot = slotAt(clickPosition());
        boolean handled = held.isEmpty() ? takeOut(player, slot, player.isSneaking()) : putItem(player, held, slot);
        if (handled) {
            markDirty();
            updateDisplay();
        }
        // The mod always consumes the click
        return true;
    }

    /**
     * Quarter from the click position (BambooTrayBlock#useItemOn).
     */
    private static int slotAt(@Nullable Vector click) {
        if (click == null) {
            return 0;
        }
        double localX = click.getX() + 0.5;
        double localZ = click.getZ() + 0.5;
        return (localZ >= 0.5 ? 2 : 0) + (localX >= 0.5 ? 1 : 0);
    }

    private boolean putItem(Player player, ItemStack held, int slot) {
        ItemStack stored = items[slot];
        int moved;
        if (stored == null) {
            moved = Math.min(held.getAmount(), held.getMaxStackSize());
            items[slot] = held.asQuantity(moved);
            resetProcessing(slot);
        } else {
            if (completed[slot] != Completion.NONE || !stored.isSimilar(held)) {
                return false;
            }
            moved = Math.min(held.getAmount(), stored.getMaxStackSize() - stored.getAmount());
            if (moved <= 0) {
                return false;
            }
            stored.add(moved);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(moved);
        }
        world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
        return true;
    }

    private boolean takeOut(Player player, int slot, boolean all) {
        ItemStack stored = items[slot];
        if (stored == null) {
            return false;
        }
        int amount = all ? stored.getAmount() : 1;
        ItemStack extracted = stored.asQuantity(amount);
        stored.subtract(amount);
        if (stored.isEmpty()) {
            items[slot] = null;
            resetProcessing(slot);
        }
        for (ItemStack rest : player.getInventory().addItem(extracted).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
        world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
        return true;
    }

    private void resetProcessing(int slot) {
        progress[slot] = 0;
        completed[slot] = Completion.NONE;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                contents.add(item);
            }
        }
        return contents;
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return true;
    }

    @Override
    public void tick(long serverTick) {
        long offset = position().x() + position().y() + position().z();
        if ((world().getGameTime() + offset) % TICK_INTERVAL != 0) {
            return;
        }
        // Adjust the feet when a tray below is added or removed
        if (stand != context.manager().at(position().below()) instanceof BambooTrayStation) {
            updateDisplay();
        }
        boolean hasItems = false;
        for (ItemStack item : items) {
            hasItems |= item != null;
        }
        if (!hasItems) {
            activeType = null;
            return;
        }
        Block above = world().getBlockAt(position().x(), position().y() + 1, position().z());
        boolean wetting = Weather.isRainingAt(above) || Weather.hasWaterDripstone(world().getBlockAt(position().x(), position().y(), position().z()));
        boolean drying = !world().hasStorm() && hasDryingExposure(above);
        if (!wetting && !drying) {
            activeType = null;
            return;
        }
        BambooTrayRecipe.Type type = wetting ? BambooTrayRecipe.Type.WETTING : BambooTrayRecipe.Type.DRYING;
        activeType = type;
        boolean changed = false;
        boolean finished = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack input = items[slot];
            if (input == null || completed[slot] == (type == BambooTrayRecipe.Type.DRYING ? Completion.DRYING : Completion.WETTING)) {
                continue;
            }
            BambooTrayRecipe recipe = plugin().getRecipes().findBambooTray(input, type);
            if (recipe == null) {
                if (progress[slot] != 0) {
                    progress[slot] = 0;
                    changed = true;
                }
                continue;
            }
            if (completed[slot] != Completion.NONE) {
                progress[slot] = 0;
                completed[slot] = Completion.NONE;
            }
            progress[slot] += TICK_INTERVAL;
            changed = true;
            if (progress[slot] >= recipe.time()) {
                ItemStack result = recipe.result().create(items());
                if (result != null) {
                    result.setAmount(Math.min(result.getMaxStackSize(), result.getAmount() * input.getAmount()));
                    items[slot] = result;
                }
                progress[slot] = recipe.time();
                completed[slot] = type == BambooTrayRecipe.Type.DRYING ? Completion.DRYING : Completion.WETTING;
                finished = true;
            }
        }
        if (changed) {
            markDirty();
        }
        if (finished) {
            updateDisplay();
        }
    }

    /**
     * BambooTrayBlockEntity#hasDryingExposure: open sky, only clear glass and trays above.
     */
    private boolean hasDryingExposure(Block above) {
        if (Weather.canSeeSky(above)) {
            return true;
        }
        for (int y = above.getY(); y < world().getMaxHeight(); y++) {
            Block block = world().getBlockAt(above.getX(), y, above.getZ());
            if (block.getType().isAir() || TRANSPARENT.contains(block.getType())) {
                continue;
            }
            return false;
        }
        return true;
    }
}
