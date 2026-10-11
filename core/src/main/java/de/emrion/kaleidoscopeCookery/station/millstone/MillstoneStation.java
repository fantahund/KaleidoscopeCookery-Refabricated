package de.emrion.kaleidoscopeCookery.station.millstone;

import de.emrion.kaleidoscopeCookery.recipe.MillstoneRecipe;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Millstone (MillstoneBlock/MillstoneBlockEntity in the mod). In the mod it occupies 3x3 blocks, but the stone itself is only
 * 2 blocks wide - so here it occupies 2x2, its centre lies on the edge between the four blocks.
 * <ul>
 *     <li>An animal (at least 1 block tall, not a player, not ridden) that steps onto the millstone is harnessed
 *     and walks in a circle (radius 2) - one round takes 80 to 500 ticks depending on the animal</li>
 *     <li>Right-clicking with an ingredient adds up to 8 items; the animal also adds items from its inventory
 *     and items thrown onto the millstone from above</li>
 *     <li>After one round the results drop out at the front</li>
 *     <li>The animal is released when it dies, takes damage, falls, is stuck in a block or is 5 blocks away</li>
 * </ul>
 */
public class MillstoneStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "millstone");
    public static final int MAX_INPUT_COUNT = 8;
    private static final int OUTPUT_SLOTS = 4;
    private static final Key MODEL_BASE = Key.key("kaleidoscope_cookery", "block/millstone_base");
    private static final Key MODEL_WHEEL = Key.key("kaleidoscope_cookery", "block/millstone_wheel");
    private static final Key MODEL_ROLL = Key.key("kaleidoscope_cookery", "block/millstone_roll");
    /** The models are scaled down to 1/4 for block models (tools/gen_entity_models.py) */
    private static final float MODEL_SCALE = 4;
    /** Centre of the roller relative to the block centre at rotation 0 */
    private static final Vector3f ROLL_CENTER = new Vector3f(0, 13 / 16f, -0.5f);
    /** Centre of the millstone relative to the centre of the station block (2x2: block edge) */
    private static final Vector3f MODEL_OFFSET = new Vector3f(0.5f, 0, 0.5f);
    /** Output: just in front of the spout (the stone reaches 1 block, the spout 1.1) */
    private static final double OUTPUT_DISTANCE = 1.45;
    private static final NamespacedKey LAYOUT = Keys.key("layout");
    private static final int WHEEL_UPDATE_TICKS = 2;
    private static final double MAX_DISTANCE_SQUARED = 5 * 5;

    /** datamap/millstone_bindable_data.json: ticks per revolution */
    private static final Map<EntityType, Integer> ROT_SPEED = Map.ofEntries(
            Map.entry(EntityType.WARDEN, 80), Map.entry(EntityType.WITHER, 80), Map.entry(EntityType.RAVAGER, 80),
            Map.entry(EntityType.MULE, 120), Map.entry(EntityType.DONKEY, 120),
            Map.entry(EntityType.VILLAGER, 180),
            Map.entry(EntityType.HORSE, 300), Map.entry(EntityType.ZOMBIE_HORSE, 300), Map.entry(EntityType.SKELETON_HORSE, 300),
            Map.entry(EntityType.TRADER_LLAMA, 300), Map.entry(EntityType.LLAMA, 300), Map.entry(EntityType.CAMEL, 300),
            Map.entry(EntityType.COW, 400), Map.entry(EntityType.MOOSHROOM, 400), Map.entry(EntityType.SHEEP, 400), Map.entry(EntityType.GOAT, 400));
    private static final int DEFAULT_ROT_SPEED = 500;

    private static final NamespacedKey ENTITY = Keys.key("entity");
    private static final NamespacedKey CACHE_ROT = Keys.key("cache_rot");
    private static final NamespacedKey ROT_SPEED_TICK = Keys.key("rot_speed_tick");
    private static final NamespacedKey INPUT = Keys.key("input");
    private static final NamespacedKey OUTPUTS = Keys.key("outputs");
    private static final NamespacedKey PROGRESS = Keys.key("progress");

    private @Nullable UUID entityId;
    private float cacheRot;
    private float rotSpeedTick = 200;
    private int progress;
    private @Nullable ItemStack input;
    private final List<ItemStack> outputs = new ArrayList<>();

    private @Nullable LivingEntity bound;
    /** Old 3x3 millstones: remove surplus barriers on the first tick */
    private boolean oldLayout;

    public MillstoneStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("millstone", ITEM, true, 1f, 1f, false, MillstoneStation::new, 2, 1);
    }

    // ---- State ----

    @Override
    protected void load(PersistentDataContainer state) {
        oldLayout = state.getOrDefault(LAYOUT, PersistentDataType.INTEGER, 1) < 2;
        String id = state.get(ENTITY, PersistentDataType.STRING);
        entityId = id == null ? null : UUID.fromString(id);
        cacheRot = fixRot(state.getOrDefault(CACHE_ROT, PersistentDataType.FLOAT, 0f));
        rotSpeedTick = state.getOrDefault(ROT_SPEED_TICK, PersistentDataType.FLOAT, 200f);
        progress = state.getOrDefault(PROGRESS, PersistentDataType.INTEGER, 0);
        byte[] inputBytes = state.get(INPUT, PersistentDataType.BYTE_ARRAY);
        input = inputBytes == null ? null : ItemStack.deserializeBytes(inputBytes);
        outputs.clear();
        for (byte[] bytes : state.getOrDefault(OUTPUTS, PersistentDataType.LIST.byteArrays(), List.of())) {
            outputs.add(ItemStack.deserializeBytes(bytes));
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(LAYOUT, PersistentDataType.INTEGER, 2);
        if (entityId != null) {
            state.set(ENTITY, PersistentDataType.STRING, entityId.toString());
        }
        state.set(CACHE_ROT, PersistentDataType.FLOAT, fixRot(cacheRot));
        state.set(ROT_SPEED_TICK, PersistentDataType.FLOAT, rotSpeedTick);
        state.set(PROGRESS, PersistentDataType.INTEGER, progress);
        if (input != null) {
            state.set(INPUT, PersistentDataType.BYTE_ARRAY, input.serializeAsBytes());
        }
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack output : outputs) {
            stored.add(output.serializeAsBytes());
        }
        state.set(OUTPUTS, PersistentDataType.LIST.byteArrays(), stored);
    }

    private static float fixRot(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0;
        }
        float rot = value % 360f;
        return rot < 0 ? rot + 360f : rot;
    }

    public boolean hasEntity() {
        return entityId != null;
    }

    public boolean isBoundTo(Entity entity) {
        return bound == entity || entity.getUniqueId().equals(entityId);
    }

    /**
     * Rotation angle in degrees (MillstoneBlockEntity#getRotation).
     * Modulo on ticks first to avoid precision loss on long-running worlds.
     */
    private float rotation() {
        long speed = Math.max((long) rotSpeedTick, 1);
        double degPerTick = 360.0 / speed;
        double current = cacheRot + (world().getGameTime() % speed) * degPerTick;
        return fixRot((float) current);
    }

    // ---- Display ----

    @Override
    public void updateDisplay() {
        showPart("base", MODEL_BASE, new Transformation(new Vector3f(MODEL_OFFSET), facing().blockRotation(), new Vector3f(MODEL_SCALE), new Quaternionf()));
        // Clicks on the whole 2x2 area (the root hitbox only sits on the station block)
        showHitbox("area", new org.bukkit.util.Vector(MODEL_OFFSET.x, 0, MODEL_OFFSET.z), 2.02f, 1.02f);
        updateWheel(0);
        updateItems();
    }

    /**
     * Wheel and shaft point towards the harnessed animal; the roller rolls around its own axis at the same time.
     */
    private void updateWheel(int interpolation) {
        float rot = hasEntity() ? rotation() : cacheRot;
        Quaternionf wheel = new Quaternionf().rotationY((float) Math.toRadians(180 + rot));
        showPart("wheel", MODEL_WHEEL, new Transformation(new Vector3f(MODEL_OFFSET), wheel, new Vector3f(MODEL_SCALE), new Quaternionf()), interpolation);
        float roll = hasEntity() ? (float) Math.toRadians(facing().dataValue() * 90 + rot) : 0;
        Vector3f rollCenter = wheel.transform(new Vector3f(ROLL_CENTER)).add(MODEL_OFFSET);
        showPart("roll", MODEL_ROLL, new Transformation(rollCenter, new Quaternionf(wheel).rotateZ(roll), new Vector3f(MODEL_SCALE), new Quaternionf()), interpolation);
    }

    /**
     * Ingredients or results lie in a circle on the stone (MillstoneBlockEntityRender#renderItems).
     */
    private void updateItems() {
        ItemStack shown = !outputs.isEmpty() ? outputs.getFirst() : input;
        int count = shown == null ? 0 : Math.min(shown.getAmount(), MAX_INPUT_COUNT);
        Random random = new Random(position().hashCode());
        for (int i = 0; i < count; i++) {
            Quaternionf around = new Quaternionf().rotationY((float) Math.toRadians(i * 45 + random.nextInt(15)));
            Vector3f translation = around.transform(new Vector3f(-0.5f, 0, -0.5f)).add(0, 0.375f, 0).add(MODEL_OFFSET);
            Quaternionf rotation = new Quaternionf(around).rotateY((float) Math.toRadians(random.nextInt(20)))
                    .rotateX((float) Math.toRadians(-(80 + random.nextInt(20))));
            showItemPart("item_" + i, shown, new Transformation(translation, rotation, new Vector3f(0.65f), new Quaternionf()), 0, null);
        }
        hidePartsStartingWith("item_", count);
    }

    @Override
    public String describe() {
        return "Mühlstein (" + (hasEntity() ? "Tier eingespannt" : "kein Tier") + ", Fortschritt " + progress + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        if (!outputs.isEmpty()) {
            state = Lang.get("hud.millstone.done").color(NamedTextColor.GREEN);
        } else if (input != null) {
            int percent = Math.round((Math.max(rotSpeedTick, 1) - progress) / Math.max(rotSpeedTick, 1) * 100);
            state = hasEntity() ? Lang.get("hud.millstone.grinding", Math.clamp(percent, 0, 100)).color(NamedTextColor.YELLOW)
                    : Lang.get("hud.millstone.no_animal").color(NamedTextColor.GOLD);
        } else {
            state = Lang.get(hasEntity() ? "hud.millstone.empty" : "hud.millstone.no_animal").color(NamedTextColor.GRAY);
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.millstone").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(state).build();
    }

    // ---- Interaction ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND) {
            return false;
        }
        return putItem(player.getInventory().getItemInMainHand());
    }

    /**
     * MillstoneBlockEntity#onPutItem: takes up to 8 items from the stack (also in creative mode, as in the mod).
     */
    public boolean putItem(ItemStack stack) {
        if (stack.isEmpty() || !outputs.isEmpty() || input != null) {
            return false;
        }
        if (plugin().getRecipes().findMillstone(stack) == null) {
            return false;
        }
        int amount = Math.min(stack.getAmount(), MAX_INPUT_COUNT);
        input = stack.asQuantity(amount);
        stack.subtract(amount);
        progress = Math.max(Math.round(rotSpeedTick), 1);
        world().playSound(center(), Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f, ThreadLocalRandom.current().nextFloat() * 0.2f + 0.9f);
        markDirty();
        updateItems();
        return true;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>(outputs);
        if (input != null) {
            contents.add(input);
        }
        return contents;
    }

    // ---- Animal ----

    /**
     * MillstoneBlockEntity#canBindEntity: at least 1 block tall, not a player, not riding and not ridden.
     */
    public static boolean canBind(LivingEntity entity) {
        if (entity instanceof Player || entity.getHeight() < 1 || entity.isInsideVehicle()) {
            return false;
        }
        // Saddled mounts that are currently being steered
        return !(entity instanceof AbstractHorse || entity.getType() == EntityType.PIG || entity.getType() == EntityType.STRIDER)
                || entity.getPassengers().isEmpty() || !(entity.getPassengers().getFirst() instanceof Player);
    }

    private void bind(LivingEntity entity) {
        if (!canBind(entity) || entity.isDead()) {
            return;
        }
        float currentRot = hasEntity() ? rotation() : cacheRot;
        entityId = entity.getUniqueId();
        bound = entity;
        rotSpeedTick = ROT_SPEED.getOrDefault(entity.getType(), DEFAULT_ROT_SPEED);
        long newSpeed = Math.max((long) rotSpeedTick, 1);
        double newDegPerTick = 360.0 / newSpeed;
        cacheRot = fixRot((float) (currentRot - (world().getGameTime() % newSpeed) * newDegPerTick));
        markDirty();
        updateWheel(0);
    }

    /**
     * Release the animal (e.g. on damage), the wheel stays at its current angle.
     */
    public void unbind() {
        float rot = rotation();
        entityId = null;
        bound = null;
        cacheRot = rot;
        markDirty();
        updateWheel(0);
    }

    /**
     * Bottom centre of the millstone (block edge between the four blocks).
     */
    private Location bottomCenter() {
        return position().bottomCenter(world()).add(MODEL_OFFSET.x, 0, MODEL_OFFSET.z);
    }

    private Location outputLocation() {
        BlockFace front = facing().face();
        return bottomCenter().add(front.getModX() * OUTPUT_DISTANCE, 0.1, front.getModZ() * OUTPUT_DISTANCE);
    }

    /**
     * Remove surplus barriers of an old 3x3 millstone.
     */
    private void migrateLayout() {
        oldLayout = false;
        java.util.List<de.emrion.kaleidoscopeCookery.station.BlockKey> now = occupied();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                de.emrion.kaleidoscopeCookery.station.BlockKey key = position().relative(dx, 0, dz);
                org.bukkit.block.Block block = key.block();
                if (!now.contains(key) && block != null && block.getType() == org.bukkit.Material.BARRIER && context.manager().at(key) == null) {
                    block.setType(org.bukkit.Material.AIR, false);
                }
            }
        }
        markDirty();
    }

    /**
     * MillstoneBlock#stepOn: living entities standing on the millstone.
     */
    private void checkStepOn() {
        BoundingBox top = new BoundingBox(position().x(), position().y() + 0.9, position().z(),
                position().x() + 2, position().y() + 1.2, position().z() + 2);
        for (Entity entity : world().getNearbyEntities(top, e -> e instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) entity;
            if (living.isOnGround() && living.getLocation().getY() >= position().y() + 0.9 && canBind(living)) {
                bind(living);
                return;
            }
        }
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return true;
    }

    @Override
    public void tick(long serverTick) {
        if (oldLayout) {
            migrateLayout();
        }
        long gameTime = world().getGameTime();
        if (entityId == null) {
            if (gameTime % 5 == 4) {
                checkStepOn();
            }
            return;
        }

        // Eject the results at the front
        if (gameTime % 20 == 9 && !outputs.isEmpty()) {
            Location drop = outputLocation();
            for (ItemStack output : outputs) {
                world().dropItem(drop, output, item -> item.setVelocity(new org.bukkit.util.Vector()));
            }
            outputs.clear();
            progress = 0;
            markDirty();
            updateItems();
        }

        float rot = rotation();
        Location center = bottomCenter();
        if (bound == null) {
            // After loading: find the animal again
            if (world().getEntity(entityId) instanceof LivingEntity living && !living.isDead() && living.getHeight() >= 1
                    && living.getLocation().distanceSquared(center) < MAX_DISTANCE_SQUARED && canBind(living)) {
                bound = living;
            } else {
                entityId = null;
                cacheRot = 0;
                markDirty();
                updateWheel(0);
                return;
            }
        } else if (!bound.isValid() || bound.isDead() || !bound.getWorld().equals(world())
                || bound.getLocation().distanceSquared(center) >= MAX_DISTANCE_SQUARED
                || bound.getFallDistance() > 0.5f || isInWall(bound)) {
            unbind();
            return;
        }

        // Lead it in a circle
        double radians = Math.toRadians(rot);
        Location target = center.clone().add(2 * Math.sin(radians), 0, 2 * Math.cos(radians));
        target.setYaw(-rot - 90);
        target.setPitch(0);
        bound.teleport(target, TeleportFlag.EntityState.RETAIN_PASSENGERS);

        if (bound.getTicksLived() % 10 == 0 && outputs.isEmpty() && input == null && progress <= 0) {
            pullItems(bound);
        }

        if (gameTime % 5 == 2) {
            spawnParticles(rot);
        }
        if (gameTime % 25 == 0) {
            world().playSound(center(), "kaleidoscope_cookery:block.millstone", SoundCategory.BLOCKS, 0.5f, ThreadLocalRandom.current().nextFloat() * 0.2f + 0.9f);
        }

        if (progress > 0 && outputs.isEmpty()) {
            progress--;
            if (progress % 10 == 0) {
                markDirty();
            }
        }
        if (progress <= 0 && input != null && outputs.isEmpty()) {
            grind();
        }

        if (serverTick % WHEEL_UPDATE_TICKS == 0) {
            updateWheel(WHEEL_UPDATE_TICKS);
        }
    }

    private static boolean isInWall(LivingEntity entity) {
        return entity.getEyeLocation().getBlock().getType().isOccluding();
    }

    /**
     * The animal adds items from its inventory, otherwise items lying on top of the millstone.
     */
    private void pullItems(LivingEntity entity) {
        if (entity instanceof InventoryHolder holder) {
            Inventory inventory = holder.getInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemStack copy = stack.clone();
                if (putItem(copy)) {
                    inventory.setItem(i, copy.isEmpty() ? null : copy);
                    return;
                }
            }
        }
        Location middle = bottomCenter();
        BoundingBox above = new BoundingBox(middle.getX() - 0.8125, position().y() + 1, middle.getZ() - 0.8125,
                middle.getX() + 0.8125, position().y() + 1.5, middle.getZ() + 0.8125);
        for (Entity entity1 : world().getNearbyEntities(above, e -> e instanceof Item)) {
            Item item = (Item) entity1;
            ItemStack copy = item.getItemStack().clone();
            if (putItem(copy)) {
                if (copy.isEmpty()) {
                    item.remove();
                } else {
                    item.setItemStack(copy);
                }
                return;
            }
        }
    }

    private void spawnParticles(float rot) {
        ItemStack shown = !outputs.isEmpty() ? outputs.getFirst() : input;
        if (shown == null) {
            return;
        }
        double radians = Math.toRadians(rot);
        Location location = bottomCenter().add(Math.sin(radians), 1, Math.cos(radians));
        if (shown.getType().isBlock()) {
            world().spawnParticle(Particle.BLOCK, location, 5, 0.1, 0.1, 0.1, 0.05, shown.getType().createBlockData());
        } else {
            world().spawnParticle(Particle.ITEM, location, 5, 0.1, 0.1, 0.1, 0.05, shown.asOne());
        }
    }

    /**
     * One round done: every added item yields the outputs with their chance.
     */
    private void grind() {
        ItemStack ground = input;
        input = null;
        MillstoneRecipe recipe = ground == null ? null : plugin().getRecipes().findMillstone(ground);
        if (recipe == null) {
            if (ground != null) {
                outputs.add(ground);
            }
        } else {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < ground.getAmount(); i++) {
                for (MillstoneRecipe.Output output : recipe.results()) {
                    if (random.nextDouble() < output.chance()) {
                        ItemStack result = output.result().create(items());
                        if (result != null) {
                            addOutput(result);
                        }
                    }
                }
            }
        }
        markDirty();
        updateItems();
    }

    /**
     * Like ItemHandlerHelper#insertItemStacked with 4 slots; whatever does not fit drops out at the front right away.
     */
    private void addOutput(ItemStack stack) {
        for (ItemStack output : outputs) {
            if (output.isSimilar(stack) && output.getAmount() < output.getMaxStackSize()) {
                int moved = Math.min(stack.getAmount(), output.getMaxStackSize() - output.getAmount());
                output.add(moved);
                stack.subtract(moved);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        if (outputs.size() < OUTPUT_SLOTS) {
            outputs.add(stack);
            return;
        }
        world().dropItem(outputLocation(), stack);
    }
}
