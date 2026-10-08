package uz.dukeengine.dungeon.story;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.dungeon.run.Tracker;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;

/**
 * A run's quests, as the simulation keeps them: which are going and how far each has got, which are finished, and
 * which conversations have been heard. Plain lists in the order things happened — never a hash's order — so it is the
 * same on every machine of a party, and plain records, so a save can write it as it is.
 *
 * <p>It hears the world through its events — a death, a talk, a place reached, the bags looked at — and each goal
 * moves on its own event and only on it. It touches no world: what it gives a bag it puts in the bag, and the
 * experience it pays is held for the run to hand the heroes ({@link #takeExperience}).
 */
public final class QuestLog {

    /** A quest going: its name, the step it is on, how far that step has counted, and the frame the step began. */
    public record Active(String quest, int step, int count, int since) {
    }

    /** What a person shows over their head: nothing, a quest to give, or a quest waiting on them. */
    public enum Mark {
        NONE, GIVE, HAND_IN
    }

    private final Story story;
    private final List<Loot> items;
    private final int join;

    private final List<Active> active = new ArrayList<>();
    private final List<String> finished = new ArrayList<>();
    private final List<String> heard = new ArrayList<>();
    private int experience;
    private int now;
    /** Every conversation played this session, in order — for the window, from any thread; never cleared. */
    private volatile List<String> said = List.of();

    /**
     * @param items every LootItem the files have, for what a quest gives
     * @param join  how many alike things join into one of the next level: what a joined thing counts as
     */
    public QuestLog(Story story, List<Loot> items, int join) {
        this.story = story;
        this.items = List.copyOf(items);
        this.join = Math.max(1, join);
    }

    /** Everything back to nothing: a run has ended. What was said stays said, for the window's count. */
    public void reset() {
        active.clear();
        finished.clear();
        heard.clear();
        experience = 0;
    }

    /** The frame it is now, for when a step begins. */
    public void frame(int frame) {
        now = frame;
    }

    /** Start {@code quest}, unless it is going or finished already. */
    public void start(String quest) {
        var known = story.quest(quest);
        if (known == null || finished.contains(quest) || active.stream().anyMatch(a -> a.quest().equals(quest))) {
            return;
        }
        active.add(new Active(quest, 0, 0, now));
    }

    /** Play {@code conversation}: heard, said, and its quests started. */
    public void play(String conversation) {
        var known = story.conversation(conversation);
        if (known == null) {
            return;
        }
        if (!heard.contains(conversation)) {
            heard.add(conversation);
        }
        var more = new ArrayList<>(said);
        more.add(conversation);
        said = List.copyOf(more);
        for (var quest : known.starts()) {
            start(quest);
        }
    }

    // ---- the events ----

    /** Something of {@code template} died: every KILL of it counts one. */
    public void died(String template, List<LootBag> bags) {
        for (var each : List.copyOf(active)) {
            var step = stepOf(each);
            if (step.goal() == Quest.Goal.KILL && step.target().equals(template)) {
                counted(each, each.count() + 1, bags.isEmpty() ? null : bags.getFirst());
            }
        }
    }

    /** A hero stands in the zone {@code zone}: every REACH of it is done. */
    public void reached(String zone, LootBag bag) {
        for (var each : List.copyOf(active)) {
            var step = stepOf(each);
            if (step.goal() == Quest.Goal.REACH && step.target().equals(zone)) {
                finish(each, bag);
            }
        }
    }

    /** The heroes' bags, looked at: every COLLECT counts what they hold between them. */
    public void holding(List<LootBag> bags) {
        for (var each : List.copyOf(active)) {
            var step = stepOf(each);
            if (step.goal() == Quest.Goal.COLLECT) {
                int held = 0;
                for (var bag : bags) {
                    held += worth(bag, step.target());
                }
                if (held != each.count()) {
                    counted(each, held, bags.isEmpty() ? null : bags.getFirst());
                }
            }
        }
    }

    /**
     * A hero whose bag is {@code bag} talks to {@code npc}: a quest waiting on them is handed in — the first, in the
     * order they were started — or, failing that, their greeting plays if it has not been heard, or was {@code asked}
     * for.
     *
     * @return whether anything was said or done
     */
    public boolean talk(String npc, LootBag bag, boolean asked) {
        for (var each : List.copyOf(active)) {
            var step = stepOf(each);
            if (!step.target().equals(npc)) {
                continue;
            }
            if (step.goal() == Quest.Goal.TALK) {
                finish(each, bag);
                return true;
            }
            if (step.goal() == Quest.Goal.GIVE && bag != null && worth(bag, step.item()) >= step.count()) {
                hand(bag, step.item(), step.count());
                finish(each, bag);
                return true;
            }
        }
        var person = story.npc(npc);
        if (person != null && !person.talk().isBlank() && (asked || !heard.contains(person.talk()))) {
            play(person.talk());
            return true;
        }
        return false;
    }

