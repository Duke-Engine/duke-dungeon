package uz.dukeengine.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.combat.LevelBonus;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.Summoned;
import uz.dukeengine.dungeon.stage.StageCheck;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.module.ExperienceModule;

/**
 * A monster's level: where it is met, and what the level makes of it.
 *
 * <p>A place has a tier -- today a floor's depth, which is what a stage's {@code Difficulty} already means -- and the
 * level rule is handed the tier and nothing else: the way in at one end, the chamber before the boss's at the other,
 * the boss two above, and nothing above the cap.
 */
class MonsterLevelTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The shipped files with one line of them saying something else. */
    private static String dataWith(String line, String instead) {
        var data = Content.data().replace(line, instead);
        assertNotEquals(Content.data(), data, "the premise: the shipped files say " + line.strip());
        return data;
    }

    // ---- the rule ----

    /** Each tier runs from where the one before closed: 1 to 8, 8 to 13, 13 to 20, 20 to 33, the boss two above. */
    @Test
    void eachTierRunsFromWhereTheOneBeforeClosed() {
        int[][] tiers = {{1, 1, 8, 10}, {2, 8, 13, 15}, {3, 13, 20, 22}, {4, 20, 33, 35}};
        for (var tier : tiers) {
            assertEquals(tier[1], SETTINGS.wayInLevel(tier[0]), "tier " + tier[0] + "'s way in");
            assertEquals(tier[2], SETTINGS.beforeBossLevel(tier[0]), "tier " + tier[0] + " before its boss");
            assertEquals(tier[3], SETTINGS.bossLevel(tier[0]), "tier " + tier[0] + "'s boss");
        }
    }

    /** Asked straight for the fourth tier, it is the fourth tier: worked out in one step, never tier by tier. */
    @Test
    void theFourthTierAskedDirectlyIsTheFourthTier() {
        var fresh = DungeonSettings.load();

        assertEquals(33, fresh.beforeBossLevel(4));
        assertEquals(20, fresh.wayInLevel(4));
        assertEquals(35, fresh.bossLevel(4));
    }

    /** With no growth, every tier after the first stays where the first closed: 8 to 8. */
    @Test
    void withNoGrowthEveryTierAfterTheFirstStaysWhereTheFirstClosed() {
        var flat = DungeonSettings.parse(dataWith("    TierGrowthPercent = 60\n", "    TierGrowthPercent = 0\n"));

        assertEquals(1, flat.wayInLevel(1));
        for (int tier = 2; tier <= 6; tier++) {
            assertEquals(8, flat.wayInLevel(tier), "tier " + tier + "'s way in");
            assertEquals(8, flat.beforeBossLevel(tier), "tier " + tier + " before its boss");
        }
    }

    /** Nothing stands above the cap, the boss included: the deep stage plays at it throughout. */
    @Test
    void nothingStandsAboveTheCap() {
        assertEquals(50, SETTINGS.maxMonsterLevel());
        for (int tier = 1; tier <= 20; tier++) {
            assertTrue(SETTINGS.bossLevel(tier) <= SETTINGS.maxMonsterLevel(),
                    "tier " + tier + "'s boss stands at " + SETTINGS.bossLevel(tier));
        }
        int deep = Stages.load("deep", SETTINGS).difficulty();
        assertEquals(50, SETTINGS.wayInLevel(deep), "the deep stage's way in");
        assertEquals(50, SETTINGS.beforeBossLevel(deep), "and before its boss");
        assertEquals(50, SETTINGS.bossLevel(deep), "and its boss");
    }

    /**
     * Along the way a level is the way in plus its share of the climb, the share the steps walked over the steps to
     * the chamber -- rounded once, and never past that chamber's.
     */
    @Test
    void alongTheWayALevelIsItsShareOfTheClimb() {
        assertEquals(1, SETTINGS.levelAlong(1, 0, 70), "at the way in");
        assertEquals(5, SETTINGS.levelAlong(1, 35, 70), "half way: 1 + 7 x 0.5 is 4.5, rounded once");
        assertEquals(7, SETTINGS.levelAlong(1, 60, 70), "most of the way: 1 + 7 x 6/7 is 7");
        assertEquals(8, SETTINGS.levelAlong(1, 70, 70), "in the middle of the chamber");
        assertEquals(8, SETTINGS.levelAlong(1, 140, 70), "and past it, the chamber's");
        assertEquals(8, SETTINGS.levelAlong(1, -1, 70), "and where no step reaches, the whole way");
        assertEquals(11, SETTINGS.levelAlong(2, 35, 70), "the second tier half way: 8 + 5 x 0.5, rounded once");
        assertEquals(1, SETTINGS.levelAlong(1, 1, 70), "a step in: 7 x 1/70 of the climb is a tenth, rounded down");
        assertEquals(2, SETTINGS.levelAlong(1, 5, 70), "five in: 7 x 5/70 is exactly a half, rounded up -- once, at the"
                + " end, and not the share to whole percents first, which would take it to 0.49");
    }

    /** The cap holds for the first tier's way in too: a file that starts a descent above it stands at it. */
    @Test
    void aWayInAboveTheCapStandsAtTheCap() {
        var high = DungeonSettings.parse(dataWith("    WayInLevel = 1\n    BeforeBossLevel = 8\n",
                "    WayInLevel = 60\n    BeforeBossLevel = 70\n"));

        assertEquals(50, high.maxMonsterLevel(), "the premise: the cap is where it was");
        assertEquals(50, high.wayInLevel(1), "the way in");
        assertEquals(50, high.beforeBossLevel(1), "and before the boss");
        assertEquals(50, high.bossLevel(1), "and the boss");
    }

    /** A rule that steps back is refused when the file is read: no way in below 1, no chamber below it, no shrinking. */
    @Test
    void aRuleThatStepsBackIsRefused() {
        for (var wrong : new String[][] {
                {"    WayInLevel = 1\n", "    WayInLevel = 0\n"},
                {"    BeforeBossLevel = 8\n", "    BeforeBossLevel = 0\n"},
                {"    TierGrowthPercent = 60\n", "    TierGrowthPercent = -10\n"},
                {"    BossLevelsAbove = 2\n", "    BossLevelsAbove = -1\n"},
                {"    MaxMonsterLevel = 50\n", "    MaxMonsterLevel = 0\n"}}) {
            var data = dataWith(wrong[0], wrong[1]);
            assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(data), wrong[1].strip());
        }
    }

    // ---- along the way ----

    /** The first floor of the descent drawn from {@code seed}. */
    private static GeneratedDungeon firstFloor(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1);
    }

    /** How many steps from the way in a monster stands. */
    private static int stepsTo(StageCheck.Walk walk, GeneratedDungeon.Monster monster) {
        return walk.to(monster.at().cellX(), monster.at().cellY());
    }

    /** Whether a monster stands inside a chamber's footprint. */
    private static boolean inside(GeneratedDungeon.Room room, GeneratedDungeon.Monster monster) {
        int x = monster.at().cellX();
        int y = monster.at().cellY();
        return x >= room.x() && y >= room.y() && x < room.x() + room.w() && y < room.y() + room.h();
    }

    /** Further along is never lower: of two monsters, the one more steps from the way in stands at least as high. */
    @Test
    void aMonsterFurtherAlongIsNeverBelowOneNearer() {
        for (long seed = 1; seed <= 6; seed++) {
            var floor = firstFloor(seed);
            var walk = StageCheck.walk(floor);
            var levels = Spawner.levelsOf(floor, SETTINGS, 1);
            var monsters = floor.monsters();
            for (int a = 0; a < levels.length; a++) {
                assertTrue(stepsTo(walk, monsters.get(a)) >= 0, "the premise: every monster can be walked to");
                for (int b = 0; b < levels.length; b++) {
                    if (stepsTo(walk, monsters.get(a)) > stepsTo(walk, monsters.get(b))) {
                        assertTrue(levels[a] >= levels[b], "seed " + seed + ": " + stepsTo(walk, monsters.get(a))
                                + " steps in at " + levels[a] + ", " + stepsTo(walk, monsters.get(b)) + " at "
                                + levels[b]);
                    }
                }
            }
        }
    }

    /**
     * On a floor with a keep the way is the walk to the middle of the chamber the keep's road leaves from, and every
     * monster stands at its share of it: a floor flattened to the top level, or climbing toward somewhere else, is
     * neither.
     */
    @Test
    void onAFloorWithAKeepEachMonsterStandsAtItsShareOfTheWayToItsChamber() {
        int lowest = Integer.MAX_VALUE;
        for (long seed = 1; seed <= 6; seed++) {
            var floor = firstFloor(seed);
            var keep = floor.keep();
            assertNotNull(keep, "the premise: seed " + seed + "'s first floor has its keep");
            var chamber = floor.rooms().get(keep.chamber());
            var walk = StageCheck.walk(floor);
            int way = walk.to(chamber.centerCellX(), chamber.centerCellY());
            var levels = Spawner.levelsOf(floor, SETTINGS, 1);

            assertTrue(way > 0, "the premise: seed " + seed + "'s chamber before the keep can be walked to, and is not"
                    + " the way in");
            for (int i = 0; i < levels.length; i++) {
                var monster = floor.monsters().get(i);
                assertEquals(SETTINGS.levelAlong(1, stepsTo(walk, monster), way), levels[i], "seed " + seed + ": a "
                        + monster.kind() + " " + stepsTo(walk, monster) + " steps in, of " + way);
                lowest = Math.min(lowest, levels[i]);
            }
        }
        assertTrue(lowest < 7, "the climb starts low: over the seeds the lowest monster stands at " + lowest);
    }

    /**
     * The climb ends in the chamber the keep's road leaves from: every monster in it stands at 7 or 8, and everything
     * past it -- the guard in the court -- at 8.
     */
    @Test
    void theChamberBeforeTheKeepIsNearlyAtTheTopAndItsCourtAtIt() {
        int inChamber = 0;
        int inCourt = 0;
        for (long seed = 1; seed <= 6; seed++) {
            var floor = firstFloor(seed);
            var keep = floor.keep();
            assertNotNull(keep, "the premise: seed " + seed + "'s first floor has its keep");
            var chamber = floor.rooms().get(keep.chamber());
            var levels = Spawner.levelsOf(floor, SETTINGS, 1);
            for (int i = 0; i < levels.length; i++) {
                var monster = floor.monsters().get(i);
                if (keep.isCourt(monster.at().cellX(), monster.at().cellY())) {
                    inCourt++;
                    assertEquals(8, levels[i], "seed " + seed + ": a " + monster.kind() + " in the court");
                } else if (inside(chamber, monster)) {
                    inChamber++;
                    assertTrue(levels[i] == 7 || levels[i] == 8,
                            "seed " + seed + ": a " + monster.kind() + " before the keep at " + levels[i]);
                }
            }
        }
        assertTrue(inChamber > 0, "the premise: some chamber before a keep held a monster");
        assertTrue(inCourt > 0, "the premise: some court held its guard");
    }

    /** One seed gives every monster the same level twice: the walk and the rule know no dice and no clock. */
    @Test
    void oneSeedGivesEveryMonsterTheSameLevelTwice() {
        assertArrayEquals(Spawner.levelsOf(firstFloor(7L), SETTINGS, 1), Spawner.levelsOf(firstFloor(7L), SETTINGS, 1));
    }

    /**
     * A stage has no keep: its way ends at its boss, and it is played at its Difficulty -- the first stage climbing
     * toward its boss from the way in, and the deep one at the cap throughout.
     */
    @Test
    void aStagesWayEndsAtItsBoss() {
        var stage = Stages.load("first", SETTINGS);
        var floor = stage.floor();
        var walk = StageCheck.walk(floor);
        int toTheBoss = walk.to(floor.boss().at().cellX(), floor.boss().at().cellY());
        var levels = Spawner.levelsOf(floor, SETTINGS, stage.difficulty());

        assertTrue(toTheBoss > 0, "the premise: the boss can be walked to");
        for (int i = 0; i < levels.length; i++) {
            int steps = stepsTo(walk, floor.monsters().get(i));
            assertEquals(SETTINGS.levelAlong(stage.difficulty(), steps, toTheBoss), levels[i],
                    "a " + floor.monsters().get(i).kind() + " " + steps + " steps in, of " + toTheBoss);
        }
        var deep = Stages.load("deep", SETTINGS);
        for (int level : Spawner.levelsOf(deep.floor(), SETTINGS, deep.difficulty())) {
            assertEquals(SETTINGS.maxMonsterLevel(), level, "the deep stage plays at the cap throughout");
        }
    }

    // ---- what a level gives ----

    private static final String MAGE = "SkeletonMage";
    private static final String HEALER = "SkeletonHealer";
    private static final String SUMMONER = "SkeletonSummoner";

    /** An open room forty cells by thirty, stone only round its edge. */
    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /** One {@code kind}, alone in a room with nobody to notice: as its block builds it, and no level put on it. */
    private static GameObject alone(String kind) {
        var arena = Dungeon.world(room(), SETTINGS);
        arena.game().spawn(kind, arena.dungeon(), 150f, 150f);
        arena.game().runHeadless(1);
        return creature(arena.game(), kind);
    }

    private static int worthOf(GameObject creature) {
        return creature.findModule(ExperienceModule.class).getExperienceValue();
    }

    /** A number the shipped block of {@code unit} writes once. */
    private static float shipped(String unit, String key) {
        return Float.parseFloat(ShippedBlock.of(unit).value(key));
    }

    /** The shipped units with the Rogue's bow reaching nothing: whatever he loses, the monster took. */
    private static String unarmedRogue() {
        var rogue = ShippedBlock.of("Rogue");
        return Content.units().replace(rogue.text(), rogue.with("AttackRange", 0).text());
    }

    /** The shipped files with {@code kind} noticing nobody: it does only what a test does for it. */
    private static DungeonSettings unseeing(String kind) {
        return DungeonSettings.parse(ShippedBlock.of(kind).with("SenseRadius", 1).with("ChaseRadius", 1)
                .with("AlertRadius", 0).text());
    }

    /** A level-1 monster is its block exactly: its own health, its own blow, its own worth. */
    @Test
    void aLevelOneMonsterIsItsBlockExactly() {
        var mage = alone(MAGE);
        float health = mage.getBody().getMaxHealth();
        int worth = worthOf(mage);

        Spawner.scale(mage, 1, SETTINGS);

        assertEquals(health, mage.getBody().getMaxHealth(), 0.001f, "its health");
        assertEquals(worth, worthOf(mage), "its worth");
        var bonus = mage.findModule(LevelBonus.class);
        assertEquals(1, bonus.level(), "and it carries its level, the first");
        assertEquals(1f, bonus.damageMultiplier(), 0.001f, "its blow");
    }

    /** At level 8 it has 1.7 times its health, all of it standing, hits 1.35 times as hard, and is worth 1.35 times. */
    @Test
    void atLevelEightItIsTougherHitsHarderAndIsWorthMore() {
        var mage = alone(MAGE);
        float health = mage.getBody().getMaxHealth();
        int worth = worthOf(mage);

        Spawner.scale(mage, 8, SETTINGS);

        assertEquals(health * 1.7f, mage.getBody().getMaxHealth(), 0.01f, "its health");
        assertEquals(mage.getBody().getMaxHealth(), mage.getBody().getHealth(), 0.01f, "all of it standing");
        assertEquals(Math.round(worth * 1.35f), worthOf(mage), "its worth");
        assertEquals(8, mage.findModule(LevelBonus.class).level());
        assertEquals(1.35f, mage.findModule(LevelBonus.class).damageMultiplier(), 0.001f, "its blow");
    }

    /** Its blow, landed for real: a level-8 skeleton's takes 1.35 times its block's off a Rogue who does not answer. */
    @Test
    void itsBlowLandsItsLevelsShareHarder() {
        var arena = Dungeon.world(room(), SETTINGS, unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 162f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var hero = creature(game, "Rogue");
        Spawner.scale(creature(game, "Skeleton"), 8, SETTINGS);
        float before = hero.getBody().getHealth();

        for (int frame = 0; frame < 90 && hero.getBody().getHealth() == before; frame++) {
            game.runHeadless(1);
        }

        assertEquals(shipped("Skeleton", "Damage") * 1.35f, before - hero.getBody().getHealth(), 0.01f);
    }

    /** Its skills' damage the same share: the fire mage's fireball, thrown by hand at level 1 and at level 8. */
    @Test
    void itsSkillsHitItsLevelsShareHarder() {
        float plain = aFireballFrom(1);

        assertTrue(plain > 0f, "the premise: the fireball reached him");
        assertEquals(plain * 1.35f, aFireballFrom(8), 0.01f);
    }

    /** What the fire mage's fireball, thrown by hand from {@code level}, takes off a Rogue it never noticed. */
    private static float aFireballFrom(int level) {
        var arena = Dungeon.world(room(), unseeing(MAGE), unarmedRogue());
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        game.spawn(MAGE, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        arena.orders().hold(arena.hero().getIndex(), true);
        var mage = creature(game, MAGE);
        var hero = creature(game, "Rogue");
        Spawner.scale(mage, level, SETTINGS);
        float before = hero.getBody().getHealth();

        assertTrue(mage.findModule(SkillBook.class).cast('Q', 1, null, hero.getPosition()), "the premise: it threw");
        game.runHeadless(30);
        return before - hero.getBody().getHealth();
    }

    /** And a mending's: the healer's light, called down by hand at level 1 and at level 8. */
    @Test
    void itsMendingMendsItsLevelsShareMore() {
        float plain = aMendingFrom(1);

        assertTrue(plain > 0f, "the premise: the light landed");
        assertEquals(plain * 1.35f, aMendingFrom(8), 0.01f);
    }

    /** What the healer's light, called down by hand from {@code level}, gives back to a skeleton left at 10. */
    private static float aMendingFrom(int level) {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(HEALER, arena.dungeon(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 180f, 150f);
        game.runHeadless(1);
        var healer = creature(game, HEALER);
        var patient = creature(game, "Skeleton");
        Spawner.scale(healer, level, SETTINGS);
        patient.getBody().setHealth(10f);

        assertTrue(healer.findModule(SkillBook.class).cast('Q', 1, patient.getId(), null), "the premise: it mended");
        game.runHeadless(40);
        return patient.getBody().getHealth() - 10f;
    }

    /**
     * What rises from a rift stands at its caller's level, read when the rift opens: its figures, and its share of
     * what its kind is worth at that level.
     */
    @Test
    void whatRisesStandsAtItsCallersLevel() {
        var settings = DungeonSettings.parse(ShippedBlock.of(SUMMONER).with("SummonExperiencePercent", 50).text());
        var arena = Dungeon.world(room(), settings);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var summoner = creature(game, SUMMONER);
        Spawner.scale(summoner, 8, SETTINGS);

        assertTrue(summoner.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        game.runHeadless(30);

        var risen = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertEquals(4, risen.size(), "the premise: two swordsmen and two archers rose");
        for (var one : risen) {
            var kind = one.getTemplate().name();
            assertEquals(8, one.findModule(LevelBonus.class).level(), kind + "'s level");
            assertEquals(1.35f, one.findModule(LevelBonus.class).damageMultiplier(), 0.001f, kind + "'s blow");
            assertEquals(shipped(kind, "MaxHealth") * 1.7f, one.getBody().getMaxHealth(), 0.01f, kind + "'s health");
            assertEquals(Math.round(shipped(kind, "ExperienceValue") * 1.35f) * 50 / 100, worthOf(one),
                    kind + "'s worth: half of what its kind is worth at 8");
        }
    }

    /** The card shows its own blow: its weapon's figure times its own level's share -- not the floor's. */
    @Test
    void theCardShowsItsOwnBlow() {
        var skeleton = alone("Skeleton");
        Spawner.scale(skeleton, 8, SETTINGS);

        var line = HeroStatus.creature(skeleton, 4, 4, SETTINGS, "", false);

        assertTrue(line.contains("|stat=" + SETTINGS.hud().attackWord() + ","
                + Math.round(shipped("Skeleton", "Damage") * 1.35f) + ","), line);
    }

    /** Placed for real: the first floor's boss stands at 10 with 1.9 times its health, and every monster at 1 to 8. */
    @Test
    void aPlacedFloorStandsAtItsLevels() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(1);
        var boss = SETTINGS.bossKindAt(1);

        int monsters = 0;
        for (var object : session.game().getLogic().getObjects()) {
            var name = object.getTemplate().name();
            if (SETTINGS.monster(name) == null) {
                continue; // a hero, a prop, a shot
            }
            var bonus = object.findModule(LevelBonus.class);
            assertNotNull(bonus, name + " was placed without its level");
            if (name.equals(boss)) {
                assertEquals(10, bonus.level(), "the boss");
                assertEquals(shipped(boss, "MaxHealth") * 1.9f, object.getBody().getMaxHealth(), 0.01f);
            } else {
                monsters++;
                assertTrue(bonus.level() >= 1 && bonus.level() <= 8, name + " at " + bonus.level());
            }
        }
        assertTrue(monsters > 0, "the premise: the floor holds monsters");
    }

    /** What a level gives replaces what a depth gave: a file still writing a per-depth line of it is refused. */
    @Test
    void aPerDepthLineOfHealthDamageOrExperienceIsRefused() {
        for (var gone : new String[] {"MonsterHealthPercentPerDepth", "MonsterDamagePercentPerDepth",
                "BossHealthPercentPerDepth", "BossDamagePercentPerDepth", "ExperiencePercentPerDepth"}) {
            var data = dataWith("    MonsterCountPercentPerDepth = 20\n",
                    "    MonsterCountPercentPerDepth = 20\n    " + gone + " = 25\n");

            var refused = assertThrows(DataException.class, () -> DungeonSettings.parse(data));
            assertTrue(refused.getMessage().contains(gone), refused.getMessage());
        }
    }

    // ---- on the bar ----

    /** The words a creature holds that begin as the bar's level word does. */
    private static List<String> levelWords(GameObject creature) {
        return creature.getConditions().stream()
                .filter(word -> word.startsWith(SETTINGS.unitBar().levelWord())).toList();
    }

    /** Every monster placed on a floor, and its boss, carries exactly one level word: its own. */
    @Test
    void everyPlacedMonsterWearsItsOwnLevel() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(1);

        int worn = 0;
        for (var object : session.game().getLogic().getObjects()) {
            if (SETTINGS.monster(object.getTemplate().name()) != null) {
                worn++;
                assertEquals(List.of(SETTINGS.unitBar().levelWord() + LevelBonus.levelOf(object)), levelWords(object),
                        object.getTemplate().name());
            }
        }
        assertTrue(worn > 1, "the premise: the floor holds its monsters and its boss");
    }

    /** And whatever rises from a rift wears its caller's. */
    @Test
    void whatRisesWearsItsCallersLevel() {
        var arena = Dungeon.world(room(), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), 200f, 150f);
        game.runHeadless(1);
        var summoner = creature(game, SUMMONER);
        Spawner.scale(summoner, 8, SETTINGS);

        assertTrue(summoner.findModule(SkillBook.class).cast('Q', 1, null, new Coord3D(150f, 150f, 0f)),
                "the premise: it opened its rifts");
        game.runHeadless(30);

        var risen = game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null).toList();
        assertFalse(risen.isEmpty(), "the premise: something rose");
        for (var one : risen) {
            assertEquals(List.of(SETTINGS.unitBar().levelWord() + 8), levelWords(one), one.getTemplate().name());
        }
    }
}
