package de.emrion.kaleidoscopeCookery.food;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.effect.CookeryEffect;
import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Essen: Qualität (Kaleidoscope Cookery), Hitze/烟火气 (Kaleidoscope Grilling), Mod-Effekte beim Essen und die
 * Abschwächung von Vanilla-Essen.
 * <ul>
 *     <li>Qualität steht im PDC und skaliert Nährwert, Sättigung und Effektdauer (wie QualityUtils im Mod)</li>
 *     <li>Hitze: Zeitstempel (Spielzeit der Welt) im PDC, auf 5 s gerundet, damit gleich heiße Stücke stapeln.
 *         Heißes Essen sättigt stärker (Standard 125 %).</li>
 * </ul>
 */
public class FoodService implements Listener {

    public static final NamespacedKey QUALITY = Keys.key("quality");
    public static final NamespacedKey HOT_UNTIL = Keys.key("hot_until");
    private static final long HEAT_BUCKET_TICKS = 5 * 20;
    private static final int LORE_UPDATE_INTERVAL = 100;

    private final KaleidoscopeCookery plugin;
    private final Set<UUID> eatingCake = new HashSet<>();
    private BukkitTask loreTask;

    public FoodService(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public void start() {
        loreTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateHeldLore, LORE_UPDATE_INTERVAL, LORE_UPDATE_INTERVAL);
    }

    public void stop() {
        if (loreTask != null) {
            loreTask.cancel();
        }
    }

    // ---- Qualität ----

    public static @Nullable Quality quality(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return Quality.byId(stack.getPersistentDataContainer().get(QUALITY, PersistentDataType.STRING));
    }

    /**
     * Qualität setzen und Nahrungswerte entsprechend skalieren.
     */
    public void setQuality(ItemStack stack, Quality quality) {
        stack.editPersistentDataContainer(pdc -> pdc.set(QUALITY, PersistentDataType.STRING, quality.id()));
        CookeryItem item = plugin.getItems().get(CookeryItems.typeOf(stack));
        if (item != null && item.food() != null) {
            plugin.getItems().applyFood(stack, item.food(), quality.ratio());
        }
        refreshLore(stack, null);
    }

    // ---- Hitze ----

    /**
     * Essen heiß machen (烟火气).
     */
    public void makeHot(ItemStack stack, World world, int seconds) {
        if (seconds <= 0 || !plugin.getCookeryConfig().food().hotEnabled()) {
            return;
        }
        long until = world.getGameTime() + seconds * 20L;
        until -= Math.floorMod(until, HEAT_BUCKET_TICKS);
        long finalUntil = until;
        stack.editPersistentDataContainer(pdc -> pdc.set(HOT_UNTIL, PersistentDataType.LONG, finalUntil));
        refreshLore(stack, world);
    }

    public static long hotUntil(ItemStack stack) {
        return stack.getPersistentDataContainer().getOrDefault(HOT_UNTIL, PersistentDataType.LONG, 0L);
    }

    public static boolean isHot(ItemStack stack, World world) {
        return hotUntil(stack) > world.getGameTime();
    }

