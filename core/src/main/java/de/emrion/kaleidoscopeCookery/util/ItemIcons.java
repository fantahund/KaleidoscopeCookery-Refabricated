package de.emrion.kaleidoscopeCookery.util;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;


/**
 * Kleine Item-Bilder als Text (Sprite aus dem Item-Atlas), z. B. für Knöpfe im Kochbuch.
 */
public final class ItemIcons {

    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");
    private static final Key GUI_ATLAS = Key.key("minecraft", "gui");
    private static final Key BLOCKS_ATLAS = Key.key("minecraft", "blocks");
    /** Item -> Sprite (aus item_icons.txt, erzeugt von tools/build_pack.py aus den Item-Modellen) */
    private static final java.util.Map<Key, Component> ICONS = new java.util.HashMap<>();
    /** Item -> großes Bild (16 Pixel) als Zeichen der Schrift kaleidoscope:icons */
    private static final java.util.Map<Key, Component> BIG_ICONS = new java.util.HashMap<>();
    /** Item -> großes Stations-Bild fürs Rezept-Diagramm (32 Pixel) */
    private static final java.util.Map<Key, Component> DIAGRAMS = new java.util.HashMap<>();

    private ItemIcons() {
    }

    public static void load(org.bukkit.plugin.Plugin plugin) {
        ICONS.clear();
        BIG_ICONS.clear();
        DIAGRAMS.clear();
        add(plugin);
    }

    /**
     * Bilder eines weiteren Plugins (Addon mit eigenem Pack und eigener Bild-Schrift) dazunehmen.
     */
    public static void add(org.bukkit.plugin.Plugin plugin) {
        Key font = Key.key("kaleidoscope", "icons");
        try (java.io.InputStream in = plugin.getResource("item_icons.txt")) {
            if (in == null) {
                plugin.getLogger().warning("item_icons.txt fehlt im Plugin – keine Bilder im Kochbuch");
                return;
            }
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith("@font|")) {
                    font = Key.key(line.substring("@font|".length()).trim());
                    continue;
                }
                if (line.startsWith("@diagram|")) {
                    String[] parts = line.substring("@diagram|".length()).trim().split("=");
                    DIAGRAMS.put(Key.key(parts[0]), padded(parts[1], DIAGRAM_WIDTH, font));
                    continue;
                }
                int equals = line.indexOf('=');
                int bar = line.indexOf('|');
                if (equals < 0 || bar < equals) {
                    continue;
                }
                String[] sprite = line.substring(bar + 1).trim().split("\\|");
                Key item = Key.key(line.substring(0, equals));
                // 3D-Modelle haben eine kleine gerenderte Ansicht, sonst die flache Textur
                ICONS.put(item, sprite.length > 2 ? glyph(sprite[2], font)
                        : Component.object(ObjectContents.sprite(Key.key(line.substring(equals + 1, bar)), Key.key(sprite[0]))));
                if (sprite.length > 1 && !sprite[1].isEmpty()) {
                    BIG_ICONS.put(item, padded(sprite[1], BIG_WIDTH, font));
                }
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("item_icons.txt nicht lesbar: " + e.getMessage());
        }
    }

    /**
     * Bild eines Items (flache Item-Textur, bei 3D-Modellen eine kleine gerenderte Ansicht); null, wenn keins bekannt ist.
     */
    public static @Nullable Component of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = CookeryItems.idOf(stack);
        return ICONS.get(id != null ? id : stack.getType().key());
    }

    /** Vorschub eines großen Item-Bildes bzw. Stations-Bildes, wie die Slots im Kochbuch ihn erwarten */
    public static final int BIG_WIDTH = 17;
    public static final int DIAGRAM_WIDTH = 33;
    private static final Key PAD_FONT = Key.key("kaleidoscope", "cookbook");

    /**
     * Schriftzeichen "HEX:Vorschub" auf eine feste Breite bringen: der Client kürzt Bilder rechts auf den letzten
     * sichtbaren Pixel, schmalere Bilder werden deshalb mit Abständen links und rechts mittig ausgeglichen.
     */
    private static Component padded(String glyph, int width, Key font) {
        String[] parts = glyph.split(":");
        int advance = parts.length > 1 ? Integer.parseInt(parts[1]) : width;
        int missing = width - advance;
        Component image = glyph(glyph, font);
        if (missing == 0) {
            return image;
        }
        return Component.text().append(pad(missing / 2)).append(image).append(pad(missing - missing / 2)).build();
    }

    private static Component glyph(String glyph, Key font) {
        return Component.text(Character.toString(Integer.parseInt(glyph.split(":")[0], 16))).font(font)
                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE).shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
    }

    /**
     * Leerraum von n Pixeln (auch negativ), Schrift kaleidoscope:cookbook.
     */
    public static Component pad(int pixels) {
        int clamped = Math.clamp(pixels, -63, 63);
        return Component.text(Character.toString(clamped >= 0 ? 0xE300 + clamped : 0xE340 - clamped)).font(PAD_FONT);
    }

    public static @Nullable Component diagram(Key item) {
        return DIAGRAMS.get(item);
    }

    /**
     * Großes Bild (16 Pixel, für das Werkbank-Raster), null wenn keins bekannt ist.
     */
    public static @Nullable Component big(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = CookeryItems.idOf(stack);
        Component icon = BIG_ICONS.get(id != null ? id : stack.getType().key());
        if (icon == null) {
            // Eigene Items mit Vanilla-Aussehen (Kochbuch = Buch): Bild des Modells
            @SuppressWarnings("UnstableApiUsage")
            Key model = stack.getData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL);
            icon = model == null ? null : BIG_ICONS.get(model);
        }
        return icon;
    }

    /**
     * Bild + Leerzeichen + Text, ohne Bild nur der Text.
     */
    public static Component withIcon(@Nullable ItemStack stack, Component text) {
        Component icon = of(stack);
        return icon == null ? text : Component.text().append(icon).append(Component.text(" ")).append(text).build();
    }

    public static Component item(Key sprite) {
        return Component.object(ObjectContents.sprite(ITEMS_ATLAS, sprite));
    }

    public static Component gui(Key sprite) {
        return Component.object(ObjectContents.sprite(GUI_ATLAS, sprite));
    }

    public static Component block(Key sprite) {
        return Component.object(ObjectContents.sprite(BLOCKS_ATLAS, sprite));
    }
}
