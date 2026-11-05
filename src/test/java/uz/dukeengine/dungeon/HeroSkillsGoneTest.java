package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * The heroes have no skills to cast or buy (PLAN-dungeon-action.md): what is left in their books is the mechanics the
 * game itself needs -- the dodge, the stagger, the horse and the landing's push -- while the monsters still cast through
 * the same SkillBook.
 */
class HeroSkillsGoneTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final Set<SkillEffect> MECHANICS =
            Set.of(SkillEffect.MODULE, SkillEffect.STAGGER, SkillEffect.MOUNT, SkillEffect.PUSH);

    @Test
    void aHeroBookHoldsOnlyTheMechanicsTheGameNeeds() {
        for (var hero : SETTINGS.heroes()) {
            assertTrue(SETTINGS.skillsFor(hero.name()).isEmpty(), hero.name() + " has no slot");
            for (var skill : SETTINGS.bookFor(hero.name())) {
                assertTrue(MECHANICS.contains(skill.effect()), hero.name() + "'s " + skill.key() + " is a skill");
            }
        }
    }

    @Test
    void theBarSaysNothingOfSkillsOrPoints() {
        var session = Dungeon.newSession(4321L);
        session.game().runHeadless(2);
        var line = session.game().getSnapshot().status();

        assertFalse(line.contains("|skill="), line);
        assertFalse(line.contains("|pts="), line);
        assertFalse(line.contains("|cmd="), line);
    }

    /** The skeleton mage still has its fireball in a SkillBook, which its brain casts (MonsterSkillTest fights it). */
    @Test
    void aMonsterStillCastsThroughItsSkillBook() {
        var world = Dungeon.world("##########\n#........#\n#........#\n#........#\n##########\n", SETTINGS);
        var game = world.game();
        game.spawn("SkeletonMage", world.dungeon(), 60f, 20f);
        game.runHeadless(1);
        var mage = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals("SkeletonMage")).findFirst().orElseThrow();
        var book = mage.findModule(SkillBook.class);

        assertTrue(book != null && !book.getSkills().isEmpty(), "a book with something to cast");
        assertEquals(SETTINGS.skillsFor("SkeletonMage").size(), book.getSkills().size(), "the file's skills");
    }
}
