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
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.skill.SkillRanks;

/**
 * A run saved at a floor's start: enough to lay that floor again from its seed and put the hero back on it — see
 * {@code docs/plan/2026-10-08-saving.md}. Written as {@code .duke} text, read back with the engine's {@link DukeText}.
 *
 * @param seed       the seed of the floor he is on
 * @param stage      the stage's name, or {@code null} for the descent
 * @param experience everything earned this run; his level is worked out from it
 * @param ranks      what he has learnt, by skill key
 * @param bag        what he carries, by slot
 */
public record SaveGame(long seed, String stage, int depth, String hero, int experience, List<Rank> ranks,
        List<Item> bag) {

    /** The format this game writes and the only one it reads. */
    public static final int VERSION = 1;

    public record Rank(char key, int rank) {
    }

    /** A thing in his bag: which, at what level and worth, in which slot. The rest is read from its block by id. */
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
    }

    /** What this hero has, as a save. */
    public static SaveGame of(long seed, String stage, int depth, String hero, int experience, SkillRanks learnt,
            LootBag carried) {
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
        return new SaveGame(seed, stage, depth, hero, experience, ranks, bag);
    }

    /**
     * Put what he had learnt and carried back, into a bag and a book just begun for this hero.
     *
     * <p>Only through what the engine offers anyone: a rank is raised the way a click raises it, round the skills
     * until each stands where it was — so a save no level could have reached raises no further than the rules allow —
     * and a thing goes into the bag the way a pick-up puts it there, each slot in order, an empty one held by a
     * stand-in taken out again. A thing the data no longer has is left out.
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
        var holder = new Loot("", "", "", "", 0, 0, 0, "", "", 0, 0, 1, ItemUse.UNLOCK, "");
        int last = bag.stream().mapToInt(Item::slot).max().orElse(-1);
        var held = new ArrayList<Integer>();
        for (int slot = 0; slot <= last; slot++) {
            var item = itemAt(slot, settings);
            into.take(item == null ? holder : item, 0, 0);
            if (item == null) {
                held.add(slot);
            }
        }
        held.forEach(into::remove);
    }

    private Loot itemAt(int slot, DungeonSettings settings) {
        for (var saved : bag) {
            if (saved.slot() != slot) {
                continue;
            }
            for (var base : settings.loot()) {
                if (base.id().equals(saved.id())) {
                    return new Loot(base.id(), base.name(), base.icon(), base.kind(), saved.value(), base.weight(),
                            base.minTier(), base.attribute(), base.extra(), saved.extra(), base.extraStep(),
                            saved.level(), base.use(), base.liesAs());
                }
            }
        }
        return null;
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
            if (version != VERSION) {
                throw new IllegalArgumentException("it was written as version " + version
                        + ", and this game reads version " + VERSION);
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
            }
            var bag = new ArrayList<Item>();
            for (var item : save.blocks()) {
                if (!item.word().equals("Item")) {
                    throw new IllegalArgumentException("an unknown block: " + item.word());
                }
                bag.add(new Item(number(item, "Slot"), word(item, "Id"), number(item, "Level"),
                        number(item, "Value"), number(item, "Extra")));
            }
            var stage = field(save, "Stage");
            return new SaveGame(Long.parseLong(word(save, "Seed")), stage == null ? null : text(stage, "Stage"),
                    number(save, "Depth"), word(save, "Hero"), number(save, "Experience"), ranks, bag);
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
