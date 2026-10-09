package uz.dukeengine.dungeon;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;

/**
 * The live scene behind the front menu: the kingdom's soldiers holding a forest clearing against the skeletons — the
 * game's own creatures, brains and weapons, a real fight — the fallen coming back to their side's edge after a while,
 * so it never ends; the camera drifting slowly over it. Who stands on each side and the look the clearing wears are
 * {@code data/world/menu.duke}'s.
 *
 * <p>Local only: made by the client for its front end ({@code Duke3D.backdrop}), never part of a played match, and
 * torn down when one starts. Its dice are wound to {@link #SEED}, so it plays the same way every time.
 */
final class MenuScene {

    static final long SEED = 20261009L;
    static final int WIDTH = 40;
    static final int HEIGHT = 26;
    /** How long a fallen creature lies before another of its kind comes in at its side's edge: three seconds. */
    static final int RESPAWN_FRAMES = 90;
    /** How long the camera takes to drift from one end of its path to the other: twenty seconds. */
    static final int DRIFT_FRAMES = 600;

    private final DukeGame game;
    private final Side soldiers;
    private final Side foes;
    private final uz.dukeengine.dungeon.ai.Orders orders;
    private int deaths;

    private MenuScene(DukeGame game, Side soldiers, Side foes, uz.dukeengine.dungeon.ai.Orders orders) {
        this.game = game;
        this.soldiers = soldiers;
        this.foes = foes;
        this.orders = orders;
    }

    /** A fresh scene, not yet started. */
    static MenuScene of(DungeonSettings settings) {
        var menu = settings.mainMenu();
        var arena = Dungeon.world(clearing(), settings);
        var game = arena.game().randomSeed(SEED);
        var scene = new MenuScene(game, new Side(arena.hero(), menu.soldiers(), 8),
                new Side(arena.dungeon(), menu.foes(), WIDTH - 9), arena.orders());
        game.onStart(started -> {
            if (menu.sceneLook() != null && !menu.sceneLook().isBlank()) {
                started.setStatus("|look=" + menu.sceneLook()); // the theme the clearing wears, as a run names one
            }
            scene.soldiers.fill(started, 0);
            scene.foes.fill(started, 0);
            var camera = started.camera();
            camera.moveTo(cell(WIDTH / 2 - 6), cell(HEIGHT / 2 + 3), 0);
            camera.lookToward(cell(WIDTH / 2), cell(HEIGHT / 2));
        });
        game.onDied(died -> scene.deaths++);
        game.onTick(ticking -> scene.tick(ticking));
        return scene;
    }

    DukeGame game() {
        return game;
    }

    /** How many have fallen since the scene began, on both sides. */
    int deaths() {
        return deaths;
    }

    /** How many of each side stand now. */
    int standing(boolean soldiersSide) {
        return (soldiersSide ? soldiers : foes).standing(game);
    }

    private void tick(DukeGame running) {
        int frame = running.getLogic().getFrame();
        soldiers.fill(running, frame);
        foes.fill(running, frame);
        if (frame % 30 == 0 && orders.attackMovingTo(soldiers.player.getIndex()) == null) {
            // The soldiers go out to meet them, as a player's hero is sent: "go there, and kill what you meet".
            var foe = foes.first();
            orders.attackMove(soldiers.player.getIndex(), foe != null ? foe.getPosition()
                    : new Coord3D(cell(foes.column), cell(HEIGHT / 2), 0f));
        }
        if (frame % DRIFT_FRAMES == 1) {
            // Back and forth along the soldiers' flank, always turned toward the middle of the clearing.
            boolean out = (frame / DRIFT_FRAMES) % 2 == 0;
            var camera = running.camera();
            camera.moveTo(cell(WIDTH / 2 + (out ? 6 : -6)), cell(HEIGHT / 2 + 3), DRIFT_FRAMES);
            camera.lookToward(cell(WIDTH / 2), cell(HEIGHT / 2));
        }
    }

    /** An open clearing ringed by stone — trees, in the forest's look — with a few clumps standing in it. */
    static String clearing() {
        var text = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean edge = x == 0 || y == 0 || x == WIDTH - 1 || y == HEIGHT - 1;
                boolean clump = (x == 14 || x == 15) && (y == 4 || y == 5)
                        || (x == 25 || x == 26) && (y == 20 || y == 21);
                text.append(edge || clump ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static float cell(int at) {
        return (at + 0.5f) * PathGrid.DEFAULT_CELL_SIZE;
    }

    /** One side: who stands on it, where it comes in, and when each of its places was emptied. */
    private static final class Side {

        private final GamePlayer player;
        private final List<String> kinds;
        private final int column;
        private final GameObject[] standing;
        private final int[] fellAt;

        Side(GamePlayer player, List<String> kinds, int column) {
            this.player = player;
            this.kinds = List.copyOf(kinds);
            this.column = column;
            this.standing = new GameObject[kinds.size()];
            this.fellAt = new int[kinds.size()];
        }

        /** Bring in whoever's place has been empty long enough: at once on the first frame. */
        void fill(DukeGame game, int frame) {
            var logic = game.getLogic();
            for (int i = 0; i < kinds.size(); i++) {
                var thing = standing[i];
                if (thing != null && !thing.isEffectivelyDead() && logic.findObject(thing.getId()) != null) {
                    continue;
                }
                if (thing != null) {
                    standing[i] = null;
                    fellAt[i] = frame;
                }
                if (frame != 0 && frame - fellAt[i] < RESPAWN_FRAMES) {
                    continue;
                }
                var template = logic.getThingFactory().findTemplate(kinds.get(i));
                if (template == null) {
                    continue;
                }
                float x = cell(column);
                float y = cell(HEIGHT / 2 - kinds.size() + 2 * i);
                var at = new Coord3D(x, y, 0f);
                standing[i] = logic.spawn(template, new Coord3D(x, y, logic.groundHeight(at)), player.getIndex());
            }
        }

        /** The first of this side still standing, or null. */
        GameObject first() {
            for (var thing : standing) {
                if (thing != null && !thing.isEffectivelyDead()) {
                    return thing;
                }
            }
            return null;
        }

        int standing(DukeGame game) {
            int count = 0;
            for (var thing : standing) {
                if (thing != null && !thing.isEffectivelyDead()) {
                    count++;
                }
            }
            return count;
        }
    }
}
