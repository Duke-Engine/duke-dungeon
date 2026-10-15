package uz.dukeengine.dungeon.content;

import uz.dukeengine.core.content.Sound;
import uz.dukeengine.core.content.AnimationSet;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.client3d.OrderMark;
import uz.dukeengine.client3d.HitNumbers;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.rpg.PanelLook;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.Stat;
import uz.dukeengine.rpg.fight.Bow;
import uz.dukeengine.rpg.skill.Skill;
import uz.dukeengine.rpg.skill.SkillBook;
import uz.dukeengine.rpg.skill.SkillEffect;
import uz.dukeengine.rpg.hero.Attribute;
import uz.dukeengine.rpg.hero.AttributeRules;
import uz.dukeengine.rpg.hero.Attributes;
import uz.dukeengine.rpg.hero.HeroAttributes;
import uz.dukeengine.rpg.hero.Levelling;
import uz.dukeengine.dungeon.map.ProceduralMap;
import uz.dukeengine.dungeon.world.Audio;
import uz.dukeengine.client3d.Camera;
import uz.dukeengine.dungeon.world.Combat;
import uz.dukeengine.client3d.Cursor;
import uz.dukeengine.client3d.EffectBudget;
import uz.dukeengine.client3d.EffectLayer;
import uz.dukeengine.dungeon.world.Fog;
import uz.dukeengine.dungeon.world.PaintedGround;
import uz.dukeengine.client3d.HitFeel;
import uz.dukeengine.dungeon.world.Hud;
import uz.dukeengine.dungeon.world.LootDrops;
import uz.dukeengine.dungeon.world.LootItem;
import uz.dukeengine.client3d.Moment;
import uz.dukeengine.dungeon.world.Progression;
import uz.dukeengine.dungeon.world.Run;
import uz.dukeengine.client3d.Skin;
import uz.dukeengine.client3d.SkillRing;
import uz.dukeengine.dungeon.world.StatBlock;
import uz.dukeengine.client3d.Sun;
import uz.dukeengine.dungeon.world.Theme;
import uz.dukeengine.dungeon.world.Tiles;
import uz.dukeengine.dungeon.world.UnitBar;
import uz.dukeengine.dungeon.world.World;

/**
 * Every tuning number that is not a unit stat: the world's own blocks in {@code data/world/} and the
 * dungeon's part of the unit, skill, effect and sound blocks — every file the game's manifest lists.
 *
 * <p>Maps are not among them: a map is a folder of its own under {@code maps/}, found rather than listed —
 * see {@code uz.dukeengine.core.map.MapPackage} — and the blocks it brings with it are read after these, by
 * {@link #of}.
 *
 * <p>Unit stats belong in the unit blocks, where the engine's template
 * loader reads them. What is left is the shape of a dungeon and the decisions its
 * creatures make — neither of which is a property of any one unit, so the engine
 * has nowhere to put them and no opinion about them. They were constants in Java;
 * now they are data, and tuning the game is editing a file.
 *
 * <p>Each block is the record its word names, read by the engine's own {@code Binder} — the same
 * reading every unit gets, so a setting is written the way a unit's line is.
 *
 * <p>Settings are read once and passed to whatever needs them, rather than reached
 * for through a global. That keeps the simulation's inputs explicit, which is what
 * a deterministic world needs.
 */
public final class DungeonSettings {

    /** The world as the engine reads it: its name, and how far apart its storeys stand. */
    private World world = World.DEFAULTS;

    /** The map the endless descent is drawn from. */
    private ProceduralMap map = ProceduralMap.DEFAULTS;

    /** The Kind of a key: no figure's, and the one item the floor lays rather than drops. */
    private static final String KEY = "KEY";
    /** The Kind of a thing a quest asks for: no figure's, and never dropped. */
    private static final String QUEST = "QUEST";
    /** The words an item's Kind may be: those a hero's figures read, and a key's. */
    private static final java.util.Set<String> KINDS =
            java.util.Set.of(Stat.ATTACK, Stat.HEALTH, Stat.ARMOUR, Stat.MANA, Stat.ATTRIBUTE, KEY, QUEST);
    /** The skills whose StunFrames something reads. */
    private static final java.util.Set<SkillEffect> STUNNERS =
            java.util.EnumSet.of(SkillEffect.SKILLSHOT, SkillEffect.WAVE, SkillEffect.SKEWER, SkillEffect.PULL,
                    SkillEffect.STAGGER);
    /** And its Extra, beside none. */
    private static final java.util.Set<String> EXTRAS =
            java.util.Set.of(Stat.HEALTH_REGEN, Stat.MANA_REGEN, Stat.ATTACK_SPEED);

    // ---- the world's own blocks, each the record its word names ----

    private Combat combat = Combat.DEFAULTS;
    private Run run = Run.DEFAULTS;
    private Progression progression = Progression.DEFAULTS;
    private LootDrops lootDrops = LootDrops.DEFAULTS;
    private Camera camera = Camera.DEFAULTS;
    private Hud hud = Hud.DEFAULTS;
    private uz.dukeengine.dungeon.world.Party party = uz.dukeengine.dungeon.world.Party.DEFAULTS;
    private uz.dukeengine.dungeon.world.SaveMenu saveMenu = uz.dukeengine.dungeon.world.SaveMenu.DEFAULTS;
    private uz.dukeengine.dungeon.world.MainMenu mainMenu = uz.dukeengine.dungeon.world.MainMenu.DEFAULTS;
    private uz.dukeengine.dungeon.world.Splash splash = uz.dukeengine.dungeon.world.Splash.DEFAULTS;
    private uz.dukeengine.dungeon.world.Screens screens = uz.dukeengine.dungeon.world.Screens.DEFAULTS;
    private uz.dukeengine.dungeon.world.Controls controls = uz.dukeengine.dungeon.world.Controls.DEFAULTS;
    /** The scheme played: the file's until the launch says the player's own (see useScheme). */
    private uz.dukeengine.dungeon.world.Controls.Scheme scheme;
    private uz.dukeengine.dungeon.world.Travel travel = uz.dukeengine.dungeon.world.Travel.DEFAULTS;
    private UnitBar unitBar = UnitBar.DEFAULTS;
    private StatBlock statBlock = StatBlock.DEFAULTS;
    private SkillRing skillRing = SkillRing.DEFAULTS;
    private OrderMark orderMark = OrderMark.DEFAULTS;
    private PanelLook panelLook = PanelLook.DEFAULTS;
    private HitNumbers hitNumbers = HitNumbers.DEFAULTS;
    private MenuStyle menu = MenuStyle.DEFAULTS;
    private Sun sun = Sun.DEFAULTS;
    private Fog fog = Fog.DEFAULTS;
    private PaintedGround paintedGround = PaintedGround.DEFAULTS;
    private Tiles tiles = Tiles.DEFAULTS;
    private EffectBudget effectBudget = EffectBudget.DEFAULTS;
    private HitFeel hitFeel = HitFeel.DEFAULTS;
    private Audio audio = Audio.DEFAULTS;
    private uz.dukeengine.dungeon.story.Dialogue dialogue = uz.dukeengine.dungeon.story.Dialogue.DEFAULTS;
    private final List<uz.dukeengine.dungeon.story.Speaker> speakers = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Conversation> conversations = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Npc> npcs = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Quest> quests = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Trigger> triggers = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Cutscene> cutscenes = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Merchant> merchants = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Boss> bosses = new java.util.ArrayList<>();
    private uz.dukeengine.dungeon.story.Trade trade = uz.dukeengine.dungeon.story.Trade.DEFAULTS;
    private uz.dukeengine.dungeon.story.GoldBurst goldBurst = uz.dukeengine.dungeon.story.GoldBurst.DEFAULTS;
    private final List<HeavyBlow> heavyBlows = new java.util.ArrayList<>();
    private final List<BigDeath> bigDeaths = new java.util.ArrayList<>();
    private final List<CritBlow> critBlows = new java.util.ArrayList<>();
    private uz.dukeengine.client3d.HitStop hitStop = new uz.dukeengine.client3d.HitStop(0f, 1f, true, false);
    private uz.dukeengine.client3d.Flinch flinch = uz.dukeengine.client3d.Flinch.DEFAULTS;
    private final List<uz.dukeengine.core.content.EffectList> effectLists = new java.util.ArrayList<>();
    private uz.dukeengine.dungeon.story.Medallion medallion = uz.dukeengine.dungeon.story.Medallion.DEFAULTS;
    private uz.dukeengine.dungeon.food.Food food = uz.dukeengine.dungeon.food.Food.DEFAULTS;
    private uz.dukeengine.dungeon.mount.Mount mount = uz.dukeengine.dungeon.mount.Mount.DEFAULTS;
    private final List<uz.dukeengine.dungeon.story.Campaign> campaigns = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Place> places = new java.util.ArrayList<>();
    private uz.dukeengine.dungeon.story.Credits credits = uz.dukeengine.dungeon.story.Credits.DEFAULTS;
    private final List<uz.dukeengine.dungeon.story.Title> titles = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Battle> battles = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Shockwave> shockwaves = new java.util.ArrayList<>();
    private final List<uz.dukeengine.dungeon.story.Gate> gates = new java.util.ArrayList<>();
    private final List<uz.dukeengine.core.content.Laser> lasers = new java.util.ArrayList<>();

    /** Every attribute, in file order — the order a hero's are held in and the panel draws them. */
    private final List<Attribute> attributes = new java.util.ArrayList<>();
    private final List<Moment> moments = new java.util.ArrayList<>();
    private final List<Cursor> cursors = new java.util.ArrayList<>();
    private final List<Skin> skins = new java.util.ArrayList<>();
    private final List<Theme> themes = new java.util.ArrayList<>();
    /** How things look outside any theme — in every theme that does not dress them. */
    private final List<Theme.ThemeMonster> looks = new java.util.ArrayList<>();
    /** What units move by, each linked by name from the units that use it. */
    private final List<AnimationSet> animationSets = new java.util.ArrayList<>();

    public Combat combat() {
        return combat;
    }

    public Run run() {
        return run;
    }

    public Progression progression() {
        return progression;
    }

    public LootDrops lootDrops() {
        return lootDrops;
    }

    public Camera camera() {
        return camera;
    }

    /** Going down together: how many, where the host listens, and the words on the way in. */
    public uz.dukeengine.dungeon.world.Party party() {
        return party;
    }

    /** The words of the saved games on the way in: continue, load, a new game. */
    public uz.dukeengine.dungeon.world.SaveMenu saveMenu() {
        return saveMenu;
    }

    /** The front menu's rows, the client's menu words, and the live scene behind them. */
    public uz.dukeengine.dungeon.world.MainMenu mainMenu() {
        return mainMenu;
    }

    /** What the window shows before the front menu and while a map loads. */
    public uz.dukeengine.dungeon.world.Splash splash() {
        return splash;
    }

    /** The character sheet, the quest log and the world map: their keys and words. */
    /** Fast travel between a map's watchtowers: see {@link uz.dukeengine.dungeon.world.Travel}. */
    public uz.dukeengine.dungeon.world.Travel travel() {
        return travel;
    }

    public uz.dukeengine.dungeon.world.Screens screens() {
        return screens;
    }

    public uz.dukeengine.dungeon.world.Controls controls() {
        return controls;
    }

    /** The scheme the hero is played by: the player's own once {@link #useScheme} says it, else the file's. */
    public uz.dukeengine.dungeon.world.Controls.Scheme scheme() {
        return scheme != null ? scheme : controls.fileScheme();
    }

    /** Play by {@code chosen} -- the launch's, from the player's own choice (Controls.remembered). */
    public void useScheme(uz.dukeengine.dungeon.world.Controls.Scheme chosen) {
        this.scheme = chosen;
    }

    /** The letter on the keyboard that presses the skill on {@code slot}, in the scheme played. */
    public char letterOf(char slot) {
        return controls.letterOf(scheme(), slot);
    }

    public Hud hud() {
        return hud;
    }

    public UnitBar unitBar() {
        return unitBar;
    }

    public StatBlock statBlock() {
        return statBlock;
    }

    public SkillRing skillRing() {
        return skillRing;
    }

    public OrderMark orderMark() {
        return orderMark;
    }

    public PanelLook panelLook() {
        return panelLook;
    }

    public HitNumbers hitNumbers() {
        return hitNumbers;
    }

    public MenuStyle menu() {
        return menu;
    }

    public Sun sun() {
        return sun;
    }

    /** How a painted map's ground is drawn — see {@link PaintedGround}. */
    public PaintedGround paintedGround() {
        return paintedGround;
    }

    public Fog fog() {
        return fog;
    }

    /** The kit to draw the floor with when no theme says otherwise; {@code floor()} is null for none. */
    public Tiles tiles() {
        return tiles;
    }

    public EffectBudget effectBudget() {
        return effectBudget;
    }

    public HitFeel hitFeel() {
        return hitFeel;
    }

    public Audio audio() {
        return audio;
    }

    /** People, their talk and the quests: see {@code docs/plan/2026-10-08-dialogue-and-quests.md}. */
    public uz.dukeengine.dungeon.story.Story story() {
        // Made once and kept: the run asks it every frame (its people, bosses, quest marks), and the settings do not
        // change once read. Unguarded on purpose -- two threads at worst each make an equal one.
        var made = story;
        if (made == null) {
            made = new uz.dukeengine.dungeon.story.Story(dialogue, speakers, conversations, npcs, quests, triggers,
                    cutscenes, trade, merchants, bosses);
            story = made;
        }
        return made;
    }

