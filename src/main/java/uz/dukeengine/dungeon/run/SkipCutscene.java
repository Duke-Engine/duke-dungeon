package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.message.Command;

/** "Skip it": the cutscene playing is over, for everybody — Esc, or Space with no line on the screen. */
public record SkipCutscene(int playerIndex) implements Command {
}
