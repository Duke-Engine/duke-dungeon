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
        return BagScreen.on(duke, settings.menu(), words(settings), item -> gives(item, settings), shown(settings));
    }

    /** The same, arming what is taken in hand by {@code arm}, on {@code clock}: for a test, with no window. */
    static BagScreen of(DungeonSettings settings,
            BiConsumer<CommandButton, Consumer<AimOutcome>> arm, LongSupplier clock) {
        return new BagScreen(settings.menu(), words(settings), item -> gives(item, settings), shown(settings), arm,
                clock);
    }

    /**
     * The buffs the bag draws over the bars, kept for the target frame, which draws the same pictures of the creature
     * picked: one screen, read once a frame, for both.
     */
    private static volatile BuffScreen shown;

    private static BuffScreen shown(DungeonSettings settings) {
        shown = buffs(settings);
        return shown;
    }

    /** What is on the creature he picks, over its bar, in the game's words. */
    static BuffScreen buffs(DungeonSettings settings) {
        var hud = settings.hud();
        var drops = settings.lootDrops();
        return new BuffScreen(settings.skills(), settings.effects(), settings.menu(), settings.panelLook(),
                hud.buffIcon(), new BuffScreen.Words(hud.attackWord(), drops.manaRegenWord(), hud.lifestealWord(),
                        drops.attackSpeedWord(), hud.secondsWord(), settings.medallion().soulsWord()));
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
        souls = () -> {
            try {
                return match.game().getLogic() == null ? new int[2] : new int[] {
                    match.run().soulsOf(match.game().getLocalPlayerIndex()),
                    match.run().soulPercentOf(match.game().getLocalPlayerIndex())};
            } catch (IllegalStateException notNumberedYet) {
                return new int[2];
            }
        };
        bag.show(match.game(), () -> bagOf(match), PartyOrders::of,
                canvas -> {
                    MissionScreen.paint(canvas, match, settings);
                    targetFrame(canvas, match, settings);
                });
        // And a click on a person is a talk, a rule beside the bag's own (E20).
        var story = settings.story();
        match.game().addContextOrder((selection, target) ->
                target.getPlayerIndex() == uz.dukeengine.core.player.PlayerList.NEUTRAL_INDEX
                        && story.npc(target.getTemplate().name()) != null ? PartyOrders.TALK : null);
        DialogueScreen.show(match);
        ItemCard.watch(match.game());
    }

    /** The target frame over {@code match}: the run's pick, with what the buffs say is on it. */
    static void targetFrame(uz.dukeengine.client3d.Canvas canvas, Dungeon.Session match, DungeonSettings settings) {
        var target = match.run().getTarget();
        var buffs = shown;
        TargetFrame.paint(canvas, target, target == null || buffs == null ? List.of()
                : buffs.buffs().getOrDefault(target.id(), List.of()), settings);
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
        // A generated thing or a potion says its figure, affixes and story (ItemCard paints them in colour over it).
        var made = settings.items().lines(item);
        if (made != null) {
            return made.stream().map(uz.dukeengine.dungeon.loot.Items.Line::text).toList();
        }
        var said = new ArrayList<String>();
        var bonus = bonusOf(item, settings);
        if (!bonus.isEmpty()) {
            said.add(bonus);
        }
        var extra = extraOf(item, settings);
        if (!extra.isEmpty()) {
            said.add(extra);
        }
        var held = souls.get();
        said.addAll(medallionOf(item, settings, held[0], held[1]));
        return said;
    }

    /**
     * The souls this machine's hero has drunk and what they add to his blows, in percent, as the match being played
     * says: read by the medallion's card.
     */
    private static volatile java.util.function.Supplier<int[]> souls = () -> new int[2];

    /**
     * The medallion's card: its soul drink's name, the souls he holds ({@code Jonlar: 37}) and what they add to his
     * blows ({@code percent}, his book's); nothing for any other thing. See docs/plan/2026-10-09-medallion.md.
     */
    static List<String> medallionOf(Loot item, DungeonSettings settings, int souls, int percent) {
        var medallion = settings.medallion();
        if (medallion.item().isBlank() || !item.id().equals(medallion.item())) {
            return List.of();
        }
        var drink = item.skills().stream()
                .filter(skill -> skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.SOUL_DRINK).findFirst();
        return List.of(drink.map(skill -> skill.name()).orElse(""), medallion.soulsWord() + ": " + souls,
                "+" + percent + "% " + settings.hud().attackWord());
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
