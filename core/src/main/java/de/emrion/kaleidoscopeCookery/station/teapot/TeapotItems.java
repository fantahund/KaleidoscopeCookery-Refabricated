package de.emrion.kaleidoscopeCookery.station.teapot;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The teapot as an item (TeapotItem in the mod): carries tea base or finished tea, scoops from water/lava and pours into cups.
 */
public class TeapotItems implements Listener {

    private final KaleidoscopeCookery plugin;

    public TeapotItems(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    public static boolean isTeapot(@Nullable ItemStack stack) {
        return TeapotStation.ITEM.equals(CookeryItems.idOf(stack));
    }

    /**
     * Tea that can be poured out (getPourOut), otherwise null.
     */
    public static @Nullable ItemStack pourOut(ItemStack teapot) {
        PersistentDataContainerView pdc = teapot.getPersistentDataContainer();
        if (pdc.getOrDefault(TeapotStation.ITEM_STATUS, PersistentDataType.INTEGER, TeapotStation.PUT_INGREDIENT) != TeapotStation.FINISHED) {
            return null;
        }
        byte[] bytes = pdc.get(TeapotStation.ITEM_RESULT, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    /**
     * Pour out one cup (pourOut): write back the rest or empty the pot.
     */
    public static void pourOne(ItemStack teapot) {
        ItemStack result = pourOut(teapot);
        if (result == null) {
            return;
        }
        result.subtract();
        teapot.editPersistentDataContainer(pdc -> {
            if (result.isEmpty()) {
                pdc.remove(TeapotStation.ITEM_STATUS);
                pdc.remove(TeapotStation.ITEM_RESULT);
            } else {
                pdc.set(TeapotStation.ITEM_RESULT, PersistentDataType.BYTE_ARRAY, result.serializeAsBytes());
            }
        });
        updateLore(teapot);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static void updateLore(ItemStack teapot) {
        List<Component> lore = new ArrayList<>();
        PersistentDataContainerView pdc = teapot.getPersistentDataContainer();
        String fluid = pdc.get(TeapotStation.ITEM_FLUID, PersistentDataType.STRING);
        ItemStack result = pourOut(teapot);
        if (result != null) {
            lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_cookery.teapot.statue.result",
                    result.effectiveName().append(Component.text(" ×" + result.getAmount()))).color(NamedTextColor.GRAY)));
        } else if (fluid != null && Key.parseable(fluid)) {
            Material bucket = TeapotStation.bucketOf(Key.key(fluid));
            lore.add(Text.noItalic(Lang.raw("tooltip.kaleidoscope_cookery.teapot.statue.fluid_ingredient",
                    Component.translatable(bucket.translationKey()), Component.text("-")).color(NamedTextColor.GRAY)));
        }
        if (lore.isEmpty()) {
            teapot.unsetData(DataComponentTypes.LORE);
        } else {
            teapot.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
    }

    /**
     * An empty pot scoops from a water or lava source (TeapotItem#use).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (!isTeapot(item) || player.isSneaking() || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        PersistentDataContainerView pdc = item.getPersistentDataContainer();
        if (pdc.has(TeapotStation.ITEM_FLUID, PersistentDataType.STRING) || pourOut(item) != null) {
            return;
        }
        RayTraceResult hit = player.rayTraceBlocks(5, FluidCollisionMode.SOURCE_ONLY);
        if (hit == null || hit.getHitBlock() == null) {
            return;
        }
        Block block = hit.getHitBlock();
        Material type = block.getType();
        if ((type != Material.WATER && type != Material.LAVA) || !(block.getBlockData() instanceof Levelled levelled) || levelled.getLevel() != 0) {
            return;
        }
        event.setCancelled(true);
        if (!new org.bukkit.event.block.BlockBreakEvent(block, player).callEvent()) {
            return;
        }
        block.setType(Material.AIR);
        String fluid = type == Material.LAVA ? "minecraft:lava" : "minecraft:water";
        item.editPersistentDataContainer(data -> data.set(TeapotStation.ITEM_FLUID, PersistentDataType.STRING, fluid));
        updateLore(item);
        player.getWorld().playSound(player.getLocation(), type == Material.LAVA ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, SoundCategory.PLAYERS, 1, 1);
        plugin.getLogger().fine(player.getName() + " füllt eine Teekanne mit " + fluid);
    }
}
