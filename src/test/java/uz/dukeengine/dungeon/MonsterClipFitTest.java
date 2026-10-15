package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.anim.tween.action.BlendableAction;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.Control;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.AnimationLibrary;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.MonsterKind;
import uz.dukeengine.dungeon.content.MonsterLook;
import uz.dukeengine.rpg.fight.Bow;
import uz.dukeengine.rpg.fight.Swing;

/**
 * A monster's clips fit what the game does with them -- {@link HeroClipFitTest}'s measurements, on every creature.
 *
 * <p>The client plays a walk at the rate that covers {@code WalkStride} at the creature's speed, so a stride written
 * wrong is feet skating; and it starts a blow as the weapon winds up, the blow landing {@code PreAttackFrames} later,
 * which has to be where the swing (or the bite) is fastest. Neither number is visible in the files, so both are
 * measured here, off the clips as jME plays them.
 *
 * <p>What cannot be measured is said, not guessed: a creature with no feet (a ghost, a slime, a winged thing that
 * hovers) has no stride, so it keeps its walk at its own pace ({@code WalkStride} 0); and one that shoots looses on
 * its clip's first frames and needs no wind-up.
 */
class MonsterClipFitTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final AssetManager ASSETS = new DesktopAssetManager(true);
    private static final double STEP = 1 / 60.0;
    /** How far apart WalkStride and the clip may be: the measurement's own accuracy, and then some for paws. */
    private static final float STRIDE_SLACK = 0.15f;
    /** How far apart the blow and the swing's fastest frame may be: three frames of the game's thirty. */
    private static final double IMPACT_WITHIN = 0.1;
    /** What touches the ground: a foot (KayKit, Quaternius' IK feet), a leg's tip, a deer's hoof target. */
    private static final Pattern FOOT = Pattern.compile("(?!.*toe).*foot.*|.*leg\\.[lr]_end|ffb?\\.[lr]");
    /** What strikes: a hand or what it holds, an arm's tip, or a head that bites. */
    private static final Pattern STRIKER = Pattern.compile("hand\\.[lr]|handslot\\.[lr]|arm\\.[lr]_end|lowerarm\\.[lr]|head");

    /** One clip sampled every STEP: each joint's world position, per sample. */
    private record Samples(List<String> joints, List<Vector3f[]> at, double length) {
    }

    @Test
    void everyMonstersStrideIsTheOneItsWalkPlantsItsFeetAt() {
        var problems = new ArrayList<String>();
        var table = new StringBuilder("| Monster | Model | Walk | Length | Stride (u) | WalkStride | Speed | Rate |\n");
        for (var kind : SETTINGS.monsters().stream().filter(k -> !SETTINGS.isMount(k.name()) || k.name().equals(SETTINGS.mount().horse())).toList()) {
            var look = SETTINGS.lookOf(kind);
            if (!look.hasModel() || look.walk() == null) {
                continue;
            }
            var walk = sample(look, look.walk());
            if (walk == null) {
                continue; // a name that is in no file: DungeonMonsterArtTest's to say
            }
            if (walk.joints().stream().noneMatch(MonsterClipFitTest::isFoot)) {
                table.append(row(kind, look, walk, "no feet", 0)).append('\n');
                if (look.walkStride() != 0) {
                    problems.add(kind.name() + " has no feet to measure a stride by, and its WalkStride says "
                            + look.walkStride() + ": a guess");
                }
                continue;
            }
            float measured = (float) (stanceSpeed(walk) * walk.length() * look.modelScale());
            table.append(row(kind, look, walk, String.format("%.1f", measured), measured)).append('\n');
            if (Math.abs(look.walkStride() - measured) > STRIDE_SLACK * measured) {
                problems.add(kind.name() + "'s " + look.walk() + " plants its feet " + measured
                        + " units a play, and its WalkStride says " + look.walkStride());
            }
        }
        System.out.println(table);
        assertTrue(problems.isEmpty(), String.join("\n", problems) + "\n" + table);
    }

    @Test
    void everyMeleeMonstersBlowLandsAsItsSwingDoes() {
        var problems = new ArrayList<String>();
        var table = new StringBuilder("| Monster | Attack | Length | Fastest at | PreAttack | Between |\n");
        for (var kind : SETTINGS.monsters().stream().filter(k -> !SETTINGS.isMount(k.name()) || k.name().equals(SETTINGS.mount().horse())).toList()) {
            var look = SETTINGS.lookOf(kind);
            if (!look.hasModel() || look.attack() == null || !melee(kind.name())) {
                continue;
            }
            var attack = sample(look, look.attack());
            if (attack == null) {
                continue;
            }
            double impact = fastestStriker(attack);
            var weapon = weaponOf(kind.name());
            double lands = weapon.preAttackFrames() / (double) GameConstants.LOGICFRAMES_PER_SECOND;
            double between = (weapon.reloadFrames() + weapon.preAttackFrames())
                    / (double) GameConstants.LOGICFRAMES_PER_SECOND;
            table.append(String.format("| %s | %s | %.3f | %.3f | %.3f | %.3f |%n", kind.name(), look.attack(),
                    attack.length(), impact, lands, between));
            if (Math.abs(impact - lands) > IMPACT_WITHIN) {
                problems.add(kind.name() + "'s " + look.attack() + " is fastest " + impact + "s in and its blow"
                        + " lands " + lands + "s in (PreAttackFrames): set it to the impact");
            }
        }
        System.out.println(table);
        assertTrue(problems.isEmpty(), String.join("\n", problems) + "\n" + table);
    }

    @Test
    void everyMonstersUpperBodyIsAJointOfIt() {
        for (var kind : SETTINGS.monsters().stream().filter(k -> !SETTINGS.isMount(k.name()) || k.name().equals(SETTINGS.mount().horse())).toList()) {
            var look = SETTINGS.lookOf(kind);
            if (!look.hasModel() || look.upperBody() == null) {
                continue;
            }
            var skin = control(ASSETS.loadModel(look.model()), SkinningControl.class);
            assertTrue(skin != null && skin.getArmature().getJoint(look.upperBody()) != null,
                    kind.name() + "'s UpperBody " + look.upperBody() + " is no joint of " + look.model());
        }
    }

    /** FeelTest says every body names a death; this says the death is there to play, and plays for a while. */
    @Test
    void everyMonstersDeathIsAClipItPlays() {
        for (var kind : SETTINGS.monsters().stream().filter(k -> !SETTINGS.isMount(k.name()) || k.name().equals(SETTINGS.mount().horse())).toList()) {
            var look = SETTINGS.lookOf(kind);
            if (!look.hasModel() || look.death() == null) {
                continue;
            }
            var it = dressed(look);
            var clip = control(it, AnimComposer.class) == null ? null
                    : control(it, AnimComposer.class).getAnimClip(look.death());
            assertTrue(clip != null && clip.getLength() > 0.2, kind.name() + "'s death " + look.death()
                    + " is not a clip " + look.model() + " can play: it would pop out of the world");
        }
    }

    private static String row(MonsterKind kind, MonsterLook look, Samples walk, String stride, float measured) {
        float speed = speedOf(kind.name());
        String rate = measured > 0 ? String.format("%.2fx", speed / (measured / walk.length())) : "own pace";
        return String.format("| %s | %s | %s | %.3f | %s | %.2f | %.1f | %s |", kind.name(),
                look.model().replaceAll(".*/", ""), look.walk(), walk.length(), stride, look.walkStride(), speed,
                rate);
    }

    private static boolean isFoot(String joint) {
        return FOOT.matcher(joint.toLowerCase()).matches();
    }

    /** The median speed of a foot while it is down, all feet averaged: how fast the ground goes past it. */
    private static double stanceSpeed(Samples clip) {
        double sum = 0;
        int feet = 0;
        for (int j = 0; j < clip.joints().size(); j++) {
            if (!isFoot(clip.joints().get(j))) {
                continue;
            }
            int joint = j;
            double low = clip.at().stream().mapToDouble(p -> p[joint].y).min().orElseThrow();
            double high = clip.at().stream().mapToDouble(p -> p[joint].y).max().orElseThrow();
            double down = low + 0.1 * (high - low);
            var speeds = new ArrayList<Double>();
            for (int i = 1; i < clip.at().size(); i++) {
                var a = clip.at().get(i - 1)[joint];
                var b = clip.at().get(i)[joint];
                if (a.y <= down && b.y <= down) {
                    speeds.add(Math.hypot(b.x - a.x, b.z - a.z) / STEP);
                }
            }
            if (speeds.isEmpty()) {
                continue;
            }
            speeds.sort(null);
            sum += speeds.get(speeds.size() / 2);
            feet++;
        }
        return feet == 0 ? 0 : sum / feet;
    }

    /** When the fastest of what it strikes with moves fastest, in seconds into the clip. */
    private static double fastestStriker(Samples clip) {
        double best = -1;
        double when = 0;
        for (int j = 0; j < clip.joints().size(); j++) {
            if (!STRIKER.matcher(clip.joints().get(j).toLowerCase()).matches()) {
                continue;
            }
            for (int i = 1; i < clip.at().size(); i++) {
                float moved = clip.at().get(i)[j].distance(clip.at().get(i - 1)[j]);
                if (moved > best) {
                    best = moved;
                    when = i * STEP;
                }
            }
        }
        return when;
    }

    /** Its model with the clips its look names copied on, as the client dresses it. */
    private static Spatial dressed(MonsterLook look) {
        var it = ASSETS.loadModel(look.model());
        var clips = Stream.of(look.idle(), look.walk(), look.attack(), look.death()).filter(Objects::nonNull)
                .toList();
        for (var library : look.libraries()) {
            AnimationLibrary.copy(ASSETS.loadModel(library), it, clips);
        }
        return it;
    }

    /** Every joint of the creature, every STEP of the clip, as its model plays it; null for no such clip. */
    private static Samples sample(MonsterLook look, String clipName) {
        var it = dressed(look);
        var composer = control(it, AnimComposer.class);
        var skin = control(it, SkinningControl.class);
        if (composer == null || skin == null || composer.getAnimClip(clipName) == null) {
            return null;
        }
        var armature = skin.getArmature();
        var joints = armature.getJointList();
        var clip = composer.getAnimClip(clipName);
        if (composer.action(clipName) instanceof BlendableAction blendable) {
            blendable.setTransitionLength(0);
        }
        composer.setCurrentAction(clipName, AnimComposer.DEFAULT_LAYER, true);
        var at = new ArrayList<Vector3f[]>();
        for (int i = 0; i * STEP < clip.getLength() - 1e-6; i++) {
            composer.setTime(AnimComposer.DEFAULT_LAYER, i * STEP);
            composer.update(0f);
            armature.update();
            it.updateGeometricState();
            var world = skin.getSpatial().getWorldTransform();
            at.add(joints.stream().map(j -> world.transformVector(j.getModelTransform().getTranslation(), null))
                    .toArray(Vector3f[]::new));
        }
        return new Samples(joints.stream().map(Joint::getName).toList(), at, clip.getLength());
    }

    /** Whether it strikes where it stands (a Swing and no Bow): a wind-up is a melee blow's. */
    private static boolean melee(String template) {
        var modules = templates().findTemplate(template).modules();
        return modules.stream().anyMatch(Swing.Data.class::isInstance)
                && modules.stream().noneMatch(Bow.Data.class::isInstance);
    }

    private static WeaponUpdate.Data weaponOf(String template) {
        return templates().findTemplate(template).modules().stream().filter(WeaponUpdate.Data.class::isInstance)
                .map(WeaponUpdate.Data.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError(template + " carries no weapon"));
    }

    private static float speedOf(String template) {
        return templates().findTemplate(template).modules().stream().filter(MoveUpdate.Data.class::isInstance)
                .map(m -> ((MoveUpdate.Data) m).speed()).findFirst().orElse(0f);
    }

    private static ThingFactory templates;

    private static ThingFactory templates() {
        if (templates == null) {
            var game = Dungeon.world(".....\n.....\n.....\n", SETTINGS).game();
            game.runHeadless(1);
            templates = game.getLogic().getThingFactory();
        }
        return templates;
    }

    private static <T extends Control> T control(Spatial spatial, Class<T> type) {
        var found = spatial.getControl(type);
        if (found != null) {
            return found;
        }
        if (spatial instanceof Node node) {
            for (var child : node.getChildren()) {
                var deeper = control(child, type);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }
}
