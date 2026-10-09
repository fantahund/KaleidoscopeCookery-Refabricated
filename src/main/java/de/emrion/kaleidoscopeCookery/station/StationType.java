package de.emrion.kaleidoscopeCookery.station;

import net.kyori.adventure.key.Key;

/**
 * Art einer Station.
 *
 * @param id                 gespeicherter Typ-Name (nie ändern, steht im PDC)
 * @param item               Item, mit dem die Station platziert wird und das sie beim Abbauen zurückgibt
 * @param barrier            Barriere-Block für die Kollision (volle Blöcke wie Herd/Wok)
 * @param interactionWidth   Breite der Klick-Hitbox
 * @param interactionHeight  Höhe der Klick-Hitbox
 * @param needsSupport       braucht einen festen Block darunter (wird sonst abgebaut)
 * @param factory            erzeugt das Stations-Objekt
 * @param radius             Mehrblock-Stationen: belegt ein Quadrat mit diesem Radius um den Mittelblock (Mühlstein: 1 = 3x3)
 */
public record StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory,
                          int radius) {

    public StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory) {
        this(id, item, barrier, interactionWidth, interactionHeight, needsSupport, factory, 0);
    }

    /**
     * Alle Blöcke, die eine Station dieses Typs mit Mittelblock center belegt.
     */
    public java.util.List<BlockKey> footprint(BlockKey center) {
        if (radius == 0) {
            return java.util.List.of(center);
        }
        java.util.List<BlockKey> blocks = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                blocks.add(center.relative(dx, 0, dz));
            }
        }
        return blocks;
    }

    @FunctionalInterface
    public interface StationFactory {
        Station create(StationContext context);
    }
}
