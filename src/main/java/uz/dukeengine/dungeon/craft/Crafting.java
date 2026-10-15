package uz.dukeengine.dungeon.craft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import uz.dukeengine.dungeon.loot.Items;
import uz.dukeengine.rpg.item.LootBag;

/**
 * Crafting: the recipes, their tags, the scroll that teaches them and the words the Hunar screen says -- the one
 * {@code Crafting} block of {@code data/world/crafting.duke}. See docs/plan/2026-10-15-crafting.md. The work itself is
 * the food's busy state ({@code run.Camp}); this only says what a recipe takes and whether a bag has it.
 *
 * @param frames      how long a recipe that says none takes
 * @param scroll      the Potion that, taken into the bag, teaches the first recipe of the file not yet known
 * @param tags        {@code "qilich = xanjar qisqa_qilich"}: a {@code #qilich} part is any thing on those bases
 * @param recipes     in the order the screen lists them and a scroll teaches them
 * @param title       the screen's heading, and its word on the HUD's key hints
 * @param learnWord   said when a scroll teaches one; {@code %s} the thing it makes
 * @param missingWord said when a craft is asked of a bag that lacks something
 * @param unknownWord said when a craft is asked of a recipe he does not know
 * @param goldWord    after a recipe's gold on the screen
 * @param secondsWord after its seconds
 * @param toLearnWord under the list: {@code %d} the recipes left to learn
 * @param noneWord    what the screen says with no recipe known
 */
public record Crafting(int frames, String scroll, List<String> tags, List<Recipe> recipes, String title,
        String learnWord, String missingWord, String unknownWord, String goldWord, String secondsWord,
        String toLearnWord, String noneWord) {

    public static final Crafting DEFAULTS = new Crafting(75, "", List.of(), List.of(), "Crafting",
            "Learnt: %s", "Something is missing", "Not a recipe he knows", "gold", "s", "%d to learn",
            "No recipe known");

    /** The flag a recipe learnt sets in the run's script: a quest teaches one by setting it. */
    public static final String FLAG = "hunar:";

    public Crafting {
        tags = tags == null ? List.of() : List.copyOf(tags);
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }

    /**
     * One recipe -- see the spec's "Writing a recipe".
     *
     * @param makes  a base, a unique or a Potion
     * @param count  how many of a Potion
     * @param rarity a base's rarity, drawn out of 1000 -- common, magic, rare, epic; none: common
     * @param level  the least item level it is made at; the hero's level when more
     * @param from   what it takes, each {@code name [count]}: see {@link Part}
     * @param frames how long it takes; 0 the block's
     * @param known  known from the start
     * @param hero   the hero whose own it is; blank for every hero
     */
    public record Recipe(String name, String makes, int count, List<Integer> rarity, int level, List<String> from,
            int gold, int frames, boolean known, String hero) {

        public static final Recipe DEFAULTS = new Recipe("", "", 1, List.of(), 1, List.of(), 0, 0, false, "");

        public Recipe {
            rarity = rarity == null ? List.of() : List.copyOf(rarity);
            from = from == null ? List.of() : List.copyOf(from);
        }

        public List<Part> parts() {
            return from.stream().map(Part::of).toList();
        }
    }

    /**
     * A part of a recipe: {@code otin 3} a material, {@code keng_qilich/R} a thing on that base (or that unique) of
     * rare or better, {@code *Uzuk/M 2} two things of that slot, {@code #qilich} a thing of that tag.
     */
    public record Part(String what, int least, int count) {

        private static final String RARITIES = "CMRE";

        public static Part of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length < 1 || w.length > 2 || w[0].isEmpty()) {
                throw new IllegalArgumentException("'" + written + "' is a name and how many");
            }
            int count = w.length == 2 ? Integer.parseInt(w[1]) : 1;
            int cut = w[0].indexOf('/');
            int least = cut < 0 ? 0 : RARITIES.indexOf(w[0].charAt(cut + 1));
            if (cut >= 0 && (least < 0 || w[0].length() != cut + 2) || count < 1) {
                throw new IllegalArgumentException("'" + written + "': a least rarity is one of /C /M /R /E, a count 1 or more");
            }
            return new Part(cut < 0 ? w[0] : w[0].substring(0, cut), least, count);
        }
    }

    /** The bases a {@code #tag} names, or none. */
    public List<String> tag(String name) {
        for (var line : tags) {
            int at = line.indexOf('=');
            if (at > 0 && line.substring(0, at).strip().equals(name)) {
                return List.of(line.substring(at + 1).strip().split("\\s+"));
            }
        }
        return List.of();
    }

    /** What the backpack holds of a recipe's parts: whether each is there, and the things it would spend. */
    public record Match(List<Boolean> have, List<Integer> things) {

        public boolean all() {
            return !have.contains(false);
        }
    }

    /**
     * Whether {@code bag}'s backpack -- never what he wears -- holds each of {@code recipe}'s parts, and which of its
     * places the things would be spent from: the least rare that fits first, and a thing named by its base before one
     * of a slot or a tag, so a part never takes what a stricter one needs.
     */
    public Match match(Recipe recipe, LootBag bag, Items items) {
        var parts = recipe.parts();
        var have = new ArrayList<Boolean>(parts.size());
        parts.forEach(part -> have.add(false));
        var taken = new ArrayList<Integer>();
        // ponytail: greedy, the strict parts first; a recipe whose loose part can only be met by what a stricter one
        // leaves is the data's to avoid.
        var order = new ArrayList<Integer>();
        for (int i = 0; i < parts.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> loose(parts.get(i))));
        for (int i : order) {
            var part = parts.get(i);
            if (items.potion(part.what()) != null) {
                have.set(i, materials(bag, items, part.what()) >= part.count());
                continue;
            }
            var fits = new ArrayList<Integer>();
            for (int place = 0; place < bag.backpackSize(); place++) {
                if (!taken.contains(place) && fits(part, bag.at(place), items)) {
                    fits.add(place);
                }
            }
            fits.sort(Comparator.comparingInt(place -> items.rarityOf(bag.at(place))));
            if (fits.size() >= part.count()) {
                have.set(i, true);
                taken.addAll(fits.subList(0, part.count()));
            }
        }
        return new Match(List.copyOf(have), List.copyOf(taken));
    }

    private static int loose(Part part) {
        return part.what().startsWith("*") || part.what().startsWith("#") ? 1 : 0;
    }

    /** How many of a material the backpack holds, all its stacks. */
    public static int materials(LootBag bag, Items items, String potion) {
        int count = 0;
        for (int place = 0; place < bag.backpackSize(); place++) {
            var kind = items.potionOf(bag.at(place));
            if (kind != null && kind.name().equals(potion)) {
                count += bag.at(place).value();
            }
        }
        return count;
    }

    private boolean fits(Part part, uz.dukeengine.rpg.item.Loot thing, Items items) {
        var made = thing == null ? null : items.made(thing.id());
        if (made == null || made.base() == null || made.potion() != null || made.rarity() < part.least()) {
            return false;
        }
        var what = part.what();
        if (made.unique() != null) {
            return made.unique().name().equals(what); // a unique is never spent but by its own name
        }
        if (what.startsWith("*")) {
            return made.base().slot().equals(what.substring(1));
        }
        if (what.startsWith("#")) {
            return tag(what.substring(1)).contains(made.base().name());
        }
        return made.base().name().equals(what);
    }
}
