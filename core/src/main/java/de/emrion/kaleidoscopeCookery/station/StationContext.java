package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.entity.Interaction;

/**
 * Alles, was eine Station über sich selbst wissen muss.
 *
 * @param root Interaction-Entity: Klick-Hitbox und Träger des gespeicherten Zustands
 */
public record StationContext(StationManager manager, StationType type, Interaction root, BlockKey position, Facing facing) {
}
