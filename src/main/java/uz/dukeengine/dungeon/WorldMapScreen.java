package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Picture;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.story.MapStory;

/**
 * The world map (M): the whole of the map being played, full-screen, as the player knows it -- the engine's picture of
 * the world in the minimap's colours ({@code Duke3D.worldPicture}), asked for every map -- with the named places over
 * it (the Screens block's, bright once he has stood in them), where each quest going wants him, and the hero himself,
 * an arrow the way he faces. The wheel zooms about the pointer, a drag pans; it opens fitted to the window, or on a
 * quest's target from the log.
 */
final class WorldMapScreen {

    /**
     * Where the map stands on the screen: a world point at the middle of a box, so many pixels to a world unit. The
     * world's x runs right and its y down, as the minimap and the world's picture draw them. Whole, so a test can work
     * it from both ends.
     */
    static final class View {

        /** How far in the wheel goes past the map fitting the box, and how much each notch is. */
        static final float MOST_ZOOM = 16f;
        static final float NOTCH = 1.25f;

        private float worldWide = 1f;
        private float worldHigh = 1f;
        private Canvas.Box box = new Canvas.Box(0f, 0f, 1f, 1f);
        private float middleX = 0.5f;
        private float middleY = 0.5f;
        private float scale = 1f;
        private boolean fitted;

        /**
         * The world {@code wide} by {@code high} units in {@code box}: the same as last time keeps where it was looked
         * at; anything else -- a new map, another window -- fits it whole again.
         */
        void frame(Canvas.Box box, float wide, float high) {
            if (!fitted || !box.equals(this.box) || wide != worldWide || high != worldHigh) {
                this.box = box;
                worldWide = Math.max(1f, wide);
                worldHigh = Math.max(1f, high);
                fit();
            }
        }

        /** The whole world in the box, in its middle. */
        void fit() {
            scale = fitScale();
            middleX = worldWide / 2f;
            middleY = worldHigh / 2f;
            fitted = true;
        }

        /** Looked at again from scratch the next time it is framed. */
        void forget() {
            fitted = false;
        }

        private float fitScale() {
            return Math.min(box.width() / worldWide, box.height() / worldHigh);
        }

        float scale() {
            return scale;
        }

        /** Where the world point {@code (x, y)} is on the screen. */
        Canvas.Point toScreen(float x, float y) {
            return new Canvas.Point(box.x() + box.width() / 2f + (x - middleX) * scale,
                    box.y() + box.height() / 2f + (y - middleY) * scale);
        }

        /** And the world point at the screen's {@code (x, y)}. */
        Canvas.Point toWorld(float x, float y) {
            return new Canvas.Point(middleX + (x - box.x() - box.width() / 2f) / scale,
                    middleY + (y - box.y() - box.height() / 2f) / scale);
        }

        /** {@code notches} of the wheel at the screen's {@code (x, y)}, in for more: the point under it stays put. */
        void zoom(int notches, float x, float y) {
            var held = toWorld(x, y);
            float fit = fitScale();
            scale = Math.clamp(scale * (float) Math.pow(NOTCH, notches), fit, fit * MOST_ZOOM);
            var now = toScreen(held.x(), held.y());
            middleX += (now.x() - x) / scale;
            middleY += (now.y() - y) / scale;
            keepOn();
        }

        /** Dragged {@code (dx, dy)} pixels: the map goes with the pointer. */
        void pan(float dx, float dy) {
            middleX -= dx / scale;
            middleY -= dy / scale;
            keepOn();
        }

        /** The world point {@code (x, y)} in the middle, closer in: the log's way to a quest's target. */
        void centreOn(float x, float y, float zoom) {
            scale = Math.clamp(fitScale() * zoom, fitScale(), fitScale() * MOST_ZOOM);
            middleX = x;
            middleY = y;
            keepOn();
        }

        /** The middle kept on the world, so the map is never dragged off the screen. */
        private void keepOn() {
            middleX = Math.clamp(middleX, 0f, worldWide);
            middleY = Math.clamp(middleY, 0f, worldHigh);
        }
    }

    /** A named place on the map: what it says, where its middle is, and whether he has stood in it. */
    record Place(String label, float x, float y, boolean found) {
    }

    /** The places of {@code story} the Screens block names, in the map's order: a zone it does not name is no place. */
    static List<Place> places(MapStory story, Map<String, String> names, List<String> visited) {
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        var places = new ArrayList<Place>();
        for (var zone : story.zones()) {
            var label = names.get(zone.name());
            if (label != null) {
                places.add(new Place(label, zone.middleX(cell), zone.middleY(cell), visited.contains(zone.name())));
            }
        }
        return places;
    }

    /**
     * How many world units the picture covers across and down: a texel is {@code cells} cells of the grid, as many as
     * keep the picture within {@code mostTexels} a side ({@code WorldPicture}'s own sum).
     */
    static float[] covered(int gridWide, int gridHigh, float cellSize, int mostTexels) {
        int cells = Math.max(1, Math.ceilDiv(Math.max(gridWide, gridHigh), Math.max(1, mostTexels)));
        return new float[] {Math.ceilDiv(gridWide, cells) * cells * cellSize,
            Math.ceilDiv(gridHigh, cells) * cells * cellSize};
    }

