package uz.dukeengine.dungeon.loot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import uz.dukeengine.dungeon.gen.DeterministicRng;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.Stat;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillEffect;

/**
 * Every generated thing: rolled, named, read back from its id, priced and said on a card. See
 * docs/plan/2026-10-15-loot.md.
 *
 * <p>A generated thing is one engine {@link Loot} whose id is how to make it again — {@code keng_qilich-R7-1k3x9a}:
 * its base (or an epic's unique, or a potion), its rarity's letter and item level, and its seed in base 36. Rolling is
 * a pure function of those, so a save keeps the id alone, every machine of a party rolls the same thing off the same
 * seed, and no two things share an id (so the engine's three-alike join never takes them).
 *
 * <p>Its affixes are the engine's {@code Affix}es (E43), each a figure and how much, added up by the bag for what he
 * wears; lifesteal alone is a passive {@code LIFESTEAL} skill it lends, as every lifesteal is.
 */
public final class Items {

    public static final int COMMON = 0;
    public static final int MAGIC = 1;
    public static final int RARE = 2;
    public static final int EPIC = 3;
    /** A potion stack's figure: a word nothing adds up, so a stack in the backpack moves nothing. */
    public static final String POTION = "POTION";
    /** An affix lent as a passive {@code LIFESTEAL}: percent of every blow back as health. */
    public static final String LIFESTEAL = "LIFESTEAL";

    /** The base's own figures: an affix of one is added to the thing's {@code Value}. */
    static final Set<String> FIGURES = Set.of(Stat.ATTACK, Stat.ARMOUR, Stat.HEALTH, Stat.MANA, Stat.ATTRIBUTE);
    /** Every figure an affix may move (an attribute's by its name, a skill's rank as {@code SKILL_RANK:Q}). */
    public static final Set<String> STATS = Set.of(Stat.ATTACK, Stat.ARMOUR, Stat.HEALTH, Stat.MANA, Stat.ATTRIBUTE,
            Stat.HEALTH_REGEN, Stat.MANA_REGEN, Stat.ATTACK_SPEED, Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE, Stat.MOVE_SPEED,
            Stat.SKILL_RANK, LIFESTEAL);
    /** The order a card and a comparison say figures in. */
    private static final List<String> ORDER = List.of(Stat.ATTACK, Stat.ARMOUR, Stat.HEALTH, Stat.MANA,
            Stat.ATTRIBUTE, Stat.ATTACK_SPEED, Stat.CRIT_CHANCE, Stat.CRIT_DAMAGE, Stat.HEALTH_REGEN, Stat.MANA_REGEN,
            LIFESTEAL, Stat.MOVE_SPEED, Stat.SKILL_RANK);
    private static final String LETTERS = "CMRE";
    private static final List<String> FRAMES = List.of("common", "magic", "rare", "epic");
    /** The highest item level an affix's range is written for; above it, it rolls as at it. */
    static final int TOP_LEVEL = 15;

    /** Where a thing was found, which decides its rarity's chances (and whether it may be a potion). */
    public enum Source { NORMAL, ELITE, BOSS, CHEST, SHOP }

    /** The game's words for each figure, as a card says them. */
    public record Words(String attack, String armour, String health, String mana, String healthRegen,
            String manaRegen, String attackSpeed, String lifesteal, Map<String, String> attributes) {

        public static final Words PLAIN = new Words("Attack", "Armour", "Health", "Mana", "Health/s", "Mana/s",
                "Attack speed", "Lifesteal", Map.of());
    }

    /** A line of a card and its colour, {@code 0xRRGGBB}. */
    public record Line(String text, int colour) {
    }

    /** One affix as rolled: its figure, how much, and the word a magic thing is named with. */
    public record Rolled(String stat, int value, String prefix) {
    }

    /**
     * What a generated thing is, as its id says: its base, the unique it is if epic, the potion it is if one, its
     * rarity ({@code -1} for a potion), item level and seed, the attribute it gives, its base figure and its affixes.
     */
    public record Made(String id, ItemBase base, Unique unique, Potion potion, int rarity, int level, long seed,
            String attribute, int figure, List<Rolled> affixes, String name) {
    }

