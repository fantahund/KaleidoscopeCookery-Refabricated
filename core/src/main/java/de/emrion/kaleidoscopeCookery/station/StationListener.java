package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.crop.CropKind;
import de.emrion.kaleidoscopeCookery.station.crop.CropStation;
import de.emrion.kaleidoscopeCookery.station.oil.OilPotStation;
import de.emrion.kaleidoscopeCookery.station.plate.PlateStation;
import de.emrion.kaleidoscopeCookery.station.teapot.CupStation;
import de.emrion.kaleidoscopeCookery.station.teapot.TeapotStation;
import net.kyori.adventure.key.Key;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Clicks on stations, placing via the station item and protection of the station block.
 * <ul>
 *     <li>Right-click: {@link Station#onInteract}</li>
 *     <li>Left click: {@link Station#onAttack}</li>
 *     <li>Sneak + left click: break the station</li>
 * </ul>
 */
public class StationListener implements Listener {

    private final KaleidoscopeCookery plugin;
    private final StationManager stations;
    /** A self-triggered event for permission checks is currently running */
    private boolean checking;
    private final Map<UUID, Long> lastHandledTick = new HashMap<>();

    public StationListener(KaleidoscopeCookery plugin, StationManager stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    // ---- Clicks on the hitbox ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (checking) {
            return;
        }
        Station station = stations.byEntity(event.getRightClicked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.hasPermission(Permissions.USE)) {
            return;
        }
        // To be safe, act only once per tick (in case two click events do arrive)
        long tick = stations.serverTick();
        Long last = lastHandledTick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean stationItem = stations.typeByItem(CookeryItems.idOf(held)) != null;
        // Place station items (wok on stove ...) first, dishes only if the station does nothing with them
        if (stationItem && station.interceptPlacement(player, held)) {
            player.swingMainHand();
            return;
        }
        if (stationItem && placeOnStation(player, held, event)) {
            return;
        }
        boolean handled;
        try {
            station.setClickPosition(clickPosition(station, event));
            handled = station.onInteract(player, EquipmentSlot.HAND);
        } finally {
            station.setClickPosition(null);
        }
        if (handled) {
            player.swingMainHand();
            return;
        }
        if (!stationItem && placeableType(held) != null) {
            placeOnStation(player, held, event);
        }
    }

    /**
     * Click position relative to the bottom centre of the station block.
     */
    private static @Nullable Vector clickPosition(Station station, PlayerInteractEntityEvent event) {
        if (!(event instanceof PlayerInteractAtEntityEvent at) || at.getClickedPosition() == null) {
            return null;
        }
        return event.getRightClicked().getLocation().toVector().add(at.getClickedPosition())
                .subtract(station.position().bottomCenter(station.world()).toVector());
    }

    /**
     * Placing by clicking a hitbox: determine the face from the click position.
     */
    private boolean placeOnStation(Player player, ItemStack held, PlayerInteractEntityEvent event) {
        // Paper 26.3 always reports right-clicks on entities with a click position
        if (!(event instanceof PlayerInteractAtEntityEvent at) || !(event.getRightClicked() instanceof Interaction hitbox)) {
            return false;
        }
        Vector clicked = at.getClickedPosition();
        if (clicked == null) {
            return false;
        }
        BlockFace face = faceOf(clicked, hitbox.getInteractionWidth(), hitbox.getInteractionHeight());
        Block block = hitbox.getLocation().add(0, 0.05, 0).getBlock();
        return tryPlace(player, EquipmentSlot.HAND, held, block, face);
    }

    private static BlockFace faceOf(Vector clicked, float width, float height) {
        if (clicked.getY() >= height - 0.02) {
            return BlockFace.UP;
        }
        if (clicked.getY() <= 0.02) {
            return BlockFace.DOWN;
        }
        if (Math.abs(clicked.getX()) >= Math.abs(clicked.getZ())) {
            return clicked.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return clicked.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Station station = stations.byEntity(event.getAttacked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking() || station.breaksOnAttack()) {
            if (canBreak(player, station)) {
                stations.breakStation(station, player);
            }
            return;
        }
        if (!player.hasPermission(Permissions.USE) || !canUse(player, station.root())) {
            return;
        }
        station.onAttack(player);
    }

    /**
     * Ask protection plugins (WorldGuard ...): may the player use the station?
     */
    private boolean canUse(Player player, Entity root) {
        checking = true;
        try {
            return new PlayerInteractEntityEvent(player, root, EquipmentSlot.HAND).callEvent();
        } finally {
            checking = false;
        }
    }

    /**
     * Ask protection plugins: may the player break here?
     */
    private boolean canBreak(Player player, Station station) {
        Block block = station.position().block();
        if (block == null) {
            return false;
        }
        checking = true;
        try {
            return new BlockBreakEvent(block, player).callEvent();
        } finally {
            checking = false;
        }
    }

    // ---- Placing ----

    /**
     * Like blocks in vanilla: clicking a chest, furnace, door ... uses the block, it is only placed while sneaking.
     */
    public static boolean opensOnClick(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        return clicked != null && !event.getPlayer().isSneaking() && clicked.getType().isInteractable();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (placeableType(item) == null) {
            return;
        }
        CookeryItem placed = plugin.getItems().get(CookeryItems.typeOf(item));
        if (placed != null && placed.sneakToPlace() && !event.getPlayer().isSneaking()) {
            return; // drink tea, use the teapot
        }
        if (opensOnClick(event)) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking() && stations.at(event.getClickedBlock()) != null) {
            return;
        }
        tryPlace(event.getPlayer(), event.getHand(), item, event.getClickedBlock(), event.getBlockFace());
    }

    /**
     * Station type this item places (station item or plated dish), otherwise null.
     */
    private @Nullable StationType placeableType(@Nullable ItemStack item) {
        Key itemId = CookeryItems.idOf(item);
        StationType type = stations.typeByItem(itemId);
        if (type == null && itemId != null) {
            CookeryItem dish = plugin.getItems().get(itemId);
            if (dish != null && dish.plate() != null) {
                type = stations.type(PlateStation.TYPE_ID);
            } else if (CupStation.isTeacup(dish)) {
                type = stations.type(CupStation.TYPE_ID);
            }
        }
        return type;
    }

    /**
     * Place the station from the item at clicked + face (like a block).
     */
    private boolean tryPlace(Player player, EquipmentSlot hand, ItemStack item, Block clicked, BlockFace face) {
        StationType type = placeableType(item);
        if (type == null || !player.hasPermission(Permissions.USE)) {
            return false;
        }
        Key itemId = CookeryItems.idOf(item);
        CookeryItem dish = plugin.getItems().get(itemId);
        if (dish != null && dish.sneakToPlace() && !player.isSneaking()) {
            return false;
        }
        Block target = clicked.isReplaceable() && stations.at(clicked) == null ? clicked : clicked.getRelative(face);
        Facing facing = Facing.towards(player.getYaw());
        for (BlockKey block : type.footprint(BlockKey.of(target))) {
            Block part = block.block();
            if (part == null || !canPlaceAt(type, part)) {
                return false;
            }
        }
        if (dish != null && dish.plate() != null && dish.plate().wide()) {
            BlockFace side = PlateStation.secondBlock(facing);
            if (!canPlaceAt(type, target.getRelative(side))) {
                return false;
            }
        }
        if (dish != null && dish.plate() != null && dish.plate().large()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && !canPlaceAt(type, target.getRelative(dx, 0, dz))) {
                        return false;
                    }
                }
            }
        }
        BlockState replaced = target.getState();
        if (type.barrier()) {
            BoundingBox box = BoundingBox.of(target).expand(-type.minOffset(), 0, -type.minOffset(), type.maxOffset(), type.height() - 1, type.maxOffset());
            Collection<Entity> blocking = target.getWorld().getNearbyEntities(box, entity -> entity instanceof LivingEntity);
            if (!blocking.isEmpty()) {
                return false;
            }
            target.setType(Material.BARRIER, false);
        }
        checking = true;
        boolean allowed;
        try {
            if (type.width() > 1 || type.height() > 1) {
                // Multi-block station: protection plugins check all occupied blocks
                List<BlockState> states = new ArrayList<>();
                states.add(replaced);
                for (BlockKey block : type.footprint(BlockKey.of(target))) {
                    Block part = block.block();
                    if (part != null && !part.equals(target)) {
                        states.add(part.getState());
                    }
                }
                allowed = new BlockMultiPlaceEvent(states, clicked, item, player, true, hand).callEvent();
            } else {
                allowed = new BlockPlaceEvent(target, replaced, clicked, item, player, true, hand).callEvent();
            }
        } finally {
            checking = false;
        }
        if (!allowed) {
            replaced.update(true, false);
            return false;
        }
        Station placed = stations.place(type, target, facing, station -> initPlaced(station, item.asOne(), itemId, face, player));
        if (!placed.canSurvive()) {
            // e.g. a chili ristra without support above
            stations.remove(placed);
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        player.swingHand(hand);
        target.getWorld().playSound(target.getLocation().toCenterLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1);
        return true;
    }

    /**
     * Copy the data of the item onto the freshly placed station.
     */
    private static void initPlaced(Station station, ItemStack placedFrom, @Nullable Key placedId, BlockFace face, @Nullable Player placer) {
        if (station instanceof PlateStation plate) {
            plate.initFrom(placedFrom, placedId);
        } else if (station instanceof TeapotStation teapot) {
            teapot.initFrom(placedFrom);
        } else if (station instanceof CupStation cup) {
            cup.initFrom(placedId);
        } else if (station instanceof OilPotStation oilPot) {
            oilPot.initFrom(placedFrom);
        } else if (station instanceof de.emrion.kaleidoscopeCookery.station.basket.FruitBasketStation basket) {
            basket.initFrom(placedFrom);
        } else if (station instanceof de.emrion.kaleidoscopeCookery.station.decor.StrawBlockStation straw) {
            straw.initAxis(face);
        } else if (station instanceof de.emrion.kaleidoscopeCookery.station.decor.RecipeBlockStation recipe) {
            recipe.initFrom(placedFrom, face);
        } else if (station instanceof de.emrion.kaleidoscopeCookery.station.scarecrow.ScarecrowStation scarecrow && placer != null) {
            scarecrow.initYaw(placer.getYaw());
        } else if (station instanceof PlacedFromItem addon) {
            addon.initFrom(placedFrom, face);
        }
    }

    // ---- Scarecrow (ScarecrowFarmlandTrampleEvent) ----

    /** Farmland within 16 blocks of a scarecrow is not trampled */
    private static final int SCARECROW_RANGE = 16;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL && protectedFarmland(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTrample(org.bukkit.event.entity.EntityInteractEvent event) {
        if (protectedFarmland(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean protectedFarmland(@Nullable Block block) {
        return block != null && block.getType() == Material.FARMLAND
                && stations.anyNear(BlockKey.of(block), SCARECROW_RANGE, de.emrion.kaleidoscopeCookery.station.scarecrow.ScarecrowStation.TYPE_ID);
    }

    // ---- Dispensers (block/dispenser in the mod) ----

    /** ChoppingBoardDispenseBehavior: a dispenser puts these ingredients (and raw dough) on the chopping board */
    private static final java.util.Set<Material> DISPENSER_INGREDIENTS = java.util.EnumSet.of(Material.TROPICAL_FISH, Material.COD,
            Material.COOKED_COD, Material.SALMON, Material.COOKED_SALMON, Material.MUTTON, Material.COOKED_MUTTON, Material.BEEF,
            Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP, Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.RABBIT, Material.COOKED_RABBIT);
    private static final Key RAW_DOUGH = Key.key("kaleidoscope_cookery", "raw_dough");
    /** OilPot-, BambooTray- and TeapotDispenseBehavior: a dispenser places these items in front of it */
    private static final java.util.Set<Key> DISPENSER_PLACED = java.util.Set.of(de.emrion.kaleidoscopeCookery.station.oil.OilItems.OIL_POT,
            Key.key("kaleidoscope_cookery", "bamboo_tray"), Key.key("kaleidoscope_cookery", "teapot"));

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (!plugin.getCookeryConfig().dispensers() || dispenser.getType() != Material.DISPENSER || !(dispenser.getBlockData() instanceof org.bukkit.block.data.Directional directional)) {
            return;
        }
        BlockFace facing = directional.getFacing();
        Block front = dispenser.getRelative(facing);
        ItemStack item = event.getItem();
        Key id = CookeryItems.idOf(item);
        if (stations.at(front) instanceof de.emrion.kaleidoscopeCookery.station.choppingboard.ChoppingBoardStation board) {
            boolean knife = id != null && id.value().endsWith("kitchen_knife");
            if (!knife && !RAW_DOUGH.equals(id) && (id != null || !DISPENSER_INGREDIENTS.contains(item.getType()))) {
                return;
            }
            event.setCancelled(true);
            boolean done = knife ? board.cutFromDispenser(item) : board.putFromDispenser(item);
            afterDispense(dispenser, item, done, knife);
            return;
        }
        if (id == null || !DISPENSER_PLACED.contains(id)) {
            return;
        }
        event.setCancelled(true);
        StationType type = placeableType(item);
        boolean done = type != null && canPlaceAt(type, front);
        if (done) {
            // Orientation like DirectionalPlaceContext: towards the dispenser, north when pointing up/down
            Facing placeFacing = facing.getModY() != 0 ? Facing.NORTH : Facing.towards(yawOf(facing));
            Station placed = stations.place(type, front, placeFacing, station -> initPlaced(station, item.asOne(), id, BlockFace.UP, null));
            if (!placed.canSurvive()) {
                stations.remove(placed);
                done = false;
            } else {
                front.getWorld().playSound(front.getLocation().toCenterLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1);
            }
        }
        afterDispense(dispenser, item, done, false);
    }

    private static float yawOf(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            default -> 270;
        };
    }

    /**
     * Success: take one item from the dispenser or damage the knife in the next tick (after the cancelled
     * dispense). Failure: click sound like OptionalDispenseItemBehavior.
     */
    private void afterDispense(Block dispenser, ItemStack used, boolean done, boolean knife) {
        if (!done) {
            dispenser.getWorld().playSound(dispenser.getLocation().toCenterLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.BLOCKS, 1, 1.2f);
            return;
        }
        dispenser.getWorld().playSound(dispenser.getLocation().toCenterLocation(), Sound.BLOCK_DISPENSER_DISPENSE, SoundCategory.BLOCKS, 1, 1);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(dispenser.getState(false) instanceof org.bukkit.block.Dispenser state)) {
                return;
            }
            for (ItemStack stack : state.getInventory().getContents()) {
                if (stack == null || !stack.isSimilar(used)) {
                    continue;
                }
                if (knife) {
                    Integer damage = stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE);
                    Integer max = stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.MAX_DAMAGE);
                    if (damage != null && max != null && damage >= max) {
                        stack.subtract();
                    } else if (damage != null) {
                        stack.setData(io.papermc.paper.datacomponent.DataComponentTypes.DAMAGE, damage + 1);
                    }
                } else {
                    stack.subtract();
                }
                return;
            }
        });
    }

    private boolean canPlaceAt(StationType type, Block target) {
        if (stations.at(target) != null) {
            return false;
        }
        if (!target.getType().isAir() && !(target.isReplaceable() && !target.isLiquid())) {
            return false;
        }
        return !type.needsSupport() || hasSupport(target);
    }

    private static final java.util.Set<String> STACK_BASES = java.util.Set.of("pot", "stockpot", "steamer");

    private boolean hasSupport(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        Station base = stations.at(below);
        if (base != null && (base.context().type().barrier() || STACK_BASES.contains(base.context().type().id()))) {
            return true; // steamer on steamer, wok or stockpot (no barrier)
        }
        return below.getType().isSolid() && below.getType() != Material.BARRIER;
    }

    // ---- Protection of the station block ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!checking && stations.at(event.getBlockPlaced()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (stations.at(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Station station = stations.at(event.getToBlock());
        if (station == null) {
            return;
        }
        if (station instanceof CropStation crop && crop.kind() != CropKind.RICE) {
            // Water washes crops away (like vanilla crops)
            stations.breakStation(station, null);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (checking || event instanceof de.emrion.kaleidoscopeCookery.util.BreakPermissionCheck) {
            return;
        }
        Station station = stations.at(event.getBlock());
        if (station != null) {
            // Barrier in creative mode: same as breaking the station
            event.setCancelled(true);
            stations.breakStation(station, event.getPlayer());
            return;
        }
        checkSupportLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        for (Block block : blocks) {
            Station station = stations.at(block);
            if (station != null) {
                stations.breakStation(station, null);
            }
            checkSupportLater(block);
        }
        blocks.removeIf(block -> block.getType() == Material.BARRIER && stations.at(block) != null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            checkSupportLater(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            checkSupportLater(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        checkSupportLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        checkSupportLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        checkSupportLater(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (stations.at(event.getBlock()) != null) {
            event.setCancelled(true);
            return;
        }
        checkSupportLater(event.getBlock());
    }

    /**
     * The block below a station changed: check in the next tick whether it still stands.
     */
    private void checkSupportLater(Block changed) {
        Block above = changed.getRelative(BlockFace.UP);
        Station station = stations.at(above);
        if (station == null || !station.context().type().needsSupport() || !station.position().equals(BlockKey.of(above))) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!station.isRemoved() && (!hasSupport(station.position().block()) || !station.canSurvive())) {
                stations.breakStation(station, null);
            }
        });
    }
}
