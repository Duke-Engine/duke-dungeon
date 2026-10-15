"""Makes the two licensed CGTrader women into models the game draws: the queen and the summoner.

    blender -b --factory-startup -P art/licensed/export_licensed.py -- <queen|ritualist> <source.glb> \
        <UAL1_Standard.glb> <KayKit Rig_Medium folder> <out .glb>

    queen      "Dark Elf Shadow Enchantress" (dark+elf.glb)          -> licensed-assets/models/licensed/queen.glb
    ritualist  "Dark Fantasy Female Ritualist" (RITUAL+WOMEN+50k.glb) -> licensed-assets/models/licensed/ritualist.glb

NOT IN THE REPOSITORY. Both are CGTrader Royalty Free: the game may ship them, the public repository
may not. What this writes goes into the git-ignored licensed-assets/ folder, which the build adds to
the game's resources only when it is there (build.gradle.kts); without it every block that names one
draws its stand-in (Monster's Licensed look, see MonsterLook.licensedOr).

Each download is one unrigged mesh in an A pose with one picture. On the way:

A SKELETON MADE FOR HER. Bones are stood at measured points of her body (LANDMARKS, in a frame where
she is 1.7 m tall, her box centred and her feet on the floor), each limb joint moved to the middle of
her flesh round it (refined), and named as Lange's rig is (COG, spine, L.thigh ...) so art/lange/
retarget.py's table for Quaternius' library drives them as it drives him. Weights are Blender's
automatic (bone heat); a vertex heat leaves bare takes its nearest bone.

A DRESS, NOT TROUSERS. Below her hips, whatever is not her leg -- further from the bone than a leg is
thick -- is the skirt: it follows her pelvis and, partly, both thighs by how far across it hangs, and
never a shin, so a stride swings the hem instead of tearing it between her knees (skirt_weights).

CLIPS FROM QUATERNIUS (CC0). Retargeted by role onto her bones (retarget.py), under the names her
block plays (CLIPS). The summoner's summoning gesture is named Ranged_Magic_Summon, as the KayKit clip
her stand-in plays is, so one CastAnim serves both looks.

ONE PICTURE, 2048 AT MOST, base colour only (art/models/base_colour_only.js is run after this), drawn
opaque as the game draws every creature.

METRES, FEET ON THE ORIGIN, FACING +Z, as Lange: her block's ModelScale is her height in units over
1.7 and her Facing 90. WALK printed: the stride one play of her walk carries her, for WalkStride.
"""

import math
import os
import sys

import bpy
import mathutils
from mathutils import Vector

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lange"))
import retarget  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
WHO, SOURCE, UAL_FILE, KAYKIT_DIR, OUT = args
HEIGHT = 1.7
FPS = 24

