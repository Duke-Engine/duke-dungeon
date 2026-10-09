package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.dungeon.story.Boss;
import uz.dukeengine.dungeon.story.Script;
import uz.dukeengine.dungeon.story.Story;
import uz.dukeengine.dungeon.story.Trigger;

/**
 * The floor's bosses as the story fights them — see {@code docs/plan/2026-10-09-final-fight.md}: every creature a
 * {@link Boss} block names, taken up the frame it stands on the floor; beaten instead of killed where its block says
 * so; and its phases begun in turn, each once. In the simulation, iterated as lists, so the same on every machine. Not
 * saved: a save is not written while one stands (see {@link #on}), and a load lays the floor fresh.
 */
final class BossFight {

    /** What a phase's actions are done by: the run, which has the world — {@code caller} the boss whose phase it is. */
    interface Acts {
        void act(Trigger.Action action, GameObject caller);
    }

    private static final class Fighter {
        final ObjectId id;
        final Boss boss;
        final Fighting held;
        /** The phase it begins next: its count of phases once all are begun. */
        int next;
        boolean beaten;

        Fighter(ObjectId id, Boss boss, Fighting held) {
            this.id = id;
            this.boss = boss;
            this.held = held;
        }
    }

    private final Story story;
    private final List<Fighter> fighters = new ArrayList<>();

    BossFight(Story story) {
        this.story = story;
    }

    /** A floor laid: nobody fought yet. */
    void reset() {
        fighters.clear();
    }

    /** Whether a boss stands unbeaten on the floor: the fight is on, and no save is written. */
    boolean on() {
        return fighters.stream().anyMatch(each -> !each.beaten);
    }

    /** A frame of the fight: who joined it, who was beaten — heard as BEATEN — and each one's next phase, if due. */
    void tick(GameLogic logic, Script script, Acts acts, int nobodysSide) {
        if (story.bosses().isEmpty()) {
            return;
        }
        for (var thing : logic.getObjects()) {
            var boss = story.boss(thing.getTemplate().name());
            if (boss != null && !thing.isEffectivelyDead() && thing.getBody() != null
                    && fighters.stream().noneMatch(each -> each.id.equals(thing.getId()))) {
                var held = new Fighting(thing);
                thing.addModule(held);
                fighters.add(new Fighter(thing.getId(), boss, held));
            }
        }
        for (var fighter : List.copyOf(fighters)) {
            var thing = logic.findObject(fighter.id);
            if (thing == null || thing.isEffectivelyDead()) {
                fighters.remove(fighter); // its death is heard as every death is: DIED
                continue;
            }
            if (fighter.beaten) {
                continue;
            }
            // Beaten: its UNYIELDING (a passive in its Monster block) held it at its floor — the engine says so (E28).
            var book = thing.findModule(uz.dukeengine.rpg.skill.SkillBook.class);
            if (book != null && book.isBeaten()) {
                fighter.beaten = true;
                beat(logic, thing, fighter.boss, nobodysSide);
                script.heard(Trigger.When.BEATEN, fighter.boss.name(), 0);
                continue;
            }
            if (fighter.next < fighter.boss.phases().size()) {
                var phase = fighter.boss.phases().get(fighter.next);
                var body = thing.getBody();
                if (body.getHealth() * 100f <= phase.below() * body.getMaxHealth()
                        && (phase.afterFlag().isBlank() || script.flagged(phase.afterFlag()))) {
                    fighter.next++;
                    body.setDamageScale(phase.damageTaken() / 100f);
                    fighter.held.castOnly(phase.skills());
                    for (var action : phase.actions()) {
                        acts.act(action, thing);
                    }
                }
            }
        }
    }

    /**
     * Beaten: it kneels where it is, and fights no more — on nobody's side, so nothing is its enemy or takes it for
     * one; disabled for good, so its legs, its weapon and its skills stand still; and no blow takes anything from it.
     */
    private static void beat(GameLogic logic, GameObject thing, Boss boss, int nobodysSide) {
        thing.setPlayerIndex(nobodysSide);
        thing.getBody().setDamageScale(0f);
        var timers = thing.findModule(uz.dukeengine.combat.module.StatusUpdate.class);
        if (timers != null) {
            timers.apply(ObjectStatus.DISABLED, Integer.MAX_VALUE); // a stun burning on it would lift this as it ends
        }
        thing.setStatus(ObjectStatus.DISABLED);
        uz.dukeengine.combat.module.Errand.giveUpAll(thing);
        var legs = thing.getLocomotor();
        if (legs != null && legs.isMoving()) {
            legs.stop();
        }
        if (!boss.beatenLook().isBlank()) {
            logic.effect(boss.beatenLook(), thing);
        }
    }
}
