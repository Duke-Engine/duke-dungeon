package uz.dukeengine.dungeon.run;

import uz.dukeengine.rpg.item.Stat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * The line the hero's panel is drawn from, written by a real run.
 *
 * <p>Its reader is {@code uz.dukeengine.client3d.rpg.HeroPanel}, which this module cannot
 * see and which cannot see this. Nothing but a test connects them, so this holds
 * the writing end of the format and {@code HeroPanelTest} holds the reading end.
 *
 * <p>What is checked is the shape and where each part came from — that the name
 * is the one in the creature file, the words are the ones in {@code data/world/hud.duke},
 * and there is a slot for every skill the file gives him. Not the values: the
 * hero's health and the length of his cooldowns are balance, and balance is meant
 * to be tuned without a test objecting.
 */
class HeroStatusTest {

    /**
     * One frame of a real dungeon with the hero picked out, which is what the
     * player does before he reads anything about him.
     *
     * <p>Selecting him first changes nothing now -- the bar is his whatever is
     * selected -- and is kept as the player's own first move. See
     * {@link #withNothingSelectedTheBarIsHis}.
     */
    private static String lineFrom(long seed) {
        var session = Dungeon.newSession(seed);
        var game = session.game();
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Rogue"))
                .findFirst().orElseThrow();
        game.runHeadless(1);
        return game.getSnapshot().status();
    }

    /**
     * With nothing selected the bar is still his: a single-hero game's panel is the hero's, whatever is picked.
     *
     * <p>Which is the first thing a run ever says: nothing is selected when one
     * begins. So the line has to carry what belongs to the screen as well as him
     * -- and in particular the floor's LOOK, because this is the line the client
     * learns which stone to build the first floor out of from.
     */
    @Test
    void withNothingSelectedTheBarIsHis() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(1);
        var line = session.game().getSnapshot().status();

