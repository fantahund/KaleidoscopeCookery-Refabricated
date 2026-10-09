package de.emrion.kaleidoscopeCookery.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Verzauberung "Schnelles Messer" (kaleidoscope_cookery:quick_knife, registriert in KaleidoscopeCookeryBootstrap).
 * Sie hängt technisch an den Schwert-Typen, auf denen die Küchenmesser basieren. Dieser Listener sorgt dafür,
 * dass normale Schwerter sie weder am Zaubertisch noch am Amboss bekommen.
 */
public class QuickKnifeListener implements Listener {

    public static final Key KEY = Key.key("kaleidoscope_cookery", "quick_knife");

    public static @Nullable Enchantment enchantment() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(KEY);
    }

    /**
     * Stufe von "Schnelles Messer" auf dem Item (0 = keine).
     */
    public static int level(@Nullable ItemStack stack) {
        Enchantment enchantment = enchantment();
        return stack == null || enchantment == null ? 0 : stack.getEnchantmentLevel(enchantment);
    }

    /**
     * Küchenmesser = Mod-Item, dessen ID auf _kitchen_knife endet.
     */
    public static boolean isKnife(@Nullable ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null && id.value().endsWith("kitchen_knife");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        Enchantment quickKnife = enchantment();
        if (quickKnife == null || isKnife(event.getItem())) {
            return;
        }
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] != null && offers[i].getEnchantment().equals(quickKnife)) {
                // Statt "Schnelles Messer" Schärfe anbieten (gleiche Kosten)
                offers[i] = new EnchantmentOffer(Enchantment.SHARPNESS, Math.min(offers[i].getEnchantmentLevel() + 1, 5), offers[i].getCost());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Enchantment quickKnife = enchantment();
        if (quickKnife == null || isKnife(event.getItem())) {
            return;
        }
        Integer level = event.getEnchantsToAdd().remove(quickKnife);
        if (level != null && !event.getEnchantsToAdd().containsKey(Enchantment.SHARPNESS)) {
            event.getEnchantsToAdd().put(Enchantment.SHARPNESS, Math.min(level + 1, 5));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        ItemStack first = event.getInventory().getFirstItem();
        if (result == null || first == null || level(result) == 0 || isKnife(first) || first.getType() == org.bukkit.Material.ENCHANTED_BOOK
                || first.getType() == org.bukkit.Material.BOOK) {
            return;
        }
        if (level(first) == 0) {
            event.setResult(null);
        }
    }
}
