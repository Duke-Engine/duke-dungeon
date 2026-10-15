"""Makes neilhancart's "Huntress" into the Rogue hero's model.

    ./gradlew exportHuntress -PhuntressSource="C:/Users/me/Downloads/huntress" \
        -PualSource=".../Universal Animation Library[Standard]/Unreal-Godot/UAL1_Standard.glb"
    blender -b --factory-startup -P art/huntress/export_huntress.py -- \
        <scene.gltf> <animations folder> <UAL1_Standard.glb> <out .glb> [<folder for the downsized pictures>]

The KayKit libraries are read from the animations folder and her clips written into it;
Quaternius' Universal Animation Library (CC0) is not in the repository.

The source is Sketchfab's glTF of "Huntress" (Wilson / neilhancart, CC BY 4.0 -- see
CREDITS.md): a statue of her on a stone pedestal beside a banner, 63k triangles,
438 bones, a dozen 4096 pictures, and one clip -- the banner and her cloth in the
wind, holding the statue's pose. The game wants a hero, so on the way:

ONLY HER. The pedestal, the banner and its pole are dropped, and the banner's 101
bones with them; so is the arrow the statue holds to her cheek. (The importer's own
bone-shape Icosphere is not in the file.)

HER SKELETON IN HER BODY. Blender stands the QuickRig's rest skeleton 1.46 times
the size of the body it skins; it is laid back onto her by one measured scale and
shift (see as_bound).

HER CLOTH FOLLOWS HER LEGS. Her cape, sash and loincloth hang on five chains of cloth
bones -- 270 of them -- that only the statue's wind ever moved. Their weights go to her
hips, and from the top of her thighs to her knees more and more to the thighs, so
the skirt swings with her stride (cloth_onto_her); the chains are deleted; bones nothing is weighted to are pruned, and no
vertex is moved by more than 4 bones, which is what jME skins with. 438 bones come
down to the skeleton and her fingers.

ABOUT 24k TRIANGLES. Each mesh is collapsed by its own share; her face and hands
are held back from it, so the triangles come off her boots and corset first.

ONE PICTURE A MATERIAL. The game draws a creature with its first picture over flat
light (DukeRtsApp.creatureMaterial), so a normal or metal map would be read as her
colour. Only the base colour is kept, at 1024 (512 for her hair, quiver and arrows):
she is a hundred pixels tall on the screen, and 2048 for her face cost 16 MB of
memory a load for nothing anyone could see.

SIZED AS THE KAYKIT ROGUE. 2.18 tall with her feet on the origin and facing glTF's
+Z, as the KayKit heroes are -- so her block keeps ModelScale 5.50 and Facing 90.

THE ROGUE'S CLIPS, ON HER SKELETON. Her rig is Maya's HumanIK and the clips are
KayKit's (the bow) and Quaternius' (a grown body hurt, idling, rolling, casting), so
no library can be copied onto her by bone name (AnimationLibrary.copy). Each clip in
CLIPS is retargeted here: every limb bone takes its library bone's turn from rest,
after her A-pose limbs are swung onto their T-pose; her hips, spine and head keep their own rest and take
the turn alone; hands and feet ride with the forearm and shin. They are written
under their own names into animations/characters/huntress.glb, beside the KayKit
libraries, and her animation set (data/animations/huntress.duke) names that file.

A FIST ROUND THE BOW. A bone `handslot.l` is added in her left hand where her curled
fingers close round a grip of the bow's radius (fist), along her knuckles, with the
string back up her forearm; it rides her hand, so the bow never leaves her fist. In the
KayKit clips her wrist is turned about her forearm until her knuckles run where their
bow runs (turned_to_the_bow), so the bow still stands up to be drawn. (A stray joint the
download left in her middle finger is put back first: mend_stray_joints.)
And `sheath.l` on her left hip, pointing down her thigh, for her dagger (add_sheath).

HER HANDS AND EYES. No library moves her fingers, so every clip keys a curl into
them: her left hand closed round the bow, her right relaxed, or hooked on the string
in the shooting clips. Her eyes were blank white balls; they are given a pale, faintly
violet iris, a pupil and a highlight, so the stare stays eerie (paint_eyes).
"""

import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, ANIMATIONS, UAL_SOURCE, OUT = args[0], args[1], args[2], args[3]
PICTURES = args[4] if len(args) > 4 else None
CLIPS_OUT = os.path.join(ANIMATIONS, "huntress.glb")  # her clips, written beside the KayKit libraries

HEIGHT = 2.18          # the KayKit rogue's, so her block keeps his ModelScale
PREFIX = "QuickRigCharacter_"

KAYKIT, UAL = "KayKit", "Quaternius UAL"
# Every clip she is given, and whose: the Rogue's block, his portrait and his skills' gestures name them.
# KayKit's bow clips (no other library holds a bow); Quaternius' where a grown body moves better than a chibi.
CLIPS = {
    "Ranged_Bow_Idle": KAYKIT, "Ranged_Bow_Aiming_Idle": KAYKIT, "Ranged_Bow_Draw": KAYKIT,
    "Ranged_Bow_Release": KAYKIT, "Ranged_Bow_Release_Up": KAYKIT, "Spawn_Ground": KAYKIT,
    "Running_A": KAYKIT, "Death_A": KAYKIT,
    "Idle_Loop": UAL, "Hit_Chest": UAL, "Death01": UAL, "Roll": UAL,
    "Spell_Simple_Shoot": UAL,
    # Work, for the food state: the engine loops one by its exact name while she works (E67, GameObject.busy).
    # Written under RENAME's names -- Chop, Gather, Cook -- the same three the knight has.
    "Sword_Attack": UAL, "Interact": UAL, "Fixing_Kneeling": UAL,
}
# The name a clip is written under, where it is not its library's.
RENAME = {"Sword_Attack": "Chop", "Interact": "Gather", "Fixing_Kneeling": "Cook"}
# Frames a clip is cut to. The client plays her Attack from the frame the arrow leaves, once a shot, and she
# shoots every ReloadFrames 24 = 0.8s: Ranged_Bow_Release looses on its first frames and has settled by its 19th,
# so its last half second, which every next shot cut off, is dropped (HeroClipFitTest).
CUT = {"Ranged_Bow_Release": 19}
LIBRARIES = ["ranged.glb", "movement.glb", "general.glb"]
# Made on the spot, but their hips wander: the drift from first frame to last is taken out.
IN_PLACE = {"Idle_Loop", "Roll", "Spell_Simple_Shoot", "Running_A", "Interact", "Fixing_Kneeling"}

