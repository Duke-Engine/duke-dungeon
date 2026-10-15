package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.Painter;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.run.DungeonRun;

/**
 * What the game sounds like from where he stands, asked every frame the window draws: the music for the place and the
 * moment, the loop under his place, and the small sounds of his own screens — a page turned, a quest
 * taken, a coin paid.
 *
 * <p>The client's, and only read: the simulation decides none of it, and two machines in one party may hear two
 * different tracks. Which track is where is the Audio block's ({@code data/world/scene.duke}); this is the order they
 * are asked in. Drawn over the player's screens, whose painter it wraps and leaves alone.
 */
final class Soundscape implements Painter {

    /** How often the track wanted is asked for again, though unchanged: the client's own front end may have changed it. */
    private static final long AGAIN_NANOS = 1_000_000_000L;

    private final Painter under;
    private final PlayerScreens screens;
    private final Duke3D duke;
    private final DungeonSettings settings;
    private final Map<String, String> music;
    private final Set<String> bosses = new HashSet<>();

    private String asked;
    private String looping;
    private long askedAt;
    private long battleUntil;
    private Object watched;
    private PlayerScreens.Open open = PlayerScreens.Open.NONE;
    private int active = -1;
    private int finished = -1;
    private int paidUpTo = Integer.MIN_VALUE;

    Soundscape(PlayerScreens screens, Duke3D duke, DungeonSettings settings) {
        this.under = screens;
        this.screens = screens;
        this.duke = duke;
        this.settings = settings;
        this.music = settings.audio().musicByPlace();
        bosses.addAll(settings.audio().bosses());
        settings.story().bosses().forEach(boss -> bosses.add(boss.name()));
    }

    @Override
    public void paint(Canvas canvas) {
        under.paint(canvas);
        var match = DialogueScreen.match();
        boolean playing = match != null && match.game().getLogic() != null;
        if (match != watched) {
            watched = match; // a new match: nothing it says yet is news
            active = finished = -1;
            paidUpTo = Integer.MIN_VALUE;
        }
        long now = System.nanoTime();
        if (!playing) {
            loop(null);
            firesHeard(List.of()); // no match, no fire
            play(musicFor(music, false, null, null, List.of(), null, false, false), now);
            return;
        }
        var run = match.run();
        var hero = heroOf(match);
        var zones = hero == null ? List.<String>of() : zonesOf(run, hero, settings);
        boolean boss = false;
        if (hero != null) {
            for (var unit : match.game().getSnapshot().units()) {
                if (!unit.hostile() || unit.health() <= 0f) {
                    continue;
                }
                float gap = (float) Math.hypot(unit.x() - hero.x(), unit.y() - hero.y());
                boss |= bosses.contains(unit.templateName()) && gap <= settings.audio().bossReach();
                if (gap <= settings.audio().battleReach() && (unit.attacking() || hero.attacking())) {
                    battleUntil = now + (long) (settings.audio().battleSeconds() * 1e9);
                }
            }
        }
        loop(ambienceFor(settings.audio().ambienceByPlace(), zones, run.placeName()));
        play(musicFor(music, true, run.getState(), run.placeName(), zones, run.cutsceneName(), boss,
                now < battleUntil), now);
        screensHeard();
        questsHeard(run.questsNow());
        coinsHeard(run.payments(), match.game().getLocalPlayerIndex());
        firesHeard(match.game().getSnapshot().units());
    }

    @Override
    public void resized(int width, int height) {
        under.resized(width, height);
    }

    /**
     * The music's cue for this moment, or null where the Audio block names none for it — and then whatever plays goes
     * on. Asked in this order: the menu; a run lost, or won (on this map before anywhere); a cutscene of its own; a
     * boss in sight; a fight; the zones he stands in; his map; anywhere.
     */
    static String musicFor(Map<String, String> music, boolean playing, DungeonRun.State state, String place,
            List<String> zones, String cutscene, boolean boss, boolean battle) {
        var asked = new ArrayList<String>();
        if (!playing) {
            asked.add("menu");
        } else {
            if (state == DungeonRun.State.DEAD) {
                asked.add("dead");
            }
            if (state == DungeonRun.State.WON) {
                asked.add("won." + place);
                asked.add("won");
            }
            if (cutscene != null) {
                asked.add("cutscene." + cutscene);
            }
            if (boss) {
                asked.add("boss");
            }
            if (battle) {
                asked.add("battle");
            }
            asked.addAll(zones);
            asked.add(place);
            asked.add("default");
        }
        return uz.dukeengine.dungeon.world.Audio.firstOf(music, asked);
    }

