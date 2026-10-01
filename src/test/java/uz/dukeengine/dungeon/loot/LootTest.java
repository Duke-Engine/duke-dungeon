package uz.dukeengine.dungeon.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * What a dead monster leaves, and what picking it up is worth.
 *
 * <p>Nothing here asserts a balance figure — which items exist and what they are
 * worth is written in {@code data/world/world.duke}, so a re-tune should move these tests
 * with it. What is held still is the mechanism: that the draw is the seed's and
 * not the clock's, that a monster's own draw does not depend on when it died,
 * that the floor decides what may be found, and that a run's findings die with it.
 */
class LootTest {

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    private static final List<Loot> DECK = List.of(
            new Loot("Blade", "Blade", "", LootKind.ATTACK, 10, 10, 1),
            new Loot("Plate", "Plate", "", LootKind.ARMOUR, 5, 10, 3));

    /** Always drops, so a test about what drops is not a test about whether. */
    private static LootTable always(long seed) {
        return new LootTable(DECK, seed, 100, 100, 0);
    }

    // ---- the draw ----

    @Test
    void theSameMonsterAlwaysLeavesTheSameThing() {
        var table = always(99L);
        var first = table.dropFor(41, 2, false);
        assertNotNull(first);
        assertEquals(first.id(), table.dropFor(41, 2, false).id(),
                "a seed is the whole run, the loot included");
    }

    /**
     * And it does not depend on the order the player killed things in.
     *
     * <p>A generator advanced once per death would be reproducible too, but only
     * for a player who took the same route — which would make a replay depend on
     * where he walked rather than on what he ordered.
     */
    @Test
    void theOrderOfDeathsDoesNotChangeTheDraw() {
        var table = always(99L);
        var alone = table.dropFor(41, 2, false);
        table.dropFor(7, 2, false);
        table.dropFor(8, 2, false);
        table.dropFor(9, 2, false);
        assertEquals(alone.id(), table.dropFor(41, 2, false).id());
    }

    @Test
    void differentMonstersLeaveDifferentThings() {
        var table = always(99L);
        boolean anyDifferent = false;
        for (int id = 1; id <= 40 && !anyDifferent; id++) {
            anyDifferent = !table.dropFor(id, 3, false).id().equals(table.dropFor(1, 3, false).id());
        }
        assertTrue(anyDifferent, "every monster leaving the same item is not a table");
    }

    @Test
    void mostDeathsLeaveNothing() {
        var stingy = new LootTable(DECK, 5L, 20, 100, 0);
        int dropped = 0;
        for (int id = 1; id <= 200; id++) {
            if (stingy.dropFor(id, 1, false) != null) {
                dropped++;
            }
        }
        // Twenty percent of two hundred, give or take the draw.
        assertTrue(dropped > 20 && dropped < 60, "dropped " + dropped + " of 200");
    }

    @Test
    void theBossAlwaysLeavesSomething() {
        var stingy = new LootTable(DECK, 5L, 0, 100, 0);
        assertNull(stingy.dropFor(3, 1, false), "nothing drops at zero percent");
        assertNotNull(stingy.dropFor(3, 1, true), "except from the thing guarding the way down");
    }

    @Test
    void aDeepItemIsNotFoundInTheFirstRooms() {
        var table = always(1234L);
        for (int id = 1; id <= 60; id++) {
            assertEquals("Blade", table.dropFor(id, 1, false).id(),
                    "Plate has MinDepth 3 and must not appear on the first floor");
        }
        boolean anyPlate = false;
        for (int id = 1; id <= 60 && !anyPlate; id++) {
            anyPlate = "Plate".equals(table.dropFor(id, 3, false).id());
        }
        assertTrue(anyPlate, "and must appear once the floor is deep enough");
    }

    @Test
    void whatIsFoundIsWorthMoreDeeperDown() {
        // One item, so the two depths are certainly comparing the same thing:
        // with a second in the deck a deeper floor could simply have drawn it.
        var growing = new LootTable(List.of(DECK.get(0)), 7L, 100, 100, 50);
        int shallow = growing.dropFor(11, 1, false).value();
        int deep = growing.dropFor(11, 3, false).value();
        assertTrue(deep > shallow, shallow + " -> " + deep);
        // Computed from the depth in one step: two floors at fifty percent is +100%.
        assertEquals(shallow * 2, deep);
    }

