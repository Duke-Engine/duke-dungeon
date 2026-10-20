package uz.dukeengine.dungeon.story;

import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.dungeon.Dungeon;

/**
 * For a test of what comes after the opening: the intro played, every line read and every cutscene seen, and the first
 * wave out of the graves struck down as it comes, until the line's two fall back (BattleWon, open-world Act 1).
 */
public final class Wave {

    private Wave() {
    }

    /** Played to the first wave beaten, at most {@code frames} of the intro first. */
    public static boolean beaten(Dungeon.Session session, int frames) {
        var run = session.run();
        var game = session.game();
        for (int i = 0; i < frames && run.getQuests().active().stream().noneMatch(q -> q.quest().equals("FirstWave"))
                && !run.getScript().flagged("BattleWon"); i++) {
            play(session);
        }
        for (int i = 0; i < 120 && !run.getScript().flagged("BattleWon"); i++) {
            var hero = game.getLogic().getObjects().stream()
                    .filter(o -> o.getTemplate().name().equals(run.getHeroTemplate())).findFirst().orElse(null);
            game.getLogic().getObjects().stream()
                    .filter(o -> o.getTemplate().name().equals("Skeleton") && !o.isEffectivelyDead()).findFirst()
                    .ifPresent(foe -> foe.getBody().damage(1e7f, DamageType.NORMAL, hero == null ? null
                            : new Death(DeathType.NORMAL, hero.getId(), hero.getPlayerIndex())));
            play(session);
            play(session);
        }
        return run.getScript().flagged("BattleWon");
    }

    private static void play(Dungeon.Session session) {
        session.run().read(session.game().getLocalPlayerIndex(), 10_000);
        if (session.run().inCutscene()) {
            session.run().skipCutscene(session.game());
        }
        session.game().runHeadless(1);
    }
}
