package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.story.Graveyard;

/**
 * The graveyards of the map being played, as their {@link Graveyard} blocks say -- an ender dragon's crystals (the owner,
 * 2026-10-20): the raiser stands among its graves and nothing it takes holds while one of them stands; the dead climb
 * out of them on a beat; each grave is struck down on its own; the last down, the raiser can be killed, and left a while
 * unhurt it casts and raises them all again. Laid with the map, dormant -- seen, but raising nothing and taking nothing
 * -- until its RaisesAfter flag. See docs/plan/2026-10-19-open-world-act1.md.
 *
 * <p>Nothing here is saved but the flag a graveyard sets as it goes: a load lays one again whole, if it is not gone.
 */
final class Graveyards {

    /** What the run lends a graveyard: its places, its flags, its side and what a kill pays. */
    interface Run {
        /** The zone of this name on the map played, or null. */
        StaticMap.Zone zone(String name);

        boolean flagged(String flag);

        /** Sets the flag, as the story's FLAG does: heard by the triggers that wait on it. */
        void flag(String flag);

        /** The side the dead stand on. */
        int side();

        /** The level a graveyard's dead stand at, its own or the floor's. */
        int levelOf(Graveyard yard);

        /** Pays its killer as any monster of the floor pays: a raised one that is worth it. */
        void pays(GameObject monster, int level);

        /** A beam drawn for {@code frames}, as the story's BEAM draws one. */
        void beam(String look, Coord3D from, Coord3D to, int frames);

        /** A grave struck down: heard as a death of its kind, so a quest that counts them counts it. */
        void struck(GameObject grave);
    }

    /** One graveyard laid: its raiser, its graves, and what it has raised. */
    private static final class Laid {
        final Graveyard yard;
        ObjectId raiser;
        /** The graveyard's middle, where the raiser stands and its dead are kept to. */
        Coord3D middle;
        /** Where each grave stands, standing or struck down: where it is raised again. */
        final List<Coord3D> places = new ArrayList<>();
        /** The grave standing at each place, or null where it is down. */
        final List<ObjectId> graves = new ArrayList<>();
        final List<ObjectId> standing = new ArrayList<>();
        int raised;
        int next;
        int nextGrave;
        /** The raiser's health when last looked, to tell a blow; and the frame it was last struck. */
        float health = -1f;
        int struckAt;
        /** The frame it began raising its graves again; 0 while it is not. */
        int restoring;

        Laid(Graveyard yard) {
            this.yard = yard;
        }
    }

    private final DungeonSettings settings;
    private final List<Laid> laid = new ArrayList<>();

    Graveyards(DungeonSettings settings) {
        this.settings = settings;
    }

    /** Every graveyard of {@code map} that has woken and is not gone, laid afresh: a map laid stands them up again. */
    void lay(GameLogic logic, String map, Run run) {
        laid.clear();
        wake(logic, map, run);
    }

    /** Every graveyard of {@code map} whose flag has come since, laid now: one that wakes in the middle of a map. */
    void wake(GameLogic logic, String map, Run run) {
        for (var yard : settings.graveyards()) {
            if (laid.stream().anyMatch(one -> one.yard == yard)) {
                continue;
            }
            if (!yard.map().equals(map) || run.flagged(yard.goneFlag())
                    || !yard.wakes().isBlank() && !run.flagged(yard.wakes())) {
                continue;
            }
            var zone = run.zone(yard.zone());
            var raiserKind = logic.findTemplate(yard.raiser());
            if (zone == null || raiserKind == null || logic.findTemplate(yard.grave()) == null) {
                continue;
            }
            var one = new Laid(yard);
            int level = run.levelOf(yard);
            float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
            float mx = zone.middleX(cell);
            float my = zone.middleY(cell);
            var raiser = logic.spawn(raiserKind, ground(logic, mx, my), sideOf(yard, run));
            Spawner.scale(raiser, level, settings);
            run.pays(raiser, level);
            one.raiser = raiser.getId();
            one.middle = raiser.getPosition();
            for (int i = 0; i < yard.graves(); i++) {
                double around = 2 * Math.PI * i / Math.max(1, yard.graves());
                one.places.add(ground(logic, mx + (float) Math.cos(around) * yard.ring() * cell,
                        my + (float) Math.sin(around) * yard.ring() * cell));
                one.graves.add(raise(logic, run, yard, one.places.getLast()).getId());
            }
            one.next = logic.getFrame() + yard.everyFrames();
            laid.add(one);
        }
    }

