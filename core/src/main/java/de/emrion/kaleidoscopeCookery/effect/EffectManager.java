package de.emrion.kaleidoscopeCookery.effect;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.Keys;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Effekte aus Kaleidoscope Cookery, nachgebaut wie im Mod (VigorEffect, WarmthEffect, SatiatedShieldEvent ...).
 * Aktive Effekte stehen in der Seitenleiste mit den Original-Icons. Eigene Effekte im Inventar anzeigen geht nicht:
 * Effekte sind eine feste Registry im Client. Beim Verlassen werden sie im Spieler-PDC gespeichert.
 */
public class EffectManager implements Listener {

    private static final NamespacedKey SAVED = Keys.key("effects");
    private static final int TICK_INTERVAL = 5;
    /** Gesättigtes Schild: Mindest-Hunger und Erschöpfung pro abgefangenem Schaden (Mod-Standardwerte) */
    private static final int SHIELD_MIN_FOOD_LEVEL = 4;
    private static final float SHIELD_EXHAUSTION_PER_DAMAGE = 2f;
    /** Projektil-Ausweichen kostet so viel Effektdauer (Ticks) */
    private static final int DODGE_COST = 200;
    private static final Set<Material> TUNDRA_BLOCKS = Set.of(Material.SNOW, Material.SNOW_BLOCK, Material.POWDER_SNOW,
            Material.PACKED_ICE, Material.ICE, Material.FROSTED_ICE, Material.BLUE_ICE);
    private static final Set<Material> WARMTH_BLOCKS = Set.of(Material.FIRE, Material.SOUL_FIRE, Material.LAVA, Material.MAGMA_BLOCK);

    private record Active(long end, int amplifier) {
    }

    private final KaleidoscopeCookery plugin;
    private final Map<UUID, EnumMap<CookeryEffect, Active>> active = new HashMap<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    /** Effekte von Addons (z. B. Grilling): Anzeige wie die Cookery-Effekte, Wirkung setzt das Addon selbst um */
    private final Map<net.kyori.adventure.key.Key, AddonEffect> addonEffects = new java.util.LinkedHashMap<>();
    private final Map<UUID, Map<net.kyori.adventure.key.Key, Active>> addonActive = new HashMap<>();

    /**
     * Effekt eines Addons.
     *
     * @param icon Icon (Zeichen in einer Schrift des Addon-Packs)
     */
    public record AddonEffect(net.kyori.adventure.key.Key key, net.kyori.adventure.text.format.TextColor color, Component icon) {
        public String translationKey() {
            return "effect." + key.namespace() + "." + key.value();
        }
    }

    private record Sidebar(Scoreboard previous, Scoreboard board, Objective objective) {
    }
    private BukkitTask task;
    private long tick;

