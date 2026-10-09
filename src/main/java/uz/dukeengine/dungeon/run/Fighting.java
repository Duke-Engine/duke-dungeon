package uz.dukeengine.dungeon.run;

import java.util.List;
import uz.dukeengine.core.module.DamageListener;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * A boss as its fight ({@link BossFight}) holds it: the skills its phase lets it cast, which its brain asks, and — for
 * one whose Boss block says {@code Beaten} — Mag'lub bo'ladi, o'lmaydi: no blow takes it below its share of its most
 * health. The blow that would leaves it there, beaten. Told of each blow by the body as it lands, before any death is
 * reaped, so a killing blow never kills.
 *
 * <p>ponytail: the beaten half is a stand-in through the engine's own {@code DamageListener} seam until the skill
 * system carries it as a passive (E28 in docs/plan/2026-09-29-engine-requests.md); then it goes, and the Boss block's
 * {@code Beaten} becomes a Skill in the Monster's own block.
 */
@ModuleGroup(ModuleGroups.SCRIPT)
public final class Fighting extends Module implements DamageListener {

    private final float share;
    private boolean beaten;
    private List<String> castOnly = List.of();

    /** @param percent the share of its most health it is beaten at, 1 to 99; 0 for one that dies */
    Fighting(GameObject owner, int percent) {
        super(owner);
        this.share = percent / 100f;
    }

    @Override
    public void onDamage(DamageType type, float amount, ObjectId attacker, int frame) {
        var body = getOwner().getBody();
        float floor = body.getMaxHealth() * share;
        if (share > 0f && body.getHealth() <= floor) {
            body.setHealth(floor);
            beaten = true;
        }
    }

    /** Whether a blow has brought it to its share. */
    boolean beaten() {
        return beaten;
    }

    /** The keys of the skills of its own it may cast now, upper case; empty for all of them. */
    public List<String> castOnly() {
        return castOnly;
    }

    void castOnly(List<String> keys) {
        castOnly = keys.stream().map(key -> key.toUpperCase(java.util.Locale.ROOT)).toList();
    }
}
