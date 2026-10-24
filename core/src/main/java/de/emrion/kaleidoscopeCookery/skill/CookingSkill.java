package de.emrion.kaleidoscopeCookery.skill;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.api.CookeryFoodEatenEvent;
import de.emrion.kaleidoscopeCookery.api.DishServedEvent;
import de.emrion.kaleidoscopeCookery.food.FoodService;
import de.emrion.kaleidoscopeCookery.food.Quality;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import de.emrion.kaleidoscopeCore.KaleidoscopeCore;
import de.emrion.kaleidoscopeCore.skill.Skill;
import de.emrion.kaleidoscopeCore.skill.SkillManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cooking level (one skill for Cookery and Grilling, both are food), stored by KaleidoscopeCore:
 * <ul>
 *     <li>XP for finished food: wok dishes and stockpot portions (by quality), steamer, chopping board, shawarma spit and
 *     (Grilling) grilled and skewered skewers. Failed dishes (suspicious stir-fry, dark cuisine) give nothing.</li>
 *     <li>Perks per level: chance for a better quality and for an extra portion when serving from wok or stockpot</li>
 *     <li>Five ranks (every 10 levels). Served dishes carry the rank of their cook and fill the saturation of whoever
 *     eats them by 10 % per rank above the first.</li>
 * </ul>
 * Dishes unlock with the level (cooking-skill.dishes in the config: level, hunger and saturation per dish; effect
 * durations by level): simple food at level 1, the feasts at 41. A dish above the cook's level fails (wok, stockpot,
 * teapot) or cannot be started (steamer, grill, rice bowl in the crafting table).
 */
public final class CookingSkill implements Listener {

    public static final Key ID = Key.key("kaleidoscope_cookery", "cooking");
    private static final NamespacedKey CHEF_RANK = Keys.key("chef_rank");
    private static final int LEVELS_PER_RANK = 10;
    private static final int MAX_LEVEL = 50;
    /** Default XP per source (overridden by cooking-skill.xp in the config) */
    private static final Map<String, Integer> DEFAULT_XP = Map.of("pot", 12, "stockpot", 4, "steamer", 3, "chopping-board", 1,
            "shawarma-spit", 1, "grill", 8, "skewer", 2, "teapot", 2);

    private static @Nullable CookingSkill instance;

    private final SkillManager skills;
    private final Map<String, Integer> xp = new HashMap<>();
    private final double qualityChancePerLevel;
    private final double extraPortionPerLevel;
    private final double saturationPerRank;

    private CookingSkill(ConfigurationSection config) {
        this.skills = KaleidoscopeCore.getInstance().getSkills();
        long baseXp = Math.max(1, config.getLong("base-xp", 100));
        double growth = Math.max(1.0, config.getDouble("growth", 1.1));
        skills.register(new Skill(ID, Lang.raw("skill.kaleidoscope_cookery.cooking"), MAX_LEVEL, baseXp, growth));
        DEFAULT_XP.forEach((source, amount) -> xp.put(source, Math.max(0, config.getInt("xp." + source, amount))));
        this.qualityChancePerLevel = config.getDouble("perks.better-quality-per-level", 0.006);
        this.extraPortionPerLevel = config.getDouble("perks.extra-portion-per-level", 0.003);
        this.saturationPerRank = config.getDouble("perks.saturation-per-rank", 0.10);
    }

