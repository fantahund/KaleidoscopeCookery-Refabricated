package de.emrion.kaleidoscopeCookery.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item-Properties aus den Mod-Modellen (z. B. kaleidoscope_cookery:has_oil). Das Resourcepack wählt das Modell über
 * custom_model_data.floats an einem festen Index (generated/model_properties.yml, erzeugt von tools/import_assets.py).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ModelProperties {

    private static final Map<String, Integer> INDICES = new HashMap<>();

    private ModelProperties() {
    }

    public static void load(Plugin plugin) {
        INDICES.clear();
        add(plugin);
    }

    /**
     * Properties eines Addons dazunehmen (eigene Namen, Indizes passend zu dessen Pack).
     */
    public static void add(Plugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("generated/model_properties.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (Map.Entry<String, Object> entry : config.getValues(false).entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    INDICES.put(entry.getKey(), number.intValue());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("model_properties.yml konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    /**
     * Property setzen (0 = Standardmodell).
     */
    public static void set(ItemStack stack, String property, float value) {
        Integer index = INDICES.get(property);
        if (index == null) {
            return;
        }
        CustomModelData current = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        List<Float> floats = new ArrayList<>(current == null ? List.of() : current.floats());
        while (floats.size() <= index) {
            floats.add(0f);
        }
        floats.set(index, value);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloats(floats).build());
    }
}
