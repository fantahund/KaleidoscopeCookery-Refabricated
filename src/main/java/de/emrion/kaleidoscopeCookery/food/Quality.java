package de.emrion.kaleidoscopeCookery.food;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Qualität eines Gerichts (Quality im Mod): ab welcher Bewertung sie gilt und wie stark sie Nährwert und Effektdauer skaliert.
 */
public enum Quality {
    SUPERB(0.95, 1.2, NamedTextColor.GOLD),
    EXCELLENT(0.82, 0.9, NamedTextColor.GREEN),
    STANDARD(0.55, 0.6, NamedTextColor.WHITE),
    POOR(0, 0.3, NamedTextColor.DARK_GRAY);

    private final double score;
    private final double ratio;
    private final NamedTextColor color;

    Quality(double score, double ratio, NamedTextColor color) {
        this.score = score;
        this.ratio = ratio;
        this.color = color;
    }

    public double score() {
        return score;
    }

    public double ratio() {
        return ratio;
    }

    public NamedTextColor color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Übersetzungs-Key aus der Mod-Sprachdatei.
     */
    public String translationKey() {
        return "tooltip.kaleidoscope_cookery.cuisine_quality." + id();
    }

    public static @Nullable Quality byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Quality quality : values()) {
            if (quality.id().equals(id)) {
                return quality;
            }
        }
        return null;
    }
}
