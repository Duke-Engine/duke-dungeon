package uz.dukeengine.dungeon.skill;

import uz.dukeengine.rpg.skill.SkillEffect;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.Buff;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * What a creature's book holds of what others have lent it: each buff a lender gave -- of a kind, worth a figure, and
 * holding through a last frame -- and the one figure the creature reads of a kind, the largest of those that still hold.
 *
 * <p>The list alone -- what an aura's pulse gives it, and the figures that read it, are {@code AuraTest}'s -- asked of
 * a real book in a real world, a skeleton alone that nobody lends to: frames are what it is counted in, and the book's
 * own {@code update} is what lets go of a buff that has passed.
 */
class BuffTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Three of the kinds a buff may be of, and two lenders by the object ids a buff names them by. */
    private static final SkillEffect MIGHT = SkillEffect.DAMAGE_AURA;
    private static final SkillEffect MANA = SkillEffect.MANA_AURA;
    private static final SkillEffect THIRST = SkillEffect.LIFESTEAL_AURA;
    private static final int ONE = 101;
    private static final int OTHER = 102;

    private static String room(int width, int height) {
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Held(DukeGame game, SkillBook book) {

        /** The frame the next update runs, which is what a buff's last frame is counted against. */
        int frame() {
            return game.getLogic().getFrame();
        }
    }

    /** One skeleton alone in an empty room, and the book it was born with. */
    private static Held skeleton() {
        var world = Dungeon.world(room(40, 30), SETTINGS);
        var game = world.game();
        game.spawn("Skeleton", world.dungeon(), 150f, 150f);
        game.runHeadless(1);
        var creature = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Skeleton")).findFirst().orElseThrow();
        return new Held(game, creature.findModule(SkillBook.class));
    }

    /** A book that has been lent nothing holds nothing, and answers 0 for every kind. */
    @Test
    void nothingLentIsWorthNothing() {
        var it = skeleton();

        assertEquals(List.of(), it.book().getBuffs());
        for (var kind : SkillEffect.values()) {
            assertEquals(0, it.book().buffOf(kind, it.frame()), kind.toString());
        }
    }

    /** A buff is worth its figure through the last frame it names, and nothing the frame after. */
    @Test
    void aBuffIsWorthItsFigureToItsLastFrameAndNothingAfter() {
        var it = skeleton();
        it.book().give(MIGHT, ONE, 50, 100);

        assertEquals(50, it.book().buffOf(MIGHT, 0));
        assertEquals(50, it.book().buffOf(MIGHT, 99));
        assertEquals(50, it.book().buffOf(MIGHT, 100), "the last frame it holds");
        assertEquals(0, it.book().buffOf(MIGHT, 101), "past it, it counts for nothing, dropped yet or not");
        assertEquals(0, it.book().buffOf(MANA, 50), "and it is a buff of its own kind and of no other");
    }

    /** Of two lenders of one kind the larger counts, never the sum -- whichever of them gave first. */
    @Test
    void ofTwoLendersTheLargerCountsAndNeverTheSum() {
        for (boolean weakerFirst : new boolean[] {true, false}) {
            var it = skeleton();
            if (weakerFirst) {
                it.book().give(MIGHT, ONE, 40, 100);
                it.book().give(MIGHT, OTHER, 60, 100);
            } else {
                it.book().give(MIGHT, OTHER, 60, 100);
                it.book().give(MIGHT, ONE, 40, 100);
            }

            assertEquals(60, it.book().buffOf(MIGHT, 50), "the weaker given first: " + weakerFirst);
            assertEquals(2, it.book().getBuffs().size(),
                    "both are kept, for the weaker is what is left when the other goes");
        }
    }

    /** Each kind is read by itself: a figure of one is nothing to another, and one lender may lend two. */
    @Test
    void eachKindIsReadByItself() {
        var it = skeleton();
        it.book().give(MIGHT, ONE, 50, 100);
        it.book().give(MANA, ONE, 25, 100);
        it.book().give(THIRST, OTHER, 10, 100);

        assertEquals(50, it.book().buffOf(MIGHT, 50));
        assertEquals(25, it.book().buffOf(MANA, 50));
        assertEquals(10, it.book().buffOf(THIRST, 50));
        assertEquals(3, it.book().getBuffs().size());
    }

    /** A stronger lender leaving hands over to a weaker one still in reach, with no frame between worth nothing. */
    @Test
    void aStrongerLenderLeavingHandsOverToAWeakerOneWithNoGap() {
        var it = skeleton();
        it.book().give(MIGHT, ONE, 60, 20);
        it.book().give(MIGHT, OTHER, 40, 30);

        for (int frame = 0; frame <= 30; frame++) {
            assertEquals(frame <= 20 ? 60 : 40, it.book().buffOf(MIGHT, frame), "frame " + frame);
        }
        assertEquals(0, it.book().buffOf(MIGHT, 31), "and when the last goes, there is nothing");
    }

    /** A lender giving again replaces its own buff of that kind and does not stand beside it: the latest word is its. */
    @Test
    void aLenderGivingAgainReplacesItsOwnBuff() {
        var it = skeleton();
        it.book().give(MIGHT, ONE, 60, 20);
        it.book().give(MIGHT, ONE, 40, 50);

        assertEquals(List.of(new Buff(MIGHT, ONE, 40, 50)), it.book().getBuffs(), "one buff for a kind and a lender");
        assertEquals(40, it.book().buffOf(MIGHT, 10), "the weaker word is the one held, not the larger of the two");
        assertEquals(40, it.book().buffOf(MIGHT, 50), "and to the later last frame");
        assertEquals(0, it.book().buffOf(MIGHT, 51));
    }

    /**
     * Replaced where it stands, and the rest kept in the order they were first given: what is on a creature is listed
     * the same on every machine, and the list a picture is drawn from does not shuffle each time a lender gives again.
     */
    @Test
    void aBuffIsReplacedWhereItStandsAndTheOthersKeepTheOrderTheyWereGivenIn() {
        var it = skeleton();
        it.book().give(MIGHT, ONE, 60, 100);
        it.book().give(MANA, ONE, 25, 100);
        it.book().give(MIGHT, OTHER, 30, 100);
        it.book().give(MIGHT, ONE, 55, 120);

        assertEquals(List.of(new Buff(MIGHT, ONE, 55, 120), new Buff(MANA, ONE, 25, 100),
                new Buff(MIGHT, OTHER, 30, 100)), it.book().getBuffs());
    }

    /** The book lets go of a buff at the first update after its last frame, not before, and of nothing else. */
    @Test
    void aBuffPastItsLastFrameIsDroppedAtTheNextUpdate() {
        var it = skeleton();
        int last = it.frame() + 2;
        it.book().give(MIGHT, ONE, 50, last);
        it.book().give(MANA, ONE, 25, last + 100);
        it.book().give(THIRST, OTHER, 10, last);

        it.game().runHeadless(3); // the updates of the frames up to and including its last
        assertEquals(3, it.book().getBuffs().size(), "held through the frame it names");
        assertEquals(50, it.book().buffOf(MIGHT, last));

        it.game().runHeadless(1); // and the update of the frame after it
        assertEquals(List.of(new Buff(MANA, ONE, 25, last + 100)), it.book().getBuffs(),
                "what has passed is let go of, and what outlasts it stays");
        assertEquals(0, it.book().buffOf(MIGHT, 0), "gone, and not only not counted at the frame it passed on");
    }
}
