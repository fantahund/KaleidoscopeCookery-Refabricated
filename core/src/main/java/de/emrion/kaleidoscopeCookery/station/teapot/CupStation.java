package de.emrion.kaleidoscopeCookery.station.teapot;

import de.emrion.kaleidoscopeCookery.item.CookeryItem;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
 * Hingestellte Tassen (EmptyCupBlock / TeacupBlock im Mod): bis zu 4 Tassen, davon einige mit Tee einer Sorte.
 * <ul>
 *     <li>Leere Tasse bzw. Tee (schleichend) darauf: eine Tasse mehr</li>
 *     <li>Teekanne mit fertigem Tee: in eine leere Tasse gießen</li>
 *     <li>Rechtsklick sonst: eine Tasse nehmen (zuerst die mit Tee)</li>
 * </ul>
 */
public class CupStation extends Station {

    public static final String TYPE_ID = "cup";
    public static final Key EMPTY_CUP = Key.key("kaleidoscope_cookery", "empty_cup");
    private static final int MAX_COUNT = 4;

    private static final NamespacedKey CUPS = Keys.key("cups");
    private static final NamespacedKey TEAS = Keys.key("teas");
    private static final NamespacedKey TEA = Keys.key("tea");

    private int cups = 1;
    private int teas;
    private @Nullable Key tea;

    public CupStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType(TYPE_ID, EMPTY_CUP, false, 0.7f, 0.35f, true, CupStation::new);
    }

    /**
     * Teetassen (Getränk mit leerer Tasse als Rest) lassen sich ebenfalls hinstellen.
     */
    public static boolean isTeacup(@Nullable CookeryItem item) {
        return item != null && item.food() != null && item.food().drink() && EMPTY_CUP.equals(item.food().remainder());
    }

    public void initFrom(Key placed) {
        cups = 1;
        if (EMPTY_CUP.equals(placed)) {
            teas = 0;
            tea = null;
        } else {
            teas = 1;
            tea = placed;
        }
        markDirty();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        cups = Math.clamp(state.getOrDefault(CUPS, PersistentDataType.INTEGER, 1), 1, MAX_COUNT);
        teas = Math.clamp(state.getOrDefault(TEAS, PersistentDataType.INTEGER, 0), 0, cups);
        String id = state.get(TEA, PersistentDataType.STRING);
        tea = id != null && Key.parseable(id) ? Key.key(id) : null;
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(CUPS, PersistentDataType.INTEGER, cups);
        state.set(TEAS, PersistentDataType.INTEGER, teas);
        if (tea != null) {
            state.set(TEA, PersistentDataType.STRING, tea.asString());
        }
    }

    /**
     * Tassen und Kanne auf die Tassen: hinzufügen bzw. eingießen statt eine neue Station darauf zu setzen.
     */
    @Override
    public boolean interceptPlacement(Player player, ItemStack held) {
        Key heldId = CookeryItems.idOf(held);
        if (EMPTY_CUP.equals(heldId) || TeapotItems.isTeapot(held) || (heldId != null && isTeacup(items().get(heldId)))) {
            onInteract(player, EquipmentSlot.HAND);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        Key heldId = CookeryItems.idOf(held);
        if (TeapotItems.isTeapot(held)) {
            ItemStack pour = TeapotItems.pourOut(held);
            Key pourId = CookeryItems.idOf(pour);
            if (pour == null || pourId == null || teas >= cups || (tea != null && teas > 0 && !tea.equals(pourId))) {
                return false;
            }
            tea = pourId;
            teas++;
            TeapotItems.pourOne(held);
            world().playSound(center(), Sound.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 1, 1);
            world().spawnParticle(Particle.SPLASH, center().add(0, -0.2, 0), 8, 0.15, 0.05, 0.15, 0);
            changed();
            return true;
        }
        if (EMPTY_CUP.equals(heldId)) {
            if (cups >= MAX_COUNT) {
                return false;
            }
            cups++;
            consume(player, held);
            changed();
            return true;
        }
        if (heldId != null && isTeacup(items().get(heldId))) {
            if (cups >= MAX_COUNT || (tea != null && teas > 0 && !tea.equals(heldId))) {
                return false;
            }
            cups++;
            teas++;
            tea = heldId;
            consume(player, held);
            changed();
            return true;
        }
        // Eine Tasse nehmen
        ItemStack taken = teas > 0 && tea != null ? items().create(tea, 1) : items().create(EMPTY_CUP, 1);
        if (taken == null) {
            return false;
        }
        if (teas > 0) {
            teas--;
        }
        cups--;
        if (held.isEmpty()) {
            player.getInventory().setItemInMainHand(taken);
        } else {
            for (ItemStack leftover : player.getInventory().addItem(taken).values()) {
                world().dropItem(player.getLocation(), leftover);
            }
        }
        world().playSound(center(), Sound.BLOCK_DECORATED_POT_HIT, SoundCategory.BLOCKS, 0.6f, 1.4f);
        if (cups <= 0) {
            context.manager().remove(this);
            return true;
        }
        changed();
        return true;
    }

    private void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        world().playSound(center(), Sound.BLOCK_DECORATED_POT_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
    }

    private void changed() {
        if (teas == 0) {
            tea = null;
        }
        markDirty();
        updateDisplay();
    }

    @Override
    public void updateDisplay() {
        String model = tea == null || teas == 0
                ? "block/teacup/empty_cup/count" + cups
                : "block/teacup/" + tea.value() + "/count" + cups + "_" + Math.min(teas, cups);
        showPart("cups", Key.key("kaleidoscope_cookery", model), blockTransform(0));
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        return null;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> drops = new ArrayList<>();
        ItemStack empty = items().create(EMPTY_CUP, cups - teas);
        if (empty != null && cups - teas > 0) {
            drops.add(empty);
        }
        if (tea != null && teas > 0) {
            ItemStack filled = items().create(tea, teas);
            if (filled != null) {
                drops.add(filled);
            }
        }
        return drops;
    }

    @Override
    public String describe() {
        return "Tassen (" + cups + ", davon " + teas + " mit " + tea + ")";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        Component line = Lang.get("hud.cup.cups", cups, teas).color(NamedTextColor.WHITE);
        if (tea != null && teas > 0) {
            CookeryItem teaItem = items().get(tea);
            if (teaItem != null) {
                line = line.append(Component.text(" · ", NamedTextColor.DARK_GRAY)).append(Lang.raw(teaItem.nameKey()).color(NamedTextColor.GREEN));
            }
        }
        return line;
    }
}
