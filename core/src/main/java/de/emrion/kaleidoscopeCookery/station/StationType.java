package de.emrion.kaleidoscopeCookery.station;

import net.kyori.adventure.key.Key;

/**
 * Kind of station.
 *
 * @param id                 stored type name (never change it, it is in the PDC)
 * @param item               item that places the station and that it returns when broken
 * @param barrier            barrier block for collision (full blocks such as the stove)
 * @param interactionWidth   width of the click hitbox
 * @param interactionHeight  height of the click hitbox
 * @param needsSupport       needs a solid block below (otherwise it is broken)
 * @param factory            creates the station object
 * @param width              multi-block stations: edge length of the occupied square (millstone: 2 = 2x2). Odd widths are
 *                           centred on the station block, even ones extend towards +x/+z (the centre is then on the block edge)
 * @param height             number of occupied blocks stacked on top of each other (shawarma spit: 2)
 */
public record StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory,
                          int width, int height) {

    public StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory) {
        this(id, item, barrier, interactionWidth, interactionHeight, needsSupport, factory, 1, 1);
    }

    /**
     * All blocks a station of this type with centre block center occupies.
     */
    public java.util.List<BlockKey> footprint(BlockKey center) {
        if (width == 1 && height == 1) {
            return java.util.List.of(center);
        }
        java.util.List<BlockKey> blocks = new java.util.ArrayList<>();
        for (int dy = 0; dy < height; dy++) {
            for (int dx = minOffset(); dx <= maxOffset(); dx++) {
                for (int dz = minOffset(); dz <= maxOffset(); dz++) {
                    blocks.add(center.relative(dx, dy, dz));
                }
            }
        }
        return blocks;
    }

    @FunctionalInterface
    public interface StationFactory {
        Station create(StationContext context);
    }

    public int minOffset() {
        return -(width - 1) / 2;
    }

    public int maxOffset() {
        return width / 2;
    }

    /**
     * Centre of the footprint relative to the centre of the station block (offset by half a block for even widths).
     */
    public double centerOffset() {
        return (minOffset() + maxOffset()) / 2.0;
    }
}
