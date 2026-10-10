package de.emrion.kaleidoscopeCookery.station;

import org.bukkit.entity.Interaction;

/**
 * Everything a station needs to know about itself.
 *
 * @param root Interaction entity: click hitbox and holder of the stored state
 */
public record StationContext(StationManager manager, StationType type, Interaction root, BlockKey position, Facing facing) {
}
