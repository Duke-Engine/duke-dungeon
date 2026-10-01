package uz.dukeengine.dungeon.content;

import uz.dukeengine.core.content.Sound;
import uz.dukeengine.core.content.AnimationSet;
import java.util.List;
import java.util.Map;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.client3d.OrderMark;
import uz.dukeengine.client3d.HitNumbers;
import uz.dukeengine.client3d.MenuStyle;
import uz.dukeengine.client3d.PanelLook;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.thing.ThingTemplateLoader;
import uz.dukeengine.dungeon.loot.Loot;
import uz.dukeengine.dungeon.loot.LootKind;
import uz.dukeengine.dungeon.skill.Skill;
import uz.dukeengine.dungeon.skill.SkillBook;
import uz.dukeengine.dungeon.skill.SkillEffect;
import uz.dukeengine.dungeon.level.Attribute;
import uz.dukeengine.dungeon.level.AttributeRules;
import uz.dukeengine.dungeon.level.Attributes;
import uz.dukeengine.dungeon.level.HeroAttributes;
import uz.dukeengine.dungeon.level.Levelling;
import uz.dukeengine.dungeon.map.ProceduralMap;
import uz.dukeengine.dungeon.world.Audio;
import uz.dukeengine.client3d.Camera;
import uz.dukeengine.dungeon.world.Combat;
import uz.dukeengine.client3d.Cursor;
import uz.dukeengine.client3d.EffectBudget;
import uz.dukeengine.dungeon.world.Fog;
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

    // ---- the world's own blocks, each the record its word names ----

    private Combat combat = Combat.DEFAULTS;
    private Run run = Run.DEFAULTS;
    private Progression progression = Progression.DEFAULTS;
    private LootDrops lootDrops = LootDrops.DEFAULTS;
    private Camera camera = Camera.DEFAULTS;
    private Hud hud = Hud.DEFAULTS;
    private uz.dukeengine.dungeon.world.Party party = uz.dukeengine.dungeon.world.Party.DEFAULTS;
    private UnitBar unitBar = UnitBar.DEFAULTS;
    private StatBlock statBlock = StatBlock.DEFAULTS;
    private SkillRing skillRing = SkillRing.DEFAULTS;
    private OrderMark orderMark = OrderMark.DEFAULTS;
    private PanelLook panelLook = PanelLook.DEFAULTS;
    private HitNumbers hitNumbers = HitNumbers.DEFAULTS;
    private MenuStyle menu = MenuStyle.DEFAULTS;
    private Sun sun = Sun.DEFAULTS;
    private Fog fog = Fog.DEFAULTS;
    private Tiles tiles = Tiles.DEFAULTS;
    private EffectBudget effectBudget = EffectBudget.DEFAULTS;
    private HitFeel hitFeel = HitFeel.DEFAULTS;
    private Audio audio = Audio.DEFAULTS;

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
                case LootItem item -> loot.add(item.loot());
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
                case UnitBar block -> unitBar = once(block, once);
                case StatBlock block -> statBlock = once(block, once);
                case SkillRing block -> skillRing = once(block, once);
                case OrderMark block -> orderMark = once(block, once);
                case PanelLook block -> panelLook = once(block, once);
                case HitNumbers block -> hitNumbers = once(block, once);
                case MenuStyle block -> menu = once(block, once);
                case Sun block -> sun = once(block, once);
                case Fog block -> fog = once(block, once);
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
                "BiomeSize is at least 8 cells: a sweep of climate smaller than a chamber is no region at all");
        require(run.respawnDelayFrames() >= 0, "the death pause cannot be negative");
        require(run.descendDelayFrames() >= 0, "the pause before descending cannot be negative");
        require(progression.maxLevel() >= Levelling.FIRST_LEVEL, "MaxLevel cannot be below the first level");
        require(progression.xpBase() > 0, "XpBase must be positive or no level is ever reached");
        require(progression.xpStep() >= 0, "XpStep cannot make later levels cheaper");
        require(progression.armourPercentPerLevel() >= 0, "a level cannot take armour away");
        validateAttributes();
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
            require(item.kind() != LootKind.KEY || item.weight() == 0,
                    "LootItem " + item.id() + " is a KEY, which is given and never found: its Weight is 0");
        }
        require(map.keep().sizes().isEmpty()
                        || loot.stream().anyMatch(item -> item.use() == uz.dukeengine.dungeon.loot.ItemUse.UNLOCK),
                "a Keep's gate opens only to a key, and no LootItem has Use = UNLOCK");
        for (var moment : moments) {
            require(!moment.effect().isBlank(), "Moment " + moment.name() + " plays no Effect");
            require(moment.scale() > 0f, "Moment " + moment.name() + " has to be drawn at some size");
        }
        for (var skill : skills) {
            // The panel is told which picture to draw down the status line, and
            // the line is split on those two characters.
            require(sayable(skill.icon()),
                    "a skill's Icon may not contain ',' or '|': " + skill.key());
            // A stun rides what a skillshot throws, and nothing else a skill does carries
            // one: written on any other, it would be a number nothing read.
            require(skill.stunFrames() == 0
                            || skill.stunFrames() > 0 && skill.effect() == SkillEffect.SKILLSHOT,
                    skill.heroTemplate() + "'s Skill " + skill.key() + " has StunFrames = " + skill.stunFrames()
                            + ": only a SKILLSHOT stuns, and for no fewer than no frames");
            // Passives are monsters' for now: a hero's slots, aims, rings and tips know nothing of a skill that is
            // not cast. Asked first, so that a hero's is refused for that and not for what else it has.
            require(!skill.effect().isPassive()
                            || heroes.stream().noneMatch(hero -> hero.name().equals(skill.heroTemplate())),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + ", never cast: only a monster may have one, for now -- a hero's bar has no place for it");
            // A passive is never cast, so what only a cast reads would be a number nothing read.
            require(!skill.effect().isPassive() || skill.damage() == 0f && skill.manaCost() == 0
                            && skill.windUpFrames() == 0 && !skill.hasProjectile() && !skill.hasLook(),
                    skill.heroTemplate() + "'s Skill " + skill.key() + " is a " + skill.effect()
                            + ", never cast: a Damage, ManaCost, WindUpFrames, Projectile or Look on it is read by"
                            + " nothing");
            switch (skill.effect()){
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
                case LIFESTEAL -> require(skill.boostPercent() >= 1 && skill.boostPercent() <= 100,
                        skill.heroTemplate() + "'s Skill " + skill.key() + "'s BoostPercent is the share of every"
                                + " blow it drinks, from 1 to 100");
            }
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
            if (item.kind() == LootKind.ATTRIBUTE) {
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
        return new Biomes(named, map.biomeSize(), map.climatePerDepth());
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
