package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.module.StatusUpdate;
import uz.dukeengine.combat.network.CombatOrderCodec;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Combo;
import uz.dukeengine.dungeon.run.Striking;
import uz.dukeengine.game.DukeGame;

/**
 * The knight's attack combinations (PLAN-dungeon-action.md, step 1.4; the Combos block of data/units/knight.duke): the
 * chains, a press buffered until the step's window, the reset, the blow on its frame, soft aim, the heavy stagger, the
 * dodge that cuts a chain off, and that the same orders give the same world, in a save and over the wire.
 */
class ComboTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final uz.dukeengine.dungeon.world.Combos KNIGHT = SHIPPED.combosOf("Knight");

    /** Frames from a command posted to the order heard in the simulation: the lock-step delay. */
    private static final int HEARD = 3;

    /** An open room of cells ten units square: x and y from 10 to 290 and 100. */
    private static final String ROOM = "################################\n" + "#..............................#\n"
            .repeat(11) + "################################\n";

    /** The knight at (100, 60) facing east, and what he is fighting. */
    private static final class Field {
        final DukeGame game;
        final GameObject knight;
        final Combo combo;
        final uz.dukeengine.game.GamePlayer foes;

        Field() {
            var arena = Dungeon.world(ROOM, SHIPPED);
            game = arena.game();
            game.spawn("Knight", arena.hero(), 100f, 60f);
            foes = arena.dungeon();
            game.runHeadless(1);
            knight = find("Knight");
            knight.setOrientation(0f);
            combo = knight.findModule(Combo.class);
        }

        GameObject find(String template) {
            return game.getLogic().getObjects().stream()
                    .filter(thing -> thing.getTemplate().name().equals(template)).findFirst().orElseThrow();
        }

        /** A skeleton {@code distance} from his middle at {@code degrees} off east, stunned for good so it stands. */
        GameObject dummy(float distance, float degrees) {
            double way = Math.toRadians(degrees);
            var before = game.getLogic().getObjects().size();
            game.spawn("Skeleton", foes, 100f + (float) Math.cos(way) * distance,
                    60f + (float) Math.sin(way) * distance);
            game.runHeadless(1);
            var made = game.getLogic().getObjects().stream().skip(before).findFirst().orElseThrow();
            made.setStatus(ObjectStatus.DISABLED);
            return made;
        }

        void run(int frames) {
            game.runHeadless(frames);
        }
    }

    /** The real dungeon with the knight laid down in it (the file's default hero is the archer), a few frames in. */
    private static Dungeon.Session knightSession() {
        var session = Dungeon.newSession(7L, SHIPPED);
        session.run().startWith(session.game(), "Knight");
        session.game().runHeadless(5);
        return session;
    }

    /**
     * The steps begun, in order, as each press is made right after the step before it begins; each press is a letter,
     * L for the left button and H for the right.
     */
    private static List<String> played(String presses) {
        return startsOf(presses, 300).stream().map(start -> start.name).toList();
    }

    private record Start(String name, int frame) {
    }

    private static List<Start> startsOf(String presses, int frames) {
        var field = new Field();
        var starts = new ArrayList<Start>();
        int next = 1;
        field.combo.press(presses.charAt(0) == 'H');
        for (int frame = 0; frame < frames; frame++) {
            field.run(1);
            if (field.combo.step() != null && field.combo.age() == 0) {
                starts.add(new Start(field.combo.step().name(), frame));
                if (next < presses.length()) {
                    field.combo.press(presses.charAt(next++) == 'H');
                }
            }
        }
        return starts;
    }

    @Test
    void theChainsAreLightLightLightLightLightHeavyLightHeavyAndHeavyHeavy() {
        assertEquals(List.of("L1", "L2", "L3"), played("LLL"));
        assertEquals(List.of("L1", "L2", "Finisher"), played("LLH"));
        assertEquals(List.of("L1", "H1"), played("LH"));
        assertEquals(List.of("H1", "H2"), played("HH"));
    }

    @Test
    void aPressWithNoStepAfterItResetsTheChainAndBeginsANewOneWhenTheSwingIsOver() {
        assertEquals(List.of("L1", "L2", "L3", "L1"), played("LLLL"), "a fourth light: a new chain");
        assertEquals(List.of("H1", "H2", "H1"), played("HHH"));
        assertEquals(List.of("L1", "H1", "L1"), played("LHL"), "a light after L-H: nothing follows H1 but a heavy");
    }

    @Test
    void aPressKeptDuringTheSwingPlaysTheNextStepWhenItsWindowOpens() {
        var starts = startsOf("LL", 100);
        var l1 = KNIGHT.step("L1");
        assertEquals(l1.windowOpen(), starts.get(1).frame() - starts.get(0).frame(),
                "pressed on the first frame, the next step waits for the window");
    }

    @Test
    void aPressInsideTheWindowPlaysTheNextAtOnceAndOneAfterItDoesNot() {
        var l1 = KNIGHT.step("L1");
        var field = new Field();
        field.combo.press(false);
        field.run(l1.windowClose()); // begun, and run to the frame before the window's last
        assertEquals(l1.windowClose() - 1, field.combo.age());
        field.combo.press(false); // heard on the window's last frame
        field.run(1);
        assertEquals("L2", field.combo.step().name(), "the last frame of the window still chains");

        var late = new Field();
        late.combo.press(false);
        late.run(1 + l1.windowClose()); // the window's last frame is done
        late.combo.press(false); // heard on the first frame after it
        late.run(1);
        assertEquals("L1", late.combo.step().name(), "after the window the swing runs on");
        assertEquals(l1.windowClose() + 1, late.combo.age());
        late.run(l1.frames() - late.combo.age());
        assertEquals("L1", late.combo.step().name(), "and the press kept begins a new chain, not the second step");
        assertEquals(0, late.combo.age());
    }

    @Test
    void withNoPressTheChainEndsWhenTheSwingDoes() {
        var field = new Field();
        field.combo.press(false);
        field.run(KNIGHT.step("L1").frames() + 5);
        assertNull(field.combo.step());
        field.combo.press(false);
        field.run(1);
        assertEquals("L1", field.combo.step().name(), "a new chain begins at its first step");
    }

    @Test
    void theBlowLandsOnTheStepsFrameAndNotBefore() {
        var field = new Field();
        var dummy = field.dummy(8f, 0f);
        float whole = dummy.getBody().getHealth();
        var l1 = KNIGHT.step("L1");
        field.combo.press(false);
        field.run(1 + l1.hitFrame() - 1);
        assertEquals(l1.hitFrame() - 1, field.combo.age());
        assertEquals(whole, dummy.getBody().getHealth(), "the frame before");
        field.run(1);
        assertEquals(l1.hitFrame(), field.combo.age());
        assertTrue(dummy.getBody().getHealth() < whole, "on the frame");
        float once = dummy.getBody().getHealth();
        field.run(l1.frames() - l1.hitFrame() - 1);
        assertEquals(once, dummy.getBody().getHealth(), "one blow a step");
    }

    @Test
    void aHeavyBlowDealsItsShareOfTheWeaponsAndTheLightItsOwn() {
        float light = damageOf(false);
        float heavy = damageOf(true);
        assertTrue(light > 0f);
        assertEquals(KNIGHT.step("H1").damagePercent() / (float) KNIGHT.step("L1").damagePercent(), heavy / light, 0.05f);
    }

    private static float damageOf(boolean heavy) {
        var field = new Field();
        var dummy = field.dummy(8f, 0f);
        float whole = dummy.getBody().getHealth();
        field.combo.press(heavy);
        field.run(1 + KNIGHT.step(heavy ? "H1" : "L1").hitFrame());
        return whole - dummy.getBody().getHealth();
    }

    @Test
    void theBlowStrikesEveryoneInTheArcAndNobodyBehindOrBeyondReach() {
        var field = new Field();
        var ahead = field.dummy(8f, 0f);
        var aside = field.dummy(8f, 50f);
        var behind = field.dummy(8f, 180f);
        var far = field.dummy(60f, 0f);
        field.combo.press(false);
        field.run(1 + KNIGHT.step("L1").hitFrame());
        assertTrue(ahead.getBody().getHealth() < ahead.getBody().getMaxHealth(), "ahead");
        assertTrue(aside.getBody().getHealth() < aside.getBody().getMaxHealth(), "within the arc: it cleaves");
        assertEquals(behind.getBody().getMaxHealth(), behind.getBody().getHealth(), "behind him");
        assertEquals(far.getBody().getMaxHealth(), far.getBody().getHealth(), "out of reach");
    }

    @Test
    void aHeavyStepStunsWhomItStrikesForItsFramesAndALightOneDoesNot() {
        var field = new Field();
        var dummy = field.dummy(8f, 0f);
        dummy.clearStatus(ObjectStatus.DISABLED);
        dummy.findModule(StatusUpdate.class).apply(ObjectStatus.HELD, 1000);
        var h1 = KNIGHT.step("H1");
        field.combo.press(true);
        field.run(1 + h1.hitFrame());
        assertTrue(dummy.hasStatus(ObjectStatus.DISABLED), "stunned by the heavy blow");
        field.run(h1.staggerFrames());
        assertFalse(dummy.hasStatus(ObjectStatus.DISABLED), "for its StaggerFrames");

        var light = new Field();
        var other = light.dummy(8f, 0f);
        other.clearStatus(ObjectStatus.DISABLED);
        other.findModule(StatusUpdate.class).apply(ObjectStatus.HELD, 1000);
        light.combo.press(false);
        light.run(1 + KNIGHT.step("L1").hitFrame());
        assertFalse(other.hasStatus(ObjectStatus.DISABLED), "a light blow does not");
    }

    @Test
    void softAimTurnsHimToTheNearestEnemyInsideTheArcAndRange() {
        var inside = new Field();
        inside.dummy(12f, 40f);
        inside.combo.press(false);
        inside.run(1);
        assertEquals(Math.toRadians(40), inside.knight.getOrientation(), 0.05, "turned to the one 40 degrees off");

        var nearest = new Field();
        nearest.dummy(18f, -30f);
        nearest.dummy(10f, 45f);
        nearest.combo.press(false);
        nearest.run(1);
        assertEquals(Math.toRadians(45), nearest.knight.getOrientation(), 0.05, "the nearer of two");

        var outside = new Field();
        outside.dummy(12f, 100f);
        outside.combo.press(false);
        outside.run(1);
        assertEquals(0f, outside.knight.getOrientation(), 1e-4f, "outside the arc: where he faces");

        var far = new Field();
        far.dummy(80f, 10f);
        far.combo.press(false);
        far.run(1);
        assertEquals(0f, far.knight.getOrientation(), 1e-4f, "beyond the range: where he faces");
    }

    @Test
    void eachStepOfAChainAimsAgainAndASwingAtNobodyStrikesAir() {
        var field = new Field();
        field.dummy(14f, 20f);
        field.combo.press(false);
        field.run(1);
        assertEquals(Math.toRadians(20), field.knight.getOrientation(), 0.05, "the first swing turned to the first");
        field.dummy(10f, -30f); // nearer, and 50 degrees off where he now faces: inside the aim
        field.combo.press(false);
        field.run(KNIGHT.step("L1").windowOpen() - field.combo.age());
        assertEquals("L2", field.combo.step().name());
        assertEquals(0, field.combo.age());
        assertEquals(Math.toRadians(-30), field.knight.getOrientation(), 0.2, "the next swing turned to the nearer (he has lunged since)");

        var empty = new Field();
        float before = empty.knight.getOrientation();
        empty.combo.press(false);
        empty.run(KNIGHT.step("L1").frames());
        assertEquals(before, empty.knight.getOrientation(), 1e-4f, "nobody: he swings where he faces");
    }

    @Test
    void theLungeCarriesHimForwardUpToTheBlowAndNoFurther() {
        var field = new Field();
        float from = field.knight.getPosition().x();
        field.combo.press(false);
        field.run(KNIGHT.step("L1").frames());
        assertEquals(KNIGHT.step("L1").lunge(), field.knight.getPosition().x() - from, 0.3f);
    }

    @Test
    void aStunCutsTheChainOffAndABlowThatDoesNotStaggerHimDoesNot() {
        var field = new Field();
        field.combo.press(false);
        field.run(5);
        field.knight.getBody().damage(1f, uz.dukeengine.core.module.DamageType.NORMAL,
                new uz.dukeengine.core.module.Death(uz.dukeengine.core.module.DeathType.NORMAL, null, -1));
        field.run(1);
        assertEquals("L1", field.combo.step().name(), "hurt, not staggered: the swing goes on");
        assertEquals("Attack_L1", field.knight.getBusyClip(), "and its clip with it");

        field.knight.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 11);
        field.run(1);
        assertNull(field.combo.step(), "a stun ends the chain");
        field.combo.press(false);
        field.run(1);
        assertNull(field.combo.step(), "and a press while stunned is not kept");
    }

    @Test
    void theClipIsHisBusyClipForTheWholeOfTheStep() {
        var field = new Field();
        field.combo.press(true);
        field.run(2);
        assertEquals("Attack_H1", field.knight.getBusyClip());
        field.run(KNIGHT.step("H1").frames());
        assertNull(field.knight.getBusyClip(), "and no longer");
    }

    @Test
    void theRollCutsTheChainOffAndOnlyOnceItIsCast() {
        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        var knight = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow();
        var combo = knight.findModule(Combo.class);

        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), false)));
        game.runHeadless(HEARD + 2);
        assertNotNull(combo.step(), "the order began a swing");

        Main.roll(game, 0, SHIPPED);
        game.runHeadless(HEARD);
        assertNull(combo.step(), "the roll ended the chain");
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), false)));
        game.runHeadless(HEARD);
        assertNull(combo.step(), "a press while he rolls, out of harm's way, begins nothing");
        game.runHeadless(18);
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), false)));
        game.runHeadless(HEARD);
        assertNotNull(combo.step(), "and the next press, once he is up, begins a new chain");

        game.runHeadless(10);
        Main.roll(game, 0, SHIPPED); // within its cooldown of 75 frames: refused, nothing cut
        game.runHeadless(HEARD);
        assertNotNull(combo.step());
    }

    /** What the world sums each frame of the same orders, from a fresh game. */
    private static List<Long> sums(boolean heavy, int pressAt) {
        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        var sums = new ArrayList<Long>();
        for (int frame = 0; frame < 150; frame++) {
            if (frame == pressAt || frame == pressAt + 3) {
                game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), heavy && frame == pressAt)));
            }
            game.runHeadless(1);
            sums.add(game.getLogic().checksum());
        }
        return sums;
    }

    @Test
    void theSameOrdersGiveTheSameWorldAndADifferentChainADifferentSum() {
        assertEquals(sums(false, 10), sums(false, 10), "deterministic, frame for frame");
        assertNotEquals(sums(false, 10), sums(true, 10), "the heavy chain is another world");
        assertNotEquals(sums(false, 10), sums(false, 11), "a frame's difference in the press is seen");
    }

    @Test
    void whereHeIsInAChainIsInTheChecksumBeforeAnythingElseShowsIt() {
        var field = new Field();
        field.run(1);
        long still = field.game.getLogic().checksum();
        field.combo.press(false);
        field.run(1); // begun, standing, no one to hit: nothing of the world but his clip and the chain moved
        assertNotEquals(still, field.game.getLogic().checksum());
    }

    @Test
    void aChainMidwayIsSavedAndLoadedIntoAFreshKnightWhichPlaysItOutAlike() {
        var field = new Field();
        field.combo.press(false);
        field.run(6);
        field.combo.press(true);
        var out = new SaveOut();
        field.combo.save(out);

        var twin = new Field();
        twin.combo.load(new SaveIn(out.text(), twin.game.getLogic(), Combo.class.getName()));
        assertEquals(field.combo.step(), twin.combo.step());
        assertEquals(field.combo.age(), twin.combo.age());
        assertEquals(field.combo.checksum(), twin.combo.checksum());

        for (int frame = 0; frame < 80; frame++) {
            field.run(1);
            twin.run(1);
            assertEquals(field.combo.step(), twin.combo.step(), "frame " + frame);
            assertEquals(field.combo.age(), twin.combo.age(), "frame " + frame);
        }
    }

    @Test
    void whatTheHeroCarriesThatChangesIsSavable() {
        var field = new Field();
        assertTrue(field.combo instanceof uz.dukeengine.core.save.Saveable);
        assertTrue(field.knight.findModule(uz.dukeengine.dungeon.run.Sprint.class) instanceof uz.dukeengine.core.save.Saveable);
        assertTrue(field.knight.findModule(uz.dukeengine.action.Dodge.class) instanceof uz.dukeengine.core.save.Saveable);
    }

    @Test
    void theStrikeTravelsAsAnOrderOverTheWireAndIsObeyed() {
        var order = PartyOrders.of(new Striking(2, true));
        assertEquals(new Striking(2, true), PartyOrders.commandOf(order));
        assertEquals(new Striking(2, false), PartyOrders.commandOf(PartyOrders.of(new Striking(2, false))));
        assertEquals(order, CombatOrderCodec.decode(CombatOrderCodec.encode(order)), "the wire keeps it whole");

        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        var combo = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow()
                .findModule(Combo.class);
        game.postCommand(CombatOrderCodec.decode(CombatOrderCodec.encode(
                PartyOrders.of(new Striking(game.getLocalPlayerIndex(), true)))));
        game.runHeadless(HEARD);
        assertEquals("H1", combo.step().name(), "heard from the wire, the heavy press began the heavy chain");
    }

    @Test
    void theRunIsWrittenAsItStandsWhileHeSwingsOrRuns() {
        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        Main.walk(game, 0);
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), false)));
        game.runHeadless(8);
        assertNotNull(session.run().saveNow(), "mid-swing and with a direction held, the save is written");
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), true)));
        game.runHeadless(40);
        assertNotNull(session.run().saveNow(), "and mid-chain");
    }

    @Test
    void aHeldDirectionWalksOnUnderTheSwing() {
        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        var knight = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow();
        int open = 0;
        float most = 0f;
        for (int angle = 0; angle < 256; angle += 64) {
            var probe = knightSession().game();
            probe.runHeadless(5);
            var start = probe.getLogic().getObjects().stream()
                    .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow()
                    .getPosition();
            Main.walk(probe, angle);
            probe.runHeadless(30);
            float ran = probe.getLogic().getObjects().stream()
                    .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow()
                    .getPosition().distance(start);
            if (ran > most) {
                most = ran;
                open = angle;
            }
        }
        Main.walk(game, open);
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), false)));
        game.runHeadless(HEARD);
        var held = knight.getPosition();
        game.runHeadless(6);
        var combo = knight.findModule(Combo.class);
        assertNotNull(combo.step(), "swinging");
        assertTrue(knight.getPosition().distance(held) > 3f, "the held key carries him on in the swing");
        var view = game.getSnapshot().units().stream().filter(unit -> unit.id() == knight.getId().value())
                .findFirst().orElseThrow();
        assertTrue(view.moving(), "drawn walking");
        assertEquals(combo.step().clip(), view.busyClip(), "and swinging over it");
        game.runHeadless(KNIGHT.step("L1").frames());
        game.runHeadless(20);
        assertTrue(knight.getPosition().distance(held) > 8f, "and after it");
    }

    @Test
    void theRogueAndTheMageHaveNoComboAndAPressForThemIsIgnored() {
        assertTrue(SHIPPED.combosOf("Rogue").isEmpty());
        assertTrue(SHIPPED.combosOf("Mage").isEmpty());
        var session = Dungeon.newSession(7L, SHIPPED);
        var game = session.game();
        session.run().startWith(game, "Rogue");
        game.runHeadless(5);
        assertFalse(Main.strike(game, false, SHIPPED), "left to the swing ahead they had");
        game.postCommand(PartyOrders.of(new Striking(game.getLocalPlayerIndex(), true)));
        game.runHeadless(30);
        assertTrue(game.getLogic().getObjects().stream()
                .anyMatch(thing -> thing.getTemplate().name().equals("Rogue")), "nothing crashed, he stands");
    }

    @Test
    void theKnightsPressesReachTheSimulationThroughTheGamesOwnOrder() {
        var session = knightSession();
        var game = session.game();
        game.runHeadless(5);
        assertTrue(Main.strike(game, false, SHIPPED));
        game.runHeadless(HEARD);
        var combo = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("Knight")).findFirst().orElseThrow()
                .findModule(Combo.class);
        assertEquals("L1", combo.step().name());
    }
}
