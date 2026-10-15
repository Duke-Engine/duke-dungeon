package uz.dukeengine.dungeon;

import java.util.List;

import uz.dukeengine.client3d.Duke3D;
import uz.dukeengine.client3d.rpg.BagScreen;
import uz.dukeengine.client3d.EdgeScroll;
import uz.dukeengine.core.content.Effect;
import uz.dukeengine.client3d.Fog;
import uz.dukeengine.client3d.rpg.HeroLooks;
import uz.dukeengine.client3d.Hotkeys;
import uz.dukeengine.client3d.Shell;
import uz.dukeengine.client3d.Tileset;
import uz.dukeengine.client3d.Visuals;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.HeroLook;
import uz.dukeengine.dungeon.world.Theme;
import uz.dukeengine.dungeon.party.PartyOrders;
import uz.dukeengine.rpg.skill.CastSkill;
import uz.dukeengine.dungeon.stage.Stages;
import uz.dukeengine.dungeon.run.Floors;
import uz.dukeengine.dungeon.run.SaveGame;
import uz.dukeengine.dungeon.run.Saves;

/**
 * Opens the dungeon in the engine's 3D client.
 *
 * <p>No visuals are bound. Every creature falls back to a coloured primitive,
 * lit and cast into a world the camera looks across rather than straight down —
 * which is the whole difference between a diagram of a game and a game.
 *
 * <p>The front menu is this game's, not the client's. A dungeon has a run to
 * begin and a way out — alone, or with a party. The client's own LAN rows are
 * not used: they seat every player the game has, monsters' side included, and
 * start without asking anybody who he is. A party meets through the game's own
 * rows instead — see {@link PartyFront}.
 *
 * <p>Controls are the engine's own: left-click the hero to select him,
 * right-click the floor to walk or a skeleton to attack it.
 *
 * <p>Each launch draws a different dungeon. The seed is the one thing here the
 * clock touches — and it is outside the simulation, choosing <em>which</em>
 * deterministic dungeon to play rather than reaching into how one is built. From
 * that seed on, generation and the run loop are a pure, reproducible chain.
 */
public final class Main {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(Main.class.getName());

    private Main() {
    }

    /** One movement, from the file that holds it, under the name the game uses. */
    private static void animation(Visuals.UnitVisual unit, String assetPath, String clipName) {
        if (assetPath != null) {
            unit.animationFrom(assetPath, clipName);
        }
    }

    /**
     * One recipe for what a thing looks like, handed to the client: its knock and its glow
     * here, its layers one by one after — see {@link #layerOf}.
     */
    private static void effect(Visuals visuals, Effect look) {
        visuals.effect(look.name(), recipe -> {
            var glow = look.glow();
            if (glow != null) {
                recipe.glow(glow.parts(), new java.awt.Color(glow.colour()));
            }
            recipe.shake(look.shakeSeconds(), look.shakePower());
        });
    }

    /**
     * One layer, in the client's words: whatever the file said, laid over the client's own
     * defaults.
     *
     * <p>Only what was said, because the defaults are the client's and are written down once,
     * in {@code EffectLayer.Builder}. Renewing is said too, layer by layer: an AURA layer that
     * {@code Renews} is carried on to the new end when it is cast again on somebody it still
     * burns on, where one that does not is dropped, as a lasting AREA_DAMAGE — cast again at
     * each landing — must be. A look worn as a state is played again as the state is given
     * again: the stun's count starts again at each stun, a second haste starts the haste again,
     * and an aura is played on its bearer again at every beat. So every AURA layer such a look
     * has says it, and the file is refused where one does not (see {@code DungeonSettings});
     * the shipped auras' looks are each one MARK, which the client lays anew each beat, and
     * needs none.
     *
     * <p>Package-private so the game's own test can ask what a block in the file turns into on
     * screen.
     */
    static uz.dukeengine.client3d.EffectLayer layerOf(DungeonSettings.EffectLayerArt art) {
        var layer = uz.dukeengine.client3d.EffectLayer.builder();
        art.fields().forEach((field, value) -> {
            switch (field) {
                case "type" -> layer.type(value);
                case "texture" -> layer.texture(value);
                case "additive" -> layer.additive(Boolean.parseBoolean(value));
                case "count" -> layer.count(Integer.parseInt(value));
                case "colourStart" -> layer.colourStart(Integer.parseInt(value));
                case "colourEnd" -> layer.colourEnd(Integer.parseInt(value));
                case "lightColour" -> layer.lightColour(Integer.parseInt(value));
                case "direction" -> layer.direction(value);
                case "at" -> layer.at(value);
                case "measure" -> layer.measure(value);
                case "follows" -> layer.follows(Boolean.parseBoolean(value));
                case "renews" -> layer.renews(Boolean.parseBoolean(value));
                case "home" -> layer.home(Boolean.parseBoolean(value));
                default -> number(layer, field, Float.parseFloat(value));
            }
        });
        return layer.build();
    }

    private static void number(uz.dukeengine.client3d.EffectLayer.Builder layer, String field,
            float value) {
        switch (field) {
            case "rate" -> layer.rate(value);
            case "delay" -> layer.delay(value);
            case "seconds" -> layer.seconds(value);
            case "lifeMin" -> layer.lifeMin(value);
            case "lifeMax" -> layer.lifeMax(value);
            case "sizeStart" -> layer.sizeStart(value);
            case "sizeEnd" -> layer.sizeEnd(value);
            case "sizeEase" -> layer.sizeEase(value);
            case "sizeJitter" -> layer.sizeJitter(value);
            case "alphaStart" -> layer.alphaStart(value);
            case "alphaEnd" -> layer.alphaEnd(value);
            case "colourEase" -> layer.colourEase(value);
            case "fadeIn" -> layer.fadeIn(value);
            case "fadeOut" -> layer.fadeOut(value);
            case "speedMin" -> layer.speedMin(value);
            case "speedMax" -> layer.speedMax(value);
            case "spread" -> layer.spread(value);
            case "radius" -> layer.radius(value);
            case "height" -> layer.height(value);
            case "gravity" -> layer.gravity(value);
            case "drag" -> layer.drag(value);
            case "stretch" -> layer.stretch(value);
            case "spin" -> layer.spin(value);
            case "turn" -> layer.turn(value);
            case "turnJitter" -> layer.turnJitter(value);
            case "pulseRate" -> layer.pulseRate(value);
            case "pulseDepth" -> layer.pulseDepth(value);
            case "lightPower" -> layer.lightPower(value);
            case "lightRadius" -> layer.lightRadius(value);
            case "fall" -> layer.fall(value);
            case "cover" -> layer.cover(value);
            case "rise" -> layer.rise(value);
            case "riseEase" -> layer.riseEase(value);
            case "bounce" -> layer.bounce(value);
            case "homeDelay" -> layer.homeDelay(value);
            case "flip" -> layer.flip(value);
            default -> LOG.warning(() -> "an effect layer says " + field
                    + ", which the client does not draw -- ignored");
        }
    }

    /**
     * How long and how far each look's skill goes, handed to the client so a layer
     * that says neither is drawn exactly that long and exactly that wide.
     *
     * <p>From the SKILL, and nowhere else. The knight's guard used to say 4.0 in its
     * effect block and 120 frames in its skill block, and the meteor's warning said
     * 1.5 seconds beside a comment asking whoever changed WindUpFrames to remember
     * to change it too. Now there is one number and the picture follows it.
     *
     * <p>How long: a skill that lasts gives its duration; an aura, two of the beats its
     * bearer wears it at, so the one played at each beat takes over from the last; one
     * that is aimed and then lands gives its wind-up, which is how long the ground is
     * marked; one that slows what it caught gives the slow, which is how long they wear
     * the frost.
     * How far: its radius. And a projectile's effect is given its skill's numbers
     * too -- a meteor's falling mark takes the same wind-up to come down, and a
     * fireball's blast is as wide as the skill that threw it.
     *
     * <p>And the stars over a stunned head last as long as the longest stun any skill
     * gives: the look is the Combat block's, one for every stun, played on whoever
     * was stunned rather than where a skill went off. So does each mark under a
     * creature an aura reaches, from the same block: two of the beats its book lays
     * it at, as an aura's look is, so it rides the creature while an aura is lent to
     * it and is gone within two beats of the last lapsing.
     */
    static void measureLooks(uz.dukeengine.client3d.Visuals visuals, DungeonSettings settings) {
        float perSecond = uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
        var carriedBy = new java.util.HashMap<String, String>();
        for (var arrow : settings.projectiles()) {
            carriedBy.put(arrow.name(), arrow.effect());
        }
        for (var skill : settings.skills()) {
            var carried = skill.hasProjectile() ? carriedBy.get(skill.projectile()) : null;
            if (skill.hasLook()) {
                int frames = skill.durationFrames() > 0 ? skill.durationFrames()
                        : skill.effect().isAura() ? 2 * skill.tickFrames()
                        : skill.windUpFrames() > 0 ? skill.windUpFrames()
                        : skill.slowFrames();
                if (frames > 0) {
                    visuals.effectSeconds(skill.look(), frames / perSecond);
                }
                visuals.effectReach(skill.look(), skill.radius());
            }
            if (carried != null) {
                visuals.effectReach(carried, skill.radius());
                if (skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.METEOR
                        && skill.windUpFrames() > 0) {
                    visuals.effectSeconds(carried, skill.windUpFrames() / perSecond);
                }
            }
        }
        int longestStun = 0;
        for (var skill : settings.skills()) {
            longestStun = Math.max(longestStun, skill.stunFrames());
        }
        if (longestStun > 0 && !settings.combat().stunLook().isBlank()) {
            visuals.effectSeconds(settings.combat().stunLook(), longestStun / perSecond);
        }
        // A quest's target glows from one beat to the next, so each glow lasts two of them. See DungeonRun.glow.
        var dialogue = settings.story().dialogue();
        if (!dialogue.glowLook().isBlank()) {
            visuals.effectSeconds(dialogue.glowLook(), 2 * dialogue.glowFrames() / perSecond);
        }
        // A thing's light on the ground, likewise: each beam lasts two beats. See DungeonRun.beams.
        var itemRules = settings.items().rules();
        for (var beam : itemRules.beams()) {
            if (!beam.isBlank()) {
                visuals.effectSeconds(beam, 2 * itemRules.beamFrames() / perSecond);
            }
        }
        // And the key's and a quest's place's. See DungeonRun.beams.
        if (!settings.run().keyBeam().isBlank()) {
            visuals.effectSeconds(settings.run().keyBeam(), 2 * itemRules.beamFrames() / perSecond);
        }
        if (!settings.run().questBeam().isBlank()) {
            visuals.effectSeconds(settings.run().questBeam(), 2 * itemRules.beamFrames() / perSecond);
        }
        // The medallion appearing in the hand it is given to glows this long.
        var medallion = settings.medallion();
        if (!medallion.givenLook().isBlank()) {
            visuals.effectSeconds(medallion.givenLook(), medallion.givenLookFrames() / perSecond);
        }
        for (var mark : settings.combat().auraMarkLooks()) {
            visuals.effectSeconds(mark, 2 * settings.combat().auraMarkTickFrames() / perSecond);
        }
    }

