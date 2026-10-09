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
    private static final Key ICON_FONT = Key.key("kaleidoscope", "icons");

    private ItemIcons() {
    }

    public static void load(org.bukkit.plugin.Plugin plugin) {
        ICONS.clear();
        BIG_ICONS.clear();
        try (java.io.InputStream in = plugin.getResource("item_icons.txt")) {
            if (in == null) {
                plugin.getLogger().warning("item_icons.txt fehlt im Plugin – keine Bilder im Kochbuch");
                return;
            }
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                int equals = line.indexOf('=');
                int bar = line.indexOf('|');
                if (equals < 0 || bar < equals) {
                    continue;
                }
                String[] sprite = line.substring(bar + 1).trim().split("\\|");
                Key item = Key.key(line.substring(0, equals));
                ICONS.put(item, Component.object(ObjectContents.sprite(Key.key(line.substring(equals + 1, bar)), Key.key(sprite[0]))));
                if (sprite.length > 1 && !sprite[1].isEmpty()) {
                    BIG_ICONS.put(item, Component.text(Character.toString(Integer.parseInt(sprite[1], 16))).font(ICON_FONT)
                            .color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
                }
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("item_icons.txt nicht lesbar: " + e.getMessage());
        }
    }

    /**
     * Bild eines Items (flache Item-Textur oder bei Blöcken eine Seite des Blocks); null, wenn keins bekannt ist.
     */
    public static @Nullable Component of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = CookeryItems.idOf(stack);
        return ICONS.get(id != null ? id : stack.getType().key());
    }

    /**
     * Großes Bild (16 Pixel, für das Werkbank-Raster), null wenn keins bekannt ist.
     */
    public static @Nullable Component big(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = CookeryItems.idOf(stack);
        return BIG_ICONS.get(id != null ? id : stack.getType().key());
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
