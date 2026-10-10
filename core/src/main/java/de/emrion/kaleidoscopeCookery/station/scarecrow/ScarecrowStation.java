package de.emrion.kaleidoscopeCookery.station.scarecrow;

import de.emrion.kaleidoscopeCookery.station.BlockKey;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.ItemDisplay;
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
 * Vogelscheuche (ScarecrowEntity im Mod, hier Station mit Hitbox statt Entity):
 * <ul>
 *     <li>steht in 8 Richtungen (45°-Schritte), schützt Ackerland in 16 Blöcken vor dem Zertrampeln</li>
 *     <li>Rechtsklick auf die Arme: Laterne in die linke Hand (leuchtet, Lichtstärke 12), Werkzeug/Waffe in die rechte;
 *     mit leerer Hand zuerst die rechte, dann die linke Hand leeren</li>
 *     <li>Rechtsklick auf den Kopf: Mob- oder Spielerkopf aufsetzen bzw. mit leerer Hand abnehmen</li>
 *     <li>zweimal schnell schlagen (wie ein Rüstungsständer) baut sie ab, im Kreativmodus einmal</li>
 * </ul>
 * Papageien auf der Schulter gibt es nicht (dafür bräuchte es ein echtes Entity).
 */
public class ScarecrowStation extends Station {

    public static final String TYPE_ID = "scarecrow";
    public static final Key ITEM = Key.key("kaleidoscope_cookery", "scarecrow");
    private static final Key BODY = Key.key("kaleidoscope_cookery", "block/scarecrow_body");
    private static final Key HEAD = Key.key("kaleidoscope_cookery", "block/scarecrow_head");
    /** Modell auf 1/2 verkleinert (tools/gen_entity_models.py) */
    private static final float MODEL_SCALE = 2;
    /** ScarecrowEntity#isClickHand / #isClickHead: Höhe über dem Boden in Blöcken */
    private static final double HAND_MIN = 17 / 16.0;
    private static final double HAND_MAX = 27 / 17.0;
    /** Arme in Blöcken relativ zur Blockmitte bei Blick nach Norden (rechte Hand im Osten) */
    private static final Vector3f RIGHT_HAND = new Vector3f(0.75f, (25.77f - 8) / 16, 0);
    private static final Vector3f LEFT_HAND = new Vector3f(-0.75f, (25.77f - 8) / 16, 0);
    /** Kopf: Unterkante 27 Pixel über dem Boden, 9 Pixel groß */
    private static final float HEAD_SCALE = 9 / 8f;
    private static final Vector3f HEAD_POSITION = new Vector3f(0, 27 / 16f + 0.5f * HEAD_SCALE - 0.5f, 0.0156f);
    private static final int LIGHT_LEVEL = 12;

    private static final NamespacedKey YAW = Keys.key("yaw");
    private static final NamespacedKey MAIN_HAND = Keys.key("main_hand");
    private static final NamespacedKey OFF_HAND = Keys.key("off_hand");
    private static final NamespacedKey HEAD_ITEM = Keys.key("head_item");

    private float yaw = 180;
    private @Nullable ItemStack mainHand;
    private @Nullable ItemStack offHand;
    private @Nullable ItemStack head;
    private long lastHit = Long.MIN_VALUE / 2;
    private long cooldownUntil;

    public ScarecrowStation(StationContext context) {
        super(context);
    }

    public static StationType type() {
        // 1 x 3 Blöcke (Entity 0,5 x 2,375): die Blöcke darüber müssen frei sein
        return new StationType(TYPE_ID, ITEM, false, 0.5f, 2.375f, false, ScarecrowStation::new, 1, 3);
    }

    /**
     * ScarecrowItem#useOn: in 45°-Schritten zum Spieler gedreht.
     */
    public void initYaw(float playerYaw) {
        float wrapped = (float) Math.IEEEremainder(playerYaw - 180, 360);
        yaw = (float) Math.floor((wrapped + 22.5f) / 45f) * 45f;
        markDirty();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        yaw = state.getOrDefault(YAW, PersistentDataType.FLOAT, 180f);
        mainHand = item(state, MAIN_HAND);
        offHand = item(state, OFF_HAND);
        head = item(state, HEAD_ITEM);
    }

