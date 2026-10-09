package de.emrion.kaleidoscopeCookery.station.stockpot;

import de.emrion.kaleidoscopeCookery.food.Quality;
import de.emrion.kaleidoscopeCookery.food.QualityEvaluator;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.Ingredient;
import de.emrion.kaleidoscopeCookery.recipe.StockpotRecipe;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.stove.StoveStation;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
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
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Suppentopf (StockpotBlockEntity im Mod).
 * <ol>
 *     <li>Brühe hinein (Wasser-, Lava-, Milch- oder Fischeimer), dann bis zu 9 Zutaten</li>
 *     <li>Deckel drauf: auf einer Hitzequelle kocht die Suppe (Standard 15 s)</li>
 *     <li>Deckel ab, mit Schüssel (je nach Rezept) portionsweise herausnehmen – bis zu 9 Portionen</li>
 * </ol>
 * Ohne Rezept wird es ein Verdächtiger Eintopf. Anbrennen kann nichts.
 */
public class StockpotStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "stockpot");
    public static final Key LID = Key.key("kaleidoscope_cookery", "stockpot_lid");

    public static final int PUT_SOUP_BASE = 0;
    public static final int PUT_INGREDIENT = 1;
    public static final int COOKING = 2;
    public static final int FINISHED = 3;
    private static final int MAX_TAKEOUT_COUNT = 9;
    private static final float SOUP_HEIGHT = 0.38f;

    /** Brühen: Eimer -> Key und Farbe der Blasen (ModSoupBases) */
    private record SoupBase(Key key, Material bucket, int color, Key surface, @Nullable Material floating) {
    }

    private static final List<SoupBase> SOUP_BASES = List.of(
            new SoupBase(Key.key("minecraft", "water"), Material.WATER_BUCKET, 0x3F76E4, surface("water"), null),
            new SoupBase(Key.key("minecraft", "lava"), Material.LAVA_BUCKET, 0xFF9838, surface("lava"), null),
            new SoupBase(Key.key("minecraft", "milk"), Material.MILK_BUCKET, 0xFFF4D6, surface("milk"), null),
            new SoupBase(Key.key("minecraft", "axolotl_bucket"), Material.AXOLOTL_BUCKET, 0x3F76E4, surface("water"), Material.AXOLOTL_BUCKET),
            new SoupBase(Key.key("minecraft", "cod_bucket"), Material.COD_BUCKET, 0x3F76E4, surface("water"), Material.COD),
            new SoupBase(Key.key("minecraft", "salmon_bucket"), Material.SALMON_BUCKET, 0x3F76E4, surface("water"), Material.SALMON),
            new SoupBase(Key.key("minecraft", "tropical_fish_bucket"), Material.TROPICAL_FISH_BUCKET, 0x3F76E4, surface("water"), Material.TROPICAL_FISH),
            new SoupBase(Key.key("minecraft", "pufferfish_bucket"), Material.PUFFERFISH_BUCKET, 0x3F76E4, surface("water"), Material.PUFFERFISH),
            new SoupBase(Key.key("minecraft", "tadpole_bucket"), Material.TADPOLE_BUCKET, 0x3F76E4, surface("water"), Material.FROGSPAWN));

    private static final NamespacedKey INPUTS = Keys.key("inputs");
    private static final NamespacedKey SOUP = Keys.key("soup_base");
    private static final NamespacedKey STATUS = Keys.key("status");
    private static final NamespacedKey CURRENT_TICK = Keys.key("current_tick");
    private static final NamespacedKey TAKEOUT = Keys.key("takeout");
    private static final NamespacedKey RESULT = Keys.key("result");
    private static final NamespacedKey LID_ITEM = Keys.key("lid");
    private static final NamespacedKey RECIPE = Keys.key("recipe");
    private static final NamespacedKey CARRIER = Keys.key("carrier");
    private static final NamespacedKey VISUALS = Keys.key("visuals");

    private final ItemStack[] inputs = new ItemStack[StockpotRecipe.SLOTS];
    private Key soupBase = Key.key("minecraft", "water");
    private int status = PUT_SOUP_BASE;
    private int currentTick = -1;
    private int takeoutCount;
    private @Nullable ItemStack result;
    private @Nullable ItemStack lid;
    private @Nullable String recipeId;
    /** Gefäß zum Herausnehmen; leer = keins nötig */
    private List<Key> carrier = List.of(Material.BOWL.key());
    private Key cookingTexture = StockpotRecipe.DEFAULT_COOKING_TEXTURE;
    private Key finishedTexture = StockpotRecipe.DEFAULT_FINISHED_TEXTURE;
    private int cookingColor = StockpotRecipe.DEFAULT_COOKING_COLOR;
    private int finishedColor = StockpotRecipe.DEFAULT_FINISHED_COLOR;

    private boolean heatCache;
    private long heatCheckedAt = -100;

    public StockpotStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("stockpot", ITEM, true, 1f, 1f, false, StockpotStation::new);
    }

    private static Key surface(String name) {
        return Key.key("kaleidoscope", "surface/" + name);
    }

    // ---- Zustand ----

    @Override
    protected void load(PersistentDataContainer state) {
        Arrays.fill(inputs, null);
        List<byte[]> stored = state.getOrDefault(INPUTS, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < Math.min(stored.size(), inputs.length); i++) {
            inputs[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
        soupBase = key(state.get(SOUP, PersistentDataType.STRING), Key.key("minecraft", "water"));
        status = state.getOrDefault(STATUS, PersistentDataType.INTEGER, PUT_SOUP_BASE);
        currentTick = state.getOrDefault(CURRENT_TICK, PersistentDataType.INTEGER, -1);
        takeoutCount = state.getOrDefault(TAKEOUT, PersistentDataType.INTEGER, 0);
        byte[] resultBytes = state.get(RESULT, PersistentDataType.BYTE_ARRAY);
        result = resultBytes == null ? null : ItemStack.deserializeBytes(resultBytes);
        byte[] lidBytes = state.get(LID_ITEM, PersistentDataType.BYTE_ARRAY);
        lid = lidBytes == null ? null : ItemStack.deserializeBytes(lidBytes);
        recipeId = state.get(RECIPE, PersistentDataType.STRING);
        List<Key> carrierKeys = new ArrayList<>();
        for (String value : state.getOrDefault(CARRIER, PersistentDataType.LIST.strings(), List.of(Material.BOWL.key().asString()))) {
            if (Key.parseable(value)) {
                carrierKeys.add(Key.key(value));
            }
        }
        carrier = List.copyOf(carrierKeys);
        List<String> visuals = state.getOrDefault(VISUALS, PersistentDataType.LIST.strings(), List.of());
        if (visuals.size() == 4) {
            cookingTexture = key(visuals.get(0), StockpotRecipe.DEFAULT_COOKING_TEXTURE);
            finishedTexture = key(visuals.get(1), StockpotRecipe.DEFAULT_FINISHED_TEXTURE);
            cookingColor = Integer.parseInt(visuals.get(2));
            finishedColor = Integer.parseInt(visuals.get(3));
        }
    }

    private static Key key(@Nullable String value, Key fallback) {
        return value != null && Key.parseable(value) ? Key.key(value) : fallback;
    }

    @Override
    protected void save(PersistentDataContainer state) {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack input : inputs) {
            stored.add(input == null ? new byte[0] : input.serializeAsBytes());
        }
        state.set(INPUTS, PersistentDataType.LIST.byteArrays(), stored);
        state.set(SOUP, PersistentDataType.STRING, soupBase.asString());
        state.set(STATUS, PersistentDataType.INTEGER, status);
        state.set(CURRENT_TICK, PersistentDataType.INTEGER, currentTick);
        state.set(TAKEOUT, PersistentDataType.INTEGER, takeoutCount);
        if (result != null) {
            state.set(RESULT, PersistentDataType.BYTE_ARRAY, result.serializeAsBytes());
        }
        if (lid != null) {
            state.set(LID_ITEM, PersistentDataType.BYTE_ARRAY, lid.serializeAsBytes());
        }
        if (recipeId != null) {
            state.set(RECIPE, PersistentDataType.STRING, recipeId);
        }
        state.set(CARRIER, PersistentDataType.LIST.strings(), carrier.stream().map(Key::asString).toList());
        state.set(VISUALS, PersistentDataType.LIST.strings(), List.of(cookingTexture.asString(), finishedTexture.asString(),
                String.valueOf(cookingColor), String.valueOf(finishedColor)));
    }

    private boolean hasLid() {
        return lid != null;
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

    private @Nullable SoupBase soup() {
        for (SoupBase base : SOUP_BASES) {
            if (base.key().equals(soupBase)) {
                return base;
            }
        }
        return null;
    }

    public boolean hasHeatSource() {
        long now = context.manager().serverTick();
        if (now - heatCheckedAt >= 10) {
            heatCheckedAt = now;
            heatCache = context.manager().isHeatSource(world().getBlockAt(position().x(), position().y() - 1, position().z()));
        }
        return heatCache;
    }

    // ---- Tick ----

    @Override
    public boolean isTicking() {
        return status != PUT_SOUP_BASE;
    }

    @Override
    public void tick(long serverTick) {
        if (!hasHeatSource()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (serverTick % 15 == 0) {
            float volume = hasLid() ? 0.075f : 0.2f;
            float pitch = hasLid() ? 0.1f + random.nextFloat() * 0.05f : 1 + random.nextFloat() * 0.1f;
            world().playSound(center(), "kaleidoscope_cookery:block.stockpot", SoundCategory.BLOCKS, volume, pitch);
        }
        if (!hasLid()) {
            // Offener Topf: Blasen in der Farbe der Suppe, es kocht nichts
            if (random.nextFloat() < 0.25f) {
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(bubbleColor()), 1);
                world().spawnParticle(Particle.DUST, position().x() + 0.25 + random.nextFloat() * 0.5, position().y() + 0.375,
                        position().z() + 0.25 + random.nextFloat() * 0.5, 2, 0.05, 0, 0.05, 0, dust);
            }
            return;
        }
        if (random.nextFloat() < 0.05f) {
            world().spawnParticle(Particle.WHITE_SMOKE, position().x() + 0.5 + offset(random), position().y() + 0.375 + random.nextDouble() / 3,
                    position().z() + 0.5 + offset(random), 1, 0, 0, 0, 0.05);
        }
        if (status == PUT_INGREDIENT && serverTick % 5 == 0 && !isEmpty()) {
            setRecipe();
            status = COOKING;
            markDirty();
            updateDisplay();
            return;
        }
        if (status == COOKING) {
            if (currentTick > 0) {
                currentTick--;
                markDirty();
                return;
            }
            status = FINISHED;
            currentTick = -1;
            Arrays.fill(inputs, null);
            markDirty();
            updateDisplay();
        }
    }

    private static double offset(ThreadLocalRandom random) {
        return random.nextDouble() / 3 * (random.nextBoolean() ? 1 : -1);
    }

    private int bubbleColor() {
        if (status == COOKING) {
            return cookingColor;
        }
        if (status == FINISHED) {
            return finishedColor;
        }
        SoupBase soup = soup();
        return soup == null ? 0xFFFFFF : soup.color();
    }

    private void setRecipe() {
        List<ItemStack> inputList = new ArrayList<>();
        for (ItemStack input : inputs) {
            inputList.add(input == null ? ItemStack.empty() : input);
        }
        StockpotRecipe recipe = plugin().getRecipes().findStockpot(inputList, soupBase);
        ItemStack created = recipe == null ? null : items().create(recipe.result().item(), recipe.result().count());
        if (recipe == null || created == null) {
            recipeId = null;
            result = new ItemStack(Material.SUSPICIOUS_STEW);
            currentTick = StockpotRecipe.DEFAULT_TIME;
            takeoutCount = 1;
            carrier = List.of(Material.BOWL.key());
            cookingTexture = StockpotRecipe.DEFAULT_COOKING_TEXTURE;
            finishedTexture = StockpotRecipe.DEFAULT_FINISHED_TEXTURE;
            cookingColor = StockpotRecipe.DEFAULT_COOKING_COLOR;
            finishedColor = StockpotRecipe.DEFAULT_FINISHED_COLOR;
            return;
        }
        if (recipe.flex()) {
            Quality quality = QualityEvaluator.evaluate(inputList, recipe.ingredients(), recipe.id(), world().getSeed());
            plugin().getFoodService().setQuality(created, quality);
        }
        recipeId = recipe.id();
        result = created;
        currentTick = recipe.time();
        takeoutCount = Math.min(created.getAmount(), MAX_TAKEOUT_COUNT);
        carrier = recipe.carrier() == null ? List.of() : List.copyOf(recipe.carrier().items());
        cookingTexture = recipe.cookingTexture();
        finishedTexture = recipe.finishedTexture();
        cookingColor = recipe.cookingColor();
        finishedColor = recipe.finishedColor();
    }

    // ---- Bedienung (Reihenfolge wie StockpotBlock#useItemOn) ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (onLidClick(player, held)) {
            return true;
        }
        if (addSoupBase(player, held)) {
            return true;
        }
        if (removeSoupBase(player, held)) {
            return true;
        }
        if (!held.isEmpty() && addIngredient(player, held)) {
            return true;
        }
        if ((held.isEmpty() || isContainer(held)) && removeIngredient(player)) {
            return true;
        }
        return takeOutProduct(player, held);
    }

    private boolean onLidClick(Player player, ItemStack held) {
        if (!hasLid() && LID.equals(CookeryItems.idOf(held))) {
            lid = held.asOne();
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract();
            }
            world().playSound(center(), Sound.BLOCK_LANTERN_PLACE, SoundCategory.BLOCKS, 0.5f, 0.5f);
            markDirty();
            updateDisplay();
            return true;
        }
        if (hasLid()) {
            ItemStack removed = lid;
            lid = null;
            if (held.isEmpty()) {
                player.getInventory().setItemInMainHand(removed);
            } else {
                world().dropItemNaturally(center().add(0, 0.5, 0), removed);
            }
            world().playSound(center(), Sound.BLOCK_LANTERN_BREAK, SoundCategory.BLOCKS, 0.5f, 0.5f);
            markDirty();
            updateDisplay();
            return true;
        }
        return false;
    }

    private boolean addSoupBase(Player player, ItemStack held) {
        if (hasLid() || status != PUT_SOUP_BASE || CookeryItems.isCustom(held)) {
            return false;
        }
        for (SoupBase base : SOUP_BASES) {
            if (held.getType() == base.bucket()) {
                soupBase = base.key();
                status = PUT_INGREDIENT;
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                    give(player, new ItemStack(Material.BUCKET));
                }
                world().playSound(center(), base.bucket() == Material.LAVA_BUCKET ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY,
                        SoundCategory.BLOCKS, 1, 1);
                markDirty();
                updateDisplay();
                context.manager().updateTicking(this);
                return true;
            }
        }
        return false;
    }

    private boolean removeSoupBase(Player player, ItemStack held) {
        SoupBase base = soup();
        if (status != PUT_INGREDIENT || !isEmpty() || base == null || held.getType() != Material.BUCKET || CookeryItems.isCustom(held)) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        give(player, new ItemStack(base.bucket()));
        world().playSound(center(), base.bucket() == Material.LAVA_BUCKET ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1, 1);
        soupBase = Key.key("minecraft", "water");
        status = PUT_SOUP_BASE;
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
        return true;
    }

    private boolean addIngredient(Player player, ItemStack held) {
        if (hasLid() || status != PUT_INGREDIENT || isBlocked(held)) {
            return false;
        }
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                continue;
            }
            Material container = CookeryItems.isCustom(held) ? null : held.getType().getCraftingRemainingItem();
            if (container != null && !container.isAir()) {
                give(player, new ItemStack(container));
            }
            inputs[i] = held.asOne();
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract();
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            world().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f,
                    ((random.nextFloat() - random.nextFloat()) * 0.7f + 1) * 2);
            markDirty();
            updateDisplay();
            return true;
        }
        return false;
    }

    private boolean removeIngredient(Player player) {
        if (hasLid() || status != PUT_INGREDIENT) {
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
            // Lava ist heiß (Temperatur > 500 im Mod)
            if (soupBase.value().equals("lava")) {
                player.damage(1, DamageSource.builder(DamageType.IN_FIRE).build());
            }
            markDirty();
            updateDisplay();
            return true;
        }
        return false;
    }

    private boolean takeOutProduct(Player player, ItemStack held) {
        if (hasLid() || status != FINISHED || result == null || takeoutCount <= 0) {
            return false;
        }
        if (!carrier.isEmpty()) {
            Ingredient carrierIngredient = new Ingredient(Set.copyOf(carrier));
            if (!carrierIngredient.test(held)) {
                ItemStack carrierStack = items().create(carrier.getFirst(), 1);
                Component name = carrierStack == null ? Component.text(carrier.getFirst().asString()) : carrierStack.effectiveName();
                context.manager().actionBar(player, Lang.raw("tip.kaleidoscope_cookery.pot.need_carrier", name));
                return false;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract();
            }
        }
        ItemStack portion = result.asOne();
        plugin().getFoodService().makeHot(portion, world(), plugin().getCookeryConfig().food().stockpotHotSeconds());
        give(player, portion);
        takeoutCount--;
        if (takeoutCount <= 0) {
            status = PUT_SOUP_BASE;
            Arrays.fill(inputs, null);
            recipeId = null;
            soupBase = Key.key("minecraft", "water");
            result = null;
            currentTick = -1;
            context.manager().updateTicking(this);
        }
        markDirty();
        updateDisplay();
        return true;
    }

    private static boolean isContainer(ItemStack stack) {
        return !CookeryItems.isCustom(stack) && (stack.getType() == Material.BOWL || stack.getType() == Material.BUCKET || stack.getType() == Material.GLASS_BOTTLE);
    }

    private static boolean isBlocked(ItemStack stack) {
        Material type = stack.getType();
        if (type.getMaxDurability() > 0) {
            return true;
        }
        if (CookeryItems.isCustom(stack)) {
            return false;
        }
        return type.name().endsWith("SHULKER_BOX") || type.name().endsWith("BUNDLE") || type.name().endsWith("BUCKET")
                || type == Material.BOWL || type == Material.GLASS_BOTTLE;
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
    }

    // ---- Darstellung ----

    @Override
    public void updateDisplay() {
        Block below = world().getBlockAt(position().x(), position().y() - 1, position().z());
        Block above = world().getBlockAt(position().x(), position().y() + 1, position().z());
        boolean base = !(context.manager().at(below) instanceof StoveStation) && !below.getType().isOccluding();
        boolean chains = !base && above.getBlockData().isFaceSturdy(BlockFace.DOWN, org.bukkit.block.BlockSupport.CENTER);
        String model = base ? "block/stockpot_base" : chains ? "block/stockpot_chains" : "block/stockpot";
        showPart("body", Key.key("kaleidoscope_cookery", model + (hasLid() ? "_has_lid" : "")), blockTransform(0));

        if (hasLid() || status == PUT_SOUP_BASE) {
            hidePart("surface");
            hidePart("floating");
            hidePartsStartingWith("input_", 0);
            return;
        }
        SoupBase soup = soup();
        Key surface;
        float height = SOUP_HEIGHT;
        if (status == PUT_INGREDIENT) {
            surface = soup == null ? surface("water") : soup.surface();
        } else if (status == COOKING) {
            surface = textureSurface(cookingTexture);
        } else {
            surface = textureSurface(finishedTexture);
            int max = result == null ? 1 : Math.max(1, Math.min(result.getAmount(), MAX_TAKEOUT_COUNT));
            height = 0.065f + 0.315f / max * takeoutCount;
        }
        showPart("surface", surface, new Transformation(new Vector3f(0, height, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
        if (status == PUT_INGREDIENT && soup != null && soup.floating() != null) {
            showItemPart("floating", new ItemStack(soup.floating()),
                    new Transformation(new Vector3f(0, SOUP_HEIGHT - 0.45f, 0), new Quaternionf().rotationX((float) Math.toRadians(-90)),
                            new Vector3f(0.4f, 0.4f, 0.4f), new Quaternionf()), 0, null);
        } else {
            hidePart("floating");
        }
        int shown = 0;
        if (status != FINISHED) {
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] != null) {
                    showItemPart("input_" + shown, inputs[i], itemTransform(i, inputs[i]), 0, null);
                    shown++;
                }
            }
        }
        hidePartsStartingWith("input_", shown);
    }

    /**
     * Fertige/Koch-Texturen (kaleidoscope_cookery:stockpot/x) liegen als Modell kaleidoscope:surface/x im Pack.
     */
    private static Key textureSurface(Key texture) {
        String value = texture.value();
        return surface(value.substring(value.lastIndexOf('/') + 1));
    }

    /**
     * Wie StockpotBlockEntityRender#renderItems: Zutaten schwimmen schräg in der Suppe
     * (Mod: leicht schaukelnd – hier feste Lage).
     */
    private Transformation itemTransform(int slot, ItemStack stack) {
        int random = Math.abs(31 * slot + CookeryItems.typeOf(stack).asString().hashCode());
        float offsetX = (random % 100) * 0.002f;
        float offsetY = random % 50 * 0.004f;
        float offsetZ = (float) (Math.sin(random) * 0.2);
        float yRot = (random % 2 == 0 ? -1 : 1) * 20 + random % 10;
        Quaternionf rotX = new Quaternionf().rotationX((float) Math.toRadians(85 + random % 10));
        Vector3f position = new Vector3f(0.5f * (0.9f + offsetX), 0.5f * (0.9f + offsetY), 0.5f * (-0.5f + offsetZ));
        rotX.transform(position);
        position.sub(0.5f, 0.5f, 0.5f);
        Quaternionf rotation = new Quaternionf(rotX).rotateY((float) Math.toRadians(yRot)).rotateZ((float) Math.toRadians(random % 360));
        return new Transformation(position, rotation, new Vector3f(0.5f, 0.5f, 0.5f), new Quaternionf());
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        if (lid != null) {
            contents.add(lid);
        }
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
        return "Suppentopf (Status " + status + ", Brühe " + soupBase.value() + ", " + inputCount() + " Zutaten, Deckel " + hasLid()
                + ", Rest " + currentTick + ", Portionen " + takeoutCount + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component state;
        if (status == PUT_SOUP_BASE) {
            state = Lang.get("hud.stockpot.need_soup").color(NamedTextColor.GOLD);
        } else if (!hasHeatSource()) {
            state = Lang.raw("hud.kaleidoscope_grilling.pot.state.need_heat").color(NamedTextColor.RED);
        } else if (status == PUT_INGREDIENT) {
            state = (hasLid() ? Lang.get("hud.stockpot.lid_wait") : Lang.get("hud.stockpot.add", inputCount(), StockpotRecipe.SLOTS)).color(NamedTextColor.YELLOW);
        } else if (status == COOKING) {
            state = hasLid() ? Lang.get("hud.stockpot.cooking", (currentTick + 19) / 20).color(NamedTextColor.GREEN)
                    : Lang.get("hud.stockpot.need_lid").color(NamedTextColor.GOLD);
        } else {
            state = (hasLid() ? Lang.get("hud.stockpot.open") : Lang.get("hud.stockpot.portions", takeoutCount)).color(NamedTextColor.GREEN);
        }
        return Component.text().append(Lang.raw("block.kaleidoscope_cookery.stockpot").color(NamedTextColor.WHITE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(state).build();
    }
}
