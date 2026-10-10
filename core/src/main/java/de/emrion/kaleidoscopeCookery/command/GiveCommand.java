package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.SubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /kc give &lt;item&gt; [player] [amount] - give a mod item.
 */
public class GiveCommand extends SubCommand {

    private final KaleidoscopeCookery plugin;

    public GiveCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String commandString, ArgsParser args) {
        if (!args.hasNext()) {
            ChatUtil.sendWarningMessage(sender, Lang.get("command.usage", commandString + getUsage()));
            return true;
        }
        String itemId = args.getNext();
        CookeryItem item = find(itemId);
        if (item == null) {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.give.unknown_item", ChatUtil.highlight(itemId)));
            return true;
        }
        Player target;
        if (args.hasNext()) {
            String name = args.getNext();
            target = Bukkit.getPlayerExact(name);
            if (target == null) {
                ChatUtil.sendErrorMessage(sender, Lang.get("command.player_offline", ChatUtil.highlight(name)));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.player_required"));
            return true;
        }
        int amount = 1;
        if (args.hasNext()) {
            Integer parsed = CommandHelper.parsePositive(args.getNext());
            if (parsed == null) {
                ChatUtil.sendErrorMessage(sender, Lang.get("command.invalid_number"));
                return true;
            }
            amount = Math.min(parsed, 64 * 36);
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = plugin.getItems().create(item, remaining);
            remaining -= stack.getAmount();
            for (ItemStack leftover : target.getInventory().addItem(stack).values()) {
                target.getWorld().dropItem(target.getLocation(), leftover).setOwner(target.getUniqueId());
            }
        }
        ChatUtil.sendNormalMessage(sender, Lang.get("command.give.done", ChatUtil.highlight(target.getName()), ChatUtil.highlight(amount),
                ChatUtil.highlight(Lang.raw(item.nameKey()))));
        return true;
    }

    private CookeryItem find(String id) {
        CookeryItem item = plugin.getItems().get(id);
        if (item == null && !id.contains(":")) {
            // Without namespace: Cookery first, then the addons
            item = plugin.getItems().get(Key.key("kaleidoscope_cookery", id));
            if (item == null) {
                item = plugin.getItems().all().values().stream().filter(i -> i.id().value().equals(id)).findFirst().orElse(null);
            }
        }
        return item;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.GIVE;
    }

    @Override
    public String getUsage() {
        return "<item> [spieler] [anzahl]";
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.give.description");
    }

    @Override
    public Collection<String> onTabComplete(CommandSender sender, Command command, String alias, ArgsParser args) {
        if (args.remaining() == 1) {
            List<String> ids = new ArrayList<>();
            for (Key key : plugin.getItems().all().keySet()) {
                ids.add(key.asString());
            }
            return CommandHelper.complete(args.getNext(""), ids);
        }
        args.getNext("");
        if (args.remaining() == 1) {
            return CommandHelper.complete(args.getNext(""), CommandHelper.onlinePlayerNames());
        }
        return List.of();
    }
}
