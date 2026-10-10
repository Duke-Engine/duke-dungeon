package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.hero.GrowableBody;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * The second hero, and the promise that adding him was a file rather than a
 * rewrite.
 *
 * <p>Two things are being held here and they pull in opposite directions. The
 * first is that he <em>is</em> the archer's opposite — a hero who played the same
 * way would be a reskin, and the numbers that make him different are the ones
 * worth failing a build over. The second is that the archer did not move: a
 * second hero that quietly rebalanced the first is the classic way this goes
 * wrong, and nothing here would notice it except a test that says so.
 *
 * <p>What is deliberately <em>not</em> asserted is his art. His model has not
 * arrived yet, and a hero without one is a coloured shape by design — see the
 * note over {@code Hero Knight}. Naming a file that is not there would
 * make this class fail for the one reason that is not a fault.
 */
class KnightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARCHER = "Rogue";
    private static final String KNIGHT = "Knight";

    // ---- the two of them, as the file describes them ----

    @Test
    void theFileDescribesBothOfThem() {
        var names = SETTINGS.heroes().stream().map(hero -> hero.name()).toList();

        assertTrue(names.contains(ARCHER), "the archer went missing: " + names);
        assertTrue(names.contains(KNIGHT), "the knight is not in the file: " + names);
    }

    /** Each is a whole hero: a creature, a block, four skills and a face. */
    @Test
    void eachHeroIsComplete() {
        for (var name : java.util.List.of(ARCHER, KNIGHT)) {
            assertNotNull(templateOf(name), name + " has no creature block");
            assertEquals(name, SETTINGS.heroNamed(name).name(), name + " has no Hero block");
            assertEquals(4, SETTINGS.skillsFor(name).size(), name + " does not have four skills");
            assertTrue(SETTINGS.portraits().stream().anyMatch(art -> art.name().equals(name)),
                    name + " has no portrait");
        }
    }

    /** And each is called something of his own, rather than the panel's one word. */
    @Test
    void eachHeroHasHisOwnTitle() {
        var archer = SETTINGS.heroNamed(ARCHER).title();
        var knight = SETTINGS.heroNamed(KNIGHT).title();

        assertFalse(archer.isBlank(), "the archer's title was left to the panel's");
        assertFalse(knight.isBlank(), "the knight would wear the archer's title");
        assertFalse(archer.equals(knight), "both heroes are called the same thing");
    }

    // ---- he is the archer's opposite, and that is the whole point of him ----

    /**
     * More of him to kill, and each blow worth less.
     *
     * <p>Both, rather than either: health is how long he lasts and armour is what
     * a blow is worth, and a knight who had only the first would be an archer who
     * takes longer to grind down.
     */
    @Test
    void heIsHarderToKillThanTheArcher() {
        assertTrue(health(KNIGHT) > health(ARCHER) * 1.5f,
                "the knight has " + health(KNIGHT) + " against the archer's " + health(ARCHER));
        assertTrue(SETTINGS.heroNamed(KNIGHT).armourPercent()
                        > SETTINGS.heroNamed(ARCHER).armourPercent(),
                "the knight wears no more than the archer does");
    }

    /**
     * And he pays for it in the two things that decide whether a mistake can be
     * walked away from.
     */
    @Test
    void hePaysForItInReachAndSpeed() {
        assertTrue(reach(KNIGHT) < reach(ARCHER) / 3f,
                "the knight reaches " + reach(KNIGHT) + " against the archer's " + reach(ARCHER)
                        + " — a knight who outranges a fist is not walking into anything");
        assertTrue(speed(KNIGHT) < speed(ARCHER),
                "the knight moves at " + speed(KNIGHT) + " against " + speed(ARCHER));
    }

    /** He hits harder for it, which is what makes a single swing worth taking. */
    @Test
    void heHitsHarderThanTheArcher() {
        assertTrue(damage(KNIGHT) > damage(ARCHER),
                "the knight swings for " + damage(KNIGHT) + " against " + damage(ARCHER));
    }

    /**
     * He cannot see as far, and it costs him: the floor opens up around exactly
     * this number, so it is map knowledge as well as sight.
     */
    @Test
    void heSeesLessOfTheFloorThanTheArcher() {
        assertTrue(vision(KNIGHT) < vision(ARCHER),
                "the knight sees " + vision(KNIGHT) + " against the archer's " + vision(ARCHER));
    }

    /**
     * Every one of those numbers is in the file.
     *
     * <p>The promise, held from the outside: change the figures and the hero
     * changes, with nothing rebuilt. If this ever fails it means a number moved
     * into Java, which is the thing the whole arrangement exists to prevent.
     */
    @Test
    void hisNumbersComeOutOfTheFile() {
        var rewritten = DungeonSettings.parse("""
                Hero
                  Name = Knight
                  Title = Boshqacha
                  ArmourPercent = 44
                End
                """);

        assertEquals("Boshqacha", rewritten.heroNamed(KNIGHT).title());
        assertEquals(44, rewritten.heroNamed(KNIGHT).armourPercent());
    }

    // ---- his four ----

    /**
     * Four skills, four shapes, and none of them the archer's four.
     *
     * <p>Not a check that they are "different enough" — a check that he plays at a
     * different distance. The archer's longest reaches most of a room; nothing of
     * the knight's reaches past a doorway, and that is what forces the player in.
     */
    @Test
    void nothingHeCastsReachesAcrossARoom() {
        float furthest = 0f;
        for (var skill : SETTINGS.skillsFor(KNIGHT)) {
            furthest = Math.max(furthest,
                    Math.max(skill.range(), Math.max(skill.distance(), skill.radius())));
        }
        float archersLongest = 0f;
        for (var skill : SETTINGS.skillsFor(ARCHER)) {
            archersLongest = Math.max(archersLongest, skill.range());
        }

        assertTrue(furthest < archersLongest, "the knight's longest skill reaches " + furthest
                + " against the archer's " + archersLongest);
    }

    /** His four are the owner's four (2026-10-09), in their keys, and none of them is the archer's. */
    @Test
    void hisFourAreTheWaveTheChargeTheBloodAndThePull() {
        assertEquals(SkillEffect.WAVE, skillOf(KNIGHT, 'Q').effect());
        assertEquals(SkillEffect.SKEWER, skillOf(KNIGHT, 'W').effect());
        assertEquals(SkillEffect.DRAGON_BLOOD, skillOf(KNIGHT, 'E').effect());
        assertEquals(SkillEffect.PULL, skillOf(KNIGHT, 'R').effect());
        var archers = SETTINGS.skillsFor(ARCHER).stream().map(skill -> skill.effect()).toList();
        for (var skill : SETTINGS.skillsFor(KNIGHT)) {
            assertFalse(archers.contains(skill.effect()), "the archer was given the knight's " + skill.effect());
            assertFalse(skill.name().isBlank() || skill.blurb().isBlank(), skill.key() + " has no words");
            assertFalse(skill.icon().isBlank(), skill.key() + " has no picture");
        }
        assertTrue(SETTINGS.projectiles().stream().anyMatch(each -> each.name().equals(skillOf(KNIGHT, 'Q')
                .projectile())), "the wave flies as nothing the file describes");
    }

    /**
     * The owner, 2026-10-10: the wave is a half-moon on the floor and half as fast as it was; the charge is aimed as a
     * line, like the wave, not at a point inside a circle.
     */
    @Test
    void theWaveIsAHalfMoonAndTheChargeIsAimedAsALine() {
        var wave = skillOf(KNIGHT, 'Q');
        assertEquals(55f, wave.projectileSpeed(), 0.001f, "the wave flies at other than half its first 110");
        var looks = SETTINGS.effectLayers().stream().filter(art -> art.effect().equals("SwordWave")).toList();
        assertTrue(looks.stream().anyMatch(art -> "ARC".equals(art.fields().get("type"))),
                "the wave is not drawn as a half-moon lying on the floor");
        assertTrue(looks.stream().noneMatch(art -> "AURA".equals(art.fields().get("type"))),
                "the wave still carries a ball round it");

        var charge = skillOf(KNIGHT, 'W');
        var ring = Main.rangeOf(charge, SETTINGS.skillRing().selfRadius());
        assertEquals(uz.dukeengine.client3d.SkillRange.Shape.DOWN_A_LANE, ring.shape(), "the charge is not aimed as a line");
        assertEquals(charge.distance(), ring.reach(), 0.001f, "its line is not as long as his drive");
        assertEquals(2f * charge.radius(), ring.width(), 0.001f, "its line is not as wide as what it catches");
    }

    /** And a click down that line, near or far, drives him its whole Distance that way. */
    @Test
    void aClickDownTheLaneSendsTheChargeItsWholeLength() {
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 100f, 150f);
        game.runHeadless(1);
        int player = world.hero().getIndex();
        var near = new uz.dukeengine.rpg.skill.CastSkill(player, 'W', null, new Coord3D(110f, 150f, 0f));

        var sent = Dungeon.toTheEndOfItsLane(game.getLogic(), near, SETTINGS.skillsFor(KNIGHT));
        assertEquals(100f + skillOf(KNIGHT, 'W').distance(), sent.point().x(), 0.01f, "a near click cut the charge short");
        assertEquals(150f, sent.point().y(), 0.01f, "the charge went another way than the click");
        var wave = new uz.dukeengine.rpg.skill.CastSkill(player, 'Q', null, new Coord3D(110f, 150f, 0f));
        assertEquals(wave, Dungeon.toTheEndOfItsLane(game.getLogic(), wave, SETTINGS.skillsFor(KNIGHT)),
                "a skill that is not the charge was moved");
    }

    /** His ultimate is earned, grows fewer times, and holds a room longer than his charge holds a line. */
    @Test
    void hisUltimateIsEarnedAndHoldsLongest() {
        var ultimate = skillOf(KNIGHT, 'R');

        assertTrue(ultimate.isUltimate(), "an ultimate has to be earned");
        assertTrue(ultimate.maxRank() < skillOf(KNIGHT, 'Q').maxRank(),
                "and it grows fewer times than an ordinary skill");
        assertTrue(ultimate.stunFrames() > skillOf(KNIGHT, 'W').stunFrames(),
                "the pull holds a room no longer than the charge holds a line");
    }

    /** And his charge carries and stuns, where the archer's sprint hurts nothing. */
    @Test
    void hisChargeCarriesWhereTheArchersSprintDoesNot() {
        var charge = skillOf(KNIGHT, 'W');
        var sprint = skillOf(ARCHER, 'E');

        assertEquals(SkillEffect.DASH, sprint.effect());
        assertTrue(charge.damage() > 0f && charge.stunFrames() > 0, "a charge that hurts and holds nothing is a sprint");
        assertTrue(charge.projectileSpeed() > speed(KNIGHT) * 1.4f && charge.projectileSpeed() < speed(KNIGHT) * 2.2f,
                "the push is driven at " + charge.projectileSpeed() + " against his walk of " + speed(KNIGHT)
                        + ": a charge, and slow enough to be seen");
        assertEquals(0f, sprint.damage(), 0.001f,
                "the archer's sprint was given damage it never had");
    }

    // ---- and the archer did not move ----

    /**
     * The archer is exactly what he was.
     *
     * <p>The failure this class is most likely to catch, and the least likely to
     * be noticed any other way: a second hero balanced by moving the first. These
     * are his shipped figures, written down.
     */
    @Test
    void theArcherWasNotRebalanced() {
        assertEquals(550f, health(ARCHER), 0.001f);
        assertEquals(29f, speed(ARCHER), 0.001f);
        assertEquals(14f, damage(ARCHER), 0.001f);
        assertEquals(60f, reach(ARCHER), 0.001f);
        assertEquals(0, SETTINGS.heroNamed(ARCHER).armourPercent(),
                "the archer was given armour he never had");
    }

    /** And his four skills are the four they were. */
    @Test
    void theArchersSkillsWereNotRebalanced() {
        assertEquals(SkillEffect.STRIKE, skillOf(ARCHER, 'Q').effect());
        assertEquals(45f, skillOf(ARCHER, 'Q').damage(), 0.001f);
        assertEquals(SkillEffect.AREA_DAMAGE, skillOf(ARCHER, 'W').effect());
        assertFalse(skillOf(ARCHER, 'W').lasts(), "his panic button became a whirlwind");
        assertEquals(SkillEffect.DASH, skillOf(ARCHER, 'E').effect());
        assertEquals(SkillEffect.EMPOWER, skillOf(ARCHER, 'R').effect());
    }

    // ---- who actually walks into the dungeon ----

    /**
     * The file says which of them is played, and it is one of them.
     *
     * <p>Which one is a content decision and not something to fail a build over —
     * the whole reason the line exists is that somebody will change it to try the
     * other hero. What is worth holding is that the name resolves: a typo there is
     * a dungeon with nobody in it, and nothing else would say so.
     */
    @Test
    void theFileNamesAHeroItAlsoDescribes() {
        var played = SETTINGS.run().defaultHero();

        assertEquals(played, SETTINGS.playedHeroLook().name(),
                "DefaultHero names " + played + ", which has no Hero block");
        assertEquals(4, SETTINGS.skillsFor(played).size(), played + " has no four skills");
        assertNotNull(templateOf(played), played + " has no creature block");
    }

    /** And naming the other one is all it takes to swap him in. */
    @Test
    void namingTheOtherOneSwapsHimIn() {
        var swapped = DungeonSettings.parse("""
                Run
                  DefaultHero = Knight
                End
                """);

        assertEquals(KNIGHT, swapped.run().defaultHero());
    }

    /**
     * Which really does decide who is standing in the dungeon.
     *
     * <p>The settings line and the spawned creature, checked together — the point
     * of the whole change is that those two agree, and they did not before: the
     * word was in Java, so a hero could be described in full and never walk in.
     */
    @Test
    void theHeroTheFileNamesIsTheHeroWhoSpawns() {
        var game = Dungeon.create(11L);
        game.runHeadless(1);

        var spawned = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(SETTINGS.run().defaultHero()))
                .findFirst().orElse(null);

        assertNotNull(spawned, "nobody of the played hero's template is in the dungeon");
    }

    /**
     * And a whole run really starts with him in it.
     *
     * <p>The test the rest of this class is arithmetic for. Everything else asks
     * whether a number is in a file; this one generates a dungeon, puts the knight
     * in it, runs it and asks whether he is alive and armed at the end — which is
     * the only question that catches a hero who parses perfectly and cannot be
     * played.
     */
    @Test
    void aWholeRunStartsWithTheKnightInIt() {
        var asKnight = DungeonSettings.parse(withDefaultHero(KNIGHT));
        var game = Dungeon.create(4242L, asKnight);

        game.runHeadless(120); // four seconds of a real floor

        var him = find(game, KNIGHT);
        assertNotNull(him, "the knight never walked into the dungeon");
        assertTrue(him.getBody().getHealth() > 0f, "he did not survive four seconds of floor one");
        assertNotNull(him.findModule(SkillBook.class), "he arrived without his skills");
        assertEquals(4, him.findModule(SkillBook.class).getSkills().size(),
                "he arrived with somebody else's skills, or none");
        assertNull(find(game, ARCHER), "the archer came too");
    }

    /** And swapping back is the same one line. */
    @Test
    void andSwappingBackStartsTheArcher() {
        var game = Dungeon.create(4242L, DungeonSettings.parse(withDefaultHero(ARCHER)));

        game.runHeadless(30);

        assertNotNull(find(game, ARCHER), "the archer did not come back");
        assertNull(find(game, KNIGHT), "the knight is still in the dungeon");
    }

    /**
     * The shipped file with one line changed.
     *
     * <p>The whole file rather than a fragment, because a partial one keeps its
     * lists and loses its loose figures — a dungeon parsed from four lines has no
     * map size — and this has to be a real run.
     */
    private static String withDefaultHero(String template) {
        var file = Content.data();
        // Whatever it currently says, not "the archer's line": the point of the
        // line is that somebody changes it, so a test that only knew how to change
        // it away from one value would fail for the person using it.
        var swapped = file.replaceAll("(?m)^(\\s*)DefaultHero\\s*=.*$",
                "$1DefaultHero = " + template);
        assertFalse(swapped.equals(file) && !SETTINGS.run().defaultHero().equals(template),
                "DefaultHero is not written the way this test expects to find it");
        return swapped;
    }

    // ---- what his armour is worth, once it reaches his body ----

    /**
     * His plate reaches the body that takes the blows.
     *
     * <p>Not a check that a number was stored: a check that the same blow costs
     * him less than it costs an archer. The armour is set through the level and
     * the loot, in one place, and a figure that never got there would be invisible
     * on every screen except the one that matters.
     */
    @Test
    void hisArmourReachesTheBodyThatTakesTheBlow() {
        float knightsLoss = blowSuffered(KNIGHT);
        float archersLoss = blowSuffered(ARCHER);

        assertTrue(knightsLoss < archersLoss, "a blow costs the knight " + knightsLoss
                + " and the archer " + archersLoss + " — his plate never arrived");
    }

    /** What one 100-point blow actually takes off that hero, armour and all. */
    private static float blowSuffered(String template) {
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(template, world.hero(), 150f, 150f);
        game.runHeadless(1);
        var him = find(game, template);
        var progress = new HeroProgress(world.hero()::getIndex,
                uz.dukeengine.dungeon.Dungeon.banner(world.game(), world.hero()), SETTINGS.levelling(),
                SETTINGS.attributeRules(), SETTINGS.progression().levelUpBannerFrames(),
                new uz.dukeengine.rpg.item.LootBag());
        progress.playing(SETTINGS.heroNamed(template));
        progress.carryOver(game.getLogic(), him);

        float before = him.getBody().getHealth();
        him.getBody().damage(100f);
        return before - him.getBody().getHealth();
    }

    // ---- the shapes themselves, cast in a real world ----

    /**
     * Q: the wave runs down the line and cuts all three skeletons in it, once each, and not the one beside the line.
     * They are held still, so this is about the wave and not about who walked where.
     */
    @Test
    void theWaveCutsEveryoneInItsLineOnce() {
        var wave = skillOf(KNIGHT, 'Q');
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 100f, 150f);
        for (float x : new float[] {124f, 134f, 144f}) { // past his sword's reach, inside the wave's
            game.spawn("Skeleton", world.dungeon(), x, 150f);
        }
        game.spawn("Skeleton", world.dungeon(), 132f, 150f + wave.hitWidth() + 20f); // beside the line
        game.runHeadless(1);
        var skeletons = all(game, "Skeleton");
        skeletons.forEach(KnightTest::sturdy);
        skeletons.forEach(KnightTest::holdStill);
        world.orders().hold(world.hero().getIndex(), true); // his sword picks no fights: the skill alone
        var book = find(game, KNIGHT).findModule(SkillBook.class);
        var full = skeletons.stream().map(each -> each.getBody().getHealth()).toList();

        assertTrue(book.cast('Q', 1, null, new Coord3D(250f, 150f, 0f)), "the wave was not thrown");
        game.runHeadless(45); // long past the end of its Distance

        for (int i = 0; i < 3; i++) {
            assertEquals(full.get(i) - wave.damage(), skeletons.get(i).getBody().getHealth(), 0.5f,
                    "skeleton " + i + " in the line was not cut exactly once");
        }
        assertEquals(full.get(3), skeletons.get(3).getBody().getHealth(), 0.001f, "the one beside the line was cut");
    }

    /**
     * W: the shield charge catches what it meets -- three skeletons, two side by side and one further on -- carries
     * them along at his front while it drives its whole Distance, and drops them there stunned and hurt when it ends.
     */
    @Test
    void theChargeCarriesWhatItMeetsAndStunsIt() {
        var charge = skillOf(KNIGHT, 'W');
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 80f, 150f);
        game.spawn("Skeleton", world.dungeon(), 100f, 146f);
        game.spawn("Skeleton", world.dungeon(), 100f, 154f);
        game.spawn("Skeleton", world.dungeon(), 112f, 150f);
        game.runHeadless(1);
        var knight = find(game, KNIGHT);
        var skeletons = all(game, "Skeleton");
        skeletons.forEach(KnightTest::sturdy);
        var book = knight.findModule(SkillBook.class);
        var full = skeletons.stream().map(each -> each.getBody().getHealth()).toList();

        assertTrue(book.cast('W', 1, null, new Coord3D(250f, 150f, 0f)), "he did not charge");
        game.runHeadless(20);
        assertTrue(book.keepsBusy(), "the charge was over in two-thirds of a second: it is meant to be seen");
        for (int i = 0; i < skeletons.size(); i++) {
            float ahead = skeletons.get(i).getPosition().x() - knight.getPosition().x();
            assertTrue(ahead > 0f && ahead < 30f, "skeleton " + i + " is " + ahead + " from him, not at his front");
            assertTrue(skeletons.get(i).getPosition().x() > 112f, "skeleton " + i + " was not carried along");
        }

        int frames = 0;
        while (book.keepsBusy() && frames++ < 120) {
            game.runHeadless(1);
        }
        game.runHeadless(1); // the stun's timer is read on the creature's own next frame
        assertEquals(80f + charge.distance(), knight.getPosition().x(), 1f, "he did not drive its whole Distance");
        for (int i = 0; i < skeletons.size(); i++) {
            var each = skeletons.get(i);
            assertTrue(each.getPosition().x() > knight.getPosition().x(), "skeleton " + i + " was not dropped at his front");
            assertTrue(each.getBody().getHealth() <= full.get(i) - charge.damage() + 0.5f, "skeleton " + i + " not hurt");
            assertTrue(each.hasStatus(ObjectStatus.DISABLED), "skeleton " + i + " was dropped and not stunned");
        }
        game.runHeadless(charge.stunFrames() + 2);
        assertTrue(skeletons.stream().noneMatch(each -> each.hasStatus(ObjectStatus.DISABLED)),
                "the stun outlasted its StunFrames");
    }

    /**
     * E unlearnt: a knight who has put no point in his blood has none of it -- no blow turned aside, nothing mended
     * past his own regeneration. A run tells his book rank 0 (see thePointsHeSpendsReachHisBlood); here it is said
     * straight to the book, so the mend is his alone to compare against.
     */
    @Test
    void anUnlearntDragonBloodDoesNothing() {
        var session = Dungeon.newSession(11L, DungeonSettings.parse(withDefaultHero(KNIGHT)));
        session.game().runHeadless(3);
        assertEquals(0, session.run().getLearnt().rankOf('E'), "the premise: no point in his blood");
        assertEquals(1f, find(session.game(), KNIGHT).getBody().getDamageScale(), 0.0001f,
                "in a run, an unlearnt blood turned a blow aside");

        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 150f, 150f);
        game.runHeadless(1);
        var knight = find(game, KNIGHT);
        knight.findModule(SkillBook.class).setRank('E', 0);
        game.runHeadless(1);
        var body = knight.getBody();
        assertEquals(1f, body.getDamageScale(), 0.0001f, "an unlearnt blood turned a blow aside");
        body.damage(400f);
        float hurt = body.getHealth();
        game.runHeadless(60); // two seconds
        float ownMend = 2f * SETTINGS.heroNamed(KNIGHT).healthRegen() / 10f;
        // No more than his own mend (none at all in a bare world, which gives him no HealthRegen): rank 1 would be 6.
        assertTrue(body.getHealth() - hurt <= ownMend + 1f, "an unlearnt blood mended him " + (body.getHealth() - hurt));
    }

    /**
     * E: the dragon blood turns aside a share of every blow and mends him every second, both rising with the rank his
     * points say -- told to his book by setRank.
     */
    @Test
    void dragonBloodCutsBlowsAndMendsMoreWithEachRank() {
        var blood = skillOf(KNIGHT, 'E');
        float[] lost = new float[5];
        float[] mended = new float[5];
        for (int rank : new int[] {1, 4}) {
            var world = Dungeon.world(room(), SETTINGS, Content.units());
            var game = world.game();
            game.spawn(KNIGHT, world.hero(), 150f, 150f);
            game.runHeadless(1);
            var knight = find(game, KNIGHT);
            knight.findModule(SkillBook.class).setRank('E', rank);
            game.runHeadless(1);
            var body = knight.getBody();

            assertEquals(1f - blood.boostAt(rank) / 100f, body.getDamageScale(), 0.001f,
                    "rank " + rank + " of his blood is not what his body's blows are scaled by");
            float before = body.getHealth();
            body.damage(400f);
            lost[rank] = before - body.getHealth();
            float hurt = body.getHealth();
            game.runHeadless(60); // two seconds
            mended[rank] = body.getHealth() - hurt;
        }
        assertTrue(lost[4] < lost[1], "rank 4 turns aside no more than rank 1: " + lost[1] + " against " + lost[4]);
        // His own regeneration is the same at both ranks: what differs is the blood's, 3 more a second for each rank.
        float more = (4 - 1) * blood.heal() * 2f;
        assertEquals(more, mended[4] - mended[1], 2f, "the blood mends " + mended[1] + " at rank 1 and "
                + mended[4] + " at rank 4 over two seconds");
    }

    /** And the run tells his book the rank he bought, so a point in E is a point in his blood. */
    @Test
    void thePointsHeSpendsReachHisBlood() {
        var session = Dungeon.newSession(11L, DungeonSettings.parse(withDefaultHero(KNIGHT)));
        session.game().runHeadless(2);
        var learnt = session.run().getLearnt();
        assertTrue(learnt.raise('E', 5) && learnt.raise('E', 5), "the premise: two points went into his blood");
        session.game().runHeadless(2);

        assertEquals(1f - skillOf(KNIGHT, 'E').boostAt(2) / 100f,
                find(session.game(), KNIGHT).getBody().getDamageScale(), 0.001f,
                "his book was not told the rank he bought");
    }

    /** And a save puts it back: the ranks restored are the ranks his book is told. */
    @Test
    void aLoadedRunKeepsHisBloodsRank() {
        var asKnight = DungeonSettings.parse(withDefaultHero(KNIGHT));
        var save = new uz.dukeengine.dungeon.run.SaveGame(11L, null, 1, KNIGHT, asKnight.levelling().totalXpFor(6),
                java.util.List.of(new uz.dukeengine.dungeon.run.SaveGame.Rank('Q', 1),
                        new uz.dukeengine.dungeon.run.SaveGame.Rank('W', 1),
                        new uz.dukeengine.dungeon.run.SaveGame.Rank('E', 3)),
                java.util.List.of());
        var session = Dungeon.newSession(99L, asKnight);
        session.run().resume(save, uz.dukeengine.dungeon.run.Floors.resumed(save, asKnight));
        session.game().runHeadless(3);

        assertEquals(3, session.run().getLearnt().rankOf('E'), "the premise: the save put three points back");
        assertEquals(1f - skillOf(KNIGHT, 'E').boostAt(3) / 100f,
                find(session.game(), KNIGHT).getBody().getDamageScale(), 0.001f,
                "loaded, his blood runs at a rank the save never had");
    }

    /**
     * R: everything within its Radius is moved to one point in front of him, hurt and stunned; what stands further
     * off is left where it is.
     */
    @Test
    void thePullGathersTheRoomToOnePointAndStunsIt() {
        var pull = skillOf(KNIGHT, 'R');
        var world = Dungeon.world(room(), SETTINGS, Content.units());
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 150f, 150f);
        float near = pull.radius() - 6f;
        game.spawn("Skeleton", world.dungeon(), 150f, 150f + near);
        game.spawn("Skeleton", world.dungeon(), 150f - near, 150f);
        game.spawn("Skeleton", world.dungeon(), 150f, 150f - near);
        game.spawn("Skeleton", world.dungeon(), 150f + pull.radius() + 30f, 150f); // out of reach
        game.runHeadless(1);
        var knight = find(game, KNIGHT);
        var skeletons = all(game, "Skeleton");
        skeletons.forEach(KnightTest::sturdy);
        world.orders().hold(world.hero().getIndex(), true); // his sword picks no fights: the skill alone
        var far = skeletons.getLast().getPosition();
        var full = skeletons.stream().map(each -> each.getBody().getHealth()).toList();

        assertTrue(knight.findModule(SkillBook.class).cast('R', 1), "the pull did not go off");
        // Read before a frame runs: gathered in front of him, they are inside his sword's reach.
        var hurt = skeletons.stream().map(each -> each.getBody().getHealth()).toList();
        game.runHeadless(1); // the stun's timer is read on each creature's own next frame

        var at = knight.getPosition();
        var point = new Coord3D(at.x() + (float) Math.cos(knight.getOrientation()) * pull.range(),
                at.y() + (float) Math.sin(knight.getOrientation()) * pull.range(), 0f);
        for (int i = 0; i < 3; i++) {
            var each = skeletons.get(i);
            float off = (float) Math.hypot(each.getPosition().x() - point.x(), each.getPosition().y() - point.y());
            assertTrue(off < near - 10f, "skeleton " + i + " stands " + off + " from the point: not gathered");
            assertTrue(each.hasStatus(ObjectStatus.DISABLED), "skeleton " + i + " was gathered and not stunned");
            assertEquals(full.get(i) - pull.damage(), hurt.get(i), 0.5f, "skeleton " + i + " not hurt");
        }
        assertEquals(far, skeletons.getLast().getPosition(), "the one out of its reach was pulled");
        assertEquals(full.getLast(), skeletons.getLast().getBody().getHealth(), 0.001f, "the one out of reach was hurt");
    }

    private static java.util.List<GameObject> all(uz.dukeengine.game.DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template)).toList();
    }

    /** Health enough that no single skill kills it, so what each one takes can be read off what is left. */
    private static void sturdy(GameObject creature) {
        ((GrowableBody) creature.getBody()).setMaxHealth(500f);
        creature.getBody().setHealth(500f);
    }

    /** Stunned for good: it neither walks nor swings, so what happens to it is the skill's alone. */
    private static void holdStill(GameObject creature) {
        var timers = creature.findModule(uz.dukeengine.combat.module.StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, Integer.MAX_VALUE);
        }
        creature.setStatus(ObjectStatus.DISABLED);
    }

    // ---- what each of them carries ----

    /**
     * {@code Holds} is repeatable, and each one keeps its own lines.
     *
     * <p>The trap in a repeatable block with fields under it: the fields have to
     * attach to the {@code Holds} ABOVE them and not to the last one parsed or to
     * all of them at once. Written the obvious way, a mage's book would have
     * ended up in the hand his staff is in — both lines say {@code HeldIn}, and
     * whichever was read last would have won for both. Asked of the mage since the
     * knight, Lange, carries his sword and shield in his own file.
     */
    @Test
    void eachThingCarriedKeepsItsOwnLines() {
        var carried = SETTINGS.heroNamed("Mage").held();

        assertEquals(2, carried.size(), "a staff and a book");
        assertTrue(carried.get(0).model().contains("staff"), "the staff is named first");
        assertTrue(carried.get(1).model().contains("spellbook"));
        assertNotEquals(carried.get(0).bone(), carried.get(1).bone(),
                "both are in the same hand, so the second line overwrote the first");
    }

    /**
     * And every hero carries what he fights with -- in his hands, or in his own file.
     *
     * <p>It was two things each while all three came out of one kit and wore their
     * weapons apart. The knight, Lange, holds nothing in {@code Held}: his sword and shield
     * are part of his model, the sword skinned to a bone of its own (art/lange/export_lange.py).
     * The Huntress wears her quiver and its arrows in her own mesh, on her hip, so the bow
     * is the one thing she is handed.
     */
    @Test
    void everyHeroCarriesSomethingOnABone() {
        for (var him : SETTINGS.heroes()) {
            if (him.name().equals(KNIGHT)) {
                assertTrue(him.held().isEmpty(), "Lange's sword and shield are in his own file");
                continue;
            }
            assertFalse(him.held().isEmpty(), him.name() + " carries nothing at all");
            for (var held : him.held()) {
                assertTrue(held.isCarried(),
                        him.name() + " has a Holds with no bone under it, which hangs nothing");
            }
        }
    }

    /**
     * A hero who carries one thing writes one {@code Held} block.
     *
     * <p>A monster carries at most one, written the same way; this checks the hero side of
     * the same shape by reading a block that names exactly one.
     */
    @Test
    void oneThingCarriedIsStillOneBlock() {
        var one = DungeonSettings.parse("""
                Hero
                  Name = Solo
                  Model = models/heroes/rogue.glb
                  Held = [
                    Held
                      Model = models/heroes/bow.gltf
                      Bone = handslot.l
                      Scale = 2
                      Roll = 180
                    End
                  ]
                End
                """).heroNamed("Solo").held();

        assertEquals(1, one.size());
        assertEquals("models/heroes/bow.gltf", one.get(0).model());
        assertEquals("handslot.l", one.get(0).bone());
        assertEquals(2f, one.get(0).scale(), 0.001f);
        assertEquals(180f, one.get(0).roll(), 0.001f);
    }

    /** And a carried thing may sit off its bone, by X, Y and Z. */
    @Test
    void aCarriedThingMayBeShiftedOffItsBone() {
        // Read from a block of its own: the archer's quiver was the shifted one, and the
        // Huntress who replaced him carries hers in her own mesh.
        var quiver = DungeonSettings.parse("""
                Hero
                  Name = Solo
                  Model = models/heroes/rogue.glb
                  Held = [
                    Held
                      Model = models/heroes/quiver.gltf
                      Bone = chest
                      X = 0.12
                      Y = 0.10
                      Z = -0.22
                    End
                  ]
                End
                """).heroNamed("Solo").held().get(0);

        assertEquals("chest", quiver.bone());
        assertEquals(0.12f, quiver.x(), 0.001f);
        assertEquals(0.10f, quiver.y(), 0.001f);
        assertEquals(-0.22f, quiver.z(), 0.001f);
    }

    // ---- helpers ----

    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                boolean edge = x == 0 || y == 0 || x == 29 || y == 29;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(uz.dukeengine.game.DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static uz.dukeengine.rpg.skill.Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream()
                .filter(skill -> skill.key() == key)
                .findFirst().orElseThrow(() -> new AssertionError(hero + " has no " + key));
    }

    /**
     * The creature definitions, read once.
     *
     * <p>One world for the class rather than one per question. A game has no logic
     * until it has run a frame — {@code getLogic()} is null before that — and
     * building a fresh dungeon to ask what a knight's health is would be a
     * generated map per assertion.
     */
    private static uz.dukeengine.core.thing.ThingFactory templates;

    private static ThingTemplate templateOf(String name) {
        if (templates == null) {
            var game = Dungeon.world(room(), SETTINGS, Content.units()).game();
            game.runHeadless(1);
            templates = game.getLogic().getThingFactory();
        }
        return templates.findTemplate(name);
    }

    /**
     * What he is at his first level: his creature block and his attributes together,
     * the way the game builds him. The block alone is only what the attributes are
     * added to.
     */
    private static uz.dukeengine.rpg.hero.HeroFigures firstLevel(String template) {
        var look = SETTINGS.heroNamed(template);
        return uz.dukeengine.rpg.hero.HeroFigures.of(
                uz.dukeengine.rpg.hero.HeroBase.of(templateOf(template)), look.maxMana(),
                look.attributes(), SETTINGS.attributeRules(), 1,
                uz.dukeengine.rpg.hero.HeroFigures.Found.NOTHING);
    }

    private static float health(String template) {
        return firstLevel(template).maxHealth();
    }

    private static float damage(String template) {
        return firstLevel(template).attack();
    }

    private static float reach(String template) {
        for (var entry : templateOf(template).modules()) {
            if (entry instanceof WeaponUpdate.Data weapon) {
                return weapon.attackRange();
            }
        }
        return 0f;
    }

    private static float speed(String template) {
        return firstLevel(template).speed();
    }

    private static float vision(String template) {
        return uz.dukeengine.core.thing.Sighted.of(templateOf(template));
    }
}
