package uz.dukeengine.dungeon.story;

/**
 * A person: the Monster template they are, what they say when talked to, and how near a hero comes before they speak.
 *
 * @param talk   the conversation they greet with, or blank for none
 * @param radius how near, in world units, a hero walks before they talk of themselves
 */
public record Npc(String name, String talk, float radius) {

    public static final Npc DEFAULTS = new Npc("", "", 30f);
}
