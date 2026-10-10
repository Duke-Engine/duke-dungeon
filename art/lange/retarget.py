"""Clips made for other rigs, put onto Lange's.

His download moves him two ways, idling and walking. Everything else he does
is borrowed from two CC0 libraries made on other skeletons: Quaternius'
Universal Animation Library (a UE-style rig: pelvis, spine_01..03, upperarm_l
...) and KayKit's Character Animations (Rig_Medium: hips, chest,
upperarm.l ...). A bone of his is driven by a bone of theirs by ROLE, from the
tables below; a bone of his no table names follows its parent as it rests
(and his fingers keep the grip his own idle closes them in).

HOW A BONE IS MOVED. The three rigs rest differently -- his in an A pose with
his bones pointing every which way (FBX), theirs in a T pose -- so a rotation
cannot be copied as it is. Each bone of each rig has a DIRECTION at rest: from
its head to the head of the joint it carries (the knee for a thigh) or, at the
end of a chain, its own length. At rest his bone is turned (by the shortest
arc) to point where theirs points; from then on it is moved by exactly what
moves theirs, in world space. So his arm hangs where their arm hangs, at every
frame, whatever either rig's rest was, and the twist about the arm comes along.

HIS HIPS go up and down with theirs, scaled by how much taller he stands, and
across as theirs do, so a fall falls; nothing walks him anywhere (every clip
used is made in place).

MIRRORED. He holds his sword in his LEFT hand and every KayKit swing is made
right-handed, so a clip may be taken mirrored: their right side drives his
left, every rotation and position reflected through the plane down the middle
of the body.

THE SWORD. On a KayKit clip the sword's bone is driven by the hand slot the
clip was made to swing a weapon in -- so the blade points where theirs did --
and is put back in his fist every frame (its grip at rest kept on the palm).
On the Quaternius clips it stays in his hand as it rests.
"""

import math

import bpy
import mathutils

M = mathutils.Matrix.Diagonal((-1.0, 1.0, 1.0, 1.0))  # the reflection through the body's middle

# His bone -> (theirs, where its direction runs to: a joint's head, "UP", or None for its own length).
KAYKIT = {
    "COG": ("hips", "spine"),
    "spine": ("spine", "chest"),
    "spine2": ("chest", "head"),
    "circle": ("head", None),
    "L.shoulder": ("upperarm.l", "lowerarm.l"),
    "L.forearm": ("lowerarm.l", "wrist.l"),
    "R.shoulder": ("upperarm.r", "lowerarm.r"),
    "R.forearm": ("lowerarm.r", "wrist.r"),
    "L.thigh": ("upperleg.l", "lowerleg.l"),
    "L.leg": ("lowerleg.l", "foot.l"),
    "L.foot": ("foot.l", "toes.l"),
    "L.toes": ("toes.l", None),
    "R.thigh": ("upperleg.r", "lowerleg.r"),
    "R.leg": ("lowerleg.r", "foot.r"),
    "R.foot": ("foot.r", "toes.r"),
    "R.toes": ("toes.r", None),
    "sword": ("handslot.l", None),
}
UAL = {
    "COG": ("pelvis", "spine_01"),
    "spine": ("spine_01", "spine_02"),
    "spine1": ("spine_02", "spine_03"),
    "spine2": ("spine_03", "neck_01"),
    "neck": ("neck_01", "Head"),
    "circle": ("Head", None),
    "L.clavicle": ("clavicle_l", "upperarm_l"),
    "L.shoulder": ("upperarm_l", "lowerarm_l"),
    "L.forearm": ("lowerarm_l", "hand_l"),
    "R.clavicle": ("clavicle_r", "upperarm_r"),
    "R.shoulder": ("upperarm_r", "lowerarm_r"),
    "R.forearm": ("lowerarm_r", "hand_r"),
    "L.thigh": ("thigh_l", "calf_l"),
    "L.leg": ("calf_l", "foot_l"),
    "L.foot": ("foot_l", "ball_l"),
    "L.toes": ("ball_l", None),
    "R.thigh": ("thigh_r", "calf_r"),
    "R.leg": ("calf_r", "foot_r"),
    "R.foot": ("foot_r", "ball_r"),
    "R.toes": ("ball_r", None),
    "sword": ("hand_l", "FORWARD"),
}
# Where each of HIS directions runs to (his bones point every which way at rest, so his own lengths say little).
LANGE_ENDS = {
    "COG": "spine1", "spine": "spine1", "spine1": "spine2", "spine2": "spine3", "neck": "circle", "circle": "UP",
    "L.clavicle": "L.shoulder", "L.shoulder": "L.forearm", "L.forearm": None,
    "R.clavicle": "R.shoulder", "R.shoulder": "R.forearm", "R.forearm": None,
    "L.thigh": "L.leg", "L.leg": "L.foot", "L.foot": "L.toes", "L.toes": None,
    "R.thigh": "R.leg", "R.leg": "R.foot", "R.foot": "R.toes", "R.toes": None,
    "sword": None,
}
FINGERS = ("thumb", "index", "mid", "ring", "pinky")


def swap_side(name):
    for a, b in ((".l", ".r"), ("_l", "_r")):
        if name.endswith(a):
            return name[: -len(a)] + b
        if name.endswith(b):
            return name[: -len(b)] + a
    return name


