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
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.clamp(page, 0, pages - 1);
        List<ActionButton> buttons = new ArrayList<>();
        Supplier<Dialog> self = () -> pageOf.apply(current);
        for (CookbookEntry entry : list.subList(current * PER_PAGE, Math.min(list.size(), (current + 1) * PER_PAGE))) {
            ItemStack result = stack(entry.main().item(), entry.main().amount());
            Component label = ItemIcons.withIcon(result, name(entry.main().item()));
            buttons.add(button(label, entry.category().title(), (response, audience) -> show(audience, entryPage(entry, self))));
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
        Supplier<Dialog> self = () -> entryPage(entry, back);
        List<DialogBody> body = new ArrayList<>();
        // Ergebnis(se)
        for (CookbookEntry.Output output : entry.outputs()) {
            Component description = name(output.item()).append(Component.text(" ×" + output.amount(), NamedTextColor.GRAY));
            if (output.chance() < 1) {
                description = description.append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                        .append(Lang.get("cookbook.chance", Math.round(output.chance() * 100)).color(NamedTextColor.GRAY));
            }
            body.add(DialogBody.item(stack(output.item(), output.amount())).description(DialogBody.plainMessage(description, 250))
                    .showDecorations(true).showTooltip(true).build());
        }
        // Zutaten
        body.add(DialogBody.plainMessage(Lang.get("cookbook.ingredients").color(NamedTextColor.GOLD)));
        for (CookbookEntry.Input input : entry.inputs()) {
            Key first = input.ingredient().items().iterator().next();
            Component description = name(first).append(Component.text(" ×" + input.amount(), NamedTextColor.GRAY));
            int others = input.ingredient().items().size() - 1;
            if (others > 0) {
                description = description.append(Component.text(" ")).append(Lang.get("cookbook.or_more", others).color(NamedTextColor.DARK_GRAY));
            }
            body.add(DialogBody.item(stack(first, input.amount())).description(DialogBody.plainMessage(description, 250))
                    .showDecorations(true).showTooltip(true).build());
        }
        // Hinweise
        Component info = entry.category().title().color(NamedTextColor.WHITE);
        for (Component line : entry.info()) {
            info = info.append(Component.newline()).append(Component.text("• ", NamedTextColor.DARK_GRAY)).append(line.colorIfAbsent(NamedTextColor.GRAY));
        }
        body.add(DialogBody.plainMessage(info, 300));

        List<ActionButton> buttons = new ArrayList<>();
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
        return dialog(name(result), body, List.of(), buttons, back);
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
            buttons.add(button(Lang.get("cookbook.back"), null, (response, audience) -> show(audience, back.get())));
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
