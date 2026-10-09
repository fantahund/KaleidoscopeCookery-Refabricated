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
 * @param height             Anzahl belegter Blöcke übereinander (Dönerspieß: 2)
 */
public record StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory,
                          int radius, int height) {

    public StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory) {
        this(id, item, barrier, interactionWidth, interactionHeight, needsSupport, factory, 0, 1);
    }

    /**
     * Alle Blöcke, die eine Station dieses Typs mit Mittelblock center belegt.
     */
    public java.util.List<BlockKey> footprint(BlockKey center) {
        if (radius == 0 && height == 1) {
            return java.util.List.of(center);
        }
        java.util.List<BlockKey> blocks = new java.util.ArrayList<>();
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
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
}
