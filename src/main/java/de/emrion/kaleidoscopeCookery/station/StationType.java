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
 */
public record StationType(String id, Key item, boolean barrier, float interactionWidth, float interactionHeight, boolean needsSupport, StationFactory factory) {

    @FunctionalInterface
    public interface StationFactory {
        Station create(StationContext context);
    }
}