# Joint heads, measured off front and side renders of her at HEIGHT: (x, y, z), x to her left, -y ahead.
# A limb joint is then moved to the middle of the flesh round it (REFINE).
LANDMARKS = {
    "queen": {
        "COG": (0, -0.08, 1.00), "spine": (0, -0.09, 1.06), "spine1": (0, -0.09, 1.17), "spine2": (0, -0.08, 1.275),
        "neck": (0, -0.04, 1.39), "circle": (0, -0.05, 1.455), "top": (0, -0.05, 1.69),
        "clavicle": (0.04, -0.05, 1.35), "shoulder": (0.20, 0.0, 1.317), "forearm": (0.319, 0.03, 1.137),
        "hand": (0.404, 0.04, 1.009), "fingers": (0.52, 0.09, 0.935),
        "thigh": (0.096, -0.075, 0.967), "leg": (0.174, -0.05, 0.552), "foot": (0.308, -0.01, 0.117),
        "toes": (0.32, -0.085, 0.03), "toe_end": (0.33, -0.13, 0.01),
    },
    "ritualist": {
        "COG": (0.06, -0.22, 1.02), "spine": (0.06, -0.235, 1.084), "spine1": (0.06, -0.24, 1.19),
        "spine2": (0.06, -0.234, 1.286), "neck": (0.06, -0.21, 1.413), "circle": (0.06, -0.234, 1.509),
        "top": (0.06, -0.234, 1.70),
        "clavicle": (0.10, -0.2, 1.37), "shoulder": (0.212, -0.17, 1.317), "forearm": (0.276, -0.17, 1.126),
        "hand": (0.34, -0.18, 0.999), "fingers": (0.42, -0.21, 0.90),
        "thigh": (0.149, -0.22, 0.977), "leg": (0.149, -0.33, 0.53), "foot": (0.145, -0.15, 0.117),
        "toes": (0.145, -0.25, 0.03), "toe_end": (0.145, -0.31, 0.01),
    },
}
# Her right side where it is not her left mirrored about the body's middle (x of the COG): (x) only.
RIGHT_X = {
    "ritualist": {"shoulder": -0.089, "forearm": -0.149, "hand": -0.212, "fingers": -0.29,
                  "thigh": -0.021, "leg": 0.0, "foot": 0.06, "toes": 0.06, "toe_end": 0.06},
}
REFINE = ("forearm", "hand", "leg", "foot")
TRIANGLES = 26000
# Quaternius' clips each plays: (her clip, theirs, how much faster). A tuple of theirs is a Blend.
CLIPS = {
    "queen": [
        ("Idle", "Idle_Loop", 1.0),
        ("Walk", "Walk_Formal_Loop", 1.0),
        ("Attack", "Spell_Simple_Shoot", 1.0),
        ("Hurt", "Hit_Chest", 1.0),
        ("Death", "Death01", 1.5),
        ("Talk", "Idle_Talking_Loop", 1.0),
        ("Cast_Idle", "Spell_Simple_Idle_Loop", 1.0),
    ],
    "ritualist": [
        ("Idle", "Spell_Simple_Idle_Loop", 1.0),
        ("Walk", "Walk_Loop", 1.0),
        ("Attack", "Spell_Simple_Shoot", 1.0),
        ("Hurt", "Hit_Chest", 1.0),
        ("Death", "Death01", 1.5),
    ],
}
# KayKit's (CC0) on the summoner: her summoning, looped while she calls them up.
KAYKIT_CLIPS = {"ritualist": [("Ranged_Magic_Summon", "Rig_Medium_CombatRanged.glb", "Ranged_Magic_Summon", 2.5)]}

SIDES = ("clavicle", "shoulder", "forearm", "hand", "thigh", "leg", "foot", "toes")


def landmarks():
    marks = {k: Vector(v) for k, v in LANDMARKS[WHO].items()}
    mid = marks["COG"].x
    out = {}
    for name, at in marks.items():
        if name in SIDES or name in ("fingers", "toe_end"):
            right = at.copy()
            right.x = RIGHT_X.get(WHO, {}).get(name, 2 * mid - at.x)
            out["L." + name], out["R." + name] = at, right
        else:
            out[name] = at
    return out


def refine(points, at, side):
    """The middle of the flesh round a limb joint: verts within a thin slab across it, on its side."""
    near = [p for p in points if abs(p.z - at.z) < 0.025 and abs(p.x - at.x) < 0.07 and abs(p.y - at.y) < 0.12
            and (p.x - points_mid) * side > 0]
    if len(near) < 8:
        print("REFINE kept", at, len(near))
        return at
    xs, ys = [p.x for p in near], [p.y for p in near]
    return Vector(((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, at.z))


# ---- her ----

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS
bpy.ops.import_scene.gltf(filepath=SOURCE)
body = next(o for o in bpy.data.objects if o.type == "MESH")
for o in [o for o in bpy.data.objects if o is not body]:
    bpy.data.objects.remove(o, do_unlink=True)
body.name = WHO
bpy.context.view_layer.update()
pts = [body.matrix_world @ v.co for v in body.data.vertices]
low = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
high = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
s = HEIGHT / (high.z - low.z)
centre = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z))
body.data.transform(mathutils.Matrix.Scale(s, 4) @ mathutils.Matrix.Translation(-centre) @ body.matrix_world)
body.matrix_world = mathutils.Matrix.Identity(4)

tris = sum(len(p.vertices) - 2 for p in body.data.polygons)
if tris > TRIANGLES:
    mod = body.modifiers.new("Decimate", "DECIMATE")
    mod.decimate_type = "COLLAPSE"
    mod.ratio = TRIANGLES / tris
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.modifier_apply(modifier=mod.name)
print(f"MESH {tris} -> {sum(len(p.vertices) - 2 for p in body.data.polygons)} triangles")

