package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.content.EffectList;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.Feel;

/** What a fight feels like: which hits and deaths knock the camera, and that every body has a way to fall. */
class FeelTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The people who are placed in a story and never killed in play: one rig, an Idle and no fall. */
    private static final Set<String> NEVER_KILLED = Set.of("Imp", "BabaYaga", "Damsel", "DamselCaged", "FreedDamsel", "EmptyCage");

    private static Visuals wired() {
        var visuals = Visuals.create();
        visuals.effectLists(SETTINGS.effectLists());
        Feel.apply(visuals, SETTINGS);
        return visuals;
    }

    private static boolean shakes(String list) {
        return SETTINGS.effectLists().stream().filter(each -> each.name().equals(list)).findFirst()
                .orElseThrow().entries().stream().anyMatch(EffectList.Shake.class::isInstance);
    }

    @Test
    void everyFeltBlowAndDeathPlaysAListThatKnocksTheCamera() {
        assertFalse(SETTINGS.heavyBlows().isEmpty());
        assertFalse(SETTINGS.bigDeaths().isEmpty());
        SETTINGS.heavyBlows().forEach(blow -> assertTrue(shakes(blow.plays()), blow.template() + " plays " + blow.plays()));
        SETTINGS.bigDeaths().forEach(death -> assertTrue(shakes(death.plays()), death.template() + " plays " + death.plays()));
    }

    @Test
    void aHeavyBlowIsMajorOnlyOnTheCreaturesTheFilesNameAndOnlyFromTheirBar() {
        var visuals = wired();
        assertEquals(45f, visuals.hurtRule("Knight", "NORMAL").majorAt());
        assertEquals(0, visuals.hurtRule("Knight", "NORMAL").throttleFrames(), "the pain sound wants every blow");
        assertEquals("HeroHit", visuals.getMoment("hurt.Knight.normal.major").effect());
        assertEquals("HeavyHit", visuals.getMoment("hurt.Champion.normal.major").effect());
        // A lighter blow is the minor look, and a skeleton -- fodder -- has no look at all.
        assertNull(visuals.getMoment("hurt.Knight.normal.minor"));
        assertNull(visuals.getMoment("hurt.Skeleton.normal.major"));
        assertEquals(Float.POSITIVE_INFINITY, visuals.hurtRule("Skeleton", "NORMAL").majorAt());
    }

    @Test
    void anOrdinaryBlowNeverReachesAnyBar() {
        // The hero's plain swing and the fodder's: a number and a flash, never a knock.
        float lowest = (float) SETTINGS.heavyBlows().stream().mapToDouble(blow -> blow.majorAt()).min().orElseThrow();
        assertTrue(lowest >= 30f, "the lowest bar is " + lowest);
        for (var kind : SETTINGS.monsters()) {
            assertTrue(SETTINGS.heavyBlows().stream().noneMatch(blow -> blow.template().equals(kind.name())
                    && blow.majorAt() < 40f), kind.name() + " is shaken by a small blow");
        }
    }

    @Test
    void onlyBigBodiesShakeTheScreenAsTheyFall() {
        var visuals = wired();
        assertEquals("Slam", visuals.getMoment("died.Champion").effect());
        assertEquals("Slam", visuals.getMoment("died.QueenBoss").effect());
        assertNull(visuals.getMoment("died.Skeleton"));
        assertNull(visuals.getMoment("died.Runner"));
    }

    @Test
    void theKnightsWAndRKnockHarderThanTheKitsDid() {
        for (var name : new String[] {"Charge", "Whirlwind"}) {
            var knock = SETTINGS.effects().stream().filter(each -> each.name().equals(name)).findFirst().orElseThrow();
            assertTrue(knock.shakePower() >= 1.5f && knock.shakeSeconds() >= 0.2f, name);
        }
    }

    /** Nothing that can die in play pops out of the world: it has a death clip to play. */
    @Test
    void everyCreatureThatCanDieHasADeathToPlay() {
        var pops = new ArrayList<String>();
        for (var kind : SETTINGS.monsters()) {
            var look = SETTINGS.lookOf(kind);
            if (look.hasModel() && look.death() == null && !NEVER_KILLED.contains(kind.name())) {
                pops.add(kind.name());
            }
        }
        assertTrue(pops.isEmpty(), "these pop when they die: " + pops);
        SETTINGS.heroes().forEach(hero -> assertNotNull(hero.death(), hero.name() + " pops when he dies"));
    }
}
