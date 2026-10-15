package uz.dukeengine.dungeon.loot;

import java.util.List;

/**
 * How things are found, named, priced, shown and drunk: everything about loot that is not a base, an affix, a unique
 * or a potion. See docs/plan/2026-10-15-loot.md. Each list by rarity is common, magic, rare, epic.
 *
 * @param rarities       each rarity's word on a card
 * @param colours        each rarity's colour: its name on a card, its light on the ground
 * @param normal         the rarities an ordinary monster drops, out of their sum
 * @param elite          an elite's ({@code Elites}); {@code boss}, a boss's; {@code chest}, a map's treasure's;
 *     {@code shop}, the merchant's
 * @param magicAffixes   how many affixes a magic thing rolls, from and to
 * @param rareAffixes    and a rare; never more than one of each kind (see {@link Affix})
 * @param prices         what each rarity is worth, in percent of its base's price
 * @param pricePerLevel  and percent more every item level past the first
 * @param eliteDropPercent the chance an elite drops anything, out of a hundred (a monster's and a boss's are LootDrops')
 * @param potionPercent  of an ordinary drop, how many in a hundred are a potion
 * @param bossDrops      how many things a boss drops
 * @param bossLevel      how many item levels above its own a boss's drop is
 * @param baseWindow     how many levels below the item level a base is still found: older bases stop dropping
 * @param elites         the monsters, by template, that drop as elites
 * @param firstNames     a rare's name is one of these and one of {@code secondNames}
 * @param beams          the light a magic, a rare and an epic thing lying on the ground stand in, an Effect each
 * @param beamFrames     how often it is played on it again, in logic frames
 * @param beltSize       how many potion stacks the belt holds: keys 1 and on
 * @param beltFrames     how long after a draught the belt is ready again, in logic frames
 * @param levelWord      after the rarity on a card: the item level, {@code %d}
 * @param compareWord    over the differences to what is worn
 * @param countWord      a stack's count on its card, {@code %d}
 * @param drinkHint      under a potion's card
 * @param beltKeyWord    what a belt key does, on the Settings screen's list of keys; {@code %d} the key
 * @param treasure       a map's placed thing of this name is a treasure: a chest-weighted roll at the floor's level
 * @param figureColour   a base figure's line; {@code affixColour} an affix's; {@code loreColour} a unique's story;
 *     {@code betterColour} and {@code worseColour} a difference to what is worn
 * @param critChanceWord a critical blow's chance on a card and the sheet; {@code critDamageWord} how much harder it lands;
 *     {@code moveSpeedWord} quicker legs; {@code skillRankWord} a rank on a key's skill, {@code %s} the key
 * @param wrap           the longest a card's line is, in letters: a unique's story is broken to it
 */
public record ItemRules(List<String> rarities, List<Integer> colours, List<Integer> normal, List<Integer> elite,
        List<Integer> boss, List<Integer> chest, List<Integer> shop, List<Integer> magicAffixes,
        List<Integer> rareAffixes, List<Integer> prices, int pricePerLevel, int eliteDropPercent, int potionPercent,
        int bossDrops, int bossLevel, int baseWindow, List<String> elites, List<String> firstNames,
        List<String> secondNames, List<String> beams, int beamFrames, int beltSize, int beltFrames, String levelWord,
        String compareWord, String countWord, String drinkHint, String beltKeyWord, String treasure, int figureColour,
        int affixColour,
        int loreColour, int betterColour, int worseColour, String critChanceWord, String critDamageWord,
        String moveSpeedWord, String skillRankWord, int wrap) {

    public static final ItemRules DEFAULTS = new ItemRules(List.of("Oddiy", "Sehrli", "Noyob", "Afsonaviy"),
            List.of(0xC8CCD2, 0x5B9BFF, 0xE8B23A, 0xB46CFF), List.of(700, 250, 45, 5), List.of(380, 430, 160, 30),
            List.of(0, 450, 430, 120), List.of(250, 450, 260, 40), List.of(550, 380, 70, 0), List.of(1, 2),
            List.of(3, 4), List.of(100, 250, 500, 1200), 8, 40, 35, 2, 1, 6, List.of(), List.of("Qora"),
            List.of("tish"), List.of(), 45, 4, 30, "%d-daraja", "Kiyilgan bilan solishtiring", "Soni: %d",
            "1-4: ichish", "Belbog'dan ichish: %d", "Xazina", 0xFFFFFF, 0x7FB2FF, 0xD8B05A, 0x6CD86C, 0xE05A4A,
            "Kritik zarba", "Kritik zarba kuchi", "Yurish tezligi", "%s mahorati", 40);

    public ItemRules {
        rarities = rarities == null ? List.of() : List.copyOf(rarities);
        colours = colours == null ? List.of() : List.copyOf(colours);
        normal = normal == null ? List.of() : List.copyOf(normal);
        elite = elite == null ? List.of() : List.copyOf(elite);
        boss = boss == null ? List.of() : List.copyOf(boss);
        chest = chest == null ? List.of() : List.copyOf(chest);
        shop = shop == null ? List.of() : List.copyOf(shop);
        magicAffixes = magicAffixes == null ? List.of() : List.copyOf(magicAffixes);
        rareAffixes = rareAffixes == null ? List.of() : List.copyOf(rareAffixes);
        prices = prices == null ? List.of() : List.copyOf(prices);
        elites = elites == null ? List.of() : List.copyOf(elites);
        firstNames = firstNames == null ? List.of() : List.copyOf(firstNames);
        secondNames = secondNames == null ? List.of() : List.copyOf(secondNames);
        beams = beams == null ? List.of() : List.copyOf(beams);
    }
}
