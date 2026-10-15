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
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.AnimationLibrary;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.HeroLook;

/**
 * A hero's clips fit what the game does with them -- measured off the clips, as the client plays them.
 *
 * <p>The owner (2026-10-15): the heroes' walk and swing "don't stick". Two numbers in the files decide that and
 * neither is visible in them. The client plays the walk at the rate that covers {@code WalkStride} at his speed
 * (the engine's {@code UnitVisual.walk(clip, distance)}), so a stride written wrong is feet skating; and it starts
 * the attack clip as the weapon winds up (E69, {@code WeaponWoundUp}; on {@code WeaponFired} for one that does not),
 * and the blow lands {@code PreAttackFrames} later -- which has to be where the swing's blade is fastest.
 */
class HeroClipFitTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final AssetManager ASSETS = new DesktopAssetManager(true);
    private static final double STEP = 1 / 60.0;
    /** How far apart WalkStride and the clip may be: the measurement's own accuracy, checked on Quaternius' rigs. */
    private static final float STRIDE_SLACK = 0.12f;
    /** How far apart the blow and the swing's fastest frame may be: three frames of the game's thirty. */
    private static final double IMPACT_WITHIN = 0.1;

    /** One clip sampled every STEP: each joint's world position, per sample. */
    private record Samples(List<String> joints, List<Vector3f[]> at, double length) {
    }

    @Test
    void everyHerosStrideIsTheOneHisWalkPlantsHisFeetAt() {
        for (var hero : heroes()) {
            var walk = sample(hero, hero.walk());
            float measured = (float) (stanceSpeed(walk) * walk.length() * hero.modelScale());
            assertTrue(Math.abs(hero.walkStride() - measured) <= STRIDE_SLACK * measured,
                    hero.name() + "'s " + hero.walk() + " plants his feet " + measured + " units a play, and his"
                            + " WalkStride says " + hero.walkStride() + ": his feet slide by the difference");
        }
    }

    @Test
    void everyHerosBlowLandsAsHisSwingDoes() {
        for (var hero : heroes()) {
            var attack = sample(hero, hero.attack());
            double impact = fastestHand(attack);
            var weapon = weaponOf(hero.name());
            double lands = weapon.preAttackFrames() / (double) GameConstants.LOGICFRAMES_PER_SECOND;
            assertTrue(Math.abs(impact - lands) <= IMPACT_WITHIN, hero.name() + "'s " + hero.attack()
                    + " is fastest " + impact + "s in and his blow lands " + lands
                    + "s in (PreAttackFrames): set it to the impact");
            double between = (weapon.reloadFrames() + weapon.preAttackFrames())
                    / (double) GameConstants.LOGICFRAMES_PER_SECOND;
            assertTrue(attack.length() <= between + 1e-3, hero.name() + "'s " + hero.attack() + " runs "
                    + attack.length() + "s and he strikes every " + between + "s: every one is cut off");
        }
    }

    @Test
    void everyHerosUpperBodyIsAJointOfHis() {
        for (var hero : heroes()) {
            if (hero.upperBody() == null) {
                continue;
            }
            var him = ASSETS.loadModel(hero.model());
            assertTrue(control(him, SkinningControl.class).getArmature().getJoint(hero.upperBody()) != null,
                    hero.name() + "'s UpperBody " + hero.upperBody() + " is no joint of his: his blows on the move"
                            + " would play over the whole of him");
        }
    }

    /**
     * Every hero carries his work's clips (data/world/food.duke), and his Chop is a woodcutter's loop: about 1.2s, his
     * hands going up over his head and coming down -- not the sword stand-in it was.
     */
    @Test
    void everyHeroCarriesHisWorkAndHisChopIsAnOverheadLoop() {
        var food = SETTINGS.food();
        for (var hero : heroes()) {
            var him = ASSETS.loadModel(hero.model());
            var work = uz.dukeengine.dungeon.food.Food.WORDS.stream().map(food::clipOf)
                    .filter(java.util.Objects::nonNull).distinct().toList();
            for (var library : hero.animations()) {
                AnimationLibrary.copy(ASSETS.loadModel(library), him, work);
            }
            var composer = control(him, AnimComposer.class);
            for (var clip : work) {
                assertTrue(composer.getAnimClip(clip) != null, hero.name() + " has no " + clip + ": he stands still at it");
            }
            var chop = sample(hero, food.clipOf("Chop"));
            assertTrue(chop.length() >= 1.0 && chop.length() <= 1.5, hero.name() + "'s chop runs " + chop.length() + "s");
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (int j = 0; j < chop.joints().size(); j++) {
                if (!chop.joints().get(j).toLowerCase().matches(".*(hand|sword|slot).*")) { // the knight has no hand bone: his sword is in it
                    continue;
                }
                for (var at : chop.at()) {
                    low = Math.min(low, at[j].y);
                    high = Math.max(high, at[j].y);
                }
            }
            float tall = 12f / hero.modelScale(); // every hero stands 12 units: his height in his model's own units
            assertTrue((high - low) >= 0.3 * tall, hero.name() + "'s hands travel " + (high - low)
                    + " up and down in his chop, under a third of his height: it is no overhead swing");
        }
    }

    private static List<HeroLook> heroes() {
        return SETTINGS.heroes().stream().filter(HeroLook::hasModel).toList();
    }

    /** The median speed of a foot while it is down, both feet averaged: how fast the ground goes past him. */
    private static double stanceSpeed(Samples clip) {
        double sum = 0;
        int feet = 0;
        for (int j = 0; j < clip.joints().size(); j++) {
            String name = clip.joints().get(j).toLowerCase();
            if (!name.contains("foot")) {
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
            assertTrue(!speeds.isEmpty(), name + " never touches the ground");
            speeds.sort(null);
            sum += speeds.get(speeds.size() / 2);
            feet++;
        }
        assertTrue(feet > 0, "no foot joint to measure a stride by");
        return sum / feet;
    }

    /** When the fastest of his hands (or the weapon in one) moves fastest, in seconds into the clip. */
    private static double fastestHand(Samples clip) {
        double best = -1;
        double when = 0;
        for (int j = 0; j < clip.joints().size(); j++) {
            String name = clip.joints().get(j).toLowerCase();
            if (!name.matches(".*(hand|sword|slot).*")) {
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

    /** The hand and foot joints of the hero, every STEP of the clip, as his model plays it. */
    private static Samples sample(HeroLook hero, String clipName) {
        var him = ASSETS.loadModel(hero.model());
        for (var library : hero.animations()) {
            AnimationLibrary.copy(ASSETS.loadModel(library), him, java.util.stream.Stream.concat(hero.clips().stream(),
                    java.util.stream.Stream.of(clipName)).distinct().toList());
        }
        var composer = control(him, AnimComposer.class);
        var skin = control(him, SkinningControl.class);
        var armature = skin.getArmature();
        var joints = armature.getJointList().stream().filter(HeroClipFitTest::measured).toList();
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
            him.updateGeometricState();
            var world = skin.getSpatial().getWorldTransform();
            at.add(joints.stream().map(j -> world.transformVector(j.getModelTransform().getTranslation(), null))
                    .toArray(Vector3f[]::new));
        }
        return new Samples(joints.stream().map(Joint::getName).toList(), at, clip.getLength());
    }

    /** A foot (not a toe), or a hand or what a hand holds (not a finger). */
    private static boolean measured(Joint joint) {
        String name = joint.getName().toLowerCase();
        if (Arrays.stream(new String[] {"toe", "thumb", "index", "mid", "ring", "pinky", "finger"})
                .anyMatch(name::contains)) {
            return false;
        }
        return name.matches(".*(foot|hand|sword|slot).*");
    }

    private static WeaponUpdate.Data weaponOf(String template) {
        var game = Dungeon.world(".....\n.....\n.....\n", SETTINGS).game();
        game.runHeadless(1);
        for (var module : game.getLogic().getThingFactory().findTemplate(template).modules()) {
            if (module instanceof WeaponUpdate.Data weapon) {
                return weapon;
            }
        }
        throw new AssertionError(template + " carries no weapon");
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
