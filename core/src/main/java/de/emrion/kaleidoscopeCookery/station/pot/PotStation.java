package de.emrion.kaleidoscopeCookery.station.pot;

import de.emrion.kaleidoscopeCookery.food.FoodService;
import de.emrion.kaleidoscopeCookery.food.Quality;
import de.emrion.kaleidoscopeCookery.food.QualityEvaluator;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import de.emrion.kaleidoscopeCookery.recipe.PotRecipe;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.station.stove.StoveStation;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wok (PotBlockEntity im Mod).
 * <ol>
 *     <li>Hitze darunter (brennender Herd, Lagerfeuer, Feuer ...), dann Öl hinein: 60 s Zeit für bis zu 9 Zutaten</li>
 *     <li>Mit dem Pfannenwender rühren startet das Kochen; jedes weitere Rühren zählt die nötigen Rührvorgänge herunter</li>
 *     <li>Nach der Kochzeit: nicht genug gerührt = Verdächtiges Pfannengericht, sonst das Gericht (Flex-Rezepte mit Qualität)</li>
 *     <li>40 s zum Herausnehmen (mit Gefäß bzw. Schleichen + Pfannenwender), dann 20 s Anbrennen, dann Holzkohle</li>
 * </ol>
 * Mit der Hand in den heißen Wok greifen kostet ein halbes Herz.
 */
