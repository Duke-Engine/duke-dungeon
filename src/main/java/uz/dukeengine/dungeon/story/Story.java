package uz.dukeengine.dungeon.story;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Everything the files say about people and their talk: who may speak, the conversations, the people, the quests,
 * each in file order. Read with the settings, and checked there: a name that points at nothing is refused when the
 * files are read, never found in play.
 */
public record Story(Dialogue dialogue, List<Speaker> speakers, List<Conversation> conversations, List<Npc> npcs,
        List<Quest> quests, List<Trigger> triggers, List<Cutscene> cutscenes, Trade trade,
        List<Merchant> merchants, List<Boss> bosses) {

    public static final Story NONE = new Story(Dialogue.DEFAULTS, List.of(), List.of(), List.of(), List.of());

    public Story {
        dialogue = dialogue == null ? Dialogue.DEFAULTS : dialogue;
        trade = trade == null ? Trade.DEFAULTS : trade;
        merchants = List.copyOf(merchants);
        bosses = bosses == null ? List.of() : List.copyOf(bosses);
        speakers = List.copyOf(speakers);
        conversations = List.copyOf(conversations);
        npcs = List.copyOf(npcs);
        quests = List.copyOf(quests);
        triggers = List.copyOf(triggers);
        cutscenes = List.copyOf(cutscenes);
    }

    /** A story with no triggers and no cutscenes. */
    public Story(Dialogue dialogue, List<Speaker> speakers, List<Conversation> conversations, List<Npc> npcs,
            List<Quest> quests) {
        this(dialogue, speakers, conversations, npcs, quests, List.of(), List.of(), Trade.DEFAULTS, List.of(),
                List.of());
    }

    /** The Boss block of that Monster, or null for one the story does not fight. */
    public Boss boss(String name) {
        return bosses.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** The merchant of that name, or null for a person who does not trade. */
    public Merchant merchant(String name) {
        return merchants.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** What one of {@code item} costs: the first merchant's price for it, or the Trade block's BasePrice. */
    public int priceOf(String item) {
        for (var merchant : merchants) {
            for (var ware : merchant.stock()) {
                if (ware.item().equals(item)) {
                    return ware.price();
                }
            }
        }
        return trade.basePrice();
    }

    public Trigger trigger(String name) {
        return triggers.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    public Cutscene cutscene(String name) {
        return cutscenes.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    public Speaker speaker(String name) {
        return speakers.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    public Conversation conversation(String name) {
        return conversations.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    public Npc npc(String name) {
        return npcs.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    public Quest quest(String name) {
        return quests.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /**
     * What is wrong with it, given the Monster templates and LootItems the files have: a name that points at nothing,
     * a conversation with nothing in it, a step with no goal. Empty for a story that can be played.
     */
    public List<String> problems(Collection<String> monsters, Collection<String> items) {
        var wrong = new ArrayList<String>();
        for (var conversation : conversations) {
            var named = "Conversation " + conversation.name();
            if (conversation.lines().isEmpty()) {
                wrong.add(named + " has no Lines");
            }
            for (var line : conversation.lines()) {
                if (!Speaker.HERO.equals(line.speaker()) && speaker(line.speaker()) == null) {
                    wrong.add(named + " has a line said by " + line.speaker() + ", and no Speaker is called that");
                }
            }
            for (var start : conversation.starts()) {
                quest(start, named + " Starts", wrong);
            }
        }
        for (var npc : npcs) {
            if (!monsters.contains(npc.name())) {
                wrong.add("Npc " + npc.name() + " is no Monster: a person is a Monster block of that Name");
            }
            if (!npc.talk().isBlank()) {
                conversation(npc.talk(), "Npc " + npc.name() + " Talk", wrong);
            }
        }
        for (var quest : quests) {
            var named = "Quest " + quest.name();
            if (quest.steps().isEmpty()) {
                wrong.add(named + " has no Steps");
            }
            for (int i = 0; i < quest.steps().size(); i++) {
                step(quest.steps().get(i), named + " step " + (i + 1), monsters, items, wrong);
            }
        }
        for (var trigger : triggers) {
            trigger(trigger, monsters, items, wrong);
        }
        for (var boss : bosses) {
            var named = "Boss " + boss.name();
            if (!monsters.contains(boss.name())) {
                wrong.add(named + " is no Monster: a boss is a Monster block of that Name");
            }
            if (boss.beaten() < 0 || boss.beaten() >= 100) {
                wrong.add(named + " is beaten at " + boss.beaten() + "% of its health: 0 (it dies) to 99");
            }
            for (int i = 0; i < boss.phases().size(); i++) {
                var phase = boss.phases().get(i);
                var which = named + " phase " + (i + 1);
                if (phase.below() < 1 || phase.below() > 100 || phase.damageTaken() < 0) {
                    wrong.add(which + ": Below is 1 to 100, and DamageTaken at least 0");
                }
                for (var action : phase.actions()) {
                    action(which, action, true, monsters, items, wrong);
                }
            }
        }
        for (var merchant : merchants) {
            var named = "Merchant " + merchant.name();
            if (npc(merchant.name()) == null) {
                wrong.add(named + " is no Npc: a merchant is a person of that Name");
            }
            for (var ware : merchant.stock()) {
                if (!items.contains(ware.item())) {
                    wrong.add(named + " sells " + ware.item() + ", and no LootItem is called that");
                }
                if (ware.price() < 0 || ware.count() < 1) {
                    wrong.add(named + " sells " + ware.item() + " for less than nothing, or none of it");
                }
            }
        }
        if (trade.sellPercent() < 0 || trade.sellPercent() > 100 || trade.wanderFrames() < 1) {
            wrong.add("Trade: SellPercent is 0 to 100, and WanderFrames at least 1");
        }
        for (var cutscene : cutscenes) {
            var named = "Cutscene " + cutscene.name();
            if (cutscene.shots().isEmpty()) {
                wrong.add(named + " has no Shots");
            }
            for (var shot : cutscene.shots()) {
                if (!shot.says().isBlank()) {
                    conversation(shot.says(), named + " Says", wrong);
                }
                if (shot.frames() < 0 || shot.hold() < 0) {
                    wrong.add(named + " has a shot of fewer than no frames");
                }
            }
        }
        return wrong;
    }

    private void trigger(Trigger trigger, Collection<String> monsters, Collection<String> items, List<String> wrong) {
        var named = "Trigger " + trigger.name();
        if (trigger.when() == null) {
            wrong.add(named + " has no When: one of " + java.util.Arrays.toString(Trigger.When.values()));
            return;
        }
        if (trigger.delay() < 0) {
            wrong.add(named + " has a Delay of fewer than no frames");
        }
        var target = trigger.target();
        switch (trigger.when()) {
            case MAP_START -> {
            }
            case ZONE, FLAG -> {
                if (target.isBlank()) {
                    wrong.add(named + " waits on a " + trigger.when() + " and names none");
                }
            }
            case DIED, BEATEN -> {
                if (!monsters.contains(target)) {
                    wrong.add(named + " waits for a " + target + " to fall, and no Monster is called that");
                }
            }
            case SEEN -> {
                if (cutscene(target) == null) {
                    wrong.add(named + " waits for " + target + " to be seen, and no Cutscene is called that");
                }
            }
            case QUEST_STARTED, QUEST_DONE, STEP_DONE -> {
                var quest = quest(target);
                if (quest == null) {
                    wrong.add(named + " waits on " + target + ", and no Quest is called that");
                } else if (trigger.when() == Trigger.When.STEP_DONE
                        && (trigger.step() < 1 || trigger.step() > quest.steps().size())) {
                    wrong.add(named + " waits on step " + trigger.step() + " of " + target + ", which has "
                            + quest.steps().size());
                }
            }
            case HEARD -> conversation(target, named, wrong);
            case AFTER -> {
                if (trigger(target) == null) {
                    wrong.add(named + " comes after " + target + ", and no Trigger is called that");
                }
            }
        }
        if (trigger.actions().isEmpty()) {
            wrong.add(named + " does nothing: it has no Actions");
        }
        for (var action : trigger.actions()) {
            action(named, action, false, monsters, items, wrong);
        }
        for (var condition : trigger.If()) {
            var what = condition.target();
            boolean known = condition.is() != null && switch (condition.is()) {
                case FLAG_SET, FLAG_UNSET -> !what.isBlank();
                case HOLDS, WEARS -> items.contains(what);
                case QUEST_ACTIVE, QUEST_DONE -> quest(what) != null;
                case ALIVE, DEAD -> monsters.contains(what);
            };
            if (!known) {
                wrong.add(named + " asks If " + condition.is() + " " + what + ", and the files have no such thing: "
                        + "a flag, a LootItem, a Quest or a Monster, by what it asks");
            }
        }
    }

    /** What is wrong with one action: a trigger's, or ({@code inPhase}) a phase's, whose CALL and EFFECT need no Zone. */
    private void action(String named, Trigger.Action action, boolean inPhase, Collection<String> monsters,
            Collection<String> items, List<String> wrong) {
        if (action.act() == null) {
            wrong.add(named + " has an Action with no Act");
            return;
        }
        var what = action.target();
        switch (action.act()) {
            case CALL -> {
                if (!monsters.contains(what) || !inPhase && action.zone().isBlank()) {
                    wrong.add(named + " calls " + what + ", who is no Monster, or to no Zone");
                }
            }
            case EFFECT -> {
                if (what.isBlank() || !inPhase && action.zone().isBlank()) {
                    wrong.add(named + " plays an EFFECT that names none, or at no Zone");
                }
            }
            case WIN -> {
            }
            case SAY -> conversation(what, named, wrong);
            case START_QUEST, FINISH_QUEST -> quest(what, named, wrong);
            case SPAWN -> {
                if (!monsters.contains(what)) {
                    wrong.add(named + " spawns " + what + ", and no Monster is called that");
                }
                if (action.zone().isBlank() || action.count() < 1) {
                    wrong.add(named + " spawns " + what + " with no Zone, or fewer than one");
                }
            }
            case WALK -> {
                if (npc(what) == null || action.zone().isBlank()) {
                    wrong.add(named + " walks " + what + ", who is no Npc, or to no Zone");
                }
            }
            case GIVE, TAKE -> {
                if (!items.contains(what)) {
                    wrong.add(named + " gives or takes " + what + ", and no LootItem is called that");
                }
            }
            case FLAG, MAP -> {
                if (what.isBlank()) {
                    wrong.add(named + " has a " + action.act() + " that names none");
                }
            }
            case CURSE -> {
                if (npc(what) == null) {
                    wrong.add(named + " gives the medallion to " + what + ", who is no Npc");
                }
            }
            case CUTSCENE -> {
                if (cutscene(what) == null) {
                    wrong.add(named + " plays " + what + ", and no Cutscene is called that");
                }
            }
            case OPEN_GATE -> {
            }
        }
    }

    private void step(Quest.Step step, String named, Collection<String> monsters, Collection<String> items,
            List<String> wrong) {
        if (step.goal() == null) {
            wrong.add(named + " has no Goal: one of REACH, KILL, COLLECT, TALK, GIVE");
            return;
        }
        if (step.count() < 1) {
            wrong.add(named + " has Count = " + step.count() + ": at least one");
        }
        switch (step.goal()) {
            case REACH -> {
                if (step.target().isBlank()) {
                    wrong.add(named + " is a REACH with no Target: the zone of the map to reach");
                }
            }
            case KILL -> {
                if (!monsters.contains(step.target())) {
                    wrong.add(named + " is a KILL of " + step.target() + ", and no Monster is called that");
                }
            }
            case COLLECT -> {
                if (!items.contains(step.target())) {
                    wrong.add(named + " is a COLLECT of " + step.target() + ", and no LootItem is called that");
                }
            }
            case TALK, GIVE -> {
                if (npc(step.target()) == null) {
                    wrong.add(named + " is a " + step.goal() + " to " + step.target() + ", and no Npc is called that");
                }
                if (step.goal() == Quest.Goal.GIVE && !items.contains(step.item())) {
                    wrong.add(named + " gives " + step.item() + ", and no LootItem is called that");
                }
            }
        }
        var done = step.onDone();
        if (done != null) {
            if (!done.says().isBlank()) {
                conversation(done.says(), named + " Says", wrong);
            }
            if (!done.starts().isBlank()) {
                quest(done.starts(), named + " Starts", wrong);
            }
            if (!done.item().isBlank() && !items.contains(done.item())) {
                wrong.add(named + " rewards " + done.item() + ", and no LootItem is called that");
            }
        }
    }

    private void conversation(String name, String where, List<String> wrong) {
        if (conversation(name) == null) {
            wrong.add(where + " names " + name + ", and no Conversation is called that");
        }
    }

    private void quest(String name, String where, List<String> wrong) {
        if (quest(name) == null) {
            wrong.add(where + " names " + name + ", and no Quest is called that");
        }
    }
}