    /** One portrait block, in the words the client keeps them in. */
    private static uz.dukeengine.client3d.rpg.PortraitLook portraitLook(
            uz.dukeengine.dungeon.content.PortraitArt art) {
        return new uz.dukeengine.client3d.rpg.PortraitLook(
                new uz.dukeengine.client3d.rpg.PortraitLook.Camera(art.head(), art.show(),
                        art.yaw(), art.pitch(), art.fov()),
                new uz.dukeengine.client3d.rpg.PortraitLook.Clips(art.calm(), art.fight(),
                        art.hurt(), art.dead(), art.levelUp()),
                art.hurtBelowPercent(), art.hurtSpeed());
    }

    /** What this creature has in its hand, if the file gave it anything. */
    private static void carry(Visuals.UnitVisual unit, uz.dukeengine.client3d.Held held) {
        if (held.isCarried()) {
            unit.holds(held.model(), held.bone(), held.scale())
                    .heldTurn(held.pitch(), held.yaw(), held.roll())
                    .heldAt(held.x(), held.y(), held.z());
        }
    }

    /** Everything he carries, in the order the file named it. */
    private static void carry(Visuals.UnitVisual unit,
            java.util.List<uz.dukeengine.client3d.Held> held) {
        for (var one : held) {
            carry(unit, one);
        }
    }

    /**
     * An arrow's look. Two creatures use it — his ordinary shot and the one Q
     * looses — differing only in the numbers, which is why it is one method.
     */
    private static void arrow(Visuals visuals, String template, DungeonSettings.ArrowLook look) {
        visuals.unit(template, unit -> {
            unit.colour(look.awtTint()); // the minimap dot, and the fallback shape
            unit.effect(look.effect()).effectAt(look.effectOffset());
            if (!look.hasModel()) {
                // A fireball has no file anywhere: its effect is its body, and the
                // height is still wanted, because a shot travels at bow height
                // whether or not there is a mesh on it.
                unit.scale(0.28f).yOffset(look.height());
                return;
            }
            unit.modelPart(look.model(), look.part())
                    .scale(look.scale())
                    .facing(look.facing())
                    // Drawing only: the simulation is flat, so height is not a
                    // position but the line the shot is drawn along.
                    .yOffset(look.height())
                    // Not decoration: the tint is what gets it a material this
                    // client can light. Without one it keeps the loader's PBR and
                    // the arrow is a black splinter.
                    .tint(look.awtTint());
        });
    }

    /**
     * The panel's painted edges, turned from the file's words into the client's.
     *
     * <p>Every name in the file has to be one the panel answers to, and the panel
     * is the only thing that knows the list — so this is where the two meet, in
     * the same place and for the same reason the sound channels do below.
     *
     * <p>An unknown name costs that one piece and nothing else. The whole point of
     * a skin is that it is a coat of paint: a misspelt part is drawn the way it
     * was drawn before there was any paint, which is a working panel, and refusing
     * to start the game over a decoration would be the wrong trade.
     */
    private static uz.dukeengine.client3d.PanelSkin panelSkin(DungeonSettings settings) {
        var pieces = new java.util.LinkedHashMap<String, uz.dukeengine.client3d.PanelSkin.Piece>();
        var known = java.util.Set.of(uz.dukeengine.client3d.PanelSkin.MINIMAP,
                uz.dukeengine.client3d.PanelSkin.PORTRAIT, uz.dukeengine.client3d.PanelSkin.SLOT,
                uz.dukeengine.client3d.PanelSkin.GAUGE, uz.dukeengine.client3d.PanelSkin.CHIP,
                uz.dukeengine.client3d.PanelSkin.BUTTON, uz.dukeengine.client3d.PanelSkin.ITEM,
                uz.dukeengine.client3d.PanelSkin.DIVIDER, uz.dukeengine.client3d.PanelSkin.BANNER,
                uz.dukeengine.client3d.PanelSkin.BANNER_WON, uz.dukeengine.client3d.PanelSkin.BANNER_LOST);
        for (var piece : settings.skins()) {
            if (!known.contains(piece.name())) {
                LOG.warning(() -> "Skin names no part of the panel: " + piece.name()
                        + " — that part is drawn as it was; known parts are " + known);
                continue;
            }
            pieces.put(piece.name(), new uz.dukeengine.client3d.PanelSkin.Piece(
                    piece.texture(), piece.inset(), piece.scale(), piece.awtTint()));
        }
        return new uz.dukeengine.client3d.PanelSkin(pieces);
    }

    /**
     * How a creature's bar is drawn, handed over whole.
     *
     * <p>Package-private rather than private so the game's own test can ask for
     * exactly what the client will be given. The table is the thing worth
     * checking -- see {@code DungeonUnitBarTest} -- and a test that rebuilt it
     * from the same accessors would be checking its own copy.
     */
    static uz.dukeengine.client3d.UnitBarLook unitBars(DungeonSettings settings) {
        var steps = new java.util.ArrayList<uz.dukeengine.client3d.UnitBarLook.Step>();
        for (var rung : settings.unitBar().segments()) {
            steps.add(new uz.dukeengine.client3d.UnitBarLook.Step(rung.upTo(), rung.value()));
        }
        return new uz.dukeengine.client3d.UnitBarLook(steps,
                settings.unitBar().shortestAt(), settings.unitBar().longestAt(),
                settings.unitBar().shortest(), settings.unitBar().longest(),
                settings.unitBar().height(), settings.unitBar().manaHeight(),
                settings.unitBar().gap(), settings.unitBar().lift(),
                settings.unitBar().ring(), settings.unitBar().ringEdge(),
                settings.unitBar().ringGap(), settings.unitBar().arc(),
                settings.unitBar().enemy(), settings.unitBar().friend(), settings.unitBar().mana(),
                settings.unitBar().trough(), settings.unitBar().tick(), settings.unitBar().ringFace(),
                settings.unitBar().ringRim(), settings.unitBar().bossRim(),
                settings.unitBar().lettering(),
                settings.unitBar().nameSize(), settings.unitBar().bossNameSize(),
                settings.unitBar().countSize(), settings.unitBar().levelSize())
                // A monster's medallion shows its own level, held in a word on it; see Spawner.scale.
                .withLevelWord(settings.unitBar().levelWord());
    }

    /**
     * Every moment the game has a sound for, handed to the client at launch.
     *
     * <p>Nothing here is a decision. Which channel, how loud, how far apart and
     * which files all come out of {@code data/sounds/}, so a new sound is a block
     * there and this method does not change. An unknown channel name is
     * read as an effect rather than refused: a typo should cost the right knob,
     * not the sound.
     */
    private static uz.dukeengine.client3d.SoundBank soundsOf(DungeonSettings settings) {
        var bank = uz.dukeengine.client3d.SoundBank.create();
        bank.voiceGap(settings.audio().voiceGapSeconds());
        for (var cue : settings.sounds()) {
            bank.cue(cue.name(), channelOf(cue.channel()), cue.positional(), cue.gain(),
                    cue.gapSeconds(), cue.files(), cue.label());
        }
        return bank.build();
    }

    static uz.dukeengine.client3d.SoundBank.Channel channelOf(String named) {
        for (var channel : uz.dukeengine.client3d.SoundBank.Channel.values()) {
            if (channel.name().equalsIgnoreCase(named)) {
                return channel;
            }
        }
        return uz.dukeengine.client3d.SoundBank.Channel.EFFECTS;
    }

    /**
     * Every way a floor can look, handed to the client at launch.
     *
     * <p>One registered look per theme <em>and</em> variation, because a variation
     * changes the kit and the client only ever holds finished looks. Which of them
     * a floor wears is the run's to say, floor by floor, down the status channel
     * — see {@code DungeonRun} and {@code HeroStatus}.
     *
     * <p>Nothing here is a decision. Every number and every path comes out of
     * {@code data/world/themes/}, so a fourth theme is a file and a folder of
     * models, and this method does not change.
     */
    private static void themes(Visuals visuals, DungeonSettings settings) {
        for (var theme : settings.themes().all()) {
            for (var tone : theme.tones()) {
                visuals.theme(theme.name() + "," + tone.name(), look -> {
                    look.tiles(Tileset.create()
                            .floor(tone.floor())
                            .wall(tone.allWalls().toArray(String[]::new))
                            .corner(tone.corner())
                            .stairs(theme.stairs())
                            .tileSize(theme.tileSize())
                            .wallTileSize(theme.wallTileSize())
                            .wallHeight(theme.wallHeight())
                            .wallLift(theme.wallLift())
                            .wallShift(theme.wallShift())
                            .ownMaterials(theme.ownMaterials())
                            .wallFillsRock(theme.standing().fillsRock())
                            .wallClump(theme.standing().clump())
                            .wallSpread(theme.standing().spread())
                            .wallVariety(theme.standing().variety())
                            .rockFace(theme.rockFace())
                            .capTint(theme.capTint())
                            .storeyShade(theme.storeyShadePercent() / 100f)
                            .tint(tone.tint()));
                    look.fogTint(theme.fogTint());
                    for (var themed : theme.monsters()) {
                        look.unit(themed.name(), dressed(themed, settings));
                    }
                });
            }
        }
    }