    /** The loop under the place he is in, or null: the Audio block's Ambience, his zones before his map. */
    static String ambienceFor(Map<String, String> ambience, List<String> zones, String place) {
        var places = new ArrayList<>(zones);
        places.add(place);
        return uz.dukeengine.dungeon.world.Audio.firstOf(ambience, places);
    }

    /** Fades the place's loop in as he comes to it and out as he leaves (or leaves the match). */
    private void loop(String cue) {
        if (java.util.Objects.equals(cue, looping)) {
            return;
        }
        float fade = settings.audio().fadeSeconds();
        if (looping != null) {
            duke.stopLoop(looping, fade);
        }
        if (cue != null) {
            duke.loop(cue, fade);
        }
        looping = cue;
    }

    private void play(String cue, long now) {
        if (cue == null || cue.equals(asked) && now - askedAt < AGAIN_NANOS) {
            return;
        }
        float fade = settings.audio().fadeSeconds();
        duke.music(cue, fade, fade); // the same track asked again while it plays changes nothing
        asked = cue;
        askedAt = now;
    }

    /** A page turned as one of his screens opens, and the book shut as the last one closes. */
    private void screensHeard() {
        var now = screens.open();
        if (now != open) {
            duke.sound(now == PlayerScreens.Open.NONE ? "screen_close" : "screen_open");
            open = now;
        }
    }

    /** One more quest finished, or one more taken: a jump of several is a save loaded, and says nothing. */
    private void questsHeard(uz.dukeengine.dungeon.story.QuestLog.State state) {
        int nowActive = state.active().size();
        int nowFinished = state.finished().size();
        if (finished >= 0 && nowFinished == finished + 1) {
            duke.sound("quest_done");
        } else if (active >= 0 && nowActive == active + 1 && nowFinished == finished) {
            duke.sound("quest_taken");
        }
        active = nowActive;
        finished = nowFinished;
    }

    /** Coins, as gold is paid to him or by him: once a frame it happened on, from the first one after he is seen. */
    /** The fires crackling now, by their keys: {@code fire:} and the fire's id. */
    private final Set<String> fires = new HashSet<>();

    /**
     * Every fire the window sees crackles where it stands, a placed loop the client starts when the listener comes
     * near (Duke3D.loopAt); one gone -- burnt out, or out of sight -- fades. See docs/plan/2026-10-15-food-and-fire.md.
     */
    private void firesHeard(List<UnitView> units) {
        var food = settings.food();
        if (food.crackle().isBlank()) {
            return;
        }
        var now = new HashSet<String>();
        for (var unit : units) {
            if (unit.templateName().equals(food.campfire())) {
                var key = "fire:" + unit.id();
                now.add(key);
                if (fires.add(key)) {
                    duke.loopAt(key, food.crackle(), unit.x(), unit.y());
                }
            }
        }
        for (var it = fires.iterator(); it.hasNext(); ) {
            var key = it.next();
            if (!now.contains(key)) {
                duke.stopLoopAt(key, 1f);
                it.remove();
            }
        }
    }

    private void coinsHeard(List<DungeonRun.Payment> payments, int him) {
        int latest = paidUpTo;
        for (var paid : payments) {
            if (paid.player() == him && paid.frame() > paidUpTo) {
                latest = Math.max(latest, paid.frame());
            }
        }
        if (latest > paidUpTo && paidUpTo != Integer.MIN_VALUE) {
            duke.sound("coins");
        }
        paidUpTo = latest == Integer.MIN_VALUE ? -1 : latest;
    }

    /** This machine's hero as the snapshot last saw him, or null. */
    private UnitView heroOf(Dungeon.Session match) {
        for (var unit : match.game().getSnapshot().units()) {
            if (unit.playerIndex() == match.game().getLocalPlayerIndex()
                    && settings.heroes().stream().anyMatch(each -> each.name().equals(unit.templateName()))) {
                return unit;
            }
        }
        return null;
    }

    /** The zones he stands in: his map's own, and the places the story adds to it (Place blocks). */
    private static List<String> zonesOf(DungeonRun run, UnitView hero, DungeonSettings settings) {
        float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
        var all = new ArrayList<>(run.storyNow().zones());
        all.addAll(settings.placesOn(run.getStageName()));
        var zones = new ArrayList<String>();
        for (var zone : all) {
            if (zone.holds(hero.x(), hero.y(), cell)) {
                zones.add(zone.name());
            }
        }
        return zones;
    }
}
