package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.iani.cubesideutils.bukkit.commands.CommandRouter;
import de.iani.cubesideutils.bukkit.commands.CommandUtil;

import java.util.List;

/**
 * Registers /kaleidoscope (alias /kc). Paper plugins have no commands in the plugin.yml,
 * so the router is added to the CommandMap via CommandUtil (CubesideUtils).
 */
public record CommandRegistration(KaleidoscopeCookery plugin) {

    public static final String COMMAND = "kaleidoscope";
    public static final List<String> ALIASES = List.of("kc", "kochen");

    public void registerCommands() {
        CommandRouter router = new CommandRouter(new GermanExceptionHandler());
        router.addCommandMapping(new GiveCommand(plugin), "give");
        router.addCommandMapping(new InfoCommand(plugin), "info");
        router.addCommandMapping(new ListCommand(plugin), "list");
        router.addCommandMapping(new PlaceCommand(plugin), "place");
        router.addCommandMapping(new RepairCommand(plugin), "repair");
        router.addCommandMapping(new ReloadCommand(plugin), "reload");
        router.addCommandMapping(new PackCommand(plugin), "pack");
        router.addCommandMapping(new MenuCommand(plugin), "menu");
        router.addCommandMapping(new CookbookCommand(plugin, false), "rezepte");
        router.addCommandMapping(new CookbookCommand(plugin, false), "recipes");
        router.addCommandMapping(new CookbookCommand(plugin, true), "rezept");
        router.addCommandMapping(new CookbookCommand(plugin, true), "recipe");

        if (CommandUtil.registerCommand(plugin, COMMAND, ALIASES, router) == null) {
            plugin.getLogger().warning("/" + COMMAND + " war schon belegt und ist nur als /" + plugin.getPluginMeta().getName().toLowerCase() + ":" + COMMAND + " erreichbar");
        }
    }
}
