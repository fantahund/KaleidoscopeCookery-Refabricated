package de.emrion.kaleidoscopeCookery.station.steamer;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.SteamerRecipe;
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
import org.bukkit.Particle;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Steamer (SteamerBlockEntity in the mod). Half layer = 4 slots, full layer (two baskets) = 8.
 * <ul>
 *     <li>Heat directly below gives level 4; every full layer above one level less (max. 4 layers steam)</li>
 *     <li>It only cooks when a lid is on top (sneak + right-click with an empty hand)</li>
 *     <li>Without heat the progress slowly cools down again</li>
 *     <li>Right-click with a steamer: a half layer becomes full, or a new layer on top</li>
 *     <li>Right-click with an empty hand: take everything out; if the top layer is empty you take it with you</li>
 * </ul>
 */
public class SteamerStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "steamer");
    public static final int MAX_LIT_LEVEL = 4;
    private static final int SLOTS = 8;

    private static final NamespacedKey HALF = Keys.key("half");
    private static final NamespacedKey LID = Keys.key("lid");
    private static final NamespacedKey ITEMS = Keys.key("items");
    private static final NamespacedKey TIMES = Keys.key("cooking_time");
    private static final NamespacedKey PROGRESS = Keys.key("cooking_progress");

    private boolean half = true;
    private boolean lid;
    private final ItemStack[] items = new ItemStack[SLOTS];
    /** Cooking time per slot; -1 = done */
    private final int[] cookingTime = new int[SLOTS];
    private final int[] cookingProgress = new int[SLOTS];

    private int litCache;
    private long litCheckedAt = -100;

    public SteamerStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("steamer", ITEM, false, 0.9f, 1f, true, SteamerStation::new);
    }

    // ---- State ----

    @Override
    protected void load(PersistentDataContainer state) {
        half = state.getOrDefault(HALF, PersistentDataType.BOOLEAN, true);
        lid = state.getOrDefault(LID, PersistentDataType.BOOLEAN, false);
        Arrays.fill(items, null);
        List<byte[]> stored = state.getOrDefault(ITEMS, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < Math.min(stored.size(), SLOTS); i++) {
            items[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
        int[] times = state.getOrDefault(TIMES, PersistentDataType.INTEGER_ARRAY, new int[SLOTS]);
        int[] progress = state.getOrDefault(PROGRESS, PersistentDataType.INTEGER_ARRAY, new int[SLOTS]);
        System.arraycopy(times, 0, cookingTime, 0, Math.min(SLOTS, times.length));
        System.arraycopy(progress, 0, cookingProgress, 0, Math.min(SLOTS, progress.length));
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(HALF, PersistentDataType.BOOLEAN, half);
        state.set(LID, PersistentDataType.BOOLEAN, lid);
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item == null ? new byte[0] : item.serializeAsBytes());
        }
        state.set(ITEMS, PersistentDataType.LIST.byteArrays(), stored);
        state.set(TIMES, PersistentDataType.INTEGER_ARRAY, cookingTime);
        state.set(PROGRESS, PersistentDataType.INTEGER_ARRAY, cookingProgress);
    }

    private boolean hasItems() {
        for (ItemStack item : items) {
            if (item != null) {
                return true;
            }
        }
        return false;
    }

    private int slotCount() {
        return half ? 4 : 8;
    }

    private @Nullable SteamerStation steamerAt(int dy) {
        return context.manager().at(position().relative(0, dy, 0)) instanceof SteamerStation steamer ? steamer : null;
    }

    private Block blockAt(int dy) {
        return world().getBlockAt(position().x(), position().y() + dy, position().z());
    }

    /**
     * Heat level like updateLitLevel in the mod: heat source below = 4, a full steamer below = its level - 1.
     */
    public int litLevel() {
        long now = context.manager().serverTick();
        if (now - litCheckedAt < 5) {
            return litCache;
        }
        litCheckedAt = now;
        if (context.manager().isHeatSource(blockAt(-1))) {
            litCache = MAX_LIT_LEVEL;
        } else {
            SteamerStation below = steamerAt(-1);
            litCache = below == null || below.half ? 0 : Math.max(below.litLevel() - 1, 0);
        }
        return litCache;
    }

    /**
     * A block above whose bottom face supports it (then the contents cannot be reached).
     */
    private boolean blockedAbove() {
        Block above = blockAt(1);
        return steamerAt(1) == null && context.manager().at(above) == null && above.getBlockData().isFaceSturdy(BlockFace.DOWN, org.bukkit.block.BlockSupport.FULL);
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return hasItems();
    }

    @Override
    public void tick(long serverTick) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (serverTick % 5 == 0) {
            for (int i = 0; i < SLOTS; i++) {
                if (items[i] != null && cookingTime[i] == -1) {
                    if (random.nextFloat() < 0.5f) {
                        double y = half ? 0.25 : 0.75;
                        world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + offset(random, 1.25), position().y() + y + random.nextDouble() / 2,
                                position().z() + 0.5 + offset(random, 1.25), 1, 0, 0, 0, 0.05);
                    }
                    break;
                }
            }
        }
        if (litLevel() > 0) {
            cookingTick(random);
        } else {
            cooldownTick();
        }
    }

    private static double offset(ThreadLocalRandom random, double divisor) {
        return random.nextDouble() / divisor * (random.nextBoolean() ? 1 : -1);
    }

    private void cookingTick(ThreadLocalRandom random) {
        if (steamerAt(1) == null) {
            if (random.nextFloat() < 0.1f) {
                double y = half ? 0.5 : 1;
                world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + offset(random, 2), position().y() + y + random.nextDouble() / 2,
                        position().z() + 0.5 + offset(random, 2), 1, 0, 0, 0, 0.05);
            }
            if (!lid) {
                return;
            }
        }
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items[i];
            if (stack == null || cookingTime[i] == -1) {
                continue;
            }
            changed = true;
            int progress = cookingProgress[i]++;
            if (progress < cookingTime[i]) {
                continue;
            }
            SteamerRecipe recipe = plugin().getRecipes().findSteamer(stack);
            ItemStack result = recipe == null ? null : recipe.result().create(items());
            if (result != null && !result.isEmpty()) {
                items[i] = result;
            }
            cookingTime[i] = -1;
            updateDisplay();
        }
        if (changed) {
            markDirty();
        }
    }

    private void cooldownTick() {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            if (cookingProgress[i] > 0) {
                changed = true;
                cookingProgress[i] = Math.clamp(cookingProgress[i] - 2, 0, Math.max(0, cookingTime[i]));
            }
        }
        if (changed) {
            markDirty();
        }
    }

    // ---- Handling ----

    /**
     * Steamer in hand: stacking takes priority over normal placing.
     */
    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        if (!ITEM.equals(CookeryItems.idOf(held))) {
            return false;
        }
        // Like SteamerBlock#useItemOn: upwards through full layers without a lid
        SteamerStation target = this;
        while (target != null && !target.lid && !target.half) {
            SteamerStation above = target.steamerAt(1);
            if (above == null) {
                Block block = target.blockAt(1);
                if ((block.getType().isAir() || block.isReplaceable()) && context.manager().at(block) == null) {
                    context.manager().place(type(context), block, target.facing());
                    consume(player, held);
                    world().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_BAMBOO_PLACE, SoundCategory.BLOCKS, 1, 1);
                    return true;
                }
                return false;
            }
            target = above;
        }
        if (target != null && target.lid && target.steamerAt(1) == null) {
            // Onto a layer with a lid: new layer above it, the lid below disappears (SteamerBlock#updateShape)
            Block block = target.blockAt(1);
            if ((block.getType().isAir() || block.isReplaceable()) && context.manager().at(block) == null) {
                target.lid = false;
                target.markDirty();
                target.updateDisplay();
                context.manager().place(type(context), block, target.facing());
                consume(player, held);
                world().playSound(block.getLocation().toCenterLocation(), Sound.BLOCK_BAMBOO_PLACE, SoundCategory.BLOCKS, 1, 1);
                return true;
            }
            return false;
        }
        if (target != null && target.half && !target.lid) {
            target.half = false;
            target.markDirty();
            target.updateDisplay();
            consume(player, held);
            world().playSound(target.center(), Sound.BLOCK_BAMBOO_PLACE, SoundCategory.BLOCKS, 1, 1);
            return true;
        }
        return false;
    }

    private static StationType type(StationContext context) {
        return context.type();
    }

    private static void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty() && player.isSneaking() && (lid || steamerAt(1) == null)) {
            lid = !lid;
            world().playSound(center(), lid ? Sound.BLOCK_BAMBOO_PLACE : Sound.BLOCK_BAMBOO_BREAK, SoundCategory.BLOCKS, 0.7f, 1.2f);
            markDirty();
            updateDisplay();
            return true;
        }
        if (placeFood(held, player)) {
            return true;
        }
        return held.isEmpty() && takeFood(player);
    }

    private boolean placeFood(ItemStack food, Player player) {
        if (food.isEmpty() || blockedAbove()) {
            return false;
        }
        SteamerRecipe recipe = plugin().getRecipes().findSteamer(food);
        if (recipe == null) {
            return false;
        }
        boolean added = false;
        for (int i = 0; i < slotCount() && !food.isEmpty(); i++) {
            if (items[i] == null) {
                cookingTime[i] = recipe.time();
                cookingProgress[i] = 0;
                items[i] = food.asOne();
                if (player.getGameMode() != GameMode.CREATIVE) {
                    food.subtract();
                }
                added = true;
            }
        }
        if (added) {
            world().playSound(center(), Sound.BLOCK_BAMBOO_HIT, SoundCategory.BLOCKS, 1, 1);
            markDirty();
            updateDisplay();
            context.manager().updateTicking(this);
        }
        return added;
    }

    private boolean takeFood(Player player) {
        if (blockedAbove()) {
            return false;
        }
        boolean allEmpty = true;
        for (int i = 0; i < slotCount(); i++) {
            if (items[i] == null) {
                continue;
            }
            allEmpty = false;
            give(player, items[i]);
            items[i] = null;
            cookingTime[i] = 0;
            cookingProgress[i] = 0;
        }
        if (allEmpty && !lid && steamerAt(1) == null) {
            ItemStack steamer = items().create(ITEM, 1);
            if (steamer != null && player.getGameMode() != GameMode.CREATIVE) {
                give(player, steamer);
            }
            world().playSound(center(), Sound.BLOCK_BAMBOO_BREAK, SoundCategory.BLOCKS, 1, 1);
            if (half) {
                context.manager().remove(this);
                return true;
            }
            half = true;
            markDirty();
            updateDisplay();
            return true;
        }
        if (allEmpty) {
            return false;
        }
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
        return true;
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    // ---- Breaking ----

    @Override
    public @Nullable ItemStack selfDrop() {
        ItemStack steamer = items().create(ITEM, half ? 1 : 2);
        return steamer;
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

    // ---- Display ----

    @Override
    public void updateDisplay() {
        String model = "block/steamer_" + (half ? "half" : "full") + (lid ? "_lid" : "") + (hasBase() ? "_base" : "");
        showPart("body", Key.key("kaleidoscope_cookery", model), blockTransform(0));
        int shown = 0;
        if (!lid) {
            for (int i = 0; i < SLOTS; i++) {
                if (items[i] == null) {
                    continue;
                }
                // SteamerBlockEntityRender: 2x2 per layer, lying flat
                float x = (i % 2) * 0.3f + 0.35f - 0.5f;
                float y = (i / 4) * 0.5f + 0.25f + (i % 4) * 0.01f - 0.5f;
                float z = ((i / 2) % 2) * 0.3f + 0.35f - 0.5f;
                showItemPart("item_" + i, items[i], new Transformation(new Vector3f(x, y, z), new Quaternionf().rotationX((float) Math.toRadians(-90)),
                        new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf()), 0, null);
                shown++;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (lid || items[i] == null) {
                hidePart("item_" + i);
            }
        }
        if (shown == 0) {
            hidePartsStartingWith("item_", 0);
        }
    }

    /**
     * Stand like SteamerBlock#shouldHasBase: not on a steamer, but above heat or a non-supporting block.
     */
    private boolean hasBase() {
        if (steamerAt(-1) != null) {
            return false;
        }
        Block below = blockAt(-1);
        if (below.getBlockData() instanceof org.bukkit.block.data.Lightable || context.manager().at(below) != null) {
            return true;
        }
        Material type = below.getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.LAVA || type == Material.MAGMA_BLOCK) {
            return true;
        }
        return !below.getBlockData().isFaceSturdy(BlockFace.UP, org.bukkit.block.BlockSupport.FULL);
    }

    @Override
    public String describe() {
        return "Dampfkorb (" + (half ? "halb" : "voll") + ", Deckel " + lid + ", Hitze " + litLevel() + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        int lit = litLevel();
        if (!hasItems()) {
            state = Lang.get("hud.steamer.empty").color(NamedTextColor.GRAY);
        } else if (lit == 0) {
            state = Lang.get("station.need_heat").color(NamedTextColor.RED);
        } else if (steamerAt(1) == null && !lid) {
            state = Lang.get("hud.steamer.need_lid").color(NamedTextColor.GOLD);
        } else {
            int remaining = 0;
            int done = 0;
            int count = 0;
            for (int i = 0; i < SLOTS; i++) {
                if (items[i] == null) {
                    continue;
                }
                count++;
                if (cookingTime[i] == -1) {
                    done++;
                } else {
                    remaining = Math.max(remaining, cookingTime[i] - cookingProgress[i]);
                }
            }
            state = done == count ? Lang.get("hud.steamer.done").color(NamedTextColor.GREEN)
                    : Lang.get("hud.steamer.cooking", (remaining + 19) / 20, lit).color(NamedTextColor.YELLOW);
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.steamer").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(state).build();
    }
}
