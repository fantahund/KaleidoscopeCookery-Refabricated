package de.emrion.kaleidoscopeCookery;

import de.emrion.kaleidoscopeCookery.command.CommandRegistration;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.ItemProtectionListener;
import de.emrion.kaleidoscopeCookery.loot.OilDropListener;
import de.emrion.kaleidoscopeCookery.pack.ResourcePackService;
import de.emrion.kaleidoscopeCookery.recipe.Recipes;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import de.emrion.kaleidoscopeCookery.station.StationListener;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import de.emrion.kaleidoscopeCookery.station.choppingboard.ChoppingBoardStation;
import de.emrion.kaleidoscopeCookery.util.Lang;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.zip.ZipFile;

public final class KaleidoscopeCookery extends JavaPlugin {

    private static KaleidoscopeCookery instance;

    private CookeryConfig cookeryConfig;
    private ResourcePackService resourcePack;
    private CookeryItems items;
    private Recipes recipes;
    private VanillaRecipes vanillaRecipes;
    private StationManager stations;

    @Override
    public void onEnable() {
        instance = this;
        PluginManager pluginManager = getServer().getPluginManager();
        saveDefaultConfig();
        if (!new File(getDataFolder(), "recipes.yml").exists()) {
            saveResource("recipes.yml", false);
        }

        resourcePack = new ResourcePackService(this);
        try (ZipFile pack = new ZipFile(resourcePack.extract().toFile())) {
            Lang.load(pack, getLogger());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Resourcepack/Sprachdateien konnten nicht geladen werden", e);
        }

        cookeryConfig = CookeryConfig.load(getConfig(), getLogger());
        items = CookeryItems.loadBundled(this);
        recipes = loadRecipes();
        vanillaRecipes = new VanillaRecipes(this, items);
        vanillaRecipes.register(recipes);

        stations = new StationManager(this);
        stations.register(ChoppingBoardStation.type());

        pluginManager.registerEvents(resourcePack, this);
        pluginManager.registerEvents(stations, this);
        pluginManager.registerEvents(new StationListener(this, stations), this);
        pluginManager.registerEvents(new ItemProtectionListener(), this);
        pluginManager.registerEvents(vanillaRecipes, this);
        pluginManager.registerEvents(new OilDropListener(this), this);

        stations.start();
        resourcePack.start(cookeryConfig.resourcePack());
        new CommandRegistration(this).registerCommands();
        getLogger().info(items.all().size() + " Items, " + recipes.choppingBoard().size() + " Schneidebrett-Rezepte geladen");
    }

    @Override
    public void onDisable() {
        if (stations != null) {
            stations.stop();
        }
        if (vanillaRecipes != null) {
            vanillaRecipes.unregister();
        }
        if (resourcePack != null) {
            resourcePack.stop();
        }
        Lang.unload();
        instance = null;
    }

    private Recipes loadRecipes() {
        return Recipes.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "recipes.yml")), getLogger());
    }

    /**
     * /kc reload: config.yml und recipes.yml neu einlesen.
     */
    public void reloadAll() {
        reloadConfig();
        cookeryConfig = CookeryConfig.load(getConfig(), getLogger());
        resourcePack.updateSettings(cookeryConfig.resourcePack());
        recipes = loadRecipes();
        vanillaRecipes.register(recipes);
    }

    public static KaleidoscopeCookery getInstance() {
        return instance;
    }

    public CookeryConfig getCookeryConfig() {
        return cookeryConfig;
    }

    public ResourcePackService getResourcePack() {
        return resourcePack;
    }

    public CookeryItems getItems() {
        return items;
    }

    public Recipes getRecipes() {
        return recipes;
    }

    public StationManager getStations() {
        return stations;
    }
}