    // ---- what a bag comes to ----

    @Test
    void aBagAddsUpWhatIsInIt() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(1), 0, 10);

        assertEquals(20, bag.attackPercent());
        assertEquals(5, bag.armourPercent());
        assertEquals(0, bag.health());
        assertEquals(3, bag.getFound().size());
    }

    @Test
    void theMessageStopsBeingSaid() {
        var bag = new LootBag();
        bag.take(DECK.get(0), 100, 30);

        assertEquals("Blade", bag.noteAt(100));
        assertEquals("Blade", bag.noteAt(129));
        assertEquals("", bag.noteAt(130), "it has been said; the panel goes quiet again");
    }

    @Test
    void aBagHasRoomForSoManyAndNoMore() {
        var bag = new LootBag(2);
        assertTrue(bag.take(DECK.get(0), 0, 10));
        assertTrue(bag.take(DECK.get(1), 0, 10));

        assertTrue(bag.isFull());
        assertFalse(bag.take(DECK.get(0), 0, 10), "a full bag takes nothing more");
        assertEquals(2, bag.getFound().size());
    }

    @Test
    void whatIsTakenOutLeavesItsSlotForTheNext() {
        var bag = new LootBag(3);
        bag.take(DECK.get(0), 0, 10);
        bag.take(DECK.get(1), 0, 10);
        int before = bag.version();

        assertEquals(DECK.get(0), bag.remove(0));
        assertTrue(bag.version() > before, "a change the figures have to hear of");
        assertEquals(java.util.Arrays.asList(null, DECK.get(1), null), bag.slots());
        assertEquals(0, bag.attackPercent(), "and it no longer counts");

        bag.take(DECK.get(0), 0, 10);
        assertEquals(DECK.get(0), bag.slots().getFirst(), "the next goes into the first empty slot");
        assertNull(bag.remove(2), "an empty slot gives nothing");
        assertNull(bag.remove(9), "nor one the bag does not have");
    }

    // ---- three alike ----

    private static final Loot GAUNTLET = new Loot("Gauntlet", "Gauntlet", "", LootKind.ATTRIBUTE, 3, 10, 1,
            "Strength", LootExtra.HEALTH_REGEN, 0, 2, 1, ItemUse.NONE, "");

    @Test
    void threeAlikeJoinIntoOneOfTheNextLevel() {
        var bag = new LootBag(6, 3, 3);
        bag.take(GAUNTLET, 0, 10);
        bag.take(GAUNTLET, 0, 10);
        assertEquals(2, bag.getFound().size(), "two are two");

        bag.take(GAUNTLET, 0, 10);

        var joined = bag.getFound();
        assertEquals(1, joined.size(), "three are one");
        assertEquals(2, joined.getFirst().level());
        assertEquals(9, joined.getFirst().value(), "worth the three of them");
        assertEquals(2, joined.getFirst().extraValue(), "and the second level brings its extra");
        assertEquals(2, bag.healthRegen());
        assertEquals(joined.getFirst(), bag.slots().getFirst(), "in the first of their slots");
        assertEquals("Gauntlet II", bag.noteAt(0), "and the panel says what it became");
    }

    @Test
    void theThirdLevelIsThreeOfTheSecondAndNothingGoesHigher() {
        var bag = new LootBag(6, 3, 3);
        for (int i = 0; i < 9; i++) {
            bag.take(GAUNTLET, 0, 10);
        }
        assertEquals(1, bag.getFound().size(), "nine of the first level are one of the third");
        var top = bag.getFound().getFirst();
        assertEquals(3, top.level());
        assertEquals(27, top.value());
        assertEquals(6, top.extraValue(), "three of the second level's extra");

        for (int i = 0; i < 18; i++) {
            bag.take(GAUNTLET, 0, 10);
        }
        assertEquals(3, bag.getFound().size(), "three of the top level stay three");
        assertTrue(bag.getFound().stream().allMatch(item -> item.level() == 3));
    }

    @Test
    void aThirdThatJoinsNeedsNoRoomOfItsOwn() {
        var bag = new LootBag(3, 3, 3);
        bag.take(GAUNTLET, 0, 10);
        bag.take(GAUNTLET, 0, 10);
        bag.take(DECK.get(0), 0, 10);
        assertTrue(bag.isFull());

        assertTrue(bag.take(GAUNTLET, 0, 10), "the third joins as it comes in");
        assertEquals(2, bag.getFound().size());
        assertTrue(bag.take(DECK.get(1), 0, 10), "and the room it left is room for one more");
        assertFalse(bag.take(DECK.get(1), 0, 10), "but no more than that");
    }

    @Test
    void onlyTheSameThingAtTheSameLevelJoins() {
        var bag = new LootBag(6, 3, 3);
        bag.take(GAUNTLET, 0, 10);
        bag.take(GAUNTLET.joined(3), 0, 10);
        bag.take(GAUNTLET, 0, 10);
        bag.take(DECK.get(0), 0, 10);

        assertEquals(4, bag.getFound().size(), "two of the first level, one of the second and a blade join nothing");
    }

    /** What a thing does when used and what it lies as are its own: joining it or pricing it anew keeps both. */
    @Test
    void whatAThingDoesAndLiesAsSurvivesJoiningAndAnotherWorth() {
        var charm = new Loot("Charm", "Charm", "", LootKind.ATTACK, 10, 10, 1, "", LootExtra.NONE, 0, 0, 1,
                ItemUse.UNLOCK, "Key");

        assertEquals(ItemUse.UNLOCK, charm.joined(3).use(), "joined, it does what it did");
        assertEquals("Key", charm.joined(3).liesAs("Chest"), "and lies as what it lay as");
        assertEquals(ItemUse.UNLOCK, charm.worth(5).use(), "worth another figure, it does what it did");
        assertEquals("Key", charm.worth(5).liesAs("Chest"), "and lies as what it lay as");
    }

    // ---- in the game ----

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    /** Every death leaves something, so a test about what is left is not a test about whether. */
    private static final DungeonSettings GENEROUS = DungeonSettings.parse("""
            LootDrops
              Template = Chest
              DropPercent = 100
              BossDropPercent = 100
              PickupRange = 14
              ValuePercentPerDepth = 0
              NoteFrames = 90
            End
            """);

    /** A monster killed on a real floor, and the chest it left where it fell. */
    private static GameObject killOneFor(DukeGame game) {
        var victim = game.getLogic().getObjects().stream()
                .filter(object -> object.getPlayerIndex() != game.getLocalPlayerIndex())
                .filter(object -> object.getBody() != null)
                .findFirst().orElseThrow();
        game.getLogic().destroyObject(victim);
        game.runHeadless(2);
        var chest = find(game, "Chest");
        assertNotNull(chest, "a monster that always drops should have left something");
        return chest;
    }

    /**
     * A monster killed on a real floor leaves a chest, and walking over it takes nothing: he has to be sent.
     *
     * <p>Driven through the shipped game rather than a fixture, because what could go wrong is wiring: that a
     * monster carries the drop at all, and that the chest the data file describes holds what it dropped.
     */
    @Test
    void aChestIsLeftAndWalkingOverItTakesNothing() {
        var session = Dungeon.newSession(21L, GENEROUS);
        var game = session.game();
        game.runHeadless(1);
        var chest = killOneFor(game);
        var lying = chest.findModule(GroundItem.class);
        assertNotNull(lying);
        assertNotNull(lying.getHolding(), "and it should be holding something");

        // Placed rather than ordered: this is about standing on it, not about the pathfinder.
        find(game, "Rogue").setPosition(chest.getPosition());
        game.runHeadless(30);

        assertNotNull(find(game, "Chest"), "standing on it is not taking it");
        assertTrue(session.progress().getLoot().getFound().isEmpty());
    }

    /** And a click on it — the order the client sends — hands over what it held. */
    @Test
    void aClickOnTheChestHandsItOver() {
        var session = Dungeon.newSession(21L, GENEROUS);
        var game = session.game();
        game.runHeadless(1);
        var chest = killOneFor(game);
        var item = chest.findModule(GroundItem.class).getHolding();
        find(game, "Rogue").setPosition(chest.getPosition());

        game.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                new PickUp(game.getLocalPlayerIndex(), chest.getId())));
        game.runHeadless(3);

        assertNull(find(game, "Chest"), "the chest is gone the moment it is his");
        assertEquals(List.of(item.id()),
                session.progress().getLoot().getFound().stream().map(Loot::id).toList());
    }

    /** What he puts down lies on the floor, the dungeon's to keep until somebody picks it up. */
    @Test
    void whatHePutsDownLiesOnTheFloor() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var hero = find(game, "Rogue");
        var player = RtsPlayer.of(game.getLogic(), game.getLocalPlayerIndex());
        float plain = player.getWeaponDamageBonus();
        var blade = new Loot("Blade", "Blade", "", LootKind.ATTACK, 25, 10, 1);
        session.progress().getLoot().take(blade, 0, 30);
        game.runHeadless(2);
        assertTrue(player.getWeaponDamageBonus() > plain, "a sword in his bag is a sword in his hand");

        game.postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                new DropItem(game.getLocalPlayerIndex(), 0, hero.getPosition())));
        game.runHeadless(3);

        assertTrue(session.progress().getLoot().getFound().isEmpty(), "out of his bag");
        assertEquals(plain, player.getWeaponDamageBonus(), 0.0001f, "and out of his hand");
        var chest = find(game, "Chest");
        assertNotNull(chest, "and on the floor");
        assertEquals(blade, chest.findModule(GroundItem.class).getHolding());
        assertTrue(chest.getPlayerIndex() != game.getLocalPlayerIndex(), "nobody's hero's to select");
    }

    /**
     * ★ Putting a heart down and taking it up again heals nothing. What he carries is room in him, not health:
     * were a heart to bring the health it makes room for, that would be a heal any time he liked.
     */
    @Test
    void puttingAHeartDownAndTakingItUpAgainHealsNothing() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var body = find(game, "Rogue").getBody();
        var bag = session.progress().getLoot();
        var heart = new Loot("Heart", "Heart", "", LootKind.HEALTH, 90, 10, 1);
        float bare = body.getMaxHealth();

        bag.take(heart, 0, 30);
        game.runHeadless(2);
        assertEquals(bare + 90f, body.getMaxHealth(), 0.01f, "room for ninety more");
        body.setHealth(body.getMaxHealth() * 0.4f);
        float wounded = body.getHealth();

        bag.remove(0);
        game.runHeadless(2);
        assertEquals(bare, body.getMaxHealth(), 0.01f, "the room goes with it");
        bag.take(heart, 0, 30);
        game.runHeadless(2);

        assertEquals(bare + 90f, body.getMaxHealth(), 0.01f, "and comes back with it");
        assertEquals(wounded, body.getHealth(), 1f, "and none of it came back filled");
    }

    @Test
    void whatHeFoundIsFeltAndThenLostWithTheRun() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(1);
        var player = RtsPlayer.of(game.getLogic(), game.getLocalPlayerIndex());
        float plain = player.getWeaponDamageBonus();

        session.progress().getLoot().take(
                new Loot("Blade", "Blade", "", LootKind.ATTACK, 25, 10, 1), 0, 30);
        game.runHeadless(2);
        assertTrue(player.getWeaponDamageBonus() > plain,
                "a sword he found should reach the arrow he looses");

        game.getLogic().destroyObject(find(game, "Rogue"));
        game.runHeadless(DungeonSettings.load().run().respawnDelayFrames() + 4);
        assertTrue(session.progress().getLoot().getFound().isEmpty(),
                "a new run starts with nothing, what he found included");
        assertEquals(plain, player.getWeaponDamageBonus(), 0.0001f,
                "and the bonus goes with it rather than outliving him");
    }

    private static Loot shipped(String id) {
        return SHIPPED.loot().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    /**
     * What a joined thing brings beside its figure reaches him: health and mana coming back quicker, and quicker
     * blows — and goes again with it.
     */
    @Test
    void whatAJoinedThingBringsBesideItsFigureReachesHim() {
        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var recovery = hero.findModule(uz.dukeengine.dungeon.level.Recovery.class);
        var book = hero.findModule(uz.dukeengine.dungeon.skill.SkillBook.class);
        int healthWas = recovery.getRate();
        int manaWas = book.getManaRegen();
        var bag = session.progress().getLoot();

        bag.take(shipped("Gauntlet").joined(3), 0, 30);
        bag.take(shipped("Tome").joined(3), 0, 30);
        bag.take(shipped("Boots").joined(3), 0, 30);
        game.runHeadless(2);

        assertEquals(healthWas + 20, recovery.getRate(), "two points of health a second more, in tenths");
        assertEquals(manaWas + 20, book.getManaRegen(), "and two of mana");
        var quick = hero.findModule(uz.dukeengine.dungeon.level.AttackSpeed.class);
        assertNotNull(quick, "and his blows made quicker");
        assertEquals(1.2f, quick.rateOfFireMultiplier(), 0.0001f, "by a fifth");

        bag.clear();
        game.runHeadless(2);
        assertEquals(healthWas, recovery.getRate(), "and all of it goes with them");
        assertEquals(manaWas, book.getManaRegen());
        assertEquals(1f, quick.rateOfFireMultiplier(), 0.0001f);
    }

    /** What the dungeon leaves at random: the hero's three attributes, three points each, each with an extra to come. */
    @Test
    void theDungeonLeavesTheThreeAttributes() {
        var found = SHIPPED.loot().stream().filter(item -> item.weight() > 0).toList();
        assertEquals(List.of("Gauntlet", "Boots", "Tome"), found.stream().map(Loot::id).toList());
        for (var item : found) {
            assertEquals(LootKind.ATTRIBUTE, item.kind(), item.id());
            assertEquals(3, item.value(), item.id());
            assertTrue(item.extra() != LootExtra.NONE && item.extraStep() > 0, item.id() + " has an extra to come");
        }
        assertEquals(0, SHIPPED.lootDrops().valuePercentPerDepth(), "alike things have to stay alike to join");
    }

    @Test
    void theFileDecidesWhatCanBeFound() {
        assertFalse(SHIPPED.loot().isEmpty(), "the shipped game leaves something behind");
        assertEquals("Chest", SHIPPED.lootDrops().template());
        for (var item : SHIPPED.loot()) {
            assertFalse(item.name().isBlank(), item.id() + " has nothing to say for itself");
            assertTrue(item.value() > 0 || item.kind() == LootKind.KEY, item.id() + " is worth nothing");
        }
    }

    private static Loot key() {
        return SHIPPED.loot().stream().filter(item -> item.kind() == LootKind.KEY).findFirst().orElseThrow();
    }

    /** The shipped key: given, never found, used on a gate, lying as a key rather than in a chest. */
    @Test
    void theKeyIsAThingThatMayBeUsed() {
        var key = key();
        assertEquals("Key", key.id());
        assertEquals(ItemUse.UNLOCK, key.use());
        assertEquals("Key", key.liesAs("Chest"), "it lies as itself");
        assertEquals("Chest", SHIPPED.loot().getFirst().liesAs("Chest"), "and everything else in a chest");
        assertEquals(ItemUse.NONE, SHIPPED.loot().getFirst().use(), "which only counts while it is carried");
    }

    /** Carrying it changes nothing about him: the same health, the same blow, nothing coming back quicker. */
    @Test
    void theKeyGivesNothing() {
        var bag = new LootBag();
        bag.take(key(), 0, 0);
        assertEquals(0, bag.attackPercent() + bag.health() + bag.mana() + bag.armourPercent() + bag.healthRegen()
                + bag.manaRegen() + bag.attackSpeedPercent(), "a figure from a key");
        assertTrue(bag.holds(LootKind.KEY));

        var session = Dungeon.newSession(21L);
        var game = session.game();
        game.runHeadless(2);
        var hero = find(game, "Rogue");
        var player = RtsPlayer.of(game.getLogic(), game.getLocalPlayerIndex());
        float health = hero.getBody().getMaxHealth();
        float blow = player.getWeaponDamageBonus();
        session.progress().getLoot().take(key(), 0, 30);
        game.runHeadless(2);
        assertEquals(health, hero.getBody().getMaxHealth(), 0.0001f, "his health");
        assertEquals(blow, player.getWeaponDamageBonus(), 0.0001f, "his blow");
    }

    /** Three keys are three keys: a key never joins into a higher one. */
    @Test
    void keysNeverJoin() {
        var bag = new LootBag();
        for (int n = 0; n < LootBag.JOIN; n++) {
            assertTrue(bag.take(key(), 0, 0));
        }
        assertEquals(List.of(key(), key(), key()), bag.getFound());
    }

    /** Nothing leaves one at random, however often things drop. */
    @Test
    void theKeyIsNeverDrawnAsADrop() {
        assertEquals(0, key().weight());
        var table = new LootTable(SHIPPED.loot(), 7L, 100, 100, 0);
        for (int id = 0; id < 2000; id++) {
            var drop = table.dropFor(id, 1 + id % 4, id % 10 == 0);
            assertNotNull(drop);
            assertTrue(drop.kind() != LootKind.KEY, "monster " + id + " dropped the key");
        }
    }

    /** And a file that would let one drop at random is refused: a key is given, never found. */
    @Test
    void aKeyThatWouldDropIsRefused() {
        var shipped = "  Kind = KEY\n  Use = UNLOCK\n  LiesAs = Key\n  Value = 0\n  Weight = 0\n";
        var data = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(data.contains(shipped), "the shipped key is no longer written this way");

        var refused = assertThrows(IllegalArgumentException.class,
                () -> DungeonSettings.parse(data.replace(shipped, shipped.replace("Weight = 0", "Weight = 5"))));
        assertTrue(refused.getMessage().contains("LootItem Key") && refused.getMessage().contains("Weight"),
                refused.getMessage());
    }

    /** What he says of a thing the key does nothing to goes down the status line, which splits on ',' and '|'. */
    @Test
    void aNoUseWordTheStatusLineCouldNotCarryIsRefused() {
        var shipped = "  NoUseWord = Bu kalit faqat boss darvozasini ochadi\n";
        var data = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(data.contains(shipped), "the shipped word is no longer written this way");

        for (var word : new String[] {"Bu kalit, faqat darvozani ochadi", "Bu kalit | faqat darvozani ochadi"}) {
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> DungeonSettings.parse(data.replace(shipped, "  NoUseWord = " + word + "\n")));
            assertTrue(refused.getMessage().contains("NoUseWord"), refused.getMessage());
        }
    }

    /** And what he says when he gives up on getting to a thing goes down the same line. */
    @Test
    void aNoWayWordTheStatusLineCouldNotCarryIsRefused() {
        var shipped = "  NoWayWord = U yerga yetib bora olmadim\n";
        var data = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(data.contains(shipped), "the shipped word is no longer written this way");

        for (var word : new String[] {"U yerga, yetib bora olmadim", "U yerga | yetib bora olmadim"}) {
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> DungeonSettings.parse(data.replace(shipped, "  NoWayWord = " + word + "\n")));
            assertTrue(refused.getMessage().contains("NoWayWord"), refused.getMessage());
        }
    }

    /**
     * How long he may get no nearer before he gives an errand up is at least a frame, and the count that takes his
     * fighting in too is at least as long: either way round, the file is refused and says which.
     */
    @Test
    void aStuckLimitThatCannotHoldIsRefused() {
        var data = uz.dukeengine.dungeon.content.Content.data();
        assertTrue(data.matches("(?s).*\\n  StuckFrames = \\d+\\n.*")
                        && data.matches("(?s).*\\n  StuckFightingFrames = \\d+\\n.*"),
                "the shipped limits are no longer written this way");

        var none = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(
                data.replaceFirst("\\n  StuckFrames = \\d+\\n", "\n  StuckFrames = 0\n")));
        assertTrue(none.getMessage().contains("StuckFrames"), none.getMessage());
        var shorter = assertThrows(IllegalArgumentException.class, () -> DungeonSettings.parse(
                data.replaceFirst("\\n  StuckFightingFrames = \\d+\\n", "\n  StuckFightingFrames = 1\n")));
        assertTrue(shorter.getMessage().contains("StuckFightingFrames"), shorter.getMessage());
    }
}
