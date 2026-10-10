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
 * Manages all loaded stations: loading/unloading with the chunks, one central tick task,
 * placing, breaking and repair.
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
    /** Additional hitboxes (Interaction with STATION_OWNER) -> station */
    private final Map<UUID, Station> byHitbox = new HashMap<>();
    private final Set<Station> ticking = new LinkedHashSet<>();
    /** Scheduled single ticks: server tick -> stations (e.g. the next random tick of a crop) */
    private final Map<Long, List<Station>> scheduled = new HashMap<>();
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
        if (task != null) {
            attachLoaded(); // an addon's type: adopt its stations in chunks that are already loaded
        }
    }

    /**
     * Another item that places the same station type (e.g. full and empty seasoning bottle).
     */
    public void registerItem(Key item, StationType type) {
        typesByItem.put(item, type);
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
        attachLoaded();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void attachLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.isEntitiesLoaded()) {
                    attach(List.of(chunk.getEntities()));
                }
            }
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flushAll();
        byRoot.clear();
        byChunk.clear();
        byBlock.clear();
        byHitbox.clear();
        ticking.clear();
    }

    // ---- Tick ----

    private void tick() {
        serverTick++;
        List<Station> due = scheduled.remove(serverTick);
        if (due != null) {
            for (Station station : due) {
                if (station.isRemoved() || byRoot.get(station.root().getUniqueId()) != station) {
                    continue; // broken or chunk unloaded (rescheduled on load)
                }
                try {
                    station.scheduledTick(serverTick);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Fehler im geplanten Tick der Station " + station.describe() + " bei " + station.position(), e);
                }
            }
        }
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
        if (serverTick % VISIBILITY_INTERVAL == 0) {
            updateVisibility();
        }
    }

    // ---- Visibility of the click entities ----

    /** Only players this close get the Interaction entities (click reach is 3 blocks in survival, 5 in creative) */
    private static final double SHOW_DISTANCE = 8;
    private static final double HIDE_DISTANCE = 10;
    private static final int VISIBILITY_INTERVAL = 5;
    /** Player -> Interaction entities they currently see */
    private final Map<UUID, java.util.Set<UUID>> visible = new HashMap<>();
    /** Stations per chunk, so each player only checks the stations around them (not all on the server) */
    private final Map<ChunkKey, java.util.List<Station>> byChunk = new HashMap<>();

    private record ChunkKey(UUID world, int x, int z) {
        static ChunkKey of(BlockKey block) {
            return new ChunkKey(block.world(), block.x() >> 4, block.z() >> 4);
        }
    }

    /**
     * Is there a station of this type whose block is at most radius blocks (per axis) away from block?
     * Only searches the chunks around it (index byChunk).
     */
    public boolean anyNear(BlockKey block, int radius, String typeId) {
        for (int cx = (block.x() - radius) >> 4; cx <= (block.x() + radius) >> 4; cx++) {
            for (int cz = (block.z() - radius) >> 4; cz <= (block.z() + radius) >> 4; cz++) {
                for (Station station : byChunk.getOrDefault(new ChunkKey(block.world(), cx, cz), List.of())) {
                    BlockKey at = station.position();
                    if (station.context().type().id().equals(typeId) && Math.abs(at.x() - block.x()) <= radius
                            && Math.abs(at.y() - block.y()) <= radius && Math.abs(at.z() - block.z()) <= radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Click entities are invisible by default (otherwise the server sends hundreds of field crops to every
     * client within 96 blocks). They are only shown to players nearby.
     */
    private void updateVisibility() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            java.util.Set<UUID> shown = visible.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashSet<>());
            Location eye = player.getLocation();
            java.util.Set<UUID> keep = new java.util.HashSet<>();
            UUID world = eye.getWorld().getUID();
            int minX = (int) Math.floor(eye.getX() - HIDE_DISTANCE) >> 4;
            int maxX = (int) Math.floor(eye.getX() + HIDE_DISTANCE) >> 4;
            int minZ = (int) Math.floor(eye.getZ() - HIDE_DISTANCE) >> 4;
            int maxZ = (int) Math.floor(eye.getZ() + HIDE_DISTANCE) >> 4;
            List<Station> nearby = new ArrayList<>();
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    nearby.addAll(byChunk.getOrDefault(new ChunkKey(world, cx, cz), List.of()));
                }
            }
            for (Station station : nearby) {
                Interaction root = station.root();
                double distance = root.getLocation().distanceSquared(eye);
                boolean near = distance < SHOW_DISTANCE * SHOW_DISTANCE;
                boolean stay = distance < HIDE_DISTANCE * HIDE_DISTANCE;
                for (Interaction interaction : station.interactions()) {
                    UUID id = interaction.getUniqueId();
                    if (near && !shown.contains(id)) {
                        player.showEntity(plugin, interaction);
                        shown.add(id);
                    }
                    if ((near || stay) && shown.contains(id)) {
                        keep.add(id);
                    }
                }
            }
            for (java.util.Iterator<UUID> it = shown.iterator(); it.hasNext(); ) {
                UUID id = it.next();
                if (!keep.contains(id)) {
                    Entity entity = plugin.getServer().getEntity(id);
                    if (entity != null) {
                        player.hideEntity(plugin, entity);
                    }
                    it.remove();
                }
            }
        }
        visible.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    /**
     * Show a new station or hitbox to nearby players right away (not only on the next pass).
     */
    public void revealNear(Station station) {
        for (Player player : station.world().getPlayers()) {
            if (player.getLocation().distanceSquared(station.root().getLocation()) < SHOW_DISTANCE * SHOW_DISTANCE) {
                java.util.Set<UUID> shown = visible.computeIfAbsent(player.getUniqueId(), k -> new java.util.HashSet<>());
                for (Interaction interaction : station.interactions()) {
                    if (shown.add(interaction.getUniqueId())) {
                        player.showEntity(plugin, interaction);
                    }
                }
            }
        }
    }

    /**
     * Status display: whoever looks at a station (max. 5 blocks) sees its status in the action bar.
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
                continue; // block in front
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
     * A station's message in the action bar: do not overwrite it with the status display for 2 s.
     */
    public void actionBar(Player player, Component message) {
        player.sendActionBar(message);
        hudPausedUntil.put(player.getUniqueId(), serverTick + 40);
    }

    /**
     * The station ticks (or no longer does), e.g. after a state change.
     */
    /**
     * Schedule a single tick in delay ticks ({@link Station#scheduledTick}).
     */
    public void schedule(Station station, long delay) {
        scheduled.computeIfAbsent(serverTick + Math.max(1, delay), k -> new ArrayList<>()).add(station);
    }

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

    // ---- Loading / unloading ----

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
     * Find stations among a chunk's entities, assign their displays and remove orphaned displays.
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
                continue; // unknown type: leave the entities alone
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
        // Displays without a station in the same chunk are orphaned (parts are always in the block of the station)
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
     * For barrier stations the hitbox must slightly exceed the whole block, otherwise clicks hit the barrier.
     */
    private static float hitboxWidth(StationType type) {
        return type.barrier() ? Math.max(type.interactionWidth(), 1f) + 0.02f : type.interactionWidth();
    }

    private static float hitboxHeight(StationType type) {
        return type.barrier() ? Math.max(type.interactionHeight(), 1f) + 0.02f : type.interactionHeight();
    }

    private @Nullable Station create(Interaction root) {
        if (root.isVisibleByDefault()) {
            root.setVisibleByDefault(false); // older stations: show the click entity only nearby
        }
        PersistentDataContainer pdc = root.getPersistentDataContainer();
        String typeId = pdc.get(Keys.STATION_TYPE, PersistentDataType.STRING);
        StationType type = types.get(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unbekannter Stations-Typ " + typeId + " bei " + root.getLocation().toVector() + " – wird ignoriert");
            return null;
        }
        Facing facing = Facing.byDataValue(pdc.getOrDefault(Keys.STATION_FACING, PersistentDataType.INTEGER, Facing.NORTH.dataValue()));
        // Adjust the hitbox to the type's current size (older stations)
        if (root.getInteractionWidth() != hitboxWidth(type) || root.getInteractionHeight() != hitboxHeight(type)) {
            root.setInteractionWidth(hitboxWidth(type));
            root.setInteractionHeight(hitboxHeight(type));
        }
        BlockKey position = BlockKey.of(root.getLocation().add(0, 0.05, 0));
        // The type has no barrier any more (wok, stockpot, steamer): remove the old barrier
        Block old = type.barrier() ? null : position.block();
        if (old != null && old.getType() == Material.BARRIER) {
            old.setType(Material.AIR, false);
        }
        Station station = type.factory().create(new StationContext(this, type, root, position, facing));
        try {
            station.loadState();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Zustand der Station bei " + position + " ist beschädigt und wird zurückgesetzt", e);
            station.load(pdc.getAdapterContext().newPersistentDataContainer());
            station.forceDirty();
        }
        byRoot.put(root.getUniqueId(), station);
        byChunk.computeIfAbsent(ChunkKey.of(station.position()), k -> new java.util.ArrayList<>()).add(station);
        for (BlockKey block : station.occupied()) {
            Station previous = byBlock.put(block, station);
            if (previous != null && previous != station) {
                plugin.getLogger().warning("Zwei Stationen im selben Block " + block + " – die ältere wird ignoriert");
                detach(previous);
                byBlock.put(block, station);
            }
        }
        updateTicking(station);
        station.onAttached();
        return station;
    }

    private void detach(Station station) {
        byRoot.remove(station.root().getUniqueId());
        java.util.List<Station> inChunk = byChunk.get(ChunkKey.of(station.position()));
        if (inChunk != null) {
            inChunk.remove(station);
            if (inChunk.isEmpty()) {
                byChunk.remove(ChunkKey.of(station.position()));
            }
        }
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
     * The occupied blocks of a station changed (e.g. a 1x2 dish was placed).
     */
    public void updateOccupied(Station station, List<BlockKey> before) {
        for (BlockKey block : before) {
            byBlock.remove(block, station);
        }
        for (BlockKey block : station.occupied()) {
            byBlock.put(block, station);
        }
    }

    // ---- Queries ----

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
     * Station for a root entity or an additional hitbox.
     */
    public @Nullable Station byEntity(Entity entity) {
        Station station = byRoot.get(entity.getUniqueId());
        return station != null ? station : byHitbox.get(entity.getUniqueId());
    }

    /**
     * Heat source for wok/stockpot like hasHeatSource in the mod: burning stove, blocks with LIT=true
     * (campfire, furnace ...) or fire, soul fire, lava, magma.
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

    // ---- Placing / breaking ----

    /**
     * Create a station. The caller does the checks (space, permissions).
     */
    public Station place(StationType type, Block block, Facing facing) {
        return place(type, block, facing, station -> {
        });
    }

    /**
     * @param init called before the first save/display (e.g. which dish is on the plate)
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
            interaction.setVisibleByDefault(false); // only shown to players in range (updateVisibility)
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
        if (type.barrier()) {
            for (BlockKey key : station.occupied()) {
                Block occupied = key.block();
                if (occupied != null && occupied.getType() != Material.BARRIER) {
                    occupied.setType(Material.BARRIER, false);
                }
            }
        }
        init.accept(station);
        station.forceDirty();
        station.flush();
        station.updateDisplay();
        updateTicking(station);
        revealNear(station);
        return station;
    }

    /**
     * Break a station: drop its contents and (except in creative mode) the station item.
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
        // A station above without support (e.g. steamer on wok, stockpot or steamer) drops as well
        Block below = station.position().block();
        Station above = below == null ? null : at(below.getRelative(org.bukkit.block.BlockFace.UP));
        if (above != null && above.context().type().needsSupport() && (!below.getType().isSolid() || below.getType() == Material.BARRIER)) {
            breakStation(above, player);
        }
        drop.getWorld().playSound(drop, station instanceof de.emrion.kaleidoscopeCookery.station.crop.CropStation ? Sound.BLOCK_CROP_BREAK : Sound.BLOCK_WOOD_BREAK,
                SoundCategory.BLOCKS, 1, 1);
    }

    /**
     * Remove a station without drops.
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

    // ---- Repair ----

    /**
     * Checks all loaded stations: recreate missing displays, set missing barriers,
     * remove stations without space, delete orphaned displays/interactions.
     *
     * @return number of corrections
     */
    public int repair() {
        int fixes = 0;
        for (Station station : new ArrayList<>(byRoot.values())) {
            Block block = station.position().block();
            if (block == null) {
                continue;
            }
            if (!station.root().isValid()) {
                // The Interaction was removed (e.g. /kill): clean up the parts and barrier too
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
            if (station.context().type().barrier()) {
                for (BlockKey key : station.occupied()) {
                    Block occupied = key.block();
                    if (occupied != null && occupied.getType() != Material.BARRIER && (occupied.getType().isAir() || occupied.isReplaceable())) {
                        occupied.setType(Material.BARRIER, false);
                        fixes++;
                    }
                }
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
                    // Duplicate station in the same block
                    interaction.remove();
                    fixes++;
                }
            }
        }
        return fixes;
    }
}
