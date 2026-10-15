package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * The campaign as the front menu starts it and its triggers chain it — see {@code docs/plan/2026-10-15-act1.md}: the
 * menu's row, the hero it is played with, the named descents its MAP actions may send him down, and its joints in
 * order, each the map it opens on and what a hero skipped to it (the {@code --act1=N} debug arg) is given.
 *
 * @param word     the front menu's row; blank for a game with no campaign
 * @param blurb    the line under it
 * @param hero     the Hero template it is played with
 * @param descents generated floors a MAP may name as if they were a map: the descent's, under a name of their own
 * @param joints   in order: the first is where a new game opens
 */
public record Campaign(String word, String blurb, String hero, List<Descent> descents, List<Joint> joints) {

    public static final Campaign DEFAULTS = new Campaign("", "", "", List.of(), List.of());

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
     * is free), his experience and his gold.
     */
    public record Joint(String map, List<String> flags, List<String> items, int experience, int gold) {

        public static final Joint DEFAULTS = new Joint("", List.of(), List.of(), 0, 0);

        public Joint {
            flags = flags == null ? List.of() : List.copyOf(flags);
            items = items == null ? List.of() : List.copyOf(items);
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
}
