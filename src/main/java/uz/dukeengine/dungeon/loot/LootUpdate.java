package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.rts.module.RtsModuleGroups;

/**
 * A thing lying on the floor, waiting to be walked over.
 *
 * <p>There is no picking-up button and no inventory: he stands on it and it is
 * his. That is the whole interaction, and it is the right one for a game whose
 * decisions are about where to walk — going to fetch something is the decision,
 * and a second one at the end of it would be a formality.
 *
 * <p>Whose it is, is decided by having skills, the same rule
 * {@link uz.dukeengine.dungeon.skill.Skills} uses to find a hero — and a bag to put it in. Monsters have
 * skills too now, and no bag, so a skeleton mage walks over a chest and leaves it lying. In a party each hero
 * has his own: the nearest to stand on it takes it, the lowest id where two stand as near.
 *
 * <p>Deterministic: a distance in the simulation's own units, checked on a frame
 * boundary like everything else. What it holds was settled when it dropped.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class LootUpdate extends UpdateModule {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    /** Each player's bag by his index, or {@code null} for a player who has none: the dungeon. */
    private final java.util.function.IntFunction<LootBag> bags;
    private final float pickupRange;
    private final int noteFrames;

    private Loot holding;

    public LootUpdate(GameObject owner, java.util.function.IntFunction<LootBag> bags, float pickupRange,
            int noteFrames) {
        super(owner);
        this.bags = bags;
        this.pickupRange = pickupRange;
        this.noteFrames = noteFrames;
    }

    /** Say what is in it. Called once, by whatever dropped it. */
    void holds(Loot item) {
        this.holding = item;
    }

    /** What is in it, for a test that would rather not go and stand on it. */
    public Loot getHolding() {
        return holding;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || holding == null) {
            return;
        }
        var takers = world.objectsInRange(owner.getPosition(), pickupRange,
                candidate -> !candidate.isEffectivelyDead()
                        && candidate.findModule(SkillBook.class) != null
                        && bags.apply(candidate.getPlayerIndex()) != null);
        if (takers.isEmpty()) {
            return;
        }
        var taker = takers.getFirst();
        for (var other : takers) {
            float nearer = owner.getPosition().distance(other.getPosition());
            float nearest = owner.getPosition().distance(taker.getPosition());
            if (nearer < nearest || nearer == nearest && other.getId().value() < taker.getId().value()) {
                taker = other;
            }
        }
        bags.apply(taker.getPlayerIndex()).take(holding, world.getFrame(), noteFrames);
        holding = null;
        // Gone the moment it is his: a chest that stayed would be picked up again
        // every frame he stood on it.
        owner.markDestroyed();
    }
}
