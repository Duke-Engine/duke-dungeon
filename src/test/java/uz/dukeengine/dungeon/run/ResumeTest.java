package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/** The checkpoint: written as a floor is entered, and a load lays that floor again with the hero put back. */
class ResumeTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();
    private static final String HERO = SHIPPED.run().defaultHero();

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static void defeatTheBoss(Dungeon.Session session) {
        var boss = find(session.game(), SHIPPED.bossKindAt(session.run().getDepth()));
        assertNotNull(boss);
        session.game().getLogic().destroyObject(boss);
        session.game().runHeadless(SHIPPED.run().descendDelayFrames() + 4);
    }

    @Test
    void aSaveIsWrittenAsEachFloorIsEntered() {
        var session = Dungeon.newSession(11L, SHIPPED);
        var saved = new ArrayList<SaveGame>();
        session.run().onCheckpoint(saved::add);
        session.game().runHeadless(1);
        assertEquals(1, saved.size(), "the first floor is a floor entered");
        assertEquals(1, saved.getFirst().depth());
        assertEquals(HERO, saved.getFirst().hero());

        defeatTheBoss(session);
        assertEquals(2, saved.size());
        assertEquals(2, saved.getLast().depth());
        assertEquals(session.progress().getExperience(), saved.getLast().experience());
    }

    @Test
    void aLoadLaysTheSameFloorAgain() {
        var first = Dungeon.newSession(11L, SHIPPED);
        var saved = new ArrayList<SaveGame>();
        first.run().onCheckpoint(saved::add);
        first.game().runHeadless(1);
        defeatTheBoss(first);
        var save = SaveGame.read(saved.getLast().text(), "test");
        var heroThen = find(first.game(), HERO).getPosition();
        var bossThen = find(first.game(), SHIPPED.bossKindAt(2)).getPosition();

        var again = Dungeon.newSession(99L, SHIPPED);
        var written = new ArrayList<SaveGame>();
        again.run().onCheckpoint(written::add);
        again.run().resume(save, Floors.resumed(save, SHIPPED));
        again.game().runHeadless(2);

        assertEquals(2, again.run().getDepth());
        assertEquals(heroThen, find(again.game(), HERO).getPosition(), "he comes in where he came in");
        assertEquals(bossThen, find(again.game(), SHIPPED.bossKindAt(2)).getPosition(), "the same floor");
        assertEquals(List.of(), written, "a load does not write back what it just read");
    }

    @Test
    void aLoadPutsBackHisLevelSkillsAndBag() {
        var skills = SHIPPED.skillsFor(HERO).stream().filter(skill -> !skill.isUltimate()).toList();
        var save = new SaveGame(11L, null, 1, HERO, SHIPPED.levelling().totalXpFor(4),
                List.of(new SaveGame.Rank(skills.get(0).key(), 2), new SaveGame.Rank(skills.get(1).key(), 1)),
                List.of(new SaveGame.Item(2, "Boots", 1, 3, 0)));

        var session = Dungeon.newSession(99L, SHIPPED);
        session.run().resume(save, Floors.resumed(save, SHIPPED));
        session.game().runHeadless(3);

        assertEquals(4, session.progress().getLevel());
        assertEquals(save.experience(), session.progress().getExperience());
        assertEquals(2, session.run().getLearnt().rankOf(skills.get(0).key()));
        assertEquals(1, session.run().getLearnt().rankOf(skills.get(1).key()));
        assertEquals("Boots", session.progress().getLoot().at(2).id());
        var hero = find(session.game(), HERO);
        assertTrue(hero.getBody().getHealth() >= hero.getBody().getMaxHealth(), "he arrives whole");

        // And the next floor carries it on, as any descent does.
        var saved = new ArrayList<SaveGame>();
        session.run().onCheckpoint(saved::add);
        defeatTheBoss(session);
        assertTrue(saved.getLast().experience() >= save.experience());
        assertEquals("Boots", saved.getLast().bag().getFirst().id());
    }
}