# Hers <- theirs, a table a library. ALIGN: her bone is swung from her A-pose onto their T-pose
# first, by the direction to the bone named; RIDE: it keeps her bone's rest relative to the one
# named; neither: it takes their turn alone. Her shoulders follow Quaternius' clavicles; KayKit has none.
MAP = {
    KAYKIT: {
        "Hips_03": "hips", "Spine_012": "spine", "Spine2_014": "chest", "Head_062": "head",
        "LeftArm_016": "upperarm.l", "LeftForeArm_017": "lowerarm.l", "LeftHand_018": "wrist.l",
        "RightArm_039": "upperarm.r", "RightForeArm_040": "lowerarm.r", "RightHand_041": "wrist.r",
        "LeftUpLeg_04": "upperleg.l", "LeftLeg_05": "lowerleg.l", "LeftFoot_06": "foot.l",
        "LeftToeBase_07": "toes.l", "RightUpLeg_08": "upperleg.r", "RightLeg_09": "lowerleg.r",
        "RightFoot_010": "foot.r", "RightToeBase_011": "toes.r",
    },
    UAL: {
        "Hips_03": "pelvis", "Spine_012": "spine_01", "Spine1_013": "spine_02", "Spine2_014": "spine_03",
        "Neck_061": "neck_01", "Head_062": "Head",
        "LeftShoulder_015": "clavicle_l", "RightShoulder_038": "clavicle_r",
        "LeftArm_016": "upperarm_l", "LeftForeArm_017": "lowerarm_l", "LeftHand_018": "hand_l",
        "RightArm_039": "upperarm_r", "RightForeArm_040": "lowerarm_r", "RightHand_041": "hand_r",
        "LeftUpLeg_04": "thigh_l", "LeftLeg_05": "calf_l", "LeftFoot_06": "foot_l", "LeftToeBase_07": "ball_l",
        "RightUpLeg_08": "thigh_r", "RightLeg_09": "calf_r", "RightFoot_010": "foot_r", "RightToeBase_011": "ball_r",
    },
}
ALIGN = {
    "LeftArm_016": "LeftForeArm_017", "LeftForeArm_017": "LeftHand_018",
    "RightArm_039": "RightForeArm_040", "RightForeArm_040": "RightHand_041",
    "LeftUpLeg_04": "LeftLeg_05", "LeftLeg_05": "LeftFoot_06",
    "RightUpLeg_08": "RightLeg_09", "RightLeg_09": "RightFoot_010",
}
RIDE = {
    "LeftHand_018": "LeftForeArm_017", "RightHand_041": "RightForeArm_040",
    "LeftFoot_06": "LeftLeg_05", "LeftToeBase_07": "LeftLeg_05",
    "RightFoot_010": "RightLeg_09", "RightToeBase_011": "RightLeg_09",
}

# Her fingers, which no library moves: a curl in degrees at each joint from the knuckle out, a hand.
RELAXED = {"finger": (18, 25, 15), "thumb": (8, 10, 5)}
GRIP = {"finger": (70, 85, 55), "thumb": (40, 45, 30)}  # round the bow, in her left hand, always
# The elven bow's grip, a cylinder this round where her hand holds it (at its Scale 0.8), and half the
# thickness of a finger of hers: the fist is closed to wrap one with the other (see fist).
HANDLE_RADIUS, FINGER_HALF = 0.022, 0.006
DRAW = {"Index": (40, 55, 30), "MiddleF": (40, 55, 30), "Ring": (65, 80, 50), "Pinky": (70, 85, 55),
        "thumb": (30, 30, 20)}  # two fingers hooked on the string, in her right hand while she shoots
DRAWING = {"Ranged_Bow_Aiming_Idle", "Ranged_Bow_Draw", "Ranged_Bow_Release", "Ranged_Bow_Release_Up"}

# Her cloth -- cape, sash, loincloth -- hung from her hips; from the top of her thighs down to her
# knees it follows them more and more, up to this share.
CLOTH_FOLLOW = 0.7

# Meshes by what they are (the source names them Object_4xx).
BANNER = {"Object_17"}
HAIR = "Object_477"
HANDS = "Object_469"  # her arms and legs, the hands among them
SHARE = {  # how much of a mesh is kept; the rest default to DEFAULT_SHARE
    HAIR: 0.25, "Object_467": 0.6, "Object_469": 0.42, "Object_472": 1.0, "Object_473": 1.0,
    "Object_456": 1.0, "Object_458": 1.0, "Object_460": 1.0, "Object_462": 1.0,
}
DEFAULT_SHARE = 0.4
PROTECTED = {"Object_467", "Object_469"}  # her skin: the face and hands are held back from the collapse
# Her hair (a flat black), and her quiver and arrows: half the size of the rest.
SMALL_PICTURES = {"aiStandardSurface9", "aiStandardSurface12", "aiStandardSurface2"}


def tris(meshes):
    return sum(sum(len(p.vertices) - 2 for p in m.data.polygons) for m in meshes)