    private static @Nullable ItemStack item(PersistentDataContainer state, NamespacedKey key) {
        byte[] bytes = state.get(key, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(YAW, PersistentDataType.FLOAT, yaw);
        store(state, MAIN_HAND, mainHand);
        store(state, OFF_HAND, offHand);
        store(state, HEAD_ITEM, head);
    }

    private static void store(PersistentDataContainer state, NamespacedKey key, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            state.remove(key);
        } else {
            state.set(key, PersistentDataType.BYTE_ARRAY, stack.serializeAsBytes());
        }
    }

    // ---- Darstellung ----

    private Quaternionf rotation() {
        // ScarecrowRender#setupRotations: YP(180 - yRot); die Modelle sind für yRot = 180 (Blick nach Norden) gebaut
        return new Quaternionf().rotationY((float) Math.toRadians(180 - yaw));
    }

    @Override
    public void updateDisplay() {
        Quaternionf rotation = rotation();
        showPart("body", BODY, new Transformation(new Vector3f(), rotation, new Vector3f(MODEL_SCALE), new Quaternionf()));
        if (head == null) {
            hidePart("head_item");
            showPart("head", HEAD, new Transformation(new Vector3f(), rotation, new Vector3f(MODEL_SCALE), new Quaternionf()));
        } else {
            hidePart("head");
            // Kopf-Item mit Profil (Spielerköpfe), Gesicht nach vorn
            showPart("head_item", new PartLook(head.asOne(), ItemDisplay.ItemDisplayTransform.NONE,
                    new Transformation(rotation.transform(new Vector3f(HEAD_POSITION)), new Quaternionf(rotation).rotateY((float) Math.PI),
                            new Vector3f(HEAD_SCALE), new Quaternionf()), null), 0);
        }
        if (mainHand == null) {
            hidePart("main_hand");
        } else {
            // in der Hand: Stiel unten, Kopf oben und 20° nach außen geneigt, Vorderseite nach vorn, am Stiel gehalten
            showItemPart("main_hand", mainHand, new Transformation(rotation.transform(new Vector3f(RIGHT_HAND).add(0, 0.3f, 0)),
                    new Quaternionf(rotation).rotateY((float) Math.PI).rotateZ((float) Math.toRadians(-25)), new Vector3f(0.75f),
                    new Quaternionf()), 0, null);
        }
        Key lantern = lanternModel(offHand);
        if (lantern == null) {
            hidePart("off_hand");
        } else {
            // Laterne hängt mit dem Griff an der linken Hand
            Vector3f at = rotation.transform(new Vector3f(LEFT_HAND).add(0, -0.16f, 0));
            showPart("off_hand", lantern, new Transformation(at, rotation, new Vector3f(0.75f), new Quaternionf()));
        }
        updateLight();
    }

    /**
     * Blockmodell der Laterne (LanternBlock#defaultBlockState, stehend); gewachste Kupferlaternen wie ungewachste.
     */
    private static @Nullable Key lanternModel(@Nullable ItemStack stack) {
        if (stack == null || !Tag.ITEMS_LANTERNS.isTagged(stack.getType())) {
            return null;
        }
        return Key.key("kaleidoscope_cookery", "block/scarecrow/" + stack.getType().key().value().replace("waxed_", ""));
    }