    private uz.dukeengine.dungeon.story.Story story;

    /** The blows that are felt, and what they play: see {@link HeavyBlow}. */
    public List<HeavyBlow> heavyBlows() {
        return List.copyOf(heavyBlows);
    }

    /** The catch in the motion of a heavy blow; none (0 seconds) unless the files say. */
    public uz.dukeengine.client3d.HitStop hitStop() {
        return hitStop;
    }

    /** When a hurt creature flinches; at every blow it is free for unless the files say. */
    public uz.dukeengine.client3d.Flinch flinch() {
        return flinch;
    }

    /** What a critical blow adds, see {@link CritBlow}. */
    public List<CritBlow> critBlows() {
        return List.copyOf(critBlows);
    }

    /** The deaths that are felt. */
    public List<BigDeath> bigDeaths() {
        return List.copyOf(bigDeaths);
    }

    /** The game's effect lists: several things at once by one name, a camera knock among them. */
    public List<uz.dukeengine.core.content.EffectList> effectLists() {
        return List.copyOf(effectLists);
    }

    /** What a kill that pays looks like: the coins out of the corpse and the gold taken floating over it. */
    public uz.dukeengine.dungeon.story.GoldBurst goldBurst() {
        return goldBurst;
    }

    /** Riding a horse on the open world: see {@code docs/plan/2026-10-15-mounts.md}. */
    public uz.dukeengine.dungeon.mount.Mount mount() {
        return mount;
    }

    /** Food and fire: see {@code docs/plan/2026-10-15-food-and-fire.md}. */
    public uz.dukeengine.dungeon.food.Food food() {
        return food;
    }

    /** The queen's medallion and its passive: see {@code docs/plan/2026-10-09-medallion.md}. */
    public uz.dukeengine.dungeon.story.Medallion medallion() {
        return medallion;
    }

    /** The credits a story's CREDITS rolls. */
    public uz.dukeengine.dungeon.story.Credits credits() {
        return credits;
    }

