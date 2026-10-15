package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.dungeon.ai.Doing;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.hero.Attribute;
import uz.dukeengine.rpg.hero.AttributeRules;
import uz.dukeengine.rpg.hero.HeroFigures;
import uz.dukeengine.rpg.hero.HeroProgress;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.client3d.rpg.HeroCard;

/**
 * Everything the hero's panel shows, as one line of the status channel.
 *
 * <p>Written through the engine's {@link HeroCard}, which owns the format beside the panel that reads it
 * ({@code uz.dukeengine.client3d.rpg.HeroPanel}). What goes on the card — whose it is, its words, its
 * pictures — is this game's, and is decided here.
 *
 * <p>The split between the two halves is: whatever is <em>words</em> is finished
 * here, and whatever is <em>drawn</em> is sent as numbers. So the client never
 * writes a word of its own — it has three other games to serve and no business
 * knowing which language this one speaks — and this class never decides how wide
 * a bar is or how much of a slot is still in shadow.
 *
 * <p>Cooldowns cross as frames, not seconds. Frames are what the simulation
 * counts; the conversion is a presentation decision and belongs on the far side
 * with the rest of them.
 */
final class HeroStatus {

    private HeroStatus() {
    }

    /**
     * The card for nobody: the floor, and nothing else.
     *
     * <p>What the bar says when the player has selected nothing. It is not a
     * shorter hero's card — it has no hero on it at all, and the panel answers by
     * taking the portrait, the figures, the bag and the skills off the bar and
     * closing the gap. What is left is what was never about a creature: the map,
     * how far down he is, and anything that has just happened.
     *
     * <p><b>The floor's look has to be here.</b> It is the first line the client
     * ever reads — nothing is selected when a run begins — and it is the line that
     * says which stone this floor is built from. A card that left it out would
     * have the first floor drawn in the wrong kit until something was clicked.
     */
    static String nothing(int depth, int lastDepth, DungeonSettings settings, String note,
            String look, boolean his) {
        var card = HeroCard.named("")
                .depth(howFarDown(depth, lastDepth), settings.hud().depthWord())
                // The headings belong to the furniture rather than to whoever is
                // selected: the sockets under them are drawn empty, and an empty
                // grid with no heading over it is a hole rather than a bag.
                .headings(settings.hud().skillsWord(), settings.hud().itemsWord());
        // The buttons are furniture too, and stay for the same reason the sockets
        // do. Nothing is selected, so nothing is doing anything for them to show;
        // they may be pressed while his hero stands, whose orders they give -- {@code his}.
        orders(card, settings, null, his);
        if (note != null && !note.isEmpty()) {
            // Finding a sword is worth saying whether or not he is selected.
            card.note(note);
        }
        if (look != null && !look.isBlank()) {
            card.field("look", look);
        }
        return card.toString();
    }

