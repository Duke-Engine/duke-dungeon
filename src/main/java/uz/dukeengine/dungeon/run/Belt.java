package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * The belt: the first stacks of the backpack that are eaten or drunk, in its order, taken with keys 1-4 — and stacks
 * of one kind joined into the first as they come in (wood too, which is never on the belt). Nothing of its own but a
 * cooldown, kept by the run: what is on the belt is what is in the bag, so it is saved with the bag. A meal gives its
 * share over its frames as a buff on his skill book (the owner's rule: buffs are the skill system's) -- health as a
 * {@code HEAL} buff the book heals by (E68), mana as a {@code MANA_AURA} its own trickle reads. See docs/plan/2026-10-15-loot.md and docs/plan/2026-10-15-food-and-fire.md.
 */
public final class Belt {

    private Belt() {
    }

    /** The backpack places of the belt's stacks, in order, at most {@code size}. */
    public static List<Integer> places(LootBag bag, Items items, int size) {
        var out = new ArrayList<Integer>();
        for (int place = 0; place < bag.backpackSize() && out.size() < size; place++) {
            var potion = items.potionOf(bag.at(place));
            if (potion != null && potion.eaten()) {
                out.add(place);
            }
        }
        return out;
    }

    /**
     * Every stack of a potion poured into the first of its kind, up to its {@code Stack}; a stack emptied leaves its
     * place. On the simulation thread, after anything came into the bag.
     */
    public static void stack(LootBag bag, Items items) {
        for (int first = 0; first < bag.backpackSize(); first++) {
            var potion = items.potionOf(bag.at(first));
            if (potion == null) {
                continue;
            }
            for (int later = first + 1; later < bag.backpackSize(); later++) {
                var here = bag.at(first);
                var there = bag.at(later);
                if (here.value() >= potion.stack()) {
                    break;
                }
                if (items.potionOf(there) != potion) {
                    continue;
                }
                int poured = Math.min(there.value(), potion.stack() - here.value());
                bag.remove(first);
                bag.put(first, here.worth(here.value() + poured));
                bag.remove(later);
                if (there.value() > poured) {
                    bag.put(later, there.worth(there.value() - poured));
                }
            }
        }
    }

    /**
     * Drink one of the belt's {@code slot}th stack: its share of his most health and mana back, one fewer in the
     * stack, an empty one gone. What was eaten, or null when there is nothing there, or no body to eat it.
     */
    public static uz.dukeengine.dungeon.loot.Potion drink(GameObject hero, LootBag bag, Items items, int slot) {
        var places = places(bag, items, items.rules().beltSize());
        if (hero == null || hero.isEffectivelyDead() || hero.getBody() == null || slot < 0
                || slot >= places.size()) {
            return null;
        }
        int place = places.get(slot);
        var stack = bag.at(place);
        var potion = items.potionOf(stack);
        eat(hero, potion);
        bag.remove(place);
        if (stack.value() > 1) {
            bag.put(place, stack.worth(stack.value() - 1));
        }
        return potion;
    }

    /**
     * What a meal gives: at once for one with no frames (the old potions), else a buff on his book worth tenths of a
     * point a second, health ({@code HEAL}) and mana ({@code MANA_AURA}), holding its frames from this one. His own id
     * lends it, so a second meal replaces the first rather than adding to it.
     */
    static void eat(GameObject hero, uz.dukeengine.dungeon.loot.Potion potion) {
        var body = hero.getBody();
        var book = hero.findModule(SkillBook.class);
        boolean pool = book != null && book.getMaxMana() > 0;
        var world = hero.getWorld();
        if (potion.frames() <= 0 || book == null || world == null) {
            if (potion.heal() > 0) {
                body.heal(body.getMaxHealth() * potion.heal() / 100f);
            }
            if (potion.mana() > 0 && pool) {
                book.restoreMana(Math.max(1, book.getMaxMana() * potion.mana() / 100));
            }
            return;
        }
        int until = world.getFrame() + potion.frames() - 1;
        int lender = hero.getId().value();
        if (potion.heal() > 0) {
            book.give(SkillEffect.HEAL, lender, tenthsASecond(body.getMaxHealth() * potion.heal() / 100f,
                    potion.frames()), until);
        }
        if (potion.mana() > 0 && pool) {
            book.give(SkillEffect.MANA_AURA, lender, tenthsASecond(book.getMaxMana() * potion.mana() / 100f,
                    potion.frames()), until);
        }
    }

    private static int tenthsASecond(float total, int frames) {
        return Math.max(1, Math.round(total * 10f * GameConstants.LOGICFRAMES_PER_SECOND / frames));
    }
}
