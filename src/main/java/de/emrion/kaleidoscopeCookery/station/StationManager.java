package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.stove.StoveStation;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Verwaltet alle geladenen Stationen: Laden/Entladen mit den Chunks, ein zentraler Tick-Task,
 * Platzieren, Abbauen und Reparatur.
 */
public class StationManager implements Listener {

    public static final int STATE_VERSION = 1;
    private static final Set<Material> HEAT_SOURCES = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK);
    private static final int FLUSH_INTERVAL = 20;
    private static final int HUD_INTERVAL = 10;
    private static final double HUD_RANGE = 5;

    private final KaleidoscopeCookery plugin;
    private final Map<String, StationType> types = new LinkedHashMap<>();
    private final Map<Key, StationType> typesByItem = new HashMap<>();
    private final Map<UUID, Station> byRoot = new HashMap<>();
    private final Map<BlockKey, Station> byBlock = new HashMap<>();
    /** Zusätzliche Hitboxen (Interaction mit STATION_OWNER) -> Station */
    private final Map<UUID, Station> byHitbox = new HashMap<>();
    private final Set<Station> ticking = new LinkedHashSet<>();
    private final Map<UUID, Long> hudPausedUntil = new HashMap<>();
    private BukkitTask task;
    private long serverTick;

    public StationManager(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public KaleidoscopeCookery plugin() {
        return plugin;
    }

    public void register(StationType type) {
        types.put(type.id(), type);
        typesByItem.put(type.item(), type);
    }

    public @Nullable StationType typeByItem(@Nullable Key item) {
        return item == null ? null : typesByItem.get(item);
    }

    public @Nullable StationType type(String id) {
        return types.get(id);
    }

    public Collection<StationType> types() {
        return Collections.unmodifiableCollection(types.values());
    }

    public void start() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isEntitiesLoaded()) {
                    attach(List.of(chunk.getEntities()));
                }
            }
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flushAll();
        byRoot.clear();
        byBlock.clear();
        byHitbox.clear();
        ticking.clear();
    }

    // ---- Tick ----

    private void tick() {
        serverTick++;
        for (Station station : new ArrayList<>(ticking)) {
            if (station.isRemoved()) {
                ticking.remove(station);
                continue;
            }
            try {
                station.tick(serverTick);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler im Tick der Station " + station.describe() + " bei " + station.position(), e);
            }
        }
        if (serverTick % FLUSH_INTERVAL == 0) {
            flushAll();
        }
        if (serverTick % HUD_INTERVAL == 0) {
            updateHud();
        }
    }

    /**
     * Statusanzeige: Wer eine Station anschaut (max. 5 Blöcke), sieht ihren Status in der Actionbar.
     */
    private void updateHud() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (byRoot.isEmpty()) {
                return;
            }
            Long paused = hudPausedUntil.get(player.getUniqueId());
            if (paused != null && paused > serverTick) {
                continue;
            }
            RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), HUD_RANGE, 0,
                    entity -> entity instanceof Interaction && byEntity(entity) != null);
            if (hit == null || hit.getHitEntity() == null) {
                continue;
            }
            RayTraceResult block = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), HUD_RANGE,
                    FluidCollisionMode.NEVER, true);
            if (block != null && block.getHitPosition().distanceSquared(player.getEyeLocation().toVector())
                    < hit.getHitPosition().distanceSquared(player.getEyeLocation().toVector()) - 0.05
                    && at(block.getHitBlock()) == null) {
                continue; // Block davor
            }
            Station station = byEntity(hit.getHitEntity());
            Component hud = station == null ? null : station.hud(player);
            if (hud != null) {
                player.sendActionBar(hud);
            }
        }
    }

    public long serverTick() {
        return serverTick;
    }

    /**
     * Nachricht einer Station in der Actionbar: Statusanzeige 2 s lang nicht darüberschreiben.
     */
    public void actionBar(Player player, Component message) {
        player.sendActionBar(message);
        hudPausedUntil.put(player.getUniqueId(), serverTick + 40);
    }

    /**
     * Station tickt (oder nicht mehr), z. B. nach einer Zustandsänderung.
     */
    public void updateTicking(Station station) {
        if (station.isTicking() && !station.isRemoved()) {
            ticking.add(station);
        } else {
            ticking.remove(station);
        }
    }

    public void flushAll() {
        for (Station station : byRoot.values()) {
            station.flush();
        }
    }

    // ---- Laden / Entladen ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        attach(event.getEntities());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            Station station = byRoot.get(entity.getUniqueId());
            if (station != null) {
                station.flush();
                detach(station);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldSave(WorldSaveEvent event) {
        for (Station station : byRoot.values()) {
            if (station.root().getWorld().equals(event.getWorld())) {
                station.flush();
            }
        }
    }

    /**
     * Stationen unter den Entities eines Chunks finden, ihre Displays zuordnen und verwaiste Displays entfernen.
     */
    private void attach(List<Entity> entities) {
        Map<String, List<Entity>> displaysByOwner = new HashMap<>();
        List<Interaction> roots = new ArrayList<>();
        for (Entity entity : entities) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if ((entity instanceof ItemDisplay || entity instanceof Interaction) && pdc.has(Keys.STATION_OWNER, PersistentDataType.STRING)) {
                displaysByOwner.computeIfAbsent(pdc.get(Keys.STATION_OWNER, PersistentDataType.STRING), k -> new ArrayList<>()).add(entity);
            } else if (entity instanceof Interaction interaction && pdc.has(Keys.STATION_TYPE, PersistentDataType.STRING)) {
                roots.add(interaction);
            }
        }
        for (Interaction root : roots) {
            if (byRoot.containsKey(root.getUniqueId())) {
                displaysByOwner.remove(root.getUniqueId().toString());
                continue;
            }
            Station station = create(root);
            List<Entity> displays = displaysByOwner.remove(root.getUniqueId().toString());
            if (station == null) {
                continue; // unbekannter Typ: Entities stehen lassen
            }
            if (displays != null) {
                for (Entity part : displays) {
                    String name = part.getPersistentDataContainer().get(Keys.STATION_PART, PersistentDataType.STRING);
                    if (name == null) {
                        part.remove();
                    } else if (part instanceof ItemDisplay display) {
                        station.adoptPart(name, display);
                    } else if (part instanceof Interaction hitbox) {
                        station.adoptHitbox(name, hitbox);
                    }
                }
            }
            try {
                station.updateDisplay();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Station bei " + station.position() + " konnte nicht angezeigt werden", e);
            }
        }
        // Displays ohne Station im selben Chunk sind verwaist (Teile liegen immer im Block der Station)
        for (List<Entity> orphans : displaysByOwner.values()) {
            for (Entity orphan : orphans) {
                UUID owner = UUID.fromString(orphan.getPersistentDataContainer().get(Keys.STATION_OWNER, PersistentDataType.STRING));
                if (!byRoot.containsKey(owner)) {
                    orphan.remove();
                }
            }
        }
    }

    /**
     * Bei Barriere-Stationen muss die Hitbox den ganzen Block etwas überragen, sonst treffen Klicks die Barriere.
     */
    private static float hitboxWidth(StationType type) {
        return type.barrier() ? Math.max(type.interactionWidth(), 1f) + 0.02f : type.interactionWidth();
    }

    private static float hitboxHeight(StationType type) {
        return type.barrier() ? Math.max(type.interactionHeight(), 1f) + 0.02f : type.interactionHeight();
    }

    private @Nullable Station create(Interaction root) {
        PersistentDataContainer pdc = root.getPersistentDataContainer();
        String typeId = pdc.get(Keys.STATION_TYPE, PersistentDataType.STRING);
        StationType type = types.get(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unbekannter Stations-Typ " + typeId + " bei " + root.getLocation().toVector() + " – wird ignoriert");
            return null;
        }
        Facing facing = Facing.byDataValue(pdc.getOrDefault(Keys.STATION_FACING, PersistentDataType.INTEGER, Facing.NORTH.dataValue()));
        // Hitbox an die aktuelle Größe des Typs anpassen (ältere Stationen)
        if (root.getInteractionWidth() != hitboxWidth(type) || root.getInteractionHeight() != hitboxHeight(type)) {
            root.setInteractionWidth(hitboxWidth(type));
            root.setInteractionHeight(hitboxHeight(type));
        }
        BlockKey position = BlockKey.of(root.getLocation().add(0, 0.05, 0));
        Station station = type.factory().create(new StationContext(this, type, root, position, facing));
        try {
            station.loadState();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Zustand der Station bei " + position + " ist beschädigt und wird zurückgesetzt", e);
            station.load(pdc.getAdapterContext().newPersistentDataContainer());
            station.forceDirty();
        }
        byRoot.put(root.getUniqueId(), station);
        for (BlockKey block : station.occupied()) {
            Station previous = byBlock.put(block, station);
            if (previous != null && previous != station) {
                plugin.getLogger().warning("Zwei Stationen im selben Block " + block + " – die ältere wird ignoriert");
                detach(previous);
                byBlock.put(block, station);
            }
        }
        updateTicking(station);
        return station;
    }

    private void detach(Station station) {
        byRoot.remove(station.root().getUniqueId());
        for (BlockKey block : station.occupied()) {
            byBlock.remove(block, station);
        }
        for (Interaction hitbox : station.hitboxes().values()) {
            byHitbox.remove(hitbox.getUniqueId(), station);
        }
        ticking.remove(station);
    }

    void registerHitbox(Interaction hitbox, Station station) {
        byHitbox.put(hitbox.getUniqueId(), station);
    }

    void unregisterHitbox(Interaction hitbox) {
        byHitbox.remove(hitbox.getUniqueId());
    }

    /**
     * Belegte Blöcke einer Station haben sich geändert (z. B. 1x2-Gericht platziert).
     */
    public void updateOccupied(Station station, List<BlockKey> before) {
        for (BlockKey block : before) {
            byBlock.remove(block, station);
        }
        for (BlockKey block : station.occupied()) {
            byBlock.put(block, station);
        }
    }

    // ---- Abfragen ----

    public @Nullable Station at(BlockKey position) {
        return byBlock.get(position);
    }

    public @Nullable Station at(Block block) {
        return byBlock.get(BlockKey.of(block));
    }

    public @Nullable Station byRoot(Entity entity) {
        return byRoot.get(entity.getUniqueId());
    }

    /**
     * Station zu einer Wurzel-Entity oder einer zusätzlichen Hitbox.
     */
    public @Nullable Station byEntity(Entity entity) {
        Station station = byRoot.get(entity.getUniqueId());
        return station != null ? station : byHitbox.get(entity.getUniqueId());
    }

    /**
     * Hitzequelle für Wok/Suppentopf wie hasHeatSource im Mod: brennender Herd, Blöcke mit LIT=true
     * (Lagerfeuer, Ofen ...) oder Feuer, Seelenfeuer, Lava, Magma.
     */
    public boolean isHeatSource(Block block) {
        Station station = at(block);
        if (station != null) {
            return station instanceof StoveStation stove && stove.isLit();
        }
        if (block.getBlockData() instanceof Lightable lightable) {
            return lightable.isLit();
        }
        return HEAT_SOURCES.contains(block.getType());
    }

    public Collection<Station> loaded() {
        return Collections.unmodifiableCollection(byRoot.values());
    }

    // ---- Platzieren / Abbauen ----

    /**
     * Station erzeugen. Prüfungen (Platz, Rechte) macht der Aufrufer.
     */
    public Station place(StationType type, Block block, Facing facing) {
        return place(type, block, facing, station -> {
        });
    }

    /**
     * @param init wird vor dem ersten Speichern/Anzeigen aufgerufen (z. B. welches Gericht auf dem Teller liegt)
     */
    public Station place(StationType type, Block block, Facing facing, java.util.function.Consumer<Station> init) {
        if (type.barrier()) {
            block.setType(Material.BARRIER, false);
        }
        Location location = BlockKey.of(block).bottomCenter(block.getWorld());
        if (type.barrier()) {
            location.subtract(0, 0.01, 0);
        }
        float finalWidth = hitboxWidth(type);
        float finalHeight = hitboxHeight(type);
        Interaction root = block.getWorld().spawn(location, Interaction.class, interaction -> {
            interaction.setInteractionWidth(finalWidth);
            interaction.setInteractionHeight(finalHeight);
            interaction.setResponsive(true);
            interaction.setPersistent(true);
            PersistentDataContainer pdc = interaction.getPersistentDataContainer();
            pdc.set(Keys.STATION_TYPE, PersistentDataType.STRING, type.id());
            pdc.set(Keys.STATION_VERSION, PersistentDataType.INTEGER, STATE_VERSION);
            pdc.set(Keys.STATION_FACING, PersistentDataType.INTEGER, facing.dataValue());
        });
        Station station = create(root);
        if (station == null) {
            throw new IllegalStateException("Station " + type.id() + " nicht registriert");
        }
        init.accept(station);
        station.forceDirty();
        station.flush();
        station.updateDisplay();
        updateTicking(station);
        return station;
    }

    /**
     * Station abbauen: Inhalt und (außer im Kreativmodus) das Stations-Item fallen lassen.
     */
    public void breakStation(Station station, @Nullable Player player) {
        if (station.isRemoved()) {
            return;
        }
        Location drop = station.center();
        for (ItemStack content : station.contents(player)) {
            if (content != null && !content.isEmpty()) {
                drop.getWorld().dropItemNaturally(drop, content);
            }
        }
        if (player == null || player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = station.selfDrop();
            if (item != null) {
                drop.getWorld().dropItemNaturally(drop, item);
            }
        }
        remove(station);
        drop.getWorld().playSound(drop, station instanceof de.emrion.kaleidoscopeCookery.station.crop.CropStation ? Sound.BLOCK_CROP_BREAK : Sound.BLOCK_WOOD_BREAK,
                SoundCategory.BLOCKS, 1, 1);
    }

    /**
     * Station ohne Drops entfernen.
     */
    public void remove(Station station) {
        detach(station);
        station.destroy();
        if (station.context().type().barrier()) {
            for (BlockKey key : station.occupied()) {
                Block block = key.block();
                if (block != null && block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    // ---- Reparatur ----

    /**
     * Prüft alle geladenen Stationen: fehlende Displays neu erzeugen, fehlende Barrieren setzen,
     * Stationen ohne Platz entfernen, verwaiste Displays/Interactions löschen.
     *
     * @return Anzahl der Korrekturen
     */
    public int repair() {
        int fixes = 0;
        for (Station station : new ArrayList<>(byRoot.values())) {
            Block block = station.position().block();
            if (block == null) {
                continue;
            }
            if (!station.root().isValid()) {
                // Interaction wurde entfernt (z. B. /kill): Teile und Barriere mit aufräumen
                remove(station);
                fixes++;
                continue;
            }
            if (station.context().type().barrier() && block.getType() != Material.BARRIER) {
                if (block.getType().isAir() || block.isReplaceable()) {
                    block.setType(Material.BARRIER, false);
                } else {
                    breakStation(station, null);
                }
                fixes++;
                continue;
            }
            Map<String, ItemDisplay> parts = station.parts();
            int before = (int) parts.values().stream().filter(Entity::isValid).count();
            station.updateDisplay();
            int after = (int) parts.values().stream().filter(Entity::isValid).count();
            if (after != before) {
                fixes++;
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                String owner = display.getPersistentDataContainer().get(Keys.STATION_OWNER, PersistentDataType.STRING);
                if (owner == null) {
                    continue;
                }
                Station station = byRoot.get(UUID.fromString(owner));
                if (station == null || !display.equals(station.parts().get(display.getPersistentDataContainer().get(Keys.STATION_PART, PersistentDataType.STRING)))) {
                    display.remove();
                    fixes++;
                }
            }
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                String hitboxOwner = interaction.getPersistentDataContainer().get(Keys.STATION_OWNER, PersistentDataType.STRING);
                if (hitboxOwner != null) {
                    Station owner = byRoot.get(UUID.fromString(hitboxOwner));
                    if (owner == null || !interaction.equals(owner.hitboxes().get(interaction.getPersistentDataContainer().get(Keys.STATION_PART, PersistentDataType.STRING)))) {
                        interaction.remove();
                        fixes++;
                    }
                    continue;
                }
                if (interaction.getPersistentDataContainer().has(Keys.STATION_TYPE, PersistentDataType.STRING) && !byRoot.containsKey(interaction.getUniqueId())
                        && types.containsKey(interaction.getPersistentDataContainer().get(Keys.STATION_TYPE, PersistentDataType.STRING))) {
                    // Doppelte Station im selben Block
                    interaction.remove();
                    fixes++;
                }
            }
        }
        return fixes;
    }
}