    /**
     * What the bars over everybody's heads need, which is not much.
     *
     * <p>Appended to whichever card is being sent, because it is about the FLOOR
     * rather than about whatever is selected: the bars are drawn over every
     * creature in sight whether or not the player has clicked on one.
     *
     * <p><b>Why it is here and not in {@code UnitView}.</b> The snapshot already
     * carries what a bar mostly needs — where a creature is, whose it is, and
     * what is left of it. What it does not carry is a level, a pool of mana, a
     * name worth printing, or which of them is the boss, and adding four fields
     * to the engine's own view would put one game's vocabulary in a record three
     * other games are handed. The status channel is the seam that exists for
     * exactly this: a string the engine carries and never reads.
     *
     * <p><b>And it collapses.</b> Written per creature this would be a line of
     * fifty entries rebuilt thirty times a second. It is not, because none of the
     * four facts is really per creature:
     *
     * <ul>
     * <li>a monster's level is its own, and rides the creature rather than this
     *     line: a word on it, {@code level:8}, set where it is placed (see
     *     {@code Spawner.scale}) and carried with its other words. What is sent
     *     here is the DEPTH, one number for the whole floor, which the medallion
     *     shows on a creature holding no level of its own
     * <li>the boss is one id
     * <li>the name belongs to the TEMPLATE, so it is a dictionary of about a
     *     dozen rather than one entry a creature
     * <li>and the bar's mana is the hero's alone — the casting mages pay from pools
     *     of their own, and nobody is shown them — so it is one entry
     * </ul>
     *
     * <p>The ring around the hero's medallion is his experience, and only his:
     * he is the only thing in the game that earns any. An empty ring on a
     * skeleton would be a promise that it could fill.
     */
    static String world(uz.dukeengine.core.GameLogic logic, GameObject hero, HeroProgress progress,
            int depth, uz.dukeengine.core.thing.ObjectId bossId, DungeonSettings settings) {
        var line = new StringBuilder("|deep=").append(depth);
        if (bossId != null) {
            line.append("|boss=").append(bossId.value());
        }
        if (hero != null && progress != null) {
            var book = hero.findModule(SkillBook.class);
            line.append("|hero=").append(hero.getId().value())
                    .append(',').append(progress.getLevel())
                    .append(',').append(book == null ? 0 : book.getMana())
                    .append(',').append(book == null ? 0 : book.getMaxMana())
                    .append(',').append(progress.getExperienceIntoLevel())
                    .append(',').append(progress.getExperienceForNextLevel());
        }
        // The dictionary. Every kind the file names rather than every creature in
        // sight: the same dozen entries whatever is on the floor, and no work at
        // all that depends on how busy the fight is.
        for (var kind : settings.monsters()) {
            named(line, logic, kind.name());
        }
        for (var look : settings.heroes()) {
            named(line, logic, look.name());
        }
        return line.toString();
    }

    /** One template's printed name, skipped when it has none worth printing. */
    private static void named(StringBuilder line, uz.dukeengine.core.GameLogic logic,
            String template) {
        var found = logic.getThingFactory().findTemplate(template);
        if (found == null) {
            return;
        }
        var display = uz.dukeengine.core.thing.Titled.of(found);
        if (display == null || display.isBlank() || display.equals(template)) {
            return; // nothing the client could not have worked out from the name
        }
        line.append("|who=").append(template).append(',').append(display);
    }

