package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;

/**
 * Horizontal orientation of a station (like the FACING property of the mod blocks).
 */
public enum Facing {
    SOUTH(0, 180, BlockFace.SOUTH),
    WEST(1, 270, BlockFace.WEST),
    NORTH(2, 0, BlockFace.NORTH),
    EAST(3, 90, BlockFace.EAST);

    /** Minecraft get2DDataValue() - used by the mods' block entity renderers */
    private final int dataValue;
    /** y rotation in the mods' blockstate files (clockwise) */
    private final int blockstateYaw;
    private final BlockFace face;

    Facing(int dataValue, int blockstateYaw, BlockFace face) {
        this.dataValue = dataValue;
        this.blockstateYaw = blockstateYaw;
        this.face = face;
    }

    public int dataValue() {
        return dataValue;
    }

    public BlockFace face() {
        return face;
    }

    /**
     * Rotation of the block model as in the blockstate ("y": 90 ...).
     */
    public Quaternionf blockRotation() {
        return new Quaternionf().rotationY((float) Math.toRadians(-blockstateYaw));
    }

    /**
     * Rotation as in the mods' block entity renderers: Axis.YP.rotationDegrees(get2DDataValue() * 90).
     */
    public Quaternionf rendererRotation() {
        return new Quaternionf().rotationY((float) Math.toRadians(dataValue * 90));
    }

    public static Facing byDataValue(int value) {
        for (Facing facing : values()) {
            if (facing.dataValue == value) {
                return facing;
            }
        }
        return NORTH;
    }

    /**
     * The station faces the player (like getHorizontalDirection().getOpposite() when placing in the mod).
     */
    public static Facing towards(float playerYaw) {
        int index = Math.floorMod(Math.round(playerYaw / 90f), 4);
        // Player's view direction: 0 = south, 1 = west, 2 = north, 3 = east -> opposite direction
        return byDataValue((index + 2) % 4);
    }
}
