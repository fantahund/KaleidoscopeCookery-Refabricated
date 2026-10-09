package de.emrion.kaleidoscopeCookery.station.teapot;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.TeapotRecipe;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Teekanne (TeapotBlockEntity im Mod).
 * <ol>
 *     <li>Teebasis hinein (Wasser- oder Milcheimer; die leere Kanne kann auch direkt aus Wasser schöpfen)</li>
 *     <li>Zutat (Teebeutel, Blumen ...) hinein, auf einer Hitzequelle ziehen lassen</li>
 *     <li>Fertig: Kanne aufnehmen (Rechtsklick mit leerer Hand) und auf hingestellte leere Tassen gießen</li>
 * </ol>
 * Die Kanne als Item trägt ihren Zustand (Teebasis bzw. fertigen Tee) im PDC.
 */
public class TeapotStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "teapot");
    /** Das Blockmodell ist im Mod leer (Kanne per Code gezeichnet) – das 3D-Item-Modell zeigt dieselbe Kanne */
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "teapot_3d");
    /** TeapotRecipe#OUTPUT_COUNT: jede Kanne ergibt 12 Tassen */
    private static final int OUTPUT_COUNT = 12;
    private static final Key MYSTERY_TEA = Key.key("kaleidoscope_cookery", "mystery_tea");

    public static final int PUT_INGREDIENT = 0;
    public static final int PROCESSING = 1;
    public static final int FINISHED = 2;
    private static final int INGREDIENT_TIME = 200;

    /** Zustand im Kannen-Item (Mod: block_entity_data) */
    public static final NamespacedKey ITEM_FLUID = Keys.key("teapot_fluid");
    public static final NamespacedKey ITEM_STATUS = Keys.key("teapot_status");
    public static final NamespacedKey ITEM_RESULT = Keys.key("teapot_result");

    private static final NamespacedKey FLUID = Keys.key("fluid");
    private static final NamespacedKey INPUT = Keys.key("input");
    private static final NamespacedKey STATUS = Keys.key("status");
    private static final NamespacedKey CURRENT_TICK = Keys.key("current_tick");
    private static final NamespacedKey RESULT = Keys.key("result");

    private @Nullable Key fluid;
    private @Nullable ItemStack input;
    private int status = PUT_INGREDIENT;
    private int currentTick = -1;
    private @Nullable ItemStack result;

    public TeapotStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("teapot", ITEM, false, 0.6f, 0.6f, true, TeapotStation::new);
    }

    /**
     * Zustand aus dem platzierten Kannen-Item übernehmen.
     */
    public void initFrom(ItemStack item) {
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        String fluidId = pdc.get(ITEM_FLUID, PersistentDataType.STRING);
        fluid = fluidId != null && Key.parseable(fluidId) ? Key.key(fluidId) : null;
        status = pdc.getOrDefault(ITEM_STATUS, PersistentDataType.INTEGER, PUT_INGREDIENT);
        byte[] resultBytes = pdc.get(ITEM_RESULT, PersistentDataType.BYTE_ARRAY);
        result = resultBytes == null ? null : ItemStack.deserializeBytes(resultBytes);
        if (status == FINISHED && result == null) {
            status = PUT_INGREDIENT;
        }
        markDirty();
        context.manager().updateTicking(this);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        String fluidId = state.get(FLUID, PersistentDataType.STRING);
        fluid = fluidId != null && Key.parseable(fluidId) ? Key.key(fluidId) : null;
        byte[] inputBytes = state.get(INPUT, PersistentDataType.BYTE_ARRAY);
        input = inputBytes == null ? null : ItemStack.deserializeBytes(inputBytes);
        status = state.getOrDefault(STATUS, PersistentDataType.INTEGER, PUT_INGREDIENT);
        currentTick = state.getOrDefault(CURRENT_TICK, PersistentDataType.INTEGER, -1);
        byte[] resultBytes = state.get(RESULT, PersistentDataType.BYTE_ARRAY);
        result = resultBytes == null ? null : ItemStack.deserializeBytes(resultBytes);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        if (fluid != null) {
            state.set(FLUID, PersistentDataType.STRING, fluid.asString());
        }
        if (input != null) {
            state.set(INPUT, PersistentDataType.BYTE_ARRAY, input.serializeAsBytes());
        }
        state.set(STATUS, PersistentDataType.INTEGER, status);
        state.set(CURRENT_TICK, PersistentDataType.INTEGER, currentTick);
        if (result != null) {
            state.set(RESULT, PersistentDataType.BYTE_ARRAY, result.serializeAsBytes());
        }
    }

    private boolean hasHeatSource() {
        return context.manager().isHeatSource(world().getBlockAt(position().x(), position().y() - 1, position().z()));
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return fluid != null || status != PUT_INGREDIENT;
    }

    @Override
    public void tick(long serverTick) {
        // Mod: alle 23 Ticks (versetzt je Position) 23 Ticks Fortschritt
        long offset = serverTick + position().hashCode();
        if (status == PUT_INGREDIENT) {
            if (Math.floorMod(offset, 23) != 0 || fluid == null || !hasHeatSource()) {
                return;
            }
            processingEffects();
            if (input == null) {
                return;
            }
            if (currentTick > 0) {
                currentTick = Math.max(-1, currentTick - 23);
                markDirty();
                return;
            }
            TeapotRecipe recipe = plugin().getRecipes().findTeapot(input, fluid);
            ItemStack created = recipe == null ? null : items().create(recipe.result().item(), OUTPUT_COUNT);
            if (created != null) {
                result = created;
                currentTick = recipe.time();
            } else {
                result = items().create(MYSTERY_TEA, 4);
                currentTick = TeapotRecipe.DEFAULT_TIME;
            }
            status = PROCESSING;
            markDirty();
            return;
        }
        if (status == PROCESSING) {
            if (Math.floorMod(offset, 23) != 0 || !hasHeatSource()) {
                return;
            }
            processingEffects();
            if (currentTick > 0) {
                currentTick = Math.max(-1, currentTick - 23);
                markDirty();
                return;
            }
            status = FINISHED;
            currentTick = -1;
            input = null;
            markDirty();
            return;
        }
        if (status == FINISHED && Math.floorMod(offset, 11) == 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (hasHeatSource()) {
                world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4f, 0.8f + random.nextFloat() * 0.2f);
                world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + (random.nextFloat() - 0.5), position().y() + 0.6 + random.nextDouble() / 5,
                        position().z() + 0.5 + (random.nextFloat() - 0.5), 3, 0.02, 0.05, 0.02, 0.02);
            } else {
                finishEffects();
            }
        }
    }

    private void processingEffects() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world().playSound(center(), "kaleidoscope_cookery:block.teapot.processing", SoundCategory.BLOCKS, 0.6f, 0.8f + random.nextFloat() * 0.2f);
        finishEffects();
    }

    private void finishEffects() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + (random.nextFloat() - 0.5) / 3, position().y() + 0.55,
                position().z() + 0.5 + (random.nextFloat() - 0.5) / 3, 1, 0, 0.03, 0, 0.01);
    }

    // ---- Bedienung (TeapotBlock#useItemOn) ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Key bucketFluid = fluidOf(held);
        if (bucketFluid != null) {
            return addTeaFluid(player, held, bucketFluid);
        }
        if (held.getType() == Material.BUCKET && !CookeryItems.isCustom(held)) {
            return removeTeaFluid(player, held);
        }
        if (!held.isEmpty()) {
            return addIngredient(player, held);
        }
        if (player.isSneaking()) {
            return removeIngredient(player);
        }
        return takeTeapot(player);
    }

    /**
     * Teebasis eines Eimers (Mod: alles mit Fluid-Capability, mind. 1000 mB).
     */
    public static @Nullable Key fluidOf(ItemStack stack) {
        if (CookeryItems.isCustom(stack)) {
            return null;
        }
        return switch (stack.getType()) {
            case WATER_BUCKET -> Key.key("minecraft", "water");
            case LAVA_BUCKET -> Key.key("minecraft", "lava");
            case MILK_BUCKET -> Key.key("minecraft", "milk");
            default -> null;
        };
    }

    public static Material bucketOf(Key fluid) {
        return switch (fluid.value()) {
            case "lava" -> Material.LAVA_BUCKET;
            case "milk" -> Material.MILK_BUCKET;
            default -> Material.WATER_BUCKET;
        };
    }

    private boolean addTeaFluid(Player player, ItemStack held, Key bucketFluid) {
        if (status != PUT_INGREDIENT) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.add_tea_fluid.state_incorrect", statusText());
            return false;
        }
        if (fluid != null) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.add_tea_fluid.has_fluid");
            return false;
        }
        fluid = bucketFluid;
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
            give(player, new ItemStack(Material.BUCKET));
        }
        world().playSound(center(), bucketFluid.value().equals("lava") ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1, 1);
        markDirty();
        context.manager().updateTicking(this);
        return true;
    }

    private boolean removeTeaFluid(Player player, ItemStack held) {
        if (status != PUT_INGREDIENT || fluid == null || input != null) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.take_tea_fluid.blocked");
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        give(player, new ItemStack(bucketOf(fluid)));
        world().playSound(center(), fluid.value().equals("lava") ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1, 1);
        fluid = null;
        currentTick = -1;
        markDirty();
        context.manager().updateTicking(this);
        return true;
    }

    private boolean addIngredient(Player player, ItemStack held) {
        if (status != PUT_INGREDIENT) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.add_ingredient.state_incorrect", statusText());
            return false;
        }
        if (fluid == null) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.add_ingredient.no_fluid");
            return false;
        }
        if (input != null) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.add_ingredient.has_ingredient");
            return false;
        }
        input = held.asOne();
        currentTick = INGREDIENT_TIME;
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        world().playSound(center(), Sound.BLOCK_LANTERN_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        markDirty();
        return true;
    }

    private boolean removeIngredient(Player player) {
        if (status != PUT_INGREDIENT || input == null) {
            return false;
        }
        give(player, input);
        input = null;
        markDirty();
        return true;
    }

    private boolean takeTeapot(Player player) {
        if (status == PROCESSING) {
            actionBar(player, "tooltip.kaleidoscope_cookery.teapot.take_teapot.state_incorrect");
            return false;
        }
        if (input != null && status == PUT_INGREDIENT) {
            give(player, input);
            input = null;
        }
        ItemStack teapot = selfDrop();
        if (teapot != null) {
            give(player, teapot);
        }
        world().playSound(center(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.BLOCKS, 0.6f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        context.manager().remove(this);
        return true;
    }

    private Component statusText() {
        return Lang.raw("tooltip.kaleidoscope_cookery.teapot.statue." + switch (status) {
            case PROCESSING -> "processing";
            case FINISHED -> "finished";
            default -> "put_ingredient";
        });
    }

    private void actionBar(Player player, String key, Object... args) {
        context.manager().actionBar(player, Lang.raw(key, args).color(NamedTextColor.GOLD));
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    // ---- Abbauen / Item ----

    /**
     * Kanne mit Zustand (TeapotBlockEntity#getDrops): Teebasis bzw. fertiger Tee bleiben erhalten, während des Ziehens nicht.
     */
    @Override
    public @Nullable ItemStack selfDrop() {
        ItemStack teapot = items().create(ITEM, 1);
        if (teapot == null) {
            return null;
        }
        teapot.editPersistentDataContainer(pdc -> {
            if (status == PUT_INGREDIENT && fluid != null) {
                pdc.set(ITEM_FLUID, PersistentDataType.STRING, fluid.asString());
            } else if (status == FINISHED && result != null) {
                pdc.set(ITEM_STATUS, PersistentDataType.INTEGER, FINISHED);
                pdc.set(ITEM_RESULT, PersistentDataType.BYTE_ARRAY, result.serializeAsBytes());
            }
        });
        TeapotItems.updateLore(teapot);
        return teapot;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        if (status == PUT_INGREDIENT && input != null) {
            contents.add(input);
        }
        return contents;
    }

    // ---- Darstellung ----

    @Override
    public void updateDisplay() {
        showPart("body", MODEL, blockTransform(0));
    }

    @Override
    public String describe() {
        return "Teekanne (Status " + status + ", Basis " + fluid + ", Rest " + currentTick + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        if (status == FINISHED) {
            state = Lang.raw("tooltip.kaleidoscope_cookery.teapot.progress.finished").color(NamedTextColor.GREEN);
        } else if (fluid == null) {
            state = Lang.raw("tooltip.kaleidoscope_cookery.teapot.progress.no_fluid").color(NamedTextColor.GOLD);
        } else if (!hasHeatSource()) {
            state = Lang.raw("tooltip.kaleidoscope_cookery.teapot.progress.no_heat").color(NamedTextColor.RED);
        } else if (input == null && status == PUT_INGREDIENT) {
            state = Lang.raw("tooltip.kaleidoscope_cookery.teapot.progress.no_tea_base").color(NamedTextColor.GOLD);
        } else {
            state = Lang.raw("tooltip.kaleidoscope_cookery.teapot.progress.processing").color(NamedTextColor.YELLOW)
                    .append(Component.text(" · " + Math.max(0, (currentTick + 19) / 20) + " s", NamedTextColor.GRAY));
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.teapot").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(state).build();
    }
}
