package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;

/**
 * The six castle kits (models/castles, written by art/castles/export_castles.py): every piece loads wearing its
 * picture and stands on the ground; the Castles theme offers every piece with its footprint, and the fort's again with
 * its dark picture; and the courtyard of maps/qasr loads, checks, has its walls meeting its towers and in the way, and
 * is entered by its gate.
 */
class CastleKitsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);
    private static final String ROOT = "models/castles/";

    private static List<String> pieces() throws IOException, URISyntaxException {
        var root = Path.of(CastleKitsTest.class.getClassLoader().getResource(ROOT).toURI());
        try (var files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".gltf"))
                    .map(file -> ROOT + root.relativize(file).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private static BoundingBox box(Spatial model) {
        model.updateModelBound();
        model.updateGeometricState();
        return (BoundingBox) model.getWorldBound();
    }

    private static Set<String> pictures(Spatial model) {
        var names = new java.util.TreeSet<String>();
        model.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry geometry && geometry.getMaterial() != null) {
                geometry.getMaterial().getParams().stream()
                        .filter(param -> param.getValue() instanceof com.jme3.texture.Texture)
                        .map(param -> (com.jme3.texture.Texture) param.getValue())
                        .forEach(texture -> names.add(texture.getKey() != null ? texture.getKey().getName()
                                : String.valueOf(texture.getName())));
            }
        });
        return names;
    }

    /** Every piece loads with a picture; walls and towers stand on the ground, a floor slab has its top on it. */
    @Test
    void everyPieceLoadsWearingItsPictureOnTheGround() throws Exception {
        var all = pieces();
        assertTrue(all.size() >= 75, "only " + all.size() + " castle pieces");
        for (var path : all) {
            var model = ASSETS.loadModel(path);
            var box = box(model);
            float bottom = box.getCenter().y - box.getYExtent();
            float top = box.getCenter().y + box.getYExtent();
            assertTrue(top > (path.contains("floor") ? -0.01f : 0.2f) && top < 80f, path + " reaches " + top);
            if (path.endsWith("room_floor.gltf")) {
                assertEquals(0f, top, 0.3f, "the room's floor is a slab whose top is the ground");
            } else if (path.matches(".*_(wall|tower|gate|keep|column|pillar|block|house)[^/]*")) {
                // On the ground, or its foundation sunk a little into it, as the city wall's are.
                assertTrue(bottom > -2.5f && bottom < 0.6f, path + " stands at " + bottom + ", not on the ground");
            } else if (!path.contains("bridge")) { // a bridge dips over its water; a prop keeps its kit's height
                assertTrue(bottom > -2.5f, path + " is sunk to " + bottom);
            }
            assertTrue(!pictures(model).isEmpty(), path + " carries no picture");
        }
    }

    /** A wall runs east from its origin; the rampart's is three cells, the length the courtyard is laid on. */
    @Test
    void aWallRunsEastFromItsOrigin() {
        for (var wall : List.of("rampart/rampart_wall", "rampart/rampart_wall_gate", "rampart/rampart_wall_arcade",
                "fort/fort_wall", "town/town_wall_long", "hall/hall_wall", "desert/desert_wall_tall")) {
            var box = box(ASSETS.loadModel(ROOT + wall + ".gltf"));
            float west = box.getCenter().x - box.getXExtent();
            assertTrue(west > -0.6f && west < 6f, wall + " starts at " + west + ", not at its origin");
            assertTrue(Math.abs(box.getCenter().z) < 1.5f, wall + " is not centred on its line");
        }
        var rampart = box(ASSETS.loadModel(ROOT + "rampart/rampart_wall.gltf"));
        assertEquals(30f, 2 * rampart.getXExtent(), 0.5f, "the rampart's wall is three cells");
    }

    /**
     * The queen's castle: every fort piece is offered again drawn with its dark picture (model@picture, one mesh),
     * and that picture is shipped beside its own.
     */
    @Test
    void everyFortPieceIsOfferedWithItsDarkPicture() throws Exception {
        var fort = pieces().stream().filter(p -> p.contains("/fort/")).toList();
        assertTrue(fort.size() >= 15, "only " + fort.size() + " fort pieces");
        var offered = castles().scenery().stream().map(s -> s.model()).toList();
        for (var piece : fort) {
            var dark = offered.stream().filter(m -> m.startsWith(piece + "@")).findFirst()
                    .orElseThrow(() -> new AssertionError(piece + " is not offered dark"));
            var picture = dark.substring(dark.indexOf('@') + 1);
            assertTrue(picture.endsWith("_dark.jpg"), dark);
            assertNotNull(ASSETS.loadTexture(picture), picture);
            var own = pictures(ASSETS.loadModel(piece));
            assertEquals(Set.of(picture.replace("_dark.jpg", ".jpg")), own,
                    piece + " is drawn with more than the one picture its dark one replaces");
        }
    }

    /**
     * The Castles theme offers every shipped piece to the brush and strews none; a wall stands in the way along its
     * length, a gate or an arch does not, a tower is round.
     */
    @Test
    void theCastlesThemeOffersEveryPieceWithItsFootprint() throws Exception {
        var theme = castles();
        var offered = theme.scenery().stream().map(s -> s.model().split("@")[0]).collect(Collectors.toSet());
        assertEquals(Set.copyOf(pieces()), offered);
        assertTrue(theme.scenery().stream().allMatch(s -> s.perHundred() == 0 && s.scale() == 1f));
        assertTrue(SETTINGS.biomes().all().stream().noneMatch(t -> t.name().equals("Castles")),
                "the castles are no biome");
        for (var scatter : theme.scenery()) {
            var model = scatter.model();
            var line = GeneratedDungeon.Piece.of(model + " 0 0 0 1 " + scatter.footprint());
            var name = model.split("@")[0];
            name = name.substring(name.lastIndexOf('/') + 1, name.length() - ".gltf".length());
            if (name.endsWith("_gate") || name.endsWith("_gatehouse") || name.endsWith("_wall_arch")) {
                assertEquals(0f, line.boxLength() + line.footprint(), model + " shuts its own way through");
            } else if (name.contains("_wall") || name.contains("_block")) {
                assertTrue(line.boxLength() >= 0.5f && line.boxDepth() >= 1.1f, model + " is in nobody's way: "
                        + scatter.footprint());
            } else if (name.contains("_tower") || name.endsWith("_keep") || name.endsWith("_turret")) {
                assertTrue(line.footprint() > 0.3f && line.boxLength() == 0f, model + " is not round");
            }
        }
    }

    private static uz.dukeengine.dungeon.world.Theme castles() {
        return SETTINGS.themes().all().stream().filter(t -> t.name().equals("Castles")).findFirst()
                .orElseThrow(() -> new AssertionError("no Castles theme"));
    }

    /** The castle hall's solid things are props with a look that loads. */
    @Test
    void theHallsSolidThingsAreProps() {
        for (var name : List.of("CastleColumn", "CastleTable", "CastleBasin")) {
            assertNotNull(SETTINGS.prop(name), "no Prop " + name);
            var look = SETTINGS.looks().stream().filter(one -> one.name().equals(name)).findFirst()
                    .orElseThrow(() -> new AssertionError("nothing says what " + name + " looks like"));
            assertNotNull(ASSETS.loadModel(look.model()), look.model());
        }
    }

    /**
     * The courtyard loads and checks; every piece on it is shipped; and each rampart wall begins a cell past one
     * tower's middle and ends a cell short of the next's, which is the snap the kit is cut for.
     */
    @Test
    void theCourtyardStandsTogether() {
        var stage = Stages.load("qasr", SETTINGS);
        assertEquals(List.of(), StageCheck.problems(stage, SETTINGS));
        var scenery = stage.paint().scenery();
        assertTrue(scenery.size() >= 20, "only " + scenery.size() + " pieces in the courtyard");
        for (var piece : scenery) {
            assertNotNull(ASSETS.loadModel(piece.model()), piece.model());
        }
        var towers = scenery.stream().filter(p -> p.model().contains("rampart_tower")).toList();
        var walls = scenery.stream().filter(p -> p.model().contains("rampart_wall")).toList();
        assertEquals(8, towers.size());
        assertEquals(8, walls.size());
        for (var wall : walls) {
            double a = Math.toRadians(wall.facing());
            float dx = (float) Math.cos(a);
            float dy = (float) -Math.sin(a); // facing 90 runs north, up the map
            assertTrue(near(towers, wall.x() - dx, wall.y() - dy), wall.written() + " starts by no tower");
            assertTrue(near(towers, wall.x() + 4 * dx, wall.y() + 4 * dy), wall.written() + " ends by no tower");
        }
    }

    /**
     * The courtyard's walls stand in the way and its gate does not: the cells under a wall are shut, those in the
     * gateway open, and the way from before the gate to the middle of the court goes in by the gateway, never
     * through a wall.
     */
    @Test
    void theHeroComesInByTheGateAndNotThroughAWall() {
        var game = uz.dukeengine.dungeon.Dungeon.newStageSession(Stages.load("qasr", SETTINGS), SETTINGS).game();
        game.runHeadless(1);
        var logic = game.getLogic();
        var grid = logic.getPathGrid();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        // The north wall from (9, 8) east, the west wall from (8, 9) south, the south wall west of the gate.
        for (int[] shut : new int[][] {{10, 7}, {10, 8}, {7, 10}, {8, 10}, {10, 17}, {10, 18}, {18, 15}}) {
            assertTrue(MapCells.shut(grid, shut[0], shut[1]), "the wall leaves " + shut[0] + "," + shut[1] + " open");
        }
        for (int[] open : new int[][] {{15, 17}, {15, 18}, {12, 12}}) {
            assertTrue(!MapCells.shut(grid, open[0], open[1]), open[0] + "," + open[1] + " is shut");
        }
        var path = logic.findPath(new uz.dukeengine.core.math.Coord3D(15.5f * cell, 22.5f * cell, 0f),
                new uz.dukeengine.core.math.Coord3D(13.5f * cell, 12.5f * cell, 0f));
        assertTrue(!path.isEmpty(), "no way into the court");
        // Waypoints may be sparse: the way is walked along its straight legs, so it is the legs that cross the wall's
        // row, and they cross it in the gateway.
        boolean byTheGate = false;
        var waypoints = path.getWaypoints();
        for (int i = 0; i + 1 < waypoints.size(); i++) {
            var a = waypoints.get(i);
            var b = waypoints.get(i + 1);
            int steps = (int) Math.ceil(Math.hypot(b.x() - a.x(), b.y() - a.y()));
            for (int s = 0; s <= steps; s++) {
                float x = a.x() + (b.x() - a.x()) * s / Math.max(1, steps);
                float y = a.y() + (b.y() - a.y()) * s / Math.max(1, steps);
                int cx = (int) Math.floor(x / cell);
                int cy = (int) Math.floor(y / cell);
                if (cy >= 17 && cy <= 18) {
                    assertTrue(cx >= 14 && cx <= 16, "the way crosses the south wall at " + cx + "," + cy);
                    byTheGate = true;
                }
            }
        }
        assertTrue(byTheGate, "the way in misses the gate");
    }

    private static boolean near(List<GeneratedDungeon.Piece> towers, float x, float y) {
        return towers.stream().anyMatch(t -> Math.hypot(t.x() - x, t.y() - y) < 0.05);
    }
}
