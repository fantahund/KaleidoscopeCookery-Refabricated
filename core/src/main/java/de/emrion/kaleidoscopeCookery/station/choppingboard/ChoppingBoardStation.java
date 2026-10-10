package de.emrion.kaleidoscopeCookery.station.choppingboard;

import de.emrion.kaleidoscopeCookery.config.CookeryConfig;
import de.emrion.kaleidoscopeCookery.item.QuickKnifeListener;
import de.emrion.kaleidoscopeCookery.recipe.ChoppingBoardRecipe;
import de.emrion.kaleidoscopeCookery.recipe.RecipeResult;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schneidebrett (ChoppingBoardBlockEntity im Mod).
 * <ul>
 *     <li>Rechtsklick mit Zutat: auflegen (1 Stück)</li>
 *     <li>Linksklick mit Küchenmesser: schneiden, Modell wechselt je Schnitt</li>
 *     <li>Fertig geschnitten: Klick lässt die Ergebnisse fallen</li>
 *     <li>Schleichen + Rechtsklick, solange noch nicht geschnitten: Zutat zurücknehmen</li>
 * </ul>
 */
public class ChoppingBoardStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "chopping_board");
    private static final Key BOARD_MODEL = Key.key("kaleidoscope_cookery", "block/chopping_board");
    private static final float FOOD_OFFSET = 0.125f;

    private static final NamespacedKey RECIPE = Keys.key("recipe");
    private static final NamespacedKey MODEL = Keys.key("model");
    private static final NamespacedKey MAX_CUTS = Keys.key("max_cuts");
    private static final NamespacedKey CUTS = Keys.key("cuts");
    private static final NamespacedKey INPUT = Keys.key("input");
    private static final NamespacedKey RESULTS = Keys.key("results");

    private @Nullable String recipeId;
    private @Nullable Key model;
    private int maxCuts;
    private int cuts;
    private @Nullable ItemStack input;
    private List<ItemStack> results = new ArrayList<>();
    private long lastCutTick;

    public ChoppingBoardStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("chopping_board", ITEM, false, 0.9f, 0.2f, true, ChoppingBoardStation::new);
    }

    // ---- Zustand ----

    @Override
    protected void load(PersistentDataContainer state) {
        recipeId = state.get(RECIPE, PersistentDataType.STRING);
        String modelId = state.get(MODEL, PersistentDataType.STRING);
        model = modelId != null && Key.parseable(modelId) ? Key.key(modelId) : null;
        maxCuts = state.getOrDefault(MAX_CUTS, PersistentDataType.INTEGER, 0);
        cuts = state.getOrDefault(CUTS, PersistentDataType.INTEGER, 0);
        byte[] inputBytes = state.get(INPUT, PersistentDataType.BYTE_ARRAY);
        input = inputBytes != null ? ItemStack.deserializeBytes(inputBytes) : null;
        results = new ArrayList<>();
        for (byte[] bytes : state.getOrDefault(RESULTS, PersistentDataType.LIST.byteArrays(), List.of())) {
            results.add(ItemStack.deserializeBytes(bytes));
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        if (results.isEmpty() || model == null) {
            return;
        }
        if (recipeId != null) {
            state.set(RECIPE, PersistentDataType.STRING, recipeId);
        }
        state.set(MODEL, PersistentDataType.STRING, model.asString());
        state.set(MAX_CUTS, PersistentDataType.INTEGER, maxCuts);
        state.set(CUTS, PersistentDataType.INTEGER, cuts);
        if (input != null) {
            state.set(INPUT, PersistentDataType.BYTE_ARRAY, input.serializeAsBytes());
        }
        List<byte[]> resultBytes = new ArrayList<>();
        for (ItemStack result : results) {
            resultBytes.add(result.serializeAsBytes());
        }
        state.set(RESULTS, PersistentDataType.LIST.byteArrays(), resultBytes);
    }

    private boolean isOccupied() {
        return !results.isEmpty();
    }

    private boolean isDone() {
        return isOccupied() && cuts >= maxCuts;
    }

    // ---- Darstellung ----

    @Override
    public void updateDisplay() {
        showPart("board", BOARD_MODEL, blockTransform(0));
        if (isOccupied() && model != null) {
            int stage = Math.min(cuts, maxCuts);
            showPart("food", de.emrion.kaleidoscopeCookery.recipe.ChoppingBoardRecipe.stageModel(model, stage), rendererTransform(FOOD_OFFSET));
        } else {
            hidePart("food");
        }
    }

    @Override
    public String describe() {
        if (!isOccupied()) {
            return "Schneidebrett (leer)";
        }
        return "Schneidebrett (" + recipeId + ", " + cuts + "/" + maxCuts + ")";
    }

    @Override
    public List<ItemStack> contents() {
        if (!isOccupied()) {
            return List.of();
        }
        if (isDone()) {
            return results;
        }
        // Angefangen: wie im Mod geht die Zutat verloren, sobald geschnitten wurde – beim Abbauen geben wir sie zurück
        return input != null ? List.of(input) : List.of();
    }

    // ---- Bedienung ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (isOccupied()) {
            if (isDone()) {
                popResults();
                return true;
            }
            if (player.isSneaking() && cuts == 0) {
                takeOut(player);
                return true;
            }
            context.manager().actionBar(player, Lang.get("chopping_board.need_tool").color(NamedTextColor.GOLD));
            return false;
        }
        if (held.isEmpty()) {
            return false;
        }
        ChoppingBoardRecipe recipe = plugin().getRecipes().findChoppingBoard(held);
        if (recipe == null) {
            context.manager().actionBar(player, Lang.get("chopping_board.no_recipe").color(NamedTextColor.GRAY));
            return false;
        }
        putOn(recipe, held.asOne());
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
            player.getInventory().setItem(hand, held);
        }
        world().playSound(center(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1.2f);
        return true;
    }

    @Override
    public boolean onAttack(Player player) {
        if (!isOccupied()) {
            return false;
        }
        if (isDone()) {
            popResults();
            return true;
        }
        CookeryConfig.Cutting cutting = plugin().getCookeryConfig().cutting();
        ItemStack tool = player.getInventory().getItemInMainHand();
        // Mod: 1 << Stufe von "Schnelles Messer" (max. II) Schnitte pro Hieb
        int perHit = cutting.cuts(tool) * (1 << Math.clamp(QuickKnifeListener.level(tool), 0, 2));
        if (perHit <= 0) {
            context.manager().actionBar(player, Lang.get("chopping_board.need_tool").color(NamedTextColor.GOLD));
            return false;
        }
        long now = context.manager().serverTick();
        if (now - lastCutTick < cutting.cooldownTicks()) {
            return false;
        }
        lastCutTick = now;
        cuts = Math.min(maxCuts, cuts + perHit);
        markDirty();
        playCutEffects();
        if (player.getGameMode() != GameMode.CREATIVE && ThreadLocalRandom.current().nextDouble() < cutting.durabilityChance()) {
            player.damageItemStack(EquipmentSlot.HAND, 1);
        }
        updateDisplay();
        context.manager().actionBar(player, Lang.get("chopping_board.progress", cuts, maxCuts).color(isDone() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return true;
    }

    /**
     * ChoppingBoardDispenseBehavior: Zutat aus dem Spender auflegen (onPutItem ohne Spieler).
     */
    public boolean putFromDispenser(ItemStack item) {
        ChoppingBoardRecipe recipe = isOccupied() ? null : plugin().getRecipes().findChoppingBoard(item);
        if (recipe == null) {
            return false;
        }
        putOn(recipe, item.asOne());
        world().playSound(center(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1.2f);
        return true;
    }

    /**
     * ChoppingBoardDispenseBehavior: Messer aus dem Spender schneidet einmal (onCutItem), fertige Ergebnisse springen heraus.
     */
    public boolean cutFromDispenser(ItemStack knife) {
        if (!isOccupied()) {
            return false;
        }
        if (isDone()) {
            popResults();
            return true;
        }
        cuts = (int) Math.min(maxCuts, (long) cuts + (1L << Math.clamp(QuickKnifeListener.level(knife), 0, 2)));
        markDirty();
        playCutEffects();
        updateDisplay();
        return true;
    }

    private void putOn(ChoppingBoardRecipe recipe, ItemStack item) {
        recipeId = recipe.id();
        model = recipe.model();
        maxCuts = recipe.cuts();
        cuts = 0;
        input = item;
        results = new ArrayList<>();
        for (RecipeResult result : recipe.results()) {
            ItemStack stack = result.create(items());
            if (stack != null) {
                results.add(stack);
            }
        }
        if (results.isEmpty()) {
            // Ergebnis-Item unbekannt (recipes.yml): Zutat einfach zurückgeben lassen
            results.add(item.clone());
        }
        markDirty();
        updateDisplay();
    }

    private void takeOut(Player player) {
        if (input != null) {
            for (ItemStack leftover : player.getInventory().addItem(input).values()) {
                drop(leftover);
            }
        }
        reset();
        world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1.2f + ThreadLocalRandom.current().nextFloat() * 0.2f);
    }

    private void popResults() {
        for (ItemStack result : results) {
            drop(result.clone());
        }
        reset();
        world().playSound(center(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 2 + ThreadLocalRandom.current().nextFloat() * 0.2f);
    }

    private void reset() {
        recipeId = null;
        model = null;
        maxCuts = 0;
        cuts = 0;
        input = null;
        results = new ArrayList<>();
        markDirty();
        updateDisplay();
    }

    /**
     * Wie popResource im Mod: ohne Schwung an der Brettmitte.
     */
    private void drop(ItemStack stack) {
        Location location = new Location(world(), position().x() + 0.5, position().y() + 0.25, position().z() + 0.5);
        world().dropItem(location, stack, item -> {
            item.setVelocity(new Vector());
            item.setPickupDelay(10);
        });
    }

    private void playCutEffects() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = new Location(world(), position().x() + 0.25 + random.nextDouble() / 2, position().y() + 0.25,
                position().z() + 0.25 + random.nextDouble() / 2);
        world().spawnParticle(Particle.CRIT, location, 2, 0, 0, 0, 0.1);
        world().playSound(center(), Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1, 1.5f + random.nextFloat() * 0.4f);
    }
}
