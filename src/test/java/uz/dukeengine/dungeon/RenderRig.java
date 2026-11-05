package uz.dukeengine.dungeon;

import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** What the render checks share with the game: the client's canvas as Main wires it (bag, talk, screens). */
final class RenderRig {

    private RenderRig() {
    }

    /** The game's own drawing over the world -- his bars, the belt, the talk box, the screens -- as in {@code Main}. */
    static PlayerScreens canvas(Duke3D duke, Dungeon.Session session, DungeonSettings settings) {
        var bag = Bag.on(settings, duke);
        Bag.show(bag, session, settings);
        var screens = new PlayerScreens(new DialogueScreen(bag, bag, settings), settings);
        duke.canvas(screens).input(screens);
        return screens;
    }
}