def select(objects, active):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = active


# ---- her, alone ----

def open_her():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=SOURCE, bone_heuristic="BLENDER")
    rig = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    meshes = [o for o in bpy.data.objects
              if o.type == "MESH" and o.parent == rig and o.name not in BANNER]
    for obj in list(bpy.data.objects):
        if obj.type == "MESH" and obj not in meshes:
            bpy.data.objects.remove(obj, do_unlink=True)
    return rig, meshes


def joints_in_the_mesh(rig, meshes):
    """Where her body actually bends at each joint, and where the rig says it does, both at rest in the world.

    Where a bone's skin meets its parent's -- the vertices weighted to both -- is the joint, whatever the
    rig's numbers say: the middle of those, weighted by the lesser of the two weights.
    """
    found = {}
    for obj in meshes:
        names = {g.index: g.name for g in obj.vertex_groups}
        for v in obj.data.vertices:
            weights = {names[g.group]: g.weight for g in v.groups if g.weight > 0.05}
            where = obj.matrix_world @ v.co
            for name, weight in weights.items():
                bone = rig.data.bones.get(name)
                if name.startswith(PREFIX) and bone.parent is not None and bone.parent.name in weights:
                    share = min(weight, weights[bone.parent.name])
                    total = found.setdefault(name, [Vector(), 0.0])
                    total[0] += where * share
                    total[1] += share
    return {name: (rig.matrix_world @ rig.data.bones[name].head_local, total / share)
            for name, (total, share) in found.items() if share > 0.5}


def as_bound(rig, meshes):
    """Her rig and meshes with no transform over them, the skeleton standing inside the body it skins.

    The download is a Maya QuickRig with scale-compensation joints under a 0.01 scale, and Blender's rest
    skeleton comes in 1.46 times the size of her body -- her head joint above her hair, her hips at her
    chest. The statue's own clip hides it (its keys put the skin together), but turned about those joints
    her leg swings from her waist. Measured, the two differ by one scale and one shift and nothing else
    (every joint within a few centimetres of it), so the skeleton is laid onto her body by that one map.
    """
    import numpy as np
    rig.animation_data_clear()
    for bone in rig.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)
    bpy.context.view_layer.update()
    pairs = joints_in_the_mesh(rig, meshes)
    rig_at = np.array([list(a) for a, _ in pairs.values()])
    body_at = np.array([list(b) for _, b in pairs.values()])
    rm, bm = rig_at.mean(0), body_at.mean(0)
    scale = ((rig_at - rm) * (body_at - bm)).sum() / ((rig_at - rm) ** 2).sum()
    shift = bm - scale * rm
    worst = np.linalg.norm(scale * rig_at + shift - body_at, axis=1).max()
    print(f"HUNTRESS skeleton laid on her body: scale {scale:.4f}, {len(pairs)} joints, worst {worst:.3f}")
    if worst > 0.06:
        raise SystemExit("her skeleton is not one scale and shift away from her body; look before exporting")
    onto = Matrix.Translation(Vector(shift)) @ Matrix.Scale(scale, 4)
    for obj in [rig] + meshes:
        world = obj.matrix_world.copy()
        obj.parent = None
        obj.matrix_world = world
    select([rig] + meshes, rig)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    for bone in rig.data.edit_bones:
        bone.use_connect = False
    for bone in rig.data.edit_bones:
        bone.head, bone.tail = onto @ bone.head, onto @ bone.tail
    bpy.ops.object.mode_set(mode="OBJECT")
    for obj in meshes:
        obj.parent = rig
        obj.matrix_parent_inverse = Matrix.Identity(4)
    for obj in list(bpy.data.objects):  # the Sketchfab and Maya nodes that held them
        if obj != rig and obj not in meshes:
            bpy.data.objects.remove(obj, do_unlink=True)
    for action in list(bpy.data.actions):  # the statue's wind
        bpy.data.actions.remove(action)


def mend_stray_joints(rig):
    """A joint the download left far from its finger -- her left middle finger's second joint stood
    at the statue's origin, so bending that finger swung it about a point a metre away -- is put back
    halfway between the joints either side of it. Moving a joint at rest moves no vertex."""
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    for bone in rig.data.edit_bones:
        if bone.parent is None or not bone.children:
            continue
        after = bone.children[0].head
        if (bone.head - bone.parent.head).length > 4 * (after - bone.parent.head).length:
            bone.head = (bone.parent.head + after) / 2
            bone.tail = after
            print(f"HUNTRESS mended {bone.name}: put halfway along its finger")
    bpy.ops.object.mode_set(mode="OBJECT")


def stand_her_up(rig, meshes):
    """Her feet on the origin, HEIGHT tall, written into the bones and vertices themselves."""
    as_bound(rig, meshes)
    mend_stray_joints(rig)
    bpy.context.view_layer.update()
    points = [v.co for m in meshes for v in m.data.vertices]
    lo = Vector([min(p[i] for p in points) for i in range(3)])
    hi = Vector([max(p[i] for p in points) for i in range(3)])
    hips = rig.data.bones[PREFIX + "Hips_03"].head_local
    factor = HEIGHT / (hi.z - lo.z)
    move = Matrix.Scale(factor, 4) @ Matrix.Translation((-hips.x, -hips.y, -lo.z))
    for obj in [rig] + meshes:
        obj.matrix_world = move
    bpy.context.view_layer.update()
    select([rig] + meshes, rig)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    # Her root joint stood at the statue's origin, under the pedestal: it goes to her feet. Nothing is
    # weighted to it, so moving it moves no vertex.
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    root = rig.data.edit_bones["_rootJoint"]
    root.head, root.tail, root.roll = Vector((0, 0, 0)), Vector((0, 0, 0.1)), 0.0
    bpy.ops.object.mode_set(mode="OBJECT")
    return factor


