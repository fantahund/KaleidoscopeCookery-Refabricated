package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.command.Permissions;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.pot.PotStation;
import de.emrion.kaleidoscopeCookery.station.stockpot.StockpotStation;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Rezept (RecipeItem im Mod).
 * <ul>
 *     <li>Leeres Rezept auf einen Wok/Suppentopf mit Zutaten: merkt sich Zutaten und Ergebnis (ein Rezept vom Stapel)</li>
 *     <li>Beschriebenes Rezept auf einen leeren Wok (mit Öl) bzw. Suppentopf (ohne Deckel): nimmt die Zutaten aus dem
 *     Inventar und legt sie ein; fehlt etwas, steht im Chat was</li>
 * </ul>
 */
public class RecipeItems implements Listener {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "recipe_item");
    public static final String POT = "pot";
    public static final String STOCKPOT = "stockpot";
    private static final NamespacedKey RECORD_TYPE = Keys.key("recipe_type");
    private static final NamespacedKey RECORD_INPUT = Keys.key("recipe_input");
    private static final NamespacedKey RECORD_OUTPUT = Keys.key("recipe_output");
    private static final NamespacedKey RECORD_FLEX = Keys.key("recipe_flex");
    private static final String HAS_RECIPE_PROPERTY = "kaleidoscope_cookery:has_recipe";

    public record Record(String type, List<ItemStack> input, ItemStack output, boolean flex) {
    }

    private final KaleidoscopeCookery plugin;

    public RecipeItems(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public static boolean isRecipe(@Nullable ItemStack stack) {
        return stack != null && ITEM.equals(CookeryItems.idOf(stack));
    }

    public static @Nullable Record record(ItemStack stack) {
        io.papermc.paper.persistence.PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        String type = pdc.get(RECORD_TYPE, PersistentDataType.STRING);
        byte[] output = pdc.get(RECORD_OUTPUT, PersistentDataType.BYTE_ARRAY);
        if (type == null || output == null) {
            return null;
        }
        List<ItemStack> input = new ArrayList<>();
        for (byte[] bytes : pdc.getOrDefault(RECORD_INPUT, PersistentDataType.LIST.byteArrays(), List.of())) {
            input.add(ItemStack.deserializeBytes(bytes));
        }
        return new Record(type, input, ItemStack.deserializeBytes(output), pdc.getOrDefault(RECORD_FLEX, PersistentDataType.BOOLEAN, false));
    }

    /**
     * Rezept aufschreiben: Name "Rezept: Ergebnis, zubereitet im Wok", Zutaten und Ergebnis in der Beschreibung.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void write(ItemStack stack, Record record) {
        List<byte[]> input = new ArrayList<>();
        for (ItemStack item : record.input()) {
            input.add(item.serializeAsBytes());
        }
        stack.editPersistentDataContainer(pdc -> {
            pdc.set(RECORD_TYPE, PersistentDataType.STRING, record.type());
            pdc.set(RECORD_INPUT, PersistentDataType.LIST.byteArrays(), input);
            pdc.set(RECORD_OUTPUT, PersistentDataType.BYTE_ARRAY, record.output().serializeAsBytes());
            pdc.set(RECORD_FLEX, PersistentDataType.BOOLEAN, record.flex());
        });
        Component station = Lang.raw(record.type().equals(POT) ? "block.kaleidoscope_cookery.pot" : "block.kaleidoscope_cookery.stockpot");
        stack.setData(DataComponentTypes.ITEM_NAME, Text.noItalic(Lang.raw("block.kaleidoscope_cookery.recipe_block.has_record",
                record.output().effectiveName(), station)));
        List<Component> lore = new ArrayList<>();
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_cookery.recipe_item").color(NamedTextColor.GRAY)));
        for (Entry<String, Integer> entry : count(record.input()).entrySet()) {
            ItemStack sample = record.input().stream().filter(item -> typeKey(item).equals(entry.getKey())).findFirst().orElseThrow();
            lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_cookery.recipe_item.ingredient").color(NamedTextColor.DARK_GRAY)
                    .append(sample.effectiveName().color(NamedTextColor.GRAY)).append(Component.text(" ×" + entry.getValue(), NamedTextColor.GRAY))));
        }
        lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_cookery.recipe_item.output").color(NamedTextColor.DARK_GRAY)
                .append(record.output().effectiveName().color(NamedTextColor.GOLD))
                .append(Component.text(" ×" + record.output().getAmount(), NamedTextColor.GOLD))));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        ModelProperties.set(stack, HAS_RECIPE_PROPERTY, 1);
    }

    /**
     * Item-Art wie Item#equals im Mod (Komponenten wie Qualität zählen nicht).
     */
    private static String typeKey(ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return (id != null ? id : stack.getType().key()).asString();
    }

    private static Map<String, Integer> count(List<ItemStack> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : items) {
            if (item != null && !item.isEmpty()) {
                counts.merge(typeKey(item), 1, Integer::sum);
            }
        }
        return counts;
    }

    // ---- Benutzen ----

    /**
     * Vor dem StationListener: sonst würde das Rezept als Zettel neben den Wok gelegt.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (event.getHand() != EquipmentSlot.HAND || !isRecipe(held) || !player.hasPermission(Permissions.USE)) {
            return;
        }
        Station station = plugin.getStations().byEntity(event.getRightClicked());
        if (!(station instanceof PotStation) && !(station instanceof StockpotStation)) {
            return;
        }
        Record record = record(held);
        boolean handled = record == null ? recordFrom(player, held, station) : putInto(player, record, station);
        if (handled) {
            event.setCancelled(true);
            player.swingMainHand();
        }
    }

    /**
     * RecipeItem#onRecordRecipe
     */
    private boolean recordFrom(Player player, ItemStack held, Station station) {
        List<ItemStack> inputs;
        Entry<ItemStack, Boolean> result;
        String type;
        if (station instanceof PotStation pot) {
            inputs = pot.recordableInputs();
            result = inputs.isEmpty() ? null : pot.previewResult();
            type = POT;
        } else if (station instanceof StockpotStation stockpot) {
            inputs = stockpot.recordableInputs();
            result = inputs.isEmpty() ? null : stockpot.previewResult();
            type = STOCKPOT;
        } else {
            return false;
        }
        if (result == null) {
            return false;
        }
        ItemStack recordStack = held.asOne();
        held.subtract();
        write(recordStack, new Record(type, inputs, result.getKey(), result.getValue()));
        // Qualität des Flex-Rezepts steht auch auf dem Rezept selbst
        de.emrion.kaleidoscopeCookery.food.Quality quality = de.emrion.kaleidoscopeCookery.food.FoodService.quality(result.getKey());
        if (result.getValue() && quality != null) {
            recordStack.editPersistentDataContainer(pdc -> pdc.set(de.emrion.kaleidoscopeCookery.food.FoodService.QUALITY, PersistentDataType.STRING, quality.id()));
        }
        for (ItemStack rest : player.getInventory().addItem(recordStack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
        return true;
    }

    /**
     * RecipeItem#onPutRecipe + handlePutRecipe: Zutaten zählen, fehlende melden, sonst abziehen und einlegen.
     */
    private boolean putInto(Player player, Record record, Station station) {
        boolean pot = station instanceof PotStation potStation && record.type().equals(POT) && potStation.canReceiveRecipe();
        boolean stockpot = station instanceof StockpotStation stockpotStation && record.type().equals(STOCKPOT) && stockpotStation.canReceiveRecipe();
        if (!pot && !stockpot) {
            return false;
        }
        Map<String, Integer> need = count(record.input());
        PlayerInventory inventory = player.getInventory();
        Map<String, Integer> supply = new LinkedHashMap<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && !stack.isEmpty()) {
                supply.merge(typeKey(stack), stack.getAmount(), Integer::sum);
            }
        }
        Component missing = Component.empty();
        boolean anyMissing = false;
        for (Entry<String, Integer> entry : need.entrySet()) {
            int lacking = entry.getValue() - supply.getOrDefault(entry.getKey(), 0);
            if (lacking > 0) {
                ItemStack sample = record.input().stream().filter(item -> typeKey(item).equals(entry.getKey())).findFirst().orElseThrow();
                missing = missing.append(Component.text(" ")).append(sample.effectiveName()).append(Component.text("×" + lacking));
                anyMissing = true;
            }
        }
        if (anyMissing) {
            player.sendMessage(Lang.raw("tooltip.kaleidoscope_cookery.recipe_item.missing").append(missing));
            return true;
        }
        ItemStack[] contents = inventory.getStorageContents();
        for (Entry<String, Integer> entry : need.entrySet()) {
            int needCount = entry.getValue();
            for (int i = 0; i < contents.length && needCount > 0; i++) {
                ItemStack stack = contents[i];
                if (stack != null && !stack.isEmpty() && typeKey(stack).equals(entry.getKey())) {
                    int taken = Math.min(needCount, stack.getAmount());
                    stack.subtract(taken);
                    needCount -= taken;
                }
            }
        }
        inventory.setStorageContents(contents);
        if (station instanceof PotStation potStation) {
            potStation.addAllIngredients(record.input(), player);
        } else if (station instanceof StockpotStation stockpotStation) {
            stockpotStation.addAllIngredients(record.input(), player);
        }
        return true;
    }
}