    private final List<ItemBase> bases;
    private final List<Affix> affixes;
    private final List<Unique> uniques;
    private final List<Potion> potions;
    private final ItemRules rules;
    private final Words words;
    /** The data's own things, by id: the key, the medallion, the old attribute things. */
    private final Map<String, Loot> plain = new LinkedHashMap<>();

    /** The letter that presses a skill's key in the scheme played: a card's "+1 E mahorati" for the W skill, modern. */
    private final java.util.function.UnaryOperator<Character> letterOf;

    public Items(List<ItemBase> bases, List<Affix> affixes, List<Unique> uniques, List<Potion> potions,
            ItemRules rules, Words words, List<Loot> plain) {
        this(bases, affixes, uniques, potions, rules, words, plain, key -> key);
    }

    public Items(List<ItemBase> bases, List<Affix> affixes, List<Unique> uniques, List<Potion> potions,
            ItemRules rules, Words words, List<Loot> plain, java.util.function.UnaryOperator<Character> letterOf) {
        this.letterOf = letterOf;
        this.bases = List.copyOf(bases);
        this.affixes = List.copyOf(affixes);
        this.uniques = List.copyOf(uniques);
        this.potions = List.copyOf(potions);
        this.rules = rules;
        this.words = words;
        plain.forEach(item -> this.plain.putIfAbsent(item.id(), item));
    }

    /** Whether the game has bases to roll: a test's own settings may have none, and drop the old way. */
    public boolean active() {
        return !bases.isEmpty();
    }

    public ItemRules rules() {
        return rules;
    }

    public List<ItemBase> bases() {
        return bases;
    }

    public List<Affix> affixes() {
        return affixes;
    }

    public List<Unique> uniques() {
        return uniques;
    }

    public List<Potion> potions() {
        return potions;
    }

    // ---- rolling ----

    /**
     * What one drop is, drawn from {@code seed}: a potion now and then off an ordinary or elite monster, else a thing
     * of the rarity {@code source} favours, at item level {@code level}.
     */
    public Loot drop(long seed, int level, Source source) {
        var rng = rng(seed, "drop");
        if ((source == Source.NORMAL || source == Source.ELITE) && rng.nextInt(100) < rules.potionPercent()) {
            var potion = potionFor(rng, level);
            if (potion != null) {
                return potion(potion, 1, seed);
            }
        }
        return gear(seed, level, pickIndex(rng, weightsOf(source)));
    }

    /** A thing of {@code rarity} at {@code level}, its base (or unique) drawn from {@code seed}. */
    public Loot gear(long seed, int level, int rarity) {
        var rng = rng(seed, "gear");
        int at = Math.max(1, level);
        if (rarity == EPIC) {
            var open = uniques.stream().filter(unique -> unique.level() <= at && base(unique.base()) != null).toList();
            if (!open.isEmpty()) {
                return loot(unique(pick(rng, open, Unique::weight), at, seed));
            }
            rarity = RARE; // no epic is found this shallow
        }
        var base = pick(rng, basesAt(at), ItemBase::weight);
        return loot(make(base, rarity, at, seed));
    }

    /**
     * What a recipe makes (docs/plan/2026-10-15-crafting.md): a unique as written, at its level at least; a stack of
     * {@code count} of a Potion; or a thing on a base, its rarity drawn by {@code rarity} (out of 1000; none: common,
     * an epic a rare), at {@code level}, rolled from {@code seed}. Null for a name nothing has.
     */
    public Loot craft(String makes, int count, List<Integer> rarity, int level, long seed) {
        int at = Math.max(1, level);
        for (var unique : uniques) {
            if (unique.name().equals(makes) && base(unique.base()) != null) {
                return loot(unique(unique, Math.max(at, unique.level()), seed));
            }
        }
        var potion = potion(makes);
        if (potion != null) {
            return potion(potion, count, seed);
        }
        var base = base(makes);
        return base == null ? null
                : loot(make(base, Math.min(RARE, pickIndex(rng(seed, "craft"), rarity)), at, seed));
    }

    /** {@code count} things for a merchant to sell at {@code level}, each its own seed drawn from {@code seed}. */
    public List<Loot> stock(long seed, int level, int count) {
        var rng = rng(seed, "shop");
        var out = new ArrayList<Loot>();
        for (int i = 0; i < count && active(); i++) {
            long one = rng.nextInt(1 << 30);
            out.add(gear(one, level, pickIndex(rng(one, "rarity"), rules.shop())));
        }
        return out;
    }

