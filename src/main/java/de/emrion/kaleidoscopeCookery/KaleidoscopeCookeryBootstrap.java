package de.emrion.kaleidoscopeCookery;

import de.emrion.kaleidoscopeCookery.item.QuickKnifeListener;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

import java.util.List;

/**
 * Registriert beim Serverstart die Verzauberung "Schnelles Messer" (quick_knife aus Kaleidoscope Cookery)
 * über die Registry-API von Paper – ohne Datapack und ohne NMS.
 * <p>
 * Werte wie im Mod (data/kaleidoscope_cookery/enchantment/quick_knife.json). Erlaubte Items sind die Schwert-Typen,
 * auf denen die Küchenmesser basieren; {@link QuickKnifeListener} hält sie von normalen Schwertern fern.
 */
@SuppressWarnings("UnstableApiUsage")
public class KaleidoscopeCookeryBootstrap implements PluginBootstrap {

    public static final TypedKey<Enchantment> QUICK_KNIFE = TypedKey.create(RegistryKey.ENCHANTMENT, QuickKnifeListener.KEY);

    @Override
    public void bootstrap(BootstrapContext context) {
        List<TypedKey<ItemType>> knifeBases = List.of(ItemTypeKeys.IRON_SWORD, ItemTypeKeys.GOLDEN_SWORD, ItemTypeKeys.DIAMOND_SWORD, ItemTypeKeys.NETHERITE_SWORD);
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event ->
                event.registry().register(QUICK_KNIFE, builder -> builder
                        .description(Component.translatable("enchantment.kaleidoscope_cookery.quick_knife"))
                        .supportedItems(RegistrySet.keySet(RegistryKey.ITEM, knifeBases))
                        .primaryItems(RegistrySet.keySet(RegistryKey.ITEM, knifeBases))
                        .weight(10)
                        .maxLevel(2)
                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 11))
                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(21, 11))
                        .anvilCost(2)
                        .activeSlots(EquipmentSlotGroup.MAINHAND))));
        // Wie im Mod: am Zaubertisch erhältlich, kein Schatz
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.ENCHANTMENT), event -> {
            event.registrar().addToTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE, List.of(QUICK_KNIFE));
            event.registrar().addToTag(EnchantmentTagKeys.NON_TREASURE, List.of(QUICK_KNIFE));
        });
    }
}
