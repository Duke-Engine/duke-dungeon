package uz.dukeengine.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.story.Person;

/** A patrol's stops, in the order it walks them (docs/plan/2026-10-21-npc-brains-and-quests.md). */
class PatrolTest {

    private static int[] walked(Patrol patrol, int legs) {
        var walked = new int[legs];
        for (int i = 0; i < legs; i++) {
            walked[i] = patrol.next();
        }
        return walked;
    }

    @Test
    void loopGoesRoundAndRound() {
        assertArrayEquals(new int[] {1, 2, 0, 1}, walked(new Patrol(3, Person.Mode.LOOP), 4));
    }

    @Test
    void backAndForthTurnsAtTheEnds() {
        assertArrayEquals(new int[] {1, 2, 1, 0, 1}, walked(new Patrol(3, Person.Mode.BACK_AND_FORTH), 5));
    }

    @Test
    void twoPointsBackAndForthIsAToB() {
        assertArrayEquals(new int[] {1, 0, 1}, walked(new Patrol(2, Person.Mode.BACK_AND_FORTH), 3));
    }

    @Test
    void onePointStands() {
        var patrol = new Patrol(1, Person.Mode.LOOP);
        assertArrayEquals(new int[] {0, 0}, walked(patrol, 2));
        assertEquals(0, patrol.at());
    }

    @Test
    void itPicksUpAtTheNearestAndGoesOnFromThere() {
        var patrol = new Patrol(3, Person.Mode.LOOP);
        patrol.nearest(new double[] {9, 1, 5});
        assertEquals(1, patrol.at());
        assertEquals(2, patrol.next());
    }
}