    /** A frame of every graveyard laid: dormant, or keeping its raiser, raising, struck down, raised again, gone. */
    void update(GameLogic logic, Run run) {
        int frame = logic.getFrame();
        for (var each = laid.iterator(); each.hasNext(); ) {
            var one = each.next();
            var yard = one.yard;
            var raiser = logic.findObject(one.raiser);
            if (raiser == null || raiser.isEffectivelyDead()) {
                run.flag(yard.goneFlag()); // the raiser dead: the graveyard is gone
                each.remove();
                continue;
            }
            boolean awake = awake(yard, run);
            if (awake && raiser.getPlayerIndex() != run.side()) {
                raiser.setPlayerIndex(run.side()); // woken: the dead's side, its graves with it
            }
            var graves = new ArrayList<GameObject>();
            for (int i = 0; i < one.graves.size(); i++) {
                var id = one.graves.get(i);
                var grave = id == null ? null : logic.findObject(id);
                if (grave == null || grave.getBody() == null) {
                    one.graves.set(i, null);
                    continue;
                }
                var body = grave.getBody();
                if (awake && grave.getPlayerIndex() != run.side()) {
                    grave.setPlayerIndex(run.side());
                }
                var place = one.places.get(i);
                if (grave.getPosition().x() != place.x() || grave.getPosition().y() != place.y()) {
                    grave.setPosition(place); // a building: a pull or a push moves it nowhere (the owner, 2026-10-20)
                }
                if (!awake) {
                    body.setHealth(body.getMaxHealth()); // dormant: nothing of it is hurt
                    graves.add(grave);
                } else if (body.getHealth() <= body.getMaxHealth() * 0.02f) {
                    // Beaten (its UNYIELDING keeps it from dying by a blow): the game strikes it down.
                    if (!yard.brokenLook().isBlank()) {
                        logic.effect(yard.brokenLook(), grave.getPosition(), 0f);
                    }
                    run.struck(grave);
                    grave.markDestroyed();
                    one.graves.set(i, null);
                } else {
                    graves.add(grave);
                }
            }
            var raiserBody = raiser.getBody();
            if (!awake || !graves.isEmpty()) {
                // Kept whole by its graves: nothing it takes holds while one of them stands.
                raiserBody.setHealth(raiserBody.getMaxHealth());
                one.health = raiserBody.getMaxHealth();
                one.restoring = 0;
                one.struckAt = frame;
                if (awake && frame % Math.max(1, yard.shieldFrames()) == 0) { // dormant, it stands quiet
                    if (!yard.shieldLook().isBlank()) {
                        logic.effect(yard.shieldLook(), raiser);
                    }
                    if (!yard.raiseBeam().isBlank()) {
                        var from = raiser.getPosition();
                        for (var grave : graves) {
                            var to = grave.getPosition();
                            run.beam(yard.raiseBeam(), new Coord3D(from.x(), from.y(), from.z() + Battles.HANDS),
                                    new Coord3D(to.x(), to.y(), to.z() + 2f), Math.max(1, yard.shieldFrames()) + 2);
                        }
                    }
                }
            } else {
                restore(logic, run, one, raiser, frame);
            }
            if (!awake) {
                continue;
            }
            one.standing.removeIf(id -> {
                var risen = logic.findObject(id);
                return risen == null || risen.isEffectivelyDead();
            });
            if (!graves.isEmpty() && frame >= one.next && one.standing.size() < yard.mostStanding()
                    && !yard.raises().isEmpty()) {
                one.next = frame + yard.everyFrames();
                var grave = graves.get(one.nextGrave++ % graves.size());
                rise(logic, run, one, grave, raiser);
            }
        }
    }