    static String of(GameObject hero, HeroProgress progress, int depth, int lastDepth,
            DungeonSettings settings, int frame, String look,
            boolean holding, uz.dukeengine.rpg.skill.SkillRanks learnt) {
        if (hero == null || hero.getBody() == null) {
            return "";
        }
        int level = progress.getLevel();
        var card = HeroCard.named(nameOf(hero))
                .title(titleOf(hero, settings))
                .rank(level + settings.hud().rankSuffix())
                .health(Math.round(hero.getBody().getHealth()), Math.round(hero.getBody().getMaxHealth()))
                .experience(progress.getExperienceIntoLevel(), progress.getExperienceForNextLevel())
                .depth(howFarDown(depth, lastDepth), settings.hud().depthWord());
        stats(card, hero, progress, settings);
        orders(card, settings, Doing.of(hero, holding), true);
        card.headings(settings.hud().skillsWord(), settings.hud().itemsWord());
        items(card, progress);
        var book = hero.findModule(SkillBook.class);
        if (book != null && book.getMaxMana() > 0) {
            // Beside the health, and read the same way. A hero whose file gives
            // him no pool sends no field, and the panel draws no bar -- which is
            // every hero in the game until one of them was given one.
            card.mana(book.getMana(), book.getMaxMana());
            // And the moment he asked for something he could not pay for. A
            // stamp rather than a flag, because the line is rebuilt and sent
            // every frame: without it the refusal would sound thirty times a
            // second for as long as nothing else happened.
            if (book.getRefusedForManaFrame() > 0) {
                card.refusedForMana(book.getRefusedForManaFrame());
            }
        }
        if (book != null) {
            var hud = settings.hud();
            card.skills(book, learnt, level, new HeroCard.Words(hud.rankSuffix(), hud.masterWord(),
                    hud.lockedWord(), new HeroCard.TipWords(hud.damageWord(), hud.cooldownWord(),
                            hud.radiusWord(), hud.rangeWord(), hud.boostWord(), hud.raiseWord(),
                            hud.raiseKeyWord(), hud.maxedWord(), hud.noPointsWord(), hud.rankSuffix(),
                            hud.secondsWord(), hud.manaWord())),
                    // Each slot drawn and pressed on the letter of the scheme played: W's skill on E, modern (E79).
                    settings::letterOf);
            passives(card, book, learnt, hud);
            // How many levels he has not spent yet, and the word for them. Beside
            // the heading rather than on a slot, because it belongs to none of
            // them: it is what the four are competing for.
            card.points(learnt.unspent(level), hud.pointsWord());
            // Where his last cast wants drawing, and as what -- only the hero's: a skeleton mage's fire is drawn by
            // the fire, which is a thing in the world and needs nobody to say so.
            card.casts(book, hero);
        }
        // What he just picked up, for as long as it is worth saying. A line rather
        // than a banner: the banner interrupts, and finding a sword is news, not
        // an interruption.
        var found = progress.getLoot().noteAt(frame);
        if (!found.isEmpty()) {
            card.note(found);
        }
        // How this floor is drawn: a theme and one of its variations, by name. The
        // client keeps every look it was given at launch and is only ever told
        // which of them is current -- see Visuals.theme.
        if (look != null && !look.isBlank()) {
            card.field("look", look);
        }
        return withoutHisStagger(card.toString(), book);
    }

    /** The fields HeroCard.skills writes for a skill, each {@code name=<key>,...}. */
    private static final java.util.Set<String> SLOT_FIELDS =
            java.util.Set.of("cost", "skill", "srank", "tipName", "tipAt", "tipText", "tipFoot", "tipRow", "letter");

    /**
     * The line without his STAGGER's slot: no slot of the four, never bought and always his, it would be a fifth
     * framed picture on a bar of four that says nothing he can act on. HeroCard writes every skill of the book, so
     * its fields are taken back out here.
     */
    static String withoutHisStagger(String line, SkillBook book) {
        if (book == null) {
            return line;
        }
        var keys = book.getSkills().stream()
                .filter(skill -> skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.STAGGER
                        || skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.DODGE // no slot either: Space
                        || skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.MOUNT // nor his horse: T
                        || skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.PUSH) // nor the story's landing
                .map(skill -> skill.key() + ",").toList();
        if (keys.isEmpty()) {
            return line;
        }
        var kept = new java.util.ArrayList<String>();
        for (var field : line.split("[|]", -1)) {
            int equals = field.indexOf('=');
            boolean his = equals > 0 && SLOT_FIELDS.contains(field.substring(0, equals))
                    && keys.stream().anyMatch(key -> field.startsWith(key, equals + 1));
            if (!his) {
                kept.add(field);
            }
        }
        return String.join("|", kept);
    }

