package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A Monster the story fights: one that may be beaten rather than killed, and the phases it goes through — see
 * {@code docs/plan/2026-10-09-final-fight.md}.
 *
 * @param name       the Monster template
 * @param beaten     the share of its most health, in percent, it is beaten at instead of dying: it kneels, fights no
 *                   more, nobody's enemy, and BEATEN is heard. 0 dies as anything does
 * @param beatenLook an Effect played on it as it is beaten; blank for none
 * @param phases     in order, each begun once, the next only after the one before it
 */
public record Boss(String name, int beaten, String beatenLook, List<Phase> phases) {

    public static final Boss DEFAULTS = new Boss("", 0, "", List.of());

    public Boss {
        phases = phases == null ? List.of() : List.copyOf(phases);
    }

    /**
     * A stretch of the fight: begun the first frame its health is at or below {@code below} percent of its most and
     * {@code afterFlag} (if any) is set. Begun, it takes {@code damageTaken} percent of every blow, casts only the
     * skills of its own whose keys {@code skills} lists (empty: all of them), and does its actions, once, in order.
     */
    public record Phase(int below, String afterFlag, int damageTaken, List<String> skills,
            List<Trigger.Action> actions) {

        public static final Phase DEFAULTS = new Phase(100, "", 100, List.of(), List.of());

        public Phase {
            skills = skills == null ? List.of() : List.copyOf(skills);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
}
