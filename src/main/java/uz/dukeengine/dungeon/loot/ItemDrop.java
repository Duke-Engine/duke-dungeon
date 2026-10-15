package uz.dukeengine.dungeon.loot;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;
import uz.dukeengine.rpg.hero.LevelBonus;
import uz.dukeengine.rpg.item.GroundItem;

/**
 * What a monster leaves when it goes, made by {@link Items}: the engine's {@code LootDrop} with a generated thing in
 * place of a catalogue's. Its chance and its seed are the world's draws, so every machine of a party drops the same
 * thing off the same death; the rest — potion or gear, rarity, base, affixes — is drawn from that seed.
 *
 * <p>An ordinary monster drops at {@code LootDrops.DropPercent}, an elite ({@code ItemRules.Elites}) at its
 * {@code EliteDropPercent}, a boss always and {@code BossDrops} things. A thing's item level is the monster's level, a
 * boss's {@code BossLevel} more. See docs/plan/2026-10-15-loot.md.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
public final class ItemDrop extends Module implements DieModule {

    private final Items items;
    private final String chest;
    private final int dropPercent;
    private final int bossDropPercent;
    private final boolean boss;

    public ItemDrop(GameObject owner, Items items, String chest, int dropPercent, int bossDropPercent, boolean boss) {
        super(owner);
        this.items = items;
        this.chest = chest;
        this.dropPercent = dropPercent;
        this.bossDropPercent = bossDropPercent;
        this.boss = boss;
    }

    @Override
    public void onDie() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || chest == null || chest.isBlank()) {
            return;
        }
        var rules = items.rules();
        boolean elite = !boss && items.isElite(owner.getTemplate().name());
        int chance = boss ? bossDropPercent : elite ? rules.eliteDropPercent() : dropPercent;
        var random = world.random();
        if (random.nextInt(0, 99) >= chance) {
            return; // most deaths leave nothing, which is what makes the rest worth it
        }
        var source = boss ? Items.Source.BOSS : elite ? Items.Source.ELITE : Items.Source.NORMAL;
        int level = LevelBonus.levelOf(owner) + (boss ? rules.bossLevel() : 0);
        int count = boss ? Math.max(1, rules.bossDrops()) : 1;
        var at = owner.getPosition();
        for (int i = 0; i < count; i++) {
            long seed = random.nextInt(0, (1 << 30) - 1);
            // A boss's things lie side by side, a few steps apart, so each is its own click.
            var place = new Coord3D(at.x() + i * 6f, at.y() + (i % 2) * 4f, at.z());
            var item = items.drop(seed, level, source);
            GroundItem.lay(world, item.liesAs(chest), item, place, owner.getPlayerIndex());
        }
    }
}
