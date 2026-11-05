package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.stage.Stage;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.skill.CastSkill;
import uz.dukeengine.rpg.skill.SkillBook;

/** A horse on the open world: called, got onto, ridden at twice his walk, got off. See docs/plan/2026-10-15-mounts.md. */
class MountTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final Stage WORLD = Stages.load("world", SHIPPED);
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
    private static final uz.dukeengine.dungeon.mount.Mount MOUNT = SHIPPED.mount();

    private static GameObject hero(DukeGame game) {
        return FoodTest.hero(game);
    }

    private static GameObject horse(DukeGame game) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(MOUNT.horse()))
                .findFirst().orElse(null);
    }

    /** His MOUNT skill (E89): riding is it. */
    private static uz.dukeengine.rpg.skill.Skill riding(GameObject hero) {
        return SHIPPED.bookFor(hero.getTemplate().name()).stream()
                .filter(s -> s.effect() == uz.dukeengine.rpg.skill.SkillEffect.MOUNT).findFirst().orElseThrow();
    }

    private static StaticZone zone(String name) {
        return WORLD.story().zones().stream().filter(z -> z.name().equals(name)).findFirst()
                .map(z -> new StaticZone(z.middleX(CELL), z.middleY(CELL), z)).orElseThrow();
    }

    private record StaticZone(float x, float y, uz.dukeengine.dungeon.map.StaticMap.Zone zone) {
    }

    private static void put(GameObject thing, float x, float y) {
        var logic = thing.getWorld();
        thing.setPosition(new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f))));
    }

    /** The world, its hero by its second watchtower (out of town, no camp near) with the whistle, or without it. */
    private static Dungeon.Session byATower(boolean whistle) {
        var session = Dungeon.newStageSession(WORLD, SHIPPED);
        var game = session.game();
        game.runHeadless(2);
        var tower = zone("Minora_2");
        put(hero(game), tower.x(), tower.y());
        if (whistle) {
            var items = SHIPPED.items();
            session.progress().getLoot().take(items.potion(items.potion(MOUNT.whistle()), 1, 1), 0, 0);
        }
        game.runHeadless(2);
        return session;
    }

    private static void press(DukeGame game) {
        game.postCommand(PartyOrders.of(new Ride(game.getLocalPlayerIndex(), null)));
    }

    /** Called, run in, and got up onto: the frames it took. */
    private static void mount(Dungeon.Session session) {
        var game = session.game();
        var horse = horse(game);
        if (horse == null || Math.hypot(horse.getPosition().x() - hero(game).getPosition().x(),
                horse.getPosition().y() - hero(game).getPosition().y()) > MOUNT.reach()) {
            press(game); // called: it runs in; one standing by him is got onto at the first press
            game.runHeadless(2);
            horse = horse(game);
        }
        assertNotNull(horse, "the whistle calls a horse");
        for (int run = 0; run < 400 && Math.hypot(horse.getPosition().x() - hero(game).getPosition().x(),
                horse.getPosition().y() - hero(game).getPosition().y()) > MOUNT.reach(); run++) {
            game.runHeadless(1);
        }
        press(game);
        game.runHeadless(riding(hero(game)).windUpFrames() + 3);
        assertTrue(session.run().mounted(game.getLocalPlayerIndex()), "up on it");
    }

    /**
     * His speed at full stride, units a second: the way he went over a second, once under way walking east on his keys
     * (E74). On his keys, as the engine multiplies his MOUNT into a walk along (a click-to-move's step is held to his
     * own top speed by its giving way: noted for the engine in E89's section).
     */
    /** The way from where he stands that is open for 150 units, by the walking grid: no stone, nothing in the way. */
    private static int openWay(DukeGame game) {
        var hero = hero(game);
        var grid = game.getLogic().getPathGrid();
        float r = uz.dukeengine.core.thing.Solid.of(hero.getTemplate()).footprintRadius() + 2f;
        for (int angle : new int[] {0, 90, 180, 270}) {
            boolean open = true;
            for (float along = 0f; along <= 150f && open; along += 2.5f) {
                float x = hero.getPosition().x() + along * (float) Math.cos(Math.toRadians(angle));
                float y = hero.getPosition().y() + along * (float) Math.sin(Math.toRadians(angle));
                int cx = (int) (x / grid.getCellSize());
                int cy = (int) (y / grid.getCellSize());
                open = !grid.isBlocked(cx, cy) && !grid.isCliff(cx, cy) && grid.clearOfCircles(x, y, r);
            }
            if (open) {
                return angle;
            }
        }
        throw new AssertionError("no way open from the tower: the scene is gone");
    }

    private static float stride(DukeGame game) {
        var hero = hero(game);
        var legs = hero.findModule(MoveUpdate.class);
        int way = openWay(game); // straight on along the open way: a walk to a point 150 units off
        var at = hero.getPosition();
        legs.moveTo(new uz.dukeengine.core.math.Coord3D(at.x() + 150f * (float) Math.cos(Math.toRadians(way)),
                at.y() + 150f * (float) Math.sin(Math.toRadians(way)), at.z()));
        game.runHeadless(10);
        float went = 0f;
        for (int frame = 0; frame < 30; frame++) {
            var from = hero.getPosition();
            game.runHeadless(1);
            var to = hero.getPosition();
            went += (float) Math.hypot(to.x() - from.x(), to.y() - from.y());
        }
        legs.stop();
        return went;
    }

    @Test
    void theWhistleCallsAHorseThatRunsInAndRidingHeGoesTwiceHisWalkDrawnRiding() {
        var session = byATower(true);
        var game = session.game();
        float walking = stride(game);
        assertTrue(walking > 10f, "he walks: " + walking);
        hero(game).getLocomotor().stop();
        game.runHeadless(2);

        press(game);
        game.runHeadless(2);
        var horse = horse(game);
        assertNotNull(horse, "a horse answers");
        float off = (float) Math.hypot(horse.getPosition().x() - hero(game).getPosition().x(),
                horse.getPosition().y() - hero(game).getPosition().y());
        assertTrue(off > 60f, "from off the screen: " + off);
        mount(session);
        assertEquals(horse.getId(), hero(game).getMount(), "on the horse's back (E88)");
        assertEquals(MOUNT.bone(), hero(game).getMountBone());
        assertEquals(riding(hero(game)).look(), hero(game).getDrawnAs(), "drawn as his riding look");
        float riding = stride(game);
        assertEquals(walking * (100 + riding(hero(game)).boostPercent()) / 100f, riding, walking * 0.1f,
                "twice his walk");
        var book = hero(game).findModule(SkillBook.class);
        assertTrue(book.buffOf(uz.dukeengine.rpg.skill.SkillEffect.MOVE_SPEED, game.getLogic().getFrame()) > 0,
                "his quicker legs on the buff row");
        float apart = (float) Math.hypot(horse.getPosition().x() - hero(game).getPosition().x(),
                horse.getPosition().y() - hero(game).getPosition().y());
        assertTrue(apart < 1f, "the horse goes where he goes: " + apart);

        press(game); // the key again: off it
        game.runHeadless(2);
        assertFalse(session.run().mounted(game.getLocalPlayerIndex()));
        assertNull(hero(game).getDrawnAs(), "drawn as himself");
        assertNull(hero(game).getMount(), "off its back");
        assertNotNull(horse(game), "the horse stands where he got off");
        hero(game).getLocomotor().stop();
        game.runHeadless(2);
        assertEquals(walking, stride(game), walking * 0.1f, "and he walks again");
    }

    @Test
    void aBlowASkillAndATownEachGetHimOff() {
        var session = byATower(true);
        var game = session.game();
        int player = game.getLocalPlayerIndex();
        mount(session);
        hero(game).getBody().damage(5f);
        game.runHeadless(2);
        assertFalse(session.run().mounted(player), "a blow unseats him");
        assertNull(hero(game).getDrawnAs());

        mount(session);
        var book = hero(game).findModule(SkillBook.class);
        int mana = book.getMana();
        var skill = SHIPPED.bookFor(hero(game).getTemplate().name()).stream()
                .filter(each -> each.effect() == uz.dukeengine.rpg.skill.SkillEffect.MODULE).findFirst().orElseThrow();
        game.postCommand(PartyOrders.of(new CastSkill(player, skill.key(), null, hero(game).getPosition())));
        game.runHeadless(2);
        assertFalse(session.run().mounted(player), "the dodge pressed gets him off");
        assertEquals(mana, book.getMana(), 2, "and is not cast: no mana spent");

        mount(session);
        var city = zone("Poytaxt");
        put(hero(game), city.x(), city.y());
        game.runHeadless(2);
        assertFalse(session.run().mounted(player), "riding into the city he gets off");
    }

    @Test
    void indoorsAndWithoutAWhistleThereIsNoHorse() {
        var session = byATower(false);
        var game = session.game();
        press(game);
        game.runHeadless(2);
        assertNull(horse(game), "no whistle, no horse");

        var items = SHIPPED.items();
        session.progress().getLoot().take(items.potion(items.potion(MOUNT.whistle()), 1, 1), 0, 0);
        var city = zone("Poytaxt");
        put(hero(game), city.x(), city.y());
        game.runHeadless(2);
        press(game);
        game.runHeadless(2);
        assertNull(horse(game), "not in the city");

        var descent = Dungeon.newSession(21L, SHIPPED);
        descent.game().runHeadless(2);
        descent.progress().getLoot().take(items.potion(items.potion(MOUNT.whistle()), 1, 1), 0, 0);
        press(descent.game());
        descent.game().runHeadless(2);
        assertNull(horse(descent.game()), "nor down a dungeon");
    }

    @Test
    void theSaveKeepsHimOnHisHorse() {
        // A save puts him back where the last tower he found stands: a tower out of town, so he rides there.
        var session = byATower(true);
        var game = session.game();
        game.runHeadless(12); // the tower found
        mount(session);
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertTrue(save.mounted(), "saved riding");
        assertTrue(save.text().contains("Mounted = Yes"));

        var again = Dungeon.newStageSession(WORLD, SHIPPED);
        again.run().resume(save, Floors.resumed(save, SHIPPED));
        again.game().runHeadless(riding(hero(game)).windUpFrames() + 4);
        assertTrue(again.run().mounted(again.game().getLocalPlayerIndex()), "loaded riding");
        assertEquals(riding(hero(again.game())).look(), hero(again.game()).getDrawnAs());
        assertFalse(SaveGame.read(save.withMounted(false).text(), "test").mounted(), "and on foot, on foot");
    }

    @Test
    void theStableKeeperSellsTheWhistleInTheCity() {
        var stock = SHIPPED.story().merchant("Otboqar").stock();
        assertTrue(stock.stream().anyMatch(ware -> ware.item().equals(MOUNT.whistle())), "the whistle is sold");
        var keeper = WORLD.story().npcs().stream().filter(p -> p.kind().equals("Otboqar")).findFirst().orElseThrow();
        assertTrue(zone("Poytaxt").zone().holds((keeper.x() + 0.5f) * CELL, (keeper.y() + 0.5f) * CELL, CELL),
                "in the capital");
    }

    /** A ride across the world costs a frame what a walk does: ms a frame, on foot then riding, warm. */
    @Test
    void ridingCostsAFrameWhatWalkingDoes() {
        var session = byATower(true);
        var game = session.game();
        float onFoot = cost(game, 300);
        mount(session);
        float riding = cost(game, 300);
        System.out.printf("open world: %.3f ms a frame on foot, %.3f riding%n", onFoot, riding);
        assertTrue(riding < 4f, riding + " ms a frame riding is too dear");
    }

    /** Ms a frame over {@code frames}, walking to and fro, after as many to warm up. */
    private static float cost(DukeGame game, int frames) {
        var hero = hero(game);
        var start = hero.getPosition();
        float best = Float.MAX_VALUE;
        for (int round = 0; round < 2; round++) {
            hero.getLocomotor().moveTo(new Coord3D(start.x() + (round == 0 ? 200f : 0f), start.y(), start.z()));
            long began = System.nanoTime();
            game.runHeadless(frames);
            best = Math.min(best, (System.nanoTime() - began) / 1e6f / frames);
        }
        hero.getLocomotor().stop();
        game.runHeadless(2);
        return best;
    }
}