        assertTrue(line.startsWith("name=Erika"), "nothing is selected, and the bar is his: " + line);
        assertTrue(line.contains("|hp="), "his health: " + line);
        assertTrue(line.contains("|hero="), "the premise: his hero stands: " + line);
        assertTrue(line.contains("|depth="), "the floor is still the floor: " + line);
        assertTrue(line.contains("|look="),
                "and the client learns the floor's stone from this line: " + line);
    }

    @Test
    void aRunDescribesItsHero() {
        var line = lineFrom(4321L);

        assertTrue(line.startsWith("name="), "the panel reads nothing else: " + line);
        for (var field : new String[] {"rank=", "hp=", "xp=", "depth=", "depthWord="}) {
            assertTrue(line.contains("|" + field) || line.startsWith(field),
                    field + " missing from " + line);
        }
    }

    /**
     * The name is the one the file gives him, not the one the code calls him.
     *
     * <p>He is the {@code Hero} template everywhere in the game — the spawner, the
     * skill blocks, every test above this one — and {@code DisplayName} is the only
     * place that says what the player should call him.
     */
    @Test
    void heIsCalledWhateverTheCreatureFileCallsHim() {
        assertTrue(Content.units().contains("DisplayName = Erika"),
                "the shipped hero should have a name of his own");
        assertTrue(lineFrom(11L).startsWith("name=Erika"), lineFrom(11L));
        assertFalse(lineFrom(11L).startsWith("name=Hero"),
                "that is his template, not his name");
    }

    /**
     * Depth is a numeral, being the one number in the game that only goes up.
     *
     * <p>And anything a numeral cannot say comes back as a digit, because a wrong
     * numeral on the screen is worse than a plain number.
     */
    @Test
    void depthIsRoman() {
        assertEquals("I", HeroStatus.roman(1));
        assertEquals("IV", HeroStatus.roman(4));
        assertEquals("IX", HeroStatus.roman(9));
        assertEquals("XLII", HeroStatus.roman(42));
        assertEquals("MMMCMXCIX", HeroStatus.roman(3999));
        assertEquals("0", HeroStatus.roman(0));
        assertEquals("4000", HeroStatus.roman(4000));
    }

    // ---- what a levelling run adds to the line ----

    /**
     * The figures beside his attributes come from the creature file and his level, and
     * are named by {@code data/world/hud.duke}. His speed is not one of them any more: it is what
     * one of his attributes became, and it is read on that attribute's card.
     */
    @Test
    void theFiguresBesideHisAttributesAreThere() {
        var settings = DungeonSettings.load();
        var line = lineFrom(4321L);

        for (var word : new String[] {settings.hud().attackWord(), settings.hud().armourWord()}) {
            assertFalse(word.isBlank(), "the shipped file should name its own figures");
            assertTrue(line.contains("|stat=" + word + ","), word + " missing from " + line);
        }
        assertFalse(line.contains("|stat=" + settings.hud().speedWord() + ","),
                "his speed is what his agility became, not a figure beside it: " + line);
        assertFalse(settings.hud().speedNowWord().isBlank(), "the shipped file should name it");
        assertTrue(line.contains("|atTipFoot=" + settings.attributeRules().indexOf("AGI") + ","
                        + settings.hud().speedNowWord() + " "),
                "it is said on agility's card instead: " + line);
    }

    /**
     * His attributes ride the line as a field of their own, in the file's order, his
     * primary marked, and each with the card that says what a point of it is worth.
     */
    @Test
    void hisAttributesAreOnTheLineWithHisPrimaryMarked() {
        var settings = DungeonSettings.load();
        var line = lineFrom(4321L);
        var art = settings.attributeArt();
        var rules = settings.attributeRules();
        int strength = rules.indexOf("STR");
        int agility = rules.indexOf("AGI");

        int after = -1;
        for (var shown : art) {
            assertFalse(shown.word().isBlank(), "the shipped file should name its attributes");
            int here = line.indexOf("|attr=" + shown.word() + ",");
            assertTrue(here > after, shown.word() + " missing, or out of the file's order: " + line);
            after = here;
        }
        assertEquals(1, line.split(java.util.regex.Pattern.quote(",primary"), -1).length - 1,
                "exactly one of them is his primary: " + line);
        assertTrue(line.contains("|attr=" + art.get(agility).word() + ",12,,"
                        + art.get(agility).icon() + ",primary"),
                "the archer's is agility, twelve of it at the first level: " + line);
        assertTrue(line.contains("|atTipName=" + strength + "," + art.get(strength).word()),
                "a card over strength: " + line);
        assertTrue(line.contains("|atTipRow=" + strength + "," + settings.hud().healthWord() + ",+12,"),
                "which says a point of it is twelve health: " + line);
        assertTrue(line.contains("|atTipRow=" + agility + "," + settings.hud().attackWord() + ",+1,"),
                "and agility's says it is his arrow as well: " + line);
        assertFalse(line.contains("|atTipRow=" + strength + "," + settings.hud().attackWord() + ","),
                "strength is not the archer's arrow: " + line);
        assertFalse(line.contains("|atTipRow=" + strength + "," + settings.hud().speedWord() + ","),
                "and a figure a point of it does not move gets no row: " + line);
    }

    /** What he picks up of an attribute is green beside the attribute. */
    @Test
    void anAttributeHeFindsIsGreenBesideTheAttribute() {
        var session = Dungeon.newSession(11L);
        var game = session.game();
        game.runHeadless(2);
        session.progress().getLoot().take(new uz.dukeengine.rpg.item.Loot("Tome", "Tome", "flask",
                Stat.ATTRIBUTE, 5, 1, 1, "STR", "", 0, 0, 1, uz.dukeengine.rpg.item.ItemUse.NONE, "", "Zirh"), // worn
                game.getLogic().getFrame(), 60);
        game.runHeadless(2);

        var settings = DungeonSettings.load();
        var strength = settings.attributeArt().get(settings.attributeRules().indexOf("STR")).word();
        var line = game.getSnapshot().status();
        assertTrue(line.contains("|attr=" + strength + ",15,+5,"),
                "ten of his own and five he found: " + line);
    }

    /**
     * An attribute the file adds rides the line with its card, and no Java was touched.
     *
     * <p>The writing half of that promise: vigour is invented in text, the archer is given
     * some, and the line carries it after the shipped three with its own word, its own
     * picture and a row for the one figure a point of it moves. {@code HeroPanelTest}
     * holds the reading half.
     */
    @Test
    void anAttributeTheFileAddsRidesTheLineWithItsCard() {
        var block = """
                Attribute
                  Name = Vigour
                  ShortName = VIG
                  Word = Quvvat
                  Icon = icons/stats/stat_vigour.png
                  HealthPerPoint = 3
                End
                """;
        var rogueAgility = "    Attribute\n      Name = AGI\n      Base = 12\n      Growth = 0.6\n    End,\n";
        var data = Content.data().replace(rogueAgility, rogueAgility
                + "    Attribute\n      Name = VIG\n      Base = 7\n      Growth = 0.5\n    End,\n");
        var settings = DungeonSettings.parse(data + block);
        var session = Dungeon.newSession(4321L, settings);
        var game = session.game();
        game.runHeadless(1);
        game.runHeadless(1);
        var line = game.getSnapshot().status();

        int vigour = line.indexOf("|attr=Quvvat,7,,icons/stats/stat_vigour.png");
        assertTrue(vigour > line.indexOf("|attr=" + settings.attributeArt().get(2).word() + ","),
                "vigour, after the shipped three: " + line);
        assertTrue(line.contains("|atTipName=3,Quvvat"), "with a card of its own: " + line);
        assertTrue(line.contains("|atTipRow=3," + settings.hud().healthWord() + ",+3,"),
                "saying what a point of it is worth: " + line);
    }

    /**
     * Everything the design asks for is on the line the game really sends.
     *
     * <p>The panel and this class are two halves of one format that no compiler
     * checks, so every field the design added is worth naming: a heading the game
     * forgets is a heading the panel draws as an empty gold wash, which looks
     * deliberate and is not.
     */
    @Test
    void theLineCarriesEveryPartOfTheDesign() {
        // The shipped settings, whose four orders have their buttons.
        var classic = uz.dukeengine.dungeon.content.DungeonSettings.load();
        var session = Dungeon.newSession(7L, classic);
        var game = session.game();
        game.runHeadless(2);
        game.runHeadless(30);
        var line = game.getSnapshot().status();

        assertTrue(line.contains("|title="), "what he is, under his name: " + line);
        assertTrue(line.contains("|itWord="), "the heading over his bag: " + line);
    }

    /**
     * A thing picked up shows in his bag and in green under the bars.
     *
     * <p>Two fields from one event, and the pair is the point: the socket says he
     * has it and the green says what it was worth. Neither is worth much alone.
     */
    @Test
    void whatHeFindsReachesTheBagAndTheFigures() {
        var session = Dungeon.newSession(11L);
        var game = session.game();
        game.runHeadless(2);
        game.runHeadless(1);
        var before = game.getSnapshot().status();
        assertFalse(before.contains("|it="), "he starts with nothing: " + before);

        var settings = DungeonSettings.load();
        var blade = settings.loot().stream()
                .filter(item -> Stat.ATTRIBUTE.equals(item.kind()))
                .findFirst().orElseThrow();
        session.progress().getLoot().take(blade, game.getLogic().getFrame(), 60);
        game.runHeadless(2);

        var line = game.getSnapshot().status();
        assertTrue(line.contains("|it=" + blade.icon() + ",1"),
                blade.id() + " should be in his bag: " + line);
        assertTrue(line.contains(",+"), "and what it is worth should be in green: " + line);
    }

    // ---- the floor's own half, which is not about whatever is selected ----

    /**
     * The bars over everybody's heads are fed whatever is under the thumb.
     *
     * <p>Three cards can be sent — the hero's, somebody else's creature, and the
     * empty one — and the floor's fields have to ride all three, because the bars
     * are drawn over every creature in sight whether the player has clicked on
     * anything or not. The first version appended them inside one branch and the
     * bars went out whenever a skeleton was selected.
     */
    @Test
    void theFloorsOwnFieldsRideEveryCardThereIs() {
        var session = Dungeon.newSession(4321L);
        var game = session.game();
        game.runHeadless(2);

        var nothingSelected = game.getSnapshot().status();
        game.runHeadless(2);
        var heroSelected = game.getSnapshot().status();
        game.runHeadless(2);
        var monsterSelected = game.getSnapshot().status();

        for (var line : new String[] {nothingSelected, heroSelected, monsterSelected}) {
            assertTrue(line.contains("|deep="), "no floor on: " + line);
            assertTrue(line.contains("|hero="), "no hero on: " + line);
            assertTrue(line.contains("|boss="), "no boss on: " + line);
        }
    }

    /**
     * The depth on the line is the number, not the numeral.
     *
     * <p>{@code depth=} is already there and is {@code III / IV} — finished words
     * for the corner of the panel, which is the rule for everything the client
     * draws as lettering. The medallion over a creature holding no level of its
     * own needs it as a figure it can count with.
     */
    @Test
    void theFloorIsSentAsAFigureAsWellAsAsWords() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(2);

        var line = session.game().getSnapshot().status();
        assertTrue(line.contains("|deep=1"), line);
        assertTrue(line.contains("|depth=I / IV"), "and the words are still there: " + line);
    }

    /**
     * His pool rides the floor's half, so the bar under him is drawn while
     * somebody else is selected.
     *
     * <p>Separate from the {@code mana=} on his own card, which is the panel's and
     * stays whatever is selected. The two carry the same figures and
     * answer different questions: one is the bar at the bottom of the screen, the
     * other is the thin bar over his head in the middle of a fight.
     */
    @Test
    void hisPoolIsOnTheLineWhileSomethingElseIsSelected() {
        var session = Dungeon.newSession(4321L);
        var game = session.game();
        game.runHeadless(2);
        game.runHeadless(2);

        var line = game.getSnapshot().status();
        assertTrue(line.contains("|mana="), "the card is his, pool and all, whatever is picked: " + line);
        var hero = line.substring(line.indexOf("|hero=") + 6);
        hero = hero.contains("|") ? hero.substring(0, hero.indexOf('|')) : hero;
        assertEquals(6, hero.split(",").length, "id, level, mana, pool, xp, needed: " + hero);
        assertTrue(Integer.parseInt(hero.split(",")[3]) > 0, "he holds a pool: " + hero);
    }

    /**
     * A printed name is sent once per KIND, and only when it says something the
     * template name does not.
     *
     * <p>Both halves matter and both are about the size of the line. Per kind
     * rather than per creature, so a floor of forty skeletons costs one entry;
     * and skipped when the two agree, which is nearly every monster in the file —
     * the client falls back to the template name and gets the same word.
     */
    @Test
    void aPrintedNameIsSentPerKindAndOnlyWhenItAddsSomething() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(2);

        var line = session.game().getSnapshot().status();
        assertTrue(line.contains("|who=Rogue,Erika"), line);
        assertTrue(line.contains("|who=Runner,Chopqir"), "named in Uzbek, not by the template: " + line);
        assertTrue(line.contains("|who=SkeletonMage,Skelet sehrgar"),
                "a monster whose name is two words says so: " + line);
        var settings = DungeonSettings.load();
        var templates = session.game().getLogic().getThingFactory();
        long renaming = java.util.stream.Stream.concat(
                        settings.heroes().stream().map(uz.dukeengine.dungeon.content.HeroLook::name),
                        settings.monsters().stream().map(uz.dukeengine.dungeon.content.MonsterKind::name))
                .map(templates::findTemplate)
                .filter(template -> template != null
                        && !uz.dukeengine.core.thing.Titled.of(template).equals(template.name()))
                .count();
        assertEquals(renaming, line.split("\\|who=", -1).length - 1,
                "one entry per kind that renames itself, and none for one that does not");
    }

    /**
     * The playtest of 1.0.41: "Skeleton", "Runner" over the monsters. Every creature the file describes is named in
     * the game's own words, so no bar falls back to an English template name.
     */
    @Test
    void everyCreatureHasANameOfItsOwn() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(2);
        var settings = DungeonSettings.load();
        var templates = session.game().getLogic().getThingFactory();
        var unnamed = java.util.stream.Stream.concat(
                        settings.heroes().stream().map(uz.dukeengine.dungeon.content.HeroLook::name),
                        settings.monsters().stream().map(uz.dukeengine.dungeon.content.MonsterKind::name))
                .map(templates::findTemplate)
                .filter(template -> template != null
                        && uz.dukeengine.core.thing.Titled.of(template).equals(template.name()))
                .map(uz.dukeengine.core.thing.ThingTemplate::name)
                .toList();
        assertTrue(unnamed.isEmpty(), "named only by their templates: " + unnamed);
    }
}
