package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // ---- a save taken mid-floor: Saqlash, and leaving the game ----

    @Test
    void aSaveMidFloorHoldsTheHeroAsHeIsNowOnTheFloorHeEntered() {
        var session = Dungeon.newSession(11L, SHIPPED);
        var entered = new ArrayList<SaveGame>();
        session.run().onCheckpoint(entered::add);
        session.game().runHeadless(1);
        var earned = SHIPPED.levelling().totalXpFor(3);
        find(session.game(), HERO).findModule(uz.dukeengine.combat.module.ExperienceModule.class)
                .setExperience(earned);
        var boots = SHIPPED.loot().stream().filter(loot -> loot.id().equals("Boots")).findFirst().orElseThrow();
        session.progress().getLoot().take(boots, 0, 0);
        session.game().runHeadless(1);

        var now = session.run().saveNow();

        assertEquals(earned, now.experience(), "what he earned on the floor is kept");
        assertEquals("Boots", now.bag().getFirst().id());
        assertEquals(entered.getFirst().seed(), now.seed(), "on the floor he entered");
        assertEquals(entered.getFirst().depth(), now.depth());

        var again = Dungeon.newSession(99L, SHIPPED);
        again.run().resume(now, Floors.resumed(now, SHIPPED));
        again.game().runHeadless(3);
        assertEquals(3, again.progress().getLevel());
        assertEquals("Boots", again.progress().getLoot().at(again.progress().getLoot().wornPlace("Etik")).id());
    }

    @Test
    void nothingIsSavedOnceTheRunIsLost() {
        var session = Dungeon.newSession(11L, SHIPPED);
        session.game().runHeadless(1);
        session.game().getLogic().destroyObject(find(session.game(), HERO));
        session.game().runHeadless(2);
        assertEquals(DungeonRun.State.DEAD, session.run().getState());
        assertNull(session.run().saveNow(), "the floor's entry save stands");
    }

    @Test
    void aPartyIsNotSaved() {
        var party = Dungeon.newPartySession(uz.dukeengine.dungeon.party.PartyMatch.endless(4242L), 2, null, SHIPPED,
                HERO);
        party.game().runHeadless(1);
        assertNull(party.run().saveNow());
    }
}
