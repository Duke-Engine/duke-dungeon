package uz.dukeengine.dungeon.story;

/**
 * A person: the Monster template they are, what they say when talked to, how near a hero comes before they speak, and
 * how far they stroll round home.
 *
 * @param talk   the conversation they greet with, or blank for none
 * @param radius how near, in world units, a hero walks before they talk of themselves
 * @param wander how far from home, in world units, they stroll when nobody is near; 0 stands still. A person strolls
 *               only on legs: a {@code MoveUpdate} in their Monster block
 * @param role   what they are, under their name in the target frame — {@code Savdogar}, {@code Qo'mondon} — or blank
 */
public record Npc(String name, String talk, float radius, float wander, String role) {

    public static final Npc DEFAULTS = new Npc("", "", 30f, 0f, "");

    /** A person who stands still. */
    public Npc(String name, String talk, float radius) {
        this(name, talk, radius, 0f, "");
    }
}