points = [v.co.copy() for v in body.data.vertices]
marks = landmarks()
points_mid = marks["COG"].x
for side, sign in (("L.", 1), ("R.", -1)):
    for joint in REFINE:
        before = marks[side + joint]
        marks[side + joint] = refine(points, before, sign)
        print(f"REFINE {side}{joint} {tuple(round(c, 3) for c in before)} -> "
              f"{tuple(round(c, 3) for c in marks[side + joint])}")
# The shoulder and hip sit as far into the body as the elbow and knee say, front to back.
for side in ("L.", "R."):
    marks[side + "shoulder"].y = marks[side + "forearm"].y
    marks[side + "clavicle"].y = (marks[side + "shoulder"].y + marks["neck"].y) / 2

# Everything moved so her pelvis is over the origin.
shift = Vector((marks["COG"].x, marks["COG"].y, 0))
body.data.transform(mathutils.Matrix.Translation(-shift))
marks = {k: v - shift for k, v in marks.items()}

# ---- the skeleton ----

BONES = [  # (bone, head, tail, parent)
    ("COG", "COG", "spine", None),
    ("spine", "spine", "spine1", "COG"),
    ("spine1", "spine1", "spine2", "spine"),
    ("spine2", "spine2", "neck", "spine1"),
    ("neck", "neck", "circle", "spine2"),
    ("circle", "circle", "top", "neck"),
]
for side in ("L.", "R."):
    BONES += [
        (side + "clavicle", side + "clavicle", side + "shoulder", "spine2"),
        (side + "shoulder", side + "shoulder", side + "forearm", side + "clavicle"),
        (side + "forearm", side + "forearm", side + "hand", side + "shoulder"),
        (side + "hand", side + "hand", side + "fingers", side + "forearm"),
        (side + "thigh", side + "thigh", side + "leg", "COG"),
        (side + "leg", side + "leg", side + "foot", side + "thigh"),
        (side + "foot", side + "foot", side + "toes", side + "leg"),
        (side + "toes", side + "toes", side + "toe_end", side + "foot"),
    ]

arm_data = bpy.data.armatures.new("Armature")
arm = bpy.data.objects.new("Armature", arm_data)
scene.collection.objects.link(arm)
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode="EDIT")
for name, head, tail, parent in BONES:
    eb = arm_data.edit_bones.new(name)
    eb.head, eb.tail = marks[head], marks[tail]
    # Rolled so each bone's X lies across her the same way on both sides (no twist at rest to undo).
    eb.align_roll(Vector((0, -1, 0)) if name.split(".")[-1] not in ("foot", "toes") else Vector((0, 0, 1)))
    if parent:
        eb.parent = arm_data.edit_bones[parent]
        eb.use_connect = False
bpy.ops.object.mode_set(mode="OBJECT")

# ---- weights ----

# Bone heat cannot solve these meshes -- many loose, crossing shells, as such downloads are; it gives up on
# every bone, even on a voxel remesh of her. So each vertex takes the bones nearest it, each by the inverse
# fourth power of its distance from the bone's length: all one bone's along a limb, shared at a joint.
body.parent = arm
mod = body.modifiers.new("Armature", "ARMATURE")
mod.object = arm
bone_of = {b.name: b for b in arm.data.bones}
for name in bone_of:
    body.vertex_groups.new(name=name)
groups = {g.index: g.name for g in body.vertex_groups}


def segment_distance(p, a, b):
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / ab.length_squared))
    return (a + ab * t - p).length, t


segments = {n: (b.head_local.copy(), b.tail_local.copy()) for n, b in bone_of.items()}
# The head's bone runs to the top of her head; a toe's is short. Neither should reach past its flesh.
for v in body.data.vertices:
    d = sorted((max(segment_distance(v.co, a, b)[0], 0.004), n) for n, (a, b) in segments.items())[:3]
    w = [(n, 1.0 / dist ** 4) for dist, n in d]
    total = sum(x for _, x in w)
    for n, x in w:
        if x / total > 0.04:
            body.vertex_groups[n].add([v.index], x / total, "REPLACE")
print(f"WEIGHTS {len(body.data.vertices)} vertices weighted by distance")


