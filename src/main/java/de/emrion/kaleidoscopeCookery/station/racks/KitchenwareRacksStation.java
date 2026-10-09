package de.emrion.kaleidoscopeCookery.station.racks;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.item.QuickKnifeListener;
import de.emrion.kaleidoscopeCookery.item.SickleListener;
import de.emrion.kaleidoscopeCookery.station.oil.OilItems;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
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
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Küchengeräteregal (KitchenwareRacksBlock im Mod): hängt an der Wand und hält links und rechts je ein Werkzeug.
 * Rechtsklick mit einem Werkzeug hängt es auf, mit leerer Hand nimmt man es wieder ab.
 */
public class KitchenwareRacksStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "kitchenware_racks");
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/kitchenware_racks");
    private static final NamespacedKey LEFT = Keys.key("left");
    private static final NamespacedKey RIGHT = Keys.key("right");

    private @Nullable ItemStack left;
    private @Nullable ItemStack right;

    public KitchenwareRacksStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("kitchenware_racks", ITEM, false, 1f, 1f, false, KitchenwareRacksStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        byte[] leftBytes = state.get(LEFT, PersistentDataType.BYTE_ARRAY);
        byte[] rightBytes = state.get(RIGHT, PersistentDataType.BYTE_ARRAY);
        left = leftBytes == null ? null : ItemStack.deserializeBytes(leftBytes);
        right = rightBytes == null ? null : ItemStack.deserializeBytes(rightBytes);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        if (left != null) {
            state.set(LEFT, PersistentDataType.BYTE_ARRAY, left.serializeAsBytes());
        }
        if (right != null) {
            state.set(RIGHT, PersistentDataType.BYTE_ARRAY, right.serializeAsBytes());
        }
    }

    // ---- Anzeige ----

    @Override
    public void updateDisplay() {
        showPart("body", MODEL, blockTransform(0));
        showTool("left", left, -0.2f);
        showTool("right", right, 0.2f);
    }

    /**
     * KitchenwareRacksBlockEntityRender: YN(Richtung * 90), dann seitlich versetzt, schräg aufgehängt.
     */
    private void showTool(String name, @Nullable ItemStack stack, float x) {
        if (stack == null) {
            hidePart(name);
            return;
        }
        Quaternionf facingRotation = new Quaternionf().rotationY((float) Math.toRadians(-facing().dataValue() * 90));
        Vector3f translation = facingRotation.transform(new Vector3f(x, 0.4375f, -0.3f)).add(0, -0.5f, 0);
        Quaternionf rotation = new Quaternionf(facingRotation)
                .rotateX((float) Math.toRadians(-180))
                .rotateY((float) Math.toRadians(25))
                .rotateZ((float) Math.toRadians(-45));
        showItemPart(name, stack, new Transformation(translation, rotation, new Vector3f(0.75f), new Quaternionf()), 0, null);
    }

    @Override
    public String describe() {
        return "Küchengeräteregal";
    }

    // ---- Interaktion ----

    /** Werkzeuge aus c:tools (NeoForge), die keinen eigenen Vanilla-Tag haben */
    private static final java.util.Set<Material> OTHER_TOOLS = java.util.EnumSet.of(Material.BOW, Material.CROSSBOW, Material.FISHING_ROD,
            Material.SHEARS, Material.SHIELD, Material.TRIDENT, Material.BRUSH, Material.MACE, Material.FLINT_AND_STEEL);

    /**
     * Werkzeug im Sinne von c:tools (Schwerter, Äxte, Spitzhacken, Schaufeln, Hacken, Speere, Bogen, Angel, Schere ...).
     * Von den eigenen Items: Küchenmesser, Sichel und Pfannenwender (im Mod in #minecraft:swords bzw. #minecraft:shovels).
     */
    private static boolean isTool(ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        if (id != null) {
            return QuickKnifeListener.isKnife(stack) || id.equals(SickleListener.ITEM) || id.equals(OilItems.KITCHEN_SHOVEL);
        }
        Material type = stack.getType();
        return Tag.ITEMS_SWORDS.isTagged(type) || Tag.ITEMS_AXES.isTagged(type) || Tag.ITEMS_PICKAXES.isTagged(type)
                || Tag.ITEMS_SHOVELS.isTagged(type) || Tag.ITEMS_HOES.isTagged(type) || Tag.ITEMS_SPEARS.isTagged(type) || OTHER_TOOLS.contains(type);
    }

    /**
     * Seite wie KitchenwareRacksBlock#useItemOn: Klickpunkt in die Blickrichtung des Regals gedreht, x &gt; 0 = links.
     */
    private boolean clickedLeft() {
        Vector click = clickPosition();
        if (click == null) {
            return true;
        }
        // Direction#getOpposite().toYRot(): Süden 0, Westen 90, Norden 180, Osten 270
        double yRot = Math.toRadians(switch (facing()) {
            case SOUTH -> 180;
            case WEST -> 270;
            case NORTH -> 0;
            case EAST -> 90;
        });
        double x = click.getX() * Math.cos(yRot) + click.getZ() * Math.sin(yRot);
        return x > 0;
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean isLeft = clickedLeft();
        ItemStack stored = isLeft ? left : right;
        if (held.isEmpty() && stored != null) {
            for (ItemStack rest : player.getInventory().addItem(stored).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
            set(isLeft, null);
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
            return true;
        }
        if (!held.isEmpty() && stored == null && isTool(held)) {
            set(isLeft, held.asOne());
            held.subtract();
            world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
            return true;
        }
        return false;
    }

    private void set(boolean isLeft, @Nullable ItemStack stack) {
        if (isLeft) {
            left = stack;
        } else {
            right = stack;
        }
        markDirty();
        updateDisplay();
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        if (left != null) {
            contents.add(left);
        }
        if (right != null) {
            contents.add(right);
        }
        return contents;
    }
}
