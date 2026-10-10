package de.emrion.kaleidoscopeCookery.station.oil;

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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Placed oil pot (OilPotBlock): stores up to 256 oil.
 * Right-click with oil refills it, with an empty hand you take up to 64 oil out.
 * When broken the pot keeps its contents.
 */
public class OilPotStation extends Station {

    private static final NamespacedKey OIL = Keys.key("oil");
    private static final NamespacedKey VARIANT = Keys.key("oil_variant");
    private int oil;
    /** Oil type of an addon ("" = fat from Cookery) */
    private String variant = "";

    /**
     * An addon's oil types (Grilling: canola oil, chili oil ...) in the placed pot.
     */
    public interface Variants {
        /** Type of the pot item ("" = fat) */
        String of(ItemStack pot);

        /** Write the type onto the pot item (when breaking) */
        void apply(ItemStack pot, String variant);

        /** Models of the placed pot with this type; the second one (if present) glows */
        java.util.List<Key> models(String variant);

        /** Right-click with an item Cookery does not know (e.g. oil bucket); true = handled */
        boolean interact(OilPotStation pot, Player player, ItemStack held);
    }

    private static @Nullable Variants variants;

    public static void setVariants(Variants variants) {
        OilPotStation.variants = variants;
    }

    public int oil() {
        return oil;
    }

    public String variant() {
        return variant;
    }

    /** Set the contents (addons); when empty the pot becomes a fat pot again */
    public void setContent(int oil, String variant) {
        this.oil = Math.max(0, oil);
        this.variant = this.oil == 0 ? "" : variant;
        changed();
    }

    public OilPotStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("oil_pot", OilItems.OIL_POT, false, 0.4f, 0.65f, true, OilPotStation::new);
    }

    public void initFrom(ItemStack item) {
        oil = OilItems.oilCount(item);
        variant = variants == null || oil == 0 ? "" : variants.of(item);
        markDirty();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        oil = state.getOrDefault(OIL, PersistentDataType.INTEGER, 0);
        variant = state.getOrDefault(VARIANT, PersistentDataType.STRING, "");
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(OIL, PersistentDataType.INTEGER, oil);
        state.set(VARIANT, PersistentDataType.STRING, variant);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (variants != null && variants.interact(this, player, held)) {
            return true;
        }
        if (!variant.isEmpty()) {
            return false; // oil types (addon) cannot be taken out as fat or mixed with fat
        }
        if (held.isEmpty()) {
            if (oil <= 0) {
                return false;
            }
            int amount = Math.min(oil, 64);
            ItemStack oilStack = items().create(OilItems.OIL, amount);
            if (oilStack == null) {
                return false;
            }
            player.getInventory().setItemInMainHand(oilStack);
            oil -= amount;
            world().playSound(center(), Sound.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, 1, random.nextFloat() * 0.2f + 0.8f);
            changed();
            return true;
        }
        if (OilItems.isOil(held)) {
            int add = Math.min(OilItems.OIL_POT_MAX - oil, held.getAmount());
            if (add <= 0) {
                return false;
            }
            oil += add;
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract(add);
            }
            world().playSound(center(), Sound.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, 1, random.nextFloat() * 0.2f + 0.4f);
            changed();
            return true;
        }
        return false;
    }

    private void changed() {
        markDirty();
        updateDisplay();
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        ItemStack pot = items().create(OilItems.OIL_POT, 1);
        if (pot != null) {
            OilItems.setOilCount(pot, oil);
            if (variants != null && !variant.isEmpty()) {
                variants.apply(pot, variant);
            }
        }
        return pot;
    }

    @Override
    public void updateDisplay() {
        java.util.List<Key> models = oil > 0 && !variant.isEmpty() && variants != null ? variants.models(variant) : java.util.List.of();
        showPart("body", models.isEmpty() ? Key.key("kaleidoscope_cookery", oil > 0 ? "block/oil_pot_with_oil" : "block/oil_pot") : models.getFirst(),
                blockTransform(0));
        if (models.size() > 1) {
            showPart("glow", new PartLook(modelStack(models.get(1)), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, blockTransform(0),
                    new org.bukkit.entity.Display.Brightness(15, 15)), 0);
        } else {
            hidePart("glow");
        }
    }

    @Override
    public String describe() {
        return "Ölkanne (" + oil + " Öl)";
    }

    @Override
    public @Nullable Component hud(Player viewer) {
        return Lang.raw("block.kaleidoscope_cookery.oil_pot").color(NamedTextColor.WHITE).append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Lang.raw("tooltip.kaleidoscope_cookery.oil_pot.count", oil).color(NamedTextColor.YELLOW));
    }
}
