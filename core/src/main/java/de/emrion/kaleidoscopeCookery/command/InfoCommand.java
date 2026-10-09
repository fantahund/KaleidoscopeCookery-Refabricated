package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.PlayerSubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.List;

/**
 * /kc info – Zustand der angeschauten Station und Anzahl geladener Stationen.
 */
public class InfoCommand extends PlayerSubCommand {

    private final KaleidoscopeCookery plugin;

    public InfoCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(Player player, Command command, String alias, String commandString, ArgsParser args) {
        ChatUtil.sendInfoMessage(player, Lang.get("command.info.loaded", ChatUtil.highlight(plugin.getStations().loaded().size())));
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), 6, FluidCollisionMode.NEVER, true, 0,
                entity -> plugin.getStations().byRoot(entity) != null);
        Station station = null;
        if (result != null && result.getHitEntity() != null) {
            station = plugin.getStations().byRoot(result.getHitEntity());
        } else if (result != null && result.getHitBlock() != null) {
            station = plugin.getStations().at(result.getHitBlock());
        }
        if (station == null) {
            ChatUtil.sendInfoMessage(player, Lang.get("command.info.no_station"));
            return true;
        }
        ChatUtil.sendNormalMessage(player, Lang.get("command.info.station", ChatUtil.highlight(station.describe()),
                ChatUtil.highlight(station.position().x() + " " + station.position().y() + " " + station.position().z()), ChatUtil.highlight(station.facing().name())));
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.INFO;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.info.description");
    }

    @Override
    public Collection<String> onTabComplete(Player sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
