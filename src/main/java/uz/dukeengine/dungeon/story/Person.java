package uz.dukeengine.dungeon.story;

import java.util.List;
import uz.dukeengine.core.data.Link;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.rpg.story.Conversation;
import uz.dukeengine.rpg.story.Npc;

/**
 * One person placed on a map, by a name of their own (docs/plan/2026-10-21-npc-brains-and-quests.md): the creature
 * they are drawn and move as, what they say, and the brain that walks them. Written in the map folder's own
 * {@code .duke}, beside the map, and named in its {@code Npcs} where a creature's name would be -- {@code GateGuard 30
 * 40} is this one gate's guard, where {@code Guard 30 40} is any guard. The story knows them as an {@link Npc} of their
 * own name (see {@link #asNpc}), so talk, quest marks and TALK steps find them by it as they find anybody.
 *
 * @param name   how the map's Npcs and the quests name them
 * @param kind   the Monster they are drawn and move as
 * @param role   what they are, under their name; blank for nothing
 * @param talk   the conversation they greet with; blank for none
 * @param radius how near, in world units, a hero comes before they talk
 * @param brain  what walks them: see {@link Brain}
 * @param wander a WANDER's stroll round home, in world units
 * @param points a PATROL's or a GUARD's stops: zones of their map, walked in turn
 * @param mode   how the stops are walked: see {@link Mode}
 * @param pause  how long, in seconds, they stand at each stop (not Wait: every Java object has a wait())
 * @param leash  a GUARD's: how far, in cells, it leaves its patrol to fight
 */
public record Person(String name, @Link(Monster.class) String kind, String role, @Link(Conversation.class) String talk,
        float radius, Brain brain, float wander, List<String> points, Mode mode, float pause, float leash) {

    /** What walks a person. */
    public enum Brain {
        /** Keeps home, and turns to a hero who comes near. */
        STAND,
        /** Strolls round home, as a villager does. */
        WANDER,
        /** Walks its Points. */
        PATROL,
        /** Walks its Points on the hero's side, and fights what comes within its leash. Nobody talks to it. */
        GUARD
    }

    /** How a patrol's stops are walked. */
    public enum Mode {
        /** Round and round: A, B, C, A. */
        LOOP,
        /** There and back: A, B, C, B, A. */
        BACK_AND_FORTH
    }

    public static final Person DEFAULTS = new Person("", "", "", "", 30f, Brain.STAND, 0f, List.of(), Mode.LOOP, 2f,
            12f);

    public Person {
        points = points == null ? List.of() : List.copyOf(points);
        brain = brain == null ? Brain.STAND : brain;
        mode = mode == null ? Mode.LOOP : mode;
    }

    /** The person as the story knows them: an Npc of their own name, strolling only if they WANDER. */
    public Npc asNpc() {
        return new Npc(name, talk, radius, brain == Brain.WANDER ? wander : 0f, role, List.of());
    }
}