    /** A stack of {@code count} of {@code potion}, its id made unique by {@code seed}. */
    public Loot potion(Potion potion, int count, long seed) {
        return new Loot(potion.name() + "-P1-" + Long.toString(Math.abs(seed), 36), potion.displayName(),
                icon(potion.icon(), COMMON), POTION, count, 0, 1, "", "", 0, 0, 1, ItemUse.NONE, "", "", List.of());
    }

    /** The bases found at {@code level}: those not deeper than it and not left {@code BaseWindow} behind. */
    private List<ItemBase> basesAt(int level) {
        var near = bases.stream().filter(base -> base.level() <= level && base.level() >= level - rules.baseWindow())
                .toList();
        if (!near.isEmpty()) {
            return near;
        }
        var under = bases.stream().filter(base -> base.level() <= level).toList();
        if (!under.isEmpty()) {
            return under;
        }
        int least = bases.stream().mapToInt(ItemBase::level).min().orElse(1);
        return bases.stream().filter(base -> base.level() == least).toList();
    }

    private Potion potionFor(DeterministicRng rng, int level) {
        var open = potions.stream().filter(potion -> potion.level() <= level && potion.weight() > 0).toList();
        return open.isEmpty() ? null : pick(rng, open, Potion::weight);
    }

    private List<Integer> weightsOf(Source source) {
        return switch (source) {
            case NORMAL -> rules.normal();
            case ELITE -> rules.elite();
            case BOSS -> rules.boss();
            case CHEST -> rules.chest();
            case SHOP -> rules.shop();
        };
    }

    /** A thing on {@code base} of {@code rarity} at {@code level}, rolled from {@code seed}: what its id makes. */
    public Made make(ItemBase base, int rarity, int level, long seed) {
        var rng = rng(seed, base.name());
        var attribute = base.attributes().isEmpty() ? ""
                : base.attributes().get(rng.nextInt(base.attributes().size()));
        int figure = between(rng, base.value().get(0), base.value().get(base.value().size() - 1));
        int count = switch (rarity) {
            case MAGIC -> between(rng, rules.magicAffixes().get(0), rules.magicAffixes().getLast());
            case RARE -> between(rng, rules.rareAffixes().get(0), rules.rareAffixes().getLast());
            default -> 0;
        };
        var rolled = new ArrayList<Rolled>();
        var taken = new HashSet<String>();
        for (int i = 0; i < count; i++) {
            var open = affixes.stream().filter(affix -> fits(affix, base) && !taken.contains(affix.name())).toList();
            if (open.isEmpty()) {
                break;
            }
            var affix = pick(rng, open, Affix::weight);
            taken.add(affix.name());
            rolled.add(new Rolled(statOf(rng, affix, attribute), roll(rng, affix, level), affix.prefix()));
            for (var also : affix.also()) {
                var words = also.strip().split("\s+");
                rolled.add(new Rolled(words[0], Integer.parseInt(words[1]), ""));
            }
        }
        var name = switch (rarity) {
            case MAGIC -> magicName(base, rolled);
            case RARE -> rules.firstNames().get(rng.nextInt(rules.firstNames().size())) + " "
                    + rules.secondNames().get(rng.nextInt(rules.secondNames().size()));
            default -> base.displayName();
        };
        return new Made(idOf(base.name(), LETTERS.charAt(rarity), level, seed), base, null, null, rarity, level,
                seed, attribute, figure, List.copyOf(rolled), name);
    }

    /**
     * The figure an affix moves on this thing: an attribute affix the thing's own attribute, or one drawn among them
     * all; a skill's rank, on one of its keys drawn; any other its own word.
     */
    private String statOf(DeterministicRng rng, Affix affix, String attribute) {
        if (Stat.ATTRIBUTE.equals(affix.stat())) {
            var all = List.copyOf(words.attributes().keySet());
            return !attribute.isBlank() || all.isEmpty() ? attribute : all.get(rng.nextInt(all.size()));
        }
        if (Stat.SKILL_RANK.equals(affix.stat()) && !affix.keys().isEmpty()) {
            return Stat.SKILL_RANK + ":" + affix.keys().get(rng.nextInt(affix.keys().size()));
        }
        return affix.stat();
    }

