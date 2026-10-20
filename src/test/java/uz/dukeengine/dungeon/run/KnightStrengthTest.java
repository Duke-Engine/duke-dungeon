package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The Black Knight stands as strong as what is left of the world's graves and caves (KnightStrength): whole with none
 * gone, half at the least with all of them gone, said in the tracker while the road to him is going, and laid in his
 * castle with his health cut to it.
 */
class KnightStrengthTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static void goneHalf(Dungeon.Session session) {
        var script = session.run().getScript();
        var yards = SETTINGS.graveyards();
        var caves = SETTINGS.caves();
        yards.subList(0, yards.size() / 2).forEach(yard -> script.flag(yard.goneFlag()));
        caves.subList(0, caves.size() / 2).forEach(cave -> script.flag(cave.goneFlag()));
    }

    @Test
    void asStrongAsWhatIsLeft() {
        var rule = SETTINGS.knightStrength();
        assertEquals(100, rule.percent(10, 10));
        assertEquals(rule.least(), rule.percent(0, 10), "half at the least");
        assertEquals(75, rule.percent(5, 10));

        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 4, null);
        session.game().runHeadless(2);
        int all = SETTINGS.graveyards().size() + SETTINGS.caves().size();
        assertEquals(rule.percent(all - 1, all), session.run().knightStrength(), "the old graves gone already");
        goneHalf(session); // the first half of each, the old graves among them
        int left = all - SETTINGS.graveyards().size() / 2 - SETTINGS.caves().size() / 2;
        assertEquals(rule.percent(left, all), session.run().knightStrength());
    }

    @Test
    void theTrackerSaysItWhileTheRoadToHimIsGoing() {
        var session = Dungeon.newSession(7L, SETTINGS);
        session.run().skipTo(SETTINGS.campaign(), 4, null);
        session.game().runHeadless(SETTINGS.story().dialogue().checkFrames() + 2);
        var titles = session.run().getTracker().entries().stream().map(e -> e.title()).toList();
        assertTrue(titles.contains(String.format(SETTINGS.knightStrength().word(), session.run().knightStrength())),
                titles.toString());
    }

    @Test
    void inHisCastleHisHealthIsCutToHowStrongHeStands() {
        var whole = Dungeon.newSession(7L, SETTINGS);
        whole.run().skipTo(SETTINGS.campaign(), 5, null);
        whole.game().runHeadless(3);
        // His whole self: what he stands at here (the old graves gone at this joint) turned back to all of him.
        float full = lord(whole).getBody().getMaxHealth() / (whole.run().knightStrength() / 100f);

        // Every grave and cave gone, and in by the camp: the castle laid with him at half of himself.
        var there = Dungeon.newSession(7L, SETTINGS);
        there.run().skipTo(SETTINGS.campaign(), 4, null);
        there.game().runHeadless(2);
        SETTINGS.graveyards().forEach(yard -> there.run().getScript().flag(yard.goneFlag()));
        SETTINGS.caves().forEach(cave -> there.run().getScript().flag(cave.goneFlag()));
        var camp = uz.dukeengine.dungeon.stage.Stages.load("world", SETTINGS).story().zones().stream()
                .filter(z -> z.name().equals("Qora_qarorgoh")).findFirst().orElseThrow();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var hero = there.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(there.run().getHeroTemplate())).findFirst().orElseThrow();
        hero.setPosition(new uz.dukeengine.core.math.Coord3D(camp.middleX(cell), camp.middleY(cell), hero.getPosition().z()));
        for (int i = 0; i < 300 && !"qora_qasr".equals(there.run().getStageName()); i++) {
            there.run().read(there.game().getLocalPlayerIndex(), 10_000);
            there.game().runHeadless(1);
        }
        there.game().runHeadless(3);
        assertEquals("qora_qasr", there.run().getStageName(), "in by his camp");
        assertEquals(full * SETTINGS.knightStrength().least() / 100f, lord(there).getBody().getMaxHealth(), 1f,
                "every grave and cave gone: half of himself");
    }

    private static uz.dukeengine.core.thing.GameObject lord(Dungeon.Session session) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.knightStrength().boss())).findFirst()
                .orElseThrow();
    }
}