    public EffectManager(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            load(player);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            save(player);
            hideBar(player);
        }
        active.clear();
    }

    // ---- API ----

    public void registerAddonEffect(AddonEffect effect) {
        addonEffects.put(effect.key(), effect);
    }

    public boolean isAddonEffect(net.kyori.adventure.key.Key key) {
        return addonEffects.containsKey(key);
    }

    /**
     * Effekt eines Addons geben (höhere Stufe bzw. längere Dauer gewinnt).
     */
    public void apply(Player player, net.kyori.adventure.key.Key key, int durationTicks, int amplifier) {
        CookeryEffect cookery = CookeryEffect.byKey(key);
        if (cookery != null) {
            apply(player, cookery, durationTicks, amplifier);
            return;
        }
        if (durationTicks <= 0 || !addonEffects.containsKey(key)) {
            return;
        }
        Map<net.kyori.adventure.key.Key, Active> effects = addonActive.computeIfAbsent(player.getUniqueId(), k -> new java.util.LinkedHashMap<>());
        long end = tick + durationTicks;
        Active old = effects.get(key);
        if (old == null || amplifier > old.amplifier() || (amplifier == old.amplifier() && end > old.end())) {
            effects.put(key, new Active(end, amplifier));
        }
        active.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(CookeryEffect.class));
        updateBar(player);
    }

    public boolean has(Entity entity, net.kyori.adventure.key.Key key) {
        Map<net.kyori.adventure.key.Key, Active> effects = addonActive.get(entity.getUniqueId());
        return effects != null && effects.containsKey(key);
    }

    public int amplifier(Entity entity, net.kyori.adventure.key.Key key) {
        Map<net.kyori.adventure.key.Key, Active> effects = addonActive.get(entity.getUniqueId());
        Active a = effects == null ? null : effects.get(key);
        return a == null ? -1 : a.amplifier();
    }

    public void remove(Player player, net.kyori.adventure.key.Key key) {
        Map<net.kyori.adventure.key.Key, Active> effects = addonActive.get(player.getUniqueId());
        if (effects != null && effects.remove(key) != null) {
            updateBar(player);
        }
    }

    /**
     * Effekt geben. Wie bei Vanilla gewinnt die höhere Stufe bzw. bei gleicher Stufe die längere Dauer.
     */
    public void apply(Player player, CookeryEffect effect, int durationTicks, int amplifier) {
        if (durationTicks <= 0) {
            return;
        }
        EnumMap<CookeryEffect, Active> effects = active.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(CookeryEffect.class));
        long end = tick + durationTicks;
        Active old = effects.get(effect);
        if (old == null || amplifier > old.amplifier() || (amplifier == old.amplifier() && end > old.end())) {
            effects.put(effect, new Active(end, amplifier));
        }
        updateBar(player);
    }

    public boolean has(Entity entity, CookeryEffect effect) {
        EnumMap<CookeryEffect, Active> effects = active.get(entity.getUniqueId());
        return effects != null && effects.containsKey(effect);
    }

    public int amplifier(Entity entity, CookeryEffect effect) {
        EnumMap<CookeryEffect, Active> effects = active.get(entity.getUniqueId());
        Active a = effects == null ? null : effects.get(effect);
        return a == null ? -1 : a.amplifier();
    }

    public void remove(Player player, CookeryEffect effect) {
        EnumMap<CookeryEffect, Active> effects = active.get(player.getUniqueId());
        if (effects != null && effects.remove(effect) != null) {
            updateBar(player);
        }
    }

    public void clear(Player player) {
        active.remove(player.getUniqueId());
        addonActive.remove(player.getUniqueId());
        hideBar(player);
    }

    // ---- Tick ----

    private void tick() {
        tick += TICK_INTERVAL;
        for (Iterator<Map.Entry<UUID, EnumMap<CookeryEffect, Active>>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, EnumMap<CookeryEffect, Active>> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            EnumMap<CookeryEffect, Active> effects = entry.getValue();
            effects.values().removeIf(a -> a.end() <= tick);
            Map<net.kyori.adventure.key.Key, Active> addon = addonActive.get(entry.getKey());
            if (addon != null) {
                addon.values().removeIf(a -> a.end() <= tick);
                if (addon.isEmpty()) {
                    addonActive.remove(entry.getKey());
                    updateBar(player);
                }
            }
            if (effects.isEmpty() && !addonActive.containsKey(entry.getKey())) {
                it.remove();
                hideBar(player);
                continue;
            }
            if (player.isDead()) {
                continue;
            }
            if (effects.containsKey(CookeryEffect.WARMTH) && tick % 20 == 0) {
                warmth(player);
            }
            if (effects.containsKey(CookeryEffect.SULFUR)) {
                sulfur(player);
            }
            if (effects.containsKey(CookeryEffect.MUSTARD) && tick % 10 == 0) {
                mustard(player);
            }
            if (effects.containsKey(CookeryEffect.TUNDRA_STRIDER)) {
                tundraStrider(player);
            }
            if (tick % 20 == 0) {
                updateBar(player);
            }
        }
    }

    /**
     * WarmthEffect: in 5x3x5 um den Spieler eine Hitzequelle -> 1 Herz pro Sekunde, im Nether manchmal ein halbes.
     */
    private void warmth(Player player) {
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() >= max) {
            return;
        }
        Block base = player.getLocation().getBlock();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = base.getRelative(x, y, z);
                    boolean lit = block.getBlockData() instanceof Lightable lightable && lightable.isLit();
                    if (lit || WARMTH_BLOCKS.contains(block.getType()) || plugin.getStations().isHeatSource(block)) {
                        player.heal(1);
                        return;
                    }
                }
            }
        }
        if (player.getWorld().getEnvironment() == World.Environment.NETHER && ThreadLocalRandom.current().nextInt(4) == 0) {
            player.heal(0.5);
        }
    }

    /**
     * SulfurEffect: Phantome in der Nähe verlieren den Spieler als Ziel.
     */
    private void sulfur(Player player) {
        for (Phantom phantom : player.getWorld().getNearbyEntitiesByType(Phantom.class, player.getLocation(), 8, 16, 8)) {
            if (player.equals(phantom.getTarget())) {
                phantom.setTarget(null);
            }
        }
    }

    /**
     * Senf: Creeper im Umkreis von 6 Blöcken fliehen (AvoidEntityGoal im Mod).
     */
    private void mustard(Player player) {
        Location location = player.getLocation();
        for (Creeper creeper : player.getWorld().getNearbyEntitiesByType(Creeper.class, location, 6)) {
            if (player.equals(creeper.getTarget())) {
                creeper.setTarget(null);
            }
            Vector away = creeper.getLocation().toVector().subtract(location.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            Location target = creeper.getLocation().add(away.normalize().multiply(7));
            creeper.getPathfinder().moveTo(target, 1.2);
        }
    }

    /**
     * Tundraläufer: schneller auf Schnee und Eis (Mod: Block-Geschwindigkeitsfaktor).
     */
    private void tundraStrider(Player player) {
        Block below = player.getLocation().subtract(0, 0.2, 0).getBlock();
        if (TUNDRA_BLOCKS.contains(below.getType()) || TUNDRA_BLOCKS.contains(player.getLocation().getBlock().getType())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, TICK_INTERVAL + 5, 0, true, false, false));
        }
    }

    // ---- Anzeige ----

    /**
     * Seitenleiste (Scoreboard) mit den aktiven Effekten: ohne Überschrift, ohne Zahlen, eine Zeile pro Effekt.
     * Solange Effekte aktiv sind, bekommt der Spieler ein eigenes Scoreboard; danach wieder das vorherige.
     */
    private void updateBar(Player player) {
        EnumMap<CookeryEffect, Active> effects = active.get(player.getUniqueId());
        Map<net.kyori.adventure.key.Key, Active> addon = addonActive.getOrDefault(player.getUniqueId(), Map.of());
        if ((effects == null || effects.isEmpty()) && addon.isEmpty()) {
            hideBar(player);
            return;
        }
        if (effects == null) {
            effects = new EnumMap<>(CookeryEffect.class);
        }
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null) {
            Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("kc_effects", Criteria.DUMMY, Component.empty());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            sidebar = new Sidebar(player.getScoreboard(), board, objective);
            sidebars.put(player.getUniqueId(), sidebar);
            player.setScoreboard(board);
        }
        record Row(Component icon, Component name, Active state) {
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<CookeryEffect, Active> entry : effects.entrySet()) {
            CookeryEffect effect = entry.getKey();
            rows.add(new Row(Component.text(String.valueOf(effect.icon())).font(CookeryEffect.FONT).color(NamedTextColor.WHITE),
                    Component.translatable(effect.translationKey()).color(effect.color()), entry.getValue()));
        }
        for (Map.Entry<net.kyori.adventure.key.Key, Active> entry : addon.entrySet()) {
            AddonEffect effect = addonEffects.get(entry.getKey());
            rows.add(new Row(effect.icon(), Component.translatable(effect.translationKey()).color(effect.color()), entry.getValue()));
        }
        int line = 0;
        for (Row row : rows) {
            int amplifier = row.state().amplifier();
            long remaining = Math.max(0, row.state().end() - tick);
            Component name = row.name();
            if (amplifier > 0) {
                name = name.append(Component.text(" " + roman(amplifier + 1)));
            }
            Component text = Component.text()
                    .append(row.icon())
                    .append(Component.text(" "))
                    .append(name)
                    .append(Component.text(" " + formatTicks(remaining), NamedTextColor.GRAY))
                    .build();
            // Unsichtbare, eindeutige Einträge; Reihenfolge über den (versteckten) Score
            Score score = sidebar.objective().getScore("\u00a7" + Integer.toHexString(line));
            score.setScore(rows.size() - line);
            score.customName(text);
            line++;
        }
        for (String entry : sidebar.board().getEntries()) {
            if (entry.length() == 2 && Integer.parseInt(entry.substring(1), 16) >= line) {
                sidebar.board().resetScores(entry);
            }
        }
    }

    private void hideBar(Player player) {
        Sidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null && player.getScoreboard() == sidebar.board()) {
            player.setScoreboard(sidebar.previous());
        }
    }

    public static String formatTicks(long ticks) {
        long seconds = ticks / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static String roman(int value) {
        return switch (value) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    // ---- Speichern ----

    private void save(Player player) {
        EnumMap<CookeryEffect, Active> effects = active.getOrDefault(player.getUniqueId(), new EnumMap<>(CookeryEffect.class));
        Map<net.kyori.adventure.key.Key, Active> addon = addonActive.getOrDefault(player.getUniqueId(), Map.of());
        if (effects.isEmpty() && addon.isEmpty()) {
            player.getPersistentDataContainer().remove(SAVED);
            return;
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<CookeryEffect, Active> entry : effects.entrySet()) {
            entries.add(entry.getKey().id() + ":" + (entry.getValue().end() - tick) + ":" + entry.getValue().amplifier());
        }
        // Addon-Effekte mit vollem Schlüssel (ns/pfad, ":" trennt die Felder)
        for (Map.Entry<net.kyori.adventure.key.Key, Active> entry : addon.entrySet()) {
            entries.add(entry.getKey().namespace() + "/" + entry.getKey().value() + ":" + (entry.getValue().end() - tick) + ":" + entry.getValue().amplifier());
        }
        player.getPersistentDataContainer().set(SAVED, PersistentDataType.STRING, String.join(";", entries));
    }

    private void load(Player player) {
        String saved = player.getPersistentDataContainer().get(SAVED, PersistentDataType.STRING);
        if (saved == null || saved.isEmpty()) {
            return;
        }
        for (String entry : saved.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            if (parts[0].contains("/")) {
                net.kyori.adventure.key.Key key = net.kyori.adventure.key.Key.key(parts[0].replace('/', ':'));
                if (addonEffects.containsKey(key)) {
                    try {
                        apply(player, key, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
                continue;
            }
            for (CookeryEffect effect : CookeryEffect.values()) {
                if (effect.id().equals(parts[0])) {
                    try {
                        apply(player, effect, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        save(event.getPlayer());
        hideBar(event.getPlayer());
        active.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Wie Vanilla-Effekte enden die Effekte mit dem Tod.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        clear(event.getPlayer());
    }

    // ---- Effekt-Verhalten über Events ----

    /**
     * Elan: Sprinten kostet keinen Hunger.
     */
    @EventHandler(ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getExhaustionReason() == EntityExhaustionEvent.ExhaustionReason.SPRINT && has(event.getEntity(), CookeryEffect.VIGOR)) {
            event.setCancelled(true);
        }
    }

    /**
     * Blähungen: Schleichen löst einen Sprung nach oben aus.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !has(player, CookeryEffect.FLATULENCE)) {
            return;
        }
        player.setVelocity(player.getVelocity().add(new Vector(0, 0.75, 0)));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 6, 0.2, 0.05, 0.2, 0.01);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LLAMA_SPIT, SoundCategory.PLAYERS, 0.8f, 0.6f);
    }

    /**
     * Sättigungsschild: Hunger fängt Schaden ab (Mod-Standard: 100 % bis 64 Schaden, 2 Erschöpfung pro Schadenspunkt,
     * nur ab 4 Hunger und nicht mit Hunger-Effekt).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !has(player, CookeryEffect.SATIATED_SHIELD)) {
            return;
        }
        DamageType type = event.getDamageSource().getDamageType();
        if (type == DamageType.OUT_OF_WORLD || type == DamageType.GENERIC_KILL || type == DamageType.STARVE) {
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.HUNGER) || player.getFoodLevel() < SHIELD_MIN_FOOD_LEVEL) {
            return;
        }
        double damage = event.getDamage();
        double reduced = Math.min(damage, 64);
        event.setDamage(damage - reduced);
        player.setExhaustion(player.getExhaustion() + Math.round(reduced * SHIELD_EXHAUSTION_PER_DAMAGE));
    }

    /**
     * Behinderung: Treffer verlangsamen das Ziel (Langsamkeit II, 5 s).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player attacker && has(attacker, CookeryEffect.HINDER)
                && event.getEntity() instanceof LivingEntity target) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }
    }

    /**
     * Projektil-Ausweichen: Projektile verfehlen, der Spieler teleportiert sich zufällig weg (kostet 10 s Effektdauer).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player player) || !has(player, CookeryEffect.PROJECTILE_DODGE)) {
            return;
        }
        event.setCancelled(true);
        randomTeleport(player);
        EnumMap<CookeryEffect, Active> effects = active.get(player.getUniqueId());
        Active a = effects.get(CookeryEffect.PROJECTILE_DODGE);
        if (a.end() - tick <= DODGE_COST) {
            remove(player, CookeryEffect.PROJECTILE_DODGE);
        } else {
            effects.put(CookeryEffect.PROJECTILE_DODGE, new Active(a.end() - DODGE_COST, a.amplifier()));
        }
    }

    private static void randomTeleport(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location origin = player.getLocation();
        for (int attempt = 0; attempt < 16; attempt++) {
            Location target = origin.clone().add((random.nextDouble() - 0.5) * 6, random.nextInt(7) - 3, (random.nextDouble() - 0.5) * 6);
            Block feet = target.getBlock();
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).isSolid()) {
                target.setY(feet.getY());
                player.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1, 1);
                player.teleport(target);
                return;
            }
        }
    }

    /**
     * Vitalität: Getötete erwachsene Tiere hinterlassen ein Jungtier; Zombies einen Baby-Zombie (5 %: ein Dorfbewohner-Kind).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !has(killer, CookeryEffect.VITALITY)) {
            return;
        }
        LivingEntity dead = event.getEntity();
        Location location = dead.getLocation();
        if (dead instanceof Zombie zombie) {
            if (!zombie.isAdult()) {
                return;
            }
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                location.getWorld().spawn(location, Villager.class, Villager::setBaby);
            } else if (dead.getType() == EntityType.ZOMBIE) {
                location.getWorld().spawn(location, Zombie.class, Zombie::setBaby);
            }
            return;
        }
        if (dead instanceof Ageable ageable && ageable.isAdult() && dead.getType().getEntityClass() != null) {
            Entity baby = location.getWorld().spawn(location, dead.getType().getEntityClass());
            if (baby instanceof Ageable babyAgeable) {
                babyAgeable.setBaby();
            }
        }
    }

    /**
     * Sofort-Schmelzen: Erze lassen bis zu (Stufe + 1) Drops geschmolzen fallen.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        int amplifier = amplifier(event.getPlayer(), CookeryEffect.INSTANT_SMELTING);
        if (amplifier < 0 || !isOre(event.getBlockState().getType())) {
            return;
        }
        int remaining = amplifier + 1;
        for (org.bukkit.entity.Item drop : event.getItems()) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = drop.getItemStack();
            ItemStack smelted = smelt(stack);
            if (smelted == null) {
                continue;
            }
            int count = Math.min(remaining, stack.getAmount());
            remaining -= count;
            ItemStack result = smelted.clone();
            result.setAmount(smelted.getAmount() * count);
            if (count >= stack.getAmount()) {
                drop.setItemStack(result);
            } else {
                stack.setAmount(stack.getAmount() - count);
                drop.setItemStack(stack);
                drop.getWorld().dropItemNaturally(drop.getLocation(), result);
            }
        }
    }

    private static boolean isOre(Material material) {
        return Tag.COAL_ORES.isTagged(material) || Tag.IRON_ORES.isTagged(material) || Tag.COPPER_ORES.isTagged(material)
                || Tag.GOLD_ORES.isTagged(material) || Tag.REDSTONE_ORES.isTagged(material) || Tag.LAPIS_ORES.isTagged(material)
                || Tag.DIAMOND_ORES.isTagged(material) || Tag.EMERALD_ORES.isTagged(material)
                || material == Material.NETHER_QUARTZ_ORE || material == Material.NETHER_GOLD_ORE || material == Material.ANCIENT_DEBRIS;
    }

    private static ItemStack smelt(ItemStack input) {
        Iterator<Recipe> recipes = org.bukkit.Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (recipes.next() instanceof FurnaceRecipe recipe && recipe.getInputChoice().test(input)) {
                return recipe.getResult();
            }
        }
        return null;
    }

    /**
     * Konservierung: schädliche Effekte von gegessenem Essen werden entfernt.
     */
    public void preserve(Player player, List<PotionEffectType> applied) {
        if (!has(player, CookeryEffect.PRESERVATION)) {
            return;
        }
        for (PotionEffectType type : applied) {
            if (type.getCategory() == PotionEffectTypeCategory.HARMFUL) {
                player.removePotionEffect(type);
            }
        }
    }
}
