package de.emrion.kaleidoscopeCookery.util;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Kleine Item-Bilder als Text (Sprite aus dem Item-Atlas), z. B. für Knöpfe im Kochbuch.
 */
public final class ItemIcons {

    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");
    private static final Key GUI_ATLAS = Key.key("minecraft", "gui");
    private static final Key BLOCKS_ATLAS = Key.key("minecraft", "blocks");
    /** Item-Texturen aus dem Resourcepack (namespace:item/name) */
    private static final Set<Key> PACK_TEXTURES = new HashSet<>();

    private ItemIcons() {
    }

    public static void load(ZipFile pack) {
        PACK_TEXTURES.clear();
        pack.stream().map(ZipEntry::getName)
                .filter(name -> name.startsWith("assets/") && name.contains("/textures/item/") && name.endsWith(".png"))
                .forEach(name -> {
                    String[] parts = name.split("/", 4);
                    String path = parts[3].substring("item/".length(), parts[3].length() - 4);
                    PACK_TEXTURES.add(Key.key(parts[1], "item/" + path));
                });
    }

    /**
     * Bild eines Items; null, wenn es keine flache Item-Textur gibt (z. B. Blöcke).
     */
    public static @Nullable Component of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Key id = CookeryItems.idOf(stack);
        if (id != null) {
            Key texture = Key.key(id.namespace(), "item/" + id.value());
            return PACK_TEXTURES.contains(texture) ? Component.object(ObjectContents.sprite(ITEMS_ATLAS, texture)) : null;
        }
        Material type = stack.getType();
        if (type.isBlock() && type != Material.WHEAT && type != Material.SUGAR_CANE && type != Material.KELP) {
            return null;
        }
        return Component.object(ObjectContents.sprite(ITEMS_ATLAS, Key.key("minecraft", "item/" + type.key().value())));
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
