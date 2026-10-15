package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * An act of the campaign as the front menu starts it and its triggers chain it — see
 * {@code docs/plan/2026-10-15-act1.md} and {@code docs/plan/2026-10-15-act2.md}: the menu's row, the hero it is played
 * with, the named descents its MAP actions may send him down, and its joints in order, each the map it opens on and
 * what a hero skipped to it (the {@code --act1=N}, {@code --act2=N} debug args) is given.
 *
 * @param name         its name: {@code act1}, {@code act2} — the debug arg's ({@code --act2=N}) and the save slot its
 *                     end writes
 * @param word         the front menu's row
 * @param blurb        the line under it
 * @param hero         the Hero template it is played with
 * @param follows      the save slot of the act before it, whose run it carries on from (its flags, and its hero's
 *                     level as the flag {@code <Follows>Level=N}); blank for the first
 * @param defaultLevel the level that act's hero is taken to have reached when there is no such save
 * @param descents     generated floors a MAP may name as if they were a map: the descent's, under a name of their own
 * @param joints       in order: the first is where a new game opens
 */
public record Campaign(String name, String word, String blurb, String hero, String follows, int defaultLevel,
        List<Descent> descents, List<Joint> joints) {

    public static final Campaign DEFAULTS = new Campaign("act1", "", "", "", "", 1, List.of(), List.of());

    public Campaign {
        descents = descents == null ? List.of() : List.copyOf(descents);
        joints = joints == null ? List.of() : List.copyOf(joints);
    }

    /**
     * The descent's generated floors under a name a trigger's {@code Map} and {@code MAP} use, opening at
     * {@code firstDepth} and with no bottom: the story says when they end (a {@code DEPTH} trigger's MAP).
     */
    public record Descent(String name, int firstDepth) {

        public static final Descent DEFAULTS = new Descent("", 1);
    }

    /**
     * One joint of the chain: the map (or descent) it opens on, and what a hero skipped straight to it is given —
     * the flags the joints before it would have set, the things they would have put in his bag (worn where the slot
     * is free), his experience and his gold — and the zone he is put in once it is laid ({@code at}; blank: the map's
     * own way in), and the quests it has going ({@code quests}, started as he arrives).
     */
    public record Joint(String map, List<String> flags, List<String> items, int experience, int gold, String at,
            List<String> quests) {

        public static final Joint DEFAULTS = new Joint("", List.of(), List.of(), 0, 0, "", List.of());

        public Joint {
            flags = flags == null ? List.of() : List.copyOf(flags);
            items = items == null ? List.of() : List.copyOf(items);
            quests = quests == null ? List.of() : List.copyOf(quests);
        }
    }

    /** The descent of that name, or null for a map. */
    public Descent descent(String name) {
        return descents.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** Whether {@code map} is one of the campaign's: a joint's map or one of its descents. */
    public boolean holds(String map) {
        return map != null && (descent(map) != null || joints.stream().anyMatch(joint -> joint.map().equals(map)));
    }

    /** The flag a carried level is kept under: {@code act1Level=7}. */
    public String levelFlag(int level) {
        return follows + "Level=" + level;
    }
}
