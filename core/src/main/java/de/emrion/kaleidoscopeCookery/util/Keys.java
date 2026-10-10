package de.emrion.kaleidoscopeCookery.util;

import org.bukkit.NamespacedKey;

/**
 * PDC keys of the plugin (namespace kaleidoscope).
 */
public final class Keys {

    public static final String NAMESPACE = "kaleidoscope";

    /** Custom-Item: ID (z. B. kaleidoscope_cookery:tomato) */
    public static final NamespacedKey ITEM = key("item");

    /** Root entity (Interaction) of a station: station type */
    public static final NamespacedKey STATION_TYPE = key("station_type");
    /** Root entity: version of the stored state */
    public static final NamespacedKey STATION_VERSION = key("station_version");
    /** Root entity: view direction when placed (0 = south, 1 = west, 2 = north, 3 = east) */
    public static final NamespacedKey STATION_FACING = key("station_facing");
    /** Root entity: state of the station (own PDC container per type) */
    public static final NamespacedKey STATION_STATE = key("station_state");
    /** Display of a station: UUID of the root entity */
    public static final NamespacedKey STATION_OWNER = key("station_owner");
    /** Display of a station: name of the part (e.g. body, ingredient) */
    public static final NamespacedKey STATION_PART = key("station_part");
    /** Seat entity (trash can, later chairs) */
    public static final NamespacedKey SEAT = key("seat");

    private Keys() {
    }

    public static NamespacedKey key(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }
}
