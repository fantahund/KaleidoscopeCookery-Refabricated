package de.emrion.kaleidoscopeCookery.util;

import org.bukkit.NamespacedKey;

/**
 * PDC-Keys des Plugins (Namespace kaleidoscope).
 */
public final class Keys {

    public static final String NAMESPACE = "kaleidoscope";

    /** Custom-Item: ID (z. B. kaleidoscope_cookery:tomato) */
    public static final NamespacedKey ITEM = key("item");

    /** Wurzel-Entity (Interaction) einer Station: Stations-Typ */
    public static final NamespacedKey STATION_TYPE = key("station_type");
    /** Wurzel-Entity: Version des gespeicherten Zustands */
    public static final NamespacedKey STATION_VERSION = key("station_version");
    /** Wurzel-Entity: Blickrichtung beim Platzieren (0 = Süden, 1 = Westen, 2 = Norden, 3 = Osten) */
    public static final NamespacedKey STATION_FACING = key("station_facing");
    /** Wurzel-Entity: Zustand der Station (eigener PDC-Container je Typ) */
    public static final NamespacedKey STATION_STATE = key("station_state");
    /** Display einer Station: UUID der Wurzel-Entity */
    public static final NamespacedKey STATION_OWNER = key("station_owner");
    /** Display einer Station: Name des Teils (z. B. body, ingredient) */
    public static final NamespacedKey STATION_PART = key("station_part");
    /** Sitz-Entity (Mülleimer, später Stühle) */
    public static final NamespacedKey SEAT = key("seat");

    private Keys() {
    }

    public static NamespacedKey key(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }
}
