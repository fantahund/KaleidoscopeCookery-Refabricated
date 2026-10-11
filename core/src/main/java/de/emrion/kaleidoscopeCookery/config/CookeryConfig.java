package de.emrion.kaleidoscopeCookery.config;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loaded and validated config.yml. Immutable - /kc reload creates a new instance.
 */
public record CookeryConfig(ResourcePackSettings resourcePack, Cutting cutting, OilDrops oilDrops, Food food, List<String> disabledRecipes,
                            boolean dispensers) {

    /**
     * Is the recipe disabled (disabled-recipes: exact id or prefix with a trailing "*", e.g. "kaleidoscope_cookery:millstone/*")?
     */
    public boolean isRecipeDisabled(String id) {
        for (String pattern : disabledRecipes) {
            if (pattern.endsWith("*") ? id.startsWith(pattern.substring(0, pattern.length() - 1)) : id.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    public record ResourcePackSettings(boolean enabled, boolean required, String externalUrl, String bindAddress, int port, String publicHost) {
    }

    /**
     * Cutting tools (kitchen knives) with their cuts per hit.
     */
    public record Cutting(Map<Key, Integer> tools, double durabilityChance, int cooldownTicks) {

        /**
         * @return cuts per hit, 0 if the item cannot cut
         */
        public int cuts(@Nullable ItemStack tool) {
            if (tool == null || tool.isEmpty()) {
                return 0;
            }
            return tools.getOrDefault(CookeryItems.typeOf(tool), 0);
        }

        public boolean isTool(@Nullable ItemStack tool) {
            return cuts(tool) > 0;
        }
    }

    public record OilDrops(boolean enabled, int min, int max, Set<EntityType> entities) {
    }

    /**
     * @param vanillaPercents percent per material, otherwise vanillaDefaultPercent
     */
    /**
     * @param dishPercents mod dishes with adjusted food values (food.dishes): item id -> percent of the mod value
     */
    public record Food(boolean vanillaNerfEnabled, int vanillaDefaultPercent, Map<Material, Integer> vanillaPercents, Map<Key, Integer> dishPercents) {

        public int vanillaPercent(Material material) {
            return vanillaPercents.getOrDefault(material, vanillaDefaultPercent);
        }
    }

    public static CookeryConfig load(FileConfiguration config, Logger logger) {
        ResourcePackSettings pack = new ResourcePackSettings(
                config.getBoolean("resource-pack.enabled", true),
                config.getBoolean("resource-pack.required", true),
                config.getString("resource-pack.external-url", ""),
                config.getString("resource-pack.bind-address", "0.0.0.0"),
                config.getInt("resource-pack.port", 8163),
                config.getString("resource-pack.public-host", "127.0.0.1"));

        Map<Key, Integer> tools = new HashMap<>();
        ConfigurationSection toolSection = config.getConfigurationSection("cutting.tools");
        if (toolSection != null) {
            // Item ids contain ":" - read the values directly instead of via paths
            for (Map.Entry<String, Object> entry : toolSection.getValues(false).entrySet()) {
                if (!Key.parseable(entry.getKey()) || !(entry.getValue() instanceof Number number)) {
                    logger.warning("cutting.tools: ungültiger Eintrag " + entry.getKey() + " wird ignoriert");
                    continue;
                }
                tools.put(Key.key(entry.getKey()), Math.max(1, number.intValue()));
            }
        }
        Cutting cutting = new Cutting(Map.copyOf(tools), Math.clamp(config.getDouble("cutting.durability-chance", 0.25), 0, 1),
                Math.max(0, config.getInt("cutting.cooldown-ticks", 4)));

        Set<EntityType> oilEntities = EnumSet.noneOf(EntityType.class);
        for (String name : config.getStringList("oil-drops.entities")) {
            try {
                oilEntities.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                logger.warning("oil-drops: unbekannter Entity-Typ " + name + " wird ignoriert");
            }
        }
        int min = Math.max(0, config.getInt("oil-drops.min", 1));
        OilDrops oilDrops = new OilDrops(config.getBoolean("oil-drops.enabled", true), min, Math.max(min, config.getInt("oil-drops.max", 2)),
                Collections.unmodifiableSet(oilEntities));
        Map<Material, Integer> percents = new EnumMap<>(Material.class);
        ConfigurationSection percentSection = config.getConfigurationSection("food.vanilla.items");
        if (percentSection != null) {
            for (String name : percentSection.getKeys(false)) {
                Material material = Material.matchMaterial(name);
                if (material == null) {
                    logger.warning("food.vanilla.items: unbekanntes Material " + name + " wird ignoriert");
                    continue;
                }
                percents.put(material, Math.max(0, percentSection.getInt(name)));
            }
        }
        // Item ids contain ":" - read the values directly instead of via paths
        Map<Key, Integer> dishes = new java.util.LinkedHashMap<>();
        ConfigurationSection dishSection = config.getConfigurationSection("food.dishes");
        if (dishSection != null) {
            for (Map.Entry<String, Object> entry : dishSection.getValues(false).entrySet()) {
                if (!Key.parseable(entry.getKey()) || !(entry.getValue() instanceof Number number)) {
                    logger.warning("food.dishes: invalid entry " + entry.getKey() + " is ignored");
                    continue;
                }
                dishes.put(Key.key(entry.getKey()), Math.max(0, number.intValue()));
            }
        }
        Food food = new Food(config.getBoolean("food.vanilla.enabled", true), Math.max(0, config.getInt("food.vanilla.default-percent", 50)),
                Collections.unmodifiableMap(percents), Collections.unmodifiableMap(dishes));
        return new CookeryConfig(pack, cutting, oilDrops, food, List.copyOf(config.getStringList("disabled-recipes")),
                config.getBoolean("dispensers", false));
    }
}
