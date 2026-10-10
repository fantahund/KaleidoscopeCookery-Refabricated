package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Block position of a station (without a reference to the world so nothing is kept alive).
 */
public record BlockKey(UUID world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public BlockKey relative(int dx, int dy, int dz) {
        return new BlockKey(world, x + dx, y + dy, z + dz);
    }

    public BlockKey below() {
        return relative(0, -1, 0);
    }

    public BlockKey above() {
        return relative(0, 1, 0);
    }

    public @Nullable Block block() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    /**
     * Centre of the bottom face of the block.
     */
    public Location bottomCenter(World w) {
        return new Location(w, x + 0.5, y, z + 0.5);
    }
}
