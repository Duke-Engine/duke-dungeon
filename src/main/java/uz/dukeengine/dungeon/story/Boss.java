package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A Monster the story fights, and the phases it goes through — see {@code docs/plan/2026-10-09-final-fight.md}. One
 * that cannot be killed, only beaten, says so in its own Monster block: an {@code UNYIELDING} passive (E28). Beaten, it
 * kneels, fights no more, nobody's enemy, and BEATEN is heard.
 *
 * @param name       the Monster template
 * @param beatenLook an Effect played on it as it is beaten; blank for none
 * @param phases     in order, each begun once, the next only after the one before it
 */
public record Boss(String name, String beatenLook, List<Phase> phases) {

    public static final Boss DEFAULTS = new Boss("", "", List.of());

    public Boss {
        phases = phases == null ? List.of() : List.copyOf(phases);
    }

    /**
     * A stretch of the fight: begun the first frame its health is at or below {@code below} percent of its most and
     * {@code afterFlag} (if any) is set. Begun, it takes {@code damageTaken} percent of every blow, casts only the
     * skills of its own whose keys {@code skills} lists (empty: all of them), and does its actions, once, in order.
     */
    public record Phase(int below, String afterFlag, int damageTaken, List<String> skills,
            List<uz.dukeengine.dungeon.story.Action> actions) {

        public static final Phase DEFAULTS = new Phase(100, "", 100, List.of(), List.of());

        public Phase {
            skills = skills == null ? List.of() : List.copyOf(skills);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
}
