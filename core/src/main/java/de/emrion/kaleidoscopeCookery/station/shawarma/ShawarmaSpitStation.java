package de.emrion.kaleidoscopeCookery.station.shawarma;

import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shawarma spit (ShawarmaSpitBlock/ShawarmaSpitBlockEntity in the mod), 2 blocks tall with one level each.
 * <ul>
 *     <li>Each level takes up to 8 identical ingredients with a campfire recipe</li>
 *     <li>It only cooks with a redstone signal on one of the two blocks - then the spit rotates</li>
 *     <li>Right-click without a matching ingredient takes out the finished food (or the raw ingredients);
 *     if the spit is on, you burn yourself doing so (1 damage)</li>
 * </ul>
 */
public class ShawarmaSpitStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "shawarma_spit");
    private static final int MAX_ITEMS = 8;
    private static final int LAYERS = 2;
    private static final int SPIN_UPDATE_TICKS = 4;
    /** Mod renderer: System.currentTimeMillis() / 10 degrees = 100 degrees per second */
    private static final float DEGREES_PER_TICK = 5;

    private static final NamespacedKey COOKING = Keys.key("cooking");
    private static final NamespacedKey COOKED = Keys.key("cooked");
    private static final NamespacedKey COOK_TIME = Keys.key("cook_time");

    /** Index 0 = bottom, 1 = top */
    private final @Nullable ItemStack[] cooking = new ItemStack[LAYERS];
    private final @Nullable ItemStack[] cooked = new ItemStack[LAYERS];
    private final int[] cookTime = new int[LAYERS];
    private boolean powered;
    private float spin;

    public ShawarmaSpitStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("shawarma_spit", ITEM, true, 1f, 2f, false, ShawarmaSpitStation::new, 0, 2);
    }

    // ---- State ----

    @Override
    protected void load(PersistentDataContainer state) {
        List<byte[]> storedCooking = state.getOrDefault(COOKING, PersistentDataType.LIST.byteArrays(), List.of());
        List<byte[]> storedCooked = state.getOrDefault(COOKED, PersistentDataType.LIST.byteArrays(), List.of());
        int[] times = state.getOrDefault(COOK_TIME, PersistentDataType.INTEGER_ARRAY, new int[LAYERS]);
        for (int i = 0; i < LAYERS; i++) {
            cooking[i] = i < storedCooking.size() && storedCooking.get(i).length > 0 ? ItemStack.deserializeBytes(storedCooking.get(i)) : null;
            cooked[i] = i < storedCooked.size() && storedCooked.get(i).length > 0 ? ItemStack.deserializeBytes(storedCooked.get(i)) : null;
            cookTime[i] = i < times.length ? times[i] : 0;
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        List<byte[]> storedCooking = new ArrayList<>();
        List<byte[]> storedCooked = new ArrayList<>();
        for (int i = 0; i < LAYERS; i++) {
            storedCooking.add(cooking[i] == null ? new byte[0] : cooking[i].serializeAsBytes());
            storedCooked.add(cooked[i] == null ? new byte[0] : cooked[i].serializeAsBytes());
        }
        state.set(COOKING, PersistentDataType.LIST.byteArrays(), storedCooking);
        state.set(COOKED, PersistentDataType.LIST.byteArrays(), storedCooked);
        state.set(COOK_TIME, PersistentDataType.INTEGER_ARRAY, cookTime);
    }

    // ---- Display ----

    /**
     * The mod turns the model in the player's view direction (not towards them like the other stations).
     */
    private Facing modelFacing() {
        return Facing.byDataValue((facing().dataValue() + 2) % 4);
    }

    @Override
    public void updateDisplay() {
        Quaternionf rotation = modelFacing().blockRotation();
        Display.Brightness light = powered ? new Display.Brightness(8, 15) : null;
        showPart("lower", new PartLook(modelStack(Key.key("kaleidoscope_cookery", powered ? "block/shawarma_spit_powered_lower" : "block/shawarma_spit_lower")),
                org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, new Transformation(new Vector3f(), rotation, new Vector3f(1), new Quaternionf()), light), 0);
        showPart("upper", new PartLook(modelStack(Key.key("kaleidoscope_cookery", powered ? "block/shawarma_spit_powered_upper" : "block/shawarma_spit_upper")),
                org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, new Transformation(new Vector3f(0, 1, 0), rotation, new Vector3f(1), new Quaternionf()), light), 0);
        updateItems(0);
    }

    /**
     * ShawarmaSpitBlockEntityRender: ingredients in a circle around the spit, the lower ones at 0.875, the upper ones at 1.5 blocks.
     */
    private void updateItems(int interpolation) {
        for (int layer = 0; layer < LAYERS; layer++) {
            ItemStack shown = cooked[layer] == null ? null : cooking[layer] != null ? cooking[layer] : cooked[layer];
            int count = shown == null ? 0 : Math.min(shown.getAmount(), MAX_ITEMS);
            float y = layer == 0 ? 0.875f - 0.5f : 1.5f - 0.5f;
            for (int i = 0; i < count; i++) {
                Quaternionf around = new Quaternionf().rotationY((float) Math.toRadians(spin + i * 45));
                Vector3f translation = around.transform(new Vector3f(-0.25f, 0, -0.25f)).add(0, y, 0);
                showItemPart("item_" + layer + "_" + i, shown, new Transformation(translation, around, new Vector3f(0.65f), new Quaternionf()), interpolation, null);
            }
            hidePartsStartingWith("item_" + layer + "_", count);
        }
    }

    @Override
    public String describe() {
        return "Dönerspieß (" + (powered ? "an" : "aus") + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        var text = Component.text().append(Lang.raw("block.kaleidoscope_cookery.shawarma_spit").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(powered ? Lang.get("hud.shawarma.on").color(NamedTextColor.GOLD) : Lang.get("hud.shawarma.off").color(NamedTextColor.GRAY));
        for (int layer = LAYERS - 1; layer >= 0; layer--) {
            text.append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(Lang.get(layer == 1 ? "hud.shawarma.upper" : "hud.shawarma.lower").color(NamedTextColor.WHITE))
                    .append(Component.text(" ")).append(layerState(layer));
        }
        return text.build();
    }

    private Component layerState(int layer) {
        if (cooking[layer] != null && cookTime[layer] > 0) {
            return Lang.get("hud.shawarma.cooking", cooking[layer].getAmount(), cooking[layer].effectiveName(), (cookTime[layer] + 19) / 20).color(NamedTextColor.YELLOW);
        }
        if (cooked[layer] != null) {
            return Lang.get("hud.shawarma.done", cooked[layer].getAmount(), cooked[layer].effectiveName()).color(NamedTextColor.GREEN);
        }
        return Lang.get("hud.shawarma.empty").color(NamedTextColor.GRAY);
    }

    // ---- Interaction ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        if (player.isSneaking()) {
            return false;
        }
        Vector click = clickPosition();
        int layer = click != null && click.getY() >= 1 ? 1 : 0;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (putCookingItem(layer, held)) {
            return true;
        }
        return takeCookedItem(layer, player);
    }

    /**
     * ShawarmaSpitBlockEntity#onPutCookingItem: takes up to 8 items (also in creative mode, as in the mod).
     */
    private boolean putCookingItem(int layer, ItemStack stack) {
        if (cooking[layer] != null || cooked[layer] != null || stack.isEmpty()) {
            return false;
        }
        CampfireRecipe recipe = findRecipe(stack);
        if (recipe == null) {
            return false;
        }
        int amount = Math.min(stack.getAmount(), MAX_ITEMS);
        cooking[layer] = stack.asQuantity(amount);
        stack.subtract(amount);
        cooked[layer] = recipe.getResult().asQuantity(amount);
        cookTime[layer] = recipe.getCookingTime();
        markDirty();
        updateItems(0);
        playSound(Sound.ENTITY_ITEM_FRAME_ADD_ITEM, layer);
        return true;
    }

    private boolean takeCookedItem(int layer, Player player) {
        ItemStack give;
        if (cookTime[layer] <= 0 && cooked[layer] != null) {
            give = cooked[layer];
        } else if (cookTime[layer] > 0 && cooking[layer] != null) {
            give = cooking[layer];
        } else {
            return false;
        }
        cooking[layer] = null;
        cooked[layer] = null;
        cookTime[layer] = 0;
        markDirty();
        updateItems(0);
        if (powered) {
            player.damage(1, DamageSource.builder(DamageType.IN_FIRE).build());
        }
        for (ItemStack rest : player.getInventory().addItem(give).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
        playSound(Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, layer);
        return true;
    }

    private static @Nullable CampfireRecipe findRecipe(ItemStack stack) {
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (recipes.next() instanceof CampfireRecipe campfire && campfire.getInputChoice().test(stack)) {
                return campfire;
            }
        }
        return null;
    }

    private void playSound(Sound sound, int layer) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world().playSound(center().add(0, layer, 0), sound, SoundCategory.BLOCKS, 0.5f + random.nextFloat(), random.nextFloat() * 0.7f + 0.6f);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < LAYERS; i++) {
            if (cooking[i] != null) {
                contents.add(cooking[i]);
            } else if (cooked[i] != null) {
                contents.add(cooked[i]);
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
        if (serverTick % 4 == 0) {
            Block lower = world().getBlockAt(position().x(), position().y(), position().z());
            boolean nowPowered = lower.isBlockIndirectlyPowered() || lower.getRelative(0, 1, 0).isBlockIndirectlyPowered();
            if (nowPowered != powered) {
                powered = nowPowered;
                updateDisplay();
            }
        }
        if (!powered) {
            return;
        }
        boolean hasItems = false;
        for (int layer = 0; layer < LAYERS; layer++) {
            if (cooked[layer] != null) {
                hasItems = true;
            }
            tickLayer(layer);
        }
        if (hasItems && serverTick % SPIN_UPDATE_TICKS == 0) {
            spin = (spin + DEGREES_PER_TICK * SPIN_UPDATE_TICKS) % 360;
            updateItems(SPIN_UPDATE_TICKS);
        }
    }

    private void tickLayer(int layer) {
        if (cooking[layer] == null) {
            if (cooked[layer] != null) {
                spawnParticles(layer);
            }
            return;
        }
        spawnParticles(layer);
        if (cookTime[layer] > 0) {
            cookTime[layer]--;
            if (cookTime[layer] % 20 == 0) {
                markDirty();
            }
        } else {
            playSound(Sound.BLOCK_FIRE_EXTINGUISH, layer);
            cooking[layer] = null;
            markDirty();
            updateItems(0);
        }
    }

    private void spawnParticles(int layer) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextFloat() < 0.25f) {
            world().spawnParticle(Particle.WHITE_SMOKE, center().add(0, layer, 0), 1, 0.25, 0.2, 0.25, 0.01);
        }
        if (random.nextInt(20) == 0) {
            playSound(Sound.BLOCK_CAMPFIRE_CRACKLE, layer);
        }
    }
}
