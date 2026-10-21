package uz.dukeengine.dungeon.ai;

import uz.dukeengine.dungeon.story.Person;

/**
 * Which of its stops a patrolling person walks to next (docs/plan/2026-10-21-npc-brains-and-quests.md): round and
 * round for a LOOP, there and back for a BACK_AND_FORTH. Only the counting -- where the stops are, and the walking to
 * them, are the run's.
 */
public final class Patrol {

    private final int points;
    private final Person.Mode mode;
    private int at;
    /** Which way a BACK_AND_FORTH goes along its stops: 1 on, -1 back. */
    private int way = 1;

    public Patrol(int points, Person.Mode mode) {
        this.points = points;
        this.mode = mode;
    }

    /** The point to walk to after the one it stands at. */
    public int next() {
        if (points <= 1) {
            return at = 0;
        }
        if (mode == Person.Mode.LOOP) {
            return at = (at + 1) % points;
        }
        if (at + way < 0 || at + way >= points) {
            way = -way; // the end of the line: back the way it came
        }
        return at += way;
    }

    /** The point it is at now. */
    public int at() {
        return at;
    }

    /** Picks up at the point nearest the given distances (one per point). */
    public void nearest(double[] distances) {
        for (int i = 0; i < distances.length && i < points; i++) {
            if (distances[i] < distances[at]) {
                at = i;
            }
        }
    }
}