    public static void init(KaleidoscopeCookery plugin) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("cooking-skill");
        instance = new CookingSkill(config != null ? config : plugin.getConfig().createSection("cooking-skill"));
        plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        plugin.getFoodService().addLoreExtension((stack, world, lore) -> {
            Key dish = CookeryItems.idOf(stack);
            int required = dish == null ? 1 : requiredLevel(dish);
            if (required > 1) {
                lore.add(Text.noItalic(Lang.raw("kaleidoscope.chef.required", required).color(NamedTextColor.GRAY)));
            }
            int rank = rankOf(stack);
            if (rank > 0) {
                lore.add(Text.noItalic(Lang.raw("kaleidoscope.chef.cooked_by", Lang.raw("kaleidoscope.chef.rank." + rank))
                        .color(NamedTextColor.GOLD)));
            }
        });
    }

    /**
     * XP for a finished piece of food (also for addons, e.g. Grilling). Unknown sources give nothing.
     *
     * @param amount number of pieces
     */
    public static void award(Player player, String source, int amount) {
        if (instance != null && amount > 0) {
            instance.skills.addXp(player, ID, (long) instance.xp.getOrDefault(source, 0) * amount);
        }
    }

    /** Kept for addons; the levels now come from cooking-skill.dishes in the Cookery config */
    public static void registerDish(Key dish) {
    }

    /** Cooking level needed for a dish (cooking-skill.dishes), 1 if it is not listed */
    public static int requiredLevel(Key dish) {
        de.emrion.kaleidoscopeCookery.config.CookeryConfig.DishValues values =
                KaleidoscopeCookery.getInstance().getCookeryConfig().food().dishValues().get(dish);
        return values == null ? 1 : values.level();
    }

    /**
     * May the player cook this dish? Tells them the level they need if not.
     */
    public static boolean mayCook(@Nullable Player player, Key dish) {
        if (player == null || level(player) >= requiredLevel(dish)) {
            return true;
        }
        player.sendActionBar(Lang.raw("kaleidoscope.chef.too_low", Lang.raw(nameKey(dish)), requiredLevel(dish)).color(NamedTextColor.RED));
        return false;
    }

    private static String nameKey(Key dish) {
        de.emrion.kaleidoscopeCookery.item.CookeryItem item = KaleidoscopeCookery.getInstance().getItems().get(dish);
        return item == null ? dish.asString() : item.nameKey();
    }

    public static int level(Player player) {
        return instance == null ? 1 : instance.skills.getLevel(player, ID);
    }

    /** 0 = kitchen help (1-10) ... 4 = star chef (41-50) */
    public static int rank(Player player) {
        return Math.min(4, (level(player) - 1) / LEVELS_PER_RANK);
    }

    private static int rankOf(@Nullable ItemStack stack) {
        return stack == null ? 0 : stack.getPersistentDataContainer().getOrDefault(CHEF_RANK, PersistentDataType.INTEGER, 0);
    }

    private static double qualityFactor(@Nullable Quality quality) {
        if (quality == null) {
            return 1;
        }
        return switch (quality) {
            case POOR -> 0.5;
            case STANDARD -> 1;
            case EXCELLENT -> 1.5;
            case SUPERB -> 2;
        };
    }

    // ---- wok and stockpot ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onServed(DishServedEvent event) {
        Player player = event.getPlayer();
        ItemStack dish = event.getDish();
        Key id = CookeryItems.idOf(dish);
        if (id == null || id.value().contains("suspicious") || id.value().equals("dark_cuisine")) {
            return;
        }
        String source = event.getStationType().equals("stockpot") ? "stockpot" : "pot";
        FoodService food = KaleidoscopeCookery.getInstance().getFoodService();
        int level = level(player);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Quality quality = FoodService.quality(dish);
        if (quality != null && quality != Quality.SUPERB && random.nextDouble() < level * qualityChancePerLevel) {
            // Quality is ordered best first
            quality = Quality.values()[quality.ordinal() - 1];
            food.setQuality(dish, quality);
            player.sendActionBar(Lang.raw("kaleidoscope.chef.better_quality").color(NamedTextColor.GREEN));
        }
        int rank = Math.min(4, (level - 1) / LEVELS_PER_RANK);
        if (rank > 0) {
            dish.editPersistentDataContainer(pdc -> pdc.set(CHEF_RANK, PersistentDataType.INTEGER, rank));
            food.refreshLore(dish, player.getWorld());
        }
        skills.addXp(player, ID, Math.round(xp.getOrDefault(source, 0) * qualityFactor(quality)));
        if (random.nextDouble() < level * extraPortionPerLevel) {
            ItemStack extra = dish.clone();
            extra.setAmount(1);
            for (ItemStack left : player.getInventory().addItem(extra).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            player.sendActionBar(Lang.raw("kaleidoscope.chef.extra_portion").color(NamedTextColor.GOLD));
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
        }
    }

    // ---- eating food of a good cook ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEaten(CookeryFoodEatenEvent event) {
        int rank = rankOf(event.getFood());
        if (rank <= 0) {
            return;
        }
        Player player = event.getPlayer();
        float extra = (float) (event.getValues().saturation() * event.getRatio() * rank * saturationPerRank);
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + extra));
    }
}
