package de.emrion.kaleidoscopeGrilling;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Addon nach Kaleidoscope Grilling: baut auf KaleidoscopeCookery auf (Stationen, Items, Rezepte, Resourcepack).
 * Das Hitze-System (烟火气) des Mods ist bewusst nicht enthalten.
 */
public class KaleidoscopeGrilling extends JavaPlugin {

    private KaleidoscopeCookery cookery;

    @Override
    public void onEnable() {
        cookery = KaleidoscopeCookery.getInstance();
        // Grilling-Items und -Rezepte (inkl. Änderungen an Cookery-Rezepten) beim Core anmelden
        cookery.registerAddon(this);
        getLogger().info("Grilling-Addon für KaleidoscopeCookery " + cookery.getPluginMeta().getVersion() + " geladen");
    }

    public KaleidoscopeCookery getCookery() {
        return cookery;
    }
}
