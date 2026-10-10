package de.emrion.kaleidoscopeCookery.station.crop;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pflanzen setzen, Ackerland unter Pflanzen erhalten und Samen aus Gras mit Strohhut.
 */
public class CropListener implements Listener {

    private static final List<Key> STRAW_HATS = List.of(Key.key("kaleidoscope_cookery", "straw_hat"), Key.key("kaleidoscope_cookery", "straw_hat_flower"));

    /** loot_table straw_hat_seed_drop: Samen mit ihrer Chance */
    private record SeedChance(Key item, double chance) {
    }

    private static final List<SeedChance> GRASS_SEEDS = List.of(
            new SeedChance(Key.key("kaleidoscope_cookery", "tomato_seed"), 0.125),
            new SeedChance(Key.key("kaleidoscope_cookery", "chili_seed"), 0.125),
            new SeedChance(Key.key("kaleidoscope_cookery", "lettuce_seed"), 0.125),
            new SeedChance(Key.key("kaleidoscope_cookery", "wild_rice"), 0.125),
            new SeedChance(Key.key("kaleidoscope_cookery", "tea_seed"), 0.125),
            new SeedChance(Key.key("minecraft", "beetroot_seeds"), 0.02),
            new SeedChance(Key.key("minecraft", "pumpkin_seeds"), 0.02),
            new SeedChance(Key.key("minecraft", "melon_seeds"), 0.02));

    private final KaleidoscopeCookery plugin;
    private final StationManager stations;
    private boolean checking;

    public CropListener(KaleidoscopeCookery plugin, StationManager stations) {
        this.plugin = plugin;
        this.stations = stations;
    }

    // ---- Setzen ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null || checking) {
            return;
        }
        ItemStack item = event.getItem();
        CropKind kind = CropKind.bySeed(CookeryItems.idOf(item));
        if (kind == null || de.emrion.kaleidoscopeCookery.station.StationListener.opensOnClick(event)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(Permissions.USE)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Block target = clicked.getRelative(event.getBlockFace());
        if (!canPlant(kind, target)) {
            return;
        }
        BlockState replaced = target.getState();
        checking = true;
        boolean allowed;
        try {
            allowed = new BlockPlaceEvent(target, replaced, clicked, item, player, true, event.getHand()).callEvent();
        } finally {
            checking = false;
        }
        if (!allowed) {
            return;
        }
        Station station = stations.place(stations.type(kind.typeId()), target, Facing.NORTH);
        if (!station.canSurvive()) {
            stations.remove(station);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        player.swingHand(event.getHand());
        target.getWorld().playSound(target.getLocation().toCenterLocation(), Sound.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 1, 1);
    }

    private boolean canPlant(CropKind kind, Block target) {
        if (stations.at(target) != null) {
            return false;
        }
        Block below = target.getRelative(BlockFace.DOWN);
        return switch (kind.soil()) {
            case FARMLAND -> target.getType().isAir() && below.getType() == Material.FARMLAND;
            case DIRT -> target.getType().isAir() && CropKind.DIRT_121.contains(below.getType());
            // RiceCropBlock#getStateForPlacement: im Wasser auf Ackerland, darüber zwei Blöcke Luft
            case WATER -> CropStation.isWaterSource(target) && below.getType() == Material.FARMLAND
                    && target.getY() < target.getWorld().getMaxHeight() - 2
                    && target.getRelative(0, 1, 0).getType().isAir() && target.getRelative(0, 2, 0).getType().isAir()
                    && stations.at(target.getRelative(0, 1, 0)) == null && stations.at(target.getRelative(0, 2, 0)) == null;
            case FARMLAND_OR_SOUL_SAND -> target.getType().isAir() && (below.getType() == Material.FARMLAND || below.getType() == Material.SOUL_SAND);
        };
    }

    // ---- Ackerland ----

    private static final java.util.Set<Material> TILLABLE = java.util.EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT);

    /**
     * Reisfelder: Vanilla hackt nur Erde mit Luft darüber – hier geht es auch unter einer Wasserquelle
     * (grobe Erde wird wie bei Vanilla zu Erde).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onTillUnderWater(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getHand() != EquipmentSlot.HAND || checking
                || !TILLABLE.contains(clicked.getType()) || !Tag.ITEMS_HOES.isTagged(event.getMaterial())
                || !CropStation.isWaterSource(clicked.getRelative(BlockFace.UP)) || event.getBlockFace() == BlockFace.DOWN) {
            return;
        }
        Player player = event.getPlayer();
        BlockState before = clicked.getState();
        Material result = clicked.getType() == Material.COARSE_DIRT ? Material.DIRT : Material.FARMLAND;
        clicked.setType(result);
        checking = true;
        boolean allowed;
        try {
            allowed = new BlockPlaceEvent(clicked, before, clicked, event.getItem(), player, true, EquipmentSlot.HAND).callEvent();
        } finally {
            checking = false;
        }
        if (!allowed) {
            before.update(true, false);
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.damageItemStack(EquipmentSlot.HAND, 1);
        }
        player.swingMainHand();
        clicked.getWorld().playSound(clicked.getLocation().toCenterLocation(), Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1, 1);
    }

    /**
     * Vanilla hält Ackerland nur unter echten Pflanzen feucht genug – unter unseren Pflanzen darf es nicht zu Erde werden.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandDry(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && stations.at(event.getBlock().getRelative(BlockFace.UP)) instanceof CropStation) {
            event.setCancelled(true);
        }
    }

    // ---- Samen aus Gras mit Strohhut ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrassBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (event.getBlock().getType() != Material.SHORT_GRASS || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack helmet = player.getInventory().getItem(EquipmentSlot.HEAD);
        if (!STRAW_HATS.contains(CookeryItems.idOf(helmet))) {
            return;
        }
        // Ein Pool mit einem Wurf: unter den bestandenen Einträgen wird einer gewählt
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Key> passed = new ArrayList<>();
        for (SeedChance seed : GRASS_SEEDS) {
            if (random.nextDouble() < seed.chance()) {
                passed.add(seed.item());
            }
        }
        if (passed.isEmpty()) {
            return;
        }
        int fortune = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE);
        int count = 1 + (fortune > 0 ? random.nextInt(fortune * 2 + 1) : 0);
        ItemStack drop = plugin.getItems().create(passed.get(random.nextInt(passed.size())), count);
        if (drop != null) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().toCenterLocation(), drop);
        }
    }
}
