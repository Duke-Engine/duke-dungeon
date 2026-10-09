package uz.dukeengine.dungeon.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * The queen's medallion and the turning, on the demo map {@code medalyon}, played headless: she gives it, only a hero
 * wears it, only its wearer's kills are souls, the souls step his blows up, the turning takes it and him, and a save
 * keeps it all. See docs/plan/2026-10-09-medallion.md.
 */
class MedallionTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Medallion MEDALLION = SETTINGS.medallion();
    private static final int CHECK = SETTINGS.story().dialogue().checkFrames();
    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static List<GameObject> all(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template)).toList();
    }

    private static GameObject hero(DukeGame game) {
        return all(game, SETTINGS.run().defaultHero()).getFirst();
    }

    private static StaticMap.Zone zone(String name) {
        return Stages.load("medalyon", SETTINGS).story().zones().stream().filter(z -> z.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static Dungeon.Session session() {
        var session = Dungeon.newStageSession(Stages.load("medalyon", SETTINGS), SETTINGS);
        session.game().runHeadless(1);
        return session;
    }

    /** He walks up to the queen: she greets him and the story puts her medallion in his bag. */
    private static void given(Dungeon.Session session) {
        var game = session.game();
        var queen = all(game, "Queen").getFirst();
        var hero = hero(game);
        var cameIn = hero.getPosition();
        hero.setPosition(new Coord3D(queen.getPosition().x() + 12f, queen.getPosition().y(), hero.getPosition().z()));
        game.runHeadless(CHECK + 3);
        hero(game).setPosition(cameIn);
    }

    private static uz.dukeengine.rpg.item.LootBag bag(Dungeon.Session session) {
        return session.run().progressOf(hero(session.game()).getPlayerIndex()).getLoot();
    }

    private static int souls(Dungeon.Session session) {
        return session.run().soulsOf(playerOf(session));
    }

    private static int playerOf(Dungeon.Session session) {
        return hero(session.game()).getPlayerIndex();
    }

    /** {@code by} deals a skeleton a killing blow that names it. */
    private static void kill(Dungeon.Session session, GameObject by) {
        var skeleton = all(session.game(), "Skeleton").getFirst();
        skeleton.getBody().damage(1e6f, uz.dukeengine.core.module.DamageType.NORMAL,
                new Death(DeathType.NORMAL, by.getId(), by.getPlayerIndex()));
        session.game().runHeadless(2);
    }

    private static int wornPlace(Dungeon.Session session) {
        return bag(session).wornPlace("Tumor");
    }

    @Test
    void theQueenGivesItAndHeWearsItAtHisNeck() {
        var session = session();
        given(session);
        var worn = bag(session).at(wornPlace(session));
        assertNotNull(worn, "worn in the Tumor slot");
        assertEquals(MEDALLION.item(), worn.id());
    }

    @Test
    void onlyItsWearersKillsAreSouls() {
        var session = session();
        var game = session.game();
        kill(session, hero(game));
        assertEquals(0, souls(session), "before he has it, nothing is drunk");

        given(session);
        kill(session, hero(game));
        assertEquals(1, souls(session), "his kill, worn: a soul");

        all(game, "Skeleton").getFirst().getBody().setHealth(0f);
        game.runHeadless(2);
        assertEquals(1, souls(session), "a death nobody dealt is nobody's soul");

        // Something of his side that is not a hero -- a monster -- never drinks, whatever his bag holds.
        var thrall = game.getLogic().spawn(game.getLogic().getThingFactory().findTemplate("Skeleton"),
                hero(game).getPosition(), playerOf(session));
        game.runHeadless(1);
        kill(session, thrall);
        assertEquals(1, souls(session), "a monster's kill is no soul of the medallion's");

        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        bag(session).shift(wornPlace(session)); // taken off, into the backpack
        kill(session, hero(game));
        assertEquals(1, souls(session), "carried, not worn: it drinks nothing");
    }

    /**
     * Fought for real: the Rogue's arrow names no killer (the rpg module's blows say none yet, E27), and the kill is
     * still his soul — his experience rose for it.
     */
    @Test
    void anArrowsKillIsASoulToo() {
        var session = session();
        var game = session.game();
        given(session);
        var hero = hero(game);
        var skeleton = all(game, "Skeleton").getFirst();
        skeleton.setPosition(new Coord3D(hero.getPosition().x() + 30f, hero.getPosition().y(), hero.getPosition().z()));
        skeleton.getBody().setHealth(1f);
        for (int frame = 0; frame < 300 && !skeleton.isEffectivelyDead(); frame++) {
            game.runHeadless(1);
        }
        game.runHeadless(2);
        assertTrue(skeleton.isEffectivelyDead(), "the premise: he shot it");
        assertEquals(1, souls(session));
    }

    @Test
    void aSoulFliesFromTheCorpseIntoHim() {
        var session = session();
        var game = session.game();
        given(session);
        kill(session, hero(game));
        assertEquals(1, all(game, MEDALLION.wisp()).size(), "a wisp rose");
        game.runHeadless(300);
        assertTrue(all(game, MEDALLION.wisp()).isEmpty(), "and flew into him");
    }

    @Test
    void theBonusStepsWithTheSoulsAndStopsAtItsCap() {
        assertEquals(0, MEDALLION.percentFor(9));
        assertEquals(MEDALLION.percentPerStep(), MEDALLION.percentFor(MEDALLION.soulsPerStep()));
        assertEquals(3 * MEDALLION.percentPerStep(), MEDALLION.percentFor(3 * MEDALLION.soulsPerStep() + 5));
        assertEquals(MEDALLION.maxPercent(), MEDALLION.percentFor(1_000_000));
    }

    @Test
    void hisBlowsLandHarderThroughHisSkillBookWhileHeWearsIt() {
        var session = session();
        var game = session.game();
        given(session);
        var save = session.run().saveNow().withMedallion(3 * MEDALLION.soulsPerStep(), "");
        var loaded = Dungeon.newStageSession(Stages.load("medalyon", SETTINGS), SETTINGS);
        loaded.run().resume(save, Floors.ofStage(Stages.load("medalyon", SETTINGS)));
        loaded.game().runHeadless(3);
        var book = hero(loaded.game()).findModule(SkillBook.class);
        assertEquals(3 * MEDALLION.percentPerStep(), SkillBook.auraOn(hero(loaded.game()), SkillEffect.DAMAGE_AURA));
        assertEquals(1f + 3 * MEDALLION.percentPerStep() / 100f, book.damageMultiplier(), 1e-5f);

        bag(loaded).shift(wornPlace(loaded));
        loaded.game().runHeadless(3);
        assertEquals(0, SkillBook.auraOn(hero(loaded.game()), SkillEffect.DAMAGE_AURA), "taken off, it adds nothing");
    }

    @Test
    void theTurningTakesItGivesItDarkensHimAndTurnsHim() {
        var session = session();
        var game = session.game();
        given(session);
        var bag = bag(session);
        var hero = hero(game);
        int player = hero.getPlayerIndex();
        var altar = zone("Altar");
        hero.setPosition(new Coord3D(altar.middleX(CELL), altar.middleY(CELL), hero.getPosition().z()));
        game.runHeadless(CHECK + 3);

        assertTrue(bag.slots().stream().noneMatch(item -> item != null
                && item.id().equals(MEDALLION.item())), "gone from his neck");
        assertEquals("Queen", session.run().medallionGivenTo(), "and in her hand");
        assertTrue(hero.hasCondition(MEDALLION.cursedWord()), "his look darkened");
        assertTrue(session.run().isCursed(player));
        assertFalse(hero.getPlayerIndex() == player, "no longer his player's");
        assertEquals(all(game, "Skeleton").getFirst().getPlayerIndex(), hero.getPlayerIndex(), "the monsters' side");
        assertNull(uz.dukeengine.rpg.skill.Skills.heroOf(game.getLogic(), player), "his player commands no hero");
        assertEquals(DungeonRun.State.WON, session.run().getState(), "the act is over");
        assertNull(session.run().saveNow(), "nothing of the turned run is saved as a checkpoint");
        game.runHeadless(600);
        assertEquals(DungeonRun.State.WON, session.run().getState(), "and stays over: a story map is won for good");
    }

    @Test
    void theSoulsAndWhoHoldsItAreSavedAndRestored() {
        var session = session();
        var game = session.game();
        given(session);
        kill(session, hero(game));
        kill(session, hero(game));
        var save = SaveGame.read(session.run().saveNow().text(), "test");
        assertEquals(2, save.souls());
        assertEquals("", save.givenTo());
        assertTrue(save.bag().stream().anyMatch(item -> item.id().equals(MEDALLION.item())), "held");

        var loaded = Dungeon.newStageSession(Stages.load("medalyon", SETTINGS), SETTINGS);
        loaded.run().resume(save, Floors.ofStage(Stages.load("medalyon", SETTINGS)));
        loaded.game().runHeadless(2);
        assertEquals(2, souls(loaded));
        assertEquals(MEDALLION.item(), bag(loaded).at(wornPlace(loaded)).id(), "worn again where it was");

        var given = SaveGame.read(save.withMedallion(5, "Queen").text(), "test");
        assertEquals("Queen", given.givenTo());
        var cursed = Dungeon.newStageSession(Stages.load("medalyon", SETTINGS), SETTINGS);
        cursed.run().resume(given, Floors.ofStage(Stages.load("medalyon", SETTINGS)));
        cursed.game().runHeadless(2);
        assertEquals("Queen", cursed.run().medallionGivenTo());
        assertTrue(hero(cursed.game()).hasCondition(MEDALLION.cursedWord()), "a cursed hero loads dark");

        var old = new SaveGame(save.seed(), save.stage(), save.depth(), save.hero(), save.experience(), save.ranks(),
                save.bag(), save.quests(), save.script(), save.gold());
        var none = SaveGame.read(old.text().replace("  Souls = 0\n", ""), "test");
        assertEquals(0, none.souls(), "a save from before the medallion has drunk nothing");
    }

    /** The whole demo, as a player plays it: she gives it, the hall's skeletons feed it, the altar turns him. */
    @Test
    void theDemoEndToEnd() {
        var session = session();
        var game = session.game();
        given(session);
        assertTrue(session.run().getQuests().said().contains("QueenGives"));
        int skeletons = 4;
        for (int at = 0; at < skeletons; at++) {
            kill(session, hero(game));
        }
        assertEquals(skeletons, souls(session));
        var hero = hero(game);
        var altar = zone("Altar");
        hero.setPosition(new Coord3D(altar.middleX(CELL), altar.middleY(CELL), hero.getPosition().z()));
        game.runHeadless(CHECK + 3);
        assertEquals("Queen", session.run().medallionGivenTo());
        game.runHeadless(SETTINGS.story().dialogue().readWaitFrames() + 300);
        assertTrue(session.run().getQuests().said().contains("QueenTakes"), "she says what it was for");
        assertTrue(hero.hasCondition(MEDALLION.cursedWord()));
    }
}
