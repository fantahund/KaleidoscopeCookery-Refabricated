package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import de.emrion.kaleidoscopeCookery.station.crop.CropStation;
import de.emrion.kaleidoscopeCookery.util.BreakPermissionCheck;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Sichel (SickleItem im Mod): Rechtsklick auf einen Block erntet reife Pflanzen und entfernt Gras, Blumen und
 * Büsche in 5x5 (mit "Schwung" 7x7 bis 11x11), jeweils in der Klickhöhe und einen Block darüber.
 */
public class SickleListener implements Listener {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "sickle");
    public static final Key SWEEP = Key.key("kaleidoscope_cookery", "sweep");
    private static final int COOLDOWN_TICKS = 10;

    /** Vanilla-Pflanzen mit Altersstufen (CropBlock): reif ernten, Stufe 0 bleibt stehen */
    private static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.TORCHFLOWER_CROP);
    /** tags/block/sickle_harvest_blacklist.json */
    private static final Set<Material> BLACKLIST = EnumSet.of(Material.MELON_STEM, Material.PUMPKIN_STEM, Material.ATTACHED_MELON_STEM, Material.ATTACHED_PUMPKIN_STEM);
    /** BushBlock in 1.21.1 (Gras, Farne, Blumen, Setzlinge, Pilze, Beeren ...) und die neuen Büsche aus 26.x */
    private static final Set<Material> BUSHES = EnumSet.of(Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN,
            Material.DEAD_BUSH, Material.SWEET_BERRY_BUSH, Material.NETHER_WART, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM,
            Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.NETHER_SPROUTS,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.LILY_PAD, Material.SMALL_DRIPLEAF, Material.PITCHER_CROP,
            Material.BUSH, Material.FIREFLY_BUSH, Material.SHORT_DRY_GRASS, Material.TALL_DRY_GRASS, Material.LEAF_LITTER);

    private final StationManager stations;

    public SickleListener(StationManager stations) {
        this.stations = stations;
    }

    public static boolean isSickle(@Nullable ItemStack stack) {
        return ITEM.equals(CookeryItems.idOf(stack));
    }

    private static int sweepLevel(ItemStack stack) {
        Enchantment sweep = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(SWEEP);
        return sweep == null ? 0 : stack.getEnchantmentLevel(sweep);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
                || !isSickle(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        use(event.getPlayer(), event.getClickedBlock());
    }

    /**
     * Klick auf eine Pflanzen-Station (Interaction) – vor dem StationListener, sonst würde nur die eine Pflanze geerntet.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseStation(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isSickle(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        Station station = stations.byEntity(event.getRightClicked());
        if (station == null) {
            return;
        }
        event.setCancelled(true);
        Block block = station.position().block();
        if (block != null) {
            use(event.getPlayer(), block);
        }
    }

    private void use(Player player, Block clicked) {
        ItemStack sickle = player.getInventory().getItemInMainHand();
        if (!player.hasPermission(Permissions.USE) || player.hasCooldown(sickle)) {
            return;
        }
        int level = sweepLevel(sickle);
        int radius = 2 + level;
        Set<Station> harvested = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    harvest(player, clicked.getRelative(x, y, z), harvested);
                }
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1, 1);
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() > 0) {
            look.normalize();
        }
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(look).add(0, player.getHeight() * 0.5, 0), 1, 0, 0, 0, 0);
        int cost = switch (level) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 8;
            default -> 1;
        };
        player.damageItemStack(EquipmentSlot.HAND, cost);
        player.setCooldown(sickle, COOLDOWN_TICKS);
        player.swingMainHand();
    }

    private void harvest(Player player, Block block, Set<Station> harvested) {
        Station station = stations.at(block);
        if (station instanceof CropStation crop) {
            if (harvested.add(crop) && mayBreak(player, block)) {
                crop.sickleHarvest();
            }
            return;
        }
        Material type = block.getType();
        if (type.isAir() || BLACKLIST.contains(type)) {
            return;
        }
        if (CROPS.contains(type) && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() >= ageable.getMaximumAge() && mayBreak(player, block)) {
                for (ItemStack drop : block.getDrops()) {
                    block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), drop);
                }
                block.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, block.getBlockData());
                ageable.setAge(0);
                block.setBlockData(ageable);
            }
            return;
        }
        if (BUSHES.contains(type) || Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type)) {
            // Wie gameMode.destroyBlock: normales Abbauen durch den Spieler (Events, Drops)
            player.breakBlock(block);
        }
    }

    /**
     * Schutz-Plugins fragen (im Mod: level.mayInteract).
     */
    private static boolean mayBreak(Player player, Block block) {
        return BreakPermissionCheck.mayBreak(player, block);
    }

    /**
     * Basis der Sichel ist der Konstruktionsblock: OPs im Kreativmodus könnten ihn sonst platzieren.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isSickle(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }
}
