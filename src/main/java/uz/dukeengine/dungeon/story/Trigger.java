package uz.dukeengine.dungeon.story;

import java.util.List;

/**
 * When something happens, do a list of things, in order — see {@code docs/plan/2026-10-08-story-scripting.md}.
 *
 * @param map     the map it belongs to, by its name; blank for every floor
 * @param target  what of: a zone, a Monster, a quest, a conversation, a trigger, a flag — by {@link #when}
 * @param step    for {@link When#STEP_DONE}, which step, 1 its first
 * @param delay   frames after the event before it acts
 * @param repeat  whether it fires every time, rather than once
 * @param If      what must hold, every one, the moment its When is heard; one that does not leaves it unfired, and a
 *                once-only trigger waits for the next time. Named as the file writes it, {@code If = [...]}
 */
public record Trigger(String name, String map, When when, String target, int step, int delay, boolean repeat,
        List<Condition> If, List<Action> actions) {

    public static final Trigger DEFAULTS = new Trigger("", "", null, "", 0, 0, false, List.of(), List.of());

    public Trigger {
        If = If == null ? List.of() : List.copyOf(If);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** What a condition asks. Of a party, any hero holding or wearing it will do. */
    public enum Is {
        /** The flag is set. */
        FLAG_SET,
        /** The flag is not set. */
        FLAG_UNSET,
        /** A hero carries the LootItem, worn or in his backpack. */
        HOLDS,
        /** A hero wears the LootItem. */
        WEARS,
        /** No hero carries the LootItem anywhere: HOLDS turned round. */
        LACKS,
        /** The quest is going. */
        QUEST_ACTIVE,
        /** The quest is finished. */
        QUEST_DONE,
        /** One of the Monster stands alive on the floor. */
        ALIVE,
        /** None of the Monster stands alive on the floor. */
        DEAD
    }

    /** One thing that must hold: {@code Is}, of {@code Target} — a flag, a LootItem, a quest or a Monster. */
    public record Condition(Is is, String target) {

        public static final Condition DEFAULTS = new Condition(null, "");
    }

    /** What a trigger waits for. */
    public enum When {
        MAP_START, ZONE, DIED, QUEST_STARTED, QUEST_DONE, STEP_DONE, HEARD, AFTER, FLAG,
        /** A Monster that cannot be killed is beaten — see {@link Boss#beaten}. */
        BEATEN,
        /** A cutscene has played to its end, or was skipped. */
        SEEN,
        /** A floor is laid at the depth its Target names: a campaign's descent reaching it. */
        DEPTH,
        /** The zone named holds no creature of the dungeon's standing any more, heard as one of them dies: a camp cleared. */
        CLEARED
    }

    /** What a trigger does. */
    public enum Act {
        SAY, START_QUEST, FINISH_QUEST, SPAWN, WALK, OPEN_GATE, GIVE, TAKE, FLAG, MAP, CUTSCENE,
        /** Every one of a Monster standing on the floor walks to the zone — in a boss's phase, with no Zone, to it. */
        CALL,
        /** An Effect played at the zone's middle — in a boss's phase, with no Zone, on it. */
        EFFECT,
        /** The run ends won, the banner saying the Target's words (blank: no banner at all). */
        WIN,
        /** The turning: the medallion leaves the hero for the Npc named, his look darkens, he goes to the monsters. */
        CURSE,
        /** {@code Count} of a Monster at the zone, on the heroes' side: they fight for him. */
        ALLY,
        /** Every hero stands at the zone's middle at once: the hero's landing. */
        PLACE,
        /** Every one of a Monster standing is thrown to the zone's middle at once: a knock-back. */
        KNOCK,
        /** Every one of a Monster standing dies, beaten or not: the commander's blow. */
        KILL,
        /** {@code Count} gold into the first hero's purse. */
        GOLD,
        /** The run written as it is now into the save slot the Target names, won or not: an act's end. */
        SAVE,
        /** Every one of the Target (a Monster, or {@code Hero}: the heroes) slides to the zone over Frames (E40). */
        MOVE,
        /** The same over an arc Height high at its middle: a leap, a knock-back's hop. */
        LEAP,
        /** The same dropped straight down onto the zone from Height above it: a fall from the sky. */
        FALL,
        /** A Title block's card over the screen: a chapter's name, an act's end (E42). */
        TITLE,
        /** Every creature of the dungeon's side standing in the zone taken away, unheard: a camp freed already, laid again. */
        CLEAR,
        /** The run written now as the checkpoint a death goes back to, and the autosave: progress on one great map. */
        CHECKPOINT,
        /** Every one of a Monster standing grown to the level the run carries of the act before (its campaign's
         * {@code <Follows>Level=N} flag), or to {@code Count} without one: the cursed hero is the player's own. */
        GROW,
        /** The game ends here: back to the front menu, once the credits have rolled. */
        MENU,
        /** The Credits block rolled over the screen with its music; the front menu once it has rolled or is skipped. */
        CREDITS,
        /** Every one of a Monster or a person standing taken away, unheard: the caged girl's cage, once she is out. */
        REMOVE,
        /** A LootItem laid on the ground at the zone's middle: the ring in the cellar, laid as she asks for it. */
        LAY,
        /** The Battle block the Target names laid: both armies and the ritualists round its landing. */
        BATTLE,
        /** Every one of a Monster standing plays {@code Clip} -- {@code Else} where it is drawn as a stand-in without it
         * -- for Frames, standing still: the ritualists' ritual (E67's busy clip). */
        POSE,
        /** A beam of the Laser {@code Drawn} for Frames: from each of a Monster standing (9 up, at their hands) to Height
         * above the zone's middle; with no Target, from the zone's ground straight up Height: a pillar of light. */
        BEAM,
        /** The heroes (Target Hero) lifted Height above the zone's middle and held there, out of sight, until a
         * STREAK brings them down. */
        SKY,
        /** The heroes carried down onto the zone over Frames from Height above it and Lean off toward Bearing (degrees,
         * 0 east, 90 south): a meteor's angled fall. {@code Drawn} is played on them every frame of it (the burning
         * trail), {@code Landed} as they come down (the impact). */
        STREAK,
        /** The Shockwave block the Target names, from the zone's middle: the dungeon's side thrown out and stunned. */
        SHOCKWAVE,
        /** Every fighter standing in the zone held still -- no blow, no step -- from now until a RELEASE. */
        HOLD,
        /** What a HOLD holds, let go. */
        RELEASE,
        /** The sound cue the Target names, heard at the zone's middle: the meteor's boom. */
        SOUND
    }

    /** The Target of a MOVE, LEAP or FALL that is the heroes rather than a Monster. */
    public static final String HEROES = "Hero";

    /**
     * One thing done.
     *
     * @param target what: a conversation, quest, Monster, Npc, LootItem, flag, map, cutscene, Effect, Title or a
     *               banner's words — by {@link #act}
     * @param count  how many, for SPAWN and ALLY; how much, for GOLD
     * @param zone   where, for SPAWN, ALLY, WALK, CALL, EFFECT, PLACE, KNOCK, MOVE, LEAP and FALL
     * @param frames how long a MOVE, LEAP or FALL takes
     * @param height a LEAP's arc at its middle; how far above the zone a FALL begins
     * @param landed an Effect played on the thing as a MOVE, LEAP, FALL or STREAK comes down — its dust, its knock of
     *               the camera (the Effect's ShakeSeconds and ShakePower); blank for none
     * @param clip   the clip a POSE plays; {@code orElse} where the look drawn has none of that name (a stand-in)
     * @param drawn  a BEAM's Laser; an Effect a STREAK plays on what it carries every frame, or REMOVE on what it takes
     * @param lean   how far off, in units, a STREAK begins, toward {@code bearing} (degrees: 0 east, 90 south)
     */
    public record Action(Act act, String target, int count, String zone, int frames, float height, String landed,
            String clip, String orElse, String drawn, float lean, float bearing) {

        public static final Action DEFAULTS = new Action(null, "", 1, "", 30, 0f, "", "", "", "", 0f, 0f);

        /** An action of only the first fields, the rest as a file leaving them out. */
        public Action(Act act, String target, int count, String zone, int frames, float height, String landed) {
            this(act, target, count, zone, frames, height, landed, "", "", "", 0f, 0f);
        }
    }
}
