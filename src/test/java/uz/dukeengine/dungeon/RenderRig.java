package uz.dukeengine.dungeon;

import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.dungeon.content.DungeonSettings;

/** What the render checks share with the game: the client's canvas as Main wires it (hour, bag, talk, screens). */
final class RenderRig {

    private RenderRig() {
    }

    /** The game's own drawing over the world -- his bars, the belt, the talk box, the screens -- without the hour's light. */
    static PlayerScreens canvas(Duke3D duke, Dungeon.Session session, DungeonSettings settings) {
        return canvas(duke, session, settings, false);
    }

    /** The same, and with {@code hour} the open world's hour and weather over it, as in {@code Main}. */
    static PlayerScreens canvas(Duke3D duke, Dungeon.Session session, DungeonSettings settings, boolean hour) {
        var bag = Bag.on(settings, duke).onRequest(true);
        Bag.show(bag, session, settings);
        uz.dukeengine.client3d.Painter under = hour ? new Atmosphere(bag, duke, settings, null) : bag;
        var screens = new PlayerScreens(new DialogueScreen(under, bag, settings), settings);
        duke.canvas(screens).input(screens);
        return screens;
    }
}