    private final DungeonSettings settings;
    private final View view = new View();
    /** The world's picture the client handed for the map laid now, or null before it has. On the window's thread. */
    private volatile Picture picture;
    /** Where a drag was last, or null while none is going. */
    private int[] dragging;

    WorldMapScreen(DungeonSettings settings) {
        this.settings = settings;
    }

    View view() {
        return view;
    }

    /** A new world's picture: a new map was laid. */
    void picture(Picture handed) {
        picture = handed;
        view.forget();
    }

    /** The map fitted to the window the next time it is opened, unless the log centres it first. */
    void reset() {
        view.forget();
        dragging = null;
    }

    /** The box the map is drawn in: the window, less a frame round it. */
    static Canvas.Box boxOf(Canvas canvas) {
        float margin = Math.max(24f, canvas.height() * 0.06f);
        return new Canvas.Box(margin, margin * 1.4f, canvas.width() - margin * 2f, canvas.height() - margin * 2.6f);
    }

    /** The world's extent in units for {@code match}: what its picture covers, or its grid for no picture yet. */
    private float[] worldOf(Dungeon.Session match) {
        var grid = match.game().getTerrain();
        if (grid == null) {
            return new float[] {1f, 1f};
        }
        // With no picture yet, the grid itself: a texel a cell.
        int most = picture == null ? Integer.MAX_VALUE : settings.screens().mostTexels();
        return covered(grid.getWidth(), grid.getHeight(), grid.getCellSize(), most);
    }

    /** The map on {@code target}, closer in: the quest log's button. */
    void centreOn(Canvas canvas, Dungeon.Session match, float[] target) {
        var world = worldOf(match);
        view.frame(boxOf(canvas), world[0], world[1]);
        view.centreOn(target[0], target[1], 4f);
    }