# ---- her bones ----

def cloth_chains(rig):
    """The bones the statue's wind moved: everything under her hips that is not her legs or spine."""
    hips = rig.data.bones[PREFIX + "Hips_03"]
    return {b.name for child in hips.children if not child.name.startswith(PREFIX)
            for b in [child] + list(child.children_recursive)}


def cloth_onto_her(rig, meshes, gone):
    """The cloth chains' weights given to her hips -- and, from the top of her thighs down to her knees,
    more and more to the thighs themselves, the left one on her left and the right on her right, both
    alike down her middle -- so a skirt that hung stiff off her hips swings with her stride."""
    bones = rig.data.bones
    hips = PREFIX + "Hips_03"
    left, right = PREFIX + "LeftUpLeg_04", PREFIX + "RightUpLeg_08"
    top = bones[left].head_local.z
    knee = bones[PREFIX + "LeftLeg_05"].head_local.z
    half = bones[left].head_local.x
    for obj in meshes:
        targets = {n: obj.vertex_groups.get(n) or obj.vertex_groups.new(name=n) for n in (hips, left, right)}
        groups = {g.index: g for g in obj.vertex_groups if g.name in gone}
        for v in obj.data.vertices:
            moved = sum(g.weight for g in v.groups if g.group in groups)
            if moved <= 0:
                continue
            follow = CLOTH_FOLLOW * min(max((top - v.co.z) / (top - knee), 0.0), 1.0)
            side = min(max(0.5 + v.co.x / (2 * half), 0.0), 1.0)  # her left is +X
            for name, share in ((hips, 1 - follow), (left, follow * side), (right, follow * (1 - side))):
                if share > 0:
                    targets[name].add([v.index], moved * share, "ADD")
        for g in list(groups.values()):
            obj.vertex_groups.remove(g)


def four_at_most(meshes):
    """No vertex moved by more than four bones -- jME skins with four -- and its weights summed to one."""
    for obj in meshes:
        for v in obj.data.vertices:
            had = [(g.group, g.weight) for g in v.groups]  # read whole: removing from v.groups reorders it
            kept = sorted((p for p in had if p[1] > 0), key=lambda p: -p[1])[:4]
            total = sum(w for _, w in kept) or 1.0
            keep = {group: w / total for group, w in kept}
            for group, _ in had:
                if group in keep:
                    obj.vertex_groups[group].add([v.index], keep[group], "REPLACE")
                else:
                    obj.vertex_groups[group].remove([v.index])


def weighted(meshes):
    names = set()
    for obj in meshes:
        index = {g.index: g.name for g in obj.vertex_groups}
        for v in obj.data.vertices:
            names.update(index[g.group] for g in v.groups if g.weight > 0)
    return names


def prune(rig, meshes, keep):
    """Bones with nothing weighted to them or under them go, and so do their empty vertex groups."""
    used = weighted(meshes) | keep
    needed = {b.name for b in rig.data.bones
              if b.name in used or any(c.name in used for c in b.children_recursive)}
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    for bone in list(rig.data.edit_bones):
        if bone.name not in needed:
            rig.data.edit_bones.remove(bone)
    bpy.ops.object.mode_set(mode="OBJECT")
    for obj in meshes:
        for g in list(obj.vertex_groups):
            if g.name not in needed:
                obj.vertex_groups.remove(g)


def add_hand_slot(rig, where, turn):
    """`handslot.l` in her left fist, where and as fist() says the grip lies; it rides her hand."""
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    hand = rig.data.edit_bones[PREFIX + "LeftHand_018"]
    slot = rig.data.edit_bones.new("handslot.l")
    slot.head = Vector((0, 0, 0))
    slot.tail = Vector((0, 0.05, 0))
    slot.matrix = Matrix.LocRotScale(where, turn, None)
    slot.parent = hand
    slot.use_deform = False
    bpy.ops.object.mode_set(mode="OBJECT")


# Where her dagger hangs: on her left hip -- her quiver is on the right -- hilt at the belt, the blade
# down the outside of her thigh, tipped a little out and back. From the top of her left thigh, in metres
# at her 2.18; the numbers to nudge if it sinks into her or floats.
SHEATH_AT = Vector((0.085, 0.03, 0.04))
SHEATH_DOWN = Vector((0.12, 0.30, -1.0))


def add_sheath(rig):
    """`sheath.l` on her hips: a bone pointing down her thigh, which a held thing runs along (its +Y),
    turned so a blade's flat lies against her leg."""
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    hips = rig.data.edit_bones[PREFIX + "Hips_03"]
    y = SHEATH_DOWN.normalized()
    x = Vector((0, -1, 0))
    x = (x - y * x.dot(y)).normalized()
    z = x.cross(y)
    sheath = rig.data.edit_bones.new("sheath.l")
    sheath.head = Vector((0, 0, 0))
    sheath.tail = Vector((0, 0.1, 0))
    at = rig.data.edit_bones[PREFIX + "LeftUpLeg_04"].head + SHEATH_AT
    sheath.matrix = Matrix.LocRotScale(at, Matrix((x, y, z)).transposed().to_quaternion(), None)
    sheath.parent = hips
    sheath.use_deform = False
    bpy.ops.object.mode_set(mode="OBJECT")


