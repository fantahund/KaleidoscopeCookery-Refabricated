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
 * @param width              Mehrblock-Stationen: Kantenlänge des belegten Quadrats (Mühlstein: 2 = 2x2). Ungerade Breiten liegen
 *                           mittig um den Stationsblock, gerade reichen nach +x/+z (die Mitte liegt dann auf der Blockkante)
 * @param height             Anzahl belegter Blöcke übereinander (Dönerspieß: 2)
 */
public record StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory,
                          int width, int height) {

    public StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory) {
        this(id, item, barrier, interactionWidth, interactionHeight, needsSupport, factory, 1, 1);
    }

    /**
     * Alle Blöcke, die eine Station dieses Typs mit Mittelblock center belegt.
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
     * Mitte der Grundfläche relativ zur Mitte des Stationsblocks (bei gerader Breite eine halbe Block-Breite versetzt).
     */
    public double centerOffset() {
        return (minOffset() + maxOffset()) / 2.0;
    }
}
