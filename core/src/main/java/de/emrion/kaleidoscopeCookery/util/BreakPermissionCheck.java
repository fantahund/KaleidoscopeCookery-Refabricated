package de.emrion.kaleidoscopeCookery.util;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Nur zum Fragen der Schutz-Plugins, ob ein Spieler hier abbauen darf – es wird danach nichts wirklich abgebaut.
 * Eigene Listener ignorieren dieses Event.
 */
public class BreakPermissionCheck extends BlockBreakEvent {

    public BreakPermissionCheck(Block block, Player player) {
        super(block, player);
        setDropItems(false);
    }

    public static boolean mayBreak(Player player, Block block) {
        return new BreakPermissionCheck(block, player).callEvent();
    }
}
