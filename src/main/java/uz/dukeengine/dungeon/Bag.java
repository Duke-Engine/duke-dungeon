package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import uz.dukeengine.client3d.AimOutcome;
import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.rpg.BagScreen;
import uz.dukeengine.client3d.rpg.BuffScreen;
import uz.dukeengine.core.view.CommandButton;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.Stat;

/**
 * The game's side of the engine's {@link BagScreen} and {@link BuffScreen}: the words they say, what a thing gives
 * as the pointer says it, whose bag is drawn, how its orders go down the wire, and what is painted under it — the
 * floor's mission. The screens themselves are the engine's.
 */
final class Bag {

    private Bag() {
    }

    /** The bag, with the buffs under it, its things taken in hand armed by {@code duke}. */
    static BagScreen on(DungeonSettings settings, Duke3D duke) {
        return BagScreen.on(duke, settings.menu(), words(settings), item -> gives(item, settings), buffs(settings));
    }

    /** The same, arming what is taken in hand by {@code arm}, on {@code clock}: for a test, with no window. */
    static BagScreen of(DungeonSettings settings,
            BiConsumer<CommandButton, Consumer<AimOutcome>> arm, LongSupplier clock) {
        return new BagScreen(settings.menu(), words(settings), item -> gives(item, settings), buffs(settings), arm,
                clock);
    }

    /** What is on the creature he picks, over its bar, in the game's words. */
    static BuffScreen buffs(DungeonSettings settings) {
        var hud = settings.hud();
        var drops = settings.lootDrops();
        return new BuffScreen(settings.skills(), settings.effects(), settings.menu(), settings.panelLook(),
                hud.buffIcon(), new BuffScreen.Words(hud.attackWord(), drops.manaRegenWord(), hud.lifestealWord(),
                        drops.attackSpeedWord(), hud.secondsWord()));
    }

    /** The bag's words: its heading, its hints, and the order words a click on the floor is answered as. */
    static BagScreen.Words words(DungeonSettings settings) {
        var drops = settings.lootDrops();
        return new BagScreen.Words(settings.hud().itemsWord(), drops.dropHint(), drops.takeHint(), drops.useHint(),
                PartyOrders.PICK_UP, PartyOrders.TO_THE_GATE, PartyOrders.USE, drops.wornIn(), drops.wearHint(),
                drops.offHint());
    }

    /** The match now being played: this machine's hero's bag in it, its orders wrapped as a party's, its mission. */
    static void show(BagScreen bag, Dungeon.Session match, DungeonSettings settings) {
        bag.show(match.game(), () -> bagOf(match), PartyOrders::of,
                canvas -> MissionScreen.paint(canvas, match, settings));
        // And a click on a person is a talk, a rule beside the bag's own (E20).
        var story = settings.story();
        match.game().addContextOrder((selection, target) ->
                target.getPlayerIndex() == uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                        && story.npc(target.getTemplate().name()) != null ? PartyOrders.TALK : null);
        DialogueScreen.show(match);
    }

    /**
     * This machine's hero's bag in {@code match}, or null before the match has numbered its players: in the front
     * end, which this is painted over too, and for the moment the match is being built.
     */
    static LootBag bagOf(Dungeon.Session match) {
        if (match.game().getLogic() == null) {
            return null;
        }
        try {
            var progress = match.run().progressOf(match.game().getLocalPlayerIndex());
            return progress == null ? null : progress.getLoot();
        } catch (IllegalStateException notNumberedYet) {
            return null;
        }
    }

    /** What a thing gives, a line each: its figure, and its extra past the first level; nothing said for nothing. */
    static List<String> gives(Loot item, DungeonSettings settings) {
        var said = new ArrayList<String>();
        var bonus = bonusOf(item, settings);
        if (!bonus.isEmpty()) {
            said.add(bonus);
        }
        var extra = extraOf(item, settings);
        if (!extra.isEmpty()) {
            said.add(extra);
        }
        return said;
    }

    /** What a thing gives, as the pointer says it: {@code +8% Zarba}, {@code +3 Kuch} — and of a key, nothing. */
    static String bonusOf(Loot item, DungeonSettings settings) {
        var hud = settings.hud();
        return switch (item.kind()) {
            case Stat.ATTACK -> "+" + item.value() + "% " + hud.attackWord();
            case Stat.ARMOUR -> "+" + item.value() + "% " + hud.armourWord();
            case Stat.HEALTH -> "+" + item.value() + " " + hud.healthWord();
            case Stat.MANA -> "+" + item.value() + " " + hud.manaWord();
            case Stat.ATTRIBUTE -> {
                var rules = settings.attributeRules();
                int at = rules.indexOf(item.attribute());
                yield "+" + item.value() + " " + (at < 0 ? item.attribute() : rules.attributes().get(at).word());
            }
            default -> ""; // a key opens a gate, and gives him nothing
        };
    }

    /** What a thing gives beside its figure past the first level, as the pointer says it — {@code +2 Mana/s}. */
    static String extraOf(Loot item, DungeonSettings settings) {
        var drops = settings.lootDrops();
        return item.extraValue() <= 0 ? "" : switch (item.extra()) {
            case Stat.HEALTH_REGEN -> "+" + item.extraValue() + " " + drops.healthRegenWord();
            case Stat.MANA_REGEN -> "+" + item.extraValue() + " " + drops.manaRegenWord();
            case Stat.ATTACK_SPEED -> "+" + item.extraValue() + "% " + drops.attackSpeedWord();
            default -> "";
        };
    }
}
