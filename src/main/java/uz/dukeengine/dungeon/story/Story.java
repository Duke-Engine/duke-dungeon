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
        List<Quest> quests) {

    public static final Story NONE = new Story(Dialogue.DEFAULTS, List.of(), List.of(), List.of(), List.of());

    public Story {
        dialogue = dialogue == null ? Dialogue.DEFAULTS : dialogue;
        speakers = List.copyOf(speakers);
        conversations = List.copyOf(conversations);
        npcs = List.copyOf(npcs);
        quests = List.copyOf(quests);
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
        return wrong;
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
