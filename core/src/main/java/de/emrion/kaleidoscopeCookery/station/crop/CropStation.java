package de.emrion.kaleidoscopeCookery.station.crop;

import de.emrion.kaleidoscopeCookery.station.BlockKey;
import de.emrion.kaleidoscopeCookery.station.Station;
import de.emrion.kaleidoscopeCookery.station.StationContext;
import de.emrion.kaleidoscopeCookery.station.StationType;
import de.emrion.kaleidoscopeCookery.util.Keys;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Eine Pflanze (Display + Hitbox, kein Block). Wachstum wie bei Vanilla-Pflanzen über zufällige Ticks
 * (gamerule randomTickSpeed) mit der Wachstumsformel von CropBlock; Reis und Teestrauch nach ihren Mod-Klassen.
 * <ul>
 *     <li>Rechtsklick in der letzten Stufe: Ernte (nicht bei Salat und Reis), Pflanze fällt auf eine frühere Stufe zurück</li>
 *     <li>Knochenmehl: Wachstum (Pflanzen 2–5 Stufen, Reis 1–2, Tee 1)</li>
 *     <li>Linksklick: abbauen mit Beute wie in den Loot-Tabellen des Mods</li>
 * </ul>
 */
public class CropStation extends Station {

    private static final NamespacedKey AGE = Keys.key("age");
    private static final NamespacedKey VARIANT = Keys.key("variant");
    private static final Set<EntityType> RICE_BOOSTERS = Set.of(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.PUFFERFISH,
            EntityType.TADPOLE, EntityType.AXOLOTL);
    /** Bonus-Samen: binomial_with_bonus_count extra 3, p = 4/7 (wie Vanilla-Weizen) */
    private static final double SEED_PROBABILITY = 0.5714286;

    private final CropKind kind;
    private int age;
    /** zweites Aussehen (CropKind#variant), beim Pflanzen ausgewürfelt */
    private boolean variant;

    public CropStation(StationContext context, CropKind kind) {
        super(context);
        this.kind = kind;
    }

    public static StationType type(CropKind kind) {
        // Platzhalter-Item: gesetzt werden Pflanzen über CropListener (Samen auf Ackerland/Erde/ins Wasser)
        return new StationType(kind.typeId(), Key.key("kaleidoscope", kind.typeId()), false, 1f, 0.2f, true,
                context -> new CropStation(context, kind));
    }

    public CropKind kind() {
        return kind;
    }

    public int age() {
        return age;
    }

    /**
     * Stufe und Aussehen von außen setzen (z. B. Fischminze in Netherfestungen, Grilling).
     */
    public void setGrowth(int newAge, boolean newVariant) {
        variant = newVariant;
        grow(newAge);
    }

    private boolean isMature() {
        return age >= kind.maxAge();
    }

    @Override
    protected void load(PersistentDataContainer state) {
        age = Math.clamp(state.getOrDefault(AGE, PersistentDataType.INTEGER, 0), 0, kind.maxAge());
        if (kind.variant() != null) {
            Boolean stored = state.get(VARIANT, PersistentDataType.BOOLEAN);
            variant = stored != null ? stored : block(-1).getType() == kind.variant().soil() || ThreadLocalRandom.current().nextDouble() < kind.variant().chance();
        }
    }

    @Override
    protected void save(PersistentDataContainer state) {
        state.set(AGE, PersistentDataType.INTEGER, age);
        if (kind.variant() != null) {
            state.set(VARIANT, PersistentDataType.BOOLEAN, variant);
        }
    }

    @Override
    public List<BlockKey> occupied() {
        if (kind == CropKind.RICE) {
            return List.of(position(), position().above(), position().above().above());
        }
        return List.of(position());
    }

    // ---- Bedingungen ----

    private Block block(int dy) {
        return world().getBlockAt(position().x(), position().y() + dy, position().z());
    }

    /**
     * Rohe Helligkeit wie getRawBrightness(pos, 0): Himmelslicht ohne Tageszeit, Blocklicht.
     */
    private static int rawLight(Block block) {
        return Math.max(block.getLightFromSky(), block.getLightFromBlocks());
    }

    private boolean canSeeSky() {
        return block(0).getLightFromSky() >= 15;
    }

    /**
     * Darf die Pflanze hier stehen (Boden, Licht, bei Reis Wasser)?
     */
    @Override
    public boolean canSurvive() {
        Block below = block(-1);
        return switch (kind.soil()) {
            case FARMLAND -> below.getType() == Material.FARMLAND && (rawLight(block(0)) >= 8 || canSeeSky());
            case DIRT -> CropKind.DIRT_121.contains(below.getType());
            case WATER -> below.getType() == Material.FARMLAND && isWaterSource(block(0)) && (rawLight(block(0)) >= 8 || canSeeSky());
            // HouttuyniaCropBlock#canSurvive
            case FARMLAND_OR_SOUL_SAND -> below.getType() == Material.SOUL_SAND || (below.getType() == Material.FARMLAND && rawLight(block(0)) >= 8);
        };
    }