    /**
     * The four orders on the buttons beside the map: key, drawing, word, state.
     *
     * <p>Sent rather than assumed because three of the four are the engine's and
     * one is this game's, and the client has no way of knowing which orders a game
     * offers — the same reason the skill row is sent rather than assumed. The keys
     * match what {@code Main.controls} claims, and the words come out of the file
     * with every other word on the panel.
     *
     * <p><b>Every one of them has a state worth sending</b>, which is the thing
     * this used to miss. They were four things to press and nothing else, and that
     * is half a control: each is also a state the creature can be in, and exactly
     * one of the four is true at any moment -- see {@link Doing}. Nobody plays by
     * clicking them, so what earns them their space on the bar is what they can
     * tell him.
     *
     * <p>{@code his} is whether the player may press them at all: whether he has a
     * hero standing, whose orders they give whatever is selected. A creature that
     * is not his still SHOWS what it is doing -- reading a skeleton's intent off
     * the bar is worth as much as reading his own -- and with no hero of his the
     * buttons go dim, because a lit button that does nothing is worse than no button.
     */
    /**
     * A passive's card says so where an active one names its key -- "1-daraja · Passiv": it is never pressed (its key
     * is bound to nothing, see Main.aimsFor). The panel reads the last {@code tipAt} it is sent. Its slot is drawn as a
     * framed picture by the engine itself (HeroCard.skills writes "passive", E65).
     */
    static void passives(HeroCard card, SkillBook book, uz.dukeengine.rpg.skill.SkillRanks learnt,
            uz.dukeengine.dungeon.world.Hud hud) {
        for (var skill : book.getSkills()) {
            if (skill.effect().isPassive()) {
                int rank = learnt.rankOf(skill.key());
                card.field("tipAt", skill.key() + ","
                        + (rank > 0 ? rank + hud.rankSuffix() + " · " : "") + hud.passiveWord());
            }
        }
    }

    private static void orders(HeroCard card, DungeonSettings settings, Doing doing, boolean his) {
        var words = settings.hud().orderWords();
        var pictures = settings.hud().orderIcons();
        // Drawn in this order and read in this order — walk, attack, stop, guard —
        // which is the order the words come in the file and the order Doing counts
        // its four states in. The LETTERS are not in that order and are not meant
        // to be: A S D are attack, stop and defend, under a hand that never leaves
        // them, and walking takes the letter left over because it is the one order
        // nobody uses the keyboard for.
        //
        // The letters are here because they are the game's claim about its own
        // controls — see Main.controls, which has to match. What each button LOOKS
        // like is not: that used to be a drawing named here by a word the client
        // held a mesh for, so changing how an order looked meant editing two
        // modules. It comes out of the file now, beside the word.
        char[] keys = {'F', 'A', 'S', 'D'};
        card.ordersAreHis(his);
        if (uz.dukeengine.dungeon.world.Controls.walksOnKeys(settings.scheme())) {
            return; // no order buttons: A S D are his feet, F a skill, the mouse fights and works
        }
        for (int i = 0; i < keys.length; i++) {
            String word = i < words.size() ? words.get(i) : "";
            if (word.isBlank()) {
                continue; // a game that does not name an order does not offer it
            }
            card.order(keys[i], i < pictures.size() ? pictures.get(i) : "", word,
                    doing != null && doing.button() == i);
        }
    }

    /**
     * What he is carrying, as one field per kind of thing with how many of it: grouped and counted, three of the
     * same sword one picture reading three.
     *
     * <p>For the client's own grid of his bag, which can only be looked at. The bag that can be used is the
     * engine's {@code BagScreen}, and the client's grid is left off the bar; the line still says what is in it,
     * as it says everything else about him.
     */
    private static void items(HeroCard card, HeroProgress progress) {
        var held = new java.util.LinkedHashMap<String, int[]>();
        var icons = new java.util.LinkedHashMap<String, String>();
        for (var item : progress.getLoot().getFound()) {
            held.computeIfAbsent(item.id(), id -> new int[1])[0]++;
            icons.putIfAbsent(item.id(), item.icon());
        }
        for (var entry : held.entrySet()) {
            card.item(icons.get(entry.getKey()), entry.getValue()[0]);
        }
    }

