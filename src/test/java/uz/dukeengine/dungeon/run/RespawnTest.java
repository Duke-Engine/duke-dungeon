package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.stage.StageFile;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.hero.LevelBonus;

/**
 * What a Respawn brings back: a monster a static map placed, and a Person placed by name, each where it stood once its
 * seconds are out and no hero is within twenty cells of the spot. Played on the people map -- a hall thirty cells
 * across, the hero coming in at (26, 3) -- with a skeleton added at (26, 26), twenty-three cells from him, and the
 * sentry made a skeleton too: the commander it is drawn as cannot be killed.
 */
class RespawnTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final float CELL = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
    private static final int FPS = uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;

    /** The people map with a skeleton at (26, 26), whose kind comes back after five seconds, and the sentry after four. */
    private static Dungeon.Session session() {
        var data = Content.data()
                .replaceFirst("(?s)(Name = Skeleton\\R.*?)(\\R  Weight = 40)", "$1\n  Respawn = 5$2");
        assertTrue(data.contains("Respawn = 5"), "the premise: the skeleton's block has it now");
        var duke = Stages.found("people").extrasText();
        assertTrue(duke.contains("  Leash = 6"));
        var settings = DungeonSettings.parse(data + "\n" + duke.replace("  Leash = 6", "  Leash = 6\n  Respawn = 4")
                .replace("Kind = Act1CommanderFighter", "Kind = Skeleton"));
        var text = Stages.found("people").text();
        assertTrue(text.contains("Monsters = []"));
        var session = Dungeon.newStageSession(StageFile.read(text.replace("Monsters = []",
                "Monsters = [\n    Skeleton 26 26,\n  ]"), "people"), settings);
        session.game().runHeadless(1);
        session.game().getLogic().setSleep(null);
        return session;
    }

    /** The living thing of {@code template} standing nearest the middle of cell {@code (x, y)}, or null. */
    private static GameObject standing(DukeGame game, String template, int x, int y) {
        GameObject nearest = null;
        double best = CELL * 2;
        for (var thing : game.getLogic().getObjects()) {
            double far = Math.hypot(thing.getPosition().x() - (x + 0.5f) * CELL,
                    thing.getPosition().y() - (y + 0.5f) * CELL);
            if (thing.getTemplate().name().equals(template) && !thing.isEffectivelyDead() && far < best) {
                nearest = thing;
                best = far;
            }
        }
        return nearest;
    }

    private static GameObject hero(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals(
                        SETTINGS.run().defaultHero()))
                .findFirst().orElseThrow();
    }

    /** Kills {@code thing} and runs on until it is gone from the world; the frame it was reaped at. */
    private static int killed(DukeGame game, GameObject thing) {
        thing.getBody().damage(thing.getBody().getHealth() + 1f);
        for (int i = 0; i < 300 && game.getLogic().findObject(thing.getId()) != null; i++) {
            game.runHeadless(1);
        }
        assertNull(game.getLogic().findObject(thing.getId()), "reaped");
        return game.getLogic().getFrame();
    }

    @Test
    void aMonsterComesBackAfterItsTimeAndNotBefore() {
        var session = session();
        var game = session.game();
        var was = standing(game, "Skeleton", 26, 26);
        assertNotNull(was, "placed by the map");
        int level = LevelBonus.levelOf(was);
        int reaped = killed(game, was);
        game.runHeadless(5 * FPS - 8 - (game.getLogic().getFrame() - reaped));
        assertNull(standing(game, "Skeleton", 26, 26), "not yet: five seconds have not gone");
        game.runHeadless(16);
        var back = standing(game, "Skeleton", 26, 26);
        assertNotNull(back, "back where it was placed");
        assertNotEquals(was.getId(), back.getId());
        assertEquals(level, LevelBonus.levelOf(back), "at the level it stood at");
        assertNotNull(back.findModule(Bounty.class), "paying whoever kills it again");
        assertEquals(was.getPlayerIndex(), back.getPlayerIndex(), "on the dungeon's side");
        // And it comes back again, as often as it dies.
        killed(game, back);
        game.runHeadless(5 * FPS + 8);
        assertNotNull(standing(game, "Skeleton", 26, 26), "a second time");
    }

    @Test
    void aMonsterWaitsWhileAHeroIsNear() {
        var session = session();
        var game = session.game();
        killed(game, standing(game, "Skeleton", 26, 26));
        var hero = hero(game);
        var home = hero.getPosition();
        hero.setPosition(new Coord3D(26.5f * CELL, 26.5f * CELL - 19f * CELL, home.z()));
        game.runHeadless(5 * FPS * 2);
        assertNull(standing(game, "Skeleton", 26, 26), "a hero stands nineteen cells off: it waits, long past its time");
        hero.setPosition(new Coord3D(26.5f * CELL, 26.5f * CELL - 21f * CELL, home.z()));
        game.runHeadless(3);
        assertNotNull(standing(game, "Skeleton", 26, 26), "he is twenty-one cells off: it comes");
    }

    @Test
    void aGuardComesBackOnTheHerosSideAndWalksItsRound() {
        var session = session();
        var game = session.game();
        var sentry = standing(game, "Skeleton", 12, 20);
        assertNotNull(sentry);
        int reaped = killed(game, sentry);
        game.runHeadless(4 * FPS - 8 - (game.getLogic().getFrame() - reaped));
        assertNull(standing(game, "Skeleton", 12, 20), "not before its four seconds");
        game.runHeadless(16);
        var back = standing(game, "Skeleton", 12, 20);
        assertNotNull(back, "home again");
        assertEquals(hero(game).getPlayerIndex(), back.getPlayerIndex(), "on the hero's side");
        assertNotNull(back.findModule(uz.dukeengine.dungeon.ai.Leash.class), "on its leash");
        var marks = session.run().getMarks();
        assertTrue(marks.stream().anyMatch(mark -> mark.id() == back.getId().value()), "marked as a person again");
        assertTrue(marks.stream().noneMatch(mark -> mark.id() == sentry.getId().value()), "and not as the dead one");
        // Its round: from home to the nearer post, PostB at (13, 5).
        boolean walked = false;
        for (int i = 0; i < 900 && !walked; i++) {
            walked = Math.hypot(back.getPosition().x() - 13.5f * CELL, back.getPosition().y() - 5.5f * CELL) <= 1.5f * CELL;
            game.runHeadless(1);
        }
        assertTrue(walked, "it walks its round: " + back.getPosition());
    }

    @Test
    void aNegativeRespawnIsRefused() {
        var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data()
                + "\nPerson\n  Name = Ghost\n  Kind = Villager\n  Respawn = -1\nEnd\n"));
        assertTrue(refused.getMessage().contains("Respawn"), refused.getMessage());
        var monster = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(Content.data()
                .replaceFirst("(?s)(Name = Skeleton\\R.*?)(\\R  Weight = 40)", "$1\n  Respawn = -1$2")));
        assertTrue(monster.getMessage().contains("Respawn"), monster.getMessage());
    }
}