    /**
     * ScarecrowEntity#tick: mit Laterne Lichtblock (12) über dem Fuß, sonst wieder Luft.
     */
    private void updateLight() {
        Block block = lightBlock();
        if (block == null) {
            return;
        }
        if (offHand != null && Tag.ITEMS_LANTERNS.isTagged(offHand.getType())) {
            if (block.getType().isAir()) {
                block.setType(Material.LIGHT, false);
                if (block.getBlockData() instanceof Light light) {
                    light.setLevel(LIGHT_LEVEL);
                    block.setBlockData(light, false);
                }
            }
        } else if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    private @Nullable Block lightBlock() {
        BlockKey above = position().relative(0, 1, 0);
        return above.block();
    }

    @Override
    protected void onRemoved() {
        Block block = lightBlock();
        if (block != null && block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
    }

    // ---- Bedienung ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        long now = context.manager().serverTick();
        Vector click = clickPosition();
        if (now < cooldownUntil || click == null) {
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean done;
        if (click.getY() > HAND_MAX) {
            done = interactHead(player, held);
        } else if (click.getY() >= HAND_MIN) {
            done = interactHands(player, held);
        } else {
            return false;
        }
        if (done) {
            cooldownUntil = now + 5;
            markDirty();
            updateDisplay();
        }
        return done;
    }

    private boolean interactHands(Player player, ItemStack held) {
        if (held.isEmpty()) {
            if (mainHand != null) {
                give(player, mainHand);
                mainHand = null;
                return true;
            }
            if (offHand != null) {
                give(player, offHand);
                offHand = null;
                return true;
            }
            return false;
        }
        if (Tag.ITEMS_LANTERNS.isTagged(held.getType())) {
            ItemStack previous = offHand;
            ItemStack placed = swap(player, held, previous);
            if (placed != null) {
                offHand = placed;
                world().playSound(center(), Sound.BLOCK_LANTERN_PLACE, SoundCategory.BLOCKS, 1, 1);
                return true;
            }
        }
        if (held.hasData(DataComponentTypes.DAMAGE)) {
            ItemStack placed = swap(player, held, mainHand);
            if (placed != null) {
                mainHand = placed;
                world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
                return true;
            }
        }
        return false;
    }

    private boolean interactHead(Player player, ItemStack held) {
        if (held.isEmpty()) {
            if (head == null) {
                return false;
            }
            give(player, head);
            head = null;
            return true;
        }
        if (!Tag.ITEMS_SKULLS.isTagged(held.getType())) {
            return false;
        }
        ItemStack placed = swap(player, held, head);
        if (placed == null) {
            return false;
        }
        head = placed;
        world().playSound(center(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
        return true;
    }

    /**
     * ScarecrowEntity#swapHand: Kreativ in einen leeren Platz eine Kopie; aus einem Stapel nur in einen leeren Platz
     * eines davon; ein einzelnes Item tauscht mit dem bisherigen.
     *
     * @return das neu angelegte Item, null wenn nichts passiert
     */
    private static @Nullable ItemStack swap(Player player, ItemStack held, @Nullable ItemStack previous) {
        if (player.getGameMode() == GameMode.CREATIVE && previous == null) {
            return held.asOne();
        }
        if (held.getAmount() > 1) {
            if (previous != null) {
                return null;
            }
            ItemStack one = held.asOne();
            held.subtract();
            return one;
        }
        ItemStack placed = held.clone();
        player.getInventory().setItemInMainHand(previous);
        return placed;
    }

    private void give(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            world().dropItem(player.getLocation(), leftover);
        }
    }

    @Override
    public boolean onAttack(Player player) {
        long now = context.manager().serverTick();
        if (player.getGameMode() == GameMode.CREATIVE || now - lastHit <= 5) {
            world().spawnParticle(Particle.BLOCK, center().add(0, 1.08, 0), 10, 0.125, 0.6, 0.125, 0.05,
                    Material.OAK_PLANKS.createBlockData());
            world().playSound(center(), Sound.ENTITY_ARMOR_STAND_BREAK, SoundCategory.BLOCKS, 1, 1);
            context.manager().breakStation(this, player);
            return true;
        }
        lastHit = now;
        world().playSound(center(), Sound.ENTITY_ARMOR_STAND_HIT, SoundCategory.BLOCKS, 0.3f, 1);
        return true;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack stack : new ItemStack[]{mainHand, offHand, head}) {
            if (stack != null && !stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    @Override
    public String describe() {
        return "Vogelscheuche (" + yaw + "°)";
    }
}
