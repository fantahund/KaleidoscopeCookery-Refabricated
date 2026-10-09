package de.emrion.kaleidoscopeCookery.util;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.PointedDripstone;

/**
 * Wetter-Abfragen, die die Bukkit-API nicht direkt bietet.
 */
public final class Weather {

    /** PointedDripstoneBlock#MAX_SEARCH_LENGTH_WHEN_LOOKING_FOR_TIP_OF_FALLING_STALACTITE bzw. Suche über dem Kessel */
    private static final int DRIPSTONE_SEARCH = 11;
    private static final int DRIPSTONE_LENGTH = 11;

    private Weather() {
    }

    /**
     * Level#canSeeSky: kein Block der Bewegung blockiert darüber.
     */
    public static boolean canSeeSky(Block block) {
        return block.getWorld().getHighestBlockYAt(block.getX(), block.getZ(), HeightMap.MOTION_BLOCKING) < block.getY();
    }

    /**
     * Level#isRainingAt: Gewitter/Regen, freier Himmel und ein Biom mit Regen (nicht Schnee, nicht Wüste/Savanne/Tafelberge).
     */
    public static boolean isRainingAt(Block block) {
        if (!block.getWorld().hasStorm() || !canSeeSky(block)) {
            return false;
        }
        double temperature = block.getTemperature();
        return temperature >= 0.15 && temperature < 1.0;
    }

    /**
     * PointedDripstoneBlock#findStalactiteTipAboveCauldron + getCauldronFillFluidType == WATER:
     * über dem Block hängt ein Tropfstein, über dessen Wurzel Wasser (oder Schlamm außerhalb des Nethers) ist.
     */
    public static boolean hasWaterDripstone(Block block) {
        Block tip = null;
        for (int i = 1; i <= DRIPSTONE_SEARCH; i++) {
            Block above = block.getRelative(0, i, 0);
            if (above.getType() == Material.POINTED_DRIPSTONE) {
                tip = above;
                break;
            }
            if (!above.getType().isAir()) {
                return false;
            }
        }
        if (tip == null || !(tip.getBlockData() instanceof PointedDripstone data) || data.getVerticalDirection() != BlockFace.DOWN
                || data.getThickness() != PointedDripstone.Thickness.TIP) {
            return false;
        }
        Block root = tip;
        for (int i = 0; i < DRIPSTONE_LENGTH && root.getRelative(BlockFace.UP).getType() == Material.POINTED_DRIPSTONE; i++) {
            root = root.getRelative(BlockFace.UP);
        }
        Block source = root.getRelative(BlockFace.UP);
        if (source.getType() == Material.WATER || source.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        return source.getType() == Material.MUD && block.getWorld().getEnvironment() != World.Environment.NETHER;
    }
}