    /** The epic {@code unique}, found at {@code level}: its figures are its own, whatever the seed. */
    public Made unique(Unique unique, int level, long seed) {
        var base = base(unique.base());
        var attribute = !unique.attribute().isBlank() ? unique.attribute()
                : base.attributes().isEmpty() ? "" : base.attributes().getFirst();
        var rolled = new ArrayList<Rolled>();
        for (var said : unique.gives()) {
            var words = said.strip().split("\s+");
            var stat = Stat.ATTRIBUTE.equals(words[0]) ? attribute : words[0];
            rolled.add(new Rolled(stat, Integer.parseInt(words[1]), ""));
        }
        return new Made(idOf(unique.name(), 'E', level, seed), base, unique, null, EPIC, level, seed, attribute,
                base.value().getLast(), List.copyOf(rolled), unique.displayName());
    }

    /** Whether {@code affix} may roll on a thing made on {@code base}. */
    static boolean fits(Affix affix, ItemBase base) {
        return affix.slots().contains(base.slot());
    }

    private static String magicName(ItemBase base, List<Rolled> rolled) {
        var name = new StringBuilder();
        for (var affix : rolled) {
            if (!affix.prefix().isEmpty()) {
                name.append(name.isEmpty() ? affix.prefix() : lowerFirst(affix.prefix())).append(' ');
            }
        }
        return name.isEmpty() ? base.displayName() : name + lowerFirst(base.displayName());
    }

    private static String lowerFirst(String word) {
        return word.isEmpty() ? word : Character.toLowerCase(word.charAt(0)) + word.substring(1);
    }

    /** What {@code affix} rolls at {@code level}: between its least and most there, each drawn between its ends. */
    static int roll(DeterministicRng rng, Affix affix, int level) {
        int low = along(affix.low(), level);
        int high = along(affix.high(), level);
        return between(rng, low, Math.max(low, high));
    }

    /** {@code range}'s figure at {@code level}: its first at level 1, its last at {@link #TOP_LEVEL}, straight between. */
    static int along(List<Integer> range, int level) {
        int from = range.getFirst();
        int to = range.getLast();
        int at = Math.clamp(level, 1, TOP_LEVEL) - 1;
        return from + Math.round((to - from) * at / (float) (TOP_LEVEL - 1));
    }

    // ---- the Loot ----

    /** The engine's thing for what {@code made} says. */
    public Loot loot(Made made) {
        if (made.potion() != null) {
            return potion(made.potion(), 1, made.seed());
        }
        var base = made.base();
        var skills = new ArrayList<Skill>();
        var affixes = new ArrayList<uz.dukeengine.rpg.item.Affix>();
        for (var affix : made.affixes()) {
            if (LIFESTEAL.equals(affix.stat())) {
                skills.add(lifesteal(made.id(), affix.value(), words.lifesteal()));
            } else {
                affixes.add(new uz.dukeengine.rpg.item.Affix(affix.stat(), affix.value()));
            }
        }
        if (made.unique() != null) {
            made.unique().skills().forEach(skill -> skills.add(skill.ownedBy(made.id())));
        }
        return new Loot(made.id(), made.name(), icon(base.icon(), made.rarity()), base.kind(), made.figure(), 0, 1,
                Stat.ATTRIBUTE.equals(base.kind()) ? made.attribute() : "", "", 0, 0, 1, ItemUse.NONE, "",
                base.slot(), skills, affixes);
    }

    /** A passive that drinks {@code percent} of every blow, lent by the thing {@code owner}. */
    static Skill lifesteal(String owner, int percent, String name) {
        return new Skill(owner, 'L', SkillEffect.LIFESTEAL, 0f, 0f, 0f, 0f, 0f, 0f, percent, 0, 0, 0, 0, 90, 0, 1, 0,
                0, 0, 0, "", "", "", "", 0f, name, "", 0f, 0f, 0, Map.of(), 0, 0, 0, 0);
    }

