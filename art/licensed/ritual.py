"""The ritualist's Ritual clip, posed on her rig (docs/plan/2026-10-15-seamless-campaign.md, section 4.1).

No library has it, so it is posed on top of her Idle, frame by frame in her armature's space (x to her left,
-y ahead, z up): her arms rise and spread wide to the sides while her face turns up to the sky (0.8 s to rise,
held to 3.3 s), then her hands snap together straight out before her (3.3-3.8 s) and are held there, firing, to
the end -- five seconds, the story's POSE of 150 game frames, played as the busy clip "Ritual" (E67).

Every bone is turned by the difference between where it points now (its head to its child's) and where it should,
so it does not matter which way a bone's own axes lie: the rig straight from export_licensed.py and the same rig
read back from the shipped .glb (add_ritual.py) pose alike.
"""

import math

import bpy
import mathutils
from mathutils import Vector

SECONDS = 5.0
X = Vector((1, 0, 0))
LOOK_UP = {"spine": 0.0, "spine1": -3.0, "spine2": -5.0, "neck": -12.0, "circle": -16.0}
# What each limb bone points at: its child's head.
CHILD = {"shoulder": "forearm", "forearm": "hand"}


def smooth01(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def turned(mat, q):
    """`mat` turned by `q` (armature space) about its own head."""
    return mathutils.Matrix.LocRotScale(mat.translation, (q @ mat.to_quaternion()).normalized(), mat.to_scale())


def two_bone(root, a, b, target, pole):
    """Where the elbow goes, and the wrist, for an arm of lengths a and b from `root` reaching `target`."""
    u = target - root
    d = max(abs(a - b) + 1e-3, min(u.length, (a + b) * 0.999))
    u.normalize()
    x = (a * a - b * b + d * d) / (2 * d)
    v = (pole - root) - u * (pole - root).dot(u)
    v.normalize()
    return root + u * x + v * math.sqrt(max(0.0, a * a - x * x)), root + u * d


def add(arm, scene, fps, idle):
    """The action "Ritual" made on `arm` from its `idle` action, and returned."""
    bones = arm.data.bones
    order = []

    def visit(bone):
        order.append(bone)
        for child in bone.children:
            visit(child)

    for bone in bones:
        if bone.parent is None:
            visit(bone)
    local = {b.name: (b.parent.matrix_local.inverted() @ b.matrix_local) if b.parent else b.matrix_local.copy()
             for b in bones}
    rest = {b.name: b.matrix_local.translation.copy() for b in bones}
    middle_x = rest["COG"].x

    def length(n, m):
        return (rest[m] - rest[n]).length

    ad = arm.animation_data or arm.animation_data_create()
    ad.action = idle
    if hasattr(ad, "action_slot") and idle.slots:
        ad.action_slot = idle.slots[0]
    first, last = (int(v) for v in idle.frame_range)
    span = max(1, last - first)
    frames = int(SECONDS * fps)
    base = []
    for f in range(frames + 1):
        scene.frame_set(first + f % span)
        base.append({pb.name: pb.matrix_basis.copy() for pb in arm.pose.bones})
    made = bpy.data.actions.new("Ritual")
    ad.action = made
    previous = {}
    for f in range(frames + 1):
        t = f / fps
        rise = smooth01(t / 0.8)
        snap = smooth01((t - 3.3) / 0.5)
        spread = rise * (1 - snap)
        mats = {}
        wrist = {}
        for bone in order:
            n = bone.name
            parent = mats[bone.parent.name] if bone.parent else mathutils.Matrix.Identity(4)
            placed = parent @ local[n]
            mat = placed @ base[f][n]
            side, part = n[:2], n[2:]
            if n in LOOK_UP:
                # Face up to the sky while the arms are spread; level again, the chin a little down, as they fire.
                mat = turned(mat, mathutils.Quaternion(X, math.radians(LOOK_UP[n] * spread
                                                                       + (2.0 if n == "circle" else 0.0) * snap)))
            elif side in ("L.", "R.") and part in CHILD:
                sign = 1 if side == "L." else -1
                child = side + CHILD[part]
                now = (mat @ local[child]).translation - mat.translation
                if part == "shoulder":
                    reach = length(n, child) + length(child, side + "hand")
                    at = mat.translation
                    wide = at + Vector((sign * 0.94 * reach * math.cos(math.radians(22)), 0.02,
                                        0.94 * reach * math.sin(math.radians(22))))
                    ahead = Vector((middle_x + sign * 0.035, at.y - 0.92 * reach, at.z - 0.04))
                    idle_wrist = (placed @ base[f][n] @ local[child] @ base[f][child]
                                  @ local[side + "hand"]).translation
                    target = idle_wrist.lerp(wide, spread).lerp(ahead, snap * rise)
                    target += Vector((0, 0, 0.006 * math.sin(f * 2.1))) * snap
                    pole = at + Vector((sign * 0.1, 0.35, -0.25)).lerp(Vector((sign * 0.35, 0.1, -0.25)), snap)
                    elbow, wrist[side] = two_bone(at, length(n, child), length(child, side + "hand"), target, pole)
                    want = elbow - at
                else:
                    want = wrist[side] - mat.translation
                mat = turned(mat, now.normalized().rotation_difference(want.normalized()))
            mats[n] = mat
            b = placed.inverted() @ mat
            pb = arm.pose.bones[n]
            pb.rotation_mode = "QUATERNION"
            q = b.to_quaternion()
            if n in previous:
                q.make_compatible(previous[n])
            previous[n] = q
            pb.location = b.translation
            pb.rotation_quaternion = q
            pb.keyframe_insert("location", frame=f, group=n)
            pb.keyframe_insert("rotation_quaternion", frame=f, group=n)
    made.use_frame_range = True
    made.frame_start, made.frame_end = 0, frames
    made.use_fake_user = True
    ad.action = None
    return made
