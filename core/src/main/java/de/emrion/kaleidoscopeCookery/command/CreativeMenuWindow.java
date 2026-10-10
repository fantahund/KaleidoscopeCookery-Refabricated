package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.util.Lang;
import de.emrion.kaleidoscopeCookery.util.Text;
import de.iani.cubesideutils.bukkit.inventory.AbstractWindow;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creative menu (/kc menu): all items in tabs like the mods' creative tabs (generated/creative_tabs.yml).
 * Tabs at the top, 36 items in the middle, paging at the bottom. Left click gives 1, shift-click a stack.
 */
public class CreativeMenuWindow extends AbstractWindow {

    private static final int SIZE = 54;
    private static final int ITEM_START = 9;
    private static final int ITEMS_PER_PAGE = 36;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    /** Tab with icon and items; items no tab lists go into the first tab of their namespace or "food" */
    private record Tab(String name, Key icon, List<Key> items) {
    }

    private final KaleidoscopeCookery plugin;
    private final List<Tab> tabs;
    private int tab;
    private int page;

    public CreativeMenuWindow(KaleidoscopeCookery plugin, Player player) {
        super(player, Bukkit.createInventory(null, SIZE, Lang.get("menu.title")));
        this.plugin = plugin;
        this.tabs = loadTabs(plugin);
    }

    private static List<Tab> loadTabs(KaleidoscopeCookery plugin) {
        Map<String, Tab> tabs = new LinkedHashMap<>();
        Set<Key> listed = new LinkedHashSet<>();
        // Tabs of Cookery, then of the addons (each ships its own generated/creative_tabs.yml)
        List<org.bukkit.plugin.Plugin> sources = new ArrayList<>(List.of(plugin));
        sources.addAll(plugin.getAddons());
        for (org.bukkit.plugin.Plugin source : sources) {
            if (source.getResource("generated/creative_tabs.yml") == null) {
                continue;
            }
            YamlConfiguration config;
            try (Reader reader = new InputStreamReader(source.getResource("generated/creative_tabs.yml"), StandardCharsets.UTF_8)) {
                config = YamlConfiguration.loadConfiguration(reader);
            } catch (java.io.IOException e) {
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection("tabs");
            if (section == null) {
                continue;
            }
            for (String name : section.getKeys(false)) {
                List<Key> items = new ArrayList<>();
                for (String id : section.getStringList(name + ".items")) {
                    Key key = Key.key(id);
                    // Only items that exist (e.g. no Grilling tab without the Grilling plugin)
                    if (plugin.getItems().get(key) != null && listed.add(key)) {
                        items.add(key);
                    }
                }
                tabs.put(name, new Tab(name, Key.key(section.getString(name + ".icon", "minecraft:barrier")), items));
            }
        }
        // The mod fills dishes, plates and cups into its tabs dynamically - append them at the end here
        for (Key key : plugin.getItems().all().keySet()) {
            if (listed.contains(key)) {
                continue;
            }
            // Into "food", otherwise into the first tab with items of the same namespace (addon items into the addon tab)
            Tab target = tabs.values().stream()
                    .filter(tab -> tab.items().stream().anyMatch(item -> item.namespace().equals(key.namespace())))
                    .min(java.util.Comparator.comparing(tab -> !tab.name().equals("food")))
                    .orElseGet(() -> tabs.computeIfAbsent("food", name -> new Tab(name, key, new ArrayList<>())));
            target.items().add(key);
        }
        tabs.values().removeIf(tab -> tab.items().isEmpty());
        return List.copyOf(tabs.values());
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    protected void rebuildInventory() {
        getInventory().clear();
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.setData(DataComponentTypes.TOOLTIP_DISPLAY, io.papermc.paper.datacomponent.item.TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        for (int slot = 0; slot < 9; slot++) {
            getInventory().setItem(slot, filler);
            getInventory().setItem(SIZE - 9 + slot, filler);
        }
        for (int i = 0; i < Math.min(9, tabs.size()); i++) {
            Tab current = tabs.get(i);
            ItemStack icon = plugin.getItems().create(current.icon(), 1);
            if (icon == null) {
                icon = ItemStack.of(Material.CHEST);
            }
            icon.setData(DataComponentTypes.ITEM_NAME, Text.noItalic(Lang.get("menu.tab." + current.name()).color(i == tab ? NamedTextColor.GOLD : NamedTextColor.WHITE)));
            icon.unsetData(DataComponentTypes.LORE);
            if (i == tab) {
                icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            getInventory().setItem(i, icon);
        }
        if (tabs.isEmpty()) {
            return;
        }
        List<Key> items = tabs.get(tab).items();
        int pages = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = page * ITEMS_PER_PAGE + i;
            if (index >= items.size()) {
                break;
            }
            getInventory().setItem(ITEM_START + i, plugin.getItems().create(items.get(index), 1));
        }
        if (page > 0) {
            getInventory().setItem(PREVIOUS_SLOT, button(Material.ARROW, Lang.get("cookbook.previous")));
        }
        if (page < pages - 1) {
            getInventory().setItem(NEXT_SLOT, button(Material.ARROW, Lang.get("cookbook.next")));
        }
        getInventory().setItem(PAGE_SLOT, button(Material.PAPER, Lang.get("cookbook.page", page + 1, pages)));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static ItemStack button(Material material, Component name) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.ITEM_NAME, Text.noItalic(name.colorIfAbsent(NamedTextColor.YELLOW)));
        return stack;
    }

    @Override
    public void onItemClicked(InventoryClickEvent event) {
        super.onItemClicked(event);
        if (event.getClickedInventory() != getInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 9) {
            if (slot < tabs.size() && slot != tab) {
                tab = slot;
                page = 0;
                rebuildInventory();
            }
        } else if (slot == PREVIOUS_SLOT || slot == NEXT_SLOT) {
            page += slot == NEXT_SLOT ? 1 : -1;
            rebuildInventory();
        } else if (slot >= ITEM_START && slot < ITEM_START + ITEMS_PER_PAGE) {
            give(event.getCurrentItem(), event.isShiftClick());
        }
    }

    private void give(ItemStack shown, boolean stack) {
        Key id = CookeryItems.idOf(shown);
        if (id == null) {
            return;
        }
        ItemStack item = plugin.getItems().create(id, 1);
        if (item == null) {
            return;
        }
        item.setAmount(stack ? item.getMaxStackSize() : 1);
        Player player = getPlayer();
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.4f);
    }
}