    static String icon(String name, int rarity) {
        return "icons/items/loot/" + name + "_" + FRAMES.get(Math.clamp(rarity, 0, 3)) + ".png";
    }

    private static String idOf(String name, char letter, int level, long seed) {
        return name + "-" + letter + level + "-" + Long.toString(Math.abs(seed), 36);
    }

    // ---- reading back ----

    /**
     * What a generated thing's id says it is, or {@code null} for one that is not generated: the data's own things
     * (the key, the medallion), and anything a newer game wrote that this one does not have.
     */
    public Made made(String id) {
        int last = id.lastIndexOf('-');
        int mid = last <= 0 ? -1 : id.lastIndexOf('-', last - 1);
        if (mid <= 0 || mid + 2 > last) {
            return null;
        }
        var name = id.substring(0, mid);
        char letter = id.charAt(mid + 1);
        int level;
        long seed;
        try {
            level = Integer.parseInt(id.substring(mid + 2, last));
            seed = Long.parseLong(id.substring(last + 1), 36);
        } catch (NumberFormatException notOurs) {
            return null;
        }
        if (letter == 'P') {
            var potion = potion(name);
            return potion == null ? null : new Made(id, null, null, potion, -1, level, seed, "", 0, List.of(),
                    potion.displayName());
        }
        if (letter == 'E') {
            var unique = uniques.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
            return unique == null || base(unique.base()) == null ? null : unique(unique, level, seed);
        }
        int rarity = LETTERS.indexOf(letter);
        var base = base(name);
        return rarity < 0 || base == null ? null : make(base, rarity, level, seed);
    }

    /**
     * The thing an id names: one of the data's own, a generated one made again, or — by name alone, as a map places
     * one or a merchant stocks one — a unique at its level, a potion, or a base common at its level; {@code null} for
     * a name nothing has.
     */
    public Loot resolve(String id) {
        var own = plain.get(id);
        if (own != null) {
            return own;
        }
        var made = made(id);
        if (made != null) {
            return loot(made);
        }
        for (var unique : uniques) {
            if (unique.name().equals(id) && base(unique.base()) != null) {
                return loot(unique(unique, unique.level(), 0));
            }
        }
        var potion = potion(id);
        if (potion != null) {
            return new Loot(potion.name(), potion.displayName(), icon(potion.icon(), COMMON), POTION, 1, 0, 1, "",
                    "", 0, 0, 1, ItemUse.NONE, "", "", List.of());
        }
        var base = base(id);
        return base == null ? null : loot(make(base, COMMON, base.level(), 0));
    }

    /** Whether a map may place, a merchant sell or a trigger give a thing of this name. */
    public boolean knows(String name) {
        return plain.containsKey(name) || base(name) != null || potion(name) != null
                || uniques.stream().anyMatch(unique -> unique.name().equals(name));
    }

    public ItemBase base(String name) {
        return bases.stream().filter(base -> base.name().equals(name)).findFirst().orElse(null);
    }

    public Potion potion(String name) {
        return potions.stream().filter(potion -> potion.name().equals(name)).findFirst().orElse(null);
    }

    /** The potion a stack is, or {@code null} for a thing that is not one. */
    public Potion potionOf(Loot item) {
        if (item == null || !POTION.equals(item.kind())) {
            return null;
        }
        int cut = item.id().indexOf('-');
        return potion(cut < 0 ? item.id() : item.id().substring(0, cut));
    }

    /** A thing's rarity, or -1 for one that has none: a potion, the data's own. */
    public int rarityOf(Loot item) {
        var made = item == null ? null : made(item.id());
        return made == null ? -1 : made.rarity();
    }

    /** The colour of a thing's name: its rarity's, or {@code fallback}. */
    public int colourOf(Loot item, int fallback) {
        int rarity = rarityOf(item);
        return rarity < 0 || rarity >= rules.colours().size() ? fallback : rules.colours().get(rarity);
    }

    /** The light a thing lying on the ground stands in, or "" for none: a common one, a potion, the data's own. */
    public String beamOf(Loot item) {
        int rarity = rarityOf(item);
        return rarity < MAGIC || rarity - 1 >= rules.beams().size() ? "" : rules.beams().get(rarity - 1);
    }

