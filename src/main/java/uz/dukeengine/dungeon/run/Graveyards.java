package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.combat.module.ExperienceModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.map.StaticMap;
import uz.dukeengine.dungeon.story.Graveyard;

/**
 * The graveyards of the map being played, as their {@link Graveyard} blocks say: laid with the map, raising their dead
 * on a beat, shielded while their raisers stand, struck down a grave at a time once they fall, and gone for good when
 * the last grave is. See docs/plan/2026-10-19-open-world-act1.md.
 *
 * <p>Nothing here is saved but the flag a graveyard sets as it goes: a load lays one again whole, raiser and all, if it
 * is not gone.
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
        final List<ObjectId> graves = new ArrayList<>();
        final List<ObjectId> standing = new ArrayList<>();
        int raised;
        int next;
        int nextGrave;

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
            var graveKind = logic.findTemplate(yard.grave());
            if (zone == null || raiserKind == null || graveKind == null) {
                continue;
            }
            var one = new Laid(yard);
            int level = run.levelOf(yard);
            float cell = uz.dukeengine.core.pathfind.PathGrid.DEFAULT_CELL_SIZE;
            float mx = zone.middleX(cell);
            float my = zone.middleY(cell);
            var raiser = logic.spawn(raiserKind, ground(logic, mx, my), run.side());
            Spawner.scale(raiser, level, settings);
            run.pays(raiser, level);
            one.raiser = raiser.getId();
            one.middle = raiser.getPosition();
            for (int i = 0; i < yard.graves(); i++) {
                double around = 2 * Math.PI * i / Math.max(1, yard.graves());
                float gx = mx + (float) Math.cos(around) * yard.ring() * cell;
                float gy = my + (float) Math.sin(around) * yard.ring() * cell;
                var grave = logic.spawn(graveKind, ground(logic, gx, gy), run.side());
                one.graves.add(grave.getId());
            }
            one.next = logic.getFrame() + yard.everyFrames();
            laid.add(one);
        }
    }

    /** A frame of every graveyard laid: shielded, raising, struck down, gone. */
    void update(GameLogic logic, Run run) {
        int frame = logic.getFrame();
        for (var each = laid.iterator(); each.hasNext(); ) {
            var one = each.next();
            var yard = one.yard;
            var raiser = logic.findObject(one.raiser);
            boolean shielded = raiser != null && !raiser.isEffectivelyDead();
            var graves = new ArrayList<GameObject>();
            for (var id : List.copyOf(one.graves)) {
                var grave = logic.findObject(id);
                if (grave == null || grave.getBody() == null) {
                    one.graves.remove(id);
                    continue;
                }
                var body = grave.getBody();
                if (shielded) {
                    // Nothing it takes holds while its raiser stands before it.
                    body.setHealth(body.getMaxHealth());
                    if (!yard.shieldLook().isBlank() && frame % Math.max(1, yard.shieldFrames()) == 0) {
                        logic.effect(yard.shieldLook(), grave);
                        if (!yard.raiseBeam().isBlank()) {
                            // Its spell on the grave, a line from its hands held while it stands.
                            var from = raiser.getPosition();
                            var to = grave.getPosition();
                            run.beam(yard.raiseBeam(), new Coord3D(from.x(), from.y(), from.z() + Battles.HANDS),
                                    new Coord3D(to.x(), to.y(), to.z() + 2f), Math.max(1, yard.shieldFrames()) + 2);
                        }
                    }
                    graves.add(grave);
                } else if (body.getHealth() <= body.getMaxHealth() * 0.02f) {
                    // Beaten (its UNYIELDING keeps it from dying by a blow): the game strikes it down.
                    if (!yard.brokenLook().isBlank()) {
                        logic.effect(yard.brokenLook(), grave.getPosition(), 0f);
                    }
                    run.struck(grave);
                    grave.markDestroyed();
                    one.graves.remove(id);
                } else {
                    graves.add(grave);
                }
            }
            if (graves.isEmpty()) {
                if (!shielded) {
                    run.flag(yard.goneFlag());
                    each.remove();
                }
                continue;
            }
            one.standing.removeIf(id -> {
                var risen = logic.findObject(id);
                return risen == null || risen.isEffectivelyDead();
            });
            if (frame >= one.next && one.standing.size() < yard.mostStanding() && !yard.raises().isEmpty()) {
                one.next = frame + yard.everyFrames();
                var grave = graves.get(one.nextGrave++ % graves.size());
                rise(logic, run, one, grave, raiser, shielded);
            }
        }
    }

    /** One of the dead climbs out of {@code grave}: paying while the graveyard has paid fewer than it may, changed after. */
    private void rise(GameLogic logic, Run run, Laid one, GameObject grave, GameObject raiser, boolean raiserStands) {
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
        if (raiserStands && !yard.raiseBeam().isBlank()) {
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
