package uz.dukeengine.dungeon.content;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Visuals;

/** Hands the client what the files say makes a hit or a death felt: see {@link HeavyBlow} and {@link BigDeath}. */
public final class Feel {

    private Feel() {
    }

    /** Every creature the game can hurt, by template name. */
    static List<String> creatures(DungeonSettings settings) {
        var names = new ArrayList<String>();
        settings.monsters().forEach(kind -> names.add(kind.name()));
        settings.heroes().forEach(hero -> names.add(hero.name()));
        return names.stream().distinct().toList();
    }

    /**
     * Heavy blows first (a block for all, then the ones for a creature, so the narrower wins), then big deaths. The
     * throttle is 0 on purpose: the same hurt moment plays the creature's pain sound, which wants every blow.
     */
    public static void apply(Visuals visuals, DungeonSettings settings) {
        var blows = new ArrayList<>(settings.heavyBlows());
        blows.sort(java.util.Comparator.comparing(blow -> !blow.template().isBlank()));
        for (var blow : blows) {
            boolean all = blow.template().isBlank();
            visuals.hurt(all ? null : blow.template(), blow.damageType(), blow.majorAt(), 0);
            for (var template : all ? creatures(settings) : List.of(blow.template())) {
                visuals.moment(blow.momentFor(template), blow.look(), 1f);
            }
        }
        for (var death : settings.bigDeaths()) {
            visuals.moment(death.moment(), death.look(), 1f);
        }
    }
}