    /** The title card of that name, or null. */
    public uz.dukeengine.dungeon.story.Title title(String name) {
        return titles.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** The campaign's first act, which a new game opens on — see {@code docs/plan/2026-10-15-act1.md}. */
    public uz.dukeengine.dungeon.story.Campaign campaign() {
        return campaigns.isEmpty() ? uz.dukeengine.dungeon.story.Campaign.DEFAULTS : campaigns.getFirst();
    }

    /** Every act of the campaign, in file order. */
    public List<uz.dukeengine.dungeon.story.Campaign> campaigns() {
        return List.copyOf(campaigns);
    }

    /** The act of that name ({@code act2}), or null. */
    public uz.dukeengine.dungeon.story.Campaign campaign(String name) {
        return campaigns.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** A campaign descent of that name, of any act, or null for a map. */
    public uz.dukeengine.dungeon.story.Campaign.Descent descent(String name) {
        return campaigns.stream().map(each -> each.descent(name)).filter(java.util.Objects::nonNull).findFirst()
                .orElse(null);
    }

    /** The Battle block of that name, or null. */
    public uz.dukeengine.dungeon.story.Battle battle(String name) {
        return battles.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** The Shockwave block of that name, or null. */
    public uz.dukeengine.dungeon.story.Shockwave shockwave(String name) {
        return shockwaves.stream().filter(each -> each.name().equals(name)).findFirst().orElse(null);
    }

    /** Every Gate block, in file order: the first that holds is the one kept. */
    public List<uz.dukeengine.dungeon.story.Gate> gates() {
        return List.copyOf(gates);
    }

    /** How the simulation's beams are drawn (the engine's Laser looks): the ritual's, the pillar of light. */
    public List<uz.dukeengine.core.content.Laser> lasers() {
        return List.copyOf(lasers);
    }

    /** The joint of any act that took the place of the retired {@code map}, or null. */
    public uz.dukeengine.dungeon.story.Campaign.Joint replacing(String map) {
        for (var campaign : campaigns) {
            var joint = map == null ? null : campaign.replacing(map);
            if (joint != null) {
                return joint;
            }
        }
        return null;
    }

    /** Whether {@code map} is a joint's map or a descent of any act. */
    public boolean inCampaign(String map) {
        return campaigns.stream().anyMatch(each -> each.holds(map));
    }

    /** The places the story adds to {@code map}, as its zones. */
    public List<uz.dukeengine.dungeon.map.StaticMap.Zone> placesOn(String map) {
        return places.stream().filter(each -> each.map().equals(map))
                .map(each -> uz.dukeengine.dungeon.map.StaticMap.Zone.of(each.zone())).toList();
    }

    /** Every moment the files give a look, in the order they give them. */
    public List<Moment> moments() {
        return List.copyOf(moments);
    }

    /** Every pointer the files describe. */
    public List<Cursor> cursors() {
        return List.copyOf(cursors);
    }

    /** Every painted edge the files describe. */
    public List<Skin> skins() {
        return List.copyOf(skins);
    }

    // ---- monsters and depth ----

    /** In file order, which is the order a seed picks through them. */
    private final java.util.List<MonsterKind> monsters = new java.util.ArrayList<>();

    /** Every hero's skills, in file order — the order a HUD lists them in. */
    private final java.util.List<Skill> skills = new java.util.ArrayList<>();

    /** Everything that can be found on a floor, in file order. */
    private final java.util.List<Loot> loot = new java.util.ArrayList<>();

    /** What is generated — see docs/plan/2026-10-15-loot.md — and the one Items made of it, once asked. */
    private final java.util.List<uz.dukeengine.dungeon.loot.ItemBase> itemBases = new java.util.ArrayList<>();
    private final java.util.List<uz.dukeengine.dungeon.loot.Affix> affixes = new java.util.ArrayList<>();
    private final java.util.List<uz.dukeengine.dungeon.loot.Unique> uniques = new java.util.ArrayList<>();
    private final java.util.List<uz.dukeengine.dungeon.loot.Potion> potions = new java.util.ArrayList<>();
    private uz.dukeengine.dungeon.loot.ItemRules itemRules = uz.dukeengine.dungeon.loot.ItemRules.DEFAULTS;
    private volatile uz.dukeengine.dungeon.loot.Items items;

    /**
     * The kind placed in the furthest room when the file names no others. Named,
     * not flagged, so it is findable.
     */
    public static final String BOSS = "Boss";

    /**
     * Which kind waits in the furthest room at this depth.
     *
     * <p>Clamped rather than wrapped, because past the last boss there is no floor
     * to be on: {@link #finalDepth()} is where the descent stops.
     */
    public String bossKindAt(int depth) {
        return map.descent().bosses().isEmpty() ? BOSS
                : map.descent().bosses().get(Math.clamp(depth, 1, map.descent().bosses().size()) - 1);
    }

    /** One kind the boss is guarded by, and how many of it. */
    public record BossGuard(String kind, int count) {
    }

    /**
     * Who stands with the boss, on every floor: each guard the file names, in the order it names them. MinDepth is
     * for the rooms the draw fills, and keeps no guard away.
     */
    public java.util.List<BossGuard> bossGuards() {
        var here = new java.util.ArrayList<BossGuard>();
        for (var guard : map.descent().bossGuards().entrySet()) {
            here.add(new BossGuard(guard.getKey(), guard.getValue()));
        }
        return here;
    }

    /**
     * How many cells out from the boss the guard on the ring round it stands: any past the fourth in a keep, and every
     * guard on a floor with no keep; see {@code DungeonGenerator}.
     */
    public int bossGuardRing() {
        return map.descent().bossGuardRing();
    }

    /**
     * The depth the last boss stands on, or {@code 0} for a descent with no
     * bottom.
     *
     * <p>Derived from the list rather than given a number of its own, so there is
     * no second figure to keep in step with it: the bosses <em>are</em> the floors.
     */
    public int finalDepth() {
        return map.descent().bosses().size();
    }

    private DungeonSettings() {
    }

    /** The settings shipped with the game. */
    public static DungeonSettings load() {
        return parse(Content.data());
    }

    /**
     * The settings the game plays a map by: its own, and then whatever blocks the map brings with it.
     *
     * <p>A map is a folder and may keep blocks of its own in it -- a monster this floor alone has, a theme it
     * is laid in -- and they are read after the game's, so one that shares a name with the game's is this
     * map's version of it while this map is played. Warcraft III carries the same thing inside its maps; here
     * it is simply another file in the folder, which is a file an editor can open.
     *
     * <p>Null is the game on its own, which is what the endless descent is played by.
     */
    public static DungeonSettings of(uz.dukeengine.core.map.MapPackage map) {
        return map == null || map.extras().isEmpty() ? load() : parse(Content.data() + "\n" + map.extrasText());
    }

    /**
     * Settings from the text of {@code .duke} files: a test's own blocks, a monster, hero,
     * projectile, prop, effect or sound each overriding the shipped one of its name — see
     * {@link #fillInMissingMonsters} — and leaving every other alone. A block the world has one
     * of that the text does not write is as a block that writes nothing would be.
     */
    public static DungeonSettings parse(String data) {
        var settings = new DungeonSettings();
        settings.read(Content.records(data, "data"));
        if (!readingShippedFile) {
            settings.fillInMissingMonsters();
            settings.fillInMissingSkills();
            settings.fillInMissingLoot();
            settings.fillInMissingAttributes();
            settings.fillInMissingAnimationSets();
        }
        settings.validate();
        return settings;
    }

    /**
     * Everything the data files hold besides templates' engine parts, each by what it is. A unit's
     * skills are its own, written inside it, and its framing is its own face.
     */
    private void read(List<Record> records) {
        story = null; // a block read after it was asked for makes another
        var once = new java.util.HashSet<Class<?>>();
        for (var record : records) {
            switch (record) {
                case Monster monster -> {
                    monsters.add(monster.kind());
                    own(monster.name(), monster.portrait(), monster.skills());
                    requireBook("Monster " + monster.name(), monster.modules(), monster.skills());
                    requireOneSkillPerKey("Monster " + monster.name(), monster.skills());
                    requirePool(monster);
                }
                case Hero hero -> {
                    heroes.add(hero);
                    own(hero.name(), hero.portrait(), hero.skills());
                    requireBook("Hero " + hero.name(), hero.modules(), hero.skills());
                    requireOneSkillPerKey("Hero " + hero.name(), hero.skills());
                }
                case Projectile projectile -> projectiles.add(projectile);
                case Prop prop -> props.add(prop);
                case Effect effect -> {
                    // The game's own with the Name of one of the kit's, read after it, is drawn instead of it.
                    effects.removeIf(earlier -> earlier.name().equals(effect.name()));
                    effects.add(effect);
                }
                case Sound sound -> sounds.add(sound);
                case Attribute attribute -> attributes.add(attribute);
                case LootItem item -> {
                    loot.add(item.loot());
                    // What it lends its wearer (the medallion's soul drink) is the item's, not a unit's: no key binds it.
                    item.skills().forEach(skill -> require(skill.effect() != null && skill.effect().isPassive(),
                            "LootItem " + item.name() + " lends a " + skill.effect() + ": a worn thing lends passives only"));
                }
                // What is generated: a later block of a name is the one rolled (a map's or a test's own).
                case uz.dukeengine.dungeon.loot.ItemBase base -> {
                    itemBases.removeIf(earlier -> earlier.name().equals(base.name()));
                    itemBases.add(base);
                }
                case uz.dukeengine.dungeon.loot.Affix affix -> {
                    affixes.removeIf(earlier -> earlier.name().equals(affix.name()));
                    affixes.add(affix);
                }
                case uz.dukeengine.dungeon.loot.Unique unique -> {
                    uniques.removeIf(earlier -> earlier.name().equals(unique.name()));
                    uniques.add(unique);
                }
                case uz.dukeengine.dungeon.loot.Potion potion -> {
                    potions.removeIf(earlier -> earlier.name().equals(potion.name()));
                    potions.add(potion);
                }
                case uz.dukeengine.dungeon.loot.ItemRules block -> itemRules = once(block, once);
                case Moment moment -> moments.add(moment);
                case Cursor cursor -> cursors.add(cursor);
                case Skin skin -> skins.add(skin);
                case Theme theme -> themes.add(theme);
                case Theme.ThemeMonster look -> looks.add(look);
                case AnimationSet set -> animationSets.add(set);
                case HeavyShot shot -> heavyShot = once(shot, once);
                case Combat block -> combat = once(block, once);
                case Run block -> run = once(block, once);
                case Progression block -> progression = once(block, once);
                case LootDrops block -> lootDrops = once(block, once);
                case Camera block -> camera = once(block, once);
                case Hud block -> hud = once(block, once);
                case uz.dukeengine.dungeon.world.Party block -> party = once(block, once);
                case uz.dukeengine.dungeon.story.Dialogue block -> dialogue = once(block, once);
                case uz.dukeengine.dungeon.story.Speaker speaker -> speakers.add(speaker);
                case uz.dukeengine.dungeon.story.Conversation conversation -> conversations.add(conversation);
                case uz.dukeengine.dungeon.story.Npc npc -> npcs.add(npc);
                case uz.dukeengine.dungeon.story.Quest quest -> quests.add(quest);
                case uz.dukeengine.dungeon.story.Trigger trigger -> triggers.add(trigger);
                case uz.dukeengine.dungeon.story.Cutscene cutscene -> cutscenes.add(cutscene);
                case uz.dukeengine.dungeon.story.Merchant merchant -> merchants.add(merchant);
                case uz.dukeengine.dungeon.story.Boss boss -> {
                    // A later Boss block of a name is the one fought: a map's or a test's own over the shipped.
                    bosses.removeIf(earlier -> earlier.name().equals(boss.name()));
                    bosses.add(boss);
                }
                case uz.dukeengine.dungeon.story.Trade block -> trade = once(block, once);
                case uz.dukeengine.dungeon.story.GoldBurst block -> goldBurst = once(block, once);
                case HeavyBlow blow -> heavyBlows.add(blow);
                case BigDeath death -> bigDeaths.add(death);
                case CritBlow crit -> critBlows.add(crit);
                case uz.dukeengine.client3d.HitStop block -> hitStop = once(block, once);
                case uz.dukeengine.client3d.Flinch block -> flinch = once(block, once);
                case uz.dukeengine.core.content.EffectList list -> effectLists.add(list);
                case uz.dukeengine.dungeon.story.Medallion block -> medallion = once(block, once);
                case uz.dukeengine.dungeon.food.Food block -> food = once(block, once);
                case uz.dukeengine.dungeon.mount.Mount block -> mount = once(block, once);
                case uz.dukeengine.dungeon.story.Campaign block -> campaigns.add(block);
                case uz.dukeengine.dungeon.story.Place place -> places.add(place);
                case uz.dukeengine.dungeon.story.Credits block -> credits = once(block, once);
                case uz.dukeengine.dungeon.story.Title title -> titles.add(title);
                case uz.dukeengine.dungeon.story.Battle battle -> battles.add(battle);
                case uz.dukeengine.dungeon.story.Shockwave wave -> shockwaves.add(wave);
                case uz.dukeengine.dungeon.story.Gate gate -> gates.add(gate);
                case uz.dukeengine.core.content.Laser laser -> lasers.add(laser);
                case uz.dukeengine.dungeon.world.SaveMenu block -> saveMenu = once(block, once);
                case uz.dukeengine.dungeon.world.MainMenu block -> mainMenu = once(block, once);
                case uz.dukeengine.dungeon.world.Splash block -> splash = once(block, once);
                case uz.dukeengine.dungeon.world.Screens block -> screens = once(block, once);
                case uz.dukeengine.dungeon.world.Controls block -> controls = once(block, once);
                case uz.dukeengine.dungeon.world.Travel block -> travel = once(block, once);
                case UnitBar block -> unitBar = once(block, once);
                case StatBlock block -> statBlock = once(block, once);
                case SkillRing block -> skillRing = once(block, once);
                case OrderMark block -> orderMark = once(block, once);
                case PanelLook block -> panelLook = once(block, once);
                case HitNumbers block -> hitNumbers = once(block, once);
                case MenuStyle block -> menu = once(block, once);
                case Sun block -> sun = once(block, once);
                case Fog block -> fog = once(block, once);
                case PaintedGround block -> paintedGround = once(block, once);
                case Tiles block -> tiles = once(block, once);
                case EffectBudget block -> effectBudget = once(block, once);
                case HitFeel block -> hitFeel = once(block, once);
                case Audio block -> audio = once(block, once);
                case World block -> world = once(block, once);
                case ProceduralMap block -> map = once(block, once);
                default -> {
                    // an Object, which is a world's to build and has nothing of the settings'
                }
            }
        }
    }

    /** A block the world has one of: a second would quietly replace the first, so it is refused. */
    private static <T extends Record> T once(T block, java.util.Set<Class<?>> seen) {
        if (!seen.add(block.getClass())) {
            throw new IllegalArgumentException("'" + block.getClass().getSimpleName()
                    + "' is written twice, and the world has one of it");
        }
        return block;
    }

    private void own(String unit, PortraitArt portrait, List<Skill> itsSkills) {
        if (portrait != null) {
            portraits.add(portrait.named(unit));
        }
        for (var skill : itsSkills) {
            skills.add(skill.ownedBy(unit));
        }
    }

    /**
     * A unit whose block lists what it is built from and gives it skills has to list the {@code SkillBook} that
     * carries them: nothing else does, so without one a boss's lifesteal, a mage's fireball and a hero's four keys
     * would be words in a file, and nothing would say so.
     *
     * <p>Asked where the record is read, since {@link #validate()} keeps a monster's kind and not its modules. And a
     * block that lists no {@code Modules} at all is let be: it is a re-tuning of a unit built elsewhere -- a map's own
     * blocks, a test's -- and says nothing of a book that unit already has.
     */
    private static void requireBook(String who, List<ModuleData> modules, List<Skill> itsSkills) {
        require(itsSkills.isEmpty() || modules.isEmpty()
                        || modules.stream().anyMatch(SkillBook.Data.class::isInstance),
                who + " has Skills and no SkillBook among its Modules: nothing would carry them");
    }

    /**
     * A unit's skills are found by their key: the book casts, recharges and charges "the skill on Q", and a monster's
     * brain asks it the same. So a second skill on a key the unit already has would be found by nobody -- a skill
     * written in the file that nothing casts, and nothing would say so. A key is the upper case of what is written, as
     * {@link Skill#ownedBy} makes it.
     *
     * <p>Asked where the record is read, of the skills its own block writes, as a book and a pool are.
     */
    private static void requireOneSkillPerKey(String who, List<Skill> itsSkills) {
        var keys = new java.util.HashSet<Character>();
        for (var skill : itsSkills) {
            char key = Character.toUpperCase(skill.key());
            require(keys.add(key), who + " has two skills on the key " + key
                    + ": a skill is found by its key, so the second would never be cast");
        }
    }

    /**
     * A monster whose skills cost mana names the pool it pays from: without one it casts free, and the cost would be a
     * number nothing read. Asked of the skills its own block writes, where the record is read, as a book is: a block
     * that re-tunes a monster and writes no skills says nothing of what they cost.
     */
    private static void requirePool(Monster monster) {
        require(monster.maxMana() > 0 || monster.skills().stream().allMatch(skill -> skill.manaAt(1) == 0),
                "Monster " + monster.name() + " has a skill that costs mana and names no pool to pay it from:"
                        + " give it a MaxMana");
    }

    /**
     * Keep the monsters this file did not mention, in the order the shipped file
     * lists them.
     *
     * <p>A monster block overrides the kind of that name and leaves the rest alone,
     * which is how every other setting in this file already behaves: naming the map
     * width does not delete the room count. The alternative — a declared list
     * replacing the whole roster — reads the same in the file and fails a long way
     * from the edit, when a creature definition asks for a behaviour nobody
     * registered because its kind quietly stopped existing.
     *
     * <p>Order is the shipped order, since that is the order a seed draws through:
     * overriding a kind must not silently change which monster a seed picks.
     */
    private void fillInMissingMonsters() {
        var declared = new java.util.ArrayList<>(monsters);
        monsters.clear();
        for (var shipped : shippedMonsters()) {
            var override = declared.stream()
                    .filter(kind -> kind.name().equals(shipped.name()))
                    .findFirst();
            monsters.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        monsters.addAll(declared); // kinds this file invented
    }

    /**
     * The same rule for skills, keyed by hero <em>and</em> key rather than by
     * name: naming one hero's Q re-tunes that one skill and leaves the other
     * three, and every other hero, alone.
     */
    private void fillInMissingSkills() {
        var declared = new java.util.ArrayList<>(skills);
        skills.clear();
        for (var shipped : shippedSkills()) {
            var override = declared.stream()
                    .filter(skill -> skill.heroTemplate().equals(shipped.heroTemplate())
                            && skill.key() == shipped.key())
                    .findFirst();
            skills.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        skills.addAll(declared); // skills — and heroes — this file invented
    }

    /** The same rule again, keyed by the item's id. */
    private void fillInMissingLoot() {
        var declared = new java.util.ArrayList<>(loot);
        loot.clear();
        for (var shipped : shippedLoot()) {
            var override = declared.stream()
                    .filter(item -> item.id().equals(shipped.id()))
                    .findFirst();
            loot.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        loot.addAll(declared); // items this file invented
    }

    /**
     * The same rule again, keyed by the attribute's name.
     *
     * <p>Order matters more here than anywhere: it is the order a hero's attributes are
     * held in and the order the panel draws them, so an attribute a file re-tunes keeps
     * its shipped place.
     */
    private void fillInMissingAttributes() {
        var declared = new java.util.ArrayList<>(attributes);
        attributes.clear();
        for (var shipped : shippedAttributes()) {
            var override = declared.stream()
                    .filter(attribute -> attribute.name().equals(shipped.name()))
                    .findFirst();
            attributes.add(override.orElse(shipped));
            override.ifPresent(declared::remove);
        }
        attributes.addAll(declared); // attributes this file invented
    }

    private static final List<MonsterKind> SHIPPED_MONSTERS = new java.util.ArrayList<>();
    private static final List<Skill> SHIPPED_SKILLS = new java.util.ArrayList<>();
    private static final List<Loot> SHIPPED_LOOT = new java.util.ArrayList<>();
    private static final List<Attribute> SHIPPED_ATTRIBUTES = new java.util.ArrayList<>();
    private static final List<AnimationSet> SHIPPED_ANIMATION_SETS = new java.util.ArrayList<>();
    private static boolean readingShippedFile;

    /**
     * The monster list from the shipped file, read once and kept.
     *
     * <p>Read by parsing that file the ordinary way — a second, partial parser
     * would be a second thing to keep in step with the first. The flag is what
     * stops that parse from asking itself the same question forever.
     */
    private static List<MonsterKind> shippedMonsters() {
        readShippedFile();
        return SHIPPED_MONSTERS;
    }

    private static List<Skill> shippedSkills() {
        readShippedFile();
        return SHIPPED_SKILLS;
    }

    private static List<Loot> shippedLoot() {
        readShippedFile();
        return SHIPPED_LOOT;
    }

    private static List<Attribute> shippedAttributes() {
        readShippedFile();
        return SHIPPED_ATTRIBUTES;
    }

    /**
     * The same rule again, keyed by the set's name, so a unit a file re-tunes still moves as it
     * did — its block links its animations, it does not carry them.
     */
    private void fillInMissingAnimationSets() {
        readShippedFile();
        for (var shipped : SHIPPED_ANIMATION_SETS) {
            if (animationSets.stream().noneMatch(set -> set.name().equals(shipped.name()))) {
                animationSets.add(shipped);
            }
        }
    }

    private static void readShippedFile() {
        if (!SHIPPED_MONSTERS.isEmpty() || readingShippedFile) {
            return;
        }
        readingShippedFile = true;
        try {
            var shipped = parse(Content.data());
            SHIPPED_MONSTERS.addAll(shipped.monsters);
            SHIPPED_SKILLS.addAll(shipped.skills);
            SHIPPED_LOOT.addAll(shipped.loot);
            SHIPPED_ATTRIBUTES.addAll(shipped.attributes);
            SHIPPED_ANIMATION_SETS.addAll(shipped.animationSets);
        } finally {
            readingShippedFile = false;
        }
    }

    /**
     * Catch a file that would produce a broken dungeon at load time, where the
     * message can name the field, rather than as a strange failure much later.
     */
    /** What is generated, each name pointing at something and each figure one the hero reads. */
    private void validateItems() {
        var figures = java.util.Set.of(Stat.ATTACK, Stat.ARMOUR, Stat.HEALTH, Stat.MANA, Stat.ATTRIBUTE);
        var slots = lootDrops.wornSlots();
        var rules = attributeRules();
        for (var base : itemBases) {
            var named = "ItemBase " + base.name();
            require(!base.name().isBlank() && !base.name().contains("-") && sayable(base.displayName()),
                    named + ": a name with no '-' and a DisplayName without ',' or '|'");
            require(figures.contains(base.kind()), named + " has Kind = " + base.kind() + ", and no figure is called that");
            require(slots.isEmpty() || slots.contains(base.slot()), named + " has Slot = " + base.slot()
                    + ", and LootDrops wears no slot called that");
            require(base.value().size() == 2 && base.value().get(0) <= base.value().get(1),
                    named + ": Value is [from, to]");
            require(!base.kind().equals(Stat.ATTRIBUTE) || !base.attributes().isEmpty(),
                    named + " gives an attribute: say which in Attributes");
            base.attributes().forEach(attribute -> require(rules.indexOf(attribute) >= 0,
                    named + " gives " + attribute + ", and no Attribute is called that"));
        }
        // Any figure the hero reads (E43, E44), an attribute by its name, or a skill's rank on a key.
        java.util.function.Predicate<String> isStat = stat -> uz.dukeengine.dungeon.loot.Items.STATS.contains(stat)
                || rules.indexOf(stat) >= 0 || stat.matches(Stat.SKILL_RANK + ":[A-Z]");
        for (var affix : affixes) {
            var named = "Affix " + affix.name();
            require(isStat.test(affix.stat()), named + " has Stat = " + affix.stat() + ", and no figure is called that");
            require(affix.low().size() == 2 && affix.high().size() == 2, named + ": Low and High are [level 1, level 15]");
            affix.slots().forEach(slot -> require(slots.isEmpty() || slots.contains(slot),
                    named + " rolls on " + slot + ", and LootDrops wears no slot called that"));
            require(sayable(affix.prefix()), named + ": its Prefix may not contain ',' or '|'");
            require(!affix.stat().equals(Stat.SKILL_RANK) || !affix.keys().isEmpty(),
                    named + " raises a skill: say whose in Keys");
            for (var also : affix.also()) {
                var words = also.strip().split(" +");
                require(words.length == 2 && words[1].matches("-?[0-9]+") && isStat.test(words[0]),
                        named + " brings '" + also + "': a Stat and a number");
            }
        }
        for (var unique : uniques) {
            var named = "Unique " + unique.name();
            var base = itemBases.stream().filter(each -> each.name().equals(unique.base())).findFirst().orElse(null);
            require(base != null, named + " is made on " + unique.base() + ", and no ItemBase is called that");
            require(!unique.name().contains("-") && sayable(unique.displayName()) && sayable(unique.lore()),
                    named + ": a name with no '-', and words without ',' or '|'");
            require(unique.gives().size() >= 4 && unique.gives().size() <= 6, named + " gives 4 to 6 affixes");
            for (var said : unique.gives()) {
                var words = said.strip().split(" +");
                require(words.length == 2 && words[1].matches("-?[0-9]+"), named + " gives '" + said + "': a Stat and a number");
                require(isStat.test(words[0]), named + " gives " + words[0] + ", and no figure is called that");
            }
            unique.skills().forEach(skill -> require(skill.effect() != null && skill.effect().isPassive(),
                    named + " lends a " + skill.effect() + ": a worn thing lends passives only"));
        }
        for (var potion : potions) {
            require(!potion.name().contains("-") && sayable(potion.displayName()) && potion.stack() >= 1,
                    "Potion " + potion.name() + ": a name with no '-', and a Stack of one at least");
        }
        validateFood();
        validateMount();
        var item = itemRules;
        for (var list : java.util.List.of(item.rarities(), item.colours(), item.normal(), item.elite(), item.boss(),
                item.chest(), item.shop(), item.prices())) {
            require(list.size() == 4, "ItemRules: a list by rarity is four: common, magic, rare, epic");
        }
        require(!item.firstNames().isEmpty() && !item.secondNames().isEmpty(), "ItemRules: a rare's name needs words");
        require(item.beltSize() >= 0 && item.beltSize() <= 9 && item.beamFrames() >= 1,
                "ItemRules: BeltSize is 0 to 9, BeamFrames at least 1");
        for (var beam : item.beams()) {
            require(beam.isBlank() || effects.stream().anyMatch(effect -> effect.name().equals(beam)),
                    "ItemRules' Beams name " + beam + ", and no Effect block describes it");
        }
        for (var guard : item.campBosses()) {
            require(monster(guard) != null, "ItemRules' CampBosses name " + guard + ", and no Monster block describes it");
        }
    }

    /** Riding: the whistle a Potion, the horse and every look a Monster, a key, and frames of one at least. */
    private void validateMount() {
        if (!mount.on()) {
            return;
        }
        require(potions.stream().anyMatch(potion -> potion.name().equals(mount.whistle())),
                "Mount: Whistle " + mount.whistle() + " is no Potion");
        require(monster(mount.horse()) != null, "Mount: Horse " + mount.horse() + " is no Monster");
        for (var line : mount.looks()) {
            var sides = line.split("=");
            require(sides.length == 2 && monster(sides[1].strip()) != null,
                    "Mount: Looks are a hero = the Monster he is drawn as riding: " + line);
        }
        require(mount.key().length() == 1 && Character.isLetter(mount.key().charAt(0)) && mount.mountFrames() >= 1
                && mount.gallopFrames() >= 1 && mount.reach() > 0f && mount.callDistance() > 0f
                && mount.speedPercent() > -100, "Mount: one letter, frames of one at least, a Reach and a CallDistance");
        // Nobody else's: the orders' and walking's, the client's halt and pause, the screens', the tracker's, the
        // fire's, and every skill's own key.
        char key = Character.toUpperCase(mount.key().charAt(0));
        var taken = new java.util.HashSet<Character>(List.of('W', 'A', 'S', 'D', 'F', 'H', 'P',
                Character.toUpperCase(run.foldKey().charAt(0))));
        screens.keys().forEach(each -> taken.add(Character.toUpperCase(each)));
        skills.forEach(skill -> taken.add(Character.toUpperCase(skill.key())));
        if (!food.buildKey().isBlank()) {
            taken.add(Character.toUpperCase(food.buildKey().charAt(0)));
        }
        require(!taken.contains(key), "Mount: Key " + key + " is already another's");
    }

    /** Food and fire: every thing it names is one, every word a cursor's. See docs/plan/2026-10-15-food-and-fire.md. */
    private void validateFood() {
        var named = new java.util.HashSet<String>();
        potions.forEach(potion -> named.add(potion.name()));
        var things = new java.util.HashSet<String>();
        props.forEach(prop -> things.add(prop.name()));
        monsters.forEach(monster -> things.add(monster.name()));
        java.util.function.Predicate<String> thing = things::contains;
        if (!food.wood().isBlank()) {
            require(named.contains(food.wood()), "Food: Wood " + food.wood() + " is no Potion");
            require(thing.test(food.campfire()), "Food: Campfire " + food.campfire() + " is no Prop");
            require(food.buildKey().length() == 1 && food.fireRadius() > 0 && food.buildWood() >= 0
                    && food.cookFrames() >= 1 && food.beatFrames() >= 1,
                    "Food: BuildKey one letter, a FireRadius, and CookFrames and BeatFrames of one at least");
        }
        for (var recipe : food.recipes()) {
            require(named.contains(recipe.makes()) && named.containsAll(recipe.from()),
                    "Food: the recipe for " + recipe.makes() + " names a Potion there is not");
        }
        for (var gather : food.gathers()) {
            // A thing worked is a template, or the model of a piece of a map's scenery (a tree felled).
            require((thing.test(gather.thing()) || gather.thing().contains("/")) && named.contains(gather.item())
                    && gather.count() >= 1
                    && gather.frames() >= 1 && (gather.leaves().isEmpty() || thing.test(gather.leaves())),
                    "Food: working " + gather.thing() + " needs a Prop, a Potion, a count and frames");
            require(uz.dukeengine.dungeon.food.Food.WORDS.contains(gather.word()),
                    "Food: " + gather.word() + " is not a word of work " + uz.dukeengine.dungeon.food.Food.WORDS);
        }
        for (var clip : food.clips()) {
            var said = clip.strip().split("\\s+");
            require(said.length == 2 && uz.dukeengine.dungeon.food.Food.WORDS.contains(said[0]),
                    "Food: Clips are a word of work and a clip: " + clip);
        }
        for (var hunt : food.hunts()) {
            require(monster(hunt.animal()) != null && named.contains(hunt.item()) && hunt.count() >= 1,
                    "Food: hunting " + hunt.animal() + " needs a Monster and a Potion");
        }
        for (var place : food.forage()) {
            require(thing.test(place.thing()) && place.count() >= 0 && place.to() >= place.from(),
                    "Food: Forage places " + place.thing() + ", and nothing is called that");
        }
    }

    private void validate() {
        require(map.generation().mapWidth() > 0 && map.generation().mapHeight() > 0, "map size must be positive");
        require(map.generation().minRooms() >= 1, "a dungeon needs at least one room");
        require(map.generation().maxRooms() >= map.generation().minRooms(), "MaxRooms must not be below MinRooms");
        require(map.generation().minRoomSize() >= 3, "a room smaller than 3 cells has no interior");
        require(map.generation().maxRoomSize() >= map.generation().minRoomSize(), "MaxRoomSize must not be below MinRoomSize");
        require(map.generation().maxRoomSize() + 3 <= Math.min(map.generation().mapWidth(), map.generation().mapHeight()),
                "rooms must fit on the map with a border");
        require(map.generation().minSkeletonsPerRoom() >= 0, "a room cannot hold fewer than no skeletons");
        require(map.generation().maxSkeletonsPerRoom() >= map.generation().minSkeletonsPerRoom(),
                "MaxSkeletonsPerRoom must not be below MinSkeletonsPerRoom");
        require(combat.skeletonChaseRadius() >= combat.skeletonSenseRadius(),
                "a skeleton should not give up closer than it first notices");
        require(combat.skeletonRepathFrames() >= 1 && combat.heroRepathFrames() >= 1,
                "re-planning every zero frames is not a plan");
        require(combat.closeDistance() >= 0, "CloseDistance cannot be negative");
        require(combat.retreatTurns() >= 0, "RetreatTurns cannot be negative");
        require(combat.retreatTurnDegrees() > 0f && combat.retreatTurnDegrees() * combat.retreatTurns() <= 180f,
                "RetreatTurnDegrees times RetreatTurns has to stay within a half turn");
        require(combat.summonTurns() >= 0, "SummonTurns cannot be negative");
        require(combat.summonTurnDegrees() > 0f && combat.summonTurnDegrees() * combat.summonTurns() <= 180f,
                "SummonTurnDegrees times SummonTurns has to stay within a half turn");
        // A mark whose name is no Effect would be drawn by nothing, and nothing would say so.
        for (var mark : combat.auraMarkLooks()) {
            require(effects.stream().anyMatch(effect -> effect.name().equals(mark)),
                    "Combat's AuraMarkLooks names " + mark + ", and no Effect block describes it");
        }
        require(combat.auraMarkLooks().isEmpty() || combat.auraMarkTickFrames() >= 1,
                "AuraMarkTickFrames is the beat the marks are laid at, at least 1 while AuraMarkLooks names any:"
                        + " laying one every zero frames is not a beat");
        // An aura is lent again every AuraPulseFrames, and what it lends holds BuffLifeFrames: neither is a beat at
        // under one frame, and a life no longer than the pulse would lapse before the next pulse could renew it.
        require(combat.auraPulseFrames() >= 1,
                "AuraPulseFrames is the beat an aura is lent again at, at least 1: lending every zero frames is not"
                        + " a beat");
        require(combat.buffLifeFrames() >= 1,
                "BuffLifeFrames is how long what an aura lends holds, at least 1: a buff that holds for no frames is"
                        + " lent to nobody");
        require(combat.buffLifeFrames() > combat.auraPulseFrames(),
                "BuffLifeFrames has to be longer than AuraPulseFrames: a buff that holds no longer than the beat it is"
                        + " lent at would lapse between two pulses of a bearer that still reaches it");
        for (var kind : monsters) {
            var name = "Monster " + kind.name();
            for (var skill : skillsFor(kind.name())) {
                // A passive is never cast, and a mending is cast on its own side, so how far off HE
                // is means nothing to either. Everything else is cast at him, from one band.
                if (skill.effect().isPassive() || skill.effect() == SkillEffect.HEAL) {
                    continue;
                }
                require(kind.skillNearest() >= 0f && kind.skillFurthest() > kind.skillNearest(),
                        name + " has to cast its Skill " + skill.key()
                                + " across some distance: SkillDistance nearest furthest");
                // And what is aimed at him has to reach the band's far end, or it falls short from there.
                // What goes off round the caster itself -- a summoning's rifts -- is aimed at nothing.
                require(skill.effect().aim() == SkillEffect.Aim.SELF || skill.range() >= kind.skillFurthest(),
                        name + "'s Skill " + skill.key() + " reaches " + skill.range() + ", short of the "
                                + kind.skillFurthest() + " its SkillDistance casts from: give it a Range of at"
                                + " least that");
            }
            require(kind.keepFurthest() == 0f
                            || kind.keepNearest() >= 0f && kind.keepFurthest() > kind.keepNearest(),
                    name + "'s KeepDistance has to be a band, nearest then furthest");
            require(kind.maxPerRoom() >= 0, name + "'s MaxPerRoom cannot be negative");
            require(kind.maxMana() >= 0 && kind.manaRegen() >= 0, name + "'s MaxMana and ManaRegen cannot be negative");
            requireLinked(kind.look().animations(), name);
        }
        for (var hero : heroes) {
            requireLinked(hero.animations(), "Hero " + hero.name());
        }
        for (var theme : themes) {
            for (var themed : theme.monsters()) {
                requireLinked(themed.animations(), "Theme " + theme.name() + "'s " + themed.name());
            }
        }
        for (var look : looks) {
            requireLinked(look.animations(), "ThemeMonster " + look.name());
        }
        for (var guard : map.descent().bossGuards().entrySet()) {
            require(monster(guard.getKey()) != null,
                    "BossGuards names " + guard.getKey() + ", and no Monster block describes it");
            require(guard.getValue() >= 1, "BossGuards has to put at least one " + guard.getKey() + " there");
        }
        require(map.descent().bossGuardRing() >= 1, "BossGuardRing has to stand the guard off the boss's own cell");
        var descent = map.descent();
        require(descent.wayInLevel() >= 1 && descent.beforeBossLevel() >= descent.wayInLevel(),
                "a monster's level starts at WayInLevel, at least 1, and climbs to BeforeBossLevel, no lower");
        require(descent.tierGrowthPercent() >= 0 && descent.bossLevelsAbove() >= 0,
                "TierGrowthPercent and BossLevelsAbove cannot step a monster's level back down");
        require(descent.maxMonsterLevel() >= 1, "MaxMonsterLevel is at least the first level");
        require(descent.healthPercentPerLevel() >= 0 && descent.damagePercentPerLevel() >= 0
                        && descent.experiencePercentPerLevel() >= 0 && descent.manaPercentPerLevel() >= 0,
                "a level cannot take a monster's health, its blow, its worth or its mana away");
        // A creature's words cross to the client joined by ',', inside a line split on '|'.
        require(sayable(unitBar.levelWord()), "the UnitBar's LevelWord may not contain ',' or '|'");
        require(map.generation().corridorWidth() >= 1, "a corridor narrower than one cell is a wall");
        require(map.generation().maxRoomSpacing() > map.generation().maxRoomSize(), "rooms could never reach one another");
        require(map.propsPerRoom().min() >= 0, "a room cannot hold fewer than no things");
        require(map.propsPerRoom().max() >= map.propsPerRoom().min(),
                "MaxPerRoom must not be below MinPerRoom");
        for (int size : map.keep().sizes()) {
            require(size >= 7 && size % 2 == 1, "a Keep is odd and at least 7 across, so its court and its doorway"
                    + " have middle cells and its guard corners apart from the boss's: " + size);
            require(size + 4 <= Math.min(map.generation().mapWidth(), map.generation().mapHeight()),
                    "a Keep " + size + " across cannot stand on the map with rock round it");
        }
        var sizes = map.keep().sizes();
        for (int i = 1; i < sizes.size(); i++) {
            require(sizes.get(i) < sizes.get(i - 1),
                    "a Keep's Sizes are tried largest first, each smaller than the one before: " + sizes);
        }
        require(map.keep().sizes().isEmpty() || !map.keep().gate().isBlank(),
                "a Keep needs a Gate: something has to stand in its doorway");
        require(map.keep().look().isBlank() || themes.stream().anyMatch(theme -> theme.name().equals(map.keep().look())),
                "the Keep's Look names no theme: " + map.keep().look());
        require(world.levelHeight() >= 0f, "LevelHeight cannot be negative");
        require(world.navigationCellsPerCell() >= 1 && world.navigationCellsPerCell() <= 4,
                "NavigationCellsPerCell is 1 to 4: finer than a quarter of a cell costs sixteen times the walking");
        for (var theme : themes) {
            validateTerrain("Theme " + theme.name() + "'s Terrain", theme.terrain());
        }
        for (var name : map.biomes()) {
            var climate = themes.stream().filter(theme -> theme.name().equals(name)).findFirst()
                    .map(Theme::climate).orElse(null);
            require(climate != null, "Biome " + name + " has no Climate: a biome grows where its climate suits it,"
                    + " and this one says nowhere");
            require(percent(climate.wild()) && percent(climate.alive()),
                    "Biome " + name + "'s Climate is how wild then how alive, each 0 to 100");
        }
        require(map.biomes().isEmpty() || map.biomeSize() >= 8,
                "BiomeSize is at least 8 cells: it is how far one sweep of the weather reaches, which picks each region's"
                        + " biome, and weather that changes inside a chamber is noise and not weather");
        // Not asked that the most regions times the share come to 100 or under: the generator lowers the count it
        // draws to what the share lets fit, and generation.duke says so.
        require(map.biomes().isEmpty() || map.biomeCount().fewest() >= 1,
                "BiomeCount's fewest is at least 1: a floor with no region has no biome to wear");
        require(map.biomes().isEmpty() || map.biomeCount().most() >= map.biomeCount().fewest(),
                "BiomeCount is the fewest regions then the most, and the most is not below the fewest: ["
                        + map.biomeCount().fewest() + ", " + map.biomeCount().most() + "]");
        require(map.biomes().isEmpty() || map.minBiomeShare() >= 1 && map.minBiomeShare() <= 50,
                "MinBiomeShare is 1 to 50 percent of the floor: a region is some of it, and over half would leave"
                        + " no room for a second");
        require(run.respawnDelayFrames() >= 0, "the death pause cannot be negative");
        require(run.descendDelayFrames() >= 0, "the pause before descending cannot be negative");
        require(progression.maxLevel() >= Levelling.FIRST_LEVEL, "MaxLevel cannot be below the first level");
        require(progression.xpBase() > 0, "XpBase must be positive or no level is ever reached");
        require(progression.xpStep() >= 0, "XpStep cannot make later levels cheaper");
        require(progression.armourPercentPerLevel() >= 0, "a level cannot take armour away");
        validateAttributes();
        validateScreens();
        validateControls();
        require(travel.cost() >= 0, "Travel: Cost cannot be negative");
        require(travel.card().isBlank() || titles.stream().anyMatch(t -> t.name().equals(travel.card())),
                "Travel: Card names no Title: " + travel.card());
        require(travel.keeper().isBlank() || npcs.stream().anyMatch(n -> n.name().equals(travel.keeper())),
                "Travel: Keeper names no Npc: " + travel.keeper());
        require(progression.minDamageTakenPercent() > 0 && progression.minDamageTakenPercent() <= 100,
                "the damage floor must leave some way to lose");
        require(progression.levelUpBannerFrames() >= 0, "the level-up message cannot last negative frames");
        require(party.maxPlayers() >= 1 && party.maxPlayers() <= 8, "a party is 1 to 8 heroes");
        require(party.port() >= 1 && party.port() <= 65535, "a port is 1 to 65535");
        require(camera.edgeMargin() >= 0, "the screen's edge cannot be a negative width");
        require(camera.edgeSpeedPercent() >= 0, "a camera cannot be shoved backwards");
        require(fog.unseenPercent() >= 0 && fog.unseenPercent() <= 100,
                "UnseenPercent is a share of a lit room");
        require(fog.rememberedPercent() >= 0 && fog.rememberedPercent() <= 100,
                "RememberedPercent is a share of a lit room");
        require(fog.visiblePercent() >= 0 && fog.visiblePercent() <= 100,
                "VisiblePercent is a share of a lit room");
        require(fog.unseenPercent() <= fog.rememberedPercent()
                        && fog.rememberedPercent() <= fog.visiblePercent(),
                "the three shades have to darken in that order, or the map reads backwards");
        require(fog.softenCells() >= 0, "the fog cannot be smeared over negative cells");
        validatePaintedGround();
        require(fog.openPerSecond() > 0, "fog that never opens is a black screen");
        require(fog.textureSize() >= 8 && fog.textureSize() <= 2048,
                "TextureSize is the fog sheet's own resolution, between 8 and 2048");
        require(lootDrops.dropPercent() >= 0 && lootDrops.dropPercent() <= 100,
                "DropPercent is a chance, not a count");
        require(lootDrops.bossDropPercent() >= 0 && lootDrops.bossDropPercent() <= 100,
                "BossDropPercent is a chance, not a count");
        require(lootDrops.pickupRange() > 0, "something he can never reach is not loot");
        require(lootDrops.noteFrames() >= 0, "the pickup message cannot last negative frames");
        require(lootDrops.slots() >= 1 && lootDrops.slots() <= 12, "a bag holds from 1 to 12 things");
        require(lootDrops.joinCount() >= 2, "JoinCount: it takes at least two alike to join");
        require(lootDrops.topLevel() >= 1 && lootDrops.topLevel() <= 3, "TopLevel is 1 to 3");
        require(sayable(lootDrops.fullWord()), "FullWord may not contain ',' or '|'");
        require(sayable(lootDrops.noUseWord()), "NoUseWord may not contain ',' or '|'");
        require(sayable(lootDrops.noWayWord()), "NoWayWord may not contain ',' or '|'");
        require(lootDrops.stuckFrames() > 0,
                "StuckFrames, how long a hero gets no nearer before he gives an errand up, is at least one frame");
        require(lootDrops.stuckFightingFrames() >= lootDrops.stuckFrames(),
                "StuckFightingFrames, the limit that counts his fighting too, is at least StuckFrames");
        for (var item : loot) {
            require(sayable(item.name()),
                    "an item's DisplayName may not contain ',' or '|': " + item.id());
        }
        for (var item : loot) {
            require(!item.kind().equals(QUEST) || item.weight() == 0,
                    "LootItem " + item.id() + " is a QUEST thing, laid or given and never found: its Weight is 0");
            require(!item.kind().equals(KEY) || item.weight() == 0,
                    "LootItem " + item.id() + " is a KEY, which is given and never found: its Weight is 0");
            // A key is what opens a gate, and nothing else is. The engine knows one by its Use, and this file names
            // it by its Kind as well: only the loader can keep the two sets equal, so it does, each way.
            require(!item.kind().equals(KEY) || item.use() == ItemUse.UNLOCK,
                    "LootItem " + item.id() + " is a KEY, which opens a gate and does nothing else: its Use is UNLOCK");
            require(item.use() != ItemUse.UNLOCK || item.kind().equals(KEY),
                    "LootItem " + item.id() + " has Use = UNLOCK and is not a KEY: only a key opens a gate");
            // Any other Kind or Extra the hero's figures do not read would be carried and count for nothing: a
            // misspelling, said here rather than found in play.
            require(KINDS.contains(item.kind()),
                    "LootItem " + item.id() + " has Kind = " + item.kind() + ", and no figure is called that");
            require(item.extra().isEmpty() || EXTRAS.contains(item.extra()),
                    "LootItem " + item.id() + " has Extra = " + item.extra() + ", and no figure is called that");
            // A Slot the bag does not wear would be carried and never worn: a misspelling, said here. A bag that wears
            // nothing — a test's, with no LootDrops of its own — has every slot act, and a Slot means nothing to it.
            require(item.slot().isEmpty() || lootDrops.wornSlots().isEmpty()
                            || lootDrops.wornSlots().contains(item.slot()),
                    "LootItem " + item.id() + " has Slot = " + item.slot() + ", and LootDrops wears no slot called that");
        }
        require(lootDrops.wornSlots().stream().distinct().count() == lootDrops.wornSlots().size(),
                "LootDrops: each of WornSlots is named once");
        require(lootDrops.wornSlots().stream().allMatch(slot -> !slot.isBlank() && sayable(slot)),
                "LootDrops: a worn slot is a word, without ',' or '|'");
        // The floor lays the first KEY there is: a keep needs one.
        require(map.keep().sizes().isEmpty() || loot.stream().anyMatch(item -> item.kind().equals(KEY)),
                "a Keep's gate opens only to a key, and no LootItem has Kind = KEY to lay on its floor");
        // People and their talk: every name in it points at something the files have.
        var known = new java.util.ArrayList<>(loot.stream().map(Loot::id).toList());
        itemBases.forEach(base -> known.add(base.name()));
        potions.forEach(potion -> known.add(potion.name()));
        uniques.forEach(unique -> known.add(unique.name()));
        var story = story().problems(monsters.stream().map(MonsterKind::name).toList(), known);
        require(story.isEmpty(), String.join("; ", story));
        validateItems();
        require(goldBurst.coinLook().isBlank()
                || effects.stream().anyMatch(effect -> effect.name().equals(goldBurst.coinLook())),
                "GoldBurst's CoinLook names " + goldBurst.coinLook() + ", and no Effect block describes it");
        require(goldBurst.goldPerBurst() > 0 && goldBurst.mostBursts() > 0 && goldBurst.floatSeconds() > 0f,
                "GoldBurst: GoldPerBurst, MostBursts and FloatSeconds are more than nothing");
        for (var blow : heavyBlows) {
            require(blow.majorAt() > 0f && effectLists.stream().anyMatch(list -> list.name().equals(blow.plays())),
                    "HeavyBlow " + blow.template() + ": MajorAt is more than nothing and Plays names an EffectList");
        }
        for (var crit : critBlows) {
            require(effectLists.stream().anyMatch(list -> list.name().equals(crit.plays())),
                    "CritBlow " + crit.template() + ": Plays names no EffectList");
        }
        for (var death : bigDeaths) {
            require(effectLists.stream().anyMatch(list -> list.name().equals(death.plays())),
                    "BigDeath " + death.template() + ": Plays names no EffectList");
        }
        if (!medallion.item().isBlank()) {
            var named = "Medallion " + medallion.item();
            var item = loot.stream().filter(each -> each.id().equals(medallion.item())).findFirst().orElse(null);
            require(item != null && !item.slot().isEmpty(), named + ": no LootItem is called that, or it names no Slot");
            require(medallion.wisp().isBlank()
                    || projectiles.stream().anyMatch(each -> each.name().equals(medallion.wisp())),
                    named + ": its Wisp " + medallion.wisp() + " is no Projectile");
            require(!medallion.cursedWord().isBlank() && sayable(medallion.cursedWord()),
                    named + ": CursedWord is a word, without ',' or '|'");
        }
        for (var campaign : campaigns) {
            // Its maps are checked as they are laid, as a MAP's are; its hero, things and descents here.
            var named = "Campaign " + campaign.name();
            require(campaign.name().matches("[a-z0-9]+") && campaigns.stream()
                    .filter(each -> each.name().equals(campaign.name())).count() == 1,
                    named + ": a Name of small letters and digits, and no other act's");
            require(heroes.stream().anyMatch(hero -> hero.name().equals(campaign.hero())),
                    named + ": its Hero " + campaign.hero() + " is no Hero block");
            require(!campaign.joints().isEmpty(), named + ": it has no Joints, so a new game has nowhere to open");
            campaign.descents().forEach(each -> require(each.firstDepth() >= 1 && !each.name().isBlank(),
                    named + ": a Descent has a Name and a FirstDepth of 1 or more"));
            campaign.joints().forEach(joint -> joint.items().forEach(item -> require(
                    loot.stream().anyMatch(each -> each.id().equals(item)),
                    named + ": the joint at " + joint.map() + " gives " + item + ", and no LootItem is called that")));
        }
        for (var place : places) {
            try {
                uz.dukeengine.dungeon.map.StaticMap.Zone.of(place.zone());
            } catch (IllegalArgumentException e) {
                require(false, "Place on " + place.map() + ": " + e.getMessage());
            }
        }
        var kinds = monsters.stream().map(MonsterKind::name).toList();
        for (var battle : battles) {
            var named = "Battle " + battle.name();
            java.util.stream.Stream.concat(battle.kingdom().stream(), battle.enemy().stream()).forEach(kind ->
                    require(kinds.contains(kind), named + ": " + kind + " is no Monster"));
            require(battle.ritualists() == 0 || kinds.contains(battle.ritualist()),
                    named + ": its Ritualist " + battle.ritualist() + " is no Monster");
            require(battle.ring() > 0f, named + ": its Ring is how far out the ritualists stand, above 0");
        }
        for (var wave : shockwaves) {
            require(wave.radius() > 0f && wave.frames() >= 1 && wave.stunFrames() >= 0,
                    "Shockwave " + wave.name() + ": a Radius above 0, Frames of 1 or more, StunFrames of 0 or more");
        }
        for (var gate : gates) {
            require(!gate.open().isEmpty() && !gate.map().isBlank(),
                    "Gate " + gate.name() + ": on a Map, with at least one Open zone");
        }
        require(dialogue.checkFrames() >= 1, "Dialogue's CheckFrames is how often people are looked for: at least 1");
        require(dialogue.glowLook().isBlank()
                || effects.stream().anyMatch(effect -> effect.name().equals(dialogue.glowLook())),
                "Dialogue's GlowLook names " + dialogue.glowLook() + ", and no Effect block describes it");
        // The looks the story plays: a boss's as it is beaten, and every EFFECT a trigger or a phase plays.
        var looks = new java.util.ArrayList<String>();
        for (var boss : bosses) {
            looks.add(boss.beatenLook());
            boss.phases().forEach(phase -> phase.actions().forEach(action -> looks.add(effectOf(action))));
        }
        triggers.forEach(trigger -> trigger.actions().forEach(action -> looks.add(effectOf(action))));
        for (var look : looks) {
            require(look.isBlank() || effects.stream().anyMatch(effect -> effect.name().equals(look)),
                    "the story plays the Effect " + look + ", and no Effect block describes it");
        }
        for (var title : titles) {
            require(!title.text().isBlank() && title.fadeIn() >= 0f && title.hold() > 0f && title.fadeOut() >= 0f,
                    "Title " + title.name() + ": it says something, and is held a while");
        }
        triggers.forEach(trigger -> trigger.actions().forEach(action -> require(
                action.act() != uz.dukeengine.dungeon.story.Trigger.Act.TITLE || title(action.target()) != null,
                "Trigger " + trigger.name() + " shows the Title " + action.target() + ", and no Title is called that")));
        require(dialogue.glowFrames() >= 1, "Dialogue's GlowFrames is how often a quest's target glows again: at least 1");
        for (var moment : moments) {
            require(!moment.effect().isBlank(), "Moment " + moment.name() + " plays no Effect");
            require(moment.scale() > 0f, "Moment " + moment.name() + " has to be drawn at some size");
        }
        for (var skill : skills) {
            // The panel is told which picture to draw down the status line, and
            // the line is split on those two characters.
            require(sayable(skill.icon()),
                    "a skill's Icon may not contain ',' or '|': " + skill.key());
            // A stun rides what a skillshot throws, a wave, the shield charge and the pull, and nothing else a skill
            // does carries one: written on any other, it would be a number nothing read.
            require(skill.stunFrames() == 0
                            || skill.stunFrames() > 0 && STUNNERS.contains(skill.effect()),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " has StunFrames = " + skill.stunFrames()
                            + ": only a SKILLSHOT, WAVE, SKEWER or PULL stuns, and for no fewer than no frames");
            // Passives are monsters' — but a SOUL_DRINK, a hero's own or lent by what he wears (the medallion's), and
            // the knight's DRAGON_BLOOD, its rank told the book from his SkillRanks (DungeonRun.tellTheRanks): a hero's
            // slots, aims, rings and tips know nothing else of a skill that is not cast. Asked first, so that a hero's
            // is refused for that and not for what else it has.
            require(!skill.effect().isPassive() || skill.effect() == SkillEffect.SOUL_DRINK
                            || skill.effect() == SkillEffect.DRAGON_BLOOD || skill.effect() == SkillEffect.STAGGER
                            || heroes.stream().noneMatch(hero -> hero.name().equals(skill.heroTemplate())),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + ", never cast: only a monster may have one but a SOUL_DRINK or a DRAGON_BLOOD -- a"
                            + " hero's bar has no place for it");
            // A passive is never cast, so what only a cast reads would be a number nothing read -- but an aura is worn,
            // and may say what it looks like: its bearer plays it on itself.
            // A SOUL_DRINK's Damage is its cap, read.
            // A STAGGER's Damage is the least blow that staggers, read.
            require(!skill.effect().isPassive() || (skill.damage() == 0f || skill.effect() == SkillEffect.SOUL_DRINK
                            || skill.effect() == SkillEffect.STAGGER)
                            && skill.manaCost() == 0
                            && skill.windUpFrames() == 0 && !skill.hasProjectile()
                            && (!skill.hasLook() || skill.effect().isAura()),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + ", never cast: a Damage, ManaCost, WindUpFrames, Projectile or Look on it is read by"
                            + " nothing -- only an aura is worn, and may name a Look");
            // And worn at the beat it names: an aura's Look and its TickFrames come together, or neither does.
            require(!skill.effect().isAura() || skill.hasLook() == (skill.tickFrames() > 0),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect() + ", worn every"
                            + " TickFrames as its Look: the two come together, or neither does");
            // An aura holds while its bearer lives, and its Look is measured two of its beats -- a DurationFrames would
            // be read first, and lay the rings more than two deep.
            require(!skill.effect().isAura() || skill.durationFrames() == 0,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect() + " and has"
                            + " DurationFrames = " + skill.durationFrames() + ": it holds while its bearer lives, and"
                            + " its Look lasts two TickFrames");
            // An aura lends what it is worth as far as its Radius and no further: without one it reaches nobody.
            require(!skill.effect().isAura() || skill.radius() > 0f,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + " and reaches nobody: it needs a Radius");
            // What a mana aura lends, and nothing else reads: written on any other skill, a number nothing read.
            require(skill.manaRegen() == 0 || skill.effect() == SkillEffect.MANA_AURA,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " has ManaRegen = " + skill.manaRegen()
                            + ": only a MANA_AURA lends mana");
            switch (skill.effect()){
                case STAGGER -> require(skill.damage() > 0f && skill.stunFrames() > 0 && skill.durationFrames() >= 0
                                && skill.castAnim() != null && !skill.castAnim().isBlank(),
                        skill.heroTemplate() + "'s STAGGER needs the least blow (Damage), StunFrames, and the flinch"
                                + " it plays (CastAnim)");
                case DODGE -> require(skill.distance() > 0f && skill.durationFrames() > 0 && skill.tickFrames() >= 0
                                && skill.tickFrames() <= skill.durationFrames() && skill.castAnim() != null
                                && !skill.castAnim().isBlank(),
                        skill.heroTemplate() + "'s DODGE needs a Distance, its DurationFrames, its TickFrames out of"
                                + " harm's way (no more than the roll) and the roll it plays (CastAnim)");
                case HEAL -> {
                    var name = skill.heroTemplate() + "'s Skill " + skill.key();
                    require(skill.heal() > 0f && skill.range() > 0f, name + " mends nobody: it needs a Heal and a Range");
                    require(skill.healBelowPercent() > 0 && skill.healBelowPercent() <= 100,
                            name + "'s HealBelowPercent is a share of health, from 1 to 100");
                    require(skill.hasProjectile(), name + " has no light to call down: name it in Projectile");
                }
                case SUMMON -> {
                    var name = skill.heroTemplate() + "'s Skill " + skill.key();
                    require(!skill.summons().isEmpty() && skill.maxSummoned() >= 1
                                    && skill.summons().values().stream().allMatch(count -> count >= 1),
                            name + " calls up nothing: it needs Summons = [Kind = count, ...], each at least one,"
                                    + " and a MaxSummoned");
                    for (var kind : skill.summons().keySet()) {
                        require(monster(kind) != null,
                                name + " calls up " + kind + ", and no Monster block describes it");
                    }
                    require(skill.radius() > 0f && skill.durationFrames() > 0,
                            name + " needs a Radius to call them up at and DurationFrames for them to last");
                    require(skill.summonExperiencePercent() >= 0 && skill.summonExperiencePercent() <= 100,
                            name + "'s SummonExperiencePercent is a share, from 0 to 100");
                    require(skill.hasProjectile(), name + " has no rift to open: name it in Projectile");
                }
                case HASTE -> require(skill.boostPercent() >= 1 && skill.durationFrames() > 0,
                        skill.heroTemplate() + "'s Skill " + skill.key() + " hastens nobody: it needs a BoostPercent"
                                + " of at least 1 and DurationFrames for it to last");
                case LIFESTEAL, LIFESTEAL_AURA -> require(skill.boostPercent() >= 1 && skill.boostPercent() <= 100,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s BoostPercent is the share of every"
                                + " blow it drinks, from 1 to 100");
                case DAMAGE_AURA -> require(skill.boostPercent() >= 1,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s BoostPercent is what it adds to every"
                                + " blow round it, at least 1");
                case WAVE -> require(skill.hasProjectile() && skill.distance() > 0f && skill.hitWidth() > 0f,
                        skill.heroTemplate() + "'s Skill " + skill.key() + " is a WAVE: it needs a Projectile to"
                                + " be seen by, a Distance to run and a HitWidth to hurt across");
                case DRAGON_BLOOD -> require(skill.boostPercent() >= 1 && skill.boostPercent() <= 100
                                && skill.heal() >= 0f,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s BoostPercent is the share of every"
                                + " blow its blood turns aside, from 1 to 100, and its Heal no less than none");
                case MANA_AURA -> require(skill.manaRegen() >= 1,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s ManaRegen is what it adds to every"
                                + " pool round it, in tenths of a point a second, at least 1");
            }
        }
        // A look worn as a state is played again as the state is given again -- a second stun, a second haste, an aura
        // at each beat -- and an AURA layer of it that does not renew is dropped where it should be carried to the new
        // end, so its picture would end with the first. A MARK is laid anew at each beat by the client, and needs none:
        // the three auras' looks are each one, and have no AURA layer for this to ask of.
        for (var effect : effects) {
            if (!wornAsAState(effect.name())) {
                continue;
            }
            for (var layer : effect.layers()) {
                require(!EffectLayer.AURA.equalsIgnoreCase(layer.type()) || Boolean.TRUE.equals(layer.renews()),
                        "Effect " + effect.name() + " is worn as a state -- a stun's, a haste's or an aura's look -- and"
                                + " its Layer " + layer.name() + " is an AURA that does not say Renews = Yes: given"
                                + " again, the state would outlast the layer, which is dropped instead of being carried"
                                + " to the new end");
            }
        }
    }

    /**
     * Whether {@code look} is worn as a state: the Combat block's {@code StunLook}, and every haste's and aura's
     * {@code Look}.
     */
    private boolean wornAsAState(String look) {
        return look != null && (look.equals(combat.stunLook()) || skills.stream().anyMatch(skill ->
                look.equals(skill.look()) && (skill.effect() == SkillEffect.HASTE || skill.effect().isAura())));
    }

    /** What a painted map's ground is drawn by: each number in the range the client draws it in. */
    private void validatePaintedGround() {
        var ground = paintedGround;
        require(ground.raggedness() >= 0f && ground.raggedness() <= 1f,
                "PaintedGround: Raggedness is how far across a cell the noise moves a border, 0 to 1");
        require(ground.sharpness() >= 1f, "PaintedGround: Sharpness is 1 for a ramp across the cell, or more");
        require(ground.noiseCoverage() > 0f && ground.variationCoverage() > 0f && ground.cliffCoverage() > 0f,
                "PaintedGround: a picture's coverage is how many cells one copy of it covers, more than none");
        require(ground.cliffSlope() > 0f && ground.cliffSlope() < 90f,
                "PaintedGround: CliffSlope is a lean in degrees from level, between 0 and 90");
        require(ground.streamCells() >= 0, "PaintedGround: StreamCells cannot be negative; 0 builds the ground whole");
        require(ground.minimapCells() >= 0, "PaintedGround: MinimapCells cannot be negative; 0 shows every map whole");
        require(ground.hazeFrom() >= 0f && ground.hazeTo() >= ground.hazeFrom(),
                "PaintedGround: the haze starts at HazeFrom cells and is whole by HazeTo, no nearer");
        require(ground.hazeTo() <= ground.streamCells() || ground.streamCells() == 0,
                "PaintedGround: HazeTo reaches no further than the StreamCells the ground is built to");
        require(ground.riverFlow() >= 0f, "PaintedGround: RiverFlow is how fast a river runs, not backwards");
        for (var tint : ground.tints().entrySet()) {
            require(tint.getValue() >= 0 && tint.getValue() <= 0xFFFFFF,
                    "PaintedGround: the tint of " + tint.getKey() + " is a colour, 0xRRGGBB");
        }
        for (var variation : ground.variations().entrySet()) {
            require(variation.getValue() >= 0f && variation.getValue() <= 1f,
                    "PaintedGround: the variation of " + variation.getKey() + " is 0 to 1");
        }
    }

    /**
     * The attributes, and everything that names one: a hero's lines and his primary, and
     * an item that gives one.
     *
     * <p>Names are checked here rather than where they are read, because a hero's block
     * may come before the attribute it names, and a partial file's attributes are only
     * all there once the shipped ones have been filled in.
     */
    private void validateAttributes() {
        require(progression.damagePerPrimary().value() >= 0,
                "Progression: a point of a primary cannot take something away");
        var rules = attributeRules();
        for (int i = 0; i < attributes.size(); i++) {
            var attribute = attributes.get(i);
            var name = "Attribute " + attribute.name();
            require(attribute.healthPerPoint().value() >= 0 && attribute.speedPerPoint().value() >= 0
                            && attribute.manaPerPoint().value() >= 0,
                    name + ": a point of it cannot take something away");
            // Both are fields on the status line, which splits on these two.
            require(sayable(attribute.word()) && sayable(attribute.icon()),
                    name + ": its Word and Icon may not contain ',' or '|'");
            for (int j = 0; j < i; j++) {
                var earlier = attributes.get(j);
                require(!earlier.isNamed(attribute.name()) && !earlier.isNamed(attribute.shortName()),
                        name + " is called what Attribute " + earlier.name()
                                + " already is, and a hero could not say which he means");
            }
        }
        for (var hero : heroes) {
            var name = "Hero " + hero.name();
            var named = new java.util.HashSet<Integer>();
            for (var attribute : hero.attributes()) {
                int at = rules.indexOf(attribute.name());
                require(at >= 0, name + " has " + attribute.name()
                        + ", and no Attribute is called that");
                require(named.add(at), name + " names " + attribute.name() + " twice");
                require(attribute.base().value() >= 0 && attribute.growth().value() >= 0,
                        name + ": an attribute cannot be negative, and a level cannot take one away");
            }
            require(hero.primary() == null || rules.indexOf(hero.primary()) >= 0,
                    name + "'s Primary is " + hero.primary()
                            + ", and no Attribute is called that");
            require(hero.primary() != null || hero.attributes().isEmpty(),
                    name + " has attributes and no Primary: say which of them he hits with");
            require(hero.manaRegen() >= 0 && hero.healthRegen() >= 0,
                    name + ": what comes back on its own cannot be negative");
        }
        for (var item : loot) {
            var name = "LootItem " + item.id();
            require(item.extraStep() >= 0, name + ": ExtraValue cannot be negative");
            if (item.kind().equals(Stat.ATTRIBUTE)) {
                require(rules.indexOf(item.attribute()) >= 0, name + " gives "
                        + item.attribute() + ", and no Attribute is called that");
            } else {
                require(item.attribute().isEmpty(),
                        name + ": Attribute only means something on an item of Kind = ATTRIBUTE");
            }
        }
        var block = statBlock;
        require(block.figureIcon() > 0f && block.primaryIcon() > 0f && block.attributeIcon() > 0f,
                "StatBlock: a socket has to have a size");
        require(block.iconShare() > 0f && block.iconShare() <= 1f,
                "StatBlock: IconShare is a share of a socket");
        require(block.figureRows() >= 1 && block.attributeRows() >= 1,
                "StatBlock: the block keeps room for at least one row of each");
        require(block.rowGap() >= 0f && block.gapUnderBar() >= 0f,
                "StatBlock: a gap cannot be negative");
        // Each is a field on the status line, which splits on these two.
        require(sayable(hud.healthWord()) && sayable(hud.speedNowWord()),
                "HealthWord and SpeedNowWord may not contain ',' or '|'");
        require(hud.primaryWord().indexOf('|') < 0 && hud.eachPointWord().indexOf('|') < 0,
                "PrimaryWord and EachPointWord may not contain '|'");
        require(hud.buffIcon() >= 1f, "Hud: BuffIcon is how big each picture over a picked creature's bar is drawn, at"
                + " least a pixel: " + hud.buffIcon());
    }

    /** The screens' keys: three letters, none of them a key something else is already on. */
    private void validateScreens() {
        var keys = screens.keys();
        require(keys.stream().allMatch(key -> key >= 'A' && key <= 'Z') && keys.stream().distinct().count() == 3,
                "Screens: SheetKey, QuestsKey and MapKey are three different letters: " + keys);
        // The orders' four, the client's halt and pause, the tracker's fold, and every skill's.
        var taken = new java.util.HashSet<Character>(List.of('A', 'S', 'D', 'F', 'H', 'P',
                Character.toUpperCase(run.foldKey().charAt(0))));
        skills.forEach(skill -> taken.add(Character.toUpperCase(skill.key())));
        require(keys.stream().noneMatch(taken::contains), "Screens: a screen's key is already another's: " + keys);
        screens.placeNames(); // every place is "Zone = said"
        require(screens.mostTexels() >= 16, "Screens: MostTexels is at least 16");
    }

    /**
     * The modern letters: one for every slot a hero presses, each a letter of its own, and none on walking (W A S D),
     * a screen, the tracker, the fire, or the client's halt and pause; a slot with none keeps its own key.
     */
    private void validateControls() {
        controls.fileScheme(); // MODERN or CLASSIC
        var modern = controls.modernKeys();
        require(modern.values().stream().allMatch(key -> key >= 'A' && key <= 'Z')
                && modern.values().stream().distinct().count() == modern.size(),
                "Controls: ModernSkillKeys presses each slot on a letter of its own: " + modern);
        var taken = new java.util.HashSet<Character>(List.of('W', 'A', 'S', 'D', 'H', 'P',
                Character.toUpperCase(run.foldKey().charAt(0))));
        screens.keys().forEach(key -> taken.add(Character.toUpperCase(key)));
        if (!food.buildKey().isBlank()) {
            taken.add(Character.toUpperCase(food.buildKey().charAt(0)));
        }
        if (mount.on()) {
            taken.add(Character.toUpperCase(mount.key().charAt(0)));
        }
        require(modern.values().stream().noneMatch(taken::contains),
                "Controls: a modern skill letter is walking's or another key's: " + modern);
        // A hero's slot on a step's own key is fine in classic (a data-only hero on A), so it is refused only where
        // modern is the file's scheme.
        for (var hero : controls.fileScheme() == uz.dukeengine.dungeon.world.Controls.Scheme.MODERN ? heroes
                : List.<uz.dukeengine.dungeon.content.Hero>of()) {
            for (var skill : skillsFor(hero.name())) {
                // A slot with no modern letter is pressed on its own key, which must not be a step.
                require(skill.effect().isPassive() || modern.containsKey(Character.toUpperCase(skill.key()))
                        || "WASD".indexOf(Character.toUpperCase(skill.key())) < 0,
                        "Controls: ModernSkillKeys says no letter for " + hero.name() + "'s " + skill.key());
            }
        }
    }

    /** Ground the generator can carve: every percentage a percentage, and no slope steep enough to be a cliff. */
    private static void validateTerrain(String who, Theme.Terrain terrain) {
        require(percent(terrain.ragged()) && percent(terrain.winding()) && percent(terrain.loops())
                && percent(terrain.level()), who + ": Ragged, Winding, Loops and Level are percentages, 0 to 100");
        require(terrain.islandsPerRoom().min() >= 0 && terrain.islandsPerRoom().max() >= terrain.islandsPerRoom().min(),
                who + ": IslandsPerRoom is fewest then most, and never fewer than none");
        require(terrain.rise() >= 0, who + ": the ground cannot rise a negative height");
        require(terrain.hillSize() >= 1, who + ": a hill is at least one cell across");
        require(terrain.slope() >= 1 && terrain.slope() < uz.dukeengine.core.pathfind.HeightMap.CLIFF_STEPS,
                who + ": Slope is 1 to 15 steps: from 16 a cell is a cliff, and a floor checked walkable would not be");
    }

    private static boolean percent(int value) {
        return value >= 0 && value <= 100;
    }

    /** A unit that links animations links ones some file declares. */
    private void requireLinked(String animations, String who) {
        require(animations == null || animationSets.stream().anyMatch(set -> set.name().equals(animations)),
                who + " moves by Animations = " + animations + ", and no AnimationSet is called that");
    }

    private static boolean sayable(String words) {
        return words != null && words.indexOf(',') < 0 && words.indexOf('|') < 0;
    }

    /** The Effect an EFFECT action plays, or blank for an action of another Act. */
    private static String effectOf(uz.dukeengine.dungeon.story.Trigger.Action action) {
        return action.act() == uz.dukeengine.dungeon.story.Trigger.Act.EFFECT ? action.target() : action.landed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("settings: " + message);
        }
    }

    // ---- what the data files hold, each block a record ----

    private final java.util.List<Hero> heroes = new java.util.ArrayList<>();
    private final java.util.List<PortraitArt> portraits = new java.util.ArrayList<>();
    private final java.util.List<Projectile> projectiles = new java.util.ArrayList<>();
    private final java.util.List<Prop> props = new java.util.ArrayList<>();
    private final java.util.List<Effect> effects = new java.util.ArrayList<>();
    private final java.util.List<Sound> sounds = new java.util.ArrayList<>();
    private HeavyShot heavyShot = HeavyShot.DEFAULTS;

    /**
     * What each hero the files describe is drawn as, in the order they name them.
     *
     * <p>A list because the roster is the files'. Empty when they name none, and then whoever
     * is playing is a coloured shape, as he was before there was a model.
     */
    public java.util.List<HeroLook> heroes() {
        var rules = attributeRules();
        return heroes.stream().map(hero -> hero.look(rules, animationSet(hero.animations()))).toList();
    }

    /** That hero's block, or {@link HeroLook#NONE} if no file describes such a one. */
    public HeroLook heroNamed(String templateName) {
        for (var hero : heroes) {
            if (hero.name().equals(templateName)) {
                return hero.look(attributeRules(), animationSet(hero.animations()));
            }
        }
        return HeroLook.NONE;
    }

    /** Every live portrait a unit frames for itself, by the creature it is the face of. */
    public java.util.List<PortraitArt> portraits() {
        return java.util.List.copyOf(portraits);
    }

    /** Every projectile the files describe, in the order they describe them. */
    public java.util.List<ArrowLook> projectiles() {
        return projectiles.stream().map(Projectile::look).toList();
    }

    /** How one is drawn, or a plain shape when no file describes such a thing. */
    public ArrowLook projectile(String template) {
        for (var projectile : projectiles) {
            if (projectile.name().equals(template)) {
                return projectile.look();
            }
        }
        return new ArrowLook(template, null, null, 1f, 90f, 0f, 0xFFFFFF, null, 0f);
    }

    /** The creature a drawn shot becomes — the same shaft, drawn bigger. */
    public String heavyArrowTemplate() {
        return heavyShot.template();
    }

    /**
     * Slower than an ordinary arrow, on purpose. It is the one shot the player
     * chose to spend, so it is the one worth watching cross the room.
     */
    public float heavyArrowSpeed() {
        return heavyShot.speed();
    }

    /** What a skill book reads beside its skills: the Combat block's numbers and the heavy shot's speed. */
    public SkillBook.Rules skillRules() {
        return new SkillBook.Rules(combat.arrowMuzzleOffset(), heavyArrowSpeed(), combat.summonTurnDegrees(),
                combat.summonTurns(), combat.auraPulseFrames(), combat.buffLifeFrames(), combat.auraMarkLooks(),
                combat.auraMarkTickFrames());
    }

    /** What a bow takes where its block says nothing: the Combat block's arrow. */
    public Bow.Defaults bowDefaults() {
        return new Bow.Defaults(combat.arrowTemplate(), combat.arrowSpeed(), combat.arrowMuzzleOffset());
    }

    /**
     * One prop by its template's name. One the files do not describe is one they never
     * scatter, which is what a weight of nothing says.
     */
    public PropKind prop(String template) {
        for (var prop : props) {
            if (prop.name().equals(template)) {
                return prop.kind();
            }
        }
        return new PropKind(template, 0);
    }

    /** What may be scattered through the rooms, in file order. */
    public java.util.List<PropKind> propKinds() {
        return props.stream().map(Prop::kind).toList();
    }

    /** Every effect the files describe, in the order they describe them. */
    public java.util.List<Effect> effects() {
        return java.util.List.copyOf(effects);
    }

    /**
     * Every layer an effect is drawn from, in the order written — which is also the order they
     * are drawn in, one over another.
     */
    public java.util.List<EffectLayerArt> effectLayers() {
        return effects.stream()
                .flatMap(effect -> effect.layers().stream()
                        .map(layer -> new EffectLayerArt(effect.name(), layer.name(), layer.fields())))
                .toList();
    }

    /** Every moment the game has a sound for. */
    public java.util.List<Sound> sounds() {
        return java.util.List.copyOf(sounds);
    }

    // ---- themes ----


    /** One kind of thing that stands about in a room, and how often it is drawn. */
    public record PropKind(String template, int weight) {
    }

    public int minPropsPerRoom() {
        return map.propsPerRoom().min();
    }

    public int maxPropsPerRoom() {
        return map.propsPerRoom().max();
    }

    /** Every theme the files describe, and which depth wears which. */
    public Themes themes() {
        return new Themes(map.themes(), map.whenExhausted(), themes);
    }

    /** The themes one floor of the descent mixes, or none — see {@link Biomes}. */
    public Biomes biomes() {
        var named = new java.util.ArrayList<Theme>();
        for (var name : map.biomes()) {
            themes.stream().filter(theme -> theme.name().equals(name)).findFirst().ifPresent(named::add);
        }
        return new Biomes(named, map.biomeCount(), map.minBiomeShare(), map.biomeSize(), map.climatePerDepth());
    }

    /**
     * How things look outside any theme, and so in every theme that does not dress them: the keep's key, one look
     * named once rather than a copy in every biome. See the ThemeMonster in {@code data/props/key.duke}.
     */
    public List<Theme.ThemeMonster> looks() {
        return List.copyOf(looks);
    }

    /** The boss's keep, as the map asks for it — see {@link ProceduralMap.Keep}. */
    public ProceduralMap.Keep keep() {
        return map.keep();
    }

    /** Everything that can be found on a floor, in file order. */
    public java.util.List<Loot> loot() {
        return java.util.List.copyOf(loot);
    }

    /** What is generated: the bases, affixes, uniques and potions, rolled, named and priced. See loot/Items. */
    public uz.dukeengine.dungeon.loot.Items items() {
        var made = items;
        if (made == null) {
            var named = new java.util.LinkedHashMap<String, String>();
            attributes.forEach(attribute -> named.put(attribute.name(), attribute.word()));
            made = new uz.dukeengine.dungeon.loot.Items(itemBases, affixes, uniques, potions, itemRules,
                    new uz.dukeengine.dungeon.loot.Items.Words(hud.attackWord(), hud.armourWord(), hud.healthWord(),
                            hud.manaWord(), lootDrops.healthRegenWord(), lootDrops.manaRegenWord(),
                            lootDrops.attackSpeedWord(), hud.lifestealWord(), named),
                    loot);
            items = made;
        }
        return made;
    }

    /** The animations called {@code name}; none when a unit links none. */
    public AnimationSet animationSet(String name) {
        for (var set : animationSets) {
            if (set.name().equals(name)) {
                return set;
            }
        }
        return AnimationSet.NONE;
    }

    /** A kind's look moving by the animations it links. */
    public MonsterLook lookOf(MonsterKind kind) {
        return animated(kind.look());
    }

    /** A look with the libraries and clips of the animations it links filled in. */
    public MonsterLook animated(MonsterLook look) {
        return look.in(animationSet(look.animations()));
    }

    /** The one being played, which is what the run spawns and the panel describes. */
    public HeroLook playedHeroLook() {
        return heroNamed(run.defaultHero());
    }

    /**
     * The portrait every selectable creature gets, or {@code null} if the files ask for none: the
     * one the {@code Hud} frames, named for nobody in particular.
     */
    public PortraitArt everyPortrait() {
        return hud.portrait() == null ? null : hud.portrait().named("");
    }

    /**
     * What an arrow is drawn as: one named mesh out of a model file, or nothing,
     * in which case it falls back to a coloured shape.
     *
     * @param part the name <em>inside</em> the file, which need not be a sensible
     *             one — see the Projectile blocks in {@code data/projectiles/}
     */
    public record ArrowLook(String name, String model, String part, float scale, float facing,
            float height, int tint, String effect, float effectOffset) {
        /**
         * A model is enough, and none at all is allowed: a fireball is drawn by its
         * effect and has no file anywhere. {@code part} is for a projectile that is
         * one mesh inside a larger file — which is how it had to be found while the
         * only arrow the game owned was the one on the hero's string, and is not
         * how a kit that ships an arrow hands it over.
         */
        public boolean hasModel() {
            return model != null;
        }

        public java.awt.Color awtTint() {
            return new java.awt.Color(tint);
        }
    }

    // ---- the layers an effect is drawn from ----

    /**
     * One layer of an effect, as the file wrote it — only the fields it said.
     *
     * <p>The fields and nothing else, because the defaults belong to the client: a
     * layer that does not mention its drag gets whatever the client gives a layer
     * that does not mention its drag, and there is one place that says what that
     * is instead of two that can disagree. Keyed by the client's own names, and
     * already checked to be the kind of value each one is, so a typo is caught
     * when the file is read rather than when the effect is drawn.
     *
     * @param effect the effect this is a layer of — an {@code Effect} name
     * @param name   what this layer is called, for whoever reads the file
     * @param fields what it said, by the client's name for each
     */
    public record EffectLayerArt(String effect, String name, java.util.Map<String, String> fields) {

        public EffectLayerArt {
            fields = java.util.Map.copyOf(fields);
        }
    }

    // ---- layout ----

    public int mapWidth() {
        return map.generation().mapWidth();
    }

    public int mapHeight() {
        return map.generation().mapHeight();
    }

    public int minRooms() {
        return map.generation().minRooms();
    }

    public int maxRooms() {
        return map.generation().maxRooms();
    }

    public int minRoomSize() {
        return map.generation().minRoomSize();
    }

    public int maxRoomSize() {
        return map.generation().maxRoomSize();
    }

    public int roomGap() {
        return map.generation().roomGap();
    }

    public int placementAttempts() {
        return map.generation().placementAttempts();
    }

    /** Corridor width in cells — wide enough for the largest creature to pass. */
    public int corridorWidth() {
        return map.generation().corridorWidth();
    }

    /** How far a new room may sit from the nearest already placed, in cells. */
    public int maxRoomSpacing() {
        return map.generation().maxRoomSpacing();
    }

    /** How far apart two storeys stand, in world units. */
    public float storeyHeight() {
        return world.levelHeight();
    }

    /** The World block as the engine reads it: its name, and how tall a storey stands. */
    public World world() {
        return world;
    }

    public int minSkeletonsPerRoom() {
        return map.generation().minSkeletonsPerRoom();
    }

    public int maxSkeletonsPerRoom() {
        return map.generation().maxSkeletonsPerRoom();
    }

    // ---- progression ----

    /** The progression rules, as one value the levelling code can be handed. */
    public Levelling levelling() {
        return progression.levelling();
    }

    /**
     * Every attribute the files describe, in their order, and what a point of each is worth — to
     * every hero alike.
     */
    public AttributeRules attributeRules() {
        return new AttributeRules(attributes, progression.damagePerPrimary().value());
    }

    /**
     * How each attribute is shown — its word and its picture — in the order the files list them,
     * which is the order a hero's attributes are held in.
     */
    public List<AttributeArt> attributeArt() {
        return attributes.stream()
                .map(attribute -> new AttributeArt(attribute.name(), attribute.shortName(), attribute.word(),
                        attribute.icon()))
                .toList();
    }

    // ---- monsters and depth ----

    /** Every kind the file describes, in file order. */
    public java.util.List<MonsterKind> monsters() {
        return java.util.List.copyOf(monsters);
    }

    /** Every skill in the file, whoever it belongs to. */
    public java.util.List<Skill> skills() {
        return java.util.List.copyOf(skills);
    }

    /**
     * The skills of one hero, in file order — which is the order a HUD lists
     * them, so writing Q W E R in the file is what puts them in that order on
     * screen. A template with none simply has none.
     */
    public java.util.List<Skill> skillsFor(String heroTemplate) {
        var his = new java.util.ArrayList<Skill>();
        for (var skill : bookFor(heroTemplate)) {
            if (skill.effect() != SkillEffect.STAGGER && skill.effect() != SkillEffect.DODGE) {
                his.add(skill);
            }
        }
        return java.util.List.copyOf(his);
    }

    /**
     * Everything his SkillBook holds: {@link #skillsFor}, his DODGE (pressed on Space, no slot either) and his STAGGER,
     * which is no slot of the four -- never bought
     * with a point, never drawn on his bar (HeroStatus hides it), held at its first rank from the start.
     */
    public java.util.List<Skill> bookFor(String heroTemplate) {
        var his = new java.util.ArrayList<Skill>();
        for (var skill : skills) {
            if (skill.heroTemplate().equals(heroTemplate)) {
                his.add(skill);
            }
        }
        return java.util.List.copyOf(his);
    }

    /** One kind by name, or {@code null} if the file never described it. */
    public MonsterKind monster(String name) {
        for (var kind : monsters) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The kinds that can fill a room at this depth: deep enough to have appeared,
     * and carrying a weight, which is how the file says "placed deliberately, not
     * scattered" — the boss has none.
     */
    public java.util.List<MonsterKind> roomFillersAt(int depth) {
        var available = new java.util.ArrayList<MonsterKind>();
        for (var kind : monsters) {
            if (kind.weight() > 0 && kind.minDepth() <= depth) {
                available.add(kind);
            }
        }
        return available;
    }

    /** What a room's count of monsters is multiplied by at this depth: who lives in a place, and how many, is its own. */
    public float monsterCountAt(int depth) {
        return scaled(map.descent().monsterCountPercentPerDepth(), depth);
    }

    /**
     * What a monster's health is multiplied by at this level -- and its blow, its skills' damage and a mending's heal
     * by {@link #damageAtLevel}, and what killing it is worth by {@link #experienceAtLevel}: alike for every monster and
     * boss, and a level-1 monster is its block exactly.
     */
    public float healthAtLevel(int level) {
        return scaled(map.descent().healthPercentPerLevel(), level);
    }

    public float damageAtLevel(int level) {
        return scaled(map.descent().damagePercentPerLevel(), level);
    }

    public float experienceAtLevel(int level) {
        return scaled(map.descent().experiencePercentPerLevel(), level);
    }

    /**
     * A monster's pool at this level, or its trickle in tenths of a point a second: {@code base} and
     * {@code ManaPercentPerLevel} of it a level past the first, in whole numbers, as everything mana is.
     */
    public int manaAtLevel(int base, int level) {
        return base + base * Math.max(0, level - 1) * map.descent().manaPercentPerLevel() / 100;
    }

    /**
     * Linear growth from the first depth or level: {@code 1 + (n - 1) * percent / 100}.
     *
     * <p>Computed from the number in one step rather than compounded, so the tenth
     * floor is the same whether you arrived by playing or by asking.
     */
    private static float scaled(int percentPerStep, int n) {
        return 1f + Math.max(0, n - 1) * percentPerStep / 100f;
    }

    // ---- a monster's level ----

    /** The highest level anything down here stands at, its boss included: {@code MaxMonsterLevel}. */
    public int maxMonsterLevel() {
        return map.descent().maxMonsterLevel();
    }

    /**
     * The level of the chamber before the boss's, in a place of this tier: {@code BeforeBossLevel} on the first, grown
     * by {@code TierGrowthPercent} a tier past it -- worked out in one step, never compounded tier by tier, so the
     * fourth is the same reached by playing or asked for -- rounded once, and never above the cap.
     *
     * <p>A tier is how hard a place is: today a floor's depth, which is what a stage's {@code Difficulty} already
     * means. The rule is handed the tier and knows nothing else of floors.
     */
    public int beforeBossLevel(int tier) {
        var descent = map.descent();
        double grown = descent.beforeBossLevel()
                * StrictMath.pow(1 + descent.tierGrowthPercent() / 100.0, Math.max(0, tier - 1));
        return (int) Math.min(descent.maxMonsterLevel(), Math.round(grown));
    }

    /**
     * The level at the way in: {@code WayInLevel} on the first tier, and on every later one where the tier before
     * closed -- so a descent never steps back down, as an open world's regions run 1-8, 8-13, 13-20.
     */
    public int wayInLevel(int tier) {
        return tier <= 1 ? Math.min(map.descent().wayInLevel(), map.descent().maxMonsterLevel())
                : beforeBossLevel(tier - 1);
    }

    /** The boss's: {@code BossLevelsAbove} over the chamber before its own, and never above the cap either. */
    public int bossLevel(int tier) {
        return Math.min(beforeBossLevel(tier) + map.descent().bossLevelsAbove(), map.descent().maxMonsterLevel());
    }

    /**
     * A monster's level {@code walked} steps along a way {@code way} steps long, from the way in to the middle of the
     * chamber before the boss's: the way in's level and its share of the climb to that chamber's -- the share never
     * above the whole of it, so everything past that chamber stands at its level -- rounded once. A monster no step
     * reaches, and a way of no steps, count as the whole way.
     */
    public int levelAlong(int tier, int walked, int way) {
        int in = wayInLevel(tier);
        int end = beforeBossLevel(tier);
        if (walked < 0 || way <= 0 || walked >= way) {
            return end;
        }
        return in + (int) Math.round((end - in) * (double) walked / way);
    }
}
