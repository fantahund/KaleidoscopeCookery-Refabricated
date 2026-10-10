package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.CommandExceptionHandler;
import de.iani.cubesideutils.bukkit.commands.exceptions.DisallowsCommandBlockException;
import de.iani.cubesideutils.bukkit.commands.exceptions.NoPermissionException;
import de.iani.cubesideutils.bukkit.commands.exceptions.NoPermissionForPathException;
import de.iani.cubesideutils.bukkit.commands.exceptions.RequiresPlayerException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Error messages of the CommandRouter via ChatUtil (texts from the language file).
 */
public class GermanExceptionHandler implements CommandExceptionHandler {

    @Override
    public boolean handleDisallowsCommandBlock(DisallowsCommandBlockException thrown) {
        ChatUtil.sendErrorMessage(thrown.getSender(), Lang.get("command.no_command_block"));
        return true;
    }

    @Override
    public boolean handleRequiresPlayer(RequiresPlayerException thrown) {
        ChatUtil.sendErrorMessage(thrown.getSender(), Lang.get("command.player_only"));
        return true;
    }

    @Override
    public boolean handleNoPermission(NoPermissionException thrown) {
        ChatUtil.sendErrorMessage(thrown.getSender(), Lang.get("command.no_permission"));
        return true;
    }

    @Override
    public boolean handleNoPermissionForPath(NoPermissionForPathException thrown) {
        ChatUtil.sendErrorMessage(thrown.getSender(), Lang.get("command.no_permission"));
        return true;
    }

    @Override
    public Component getHelpMessagePrefix() {
        return ChatUtil.PLUGIN_TAG.append(Component.text(" ", NamedTextColor.GRAY));
    }
}
