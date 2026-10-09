package de.emrion.kaleidoscopeCookery;

import de.emrion.kaleidoscopeCookery.command.CommandRegistration;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.effect.EffectManager;
import de.emrion.kaleidoscopeCookery.food.FoodService;
import de.emrion.kaleidoscopeCookery.food.VanillaFoodAdjuster;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.ItemProtectionListener;
import de.emrion.kaleidoscopeCookery.item.QuickKnifeListener;
import de.emrion.kaleidoscopeCookery.loot.OilDropListener;
import de.emrion.kaleidoscopeCookery.pack.ResourcePackService;
import de.emrion.kaleidoscopeCookery.recipe.Recipes;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import de.emrion.kaleidoscopeCookery.station.StationListener;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import de.emrion.kaleidoscopeCookery.station.choppingboard.ChoppingBoardStation;
import de.emrion.kaleidoscopeCookery.station.plate.PlateStation;
import de.emrion.kaleidoscopeCookery.station.pot.PotStation;
import de.emrion.kaleidoscopeCookery.station.stockpot.StockpotStation;
import de.emrion.kaleidoscopeCookery.station.stove.StoveListener;
import de.emrion.kaleidoscopeCookery.station.stove.StoveStation;
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
    private FoodService foodService;
    private EffectManager effects;
    private VanillaFoodAdjuster vanillaFood;

    @Override
    public void onEnable() {
        instance = this;
        PluginManager pluginManager = getServer().getPluginManager();
        saveDefaultConfig();
        updateRecipeFile();

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

        foodService = new FoodService(this);
        effects = new EffectManager(this);
        vanillaFood = new VanillaFoodAdjuster(this);

        stations = new StationManager(this);
        stations.register(ChoppingBoardStation.type());
        stations.register(StoveStation.type());
        stations.register(PotStation.type());
        stations.register(PlateStation.type());
        stations.register(StockpotStation.type());

        pluginManager.registerEvents(resourcePack, this);
        pluginManager.registerEvents(stations, this);
        pluginManager.registerEvents(new StationListener(this, stations), this);
        pluginManager.registerEvents(new ItemProtectionListener(), this);
        pluginManager.registerEvents(new QuickKnifeListener(), this);
        pluginManager.registerEvents(vanillaRecipes, this);
        pluginManager.registerEvents(new OilDropListener(this), this);
        pluginManager.registerEvents(foodService, this);
        pluginManager.registerEvents(effects, this);
        pluginManager.registerEvents(vanillaFood, this);
        pluginManager.registerEvents(new StoveListener(stations), this);

        stations.start();
        foodService.start();
        effects.start();
        vanillaFood.start();
        resourcePack.start(cookeryConfig.resourcePack());
        new CommandRegistration(this).registerCommands();
        getLogger().info(items.all().size() + " Items, " + recipes.choppingBoard().size() + " Schneidebrett-Rezepte geladen");
    }

    @Override
    public void onDisable() {
        if (stations != null) {
            stations.stop();
        }
        if (foodService != null) {
            foodService.stop();
        }
        if (effects != null) {
            effects.stop();
        }
        if (vanillaFood != null) {
            vanillaFood.stop();
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

    /**
     * recipes.yml anlegen bzw. ersetzen, wenn die mitgelieferte Version neuer ist (alte Datei bleibt als Backup).
     */
    private void updateRecipeFile() {
        File file = new File(getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            saveResource("recipes.yml", false);
            return;
        }
        int bundled;
        try (java.io.Reader reader = new java.io.InputStreamReader(getResource("recipes.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader).getInt("data-version");
        } catch (IOException | NullPointerException e) {
            return;
        }
        int current = YamlConfiguration.loadConfiguration(file).getInt("data-version");
        if (current < bundled) {
            File backup = new File(getDataFolder(), "recipes.yml.v" + current + ".bak");
            if (file.renameTo(backup)) {
                saveResource("recipes.yml", true);
                getLogger().warning("recipes.yml war veraltet (Version " + current + ", neu " + bundled + ") und wurde ersetzt. Alte Datei: " + backup.getName());
            }
        }
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

    public FoodService getFoodService() {
        return foodService;
    }

    public EffectManager getEffects() {
        return effects;
    }
}
