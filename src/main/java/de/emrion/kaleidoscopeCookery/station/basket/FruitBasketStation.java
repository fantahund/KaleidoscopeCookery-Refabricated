package de.emrion.kaleidoscopeCookery.station.basket;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
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

/**
 * Obstkorb (FruitBasketBlock/FruitBasketBlockEntity im Mod): 8 Plätze, die Items liegen sichtbar im Korb.
 * <ul>
 *     <li>Rechtsklick mit einem Item legt es hinein (wird mit gleichen Stapeln zusammengelegt)</li>
 *     <li>Schleichen + Rechtsklick nimmt den ersten Stapel heraus</li>
 *     <li>Abgebaut behält der Korb seinen Inhalt (wie eine Shulkerkiste)</li>
 * </ul>
 */
public class FruitBasketStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "fruit_basket");
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/fruit_basket");
    private static final Key LUNCH_BAG = Key.key("kaleidoscope_cookery", "transmutation_lunch_bag");
    private static final int SLOTS = 8;
    /** Inhalt im Korb-Item (FRUIT_BASKET_ITEMS im Mod) */
    public static final NamespacedKey ITEM_CONTENTS = Keys.key("basket_items");
    private static final NamespacedKey ITEMS = Keys.key("items");

    private final @Nullable ItemStack[] items = new ItemStack[SLOTS];

    public FruitBasketStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("fruit_basket", ITEM, false, 0.875f, 0.5f, true, FruitBasketStation::new);
    }

    // ---- Zustand ----

    @Override
    protected void load(PersistentDataContainer state) {
        read(state.getOrDefault(ITEMS, PersistentDataType.LIST.byteArrays(), List.of()));
    }

    private void read(List<byte[]> stored) {
        Arrays.fill(items, null);
        for (int i = 0; i < Math.min(stored.size(), SLOTS); i++) {
            items[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
    }

    private List<byte[]> write() {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item == null ? new byte[0] : item.serializeAsBytes());
        }
        return stored;
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(ITEMS, PersistentDataType.LIST.byteArrays(), write());
    }

    /**
     * Inhalt aus dem Korb-Item übernehmen (FruitBasketBlock#setPlacedBy).
     */
    public void initFrom(ItemStack basket) {
        read(basket.getPersistentDataContainer().getOrDefault(ITEM_CONTENTS, PersistentDataType.LIST.byteArrays(), List.of()));
    }

    /**
     * Korb mit Inhalt; die Liste der Items steht unter der Beschreibung.
     */
    @Override
    @SuppressWarnings("UnstableApiUsage")
    public @Nullable ItemStack selfDrop() {
        ItemStack basket = items().create(ITEM, 1);
        if (basket == null || isEmpty()) {
            return basket;
        }
        basket.editPersistentDataContainer(pdc -> pdc.set(ITEM_CONTENTS, PersistentDataType.LIST.byteArrays(), write()));
        List<Component> lore = new ArrayList<>();
        ItemLore existing = basket.getData(DataComponentTypes.LORE);
        if (existing != null) {
            lore.addAll(existing.lines());
        }
        for (ItemStack item : items) {
            if (item != null) {
                lore.add(Text.noItalic(Component.text(item.getAmount() + "× ", NamedTextColor.GRAY).append(item.effectiveName().color(NamedTextColor.GRAY))));
            }
        }
        basket.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return basket;
    }

    private boolean isEmpty() {
        for (ItemStack item : items) {
            if (item != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Der Inhalt bleibt im Korb; im Kreativmodus fällt der volle Korb trotzdem heraus (playerWillDestroy im Mod).
     */
    @Override
    public List<ItemStack> contents(@Nullable Player breaker) {
        if (breaker != null && breaker.getGameMode() == GameMode.CREATIVE && !isEmpty()) {
            ItemStack basket = selfDrop();
            return basket == null ? List.of() : List.of(basket);
        }
        return List.of();
    }

    // ---- Anzeige ----

    /**
     * FruitBasketBlockEntityRender: zwei Reihen à vier Plätze, leere Plätze rücken auf.
     */
    @Override
    public void updateDisplay() {
        showPart("body", MODEL, blockTransform(0));
        Quaternionf facingRotation = new Quaternionf().rotationY((float) Math.toRadians(-facing().dataValue() * 90));
        for (int row = 0; row < 2; row++) {
            float x = 0.1f;
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                ItemStack stack = items[index];
                if (stack == null) {
                    hidePart("item_" + index);
                    continue;
                }
                x += 0.15f;
                float z = 0.35f + row * 0.32f + (index % 2 == 0 ? -0.01f : 0.01f);
                Vector3f translation = facingRotation.transform(new Vector3f(x - 0.5f, 0, z - 0.5f)).add(0, 0.3f - 0.5f, 0);
                Quaternionf rotation = new Quaternionf(facingRotation).rotateY((float) Math.toRadians(-90)).rotateX((float) Math.toRadians(30));
                showItemPart("item_" + index, stack, new Transformation(translation, rotation, new Vector3f(0.375f), new Quaternionf()), 0, null);
            }
        }
    }

    @Override
    public String describe() {
        int count = 0;
        for (ItemStack item : items) {
            count += item == null ? 0 : 1;
        }
        return "Obstkorb (" + count + "/" + SLOTS + " Stapel)";
    }

    // ---- Interaktion ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        if (player.isSneaking()) {
            return takeOut(player);
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty() || LUNCH_BAG.equals(CookeryItems.idOf(held))) {
            return false;
        }
        putOn(held);
        return true;
    }

    /**
     * Item#canFitInsideContainerItems: keine Shulkerkisten und keine Obstkörbe.
     */
    private static boolean canFit(ItemStack stack) {
        return !Tag.SHULKER_BOXES.isTagged(stack.getType()) && !ITEM.equals(CookeryItems.idOf(stack));
    }

    /**
     * ItemHandlerHelper#insertItemStacked: erst auf gleiche Stapel, dann in freie Plätze.
     */
    private void putOn(ItemStack stack) {
        if (!canFit(stack)) {
            return;
        }
        int before = stack.getAmount();
        for (ItemStack item : items) {
            if (item != null && item.isSimilar(stack) && item.getAmount() < item.getMaxStackSize()) {
                int moved = Math.min(stack.getAmount(), item.getMaxStackSize() - item.getAmount());
                item.add(moved);
                stack.subtract(moved);
                if (stack.isEmpty()) {
                    break;
                }
            }
        }
        for (int i = 0; i < SLOTS && !stack.isEmpty(); i++) {
            if (items[i] == null) {
                items[i] = stack.clone();
                stack.setAmount(0);
            }
        }
        if (stack.getAmount() != before) {
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
            markDirty();
            updateDisplay();
        }
    }

    private boolean takeOut(Player player) {
        for (int i = 0; i < SLOTS; i++) {
            if (items[i] != null) {
                for (ItemStack rest : player.getInventory().addItem(items[i]).values()) {
                    player.getWorld().dropItem(player.getLocation(), rest);
                }
                items[i] = null;
                world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
                markDirty();
                updateDisplay();
                return true;
            }
        }
        return false;
    }
}
