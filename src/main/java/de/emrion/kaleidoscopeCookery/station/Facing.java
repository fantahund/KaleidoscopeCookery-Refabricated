package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.block.BlockFace;
import org.joml.Quaternionf;

/**
 * Horizontale Ausrichtung einer Station (wie die FACING-Property der Mod-Blöcke).
 */
public enum Facing {
    SOUTH(0, 180, BlockFace.SOUTH),
    WEST(1, 270, BlockFace.WEST),
    NORTH(2, 0, BlockFace.NORTH),
    EAST(3, 90, BlockFace.EAST);

    /** Minecraft get2DDataValue() – wird von den Block-Entity-Renderern der Mods benutzt */
    private final int dataValue;
    /** y-Rotation in den Blockstate-Dateien der Mods (im Uhrzeigersinn) */
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
     * Rotation des Blockmodells wie im Blockstate ("y": 90 ...).
     */
    public Quaternionf blockRotation() {
        return new Quaternionf().rotationY((float) Math.toRadians(-blockstateYaw));
    }

    /**
     * Rotation wie in den Block-Entity-Renderern der Mods: Axis.YP.rotationDegrees(get2DDataValue() * 90).
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
     * Station zeigt zum Spieler (wie getHorizontalDirection().getOpposite() beim Platzieren im Mod).
     */
    public static Facing towards(float playerYaw) {
        int index = Math.floorMod(Math.round(playerYaw / 90f), 4);
        // Blickrichtung des Spielers: 0 = Süden, 1 = Westen, 2 = Norden, 3 = Osten -> Gegenrichtung
        return byDataValue((index + 2) % 4);
    }
}