def drop_the_arrow_in_her_hand(meshes):
    """The statue holds an arrow to her cheek, modelled into the mesh of her arms and legs as part of her hand.

    In any other pose it is a stick waving about in her right hand, and the bow the game gives her is in the
    other one. Her arms and legs are otherwise mirror images, so what has no mirror on her other side within
    a couple of centimetres is the arrow, and goes.
    """
    import bmesh
    from mathutils.kdtree import KDTree
    obj = next(m for m in meshes if m.name == HANDS)
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    mirror = KDTree(len(bm.verts))
    for i, v in enumerate(bm.verts):
        mirror.insert(Vector((-v.co.x, v.co.y, v.co.z)), i)
    mirror.balance()
    doomed = [v for v in bm.verts if mirror.find(v.co)[2] > 0.02]
    bmesh.ops.delete(bm, geom=doomed, context="VERTS")
    bm.to_mesh(obj.data)
    bm.free()
    print(f"HUNTRESS dropped the arrow in her hand: {len(doomed)} vertices with no mirror")


# ---- triangles ----

def protect(obj, rig):
    """Her face and hands, as a vertex group the collapse is kept off."""
    bones = rig.data.bones
    neck = (bones[PREFIX + "Neck_061"].head_local).z
    left = bones[PREFIX + "LeftHand_018"].head_local.x
    right = bones[PREFIX + "RightHand_041"].head_local.x
    group = obj.vertex_groups.new(name="protect")
    for v in obj.data.vertices:
        if v.co.z > neck or v.co.x > left - 0.02 or v.co.x < right + 0.02:
            group.add([v.index], 1.0, "REPLACE")
    return group


def decimate(meshes, rig):
    before = tris(meshes)
    for obj in meshes:
        share = SHARE.get(obj.name, DEFAULT_SHARE)
        if share >= 1.0:
            continue
        mod = obj.modifiers.new("collapse", "DECIMATE")
        mod.decimate_type = "COLLAPSE"
        mod.ratio = share
        if obj.name in PROTECTED:
            mod.vertex_group = protect(obj, rig).name
            mod.invert_vertex_group = True
        select([obj], obj)
        bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
        bpy.ops.object.modifier_apply(modifier=mod.name)
        if "protect" in obj.vertex_groups:
            obj.vertex_groups.remove(obj.vertex_groups["protect"])
    print(f"HUNTRESS triangles {before} -> {tris(meshes)}")


# ---- pictures ----

EYES = "Object_473"  # two plain white balls: the statue's blank, eerie stare
EYE_SIZE = 256
SCLERA = (0.93, 0.93, 0.95)
IRIS_IN, IRIS_RIM, LIMBUS = (0.80, 0.80, 0.92), (0.56, 0.56, 0.74), (0.22, 0.20, 0.30)  # pale, faint violet
PUPIL, SHINE = (0.04, 0.04, 0.06), (1.0, 1.0, 1.0)
IRIS_R, PUPIL_R, SHINE_R = 0.25, 0.085, 0.035  # of the picture, which spans the ball's width


def paint_eyes(meshes):
    """Irises in her white eyes: pale, faintly violet, so the stare stays eerie -- a dark rim, a pupil,
    a highlight. The balls had no picture at all, so each is given one: looked at from the front, a
    ball's width is the picture's width (the same picture on both), and the iris is its middle."""
    import bmesh
    import numpy as np
    obj = next(m for m in meshes if m.name == EYES)
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    uv = bm.loops.layers.uv.verify()
    unseen = set(bm.verts)
    while unseen:
        ball, todo = [], [unseen.pop()]
        while todo:
            v = todo.pop()
            ball.append(v)
            for e in v.link_edges:
                w = e.other_vert(v)
                if w in unseen:
                    unseen.remove(w)
                    todo.append(w)
        lo = Vector([min(v.co[i] for v in ball) for i in range(3)])
        hi = Vector([max(v.co[i] for v in ball) for i in range(3)])
        middle, radius = (lo + hi) / 2, max(hi.x - lo.x, hi.z - lo.z) / 2
        for v in ball:
            for loop in v.link_loops:
                loop[uv].uv = (0.5 + (v.co.x - middle.x) / (2 * radius), 0.5 + (v.co.z - middle.z) / (2 * radius))
    bm.to_mesh(obj.data)
    bm.free()

    y, x = np.mgrid[0:EYE_SIZE, 0:EYE_SIZE] / (EYE_SIZE - 1)
    r = np.hypot(x - 0.5, y - 0.5)
    px = np.ones((EYE_SIZE, EYE_SIZE, 4), dtype=np.float32)
    px[..., :3] = SCLERA

    def lay(mask, colour):
        for c in range(3):
            px[..., c] = px[..., c] * (1 - mask) + colour[c] * mask

    soft = lambda edge, width: np.clip((edge - r) / width + 0.5, 0, 1)  # 1 inside the circle `edge`
    iris = np.clip(r / IRIS_R, 0, 1)[..., None]
    shade = np.array(IRIS_IN) * (1 - iris) + np.array(IRIS_RIM) * iris
    inside = soft(IRIS_R, 0.012)
    for c in range(3):
        px[..., c] = px[..., c] * (1 - inside) + shade[..., c] * inside
    lay(soft(IRIS_R, 0.012) * np.clip((r - (IRIS_R - 0.035)) / 0.035, 0, 1), LIMBUS)
    lay(soft(PUPIL_R, 0.01), PUPIL)
    lay(np.clip((SHINE_R - np.hypot(x - 0.45, y - 0.56)) / 0.008 + 0.5, 0, 1), SHINE)
    image = bpy.data.images.new("eyes", EYE_SIZE, EYE_SIZE)
    image.pixels.foreach_set(px.ravel())
    image.pack()
    material = obj.material_slots[0].material
    tree = material.node_tree
    bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    tex = tree.nodes.new("ShaderNodeTexImage")
    tex.image = image
    tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    print("HUNTRESS eyes painted")


