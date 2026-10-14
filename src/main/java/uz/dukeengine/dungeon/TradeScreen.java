package uz.dukeengine.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.client3d.Canvas;
import uz.dukeengine.client3d.CanvasInput;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.dungeon.run.Buy;
import uz.dukeengine.dungeon.run.Sell;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rpg.item.LootBag;

/**
 * The trade window, and the purse — see {@code docs/plan/2026-10-08-people-and-trade.md}. The window's alone and only
 * asking: it opens when the run says this machine's player asked a merchant to trade, lists his stock and the hero's
 * bag, and a click is an order — {@link Buy} or {@link Sell} — that the simulation checks and carries out on every
 * machine. Esc, or the hero walking away, closes it.
 */
final class TradeScreen {

    private final DungeonSettings settings;

    /** The match the count below is of, how many trades it had asked for when last looked, and the one open. */
    private Dungeon.Session counted;
    private int seen;
    private int open = -1;
    /** Where each row was drawn this frame, for a click: a ware by its place, or a bag slot. */
    private final List<Row> rows = new ArrayList<>();

    record Row(float x, float y, float width, float height, boolean ware, int index) {
        boolean holds(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    TradeScreen(DungeonSettings settings) {
        this.settings = settings;
    }

    /** The merchant whose window is open, by the id of the person, or -1. */
    int open(Dungeon.Session match) {
        if (match == null || match.game().getLogic() == null) {
            return -1;
        }
        if (match != counted) {
            counted = match;
            seen = 0;
            open = -1;
        }
        var trades = match.run().getTrades();
        for (; seen < trades.size(); seen++) {
            if (trades.get(seen).player() == match.game().getLocalPlayerIndex()) {
                open = trades.get(seen).merchant();
            }
        }
        if (open >= 0 && !near(match, open)) {
            open = -1; // he walked away
        }
        return open;
    }

    void close() {
        open = -1;
    }

    /** Whether this machine's hero stands near enough the merchant to trade, as the screen last saw them. */
    private boolean near(Dungeon.Session match, int merchant) {
        var snapshot = match.game().getSnapshot();
        uz.dukeengine.core.view.UnitView him = null;
        uz.dukeengine.core.view.UnitView hero = null;
        for (var unit : snapshot.units()) {
            if (unit.id() == merchant) {
                him = unit;
            } else if (unit.playerIndex() == match.game().getLocalPlayerIndex()
                    && settings.heroes().stream().anyMatch(each -> each.name().equals(unit.templateName()))) {
                hero = unit;
            }
        }
        if (him == null || hero == null) {
            return false;
        }
        var npc = settings.story().npc(him.templateName());
        float reach = npc == null ? 0f : npc.radius() * 2f;
        float dx = hero.x() - him.x();
        float dy = hero.y() - him.y();
        return dx * dx + dy * dy <= reach * reach;
    }

    /** The purse, at the bottom right above the bar: {@code Oltin: 120}. */
    void purse(Canvas canvas, Dungeon.Session match) {
        if (match == null || match.game().getLogic() == null) {
            return;
        }
        int gold;
        try {
            gold = match.run().goldOf(match.game().getLocalPlayerIndex());
        } catch (IllegalStateException notNumberedYet) {
            return;
        }
        var look = settings.menu();
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.02f), 12, 24));
        var words = settings.story().trade().goldWord() + ": " + gold;
        var size = canvas.measure(font, words);
        var panel = canvas.bottomPanel();
        float floor = panel == null ? canvas.height() - 8f : panel.y() - 8f;
        float x = canvas.width() - size.width() - 16f;
        float y = floor - size.lineHeight();
        canvas.drawText(font, words, x + 1f, y + 1f, 0xC0000000);
        coins(canvas, x, y, size.lineHeight());
        canvas.drawText(font, words, x, y, 0xFF000000 | look.torchColour());
    }

    /**
     * One kill's gold as it floats this frame: its words, the point of the world it rises from, how far it has risen
     * (a share of the window's height), how solid it is and how big against the GoldBurst's text size.
     */
    record GoldFloat(String text, float x, float y, float z, float rise, float alpha, float scale) {
    }

    /**
     * The gold of every kill paid to {@code player} still floating in {@code frame}: the Trade's GainWord with the
     * gold in it, from the GoldBurst's height over the corpse, risen as far as its life has gone, solid for HoldShare
     * of it and then fading, bigger from BigGold. Counted in the game's frames, so a paused game holds it.
     */
    static List<GoldFloat> floating(List<uz.dukeengine.dungeon.run.DungeonRun.Payment> payments, int player, int frame,
            DungeonSettings settings) {
        var burst = settings.goldBurst();
        float life = burst.floatSeconds() * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        var out = new ArrayList<GoldFloat>();
        for (var paid : payments) {
            float age = (frame - paid.frame()) / life;
            if (paid.player() != player || age < 0f || age >= 1f) {
                continue;
            }
            float eased = 1f - (1f - age) * (1f - age);
            float alpha = age <= burst.holdShare() ? 1f : 1f - (age - burst.holdShare()) / (1f - burst.holdShare());
            out.add(new GoldFloat(settings.story().trade().gainWord().replace("%d", Integer.toString(paid.gold())),
                    paid.x(), paid.y(), paid.z() + burst.floatHeight(), burst.floatRise() * eased, alpha,
                    paid.gold() >= burst.bigGold() ? burst.bigScale() : 1f));
        }
        return out;
    }

    /** The gold of this machine's kills floating up over the corpses: see {@link #floating}. */
    void floats(Canvas canvas, Dungeon.Session match) {
        if (match == null || match.game().getLogic() == null) {
            return;
        }
        var burst = settings.goldBurst();
        for (var one : floating(match.run().payments(), match.game().getLocalPlayerIndex(),
                match.game().getLogic().getFrame(), settings)) {
            var at = canvas.screenOf(one.x(), one.y(), one.z());
            if (at == null) {
                continue;
            }
            int px = Math.max(10, Math.round(canvas.height() * burst.textSize() * one.scale()));
            var font = Canvas.Font.of("Georgia", px);
            var size = canvas.measure(font, one.text());
            float x = at.x() - size.width() / 2f;
            float y = at.y() - size.lineHeight() - one.rise() * canvas.height();
            int alpha = Math.round(one.alpha() * 255f) << 24;
            canvas.drawText(font, one.text(), x + 1.5f, y + 1.5f, alpha | 0x000000);
            canvas.drawText(font, one.text(), x, y, alpha | burst.colour() & 0xFFFFFF);
        }
    }

    /** The coins before a purse whose words start at {@code x}, as tall as a line and a third, centred on it. */
    private void coins(Canvas canvas, float x, float y, float line) {
        var icon = settings.story().trade().goldIcon();
        if (icon == null || icon.isBlank()) {
            return;
        }
        float face = line * 1.35f;
        float top = y + (line - face) / 2f;
        canvas.drawImage(Canvas.Image.of(icon), x - face - 4f, top, x - 4f, top + face,
                0xFFFFFFFF, Canvas.Blend.ALPHA);
    }

    /** The window, over everything but the dialogue box. */
    void paint(Canvas canvas, Dungeon.Session match) {
        rows.clear();
        int merchant = open(match);
        if (merchant < 0) {
            return;
        }
        var look = settings.menu();
        var trade = settings.story().trade();
        var font = Canvas.Font.of("Georgia", Math.clamp(Math.round(canvas.height() * 0.02f), 12, 24));
        float line = canvas.measure(font, "Ag").lineHeight() * 1.5f;
        float pad = font.pixelHeight();
        float wide = Math.min(canvas.width() - 32f, 820f);
        float column = (wide - pad * 3f) / 2f;
        var wares = match.run().stockOf(merchant);
        var bag = Bag.bagOf(match);
        // Only the backpack is offered: what he wears he takes off first.
        int bagRows = bag == null ? 0 : bag.backpackSize();
        float high = pad * 3f + line * (2 + Math.max(wares.size(), bagRows)) + line;
        float left = (canvas.width() - wide) / 2f;
        float top = Math.max(8f, (canvas.height() - high) / 2.5f);
        canvas.fillRect(left, top, wide, high, 0xF0000000 | look.stoneDeepColour());
        canvas.openRect(left, top, wide, high, 2f, 0xFF000000 | look.stoneEdgeColour());
        int gold = 0xFF000000 | look.torchColour();
        int bone = 0xFF000000 | look.boneColour();
        int dim = 0x90000000 | look.boneColour();

        var name = settings.story().speaker(npcOf(match, merchant)) == null ? npcOf(match, merchant)
                : settings.story().speaker(npcOf(match, merchant)).displayName();
        canvas.drawText(font, name, left + pad, top + pad, gold);
        var purse = trade.goldWord() + ": " + match.run().goldOf(match.game().getLocalPlayerIndex());
        float purseAt = left + wide - pad - canvas.measure(font, purse).width();
        coins(canvas, purseAt, top + pad, canvas.measure(font, purse).lineHeight());
        canvas.drawText(font, purse, purseAt, top + pad, gold);

        float y = top + pad + line * 1.3f;
        canvas.drawText(font, trade.stockWord(), left + pad, y, gold);
        canvas.drawText(font, trade.bagWord(), left + pad * 2f + column, y, gold);
        y += line;
        for (int i = 0; i < wares.size(); i++) {
            var ware = wares.get(i);
            var item = item(ware.item());
            var said = (item == null ? ware.item() : item.name()) + " — " + ware.price() + " (" + ware.count() + ")";
            row(canvas, font, left + pad, y + i * line, column, line, item == null ? null : item.icon(), said,
                    ware.count() > 0 ? bone : dim, true, i);
        }
        for (int slot = 0; slot < bagRows; slot++) {
            var thing = bag.at(slot);
            if (thing == null) {
                continue;
            }
            int worth = match.run().worth(thing);
            var said = LootBag.nameOf(thing) + (worth < 0 ? "" : " — " + worth);
            row(canvas, font, left + pad * 2f + column, y + slot * line, column, line, thing.icon(), said,
                    worth < 0 ? dim : bone, false, slot);
        }
        var hint = trade.closeHint();
        canvas.drawText(font, hint, left + wide - pad - canvas.measure(font, hint).width(), top + high - pad - line * 0.6f,
                dim);
    }

    private void row(Canvas canvas, Canvas.Font font, float x, float y, float wide, float high, String icon,
            String said, int argb, boolean ware, int index) {
        float face = high * 0.8f;
        if (icon != null && !icon.isBlank()) {
            canvas.drawImage(Canvas.Image.of(icon), x, y, x + face, y + face, 0xFFFFFFFF, Canvas.Blend.ALPHA);
        }
        canvas.drawText(font, said, x + face + 6f, y + (face - canvas.measure(font, said).lineHeight()) / 2f, argb);
        rows.add(new Row(x, y, wide, high, ware, index));
    }

    /** The click, the key: a ware bought, a thing sold, Esc closing. Everything is taken while it is open. */
    boolean take(CanvasInput.Event event, Dungeon.Session match) {
        int merchant = open(match);
        if (merchant < 0) {
            return false;
        }
        switch (event) {
            case CanvasInput.Key key -> {
                if (key.down() && key.code() == DialogueScreen.ESCAPE) {
                    close();
                    return true;
                }
                return false; // his keys are still his: the bar's, the camera's
            }
            case CanvasInput.Button button -> {
                if (button.down() && button.button() == CanvasInput.Mouse.LEFT) {
                    for (var row : rows) {
                        if (row.holds(button.x(), button.y())) {
                            int player = match.game().getLocalPlayerIndex();
                            var who = new ObjectId(merchant);
                            match.game().postCommand(PartyOrders.of(row.ware() ? new Buy(player, who, row.index())
                                    : new Sell(player, who, row.index())));
                            return true;
                        }
                    }
                }
                return inside(button.x(), button.y()); // a click on the window is not an order to the world
            }
            default -> {
                return false;
            }
        }
    }

    private boolean inside(int x, int y) {
        if (rows.isEmpty()) {
            return false;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = 0f;
        float maxY = 0f;
        for (var row : rows) {
            minX = Math.min(minX, row.x());
            minY = Math.min(minY, row.y());
            maxX = Math.max(maxX, row.x() + row.width());
            maxY = Math.max(maxY, row.y() + row.height());
        }
        return x >= minX && x <= maxX && y >= minY - 80 && y <= maxY + 80;
    }

    List<Row> rows() {
        return List.copyOf(rows);
    }

    private uz.dukeengine.rpg.item.Loot item(String id) {
        return settings.loot().stream().filter(loot -> loot.id().equals(id)).findFirst().orElse(null);
    }

    private static String npcOf(Dungeon.Session match, int id) {
        for (var unit : match.game().getSnapshot().units()) {
            if (unit.id() == id) {
                return unit.templateName();
            }
        }
        return "";
    }
}
