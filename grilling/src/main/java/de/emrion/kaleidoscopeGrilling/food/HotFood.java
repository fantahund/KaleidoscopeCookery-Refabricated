package de.emrion.kaleidoscopeGrilling.food;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.api.CookeryFoodEatenEvent;
import de.emrion.kaleidoscopeCookery.api.DishServedEvent;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Heißes Essen (烟火气, HotFoodHandler/FoodState im Mod).
 * <ul>
 *     <li>Gerichte aus Wok und Suppentopf sind eine Zeit lang heiß (Restzeit in der Beschreibung)</li>
 *     <li>Beim Essen eines heißen Gerichts halten neu bekommene Effekte doppelt so lange
 *     und der Sättigungs-Gewinn wird mit saturation-percent multipliziert</li>
 * </ul>
 */
public class HotFood implements Listener {

    /** Gleicher Schlüssel wie früher im Core, damit schon heiße Items heiß bleiben */
    public static final NamespacedKey HOT_UNTIL = new NamespacedKey("kaleidoscope", "hot_until");
    /** FoodState#bucket: Restzeit in 5-Sekunden-Schritten, damit gleich heiße Items stapeln */
    private static final long HEAT_BUCKET_TICKS = 5 * 20;
    private static final long LORE_INTERVAL = 100;

    private final Plugin plugin;
    private final KaleidoscopeCookery cookery;
    private boolean enabled;
    private int saturationPercent;
    private int wokSeconds;
    private int stockpotSeconds;
    private @Nullable BukkitTask loreTask;

    public HotFood(Plugin plugin, KaleidoscopeCookery cookery) {
        this.plugin = plugin;
        this.cookery = cookery;
    }

    public void load(ConfigurationSection config) {
        enabled = config.getBoolean("enabled", true);
        saturationPercent = Math.clamp(config.getInt("saturation-percent", 125), 100, 200);
        wokSeconds = Math.max(0, config.getInt("wok-seconds", 60));
        stockpotSeconds = Math.max(0, config.getInt("stockpot-seconds", 60));
    }

    public void start() {
        cookery.getFoodService().addLoreExtension(this::lore);
        loreTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateInventories, LORE_INTERVAL, LORE_INTERVAL);
    }

    public void stop() {
        if (loreTask != null) {
            loreTask.cancel();
        }
    }

    // ---- Zustand ----

    public void makeHot(ItemStack stack, World world, int seconds) {
        if (!enabled || seconds <= 0) {
            return;
        }
        long until = world.getGameTime() + seconds * 20L;
        long bucketed = until - Math.floorMod(until, HEAT_BUCKET_TICKS);
        stack.editPersistentDataContainer(pdc -> pdc.set(HOT_UNTIL, PersistentDataType.LONG, bucketed));
        cookery.getFoodService().refreshLore(stack, world);
    }

    public static long hotUntil(ItemStack stack) {
        return stack.getPersistentDataContainer().getOrDefault(HOT_UNTIL, PersistentDataType.LONG, 0L);
    }

    public static boolean isHot(ItemStack stack, World world) {
        return hotUntil(stack) > world.getGameTime();
    }

    /**
     * Beschreibung: Leerzeile und "🔥 noch m:ss heiß" (rot wie im Mod); abgelaufene Hitze wird entfernt.
     */
    private void lore(ItemStack stack, @Nullable World world, List<Component> lore) {
        long until = hotUntil(stack);
        if (until <= 0 || world == null) {
            return;
        }
        if (until <= world.getGameTime()) {
            stack.editPersistentDataContainer(pdc -> pdc.remove(HOT_UNTIL));
            return;
        }
        long seconds = Math.max(1, (until - world.getGameTime()) / 20);
        lore.add(Component.empty());
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_grilling.hot_food", seconds / 60, String.format("%02d", seconds % 60))
                .color(NamedTextColor.RED)));
    }

    /**
     * Restzeit im Inventar alle 5 s aktualisieren – nicht während man isst (sonst bricht der Client das Essen ab).
     */
    private void updateInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasActiveItem()) {
                continue;
            }
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && hotUntil(stack) > 0 && cookery.getFoodService().refreshLore(stack, player.getWorld())) {
                    player.getInventory().setItem(slot, stack);
                }
            }
        }
    }

    // ---- Entstehen ----

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDishServed(DishServedEvent event) {
        int seconds = switch (event.getStationType()) {
            case "pot" -> wokSeconds;
            case "stockpot" -> stockpotSeconds;
            default -> 0;
        };
        makeHot(event.getDish(), event.getPlayer().getWorld(), seconds);
    }

    // ---- Essen ----

    /**
     * Aus der Hand: Effekte und Sättigung vorher merken, einen Tick später die neuen Effekte verdoppeln und den
     * Sättigungs-Gewinn vervielfachen (HotFoodHandler#onStart/onFinish).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!isHot(event.getItem(), player.getWorld())) {
            return;
        }
        Map<PotionEffectType, Integer> before = snapshot(player);
        float saturationBefore = player.getSaturation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            doubleNewDurations(player, before);
            multiplySaturationGain(player, saturationBefore);
        });
    }

    /**
     * Vom Teller und aus dem Lunchbeutel: Werte sind schon verrechnet – Effekte des Gerichts verdoppeln,
     * Sättigung nachträglich erhöhen.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCookeryFoodEaten(CookeryFoodEatenEvent event) {
        Player player = event.getPlayer();
        if (event.getSource() == CookeryFoodEatenEvent.Source.HAND || !isHot(event.getFood(), player.getWorld())) {
            return;
        }
        float gained = (float) (event.getValues().saturation() * event.getRatio());
        float bonus = gained * (saturationPercent - 100) / 100f;
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + bonus));
    }

    private static Map<PotionEffectType, Integer> snapshot(Player player) {
        Map<PotionEffectType, Integer> result = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            result.put(effect.getType(), effect.getDuration());
        }
        return result;
    }

    private static void doubleNewDurations(Player player, Map<PotionEffectType, Integer> before) {
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            int old = before.getOrDefault(effect.getType(), 0);
            if (effect.getDuration() <= old || effect.isInfinite()) {
                continue;
            }
            int duration = old + (effect.getDuration() - old) * 2;
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(new PotionEffect(effect.getType(), duration, effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
    }

    private void multiplySaturationGain(Player player, float before) {
        float multiplier = saturationPercent / 100f;
        float gained = Math.max(0, player.getSaturation() - before);
        if (multiplier <= 1 || gained <= 0) {
            return;
        }
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + gained * (multiplier - 1)));
    }
}
