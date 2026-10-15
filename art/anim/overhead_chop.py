"""A woodcutter's chop, posed on Quaternius' Universal Animation Library rig, for the heroes to borrow.

Neither library the heroes borrow from has one: UAL holds no tool clip at all, and KayKit's Chopping is made
for a chibi's short arms -- retargeted onto a grown body, the hands hardly leave the chest. So it is posed
here, on the UAL skeleton, and each hero's exporter retargets it as it does every UAL clip
(art/lange/export_lange.py, art/huntress/export_huntress.py):

    sys.path.insert(0, ".../art/anim"); import overhead_chop
    action = overhead_chop.make(ual_armature)      # a new action, "Chop_Overhead", on that armature

Both hands on one haft, as a man splits a log or fells a tree in front of him: from low in front, up over
his head and a little behind it, down hard to waist height in front, a beat stuck in the wood, and back --
LENGTH seconds, its last frame its first so it loops. His body is the first frame of UAL's Idle_Loop, the
spine leaning back as the axe goes up and over into the blow; his hands are placed by two-bone IK on each
arm, baked to plain keys, and the IK taken off again.
"""

import math

import bpy
from mathutils import Matrix, Vector

LENGTH = 1.2  # seconds a chop takes; looped while he works
BASE = "Idle_Loop"  # his body, legs and all, is this clip's first frame
# (seconds, forward, up) of the point between his hands, in heights of him from between his feet.
HANDS = [
    (0.00, 0.26, 0.50),   # low in front, the axe hanging after the last blow
    (0.42, -0.04, 1.10),  # up over his head and a little behind it
    (0.62, 0.36, 0.60),   # the blow: into the trunk at his waist, in front of him
    (0.82, 0.32, 0.54),   # a beat stuck in the wood
    (LENGTH, 0.26, 0.50),
]
# (seconds, degrees forward) his spine leans, shared over its three bones: back as the axe rises, over into the blow.
LEAN = [(0.00, 8.0), (0.42, -10.0), (0.62, 20.0), (0.82, 16.0), (LENGTH, 8.0)]
SPINE = ["spine_01", "spine_02", "spine_03"]
GRIP = 0.05  # heights of him between his two hands up the haft: the left hand above
ARMS = {"hand_l": ("upperarm_l", 1.0), "hand_r": ("upperarm_r", -1.0)}


def _at(keys, t):
    for (t0, *a), (t1, *b) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / (t1 - t0)
            k = k * k * (3 - 2 * k)  # eased in and out
            return [x + (y - x) * k for x, y in zip(a, b)]
    return list(keys[-1][1:])


def _world(arm, bone):
    return arm.matrix_world @ arm.pose.bones[bone].head