public class PotStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "pot");
    public static final Key KITCHEN_SHOVEL = Key.key("kaleidoscope_cookery", "kitchen_shovel");
    private static final Key OIL = Key.key("kaleidoscope_cookery", "oil");
    private static final Key SUSPICIOUS_STIR_FRY = Key.key("kaleidoscope_cookery", "suspicious_stir_fry");
    private static final Key DARK_CUISINE = Key.key("kaleidoscope_cookery", "dark_cuisine");
    private static final Ingredient BOWL = new Ingredient(Set.of(Material.BOWL.key()));

    public static final int PUT_INGREDIENT = 0;
    public static final int COOKING = 1;
    public static final int FINISHED = 2;
    public static final int BURNT = 3;

    private static final int PUT_INGREDIENT_TIME = 60 * 20;
    private static final int TAKEOUT_TIME = 40 * 20;
    private static final int BURNT_TIME = 20 * 20;
    private static final int SUSPICIOUS_TIME = 10 * 20;
    private static final double DURABILITY_COST_PROBABILITY = 0.25;
    private static final int HOP_TICKS = 10;

    private static final NamespacedKey INPUTS = Keys.key("inputs");
    private static final NamespacedKey HAS_OIL = Keys.key("has_oil");
    private static final NamespacedKey SHOW_OIL = Keys.key("show_oil");
    private static final NamespacedKey STATUS = Keys.key("status");
    private static final NamespacedKey CURRENT_TICK = Keys.key("current_tick");
    private static final NamespacedKey STIR_FRY_COUNT = Keys.key("stir_fry_count");
    private static final NamespacedKey RESULT = Keys.key("result");
    private static final NamespacedKey CARRIER = Keys.key("carrier");
    private static final NamespacedKey SEED = Keys.key("seed");

    private final ItemStack[] inputs = new ItemStack[PotRecipe.SLOTS];
    private boolean hasOil;
    private boolean showOil;
    private int status = PUT_INGREDIENT;
    private int currentTick;
    private int stirFryCount;
    private @Nullable ItemStack result;
    private List<Key> carrier = List.of();
    private long seed = System.currentTimeMillis();

    // nur zur Laufzeit
    private int hopTicks;
    private float[] hopHeights = new float[PotRecipe.SLOTS];
    private boolean heatCache;
    private long heatCheckedAt = -100;

    public PotStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("pot", ITEM, true, 1f, 1f, false, PotStation::new);
    }

    // ---- Zustand ----

    @Override
    protected void load(PersistentDataContainer state) {
        Arrays.fill(inputs, null);
        List<byte[]> stored = state.getOrDefault(INPUTS, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < Math.min(stored.size(), inputs.length); i++) {
            inputs[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
        hasOil = state.getOrDefault(HAS_OIL, PersistentDataType.BOOLEAN, false);
        showOil = state.getOrDefault(SHOW_OIL, PersistentDataType.BOOLEAN, false);
        status = state.getOrDefault(STATUS, PersistentDataType.INTEGER, PUT_INGREDIENT);
        currentTick = state.getOrDefault(CURRENT_TICK, PersistentDataType.INTEGER, 0);
        stirFryCount = state.getOrDefault(STIR_FRY_COUNT, PersistentDataType.INTEGER, 0);
        byte[] resultBytes = state.get(RESULT, PersistentDataType.BYTE_ARRAY);
        result = resultBytes == null ? null : ItemStack.deserializeBytes(resultBytes);
        List<Key> carrierKeys = new ArrayList<>();
        for (String key : state.getOrDefault(CARRIER, PersistentDataType.LIST.strings(), List.of())) {
            if (Key.parseable(key)) {
                carrierKeys.add(Key.key(key));
            }
        }
        carrier = List.copyOf(carrierKeys);
        seed = state.getOrDefault(SEED, PersistentDataType.LONG, System.currentTimeMillis());
    }

    @Override
    protected void save(PersistentDataContainer state) {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack input : inputs) {
            stored.add(input == null ? new byte[0] : input.serializeAsBytes());
        }
        state.set(INPUTS, PersistentDataType.LIST.byteArrays(), stored);
        state.set(HAS_OIL, PersistentDataType.BOOLEAN, hasOil);
        state.set(SHOW_OIL, PersistentDataType.BOOLEAN, showOil);
        state.set(STATUS, PersistentDataType.INTEGER, status);
        state.set(CURRENT_TICK, PersistentDataType.INTEGER, currentTick);
        state.set(STIR_FRY_COUNT, PersistentDataType.INTEGER, stirFryCount);
        if (result != null) {
            state.set(RESULT, PersistentDataType.BYTE_ARRAY, result.serializeAsBytes());
        }
        state.set(CARRIER, PersistentDataType.LIST.strings(), carrier.stream().map(Key::asString).toList());
        state.set(SEED, PersistentDataType.LONG, seed);
    }

    private boolean isEmpty() {
        for (ItemStack input : inputs) {
            if (input != null) {
                return false;
            }
        }
        return true;
    }

    private int inputCount() {
        int count = 0;
        for (ItemStack input : inputs) {
            if (input != null) {
                count++;
            }
        }
        return count;
    }

    // ---- Hitze ----

    public boolean hasHeatSource() {
        long now = context.manager().serverTick();
        if (now - heatCheckedAt >= 10) {
            heatCheckedAt = now;
            Block below = world().getBlockAt(position().x(), position().y() - 1, position().z());
            heatCache = context.manager().isHeatSource(below);
        }
        return heatCache;
    }

    /**
     * Blockmodell mit Standfuß, wenn der Wok nicht auf einem vollen Block (z. B. Herd) steht.
     */
    private boolean hasBase() {
        Block below = world().getBlockAt(position().x(), position().y() - 1, position().z());
        if (context.manager().at(below) instanceof StoveStation) {
            return false;
        }
        return !below.getType().isOccluding();
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return hasOil || status != PUT_INGREDIENT || hopTicks > 0;
    }

    @Override
    public void tick(long serverTick) {
        if (hopTicks > 0) {
            hopTicks--;
            if (hopTicks == HOP_TICKS / 2) {
                updateDisplay();
            }
            if (hopTicks == 0) {
                context.manager().updateTicking(this);
            }
        }
        if (!hasOil && status == PUT_INGREDIENT) {
            return;
        }
        if (!hasHeatSource()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (currentTick > 0) {
            currentTick--;
            markDirty();
            if (currentTick % 20 == 0) {
                world().playSound(center(), Sound.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5f + random.nextFloat() / 0.5f, 0.8f + random.nextFloat() / 0.5f);
            }
        }
        switch (status) {
            case PUT_INGREDIENT -> tickPutIngredient(random);
            case COOKING -> tickCooking(random);
            case FINISHED -> tickFinished(random);
            case BURNT -> tickBurnt(random);
            default -> {
            }
        }
    }

    private void tickPutIngredient(ThreadLocalRandom random) {
        if (currentTick % 10 == 0) {
            cookingParticle(random, 5, 1, 0);
        }
        if (currentTick == 0) {
            if (isEmpty()) {
                reset();
                world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1, (random.nextFloat() - random.nextFloat()) * 0.8f);
                cookingParticle(random, 3, 8, 0.05);
            } else {
                startCooking();
            }
        }
    }

    private void tickCooking(ThreadLocalRandom random) {
        if (currentTick == 0) {
            world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1, (random.nextFloat() - random.nextFloat()) * 0.8f);
            status = FINISHED;
            if (stirFryCount > 0) {
                result = items().create(SUSPICIOUS_STIR_FRY, 1);
                carrier = List.of(Material.BOWL.key());
            }
            currentTick = TAKEOUT_TIME;
            showOil = false;
            markDirty();
            updateDisplay();
        }
    }

    private void tickFinished(ThreadLocalRandom random) {
        if (currentTick % 10 == 0) {
            world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5, position().y() + 0.1 + random.nextDouble() / 2, position().z() + 0.5, 1, 0, 0, 0, 0);
        }
        if (currentTick == 0) {
            status = BURNT;
            currentTick = BURNT_TIME;
            markDirty();
            updateDisplay();
        }
    }

    private void tickBurnt(ThreadLocalRandom random) {
        int particleCount = 10 - currentTick / 5;
        if (currentTick % 2 == 0 && particleCount > 0) {
            world().spawnParticle(Particle.SMOKE, position().x() + 0.5 + offset(random, 3), position().y() + 0.25 + random.nextDouble() / 3,
                    position().z() + 0.5 + offset(random, 3), particleCount, 0, 0, 0, 0.05);
        }
        if (currentTick % 25 == 0) {
            updateDisplay(); // Zutaten werden dunkler
        }
        if (currentTick == 0) {
            reset();
            world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1, (random.nextFloat() - random.nextFloat()) * 0.8f);
            world().spawnParticle(Particle.SMOKE, position().x() + 0.5, position().y() + 0.4, position().z() + 0.5, 8, 0.2, 0.1, 0.2, 0.05);
            ItemStack charcoal = new ItemStack(Material.CHARCOAL, 1 + random.nextInt(3));
            world().dropItemNaturally(center(), charcoal);
        }
    }

    private void cookingParticle(ThreadLocalRandom random, int spread, int count, double speed) {
        world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + offset(random, spread), position().y() + 0.1 + random.nextDouble() / 3,
                position().z() + 0.5 + offset(random, spread), count, 0, 0, 0, speed);
    }

    private static double offset(ThreadLocalRandom random, int divisor) {
        return random.nextDouble() / divisor * (random.nextBoolean() ? 1 : -1);
    }

    // ---- Bedienung ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        // Zutat zurücknehmen
        if ((held.isEmpty() || isIngredientContainer(held)) && removeIngredient(player)) {
            return true;
        }
        // Gericht herausnehmen
        if (takeOutProduct(player, held)) {
            return true;
        }
        if (!hasHeatSource()) {
            actionBar(player, "tip.kaleidoscope_cookery.pot.need_lit_stove");
            return false;
        }
        if (!hasOil) {
            if (placeOil(player, held)) {
                return true;
            }
            actionBar(player, "tip.kaleidoscope_cookery.pot.need_oil");
            return false;
        }
        if (KITCHEN_SHOVEL.equals(CookeryItems.idOf(held))) {
            if (player.getGameMode() != GameMode.CREATIVE && ThreadLocalRandom.current().nextDouble() < DURABILITY_COST_PROBABILITY) {
                player.damageItemStack(EquipmentSlot.HAND, 1);
            }
            onShovelHit();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1 + (random.nextFloat() - random.nextFloat()) * 0.8f);
            return true;
        }
        return addIngredient(player, held);
    }

    private boolean placeOil(Player player, ItemStack held) {
        // Öl, Pfannenwender mit Öl oder Ölkanne (PotBlockEntity#onPlaceOil)
        if (!OilItems.takeOne(held, player.getGameMode() == GameMode.CREATIVE)) {
            return false;
        }
        currentTick = PUT_INGREDIENT_TIME;
        hasOil = true;
        showOil = true;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        world().playSound(center(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1, (random.nextFloat() - random.nextFloat()) * 0.8f);
        world().spawnParticle(Particle.SMOKE, position().x() + 0.5, position().y() + 0.4, position().z() + 0.5, 10, 0.2, 0.1, 0.2, 0.05);
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
        return true;
    }

    private void onShovelHit() {
        seed = System.currentTimeMillis();
        startHop();
        cookingParticle(ThreadLocalRandom.current(), 3, 1, 0.05);
        if (status == PUT_INGREDIENT && !isEmpty()) {
            startCooking();
        }
        if (status == COOKING && stirFryCount > 0) {
            stirFryCount--;
            markDirty();
        }
    }

    private void startHop() {
        Random random = new Random(seed);
        for (int i = 0; i < hopHeights.length; i++) {
            hopHeights[i] = 0.25f + random.nextFloat();
        }
        hopTicks = HOP_TICKS;
        updateDisplay();
        context.manager().updateTicking(this);
    }

    private void startCooking() {
        List<ItemStack> inputList = inputList();
        PotRecipe recipe = plugin().getRecipes().findPot(inputList);
        if (recipe != null) {
            ItemStack created = items().create(recipe.result().item(), recipe.match(inputList));
            if (created != null) {
                if (recipe.flex()) {
                    Quality quality = QualityEvaluator.evaluate(inputList, recipe.ingredients(), recipe.id(), world().getSeed());
                    plugin().getFoodService().setQuality(created, quality);
                }
                result = created;
                carrier = recipe.carrier() == null ? List.of() : List.copyOf(recipe.carrier().items());
                currentTick = recipe.time();
                stirFryCount = recipe.stirFryCount();
                status = COOKING;
                markDirty();
                updateDisplay();
                return;
            }
        }
        // Kein Rezept: verdächtiges Pfannengericht
        result = items().create(SUSPICIOUS_STIR_FRY, 1);
        carrier = List.of(Material.BOWL.key());
        currentTick = SUSPICIOUS_TIME;
        stirFryCount = 0;
        status = COOKING;
        markDirty();
        updateDisplay();
    }

    // ---- Rezept-Item (RecipeItem im Mod) ----

    /**
     * Zutaten zum Aufzeichnen (nur solange man Zutaten einlegt), sonst leer.
     */
    public List<ItemStack> recordableInputs() {
        List<ItemStack> list = new ArrayList<>();
        if (status == PUT_INGREDIENT) {
            for (ItemStack input : inputs) {
                if (input != null) {
                    list.add(input.clone());
                }
            }
        }
        return list;
    }

    /**
     * Was aus den aktuellen Zutaten würde (RecipeItem#getPotRecipeResult), mit Qualität bei Flex-Rezepten.
     *
     * @return Ergebnis und ob es ein Flex-Rezept ist
     */
    public java.util.Map.Entry<ItemStack, Boolean> previewResult() {
        List<ItemStack> inputList = inputList();
        PotRecipe recipe = plugin().getRecipes().findPot(inputList);
        ItemStack created = recipe == null ? null : items().create(recipe.result().item(), recipe.match(inputList));
        if (recipe == null || created == null) {
            ItemStack suspicious = items().create(SUSPICIOUS_STIR_FRY, 1);
            return java.util.Map.entry(suspicious == null ? new ItemStack(Material.BOWL) : suspicious, false);
        }
        if (recipe.flex()) {
            plugin().getFoodService().setQuality(created, QualityEvaluator.evaluate(inputList, recipe.ingredients(), recipe.id(), world().getSeed()));
        }
        return java.util.Map.entry(created, recipe.flex());
    }

    /**
     * Leerer, geölter Wok, in den man ein aufgezeichnetes Rezept einlegen kann.
     */
    public boolean canReceiveRecipe() {
        return status == PUT_INGREDIENT && hasOil && isEmpty();
    }

    /**
     * PotBlockEntity#addAllIngredients
     */
    public void addAllIngredients(List<ItemStack> ingredients, Player player) {
        if (status != PUT_INGREDIENT) {
            return;
        }
        for (int i = 0; i < Math.min(ingredients.size(), inputs.length); i++) {
            ItemStack stack = ingredients.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Material container = CookeryItems.isCustom(stack) ? null : stack.getType().getCraftingRemainingItem();
            if (container != null && container.isItem() && !container.isAir()) {
                give(player, new ItemStack(container));
            }
            inputs[i] = stack.asOne();
        }
        world().playSound(center(), Sound.BLOCK_LANTERN_PLACE, SoundCategory.BLOCKS, 1, 0.5f);
        markDirty();
        updateDisplay();
    }

    private List<ItemStack> inputList() {
        List<ItemStack> list = new ArrayList<>(inputs.length);
        for (ItemStack input : inputs) {
            list.add(input == null ? ItemStack.empty() : input);
        }
        return list;
    }

    private boolean addIngredient(Player player, ItemStack held) {
        if (status != PUT_INGREDIENT || held.isEmpty() || isBlocked(held)) {
            return false;
        }
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) {
                Material container = held.getType().getCraftingRemainingItem();
                if (container != null && container.isItem() && !container.isAir() && !CookeryItems.isCustom(held)) {
                    give(player, new ItemStack(container));
                }
                inputs[i] = held.asOne();
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                }
                world().playSound(center(), Sound.BLOCK_LANTERN_PLACE, SoundCategory.BLOCKS, 1, 0.5f);
                markDirty();
                updateDisplay();
                return true;
            }
        }
        return false;
    }

    private boolean removeIngredient(Player player) {
        if (status != PUT_INGREDIENT) {
            return false;
        }
        for (int i = inputs.length - 1; i >= 0; i--) {
            ItemStack stack = inputs[i];
            if (stack == null) {
                continue;
            }
            Material container = CookeryItems.isCustom(stack) ? null : stack.getType().getCraftingRemainingItem();
            if (container != null && !container.isAir()) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != container || CookeryItems.isCustom(held)) {
                    player.sendMessage(Lang.raw("tip.kaleidoscope_cookery.kitchen.remove_ingredient.need_container", Component.translatable(container.translationKey())));
                    return false;
                }
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                }
            }
            inputs[i] = null;
            give(player, stack);
            burnHand(player);
            markDirty();
            updateDisplay();
            return true;
        }
        return false;
    }

    private boolean takeOutProduct(Player player, ItemStack held) {
        if (status != FINISHED && status != BURNT) {
            return false;
        }
        ItemStack product = status == FINISHED && result != null ? result.clone() : items().create(DARK_CUISINE, 1);
        if (product == null) {
            return false;
        }
        boolean shovel = KITCHEN_SHOVEL.equals(CookeryItems.idOf(held));
        if (!carrier.isEmpty()) {
            Ingredient carrierIngredient = new Ingredient(Set.copyOf(carrier));
            Component carrierName = carrierName();
            if (carrierIngredient.test(held)) {
                if (held.getAmount() < product.getAmount()) {
                    actionBar(player, "tip.kaleidoscope_cookery.pot.carrier_count_not_enough", product.getAmount(), carrierName);
                    return false;
                }
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract(product.getAmount());
                }
                finishTakeOut(player, product);
                return true;
            }
            if (!shovel) {
                burnHand(player);
                actionBar(player, "tip.kaleidoscope_cookery.pot.need_carrier", carrierName);
            }
            return false;
        }
        if (shovel && player.isSneaking()) {
            finishTakeOut(player, product);
            return true;
        }
        if (!shovel) {
            burnHand(player);
            actionBar(player, "tip.kaleidoscope_cookery.pot.need_kitchen_shovel");
        }
        return false;
    }

    private void finishTakeOut(Player player, ItemStack product) {
        if (status == FINISHED) {
            // Addons können das Gericht anpassen (Grilling: frisch aus dem Wok ist es heiß)
            new de.emrion.kaleidoscopeCookery.api.DishServedEvent(player, this, product).callEvent();
        }
        give(player, product);
        reset();
    }

    private Component carrierName() {
        Key first = carrier.getFirst();
        ItemStack stack = items().create(first, 1);
        return stack == null ? Component.text(first.asString()) : stack.effectiveName();
    }

    private void burnHand(Player player) {
        if (hasHeatSource()) {
            player.damage(1, DamageSource.builder(DamageType.IN_FIRE).build());
        }
    }

    private static boolean isIngredientContainer(ItemStack stack) {
        return !CookeryItems.isCustom(stack) && (stack.getType() == Material.BOWL || stack.getType() == Material.BUCKET || stack.getType() == Material.GLASS_BOTTLE);
    }

    /**
     * ingredient_blocklist im Mod: Werkzeuge, Rüstung, Shulkerkisten, Bündel, Eimer, Schüssel, Flasche.
     */
    private static boolean isBlocked(ItemStack stack) {
        Material type = stack.getType();
        if (stack.getType().getMaxDurability() > 0 || type.name().endsWith("SHULKER_BOX") || type.name().endsWith("BUNDLE")
                || type.name().endsWith("BUCKET") || type == Material.BOWL || type == Material.GLASS_BOTTLE) {
            return !CookeryItems.isCustom(stack) || stack.getType().getMaxDurability() > 0;
        }
        return false;
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    private void actionBar(Player player, String key, Object... args) {
        context.manager().actionBar(player, Lang.raw(key, args));
    }

    private void reset() {
        Arrays.fill(inputs, null);
        result = null;
        carrier = List.of();
        status = PUT_INGREDIENT;
        currentTick = 0;
        stirFryCount = 0;
        hasOil = false;
        showOil = false;
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
    }

    // ---- Darstellung ----

    @Override
    public void updateDisplay() {
        boolean base = hasBase();
        String model = (base ? "block/pot_base" : "block/pot") + (hasOil && showOil ? "_has_oil" : "");
        showPart("body", Key.key("kaleidoscope_cookery", model), blockTransform(0));

        boolean showInputs = status != FINISHED && status != BURNT;
        List<ItemStack> shown = new ArrayList<>();
        if (showInputs || !carrier.isEmpty()) {
            for (ItemStack input : inputs) {
                if (input != null) {
                    shown.add(input);
                }
            }
        } else if (result != null) {
            shown.add(result);
        }
        Display.Brightness brightness = status == BURNT ? burntBrightness() : null;
        Random random = new Random(seed);
        boolean hopping = hopTicks > HOP_TICKS / 2;
        for (int i = 0; i < shown.size(); i++) {
            int spin = 90 + random.nextInt(90);
            showItemPart("input_" + i, shown.get(i), itemTransform(i, spin, hopping), HOP_TICKS / 2, brightness);
        }
        hidePartsStartingWith("input_", shown.size());
    }

    /**
     * Wie PotBlockEntityRender: Zutaten flach im Wok, gestapelt und gedreht; beim Rühren springen sie hoch und drehen sich.
     */
    private Transformation itemTransform(int index, int spin, boolean hopping) {
        // 0.025 im mit 0.5 skalierten Renderer-Raum = 0.0125 Blöcke pro Zutat
        float y = -0.4f + index * 0.0125f;
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-facing().dataValue() * 90))
                .rotateX((float) Math.toRadians(-90))
                .rotateZ((float) Math.toRadians(-index * spin));
        if (hopping) {
            y += hopHeights[index] * 0.5f;
            rotation.rotateX((float) Math.toRadians(-180));
        }
        return new Transformation(new Vector3f(0, y, 0), rotation, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf());
    }

    private Display.Brightness burntBrightness() {
        int level = Math.clamp(currentTick / 25, 0, 15);
        return new Display.Brightness(level / 2, level);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        if (status == PUT_INGREDIENT || status == COOKING) {
            for (ItemStack input : inputs) {
                if (input != null) {
                    contents.add(input);
                }
            }
        }
        return contents;
    }

    @Override
    public String describe() {
        return "Wok (Status " + status + ", " + inputCount() + " Zutaten, Rest " + currentTick + " Ticks, Rühren " + stirFryCount + ")";
    }

    // ---- Statusanzeige ----

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        if (!hasHeatSource()) {
            state = Lang.raw("hud.kaleidoscope_grilling.pot.state.need_heat").color(NamedTextColor.RED);
        } else if (!hasOil) {
            state = Lang.raw("hud.kaleidoscope_grilling.pot.state.need_oil").color(NamedTextColor.GOLD);
        } else if (status == PUT_INGREDIENT) {
            state = Lang.get("hud.pot.add", inputCount(), PotRecipe.SLOTS, seconds(currentTick)).color(NamedTextColor.YELLOW);
        } else if (status == COOKING) {
            state = stirFryCount > 0
                    ? Lang.get("hud.pot.stir", stirFryCount, seconds(currentTick)).color(NamedTextColor.YELLOW)
                    : Lang.get("hud.pot.cooking", seconds(currentTick)).color(NamedTextColor.GREEN);
        } else if (status == FINISHED) {
            state = Lang.get("hud.pot.done", seconds(currentTick)).color(NamedTextColor.GREEN);
        } else {
            state = Lang.get("hud.pot.burnt", seconds(currentTick)).color(NamedTextColor.DARK_RED);
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.pot").color(NamedTextColor.WHITE)).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(state).build();
    }

    private static String seconds(int ticks) {
        return String.valueOf((ticks + 19) / 20);
    }
}
