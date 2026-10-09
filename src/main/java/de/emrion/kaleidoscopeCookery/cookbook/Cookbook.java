package de.emrion.kaleidoscopeCookery.cookbook;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.emrion.kaleidoscopeCookery.item.CookeryItems;
import de.emrion.kaleidoscopeCookery.recipe.Recipes;
import de.emrion.kaleidoscopeCookery.util.ItemIcons;
import de.emrion.kaleidoscopeCookery.util.Lang;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Kochbuch als Dialog-Fenster: Übersicht mit Suche, Seiten je Station, Rezeptseiten mit "Wie mache ich das?"
 * und "Wofür brauche ich das?" (wie JEI). Alle Texte sind übersetzbar (kaleidoscope.cookbook.*).
 */
@SuppressWarnings("UnstableApiUsage")
public class Cookbook {

    private static final int PER_PAGE = 24;
    private static final int COLUMNS = 2;
    /** Breit genug für lange Gerichtsnamen mit Bild */
    private static final int BUTTON_WIDTH = 200;
    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    private final KaleidoscopeCookery plugin;
    private @Nullable Recipes cachedFor;
    private @Nullable CookbookEntries entries;

    public Cookbook(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    /**
     * Einträge, neu aufgebaut nach /kc reload.
     */
    private CookbookEntries entries() {
        Recipes recipes = plugin.getRecipes();
        if (entries == null || cachedFor != recipes) {
            entries = new CookbookEntries(recipes);
            cachedFor = recipes;
        }
        return entries;
    }

    public int size() {
        return entries().all().size();
    }

    // ---- Öffnen ----

    public void open(Player player) {
        player.showDialog(mainPage());
    }

    public void open(Player player, Category category) {
        player.showDialog(categoryPage(category, 0, this::mainPage));
    }

    /**
     * Rezepte, die ein Item herstellen; gibt es keine, die Verwendung.
     */
    public void openFor(Player player, Key item) {
        List<CookbookEntry> producing = entries().all().stream().filter(entry -> entry.produces(item)).toList();
        if (!producing.isEmpty()) {
            player.showDialog(listPage(Lang.get("cookbook.recipes_for", name(item)), producing, 0, this::mainPage));
        } else {
            player.showDialog(usesPage(item, this::mainPage));
        }
    }

    public void search(Player player, String query) {
        player.showDialog(searchPage(player, query));
    }

    // ---- Seiten ----

    private Dialog mainPage() {
        List<DialogBody> body = List.of(DialogBody.plainMessage(Lang.get("cookbook.intro").color(NamedTextColor.GRAY), 300));
        List<DialogInput> inputs = List.of(DialogInput.text("search", Lang.get("cookbook.search")).width(220).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(Lang.get("cookbook.search_button").color(NamedTextColor.YELLOW), null, (response, audience) -> {
            String query = response.getText("search");
            if (audience instanceof Player player) {
                player.showDialog(query == null || query.isBlank() ? mainPage() : searchPage(player, query));
            } else {
                audience.closeDialog();
            }
        }));
        for (Category category : Category.values()) {
            if (entries().of(category).isEmpty()) {
                continue;
            }
            buttons.add(button(Component.text().append(category.icon()).append(Component.text(" ")).append(category.title()).build(),
                    Lang.get("cookbook.count", entries().of(category).size()), (response, audience) -> show(audience, categoryPage(category, 0, this::mainPage))));
        }
        return dialog(Lang.get("cookbook.title"), body, inputs, buttons, null);
    }

    private Dialog categoryPage(Category category, int page, Supplier<Dialog> back) {
        List<DialogBody> body = new ArrayList<>();
        Component image = category.bigImage();
        if (image != null) {
            // Das Bild reicht von der ersten Zeile nach unten (32 Pixel ≈ 4 Zeilen): Platz freihalten
            body.add(DialogBody.plainMessage(image.append(Component.text("\n\n\n"))));
        }
        List<Component> guide = category.guide();
        if (!guide.isEmpty()) {
            Component text = Component.empty();
            for (int i = 0; i < guide.size(); i++) {
                boolean header = i == 0 || (i > 0 && isHeader(category, i));
                text = text.append(i == 0 ? Component.empty() : Component.newline())
                        .append(header ? guide.get(i).color(NamedTextColor.GOLD) : Component.text("• ", NamedTextColor.DARK_GRAY).append(guide.get(i).color(NamedTextColor.GRAY)));
            }
            body.add(DialogBody.plainMessage(text, 300));
        }
        return listDialog(category.title(), body, entries().of(category), page, back,
                newPage -> categoryPage(category, newPage, back));
    }

    /**
     * Überschriften stehen in category.guide() vor jeder Szene.
     */
    private static boolean isHeader(Category category, int index) {
        int position = 0;
        for (Component line : category.guide()) {
            if (position == index) {
                return line instanceof net.kyori.adventure.text.TranslatableComponent translatable && translatable.key().endsWith(".header");
            }
            position++;
        }
        return false;
    }

    private Dialog listPage(Component title, List<CookbookEntry> list, int page, Supplier<Dialog> back) {
        return listDialog(title, List.of(), list, page, back, newPage -> listPage(title, list, newPage, back));
    }

    private Dialog listDialog(Component title, List<DialogBody> body, List<CookbookEntry> list, int page, Supplier<Dialog> back,
                              java.util.function.IntFunction<Dialog> pageOf) {
        // Gleiches Gericht an derselben Station (z. B. Milchtee für 1 bis 9 Portionen) als ein Eintrag mit Varianten
        java.util.Map<String, List<CookbookEntry>> grouped = new java.util.LinkedHashMap<>();
        for (CookbookEntry entry : list) {
            grouped.computeIfAbsent(entry.category() + "|" + entry.main().item().asString(), key -> new ArrayList<>()).add(entry);
        }
        List<List<CookbookEntry>> groups = new ArrayList<>(grouped.values());
        int pages = Math.max(1, (groups.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.clamp(page, 0, pages - 1);
        List<ActionButton> buttons = new ArrayList<>();
        Supplier<Dialog> self = () -> pageOf.apply(current);
        for (List<CookbookEntry> group : groups.subList(current * PER_PAGE, Math.min(groups.size(), (current + 1) * PER_PAGE))) {
            CookbookEntry entry = group.getFirst();
            ItemStack result = stack(entry.main().item(), entry.main().amount());
            Component label = ItemIcons.withIcon(result, name(entry.main().item()));
            Component tooltip = group.size() > 1 ? entry.category().title().append(Component.text(" · ")).append(Lang.get("cookbook.variants", group.size()))
                    : entry.category().title();
            buttons.add(button(label, tooltip, (response, audience) -> show(audience, entryPage(group, 0, self))));
        }
        if (list.isEmpty()) {
            body = new ArrayList<>(body);
            body.add(DialogBody.plainMessage(Lang.get("cookbook.no_results").color(NamedTextColor.GRAY)));
        }
        if (current > 0) {
            buttons.add(button(Lang.get("cookbook.previous"), null, (response, audience) -> show(audience, pageOf.apply(current - 1))));
        }
        if (current < pages - 1) {
            buttons.add(button(Lang.get("cookbook.next"), null, (response, audience) -> show(audience, pageOf.apply(current + 1))));
        }
        Component fullTitle = pages > 1 ? title.append(Component.text(" ")).append(Lang.get("cookbook.page", current + 1, pages)) : title;
        return dialog(fullTitle, body, List.of(), buttons, back);
    }

    private Dialog entryPage(CookbookEntry entry, Supplier<Dialog> back) {
        return entryPage(List.of(entry), 0, back);
    }

    /**
     * Rezeptseite; bei mehreren Varianten (gleiches Ergebnis, andere Zutaten-Menge) mit Umschalter.
     */
    private Dialog entryPage(List<CookbookEntry> variants, int index, Supplier<Dialog> back) {
        int variant = Math.clamp(index, 0, variants.size() - 1);
        CookbookEntry entry = variants.get(variant);
        Supplier<Dialog> self = () -> entryPage(variants, variant, back);
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(diagram(entry), 400));
        Component amounts = amounts(entry);
        if (amounts != null) {
            body.add(DialogBody.plainMessage(amounts, 300));
        }
        // Hinweise
        Component info = entry.category().title().color(NamedTextColor.WHITE);
        for (Component line : entry.info()) {
            info = info.append(Component.newline()).append(Component.text("• ", NamedTextColor.DARK_GRAY)).append(line.colorIfAbsent(NamedTextColor.GRAY));
        }
        body.add(DialogBody.plainMessage(info, 300));

        List<ActionButton> buttons = new ArrayList<>();
        if (variants.size() > 1) {
            int min = variants.stream().mapToInt(other -> other.main().amount()).min().orElse(1);
            int max = variants.stream().mapToInt(other -> other.main().amount()).max().orElse(1);
            body.add(DialogBody.plainMessage(Lang.get(min != max ? "cookbook.variants.amounts" : "cookbook.variants.ingredients", variants.size(), min, max)
                    .color(NamedTextColor.YELLOW), 300));
            buttons.add(button(Lang.get("cookbook.variant.previous"), null, (response, audience) ->
                    show(audience, entryPage(variants, (variant - 1 + variants.size()) % variants.size(), back))));
            buttons.add(button(Lang.get("cookbook.variant.next"), null, (response, audience) ->
                    show(audience, entryPage(variants, (variant + 1) % variants.size(), back))));
        }
        Set<Key> asked = new LinkedHashSet<>();
        for (CookbookEntry.Input input : entry.inputs()) {
            for (Key item : input.ingredient().items()) {
                if (asked.add(item) && entries().all().stream().anyMatch(other -> other.produces(item))) {
                    buttons.add(button(ItemIcons.withIcon(stack(item, 1), Lang.get("cookbook.made_by", name(item))), null,
                            (response, audience) -> show(audience, listPage(Lang.get("cookbook.recipes_for", name(item)),
                                    entries().all().stream().filter(other -> other.produces(item)).toList(), 0, self))));
                    break;
                }
            }
        }
        Key result = entry.main().item();
        if (entries().all().stream().anyMatch(other -> other.uses(result))) {
            buttons.add(button(ItemIcons.withIcon(stack(result, 1), Lang.get("cookbook.used_in", name(result))), null,
                    (response, audience) -> show(audience, usesPage(result, self))));
        }
        buttons.add(button(Lang.get("cookbook.overview"), null, (response, audience) -> show(audience, mainPage())));
        Component title = variants.size() > 1 ? name(result).append(Component.text(" ")).append(Lang.get("cookbook.page", variant + 1, variants.size())) : name(result);
        return dialog(title, body, List.of(), buttons, back);
    }

    private static final Key FONT = Key.key("kaleidoscope", "cookbook");
    private static final Component SLOT = glyph(0xE210);
    private static final Component BACK = glyph(0xE211);
    private static final Component GAP = glyph(0xE212);
    private static final Component SPACE = glyph(0xE213);
    private static final Component ARROW = glyph(0xE214);
    private static final Component BLANK_ARROW = glyph(0xE215);
    private static final Component BLANK_SLOT = glyph(0xE216);

    private static Component glyph(int codepoint) {
        return Component.text(Character.toString(codepoint)).font(FONT).color(NamedTextColor.WHITE);
    }

    private static final Component BLANK_DIAGRAM = glyph(0xE217);
    private static final int MAX_INPUT_SLOTS = 9;

    /**
     * Rezept als Bild wie in JEI: links die Zutaten (bei der Werkbank das 3x3-Feld), Pfeil, Bild der Station, Pfeil,
     * rechts das Ergebnis. Drei Reihen à 18 Pixel (zwischen den Reihen eine Leerzeile, weil eine Textzeile nur 9 Pixel
     * hoch ist); jede Reihe ist gleich breit, weil der Dialog jede Zeile einzeln zentriert. Maus über einem Slot zeigt den Tooltip.
     */
    private static Component diagram(CookbookEntry entry) {
        // Zutaten-Raster
        List<@Nullable ItemStack> cells = new ArrayList<>();
        int columns;
        if (entry.grid() != null) {
            for (de.emrion.kaleidoscopeCookery.recipe.Ingredient ingredient : entry.grid()) {
                cells.add(ingredient == null || ingredient.items().isEmpty() ? null : stack(ingredient.items().iterator().next(), 1));
            }
            columns = 3;
        } else {
            int total = entry.inputs().stream().mapToInt(CookbookEntry.Input::amount).sum();
            for (CookbookEntry.Input input : entry.inputs()) {
                ItemStack stack = stack(input.ingredient().items().iterator().next(), 1);
                for (int i = 0; i < (total <= MAX_INPUT_SLOTS ? input.amount() : 1) && cells.size() < MAX_INPUT_SLOTS; i++) {
                    cells.add(stack);
                }
            }
            columns = Math.clamp(cells.size(), 1, 3);
        }
        int inputRows = Math.max(1, (cells.size() + columns - 1) / columns);
        int inputStart = (3 - inputRows) / 2;
        List<CookbookEntry.Output> outputs = entry.outputs().subList(0, Math.min(3, entry.outputs().size()));
        int outputStart = (3 - outputs.size()) / 2;
        Component station = entry.category().diagramImage();

        Component text = Component.empty();
        for (int row = 0; row < 3; row++) {
            if (row > 0) {
                text = text.append(Component.text("\n\n"));
            }
            int inputRow = row - inputStart;
            for (int column = 0; column < columns; column++) {
                int index = inputRow * columns + column;
                if (inputRow < 0 || inputRow >= inputRows || index >= cells.size()) {
                    text = text.append(entry.grid() != null ? SLOT : BLANK_SLOT);
                } else {
                    text = text.append(slot(cells.get(index)));
                }
            }
            boolean middle = row == 1;
            text = text.append(SPACE).append(middle ? ARROW : BLANK_ARROW).append(SPACE);
            if (station != null) {
                text = text.append(middle ? station : BLANK_DIAGRAM).append(SPACE).append(middle ? ARROW : BLANK_ARROW).append(SPACE);
            }
            int outputRow = row - outputStart;
            text = text.append(outputRow >= 0 && outputRow < outputs.size()
                    ? slot(stack(outputs.get(outputRow).item(), outputs.get(outputRow).amount())) : BLANK_SLOT);
        }
        return text;
    }

    /**
     * Mengen, die das Bild nicht zeigt: mehr als ein Ergebnis-Stück, Chancen, mehr Zutaten als Slots.
     */
    private static @Nullable Component amounts(CookbookEntry entry) {
        List<Component> parts = new ArrayList<>();
        int total = entry.inputs().stream().mapToInt(CookbookEntry.Input::amount).sum();
        if (entry.grid() == null && total > MAX_INPUT_SLOTS) {
            for (CookbookEntry.Input input : entry.inputs()) {
                parts.add(name(input.ingredient().items().iterator().next()).append(Component.text(" ×" + input.amount())));
            }
        }
        for (CookbookEntry.Output output : entry.outputs()) {
            if (output.amount() > 1 || output.chance() < 1 || entry.outputs().size() > 1) {
                Component part = Component.text("→ ").append(name(output.item())).append(Component.text(" ×" + output.amount()));
                if (output.chance() < 1) {
                    part = part.append(Component.text(" (")).append(Lang.get("cookbook.chance", Math.round(output.chance() * 100))).append(Component.text(")"));
                }
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(" · ", NamedTextColor.DARK_GRAY)), parts)
                .color(NamedTextColor.GRAY);
    }

    /**
     * Ein Slot (Vorschub 19), darin das Item-Bild (16 Pixel, 1 Pixel Rand rundum).
     */
    private static Component slot(@Nullable ItemStack stack) {
        Component icon = ItemIcons.big(stack);
        if (icon == null) {
            return SLOT;
        }
        return Component.text().append(SLOT).append(BACK).append(icon.hoverEvent(stack.asHoverEvent())).append(GAP).build();
    }

    private Dialog usesPage(Key item, Supplier<Dialog> back) {
        return listPage(Lang.get("cookbook.uses_of", name(item)), entries().all().stream().filter(entry -> entry.uses(item)).toList(), 0, back);
    }

    /**
     * Suche in der Sprache des Spielers nach Ergebnissen und Zutaten (Name oder ID).
     */
    private Dialog searchPage(Player player, String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        Locale locale = player.locale();
        List<CookbookEntry> found = new ArrayList<>();
        List<CookbookEntry> foundByIngredient = new ArrayList<>();
        for (CookbookEntry entry : entries().all()) {
            if (entry.outputs().stream().anyMatch(output -> matches(output.item(), needle, locale))) {
                found.add(entry);
            } else if (entry.inputs().stream().anyMatch(input -> input.ingredient().items().stream().anyMatch(item -> matches(item, needle, locale)))) {
                foundByIngredient.add(entry);
            }
        }
        found.addAll(foundByIngredient);
        return listPage(Lang.get("cookbook.results", query), found, 0, this::mainPage);
    }

    private static boolean matches(Key item, String needle, Locale locale) {
        return item.value().replace('_', ' ').contains(needle) || Lang.plain(name(item), locale).toLowerCase(locale).contains(needle);
    }

    // ---- Hilfen ----

    private static void show(net.kyori.adventure.audience.Audience audience, Dialog dialog) {
        audience.showDialog(dialog);
    }

    private static ActionButton button(Component label, @Nullable Component tooltip, DialogActionCallback callback) {
        return ActionButton.builder(label).tooltip(tooltip).width(BUTTON_WIDTH).action(DialogAction.customClick(callback, CALLBACK)).build();
    }

    private static Dialog dialog(Component title, List<DialogBody> body, List<DialogInput> inputs, List<ActionButton> buttons, @Nullable Supplier<Dialog> back) {
        // Escape löst die Exit-Aktion aus: die schließt immer das ganze Kochbuch, "Zurück" ist ein normaler Knopf
        ActionButton exit = button(Lang.get("cookbook.close"), null, (response, audience) -> audience.closeDialog());
        if (back != null) {
            buttons = new ArrayList<>(buttons);
            // Ganz vorne, damit man ihn ohne Scrollen findet
            buttons.addFirst(button(Lang.get("cookbook.back").color(NamedTextColor.YELLOW), null, (response, audience) -> show(audience, back.get())));
        }
        List<ActionButton> allButtons = buttons;
        // Nach einem Klick offen bleiben, bis die nächste Seite kommt: so springt der Mauszeiger nicht in die Mitte
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body).inputs(inputs).build())
                .type(DialogType.multiAction(allButtons).columns(COLUMNS).exitAction(exit).build()));
    }

    /**
     * Name eines Items (Mod-Items über ihren Übersetzungs-Schlüssel, Vanilla über den Client).
     */
    public static Component name(Key item) {
        ItemStack stack = stack(item, 1);
        return stack.effectiveName();
    }

    public static ItemStack stack(Key item, int amount) {
        if (!item.namespace().equals("minecraft")) {
            ItemStack created = KaleidoscopeCookery.getInstance().getItems().create(item, Math.max(1, amount));
            if (created != null) {
                return created;
            }
        }
        Material material = Material.matchMaterial(item.asString());
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.BARRIER;
        }
        return ItemStack.of(material, Math.clamp(amount, 1, material.getMaxStackSize()));
    }

    /**
     * Station zu einem Stations-Item (Rechtsklick mit dem Kochbuch).
     */
    public static @Nullable Category categoryOf(@Nullable Key stationItem) {
        if (stationItem == null) {
            return null;
        }
        return switch (stationItem.asString()) {
            case "kaleidoscope_cookery:pot" -> Category.POT;
            case "kaleidoscope_cookery:stockpot" -> Category.STOCKPOT;
            case "kaleidoscope_cookery:steamer" -> Category.STEAMER;
            case "kaleidoscope_cookery:chopping_board" -> Category.CHOPPING_BOARD;
            case "kaleidoscope_cookery:teapot" -> Category.TEAPOT;
            case "kaleidoscope_cookery:millstone" -> Category.MILLSTONE;
            case "kaleidoscope_cookery:bamboo_tray" -> Category.BAMBOO_TRAY;
            case "kaleidoscope_cookery:shawarma_spit" -> Category.SHAWARMA_SPIT;
            default -> null;
        };
    }

    public static boolean isCookbook(@Nullable ItemStack stack) {
        return stack != null && CookbookListener.ITEM.equals(CookeryItems.idOf(stack));
    }
}
