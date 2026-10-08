package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** The painted test map: written from its seed, read back whole, and painted only with pictures the game has. */
class PaintedMapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    @Test
    void itLoadsAsAStageTheGameCanPlayAndTheStageScreenOffersIt() {
        var stage = Stages.load("painted", SETTINGS);

        assertEquals("Sinov xaritasi", stage.name());
        assertNotNull(stage.paint(), "the painted map lost its paint on the way in");
        assertTrue(Stages.all().stream().anyMatch(listed -> listed.name().equals("painted")
                && listed.title().equals("Sinov xaritasi")), "the stage screen does not offer it");
    }

    @Test
    void itsPaintIsARowForEveryRowOfCellsAndACharacterForEveryCell() {
        var stage = Stages.load("painted", SETTINGS);
        var cells = stage.floor().levelMap().strip().lines().toList();

        assertEquals(PaintedMapWriter.SIZE, cells.size());
        assertEquals(cells.size(), stage.paint().rows().size());
        for (var row : stage.paint().rows()) {
            assertEquals(cells.getFirst().length(), row.length());
        }
    }

    @Test
    void itsPaletteNamesOnlyPicturesTheGameHas() {
        var paint = Stages.load("painted", SETTINGS).paint();

        assertEquals(5, paint.palette().size());
        for (var picture : paint.palette().values()) {
            assertNotNull(getClass().getClassLoader().getResource(picture), picture + " is not among the resources");
        }
    }

    /** What is shipped is what its seed writes, so it is never edited by hand. */
    @Test
    void theShippedMapIsWhatItsSeedWrites() {
        var shipped = Stages.found("painted").text().replace("\r\n", "\n");

        assertEquals(StageFile.write(PaintedMapWriter.stage(SETTINGS)), shipped);
    }

    /**
     * Played, the client is handed the paint beside the grid and told no look: a look's kit would be drawn over the
     * paint, and the descent's floors, which are drawn by one, are not touched.
     */
    @Test
    void playedItHandsTheClientItsPaintAndNoLook() {
        var session = uz.dukeengine.dungeon.Dungeon.newStageSession(Stages.load("painted", SETTINGS), SETTINGS);
        session.game().runHeadless(2);

        var record = session.game().getMapRecord();
        assertTrue(record instanceof uz.dukeengine.core.map.Painted, "the client was not given the paint: " + record);
        assertEquals(Stages.load("painted", SETTINGS).paint().rows(),
                uz.dukeengine.core.map.MapTerrain.rows(record, uz.dukeengine.core.data.Paint.class, "paint"));
        var status = session.game().getSnapshot().status();
        assertTrue(!status.contains("|look="), "a look would draw its kit over the paint: " + status);

        var descent = uz.dukeengine.dungeon.Dungeon.newSession(11L);
        descent.game().runHeadless(2);
        assertTrue(!(descent.game().getMapRecord() instanceof uz.dukeengine.core.map.Painted));
    }

    @Test
    void aMapWithoutPaintHasNone() {
        assertNull(Stages.load("first", SETTINGS).paint());
    }

    @Test
    void paintThatDoesNotFitItsMapIsRefused() {
        var shipped = Stages.found("painted").text().replace("\r\n", "\n");

        var unnamed = shipped.replaceFirst("(Paint = \\[\\n    \").", "$1x");
        assertTrue(assertThrows(DataException.class, () -> StageFile.read(unnamed, "painted")).getMessage()
                .contains("which its Palette does not name"));

        var narrow = shipped.replaceFirst("(Paint = \\[\\n    \").", "$1");
        assertTrue(assertThrows(DataException.class, () -> StageFile.read(narrow, "painted")).getMessage()
                .contains("is 127 cells"));
    }

    @Test
    void aPaintedStageIsWrittenAndReadBackTheSame() {
        var stage = PaintedMapWriter.stage(SETTINGS);
        var again = StageFile.read(StageFile.write(stage), "painted");

        assertEquals(stage.paint().rows(), again.paint().rows());
        assertEquals(List.copyOf(stage.paint().palette().entrySet()),
                List.copyOf(again.paint().palette().entrySet()));
        assertEquals(stage.paint().coverage(), again.paint().coverage());
    }
}
