package uz.dukeengine.dungeon.content;

/**
 * A hero struck hard staggers: a heavy blow on him (his {@link HeavyBlow}'s {@code MajorAt} or more, after his armour)
 * stuns him for {@code frames} -- the skill system's own stun, the engine's {@code DISABLED}, so his weapon and his
 * skills stand still under it -- while his {@code Hurt} clip plays for exactly those frames; and no second stagger
 * takes him for {@code immuneFrames} after it ends, so a crowd cannot hold him in one. See {@code run.Staggering}.
 *
 * @param frames       how long he staggers; 0 for never
 * @param immuneFrames how long after a stagger ends before another can take him
 */
public record Stagger(int frames, int immuneFrames) {

    public static final Stagger DEFAULTS = new Stagger(0, 0);
}
