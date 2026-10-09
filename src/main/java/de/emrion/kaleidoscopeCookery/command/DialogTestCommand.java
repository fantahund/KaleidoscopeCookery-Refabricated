package de.emrion.kaleidoscopeCookery.command;

import de.emrion.kaleidoscopeCookery.KaleidoscopeCookery;
import de.iani.cubesideutils.bukkit.commands.PlayerSubCommand;
import de.iani.cubesideutils.commands.ArgsParser;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * /kc dialogtest – zeigt, was die Dialog-Fenster von Minecraft können (Vorlage für das Kochbuch). Nur zum Ausprobieren.
 */
public class DialogTestCommand extends PlayerSubCommand {

    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");

    private final KaleidoscopeCookery plugin;

    public DialogTestCommand(KaleidoscopeCookery plugin) {
        this.plugin = plugin;
    }

    /**
     * Bild aus dem Item-Atlas als Teil eines Textes.
     */
    private static Component icon(String item) {
        return Component.object(ObjectContents.sprite(ITEMS_ATLAS, Key.key("kaleidoscope_cookery", "item/" + item)));
    }

    private ItemStack item(String id, int amount) {
        ItemStack stack = plugin.getItems().create(Key.key("kaleidoscope_cookery", id), amount);
        return stack == null ? new ItemStack(org.bukkit.Material.BARRIER) : stack;
    }

    @Override
    public boolean onCommand(Player player, Command command, String alias, String commandString, ArgsParser args) {
        player.showDialog(mainPage());
        return true;
    }

    @SuppressWarnings("UnstableApiUsage")
    private Dialog mainPage() {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("Text mit Bildern im Text: ")
                .append(icon("tomato")).append(Component.text(" Tomate + "))
                .append(icon("red_chili")).append(Component.text(" Chili → "))
                .append(icon("dumpling")).append(Component.text(" Teigtaschen", NamedTextColor.GOLD)), 300));
        body.add(DialogBody.item(item("tomato", 1))
                .description(DialogBody.plainMessage(Component.text("Item-Anzeige mit Beschreibung daneben. Maus drüber = normaler Item-Tooltip.")))
                .showTooltip(true).build());
        body.add(DialogBody.item(item("dumpling", 4))
                .description(DialogBody.plainMessage(Component.text("Mit Anzahl (4) wie im Inventar.", NamedTextColor.GRAY)))
                .showDecorations(true).showTooltip(true).build());
        body.add(DialogBody.plainMessage(Component.text("Lange Texte werden automatisch umgebrochen und das Fenster scrollt, "
                + "wenn es zu lang wird. Farben, ", NamedTextColor.WHITE).append(Component.text("fett", NamedTextColor.YELLOW).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text(" und Hover-Texte gehen auch.").hoverEvent(Component.text("Hallo!"))), 300));

        List<DialogInput> inputs = List.of(
                DialogInput.text("suche", Component.text("Suche")).width(200).build(),
                DialogInput.singleOption("station", Component.text("Station"), List.of(
                        SingleOptionDialogInput.OptionEntry.create("wok", Component.text("Wok"), true),
                        SingleOptionDialogInput.OptionEntry.create("stockpot", Component.text("Suppentopf"), false),
                        SingleOptionDialogInput.OptionEntry.create("steamer", Component.text("Dampfkorb"), false))).build(),
                DialogInput.bool("flex", Component.text("Nur Flex-Rezepte")).build());

        DialogAction echo = DialogAction.customClick((response, audience) -> audience.sendMessage(Component.text(
                "Eingaben: Suche=\"" + response.getText("suche") + "\", Station=" + response.getText("station") + ", Flex=" + response.getBoolean("flex"),
                NamedTextColor.GREEN)), ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
        List<ActionButton> buttons = new ArrayList<>();
        for (String[] entry : new String[][]{{"tomato", "Tomate"}, {"red_chili", "Chili"}, {"dumpling", "Teigtaschen"},
                {"baozi", "Baozi"}, {"oolong", "Oolong"}, {"flour", "Mehl"}}) {
            buttons.add(ActionButton.builder(icon(entry[0]).append(Component.text(" " + entry[1])))
                    .tooltip(Component.text("Knopf mit Bild – Klick schickt die Eingaben an den Server")).width(100).action(echo).build());
        }
        buttons.add(ActionButton.builder(Component.text("Zweite Seite →")).width(100)
                .action(DialogAction.staticAction(ClickEvent.showDialog(secondPage()))).build());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Kochbuch – Dialog-Test")).canCloseWithEscape(true).body(body).inputs(inputs).build())
                .type(DialogType.multiAction(buttons).columns(3).exitAction(ActionButton.builder(Component.text("Schließen")).width(100).build()).build()));
    }

    @SuppressWarnings("UnstableApiUsage")
    private Dialog secondPage() {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("So könnte eine Rezeptseite aussehen:", NamedTextColor.GOLD)));
        for (String[] entry : new String[][]{{"tomato", "2× Tomate"}, {"red_chili", "1× Chili"}, {"raw_dough", "1× Teig"}}) {
            body.add(DialogBody.item(item(entry[0], 1)).description(DialogBody.plainMessage(Component.text(entry[1]))).showTooltip(true).width(16).build());
        }
        body.add(DialogBody.plainMessage(Component.text("Wok · 10 s · 3× umrühren · in eine Schüssel füllen", NamedTextColor.GRAY)));
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Rezept: Teigtaschen")).body(body).build())
                .type(DialogType.notice(ActionButton.builder(Component.text("OK")).width(100).build())));
    }

    @Override
    public String getRequiredPermission() {
        return Permissions.ADMIN;
    }

    @Override
    public Collection<String> onTabComplete(Player sender, Command command, String alias, ArgsParser args) {
        return List.of();
    }
}
