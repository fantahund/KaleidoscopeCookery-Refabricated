package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.SubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

/**
 * /kc reload - reload config.yml and recipes.yml.
 */
public class ReloadCommand extends SubCommand {

    private final KaleidoscopeCookery plugin;

    public ReloadCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String commandString, ArgsParser args) {
        plugin.reloadAll();
        ChatUtil.sendNormalMessage(sender, Lang.get("command.reload.done", ChatUtil.highlight(plugin.getRecipes().choppingBoard().size()),
                ChatUtil.highlight(plugin.getRecipes().vanilla().size())));
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.RELOAD;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.reload.description");
    }

    @Override
    public Collection<String> onTabComplete(CommandSender sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
