package de.emrion.kaleidoscopeCookery.station.plate;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Servierteller und Obstplatten (PlateBlock/PlateRegistry im Mod) sowie stapelbares Essen (StackableFoodBlock: Bambusrohr-Reis).
 * <ul>
 *     <li>Teller: voll platziert; Rechtsklick gibt eine Portion, mit der passenden Speise legt man eine zurück;
 *     der leere Teller wird beim nächsten Klick zur Schüssel</li>
 *     <li>Stapel: Rechtsklick mit dem Essen stapelt (bis 4), mit leerer Hand nimmt man eins</li>
 * </ul>
 */
public class ServingPlateStation extends Station {

    /**
     * @param stackable StackableFoodBlock statt PlateBlock (Anzahl 1..max, das Essen selbst ist das Item)
     */
    public record Kind(String name, int max, List<Key> servings, boolean stackable, float height) {

        public Key item() {
            return cookery(name);
        }

        public String typeId() {
            return (stackable ? "stack_" : "serving_plate_") + name;
        }

        public Key model(int count) {
            return cookery((stackable ? "block/food/" : "block/plate/") + name + "/" + name + "_" + count);
        }
    }

    private static Key cookery(String value) {
        return Key.key("kaleidoscope_cookery", value);
    }

    private static Key vanilla(String value) {
        return Key.key("minecraft", value);
    }

    /** PlateRegistry#init und ModBlocks.BAMBOO_TUBE_RICE */
    public static final List<Kind> KINDS = List.of(
            new Kind("shengjian_mantou_plate", 5, List.of(cookery("shengjian_mantou")), false, 0.25f),
            new Kind("baozi_plate", 5, List.of(cookery("baozi")), false, 0.25f),
            new Kind("qingtuan_plate", 4, List.of(cookery("qingtuan")), false, 0.25f),
            new Kind("sticky_candy_plate", 4, List.of(cookery("sticky_candy")), false, 0.25f),
            new Kind("sticky_rice_cake_plate", 5, List.of(cookery("sticky_rice_cake")), false, 0.25f),
            new Kind("zongzi_plate", 4, List.of(cookery("zongzi")), false, 0.25f),
            new Kind("berry_platter", 4, List.of(vanilla("sweet_berries"), vanilla("glow_berries")), false, 0.25f),
            new Kind("apple_platter", 4, List.of(vanilla("apple")), false, 0.25f),
            new Kind("tomato_platter", 5, List.of(cookery("tomato")), false, 0.25f),
            new Kind("watermelon_platter", 3, List.of(vanilla("melon_slice")), false, 0.25f),
            new Kind("chorus_fruit_platter", 5, List.of(vanilla("chorus_fruit")), false, 0.25f),
            new Kind("bamboo_tube_rice", 4, List.of(cookery("bamboo_tube_rice")), true, 0.625f));

    private static final NamespacedKey COUNT = Keys.key("count");

    private final Kind kind;
    private int count;

    public ServingPlateStation(StationContext context, Kind kind) {
        super(context);
        this.kind = kind;
    }

    public static StationType type(Kind kind) {
        return new StationType(kind.typeId(), kind.item(), false, 0.875f, kind.height(), false, context -> new ServingPlateStation(context, kind));
    }

    @Override
    protected void load(PersistentDataContainer state) {
        count = Math.clamp(state.getOrDefault(COUNT, PersistentDataType.INTEGER, kind.stackable() ? 1 : kind.max()), 0, kind.max());
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(COUNT, PersistentDataType.INTEGER, count);
    }

    @Override
    public void updateDisplay() {
        showPart("body", kind.model(count), blockTransform(0));
    }

    @Override
    public String describe() {
        return kind.name() + " (" + count + "/" + kind.max() + ")";
    }

    private void setCount(int count) {
        this.count = count;
        markDirty();
        updateDisplay();
    }

    // ---- Stapeln ----

    /**
     * StackableFoodBlock: mit dem gleichen Essen stapeln (statt daneben zu platzieren).
     */
    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        if (!kind.stackable() || !kind.item().equals(CookeryItems.idOf(held)) || count >= kind.max()) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        world().playSound(center(), Sound.BLOCK_BAMBOO_PLACE, SoundCategory.BLOCKS, 1, 0.8f);
        setCount(count + 1);
        return true;
    }

    // ---- Bedienen ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return kind.stackable() ? interactStack(player, held) : interactPlate(player, held);
    }

    private boolean interactStack(Player player, ItemStack held) {
        if (!held.isEmpty()) {
            return false;
        }
        give(player, kind.item(), true);
        if (count > 1) {
            setCount(count - 1);
        } else {
            context.manager().remove(this);
        }
        return true;
    }

    /**
     * PlateBlock#useItemOn
     */
    private boolean interactPlate(Player player, ItemStack held) {
        if (!held.isEmpty() && count < kind.max() && kind.servings().size() == 1 && kind.servings().getFirst().equals(typeOf(held))) {
            held.subtract();
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
            setCount(count + 1);
            return true;
        }
        if (count > 0) {
            for (Key serving : kind.servings()) {
                give(player, serving, held.isEmpty());
            }
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
            setCount(count - 1);
        } else {
            // Leerer Teller: geht kaputt und lässt die Schüssel fallen
            context.manager().breakStation(this, player.getGameMode() == GameMode.CREATIVE ? player : null);
        }
        return true;
    }

    private static @Nullable Key typeOf(ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null ? id : stack.getType().key();
    }

    /**
     * Mit leerer Hand ins Inventar, sonst oben aus dem Teller heraus (popResourceFromFace).
     */
    private void give(Player player, Key item, boolean toPlayer) {
        ItemStack stack = items().create(item, 1);
        if (stack == null) {
            return;
        }
        if (toPlayer) {
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        } else {
            world().dropItem(center().add(0, 0.3, 0), stack);
        }
    }

    // ---- Abbauen ----

    /**
     * Teller: Schüssel (Loot-Tabelle); Stapel: nichts extra, die Speisen kommen aus contents().
     */
    @Override
    public @Nullable ItemStack selfDrop() {
        return kind.stackable() ? null : ItemStack.of(org.bukkit.Material.BOWL);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        if (count <= 0) {
            return contents;
        }
        for (Key serving : kind.stackable() ? List.of(kind.item()) : kind.servings()) {
            ItemStack stack = items().create(serving, count);
            if (stack != null) {
                contents.add(stack);
            }
        }
        return contents;
    }
}
