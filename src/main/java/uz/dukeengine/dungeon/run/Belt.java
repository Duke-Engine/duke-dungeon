package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The belt: the first potion stacks of the backpack, in its order, drunk with keys 1-4 — and stacks of one potion
 * joined into the first as they come in. Nothing of its own but a cooldown, kept by the run: what is on the belt is
 * what is in the bag, so it is saved with the bag. See docs/plan/2026-10-15-loot.md.
 */
public final class Belt {

    private Belt() {
    }

    /** The backpack places of the belt's stacks, in order, at most {@code size}. */
    public static List<Integer> places(LootBag bag, Items items, int size) {
        var out = new ArrayList<Integer>();
        for (int place = 0; place < bag.backpackSize() && out.size() < size; place++) {
            if (items.potionOf(bag.at(place)) != null) {
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
     * stack, an empty one gone. False when there is nothing there, or no body to drink it.
     */
    public static boolean drink(GameObject hero, LootBag bag, Items items, int slot) {
        var places = places(bag, items, items.rules().beltSize());
        if (hero == null || hero.isEffectivelyDead() || hero.getBody() == null || slot < 0
                || slot >= places.size()) {
            return false;
        }
        int place = places.get(slot);
        var stack = bag.at(place);
        var potion = items.potionOf(stack);
        var body = hero.getBody();
        if (potion.heal() > 0) {
            body.heal(body.getMaxHealth() * potion.heal() / 100f);
        }
        var book = hero.findModule(uz.dukeengine.rpg.skill.SkillBook.class);
        if (potion.mana() > 0 && book != null && book.getMaxMana() > 0) {
            book.restoreMana(Math.max(1, book.getMaxMana() * potion.mana() / 100));
        }
        bag.remove(place);
        if (stack.value() > 1) {
            bag.put(place, stack.worth(stack.value() - 1));
        }
        return true;
    }
}