    void paint(Canvas canvas, Dungeon.Session match) {
        var look = settings.menu();
        var screens = settings.screens();
        var box = boxOf(canvas);
        var world = worldOf(match);
        view.frame(box, world[0], world[1]);
        canvas.fillRect(0f, 0f, canvas.width(), canvas.height(), 0xF0000000 | look.stoneDeepColour());
        int text = Math.clamp(Math.round(canvas.height() * 0.02f), 12, 24);
        var font = Canvas.Font.of("Georgia", text);
        var bold = Canvas.Font.of("Georgia", Math.round(text * 1.3f)).bold(true);
        var small = Canvas.Font.of("Georgia", Math.max(10, Math.round(text * 0.85f)));
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0x90000000 | look.boneColour();
        var title = canvas.measure(bold, screens.mapTitle());
        canvas.drawText(bold, screens.mapTitle(), (canvas.width() - title.width()) / 2f,
                (box.y() - title.lineHeight()) / 2f, gold);
        if (!uz.dukeengine.dungeon.run.DungeonRun.towers(match.run().storyNow(), settings.travel()).isEmpty()) {
            // A map with towers: how to travel between them, and what it costs, under the title.
            var travel = settings.travel().travelHint().replace("{cost}", String.valueOf(settings.travel().cost()));
            var said = canvas.measure(small, travel);
            canvas.drawText(small, travel, (canvas.width() - said.width()) / 2f,
                    (box.y() - title.lineHeight()) / 2f + title.lineHeight(), gold);
        }
        var hint = canvas.measure(small, screens.mapHint());
        canvas.drawText(small, screens.mapHint(), (canvas.width() - hint.width()) / 2f,
                box.y() + box.height() + (canvas.height() - box.y() - box.height() - hint.lineHeight()) / 2f, dim);

        canvas.clip(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
        canvas.fillRect(box.x(), box.y(), box.width(), box.height(), 0xFF000000);
        var handed = picture;
        if (handed != null) {
            var from = view.toScreen(0f, 0f);
            var to = view.toScreen(world[0], world[1]);
            canvas.drawPicture(handed, from.x(), from.y(), to.x(), to.y());
        }
        var run = match.run();
        var story = run.storyNow();
        // The places, a diamond and a name each: gold where he has stood, dim where he has not yet.
        for (var place : places(story, screens.placeNames(), run.visited())) {
            var at = view.toScreen(place.x(), place.y());
            float r = text * 0.45f;
            int colour = place.found() ? gold : dim;
            canvas.fillTriangle(at.x() - r, at.y(), at.x() + r, at.y(), at.x(), at.y() - r, colour);
            canvas.fillTriangle(at.x() - r, at.y(), at.x() + r, at.y(), at.x(), at.y() + r, colour);
            var size = canvas.measure(font, place.label());
            float lx = at.x() - size.width() / 2f;
            float ly = at.y() - r - size.lineHeight() - 2f;
            canvas.drawText(font, place.label(), lx + 1f, ly + 1f, 0xC0000000);
            canvas.drawText(font, place.label(), lx, ly, place.found() ? bone : dim);
        }
        // Where every quest going wants him: the Dialogue block's mark, in its colour.
        var dialogue = settings.story().dialogue();
        float mark = text * 1.6f;
        var state = run.questsNow();
        for (var going : state.active()) {
            var target = QuestLogScreen.whereIs(going.quest(), state, settings.story(), story);
            if (target != null) {
                var at = view.toScreen(target[0], target[1]);
                canvas.drawImage(Canvas.Image.of(dialogue.giveMark()), at.x() - mark / 2f, at.y() - mark,
                        at.x() + mark / 2f, at.y(), 0xFF000000 | dialogue.markColour(), Canvas.Blend.ALPHA);
            }
        }
        // And him: an arrow the way he faces, over everything.
        var hero = heroOf(match);
        if (hero != null) {
            var at = view.toScreen(hero.x(), hero.y());
            arrow(canvas, at.x(), at.y(), hero.orientation(), text * 0.9f, gold);
        }
        canvas.noClip();
        canvas.openRect(box.x(), box.y(), box.width(), box.height(), 2f, 0xFF000000 | look.stoneEdgeColour());
    }

    /** An arrow at {@code (x, y)} pointing {@code facing} radians round from the world's x, {@code size} long. */
    static void arrow(Canvas canvas, float x, float y, float facing, float size, int argb) {
        float cos = (float) Math.cos(facing);
        float sin = (float) Math.sin(facing);
        float tipX = x + cos * size;
        float tipY = y + sin * size;
        float backX = x - cos * size * 0.6f;
        float backY = y - sin * size * 0.6f;
        float sideX = -sin * size * 0.6f;
        float sideY = cos * size * 0.6f;
        canvas.fillTriangle(tipX + cos * 2f, tipY + sin * 2f, backX + sideX * 1.2f, backY + sideY * 1.2f,
                backX - sideX * 1.2f, backY - sideY * 1.2f, 0xFF000000);
        canvas.fillTriangle(tipX, tipY, backX + sideX, backY + sideY, backX - sideX, backY - sideY, argb);
    }

    /** This machine's hero as the snapshot last saw him, or null. */
    private uz.dukeengine.core.view.UnitView heroOf(Dungeon.Session match) {
        var snapshot = match.game().getSnapshot();
        for (var unit : snapshot.units()) {
            if (unit.playerIndex() == match.game().getLocalPlayerIndex()
                    && settings.heroes().stream().anyMatch(each -> each.name().equals(unit.templateName()))) {
                return unit;
            }
        }
        return null;
    }

    /** What a click on a found tower does: closes the screen, once the journey is asked for. */
    private Runnable travelled = () -> { };
    /** Where the left button went down, to tell a click from a drag. */
    private int[] pressed;

    void onTravel(Runnable then) {
        travelled = then;
    }

    /**
     * The tower of {@code match}'s map whose mark is under the screen's {@code (x, y)}, by its place among the towers,
     * or -1: only a tower found -- he has stood in it -- can be gone to.
     */
    int towerAt(Dungeon.Session match, float x, float y, float reach) {
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        var towers = uz.dukeengine.dungeon.run.DungeonRun.towers(match.run().storyNow(), settings.travel());
        var found = match.run().visited();
        for (int i = 0; i < towers.size(); i++) {
            var zone = towers.get(i);
            var at = view.toScreen(zone.middleX(cell), zone.middleY(cell));
            if (found.contains(zone.name()) && Math.hypot(at.x() - x, at.y() - y) <= reach) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The pointer: the wheel zooms, a left drag pans, and a left click on a found tower travels there (the Travel
     * block's gold, paid in the world). Everything is taken while the map is open.
     */
    boolean take(uz.dukeengine.client3d.CanvasInput.Event event) {
        switch (event) {
            case uz.dukeengine.client3d.CanvasInput.Wheel wheel -> view.zoom(Integer.signum(wheel.notches()),
                    wheel.x(), wheel.y());
            case uz.dukeengine.client3d.CanvasInput.Button button -> {
                if (button.button() == uz.dukeengine.client3d.CanvasInput.Mouse.LEFT) {
                    dragging = button.down() ? new int[] {button.x(), button.y()} : null;
                    if (button.down()) {
                        pressed = new int[] {button.x(), button.y()};
                    } else if (pressed != null && Math.abs(button.x() - pressed[0]) + Math.abs(button.y() - pressed[1])
                            < 6) {
                        click(button.x(), button.y());
                    }
                }
            }
            case uz.dukeengine.client3d.CanvasInput.Pointer pointer -> {
                if (dragging != null) {
                    view.pan(pointer.x() - dragging[0], pointer.y() - dragging[1]);
                    dragging = new int[] {pointer.x(), pointer.y()};
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void click(int x, int y) {
        var match = DialogueScreen.match();
        if (match == null || match.game().getLogic() == null) {
            return;
        }
        int tower = towerAt(match, x, y, 18f);
        if (tower >= 0) {
            match.game().postCommand(uz.dukeengine.dungeon.party.PartyOrders.of(
                    new uz.dukeengine.dungeon.run.TravelTo(match.game().getLocalPlayerIndex(), tower)));
            travelled.run();
        }
    }
}
