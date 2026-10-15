package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/**
 * "Make it": a player's hero crafts the {@code recipe}th recipe of the Crafting block where he stands. See
 * {@code DungeonRun.craft} and docs/plan/2026-10-15-crafting.md.
 */
public record Craft(int playerIndex, int recipe) implements Command {
}
