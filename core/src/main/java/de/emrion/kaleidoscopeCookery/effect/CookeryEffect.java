package de.emrion.kaleidoscopeCookery.effect;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

/**
 * Effects from Kaleidoscope Cookery. Custom MobEffects do not exist without the mod - the plugin manages them itself
 * ({@link EffectManager}) and shows them in a boss bar with the original icons (font kaleidoscope:effects).
 */
public enum CookeryEffect {
    FLATULENCE(0xFFC6C6),
    TUNDRA_STRIDER(0xA1F8FC),
    WARMTH(0xFF5F0E),
    SATIATED_SHIELD(0xFF1313),
    VIGOR(0x84C322),
    SULFUR(0xE8B75E),
    MUSTARD(0x5A6D09),
    PRESERVATION(0xAEC639),
    HINDER(0x9E7E5A),
    PROJECTILE_DODGE(0x8E27F7),
    INSTANT_SMELTING(0xF07C1C),
    VITALITY(0x6A9E4E);

    public static final Key FONT = Key.key("kaleidoscope", "effects");
    private static final char FIRST_ICON = '';

    private final TextColor color;

    CookeryEffect(int color) {
        this.color = TextColor.color(color);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Key key() {
        return Key.key("kaleidoscope_cookery", id());
    }

    public String translationKey() {
        return "effect.kaleidoscope_cookery." + id();
    }

    public TextColor color() {
        return color;
    }

    /**
     * Character of the icon in the font kaleidoscope:effects.
     */
    public char icon() {
        return (char) (FIRST_ICON + ordinal());
    }

    public static @Nullable CookeryEffect byKey(Key key) {
        if (!key.namespace().equals("kaleidoscope_cookery")) {
            return null;
        }
        for (CookeryEffect effect : values()) {
            if (effect.id().equals(key.value())) {
                return effect;
            }
        }
        return null;
    }
}
