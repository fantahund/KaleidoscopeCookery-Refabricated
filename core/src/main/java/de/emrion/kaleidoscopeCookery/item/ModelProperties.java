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
 * Item properties from the mod models (e.g. kaleidoscope_cookery:has_oil). The resource pack picks the model via
 * custom_model_data.floats at a fixed index (generated/model_properties.yml, created by tools/import_assets.py).
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
     * Add an addon's properties (own names, indices matching its pack).
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
     * Set a property (0 = default model).
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
        CustomModelData.Builder builder = CustomModelData.customModelData().addFloats(floats);
        if (current != null) {
            builder.addFlags(current.flags()).addStrings(current.strings()).addColors(current.colors());
        }
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
    }

    /**
     * Set tint colours (custom_model_data.colors, index = tintindex in the model), floats stay.
     */
    public static void setColors(ItemStack stack, List<org.bukkit.Color> colors) {
        CustomModelData current = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        CustomModelData.Builder builder = CustomModelData.customModelData().addColors(colors);
        if (current != null) {
            builder.addFloats(current.floats()).addFlags(current.flags()).addStrings(current.strings());
        }
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
    }
}