    /** What {@code npc} shows over their head. */
    public Mark markOf(String npc) {
        for (var each : active) {
            var step = stepOf(each);
            if ((step.goal() == Quest.Goal.TALK || step.goal() == Quest.Goal.GIVE) && step.target().equals(npc)) {
                return Mark.HAND_IN;
            }
        }
        var person = story.npc(npc);
        var talk = person == null ? null : story.conversation(person.talk());
        return talk != null && !talk.starts().isEmpty() && !heard.contains(talk.name()) ? Mark.GIVE : Mark.NONE;
    }

    // ---- what it gives ----

    /** The experience earned since last asked, for the run to hand every hero; nothing the next time. */
    public int takeExperience() {
        int earned = experience;
        experience = 0;
        return earned;
    }

    // ---- the window and the tracker ----

    /** Every conversation played this session, in order: the window shows them one at a time. */
    public List<String> said() {
        return said;
    }

    /** The quests going, as the tracker lists them: the MAIN ones first, then the SIDE, each in start order. */
    public List<Tracker.Entry> entries() {
        var entries = new ArrayList<Tracker.Entry>();
        for (var kind : Tracker.Kind.values()) {
            for (var each : active) {
                var quest = story.quest(each.quest());
                if (quest.kind() == kind) {
                    var step = quest.steps().get(each.step());
                    entries.add(new Tracker.Entry(kind, quest.title(),
                            List.of(new Tracker.Step(step.said(each.count()), false, each.since()))));
                }
            }
        }
        return entries;
    }

    // ---- the state, as it is ----

    public List<Active> active() {
        return List.copyOf(active);
    }

    public List<String> finished() {
        return List.copyOf(finished);
    }

    public List<String> heard() {
        return List.copyOf(heard);
    }

    /** The log as a save keeps it: the quests going, in the order started, the finished ones, what was heard. */
    public record State(List<Active> active, List<String> finished, List<String> heard) {

        public State {
            active = List.copyOf(active);
            finished = List.copyOf(finished);
            heard = List.copyOf(heard);
        }
    }

    public State state() {
        return new State(active, finished, heard);
    }

    /**
     * The log as {@code saved} left it, in place of what it holds: a loaded run. A quest the files no longer have, or a
     * step past its last, is left out rather than refusing the save; every step begins again at frame 0.
     */
    public void restore(State saved) {
        reset();
        for (var each : saved.active()) {
            var quest = story.quest(each.quest());
            if (quest != null && each.step() >= 0 && each.step() < quest.steps().size()
                    && active.stream().noneMatch(a -> a.quest().equals(each.quest()))) {
                active.add(new Active(each.quest(), each.step(), Math.max(0, each.count()), 0));
            }
        }
        saved.finished().stream().filter(name -> story.quest(name) != null).forEach(finished::add);
        saved.heard().stream().filter(name -> story.conversation(name) != null).forEach(heard::add);
    }

    // ---- inside ----

    private Quest.Step stepOf(Active each) {
        return story.quest(each.quest()).steps().get(each.step());
    }

    private void counted(Active each, int count, LootBag bag) {
        if (count >= stepOf(each).count()) {
            finish(each, bag);
        } else if (active.contains(each)) {
            active.set(active.indexOf(each), new Active(each.quest(), each.step(), count, each.since()));
        }
    }

    /** The step {@code each} is on done: what it does when done, then the next step, or the quest finished. */
    private void finish(Active each, LootBag bag) {
        int at = active.indexOf(each);
        if (at < 0) {
            return; // finished already, by an earlier event this frame
        }
        var quest = story.quest(each.quest());
        if (each.step() + 1 < quest.steps().size()) {
            active.set(at, new Active(each.quest(), each.step() + 1, 0, now));
        } else {
            active.remove(at);
            finished.add(each.quest());
        }
        var done = quest.steps().get(each.step()).onDone();
        if (done == null) {
            return;
        }
        if (!done.says().isBlank()) {
            play(done.says());
        }
        experience += Math.max(0, done.experience());
        var item = itemNamed(done.item());
        if (item != null && bag != null) {
            bag.take(item, now, 0);
        }
        if (!done.starts().isBlank()) {
            start(done.starts());
        }
    }

    /** How many of {@code id} the bag holds, a joined one counting as all that joined into it. */
    private int worth(LootBag bag, String id) {
        int held = 0;
        for (var item : bag.slots()) {
            if (item != null && item.id().equals(id)) {
                held += (int) Math.pow(join, item.level() - 1);
            }
        }
        return held;
    }

    /** Take {@code count} of {@code id} out of the bag, a slot at a time. */
    private void hand(LootBag bag, String id, int count) {
        int given = 0;
        for (int slot = 0; slot < bag.slots().size() && given < count; slot++) {
            var item = bag.at(slot);
            if (item != null && item.id().equals(id)) {
                given += (int) Math.pow(join, item.level() - 1);
                bag.remove(slot);
            }
        }
    }

    private Loot itemNamed(String id) {
        return id == null || id.isBlank() ? null
                : items.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }
}
