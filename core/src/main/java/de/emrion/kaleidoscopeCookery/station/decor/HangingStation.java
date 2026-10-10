package de.emrion.kaleidoscopeCookery.station.decor;

import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chili ristra and strung mushrooms (ChiliRistraBlock/StrungMushroomsBlock in the mod): hang below a solid block or from each other.
 * <ul>
 *     <li>Right-click with an empty hand or the fruit: harvest 3, the second time the ristra is gone</li>
 *     <li>Breaking gives 6 fruits (3 if harvested)</li>
 *     <li>Chili ristra: undead inside it take damage</li>
 * </ul>
 */
public class HangingStation extends Station {

    public enum Kind {
        CHILI_RISTRA("chili_ristra", Key.key("kaleidoscope_cookery", "red_chili"), true),
        STRUNG_MUSHROOMS("strung_mushrooms", Key.key("minecraft", "brown_mushroom"), false);

        private final String name;
        private final Key fruit;
        private final boolean hurtsUndead;

        Kind(String name, Key fruit, boolean hurtsUndead) {
            this.name = name;
            this.fruit = fruit;
            this.hurtsUndead = hurtsUndead;
        }

        public Key item() {
            return Key.key("kaleidoscope_cookery", name);
        }
    }

    private static final NamespacedKey SHEARED = Keys.key("sheared");

    private final Kind kind;
    private boolean sheared;
    private boolean head = true;

    public HangingStation(StationContext context, Kind kind) {
        super(context);
        this.kind = kind;
    }

    public static StationType type(Kind kind) {
        return new StationType(kind.name, kind.item(), false, 0.5625f, 1f, false, context -> new HangingStation(context, kind));
    }

    @Override
    protected void load(PersistentDataContainer state) {
        sheared = state.getOrDefault(SHEARED, PersistentDataType.BOOLEAN, false);
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(SHEARED, PersistentDataType.BOOLEAN, sheared);
    }

    private @Nullable HangingStation below() {
        return context.manager().at(position().below()) instanceof HangingStation other && other.kind == kind ? other : null;
    }

    @Override
    public void updateDisplay() {
        head = below() == null;
        String model = (head ? "head" : "body") + (sheared ? "_sheared" : "");
        showPart("body", Key.key("kaleidoscope_cookery", "block/" + kind.name + "/" + model), blockTransform(0));
    }

    @Override
    public String describe() {
        return kind.name + (sheared ? " (abgeerntet)" : "");
    }

    /**
     * canSurvive: the same ristra above or a block with a solid bottom face.
     */
    @Override
    public boolean canSurvive() {
        if (context.manager().at(position().above()) instanceof HangingStation other && other.kind == kind) {
            return true;
        }
        Block above = world().getBlockAt(position().x(), position().y() + 1, position().z());
        return context.manager().at(above) == null && above.getBlockData().isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL);
    }

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!held.isEmpty() && !kind.fruit.equals(typeOf(held))) {
            return false;
        }
        ItemStack fruit = items().create(kind.fruit, 3);
        world().playSound(center(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.4f);
        world().spawnParticle(Particle.BLOCK, center(), 20, 0.25, 0.25, 0.25, 0.05,
                (kind == Kind.CHILI_RISTRA ? Material.RED_WOOL : Material.BROWN_MUSHROOM_BLOCK).createBlockData());
        if (sheared) {
            context.manager().remove(this);
        } else {
            sheared = true;
            markDirty();
            updateDisplay();
        }
        if (fruit != null) {
            if (held.isEmpty()) {
                player.getInventory().setItemInMainHand(fruit);
            } else {
                for (ItemStack rest : player.getInventory().addItem(fruit).values()) {
                    player.getWorld().dropItem(player.getLocation(), rest);
                }
            }
        }
        return true;
    }

    private static @Nullable Key typeOf(ItemStack stack) {
        Key id = CookeryItems.idOf(stack);
        return id != null ? id : stack.getType().key();
    }

    /**
     * Loot table: only fruits (6, 3 if harvested), not the ristra itself.
     */
    @Override
    public @Nullable ItemStack selfDrop() {
        return null;
    }

    @Override
    public List<ItemStack> contents() {
        ItemStack fruit = items().create(kind.fruit, sheared ? 3 : 6);
        return fruit == null ? List.of() : List.of(fruit);
    }

    @Override
    public boolean breaksOnAttack() {
        return true;
    }

    @Override
    public boolean isTicking() {
        return true;
    }

    @Override
    public void tick(long serverTick) {
        if (serverTick % 10 != 0) {
            return;
        }
        if (serverTick % 20 == 0) {
            if (!canSurvive()) {
                context.manager().breakStation(this, null);
                return;
            }
            if (head != (below() == null)) {
                updateDisplay();
            }
        }
        if (kind.hurtsUndead) {
            // entityInside: undead in the ristra take 2 damage (damage cooldown 10 ticks)
            BoundingBox box = BoundingBox.of(world().getBlockAt(position().x(), position().y(), position().z()));
            for (Entity entity : world().getNearbyEntities(box, e -> e instanceof Mob && Tag.ENTITY_TYPES_UNDEAD.isTagged(e.getType()))) {
                ((LivingEntity) entity).damage(2, DamageSource.builder(DamageType.MAGIC).build());
            }
        }
    }
}
