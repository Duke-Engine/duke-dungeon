package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;
import uz.dukeengine.dungeon.gen.DungeonGenerator;

/**
 * A theme is two things, and only one of them is a look.
 *
 * <p>Its {@code Terrain} is the ground — a wood is open and rolling, a cellar close
 * and cut — so which theme a depth wears decides what its floor is shaped like.
 * Everything else a theme says is dressing, and that half keeps the promise the
 * whole idea rests on: dressed differently, a floor is the same fight, the same
 * chambers in the same places, the same creatures with the same numbers. If the
 * look moved the world by so much as one draw, then two players down one seed
 * would be playing two different games and every claim this project makes about
 * reproducibility would be worth nothing.
 *
 * <p>So the look is held to a checksum: the same seed played twice under two looks,
 * bit-identical — the same shape as the test that holds fog out of the simulation,
 * and for the same reason. The ground is held to the floor it draws.
 */
class DungeonThemeTest {

    /**
     * The shipped files with the endless map's theme order rewritten, and its biomes taken out — the order is what
     * dresses a floor that wears one theme whole, and a floor that mixes biomes never asks it.
     */
    private static DungeonSettings withOrder(String order) {
        var themes = "[" + String.join(", ", order.split(" ")) + "]";
        return DungeonSettings.parse(wholeFloors(ShippedBlock.dataWith("Endless", "Themes", themes)));
    }

    /** {@code data} with the descent's biomes taken out, so each floor wears its depth's theme whole. */
    private static String wholeFloors(String data) {
        return data.replaceFirst("(?m)^  Biomes = .*\\n", "");
    }

    /**
     * A fixed run of one world, reduced to what the simulation ended up as.
     *
     * <p>Deep enough to cross several floors, because the theme changes with the
     * depth and a run that never left the first one would prove nothing.
     */
    private static String playedOut(DungeonSettings settings) {
        var session = Dungeon.newSession(20250910L, settings);
        var game = session.game();
        game.runHeadless(1);
        var signature = new StringBuilder();
        for (int step = 0; step < 12; step++) {
            game.runHeadless(120);
            // Read the view every step, exactly as the client does — looking at
            // the game must not disturb it either.
            game.getSnapshot();
            signature.append(game.getLogic().getObjectCount()).append(':')
                    .append(game.getLogic().checksum()).append('|');
        }
        return signature.toString();
    }

    /** The shipped files with {@code from} rewritten as {@code to}, which has to be there to rewrite. */
    private static String rewritten(String data, String from, String to) {
        assertTrue(data.contains(from), "the shipped files no longer say " + from);
        return data.replace(from, to);
    }

    /** Paint the shipped themes another colour, build them of other pieces: the same game happens either way. */
    @Test
    void whatAFloorLooksLikeChangesNothingThatHappens() {
        var shipped = uz.dukeengine.dungeon.content.Content.data();
        var repainted = rewritten(shipped, "FogTint = 0x060B06", "FogTint = 0x0000FF");
        repainted = rewritten(repainted, "FogTint = 0x07060A", "FogTint = 0xFF0000");
        repainted = rewritten(repainted, "Wall = models/tiles/forest/tree.gltf", "Wall = models/tiles/forest/tree_round.gltf");
        repainted = rewritten(repainted, "Floor = models/tiles/dungeon/floor.gltf",
                "Floor = models/tiles/dungeon/floor_rocks.gltf");

        var asShipped = playedOut(DungeonSettings.parse(shipped));
        var asRepainted = playedOut(DungeonSettings.parse(repainted));

        assertNotEquals("", asShipped, "something has to have happened for this to say anything");
        assertEquals(asShipped, asRepainted,
                "the run depended on what it was made of, which makes it a rule and not a look");
    }

    /**
     * A theme that says nothing of its ground is only a look: its floors are the ones no theme at all would give,
     * at every depth the order reaches.
     */
    @Test
    void aThemeWithNoTerrainOfItsOwnIsOnlyALook() {
        var bare = java.util.regex.Pattern.compile("(?s)  Terrain = Terrain\n.*?\n  End\n")
                .matcher(wholeFloors(uz.dukeengine.dungeon.content.Content.data())).replaceAll("");
        assertFalse(bare.contains("Terrain = Terrain"), "a theme still carries its ground");
        var themed = DungeonSettings.parse(bare);
        var unthemed = DungeonSettings.parse(wholeFloors(ShippedBlock.dataWith("Endless", "Themes", "[]")).replaceAll(
                "(?s)  Terrain = Terrain\n.*?\n  End\n", ""));

        for (int depth = 1; depth <= 4; depth++) {
            assertNotNull(themed.themes().pick(9L, depth), "depth " + depth + " should still wear a look");
            assertEquals(DungeonGenerator.generate(9L, unthemed, depth), DungeonGenerator.generate(9L, themed, depth),
                    "depth " + depth);
        }
    }

