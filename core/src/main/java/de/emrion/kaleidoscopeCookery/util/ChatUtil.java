package de.emrion.kaleidoscopeCookery.util;

import de.iani.cubesideutils.bukkit.ChatUtilBukkit;
import de.iani.cubesideutils.bukkit.ChatUtilBukkit.BukkitSendable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * All messages to players go through here (structured like EssentialsChatUtil in CubesideEssentials).
 * Texts come as translation keys from the language files (see {@link Lang}).
 */
public final class ChatUtil {

    public static final TextColor BRAND_DARK = TextColor.fromHexString("#C8553D");
    public static final TextColor BRAND_LIGHT = TextColor.fromHexString("#F2A65A");

    public static final Component PLUGIN_TAG = Component.text("[", BRAND_DARK)
            .append(Component.text("Küche", BRAND_LIGHT))
            .append(Component.text("]", BRAND_DARK));

    public static final Style NORMAL = Style.style(NamedTextColor.GREEN);
    public static final Style INFO = Style.style(NamedTextColor.GRAY);
    public static final Style WARNING = Style.style(NamedTextColor.GOLD);
    public static final Style ERROR = Style.style(NamedTextColor.RED);

    private ChatUtil() {
        // prevent instances
    }

    public static void sendMessage(CommandSender sender, Style colors, Object... messageParts) {
        ChatUtilBukkit.sendMessage(sender, PLUGIN_TAG, colors, messageParts);
    }

    public static void sendNormalMessage(CommandSender sender, Object... messageParts) {
        sendMessage(sender, NORMAL, messageParts);
    }

    public static void sendInfoMessage(CommandSender sender, Object... messageParts) {
        sendMessage(sender, INFO, messageParts);
    }

    public static void sendWarningMessage(CommandSender sender, Object... messageParts) {
        sendMessage(sender, WARNING, messageParts);
    }

    public static void sendErrorMessage(CommandSender sender, Object... messageParts) {
        sendMessage(sender, ERROR, messageParts);
    }

    public static void sendMessagesPaged(CommandSender recipient, List<? extends BukkitSendable> messages, int page, Component name, String openPageCommandPrefix) {
        ChatUtilBukkit.sendMessagesPaged(recipient, messages, page, name, openPageCommandPrefix, PLUGIN_TAG, NORMAL, WARNING);
    }

    /**
     * Short hint above the hotbar (station status, wrong usage ...).
     */
    public static void sendActionBar(Player player, Component message) {
        player.sendActionBar(message);
    }

    /**
     * Highlighted value within a message.
     */
    public static Component highlight(Object value) {
        return value instanceof Component component ? component.colorIfAbsent(NamedTextColor.WHITE) : Component.text(String.valueOf(value), NamedTextColor.WHITE);
    }
}
