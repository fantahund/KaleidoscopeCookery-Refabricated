package de.emrion.kaleidoscopeCookery.util;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Only for asking protection plugins whether a player may break here - nothing is actually broken afterwards.
 * Our own listeners ignore this event.
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
