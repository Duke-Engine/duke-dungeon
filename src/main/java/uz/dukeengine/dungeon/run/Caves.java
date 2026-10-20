package uz.dukeengine.dungeon.run;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.story.Cave;

/**
 * The caves of the open world, as their {@link Cave} blocks say: a hero stepping into a mouth goes down to its floor;
 * the floor's last monster dead, the cave is gone and he comes back up at its mouth. Which cave he is in is the map
 * being played, so nothing more is saved than the flag a cave sets as it goes.
 */
final class Caves {

    /** What the run lends the caves. */
    interface Run {
        StaticMap.Zone zone(String name);

        boolean flagged(String flag);

        void flag(String flag);

        /** The heroes standing, their bodies. */
        List<GameObject> heroes();

        /** Every monster the floor placed that still stands: what a cave is cleared of. */
        int monstersLeft();

        /** The map {@code name} laid next, the heroes put in {@code at} of it; blank for its own way in. */
        void toMap(String name, String at);
    }

    private final DungeonSettings settings;
    /** The caves whose mouths a hero was standing in when last looked: going in wants a step into one. */
    private final Set<String> standingIn = new HashSet<>();

    Caves(DungeonSettings settings) {
        this.settings = settings;
    }

    /** The cave whose floor is the map {@code stage}, or null. */
    Cave inside(String stage) {
        return settings.caves().stream().filter(cave -> cave.name().equals(stage)).findFirst().orElse(null);
    }

    /** A map laid: whoever stands in a mouth already -- come back up out of it -- has to step out before going in. */
    void laid(String stage, Run run) {
        standingIn.clear();
        standingIn.addAll(mouthsStoodIn(stage, run));
    }

    /** Looked at every check: a mouth stepped into is a way down; a cave's floor cleared is a way back up. */
    void update(String stage, Run run) {
        var cave = inside(stage);
        if (cave != null) {
            if (!run.flagged(cave.goneFlag()) && run.monstersLeft() == 0) {
                run.flag(cave.goneFlag());
                run.toMap(cave.map(), cave.zone());
            }
            return;
        }
        var now = mouthsStoodIn(stage, run);
        for (var name : now) {
            if (!standingIn.contains(name)) {
                run.toMap(name, "");
                break;
            }
        }
        standingIn.clear();
        standingIn.addAll(now);
    }

    /** The caves of {@code stage} open to a hero, whose mouths a hero stands in. */
    private Set<String> mouthsStoodIn(String stage, Run run) {
        var in = new HashSet<String>();
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        for (var cave : settings.caves()) {
            if (!cave.map().equals(stage) || run.flagged(cave.goneFlag())
                    || !cave.wakes().isBlank() && !run.flagged(cave.wakes())) {
                continue;
            }
            var zone = run.zone(cave.zone());
            if (zone == null) {
                continue;
            }
            for (var hero : run.heroes()) {
                if (zone.holds(hero.getPosition().x(), hero.getPosition().y(), cell)) {
                    in.add(cave.name());
                }
            }
        }
        return in;
    }
}