    /**
     * What one creature is drawn as, from its ThemeMonster block — in a theme, or outside any — described from
     * nothing.
     */
    private static java.util.function.Consumer<Visuals.UnitVisual> dressed(Theme.ThemeMonster themed,
            DungeonSettings settings) {
        var art = settings.animated(themed.look());
        return unit -> {
            unit.colour(art.awtTint());
            if (!art.hasModel()) {
                return;
            }
            unit.model(art.model())
                    .texture(art.texture())
                    .tint(art.awtTint())
                    .scale(art.modelScale())
                    .facing(art.facing())
                    .idle(art.idle())
                    .walk(art.walk())
                    .attack(art.attack())
                    .hurt(art.hurt())
                    .effect(art.effect())
                    .die(art.death());
            carry(unit, art.held());
            if (themed.playOnce() != null) {
                // Once, from its first frame, and held on its last: a gate swinging open as it appears.
                unit.clip(java.util.Set.of(), themed.playOnce(), Visuals.ClipMode.ONCE, Visuals.ClipStart.FIRST, null);
            }
            // Borrowed only when the file says so. A themed creature usually comes
            // with a model of its own, and a model of its own carries its own
            // clips -- copying them onto it from a second copy of the same file
            // rebinds the tracks to the wrong skeleton and the thing collapses.
            // Linking animations here is for a theme that re-skins a creature with
            // another model from the same kit.
            for (var library : art.libraries()) {
                unit.animationsFrom(library);
            }
        };
    }

    /** The blend, each surface's look, the cliffs and the quarters a painted map is drawn with. */
    static void paintedGround(Visuals visuals, uz.dukeengine.dungeon.world.PaintedGround ground) {
        if (ground.blend()) {
            visuals.paintBlend(new Visuals.PaintBlend(ground.noise(), ground.raggedness(), ground.sharpness(),
                    ground.noiseCoverage(), ground.variation(), ground.variationCoverage()));
        }
        var surfaces = new java.util.LinkedHashSet<>(ground.tints().keySet());
        surfaces.addAll(ground.variations().keySet());
        for (var surface : surfaces) {
            visuals.surface(surface, new Visuals.SurfaceLook(ground.tints().getOrDefault(surface, 0xFFFFFF),
                    ground.variations().getOrDefault(surface, 0f)));
        }
        if (ground.cliffs() != null) {
            visuals.paintCliffs(new Visuals.Cliffs(ground.cliffs(), ground.cliffSlope(), ground.cliffCoverage()));
        }
        visuals.paintInQuarters(ground.quarters());
        if (ground.water() != null) {
            // Standing water's picture drifting slowly, a copy every 15 cells; a river the same picture run down it.
            visuals.water(new Visuals.WaterLook(ground.water(), 150f, 0.01f, 0.006f, null, 0f, 0f, 0f,
                    ground.waterTint(), 3f, ground.water(), ground.riverFlow(), null));
        }
        visuals.minimapSpan(ground.minimapCells());
        // The edge of the ground built round the camera, faded away -- on a wide world only, the one predicate its
        // sleep and its sectors answer to.
        visuals.haze(ground.hazeFrom(), ground.hazeTo(), uz.dukeengine.dungeon.stage.StagePaint::isWide);
        // And it begins seen: its ground, cliffs and scenery remembered from the first frame, its creatures only where
        // the hero's eyes reach (E38). Every floor of the descent and every small map begins black.
        visuals.fogStartsExplored(uz.dukeengine.dungeon.stage.StagePaint::isWide);
    }

    public static void main(String[] args) {
        // Which map the game opens on is settled before its data is read, because a map brings blocks of its
        // own with it — see MapPackage — and they are part of the settings everything else is built from.
        var wanted = Stages.chosen(args);
        var opening = wanted == null ? null : Stages.found(wanted);
        var settings = DungeonSettings.of(opening);
        // Modern (WASD) or classic (the mouse): the player's own choice, else the file's -- see Controls.
        settings.useScheme(settings.controls().remembered());
        // The match the window opens with, and after Quit to the main menu a fresh one: a match is played once.
        var current = new java.util.concurrent.atomic.AtomicReference<>(chosenGame(opening, settings));
        var session = current.get();
        // Held rather than passed straight in: the question below reaches back
        // into it, because whose eyes open the map is part of who you chose.
        var visuals = looks(settings);
        // Asked of each map as it is laid: a painted one is built round the camera, a kit floor whole.
        visuals.streamGround(settings.paintedGround().streamCells(),
                map -> map instanceof uz.dukeengine.core.map.Painted painted
                        && painted.palette() != null && !painted.palette().isEmpty());
        // Both held rather than passed straight in, because the question below
        // reaches back into both: whose eyes open the map, what his four keys ask
        // the player to point at, and how far each of them reaches are all part of
        // who you chose, and none of them is known until he is chosen.
        var keys = controls(settings);
        // Held too: a party is a match of its own, built once its players have met, and
        // it is the client that is asked to play it — see PartyFront.
        // A player with no settings file yet starts in a 1280 x 720 window, not filling his screen (playtest of 1.0.41);
        // what he chose, once he has, is the engine's settings.properties and wins over this.
        var duke = Duke3D.of(session.game(), visuals).window(1280, 720).hotkeys(keys).musicByGame(true);
        // His bag, the engine's RPG part in the game's words: what he carries, and a hand to take a thing
        // out of it and put it down. Told of every match it is drawn over -- this one, and a party's that replaces it.
        var bag = Bag.on(settings, duke);
        Bag.show(bag, session, settings);
        // And over it, the box a conversation is said in, which hands the bag whatever it does not take.
        // Under it all, the open world's hour and weather laid over the world -- see Atmosphere.
        var talk = new DialogueScreen(new Atmosphere(bag, duke, settings, wordArg(args, "weather")), bag, settings);
        // And over that, the player's own screens -- the sheet, the quest log, the map -- each on its key, and the map
        // drawn from the engine's picture of the world as he knows it, asked for every map.
        var screens = new PlayerScreens(talk, settings);
        // And over them all, what it sounds like from where he stands -- the music for the place, the loop under it, the
        // pages and coins of his own screens. See Soundscape.
        // And over everything, the splash and the load screen: the menu comes up only once the scene behind it is laid
        // and running, and every match loads under the same bar. The scene is held back through the splash so the
        // splash plays smooth and the bar counts the whole of it. See LaunchScreen.
        var launch = new LaunchScreen(new Soundscape(screens, duke, settings), screens, settings.splash(),
                settings.mainMenu(), () -> duke.holdBackdrop(false), duke::sound);
        duke.canvas(launch).input(launch).onLoading(launch::loaded).onBackdropLoading(launch::loaded);
        // What the dodge asks before it rolls him: whether a screen over the world holds the keys.
        over = launch;
        // The mouse of the scheme: modern's left button fights, its right one works (E78).
        duke.mouseScheme(mouseOf(settings));
        duke.holdBackdrop(true);
        screensOn(keys, screens, settings);
        duke.worldPicture(settings.screens().mostTexels(), screens.map()::picture);
        // The run saves itself as each floor is entered, as the game is left by any door, and into a slot from the
        // pause menu -- see docs/plan/2026-10-08-saving.md.
        var saves = Saves.ofThePlayer();
        session.run().onCheckpoint(save -> write(saves, Saves.AUTOSAVE, save));
        session.run().onSaveInto((slot, save) -> write(saves, slot, save)); // an act's end, for the next act
        session.run().onTheEnd(duke::frontEnd); // the story's last MENU: the credits rolled, back to the front menu
        session.run().onMusic(duke::music);
        // The front menu's own rows -- continue, a new game (with whom, how, where, who), load, the credits -- then
        // Settings and Quit. Nothing opens until one is walked. See frontRows and whoGoesIn.
        var credits = FrontMenu.credits(settings, Content.read("CREDITS.md"));
        java.util.function.Function<Dungeon.Session, List<Shell.Option>> rows = match -> frontRows(settings,
                loadable(saves.list(), settings), him -> displayNameOf(match, him),
                saved -> {
                    heroChosen(match, settings, visuals, keys, saved.game().hero());
                    match.run().resume(saved.game(), Floors.resumed(saved.game(), settings));
                },
                () -> duke.startMatch(match.game()),
                whoGoesIn(match, settings, visuals, keys, duke, bag), credits);
        var shell = FrontMenu.shell(settings, rows.apply(session),
                List.of(saveRow(settings, slot -> saveTo(current.get(), saves, slot))));
        duke.onLeaving(() -> {
            saveTo(current.get(), saves, Saves.AUTOSAVE);
            // Back at the front menu, the next game is a new match, its rows read afresh (a save just made is there
            // to continue). Built here, before the menu is shown -- about 60 ms, also spent when the window closes.
            var next = chosenGame(opening, settings);
            next.run().onCheckpoint(save -> write(saves, Saves.AUTOSAVE, save));
            next.run().onSaveInto((slot, save) -> write(saves, slot, save));
            next.run().onTheEnd(duke::frontEnd);
            next.run().onMusic(duke::music);
            Bag.show(bag, next, settings);
            current.set(next);
            shell.frontRows(FrontMenu.startingFresh(rows.apply(next), () -> duke.startMatch(next.game())));
        });
        // The live scene behind the front menu: the kingdom's soldiers holding a clearing against the skeletons.
        duke.backdrop(() -> MenuScene.of(settings).game());
        duke.shell(shell);
        for (var act : settings.campaigns()) {
            int joint = actArg(args, act.name());
            if (joint > 0) {
                // The owner's testing: straight into joint N of an act, with what the joints before it would have
                // given (an act that follows another, from that act's defaults: --act2=N does not read act1.duke).
                heroChosen(session, settings, visuals, keys, act.hero());
                session.run().skipTo(act, joint, null);
                duke.startMatch(session.game());
                break;
            }
        }
        // The owner's testing: --hour=H (1 to 24) opens the open world at that hour -- after an --actN=, whose joint
        // begins in the morning, or on the map --map= opens. A game begun from the menu begins in the morning.
        int hour = actArg(args, "hour");
        if (hour > 0) {
            session.run().startAtHour(hour);
        }
        // The floor's key and every quest's target on the minimap too, held to its edge when off it (E61).
        duke.minimapMarks(() -> MissionScreen.minimapMarks(current.get(), settings));
        duke.launch();
    }

    /**
     * Save the solo game as it is this moment into {@code slot}: whether anything was written. Nothing is for a game
     * not started — a party's match is another game, and this one never starts beside it — nor while the run is over
     * (see {@code DungeonRun.saveNow}). Called with the simulation paused.
     */
    static boolean saveTo(Dungeon.Session session, Saves saves, String slot) {
        var save = session.game().getLogic() == null ? null : session.run().saveNow();
        return save != null && write(saves, slot, save);
    }

    private static boolean write(Saves saves, String slot, SaveGame save) {
        try {
            saves.write(slot, save);
            return true;
        } catch (java.io.IOException e) {
            java.util.logging.Logger.getLogger(Main.class.getName())
                    .warning(slot + " could not be written: " + e);
            return false;
        }
    }