def _direction(matrices, heads, bone, end):
    """A bone's direction in armature space: to `end`'s head, straight up, or along its own length."""
    if end == "UP":
        return mathutils.Vector((0, 0, 1))
    if end is None:
        return (matrices[bone].to_3x3() @ mathutils.Vector((0, 1, 0))).normalized()
    return (heads[end] - heads[bone]).normalized()


def _order(arm):
    out = []

    def walk(bone):
        out.append(bone)
        for child in bone.children:
            walk(child)

    for root in [b for b in arm.data.bones if b.parent is None]:
        walk(root)
    return out


class Target:
    """Lange's rig at rest, read once."""

    def __init__(self, arm, finger_pose):
        self.arm = arm
        self.order = _order(arm)
        self.rest = {b.name: b.matrix_local.copy() for b in arm.data.bones}
        self.local = {b.name: (b.parent.matrix_local.inverted() @ b.matrix_local) if b.parent else b.matrix_local.copy()
                      for b in arm.data.bones}
        self.heads = {n: m.translation.copy() for n, m in self.rest.items()}
        self.finger_pose = finger_pose
        forearm = self.rest["L.forearm"]
        palm = (self.heads["L.mid_base"] + self.heads["L.mid_1"]) / 2
        self.palm = forearm.inverted() @ palm  # in the forearm's frame
        self.grip = self.rest["sword"].inverted() @ palm  # the point of the sword his palm is on


def retarget(scene, target, source_arm, action, table, mirror, name, fps, speed=1.0):
    """`action` on `source_arm` played onto Lange as a new action called `name`, `speed` times as fast."""
    ad = source_arm.animation_data or source_arm.animation_data_create()
    ad.action = action
    if action.slots:
        ad.action_slot = action.slots[0]
    first, last = action.frame_range
    keys = int(math.ceil((last - first) / speed))

    def theirs(bone):
        return swap_side(bone) if mirror else bone

    src_rest = {b.name: source_arm.matrix_world @ b.matrix_local for b in source_arm.data.bones}
    src_heads = {n: m.translation.copy() for n, m in src_rest.items()}

    def reflect(matrix):
        return M @ matrix @ M if mirror else matrix

    # Per bone of his: A such that his world rotation is (their world rotation) @ A.
    offsets = {}
    for mine, (their, their_end) in table.items():
        if mine not in target.rest:
            continue
        s, s_end = theirs(their), theirs(their_end) if their_end else None
        rs = reflect(src_rest[s]).to_3x3().normalized()
        if s_end == "FORWARD":  # a fist held out at rest: its blade would point ahead of him
            ds = mathutils.Vector((0, -1, 0))
        elif s_end:
            ds = (src_heads[s_end] - src_heads[s]).normalized()
            if mirror:
                ds = M.to_3x3() @ ds
        else:
            ds = (rs @ mathutils.Vector((0, 1, 0))).normalized()
        rt = target.rest[mine].to_3x3().normalized()
        dt = _direction(target.rest, target.heads, mine, LANGE_ENDS.get(mine))
        c = ds.rotation_difference(dt).to_matrix()
        offsets[mine] = (s, rs.inverted() @ c.inverted() @ rt)

    hips_name = theirs(table["COG"][0])
    hips_rest = reflect(src_rest[hips_name]).translation
    # Up and down by how much longer his legs are than theirs (a chibi's hips bob in a short leg).
    rise = target.heads["L.thigh"].z / reflect(src_rest[theirs(table["L.thigh"][0])]).translation.z

    new = bpy.data.actions.new(name)
    tad = target.arm.animation_data or target.arm.animation_data_create()
    tad.action = new
    previous = {}
    for key in range(keys + 1):
        at = first + min(key * speed, last - first)
        scene.frame_set(int(at), subframe=at - int(at))
        theirs_now = {}
        for mine, (s, _) in offsets.items():
            theirs_now[mine] = reflect(source_arm.matrix_world @ source_arm.pose.bones[s].matrix)
        hips_now = reflect(source_arm.matrix_world @ source_arm.pose.bones[hips_name].matrix).translation
        mats = {}
        for bone in target.order:
            n = bone.name
            parent = mats[bone.parent.name] if bone.parent else mathutils.Matrix.Identity(4)
            placed = parent @ target.local[n]
            if n in offsets:
                rot = (theirs_now[n].to_3x3().normalized() @ offsets[n][1]).normalized()
                pos = placed.translation
                if n == "COG":
                    pos = target.heads["COG"] + (hips_now - hips_rest) * rise
                if n == "sword":
                    pos = (mats["L.forearm"] @ target.palm) - rot @ target.grip
                mat = mathutils.Matrix.LocRotScale(pos, rot, None)
            elif n in target.finger_pose:
                mat = placed @ target.finger_pose[n]
            else:
                mat = placed
            mats[n] = mat
            basis = placed.inverted() @ mat
            pb = target.arm.pose.bones[n]
            pb.rotation_mode = "QUATERNION"
            q = basis.to_quaternion()
            if n in previous:
                q.make_compatible(previous[n])
            previous[n] = q
            pb.location = basis.translation
            pb.rotation_quaternion = q
            pb.keyframe_insert("location", frame=key, group=n)
            pb.keyframe_insert("rotation_quaternion", frame=key, group=n)
    new.use_frame_range = True
    new.frame_start, new.frame_end = 0, keys
    new.use_fake_user = True
    print(f"CLIP {name} <- {action.name}{' mirrored' if mirror else ''} x{speed} = {keys / fps:.3f}s")
    return new
