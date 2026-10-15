package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * A person: the Monster template they are, what they say when talked to, how near a hero comes before they speak, and
 * how far they stroll round home -- or, a traveller, the places they walk between.
 *
 * @param talk   the conversation they greet with, or blank for none
 * @param radius how near, in world units, a hero walks before they talk of themselves
 * @param wander how far from home, in world units, they stroll when nobody is near; 0 stands still. A person strolls
 *               only on legs: a {@code MoveUpdate} in their Monster block
 * @param role   what they are, under their name in the target frame — {@code Savdogar}, {@code Qo'mondon} — or blank
 * @param route  the zones of the map a traveller walks between, in turn, round and round: from the one they are in (or
 *               nearest) on to the next. Empty for a person who keeps to home
 */
public record Npc(String name, String talk, float radius, float wander, String role, List<String> route) {

    public static final Npc DEFAULTS = new Npc("", "", 30f, 0f, "", List.of());

    public Npc {
        route = route == null ? List.of() : List.copyOf(route);
    }

    /** A person who stands still. */
    public Npc(String name, String talk, float radius) {
        this(name, talk, radius, 0f, "", List.of());
    }
}
