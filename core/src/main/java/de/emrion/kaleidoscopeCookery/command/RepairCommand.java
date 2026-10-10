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
 * /kc repair - check stations in loaded chunks: add missing displays/barriers, remove orphans.
 */
public class RepairCommand extends SubCommand {

    private final KaleidoscopeCookery plugin;

    public RepairCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String commandString, ArgsParser args) {
        int fixes = plugin.getStations().repair();
        ChatUtil.sendNormalMessage(sender, Lang.get("command.repair.done", ChatUtil.highlight(plugin.getStations().loaded().size()), ChatUtil.highlight(fixes)));
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.REPAIR;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.repair.description");
    }

    @Override
    public Collection<String> onTabComplete(CommandSender sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