    /**
     * His attributes, and the two figures beside them: what he hits for and what he
     * shrugs off.
     *
     * <p>The figures come from {@code HeroProgress} rather than being worked out here
     * again: it is the one place the attribute arithmetic is done and the one place it
     * is applied, so the number on the panel is the number taking the blows.
     *
     * <p>Everything that moves them is counted: the level and what he found on the
     * floor. What he would have without anything he found is the other half of each
     * figure — the difference is the number in green, and it is worth showing on its
     * own: a figure that only goes up says nothing about whether the last thing he
     * picked up was worth picking up.
     *
     * <p>Attributes are a field of their own rather than more figures, because the panel
     * lays them out apart and there are as many of them as the file lists, in its order.
     * His primary is marked, and each carries the card that says what a point of it is
     * worth. His speed is not a figure any more: it is what an attribute became, and it
     * is read on that one's card.
     */
    private static void stats(HeroCard card, GameObject hero, HeroProgress progress, DungeonSettings settings) {
        var now = progress.figuresOf(hero, progress.found());
        var bare = progress.figuresOf(hero, HeroFigures.Found.NOTHING);
        var his = progress.getHero();
        if (his.attributes().hasPrimary()) {
            var rules = settings.attributeRules();
            var art = settings.attributeArt();
            for (int i = 0; i < art.size(); i++) {
                boolean primary = i == his.attributes().primary();
                card.attribute(art.get(i).word(), now.attributes().whole(i), bare.attributes().whole(i),
                        art.get(i).icon(), primary);
                attributeCard(card, i, art.get(i).word(), rules.attributes().get(i), primary,
                        rules, Math.round(now.speed()), settings);
            }
        }
        int armour = armourOf(progress, settings, true);
        int bareArmour = armourOf(progress, settings, false);
        var pictures = settings.hud().statIcons();
        card.stat(settings.hud().attackWord(), Math.round(now.attack()), Math.round(bare.attack()), pictures.get(0));
        card.stat(settings.hud().armourWord(), armour, bareArmour, pictures.get(1));
    }

    /**
     * What a hero shrugs off, in percent: with what he has found, or bare.
     *
     * <p>What he was born in counts with what he has found, exactly as the body
     * counts it -- otherwise the figure on the panel is not the one taking the
     * blows, and a knight reads as an archer in a shirt. His own plate is not
     * borrowed, so it belongs on both sides of the sum.
     */
    static int armourOf(HeroProgress progress, DungeonSettings settings, boolean found) {
        int worn = progress.getHero().armourPercent();
        return Math.round((1f - settings.levelling().damageTakenWith(progress.getLevel(),
                worn + (found ? progress.getLoot().armourPercent() : 0))) * 100f);
    }

    /** What a hero hits for, with everything he has found. */
    static int attackOf(GameObject hero, HeroProgress progress) {
        return Math.round(progress.figuresOf(hero, progress.found()).attack());
    }

    /**
     * What a creature that is not a hero hits for: its weapon's figure and what its own level makes of it -- a boss's
     * as a skeleton's. Worked out rather than read off it because the engine's weapon does not hand its number back,
     * and what a level multiplies a monster by is this game's arithmetic: see {@code Spawner.scale}.
     */
    static int blowOf(GameObject creature) {
        var level = creature.findModule(LevelBonus.class);
        return Math.round(weaponDamage(creature.getTemplate()) * (level == null ? 1f : level.damageMultiplier()));
    }

    /**
     * The card over one attribute: its name, whether it is his primary, what a single
     * point of it gives, and — for one that gives speed — how fast he is now.
     *
     * <p>Sent in the same kinds of field a skill's card is, keyed by the attribute's place
     * in the file's list rather than by a key he presses. The numbers are the file's own,
     * written the way the file wrote them, and only a figure a point actually moves gets
     * a row, so an attribute the file adds next describes itself.
     */
    private static void attributeCard(HeroCard card, int index, String word,
            Attribute attribute, boolean primary, AttributeRules rules, int speedNow,
            DungeonSettings settings) {
        var hud = settings.hud();
        var rows = new java.util.ArrayList<HeroCard.Row>();
        cardRow(rows, hud.healthWord(), attribute.healthPerPoint().value());
        cardRow(rows, hud.speedWord(), attribute.speedPerPoint().value());
        cardRow(rows, hud.manaWord(), attribute.manaPerPoint().value());
        if (primary) {
            cardRow(rows, hud.attackWord(), rules.damagePerPrimary());
        }
        // His speed was a figure beside the others. It is what this attribute became,
        // so it is said here, under what a point of it is worth.
        boolean speedy = attribute.speedPerPoint().value() > 0 && !hud.speedNowWord().isEmpty();
        card.attributeTip(index, word, primary ? hud.primaryWord() : "",
                rows.isEmpty() ? "" : hud.eachPointWord(), rows,
                speedy ? hud.speedNowWord() + " " + speedNow : "");
    }

