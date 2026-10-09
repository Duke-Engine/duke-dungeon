package uz.dukeengine.dungeon.run;

import java.util.List;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * A boss as its fight ({@link BossFight}) holds it: the skills its phase lets it cast, which its brain asks. Whether
 * it is beaten is its own book's — the engine's {@code UNYIELDING} passive and {@code SkillBook.isBeaten} (E28).
 */
@ModuleGroup(ModuleGroups.SCRIPT)
public final class Fighting extends Module {

    private List<String> castOnly = List.of();

    Fighting(GameObject owner) {
        super(owner);
    }

    /** The keys of the skills of its own it may cast now, upper case; empty for all of them. */
    public List<String> castOnly() {
        return castOnly;
    }

    void castOnly(List<String> keys) {
        castOnly = keys.stream().map(key -> key.toUpperCase(java.util.Locale.ROOT)).toList();
    }
}
