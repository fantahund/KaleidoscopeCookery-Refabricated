package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.util.ItemIcons;
import de.emrion.kaleidoscopeCookery.util.Lang;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pages of the cookbook: one per station. Addons register their own with {@link #register} (e.g. the grill from Grilling).
 */
public final class Category {

    private static final Key FONT = Key.key("kaleidoscope", "cookbook");
    private static final List<Category> VALUES = new ArrayList<>();

    public static final Category POT = builtin("pot", "block.kaleidoscope_cookery.pot", 0xE200, Map.of("pot", 6));
    public static final Category STOCKPOT = builtin("stockpot", "block.kaleidoscope_cookery.stockpot", 0xE201, Map.of("stockpot", 7));
    public static final Category STEAMER = builtin("steamer", "block.kaleidoscope_cookery.steamer", 0xE202, Map.of("steamer", 5, "steamer_1", 6));
    public static final Category CHOPPING_BOARD = builtin("chopping_board", "block.kaleidoscope_cookery.chopping_board", 0xE203, Map.of());
    public static final Category TEAPOT = builtin("teapot", "block.kaleidoscope_cookery.teapot", 0xE204, Map.of());
    public static final Category MILLSTONE = builtin("millstone", "block.kaleidoscope_cookery.millstone", 0xE205, Map.of("millstone", 5));
    public static final Category BAMBOO_TRAY = builtin("bamboo_tray", "block.kaleidoscope_cookery.bamboo_tray", 0xE206, Map.of());
    public static final Category SHAWARMA_SPIT = new Category("shawarma_spit", "block.kaleidoscope_cookery.shawarma_spit", 0, Map.of(),
            () -> ItemIcons.item(Key.key("kaleidoscope_cookery", "item/shawarma_spit")),
            () -> ItemIcons.diagram(Key.key("kaleidoscope_cookery", "shawarma_spit")), Key.key("kaleidoscope_cookery", "shawarma_spit"))
            .withGuide(Map.of("shawarma_spit", 8));
    public static final Category CRAFTING = new Category("crafting", "block.minecraft.crafting_table", 0, Map.of(),
            () -> ItemIcons.block(Key.key("minecraft", "block/crafting_table_front")),
            () -> ItemIcons.diagram(Key.key("minecraft", "crafting_table")), null);
    public static final Category FURNACE = new Category("furnace", "block.minecraft.furnace", 0, Map.of(),
            () -> ItemIcons.block(Key.key("minecraft", "block/furnace_front")),
            () -> ItemIcons.diagram(Key.key("minecraft", "furnace")), null);

    private final String id;
    private final String nameKey;
    private final int bigImage;
    private Map<String, Integer> guide;
    private final Supplier<Component> icon;
    private final Supplier<@Nullable Component> diagram;
    private final @Nullable Key stationItem;

    /**
     * @param nameKey     translation key of the name
     * @param bigImage    character of the large image (font kaleidoscope:cookbook), 0 = none
     * @param guide       guide texts from the mod's Ponder scenes: scene -> number of lines
     * @param icon        small image for buttons
     * @param diagram     image of the station in the recipe diagram (32 pixels), null if none
     * @param stationItem item of the station (right-clicking it with the cookbook opens this page), otherwise null
     */
    private Category(String id, String nameKey, int bigImage, Map<String, Integer> guide, Supplier<Component> icon,
                     Supplier<@Nullable Component> diagram, @Nullable Key stationItem) {
        this.id = id;
        this.nameKey = nameKey;
        this.bigImage = bigImage;
        this.guide = guide;
        this.icon = icon;
        this.diagram = diagram;
        this.stationItem = stationItem;
        VALUES.add(this);
    }

    private Category withGuide(Map<String, Integer> guide) {
        this.guide = guide;
        return this;
    }

    /** Cookery station with images from tools/gen_cookbook_icons.py */
    private static Category builtin(String id, String nameKey, int bigImage, Map<String, Integer> guide) {
        int index = VALUES.size();
        return new Category(id, nameKey, bigImage, guide, () -> ItemIcons.gui(Key.key("kaleidoscope", "cookbook/" + id)),
                () -> Component.text(Character.toString(0xE220 + index)).font(FONT).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .shadowColor(net.kyori.adventure.text.format.ShadowColor.none()),
                Key.key("kaleidoscope_cookery", id));
    }

    /**
     * Page of an addon. The images come from the addon's pack (cookbook images of the items, see ItemIcons).
     *
     * @param stationItem item of the station; its image is used for the button and in the diagram
     */
    public static Category register(String id, String nameKey, Key stationItem) {
        for (Category category : VALUES) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return new Category(id, nameKey, 0, Map.of(),
                () -> {
                    org.bukkit.inventory.ItemStack stack = de.emrion.kaleidoscopeCookery.KaleidoscopeCookery.getInstance().getItems().create(stationItem, 1);
                    if (stack == null) {
                        org.bukkit.Material material = org.bukkit.Registry.MATERIAL.get(stationItem);
                        stack = material == null ? null : org.bukkit.inventory.ItemStack.of(material);
                    }
                    Component image = ItemIcons.of(stack);
                    return image != null ? image : Component.text("•");
                },
                () -> ItemIcons.diagram(stationItem), stationItem);
    }

    public static List<Category> values() {
        return Collections.unmodifiableList(VALUES);
    }

    public String id() {
        return id;
    }

    public @Nullable Key stationItem() {
        return stationItem;
    }

    public Component title() {
        return Lang.raw(nameKey);
    }

    /**
     * Small image for buttons.
     */
    public Component icon() {
        return icon.get();
    }

    /**
     * Image of the station in the recipe diagram (32 pixels, in the middle row), null if there is none.
     */
    public @Nullable Component diagramImage() {
        return diagram.get();
    }

    /**
     * Large image as a font character, null if there is none.
     */
    public @Nullable Component bigImage() {
        return bigImage == 0 ? null : Component.text(Character.toString(bigImage)).font(FONT).shadowColor(net.kyori.adventure.text.format.ShadowColor.none());
    }

    /**
     * Ponder texts: heading and lines per scene (in the mod's order).
     */
    public List<Component> guide() {
        List<Component> lines = new java.util.ArrayList<>();
        guide.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            lines.add(Lang.raw("kaleidoscope_cookery.ponder." + entry.getKey() + ".header"));
            for (int i = 1; i <= entry.getValue(); i++) {
                lines.add(Lang.raw("kaleidoscope_cookery.ponder." + entry.getKey() + ".text_" + i));
            }
        });
        return lines;
    }

    @Override
    public String toString() {
        return id;
    }
}