def base_colour_only(meshes):
    """Each material a base-colour picture, downsized, and nothing else: the game reads its first picture as the skin."""
    for material in {s.material for m in meshes for s in m.material_slots if s.material}:
        tree = material.node_tree
        picture = None
        bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
        if bsdf is not None and bsdf.inputs["Base Color"].is_linked:
            node = bsdf.inputs["Base Color"].links[0].from_node
            while node is not None and node.type != "TEX_IMAGE":  # through a colour-factor mix, if any
                node = next((i.links[0].from_node for i in node.inputs if i.is_linked), None)
            picture = node.image if node is not None else None
        colour = tuple(bsdf.inputs["Base Color"].default_value) if bsdf is not None else (0.5, 0.5, 0.5, 1)
        for node in list(tree.nodes):
            tree.nodes.remove(node)
        out = tree.nodes.new("ShaderNodeOutputMaterial")
        shader = tree.nodes.new("ShaderNodeBsdfPrincipled")
        shader.inputs["Metallic"].default_value = 0.0
        shader.inputs["Roughness"].default_value = 1.0
        shader.inputs["Base Color"].default_value = colour
        tree.links.new(shader.outputs["BSDF"], out.inputs["Surface"])
        if picture is not None:
            size = 512 if material.name in SMALL_PICTURES else 1024
            if max(picture.size) > size:
                picture.scale(size, size)
            picture.name = material.name
            picture.file_format = "JPEG"
            tex = tree.nodes.new("ShaderNodeTexImage")
            tex.image = picture
            tree.links.new(tex.outputs["Color"], shader.inputs["Base Color"])
            if PICTURES:  # saved where the game-assets copy wants them, and read back from there
                os.makedirs(PICTURES, exist_ok=True)
                picture.filepath_raw = os.path.join(PICTURES, material.name + ".jpg")
                picture.save()


# ---- clips ----

def rest_world(rig, name):
    return rig.matrix_world @ rig.data.bones[name].matrix_local


def rotation(matrix):
    return matrix.to_3x3().normalized()


def import_rig(path):
    """One library: its armature (everything else it holds deleted) and the actions it brought."""
    before, actions = set(bpy.data.objects), set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=path, bone_heuristic="BLENDER")
    added = [o for o in bpy.data.objects if o not in before]
    rig = next(o for o in added if o.type == "ARMATURE")
    for obj in added:
        if obj != rig:
            bpy.data.objects.remove(obj, do_unlink=True)
    return rig, set(bpy.data.actions) - actions


def open_libraries():
    """Each clip's rig and action, and one rig a library to measure rest by."""
    rigs, holder = {}, {}
    paths = [(KAYKIT, os.path.join(ANIMATIONS, name)) for name in LIBRARIES] + [(UAL, UAL_SOURCE)]
    for source, path in paths:
        rig, actions = import_rig(path)
        rigs.setdefault(source, rig)
        for action in actions:
            clip = action.name.split(".")[0]  # a second library's T-Pose comes in as T-Pose.001
            if CLIPS.get(clip) == source and clip not in holder:
                holder[clip] = (rig, action)
    missing = [c for c in CLIPS if c not in holder]
    if missing:
        raise SystemExit(f"no library holds {missing}")
    return rigs, holder


def facing(rig, table):
    """The turn about the vertical that faces a library's rig the way hers faces: her left arm along +X."""
    arm = rest_world(rig, table["LeftForeArm_017"]).translation - rest_world(rig, table["LeftArm_016"]).translation
    return Matrix.Rotation(-math.atan2(arm.y, arm.x), 4, "Z")


def swings(her, rig, table, turn):
    """For each ALIGN bone, the turn that lays her rest direction onto theirs."""
    found = {}
    for mine, after in ALIGN.items():
        a = rest_world(her, PREFIX + after).translation - rest_world(her, PREFIX + mine).translation
        b = turn.to_3x3() @ (rest_world(rig, table[after]).translation - rest_world(rig, table[mine]).translation)
        found[mine] = a.normalized().rotation_difference(b.normalized()).to_matrix()
    for mine, under in RIDE.items():
        found[mine] = found[under]
    return found


def full(name):
    return name if name == "handslot.l" else PREFIX + name


def hands(her):
    """Each finger joint: (its hand, which finger, how far from the knuckle); and the axis each hand curls about."""
    bones = her.data.bones
    found, axes = {}, {}
    for hand in (PREFIX + "LeftHand_018", PREFIX + "RightHand_041"):
        h = bones[hand]
        base = {}
        for chain in h.children:
            if chain.name == "handslot.l":
                continue
            kind = next((k for k in ("Index", "MiddleF", "Ring", "Pinky") if k in chain.name), "thumb")
            joints = [b for b in [chain] + list(chain.children_recursive) if "scaleCompensation" not in b.name]
            joints.sort(key=lambda b: len(b.parent_recursive))
            base[kind] = joints[0].head_local
            for depth, b in enumerate(joints):
                found[b.name] = (hand, kind, depth)
        forward = (base["MiddleF"] - h.head_local).normalized()
        across = (base["Index"] - base["Pinky"]).normalized()
        palm = base["thumb"] - h.head_local
        palm = (palm - forward * palm.dot(forward) - across * palm.dot(across)).normalized()
        # Turned so a finger bends toward the palm: the side the thumb stands out on.
        axes[hand] = across if (Matrix.Rotation(0.3, 3, across) @ forward).dot(palm) > forward.dot(palm) else -across
    return found, axes


def curl(pose, kind, depth):
    angles = pose["thumb"] if kind == "thumb" else pose.get(kind, pose.get("finger"))
    return math.radians(angles[depth]) if depth < len(angles) else 0.0


