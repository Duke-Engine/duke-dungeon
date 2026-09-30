package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.combat.ArrowUpdate;
import uz.dukeengine.dungeon.combat.Bow;
import uz.dukeengine.dungeon.combat.Swing;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * While the keep's gate stands, nothing that hurts or mends crosses it: a creature within the keep's walls and one
 * outside them cannot reach each other by blow, shot, burst, falling meteor or mending, nor go over it by dash or
 * blink, nor call anything up on its far side — and once it is open, everything reaches as before.
 *
 * <p>Real fights on a floor of the descent with nobody in its chambers — the boss and its guard in the keep — each of
 * them stood where the fight needs them, and the hero on the threshold or the road before it. A fight that asks
 * whether something crosses is fought twice, the gate shut and the gate open, so a gate that stops something is told
 * from a fight in which nothing was ever thrown.
 */
class SealTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final long SEED = 21L;

    /** Long enough for a fight at the gate to have been fought: ten seconds. */
    private static final int A_WHILE = 300;

    /** The shipped game with nobody in the chambers and {@code hero} played. */
    private static DungeonSettings keepOnly(String hero) {
        var data = Content.data();
        var rooms = "    MinSkeletonsPerRoom = 2\n    MaxSkeletonsPerRoom = 6\n";
        var played = "  DefaultHero = Rogue\n";
        assertTrue(data.contains(rooms) && data.contains(played), "the shipped files no longer read this way");
        return DungeonSettings.parse(data
                .replace(rooms, "    MinSkeletonsPerRoom = 0\n    MaxSkeletonsPerRoom = 0\n")
                .replace(played, "  DefaultHero = " + hero + "\n"));
    }

    /**
     * The floor, played: as it was drawn, the gate's cell, and which way is out of it — toward the threshold — so a
     * place is said as cells out from the gate (the threshold's middle at 1, the court just behind the gate at -1) and
     * along its wall.
     */
    private record Floor(Dungeon.Session session, GeneratedDungeon dungeon, int[] gateCell, int outX, int outY) {

        DukeGame game() {
            return session.game();
        }

        GameObject hero() {
            return first(game(), session.run().getHeroTemplate());
        }

        GameObject boss() {
            return first(game(), dungeon.boss().kind());
        }

        GameObject gate() {
            return first(game(), "Gate");
        }

        /** The boss's guard, in the order the floor made them. */
        List<GameObject> guards() {
            var boss = boss();
            return game().getLogic().getObjects().stream()
                    .filter(object -> object.getBody() != null && object != boss
                            && object.getPlayerIndex() == boss.getPlayerIndex())
                    .toList();
        }

        /** The middle of the cell {@code out} cells out from the gate and {@code aside} along its wall. */
        Coord3D cell(int out, int aside) {
            int x = gateCell[0] + out * outX - aside * outY;
            int y = gateCell[1] + out * outY + aside * outX;
            var flat = new Coord3D((x + 0.5f) * 10f, (y + 0.5f) * 10f, 0f);
            var logic = game().getLogic();
            assertFalse(logic.isGroundBlocked(flat), "the cell " + out + " out and " + aside + " along is stone");
            return new Coord3D(flat.x(), flat.y(), logic.groundHeight(flat));
        }

        /** Stand {@code creature} there, going nowhere. */
        GameObject put(GameObject creature, int out, int aside) {
            creature.setPosition(cell(out, aside));
            if (creature.getLocomotor() != null) {
                creature.getLocomotor().stop();
            }
            return creature;
        }

        /** Every one of {@code creatures} gone, and a frame run to take them away. */
        void remove(List<GameObject> creatures) {
            for (var creature : creatures) {
                game().getLogic().destroyObject(creature);
            }
            game().runHeadless(1);
        }

        /** The gate opened, as the key opens it, and a frame run to take it away. */
        void open() {
            gate().findModule(GateUpdate.class).open();
            game().runHeadless(1);
        }

        /** His side stands and picks no fights. */
        void hold() {
            session.orders().hold(game().getLocalPlayerIndex(), true);
        }

        void attack(GameObject who, GameObject what) {
            game().postCommand(new CombatOrder.AttackObject(game().getLocalPlayerIndex(), List.of(who.getId()),
                    what.getId()));
        }

        /** Mana to spare, for a cast that asks where it lands and not whether he can pay for it. */
        SkillBook book() {
            var book = hero().findModule(SkillBook.class);
            book.poolOf(1000, 0);
            book.fillMana();
            return book;
        }
    }

    private static Floor floor(String hero) {
        return floor(hero, SEED);
    }

    private static Floor floor(String hero, long seed) {
        var settings = keepOnly(hero);
        var session = Dungeon.newSession(seed, settings);
        session.game().runHeadless(2);
        var dungeon = DungeonGenerator.generate(seed, settings, 1);
        var keep = dungeon.keep();
        assertNotNull(keep, "the floor has no keep, so there is nothing to seal");
        var gate = keep.gate();
        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (keep.isThreshold(gate[0] + step[0], gate[1] + step[1])) {
                return new Floor(session, dungeon, gate, step[0], step[1]);
            }
        }
        throw new AssertionError("no threshold before the gate");
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow(() -> new AssertionError("no " + template + " on the floor"));
    }

    private static Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream().filter(skill -> skill.key() == key).findFirst().orElseThrow();
    }

    /** The cell {@code thing} stands in. */
    private static int[] cellOf(GameObject thing) {
        var at = thing.getPosition();
        return new int[] {(int) Math.floor(at.x() / PathGrid.DEFAULT_CELL_SIZE),
                (int) Math.floor(at.y() / PathGrid.DEFAULT_CELL_SIZE)};
    }

    /**
     * What a stretch of fight came to: the most {@code watched} had lost of its health at any frame of it, how many
     * shots {@code side} had in the air, and whether {@code swinger} struck a blow.
     */
    private record Fight(float lost, int shots, boolean struck) {
    }

    private static Fight fight(Floor floor, GameObject watched, int side, GameObject swinger, int frames) {
        var game = floor.game();
        var swing = swinger == null ? null : swinger.findModule(Swing.class);
        var shots = new HashSet<Integer>();
        float had = watched.getBody().getHealth();
        float least = had;
        boolean struck = false;
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.getPlayerIndex() == side && object.findModule(ArrowUpdate.class) != null) {
                    shots.add(object.getId().value());
                }
            }
            struck |= swing != null && swing.stillSwinging(game.getLogic().getFrame(), 2);
            least = Math.min(least, watched.getBody().getHealth());
        }
        return new Fight(had - least, shots.size(), struck);
    }

    // ---- shots and blows ----

    /** The Rogue on the threshold shooting at the boss behind the gate: nothing, and once it is open, the boss hurt. */
    @Test
    void aRogueOnTheThresholdHurtsTheBossOnlyOnceTheGateIsOpen() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var rogue = floor.hero();
            var boss = floor.boss();
            floor.remove(floor.guards()); // the boss alone, so whatever hurts it is his
            floor.put(rogue, 1, 0);
            floor.put(boss, -2, 0);
            if (opened) {
                floor.open();
            }
            floor.attack(rogue, boss);

            var fight = fight(floor, boss, rogue.getPlayerIndex(), null, 150);

            assertTrue(fight.shots() > 0, "he never shot, so this proves nothing");
            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, his arrows still did not reach the boss");
            } else {
                assertEquals(0f, fight.lost(), 0.01f, "his arrows hurt the boss through the shut gate");
            }
        }
    }

    /**
     * A hero standing his ground on the threshold, the boss swinging at him from behind the gate and its guard
     * shooting at him through the doorway, is not touched — and once it is open, he is.
     */
    @Test
    void nothingFromBehindTheShutGateReachesAHeroOnItsThreshold() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var hero = floor.hero();
            var boss = floor.boss();
            var guards = floor.guards();
            floor.hold();
            floor.put(hero, 1, 0);
            floor.put(boss, -1, 0); // within its reach of him, but for the gate
            // Where each of them sees him through the doorway, and none so near it backs away.
            int[][] spots = {{-3, -2}, {-3, 2}, {-4, -1}, {-4, 1}};
            for (int i = 0; i < guards.size() && i < spots.length; i++) {
                floor.put(guards.get(i), spots[i][0], spots[i][1]);
            }
            if (opened) {
                floor.open();
            }

            var fight = fight(floor, hero, boss.getPlayerIndex(), boss, A_WHILE);

            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, nothing reached him");
            } else {
                assertTrue(fight.shots() > 0, "the guard never shot at him, so this proves nothing");
                assertTrue(fight.struck(), "the boss never swung at him, so this proves nothing");
                assertEquals(0f, fight.lost(), 0.01f, "something from behind the shut gate hurt him");
            }
        }
    }

    /** A knight on the threshold swings at the boss behind the gate, and hits the gate — until it is open. */
    @Test
    void aKnightOnTheThresholdCannotSwingThroughTheShutGate() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Knight");
            var knight = floor.hero();
            var boss = floor.boss();
            floor.remove(floor.guards());
            floor.put(knight, 1, 0);
            floor.put(boss, -1, 0);
            if (opened) {
                floor.open();
            }
            floor.attack(knight, boss);

            var fight = fight(floor, boss, knight.getPlayerIndex(), null, 150);

            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, his sword still did not reach the boss");
            } else {
                assertEquals(0f, fight.lost(), 0.01f, "his sword reached the boss through the shut gate");
            }
        }
    }

    // ---- what falls, flies and bursts ----

    /** A meteor called down into the court from the road before the gate hurts nobody in it while the gate stands. */
    @Test
    void aMeteorCalledIntoTheCourtFromOutsideHurtsNobodyThere() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Mage");
            var mage = floor.hero();
            var inside = new ArrayList<>(floor.guards());
            inside.add(floor.boss());
            floor.hold();
            floor.put(mage, 3, 0);
            int[][] spots = {{-2, -2}, {-2, 2}, {-3, -1}, {-3, 1}, {-2, 0}};
            for (int i = 0; i < inside.size(); i++) {
                floor.put(inside.get(i), spots[i][0], spots[i][1]);
            }
            if (opened) {
                floor.open();
            }
            float had = 0f;
            for (var one : inside) {
                had += one.getBody().getHealth();
            }

            assertTrue(floor.book().cast('R', 1, null, floor.cell(-2, 0)), "the meteor was not called down");
            floor.game().runHeadless(skillOf("Mage", 'R').windUpFrames() + 5);

            float left = 0f;
            for (var one : inside) {
                left += one.getBody().getHealth();
            }
            if (opened) {
                assertTrue(left < had, "the gate open, the meteor hurt nobody in the court");
            } else {
                assertEquals(had, left, 0.01f, "the meteor hurt them in the court through the shut gate");
            }
        }
    }

    /** A fireball thrown through the doorway at the boss behind the gate is spent on the gate — until it is open. */
    @Test
    void aFireballThrownAtTheShutGateIsSpentOnIt() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Mage");
            var mage = floor.hero();
            var boss = floor.boss();
            floor.remove(floor.guards());
            floor.hold();
            floor.put(mage, 3, 0);
            floor.put(boss, -1, 0);
            if (opened) {
                floor.open();
            }

            assertTrue(floor.book().cast('Q', 1, null, boss.getPosition()), "the fireball was not thrown");
            var fight = fight(floor, boss, mage.getPlayerIndex(), null, 40);

            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, the fireball did not reach the boss");
            } else {
                assertEquals(0f, fight.lost(), 0.01f, "the fireball flew through the shut gate");
            }
        }
    }

    /**
     * A fireball bursting on a guard outside the gate hurts it, and not the boss a step behind the gate, well inside
     * the burst.
     */
    @Test
    void aBurstOutsideTheShutGateCatchesNobodyInside() {
        var floor = floor("Mage");
        var mage = floor.hero();
        var boss = floor.boss();
        var outside = floor.guards().getFirst();
        floor.remove(floor.guards().subList(1, floor.guards().size()));
        floor.hold();
        floor.put(mage, 4, 0);
        floor.put(outside, 1, 0);
        floor.put(boss, -1, 0);
        float bossHad = boss.getBody().getHealth();
        float outsideHad = outside.getBody().getHealth();

        assertTrue(floor.book().cast('Q', 1, null, outside.getPosition()), "the fireball was not thrown");
        floor.game().runHeadless(30);

        assertTrue(outside.getBody().getHealth() < outsideHad, "the fireball did not burst on the one outside");
        assertEquals(bossHad, boss.getBody().getHealth(), 0.01f, "its burst caught the boss through the shut gate");
    }

    /** A nova dropped on the gate itself takes its caster's side of it, and nobody on the other. */
    @Test
    void aNovaDroppedOnTheShutGateCatchesOnlyItsCastersSide() {
        var floor = floor("Mage");
        var mage = floor.hero();
        var boss = floor.boss();
        var outside = floor.guards().getFirst();
        floor.remove(floor.guards().subList(1, floor.guards().size()));
        floor.hold();
        floor.put(mage, 3, 0);
        floor.put(outside, 1, 0);
        floor.put(boss, -1, 0);
        float bossHad = boss.getBody().getHealth();
        float outsideHad = outside.getBody().getHealth();

        assertTrue(floor.book().cast('W', 1, null, floor.cell(0, 0)), "the nova was not dropped");

        assertTrue(outside.getBody().getHealth() < outsideHad, "the nova did not catch the one outside");
        assertTrue(outside.hasStatus(ObjectStatus.SLOWED), "nor slow it");
        assertEquals(bossHad, boss.getBody().getHealth(), 0.01f, "it hurt the boss through the shut gate");
        assertFalse(boss.hasStatus(ObjectStatus.SLOWED), "it slowed the boss through the shut gate");
    }

    /**
     * Every creature with a weapon lands it through a hand the seal can stop: a Swing, for a blow that lands where it
     * stands, or a Bow, whose arrows the gate stops. One with neither would land its blow through the shut gate, the
     * engine's weapon asking nobody.
     */
    @Test
    void everyWeaponLandsThroughAHandTheShutGateCanStop() {
        var game = Dungeon.world(".....\n.....\n.....\n", SETTINGS).game();
        game.runHeadless(1);
        var factory = game.getLogic().getThingFactory();
        var names = new ArrayList<String>();
        SETTINGS.monsters().forEach(kind -> names.add(kind.name()));
        SETTINGS.heroes().forEach(hero -> names.add(hero.name()));

        for (var name : names) {
            var template = factory.findTemplate(name);
            assertNotNull(template, "no creature is called " + name);
            boolean armed = template.modules().stream().anyMatch(data -> data instanceof WeaponUpdate.Data);
            boolean handed = template.modules().stream()
                    .anyMatch(data -> data instanceof Swing.Data || data instanceof Bow.Data);
            assertTrue(!armed || handed, name + " lands its blows through nothing the shut gate can stop");
        }
    }

    // ---- the rule itself ----

    /**
     * The threshold and the court are parted while a keep's gate stands, and on a floor with no keep nothing is — the
     * floor before it having had one or not.
     */
    @Test
    void aFloorWithNoKeepIsNeverSealed() {
        var floor = floor("Rogue");
        var world = floor.game().getLogic();
        var threshold = floor.cell(1, 0);
        var court = floor.cell(-1, 0);
        var seal = new Seal();

        assertFalse(seal.parts(world, threshold, court), "told of no floor, it parted them");
        seal.floor(null, null);
        assertFalse(seal.parts(world, threshold, court), "a floor with no keep parted them");

        seal.floor(floor.dungeon().keep(), floor.gate());
        assertTrue(seal.parts(world, threshold, court) && seal.parts(world, court, threshold),
                "the keep's gate standing, it did not part them, so this proves nothing");
        assertFalse(seal.parts(world, threshold, floor.cell(3, 0)), "it parted two places outside the keep");

        seal.floor(null, null);
        assertFalse(seal.parts(world, threshold, court), "a floor with no keep, after one with, parted them");
    }

    // ---- mending ----

    /**
     * A healer outside the gate, the boss hurt a step behind it and in plain sight through the doorway, calls no light
     * down on it — and once the gate is open, it does.
     */
    @Test
    void aHealerOutsideCannotMendTheBossThroughTheShutGate() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var boss = floor.boss();
            var healer = floor.guards().stream()
                    .filter(guard -> guard.getTemplate().name().equals("SkeletonHealer")).findFirst().orElseThrow();
            floor.remove(floor.guards().stream().filter(guard -> guard != healer).toList());
            floor.hold();
            floor.put(floor.hero(), 8, 0);
            floor.put(healer, 3, 0);
            floor.put(boss, -1, 0);
            boss.getBody().setHealth(boss.getBody().getMaxHealth() * 0.3f);
            if (opened) {
                floor.open();
            }
            var light = SETTINGS.skillsFor("SkeletonHealer").getFirst().projectile();
            float had = boss.getBody().getHealth();

            var called = new HashSet<Integer>();
            float most = had;
            for (int frame = 0; frame < A_WHILE; frame++) {
                floor.game().runHeadless(1);
                for (var object : floor.game().getLogic().getObjects()) {
                    if (object.getTemplate().name().equals(light)) {
                        called.add(object.getId().value());
                    }
                }
                most = Math.max(most, boss.getBody().getHealth());
            }

            if (opened) {
                assertFalse(called.isEmpty(), "the gate open, the healer still called no light down on the boss");
            } else {
                assertTrue(called.isEmpty(), "the healer called its light down through the shut gate");
                assertEquals(had, most, 0.01f, "the boss was mended through the shut gate");
            }
        }
    }

    // ---- what goes over it ----

    @Test
    void theRoguesSprintNeverCarriesHimOverTheShutGate() {
        overTheGate("Rogue", 'E');
    }

    @Test
    void theKnightsChargeNeverCarriesHimOverTheShutGate() {
        overTheGate("Knight", 'W');
    }

    @Test
    void theMagesBlinkNeverCarriesHimOverTheShutGate() {
        overTheGate("Mage", 'E');
    }

    /**
     * On ten floors of the descent, {@code hero} on the threshold's middle casts the skill on {@code key} deep into the
     * court: while the gate stands it is refused, as a landing in stone is — nothing spent and nothing moved — and
     * once it is open it carries him into the court.
     */
    private static void overTheGate(String hero, char key) {
        for (long seed = 21; seed <= 30; seed++) {
            var floor = floor(hero, seed);
            var keep = floor.dungeon().keep();
            var him = floor.hero();
            var book = floor.book();
            var deep = floor.cell(-6, 0); // farther in than any of the three carries
            floor.hold();
            var stood = floor.put(him, 1, 0).getPosition();
            float facing = him.getOrientation();
            int mana = book.getMana();

            boolean went = book.cast(key, 1, null, deep);

            var landed = cellOf(him);
            assertFalse(keep.isCourt(landed[0], landed[1]),
                    "seed " + seed + ": his " + key + " carried him over the shut gate into the court");
            assertFalse(went, "seed " + seed + ": his " + key + " went off with its landing over the shut gate");
            assertTrue(stood.equals(him.getPosition()) && facing == him.getOrientation(),
                    "seed " + seed + ": the refused " + key + " moved or turned him");
            assertTrue(book.isReady(key) && book.getMana() == mana,
                    "seed " + seed + ": the refused " + key + " was paid for");

            floor.open();
            floor.put(him, 1, 0);
            assertTrue(book.cast(key, 1, null, deep), "seed " + seed + ": the gate open, his " + key + " was refused");
            landed = cellOf(him);
            assertTrue(keep.isCourt(landed[0], landed[1]),
                    "seed " + seed + ": the gate open, his " + key + " did not carry him into the court");
        }
    }

    /**
     * A summoner a step behind the gate, the hero in plain sight on the road before it, opens its rifts on its own side
     * of the shut gate and none outside it — and once it is open, it opens one toward him, outside.
     */
    @Test
    void aSummonerBehindTheShutGateOpensNoRiftOutsideIt() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var keep = floor.dungeon().keep();
            var summoner = floor.guards().stream()
                    .filter(guard -> guard.getTemplate().name().equals("SkeletonSummoner")).findFirst().orElseThrow();
            floor.remove(floor.guards().stream().filter(guard -> guard != summoner).toList());
            floor.hold();
            floor.put(floor.hero(), 3, 0);
            // Pressed near the gate: from here a rift sixteen out toward him rises clear of it, on the threshold.
            var behind = floor.put(summoner, -1, 0).getPosition();
            summoner.setPosition(new Coord3D(behind.x() + 3 * floor.outX(), behind.y() + 3 * floor.outY(), behind.z()));
            if (opened) {
                floor.open();
            }
            var rift = SETTINGS.skillsFor("SkeletonSummoner").getFirst().projectile();

            var rifts = new HashSet<Integer>();
            var outside = new HashSet<Integer>();
            for (int frame = 0; frame < A_WHILE; frame++) {
                floor.game().runHeadless(1);
                for (var object : floor.game().getLogic().getObjects()) {
                    if (object.getTemplate().name().equals(rift)) {
                        rifts.add(object.getId().value());
                        var at = cellOf(object);
                        if (!keep.within(at[0], at[1])) {
                            outside.add(object.getId().value());
                        }
                    }
                }
            }

            if (opened) {
                assertFalse(outside.isEmpty(), "the gate open, it opened no rift outside, so this proves nothing");
            } else {
                assertFalse(rifts.isEmpty(), "it opened no rift at all, so this proves nothing");
                assertTrue(outside.isEmpty(), "the summoner opened a rift outside the shut gate");
            }
        }
    }
}
