package uz.dukeengine.dungeon.loot;

import java.util.List;
import uz.dukeengine.rpg.skill.Skill;

/**
 * An epic thing: written by hand, its figures fixed, with a line of its story. See docs/plan/2026-10-15-loot.md.
 *
 * @param base      the base it is made on: its slot, figure and picture
 * @param lore      the line of its story its card says, in gold
 * @param level     the item level it is found from
 * @param gives     its affixes, each a figure and how much: {@code ATTACK 14}, as an Affix's Stat says them
 * @param attribute for a base of ATTRIBUTE, which one it gives
 * @param skills    the passives it lends besides: an aura
 */
public record Unique(String name, String displayName, String base, String lore, int level, int weight,
        List<String> gives, String attribute, List<Skill> skills) {

    public static final Unique DEFAULTS = new Unique("", "", "", "", 1, 10, List.of(), "", List.of());

    public Unique {
        gives = gives == null ? List.of() : List.copyOf(gives);
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