def skirt_weights():
    """Below her hips, what is further from her leg than a leg is thick follows her pelvis and both thighs."""
    hip_z = (marks["L.thigh"].z + marks["R.thigh"].z) / 2
    legs = {side: [(marks[side + a], marks[side + b]) for a, b in (("thigh", "leg"), ("leg", "foot"))]
            for side in ("L.", "R.")}
    changed = 0
    for v in body.data.vertices:
        p = v.co
        if p.z > hip_z - 0.04:
            continue
        # Her fingers hang as low as her hips: an arm's vertex is never the skirt's.
        if any(groups[g.group].split(".")[-1] in ("hand", "forearm", "shoulder") and g.weight > 0.2 for g in v.groups):
            continue
        # How thick her leg is here: a thigh at the hip, an ankle at the floor.
        t = max(0.0, min(1.0, p.z / hip_z))
        thick = 0.05 + 0.05 * t
        d = min(segment_distance(p, a, b)[0] for side in legs for a, b in legs[side])
        if d < thick:
            continue
        # Across her: where her two legs are at this height.
        def leg_x(side):
            (a, b), (c, e) = legs[side]
            seg = (a, b) if p.z >= b.z else (c, e)
            k = (p.z - seg[1].z) / max(1e-6, seg[0].z - seg[1].z)
            return seg[1].x + (seg[0].x - seg[1].x) * max(0.0, min(1.0, k))
        xl, xr = leg_x("L."), leg_x("R.")
        across = max(0.0, min(1.0, (p.x - xr) / max(1e-6, xl - xr)))
        share = 0.6 * max(0.25, min(1.0, 1.0 - (d - thick) / 0.4))
        skirt = {"COG": 1.0 - share, "L.thigh": share * across, "R.thigh": share * (1.0 - across)}
        # Blended into the leg's own weights over the first 4 cm, so the slit's edge does not crease.
        blend = min(1.0, (d - thick) / 0.04)
        own = {groups[g.group]: g.weight for g in v.groups}
        total = sum(own.values()) or 1.0
        mixed = {n: (1 - blend) * own.get(n, 0.0) / total + blend * skirt.get(n, 0.0) for n in set(own) | set(skirt)}
        for g in list(v.groups):
            body.vertex_groups[groups[g.group]].remove([v.index])
        for n, w in mixed.items():
            if w > 1e-3:
                body.vertex_groups[n].add([v.index], w, "REPLACE")
        changed += 1
    print(f"SKIRT {changed} vertices hang from her hips")


skirt_weights()
bpy.context.view_layer.objects.active = body
bpy.ops.object.vertex_group_limit_total(group_select_mode="ALL", limit=4)
bpy.ops.object.vertex_group_normalize_all(lock_active=False)

# ---- the clips ----


class Her:
    """Her rig at rest, as retarget.retarget reads Lange's (no fingers, no sword)."""

    def __init__(self, arm):
        self.arm = arm
        self.order = retarget._order(arm)
        self.rest = {b.name: b.matrix_local.copy() for b in arm.data.bones}
        self.local = {b.name: (b.parent.matrix_local.inverted() @ b.matrix_local) if b.parent else b.matrix_local.copy()
                      for b in arm.data.bones}
        self.heads = {n: m.translation.copy() for n, m in self.rest.items()}
        self.finger_pose = {}


# Where her directions run to, as LANGE_ENDS says his: her spine2 to her neck, her hand along its length.
retarget.LANGE_ENDS.update({"COG": "spine", "spine2": "neck", "L.hand": None, "R.hand": None})
# Her hands ride her forearms, as his do: no hand of theirs points where hers does at rest.
UAL = dict(retarget.UAL)
UAL.pop("sword")
KAYKIT = {k: v for k, v in retarget.KAYKIT.items() if k != "sword"}

her = Her(arm)
clips = []


def import_gltf(path):
    before = set(bpy.data.objects)
    actions_before = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=path)
    objs = [o for o in bpy.data.objects if o not in before]
    return objs, next(o for o in objs if o.type == "ARMATURE"), set(bpy.data.actions) - actions_before


