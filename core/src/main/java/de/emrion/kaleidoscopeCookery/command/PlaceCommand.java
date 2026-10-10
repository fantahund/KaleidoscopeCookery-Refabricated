package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.station.Facing;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.ChatUtil;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.SubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /kc place &lt;type&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [world] [facing] - place a station without an item (building, tests).
 */
public class PlaceCommand extends SubCommand {

    private final KaleidoscopeCookery plugin;

    public PlaceCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String commandString, ArgsParser args) {
        if (args.remaining() < 4) {
            ChatUtil.sendWarningMessage(sender, Lang.get("command.usage", commandString + getUsage()));
            return true;
        }
        String typeId = args.getNext();
        StationType type = plugin.getStations().types().stream().filter(t -> t.id().equals(typeId)).findFirst().orElse(null);
        if (type == null) {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.place.unknown_type", ChatUtil.highlight(typeId)));
            return true;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args.getNext());
            y = Integer.parseInt(args.getNext());
            z = Integer.parseInt(args.getNext());
        } catch (NumberFormatException e) {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.invalid_number"));
            return true;
        }
        World world = args.hasNext() ? Bukkit.getWorld(args.getNext()) : sender instanceof Entity entity ? entity.getWorld() : Bukkit.getWorlds().getFirst();
        if (world == null) {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.place.unknown_world"));
            return true;
        }
        Facing facing = Facing.NORTH;
        if (args.hasNext()) {
            try {
                facing = Facing.valueOf(args.getNext().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ChatUtil.sendErrorMessage(sender, Lang.get("command.place.unknown_facing"));
                return true;
            }
        }
        Block block = world.getBlockAt(x, y, z);
        if (plugin.getStations().at(block) != null || !(block.getType().isAir() || block.isReplaceable())) {
            ChatUtil.sendErrorMessage(sender, Lang.get("command.place.occupied"));
            return true;
        }
        plugin.getStations().place(type, block, facing);
        ChatUtil.sendNormalMessage(sender, Lang.get("command.place.done", ChatUtil.highlight(type.id()), ChatUtil.highlight(x + " " + y + " " + z)));
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.PLACE;
    }

    @Override
    public String getUsage() {
        return "<typ> <x> <y> <z> [welt] [north|east|south|west]";
    }

    @Override
    public Component getDescription() {
        return Lang.get("command.place.description");
    }

    @Override
    public Collection<String> onTabComplete(CommandSender sender, Command command, String alias, ArgsParser args) {
        if (args.remaining() == 1) {
            List<String> ids = new ArrayList<>();
            for (StationType type : plugin.getStations().types()) {
                ids.add(type.id());
            }
            return CommandHelper.complete(args.getNext(""), ids);
        }
        return List.of();
    }
}
