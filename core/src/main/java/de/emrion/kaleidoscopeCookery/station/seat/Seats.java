package de.emrion.kaleidoscopeCookery.station.seat;

import de.emrion.kaleidoscopeCookery.util.Keys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Seats (SitEntity in the mod): an invisible, unsaved display the player rides on.
 * Dismounting (sneaking), logging out or breaking removes the seat.
 */
public class Seats implements Listener {

    private static final java.util.EnumSet<org.bukkit.inventory.EquipmentSlot> SLOTS = java.util.EnumSet.of(org.bukkit.inventory.EquipmentSlot.HAND,
            org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST,
            org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET);

    private final Plugin plugin;
    /** Hidden players (trash can): invisible, equipment is hidden for others */
    private final java.util.Set<java.util.UUID> hidden = new java.util.HashSet<>();

    public Seats(Plugin plugin) {
        this.plugin = plugin;
        // Hide the equipment again regularly (item changes, new viewers in sight)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (java.util.UUID id : java.util.List.copyOf(hidden)) {
                Player player = plugin.getServer().getPlayer(id);
                if (player == null || !isSeat(player.getVehicle())) {
                    if (player != null) {
                        reveal(player);
                    }
                    hidden.remove(id);
                } else {
                    sendEquipment(player, true);
                }
            }
        }, 20, 20);
    }

    /**
     * Seat a player.
     *
     * @param location seat height and view direction
     * @param kind     kind of seat (e.g. "trash_can"), stored in the PDC
     */
    public void sit(Player player, Location location, String kind) {
        sit(player, location, kind, false);
    }

    /**
     * @param hide make the player invisible (hidden in the trash can)
     */
    public void sit(Player player, Location location, String kind, boolean hide) {
        if (player.isInsideVehicle()) {
            return;
        }
        ItemDisplay seat = location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(Keys.SEAT, PersistentDataType.STRING, kind);
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
            return;
        }
        if (hide) {
            hidden.add(player.getUniqueId());
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
                    org.bukkit.potion.PotionEffect.INFINITE_DURATION, 0, false, false, false));
            sendEquipment(player, true);
        }
    }

    /**
     * Show the player's equipment as empty to everyone else in the world, or real again.
     */
    private static void sendEquipment(Player player, boolean empty) {
        java.util.Map<org.bukkit.inventory.EquipmentSlot, org.bukkit.inventory.ItemStack> equipment = new java.util.EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
        for (org.bukkit.inventory.EquipmentSlot slot : SLOTS) {
            equipment.put(slot, empty ? org.bukkit.inventory.ItemStack.empty() : player.getInventory().getItem(slot));
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (!viewer.equals(player)) {
                viewer.sendEquipmentChange(player, equipment);
            }
        }
    }

    private void reveal(Player player) {
        if (hidden.remove(player.getUniqueId())) {
            org.bukkit.potion.PotionEffect effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            if (effect != null && effect.isInfinite() && !effect.hasParticles()) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            }
            sendEquipment(player, false);
        }
    }

    /**
     * In the seat you are inside the block (trash can) - no suffocation damage.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSuffocate(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION && isSeat(event.getEntity().getVehicle())) {
            event.setCancelled(true);
        }
    }

    public static boolean isSeat(@Nullable Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(Keys.SEAT, PersistentDataType.STRING);
    }

    /**
     * Is there already a seat at this seat position?
     */
    public static boolean isOccupied(Location seat) {
        return !seat.getWorld().getNearbyEntities(seat, 0.3, 0.3, 0.3, Seats::isSeat).isEmpty();
    }

    /**
     * Remove the seats in the block (station broken).
     */
    public static void removeAt(World world, Location blockCorner) {
        world.getNearbyEntities(blockCorner.clone().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5, Seats::isSeat).forEach(Entity::remove);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (isSeat(event.getDismounted())) {
            Entity seat = event.getDismounted();
            // Lift slightly after dismounting so you are not stuck in the block
            Entity rider = event.getEntity();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                seat.remove();
                if (rider instanceof Player player) {
                    reveal(player);
                }
                if (rider.isValid()) {
                    rider.teleport(rider.getLocation().add(0, 0.5, 0));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        reveal(event.getPlayer());
        if (isSeat(vehicle)) {
            vehicle.eject();
            vehicle.remove();
        }
    }

    public void removeAll() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (isSeat(display)) {
                    for (Entity passenger : display.getPassengers()) {
                        if (passenger instanceof Player player) {
                            reveal(player);
                        }
                    }
                    display.eject();
                    display.remove();
                }
            }
        }
    }
}
