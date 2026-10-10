package de.emrion.kaleidoscopeCookery;

import de.emrion.kaleidoscopeCookery.command.CommandRegistration;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.effect.EffectManager;
import de.emrion.kaleidoscopeCookery.food.FoodService;
import de.emrion.kaleidoscopeCookery.food.VanillaFoodAdjuster;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.ItemProtectionListener;
import de.emrion.kaleidoscopeCookery.item.ModelProperties;
import de.emrion.kaleidoscopeCookery.item.QuickKnifeListener;
import de.emrion.kaleidoscopeCookery.loot.OilDropListener;
import de.emrion.kaleidoscopeCookery.pack.ResourcePackService;
import de.emrion.kaleidoscopeCookery.recipe.Recipes;
import de.emrion.kaleidoscopeCookery.recipe.VanillaRecipes;
import de.emrion.kaleidoscopeCookery.station.StationListener;
import de.emrion.kaleidoscopeCookery.station.StationManager;
import de.emrion.kaleidoscopeCookery.station.choppingboard.ChoppingBoardStation;
import de.emrion.kaleidoscopeCookery.station.crop.CropKind;
import de.emrion.kaleidoscopeCookery.station.crop.CropListener;
import de.emrion.kaleidoscopeCookery.station.bambootray.BambooTrayStation;
import de.emrion.kaleidoscopeCookery.station.millstone.MillstoneListener;
import de.emrion.kaleidoscopeCookery.item.SickleListener;
import de.emrion.kaleidoscopeCookery.station.basket.FruitBasketStation;
import de.emrion.kaleidoscopeCookery.station.racks.KitchenwareRacksStation;
import de.emrion.kaleidoscopeCookery.station.seat.Seats;
import de.emrion.kaleidoscopeCookery.station.trash.TrashCanListener;
import de.emrion.kaleidoscopeCookery.station.trash.TrashCanStation;
import de.emrion.kaleidoscopeCookery.station.shawarma.ShawarmaSpitStation;
import de.emrion.kaleidoscopeCookery.station.millstone.MillstoneStation;
import de.emrion.kaleidoscopeCookery.station.crop.CropStation;
import de.emrion.kaleidoscopeCookery.station.oil.EnamelBasinStation;
import de.emrion.kaleidoscopeCookery.station.oil.OilBlockStation;
import de.emrion.kaleidoscopeCookery.station.oil.OilPotStation;
import de.emrion.kaleidoscopeCookery.station.plate.PlateStation;
import de.emrion.kaleidoscopeCookery.station.pot.PotStation;
import de.emrion.kaleidoscopeCookery.station.steamer.SteamerStation;
import de.emrion.kaleidoscopeCookery.station.stockpot.StockpotStation;
import de.emrion.kaleidoscopeCookery.station.stove.StoveListener;
import de.emrion.kaleidoscopeCookery.station.teapot.CupStation;
import de.emrion.kaleidoscopeCookery.station.teapot.TeapotItems;
import de.emrion.kaleidoscopeCookery.station.teapot.TeapotStation;
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
    /** Registered addons (registerAddon) whose recipes are added when loading */
    private final java.util.List<org.bukkit.plugin.Plugin> addons = new java.util.ArrayList<>();
    private VanillaRecipes vanillaRecipes;
    private StationManager stations;
    private Seats seats;
    private de.emrion.kaleidoscopeCookery.cookbook.Cookbook cookbook;
    private FoodService foodService;
    private EffectManager effects;
    private VanillaFoodAdjuster vanillaFood;

    @Override
    public void onEnable() {
        instance = this;
        PluginManager pluginManager = getServer().getPluginManager();
        saveDefaultConfig();
        updateRecipeFile(this);

        resourcePack = new ResourcePackService(this);
        try (ZipFile pack = new ZipFile(resourcePack.extract(this, "").toFile())) {
            Lang.load(pack, getLogger());
            de.emrion.kaleidoscopeCookery.util.ItemIcons.load(this);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Resourcepack/Sprachdateien konnten nicht geladen werden", e);
        }

        cookeryConfig = CookeryConfig.load(getConfig(), getLogger());
        items = CookeryItems.loadBundled(this);
        ModelProperties.load(this);
        recipes = loadRecipes();
        vanillaRecipes = new VanillaRecipes(this);
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
        stations.register(SteamerStation.type());
        stations.register(TeapotStation.type());
        stations.register(CupStation.type());
        stations.register(de.emrion.kaleidoscopeCookery.station.teapot.MilkTeaStation.type());
        stations.register(de.emrion.kaleidoscopeCookery.station.scarecrow.ScarecrowStation.type());
        stations.register(OilPotStation.type());
        stations.register(EnamelBasinStation.type());
        stations.register(OilBlockStation.type());
        stations.register(MillstoneStation.type());
        stations.register(BambooTrayStation.type());
        stations.register(ShawarmaSpitStation.type());
        stations.register(KitchenwareRacksStation.type());
        stations.register(FruitBasketStation.type());
        stations.register(TrashCanStation.type());
        stations.register(de.emrion.kaleidoscopeCookery.station.decor.StrawBlockStation.type());
        stations.register(de.emrion.kaleidoscopeCookery.station.decor.RecipeBlockStation.type());
        for (de.emrion.kaleidoscopeCookery.station.decor.HangingStation.Kind kind : de.emrion.kaleidoscopeCookery.station.decor.HangingStation.Kind.values()) {
            stations.register(de.emrion.kaleidoscopeCookery.station.decor.HangingStation.type(kind));
        }
        for (de.emrion.kaleidoscopeCookery.station.plate.ServingPlateStation.Kind kind : de.emrion.kaleidoscopeCookery.station.plate.ServingPlateStation.KINDS) {
            stations.register(de.emrion.kaleidoscopeCookery.station.plate.ServingPlateStation.type(kind));
        }
        for (CropKind kind : CropKind.values()) {
            stations.register(CropStation.type(kind));
        }

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
        pluginManager.registerEvents(new TeapotItems(this), this);
        pluginManager.registerEvents(new CropListener(this, stations), this);
        pluginManager.registerEvents(new MillstoneListener(stations), this);
        pluginManager.registerEvents(new SickleListener(stations), this);
        pluginManager.registerEvents(new de.emrion.kaleidoscopeCookery.item.DoughListener(this), this);
        pluginManager.registerEvents(new de.emrion.kaleidoscopeCookery.item.LunchBag(this), this);
        pluginManager.registerEvents(new de.emrion.kaleidoscopeCookery.item.RecipeItems(this), this);
        cookbook = new de.emrion.kaleidoscopeCookery.cookbook.Cookbook(this);
        // Build the entries only after all recipes are loaded (including the campfire recipes of other plugins)
        getServer().getScheduler().runTask(this, () -> getLogger().info("Kochbuch: " + cookbook.size() + " Rezepte"));
        pluginManager.registerEvents(new de.emrion.kaleidoscopeCookery.cookbook.CookbookListener(this), this);
        pluginManager.registerEvents(new de.emrion.kaleidoscopeCookery.station.decor.StrawBlockListener(stations), this);
        seats = new Seats(this);
        pluginManager.registerEvents(seats, this);
        pluginManager.registerEvents(new TrashCanListener(stations, seats), this);

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
        if (seats != null) {
            seats.removeAll();
        }
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
     * Create or replace a plugin's recipes.yml (core or addon) if the bundled version is newer
     * (the old file is kept as a backup).
     */
    private static void updateRecipeFile(org.bukkit.plugin.Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            plugin.saveResource("recipes.yml", false);
            return;
        }
        int bundled;
        try (java.io.Reader reader = new java.io.InputStreamReader(plugin.getResource("recipes.yml"), java.nio.charset.StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader).getInt("data-version");
        } catch (IOException | NullPointerException e) {
            return;
        }
        int current = YamlConfiguration.loadConfiguration(file).getInt("data-version");
        if (current < bundled) {
            File backup = new File(plugin.getDataFolder(), "recipes.yml.v" + current + ".bak");
            if (file.renameTo(backup)) {
                plugin.saveResource("recipes.yml", true);
                plugin.getLogger().warning("recipes.yml war veraltet (Version " + current + ", neu " + bundled + ") und wurde ersetzt. Alte Datei: " + backup.getName());
            }
        }
    }

    /**
     * Recipes of the core and all addons: their sections (tags, stations) are merged into those of the core,
     * the addon overrides identical ids.
     */
    private Recipes loadRecipes() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "recipes.yml"));
        for (org.bukkit.plugin.Plugin addon : addons) {
            YamlConfiguration extra = YamlConfiguration.loadConfiguration(new File(addon.getDataFolder(), "recipes.yml"));
            for (String section : extra.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection source = extra.getConfigurationSection(section);
                if (source == null) {
                    continue;
                }
                org.bukkit.configuration.ConfigurationSection target = config.getConfigurationSection(section);
                if (target == null) {
                    target = config.createSection(section);
                }
                for (java.util.Map.Entry<String, Object> entry : source.getValues(false).entrySet()) {
                    target.set(entry.getKey(), entry.getValue());
                }
            }
        }
        // disabled-recipes (config.yml): the recipes stay in the files but are not loaded
        int disabled = 0;
        for (String section : config.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection values = config.getConfigurationSection(section);
            if (values == null || section.equals("tags") || cookeryConfig == null) {
                continue;
            }
            for (String id : java.util.List.copyOf(values.getKeys(false))) {
                if (cookeryConfig.isRecipeDisabled(id)) {
                    values.set(id, null);
                    disabled++;
                }
            }
        }
        if (disabled > 0) {
            getLogger().info(disabled + " Rezepte abgeschaltet (disabled-recipes)");
        }
        return Recipes.load(config, getLogger());
    }

    /**
     * Register an addon (separate plugin such as KaleidoscopeGrilling): its items.yml and recipes.yml are added, recipes
     * are reloaded. Its own pack (&lt;Name&gt;-Pack.zip in the jar, URL from resource-pack.external-url of the
     * addon's config.yml) is sent to the players as well, its language files and cookbook images are adopted.
     */
    public void registerAddon(org.bukkit.plugin.Plugin addon) {
        if (addons.contains(addon)) {
            return;
        }
        addons.add(addon);
        if (addon.getResource(addon.getName() + "-Pack.zip") != null) {
            try (ZipFile pack = new ZipFile(resourcePack.extract(addon, addon.getConfig().getString("resource-pack.external-url", "")).toFile())) {
                Lang.add(pack, addon.getLogger());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Resourcepack von " + addon.getName() + " konnte nicht geladen werden", e);
            }
            de.emrion.kaleidoscopeCookery.util.ItemIcons.add(addon);
        }
        if (addon.getResource("generated/model_properties.yml") != null) {
            ModelProperties.add(addon);
        }
        if (addon.getResource("items.yml") != null) {
            items = items.merge(CookeryItems.loadBundled(addon));
        }
        if (addon.getResource("recipes.yml") != null) {
            updateRecipeFile(addon);
        }
        recipes = loadRecipes();
        vanillaRecipes.register(recipes);
        getLogger().info("Addon " + addon.getName() + " angemeldet: " + items.all().size() + " Items");
    }

    /**
     * /kc reload: re-read config.yml and recipes.yml.
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

    /**
     * Register an addon's crop kind (its seeds then plant it like the Cookery crops).
     */
    public void registerCrop(CropKind kind) {
        CropKind.register(kind);
        stations.register(CropStation.type(kind));
    }

    public VanillaRecipes getVanillaRecipes() {
        return vanillaRecipes;
    }

    /** Registered addons (separate plugins such as KaleidoscopeGrilling) */
    public java.util.List<org.bukkit.plugin.Plugin> getAddons() {
        return java.util.Collections.unmodifiableList(addons);
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

    public de.emrion.kaleidoscopeCookery.cookbook.Cookbook getCookbook() {
        return cookbook;
    }

    public FoodService getFoodService() {
        return foodService;
    }

    public EffectManager getEffects() {
        return effects;
    }
}
