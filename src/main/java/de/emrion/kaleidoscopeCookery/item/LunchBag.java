package de.emrion.kaleidoscopeCookery.item;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Verwandlungs-Lunchbeutel (TransmutationLunchBagItem im Mod): fasst 24 Stapel Essen und Tränke.
 * <ul>
 *     <li>Im Inventar wie ein Bündel: Rechtsklick mit Essen auf den Beutel legt es hinein, Rechtsklick mit dem Beutel
 *     auf Essen nimmt es auf; auf leere Plätze bzw. mit leerer Hand kommt der erste Stapel wieder heraus</li>
 *     <li>Rechtsklick in der Hand: isst so lange, bis man satt ist; Tränke werden alle getrunken</li>
 *     <li>Schleichen + Schlagen: alles ins Inventar</li>
 *     <li>Am Obstkorb: leerer Korb wird befüllt, sonst nimmt der Beutel den Korb-Inhalt auf</li>
 * </ul>
 */
public class LunchBag implements Listener {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "transmutation_lunch_bag");
    private static final NamespacedKey CONTENTS = Keys.key("lunch_bag_items");
    private static final String HAS_ITEMS_PROPERTY = "kaleidoscope_cookery:has_items";
    private static final int MAX_SLOTS = 24;
    private static final List<String> TOOLTIP = List.of("tooltip.kaleidoscope_cookery.transmutation_lunch_bag.line1",
            "tooltip.kaleidoscope_cookery.transmutation_lunch_bag.line2", "tooltip.kaleidoscope_cookery.transmutation_lunch_bag.line3");

    private final KaleidoscopeCookery plugin;

    public LunchBag(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public static boolean isBag(@Nullable ItemStack stack) {
        return stack != null && ITEM.equals(CookeryItems.idOf(stack));
    }

    // ---- Inhalt ----

    public static List<ItemStack> contents(ItemStack bag) {
        List<ItemStack> items = new ArrayList<>();
        for (byte[] bytes : bag.getPersistentDataContainer().getOrDefault(CONTENTS, PersistentDataType.LIST.byteArrays(), List.of())) {
            items.add(ItemStack.deserializeBytes(bytes));
        }
        return items;
    }

    /**
     * Inhalt speichern und Beschreibung, Modell (voll/leer) und Ess-Animation anpassen.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static void setContents(ItemStack bag, List<ItemStack> items) {
        items.removeIf(item -> item == null || item.isEmpty());
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : items) {
            stored.add(item.serializeAsBytes());
        }
        bag.editPersistentDataContainer(pdc -> {
            if (stored.isEmpty()) {
                pdc.remove(CONTENTS);
            } else {
                pdc.set(CONTENTS, PersistentDataType.LIST.byteArrays(), stored);
            }
        });
        List<Component> lore = new ArrayList<>();
        for (String key : TOOLTIP) {
            lore.add(Text.noItalic(Component.translatable(key, NamedTextColor.GRAY)));
        }
        for (ItemStack item : items) {
            lore.add(Text.noItalic(Component.text(item.getAmount() + "× ", NamedTextColor.DARK_GRAY).append(item.effectiveName().color(NamedTextColor.DARK_GRAY))));
        }
        bag.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        ModelProperties.set(bag, HAS_ITEMS_PROPERTY, items.isEmpty() ? 0 : 1);
        // getUseAnimation: Essen vor Tränken; ohne Verzehrbares kein Benutzen
        ItemUseAnimation animation = null;
        for (ItemStack item : items) {
            if (item.hasData(DataComponentTypes.FOOD)) {
                animation = ItemUseAnimation.EAT;
                break;
            }
            if (item.getType() == Material.POTION && animation == null) {
                animation = ItemUseAnimation.DRINK;
            }
        }
        if (animation == null) {
            bag.unsetData(DataComponentTypes.CONSUMABLE);
        } else {
            bag.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(1.6f).animation(animation).hasConsumeParticles(false).build());
        }
    }

    /**
     * TransmutationLunchBagItem#canAdd: Essen oder Trank, nichts mit eigenem Inhalt.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static boolean canAdd(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || Tag.SHULKER_BOXES.isTagged(stack.getType()) || isBag(stack)
                || Key.key("kaleidoscope_cookery", "fruit_basket").equals(CookeryItems.idOf(stack))) {
            return false;
        }
        return stack.hasData(DataComponentTypes.FOOD) || stack.hasData(DataComponentTypes.POTION_CONTENTS);
    }

    /**
     * ItemHandlerHelper#insertItemStacked: erst auf gleiche Stapel, dann in freie Plätze.
     *
     * @return wie viele Items aufgenommen wurden (der Stapel wird entsprechend verkleinert)
     */
    public static int add(ItemStack bag, ItemStack food) {
        if (!canAdd(food)) {
            return 0;
        }
        List<ItemStack> items = contents(bag);
        int before = food.getAmount();
        for (ItemStack item : items) {
            if (item.isSimilar(food) && item.getAmount() < item.getMaxStackSize()) {
                int moved = Math.min(food.getAmount(), item.getMaxStackSize() - item.getAmount());
                item.add(moved);
                food.subtract(moved);
                if (food.isEmpty()) {
                    break;
                }
            }
        }
        if (!food.isEmpty() && items.size() < MAX_SLOTS) {
            items.add(food.clone());
            food.setAmount(0);
        }
        int added = before - food.getAmount();
        if (added > 0) {
            setContents(bag, items);
        }
        return added;
    }

    private static @Nullable ItemStack removeFirst(ItemStack bag) {
        List<ItemStack> items = contents(bag);
        if (items.isEmpty()) {
            return null;
        }
        ItemStack first = items.removeFirst();
        setContents(bag, items);
        return first;
    }

    // ---- Inventar ----

    /**
     * overrideStackedOnOther / overrideOtherStackedOnMe: Rechtsklick im Inventar wie bei einem Bündel.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT || event.getClickedInventory() == null || event.getWhoClicked().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        HumanEntity player = event.getWhoClicked();
        if (isBag(current) && current.getAmount() == 1) {
            // Etwas auf den Beutel klicken
            event.setCancelled(true);
            if (cursor.isEmpty()) {
                ItemStack removed = removeFirst(current);
                if (removed != null) {
                    event.setCurrentItem(current);
                    player.setItemOnCursor(removed);
                    sound(player, Sound.ITEM_BUNDLE_REMOVE_ONE);
                }
            } else if (add(current, cursor) > 0) {
                event.setCurrentItem(current);
                player.setItemOnCursor(cursor.isEmpty() ? null : cursor);
                sound(player, Sound.ITEM_BUNDLE_INSERT);
            }
        } else if (isBag(cursor) && cursor.getAmount() == 1) {
            // Mit dem Beutel auf etwas klicken
            event.setCancelled(true);
            if (current == null || current.isEmpty()) {
                ItemStack removed = removeFirst(cursor);
                if (removed != null) {
                    event.setCurrentItem(removed);
                    player.setItemOnCursor(cursor);
                    sound(player, Sound.ITEM_BUNDLE_REMOVE_ONE);
                }
            } else if (add(cursor, current) > 0) {
                event.setCurrentItem(current.isEmpty() ? null : current);
                player.setItemOnCursor(cursor);
                sound(player, Sound.ITEM_BUNDLE_INSERT);
            }
        }
    }

    private static void sound(HumanEntity player, Sound sound) {
        player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 0.8f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
    }

    // ---- Essen ----

    /**
     * TransmutationLunchBagItem#finishUsingItem: Essen bis der Hunger voll ist, Tränke immer ganz.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack bag = event.getItem();
        if (!isBag(bag)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        List<ItemStack> items = contents(bag);
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            boolean food = item.hasData(DataComponentTypes.FOOD);
            boolean potion = item.getType() == Material.POTION;
            if (!food && !potion) {
                continue;
            }
            while (!item.isEmpty() && (potion || player.getFoodLevel() < 20)) {
                ItemStack rest = plugin.getFoodService().eatFromBag(player, item.asOne());
                item.subtract();
                if (rest != null && player.getGameMode() != GameMode.CREATIVE) {
                    leftovers.add(rest);
                }
            }
        }
        // Der Beutel selbst bleibt in der Hand (Event abgebrochen) – Inhalt dort aktualisieren
        EquipmentSlot hand = event.getHand();
        ItemStack inHand = player.getInventory().getItem(hand);
        if (isBag(inHand)) {
            setContents(inHand, items);
            player.getInventory().setItem(hand, inHand);
        }
        for (ItemStack leftover : leftovers) {
            for (ItemStack rest : player.getInventory().addItem(leftover).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, ThreadLocalRandom.current().nextFloat() * 0.1f + 0.9f);
    }

    /**
     * onEntitySwing beim Schleichen: alles ins Inventar.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        ItemStack bag = player.getInventory().getItemInMainHand();
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || !player.isSneaking() || !isBag(bag)) {
            return;
        }
        List<ItemStack> items = contents(bag);
        if (items.isEmpty()) {
            return;
        }
        setContents(bag, new ArrayList<>());
        player.getInventory().setItemInMainHand(bag);
        for (ItemStack item : items) {
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
        sound(player, Sound.ITEM_BUNDLE_DROP_CONTENTS);
    }

    /**
     * Den Beutel nie als Block oder auf andere Weise benutzen (Basis ist Papier).
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isBag(event.getItem()) && event.getClickedBlock() != null
                && !event.getClickedBlock().getType().isInteractable()) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    /**
     * Austausch mit dem Obstkorb (TransmutationLunchBagItem#useOn).
     *
     * @param basket Inhalt des Korbs (wird angepasst)
     */
    public static void exchange(Player player, ItemStack bag, List<@Nullable ItemStack> basket) {
        boolean basketEmpty = basket.stream().allMatch(item -> item == null || item.isEmpty());
        if (basketEmpty && !contents(bag).isEmpty()) {
            List<ItemStack> items = contents(bag);
            for (ItemStack item : items) {
                for (int i = 0; i < basket.size() && !item.isEmpty(); i++) {
                    ItemStack slot = basket.get(i);
                    if (slot != null && slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                        int moved = Math.min(item.getAmount(), slot.getMaxStackSize() - slot.getAmount());
                        slot.add(moved);
                        item.subtract(moved);
                    }
                }
                for (int i = 0; i < basket.size() && !item.isEmpty(); i++) {
                    if (basket.get(i) == null) {
                        basket.set(i, item.clone());
                        item.setAmount(0);
                    }
                }
            }
            setContents(bag, items);
            sound(player, Sound.ITEM_BUNDLE_REMOVE_ONE);
            return;
        }
        for (int i = 0; i < basket.size(); i++) {
            ItemStack item = basket.get(i);
            if (item != null && canAdd(item)) {
                add(bag, item);
                if (item.isEmpty()) {
                    basket.set(i, null);
                }
            }
        }
        sound(player, Sound.ITEM_BUNDLE_DROP_CONTENTS);
    }
}
