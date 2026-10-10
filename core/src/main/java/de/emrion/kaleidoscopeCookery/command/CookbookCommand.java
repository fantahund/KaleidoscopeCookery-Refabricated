package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.iani.cubesideutils.bukkit.commands.PlayerSubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * /kc rezepte [search] - open or search the cookbook; /kc rezept - recipes for the item in hand.
 */
public class CookbookCommand extends PlayerSubCommand {

    private final KaleidoscopeCookery plugin;
    private final boolean heldItem;

    public CookbookCommand(KaleidoscopeCookery plugin, boolean heldItem) {
        this.plugin = plugin;
        this.heldItem = heldItem;
    }

    @Override
    public boolean onCommand(Player player, Command command, String alias, String commandString, ArgsParser args) {
        if (heldItem) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.isEmpty()) {
                plugin.getCookbook().open(player);
                return true;
            }
            Key id = CookeryItems.idOf(held);
            plugin.getCookbook().openFor(player, id != null ? id : held.getType().key());
            return true;
        }
        if (args.hasNext()) {
            plugin.getCookbook().search(player, args.getAll(""));
        } else {
            plugin.getCookbook().open(player);
        }
        return true;
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.USE;
    }

    @Override
    public String getUsage() {
        return heldItem ? "" : "[Suche]";
    }

    @Override
    public Component getDescription() {
        return Lang.get(heldItem ? "command.recipe.description" : "command.cookbook.description");
    }

    @Override
    public Collection<String> onTabComplete(Player sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
