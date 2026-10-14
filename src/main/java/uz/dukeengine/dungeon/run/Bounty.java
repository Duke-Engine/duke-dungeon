package uz.dukeengine.dungeon.run;

import uz.dukeengine.core.module.Death;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * What a monster the floor placed pays the hero who kills it, told to the run as it dies — its level is known here,
 * where it was placed, and gone by the time its death is heard elsewhere. A monster something summoned carries none:
 * a summoner is not a well of gold.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
final class Bounty extends Module implements DieModule {

    /** Who is paid what: the blow that killed it, and its level. */
    interface Payer {
        void pay(Death death, int level, GameObject monster);
    }

    private final int level;
    private final Payer payer;

    Bounty(GameObject monster, int level, Payer payer) {
        super(monster);
        this.level = level;
        this.payer = payer;
    }

    @Override
    public void onDie(Death death) {
        if (death != null) {
            payer.pay(death, level, getOwner());
        }
    }
}
