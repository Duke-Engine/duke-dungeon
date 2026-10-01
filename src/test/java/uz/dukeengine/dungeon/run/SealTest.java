package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.ai.SightLine;
import uz.dukeengine.dungeon.combat.ArrowUpdate;
import uz.dukeengine.dungeon.combat.Bow;
import uz.dukeengine.dungeon.combat.Swing;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.message.CombatOrder;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * While the keep's gate stands, nothing that hurts or mends crosses it: a creature within the keep's walls and one
 * outside them cannot reach each other by blow, shot, burst, falling meteor or mending — the fountain's water too —
 * nor lend each other a haste or an aura, nor go over it by dash or blink, nor call anything up on its far side — and
 * once it is open, everything reaches as before.
 *
 * <p>Real fights on a floor of the descent with nobody in its chambers — the boss and its guard in the keep — each of
 * them stood where the fight needs them, and the hero on the threshold or the road before it. A fight that asks
 * whether something crosses is fought twice, the gate shut and the gate open, so a gate that stops something is told
 * from a fight in which nothing was ever thrown.
 *
 * <p>And the data the rule leans on without asking: that the engine lands no blow of its own, and that no creature
 * outside is slim enough to stand in the doorway's cell — held to by tests of the creature files, which are no fights.
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
            float size = PathGrid.DEFAULT_CELL_SIZE;
            var flat = new Coord3D((x + 0.5f) * size, (y + 0.5f) * size, 0f);
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

        /**
         * Stand {@code creature} against the gate's face on its {@code side} of it -- 1 outside, -1 within -- on the
         * line through the gate's middle, as near the gate as a body goes: half a unit off it, since the engine counts
         * a body that only touches the gate as overlapping it, and shoves it clear.
         */
        GameObject pressToGate(GameObject creature, int side) {
            // The gate as it stood, shut or not: its template's shape across its doorway.
            var gate = new Footprint(Solid.of(game().getLogic().findTemplate("Gate")), cell(0, 0),
                    dungeon.keep().facing());
            var middle = put(creature, side, 0).getPosition();
            float inward = Footprint.of(creature).separation(gate) - 0.5f;
            creature.setPosition(new Coord3D(middle.x() - side * inward * outX, middle.y() - side * inward * outY,
                    middle.z()));
            assertEquals(0.5f, Footprint.of(creature).separation(gate), 0.01f,
                    "pressed to the gate, a body stands off it by half a unit");
            return creature;
        }

        /** A new {@code kind} of the boss's side, {@code out} cells out from the gate, {@code aside} along its wall. */
        GameObject spawn(String kind, int out, int aside) {
            var world = game().getLogic();
            var born = world.spawn(world.findTemplate(kind), cell(out, aside), boss().getPlayerIndex());
            game().runHeadless(1);
            return born;
        }

        /** The first of the boss's guard that is a {@code kind}: the floor makes two of each. */
        GameObject guard(String kind) {
            return guards().stream().filter(guard -> guard.getTemplate().name().equals(kind)).findFirst()
                    .orElseThrow(() -> new AssertionError("no " + kind + " among the guard"));
        }

        /** The boss's guard gone but {@code these}, and a frame run to take them away. */
        void removeGuardsBut(GameObject... these) {
            var kept = List.of(these);
            remove(guards().stream().filter(guard -> !kept.contains(guard)).toList());
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

            var fight = fight(floor, boss, knight.getPlayerIndex(), knight, 150);

            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, his sword still did not reach the boss");
            } else {
                assertTrue(fight.struck(), "he never swung, so this proves nothing");
                assertEquals(0f, fight.lost(), 0.01f, "his sword reached the boss through the shut gate");
            }
        }
    }

    /**
     * An archer pressed to the gate's inner face and the hero to its outer, so near that its bolt is at him the
     * moment it leaves the crossbow, before it has flown a step: it is spent on the shut gate all the same, and once
     * the gate is open it hurts him.
     */
    @Test
    void aBoltAlreadyAtItsVictimIsSpentOnTheShutGate() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var hero = floor.hero();
            var boss = floor.boss();
            floor.remove(floor.guards());
            var archer = floor.spawn("Stalker", -1, 0);
            floor.hold();
            if (opened) {
                floor.open();
            }
            floor.put(boss, -8, 0); // far back, and out of it
            floor.pressToGate(archer, -1);
            floor.pressToGate(hero, 1);

            var fight = fight(floor, hero, archer.getPlayerIndex(), null, 90);

            assertTrue(fight.shots() > 0, "the archer never loosed, so this proves nothing");
            if (opened) {
                assertTrue(fight.lost() > 0f, "the gate open, its bolt still did not reach him");
            } else {
                assertEquals(0f, fight.lost(), 0.01f, "its bolt hurt him through the shut gate");
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

    // ---- the data it leans on ----

    /** The game's own world with nobody in it, to ask the data's templates of. */
    private static World bareWorld() {
        var game = Dungeon.world(".....\n.....\n.....\n", SETTINGS).game();
        game.runHeadless(1);
        return game.getLogic();
    }

    /** The name of every creature of the data: the monsters, then the heroes. */
    private static List<String> creatureNames() {
        var names = new ArrayList<String>();
        SETTINGS.monsters().forEach(kind -> names.add(kind.name()));
        SETTINGS.heroes().forEach(hero -> names.add(hero.name()));
        return names;
    }

    /**
     * Every creature with a weapon lands it through a hand the seal can stop: a Swing, for a blow that lands where it
     * stands, or a Bow, whose arrows the gate stops. One with neither would land its blow through the shut gate, the
     * engine's weapon asking nobody. So would a blow the engine lands itself: a splash, which it spreads round the one
     * hit, or a shot a Bow declines, as it does where its projectile carries no ArrowUpdate -- a Swing returns early
     * for a Bow's carrier, so nothing else takes it.
     */
    @Test
    void everyWeaponLandsThroughAHandTheShutGateCanStop() {
        var world = bareWorld();
        for (var name : creatureNames()) {
            var template = world.findTemplate(name);
            assertNotNull(template, "no creature is called " + name);
            var weapons = template.modules().stream().filter(data -> data instanceof WeaponUpdate.Data)
                    .map(data -> (WeaponUpdate.Data) data).toList();
            boolean handed = template.modules().stream()
                    .anyMatch(data -> data instanceof Swing.Data || data instanceof Bow.Data);
            assertTrue(weapons.isEmpty() || handed, name + " lands its blows through nothing the shut gate can stop");
            for (var weapon : weapons) {
                assertEquals(0f, weapon.splashRadius(), 0f,
                        name + "'s weapon has a SplashRadius: the engine splashes round the one hit, asking nobody");
                assertTrue(weapon.weaponSets().isEmpty(), name + " has weapon sets, whose named weapons may have a"
                        + " SecondaryRadius, a second ring the engine lands round the one hit, asking nobody");
            }
            for (var data : template.modules()) {
                if (data instanceof Bow.Data bow) {
                    var arrow = bow.projectile() == null ? SETTINGS.combat().arrowTemplate() : bow.projectile();
                    var shot = world.findTemplate(arrow);
                    assertTrue(shot != null && shot.modules().stream().anyMatch(one -> one instanceof ArrowUpdate.Data),
                            name + "'s bow looses " + arrow + ", which carries no ArrowUpdate: the bow declines the"
                                    + " shot, and the engine lands the blow itself, asking nobody");
                }
            }
        }
    }

    /**
     * No creature outside the gate ever stands in the doorway's cell. The seal draws its line at that cell's edge,
     * five units from the gate's middle line, and a body pressed to the gate's outer face has its middle as far from
     * the line as the face is -- the gate's MinorRadius -- and its own footprint radius; so each creature must be broad
     * enough to put that at the edge or past it, or one outside could stand in the cell, to be taken for one within the
     * keep. At exactly half a cell, as the Runner's and the Stalker's, the middle comes to the edge and no nearer: the
     * engine counts a body that only touches the gate as overlapping it, and keeps it clear.
     */
    @Test
    void noCreatureOutsideTheGateReachesIntoTheDoorwaysCell() {
        var world = bareWorld();
        float half = PathGrid.DEFAULT_CELL_SIZE / 2f;
        float face = assertInstanceOf(Geometry.Box.class, Solid.of(world.findTemplate("Gate")), "the gate is no box")
                .minorRadius();
        for (var name : creatureNames()) {
            float radius = Solid.of(world.findTemplate(name)).footprintRadius();
            assertTrue(face + radius >= half, name + " has a radius of " + radius + ", and the gate's face stands "
                    + face + " from its middle line: pressed to the face, its middle would be " + (face + radius)
                    + " from that line, short of half a cell (" + half + "), and so in the doorway's cell, which the"
                    + " seal counts within the keep");
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
        var doorway = floor.cell(0, 0);
        assertTrue(seal.parts(world, doorway, threshold), "the doorway is within the keep: parted from the threshold");
        assertFalse(seal.parts(world, doorway, court), "the doorway is within the keep: not parted from the court");

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

    /**
     * A healer outside the gate with two of its own hurt, in reach and plain sight -- the boss, the worse of them, a
     * step behind the gate, and a Skeleton beside it -- mends the one beside it: the brain does not choose the one the
     * book would refuse, and leave the other hurt. Once the gate is open it mends the boss, the worst hurt.
     */
    @Test
    void aHealerWithTheWorseHurtBehindTheShutGateMendsTheOneBesideIt() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var boss = floor.boss();
            var healer = floor.guard("SkeletonHealer");
            floor.removeGuardsBut(healer);
            var beside = floor.spawn("Skeleton", 2, 0);
            floor.hold();
            floor.put(floor.hero(), 8, 0);
            floor.put(healer, 3, 0);
            floor.put(boss, -1, 0);
            boss.getBody().setHealth(boss.getBody().getMaxHealth() * 0.3f);
            beside.getBody().setHealth(beside.getBody().getMaxHealth() * 0.5f);
            if (opened) {
                floor.open();
            }
            float bossHad = boss.getBody().getHealth();
            float besideHad = beside.getBody().getHealth();

            float bossMost = bossHad;
            float besideMost = besideHad;
            for (int frame = 0; frame < A_WHILE; frame++) {
                floor.game().runHeadless(1);
                bossMost = Math.max(bossMost, boss.getBody().getHealth());
                besideMost = Math.max(besideMost, beside.getBody().getHealth());
            }

            if (opened) {
                assertTrue(bossMost > bossHad, "the gate open, the healer did not mend the boss, the worse hurt");
            } else {
                assertEquals(bossHad, bossMost, 0.01f, "the boss was mended through the shut gate");
                assertTrue(besideMost > besideHad, "the healer mended nobody, the worse hurt being behind the gate");
            }
        }
    }

    /**
     * The water mends whoever stands near it and stops at the shut gate. A fountain on the threshold, a Skeleton hurt on
     * the road beside it and a summoner hurt and spent just inside the gate -- twenty units from it, well within its
     * Radius: two pulses on, the Skeleton is mended either way, and the summoner, health and mana, only once the gate
     * is open. Both are put back where they stood before each frame, a wounded monster setting off for the hero.
     */
    @Test
    void aFountainOutsideMendsNobodyInsideTheShutGate() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var summoner = floor.guard("SkeletonSummoner");
            floor.removeGuardsBut(summoner);
            floor.hold();
            floor.put(floor.boss(), -8, 0); // far back, and out of it
            var beside = floor.spawn("Skeleton", 3, 0);
            floor.spawn("Fountain", 1, 0);
            if (opened) {
                floor.open();
            }
            var pool = summoner.findModule(SkillBook.class);
            pool.resize(0, 0);
            pool.resize(100, 0); // room for a hundred and none of it filled, and no trickle of its own
            summoner.getBody().setHealth(summoner.getBody().getMaxHealth() / 2f);
            beside.getBody().setHealth(beside.getBody().getMaxHealth() / 2f);
            float summonerHad = summoner.getBody().getHealth();
            float besideHad = beside.getBody().getHealth();

            for (int frame = 0; frame < 60; frame++) { // two of its pulses
                floor.put(summoner, -1, 0);
                floor.put(beside, 3, 0);
                floor.game().runHeadless(1);
            }

            int summonerPulse = Math.max(1, Math.round(summoner.getBody().getMaxHealth() * 5 / 100f));
            int besidePulse = Math.max(1, Math.round(beside.getBody().getMaxHealth() * 5 / 100f));
            assertEquals(besideHad + 2 * besidePulse, beside.getBody().getHealth(), 0.01f,
                    "the fountain did not mend the one beside it, so this proves nothing");
            if (opened) {
                assertEquals(summonerHad + 2 * summonerPulse, summoner.getBody().getHealth(), 0.01f,
                        "the gate open, the fountain did not mend the one inside");
                assertEquals(10, pool.getMana(), "and a twentieth of its pool back in each pulse");
            } else {
                assertEquals(summonerHad, summoner.getBody().getHealth(), 0.01f,
                        "the fountain mended the one inside through the shut gate");
                assertEquals(0, pool.getMana(), "and gave it mana through the shut gate");
            }
        }
    }

    // ---- what is lent ----

    /** How hard its blows and its skills land: its book's multiplier, as its weapon reads it. */
    private static float might(GameObject creature) {
        return creature.findModule(SkillBook.class).damageMultiplier();
    }

    /**
     * A summoner just inside the gate, a Skeleton just outside it, within its Radius and in its plain sight through
     * the doorway: it lends the Skeleton no might while the gate stands, and once it is open, half as hard again.
     */
    @Test
    void aSummonerInsideLendsNoMightToASkeletonOutsideTheShutGate() {
        float lent = 1f + skillOf("SkeletonSummoner", 'E').boostPercent() / 100f;
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var summoner = floor.guard("SkeletonSummoner");
            floor.removeGuardsBut(summoner);
            floor.put(summoner, -1, 0);
            var skeleton = floor.spawn("Skeleton", 1, 0);
            if (opened) {
                floor.open();
            }

            assertTrue(SightLine.clear(summoner, skeleton),
                    "it does not see it through the doorway, so this proves nothing");
            if (opened) {
                assertEquals(lent, might(skeleton), 0.0001f, "the gate open, the summoner still lent no might");
            } else {
                assertEquals(1f, might(skeleton), 0.0001f, "the summoner lent its might through the shut gate");
            }
        }
    }

    /**
     * A healer just inside the gate, a summoner just outside it with its pool emptied, within the healer's Radius and
     * in its plain sight: two seconds on, the pool holds only its own trickle while the gate stands, and once it is
     * open, the aura's as well.
     */
    @Test
    void aHealerInsideFillsNoPoolOutsideTheShutGate() {
        int aura = skillOf("SkeletonHealer", 'W').manaRegen();
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var healer = floor.guard("SkeletonHealer");
            var summoner = floor.guard("SkeletonSummoner");
            floor.removeGuardsBut(healer, summoner);
            floor.put(healer, -1, 0);
            floor.put(summoner, 1, 0);
            if (opened) {
                floor.open();
            }
            var pool = summoner.findModule(SkillBook.class);
            int most = pool.getMaxMana();
            int tenths = pool.getManaRegen();
            pool.resize(0, 0);
            pool.resize(most, tenths); // as big as it was, and empty

            floor.game().runHeadless(60);

            assertTrue(SightLine.clear(healer, summoner),
                    "it does not see it through the doorway, so this proves nothing");
            assertTrue(most > 0 && tenths > 0, "the summoner has no pool of its own trickling, so this proves nothing");
            // Two seconds at so many tenths of a point a second: a point falls out for every five tenths.
            if (opened) {
                assertEquals((tenths + aura) / 5, pool.getMana(), "the gate open, the healer still filled no pool");
            } else {
                assertEquals(tenths / 5, pool.getMana(), "the healer filled a pool through the shut gate");
            }
        }
    }

    /**
     * A summoner just inside the gate and the boss, the sturdier by far, just outside it, within its Range and in its
     * plain sight through the doorway: it hastens itself and never the boss while the gate stands, and once it is
     * open, the boss.
     */
    @Test
    void aSummonerInsideNeverHastensTheBossOutsideTheShutGate() {
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var summoner = floor.guard("SkeletonSummoner");
            var boss = floor.boss();
            floor.removeGuardsBut(summoner);
            floor.put(summoner, -1, 0);
            floor.put(boss, 1, 0);
            if (opened) {
                floor.open();
            }
            var its = summoner.findModule(SkillBook.class);
            var his = boss.findModule(SkillBook.class);

            assertTrue(SightLine.clear(summoner, boss),
                    "it does not see the boss through the doorway, so this proves nothing");
            assertTrue(its.cast('W', 1, null, null), "the summoner did not cast its haste");
            if (opened) {
                assertTrue(his.getHasteFrames() > 0, "the gate open, the summoner still did not hasten the boss");
                assertEquals(0, its.getHasteFrames(), "and it hastened itself as well");
            } else {
                assertEquals(0, his.getHasteFrames(), "the summoner hastened the boss through the shut gate");
                assertTrue(its.getHasteFrames() > 0, "and it hastened nobody, not even itself");
            }
        }
    }

    /**
     * A Revenant just outside the gate and the boss just inside it, within its Radius and in its plain sight through
     * the doorway: it lends the boss none of its thirst while the gate stands, and once it is open, its share of every
     * blow -- and the one rule for what is on a creature, which the circle under it and the HUD's row ask, says the
     * same.
     */
    @Test
    void aRevenantOutsideLendsNoThirstToTheBossInsideTheShutGate() {
        int thirst = skillOf("Revenant", 'Q').boostPercent();
        for (boolean opened : new boolean[] {false, true}) {
            var floor = floor("Rogue");
            var boss = floor.boss();
            floor.remove(floor.guards());
            floor.put(boss, -1, 0);
            var revenant = floor.spawn("Revenant", 1, 0);
            if (opened) {
                floor.open();
            }

            assertTrue(SightLine.clear(revenant, boss),
                    "it does not see the boss through the doorway, so this proves nothing");
            int lent = SkillBook.auraOn(boss, SkillEffect.LIFESTEAL_AURA);
            if (opened) {
                assertEquals(thirst, lent, "the gate open, the Revenant still lent no thirst");
                assertEquals(List.of(SkillEffect.LIFESTEAL_AURA), SkillBook.aurasOn(boss),
                        "the gate open, nothing is said to be on the boss");
            } else {
                assertEquals(0, lent, "the Revenant lent its thirst through the shut gate");
                assertEquals(List.of(), SkillBook.aurasOn(boss), "the thirst is said to be on it through the shut gate");
            }
        }
    }

    // ---- what goes over it ----

    /** The Rogue's sprint from the threshold into the court: refused while the gate stands, made once it is open. */
    @Test
    void theRoguesSprintNeverCarriesHimOverTheShutGate() {
        overTheGate("Rogue", 'E', 1, -6);
    }

    /** The Knight's charge from the threshold into the court: refused while the gate stands, made once it is open. */
    @Test
    void theKnightsChargeNeverCarriesHimOverTheShutGate() {
        overTheGate("Knight", 'W', 1, -6);
    }

    /** The Mage's blink from the threshold into the court: refused while the gate stands, made once it is open. */
    @Test
    void theMagesBlinkNeverCarriesHimOverTheShutGate() {
        overTheGate("Mage", 'E', 1, -6);
    }

    /** The other way over it: from the court's first cell toward the road, the Rogue's sprint is refused just so. */
    @Test
    void theRoguesSprintFromTheCourtNeverCarriesHimOutOverTheShutGate() {
        overTheGate("Rogue", 'E', -1, 3);
    }

    /**
     * What the gate leaves alone: a sprint or a blink that comes down on its own side of it goes off while it stands
     * -- from the threshold out along the road, and from the court's first cell deeper into the court -- carrying him
     * there and paid for, on a floor with the gate on each of the four sides.
     */
    @Test
    void aSprintOrABlinkThatKeepsToItsOwnSideGoesOffWhileTheGateIsShut() {
        for (var skill : new String[][] {{"Rogue", "E"}, {"Mage", "E"}}) {
            char key = skill[1].charAt(0);
            for (long seed : new long[] {21, 24, 25, 29}) { // the gate on the keep's south, west, east and north
                for (int from : new int[] {1, -1}) {
                    var floor = floor(skill[0], seed);
                    var keep = floor.dungeon().keep();
                    var him = floor.hero();
                    var book = floor.book();
                    floor.hold();
                    var stood = floor.put(him, from, 0).getPosition();
                    int mana = book.getMana();
                    var where = "seed " + seed + ", from " + from + ": his " + key + " ";

                    boolean went = book.cast(key, 1, null, floor.cell(3 * from, 0));

                    var landed = cellOf(him);
                    assertTrue(went, where + "was refused, landing on his own side of the shut gate");
                    assertFalse(stood.equals(him.getPosition()), where + "carried him nowhere");
                    assertEquals(from < 0, keep.within(landed[0], landed[1]), where + "carried him over the shut gate");
                    assertTrue(!book.isReady(key) && book.getMana() < mana, where + "was not paid for");
                }
            }
        }
    }

    /**
     * On ten floors of the descent, {@code hero} casts the skill on {@code key} over the gate, from {@code from} cells
     * out of it toward {@code to} cells out -- a negative number is within the keep: while the gate stands it is
     * refused, as a landing in stone is — nothing spent and nothing moved — and once it is open it carries him over.
     */
    private static void overTheGate(String hero, char key, int from, int to) {
        boolean fromTheCourt = from < 0;
        for (long seed = 21; seed <= 30; seed++) {
            var floor = floor(hero, seed);
            var keep = floor.dungeon().keep();
            var him = floor.hero();
            var book = floor.book();
            var there = floor.cell(to, 0);
            floor.hold();
            var stood = floor.put(him, from, 0).getPosition();
            float facing = him.getOrientation();
            int mana = book.getMana();

            boolean went = book.cast(key, 1, null, there);

            var landed = cellOf(him);
            assertEquals(fromTheCourt, keep.isCourt(landed[0], landed[1]),
                    "seed " + seed + ": his " + key + " carried him over the shut gate");
            assertFalse(went, "seed " + seed + ": his " + key + " went off with its landing over the shut gate");
            assertTrue(stood.equals(him.getPosition()) && facing == him.getOrientation(),
                    "seed " + seed + ": the refused " + key + " moved or turned him");
            assertTrue(book.isReady(key) && book.getMana() == mana,
                    "seed " + seed + ": the refused " + key + " was paid for");

            floor.open();
            floor.put(him, from, 0);
            assertTrue(book.cast(key, 1, null, there), "seed " + seed + ": the gate open, his " + key + " was refused");
            landed = cellOf(him);
            assertEquals(!fromTheCourt, keep.isCourt(landed[0], landed[1]),
                    "seed " + seed + ": the gate open, his " + key + " did not carry him over");
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
