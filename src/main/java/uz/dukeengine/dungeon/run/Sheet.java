package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Titled;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.hero.HeroFigures;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.skill.SkillBook;

/**
 * What the character sheet shows of this machine's hero: worked out on the simulation's thread, where his figures
 * live, from the same sums his panel and his body use ({@link HeroProgress#figuresOf}, {@link HeroStatus#armourOf},
 * {@link HeroProgress#sheetOf} for what his body does each second),
 * and handed to the window whole, as the target frame is. Numbers and names only: the sheet says them in the data's
 * words.
 *
 * @param id              his body, for its live portrait
 * @param attributes      his attributes in the file's order, with what he has found and without it
 * @param damage          what a blow of his is worth, everything found counted
 * @param blowsPerSecond  how many blows a second his weapon gives, in hundredths, his things' quickness counted
 * @param attackSpeed     how much quicker his things make his blows, in percent
 * @param armour          how much of a blow he turns aside, in percent
 * @param speed           how fast he walks
 * @param healthRegen     the health a second that comes back to him, in tenths
 * @param manaRegen       and the mana
 * @param lifesteal       the share of a blow he drinks back as health, in percent
 * @param worn            what he wears, a slot each, in the bag's order; empty for a bag that wears nothing
 * @param souls           the souls his medallion has drunk, or -1 while he does not wear it
 */
public record Sheet(int id, String name, String title, int level, int experience, int experienceToGo,
        List<Attribute> attributes, int damage, int blowsPerSecond, int attackSpeed, int armour, int speed,
        int health, int maxHealth, int mana, int maxMana, int healthRegen, int manaRegen, int lifesteal,
        List<Worn> worn, int gold, int souls) {

    public Sheet {
        attributes = List.copyOf(attributes);
        worn = List.copyOf(worn);
    }

    /** One attribute: its place in the file's list, what he has, what he would have bare, whether it is his primary. */
    public record Attribute(int index, int value, int bare, boolean primary) {
    }

    /** One worn slot: its word, and the thing in it -- its picture and name -- or blanks for nothing. */
    public record Worn(String slot, String icon, String name) {

        public boolean empty() {
            return name.isEmpty();
        }
    }

    /** The sheet of {@code hero}, whose progress is {@code progress}; null for no hero. */
    static Sheet of(GameObject hero, HeroProgress progress, int gold, int souls, DungeonSettings settings) {
        if (hero == null || hero.getBody() == null || progress == null) {
            return null;
        }
        var now = progress.figuresOf(hero, progress.found());
        var bare = progress.figuresOf(hero, HeroFigures.Found.NOTHING);
        var his = progress.getHero();
        var attributes = new ArrayList<Attribute>();
        if (his.attributes().hasPrimary()) {
            for (int i = 0; i < settings.attributeArt().size(); i++) {
                attributes.add(new Attribute(i, now.attributes().whole(i), bare.attributes().whole(i),
                        i == his.attributes().primary()));
            }
        }
        var bag = progress.getLoot();
        var book = hero.findModule(SkillBook.class);
        // The engine's own figures a second (E47), worked out by the code that applies them: a worn thing's lent
        // lifesteal and every haste included.
        var rates = progress.sheetOf(hero).rates();
        var medallion = settings.medallion().item();
        boolean wearsIt = !medallion.isBlank() && bag.acting().stream().anyMatch(item -> item.id().equals(medallion));
        return new Sheet(hero.getId().value(), Titled.of(hero.getTemplate()), HeroStatus.titleOf(hero, settings),
                progress.getLevel(), progress.getExperienceIntoLevel(), progress.getExperienceForNextLevel(),
                attributes, Math.round(now.attack()), Math.round(rates.blowsPerSecond() * 100f),
                bag.attackSpeedPercent(), HeroStatus.armourOf(progress, settings, true),
                Math.round(now.speed()), Math.round(hero.getBody().getHealth()),
                Math.round(hero.getBody().getMaxHealth()), book == null ? 0 : book.getMana(),
                book == null ? 0 : book.getMaxMana(), Math.round(rates.healthPerSecond() * 10f),
                Math.round(rates.manaPerSecond() * 10f), rates.lifestealPercent(), worn(bag), gold,
                wearsIt ? souls : -1);
    }

    /** What he wears, a slot each. */
    private static List<Worn> worn(LootBag bag) {
        var worn = new ArrayList<Worn>();
        for (var slot : bag.wornSlots()) {
            var item = bag.at(bag.wornPlace(slot));
            worn.add(item == null ? new Worn(slot, "", "") : new Worn(slot, item.icon(), LootBag.nameOf(item)));
        }
        return worn;
    }

}
