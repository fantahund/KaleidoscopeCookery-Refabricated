package de.emrion.kaleidoscopeGrilling;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Addon nach Kaleidoscope Grilling: baut auf KaleidoscopeCookery auf (Stationen, Items, Rezepte, Resourcepack).
 */
public class KaleidoscopeGrilling extends JavaPlugin {

    private KaleidoscopeCookery cookery;
    private de.emrion.kaleidoscopeGrilling.food.HotFood hotFood;

    @Override
    public void onEnable() {
        cookery = KaleidoscopeCookery.getInstance();
        // Grilling-Items und -Rezepte (inkl. Änderungen an Cookery-Rezepten) beim Core anmelden
        cookery.registerAddon(this);
        saveDefaultConfig();
        hotFood = new de.emrion.kaleidoscopeGrilling.food.HotFood(this, cookery);
        hotFood.load(getConfig().getConfigurationSection("hot") != null ? getConfig().getConfigurationSection("hot") : getConfig().createSection("hot"));
        hotFood.start();
        getServer().getPluginManager().registerEvents(hotFood, this);
        getLogger().info("Grilling-Addon für KaleidoscopeCookery " + cookery.getPluginMeta().getVersion() + " geladen");
    }

    @Override
    public void onDisable() {
        if (hotFood != null) {
            hotFood.stop();
        }
    }

    public de.emrion.kaleidoscopeGrilling.food.HotFood getHotFood() {
        return hotFood;
    }

    public KaleidoscopeCookery getCookery() {
        return cookery;
    }
}
