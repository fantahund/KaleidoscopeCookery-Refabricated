package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.PlayerSubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

/**
 * /kc menu – Kreativ-Menü zum Item-Holen öffnen.
 */
public class MenuCommand extends PlayerSubCommand {

    private final KaleidoscopeCookery plugin;

    public MenuCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(Player player, Command command, String alias, String commandString, ArgsParser args) {
        new CreativeMenuWindow(plugin, player).open();
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.GIVE;
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.menu.description");
    }
}