    /** One figure a point gives; nothing for a figure it does not move. */
    private static void cardRow(java.util.List<HeroCard.Row> rows, String label, int hundredths) {
        if (hundredths != 0) {
            rows.add(new HeroCard.Row(label, "+" + hundredths(hundredths), ""));
        }
    }

    /** A coefficient as the file wrote it: 15 is "0.15", 1200 is "12", 150 is "1.5". */
    static String hundredths(int value) {
        return java.math.BigDecimal.valueOf(value, 2).stripTrailingZeros().toPlainString();
    }

    private static float weaponDamage(ThingTemplate template) {
        for (var entry : template.modules()) {
            if (entry instanceof uz.dukeengine.combat.module.WeaponUpdate.Data weapon) {
                return weapon.damage();
            }
        }
        return 0f;
    }

    /**
     * What he is, under his name.
     *
     * <p>His own block's word, and the panel's only if he has none. It used to be
     * the panel's outright — one line in {@code Hud} — which was right
     * while there was one hero and became the archer's title on a knight the
     * moment there were two.
     */
    static String titleOf(GameObject hero, DungeonSettings settings) {
        var his = settings.heroNamed(hero.getTemplate().name()).title();
        return his == null || his.isBlank() ? settings.hud().heroTitle() : his;
    }

    /** What the player calls him, falling back to what the code calls him. */
    private static String nameOf(GameObject hero) {
        return uz.dukeengine.core.thing.Titled.of(hero.getTemplate());
    }

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] NUMERALS = {
        "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I",
    };

    /**
     * How far down he is, and — when the descent has a bottom — how far down it
     * goes.
     *
     * <p>Worth the second numeral. A floor with nothing under it is a floor the
     * player should be able to recognise <em>before</em> he beats it, and the
     * panel has no other way to say so: III on its own is a number that only ever
     * goes up, which is exactly the thing that is no longer true.
     *
     * <p>Composed here rather than sent as two fields, for the same reason every
     * other word on the panel is: the client draws four games and has no idea how
     * any of them counts its floors.
     *
     * <p>Told how deep it goes rather than reading it off the settings, because
     * that is a property of what is being played: a stage is one floor deep
     * however long the file's list of bosses is, and a panel promising nine more
     * floors that do not exist is a panel lying to the player. See {@code Floors}.
     */
    private static String howFarDown(int depth, int lastDepth) {
        return lastDepth > 0
                ? roman(depth) + " / " + roman(lastDepth)
                : roman(depth);
    }

    /**
     * Depth in Roman numerals, because it is the one number in the game that only
     * ever goes up, and a numeral says that in a way a digit does not.
     *
     * <p>Anything a numeral cannot say — nothing at all, or more floors than Rome
     * could count — is given back as a digit rather than as a wrong numeral.
     */
    static String roman(int depth) {
        if (depth < 1 || depth > 3999) {
            return String.valueOf(depth);
        }
        var numeral = new StringBuilder();
        int left = depth;
        for (int i = 0; i < VALUES.length; i++) {
            while (left >= VALUES[i]) {
                numeral.append(NUMERALS[i]);
                left -= VALUES[i];
            }
        }
        return numeral.toString();
    }
}