    /** Saqlash on the pause menu: the three manual slots, each saving into itself and going back to the menu. */
    static Shell.Option saveRow(DungeonSettings settings, java.util.function.Consumer<String> saveInto) {
        var words = settings.saveMenu();
        var slots = new java.util.ArrayList<Shell.Option>();
        for (var slot : Saves.SLOTS.subList(1, Saves.SLOTS.size())) {
            slots.add(new Shell.Option(String.format(words.slotWord(), slot.replaceAll("\\D", "")), "",
                    () -> saveInto.accept(slot), null));
        }
        return new Shell.Option(words.saveWord(), words.saveBlurb(), null,
                new Shell.Question(words.saveTitle(), words.hint(), slots));
    }

    /** The saves this build can play: a hero it has, and floors it can lay — the rest are left out, and said so. */
    static List<Saves.Saved> loadable(List<Saves.Saved> saves, DungeonSettings settings) {
        var heroes = settings.heroes().stream().map(hero -> hero.name()).toList();
        var log = java.util.logging.Logger.getLogger(Main.class.getName());
        var playable = new java.util.ArrayList<Saves.Saved>();
        for (var saved : saves) {
            if (!heroes.contains(saved.game().hero())) {
                log.warning(saved.slot() + " cannot be loaded: no hero is called " + saved.game().hero());
                continue;
            }
            try {
                Floors.resumed(saved.game(), settings);
                playable.add(saved);
            } catch (SaveGame.Refused e) {
                log.warning(saved.slot() + " cannot be loaded: " + e.getMessage());
            }
        }
        return playable;
    }

    /**
     * The game's rows on the front menu: continue the newest save (only with one), a new game down the path the game
     * always had — {@code fresh} — load one of the saves (only with some), and the credits.
     *
     * @param saves newest first, as {@link Saves#list} reads them
     * @param named what the player calls a hero, by his template
     * @param load  picks up a save, for the match that is then started
     * @param play  starts that match: Continue has no question after it, so it starts its own
     */
    static List<Shell.Option> frontRows(DungeonSettings settings, List<Saves.Saved> saves,
            java.util.function.UnaryOperator<String> named, java.util.function.Consumer<Saves.Saved> load,
            Runnable play, Shell.Question fresh, Shell.Option credits) {
        var words = settings.saveMenu();
        var front = new java.util.ArrayList<Shell.Option>();
        if (!saves.isEmpty()) {
            var newest = saves.getFirst();
            front.add(new Shell.Option(words.continueWord(), about(settings, newest, named), () -> {
                load.accept(newest);
                play.run();
            }, null));
        }
        front.add(new Shell.Option(words.newWord(), words.newBlurb(), null, fresh));
        if (!saves.isEmpty()) {
            var rows = new java.util.ArrayList<Shell.Option>();
            for (var saved : saves) {
                var slot = saved.slot().equals(Saves.AUTOSAVE) ? words.autosaveWord()
                        : String.format(words.slotWord(), saved.slot().replaceAll("\\D", ""));
                rows.add(new Shell.Option(slot, about(settings, saved, named), () -> load.accept(saved), null));
            }
            front.add(new Shell.Option(words.loadWord(), words.loadBlurb(), null,
                    new Shell.Question(words.loadTitle(), words.hint(), rows)));
        }
        front.add(credits);
        return List.copyOf(front);
    }

    /** The line under a save: its hero, his level and the floor. */
    private static String about(DungeonSettings settings, Saves.Saved saved,
            java.util.function.UnaryOperator<String> named) {
        var game = saved.game();
        return String.format(settings.saveMenu().aboutSave(), named.apply(game.hero()),
                settings.levelling().levelFor(game.experience()), game.depth());
    }

    /**
     * Who goes in: alone, down the path this game always had, or as a party — hosting one on this machine, or
     * joining one on another. A build whose parties are one hero asks nothing new.
     *
     * <p>Every row lets go of a party still being waited for: whatever the player takes next, it is not the
     * waiting room he walked away from.
     */
    static uz.dukeengine.client3d.Shell.Question whoGoesIn(Dungeon.Session session, DungeonSettings settings,
            Visuals visuals, Hotkeys keys, Duke3D duke, BagScreen bag) {
        var alone = howToPlay(session, settings, visuals, keys);
        var party = settings.party();
        if (party.maxPlayers() < 2) {
            return alone;
        }
        var front = new PartyFront(session, settings, visuals, keys, duke, bag);
        return new uz.dukeengine.client3d.Shell.Question(party.whoWord(), party.whoHint(), List.of(
                new uz.dukeengine.client3d.Shell.Option(party.aloneWord(), party.aloneBlurb(), front::cancel, alone),
                new uz.dukeengine.client3d.Shell.Option(party.hostWord(), party.hostBlurb(), front::cancel,
                        front.host()),
                new uz.dukeengine.client3d.Shell.Option(party.joinWord(), party.joinBlurb(), front::cancel,
                        front.join())));
    }

    /**
     * Point the four keys at what THIS hero's skills ask the player for.
     *
     * <p>One hero's four, never the file's twelve. A key belongs to a SLOT rather
     * than to a skill — all three of them cast on Q, W, E and R — so binding every
     * skill in the file would leave whichever hero was read last deciding what Q
     * asks for. That is not a small wrongness: the rogue's Q wants a creature, and the
     * knight's and the mage's want a direction.
     *
     * <p><b>Called again every time a hero is chosen</b>, and that is the whole
     * point of it being a method. Settled once at startup it was settled from
     * {@code DefaultHero}, so picking anybody else got you his skills with the
     * default hero's aims — and a skill handed the wrong sort of target does not
     * misfire, it refuses: {@code SkillBook} declines a fireball with no direction
     * and leaves the cooldown unspent, which from a chair is a key that does
     * nothing whatever.
     *
     * <p>Rebinding a letter replaces what it asks for and not whether the game has
     * claimed it, so the client's own controls are untouched by this — the letters
     * were claimed at startup and every hero in the file uses the same four. A
     * hero who wanted a fifth letter would need that letter claimed up front;
     * {@code HeroChoiceTest} holds the four still so the day that changes is a
     * failing test rather than a dead key.
     */
    static void aimsFor(Hotkeys keys, DungeonSettings settings, String hero) {
        for (var skill : settings.skillsFor(hero)) {
            // The skill's own key names it in the cast; the letter pressed is the scheme's (modern: W's on E).
            char key = skill.key();
            char letter = settings.letterOf(key);
            keys.describe(letter, skill.name()); // its name on the Settings screen's list of keys (E46)
            if (skill.effect().isPassive()) {
                // Never cast: its key does nothing -- no aim, no order, no refusal (the panel takes no click on its
                // slot since E65). Bound to nothing rather than left alone, so another hero's skill on the same
                // letter does not stay behind.
                keys.on(letter, game -> { });
                continue;
            }
            switch (skill.effect().aim()) {
                case UNIT -> keys.onUnit(letter, (game, id) -> game.postCommand(PartyOrders.of(new CastSkill(
                        game.getLocalPlayerIndex(), key, new ObjectId(id), null))));
                case GROUND -> keys.onGround(letter, (game, spot) -> game.postCommand(PartyOrders.of(
                        new CastSkill(game.getLocalPlayerIndex(), key, null, spot))));
                // The shield charge is aimed down a lane, as the wave is: any spot says which way, stone included
                // (Dungeon.toTheEndOfItsLane puts it out at his whole Distance).
                case OPEN_GROUND -> {
                    if (skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.SKEWER) {
                        keys.onGround(letter, (game, spot) -> game.postCommand(PartyOrders.of(
                                new CastSkill(game.getLocalPlayerIndex(), key, null, spot))));
                    } else {
                        keys.onOpenGround(letter, (game, spot) -> game.postCommand(PartyOrders.of(
                                new CastSkill(game.getLocalPlayerIndex(), key, null, spot))));
                    }
                }
                case SELF -> keys.on(letter, game -> game.postCommand(PartyOrders.of(
                        new CastSkill(game.getLocalPlayerIndex(), key))));
            }
        }
    }

    /**
     * And draw HIS four rings, for the same reason and with the same lifetime.
     *
     * <p>A ring is keyed by the letter too, so the rogue's Q and the mage's Q are
     * two quite different circles — one round a creature, one down a lane. Left at
     * the file's hero the mage would have been shown where his fireball could
     * reach in the shape of somebody else's skill.
     */
    static void ringsFor(Visuals visuals, DungeonSettings settings, String hero) {
        for (var skill : settings.skillsFor(hero)) {
            // Keyed by the letter pressed, which arms it: the scheme's, not always the skill's own.
            var ring = rangeOf(skill, settings.skillRing().selfRadius());
            visuals.skillRange(new uz.dukeengine.client3d.SkillRange(settings.letterOf(skill.key()), ring.shape(),
                    ring.reach(), ring.area(), ring.width()));
        }
    }

    /** The letter the attack order is on; see {@link #orders}. */
    static final char ATTACK_KEY = 'A';

    /**
     * And the fifth ring: how far his ordinary attack reaches.
     *
     * <p>The question a player is actually asking when he reaches for the attack
     * key — "from where?" — and the one number on the bar he has never been shown.
     * A knight reaches eleven and a rogue sixty, which is most of what playing one
     * rather than the other IS, and neither of them was ever drawn.
     *
     * <p><b>Not a fence.</b> Every other ring in the game is the edge of what a
     * skill can do and a click past it is pulled back to the edge; this one is a
     * statement, and the same key sends him to fight his way across the whole
     * floor. The client is told as much — see {@code DukeRtsApp.aimArmedKey}.
     *
     * <p>Read off his own template rather than named here, for the reason
     * {@code HeroBrain.reachOfHisWeapon} gives: two copies of one number drift the
     * first time anybody re-tunes him, and a ring that lies about his reach is
     * worse than no ring.
     */
    static void attackRingFor(Visuals visuals, uz.dukeengine.game.DukeGame game, String hero) {
        float reach = reachOf(game, hero);
        if (reach > 0f) {
            visuals.skillRange(new uz.dukeengine.client3d.SkillRange(ATTACK_KEY,
                    uz.dukeengine.client3d.SkillRange.Shape.AT_A_CREATURE, reach, 0f));
        }
    }

