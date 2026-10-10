package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.PlayerSubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * /kc pack - load the resource pack again.
 */
public class PackCommand extends PlayerSubCommand {

    private final KaleidoscopeCookery plugin;

    public PackCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(Player player, Command command, String alias, String commandString, ArgsParser args) {
        plugin.getResourcePack().send(player);
        ChatUtil.sendInfoMessage(player, Lang.get("command.pack.sent"));
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.USE;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.pack.description");
    }

    @Override
    public Collection<String> onTabComplete(Player sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
