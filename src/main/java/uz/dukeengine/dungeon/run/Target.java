package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.hero.LevelBonus;

/**
 * What the target frame shows of the one creature he has picked out to look at: the hero's own panel stays his, and
 * this is the small window beside it — WoW's target frame.
 *
 * <p>Worked out on the simulation's thread, where a creature's figures live, and handed to the window whole, as the
 * mission list is. Its buffs are not here: the engine's {@code BuffScreen} already reads what is on the creature
 * picked alone, and the frame draws them from there.
 *
 * @param id        the creature, for its buffs
 * @param name      what the player calls it
 * @param role      a person's word for what they are ({@code Savdogar}), or blank
 * @param level     its level, or 0 for one that holds none (a person)
 * @param attack    what it hits for, or 0 for one that does not
 * @param armour    what a hero shrugs off, in percent, or 0
 * @param maxHealth its whole health, or 0 for one with no body -- a person, whom nothing can hurt
 * @param letter    the first letter of its name, framed where a portrait would be
 */
public record Target(int id, String name, String role, int level, int health, int maxHealth, int attack,
        int armour, String letter) {

    /**
     * The frame for {@code creature}, or null for none: nothing picked, his own hero, or one that has fallen.
     *
     * @param itsProgress the creature's own progress when it is a hero — a fellow in a party — else null
     */
    static Target of(GameObject creature, GameObject hero, HeroProgress itsProgress, DungeonSettings settings) {
        if (creature == null || creature == hero || creature.isEffectivelyDead()) {
            return null;
        }
        var name = Titled.of(creature.getTemplate());
        var npc = settings.story().npc(creature.getTemplate().name());
        var body = creature.getBody();
        int level;
        int attack;
        int armour = 0;
        if (itsProgress != null) {
            level = itsProgress.getLevel();
            attack = HeroStatus.attackOf(creature, itsProgress);
            armour = HeroStatus.armourOf(itsProgress, settings, true);
        } else {
            level = creature.findModule(LevelBonus.class) == null ? 0 : LevelBonus.levelOf(creature);
            attack = HeroStatus.blowOf(creature);
        }
        return new Target(creature.getId().value(), name, npc == null ? "" : npc.role(), level,
                body == null ? 0 : Math.round(body.getHealth()), body == null ? 0 : Math.round(body.getMaxHealth()),
                attack, armour,
                name.isEmpty() ? "?" : name.substring(0, 1));
    }
}