    /** How far that template's weapon reaches, or 0 if it carries none. */
    static float reachOf(uz.dukeengine.game.DukeGame game, String hero) {
        // Null until the game is started, which the hero menu may well be drawn
        // over. No reach is no ring, which is the right answer for a run that has
        // not begun.
        var logic = game.getLogic();
        var template = logic == null ? null : logic.findTemplate(hero);
        if (template == null) {
            return 0f;
        }
        float reach = 0f;
        for (var module : template.modules()) {
            if (module instanceof uz.dukeengine.combat.module.WeaponUpdate.Data weapon) {
                reach = Math.max(reach, weapon.attackRange());
            }
        }
        return reach;
    }

    /**
     * The endless dungeon, or the stage somebody asked for.
     *
     * <p>A broken stage stops here, loudly, with the list of what is wrong with
     * it. It does not fall back to the endless dungeon: a player who asked for a
     * stage and silently got a random floor instead would have no way of knowing
     * anything had gone wrong, and an author editing one would think his last
     * change had worked.
     */
    private static Dungeon.Session chosenGame(uz.dukeengine.core.map.MapPackage map, DungeonSettings settings) {
        if (map == null) {
            // The seed is the one thing the clock touches, and it is outside the
            // simulation: it chooses WHICH deterministic dungeon to play.
            var session = Dungeon.newSession(System.nanoTime(), settings);
            var under = settings.mainMenu().subtitle();
            if (under != null && !under.isBlank()) {
                session.game().subtitle(under); // the line under the title, in the game's words
            }
            return session;
        }
        return Dungeon.newStageSession(Stages.load(map, settings), settings);
    }

    /**
     * Who the player is asked to be, before anything opens.
     *
     * <p>The roster is the file's — one entry per {@code Hero} block — so a
     * third hero appears on this screen by existing, and nothing here is edited.
     * The words under each name are his own {@code Title}.
     *
     * <p><b>Taking one starts the run, and nothing else does.</b> The world was
     * built before the window opened, with whoever {@code DefaultHero} names,
     * because a client cannot show a menu over a game that does not exist yet.
     * That hero is never seen: the choice lays the first floor again with whoever
     * was picked, which is the same road a death takes and for the same reason —
     * nothing the unchosen hero had was the chosen one's.
     *
     * <p>So a stage does not say which hero plays it, and cannot. The menu is the
     * only thing that decides, which is the whole of the rule.
     */
    static uz.dukeengine.client3d.Shell.Question whoToPlay(Dungeon.Session session,
            DungeonSettings settings, Visuals visuals, Hotkeys keys) {
        return whoToPlay(session, settings, visuals, keys, him -> session.run().startWith(session.game(), him),
                null);
    }

    /**
     * The same roster, for a path that goes somewhere else once he is chosen: every row settles what the client
     * knows about the hero picked, then does {@code then} with him, then opens {@code next} — or starts, where it
     * is {@code null}. A party's rows go on to a waiting room instead: see {@link PartyFront}.
     */
    static uz.dukeengine.client3d.Shell.Question whoToPlay(Dungeon.Session session, DungeonSettings settings,
            Visuals visuals, Hotkeys keys, java.util.function.Consumer<String> then,
            uz.dukeengine.client3d.Shell.Question next) {
        var options = new java.util.ArrayList<uz.dukeengine.client3d.Shell.Option>();
        for (var hero : settings.heroes()) {
            var him = hero.name();
            options.add(new uz.dukeengine.client3d.Shell.Option(
                    displayNameOf(session, him), hero.title(),
                    () -> {
                        heroChosen(session, settings, visuals, keys, him);
                        then.accept(him);
                    }, next));
        }
        return new uz.dukeengine.client3d.Shell.Question(settings.hud().chooseHeroWord(),
                settings.hud().chooseHeroHint(), options);
    }

    /** Everything the client knows about the hero played, settled for {@code him}: chosen, or a save's. */
    static void heroChosen(Dungeon.Session session, DungeonSettings settings, Visuals visuals, Hotkeys keys,
            String him) {
        // The dark opens around HIS eyes. Named rather than
        // measured, so the radius is his own VisionRange -- and a
        // knight sees a shorter way than an archer, which is part
        // of playing him. Left pointing at the file's hero, the
        // map would never open at all: nothing of that template is
        // in the dungeon.
        visuals.discoveredBy(him);
        // ★ And HIS four keys, and HIS four rings. Everything the
        // client knows about a skill is keyed by the letter, and
        // until this line it was all settled at startup from
        // DefaultHero -- so whoever you picked, Q asked you to
        // point at whatever the FILE'S hero's Q needed. Choosing
        // the mage got you the rogue's aims: his Q wants a
        // creature, and a fireball handed a creature instead of a
        // direction has nowhere to fly and refuses, which from a
        // chair is a skill that does nothing at all.
        aimsFor(keys, settings, him);
        ringsFor(visuals, settings, him);
        attackRingFor(visuals, session.game(), him);
    }

    /**
     * The path the player walks before a run begins: how, then where, then who.
     *
     * <p>Two games share this loop. The endless descent draws a floor nobody has
     * seen and asks how far down you get; a stage hands back the same rooms every
     * time and asks you to learn them. Which of the two is the first thing to
     * settle, because everything after it differs — one has a list of stages to
     * pick from and the other has nowhere to go but down.
     *
     * <p><b>Nothing is offered that cannot be played.</b> A build shipping no
     * stages, or only broken ones, has no stage row at all rather than a row
     * leading to an empty column — see {@code Stages.all}, which drops what it
     * cannot read and says so in the log. With no stages the whole question
     * collapses to the hero, which is what the game asked yesterday.
     */
    static uz.dukeengine.client3d.Shell.Question howToPlay(Dungeon.Session session,
            DungeonSettings settings, Visuals visuals, Hotkeys keys) {
        var hero = whoToPlay(session, settings, visuals, keys);
        Runnable endless = () -> session.run().playing(
                uz.dukeengine.dungeon.run.Floors.generated(System.nanoTime(), settings));
        var asked = howToPlay(settings, hero, endless,
                (listed, stage) -> session.run().playing(uz.dukeengine.dungeon.run.Floors.ofStage(stage)));
        if (settings.campaigns().isEmpty()) {
            return asked;
        }
        // The campaign's acts first, a game alone: each its own hero, from its first joint — see
        // docs/plan/2026-10-15-act1.md and -act2.md. The descent and the stages stay where they were, below them.
        var rows = new java.util.ArrayList<Shell.Option>();
        for (var campaign : settings.campaigns()) {
            rows.add(new Shell.Option(campaign.word(), campaign.blurb(), () -> {
                heroChosen(session, settings, visuals, keys, campaign.hero());
                // From its first joint, with that joint's flags (Act1: the act's world story on). An act that follows
                // one carries on from its end save, or from the act's defaults with none.
                session.run().skipTo(campaign, 1,
                        campaign.follows().isBlank() ? null : Saves.ofThePlayer().read(campaign.follows()));
            }, null));
        }
        if (asked == hero) {
            rows.add(new Shell.Option(settings.hud().endlessWord(), settings.hud().endlessBlurb(), endless, hero));
        } else {
            rows.addAll(asked.options());
        }
        return new Shell.Question(settings.hud().chooseModeWord(), settings.hud().chooseModeHint(), rows);
    }

    /** {@code --<name>=word}: the word, for testing -- {@code --weather=Rain} -- or null where none is given. */
    static String wordArg(String[] args, String name) {
        for (var arg : args) {
            if (arg.startsWith("--" + name + "=")) {
                return arg.substring(name.length() + 3).strip();
            }
        }
        return null;
    }

