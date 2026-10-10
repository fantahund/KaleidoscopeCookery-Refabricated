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
    private final Map<String, PartLook> partLooks = new HashMap<>();
    private final Map<String, Interaction> hitboxes = new HashMap<>();
    private boolean dirty;
    private boolean removed;
    private @Nullable Vector clickPosition;

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
     * Rechtsklick mit einem Stations-Item auf diese Station, bevor normal platziert wird (z. B. Dampfkörbe stapeln).
     *
     * @return true, wenn die Station das Item selbst verarbeitet hat
     */
    public boolean interceptPlacement(Player player, ItemStack held) {
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
     * Geplanter Einzel-Tick (StationManager#schedule).
     */
    public void scheduledTick(long serverTick) {
    }

    /**
     * Station wurde geladen bzw. gesetzt und ist registriert (z. B. ersten geplanten Tick anlegen).
     */
    protected void onAttached() {
    }

    /**
     * Item, das die Station beim Abbauen (außer im Kreativmodus) zurückgibt. null = keins.
     */
    public @Nullable ItemStack selfDrop() {
        return items().create(context.type().item(), 1);
    }

    /**
     * Darf die Station an ihrer Stelle bleiben (zusätzlich zum festen Block darunter, siehe StationType#needsSupport)?
     */
    public boolean canSurvive() {
        return true;
    }

    /**
     * Linksklick baut ab, ohne Schleichen (Pflanzen).
     */
    public boolean breaksOnAttack() {
        return false;
    }

    /**
     * Beute beim Abbauen durch einen Spieler (z. B. mit Glück).
     */
    public List<ItemStack> contents(@Nullable Player breaker) {
        return contents();
    }

    /**
     * Inhalt, der beim Abbauen fallen gelassen wird (ohne das Stations-Item selbst).
     */
    public List<ItemStack> contents() {
        return List.of();
    }

    /**
     * Statuszeile, die ein Spieler in der Actionbar sieht, solange er die Station anschaut. null = keine.
     */
    public net.kyori.adventure.text.@Nullable Component hud(Player viewer) {
        return null;
    }

    /**
     * Alle Blöcke, die die Station belegt (Standard: nur ihr eigener).
     */
    public List<BlockKey> occupied() {
        return context.type().footprint(position());
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
     * Aussehen eines Teils: welches Item (bzw. Modell), wie dargestellt, wie hell.
     *
     * @param brightness null = Licht der Umgebung
     */
    public record PartLook(ItemStack stack, ItemDisplay.ItemDisplayTransform displayTransform, Transformation transformation,
                              Display.@Nullable Brightness brightness) {
    }

    /**
     * Teil anzeigen bzw. aktualisieren. Displays werden nur geändert, wenn sich etwas unterscheidet.
     *
     * @param interpolationTicks 0 = sofort, sonst flüssiger Übergang der Transformation
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
     * Der Renderer von ItemDisplays dreht das Modell um 180° um die Y-Achse. Die Mod-Renderer tun das nicht –
     * deshalb hier über die rechte Rotation (wirkt direkt aufs Modell) wieder zurückdrehen.
     */
    private static final Quaternionf RENDERER_CORRECTION = new Quaternionf().rotationY((float) Math.PI);

    private static PartLook corrected(PartLook look) {
        Transformation t = look.transformation();
        Transformation fixed = new Transformation(t.getTranslation(), t.getLeftRotation(), t.getScale(), new Quaternionf(t.getRightRotation()).mul(RENDERER_CORRECTION));
        return new PartLook(look.stack(), look.displayTransform(), fixed, look.brightness());
    }

    /**
     * Modell aus dem Resourcepack anzeigen (Blockmodelle, Schnittstufen ...).
     */
    protected void showPart(String name, Key model, Transformation transformation, int interpolationTicks) {
        showPart(name, new PartLook(modelStack(model), ItemDisplay.ItemDisplayTransform.NONE, transformation, null), interpolationTicks);
    }

    protected void showPart(String name, Key model, Transformation transformation) {
        showPart(name, model, transformation, 0);
    }

    /**
     * Echtes Item anzeigen (Zutaten), wie ein Item im Rahmen (FIXED).
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
     * Alle Teile entfernen, deren Name mit prefix beginnt (z. B. "input_").
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
     * Zusätzliche Klick-Hitbox (z. B. zweite Hälfte eines 1x2-Gerichts). Klicks darauf gehen an diese Station.
     *
     * @param offset Mitte der Unterseite relativ zur Blockmitte unten der Station
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
            interaction.setVisibleByDefault(false); // nur Spielern in Reichweite gezeigt (StationManager#updateVisibility)
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
     * Render-Distanz der Displays (view_range: Vielfaches von 64 Blöcken, mal der Entity-Distanz des Clients).
     * Pflanzen stehen zu Hunderten auf Feldern und nutzen weniger.
     */
    protected float viewRange() {
        return 1.0f;
    }

    /** Höchstens bis zur Simulationsdistanz der Welt (dort wachsen Pflanzen und arbeiten Stationen) */
    private float effectiveViewRange() {
        return Math.min(viewRange(), world().getSimulationDistance() * 16f / 64f);
    }

    /**
     * Klick-Entities dieser Station (Wurzel und zusätzliche Hitboxen).
     */
    public List<Interaction> interactions() {
        List<Interaction> result = new java.util.ArrayList<>(hitboxes.size() + 1);
        result.add(root());
        result.addAll(hitboxes.values());
        return result;
    }

    /**
     * Größe der Displays fürs Culling (große Modelle wie der Mühlstein brauchen mehr).
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
     * Item für ein Display: nur Aussehen (Modell, Glitzern), ohne Namen/PDC – spart Daten beim Senden.
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
     * Klickposition des aktuellen Rechtsklicks relativ zur Blockmitte unten der Station (nur während onInteract), sonst null.
     */
    protected @Nullable Vector clickPosition() {
        return clickPosition;
    }

    // ---- intern (StationManager) ----

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
     * Alle Entities entfernen (Abbauen).
     */
    /**
     * Wird beim Entfernen der Station aufgerufen (Abbauen, /kc repair ...), bevor die Entities verschwinden.
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
