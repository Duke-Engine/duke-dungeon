package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.GamePlayer;
import uz.dukeengine.rpg.mission.GateUpdate;

/**
 * The gates of a map's gatehouses: where the scenery has a gatehouse of the fort kit, the passage between its piers is
 * shut by a gate of the sim ({@code data/props/world_gate.duke}) until it opens, and a hero who comes within
 * {@link #REACH} of one opens it. Blocking is the sim's -- the gate has a shape, and opening it takes the shape away --
 * and the swing of its leaves is only the picture of the open gate appearing, so nothing here depends on a client.
 *
 * <p>Laid from the scenery lines the map already has (a gatehouse's origin, facing and kind), so the map file carries
 * no gate of its own. Owned by the first hero's side, because the client draws a thing beyond a hero's sight only when
 * it is the viewer's own, and a gate that vanished at five cells would show an empty passage from afar. Deterministic:
 * the gates are kept in the order the scenery lists them, and a hero's distance is the only thing asked each frame.
 */
final class Gates {

    /** How near a hero comes before the gate opens, in units: far enough to be open before he runs into it. */
    static final float REACH = 55f;

    /** A gatehouse piece: the gate that shuts it and the middle of its passage, in cells along the wall. */
    private record Kind(String gate, float middle) {
    }

    private static final String FOLDER = "models/buildings/fort_kit/";
    private static final Map<String, Kind> KINDS = Map.of(
            FOLDER + "town_gatehouse.glb", new Kind("TownGate", 2f),
            FOLDER + "fort_gatehouse.glb", new Kind("Portcullis", 1f),
            FOLDER + "dark/fort_gatehouse.glb", new Kind("DarkPortcullis", 1f));

    private final List<ObjectId> shut = new ArrayList<>();

    /** The gates of {@code scenery} laid across their passages, replacing any a floor before it left. */
    void lay(GameLogic logic, GamePlayer owner, List<? extends MapScenery> scenery) {
        shut.clear();
        if (scenery == null) {
            return;
        }
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        for (var piece : scenery) {
            var kind = KINDS.get(piece.model());
            var template = kind == null ? null : logic.getThingFactory().findTemplate(kind.gate());
            if (template == null) {
                continue;
            }
            double turned = Math.toRadians(piece.facing());
            float x = (piece.x() + kind.middle() * (float) StrictMath.cos(turned)) * cell;
            float y = (piece.y() - kind.middle() * (float) StrictMath.sin(turned)) * cell;
            var gate = logic.spawn(template, new Coord3D(x, y, logic.groundHeight(new Coord3D(x, y, 0f))),
                    owner.getIndex());
            // Along the wall as the scenery is turned: an object's angle runs the other way about the vertical.
            gate.setOrientation((float) -turned);
            shut.add(gate.getId());
        }
    }

    /** One frame: every shut gate with a hero of {@code heroes} within reach is opened. */
    void tick(GameLogic logic, List<ObjectId> heroes) {
        for (int at = 0; at < shut.size(); at++) {
            var gate = logic.findObject(shut.get(at));
            if (gate == null || gate.isDestroyed()) {
                shut.remove(at--); // opened already, by a hero or by the story
                continue;
            }
            for (var id : heroes) {
                var hero = id == null ? null : logic.findObject(id);
                if (hero != null && !hero.isEffectivelyDead() && near(hero, gate)) {
                    var module = gate.findModule(GateUpdate.class);
                    if (module != null) {
                        module.open();
                    }
                    break;
                }
            }
        }
    }

    private static boolean near(GameObject hero, GameObject gate) {
        return Math.hypot(hero.getPosition().x() - gate.getPosition().x(),
                hero.getPosition().y() - gate.getPosition().y()) <= REACH;
    }
}