def fist(her):
    """Her left hand closed round the bow: how far her fingers curl, and where the grip then sits.

    The bow's grip is a cylinder HANDLE_RADIUS round (the elven bow at its Scale 0.8). Her middle finger,
    curled by the GRIP angles times k, runs round a circle; k is chosen so that circle is the handle's
    radius plus half a finger, and the handle's axis is laid through its middle, along her knuckles.
    So the fingers close on the grip rather than beside it. The slot is turned so the bow runs along
    that axis (its own Z) and its string (its own -X, half-turned to +X by Roll 180) points back up her
    forearm, toward her drawing hand. Returns (k, where, turn) in armature space at rest.
    """
    bones = her.data.bones
    hand = bones[PREFIX + "LeftHand_018"]
    _, axes = hands(her)
    axis = axes[hand.name]
    chain = next(b for b in hand.children if "MiddleF" in b.name)
    joints = [b for b in [chain] + list(chain.children_recursive) if "scaleCompensation" not in b.name]
    joints.sort(key=lambda b: len(b.parent_recursive))
    rest = [b.head_local.copy() for b in joints] + [joints[-1].tail_local.copy()]

    def curled(k):
        points = [p.copy() for p in rest]
        for depth in range(len(joints)):
            bend = Matrix.Rotation(k * curl(GRIP, "finger", depth), 3, axis)
            pivot = points[depth]
            for i in range(depth + 1, len(points)):
                points[i] = pivot + bend @ (points[i] - pivot)
        return points

    def circle(points):  # least squares, in the plane across the axis
        u = (points[1] - points[0]).normalized()
        u = (u - axis * u.dot(axis)).normalized()
        v = axis.cross(u)
        flat = np.array([[(p - points[0]).dot(u), (p - points[0]).dot(v)] for p in points])
        a = np.c_[2 * flat, np.ones(len(flat))]
        (cx, cy, c), *_ = np.linalg.lstsq(a, (flat ** 2).sum(1), rcond=None)
        return points[0] + u * cx + v * cy, math.sqrt(c + cx * cx + cy * cy)

    want = HANDLE_RADIUS + FINGER_HALF
    k = min(np.linspace(0.5, 1.8, 131), key=lambda k: abs(circle(curled(k))[1] - want))
    where, radius = circle(curled(k))
    forearm = (hand.head_local - bones[PREFIX + "LeftForeArm_017"].head_local).normalized()
    x = -forearm
    x = (x - axis * x.dot(axis)).normalized()
    z = axis
    y = z.cross(x)
    print(f"HUNTRESS fist: curl x{k:.2f}, the fingers round {radius:.3f} for a grip of {HANDLE_RADIUS}")
    return k, where, Matrix((x, y, z)).transposed().to_quaternion()


def turned_to_the_bow(hand, forearm, theirs, slot_in_hand):
    """Her bow hand turned about her forearm so the bow in her fist runs as KayKit's clip runs its bow.

    A real fist holds the bow along the knuckles, and a hand hanging down holds it level; KayKit's
    chibi hand has no knuckles and its clips stand the bow up by the hand slot's turn alone. So for a
    KayKit clip her wrist is twisted until her knuckles point where their bow points -- the bow stands
    up for the draw, and her fingers still close round it. Either way along is the same bow."""
    along = (hand.translation - forearm.translation).normalized()
    mine = (rotation(hand) @ slot_in_hand).col[2]
    want = rotation(theirs).col[2]
    mine = (mine - along * mine.dot(along)).normalized()
    want = (want - along * want.dot(along)).normalized()
    angle = math.atan2(along.dot(mine.cross(want)), mine.dot(want))
    if angle > math.pi / 2:
        angle -= math.pi
    elif angle < -math.pi / 2:
        angle += math.pi
    turned = Matrix.Rotation(angle, 3, along) @ rotation(hand)
    return Matrix.LocRotScale(hand.translation, turned.to_quaternion(), None)