    /**
     * Lore neu aufbauen: Beschreibung, Qualität, Hitze. Abgelaufene Hitze wird entfernt.
     *
     * @return true, wenn sich etwas geändert hat
     */
    @SuppressWarnings("UnstableApiUsage")
    public boolean refreshLore(ItemStack stack, @Nullable World world) {
        CookeryItem item = plugin.getItems().get(CookeryItems.typeOf(stack));
        long hotUntil = hotUntil(stack);
        if (hotUntil > 0 && world != null && hotUntil <= world.getGameTime()) {
            stack.editPersistentDataContainer(pdc -> pdc.remove(HOT_UNTIL));
            hotUntil = 0;
        }
        List<Component> lore = new ArrayList<>();
        if (item != null && item.tooltipKey() != null) {
            lore.add(CookeryItems.tooltip(item));
        }
        Quality quality = quality(stack);
        if (quality != null) {
            lore.add(Text.noItalic(Lang.raw(quality.translationKey()).color(quality.color())));
        }
        if (hotUntil > 0 && world != null) {
            long seconds = Math.max(0, (hotUntil - world.getGameTime()) / 20);
            lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.hot_food", seconds / 60, String.format("%02d", seconds % 60))
                    .color(NamedTextColor.GOLD)));
        }
        ItemLore current = stack.getData(DataComponentTypes.LORE);
        if (current != null && current.lines().equals(lore) || current == null && lore.isEmpty()) {
            return false;
        }
        if (lore.isEmpty()) {
            stack.unsetData(DataComponentTypes.LORE);
        } else {
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        return true;
    }

    /**
     * Restzeit der Hitze im Inventar aktualisieren (alle 5 s).
     */
    private void updateHeldLore() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && hotUntil(stack) > 0 && refreshLore(stack, player.getWorld())) {
                    player.getInventory().setItem(slot, stack);
                }
            }
        }
    }

    // ---- Essen ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItem();
        CookeryItem item = plugin.getItems().get(CookeryItems.typeOf(stack));
        Set<PotionEffectType> before = harmfulEffects(player);
        if (item != null && item.food() != null) {
            Quality quality = quality(stack);
            applyModEffects(player, item.food(), quality == null ? 1 : quality.ratio());
            if (isHot(stack, player.getWorld())) {
                applyHotBonus(player, (float) (item.food().saturation() * (quality == null ? 1 : quality.ratio())));
            }
        } else if (item == null && stack.hasData(DataComponentTypes.FOOD)) {
            nerfVanilla(player, stack);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Set<PotionEffectType> added = harmfulEffects(player);
            added.removeAll(before);
            plugin.getEffects().preserve(player, List.copyOf(added));
        });
    }

    /**
     * Biss von einem Teller (FoodBiteBlock#eat im Mod).
     *
     * @return false, wenn der Spieler gerade nicht essen kann
     */
    public boolean eatBite(Player player, CookeryItem.Food food, double ratio) {
        if (!food.alwaysEdible() && player.getFoodLevel() >= 20) {
            return false;
        }
        int nutrition = (int) Math.round(food.nutrition() * ratio);
        float saturation = (float) (food.saturation() * ratio);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + nutrition));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + saturation));
        for (CookeryItem.FoodEffect effect : food.effects()) {
            if (ThreadLocalRandom.current().nextFloat() >= effect.probability()) {
                continue;
            }
            PotionEffectType type = CookeryItems.vanillaEffect(effect.effect());
            int duration = (int) Math.round(effect.duration() * ratio);
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, duration, effect.amplifier()));
            }
        }
        applyModEffects(player, food, ratio);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.5f, ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f);
        return true;
    }

    private void applyModEffects(Player player, CookeryItem.Food food, double ratio) {
        for (CookeryItem.FoodEffect effect : food.effects()) {
            CookeryEffect cookeryEffect = CookeryEffect.byKey(effect.effect());
            if (cookeryEffect != null && ThreadLocalRandom.current().nextFloat() < effect.probability()) {
                plugin.getEffects().apply(player, cookeryEffect, (int) Math.round(effect.duration() * ratio), effect.amplifier());
            }
        }
    }

    private void applyHotBonus(Player player, float saturation) {
        int percent = plugin.getCookeryConfig().food().hotSaturationPercent();
        float bonus = saturation * (percent - 100) / 100f;
        if (bonus <= 0) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + bonus)));
    }

    private static Set<PotionEffectType> harmfulEffects(Player player) {
        Set<PotionEffectType> result = new HashSet<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                result.add(effect.getType());
            }
        }
        return result;
    }

    // ---- Vanilla-Essen abschwächen ----

    /**
     * Vanilla-Essen: nach dem Essen Hunger und Sättigung auf den eingestellten Anteil zurücksetzen
     * (das Item selbst bleibt unverändert, damit es weiter normal stapelt).
     */
    @SuppressWarnings("UnstableApiUsage")
    private void nerfVanilla(Player player, ItemStack stack) {
        CookeryConfig.Food config = plugin.getCookeryConfig().food();
        int percent = config.vanillaPercent(stack.getType());
        // Vanilla-Werte des Typs, nicht die des Stapels (der ist normalerweise schon angepasst, siehe VanillaFoodAdjuster)
        FoodProperties food = VanillaFoodAdjuster.vanillaFood(stack.getType());
        if (!config.vanillaNerfEnabled() || percent >= 100 || food == null) {
            return;
        }
        int foodBefore = player.getFoodLevel();
        float saturationBefore = player.getSaturation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            int foodLevel = Math.min(20, foodBefore + (int) Math.round(food.nutrition() * percent / 100.0));
            float saturation = Math.min(foodLevel, saturationBefore + food.saturation() * percent / 100f);
            // Nur kürzen, nie mehr geben als Vanilla (andere Plugins/Effekte können dazwischen gewirkt haben)
            player.setFoodLevel(Math.min(player.getFoodLevel(), foodLevel));
            player.setSaturation(Math.min(player.getSaturation(), saturation));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCakeClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType().name().endsWith("CAKE")) {
            UUID id = event.getPlayer().getUniqueId();
            eatingCake.add(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> eatingCake.remove(id));
        }
    }

    /**
     * Kuchen wird nicht als Item gegessen: Hungeränderung direkt kürzen.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevel(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getItem() != null || !eatingCake.contains(player.getUniqueId())) {
            return;
        }
        CookeryConfig.Food config = plugin.getCookeryConfig().food();
        int gain = event.getFoodLevel() - player.getFoodLevel();
        if (!config.vanillaNerfEnabled() || gain <= 0) {
            return;
        }
        int percent = config.vanillaPercent(Material.CAKE);
        event.setFoodLevel(player.getFoodLevel() + (int) Math.round(gain * percent / 100.0));
    }

    public static Key typeOf(ItemStack stack) {
        return CookeryItems.typeOf(stack);
    }
}
