package uz.dukeengine.dungeon.run;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.data.Block;
import uz.dukeengine.core.data.DukeText;
import uz.dukeengine.core.data.Field;
import uz.dukeengine.core.data.Value;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.skill.SkillRanks;

/**
 * A run saved at a floor's start: enough to lay that floor again from its seed and put the hero back on it — see
 * {@code docs/plan/2026-10-08-saving.md}. Written as {@code .duke} text, read back with the engine's {@link DukeText}.
 *
 * @param seed       the seed of the floor he is on
 * @param stage      the stage's name, or {@code null} for the descent
 * @param experience everything earned this run; his level is worked out from it
 * @param ranks      what he has learnt, by skill key
 * @param bag        what he carries, by place: the backpack's, then the worn slots'
 * @param quests     the run's quests — going, finished and heard — or {@code null} for a save written before quests
 *                   were saved, which loads with its map's starting quests
 * @param gold       what is in his purse
 * @param script     the run's triggers — fired, flags, waiting — or {@code null} for a save written before they were
 *                   saved, which loads with none fired
 * @param souls      the souls his medallion has drunk (docs/plan/2026-10-09-medallion.md)
 * @param givenTo    the person the turning gave the medallion to, which makes him cursed; blank while it never was
 * @param places     the zones a hero has stood in this run, the world map's places found; none in an older save
 * @param felled     the map's scenery taken away -- trees chopped -- by index (docs/plan/2026-10-15-food-and-fire.md);
 *                   none in an older save
 * @param mounted    whether he rode his horse (docs/plan/2026-10-15-mounts.md); on foot in an older save
 */
