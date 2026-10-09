package de.emrion.kaleidoscopeGrilling;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Addon nach Kaleidoscope Grilling: baut auf KaleidoscopeCookery auf (Stationen, Items, Rezepte, Resourcepack).
 */
public class KaleidoscopeGrilling extends JavaPlugin {

    private KaleidoscopeCookery cookery;

    @Override
    public void onEnable() {
        cookery = KaleidoscopeCookery.getInstance();
        getLogger().info("Grilling-Addon für KaleidoscopeCookery " + cookery.getPluginMeta().getVersion() + " geladen");
    }

    public KaleidoscopeCookery getCookery() {
        return cookery;
    }
}
