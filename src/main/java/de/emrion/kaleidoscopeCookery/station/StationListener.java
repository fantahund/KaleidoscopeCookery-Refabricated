package de.emrion.kaleidoscopeCookery.station;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.List;

/**
 * Klicks auf Stationen, Platzieren über das Stations-Item und Schutz des Stations-Blocks.
 * <ul>
 *     <li>Rechtsklick: {@link Station#onInteract}</li>
 *     <li>Linksklick: {@link Station#onAttack}</li>
 *     <li>Schleichen + Linksklick: Station abbauen</li>
 * </ul>
 */
public class StationListener implements Listener {

    private final KaleidoscopeCookery plugin;
    private final StationManager stations;
    /** Gerade läuft ein selbst ausgelöstes Event zur Rechte-Prüfung */
    private boolean checking;

    public StationListener(KaleidoscopeCookery plugin, StationManager stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    // ---- Klicks auf die Hitbox ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (checking) {
            return;
        }
        Station station = stations.byRoot(event.getRightClicked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !player.hasPermission(Permissions.USE)) {
            return;
        }
        if (station.onInteract(player, EquipmentSlot.HAND)) {
            player.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Station station = stations.byRoot(event.getAttacked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
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
     * Schutz-Plugins (WorldGuard ...) fragen: darf der Spieler die Station benutzen?
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
     * Schutz-Plugins fragen: darf der Spieler hier abbauen?
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

    // ---- Platzieren ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        ItemStack item = event.getItem();
        StationType type = stations.typeByItem(CookeryItems.idOf(item));
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking() && stations.at(event.getClickedBlock()) != null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Block target = clicked.isReplaceable() && stations.at(clicked) == null ? clicked : clicked.getRelative(event.getBlockFace());
        if (!canPlaceAt(type, target)) {
            return;
        }
        BlockState replaced = target.getState();
        if (type.barrier()) {
            BoundingBox box = BoundingBox.of(target);
            Collection<Entity> blocking = target.getWorld().getNearbyEntities(box, entity -> entity instanceof LivingEntity);
            if (!blocking.isEmpty()) {
                return;
            }
            target.setType(Material.BARRIER, false);
        }
        checking = true;
        boolean allowed;
        try {
            allowed = new BlockPlaceEvent(target, replaced, clicked, item, player, true, event.getHand()).callEvent();
        } finally {
            checking = false;
        }
        if (!allowed) {
            replaced.update(true, false);
            return;
        }
        stations.place(type, target, Facing.towards(player.getYaw()));
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        player.swingHand(event.getHand());
        target.getWorld().playSound(target.getLocation().toCenterLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1);
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

    private boolean hasSupport(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        if (stations.at(below) != null && stations.at(below).context().type().barrier()) {
            return true;
        }
        return below.getType().isSolid() && below.getType() != Material.BARRIER;
    }

    // ---- Schutz des Stations-Blocks ----

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
        if (stations.at(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (checking) {
            return;
        }
        Station station = stations.at(event.getBlock());
        if (station != null) {
            // Barriere im Kreativmodus: wie Abbauen der Station
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
     * Der Block unter einer Station hat sich geändert: im nächsten Tick prüfen, ob sie noch steht.
     */
    private void checkSupportLater(Block changed) {
        Block above = changed.getRelative(BlockFace.UP);
        Station station = stations.at(above);
        if (station == null || !station.context().type().needsSupport()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!station.isRemoved() && !hasSupport(above)) {
                stations.breakStation(station, null);
            }
        });
    }
}