    /** Whether a camp of the open world is guarded by this one: it drops as a boss does, wherever it stands. */
    public boolean isCampBoss(String template) {
        return rules.campBosses().contains(template);
    }

    public boolean isElite(String template) {
        return rules.elites().contains(template);
    }

    /**
     * What a generated thing or a potion stack costs, or -1 for the data's own (priced by the merchant's stock): its
     * base's price times its rarity's and its level's, a stack's its potions'.
     */
    public int price(Loot item) {
        var potion = potionOf(item);
        if (potion != null) {
            return potion.price() * Math.max(1, item.value());
        }
        var made = made(item.id());
        if (made == null || made.base() == null) {
            return -1;
        }
        long price = (long) made.base().price() * rules.prices().get(made.rarity()) / 100
                * (100 + rules.pricePerLevel() * (made.level() - 1)) / 100;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, price));
    }

    // ---- the card ----

    /** The line under a generated thing's name: its rarity and level, in its colour; null for any other. */
    public Line rarityLine(Loot item) {
        var made = made(item.id());
        if (made == null || made.rarity() < 0) {
            return null;
        }
        return new Line(rules.rarities().get(made.rarity()) + " · " + rules.levelWord().formatted(made.level()),
                rules.colours().get(made.rarity()));
    }

    /**
     * What a generated thing or a potion says under its name: its figure, its affixes, an epic's aura and story — or
     * a potion's draught and count; {@code null} for the data's own, which the bag says as it always did.
     */
    public List<Line> lines(Loot item) {
        var potion = potionOf(item);
        if (potion != null) {
            var out = new ArrayList<Line>();
            if (potion.heal() > 0) {
                out.add(new Line("+" + potion.heal() + "% " + words.health(), rules.figureColour()));
            }
            if (potion.mana() > 0) {
                out.add(new Line("+" + potion.mana() + "% " + words.mana(), rules.figureColour()));
            }
            out.add(new Line(rules.countWord().formatted(item.value()), rules.figureColour()));
            return out;
        }
        var made = made(item.id());
        if (made == null) {
            return null;
        }
        var out = new ArrayList<Line>();
        out.add(new Line(say(made.base().kind(), made.attribute(), made.figure()), rules.figureColour()));
        for (var affix : made.affixes()) {
            out.add(new Line(say(affix.stat(), made.attribute(), affix.value()), rules.affixColour()));
        }
        if (made.unique() != null) {
            for (var skill : made.unique().skills()) {
                if (!skill.name().isBlank()) {
                    out.add(new Line(skill.name(), rules.affixColour()));
                }
            }
            if (!made.unique().lore().isBlank()) {
                wrap(made.unique().lore(), Math.max(10, rules.wrap())).forEach(line -> out.add(new Line(line,
                        rules.loreColour())));
            }
        }
        return out;
    }

    /**
     * What {@code item} would change if it were worn instead of {@code worn} (nothing worn: null), figure by figure:
     * green for more, red for less; nothing for a figure it leaves as it was.
     */
    public List<Line> compare(Loot item, Loot worn) {
        var now = figures(worn);
        var then = figures(item);
        var keys = new ArrayList<String>(then.keySet());
        now.keySet().stream().filter(key -> !keys.contains(key)).forEach(keys::add);
        keys.sort(java.util.Comparator.comparingInt(key -> ORDER.indexOf(key.split(":")[0])));
        var out = new ArrayList<Line>();
        for (var key : keys) {
            int change = then.getOrDefault(key, 0) - now.getOrDefault(key, 0);
            if (change != 0) {
                var parts = key.split(":", 2);
                out.add(new Line(say(parts[0], parts.length > 1 ? parts[1] : "", change),
                        change > 0 ? rules.betterColour() : rules.worseColour()));
            }
        }
        return out;
    }

    /**
     * Every figure a thing moves while worn, by figure (an attribute's as {@code ATTRIBUTE:Strength}, a skill's rank as
     * {@code SKILL_RANK:Q}): its own, its extra, its affixes and the lifesteal it lends — read off the engine's thing, so
     * it holds for the data's own as well.
     */
    public Map<String, Integer> figures(Loot item) {
        var out = new LinkedHashMap<String, Integer>();
        if (item == null || POTION.equals(item.kind())) {
            return out;
        }
        if (FIGURES.contains(item.kind()) && item.value() != 0) {
            out.merge(Stat.ATTRIBUTE.equals(item.kind()) ? Stat.ATTRIBUTE + ":" + item.attribute() : item.kind(),
                    item.value(), Integer::sum);
        }
        if (!item.extra().isBlank() && item.extraValue() != 0) {
            out.merge(item.extra(), item.extraValue(), Integer::sum);
        }
        for (var affix : item.affixes()) {
            var key = words.attributes().containsKey(affix.stat()) ? Stat.ATTRIBUTE + ":" + affix.stat() : affix.stat();
            out.merge(key, affix.value(), Integer::sum);
        }
        for (var skill : item.skills()) {
            if (skill.effect() == SkillEffect.LIFESTEAL) {
                out.merge(LIFESTEAL, skill.boostPercent(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * One figure as a card says it: {@code +12% Zarba}, {@code -20 Jon}, {@code +3 Epchillik}, {@code +1 Q mahorati}.
     * {@code stat} may be an attribute's name, or {@code SKILL_RANK:Q}.
     */
    public String say(String stat, String attribute, int value) {
        var sign = value < 0 ? "-" : "+";
        int size = Math.abs(value);
        if (words.attributes().containsKey(stat)) {
            return sign + size + " " + words.attributes().get(stat);
        }
        if (stat.startsWith(Stat.SKILL_RANK + ":")) {
            var key = stat.substring(Stat.SKILL_RANK.length() + 1);
            var pressed = key.length() == 1 ? String.valueOf(letterOf.apply(key.charAt(0))) : key;
            return sign + size + " " + rules.skillRankWord().formatted(pressed);
        }
        return switch (stat) {
            case Stat.ATTACK -> sign + size + "% " + words.attack();
            case Stat.ARMOUR -> sign + size + "% " + words.armour();
            case Stat.HEALTH -> sign + size + " " + words.health();
            case Stat.MANA -> sign + size + " " + words.mana();
            case Stat.ATTRIBUTE -> sign + size + " " + words.attributes().getOrDefault(attribute, attribute);
            case Stat.HEALTH_REGEN -> sign + size + " " + words.healthRegen();
            case Stat.MANA_REGEN -> sign + size + " " + words.manaRegen();
            case Stat.ATTACK_SPEED -> sign + size + "% " + words.attackSpeed();
            case Stat.CRIT_CHANCE -> sign + size + "% " + rules.critChanceWord();
            case Stat.CRIT_DAMAGE -> sign + size + "% " + rules.critDamageWord();
            case Stat.MOVE_SPEED -> sign + size + "% " + rules.moveSpeedWord();
            case LIFESTEAL -> sign + size + "% " + words.lifesteal();
            default -> sign + size + " " + stat;
        };
    }

    /** {@code text} broken into lines of at most {@code width} letters, at spaces: a unique's story on its card. */
    static List<String> wrap(String text, int width) {
        var out = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    // ---- chance ----

    /** A seeded stream for one purpose of one seed: the same two give the same draws on every machine. */
    static DeterministicRng rng(long seed, String salt) {
        long z = seed * 0x9E3779B97F4A7C15L + salt.hashCode();
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new DeterministicRng(z ^ (z >>> 31));
    }

    private static int between(DeterministicRng rng, int from, int to) {
        return to <= from ? from : rng.nextInt(from, to);
    }

    private static <T> T pick(DeterministicRng rng, List<T> among, ToIntFunction<T> weight) {
        int total = 0;
        for (var one : among) {
            total += Math.max(0, weight.applyAsInt(one));
        }
        if (total <= 0) {
            return among.getFirst();
        }
        int roll = rng.nextInt(total);
        for (var one : among) {
            roll -= Math.max(0, weight.applyAsInt(one));
            if (roll < 0) {
                return one;
            }
        }
        return among.getLast();
    }

    /** An index of {@code weights}, drawn by them. */
    static int pickIndex(DeterministicRng rng, List<Integer> weights) {
        int total = weights.stream().mapToInt(weight -> Math.max(0, weight)).sum();
        if (total <= 0) {
            return COMMON;
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < weights.size(); i++) {
            roll -= Math.max(0, weights.get(i));
            if (roll < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }
}