    /** {@code --<act>=N}: that act's joint to open straight on, for testing it; 0 for none. */
    static int actArg(String[] args, String act) {
        var asked = "--" + act + "=";
        for (var arg : args) {
            if (arg.startsWith(asked)) {
                try {
                    return Math.max(0, Integer.parseInt(arg.substring(asked.length()).strip()));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * The same path to wherever {@code hero} leads, for whoever settles the answer: the endless descent is
     * {@code endless}, and a stage that can be played is handed to {@code stage} with its row. A party's host settles the
     * party's match with it — see {@link PartyFront}.
     */
    static uz.dukeengine.client3d.Shell.Question howToPlay(DungeonSettings settings,
            uz.dukeengine.client3d.Shell.Question hero, Runnable endless,
            java.util.function.BiConsumer<uz.dukeengine.dungeon.stage.Stages.Listed, uz.dukeengine.dungeon.stage.Stage> stage) {
        var stages = uz.dukeengine.dungeon.stage.Stages.all();
        if (stages.isEmpty()) {
            return hero; // nothing to choose between; the only question left is who
        }
        var modes = List.of(
                new uz.dukeengine.client3d.Shell.Option(settings.hud().endlessWord(),
                        settings.hud().endlessBlurb(), endless, hero),
                new uz.dukeengine.client3d.Shell.Option(settings.hud().stagesWord(),
                        settings.hud().stagesBlurb(), null,
                        whichStage(settings, stages, hero, stage)));
        return new uz.dukeengine.client3d.Shell.Question(settings.hud().chooseModeWord(),
                settings.hud().chooseModeHint(), modes);
    }

    /**
     * Which frozen dungeon, out of the ones this build can actually play.
     *
     * <p>The rows are the stages' own words — an author names his stage and says
     * one line about it in the file, and that is what a player reads. Nothing here
     * is a list somebody keeps in step: a map is a folder under {@code maps/} — beside the game, or inside it —
     * and dropping one there is what puts it on this screen. See {@code uz.dukeengine.core.map.MapPackage}.
     *
     * <p>Taking one only says which floors to lay. Who lays them is the next
     * question, which is the same one the endless descent asks — a stage does not
     * decide who plays it, and cannot.
     */
    private static uz.dukeengine.client3d.Shell.Question whichStage(DungeonSettings settings,
            List<uz.dukeengine.dungeon.stage.Stages.Listed> stages, uz.dukeengine.client3d.Shell.Question hero,
            java.util.function.BiConsumer<uz.dukeengine.dungeon.stage.Stages.Listed, uz.dukeengine.dungeon.stage.Stage> chosen) {
        var rows = new java.util.ArrayList<uz.dukeengine.client3d.Shell.Option>();
        for (var listed : stages) {
            // Read when it is chosen rather than when it is listed: the row is written from the head of the
            // map's file, and a map that cannot be played says so here rather than keeping its row out.
            rows.add(new uz.dukeengine.client3d.Shell.Option(listed.title(), listed.description(), () -> {
                var stage = uz.dukeengine.dungeon.stage.Stages.offer(listed, settings);
                if (stage != null) {
                    chosen.accept(listed, stage);
                }
            }, hero, listed.picture()));
        }
        return new uz.dukeengine.client3d.Shell.Question(settings.hud().chooseStageWord(),
                settings.hud().chooseStageHint(), rows);
    }

    /**
     * What the player calls him, out of his creature block rather than his art.
     *
     * <p>{@code DisplayName} is what the panel writes over his portrait, so it is
     * what the menu should offer — being asked to choose "Knight" and then playing
     * somebody called Garen is two names for one man. Falls back to the template's
     * own name, which is what the panel falls back to.
     */
    static String displayNameOf(Dungeon.Session session, String template) {
        var found = session.game().getLogic() == null ? null
                : session.game().getLogic().getThingFactory().findTemplate(template);
        return found == null ? template : uz.dukeengine.core.thing.Titled.of(found);
    }

    /**
     * The screens' keys: a thing of the window, as the tracker's fold is, so each flips the window's own state and
     * sends nothing to the simulation. Through the hotkeys, which the client answers only in play -- never on a menu,
     * nor while chat is typed; while a screen is open it takes the keys itself.
     */
    static void screensOn(Hotkeys keys, PlayerScreens screens, DungeonSettings settings) {
        var letters = settings.screens().keys();
        // Each said in the game's words on the Settings screen's list of keys (E46).
        var words = List.of(settings.screens().sheetTitle(), settings.screens().questsTitle(),
                settings.screens().mapTitle());
        for (int i = 0; i < letters.size(); i++) {
            var which = PlayerScreens.Open.values()[i + 1];
            keys.on(letters.get(i), words.get(i), game -> screens.toggle(which));
        }
    }

    /**
     * The keys that cast skills, taken from the same file that says what the
     * skills are.
     *
     * <p>Read out of the settings rather than written here, so adding a fifth
     * skill — or a second hero with different keys — binds its key by being in the
     * file. A press does one thing: post the command. Deciding whether the skill
     * is ready, unlocked, or able to reach what it was aimed at is the
     * simulation's, on its own thread, on a frame boundary.
     *
     * <p>Which of the three ways a key is bound follows from what the skill does,
     * because the effect is what knows: a strike is pointed at a creature, a dash
     * at a spot, and the two that go off around the caster are pointed at nothing.
     * The client keeps the press-then-click; this only says what to send once the
     * player has chosen.
     */

    static Hotkeys controls(DungeonSettings settings) {
        var keys = Hotkeys.create();
        // The file's default hero to begin with, and whoever is actually chosen
        // the moment he is -- see aimsFor, and whoToPlay, which calls it again.
        aimsFor(keys, settings, settings.run().defaultHero());
        // The belt's keys, 1 to its size: a draught from that stack, as this game's order, named on the Settings
        // screen's list of keys (E46; digits bind since E45's fix).
        var rules = settings.items().rules();
        for (int key = 1; key <= rules.beltSize(); key++) {
            int belt = key - 1;
            keys.on((char) ('0' + key), rules.beltKeyWord().formatted(key), game -> game.postCommand(
                    PartyOrders.of(new uz.dukeengine.dungeon.run.Drink(game.getLocalPlayerIndex(), belt))));
        }
        // A fire built at his feet from his wood, as this game's order (docs/plan/2026-10-15-food-and-fire.md).
        var food = settings.food();
        if (!food.buildKey().isBlank()) {
            keys.on(food.buildKey().charAt(0), food.buildWord(), game -> game.postCommand(
                    PartyOrders.of(new uz.dukeengine.dungeon.run.Work(game.getLocalPlayerIndex(), null))));
        }
        // Spending a level on a slot. A click on the badge rather than a letter,
        // so it comes through a door of its own -- and it is a COMMAND like every
        // other decision, settled on a frame boundary where the rules live.
        keys.onRaiseSkill((game, key) -> game.postCommand(PartyOrders.of(new uz.dukeengine.rpg.skill.UpgradeSkill(
                game.getLocalPlayerIndex(), key))));
        // The mission list folds to its tab and unfolds again: a thing of the window, so it flips the window's own
        // flag and sends nothing to the simulation.
        keys.on(settings.run().foldKey().charAt(0), settings.screens().trackerWord(), game -> MissionScreen.fold());
        if (settings.scheme() == uz.dukeengine.dungeon.world.Controls.Scheme.CLASSIC) {
            orders(keys);
            // The four orders said as the panel's buttons say them: walk, attack, stop, guard (see HeroStatus.orders).
            var orderWords = settings.hud().orderWords();
            for (int i = 0; i < orderWords.size(); i++) {
                keys.describe("FASD".charAt(i), orderWords.get(i));
            }
        } else {
            // Modern: W A S D are his feet, not the camera's (the arrows still pan), and F is a skill's. The orders'
            // letters go: the left button attacks, letting go of WASD stops (docs/plan/2026-10-15-modern-controls.md).
            keys.controls(modernKeyMap());
            // His feet: the held W A S D as a direction the camera's way, sent as an order on the lock-step stream
            // only when it changes (E74, E75) -- the simulation never reads a key.
            keys.onDirection(uz.dukeengine.client3d.DirectionKeys.WASD, Main::walk);
            // Space rolls him (E80): a key of the game's since E79, rebindable on the keys page like his letters. The
            // client's Space (the camera home) is off in this scheme (modernKeyMap); Enter still does it.
            keys.on(' ', settings.controls().dodgeWord(), game -> dodge(game, settings));
        }
        // Which single creature he has picked out. The target frame describes it, and
        // only the simulation can say what it is worth -- see Watching.
        keys.onWatch((game, id) -> game.postCommand(PartyOrders.of(new uz.dukeengine.dungeon.run.Watching(
                game.getLocalPlayerIndex(), id < 0 ? null : new ObjectId(id)))));
        return keys;
    }

    /** His hero walked along {@code angle} (256ths of a turn, or MoveAlong.NONE to stop): the held keys' order. */
    static void walk(uz.dukeengine.game.DukeGame game, int angle) {
        walking = angle;
        game.postCommand(new uz.dukeengine.combat.message.CombatOrder.MoveAlong(game.getLocalPlayerIndex(),
                selected(game), angle));
    }

    /**
     * The mouse of the scheme played (E78): modern -- the left button locks on an enemy within 60 or swings at the
     * ground, held to go on; the right one works (talk, chop, open); a skill's letter casts at the pointer -- or classic.
     */
    static uz.dukeengine.client3d.MouseScheme mouseOf(DungeonSettings settings) {
        return settings.scheme() == uz.dukeengine.dungeon.world.Controls.Scheme.MODERN
                ? uz.dukeengine.client3d.MouseScheme.MODERN : uz.dukeengine.client3d.MouseScheme.CLASSIC;
    }

    /** The way the held keys last said, for the dodge: MoveAlong.NONE while none is held. */
    private static volatile int walking = uz.dukeengine.client3d.DirectionKeys.NONE;

    /** The screens over the world, top first (the launch screen's chain): the dodge rolls only while none holds keys. */
    static volatile uz.dukeengine.client3d.CanvasInput over;

    /** Space, in the modern scheme: his dodge (E80) the way he walks, or faces when no key is held. */
    static void dodge(uz.dukeengine.game.DukeGame game, DungeonSettings settings) {
        var screens = over;
        if (screens == null || !screens.holdsKeys()) {
            roll(game, walking, settings);
        }
    }

    /**
     * His dodge cast along {@code angle} (256ths of a turn), or the way he faces for NONE: a point its Distance off,
     * though the roll goes its Distance toward any point. Nothing if his hero has none.
     */
    static void roll(uz.dukeengine.game.DukeGame game, int angle, DungeonSettings settings) {
        for (var unit : game.getSnapshot().units()) {
            if (unit.playerIndex() != game.getLocalPlayerIndex()) {
                continue;
            }
            for (var skill : settings.bookFor(unit.templateName())) {
                if (skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.DODGE) {
                    double way = angle < 0 ? unit.orientation() : angle * 2.0 * Math.PI / 256.0;
                    var point = new uz.dukeengine.core.math.Coord3D(unit.x() + 16f * (float) Math.cos(way),
                            unit.y() + 16f * (float) Math.sin(way), 0f);
                    game.postCommand(PartyOrders.of(new CastSkill(game.getLocalPlayerIndex(), skill.key(), null,
                            point)));
                    return;
                }
            }
        }
    }

    /** The client's own keys in the modern scheme: its standard ones with W A S D taken off panning. */
    static uz.dukeengine.client3d.KeyMap modernKeyMap() {
        var map = uz.dukeengine.client3d.KeyMap.standard();
        var feet = java.util.Set.of(Hotkeys.codeOf('W'), Hotkeys.codeOf('A'), Hotkeys.codeOf('S'), Hotkeys.codeOf('D'));
        for (var pan : List.of(uz.dukeengine.client3d.KeyMap.Control.PAN_UP, uz.dukeengine.client3d.KeyMap.Control.PAN_LEFT,
                uz.dukeengine.client3d.KeyMap.Control.PAN_DOWN, uz.dukeengine.client3d.KeyMap.Control.PAN_RIGHT)) {
            map.bind(pan, map.keysOf(pan).stream().filter(key -> !feet.contains(key.code()))
                    .toArray(uz.dukeengine.client3d.KeyMap.Key[]::new));
        }
        // Space is the dodge's here (Dodge); the camera comes home on Enter.
        map.bind(uz.dukeengine.client3d.KeyMap.Control.CENTRE_ON_BASE, map.keysOf(
                uz.dukeengine.client3d.KeyMap.Control.CENTRE_ON_BASE).stream()
                .filter(key -> key.code() != uz.dukeengine.client3d.CanvasInput.SPACE)
                .toArray(uz.dukeengine.client3d.KeyMap.Key[]::new));
        return map;
    }

    /**
     * What one skill's ring should look like, worked out from the skill itself.
     *
     * <p>The effect is what knows. A strike is pointed at a creature within its
     * range whatever its numbers say; a blast dropped on a spot needs both how far
     * it can be thrown and how much it covers; a shot down a lane needs the length
     * and the width of the lane. So nothing here is a second set of numbers to
     * keep in step with the skills — every figure is the skill's own, and a hero
     * added next month gets his rings by having skills.
     *
     * <p>The one number that is not a skill's is the ring for something that only
     * touches the caster: there is no reach to draw, so the look says how wide to
     * draw "just him".
     */
    static uz.dukeengine.client3d.SkillRange rangeOf(
            uz.dukeengine.rpg.skill.Skill skill, float selfRadius) {
        var shape = switch (skill.effect()) {
            // A mending is pointed at a creature as a strike is -- one of its own.
            case STRIKE, HEAL -> uz.dukeengine.client3d.SkillRange.Shape.AT_A_CREATURE;
            case AREA_AT_SPOT -> uz.dukeengine.client3d.SkillRange.Shape.AT_A_SPOT;
            // A wave runs down a lane as a shot does, only on through what it meets.
            // And the shield charge: a line as long as his drive and as wide as what it catches -- the owner,
            // 2026-10-10: "a line like the first skill's, not a point inside a circle".
            case SKILLSHOT, WAVE, SKEWER -> uz.dukeengine.client3d.SkillRange.Shape.DOWN_A_LANE;
            // A blink is pointed at a spot exactly as a dash is. That it does not
            // cross what is between is the simulation's business; what the player
            // has to do with the mouse is the same thing, so the ring is the same.
            case DASH, BLINK -> uz.dukeengine.client3d.SkillRange.Shape.AT_A_SPOT;
            case METEOR -> uz.dukeengine.client3d.SkillRange.Shape.AT_A_SPOT;
            // A summoning calls them up round him, as far out as its Radius; a haste finds one of
            // his own round him, as far out as its Range; a pull gathers whoever is within its Radius.
            case AREA_DAMAGE, SUMMON, HASTE, PULL -> uz.dukeengine.client3d.SkillRange.Shape.AROUND_HIM;
            // None of these is aimed past him: one sharpens his sword, one thickens
            // his skin, and the passives -- never cast -- drink from his blows or
            // lend round him.
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA, SOUL_DRINK, UNYIELDING,
                    DRAGON_BLOOD, STAGGER, DODGE -> uz.dukeengine.client3d.SkillRange.Shape.ON_HIMSELF;
        };
        float reach = switch (skill.effect()) {
            case STRIKE, AREA_AT_SPOT, SKILLSHOT, HEAL -> skill.range();
            case DASH, BLINK, SKEWER, WAVE -> skill.distance();
            case METEOR -> skill.range();
            case AREA_DAMAGE, SUMMON, PULL -> skill.radius();
            case HASTE -> skill.range();
            case EMPOWER, GUARD, LIFESTEAL, DAMAGE_AURA, MANA_AURA, LIFESTEAL_AURA, SOUL_DRINK, UNYIELDING,
                    DRAGON_BLOOD, STAGGER, DODGE -> selfRadius;
        };
        // What it LEAVES where it lands: a blast's radius, a lane's width. A dash
        // leaves a man, and a circle round a man-sized spot is a second ring saying
        // what the pointer already said -- so it draws none.
        // What it LEAVES where it lands: a blast's radius. A dash leaves a man,
        // and a circle round a man-sized spot is a second ring saying what the
        // pointer already said -- so it draws none.
        //
        // ★ Nor does a SKILLSHOT, though it bursts. Where it bursts depends on
        // where it STOPS, which is the first body or the first wall, so a circle
        // drawn at the far end of its reach is a promise the shot does not have
        // to keep. Its Radius is still real and still hurts -- see SkillBook --
        // it simply is not a thing that can be honestly drawn before the cast.
        float area = switch (skill.effect()) {
            case AREA_AT_SPOT, METEOR -> skill.radius();
            default -> 0f;
        };
        // A lane is as wide as what runs down it: a shot's HitWidth, a charge's catch either side of him.
        float width = skill.effect() == uz.dukeengine.rpg.skill.SkillEffect.SKEWER ? 2f * skill.radius() : skill.hitWidth();
        return new uz.dukeengine.client3d.SkillRange(skill.key(), shape, reach, area, width);
    }

    /**
     * The four orders the buttons beside the map give.
     *
     * <p>Three of them are the engine's own and the mouse already gives them; the
     * buttons are there because a right-click never told the player they existed,
     * and because a key is a faster way to say "walk there" than aiming at a piece
     * of floor that might have a skeleton on it.
     *
     * <p>The fourth is this game's — see {@link uz.dukeengine.dungeon.ai.HoldGround} —
     * and it is the one the engine cannot express: stop cancels a walk and says
     * nothing about his bow.
     *
     * <p>Claimed through the same seam the skills are, so the client's rule about
     * one key doing one thing covers all eight of them together.
     */
    private static void orders(Hotkeys keys) {
        // ★ A, S, D — attack, stop, defend, which is where a hand rests and the
        // order every RTS since Warcraft has put them in. Walking keeps the fourth
        // letter and needs it least: pointing at a piece of floor is what the mouse
        // has always done, and the button is there so a player can SEE that the
        // four orders exist rather than because anybody reaches for it.
        keys.onOpenGround('F', (game, spot) -> game.postCommand(
                new uz.dukeengine.combat.message.CombatOrder.MoveTo(
                        game.getLocalPlayerIndex(), selected(game), spot)));
        // Attack takes either, because it means two related things and a player
        // mid-fight should not have to decide which before he knows what his click
        // will land on: that creature, or fight your way to that spot.
        keys.onUnitOrGround('A',
                (game, id) -> game.postCommand(
                        new uz.dukeengine.combat.message.CombatOrder.AttackObject(
                                game.getLocalPlayerIndex(), selected(game), new ObjectId(id))),
                (game, spot) -> game.postCommand(PartyOrders.of(new uz.dukeengine.dungeon.ai.AttackMove(
                        game.getLocalPlayerIndex(), spot))));
        // Stop is the loudest of the four: drop the walk, drop the target, and
        // start nothing until told otherwise. Two commands because two things are
        // being said -- the engine's own stop, and this game's "and stay stopped".
        keys.on('S', game -> {
            game.postCommand(new uz.dukeengine.combat.message.CombatOrder.StopMoving(
                    game.getLocalPlayerIndex(), selected(game)));
            game.postCommand(PartyOrders.of(new uz.dukeengine.dungeon.ai.HoldGround(
                    game.getLocalPlayerIndex(), true)));
        });
        // And Guard is the other half of that pair: stand where you are, and fight
        // whatever comes to you. The same two commands Stop sends with the second
        // one inverted, which is the whole difference between the two buttons —
        // Stop says "stand, and start nothing", this says "stand, and start what
        // walks into you". It is also the state he is in for most of a run, which
        // is why it has a button of its own to light.
        //
        // ★ The first of the two is what it was missing, and without it this was
        // not an order at all. It only took a hold OFF: pressing it while he was
        // walking or chasing did nothing whatever, so it was the one button on the
        // bar a player could press all game without once seeing it do anything —
        // while its own word said Himoya.
        keys.on('D', game -> {
            game.postCommand(new uz.dukeengine.combat.message.CombatOrder.StopMoving(
                    game.getLocalPlayerIndex(), selected(game)));
            game.postCommand(PartyOrders.of(new uz.dukeengine.dungeon.ai.HoldGround(
                    game.getLocalPlayerIndex(), false)));
        });
    }

    /**
     * Whose orders these are: every unit the player owns.
     *
     * <p>A dungeon holds one hero, so "his units" and "the selection" are the same
     * list — and taking it from the world rather than from the client is what lets
     * a key work when nothing has been clicked on, which is the whole point of
     * having the key.
     */
    private static java.util.List<ObjectId> selected(uz.dukeengine.game.DukeGame game) {
        var mine = new java.util.ArrayList<ObjectId>();
        for (var unit : game.getSnapshot().units()) {
            if (unit.playerIndex() == game.getLocalPlayerIndex() && unit.selectable()) {
                mine.add(new ObjectId(unit.id()));
            }
        }
        return mine;
    }

    /**
     * What each kind of monster looks like, taken from the same file that says how
     * it behaves.
     *
     * <p>With no models yet, colour and size are the only things telling one
     * monster from another — and telling a runner from a brute is a decision the
     * player has to make in the second before they reach him. Player colour cannot
     * do it: every monster belongs to the same side, so they would all be one
     * shade of red, on screen and on the minimap alike.
     *
     * <p>Package-private so a test can ask what a thing is drawn as.
     */
    static Visuals looks(DungeonSettings settings) {
        var visuals = Visuals.create();
        // The simulation's beams (World.beam), as the data draws them: the ritual's, the pillar of light.
        visuals.lasers(settings.lasers());
        for (var kind : settings.monsters()) {
            var look = settings.lookOf(kind);
            visuals.unit(kind.name(), unit -> {
                // The colour is set either way: it is what the minimap dot is
                // drawn in, and what the creature falls back to if its model is
                // missing. A shape in the right colour beats nothing on screen.
                unit.colour(kind.awtColour()).scale(kind.scale());
                if (!look.hasModel()) {
                    return;
                }
                unit.model(look.model())
                        .texture(look.texture())
                        .tint(look.awtTint())
                        .scale(look.modelScale())
                        .facing(look.facing())
                        .idle(look.idle())
                        // Paced to its speed where its look says how far a play carries it (a licensed look).
                        .walk(look.walk(), look.walkStride())
                        .attack(look.attack())
                        .hurt(look.hurt())
                        .effect(look.effect())
                        .upperBody(look.upperBody());
                carry(unit, look.held());
                unit.die(look.death());
                for (var library : look.libraries()) {
                    unit.animationsFrom(library);
                }
            });
        }

        // The heroes come from a different kit on a different skeleton, and they
        // carry something — so they are described their own way rather than
        // squeezed into the monsters'. One block each, named after the creature
        // template, so a second hero is a block rather than an edit here.
        for (var hero : settings.heroes()) {
            if (!hero.hasModel()) {
                continue;
            }
            visuals.unit(hero.name(), unit -> {
                unit.model(hero.model())
                        .scale(hero.modelScale())
                        .facing(hero.facing())
                        .idle(hero.idle())
                        .walk(hero.walk(), hero.walkStride())
                        .attack(hero.attack())
                        // No .hurt: his flinch is his STAGGER's busy clip, never the client's own.
                        .die(hero.death())
                        .upperBody(hero.upperBody());
                if (hero.texture() != null) {
                    unit.texture(hero.texture());
                }
                carry(unit, hero.held());
                // The turning darkens him: a tint while he holds the medallion's CursedWord.
                var medallion = settings.medallion();
                unit.tint(java.util.Set.of(medallion.cursedWord()), medallion.cursedRed(), medallion.cursedGreen(),
                        medallion.cursedBlue(), medallion.cursedEaseFrames());
                // Whatever his own skills say he casts with. Gathered from the
                // skill blocks rather than listed again in his, so the clip is
                // named once -- a name in two files is two names waiting to
                // disagree.
                for (var skill : settings.skillsFor(hero.name())) {
                    unit.alsoAnimation(skill.castAnim());
                }
                for (var library : hero.animations()) {
                    unit.animationsFrom(library);
                }
            });
        }

        // And what everything looks like in the panel's frame, alive. One block
        // for the whole bestiary — the camera is written in fractions of whatever
        // it is looking at, so it frames a skeleton and a hero each by its own
        // measured height — and a block per creature that wants a different one.
        // No art is named anywhere here: a portrait takes the model, the scale and
        // the libraries from the creature's own block, under the same name.
        var everyone = settings.everyPortrait();
        if (everyone != null) {
            HeroLooks.of(visuals).portraits(portraitLook(everyone));
        }
        for (var portrait : settings.portraits()) {
            HeroLooks.of(visuals).portrait(portrait.name(), portraitLook(portrait));
        }
        HeroLooks.of(visuals).portraitFps(settings.hud().portraitFps());
        // What a dead monster leaves lying about. No chest in the kit, so it is
        // a box in torch colour -- which is what a thing worth walking over to
        // has to be, whatever it is eventually modelled as.
        visuals.unit("Chest", unit -> unit.colour(new java.awt.Color(0xE8A33D)).scale(0.5f));

        // And what the files dress outside any theme: drawn so in every theme that does not dress it -- the key,
        // one look named once rather than a copy in every biome. See the ThemeMonster in data/props/key.duke.
        for (var look : settings.looks()) {
            visuals.unit(look.name(), dressed(look, settings));
        }

        // Things in flight are units like any other — they are in the world, so
        // the client draws them without being told anything special, and the
        // simulation turns them so they point the way they are flying. What each
        // one burns like is named beside it and declared just below.
        for (var look : settings.effects()) {
            effect(visuals, look);
        }
        // Then the layers each is drawn from, in the file's order -- which is the
        // order they are laid one over another.
        for (var layer : settings.effectLayers()) {
            visuals.effect(layer.effect(),
                    recipe -> recipe.layer(layerOf(layer)));
        }
        for (var look : settings.projectiles()) {
            arrow(visuals, look.name(), look);
        }
        visuals.effectBudget(settings.effectBudget().maxLights(), settings.effectBudget().maxDistance());
        visuals.particleBudget(settings.effectBudget().maxParticles());
        // One hero and nobody else to command, so there is no group to draw a box round: a press is a click wherever
        // the mouse lets go, and picks the one thing under it to look at.
        visuals.dragSelects(false);
        visuals.shakeScale(settings.hitFeel().shakeScale());
        visuals.hitFlash(new Visuals.HitFlashLook(settings.hitFeel().hitFlashColour(),
                settings.hitFeel().hitFlashSeconds(), settings.hitFeel().hitFlashStrength()));
        visuals.strikeWithin(settings.hitFeel().strikeWithin());
        visuals.effectLists(settings.effectLists());
        visuals.hitStop(settings.hitStop());
        uz.dukeengine.dungeon.content.Feel.apply(visuals, settings);
        // The run's own moments -- a level, the boss down, a floor reached -- and the
        // look each one plays on the hero.
        for (var moment : settings.moments()) {
            visuals.moment(moment.name(), moment.effect(), moment.scale());
        }
        measureLooks(visuals, settings);

        // The floor is black until he walks it. Named rather than given a
        // distance: the radius is the hero's own VisionRange from his template,
        // which is also what the engine's fog uses to decide whether a monster is
        // on screen — so the ground he uncovers and the things he can see are the
        // same number, and re-tuning one cannot leave the other behind.
        // Whoever is being played: a knight sees a shorter way than an archer, and
        // the floor has to open up around the eyes that are actually there.
        visuals.discoveredBy(settings.run().defaultHero());

        // What it all sounds like — see the Sound blocks in data/sounds/. Handed over
        // whole, like the tiles and the themes: the client raises moments by name
        // and this is the only place that knows what a moment sounds like.
        visuals.sounds(soundsOf(settings));

        // The lettering the menus are set in and the stone they are cut from -- carved Roman capitals, baked
        // from the TTF by BitmapFontBaker. Named in the file rather than here for the same reason every other
        // asset is: a path in Java is a path that needs a rebuild to move. See MenuStyle in data/world/hud.duke.
        visuals.menuStyle(settings.menu());

        // What the panel's edges are painted with -- see Skin in
        // data/world/hud.duke. The client knows where a socket goes; this says what its
        // rim is made of.
        visuals.panelSkin(panelSkin(settings));
        visuals.unitBars(unitBars(settings));

        // And what the mouse pointer looks like over each thing -- see
        // Cursor in data/world/hud.duke. The client knows what is under the
        // pointer; this says what to draw there.
        for (var pointer : settings.cursors()) {
            visuals.pointer(pointer.name(), pointer.image(), pointer.hotX(), pointer.hotY(),
                    pointer.tint());
        }

        // What the dark is worth: whether stone stops sight, how dim a room he
        // has left should be, and what colour nothing is. All of it drawing, and
        // all of it in the file — see Fog in data/world/scene.duke.
        // Shoving the camera with the cursor, on top of the keys — see
        // Camera in data/world/world.duke.
        // Modern: the camera follows his hero, the arrows look away and it eases back (E77); classic: the free camera.
        visuals.cameraFollow(settings.scheme() == uz.dukeengine.dungeon.world.Controls.Scheme.MODERN
                ? uz.dukeengine.client3d.CameraFollow.of(null) : uz.dukeengine.client3d.CameraFollow.NONE);
        // Not in the modern scheme, whose camera follows him.
        visuals.edgeScroll(settings.scheme() == uz.dukeengine.dungeon.world.Controls.Scheme.MODERN ? EdgeScroll.NONE
                : new EdgeScroll(settings.camera().edgeMargin(), settings.camera().edgeSpeedPercent()));

        visuals.fog(new Fog(settings.fog().lineOfSight(),
                settings.fog().unseenPercent() / 100f, settings.fog().rememberedPercent() / 100f,
                settings.fog().visiblePercent() / 100f, settings.fog().softenCells(),
                settings.fog().openPerSecond(), settings.fog().textureSize(), settings.fog().tint(),
                settings.fog().texelsPerCell()));

        // How a painted map's ground is drawn -- see PaintedGround in data/world/scene.duke. Only painted ground
        // reads any of it: a floor laid by a kit, every floor of the descent, is drawn exactly as before.
        paintedGround(visuals, settings.paintedGround());

        // What a caster is seen doing, keyed by the recipe the cast is announced
        // under -- see Visuals.castAnim. A skill that names no gesture is cast
        // exactly as it always was, which is with an effect and a still caster.
        for (var hero : settings.heroes()) {
            for (var skill : settings.skillsFor(hero.name())) {
                visuals.castAnim(skill.look(), skill.castAnim(), skill.castSeconds());
            }
        }

        // Whether the panel may colour the skill pictures. It always did, which is
        // how one white drawing served three states; a painted set cannot take it
        // — see IconLook.
        HeroLooks.of(visuals).iconLook(
                new uz.dukeengine.client3d.rpg.IconLook(settings.hud().paintedSkillIcons()));

        // How the block under the experience bar is drawn -- see StatBlock. The
        // client knows where its three columns go; the file says how big and what colour.
        var statBlockArt = settings.statBlock();
        HeroLooks.of(visuals).statLook(new uz.dukeengine.client3d.rpg.StatLook(statBlockArt.figureIcon(),
                statBlockArt.primaryIcon(), statBlockArt.attributeIcon(), statBlockArt.iconShare(),
                statBlockArt.rowGap(), statBlockArt.gapUnderBar(), statBlockArt.figureColumn(),
                statBlockArt.primaryColumn(), statBlockArt.figureRows(),
                statBlockArt.attributeRows(), statBlockArt.figureText(),
                statBlockArt.attributeText(), statBlockArt.primaryText(),
                statBlockArt.labelColour(), statBlockArt.valueColour(),
                statBlockArt.primaryColour(), statBlockArt.attributeColour(),
                statBlockArt.gainColour(), statBlockArt.frameColour(), statBlockArt.figureTint(),
                statBlockArt.primaryTint(), statBlockArt.attributeTint()));

        // Where the light comes from — see Sun. The pitch is what decides
        // whether the floor plan reads as a place with heights in it.
        visuals.sunlight(new uz.dukeengine.client3d.Sunlight(settings.sun().pitch(), settings.sun().yaw(),
                settings.sun().strengthPercent() / 100f, settings.sun().ambientPercent() / 100f,
                settings.sun().colour(), settings.sun().ambientTint()));

        // The three arrowheads that answer a click — the client's own OrderMark, which the
        // block of that name is read straight into.
        visuals.orderMark(settings.orderMark());
        HeroLooks.of(visuals).panelLook(settings.panelLook());
        visuals.hitNumbers(settings.hitNumbers());

        // How far each skill reaches, so the client can draw it before it is spent
        // — see SkillRing, and SkillRange for what each shape means.
        visuals.rangeLook(new uz.dukeengine.client3d.RangeLook(
                settings.skillRing().bandWidth(), settings.skillRing().fillAlpha(), settings.skillRing().edgeAlpha(),
                settings.skillRing().height(), settings.skillRing().pulseDepth(), settings.skillRing().pulsePerSecond(),
                settings.skillRing().segments(), settings.skillRing().allowColour(), settings.skillRing().denyColour(),
                settings.skillRing().areaColour(), settings.skillRing().brightness()));
        // The file's default hero to begin with, and whoever is actually chosen
        // the moment he is -- see ringsFor.
        ringsFor(visuals, settings, settings.run().defaultHero());

        themes(visuals, settings);

        // The floor is a modular kit, laid out by the client from the same grid
        // the pathfinder uses. Named in data/world/scene.duke rather than here, so swapping
        // the kit — or dropping back to plain blocks — is an edit, not a rebuild.
        var art = settings.tiles();
        if (art.floor() != null) {
            visuals.tiles(Tileset.create()
                    .floor(art.floor())
                    .wall(art.wall())
                    .corner(art.corner())
                    .stairs(art.stairs())
                    .tileSize(art.tileSize())
                    .wallHeight(art.wallHeight())
                    .wallLift(art.wallLift())
                    .wallShift(art.wallShift()));
        }
        return visuals;
    }
}
