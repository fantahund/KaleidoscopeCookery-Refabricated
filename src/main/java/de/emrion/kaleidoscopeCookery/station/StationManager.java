package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private static final int FLUSH_INTERVAL = 20;

    private final KaleidoscopeCookery plugin;
    private final Map<String, StationType> types = new LinkedHashMap<>();
    private final Map<Key, StationType> typesByItem = new HashMap<>();
    private final Map<UUID, Station> byRoot = new HashMap<>();
    private final Map<BlockKey, Station> byBlock = new HashMap<>();
    private final Set<Station> ticking = new LinkedHashSet<>();
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
    }

    public long serverTick() {
        return serverTick;
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
        Map<String, List<ItemDisplay>> displaysByOwner = new HashMap<>();
        List<Interaction> roots = new ArrayList<>();
        for (Entity entity : entities) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (entity instanceof ItemDisplay display && pdc.has(Keys.STATION_OWNER, PersistentDataType.STRING)) {
                displaysByOwner.computeIfAbsent(pdc.get(Keys.STATION_OWNER, PersistentDataType.STRING), k -> new ArrayList<>()).add(display);
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
            List<ItemDisplay> displays = displaysByOwner.remove(root.getUniqueId().toString());
            if (station == null) {
                continue; // unbekannter Typ: Entities stehen lassen
            }
            if (displays != null) {
                for (ItemDisplay display : displays) {
                    String part = display.getPersistentDataContainer().get(Keys.STATION_PART, PersistentDataType.STRING);
                    if (part == null) {
                        display.remove();
                    } else {
                        station.adoptPart(part, display);
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
        for (List<ItemDisplay> orphans : displaysByOwner.values()) {
            for (ItemDisplay orphan : orphans) {
                UUID owner = UUID.fromString(orphan.getPersistentDataContainer().get(Keys.STATION_OWNER, PersistentDataType.STRING));
                if (!byRoot.containsKey(owner)) {
                    orphan.remove();
                }
            }
        }
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
        Station previous = byBlock.put(position, station);
        if (previous != null && previous != station) {
            plugin.getLogger().warning("Zwei Stationen im selben Block " + position + " – die ältere wird ignoriert");
            detach(previous);
            byBlock.put(position, station);
        }
        updateTicking(station);
        return station;
    }

    private void detach(Station station) {
        byRoot.remove(station.root().getUniqueId());
        byBlock.remove(station.position(), station);
        ticking.remove(station);
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

    public Collection<Station> loaded() {
        return Collections.unmodifiableCollection(byRoot.values());
    }

    // ---- Platzieren / Abbauen ----

    /**
     * Station erzeugen. Prüfungen (Platz, Rechte) macht der Aufrufer.
     */
    public Station place(StationType type, Block block, Facing facing) {
        if (type.barrier()) {
            block.setType(Material.BARRIER, false);
        }
        Location location = BlockKey.of(block).bottomCenter(block.getWorld());
        float width = type.interactionWidth();
        float height = type.interactionHeight();
        if (type.barrier()) {
            // Etwas größer als der Barriere-Block, damit Klicks die Hitbox treffen und nicht den Block
            width += 0.02f;
            height += 0.02f;
            location.subtract(0, 0.01, 0);
        }
        float finalWidth = width;
        float finalHeight = height;
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
        station.forceDirty();
        station.flush();
        station.updateDisplay();
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
        for (ItemStack content : station.contents()) {
            if (content != null && !content.isEmpty()) {
                drop.getWorld().dropItemNaturally(drop, content);
            }
        }
        if (player == null || player.getGameMode() != GameMode.CREATIVE) {
            ItemStack item = plugin.getItems().create(station.context().type().item(), 1);
            if (item != null) {
                drop.getWorld().dropItemNaturally(drop, item);
            }
        }
        remove(station);
        drop.getWorld().playSound(drop, Sound.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1, 1);
    }

    /**
     * Station ohne Drops entfernen.
     */
    public void remove(Station station) {
        detach(station);
        station.destroy();
        if (station.context().type().barrier()) {
            Block block = station.position().block();
            if (block != null && block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
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
            int before = parts == null ? 0 : (int) parts.values().stream().filter(Entity::isValid).count();
            station.updateDisplay();
            int after = parts == null ? 0 : (int) parts.values().stream().filter(Entity::isValid).count();
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
