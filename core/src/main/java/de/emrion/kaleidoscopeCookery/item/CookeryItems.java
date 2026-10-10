package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseRemainder;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Alle Items der Mods. Sie basieren auf {@link #BASE_MATERIAL}, Aussehen über die item_model-Komponente,
 * Name über einen Übersetzungs-Key aus dem Resourcepack, Erkennung über den PDC-Tag {@link Keys#ITEM}.
 */
public class CookeryItems {

    /** Basis-Material: nicht platzierbar, ohne eigene Rechtsklick-Funktion */
    public static final Material BASE_MATERIAL = Material.PAPER;

    private final Map<Key, CookeryItem> items;

    private CookeryItems(Map<Key, CookeryItem> items) {
        this.items = Collections.unmodifiableMap(items);
    }

    public static CookeryItems load(Reader reader, Logger logger) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
        ConfigurationSection section = config.getConfigurationSection("items");
        Map<Key, CookeryItem> items = new LinkedHashMap<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                Key key = Key.key(id);
                CookeryItem.Food food = null;
                ConfigurationSection foodSection = entry.getConfigurationSection("food");
                if (foodSection != null) {
                    food = loadFood(id, foodSection, logger);
                }
                Material base = Material.matchMaterial(entry.getString("base", BASE_MATERIAL.name()));
                if (base == null || !base.isItem()) {
                    logger.warning(id + ": unbekanntes Basis-Material " + entry.getString("base") + ", nehme " + BASE_MATERIAL);
                    base = BASE_MATERIAL;
                }
                CookeryItem.Plate plate = null;
                ConfigurationSection plateSection = entry.getConfigurationSection("plate");
                if (plateSection != null) {
                    plate = loadPlate(id, plateSection, logger);
                }
                Material prototype = entry.getString("prototype") == null ? null : Material.matchMaterial(entry.getString("prototype"));
                items.put(key, new CookeryItem(key, base, prototype, Key.key(entry.getString("model", id)), entry.getString("name", id), entry.getString("tooltip"),
                        Math.clamp(entry.getInt("max-stack", 64), 1, 99), Math.max(0, entry.getInt("max-damage", 0)), food, plate,
                        entry.getBoolean("sneak-place"), loadWearable(id, entry, logger),
                        entry.getString("repair") == null ? null : Material.matchMaterial(entry.getString("repair")), entry.getBoolean("attributes", true),
                        entry.getString("use-animation"), Math.max(0, entry.getInt("maxim-lines", 0))));
            }
        }
        return new CookeryItems(items);
    }

    /**
     * Diese Items plus die eines Addons (gleiche IDs werden vom Addon überschrieben).
     */
    public CookeryItems merge(CookeryItems other) {
        Map<Key, CookeryItem> merged = new LinkedHashMap<>(items);
        merged.putAll(other.items);
        return new CookeryItems(merged);
    }

    public static CookeryItems loadBundled(org.bukkit.plugin.Plugin plugin) {
        try (Reader reader = new InputStreamReader(plugin.getResource("items.yml"), StandardCharsets.UTF_8)) {
            return load(reader, plugin.getLogger());
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("items.yml fehlt im Plugin-Jar", e);
        }
    }

    private static CookeryItem.Food loadFood(String id, ConfigurationSection section, Logger logger) {
        List<CookeryItem.FoodEffect> effects = new ArrayList<>();
        for (Map<?, ?> effect : section.getMapList("effects")) {
            Object name = effect.get("effect");
            if (name == null) {
                logger.warning(id + ": Effekt ohne Namen wird ignoriert");
                continue;
            }
            effects.add(new CookeryItem.FoodEffect(Key.key(name.toString()), number(effect.get("duration"), 0).intValue(),
                    number(effect.get("amplifier"), 0).intValue(), number(effect.get("probability"), 1).floatValue()));
        }
        String remainder = section.getString("remainder");
        return new CookeryItem.Food(section.getInt("nutrition"), (float) section.getDouble("saturation"), section.getBoolean("always-edible"),
                (float) section.getDouble("eat-seconds", 1.6), remainder == null ? null : Key.key(remainder), List.copyOf(effects),
                section.getBoolean("drink"), section.isString("eat-sound") ? Key.key(section.getString("eat-sound")) : null);
    }

    private static CookeryItem.Plate loadPlate(String id, ConfigurationSection section, Logger logger) {
        ConfigurationSection foodSection = section.getConfigurationSection("food");
        CookeryItem.Food food = foodSection != null ? loadFood(id, foodSection, logger)
                : new CookeryItem.Food(0, 0, true, 1.6f, null, List.of(), false, null);
        List<Key> loot = new ArrayList<>();
        for (String value : section.getStringList("loot")) {
            if (Key.parseable(value)) {
                loot.add(Key.key(value));
            }
        }
        Map<String, List<Key>> models = new LinkedHashMap<>();
        ConfigurationSection modelSection = section.getConfigurationSection("models");
        if (modelSection != null) {
            for (String part : modelSection.getKeys(false)) {
                List<Key> stages = new ArrayList<>();
                for (String value : modelSection.getStringList(part)) {
                    stages.add(Key.key(value));
                }
                models.put(part, List.copyOf(stages));
            }
        }
        return new CookeryItem.Plate(Math.max(1, section.getInt("max-bites", 3)), food, List.copyOf(loot), "1x2".equals(section.getString("size")),
                "3x3".equals(section.getString("size")),
                Collections.unmodifiableMap(models));
    }

    private static CookeryItem.@Nullable Wearable loadWearable(String id, ConfigurationSection entry, Logger logger) {
        ConfigurationSection section = entry.getConfigurationSection("equippable");
        if (section == null) {
            return null;
        }
        org.bukkit.inventory.EquipmentSlot slot = switch (section.getString("slot", "head")) {
            case "chest" -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case "legs" -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case "feet" -> org.bukkit.inventory.EquipmentSlot.FEET;
            default -> org.bukkit.inventory.EquipmentSlot.HEAD;
        };
        String asset = section.getString("asset");
        return new CookeryItem.Wearable(slot, asset == null ? null : Key.key(asset), entry.getInt("armor", 0), entry.getBoolean("unbreakable"));
    }

    private static Number number(Object value, Number fallback) {
        return value instanceof Number number ? number : fallback;
    }

    public @Nullable CookeryItem get(Key id) {
        return items.get(id);
    }

    public @Nullable CookeryItem get(String id) {
        return Key.parseable(id) ? items.get(Key.key(id)) : null;
    }

    public Map<Key, CookeryItem> all() {
        return items;
    }

    /**
     * ID eines Custom-Items, null bei Vanilla-Items.
     */
    public static @Nullable Key idOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = stack.getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
        return id != null && Key.parseable(id) ? Key.key(id) : null;
    }

    /**
     * ID eines beliebigen Items: Custom-Items mit ihrer ID, Vanilla-Items als minecraft:&lt;name&gt;.
     */
    public static Key typeOf(ItemStack stack) {
        Key id = idOf(stack);
        return id != null ? id : stack.getType().key();
    }

    public static boolean isCustom(@Nullable ItemStack stack) {
        return idOf(stack) != null;
    }

    /**
     * Item anhand der ID erzeugen: Mod-Items und Vanilla-Items (minecraft:...).
     */
    public @Nullable ItemStack create(Key id, int amount) {
        CookeryItem item = items.get(id);
        if (item != null) {
            return create(item, amount);
        }
        if (id.namespace().equals(NamespacedKey.MINECRAFT)) {
            Material material = Material.matchMaterial(id.asString());
            if (material != null && material.isItem()) {
                return ItemStack.of(material, amount);
            }
        }
        return null;
    }

    /** Brennstoffe wie im Mod (cookingFuel): Öl wie Kohle, Ölblock wie Kohleblock */
    private static final Map<Key, Material> COOKING_FUEL = Map.of(
            Key.key("kaleidoscope_cookery", "oil"), Material.COAL,
            Key.key("kaleidoscope_cookery", "oil_block"), Material.COAL_BLOCK);
    private static final Key COOKING_FUEL_COMPONENT = Key.key("minecraft", "cooking_fuel");

    @SuppressWarnings("UnstableApiUsage")
    public ItemStack create(CookeryItem item, int amount) {
        ItemStack stack = ItemStack.of(item.base(), 1);
        Material fuel = COOKING_FUEL.get(item.id());
        if (fuel != null) {
            // cooking_fuel hat noch keinen Typ in DataComponentTypes: vom Vanilla-Brennstoff übernehmen
            stack.copyDataFrom(ItemStack.of(fuel), type -> type.key().equals(COOKING_FUEL_COMPONENT));
        }
        if (item.prototype() != null) {
            // Alle Eigenschaften des Vanilla-Items übernehmen (Schaden, Tempo, Haltbarkeit, Reparatur, Abbau ...)
            stack.copyDataFrom(ItemStack.of(item.prototype()), type -> true);
        }
        stack.setData(DataComponentTypes.ITEM_MODEL, item.model());
        if (item.maxDamage() > 0) {
            stack.setData(DataComponentTypes.MAX_DAMAGE, item.maxDamage());
        }
        if (!item.attributes()) {
            stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, io.papermc.paper.datacomponent.item.ItemAttributeModifiers.itemAttributes().build());
        }
        if (item.useAnimation() != null && item.food() == null) {
            // Lange "Benutzung" ohne Wirkung: der Client zeigt die Animation, das Plugin wertet das Loslassen aus
            stack.setData(DataComponentTypes.CONSUMABLE, io.papermc.paper.datacomponent.item.Consumable.consumable()
                    .consumeSeconds(3600)
                    .animation(io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation.valueOf(item.useAnimation().toUpperCase(java.util.Locale.ROOT)))
                    .sound(Key.key("minecraft", "intentionally_empty"))
                    .hasConsumeParticles(false)
                    .build());
        }
        if (item.repair() != null) {
            stack.setData(DataComponentTypes.REPAIRABLE, io.papermc.paper.datacomponent.item.Repairable.repairable(
                    io.papermc.paper.registry.set.RegistrySet.keySet(io.papermc.paper.registry.RegistryKey.ITEM,
                            io.papermc.paper.registry.TypedKey.create(io.papermc.paper.registry.RegistryKey.ITEM, item.repair().key()))));
        }
        stack.setData(DataComponentTypes.ITEM_NAME, Text.noItalic(Lang.raw(item.nameKey())));
        List<Component> lore = tooltipLines(item);
        if (!lore.isEmpty()) {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        if (item.base() == BASE_MATERIAL) {
            // Werkzeuge behalten Stapelgröße 1 und ihre Haltbarkeit
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, item.maxStack());
        }
        if (item.food() != null) {
            applyFood(stack, item.food(), 1.0);
        }
        if (item.wearable() != null) {
            applyWearable(stack, item.wearable());
        }
        stack.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM, PersistentDataType.STRING, item.id().asString()));
        stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
        return stack;
    }

    /**
     * Nahrungswerte setzen, skaliert mit ratio (Qualität). Mod-Effekte setzt das Plugin beim Essen selbst um.
     */
    @SuppressWarnings("UnstableApiUsage")
    public void applyFood(ItemStack stack, CookeryItem.Food food, double ratio) {
        if (food.drink() && food.nutrition() == 0) {
            // Getränk ohne Nährwert: trinkbar auch mit vollem Hunger, keine food-Komponente
            stack.unsetData(DataComponentTypes.FOOD);
        } else {
            stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                    .nutrition((int) Math.round(food.nutrition() * ratio))
                    .saturation((float) (food.saturation() * ratio))
                    .canAlwaysEat(food.alwaysEdible())
                    .build());
        }
        Consumable.Builder consumable = Consumable.consumable()
                .consumeSeconds(food.eatSeconds())
                .animation(food.drink() ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
                .sound(food.sound() != null ? food.sound() : food.drink() ? org.bukkit.Sound.ENTITY_GENERIC_DRINK.key() : org.bukkit.Sound.ENTITY_GENERIC_EAT.key())
                .hasConsumeParticles(!food.drink());
        for (CookeryItem.FoodEffect effect : food.effects()) {
            PotionEffectType type = vanillaEffect(effect.effect());
            int duration = (int) Math.round(effect.duration() * ratio);
            if (type != null && duration > 0) {
                consumable.addEffect(ConsumeEffect.applyStatusEffects(List.of(new PotionEffect(type, duration, effect.amplifier())), effect.probability()));
            }
        }
        stack.setData(DataComponentTypes.CONSUMABLE, consumable.build());
        if (food.remainder() != null) {
            ItemStack remainder = create(food.remainder(), 1);
            if (remainder != null) {
                stack.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(remainder));
            }
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void applyWearable(ItemStack stack, CookeryItem.Wearable wearable) {
        Equippable.Builder equippable = Equippable.equippable(wearable.slot()).equipSound(org.bukkit.Sound.ITEM_ARMOR_EQUIP_LEATHER.key());
        if (wearable.asset() != null) {
            equippable.assetId(wearable.asset());
        }
        stack.setData(DataComponentTypes.EQUIPPABLE, equippable.build());
        org.bukkit.inventory.EquipmentSlotGroup group = wearable.slot().getGroup();
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(org.bukkit.attribute.Attribute.ARMOR,
                        new org.bukkit.attribute.AttributeModifier(new NamespacedKey(Keys.NAMESPACE, "armor_" + wearable.slot().name().toLowerCase(java.util.Locale.ROOT)),
                                wearable.armor(), org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, group), group)
                .build());
        if (wearable.unbreakable()) {
            stack.unsetData(DataComponentTypes.MAX_DAMAGE);
            stack.unsetData(DataComponentTypes.DAMAGE);
        }
    }

    /**
     * Beschreibungszeile aus der Mod-Sprachdatei.
     */
    /**
     * Beschreibung (grau) und Spruch (dunkelgrau, kursiv; *.maxim.lineN) wie in den appendHoverText der Mods.
     */
    public static List<Component> tooltipLines(CookeryItem item) {
        List<Component> lines = new java.util.ArrayList<>();
        if (item.tooltipKey() != null) {
            lines.add(Text.noItalic(Lang.raw(item.tooltipKey()).color(NamedTextColor.GRAY)));
        }
        for (int i = 1; i <= item.maximLines(); i++) {
            lines.add(Lang.raw("tooltip." + item.id().namespace() + "." + item.id().value() + ".maxim.line" + i)
                    .color(NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
        }
        return lines;
    }

    public static @Nullable PotionEffectType vanillaEffect(Key key) {
        if (!key.namespace().equals(NamespacedKey.MINECRAFT)) {
            return null;
        }
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key);
    }
}