public record SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
        List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
        uz.dukeengine.dungeon.story.Script.State script, int gold, int souls, String givenTo, List<String> places,
        List<Integer> felled, boolean mounted) {

    /** A save of a hero on foot: as one written before riding was. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script, int gold, int souls, String givenTo, List<String> places,
            List<Integer> felled) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo, places, felled,
                false);
    }

    /** A save with no trees felled in it: as one written before chopping was. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script, int gold, int souls, String givenTo, List<String> places) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo, places, List.of());
    }

    /** A save with no places found in it: as one written before they were saved. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script, int gold, int souls, String givenTo) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo, List.of());
    }

    /** A save with no souls in it: as one written before the medallion was. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script, int gold) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, 0, "");
    }

    /** A save with no gold in it: as one written before gold was. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, script, 0);
    }

    /** A save with quests and no triggers in it: as one written before triggers were saved. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag, uz.dukeengine.dungeon.story.QuestLog.State quests) {
        this(seed, stage, depth, hero, experience, ranks, bag, quests, null);
    }

    /** A save with no quests in it: as one written before they were saved. */
    public SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
            List<Item> bag) {
        this(seed, stage, depth, hero, experience, ranks, bag, null, null);
    }

    /**
     * The format this game writes: 2, where a Slot is a place of a bag that wears things — the backpack's, then the
     * worn slots'. It reads 1 as well, written when every slot acted: those things go back as pick-ups do, worn where
     * their slot is free and otherwise into the backpack.
     */
    public static final int VERSION = 2;

    /**
     * The highest level a thing is read back at. Alike things join into one of the next level, so a level is a power of
     * what was found: none comes near this. A save past it was written by a hand or a broken disk, and its level would
     * be drawn as that many marks (the bag names a thing {@code Boots III}).
     */
    static final int MOST_JOINED = 30;

    /**
     * The most a thing is read back worth, either way, figure or extra: far past what any is found at, and low enough
     * that a bag full of them still adds up, in hundredths, inside an int (the hero's figures are summed so).
     */
    static final int MOST_WORTH = 1_000_000;

    public record Rank(char key, int rank) {
    }

    /**
     * A thing in his bag: which, at what level and worth, in which place — or -1, from a save of version 1, for
     * wherever a pick-up would put it. The rest is read from its block by id.
     */
    public record Item(int slot, String id, int level, int value, int extra) {
    }

    /** A save that cannot be read, and why: the menu leaves it out and the log says so. */
    public static final class Refused extends RuntimeException {
        Refused(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public SaveGame {
        ranks = List.copyOf(ranks);
        bag = List.copyOf(bag);
        givenTo = givenTo == null ? "" : givenTo;
        places = places == null ? List.of() : List.copyOf(places);
        felled = felled == null ? List.of() : List.copyOf(felled);
    }

    /** The same save, with what the medallion has drunk and whom the turning gave it to. */
    public SaveGame withMedallion(int souls, String givenTo) {
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo,
                places, felled, mounted);
    }

    /** The same save, with the map's places a hero has stood in this run (the world map's found places). */
    public SaveGame withPlaces(List<String> found) {
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo,
                found, felled, mounted);
    }

    /** The same save, with the map's scenery taken away -- the trees chopped -- by index. */
    public SaveGame withFelled(List<Integer> taken) {
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo,
                places, taken, mounted);
    }

    /** The same save, with whether he rides his horse. */
    public SaveGame withMounted(boolean riding) {
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold, souls, givenTo,
                places, felled, riding);
    }

    /** What this hero has, as a save. */
    public static SaveGame of(long seed, String stage, int depth, String hero, int experience, SkillRanks learnt,
            LootBag carried) {
        return of(seed, stage, depth, hero, experience, learnt, carried, null, null, 0);
    }

    /** The same, with the run's quests. */
    public static SaveGame of(long seed, String stage, int depth, String hero, int experience, SkillRanks learnt,
            LootBag carried, uz.dukeengine.dungeon.story.QuestLog.State quests,
            uz.dukeengine.dungeon.story.Script.State script, int gold) {
        var ranks = new ArrayList<Rank>();
        for (var skill : learnt.getSkills()) {
            ranks.add(new Rank(skill.key(), learnt.rankOf(skill.key())));
        }
        var bag = new ArrayList<Item>();
        var slots = carried.slots();
        for (int slot = 0; slot < slots.size(); slot++) {
            var item = slots.get(slot);
            if (item != null) {
                bag.add(new Item(slot, item.id(), item.level(), item.value(), item.extraValue()));
            }
        }
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag, quests, script, gold);
    }

    /**
     * Put what he had learnt and carried back, into a bag and a book just begun for this hero.
     *
     * <p>Only through what the engine offers anyone: a rank is raised the way a click raises it, round the skills
     * until each stands where it was — so a save no level could have reached raises no further than the rules allow —
     * and a thing goes back into the place it was saved in. One with no place — a save of version 1 — or whose place
     * the bag no longer has goes in as a pick-up would, after the rest. A thing the data no longer has is left out.
     */
    public void restore(LootBag into, SkillRanks learnt, DungeonSettings settings) {
        learnt.startWith(settings.skillsFor(hero));
        int level = settings.levelling().levelFor(experience);
        for (boolean raised = true; raised; ) {
            raised = false;
            for (var rank : ranks) {
                if (learnt.rankOf(rank.key()) < rank.rank() && learnt.raise(rank.key(), level)) {
                    raised = true;
                }
            }
        }
        into.clear();
        var homeless = new ArrayList<Loot>();
        for (var saved : bag) {
            var item = loot(saved, settings);
            if (item != null && !into.put(saved.slot(), item)) {
                homeless.add(item);
            }
        }
        homeless.forEach(item -> into.take(item, 0, 0));
    }

    private static Loot loot(Item saved, DungeonSettings settings) {
        for (var base : settings.loot()) {
            if (base.id().equals(saved.id())) {
                return new Loot(base.id(), base.name(), base.icon(), base.kind(), saved.value(), base.weight(),
                        base.minTier(), base.attribute(), base.extra(), saved.extra(), base.extraStep(),
                        saved.level(), base.use(), base.liesAs(), base.slot(), base.skills());
            }
        }
        // A generated thing is made again from its id, every roll as it was; a potion stack keeps its count, its
        // Value (docs/plan/2026-10-15-loot.md).
        var made = settings.items().resolve(saved.id());
        return made == null ? null : made.worth(saved.value());
    }

    // ---- the text ----

    /** The save as a {@code .duke} file. */
    public String text() {
        var out = new StringBuilder("; Duke Dungeon — a saved run, written by the game. See docs/plan/2026-10-08-saving.md.\n");
        out.append("Save\n");
        out.append("  Version = ").append(VERSION).append('\n');
        out.append("  Seed = ").append(seed).append('\n');
        if (stage != null) {
            out.append("  Stage = \"").append(stage).append("\"\n");
        }
        out.append("  Depth = ").append(depth).append('\n');
        out.append("  Hero = ").append(hero).append('\n');
        out.append("  Experience = ").append(experience).append('\n');
        out.append("  Gold = ").append(gold).append('\n');
        out.append("  Souls = ").append(souls).append('\n');
        if (!givenTo.isBlank()) {
            out.append("  GivenTo = ").append(givenTo).append('\n');
        }
        out.append("  Skills = [").append(String.join(", ", ranks.stream().map(r -> "" + r.key()).toList()))
                .append("]\n");
        out.append("  Ranks = [").append(String.join(", ", ranks.stream().map(r -> "" + r.rank()).toList()))
                .append("]\n");
        for (var item : bag) {
            out.append("  Item\n");
            out.append("    Slot = ").append(item.slot()).append('\n');
            out.append("    Id = ").append(item.id()).append('\n');
            out.append("    Level = ").append(item.level()).append('\n');
            out.append("    Value = ").append(item.value()).append('\n');
            out.append("    Extra = ").append(item.extra()).append('\n');
            out.append("  End\n");
        }
        if (quests != null) {
            // The run's quests: a Quest block for each going, in the order started, and the names of those finished
            // and the conversations heard. A save with no Quests line was written before quests were saved.
            out.append("  Quests = Yes\n");
            out.append("  Finished = [").append(String.join(", ", quests.finished())).append("]\n");
            out.append("  Heard = [").append(String.join(", ", quests.heard())).append("]\n");
            for (var going : quests.active()) {
                out.append("  Quest\n");
                out.append("    Name = ").append(going.quest()).append('\n');
                out.append("    Step = ").append(going.step()).append('\n');
                out.append("    Count = ").append(going.count()).append('\n');
                out.append("  End\n");
            }
        }
        if (script != null) {
            // The run's triggers: those fired, the flags set, the zones a hero stood in, and a Waiting block for each
            // waiting out its delay. A save with no Script line was written before triggers were saved.
            out.append("  Script = Yes\n");
            out.append("  Fired = [").append(String.join(", ", script.fired())).append("]\n");
            out.append("  Flags = [").append(String.join(", ", script.flags())).append("]\n");
            out.append("  Occupied = [").append(String.join(", ", script.occupied())).append("]\n");
            for (var waiting : script.waiting()) {
                out.append("  Waiting\n");
                out.append("    Trigger = ").append(waiting.trigger()).append('\n');
                out.append("    Frames = ").append(waiting.framesLeft()).append('\n');
                out.append("  End\n");
            }
        }
        if (!places.isEmpty()) {
            // The places found, quoted: a zone's name may hold an apostrophe. None written for none.
            out.append("  Places = [").append(String.join(", ", places.stream().map(p -> "\"" + p + "\"").toList()))
                    .append("]\n");
        }
        if (!felled.isEmpty()) {
            out.append("  Felled = [").append(String.join(", ", felled.stream().map(String::valueOf).toList()))
                    .append("]\n");
        }
        if (mounted) {
            out.append("  Mounted = Yes\n");
        }
        return out.append("End\n").toString();
    }

    /**
     * A save read back from its text, or {@link Refused}: broken text, a line missing, another version. Never half a
     * hero.
     *
     * @param source how the message names it — the file's name
     */
    public static SaveGame read(String text, String source) {
        try {
            var blocks = DukeText.parse(text, source);
            if (blocks.size() != 1 || !blocks.getFirst().word().equals("Save")) {
                throw new IllegalArgumentException("it holds no Save block");
            }
            var save = blocks.getFirst();
            int version = number(save, "Version");
            if (version != VERSION && version != 1) {
                throw new IllegalArgumentException("it was written as version " + version
                        + ", and this game reads versions 1 and " + VERSION);
            }
            var keys = items(save, "Skills");
            var levels = items(save, "Ranks");
            if (keys.size() != levels.size()) {
                throw new IllegalArgumentException("Skills and Ranks do not pair up");
            }
            var ranks = new ArrayList<Rank>();
            for (int at = 0; at < keys.size(); at++) {
                if (keys.get(at).length() != 1) {
                    throw new IllegalArgumentException("a skill key is one letter: " + keys.get(at));
                }
                ranks.add(new Rank(keys.get(at).charAt(0), Integer.parseInt(levels.get(at))));
                if (ranks.getLast().rank() < 0) {
                    throw new IllegalArgumentException("a rank below nothing: " + levels.get(at));
                }
            }
            var bag = new ArrayList<Item>();
            var going = new ArrayList<uz.dukeengine.dungeon.story.QuestLog.Active>();
            var waiting = new ArrayList<uz.dukeengine.dungeon.story.Script.Waiting>();
            for (var item : save.blocks()) {
                if (item.word().equals("Waiting")) {
                    waiting.add(new uz.dukeengine.dungeon.story.Script.Waiting(word(item, "Trigger"),
                            counted(item, "Frames")));
                    continue;
                }
                if (item.word().equals("Quest")) {
                    going.add(new uz.dukeengine.dungeon.story.QuestLog.Active(word(item, "Name"),
                            counted(item, "Step"), counted(item, "Count"), 0));
                    continue;
                }
                if (!item.word().equals("Item")) {
                    throw new IllegalArgumentException("an unknown block: " + item.word());
                }
                // Version 1's slots were all acting ones, which a bag that wears things numbers otherwise.
                bag.add(new Item(version == 1 ? -1 : counted(item, "Slot"), word(item, "Id"),
                        within(item, "Level", 1, MOST_JOINED), within(item, "Value", -MOST_WORTH, MOST_WORTH),
                        within(item, "Extra", -MOST_WORTH, MOST_WORTH)));
            }
            var quests = field(save, "Quests") == null ? null
                    : new uz.dukeengine.dungeon.story.QuestLog.State(going, items(save, "Finished"),
                            items(save, "Heard"));
            var script = field(save, "Script") == null ? null
                    : new uz.dukeengine.dungeon.story.Script.State(items(save, "Fired"), items(save, "Flags"), waiting,
                            items(save, "Occupied"));
            var stage = field(save, "Stage");
            return new SaveGame(Long.parseLong(word(save, "Seed")), stage == null ? null : text(stage, "Stage"),
                    within(save, "Depth", 1, Integer.MAX_VALUE), word(save, "Hero"), counted(save, "Experience"),
                    ranks, bag, quests, script, field(save, "Gold") == null ? 0 : counted(save, "Gold"),
                    field(save, "Souls") == null ? 0 : counted(save, "Souls"),
                    field(save, "GivenTo") == null ? "" : word(save, "GivenTo"),
                    field(save, "Places") == null ? List.of() : items(save, "Places"),
                    field(save, "Felled") == null ? List.of()
                            : items(save, "Felled").stream().map(Integer::parseInt).toList(),
                    field(save, "Mounted") != null && word(save, "Mounted").equalsIgnoreCase("Yes"));
        } catch (RuntimeException e) {
            throw new Refused(source + " cannot be loaded: " + e.getMessage(), e);
        }
    }

    private static Field field(Block block, String key) {
        return block.fields().stream().filter(field -> field.key().equals(key)).findFirst().orElse(null);
    }

    private static String word(Block block, String key) {
        var field = field(block, key);
        if (field == null) {
            throw new IllegalArgumentException(block.word() + " has no " + key);
        }
        return text(field, key);
    }

    private static String text(Field field, String key) {
        if (!(field.value() instanceof Value.Text text)) {
            throw new IllegalArgumentException(key + " is not a single value");
        }
        return text.text();
    }

    private static int number(Block block, String key) {
        return Integer.parseInt(word(block, key));
    }

    /** A number no game writes below 0: a count, a slot, a frame. */
    private static int counted(Block block, String key) {
        return within(block, key, 0, Integer.MAX_VALUE);
    }

    private static int within(Block block, String key, int least, int most) {
        int number = number(block, key);
        if (number < least || number > most) {
            throw new IllegalArgumentException(key + " is " + number + ", and a game writes " + least + " to " + most);
        }
        return number;
    }

    private static List<String> items(Block block, String key) {
        var field = field(block, key);
        if (field == null) {
            throw new IllegalArgumentException(block.word() + " has no " + key);
        }
        return switch (field.value()) {
            case Value.Items list -> list.items();
            case Value.Text one when one.text().isBlank() -> List.of();
            case Value.Text one -> List.of(one.text());
            default -> throw new IllegalArgumentException(key + " is not a list");
        };
    }
}
