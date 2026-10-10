"""Makes neilhancart's "Huntress" into the Rogue hero's model.

    ./gradlew exportHuntress -PhuntressSource="C:/Users/me/Downloads/huntress"
    blender -b --factory-startup -P art/huntress/export_huntress.py -- \
        <scene.gltf> <animations folder> <out .glb> [<folder for the downsized pictures>]

The KayKit libraries are read from the animations folder and her clips written into it.

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

HER CLOTH IS STIFF. Her cape, sash and loincloth hang on five chains of cloth bones
-- 270 of them -- that only the statue's wind ever moved. Their weights go to her
hips and the chains are deleted; bones nothing is weighted to are pruned, and no
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

THE ROGUE'S CLIPS, ON HER SKELETON. Her rig is Maya's HumanIK and the game's clips
are KayKit's, so the library cannot be copied onto her by bone name
(AnimationLibrary.copy). Each clip the Rogue's block names is retargeted here:
every limb bone takes its KayKit bone's turn from rest, after her A-pose limbs are
swung onto the KayKit T-pose; her hips, spine and head keep their own rest and take
the turn alone; hands and feet ride with the forearm and shin. They are written
under the KayKit names into animations/characters/huntress.glb, beside the KayKit
libraries, and her animation set (data/animations/huntress.duke) names that file.

A HAND SLOT. A bone `handslot.l` is added in her left palm, turned as KayKit's
`handslot.l` is in every frame, so the bow hangs from it with the same numbers it
hangs from the KayKit rogue with.
"""

import math
import os
import sys

import bpy
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, ANIMATIONS, OUT = args[0], args[1], args[2]
PICTURES = args[3] if len(args) > 3 else None
CLIPS_OUT = os.path.join(ANIMATIONS, "huntress.glb")  # her clips, written beside the KayKit libraries

HEIGHT = 2.18          # the KayKit rogue's, so her block keeps his ModelScale
PREFIX = "QuickRigCharacter_"

# What the Rogue's block plays (rogue.duke: Idle, Walk, Attack, Hurt, Death and his Portrait).
CLIPS = ["Ranged_Bow_Idle", "Running_A", "Ranged_Bow_Release", "Hit_A", "Death_A",
         "Ranged_Bow_Aiming_Idle", "Idle_B", "Spawn_Ground"]
LIBRARIES = ["ranged.glb", "movement.glb", "general.glb"]

# Hers <- KayKit's. ALIGN: her bone is swung from her A-pose onto KayKit's T-pose first;
# RIDE: it keeps her bone's rest relative to the one named; neither: it takes the turn alone.
MAP = {
    "Hips_03": "hips", "Spine_012": "spine", "Spine2_014": "chest", "Head_062": "head",
    "LeftArm_016": "upperarm.l", "LeftForeArm_017": "lowerarm.l", "LeftHand_018": "wrist.l",
    "RightArm_039": "upperarm.r", "RightForeArm_040": "lowerarm.r", "RightHand_041": "wrist.r",
    "LeftUpLeg_04": "upperleg.l", "LeftLeg_05": "lowerleg.l", "LeftFoot_06": "foot.l", "LeftToeBase_07": "toes.l",
    "RightUpLeg_08": "upperleg.r", "RightLeg_09": "lowerleg.r", "RightFoot_010": "foot.r", "RightToeBase_011": "toes.r",
    "handslot.l": "handslot.l",
}
ALIGN = {  # her bone: (her next bone, KayKit's next bone) -- the direction each is swung by
    "LeftArm_016": ("LeftForeArm_017", "lowerarm.l"), "LeftForeArm_017": ("LeftHand_018", "wrist.l"),
    "RightArm_039": ("RightForeArm_040", "lowerarm.r"), "RightForeArm_040": ("RightHand_041", "wrist.r"),
    "LeftUpLeg_04": ("LeftLeg_05", "lowerleg.l"), "LeftLeg_05": ("LeftFoot_06", "foot.l"),
    "RightUpLeg_08": ("RightLeg_09", "lowerleg.r"), "RightLeg_09": ("RightFoot_010", "foot.r"),
}
RIDE = {
    "LeftHand_018": "LeftForeArm_017", "RightHand_041": "RightForeArm_040", "handslot.l": "LeftForeArm_017",
    "LeftFoot_06": "LeftLeg_05", "LeftToeBase_07": "LeftLeg_05",
    "RightFoot_010": "RightLeg_09", "RightToeBase_011": "RightLeg_09",
}

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


def stand_her_up(rig, meshes):
    """Her feet on the origin, HEIGHT tall, written into the bones and vertices themselves."""
    as_bound(rig, meshes)
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


def weights_onto(meshes, gone, onto):
    for obj in meshes:
        target = obj.vertex_groups.get(onto) or obj.vertex_groups.new(name=onto)
        groups = {g.index: g for g in obj.vertex_groups if g.name in gone}
        for v in obj.data.vertices:
            moved = sum(g.weight for g in v.groups if g.group in groups)
            if moved > 0:
                target.add([v.index], moved, "ADD")
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


def add_hand_slot(rig, rest):
    """`handslot.l` in her left palm, at the turn the retarget gives it (see retarget)."""
    select([rig], rig)
    bpy.ops.object.mode_set(mode="EDIT")
    hand = rig.data.edit_bones[PREFIX + "LeftHand_018"]
    middle = next(b for b in hand.children if "MiddleF" in b.name)
    palm = (hand.head + middle.head) / 2
    slot = rig.data.edit_bones.new("handslot.l")
    slot.head = Vector((0, 0, 0))
    slot.tail = Vector((0, 0.05, 0))
    slot.matrix = Matrix.LocRotScale(palm, rest, None)
    slot.parent = hand
    slot.use_deform = False
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


