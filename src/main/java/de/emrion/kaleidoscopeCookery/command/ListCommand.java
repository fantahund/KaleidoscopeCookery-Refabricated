package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.Station;
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
 * /kc list – alle geladenen Stationen.
 */
public class ListCommand extends SubCommand {

    private final KaleidoscopeCookery plugin;

    public ListCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String commandString, ArgsParser args) {
        Collection<Station> stations = plugin.getStations().loaded();
        ChatUtil.sendInfoMessage(sender, Lang.get("command.info.loaded", ChatUtil.highlight(stations.size())));
        for (Station station : stations) {
            ChatUtil.sendInfoMessage(sender, Lang.get("command.info.station", ChatUtil.highlight(station.describe()),
                    ChatUtil.highlight(station.world().getName() + " " + station.position().x() + " " + station.position().y() + " " + station.position().z()),
                    ChatUtil.highlight(station.facing().name())));
        }
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.INFO;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.list.description");
    }

    @Override
    public Collection<String> onTabComplete(CommandSender sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