def borrow(path, table, wanted):
    objs, source, actions = import_gltf(path)
    by_name = {a.name: a for a in actions}
    for a in actions:  # theirs out of the way of hers, which may share a name (Ranged_Magic_Summon)
        a.name = "source." + a.name
    for name, theirs, speed in wanted:
        clips.append(retarget.retarget(scene, her, source, by_name[theirs], table, False, name, FPS, speed))
    for o in objs:
        bpy.data.objects.remove(o, do_unlink=True)
    for a in actions:
        bpy.data.actions.remove(a)


borrow(UAL_FILE, UAL, CLIPS[WHO])
for name, file, theirs, speed in KAYKIT_CLIPS.get(WHO, []):
    borrow(os.path.join(KAYKIT_DIR, file), KAYKIT, [(name, theirs, speed)])
    # From the waist up only: the chibi squats as it summons, which on her long legs is a collapse.
    action = clips[-1]
    ad = arm.animation_data
    ad.action = action
    ad.action_slot = action.slots[0]
    for frame in range(int(action.frame_range[0]), int(action.frame_range[1]) + 1):
        for pb in arm.pose.bones:
            if pb.name == "COG" or pb.name.split(".")[-1] in ("thigh", "leg", "foot", "toes"):
                pb.location = (0, 0, 0)
                pb.rotation_quaternion = (1, 0, 0, 0)
                pb.keyframe_insert("location", frame=frame, group=pb.name)
                pb.keyframe_insert("rotation_quaternion", frame=frame, group=pb.name)

ad = arm.animation_data
ad.action = None
for action in clips:
    track = ad.nla_tracks.new()
    track.name = action.name
    track.strips.new(action.name, int(action.frame_start), action)
for pb in arm.pose.bones:
    pb.matrix_basis = mathutils.Matrix.Identity(4)


def stride(action):
    """Metres one play of a walk carries her: the median speed of a planted foot, times the clip's length."""
    for t in ad.nla_tracks:
        t.mute = True
    ad.action = action
    ad.action_slot = action.slots[0]
    first, last = (int(f) for f in action.frame_range)
    feet = {"L.foot": [], "R.foot": []}
    for f in range(first, last + 1):
        scene.frame_set(f)
        for foot in feet:
            feet[foot].append((arm.matrix_world @ arm.pose.bones[foot].head).copy())
    speeds = []
    for track in feet.values():
        lo, hi = min(p.z for p in track), max(p.z for p in track)
        down = lo + 0.1 * (hi - lo)
        sp = sorted((Vector((b.x - a.x, b.y - a.y)).length * FPS) for a, b in zip(track, track[1:])
                    if a.z <= down and b.z <= down)
        speeds.append(sp[len(sp) // 2] if sp else 0.0)
    ad.action = None
    for t in ad.nla_tracks:
        t.mute = False
    return sum(speeds) / len(speeds) * (last - first) / FPS


walk = next(a for a in clips if a.name == "Walk")
print(f"WALK stride {stride(walk):.3f} m a play of {(walk.frame_range[1] - walk.frame_range[0]) / FPS:.3f}s "
      f"(WalkStride = this x ModelScale)")
scene.frame_set(0)

# ---- the picture ----

for material in body.data.materials:
    if not material or not material.use_nodes:
        continue
    material.surface_render_method = "DITHERED"
    for node in material.node_tree.nodes:
        if node.type == "BSDF_PRINCIPLED":
            alpha = node.inputs["Alpha"]
            for link in list(alpha.links):
                material.node_tree.links.remove(link)
            alpha.default_value = 1.0
        if node.type == "TEX_IMAGE" and node.image and max(node.image.size) > 2048:
            node.image.scale(2048, 2048)

# ---- the file ----

body.parent = arm
target = OUT
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(
    filepath=target,
    export_format="GLB",
    export_image_format="JPEG",
    export_jpeg_quality=88,
    export_animations=True,
    export_animation_mode="ACTIONS",
    export_anim_slide_to_zero=True,
    export_force_sampling=True,
    export_skins=True,
    export_apply=False,
    export_yup=True,
)
print(f"EXPORTED {target} {os.path.getsize(target) / 1e6:.2f} MB, {len(arm.data.bones)} bones, "
      f"{sum(len(p.vertices) - 2 for p in body.data.polygons)} triangles, clips {[a.name for a in clips]}")