def retarget(her, rigs, holder, grip):
    scene = bpy.context.scene
    order = sorted(her.data.bones, key=lambda b: len(b.parent_recursive))
    fingers, axes = hands(her)
    closed = {"finger": tuple(a * grip for a in GRIP["finger"]), "thumb": GRIP["thumb"]}
    bow_hand = PREFIX + "LeftHand_018"
    slot_in_hand = (rotation(her.data.bones[bow_hand].matrix_local).inverted()
                    @ rotation(her.data.bones["handslot.l"].matrix_local))
    for bone in her.pose.bones:
        bone.rotation_mode = "QUATERNION"
    her.animation_data_create()
    per_source = {}
    for source, rig in rigs.items():
        table = MAP[source]
        turn = facing(rig, table)
        hips = (turn @ rest_world(rig, table["Hips_03"])).translation.z
        per_source[source] = (table, turn, swings(her, rig, table, turn),
                              rest_world(her, PREFIX + "Hips_03").translation.z / hips)
    for clip, source in CLIPS.items():
        rig, action = holder[clip]
        table, turn, swing, hips_ratio = per_source[source]
        mapped = {full(k): v for k, v in table.items()}
        swing = {full(k): v for k, v in swing.items()}
        rig.animation_data_create()
        rig.animation_data.action = action
        if hasattr(rig.animation_data, "action_slot") and action.slots:
            rig.animation_data.action_slot = action.slots[0]
        their_rest = {n: turn @ rest_world(rig, n) for n in set(table.values())}
        hand_pose = {PREFIX + "LeftHand_018": closed,
                     PREFIX + "RightHand_041": DRAW if clip in DRAWING else RELAXED}
        written = RENAME.get(clip, clip)
        made = bpy.data.actions.new(written)
        her.animation_data.action = made
        start, end = action.frame_range
        end = min(end, start + CUT.get(clip, end))
        frames = [float(f) for f in range(int(start), int(math.floor(end)) + 1)]
        if end - frames[-1] > 1e-4:
            frames.append(end)
        # A clip made on the spot whose hips still wander: what they travel first frame to last is taken out.
        ends = []
        for frame in (frames[0], frames[-1]):
            scene.frame_set(int(frame), subframe=frame - int(frame))
            ends.append((turn @ rig.matrix_world @ rig.pose.bones[table["Hips_03"]].matrix).translation)
        drift = (ends[1] - ends[0]) if clip in IN_PLACE else Vector()
        drift.z = 0.0
        for frame in frames:
            scene.frame_set(int(frame), subframe=frame - int(frame))
            share = (frame - frames[0]) / max(frames[-1] - frames[0], 1e-6)
            posed = {}
            for bone in order:
                name = bone.name
                rel = (bone.parent.matrix_local.inverted() @ bone.matrix_local) if bone.parent else bone.matrix_local
                carried = (posed[bone.parent.name] @ rel) if bone.parent else rel
                if name in mapped:
                    k = mapped[name]
                    now = turn @ rig.matrix_world @ rig.pose.bones[k].matrix
                    their_turn = rotation(now) @ rotation(their_rest[k]).inverted()
                    rest = rotation(bone.matrix_local)
                    r = their_turn @ swing.get(name, Matrix.Identity(3)) @ rest
                    where = carried.translation
                    if name == PREFIX + "Hips_03":
                        moved = now.translation - their_rest[k].translation - drift * share
                        where = bone.matrix_local.translation + moved * hips_ratio
                    posed[name] = Matrix.LocRotScale(where, r.to_quaternion(), None)
                    if name == bow_hand and source == KAYKIT:
                        posed[name] = turned_to_the_bow(
                            posed[name], posed[PREFIX + "LeftForeArm_017"],
                            turn @ rig.matrix_world @ rig.pose.bones["handslot.l"].matrix, slot_in_hand)
                elif name in fingers:
                    hand, kind, depth = fingers[name]
                    hand_turn = rotation(posed[hand]) @ rotation(her.data.bones[hand].matrix_local).inverted()
                    bend = Matrix.Rotation(curl(hand_pose[hand], kind, depth), 3, hand_turn @ axes[hand])
                    posed[name] = Matrix.LocRotScale(carried.translation,
                                                     (bend @ rotation(carried)).to_quaternion(), None)
                else:
                    posed[name] = carried
                pose_bone = her.pose.bones[name]
                parent_rest = (posed[bone.parent.name] @ rel) if bone.parent else rel
                pose_bone.matrix_basis = parent_rest.inverted() @ posed[name]
                if name in mapped or name in fingers:
                    pose_bone.keyframe_insert("rotation_quaternion", frame=frame, group=name)
                    if name == PREFIX + "Hips_03":
                        pose_bone.keyframe_insert("location", frame=frame, group=name)
        track = her.animation_data.nla_tracks.new()
        track.name = written
        track.strips.new(written, int(start), made)
        her.animation_data.action = None
        print(f"HUNTRESS clip {clip} from {source} frames={len(frames)}")
    for bone in her.pose.bones:
        bone.matrix_basis = Matrix.Identity(4)


# ---- written ----

def write(rig, meshes):
    """Her model, skinned and still, and beside the KayKit libraries her clips on her bones alone.

    Apart, as the KayKit heroes and their libraries are, so the game takes her clips the way it takes
    theirs (AnimationLibrary.copy, which leaves alone a clip the model already has -- a model that carried
    its own would have the game warn that none were taken).
    """
    common = dict(export_format="GLB", use_selection=True, export_yup=True, export_apply=False,
                  export_cameras=False, export_lights=False, export_extras=False, export_morph=False,
                  export_def_bones=False)
    os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
    select([rig] + meshes, rig)
    bpy.ops.export_scene.gltf(
        filepath=OUT, export_texcoords=True, export_normals=True, export_tangents=False,
        export_materials="EXPORT", export_image_format="JPEG", export_jpeg_quality=85,
        export_skins=True, export_influence_nb=4, export_animations=False, **common)
    select([rig], rig)
    bpy.ops.export_scene.gltf(
        filepath=CLIPS_OUT, export_skins=False, export_animations=True, export_animation_mode="NLA_TRACKS",
        export_force_sampling=True, export_anim_slide_to_zero=True, **common)
    preview = os.environ.get("HUNTRESS_PREVIEW")
    if preview:  # her and her clips in one file, to look at in Blender; the game does not read it
        select([rig] + meshes, rig)
        bpy.ops.export_scene.gltf(
            filepath=preview, export_materials="EXPORT", export_image_format="JPEG", export_skins=True,
            export_influence_nb=4, export_animations=True, export_animation_mode="NLA_TRACKS",
            export_force_sampling=True, export_anim_slide_to_zero=True, **common)


def main():
    her, meshes = open_her()
    factor = stand_her_up(her, meshes)
    drop_the_arrow_in_her_hand(meshes)
    cloth_onto_her(her, meshes, cloth_chains(her))
    decimate(meshes, her)
    four_at_most(meshes)

    rigs, holder = open_libraries()
    prune(her, meshes, keep=set())
    grip, where, turn = fist(her)
    add_hand_slot(her, where, turn)
    add_sheath(her)
    retarget(her, rigs, holder, grip)
    for obj in list(bpy.data.objects):
        if obj != her and obj not in meshes:
            bpy.data.objects.remove(obj, do_unlink=True)

    paint_eyes(meshes)
    base_colour_only(meshes)
    write(her, meshes)
    print(f"HUNTRESS factor={factor:.5f} bones={len(her.data.bones)} triangles={tris(meshes)}"
          f" meshes={len(meshes)} wrote {OUT} ({os.path.getsize(OUT)} bytes)"
          f" and {CLIPS_OUT} ({os.path.getsize(CLIPS_OUT)} bytes)")


main()
