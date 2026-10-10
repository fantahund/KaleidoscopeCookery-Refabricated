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
 * A crop (display + hitbox, no block). Grows like vanilla crops via random ticks
 * (gamerule randomTickSpeed) with CropBlock's growth formula; rice and tea bush following their mod classes.
 * <ul>
 *     <li>Right-click in the last stage: harvest (not for lettuce and rice), the crop falls back to an earlier stage</li>
 *     <li>Bone meal: growth (crops 2-5 stages, rice 1-2, tea 1)</li>
 *     <li>Left click: break with loot as in the mod's loot tables</li>
 * </ul>
 */
public class CropStation extends Station {

    private static final NamespacedKey AGE = Keys.key("age");
    private static final NamespacedKey VARIANT = Keys.key("variant");
    private static final Set<EntityType> RICE_BOOSTERS = Set.of(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.PUFFERFISH,
            EntityType.TADPOLE, EntityType.AXOLOTL);
    /** Bonus seeds: binomial_with_bonus_count extra 3, p = 4/7 (like vanilla wheat) */
    private static final double SEED_PROBABILITY = 0.5714286;

    private final CropKind kind;
    private int age;
    /** Second look (CropKind#variant), rolled when planting */
    private boolean variant;

    public CropStation(StationContext context, CropKind kind) {
        super(context);
        this.kind = kind;
    }

    public static StationType type(CropKind kind) {
        // Placeholder item: crops are planted via the CropListener (seeds on farmland/dirt/into water)
        return new StationType(kind.typeId(), Key.key("kaleidoscope", kind.typeId()), false, 1f, 0.2f, true,
                context -> new CropStation(context, kind));
    }

    /** Fields have hundreds of crops: render only up to 48 blocks (view_range 0.75 × 64, at 100% entity distance) */
    @Override
    protected float viewRange() {
        return 0.75f;
    }

    public CropKind kind() {
        return kind;
    }

    public int age() {
        return age;
    }

    /**
     * Set stage and look from outside (e.g. houttuynia in Nether fortresses, Grilling).
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

    // ---- Conditions ----

    private Block block(int dy) {
        return world().getBlockAt(position().x(), position().y() + dy, position().z());
    }

    /**
     * Raw brightness like getRawBrightness(pos, 0): sky light without time of day, block light.
     */
    private static int rawLight(Block block) {
        return Math.max(block.getLightFromSky(), block.getLightFromBlocks());
    }

    private boolean canSeeSky() {
        return block(0).getLightFromSky() >= 15;
    }

    /**
     * May the crop stand here (soil, light, water for rice)?
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

    // ---- Growth ----

    /** No tick every tick: the next random tick is rolled in advance and scheduled (scheduleNext) */
    @Override
    public boolean isTicking() {
        return false;
    }

    private boolean scheduled;

    @Override
    protected void onAttached() {
        scheduleNext();
    }

    /**
     * Schedule the next random tick: vanilla gives every block one per tick with p = randomTickSpeed/4096; the waiting time
     * until the next one is geometrically distributed (each crop rolls for itself, on average 4096/speed ticks).
     */
    private void scheduleNext() {
        if (scheduled || (isMature() && kind != CropKind.RICE)) {
            return;
        }
        int speed = world().getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
        long delay;
        if (speed <= 0) {
            delay = 200; // random ticks disabled: check again later
        } else {
            double p = Math.min(1.0, speed / 4096.0);
            double u = ThreadLocalRandom.current().nextDouble();
            delay = p >= 1.0 ? 1 : 1 + (long) Math.floor(Math.log(1 - u) / Math.log(1 - p));
        }
        scheduled = true;
        context.manager().schedule(this, delay);
    }

    @Override
    public void scheduledTick(long serverTick) {
        scheduled = false;
        int speed = world().getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
        // Like vanilla: random ticks only in chunks with entity ticking (a player's simulation distance)
        org.bukkit.Chunk chunk = world().getChunkAt(position().x() >> 4, position().z() >> 4);
        if (speed > 0 && chunk.getLoadLevel() == org.bukkit.Chunk.LoadLevel.ENTITY_TICKING) {
            randomTick();
        }
        if (!isRemoved()) {
            scheduleNext();
        }
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
                    // Rice grows half as fast, fish in the 3x3 area speed it up
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
     * CropBlock#getGrowthSpeed: moist farmland around speeds it up, identical crops in both directions slow it down.
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
     * TeaTreeBlock#isPlantedInRow: neighbour in one axis only.
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
        scheduleNext(); // it grows again after the harvest
    }

    // ---- Handling ----

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
            drop = Key.key("kaleidoscope_cookery", "green_chili"); // 20% green chili
        }
        ItemStack stack = items().create(drop, kind.harvestMax() > 1 ? ThreadLocalRandom.current().nextInt(1, kind.harvestMax() + 1) : 1);
        if (stack != null) {
            world().dropItemNaturally(center(), stack);
        }
        world().playSound(center(), Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1, 1);
        grow(kind.harvestAge());
        return true;
    }

    /**
     * Harvest with the sickle (SickleItem#harvest): break a ripe crop and start over.
     *
     * @return true if it was harvested
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
     * Crops break with a normal left click (instabreak).
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
     * Loot as in the loot tables (blocks/tomato_crop.json etc.).
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
        if (kind == CropKind.CHILI && random.nextDouble() < 0.2) {
            add(drops, Key.key("kaleidoscope_cookery", "green_chili"), 1); // blocks/chili_crop.json: 20% extra green chili
        }
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

    // ---- Display ----

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
     * Random offset like OffsetType.XZ (Block#getOffset, at most 0.25 blocks).
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
