package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Eine Station in der Welt: Interaction-Entity (Klicks, Zustand im PDC) + ItemDisplays als Teile
 * (Modell, Zutaten, Flüssigkeit ...) + optional ein Barriere-Block.
 * <p>
 * Unterklassen halten ihren Zustand in Feldern, laden/speichern ihn über {@link #load}/{@link #save}
 * und bauen die Darstellung in {@link #updateDisplay()} über {@link #showPart}/{@link #hidePart} auf.
 * Displays werden nur angefasst, wenn sich Modell oder Transformation wirklich ändern.
 */
public abstract class Station {

    protected final StationContext context;
    private final Map<String, ItemDisplay> parts = new HashMap<>();
    private final Map<String, Key> partModels = new HashMap<>();
    private final Map<String, Transformation> partTransforms = new HashMap<>();
    private boolean dirty;
    private boolean removed;

    protected Station(StationContext context) {
        this.context = context;
    }

    // ---- Lebenszyklus (von Unterklassen umzusetzen) ----

    /**
     * Zustand aus dem PDC lesen (leerer Container bei einer neuen Station).
     */
    protected abstract void load(PersistentDataContainer state);

    /**
     * Zustand in den (leeren) PDC schreiben.
     */
    protected abstract void save(PersistentDataContainer state);

    /**
     * Alle Teile passend zum Zustand anzeigen.
     */
    public abstract void updateDisplay();

    /**
     * Rechtsklick (Haupthand).
     *
     * @return true, wenn etwas passiert ist
     */
    public boolean onInteract(Player player, EquipmentSlot hand) {
        return false;
    }

    /**
     * Linksklick ohne Schleichen (Schleichen + Linksklick baut ab).
     *
     * @return true, wenn etwas passiert ist
     */
    public boolean onAttack(Player player) {
        return false;
    }

    /**
     * Wird jeden Tick aufgerufen, solange {@link #isTicking()} true ist und der Chunk geladen ist.
     */
    public void tick(long serverTick) {
    }

    public boolean isTicking() {
        return false;
    }

    /**
     * Inhalt, der beim Abbauen fallen gelassen wird (ohne das Stations-Item selbst).
     */
    public List<ItemStack> contents() {
        return List.of();
    }

    /**
     * Zeile für /kc info und Hologramme.
     */
    public String describe() {
        return context.type().id();
    }

    // ---- Hilfen für Unterklassen ----

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
     * Mittelpunkt des Blocks.
     */
    public Location center() {
        return new Location(world(), position().x() + 0.5, position().y() + 0.5, position().z() + 0.5);
    }

    /**
     * Zustand geändert: wird spätestens nach einer Sekunde, beim Entladen und beim Speichern der Welt geschrieben.
     */
    protected void markDirty() {
        dirty = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    /**
     * Teil anzeigen bzw. aktualisieren.
     *
     * @param model                 Item-Model-Definition im Resourcepack
     * @param transformation        relativ zum Blockmittelpunkt
     * @param interpolationTicks    0 = sofort, sonst flüssiger Übergang
     */
    protected void showPart(String name, Key model, Transformation transformation, int interpolationTicks) {
        ItemDisplay display = parts.get(name);
        if (display == null || !display.isValid()) {
            display = spawnPart(name, model, transformation);
            parts.put(name, display);
            partModels.put(name, model);
            partTransforms.put(name, transformation);
            return;
        }
        if (!model.equals(partModels.get(name))) {
            display.setItemStack(modelStack(model));
            partModels.put(name, model);
        }
        if (!transformation.equals(partTransforms.get(name))) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(interpolationTicks);
            display.setTransformation(transformation);
            partTransforms.put(name, transformation);
        }
    }

    protected void showPart(String name, Key model, Transformation transformation) {
        showPart(name, model, transformation, 0);
    }

    /**
     * Teil mit einem echten Item anzeigen (z. B. Zutat ohne eigenes Modell).
     */
    protected void showItemPart(String name, ItemStack stack, Transformation transformation) {
        Key model = stack.getDataOrDefault(DataComponentTypes.ITEM_MODEL, stack.getType().key());
        showPart(name, Objects.requireNonNull(model), transformation, 0);
    }

    protected void hidePart(String name) {
        ItemDisplay display = parts.remove(name);
        partModels.remove(name);
        partTransforms.remove(name);
        if (display != null) {
            display.remove();
        }
    }

    protected boolean hasPart(String name) {
        ItemDisplay display = parts.get(name);
        return display != null && display.isValid();
    }

    /**
     * Blockmodell wie im Blockstate ausgerichtet.
     */
    protected Transformation blockTransform(float offsetY) {
        return new Transformation(new Vector3f(0, offsetY, 0), facing().blockRotation(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    /**
     * Modell wie in den Block-Entity-Renderern der Mods ausgerichtet.
     */
    protected Transformation rendererTransform(float offsetY) {
        return new Transformation(new Vector3f(0, offsetY, 0), facing().rendererRotation(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    private ItemDisplay spawnPart(String name, Key model, Transformation transformation) {
        String owner = root().getUniqueId().toString();
        return world().spawn(center(), ItemDisplay.class, display -> {
            display.setItemStack(modelStack(model));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transformation);
            display.setPersistent(true);
            display.setDisplayWidth(1.5f);
            display.setDisplayHeight(1.5f);
            display.getPersistentDataContainer().set(Keys.STATION_OWNER, PersistentDataType.STRING, owner);
            display.getPersistentDataContainer().set(Keys.STATION_PART, PersistentDataType.STRING, name);
        });
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack modelStack(Key model) {
        ItemStack stack = ItemStack.of(CookeryItems.BASE_MATERIAL);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    // ---- intern (StationManager) ----

    void adoptPart(String name, ItemDisplay display) {
        ItemDisplay previous = parts.put(name, display);
        if (previous != null && previous != display) {
            previous.remove();
        }
        partModels.put(name, display.getItemStack().getDataOrDefault(DataComponentTypes.ITEM_MODEL, null));
        partTransforms.put(name, display.getTransformation());
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
     * Alle Entities entfernen (Abbauen).
     */
    void destroy() {
        removed = true;
        for (ItemDisplay display : parts.values()) {
            display.remove();
        }
        parts.clear();
        root().remove();
    }

    @Nullable
    Map<String, ItemDisplay> parts() {
        return parts;
    }
}
