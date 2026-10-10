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
 * Dampfkorb (SteamerBlockEntity im Mod). Halbe Lage = 4 Plätze, volle Lage (zwei Körbe) = 8.
 * <ul>
 *     <li>Hitze direkt darunter gibt Stufe 4; jede volle Lage darüber eine Stufe weniger (max. 4 Lagen dämpfen)</li>
 *     <li>Gegart wird nur, wenn oben ein Deckel liegt (Schleichen + Rechtsklick mit leerer Hand)</li>
 *     <li>Ohne Hitze kühlt der Fortschritt langsam wieder ab</li>
 *     <li>Rechtsklick mit Dampfkorb: halbe Lage wird voll bzw. neue Lage oben drauf</li>
 *     <li>Rechtsklick mit leerer Hand: alles herausnehmen; ist die oberste Lage leer, nimmt man sie mit</li>
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
    /** Garzeit je Platz; -1 = fertig */
    private final int[] cookingTime = new int[SLOTS];
    private final int[] cookingProgress = new int[SLOTS];

    private int litCache;
    private long litCheckedAt = -100;

    public SteamerStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("steamer", ITEM, true, 0.9f, 1f, true, SteamerStation::new);
    }

    // ---- Zustand ----

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
     * Hitze-Stufe wie updateLitLevel im Mod: Hitzequelle darunter = 4, darunter ein voller Dampfkorb = dessen Stufe - 1.
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
     * Darüber ein Block, dessen Unterseite trägt (dann kommt man nicht an den Inhalt).
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

    // ---- Bedienung ----

    /**
     * Dampfkorb in der Hand: Stapeln hat Vorrang vor dem normalen Platzieren.
     */
    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        if (!ITEM.equals(CookeryItems.idOf(held))) {
            return false;
        }
        // Wie SteamerBlock#useItemOn: nach oben durch volle Lagen ohne Deckel
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
            // Auf eine Lage mit Deckel: neue Lage darüber, der Deckel darunter verschwindet (SteamerBlock#updateShape)
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

    // ---- Abbauen ----

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

    // ---- Darstellung ----

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
                // SteamerBlockEntityRender: 2x2 je Lage, flach liegend
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
     * Standfuß wie SteamerBlock#shouldHasBase: nicht auf einem Dampfkorb, sondern über Hitze oder einem nicht tragenden Block.
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
