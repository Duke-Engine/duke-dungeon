package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.event.EffectPlayed;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Monster;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.run.Spawner;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.StatusUpdate;

/**
 * The summoner's haste: the sturdiest of its own near it, or itself, fires three quarters again as fast for five
 * seconds -- every wait of its weapon divided by 1.75 and cut to whole frames, the engine's own rule.
 *
 * <p>Whom it hastens is the book's to pick, as a strike cast at nothing picks its victim, so it is asked of a haste
 * cast by hand in a room with nobody to fight; when the brain casts it is asked of a fight.
 */
class HasteTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String SUMMONER = "SkeletonSummoner";
    private static final int NO_WALL = -1;

    /** An open room forty cells by thirty; with a wall down one column, and a doorway at its far end. */
    private static String room(int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                boolean wall = x == wallColumn && y != 27;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** The summoner's haste as shipped: its W. */
    private static Skill haste() {
        return SETTINGS.skillsFor(SUMMONER).stream().filter(skill -> skill.key() == 'W').findFirst().orElseThrow();
    }

    /** One creature to place: its kind and where. */
    private record One(String kind, float x, float y) {
    }

    private static One one(String kind, float x, float y) {
        return new One(kind, x, y);
    }

    /** A room, and the dungeon's own standing in it, each in the order it was made. */
    private record Room(DukeGame game, List<GameObject> ones) {

        GameObject get(int index) {
            return ones.get(index);
        }

        /** The first of them casts its haste by hand, at nothing: whether it went. */
        boolean castsItsHaste() {
            return bookOf(ones.getFirst()).cast('W', 1, null, null);
        }

        /** The one of them a haste is burning on: exactly one, or the test says who else. */
        GameObject hastened() {
            var burning = ones.stream().filter(one -> bookOf(one).getHasteFrames() > 0).toList();
            assertEquals(1, burning.size(), "hastened: " + burning);
            return burning.getFirst();
        }
    }

    /** A room holding these of the dungeon's own, in this order, and nobody to fight. */
    private static Room room(DungeonSettings settings, int wallColumn, One... ones) {
        var arena = Dungeon.world(room(wallColumn), settings);
        var game = arena.game();
        for (var one : ones) {
            game.spawn(one.kind(), arena.dungeon(), one.x(), one.y());
        }
        game.runHeadless(1);
        return new Room(game, game.getLogic().getObjects().stream().filter(object -> object.getBody() != null)
                .toList());
    }

    private static SkillBook bookOf(GameObject creature) {
        return creature.findModule(SkillBook.class);
    }

    // ---- whom ----

    /**
     * The sturdiest of its own near it: a Brute over two Skeletons of its level, by its health at its fullest -- and it
     * turns to the one it hastens and is seen casting.
     */
    @Test
    void itHastensTheSturdiestOfItsOwnNearIt() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 130f, 150f),
                one("Brute", 100f, 180f), one("Skeleton", 100f, 120f));
        var summoner = room.get(0);

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");
        var fired = room.game().getLogic().drainEvents();

        assertEquals(room.get(2), room.hastened(), "the Brute");
        assertEquals(haste().durationFrames(), bookOf(room.get(2)).getHasteFrames(), "for its DurationFrames");
        assertEquals((float) (Math.PI / 2), summoner.getOrientation(), 0.001f, "turned to the Brute, up the column");
        assertTrue(fired.stream().anyMatch(event -> event instanceof WeaponFired shot
                        && shot.shooter().equals(summoner.getId())),
                "and seen casting: " + fired);
    }

    /** A level outweighs a body: a Skeleton a level up is hastened over the Brute. */
    @Test
    void aLevelUpOutweighsTheBiggerBody() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f, 180f),
                one("Skeleton", 130f, 150f));
        Spawner.scale(room.get(2), 2, SETTINGS);

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");

        assertEquals(room.get(2), room.hastened(), "the Skeleton at level 2 over the Brute at 1");
    }

    /** Of two alike, the nearer; of two alike and as near, the one the world made first -- whichever side it is on. */
    @Test
    void ofTwoAlikeTheNearerAndOfTwoAsNearTheFirst() {
        var nearer = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Skeleton", 100f, 190f),
                one("Skeleton", 120f, 150f));
        assertTrue(nearer.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(nearer.get(2), nearer.hastened(), "the one 20 away over the one 40 away");

        for (boolean leftFirst : new boolean[] {true, false}) {
            var asNear = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f),
                    one("Skeleton", leftFirst ? 70f : 130f, 150f), one("Skeleton", leftFirst ? 130f : 70f, 150f));
            assertTrue(asNear.castsItsHaste(), "the premise: it cast its haste");
            assertEquals(asNear.get(1), asNear.hastened(), "the one made first, 30 away as the other is");
        }
    }

    /**
     * Only in its reach and its plain sight: a Brute at its Range, middle to middle, is hastened; one a step past it,
     * or behind stone, is not -- and with nobody else, it hastens itself.
     */
    @Test
    void nobodyBehindStoneOrPastItsRangeItselfInstead() {
        float range = haste().range();
        var atItsRange = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f - range, 150f));
        assertTrue(atItsRange.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(atItsRange.get(1), atItsRange.hastened(), "the Brute at " + range);

        var outOfIt = room(SETTINGS, 12, one(SUMMONER, 100f, 150f), one("Brute", 99f - range, 150f),
                one("Brute", 150f, 150f));
        assertTrue(outOfIt.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(outOfIt.get(0), outOfIt.hastened(), "one Brute a step past its Range, one behind stone");
    }

    /** Alone, it hastens itself -- and so it does with a Range of 0, a Brute beside it. */
    @Test
    void aloneOrWithNoRangeItHastensItself() {
        var alone = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        assertTrue(alone.castsItsHaste(), "never refused for want of someone to hasten");
        assertEquals(alone.get(0), alone.hastened());

        var noReach = DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("Range", 0).text());
        var beside = room(noReach, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 110f, 150f));
        assertTrue(beside.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(beside.get(0), beside.hastened(), "a Range of 0 hastens only its caster");
    }

    // ---- what it does ----

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, a monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with the summoner noticing nobody: it does only what a test does for it. */
    private static DungeonSettings unseeingSummoner() {
        return DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("SenseRadius", 1).with("ChaseRadius", 1)
                .with("AlertRadius", 0).text());
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElseThrow();
    }

    /** Runs a frame at a time until {@code hero} loses health: whether he did, within {@code frames}. */
    private static boolean untilStruck(DukeGame game, GameObject hero, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float health = hero.getBody().getHealth();
            game.runHeadless(1);
            if (hero.getBody().getHealth() < health) {
                return true;
            }
        }
        return false;
    }

    /**
     * A hastened Skeleton waits 17 frames between blows for its 30 -- 30 / 1.75, cut to whole frames -- through the
     * haste's 150 frames, and 30 again after: a Rogue who cannot answer at its reach, and a summoner noticing nobody
     * that hastens it by hand once it is striking.
     */
    @Test
    void aHastenedSkeletonWaitsSeventeenFramesForItsThirtyThroughTheHaste() {
        var arena = Dungeon.world(room(NO_WALL), unseeingSummoner(), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 162f, 110f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = first(game, "Rogue");
        var skeleton = first(game, "Skeleton");
        int lasts = haste().durationFrames();

        assertTrue(untilStruck(game, hero, 150), "the premise: the Skeleton struck him");
        assertTrue(bookOf(first(game, SUMMONER)).cast('W', 1, null, null), "the premise: it cast its haste");
        assertEquals(lasts, bookOf(skeleton).getHasteFrames(), "the premise: on the Skeleton");
        int hastedAt = game.getLogic().getFrame();
        var blows = new ArrayList<Integer>();
        while (game.getLogic().getFrame() - hastedAt < lasts + 90) {
            if (untilStruck(game, hero, 1)) {
                blows.add(game.getLogic().getFrame() - hastedAt);
            }
        }

        int quick = 0;
        int plain = 0;
        for (int i = 0; i + 1 < blows.size(); i++) {
            boolean hasted = blows.get(i) <= lasts;
            assertEquals(hasted ? 17 : 30, blows.get(i + 1) - blows.get(i),
                    "the wait after its blow " + blows.get(i) + " frames in: " + blows);
            quick += hasted ? 1 : 0;
            plain += hasted ? 0 : 1;
        }
        assertTrue(quick >= 7 && plain >= 2, "the premise: blows through the haste and after it: " + blows);
    }

    /**
     * A second haste while one burns starts it again at the newer figures -- never stacked: two summoners hasten one
     * Brute two seconds apart, and it has the whole 150 frames again and fires 1.75 times as fast, not faster.
     */
    @Test
    void aSecondHasteStartsItAgainAndNeverStacks() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one(SUMMONER, 160f, 150f),
                one("Brute", 130f, 150f));
        var brute = bookOf(room.get(2));
        int lasts = haste().durationFrames();

        assertTrue(room.castsItsHaste(), "the premise: the first cast its haste");
        assertEquals(lasts, brute.getHasteFrames());
        room.game().runHeadless(60);
        assertEquals(lasts - 60, brute.getHasteFrames(), "the premise: two seconds of it gone");

        assertTrue(bookOf(room.get(1)).cast('W', 1, null, null), "the premise: the second cast its haste");
        assertEquals(lasts, brute.getHasteFrames(), "started again, not added to");
        assertEquals(1f + haste().boostPercent() / 100f, brute.rateOfFireMultiplier(), 0.0001f,
                "and as fast as one haste makes it");
    }

    /** It spends its ManaCost from the summoner's pool, and is refused, spending nothing, stunned or unpaid for. */
    @Test
    void itSpendsItsCostAndIsRefusedStunnedOrUnpaidFor() {
        var paid = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        var book = bookOf(paid.get(0));
        Spawner.scale(paid.get(0), 1, SETTINGS);
        int full = book.getMana();

        assertTrue(paid.castsItsHaste(), "the premise: it cast its haste");
        assertEquals(25, haste().manaCost(), "the premise: it costs 25");
        assertEquals(full - 25, book.getMana(), "what it spent");

        var stunned = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        stunned.get(0).findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 30);
        assertFalse(stunned.castsItsHaste(), "cast stunned");
        assertTrue(bookOf(stunned.get(0)).isReady('W'), "and a cast that never went off spent its cooldown");
        assertEquals(0, bookOf(stunned.get(0)).getHasteFrames(), "and hastened it");

        var poor = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        Spawner.scale(poor.get(0), 1, SETTINGS);
        bookOf(poor.get(0)).resize(24, 0);
        assertFalse(poor.castsItsHaste(), "cast with 24 for a cost of 25");
        assertEquals(24, bookOf(poor.get(0)).getMana(), "and something was taken");
        assertTrue(bookOf(poor.get(0)).isReady('W'), "and its cooldown was started");
    }

    // ---- seen ----

    /**
     * The one it hastens wears Hasted, played on it the moment it is hastened, for the haste's five seconds -- and a
     * second haste carries it on to the new end: its layers renew.
     */
    @Test
    void theHastenedWearsHastedForAsLongAsTheHaste() {
        var room = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f), one("Brute", 100f, 180f));

        assertTrue(room.castsItsHaste(), "the premise: it cast its haste");
        var worn = room.game().getLogic().drainEvents().stream().filter(EffectPlayed.class::isInstance)
                .map(EffectPlayed.class::cast).filter(played -> played.name().equals(haste().look()))
                .map(EffectPlayed::riding).toList();

        assertEquals("Hasted", haste().look());
        assertEquals(List.of(room.get(1).getId()), worn, "worn by the Brute, and by nobody else");
        var visuals = Visuals.create();
        Main.measureLooks(visuals, SETTINGS);
        assertEquals(5f, visuals.getEffectSeconds("Hasted"), 0.001f, "for five seconds");
        var layers = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals("Hasted"))
                .map(art -> Main.layerOf(art, SETTINGS)).toList();
        assertFalse(layers.isEmpty(), "the premise: Hasted is drawn in layers");
        for (var layer : layers) {
            assertEquals(EffectLayer.AURA, layer.type(), "worn, and going where it goes");
            assertTrue(layer.renews(), "a second haste carries it on to the new end");
        }
    }

    // ---- when ----

    /**
     * In a fight -- him inside its SkillDistance and in plain sight -- its haste follows its summoning; with nobody to
     * fight, never. The one it hastens is of its own, whoever stood sturdiest when it went.
     */
    @Test
    void inAFightItsHasteFollowsItsSummoningAndNeverInAnEmptyRoom() {
        var arena = Dungeon.world(room(NO_WALL), SETTINGS, unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var book = bookOf(first(game, SUMMONER));

        game.runHeadless(90);

        assertFalse(book.isReady('Q'), "the premise: it summoned");
        assertFalse(book.isReady('W'), "and then hastened");
        assertTrue(game.getLogic().getObjects().stream().anyMatch(object -> object.getPlayerIndex()
                        == arena.dungeon().getIndex() && bookOf(object) != null && bookOf(object).getHasteFrames() > 0),
                "someone of its own is hastened");

        var empty = room(SETTINGS, NO_WALL, one(SUMMONER, 100f, 150f));
        empty.game().runHeadless(400);
        assertTrue(bookOf(empty.get(0)).isReady('W'), "it hastened in an empty room");
    }

    // ---- the file ----

    /** Every monster carries a SkillBook: what a haste or an aura gives it is held there. */
    @Test
    void everyMonsterCarriesABook() {
        var bookless = new ArrayList<String>();
        for (var record : Content.records(Content.units(), "units")) {
            if (record instanceof Monster monster
                    && monster.modules().stream().noneMatch(SkillBook.Data.class::isInstance)) {
                bookless.add(monster.name());
            }
        }
        assertEquals(List.of(), bookless);
    }

    /** A haste needs a BoostPercent of at least 1 and DurationFrames, or the file is refused, saying so. */
    @Test
    void aHasteWithoutItsFiguresIsRefused() {
        var block = ShippedBlock.of(SUMMONER).text();
        for (var wrong : new String[][] {{"BoostPercent", "75"}, {"DurationFrames", "150"}}) {
            var text = block.replace("      " + wrong[0] + " = " + wrong[1] + "\n", "      " + wrong[0] + " = 0\n");
            assertNotEquals(block, text, "the premise: the haste's " + wrong[0] + " was made 0");

            var refused = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(text), wrong[0]);
            assertTrue(refused.getMessage().contains(wrong[0]), refused.getMessage());
        }
    }

    /**
     * As shipped: the summoner's W, written after its summoning -- three quarters again as fast for five seconds, one
     * of its own as far as 60 from it, 25 mana and the summoning's twelve seconds between -- and its ring drawn round
     * it as far as its Range.
     */
    @Test
    void asShippedTheSummonersHaste() {
        var haste = haste();

        assertEquals(List.of(SkillEffect.SUMMON, SkillEffect.HASTE),
                SETTINGS.skillsFor(SUMMONER).stream().map(Skill::effect).limit(2).toList(),
                "its summoning, then its haste");
        assertEquals(List.of(75, 150, 25, 360, 1), List.of(haste.boostPercent(), haste.durationFrames(),
                haste.manaCost(), haste.cooldownFrames(), haste.maxRank()));
        assertEquals(60f, haste.range(), 0.001f);
        assertEquals("Shiddat", haste.name());
        var ring = Main.rangeOf(haste, 5f);
        assertEquals(SkillRange.Shape.AROUND_HIM, ring.shape());
        assertEquals(haste.range(), ring.reach(), 0.001f);
    }
}
