package de.emrion.kaleidoscopeCookery.station.trash;

import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.station.seat.Seats;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mülleimer (TrashCanBlock/TrashCanBlockEntity im Mod).
 * <ul>
 *     <li>Rechtsklick mit einem Item wirft den ganzen Stapel hinein; sind alle 3 Plätze voll,
 *     fällt das älteste Item endgültig weg</li>
 *     <li>Schleichen + Rechtsklick mit leerer Hand holt das zuletzt Weggeworfene zurück</li>
 *     <li>Mit Redstone-Signal verschwinden Items, die oben auf dem Deckel liegen und schon im Eimer sind</li>
 *     <li>Wer aus mehr als einem Block Höhe hineinfällt, versteckt sich darin – Monster verlieren ihn aus den Augen</li>
 * </ul>
 */
public class TrashCanStation extends Station {

    public static final Key ITEM = Key.key("kaleidoscope_cookery", "trash_can");
    private static final Key MODEL = Key.key("kaleidoscope_cookery", "block/trash_can_model");
    private static final int SLOTS = 3;
    private static final NamespacedKey STORAGE = Keys.key("storage");
    /** Sitzhöhe im Eimer (SitEntity mit 0,875) */
    private static final double SEAT_HEIGHT = 0.875;

    private final @Nullable ItemStack[] storage = new ItemStack[SLOTS];
    private boolean powered;

    public TrashCanStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        return new StationType("trash_can", ITEM, true, 1f, 1f, false, TrashCanStation::new);
    }

    @Override
    protected void load(PersistentDataContainer state) {
        Arrays.fill(storage, null);
        List<byte[]> stored = state.getOrDefault(STORAGE, PersistentDataType.LIST.byteArrays(), List.of());
        for (int i = 0; i < Math.min(stored.size(), SLOTS); i++) {
            storage[i] = stored.get(i).length == 0 ? null : ItemStack.deserializeBytes(stored.get(i));
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        List<byte[]> stored = new ArrayList<>();
        for (ItemStack item : storage) {
            stored.add(item == null ? new byte[0] : item.serializeAsBytes());
        }
        state.set(STORAGE, PersistentDataType.LIST.byteArrays(), stored);
    }

    @Override
    public void updateDisplay() {
        showPart("body", MODEL, new Transformation(new Vector3f(), facing().blockRotation(), new Vector3f(1), new Quaternionf()));
    }

    @Override
    public String describe() {
        int count = 0;
        for (ItemStack item : storage) {
            count += item == null ? 0 : 1;
        }
        return "Mülleimer (" + count + "/" + SLOTS + ", " + (powered ? "an" : "aus") + ")";
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : storage) {
            if (item != null) {
                contents.add(item);
            }
        }
        return contents;
    }

    // ---- Interaktion ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty() && player.isSneaking()) {
            withdraw(player);
            return true;
        }
        if (!held.isEmpty()) {
            put(held);
            return true;
        }
        return false;
    }

    /**
     * TrashCanBlockEntity#putItem: erst auf gleiche Stapel bzw. freie Plätze; passt nichts, rückt alles auf und das älteste fällt weg.
     * Der Stapel wird immer ganz verbraucht (auch im Kreativmodus, wie im Mod).
     */
    private void put(ItemStack stack) {
        int before = stack.getAmount();
        ItemStack copy = stack.clone();
        for (ItemStack item : storage) {
            if (item != null && item.isSimilar(copy) && item.getAmount() < item.getMaxStackSize()) {
                int moved = Math.min(copy.getAmount(), item.getMaxStackSize() - item.getAmount());
                item.add(moved);
                copy.subtract(moved);
            }
        }
        for (int i = 0; i < SLOTS && !copy.isEmpty(); i++) {
            if (storage[i] == null) {
                storage[i] = copy.clone();
                copy.setAmount(0);
            }
        }
        if (copy.getAmount() == before) {
            storage[0] = storage[1];
            storage[1] = storage[2];
            storage[2] = stack.clone();
            stack.setAmount(0);
        } else {
            stack.setAmount(copy.getAmount());
        }
        effects(Sound.BLOCK_BARREL_OPEN, 0.5f);
        markDirty();
    }

    /**
     * TrashCanBlockEntity#withdrawItem: das zuletzt Weggeworfene kommt in die Hand.
     */
    private void withdraw(Player player) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (storage[i] != null) {
                player.getInventory().setItemInMainHand(storage[i]);
                storage[i] = null;
                effects(Sound.BLOCK_BARREL_CLOSE, 0.8f);
                markDirty();
                return;
            }
        }
    }

    private void effects(Sound sound, float pitch) {
        Location top = center().add(0, 0.75, 0);
        world().spawnParticle(Particle.SMOKE, top, 3, 0.25, 0.05, 0.25, 0.01);
        world().playSound(center(), sound, SoundCategory.BLOCKS, 1, pitch);
    }

    // ---- Hineinfallen ----

    /**
     * TrashCanBlock#fallOn: Spieler versteckt sich im Eimer, Monster in 32 Blöcken vergessen ihn.
     */
    public void onFallOn(Player player, Seats seats) {
        Location corner = new Location(world(), position().x(), position().y(), position().z());
        if (player.isInsideVehicle() || Seats.isOccupied(world(), corner)) {
            return;
        }
        Location seat = position().bottomCenter(world()).add(0, SEAT_HEIGHT, 0);
        seat.setYaw(switch (facing()) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
        });
        seats.sit(player, seat, "trash_can");
        for (Entity entity : world().getNearbyEntities(center(), 32.5, 32.5, 32.5, e -> e instanceof Mob)) {
            Mob mob = (Mob) entity;
            if (player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    // ---- Tick: Items oben auf dem Deckel einsaugen ----

    @Override
    public boolean isTicking() {
        return true;
    }

    @Override
    public void tick(long serverTick) {
        if (serverTick % 4 != 0) {
            return;
        }
        Block block = world().getBlockAt(position().x(), position().y(), position().z());
        powered = block.isBlockIndirectlyPowered();
        if (!powered) {
            return;
        }
        // SUCK_ZONE (oberster Pixel); die Barriere ist einen Block hoch, Items liegen also auf y + 1
        BoundingBox zone = new BoundingBox(position().x(), position().y() + 0.9375, position().z(),
                position().x() + 1, position().y() + 1.25, position().z() + 1);
        for (Entity entity : world().getNearbyEntities(zone, e -> e instanceof Item)) {
            Item item = (Item) entity;
            if (absorb(item.getItemStack())) {
                world().spawnParticle(Particle.CLOUD, item.getLocation().add(0, 0.25, 0), 1, 0.1, 0.1, 0.1, 0.01);
                item.remove();
            }
        }
    }

    /**
     * TrashCanBlockEntity#hasItem: nur Items, die schon im Eimer sind; passt noch etwas dazu, wird es aufgefüllt.
     */
    private boolean absorb(ItemStack stack) {
        for (ItemStack item : storage) {
            if (item != null && item.getType() == stack.getType()) {
                if (item.isSimilar(stack) && item.getAmount() < item.getMaxStackSize()) {
                    item.setAmount(Math.min(item.getMaxStackSize(), item.getAmount() + stack.getAmount()));
                    markDirty();
                }
                return true;
            }
        }
        return false;
    }
}