    /** And a theme that does is its floors' ground: the wood and the cellar are not one floor dressed twice. */
    @Test
    void aThemesTerrainIsTheGroundItsFloorsAreCarvedInto() {
        var wood = withOrder("Forest");
        var cellar = withOrder("Dungeon");
        int differ = 0;
        for (long seed = 0; seed < 10; seed++) {
            var inTheWood = DungeonGenerator.generate(seed, wood, 1);
            var inTheCellar = DungeonGenerator.generate(seed, cellar, 1);
            assertEquals(inTheWood.rooms(), inTheCellar.rooms(), "seed " + seed + ": the chambers stand where they stood");
            if (!inTheWood.asciiMap().equals(inTheCellar.asciiMap())) {
                differ++;
            }
        }
        assertEquals(10, differ, "every floor should be cut to its own theme's ground");
    }

    // ---- what the shipped file actually describes ----

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    /** Every theme the order names is a theme the file describes. */
    @Test
    void everyThemeInTheOrderExists() {
        var themes = SHIPPED.themes();

        assertTrue(themes.all().size() >= 2, "there should be more than one way to look");
        for (var name : themes.order()) {
            assertNotNull(themes.themeNamed(name),
                    "the order names " + name + ", which nothing describes");
        }
    }

    /** And every theme has something to draw with, at every depth it is worn. */
    @Test
    void everyDepthGetsAFloorItCanBuild() {
        var themes = SHIPPED.themes();

        for (int depth = 1; depth <= 20; depth++) {
            var chosen = themes.pick(7L, depth);
            assertNotNull(chosen, "depth " + depth + " has no look at all");
            var tone = chosen.tone();
            assertNotNull(tone.floor(), "depth " + depth + " has no ground to stand on");
            assertNotNull(tone.wall(), "depth " + depth + " has no walls");
        }
    }

    /**
     * The run tells the client which look to wear, and says it in the one place
     * the client reads.
     *
     * <p>Nothing else connects the two: the game writes this field and the client
     * looks for it, and neither compiler sees the other.
     */
    @Test
    void theRunNamesItsLookInTheStatusLine() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(2);

        var status = session.game().getSnapshot().status();
        assertTrue(status.contains("|look="), "the client is never told what to build: " + status);
        var look = status.substring(status.indexOf("|look=") + "|look=".length());
        int end = look.indexOf('|');
        look = end < 0 ? look : look.substring(0, end);
        // The shipped descent mixes biomes, so the floor is dressed as the one its heroes come in to.
        var cameInTo = DungeonGenerator.generate(11L, SHIPPED, 1).biomes().ofRoom(0);
        assertEquals(SHIPPED.themes().dressedAs(cameInTo, 11L, 1).asStatus(), look,
                "it named a different floor from the one it drew");
    }

    /**
     * And the client is told what every cell wears: a record beside the floor's grid, naming each chamber's biome in
     * the tone this floor draws for it — the chamber the heroes stand in naming the status line's own look.
     */
    @Test
    void aMixedFloorTellsTheClientWhatEveryCellWears() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(2);
        var floor = DungeonGenerator.generate(11L, SHIPPED, 1);

        var record = session.game().getMapRecord();
        assertTrue(record instanceof uz.dukeengine.core.map.Looked, "the client was told nothing per cell: " + record);
        var looked = (uz.dukeengine.core.map.Looked) record;
        for (int i = 0; i < floor.rooms().size(); i++) {
            var middle = floor.rooms().get(i);
            assertEquals(SHIPPED.themes().dressedAs(floor.biomes().ofRoom(i), 11L, 1).asStatus(),
                    looked.lookAt(middle.centerCellX(), middle.centerCellY()), "chamber " + i);
        }
        var status = session.game().getSnapshot().status();
        assertTrue(status.contains("|look=" + looked.lookAt(floor.rooms().get(0).centerCellX(),
                floor.rooms().get(0).centerCellY())), "the heroes' chamber and the status line disagree: " + status);
        // And what lies about on it, which the client draws from the same record.
        assertEquals(floor.scenery(), ((uz.dukeengine.core.map.Dressed) record).scenery());
        // And how finely it is walked: the world's two, so a grove's trunks leave a body room between them.
        assertEquals(2, ((uz.dukeengine.core.map.Subdivided) record).navigationCellsPerCell());
    }

    /**
     * A floor that mixes biomes is dressed as the biome its heroes come in to.
     *
     * <p>Proved on a floor whose first chamber grew caves where the depth alone says wood: the look can only have
     * come from the chamber.
     */
    @Test
    void aFloorThatMixesBiomesIsDressedAsItsFirstChamber() {
        var mixed = uz.dukeengine.dungeon.content.BiomeFile.listing("Forest", "Dungeon");
        assertEquals("Forest", mixed.themes().nameFor(1), "the depth alone would say wood");
        long seed = 1;
        while (!uz.dukeengine.dungeon.gen.DungeonGenerator.generate(seed, mixed, 1).biomes().ofRoom(0).name()
                .equals("Dungeon")) {
            seed++;
        }

        var session = Dungeon.newSession(seed, mixed);
        session.game().runHeadless(2);
        var status = session.game().getSnapshot().status();
        var look = status.substring(status.indexOf("|look=") + "|look=".length());

        assertTrue(look.startsWith("Dungeon,"), "seed " + seed + " came in to caves and was dressed as " + look);
    }
}