def make(arm, name="Chop_Overhead"):
    """Pose and bake the chop on the UAL armature `arm`; returns the action, left unassigned."""
    scene = bpy.context.scene
    fps = scene.render.fps  # keyed at the scene's rate, which the exporter samples at
    base = next(a for a in bpy.data.actions if a.name.split(".")[0] == BASE)
    ad = arm.animation_data or arm.animation_data_create()
    ad.action = base
    if hasattr(ad, "action_slot") and base.slots:
        ad.action_slot = base.slots[0]
    scene.frame_set(int(base.frame_range[0]))
    bpy.context.view_layer.update()

    feet = (_world(arm, "foot_l") + _world(arm, "foot_r")) / 2
    feet.z = min(_world(arm, "ball_l").z, _world(arm, "ball_r").z, feet.z)
    tall = _world(arm, "Head").z - feet.z
    side = (_world(arm, "upperarm_l") - _world(arm, "upperarm_r"))
    side.z = 0
    side.normalize()
    up = Vector((0, 0, 1))
    forward = side.cross(up).normalized()  # his left is +side: forward is left x up

    # The base pose held on every bone; arms and spine moved over it.
    rest = {pb.name: (pb.location.copy(), pb.rotation_quaternion.copy()) for pb in arm.pose.bones}
    for pb in arm.pose.bones:
        pb.rotation_mode = "QUATERNION"
    ad.action = None

    targets = {}
    for hand, (upper, sign) in ARMS.items():
        empty = bpy.data.objects.new(f"ik_{hand}", None)
        scene.collection.objects.link(empty)
        pole = bpy.data.objects.new(f"pole_{hand}", None)
        scene.collection.objects.link(pole)
        # On the forearm, whose tail is the wrist: two bones up from there is the whole arm.
        c = arm.pose.bones[upper.replace("upperarm", "lowerarm")].constraints.new("IK")
        c.target = empty
        c.chain_count = 2
        c.pole_target = pole
        c.pole_angle = math.radians(-90 if sign > 0 else -90)
        targets[hand] = (empty, pole, sign)

    frames = int(round(LENGTH * fps))
    poses = []
    for f in range(frames + 1):
        t = f / fps
        fwd, height = _at(HANDS, t)
        lean = math.radians(_at(LEAN, t)[0])
        for pb in arm.pose.bones:
            pb.location, pb.rotation_quaternion = rest[pb.name][0].copy(), rest[pb.name][1].copy()
        bpy.context.view_layer.update()
        for bone in SPINE:
            pb = arm.pose.bones[bone]
            axis_world = side
            axis_local = (arm.matrix_world @ pb.matrix).to_3x3().inverted() @ axis_world
            pb.rotation_quaternion = pb.rotation_quaternion @ Matrix.Rotation(lean / len(SPINE), 4,
                                                                             axis_local.normalized()).to_quaternion()
            bpy.context.view_layer.update()
        centre = feet + forward * (fwd * tall) + up * (height * tall)
        for hand, (empty, pole, sign) in targets.items():
            empty.location = centre + up * (sign * GRIP / 2 * tall) + side * (sign * 0.03 * tall)
            shoulder = _world(arm, ARMS[hand][0])
            pole.location = shoulder + side * (sign * 0.5 * tall) - up * (0.3 * tall) - forward * (0.1 * tall)
        bpy.context.view_layer.update()
        # The arms as the IK solved them, made plain: each bone set to where it was solved, the IK muted.
        chains = [(ARMS[h][0], ARMS[h][0].replace("upperarm", "lowerarm")) for h in ARMS]
        solved = {b: arm.pose.bones[b].matrix.copy() for chain in chains for b in chain}
        for hand in ARMS:
            arm.pose.bones[ARMS[hand][0].replace("upperarm", "lowerarm")].constraints[-1].mute = True
        for chain in chains:
            for b in chain:
                arm.pose.bones[b].matrix = solved[b]
                bpy.context.view_layer.update()
        poses.append({pb.name: (pb.location.copy(), pb.rotation_quaternion.copy()) for pb in arm.pose.bones})
        for hand in ARMS:
            arm.pose.bones[ARMS[hand][0].replace("upperarm", "lowerarm")].constraints[-1].mute = False
    # Keyed only now, every pose worked out: an action on him while he is posed plays itself over the pose.
    action = bpy.data.actions.new(name)
    ad.action = action
    for f, pose in enumerate(poses):
        for pb in arm.pose.bones:
            pb.location, pb.rotation_quaternion = pose[pb.name]
            pb.keyframe_insert("rotation_quaternion", frame=f + 1, group=pb.name)
            pb.keyframe_insert("location", frame=f + 1, group=pb.name)
    for hand, (empty, pole, _) in targets.items():
        bone = arm.pose.bones[ARMS[hand][0].replace("upperarm", "lowerarm")]
        bone.constraints.remove(bone.constraints[-1])
        bpy.data.objects.remove(empty, do_unlink=True)
        bpy.data.objects.remove(pole, do_unlink=True)
    ad.action = None
    return action