    public static boolean isWaterSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    // ---- Wachstum ----

    @Override
    public boolean isTicking() {
        return !isMature() || kind == CropKind.RICE;
    }

    @Override
    public void tick(long serverTick) {
        // Zufalls-Tick: jeder Block bekommt pro Tick mit Wahrscheinlichkeit randomTickSpeed/4096 einen
        int speed = world().getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
        if (speed <= 0 || ThreadLocalRandom.current().nextInt(4096) >= speed) {
            return;
        }
        randomTick();
    }

    private void randomTick() {
        if (!canSurvive()) {
            context.manager().breakStation(this, null);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (kind == CropKind.TEA) {
            int chance = isPlantedInRow() ? 4 : 5;
            if (!isMature() && rawLight(block(1)) >= 9 && random.nextInt(chance) == 0) {
                grow(age + 1);
            }
        } else if (kind == CropKind.RICE) {
            {
                if (world().getTime() >= 13000 && world().getTime() < 23000) {
                    world().playSound(block(1).getLocation().toCenterLocation(), "kaleidoscope_cookery:block.paddy", SoundCategory.BLOCKS,
                            random.nextFloat() * 0.2f + 0.2f, random.nextFloat() * 0.1f + 0.9f);
                }
                if (rawLight(block(0)) >= 9 && !isMature()) {
                    // Reis wächst halb so schnell, Fische im 3x3-Feld beschleunigen
                    float speed = growthSpeed() / 2;
                    BoundingBox area = BoundingBox.of(block(0)).expand(1, 0, 1);
                    int fish = world().getNearbyEntities(area, e -> e instanceof LivingEntity living && !living.isDead() && RICE_BOOSTERS.contains(e.getType())).size();
                    if (fish > 0) {
                        speed += speed * (float) (Math.log(fish) / Math.log(2));
                    }
                    if (random.nextInt((int) (25.0f / speed) + 1) == 0) {
                        grow(age + 1);
                    }
                }
            }
        } else if (rawLight(block(0)) >= 9 && !isMature() && random.nextInt((int) (25.0f / growthSpeed()) + 1) == 0) {
            grow(age + 1);
        }
    }

    /**
     * CropBlock#getGrowthSpeed: feuchtes Ackerland drumherum beschleunigt, gleiche Pflanzen in beiden Richtungen bremsen.
     */
    private float growthSpeed() {
        float speed = 1;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block below = world().getBlockAt(position().x() + x, position().y() - 1, position().z() + z);
                float value = 0;
                if (below.getType() == Material.FARMLAND) {
                    value = 1;
                    if (below.getBlockData() instanceof Farmland farmland && farmland.getMoisture() > 0) {
                        value = 3;
                    }
                }
                if (x != 0 || z != 0) {
                    value /= 4;
                }
                speed += value;
            }
        }
        boolean westEast = sameCrop(-1, 0) || sameCrop(1, 0);
        boolean northSouth = sameCrop(0, -1) || sameCrop(0, 1);
        if (westEast && northSouth) {
            speed /= 2;
        } else if (sameCrop(-1, -1) || sameCrop(1, -1) || sameCrop(1, 1) || sameCrop(-1, 1)) {
            speed /= 2;
        }
        return speed;
    }

    private boolean sameCrop(int dx, int dz) {
        return context.manager().at(position().relative(dx, 0, dz)) instanceof CropStation other && other.kind == kind;
    }

    /**
     * TeaTreeBlock#isPlantedInRow: Nachbar nur in einer Achse.
     */
    private boolean isPlantedInRow() {
        boolean x = sameCrop(-1, 0) || sameCrop(1, 0);
        boolean z = sameCrop(0, -1) || sameCrop(0, 1);
        return x != z;
    }

    private void grow(int newAge) {
        age = Math.min(kind.maxAge(), newAge);
        markDirty();
        updateDisplay();
        context.manager().updateTicking(this);
    }

    // ---- Bedienung ----

    @Override
    public boolean onInteract(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.BONE_MEAL && !de.emrion.kaleidoscopeCookery.item.CookeryItems.isCustom(held)) {
            return bonemeal(player, held);
        }
        if (!isMature() || kind.harvestAge() < 0) {
            return false;
        }
        Key drop = kind.result();
        if (kind == CropKind.CHILI && ThreadLocalRandom.current().nextInt(5) == 0) {
            drop = Key.key("kaleidoscope_cookery", "green_chili"); // 20 % grüne Chili
        }
        ItemStack stack = items().create(drop, 1);
        if (stack != null) {
            world().dropItemNaturally(center(), stack);
        }
        world().playSound(center(), Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1, 1);
        grow(kind.harvestAge());
        return true;
    }

    /**
     * Ernte mit der Sichel (SickleItem#harvest): reife Pflanze abbauen und neu anfangen.
     *
     * @return true, wenn geerntet wurde
     */
    public boolean sickleHarvest() {
        if (!isMature()) {
            return false;
        }
        for (ItemStack drop : loot(0)) {
            world().dropItemNaturally(center(), drop);
        }
        world().playSound(center(), Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1, 1);
        world().spawnParticle(Particle.BLOCK, center(), 20, 0.25, 0.25, 0.25, 0, Material.WHEAT.createBlockData());
        grow(0);
        return true;
    }

    private boolean bonemeal(Player player, ItemStack held) {
        if (isMature()) {
            return false;
        }
        int increase = kind == CropKind.RICE ? ThreadLocalRandom.current().nextInt(1, 3)
                : kind == CropKind.TEA ? 1 : ThreadLocalRandom.current().nextInt(2, 6);
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        world().spawnParticle(Particle.HAPPY_VILLAGER, center(), 12, 0.3, 0.3, 0.3, 0);
        world().playSound(center(), Sound.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 1, 1);
        grow(age + increase);
        return true;
    }

    /**
     * Pflanzen gehen mit einem normalen Linksklick kaputt (instabreak).
     */
    @Override
    public boolean breaksOnAttack() {
        return true;
    }

    @Override
    public List<ItemStack> contents(@Nullable Player breaker) {
        return loot(breaker == null ? 0 : fortuneOf(breaker));
    }

    @Override
    public @Nullable ItemStack selfDrop() {
        return null;
    }

    /**
     * Beute wie in den Loot-Tabellen (blocks/tomato_crop.json usw.).
     */
    @Override
    public List<ItemStack> contents() {
        return loot(0);
    }

    public List<ItemStack> loot(int fortune) {
        List<ItemStack> drops = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!isMature()) {
            add(drops, kind.lootSeed(), 1);
            return drops;
        }
        int resultCount = kind == CropKind.RICE ? random.nextInt(2, 5) : 1;
        add(drops, kind.result(), resultCount);
        int seeds = kind.matureExtra();
        for (int i = 0; i < kind.seedTrials() + fortune; i++) {
            if (random.nextDouble() < SEED_PROBABILITY) {
                seeds++;
            }
        }
        add(drops, kind.lootSeed(), seeds);
        if (kind == CropKind.LETTUCE && random.nextDouble() < 0.1) {
            add(drops, Key.key("kaleidoscope_cookery", "caterpillar"), 1);
        }
        return drops;
    }

    private void add(List<ItemStack> drops, Key item, int count) {
        if (count <= 0) {
            return;
        }
        ItemStack stack = items().create(item, count);
        if (stack != null) {
            drops.add(stack);
        }
    }

    public int fortuneOf(Player player) {
        return player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE);
    }

    // ---- Darstellung ----

    @Override
    public void updateDisplay() {
        if (kind == CropKind.RICE) {
            showPart("down", kind.model(age, "down"), translated(0, 0, 0));
            showPart("middle", kind.model(age, "middle"), translated(0, 1, 0));
            showPart("up", kind.model(age, "up"), translated(0, 2, 0));
            root().setInteractionHeight(1f);
            showHitbox("middle", new Vector(0, 1, 0), 0.75f, 1f);
            if (age > 3) {
                showHitbox("up", new Vector(0, 2, 0), 0.75f, (2 + (age - 4) * 2) / 16f + 0.05f);
            } else {
                hideHitbox("up");
            }
            return;
        }
        Vector3f offset = kind == CropKind.TEA ? randomOffset() : new Vector3f();
        boolean showVariant = variant && kind.variant() != null && age >= kind.variant().fromAge();
        showPart("plant", kind.model(age, showVariant ? kind.variant().suffix() : null), translated(offset.x, offset.y, offset.z));
        float height = kind == CropKind.TEA ? (4 + age) / 16f : (2 + age) / 16f;
        if (Math.abs(root().getInteractionHeight() - height) > 0.001f) {
            root().setInteractionHeight(height);
        }
    }

    private static Transformation translated(float x, float y, float z) {
        return new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    /**
     * Zufälliger Versatz wie OffsetType.XZ (Block#getOffset, maximal 0,25 Blöcke).
     */
    private Vector3f randomOffset() {
        long seed = (long) (position().x() * 3129871) ^ (long) position().z() * 116129781L;
        seed = seed * seed * 42317861L + seed * 11L;
        seed >>= 16;
        float x = ((float) (seed & 15L) / 15.0f - 0.5f) * 0.5f;
        float z = ((float) (seed >> 8 & 15L) / 15.0f - 0.5f) * 0.5f;
        return new Vector3f(Math.clamp(x, -0.25f, 0.25f), 0, Math.clamp(z, -0.25f, 0.25f));
    }

    @Override
    public String describe() {
        return "Pflanze " + kind.id() + " (Stufe " + age + "/" + kind.maxAge() + ")";
    }
}
