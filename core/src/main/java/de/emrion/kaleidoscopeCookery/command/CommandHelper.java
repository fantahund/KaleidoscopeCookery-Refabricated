package de.emrion.kaleidoscopeCookery.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class CommandHelper {

    private CommandHelper() {
    }

    public static List<String> complete(String input, Collection<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            String candidate = option.toLowerCase(Locale.ROOT);
            // Item-IDs auch ohne Namespace vervollständigen
            if (candidate.startsWith(lower) || candidate.substring(candidate.indexOf(':') + 1).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    public static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    /**
     * Positive Zahl oder null.
     */
    public static Integer parsePositive(String input) {
        try {
            int value = Integer.parseInt(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
