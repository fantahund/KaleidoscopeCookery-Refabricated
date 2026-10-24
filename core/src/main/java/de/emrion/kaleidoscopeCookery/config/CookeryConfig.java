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
public record CookeryConfig(Cutting cutting, OilDrops oilDrops, Food food, List<String> disabledRecipes,
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
            int cuts = tools.getOrDefault(CookeryItems.typeOf(tool), 0);
            // A masterwork knife from KaleidoscopeSmithing cuts once more per hit
            String quality = tool.getPersistentDataContainer().get(SMITHING_QUALITY, org.bukkit.persistence.PersistentDataType.STRING);
            return cuts > 0 && "masterwork".equals(quality) ? cuts + 1 : cuts;
        }

        private static final org.bukkit.NamespacedKey SMITHING_QUALITY = new org.bukkit.NamespacedKey("kaleidoscope_smithing", "quality");

        public boolean isTool(@Nullable ItemStack tool) {
            return cuts(tool) > 0;
        }
    }

    /**
     * Cooking level of a dish and its food totals (all bites of a plate together; -1 = keep the mod value).
     */
    public record DishValues(int level, int nutrition, float saturation) {
    }

    public record OilDrops(boolean enabled, int min, int max, Set<EntityType> entities) {
    }

    /**
     * @param vanillaPercents percent per material, otherwise vanillaDefaultPercent
     */
    /**
     * @param dishPercents mod dishes with adjusted food values (food.dishes): item id -> percent of the mod value
     */
    public record Food(boolean vanillaNerfEnabled, int vanillaDefaultPercent, Map<Material, Integer> vanillaPercents, Map<Key, Integer> dishPercents,
                       Map<Key, DishValues> dishValues, Map<Integer, Integer> effectPercentByLevel) {

        public int vanillaPercent(Material material) {
            return vanillaPercents.getOrDefault(material, vanillaDefaultPercent);
        }
    }

    public static CookeryConfig load(FileConfiguration config, Logger logger) {
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
        // cooking-skill.dishes: "id": {level, nutrition, saturation}
        Map<Key, DishValues> dishValues = new java.util.LinkedHashMap<>();
        ConfigurationSection valueSection = config.getConfigurationSection("cooking-skill.dishes");
        if (valueSection != null) {
            for (Map.Entry<String, Object> entry : valueSection.getValues(false).entrySet()) {
                if (!Key.parseable(entry.getKey()) || !(entry.getValue() instanceof ConfigurationSection values)) {
                    logger.warning("cooking-skill.dishes: invalid entry " + entry.getKey() + " is ignored");
                    continue;
                }
                dishValues.put(Key.key(entry.getKey()), new DishValues(Math.max(1, values.getInt("level", 1)),
                        values.getInt("nutrition", -1), (float) values.getDouble("saturation", -1)));
            }
        }
        Map<Integer, Integer> effectPercent = new java.util.HashMap<>();
        ConfigurationSection effectSection = config.getConfigurationSection("cooking-skill.effect-percent");
        if (effectSection != null) {
            for (String level : effectSection.getKeys(false)) {
                try {
                    effectPercent.put(Integer.parseInt(level), Math.max(0, effectSection.getInt(level)));
                } catch (NumberFormatException e) {
                    logger.warning("cooking-skill.effect-percent: invalid level " + level + " is ignored");
                }
            }
        }
        Food food = new Food(config.getBoolean("food.vanilla.enabled", true), Math.max(0, config.getInt("food.vanilla.default-percent", 50)),
                Collections.unmodifiableMap(percents), Collections.unmodifiableMap(dishes), Collections.unmodifiableMap(dishValues),
                Collections.unmodifiableMap(effectPercent));
        return new CookeryConfig(cutting, oilDrops, food, List.copyOf(config.getStringList("disabled-recipes")),
                config.getBoolean("dispensers", false));
    }
}