def open_libraries():
    """The KayKit libraries, each an armature and its actions, and which one holds each clip."""
    rigs, holder = [], {}
    for name in LIBRARIES:
        before, actions = set(bpy.data.objects), set(bpy.data.actions)
        bpy.ops.import_scene.gltf(filepath=os.path.join(ANIMATIONS, name), bone_heuristic="BLENDER")
        added = [o for o in bpy.data.objects if o not in before]
        rig = next(o for o in added if o.type == "ARMATURE")
        for obj in added:
            if obj != rig:
                bpy.data.objects.remove(obj, do_unlink=True)
        rigs.append(rig)
        for action in set(bpy.data.actions) - actions:
            clip = action.name.split(".")[0]  # a second library's T-Pose comes in as T-Pose.001
            if clip in CLIPS and clip not in holder:
                holder[clip] = (rig, action)
    return rigs[0], holder


def swings(her, kay):
    """For each ALIGN bone, the turn that lays her rest direction onto KayKit's."""
    found = {}
    for mine, (next_mine, next_kay) in ALIGN.items():
        a = (rest_world(her, PREFIX + next_mine).translation - rest_world(her, PREFIX + mine).translation)
        b = (rest_world(kay, next_kay).translation - rest_world(kay, MAP[mine]).translation)
        found[mine] = a.normalized().rotation_difference(b.normalized()).to_matrix()
    for mine, under in RIDE.items():
        found[mine] = found[under]
    return found


def full(name):
    return name if name == "handslot.l" else PREFIX + name


def retarget(her, kay, holder, swing):
    scene = bpy.context.scene
    bones = her.data.bones
    hips_ratio = (rest_world(her, PREFIX + "Hips_03").translation.z
                  / rest_world(kay, "hips").translation.z)
    order = [b for b in bones]  # parents before children, as Blender lists them
    order.sort(key=lambda b: len(b.parent_recursive))
    mapped = {full(k): v for k, v in MAP.items()}
    swing = {full(k): v for k, v in swing.items()}
    for bone in her.pose.bones:
        bone.rotation_mode = "QUATERNION"
    her.animation_data_create()
    for clip in CLIPS:
        kay, action = holder[clip]
        kay.animation_data_create()
        kay.animation_data.action = action
        if hasattr(kay.animation_data, "action_slot") and action.slots:
            kay.animation_data.action_slot = action.slots[0]
        kay_rest = {n: rest_world(kay, n) for n in set(MAP.values())}
        made = bpy.data.actions.new(clip)
        her.animation_data.action = made
        start, end = action.frame_range
        frames = [float(f) for f in range(int(start), int(math.floor(end)) + 1)]
        if end - frames[-1] > 1e-4:
            frames.append(end)
        for frame in frames:
            scene.frame_set(int(frame), subframe=frame - int(frame))
            posed = {}
            for bone in order:
                name = bone.name
                rel = (bone.parent.matrix_local.inverted() @ bone.matrix_local) if bone.parent else bone.matrix_local
                carried = (posed[bone.parent.name] @ rel) if bone.parent else rel
                if name in mapped:
                    k = mapped[name]
                    now = kay.matrix_world @ kay.pose.bones[k].matrix
                    turn = rotation(now) @ rotation(kay_rest[k]).inverted()
                    rest = rotation(bone.matrix_local)
                    if name == "handslot.l":
                        rest = swing[name].inverted() @ rotation(kay_rest[k])
                    r = turn @ swing.get(name, Matrix.Identity(3)) @ rest
                    where = carried.translation
                    if name == PREFIX + "Hips_03":
                        where = bone.matrix_local.translation + (now.translation - kay_rest[k].translation) * hips_ratio
                    posed[name] = Matrix.LocRotScale(where, r.to_quaternion(), None)
                else:
                    posed[name] = carried
                pose_bone = her.pose.bones[name]
                parent_rest = (posed[bone.parent.name] @ rel) if bone.parent else rel
                pose_bone.matrix_basis = parent_rest.inverted() @ posed[name]
                if name in mapped:
                    pose_bone.keyframe_insert("rotation_quaternion", frame=frame, group=name)
                    if name == PREFIX + "Hips_03":
                        pose_bone.keyframe_insert("location", frame=frame, group=name)
        track = her.animation_data.nla_tracks.new()
        track.name = clip
        track.strips.new(clip, int(start), made)
        her.animation_data.action = None
        print(f"HUNTRESS clip {clip} frames={len(frames)}")
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


def main():
    her, meshes = open_her()
    factor = stand_her_up(her, meshes)
    drop_the_arrow_in_her_hand(meshes)
    chains = cloth_chains(her)
    weights_onto(meshes, chains, PREFIX + "Hips_03")
    decimate(meshes, her)
    four_at_most(meshes)

    kay, holder = open_libraries()
    missing = [c for c in CLIPS if c not in holder]
    if missing:
        raise SystemExit(f"no KayKit library holds {missing}")
    swing = swings(her, kay)
    slot_rest = swing["handslot.l"].inverted() @ rotation(rest_world(kay, "handslot.l"))
    prune(her, meshes, keep=set())
    add_hand_slot(her, slot_rest.to_quaternion())
    retarget(her, kay, holder, swing)
    for obj in list(bpy.data.objects):
        if obj != her and obj not in meshes:
            bpy.data.objects.remove(obj, do_unlink=True)

    base_colour_only(meshes)
    write(her, meshes)
    print(f"HUNTRESS factor={factor:.5f} bones={len(her.data.bones)} triangles={tris(meshes)}"
          f" meshes={len(meshes)} wrote {OUT} ({os.path.getsize(OUT)} bytes)"
          f" and {CLIPS_OUT} ({os.path.getsize(CLIPS_OUT)} bytes)")


main()
