package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A station in the world: Interaction entity (clicks, state in the PDC) + ItemDisplays as parts
 * (model, ingredients, liquid ...) + optionally a barrier block.
 * <p>
 * Subclasses keep their state in fields, load/save it via {@link #load}/{@link #save}
 * and build the visuals in {@link #updateDisplay()} via {@link #showPart}/{@link #hidePart}.
 * Displays are only touched when the model or transformation really changes.
 */
public abstract class Station {

    protected final StationContext context;
    private final Map<String, ItemDisplay> parts = new HashMap<>();
    private final Map<String, PartLook> partLooks = new HashMap<>();
    private final Map<String, Interaction> hitboxes = new HashMap<>();
    private boolean dirty;
    private boolean removed;
    private @Nullable Vector clickPosition;

    protected Station(StationContext context) {
        this.context = context;
    }

    // ---- Life cycle (to be implemented by subclasses) ----

    /**
     * Read the state from the PDC (empty container for a new station).
     */
    protected abstract void load(PersistentDataContainer state);

    /**
     * Write the state into the (empty) PDC.
     */
    protected abstract void save(PersistentDataContainer state);

    /**
     * Show all parts matching the state.
     */
    public abstract void updateDisplay();

    /**
     * Right-click (main hand).
     *
     * @return true if something happened
     */
    public boolean onInteract(Player player, EquipmentSlot hand) {
        return false;
    }

    /**
     * Right-click on this station with a station item, before it is placed normally (e.g. stacking steamers).
     *
     * @return true if the station handled the item itself
     */
    public boolean interceptPlacement(Player player, ItemStack held) {
        return false;
    }

    /**
     * Left click without sneaking (sneaking + left click breaks).
     *
     * @return true if something happened
     */
    public boolean onAttack(Player player) {
        return false;
    }

    /**
     * Called every tick as long as {@link #isTicking()} is true and the chunk is loaded.
     */
    public void tick(long serverTick) {
    }

    public boolean isTicking() {
        return false;
    }

    /**
     * Scheduled single tick (StationManager#schedule).
     */
    public void scheduledTick(long serverTick) {
    }

    /**
     * The station was loaded or placed and is registered (e.g. schedule the first tick).
     */
    protected void onAttached() {
    }

    /**
     * Item the station returns when broken (except in creative mode). null = none.
     */
    public @Nullable ItemStack selfDrop() {
        return items().create(context.type().item(), 1);
    }

    /**
     * May the station stay where it is (in addition to the solid block below, see StationType#needsSupport)?
     */
    public boolean canSurvive() {
        return true;
    }

    /**
     * Left click breaks it without sneaking (crops).
     */
    public boolean breaksOnAttack() {
        return false;
    }

    /**
     * Loot when broken by a player (e.g. with Fortune).
     */
    public List<ItemStack> contents(@Nullable Player breaker) {
        return contents();
    }

    /**
     * Contents dropped when broken (without the station item itself).
     */
    public List<ItemStack> contents() {
        return List.of();
    }

    /**
     * Status line a player sees in the action bar while looking at the station. null = none.
     */
    public net.kyori.adventure.text.@Nullable Component hud(Player viewer) {
        return null;
    }

    /**
     * All blocks the station occupies (default: only its own).
     */
    public List<BlockKey> occupied() {
        return context.type().footprint(position());
    }

    /**
     * Line for /kc info and holograms.
     */
    public String describe() {
        return context.type().id();
    }

    // ---- Helpers for subclasses ----

    public StationContext context() {
        return context;
    }

    public BlockKey position() {
        return context.position();
    }

    public Facing facing() {
        return context.facing();
    }

    public Interaction root() {
        return context.root();
    }

    public World world() {
        return context.root().getWorld();
    }

    protected KaleidoscopeCookery plugin() {
        return context.manager().plugin();
    }

    protected CookeryItems items() {
        return plugin().getItems();
    }

    /**
     * Centre of the block.
     */
    public Location center() {
        return new Location(world(), position().x() + 0.5, position().y() + 0.5, position().z() + 0.5);
    }

    /**
     * State changed: written at the latest after one second, on unload and when the world is saved.
     */
    protected void markDirty() {
        dirty = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    /**
     * Look of a part: which item (or model), how it is displayed, how bright.
     *
     * @param brightness null = ambient light
     */
    public record PartLook(ItemStack stack, ItemDisplay.ItemDisplayTransform displayTransform, Transformation transformation,
                              Display.@Nullable Brightness brightness) {
    }

    /**
     * Show or update a part. Displays are only changed if something differs.
     *
     * @param interpolationTicks 0 = immediately, otherwise a smooth transition of the transformation
     */
    protected void showPart(String name, PartLook look, int interpolationTicks) {
        look = corrected(look);
        ItemDisplay display = parts.get(name);
        if (display == null || !display.isValid()) {
            parts.put(name, spawnPart(name, look));
            partLooks.put(name, look);
            return;
        }
        PartLook old = partLooks.get(name);
        if (old == null || !old.stack().equals(look.stack())) {
            display.setItemStack(look.stack());
        }
        if (old == null || old.displayTransform() != look.displayTransform()) {
            display.setItemDisplayTransform(look.displayTransform());
        }
        if (old == null || !Objects.equals(old.brightness(), look.brightness())) {
            display.setBrightness(look.brightness());
        }
        if (old == null || !old.transformation().equals(look.transformation())) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(interpolationTicks);
            display.setTransformation(look.transformation());
        }
        partLooks.put(name, look);
    }

    /**
     * The ItemDisplay renderer rotates the model by 180° around the Y axis. The mods' renderers do not -
     * so rotate it back here via the right rotation (applies directly to the model).
     */
    private static final Quaternionf RENDERER_CORRECTION = new Quaternionf().rotationY((float) Math.PI);

    private static PartLook corrected(PartLook look) {
        Transformation t = look.transformation();
        Transformation fixed = new Transformation(t.getTranslation(), t.getLeftRotation(), t.getScale(), new Quaternionf(t.getRightRotation()).mul(RENDERER_CORRECTION));
        return new PartLook(look.stack(), look.displayTransform(), fixed, look.brightness());
    }

    /**
     * Show a model from the resource pack (block models, cutting stages ...).
     */
    protected void showPart(String name, Key model, Transformation transformation, int interpolationTicks) {
        showPart(name, new PartLook(modelStack(model), ItemDisplay.ItemDisplayTransform.NONE, transformation, null), interpolationTicks);
    }

    protected void showPart(String name, Key model, Transformation transformation) {
        showPart(name, model, transformation, 0);
    }

    /**
     * Show a real item (ingredients), like an item in a frame (FIXED).
     */
    protected void showItemPart(String name, ItemStack stack, Transformation transformation, int interpolationTicks, Display.@Nullable Brightness brightness) {
        showPart(name, new PartLook(displayStack(stack), ItemDisplay.ItemDisplayTransform.FIXED, transformation, brightness), interpolationTicks);
    }

    protected void hidePart(String name) {
        ItemDisplay display = parts.remove(name);
        partLooks.remove(name);
        if (display != null) {
            display.remove();
        }
    }

    /**
     * Remove all parts whose name starts with prefix (e.g. "input_").
     */
    protected void hidePartsStartingWith(String prefix, int keep) {
        for (String name : List.copyOf(parts.keySet())) {
            if (name.startsWith(prefix)) {
                try {
                    if (Integer.parseInt(name.substring(prefix.length())) < keep) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                }
                hidePart(name);
            }
        }
    }

    protected boolean hasPart(String name) {
        ItemDisplay display = parts.get(name);
        return display != null && display.isValid();
    }

    protected @Nullable PartLook partLook(String name) {
        return partLooks.get(name);
    }

    /**
     * Additional click hitbox (e.g. the second half of a 1x2 dish). Clicks on it go to this station.
     *
     * @param offset centre of the bottom face relative to the bottom centre of the station block
     */
    protected void showHitbox(String name, Vector offset, float width, float height) {
        Interaction hitbox = hitboxes.get(name);
        Location location = position().bottomCenter(world()).add(offset);
        if (hitbox != null && hitbox.isValid()) {
            if (hitbox.getInteractionWidth() != width || hitbox.getInteractionHeight() != height) {
                hitbox.setInteractionWidth(width);
                hitbox.setInteractionHeight(height);
            }
            return;
        }
        String owner = root().getUniqueId().toString();
        hitbox = world().spawn(location, Interaction.class, interaction -> {
            interaction.setVisibleByDefault(false); // only shown to players in range (StationManager#updateVisibility)
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
            interaction.setPersistent(true);
            interaction.getPersistentDataContainer().set(Keys.STATION_OWNER, PersistentDataType.STRING, owner);
            interaction.getPersistentDataContainer().set(Keys.STATION_PART, PersistentDataType.STRING, name);
        });
        hitboxes.put(name, hitbox);
        context.manager().registerHitbox(hitbox, this);
        context.manager().revealNear(this);
    }

    protected void hideHitbox(String name) {
        Interaction hitbox = hitboxes.remove(name);
        if (hitbox != null) {
            context.manager().unregisterHitbox(hitbox);
            hitbox.remove();
        }
    }

    /**
     * Block model oriented as in the blockstate.
     */
    protected Transformation blockTransform(float offsetY) {
        return new Transformation(new Vector3f(0, offsetY, 0), facing().blockRotation(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    /**
     * Model oriented as in the mods' block entity renderers.
     */
    protected Transformation rendererTransform(float offsetY) {
        return new Transformation(new Vector3f(0, offsetY, 0), facing().rendererRotation(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    private ItemDisplay spawnPart(String name, PartLook look) {
        String owner = root().getUniqueId().toString();
        return world().spawn(center(), ItemDisplay.class, display -> {
            display.setItemStack(look.stack());
            display.setItemDisplayTransform(look.displayTransform());
            display.setTransformation(look.transformation());
            display.setBrightness(look.brightness());
            display.setPersistent(true);
            display.setViewRange(effectiveViewRange());
            display.setDisplayWidth(displaySize());
            display.setDisplayHeight(displaySize());
            display.getPersistentDataContainer().set(Keys.STATION_OWNER, PersistentDataType.STRING, owner);
            display.getPersistentDataContainer().set(Keys.STATION_PART, PersistentDataType.STRING, name);
        });
    }

    /**
     * Render distance of the displays (view_range: multiple of 64 blocks, times the client's entity distance).
     * Crops stand on fields by the hundreds and use less.
     */
    protected float viewRange() {
        return 1.0f;
    }

    /** At most up to the simulation distance of the world (crops grow and stations work there) */
    private float effectiveViewRange() {
        return Math.min(viewRange(), world().getSimulationDistance() * 16f / 64f);
    }

    /**
     * Click entities of this station (root and additional hitboxes).
     */
    public List<Interaction> interactions() {
        List<Interaction> result = new java.util.ArrayList<>(hitboxes.size() + 1);
        result.add(root());
        result.addAll(hitboxes.values());
        return result;
    }

    /**
     * Size of the displays for culling (large models like the millstone need more).
     */
    protected float displaySize() {
        return 1.5f + context.type().width() - 1 + context.type().height() - 1;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack modelStack(Key model) {
        ItemStack stack = ItemStack.of(CookeryItems.BASE_MATERIAL);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    /**
     * Item for a display: only the look (model, glint), without name/PDC - saves data when sending.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack displayStack(ItemStack stack) {
        Key model = stack.getData(DataComponentTypes.ITEM_MODEL);
        ItemStack result = modelStack(model != null ? DisplayModels.replace(CookeryItems.typeOf(stack), model) : stack.getType().key());
        if (Boolean.TRUE.equals(stack.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)) || !stack.getEnchantments().isEmpty()) {
            result.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return result;
    }

    /**
     * Click position of the current right-click relative to the bottom centre of the station block (only during onInteract), otherwise null.
     */
    protected @Nullable Vector clickPosition() {
        return clickPosition;
    }

    // ---- internal (StationManager) ----

    void setClickPosition(@Nullable Vector clickPosition) {
        this.clickPosition = clickPosition;
    }

    void adoptPart(String name, ItemDisplay display) {
        if (display.getViewRange() != effectiveViewRange()) {
            display.setViewRange(effectiveViewRange());
        }
        ItemDisplay previous = parts.put(name, display);
        if (previous != null && previous != display) {
            previous.remove();
        }
        partLooks.put(name, new PartLook(display.getItemStack(), display.getItemDisplayTransform(), display.getTransformation(), display.getBrightness()));
    }

    void adoptHitbox(String name, Interaction hitbox) {
        hitbox.setVisibleByDefault(false);
        Interaction previous = hitboxes.put(name, hitbox);
        if (previous != null && previous != hitbox) {
            context.manager().unregisterHitbox(previous);
            previous.remove();
        }
        context.manager().registerHitbox(hitbox, this);
    }

    void loadState() {
        PersistentDataContainer pdc = root().getPersistentDataContainer();
        PersistentDataContainer state = pdc.get(Keys.STATION_STATE, PersistentDataType.TAG_CONTAINER);
        load(state != null ? state : pdc.getAdapterContext().newPersistentDataContainer());
    }

    void flush() {
        if (!dirty || removed || !root().isValid()) {
            return;
        }
        PersistentDataContainer pdc = root().getPersistentDataContainer();
        PersistentDataContainer state = pdc.getAdapterContext().newPersistentDataContainer();
        save(state);
        pdc.set(Keys.STATION_STATE, PersistentDataType.TAG_CONTAINER, state);
        dirty = false;
    }

    void forceDirty() {
        dirty = true;
    }

    /**
     * Remove all entities (breaking).
     */
    /**
     * Called when the station is removed (breaking, /kc repair ...), before the entities disappear.
     */
    protected void onRemoved() {
    }

    void destroy() {
        onRemoved();
        removed = true;
        for (ItemDisplay display : parts.values()) {
            display.remove();
        }
        parts.clear();
        for (Interaction hitbox : hitboxes.values()) {
            context.manager().unregisterHitbox(hitbox);
            hitbox.remove();
        }
        hitboxes.clear();
        root().remove();
    }

    Map<String, ItemDisplay> parts() {
        return parts;
    }

    Map<String, Interaction> hitboxes() {
        return hitboxes;
    }
}
