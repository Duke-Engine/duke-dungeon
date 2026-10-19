package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * Pillars -- the descent's props, solid things with a shape and no body -- stand in the way as far as their outline and
 * no further, on every floor: a fence of them with one gap half again his width lets him through, and one narrower than
 * he is stops him. Walked a cell to a cell, as a floor of one theme was, a pillar of radius 3 closed every ten-unit cell
 * its outline came near, and two of them thirty apart left no way between (the owner, 2026-10-19).
 */
class PillarGapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int WIDE = 40;
    private static final int TALL = 24;
    private static final float FENCE = 200f;
    private static final String HERO = "Knight";

    @Test
    void aFloorOfOneThemeIsWalkedAsFinelyAsTheWorld() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 4, null); // the war's descent: a floor of one theme
        session.game().runHeadless(2);
        assertEquals(SETTINGS.world().navigationCellsPerCell(),
                session.game().getLogic().getPathGrid().cellsPerMapCell());
    }

    @Test
    void aGapHalfAgainHisWidthLetsHimThrough() {
        for (int at = 0; at < 10; at += 3) {
            assertTrue(crossed(1.5f, at / 10f), "he could not go between two pillars, set " + at + " tenths along");
        }
    }

    @Test
    void aGapNarrowerThanHimStopsHim() {
        assertFalse(crossed(0.8f, 0f), "he squeezed between two pillars");
    }

    /** Whether he crossed a fence of pillars whose one gap is {@code times} his width, edge to edge. */
    private static boolean crossed(float times, float along) {
        var arena = Dungeon.world(field(), SETTINGS);
        var rows = new ArrayList<String>();
        for (int y = 0; y < TALL; y++) {
            rows.add("g".repeat(WIDE));
        }
        DukeGame game = arena.game();
        game.applyMapTerrain(game.getTerrain(), new StagePaint("fence", rows, Map.of("g", "ground/grass.png"),
                Map.of(), List.of(), List.of()).walkedAt(SETTINGS.world().navigationCellsPerCell()));
        game.runHeadless(1);
        float pillar = Solid.of(game.getLogic().getThingFactory().findTemplate("Pillar")).footprintRadius();
        float width = 2 * Solid.of(game.getLogic().getThingFactory().findTemplate(HERO)).footprintRadius();
        float middle = TALL * 5f + along * 10f;
        float gap = times * width;
        // Down the fence, pillars touching but for the one gap.
        for (float y = middle + gap / 2f + pillar; y < TALL * 10f; y += 2 * pillar) {
            game.spawn("Pillar", arena.dungeon(), FENCE + along * 10f, y);
        }
        for (float y = middle - gap / 2f - pillar; y > 0f; y -= 2 * pillar) {
            game.spawn("Pillar", arena.dungeon(), FENCE + along * 10f, y);
        }
        game.spawn(HERO, arena.hero(), 80f, middle + 60f);
        game.runHeadless(2);
        var hero = find(game, HERO);
        hero.getLocomotor().moveTo(new Coord3D(320f, middle + 60f, 0f));
        game.runHeadless(600);
        return hero.getPosition().x() > FENCE + 20f;
    }

    private static String field() {
        var text = new StringBuilder();
        for (int y = 0; y < TALL; y++) {
            for (int x = 0; x < WIDE; x++) {
                text.append(x == 0 || y == 0 || x == WIDE - 1 || y == TALL - 1 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }
}
