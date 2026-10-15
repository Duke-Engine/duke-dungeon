package uz.dukeengine.dungeon.food;

import java.util.List;

/**
 * Food and fire: what is cut, picked, filled, hunted and cooked, how long each takes, what a fire costs and reaches, and
 * where the world's things stand -- the one {@code Food} block of {@code data/world/food.duke}. See
 * docs/plan/2026-10-15-food-and-fire.md. What is eaten is the {@code Potion} blocks of {@code data/world/loot.duke}.
 *
 * @param fireRadius   how near a fire, middle to middle, a hero cooks
 * @param buildWood    the wood a fire he builds costs
 * @param wood         the Potion that is wood
 * @param campfire     the template a fire is
 * @param fireFrames   how long a fire he builds burns; the world's own burn for good
 * @param cookFrames   how long a meal takes to cook
 * @param beatFrames   how often work plays its puff and sound on him
 * @param reach        how near a thing's outline he works it
 * @param buildKey     the key that builds a fire at his feet
 * @param buildWord    what that key is called on the Settings screen's list of keys
 * @param noWoodWord   said when he has too little wood; {@code %d} what a fire costs
 * @param nothingWord  said at a fire when he has nothing a recipe takes
 * @param recipes      best first: a click on a fire cooks the first he has everything for
 * @param gathers      what is worked: a thing, what it gives, how long, when it grows back
 * @param hunts        what an animal's death leaves
 * @param forage       where the world's things stand
 */
public record Food(int fireRadius, int buildWood, String wood, String campfire, int fireFrames, int cookFrames,
        int beatFrames, int reach, String buildKey, String buildWord, String noWoodWord, String nothingWord,
        List<Recipe> recipes, List<Gather> gathers, List<Hunt> hunts, List<Forage> forage) {

    public static final Food DEFAULTS = new Food(30, 4, "", "", 5400, 105, 30, 10, "G", "", "", "", List.of(),
            List.of(), List.of(), List.of());

    /** The word a click on a fire gives: the cook's. */
    public static final String COOK = "Cook";
    /** Every word a click on a thing to work may give, each the name of its cursor. */
    public static final List<String> WORDS = List.of("Chop", "Harvest", "Fill", COOK);

    public Food {
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        gathers = gathers == null ? List.of() : List.copyOf(gathers);
        hunts = hunts == null ? List.of() : List.copyOf(hunts);
        forage = forage == null ? List.of() : List.copyOf(forage);
    }

    /** What working {@code template} is, or null for a thing nobody works. */
    public Gather gather(String template) {
        for (var gather : gathers) {
            if (gather.thing().equals(template)) {
                return gather;
            }
        }
        return null;
    }

    /** What a click on a thing of {@code template} gives: its work's word, the cook's on a fire; null for none. */
    public String wordOn(String template) {
        var gather = gather(template);
        return gather != null ? gather.word() : template.equals(campfire) ? COOK : null;
    }

    /** A meal and what it is cooked from -- written {@code shorva = xom_gosht qovoq}. */
    public record Recipe(String makes, List<String> from) {

        public static Recipe of(String written) {
            var sides = written.split("=");
            if (sides.length != 2 || sides[0].isBlank() || sides[1].isBlank()) {
                throw new IllegalArgumentException("'" + written + "' is a meal = what it is cooked from");
            }
            return new Recipe(sides[0].strip(), List.of(sides[1].strip().split("\\s+")));
        }
    }

    /**
     * A thing worked -- written {@code ChoppableTree otin 3 90 18000 Chop Stump}: its template, the Potion it gives and
     * how many, the frames it takes, the frames it takes to grow back (0: it is never used up), the word a click on it
     * gives, and what stands in its place meanwhile ({@code -} for nothing).
     */
    public record Gather(String thing, String item, int count, int frames, int regrow, String word, String leaves) {

        public static Gather of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 7) {
                throw new IllegalArgumentException("'" + written
                        + "' is a thing, what it gives, how many, frames, frames to grow back, its word, what it leaves");
            }
            return new Gather(w[0], w[1], Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]), w[5],
                    w[6].equals("-") ? "" : w[6]);
        }
    }

    /** What an animal leaves -- written {@code Deer xom_gosht 2}: its template, the Potion and how many. */
    public record Hunt(String animal, String item, int count) {

        public static Hunt of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 3) {
                throw new IllegalArgumentException("'" + written + "' is an animal, what it leaves and how many");
            }
            return new Hunt(w[0], w[1], Integer.parseInt(w[2]));
        }
    }

    /**
     * Things the world stands about a zone -- written {@code world BerryBush Olmazor 3 3 9}: the map, the template, the
     * zone, how many, and between which distances of the zone's middle, in cells.
     */
    public record Forage(String map, String thing, String zone, int count, int from, int to) {

        public static Forage of(String written) {
            var w = written.strip().split("\\s+");
            if (w.length != 6) {
                throw new IllegalArgumentException("'" + written
                        + "' is a map, a thing, a zone, how many, and from and to how many cells of its middle");
            }
            return new Forage(w[0], w[1], w[2], Integer.parseInt(w[3]), Integer.parseInt(w[4]),
                    Integer.parseInt(w[5]));
        }
    }
}
