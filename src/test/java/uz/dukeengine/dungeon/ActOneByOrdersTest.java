package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import uz.dukeengine.action.ActionOrders;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Answer;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Read;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;

/**
 * All of Act I played through the player's own orders, as a player plays it from the hero's shoulder: nothing is put
 * anywhere and nothing is struck by the test. He runs where the world lets him (a held direction, sliding along what
 * stops it, with a route looked up only as a player reads his map), swings with the buttons, works the nearest thing on
 * E, reads what is said and answers what is offered. The one hand the test has beyond the player's is his health, kept
 * from reaching nothing: the walk is what is tested, not his fighting, and what that cost is printed.
 */
class ActOneByOrdersTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    /** One player at one machine: his keys and buttons, and what he reads off the screen. */
    private static final class Player {
        final Dungeon.Session session;
        final DukeGame game;
        final DungeonRun run;
        final int me;
        int read;
        int walking = ActionOrders.NONE;
        int healed;
        int kills;
        int strikes;

        Player(Dungeon.Session session) {
            this.session = session;
            this.game = session.game();
            this.run = session.run();
            this.me = game.getLocalPlayerIndex();
        }

        GameObject hero() {
            return game.getLogic().findObject(run.heroId());
        }

        /** One frame: every line read as it comes, every offer taken, as a player pressing Space. */
        void frame() {
            var said = run.getQuests().said();
            if (said.size() > read) {
                for (int done = read + 1; done <= said.size(); done++) {
                    var talk = SETTINGS.story().conversation(said.get(done - 1));
                    if (talk != null && talk.offer()) {
                        game.postCommand(PartyOrders.of(new Answer(me, done, true)));
                    }
                }
                read = said.size();
                walking = -2; // a line stops him and the client lets go of the keys for it: told again after
                game.postCommand(PartyOrders.of(new Read(me, read)));
            }
            var hero = run.heroId() == null ? null : hero();
            if (hero != null && !hero.isEffectivelyDead()
                    && hero.getBody().getHealth() < hero.getBody().getMaxHealth() * 0.3f) {
                hero.getBody().setHealth(hero.getBody().getMaxHealth());
                healed++;
            }
            game.runHeadless(1);
        }

        void frames(int count) {
            for (int i = 0; i < count; i++) {
                frame();
            }
        }

        /** The held keys: told only when the way changes, as the client tells it. */
        void hold(int angle) {
            if (angle != walking) {
                walking = angle;
                Main.walk(game, angle);
            }
        }

        void holdToward(float x, float y) {
            var at = hero().getPosition();
            double turn = StrictMath.atan2(y - at.y(), x - at.x());
            hold(Math.floorMod((int) Math.round(turn / (2 * Math.PI) * 256), 256));
        }

        double gap(float x, float y) {
            var at = hero().getPosition();
            return Math.hypot(at.x() - x, at.y() - y);
        }

        /**
         * Run to ({@code x}, {@code y}) until {@code there}, or fail where he stuck: the next corner of the route the
         * world would plan, looked up as a player looks at his map, and the direction held toward it.
         */
        void runTo(String what, java.util.function.Supplier<float[]> place, BooleanSupplier there, int most) {
            float[] last = null;
            int stuck = 0;
            Coord3D stood = hero().getPosition();
            Coord3D blocked = stood;
            int side = 0;
            java.util.List<Coord3D> corners = java.util.List.of();
            for (int i = 0; i < most && !there.getAsBoolean(); i++) {
                if (run.inIntro() || run.inCutscene()) {
                    hold(ActionOrders.NONE);
                    frame();
                    continue;
                }
                var want = place.get();
                if (i % 45 == 0 || corners.isEmpty()) {
                    corners = routeTo(want);
                    last = want;
                }
                var next = corners.stream().filter(c -> gap(c.x(), c.y()) > 4f).findFirst().orElse(null);
                if (next == null) {
                    holdToward(want[0], want[1]);
                } else {
                    holdToward(next.x(), next.y());
                }
                frame();
                if (i % 30 == 29) {
                    var moved = hero().getPosition();
                    if (Math.hypot(moved.x() - blocked.x(), moved.y() - blocked.y()) < 4f && nearestFoe(36f, foe -> true) != null) {
                        // Hemmed in, a player cuts his way out.
                        fight("what hems him in on the way to " + what, () -> nearestFoe(36f, foe -> true) == null,
                                foe -> true, 36f, 1800);
                    } else if (Math.hypot(moved.x() - blocked.x(), moved.y() - blocked.y()) < 4f) {
                        // Against a body in his way: a player steps round it, one way and then the other.
                        hold((walking + (side++ % 2 == 0 ? 64 : 192)) & 0xFF);
                        frames(25);
                    }
                    blocked = moved;
                }
                if (i % 150 == 149) {
                    var now = hero().getPosition();
                    stuck = Math.hypot(now.x() - stood.x(), now.y() - stood.y()) < 3f ? stuck + 1 : 0;
                    stood = now;
                    if (stuck >= 4) {
                        throw new AssertionError("he stuck on the way to " + what + " at " + now + " corners " + corners + " near " + game.getLogic().objectsInRange(now, 40f, o -> o != hero()).stream().map(o -> o.getTemplate().name() + "@" + o.getPosition()).toList() + " after " + i
                                + " frames; aiming for " + java.util.Arrays.toString(last));
                    }
                }
            }
            hold(ActionOrders.NONE);
            assertTrue(there.getAsBoolean(), "he never reached " + what + ": stands at " + hero().getPosition()
                    + ", " + (last == null ? "" : (int) gap(last[0], last[1]) + " short"));
        }

        /** The corners to {@code want}, or to beside it where somebody stands on it: a body's own cells have no route in. */
        java.util.List<Coord3D> routeTo(float[] want) {
            for (int ring = 0; ring <= 8; ring++) {
                double turn = ring * Math.PI / 4;
                float off = ring == 0 ? 0f : 12f;
                var route = game.getLogic().findPath(hero(), new Coord3D(want[0] + off * (float) Math.cos(turn),
                        want[1] + off * (float) Math.sin(turn), 0f));
                if (route != null && route.reachesGoal()) {
                    return route.getWaypoints();
                }
            }
            return java.util.List.of();
        }

        void runToZone(String zone, int most) {
            var at = zone(run.getStageName(), zone);
            runTo(zone, () -> new float[] {at.middleX(CELL), at.middleY(CELL)},
                    () -> "world".equals(run.getStageName()) && inside(zone), most);
            frames(SETTINGS.story().dialogue().checkFrames() + 3);
        }

        boolean inside(String zone) {
            var at = zone(run.getStageName(), zone);
            return at.holds(hero().getPosition().x(), hero().getPosition().y(), CELL);
        }

        java.util.List<GameObject> all(String template) {
            return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                    .filter(o -> !o.isEffectivelyDead()).toList();
        }

        /** Run up to the nearest {@code template}, within {@code near}. */
        void runUpTo(String template, float near, int most) {
            var them = nearestOf(template);
            assertNotNull(them, "nobody is called " + template);
            runTo(template, () -> {
                var at = them.getPosition();
                return new float[] {at.x(), at.y()};
            }, () -> gap(them.getPosition().x(), them.getPosition().y()) <= near, most);
        }

        GameObject nearestOf(String template) {
            return all(template).stream().min(Comparator.comparingDouble(o -> gap(o.getPosition().x(), o.getPosition().y())))
                    .orElse(null);
        }

        /** E: as the client's Interact does, the nearest thing with a word within reach and the cone round his facing. */
        boolean interact() {
            var hero = hero();
            if (game.camera().isDriven() || hero.hasStatus(ObjectStatus.DISABLED) || hero.hasStatus(ObjectStatus.HELD)) {
                return false;
            }
            var controls = SETTINGS.controls();
            float half = (float) Math.toRadians(controls.interactCone()) / 2f;
            GameObject best = null;
            String word = null;
            double bestGap = Double.MAX_VALUE;
            for (var thing : game.getLogic().objectsInRange(hero.getPosition(), controls.interactReach(), o -> o != hero)) {
                double far = gap(thing.getPosition().x(), thing.getPosition().y());
                double way = StrictMath.atan2(thing.getPosition().y() - hero.getPosition().y(),
                        thing.getPosition().x() - hero.getPosition().x()) - hero.getOrientation();
                if (Math.abs(StrictMath.atan2(StrictMath.sin(way), StrictMath.cos(way))) > half || far >= bestGap) {
                    continue;
                }
                var named = game.contextWordOn(thing);
                if (named != null) {
                    best = thing;
                    word = named;
                    bestGap = far;
                }
            }
            if (best == null) {
                return false;
            }
            var at = best.getPosition();
            game.postCommand(new GameOrder(me, word, game.getOrderedTo().stream().map(ObjectId::new).toList(),
                    new Coord3D(at.x(), at.y(), 0f), best.getId(), 0));
            return true;
        }

        /** Run up to {@code template} and press E on it, facing it. */
        void talkTo(String template) {
            runUpTo(template, SETTINGS.controls().interactReach() - 2f, 12000);
            var them = nearestOf(template);
            holdToward(them.getPosition().x(), them.getPosition().y());
            frame();
            hold(ActionOrders.NONE);
            frame();
            assertTrue(interact(), "E offers nothing on " + template + " from " + hero().getPosition());
            frames(SETTINGS.story().dialogue().checkFrames() + 3);
        }

        /** Whom his sword may be swung at, nearest first; or none. */
        GameObject nearestFoe(float within, java.util.function.Predicate<GameObject> wanted) {
            var hero = hero();
            var weapon = hero.findModule(WeaponUpdate.class);
            var world = game.getLogic();
            return world.objectsInRange(hero.getPosition(), within, o -> o != hero).stream()
                    .filter(o -> !o.isEffectivelyDead() && o.getBody() != null && wanted.test(o)
                            && world.getRelationship(me, o.getPlayerIndex()) == Relationship.ENEMIES
                            && weapon.canFireAt(o))
                    .min(Comparator.comparingDouble(o -> gap(o.getPosition().x(), o.getPosition().y()))).orElse(null);
        }

        /** Cut down what stands near until {@code done}: run at the nearest, swing when in reach, a chain a press at a time. */
        void fight(String what, BooleanSupplier done, java.util.function.Predicate<GameObject> wanted, float within, int most) {
            var hero = hero();
            var weapon = hero.findModule(WeaponUpdate.class);
            int press = 0;
            int side = 0;
            Coord3D stalled = hero.getPosition();
            java.util.List<Coord3D> corners = java.util.List.of();
            for (int i = 0; i < most && !done.getAsBoolean(); i++) {
                hero = hero();
                if (run.inIntro() || run.inCutscene()) {
                    hold(ActionOrders.NONE);
                    frame();
                    continue;
                }
                var foe = nearestFoe(within, wanted);
                if (foe == null) {
                    hold(ActionOrders.NONE);
                    frame();
                    continue;
                }
                float sword = weapon.reach() - 1f;
                var unit = hero;
                if (uz.dukeengine.core.thing.World.reachBetween(hero, foe) > sword) {
                    // Whatever stands within the sword on the way to it gets the sword first, as a player cuts his way.
                    var beside = nearestFoe(50f, any -> uz.dukeengine.core.thing.World.reachBetween(unit, any) <= sword
                            && (!any.getTemplate().name().equals("GraveRaiser") || wanted.test(any)));
                    foe = beside == null ? foe : beside;
                }
                if (uz.dukeengine.core.thing.World.reachBetween(hero, foe) > sword) {
                    if (i % 45 == 0 || corners.isEmpty()) {
                        corners = routeTo(new float[] {foe.getPosition().x(), foe.getPosition().y()});
                    }
                    var next = corners.stream().filter(c -> gap(c.x(), c.y()) > 4f).findFirst().orElse(null);
                    holdToward(next == null ? foe.getPosition().x() : next.x(), next == null ? foe.getPosition().y() : next.y());
                    if (i % 30 == 29) {
                        var moved = hero.getPosition();
                        if (Math.hypot(moved.x() - stalled.x(), moved.y() - stalled.y()) < 4f) {
                            hold((walking + (side++ % 2 == 0 ? 64 : 192)) & 0xFF);
                            frames(25);
                        }
                        stalled = moved;
                    }
                } else {
                    // Turned to him, then stood: a press swings the way soft aim finds.
                    double way = StrictMath.atan2(foe.getPosition().y() - hero.getPosition().y(),
                            foe.getPosition().x() - hero.getPosition().x()) - hero.getOrientation();
                    if (Math.abs(StrictMath.atan2(StrictMath.sin(way), StrictMath.cos(way))) > Math.toRadians(30)) {
                        holdToward(foe.getPosition().x(), foe.getPosition().y());
                    } else {
                        hold(ActionOrders.NONE);
                        if (i % 12 == 0) {
                            Main.strike(game, press++ % 4 == 3, SETTINGS);
                            strikes++;
                        }
                    }
                }
                frame();
            }
            hold(ActionOrders.NONE);
            assertTrue(done.getAsBoolean(), what + " not done after " + most + " frames; he stands at "
                    + hero().getPosition() + ", quests " + run.getQuests().active() + ", foes near "
                    + game.getLogic().objectsInRange(hero().getPosition(), 120f, o -> o != hero()).stream()
                            .filter(o -> o.getBody() != null && !o.isEffectivelyDead()
                                    && game.getLogic().getRelationship(me, o.getPlayerIndex()) == Relationship.ENEMIES)
                            .map(o -> o.getTemplate().name() + "@" + (int) gap(o.getPosition().x(), o.getPosition().y())
                                    + " hp " + o.getBody().getHealth())
                            .toList());
        }

        boolean questOn(String quest) {
            return run.getQuests().active().stream().anyMatch(q -> q.quest().equals(quest));
        }

        boolean flagged(String flag) {
            return run.getScript().flagged(flag);
        }

        void say(String stage) {
            System.out.println("ACT ONE " + stage + ": frame " + game.getLogic().getFrame() + ", hero at "
                    + hero().getPosition() + ", healed " + healed + " times, " + strikes + " swings");
        }
    }

    private static uz.dukeengine.dungeon.map.StaticMap.Zone zone(String map, String name) {
        var all = new ArrayList<>(Stages.load(map, SETTINGS).story().zones());
        all.addAll(SETTINGS.placesOn(map));
        return all.stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    @Test
    void actOneIsPlayedToItsEndByTheOrdersAPlayerGives() {
        var session = Dungeon.newSession(7L, SETTINGS);
        Bag.show(Bag.of(SETTINGS, (button, told) -> { }, System::nanoTime), session, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 1, null);
        session.game().runHeadless(1);
        var p = new Player(session);
        var run = session.run();
        // The client keeps his hero picked out, whatever else: every order goes to him.
        session.game().setSelection(java.util.List.of(p.hero().getId().value()));

        // 1. The battle, watched: the landing, what he is told, the wave.
        for (int i = 0; i < 3000 && !p.questOn("FirstWave"); i++) {
            p.frame();
        }
        assertTrue(p.questOn("FirstWave"), "the wave comes, and the screen is his");
        assertFalse(run.inIntro(), "the intro is over with the wave");
        p.say("the wave comes");
        session.game().setSelection(java.util.List.of(p.hero().getId().value()));
        p.fight("the first wave", () -> p.flagged("BattleWon"), foe -> true, 400f, 9000);
        p.say("the wave beaten");
        assertTrue(p.questOn("FollowCommander"));

        // 2. Behind her to the palace, then the queen on E.
        p.runTo("the commander's side at the palace", () -> {
            var her = p.nearestOf("Act1Commander").getPosition();
            return new float[] {her.x(), her.y()};
        }, () -> !p.questOn("FollowCommander"), 12000);
        p.say("led to the palace");
        assertTrue(p.questOn("MeetTheQueen"), "the log says to see the queen: " + run.getQuests().active());
        p.talkTo("Act1Queen");
        p.frames(10);
        assertTrue(p.flagged("Welcomed"), "welcomed: " + run.getQuests().active());
        assertTrue(p.questOn("Training"), "sent to the graves");
        var bag = run.progressOf(p.me).getLoot();
        assertEquals("Medallion", bag.at(bag.wornPlace("Tumor")).id(), "her medallion worn");
        p.say("the queen");

        // 3. The side quests of the capital: an offer on E, the cellar's nest, the ring on E, both handed back.
        p.talkTo("Blacksmith");
        p.frames(5);
        assertTrue(p.questOn("Cellar"), "the smith's offer taken: " + run.getQuests().active());
        p.talkTo("OldWoman");
        p.frames(5);
        assertTrue(p.questOn("LostRing"), "the old woman's offer taken: " + run.getQuests().active());
        p.runToZone("Yerto'la", 12000);
        p.say("in the cellar");
        p.fight("the nest", () -> !p.questOn("Cellar") || run.getQuests().active().stream()
                .anyMatch(q -> q.quest().equals("Cellar") && q.step() == 2), foe -> true, 400f, 12000);
        var ring = p.nearestOf(SETTINGS.lootDrops().template());
        assertNotNull(ring, "the ring lies in the cellar");
        p.runUpTo(SETTINGS.lootDrops().template(), SETTINGS.controls().interactReach() - 2f, 3000);
        p.holdToward(ring.getPosition().x(), ring.getPosition().y());
        p.frame();
        p.hold(ActionOrders.NONE);
        p.frame();
        assertTrue(p.interact(), "E takes the ring");
        p.frames(60);
        p.talkTo("OldWoman");
        p.frames(5);
        assertTrue(run.getQuests().finished().contains("LostRing"), "her ring back: " + run.getQuests().active());
        p.talkTo("Blacksmith");
        p.frames(5);
        assertTrue(run.getQuests().finished().contains("Cellar"), "the nest cleared: " + run.getQuests().active());
        p.say("the side quests");

        // The merchant's window on E.
        p.talkTo("Merchant");
        assertFalse(run.getTrades().isEmpty(), "E opens his trade");

        // 4. The old graves: through the north gate, each grave broken, then the raiser.
        p.runToZone("Eski_qabrlar", 12000);
        p.frames(10);
        assertEquals(1, p.all("Act1CommanderFighter").size(), "the commander comes up");
        p.say("at the old graves");
        // As the quest says: the graves first, one by one -- their raiser takes nothing while one stands -- then him.
        p.fight("the graves", () -> p.flagged("Trained"), foe -> foe.getTemplate().name()
                .equals(p.all("Grave").isEmpty() ? "GraveRaiser" : "Grave"), 400f, 18000);
        p.say("the graves gone");
        p.frames(10);
        assertTrue(p.flagged("WarBegun"), "he goes on alone");
        assertTrue(p.questOn("ToWar"), "north: " + run.getQuests().active());

        // 5. North across the world, into the Black Knight's camp and his castle.
        p.runTo("the Black Knight's camp", () -> {
            var at = zone("world", "Qora_qarorgoh");
            return new float[] {at.middleX(CELL), at.middleY(CELL)};
        }, () -> "qora_qasr".equals(run.getStageName()), 45000);
        p.say("in the dark castle");

        // 6. The dark castle: its lord cut down, the turning, the end.
        var saved = new ArrayList<String>();
        run.onSaveInto((slot, save) -> saved.add(slot));
        p.frames(120);
        session.game().setSelection(java.util.List.of(p.hero().getId().value()));
        assertNotNull(p.nearestOf("DarkLord"), "its lord is there");
        p.fight("the dark lord", () -> p.all("DarkLord").isEmpty(), foe -> foe.getTemplate().name().equals("DarkLord"), 500f, 24000);
        p.say("the dark lord down");
        for (int i = 0; i < 1800 && (run.getState() != DungeonRun.State.WON || session.game().titleCardShowing() == null
                || !"1-qism tugadi".equals(session.game().titleCardShowing().text())); i++) {
            p.frame();
        }
        assertEquals(DungeonRun.State.WON, run.getState(), "the act ends");
        assertEquals(java.util.List.of("act1"), saved, "saved for the second act");
        assertEquals("1-qism tugadi", session.game().titleCardShowing().text());
        p.say("the end of the act");
    }
}
