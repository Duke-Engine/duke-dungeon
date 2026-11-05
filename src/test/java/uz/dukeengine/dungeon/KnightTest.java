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

    // ---- who actually walks into the dungeon ----

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
        assertEquals(SETTINGS.bookFor(KNIGHT), him.findModule(SkillBook.class).getSkills(),
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
                  Model = models/characters/heroes/rogue.glb
                  Held = [
                    Held
                      Model = models/items/gear/bow.gltf
                      Bone = handslot.l
                      Scale = 2
                      Roll = 180
                    End
                  ]
                End
                """).heroNamed("Solo").held();

        assertEquals(1, one.size());
        assertEquals("models/items/gear/bow.gltf", one.get(0).model());
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
                  Model = models/characters/heroes/rogue.glb
                  Held = [
                    Held
                      Model = models/items/gear/quiver.gltf
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