    /**
     * Its graves all down: the raiser can be hurt. Left unhurt for CalmFrames it begins to raise them again, its clip
     * playing; struck in it, it begins again; through RestoreFrames of it, every grave stands again where it stood.
     */
    private void restore(GameLogic logic, Run run, Laid one, GameObject raiser, int frame) {
        var yard = one.yard;
        float health = raiser.getBody().getHealth();
        if (one.health >= 0f && health < one.health - 0.01f) {
            one.struckAt = frame;
            one.restoring = 0; // struck in it: the spell broken
        }
        one.health = health;
        if (one.restoring == 0) {
            if (frame - one.struckAt >= yard.calmFrames()) {
                one.restoring = frame;
                if (!yard.restoreClip().isBlank()) {
                    raiser.busy(yard.restoreClip(), yard.restoreFrames());
                }
            }
            return;
        }
        if (!yard.restoreLook().isBlank() && (frame - one.restoring) % 30 == 0) {
            logic.effect(yard.restoreLook(), raiser);
        }
        if (frame - one.restoring >= yard.restoreFrames()) {
            for (int i = 0; i < one.places.size(); i++) {
                if (one.graves.get(i) == null) {
                    one.graves.set(i, raise(logic, run, yard, one.places.get(i)).getId());
                    if (!yard.risesLook().isBlank()) {
                        logic.effect(yard.risesLook(), one.places.get(i), 0f);
                    }
                }
            }
            one.restoring = 0;
            one.struckAt = frame;
        }
    }

    /** Dormant, nobody's: nothing of it fights or is fought until its RaisesAfter flag; then the dead's side. */
    private static int sideOf(Graveyard yard, Run run) {
        return awake(yard, run) ? run.side() : uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX;
    }

    private static boolean awake(Graveyard yard, Run run) {
        return yard.raisesAfter().isBlank() || run.flagged(yard.raisesAfter());
    }

    private static GameObject raise(GameLogic logic, Run run, Graveyard yard, Coord3D place) {
        return logic.spawn(logic.findTemplate(yard.grave()), place, sideOf(yard, run));
    }

    /** One of the dead climbs out of {@code grave}: paying while the graveyard has paid fewer than it may, changed after. */
    private void rise(GameLogic logic, Run run, Laid one, GameObject grave, GameObject raiser) {
        var yard = one.yard;
        var kind = logic.findTemplate(yard.raises().get(one.raised % yard.raises().size()));
        if (kind == null) {
            return;
        }
        var at = grave.getPosition();
        var risen = logic.spawn(kind, ground(logic, at.x() + 6f, at.y() + 6f), run.side());
        var rules = settings.graves();
        boolean paying = one.raised < rules.paying();
        int level = run.levelOf(yard) + (paying ? 0 : rules.changedLevels());
        Spawner.scale(risen, level, settings);
        if (paying) {
            run.pays(risen, level);
        } else {
            // Changed: its tint, and worth nothing -- no experience, and no bounty or drop is given it.
            risen.setCondition(rules.changedWord());
            var worth = risen.findModule(ExperienceModule.class);
            if (worth != null) {
                risen.replaceModule(worth, new ExperienceModule(risen,
                        new ExperienceModule.Data(0, List.of(), false)));
            }
        }
        one.raised++;
        one.standing.add(risen.getId());
        if (one.middle != null) { // it keeps to its graves: past them it turns back
            risen.addModule(new uz.dukeengine.dungeon.ai.Leash(risen, one.middle,
                    yard.leash() * uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE));
        }
        if (!yard.risesLook().isBlank()) {
            logic.effect(yard.risesLook(), risen.getPosition(), 0f);
        }
        if (!yard.raiseBeam().isBlank()) {
            var from = raiser.getPosition();
            run.beam(yard.raiseBeam(), new Coord3D(from.x(), from.y(), from.z() + Battles.HANDS),
                    new Coord3D(at.x(), at.y(), at.z() + 2f), 20);
        }
    }

    /** How many have climbed out of the graveyard laid in {@code zone}, for a test. */
    int raisedIn(String zone) {
        return laid.stream().filter(one -> one.yard.zone().equals(zone)).mapToInt(one -> one.raised).sum();
    }

    private static Coord3D ground(GameLogic logic, float x, float y) {
        var flat = new Coord3D(x, y, 0f);
        return new Coord3D(x, y, logic.groundHeight(flat));
    }
}
