"""Makes the CGTrader monsters the owner downloaded into models the game draws.

    blender -b --factory-startup -P art/monsters/export_licensed_monsters.py -- <name> <source folder> \
        <UAL1_Standard.glb> <out folder>

    golem     Ultimate Golem Elemental Pack (KaidaStudios): ElementalGolemPack/ + textures/  -> golem.glb + golem_*.png
    minotaur  Animated Minotaur (vsify): Minotaur/mesh + Minotaur/animation                 -> minotaur.glb
    troll     Earthborn - Troll (souchenki): Earthborn Troll/Troll.FBX                      -> troll.glb
    bandit    Low poly Bandit (karolis-prokop): bandit.blend                                -> bandit.glb
    spider    Animated Spiders Pack (3d-rina): spider.blend + texture_spider*.png          -> spider.glb + spider_*.png
    zombie    Zombie Monster (AmeerHamza3d): zombie-glb.glb + Animations/*.fbx + Texture/   -> zombie.glb

NOT IN THE REPOSITORY. Every one is CGTrader Royalty Free: the game may ship them, the public repository may not.
What this writes goes into the git-ignored licensed-assets/models/licensed/ folder; without it every block that names
one draws its stand-in (a Monster's Licensed look, see MonsterLook.licensedOr). Run art/models/base_colour_only.js
over each .glb written.

On the way, each gets:

FOUR TO SIX CLIPS UNDER THE GAME'S NAMES: Idle, Walk, Attack, Death (and Run, Hit where it has them). Cut from one
long take by its frames (the troll's Animation.txt; the spider's take, read off its motion), gathered from one file a
clip (the minotaur, the zombie), or -- where it has none -- borrowed from Quaternius' Universal Animation Library
(CC0) by role, as art/lange/retarget.py borrows for Lange: the golems come with four attacks and nothing else, so
their idle, walk and death are UAL's; the zombie has no death, so its death is UAL's.

AT MOST ABOUT 15K TRIANGLES (the golems' 30k decimated), ONE BASE-COLOUR PICTURE A MATERIAL AT 1024 PX AT MOST,
FEET ON THE ORIGIN, METRES, FACING -Y IN BLENDER (+Z in the file), the rig flattened to scale 1. The height it
stands at is printed: a block's ModelScale is the height it is drawn at in units over this. A skin of the same model
in other colours (the golems' elements, the spiders' eight) is written beside it as a picture, for a block's Texture.
"""

import glob
import math
import os
import shutil
import sys

import bpy
import mathutils

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lange"))
import retarget  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from palette import palette  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
WHO, SOURCE, UAL_FILE, OUT = args
FPS = 30
TRIS = 14000
PICTURE = 1024



# ---- reading ----


def clear():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_any(path):
    before, actions = set(bpy.data.objects), set(bpy.data.actions)
    if path.lower().endswith(".fbx"):
        bpy.ops.import_scene.fbx(filepath=path)
    else:
        bpy.ops.import_scene.gltf(filepath=path)
    return [o for o in bpy.data.objects if o not in before], [a for a in bpy.data.actions if a not in actions]


def drop(objects):
    for obj in objects:
        bpy.data.objects.remove(obj, do_unlink=True)


def armature():
    return next(o for o in bpy.data.objects if o.type == "ARMATURE")


def meshes():
    return [o for o in bpy.data.objects if o.type == "MESH"]


def borrow_clip(path, name):
    """The one clip of another file of the same rig, as an action called `name`; the file's objects dropped."""
    objects, actions = import_any(path)
    own = next(a for a in actions if a.name.startswith("Armature") or len(actions) == 1)
    own.name = name
    own.use_fake_user = True
    for action in actions:
        if action is not own:
            bpy.data.actions.remove(action)
    drop(objects)
    return own


def cut(take, name, first, last):
    """Frames first..last of a long take, as their own action starting at frame 0."""
    clip = take.copy()
    clip.name = name
    clip.use_fake_user = True
    for layer in clip.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    keep = [(k.co.x - first, k.co.y) for k in curve.keyframe_points if first <= k.co.x <= last]
                    if not keep:
                        keep = [(0.0, curve.evaluate(first))]
                    while len(curve.keyframe_points):
                        curve.keyframe_points.remove(curve.keyframe_points[-1])
                    curve.keyframe_points.add(len(keep))
                    for point, (x, y) in zip(curve.keyframe_points, keep):
                        point.co = (x, y)
                        point.interpolation = "LINEAR"
    clip.frame_range = (0, last - first)
    return clip


def rename_bone(arm, old, new):
    """A bone renamed, its vertex groups (Blender renames them) and every clip that moves it."""
    arm.data.bones[old].name = new
    for action in bpy.data.actions:
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        curve.data_path = curve.data_path.replace('["%s"]' % old, '["%s"]' % new)


# ---- borrowing from the library ----


class Rig:
    """A rig at rest, as retarget.retarget reads Lange's (no fingers, no sword)."""

    def __init__(self, arm):
        self.arm = arm
        self.order = retarget._order(arm)
        self.rest = {b.name: b.matrix_local.copy() for b in arm.data.bones}
        self.local = {b.name: (b.parent.matrix_local.inverted() @ b.matrix_local) if b.parent else b.matrix_local.copy()
                      for b in arm.data.bones}
        self.heads = {n: m.translation.copy() for n, m in self.rest.items()}
        self.finger_pose = {}


def borrow_ual(arm, table, ends, wanted):
    """UAL's clips onto `arm` by role: table {its bone: (UAL bone, UAL end)}, ends {its bone: its end}."""
    retarget.LANGE_ENDS.update(ends)
    objects, actions = import_any(UAL_FILE)
    source = next(o for o in objects if o.type == "ARMATURE")
    by_name = {a.name: a for a in actions}
    for action in actions:
        action.name = "source." + action.name
    rig = Rig(arm)
    made = []
    for name, theirs, speed in wanted:
        clip = retarget.retarget(bpy.context.scene, rig, source, by_name[theirs], table, False, name, FPS, speed)
        made.append(clip)
    drop(objects)
    for action in actions:
        bpy.data.actions.remove(action)
    arm.animation_data.action = None
    return made


SIDE = {"L": ("l", "Left"), "R": ("r", "Right")}


# ---- each model ----


def golem():
    pack = os.path.join(SOURCE, "ElementalGolemPack")
    import_any(os.path.join(pack, "EarthGolem", "source", "Earth_Golem_FBX.fbx"))
    arm = armature()
    # The pack's four attacks, on a rig of the same bones: the smash is the one it fights with.
    objects, actions = import_any(os.path.join(SOURCE, "Animations", "Attacks.fbx"))
    for action in actions:
        action.use_fake_user = True
        if action.name.endswith("SmashAttack"):
            action.name = "Attack"
        elif action.name.endswith("StompAttack"):
            action.name = "Stomp"
        else:
            bpy.data.actions.remove(action)
    drop(objects)
    rename_bone(arm, "spine", "COG")
    rename_bone(arm, "thigh.L", "L.thigh")
    table = {
        "COG": ("pelvis", "spine_01"), "spine.001": ("spine_01", "spine_02"), "spine.002": ("spine_02", "spine_03"),
        "spine.003": ("spine_03", "neck_01"), "Head": ("Head", None),
    }
    ends = {"COG": "spine.001", "spine.001": "spine.002", "spine.002": "spine.003", "spine.003": "spine.004",
            "Head": "UP"}
    for side in ("L", "R"):
        s = SIDE[side][0]
        thigh = "L.thigh" if side == "L" else "thigh.R"
        table.update({
            "shoulder." + side: ("clavicle_" + s, "upperarm_" + s), "upper_arm." + side: ("upperarm_" + s, "lowerarm_" + s),
            "forearm." + side: ("lowerarm_" + s, "hand_" + s), thigh: ("thigh_" + s, "calf_" + s),
            "shin." + side: ("calf_" + s, "foot_" + s), "foot." + side: ("foot_" + s, "ball_" + s),
        })
        ends.update({"shoulder." + side: "upper_arm." + side, "upper_arm." + side: "forearm." + side,
                     "forearm." + side: "hand." + side, thigh: "shin." + side, "shin." + side: "foot." + side,
                     "foot." + side: "toe." + side})
    # A heavy thing's gait: the library's walk and idle slowed, its death as it is.
    borrow_ual(arm, table, ends, [("Idle", "Idle_Loop", 0.7), ("Walk", "Walk_Loop", 0.75), ("Death", "Death01", 1.0)])
    # The other elements' skins beside it, for a block's Texture.
    pictures = os.path.join(os.path.dirname(SOURCE), "golem-textures", "textures")
    for element, file in (("earth", "EarthGolem_albedo.jpeg"), ("ice", "IceGolem_albedo.jpg"),
                          ("shadow", "DarkGolem_albedo.jpg"), ("fire", "FireGolem_albedo.jpg"),
                          ("light", "LightGolem1_albedo.png")):
        image = bpy.data.images.load(os.path.join(pictures, file))
        image.scale(PICTURE, PICTURE)
        if element == "shadow":  # near black as it comes: lifted, so the game's light finds its cracks
            pixels = list(image.pixels)
            for i in range(len(pixels)):
                if i % 4 != 3:
                    pixels[i] = min(1.0, pixels[i] * 1.8)
            image.pixels = pixels
        image.filepath_raw = os.path.join(OUT, "golem_%s.png" % element)
        image.file_format = "PNG"
        image.save()
    dress(meshes(), os.path.join(pictures, "EarthGolem_albedo.jpeg"))


def minotaur():
    folder = os.path.join(SOURCE, "Minotaur")
    import_any(os.path.join(folder, "mesh", "Mesh.FBX"))
    for clip, file in (("Idle", "Idle"), ("Walk", "Walk"), ("Run", "Run"), ("Attack", "Attack"),
                       ("Hit", "Get_Hit"), ("Death", "Death1"), ("Shout", "Shout")):
        borrow_clip(os.path.join(folder, "animation", "Minotaur@%s.FBX" % file), clip)
    drop([o for o in bpy.data.objects if o.type == "EMPTY"])
    # Its pieces hang on the rig a thousandth of their size, the eyes on the head bone: each is put in the world as it
    # stands, its size applied, and skinned to the rig (the eyes to their bone) so nothing of the hanging is left.
    arm = armature()
    arm.data.pose_position = "REST"
    bpy.context.view_layer.update()
    for obj in meshes():
        world = obj.matrix_world.copy()
        bone = obj.parent_bone if obj.parent_type == "BONE" else None
        obj.parent = None
        obj.matrix_world = world
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        if bone:
            group = obj.vertex_groups.new(name=bone)
            group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
            obj.modifiers.new("Armature", "ARMATURE").object = arm
        obj.parent = arm
        obj.matrix_parent_inverse = arm.matrix_world.inverted()
    arm.data.pose_position = "POSE"
    bpy.context.view_layer.update()
    pictures = os.path.join(folder, "texture")
    for obj in meshes():
        dress([obj], os.path.join(pictures, "Pants_diffuse.tga" if obj.name.startswith("Pants")
                                  else "Minotaur_diffuse.tga"))


def troll():
    folder = os.path.join(SOURCE, "Earthborn Troll")
    objects, actions = import_any(os.path.join(folder, "Troll.FBX"))
    take = actions[0]
    # Its Animation.txt, frame for frame. Its first attack leaps a metre off the ground: the second, a swing
    # where it stands, is the one it fights with.
    for name, first, last in (("Idle", 20, 140), ("Walk", 330, 390), ("Run", 410, 446), ("Leap", 520, 565),
                              ("Attack", 580, 660), ("Hit", 680, 710), ("Death", 730, 790)):
        cut(take, name, first, last)
    bpy.data.actions.remove(take)
    dress_by_own(meshes())


def bandit():
    bpy.ops.wm.open_mainfile(filepath=os.path.join(SOURCE, "bandit.blend"))
    drop([o for o in bpy.data.objects if o.type in ("CAMERA", "LIGHT") or o.name == "Ground"])
    arm = armature()
    arm.hide_render = False
    for obj in meshes():
        obj.hide_set(False)
        bpy.context.view_layer.objects.active = obj
        for modifier in list(obj.modifiers):
            if modifier.type == "MIRROR":
                bpy.ops.object.modifier_apply(modifier=modifier.name)
            elif modifier.type == "NODES":  # a smooth-by-angle: shading only
                obj.modifiers.remove(modifier)
    # The sword is held by a Child Of constraint: skinned to the hand instead, where it rests.
    sword = bpy.data.objects["Sword"]
    arm.data.pose_position = "REST"
    bpy.context.view_layer.update()
    world = sword.matrix_world.copy()
    bone = sword.constraints[0].subtarget
    sword.constraints.clear()
    sword.parent = arm
    sword.matrix_world = world
    group = sword.vertex_groups.new(name=bone)
    group.add(range(len(sword.data.vertices)), 1.0, "REPLACE")
    sword.modifiers.new("Armature", "ARMATURE").object = arm
    arm.data.pose_position = "POSE"
    for action in list(bpy.data.actions):
        action.name = {"Thrust Attack": "Attack"}.get(action.name, action.name)
        if action.name not in ("Idle", "Walk", "Attack", "Hit", "Death"):
            bpy.data.actions.remove(action)
    # Flat colours, as Quaternius' are: one palette picture. Its IK feet are baked into every clip as it is
    # exported (export_force_sampling).
    palette("bandit", meshes(), OUT)


def spider():
    bpy.ops.wm.open_mainfile(filepath=os.path.join(SOURCE, "spider.blend"))
    drop([o for o in bpy.data.objects if o.type in ("CAMERA", "LIGHT")])
    take = bpy.data.actions[0]
    # The take, read off its motion (still to 61, two steps of its walk, a short stand, a rearing strike, a fall):
    for name, first, last in (("Idle", 125, 145), ("Walk", 65, 95), ("Attack", 146, 187), ("Death", 191, 229)):
        cut(take, name, first, last)
    armature().animation_data.action = None
    bpy.data.actions.remove(take)
    for skin in (1, 3, 6):
        image = bpy.data.images.load(os.path.join(SOURCE, "texture_spider%d.png" % skin))
        if max(image.size) > PICTURE:
            image.scale(PICTURE, PICTURE)
        image.filepath_raw = os.path.join(OUT, "spider_%d.png" % skin)
        image.file_format = "PNG"
        image.save()
    dress(meshes(), os.path.join(SOURCE, "texture_spider1.png"))


def zombie():
    import_any(os.path.join(SOURCE, "zombie-glb.glb"))
    for action in list(bpy.data.actions):
        bpy.data.actions.remove(action)
    folder = os.path.join(SOURCE, "Animations")
    # Its "Zombie" file is its pose at rest: its shamble is the one it calls a run, and walks by.
    for clip, file in (("Idle", "Zombie Idle"), ("Walk", "Zombie Run"), ("Attack", "Zombie Attack")):
        borrow_clip(os.path.join(folder, file + ".fbx"), clip)
    arm = armature()
    rename_bone(arm, "mixamorig:Hips", "COG")
    rename_bone(arm, "mixamorig:LeftUpLeg", "L.thigh")
    m = "mixamorig:"
    table = {"COG": ("pelvis", "spine_01"), m + "Spine": ("spine_01", "spine_02"), m + "Spine1": ("spine_02", "spine_03"),
             m + "Spine2": ("spine_03", "neck_01"), m + "Neck": ("neck_01", "Head"), m + "Head": ("Head", None)}
    ends = {"COG": m + "Spine", m + "Spine": m + "Spine1", m + "Spine1": m + "Spine2", m + "Spine2": m + "Neck",
            m + "Neck": m + "Head", m + "Head": "UP"}
    for side in ("L", "R"):
        s, word = SIDE[side]
        up = "L.thigh" if side == "L" else m + "RightUpLeg"
        table.update({m + word + "Shoulder": ("clavicle_" + s, "upperarm_" + s),
                      m + word + "Arm": ("upperarm_" + s, "lowerarm_" + s),
                      m + word + "ForeArm": ("lowerarm_" + s, "hand_" + s), up: ("thigh_" + s, "calf_" + s),
                      m + word + "Leg": ("calf_" + s, "foot_" + s), m + word + "Foot": ("foot_" + s, "ball_" + s)})
        ends.update({m + word + "Shoulder": m + word + "Arm", m + word + "Arm": m + word + "ForeArm",
                     m + word + "ForeArm": m + word + "Hand", up: m + word + "Leg", m + word + "Leg": m + word + "Foot",
                     m + word + "Foot": m + word + "ToeBase"})
    AFTER.append(lambda: borrow_ual(arm, table, ends, [("Death", "Death01", 1.0)]))
    pictures = os.path.join(SOURCE, "Texture")
    dress(meshes(), os.path.join(pictures, "Zombie_DIF.png"))


# ---- the picture ----


def dress(objects, picture):
    """One material of `picture` as its base colour, on every face of `objects`."""
    image = bpy.data.images.load(picture)
    material = bpy.data.materials.new(os.path.splitext(os.path.basename(picture))[0])
    material.use_nodes = True
    tree = material.node_tree
    bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
    for obj in objects:
        obj.data.materials.clear()
        obj.data.materials.append(material)


def dress_by_own(objects):
    """Each material keeps the picture its own base colour is drawn from, and nothing else."""
    for obj in objects:
        for slot in obj.material_slots:
            material = slot.material
            if not material or not material.use_nodes:
                continue
            tree = material.node_tree
            bsdf = next((n for n in tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
            if bsdf is None:
                continue
            for name in ("Normal", "Emission Color", "Metallic", "Roughness", "Specular IOR Level", "Alpha"):
                if name in bsdf.inputs:
                    for link in list(bsdf.inputs[name].links):
                        tree.links.remove(link)
            bsdf.inputs["Metallic"].default_value = 0.0
            bsdf.inputs["Roughness"].default_value = 1.0
            bsdf.inputs["Alpha"].default_value = 1.0


def shrink_pictures():
    for image in bpy.data.images:
        if image.size[0] > PICTURE or image.size[1] > PICTURE:
            image.scale(min(PICTURE, image.size[0]), min(PICTURE, image.size[1]))


# ---- size, ground, scale ----


def decimate(objects):
    total = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in objects)
    if total <= TRIS:
        return
    for obj in objects:
        bpy.context.view_layer.objects.active = obj
        modifier = obj.modifiers.new("Decimate", "DECIMATE")
        modifier.ratio = TRIS / total
        while obj.modifiers[0] != modifier:
            bpy.ops.object.modifier_move_up(modifier=modifier.name)
        bpy.ops.object.modifier_apply(modifier=modifier.name)


def play(arm, action):
    ad = arm.animation_data or arm.animation_data_create()
    ad.action = action
    if action is not None and action.slots:
        ad.action_slot = action.slots[0]


def extent(objects, arm, action):
    play(arm, action)
    low, high = 1e9, -1e9
    start, end = (int(action.frame_range[0]), int(action.frame_range[1])) if action else (0, 0)
    for frame in range(start, end + 1, max(1, (end - start) // 6)):
        bpy.context.scene.frame_set(frame)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        for obj in objects:
            evaluated = obj.evaluated_get(depsgraph)
            mesh = evaluated.to_mesh()
            for v in mesh.vertices:
                z = (evaluated.matrix_world @ v.co).z
                low, high = min(low, z), max(high, z)
            evaluated.to_mesh_clear()
    play(arm, None)
    return low, high


def flatten(arm, height):
    """Turned and scaled as it stands, so every node is 1 and it is `height` metres tall; its clips' moves too."""
    low, high = extent(meshes(), arm, bpy.data.actions.get("Idle"))
    factor = height / (high - low)
    arm.scale = arm.scale * factor
    bpy.context.view_layer.update()
    factor = arm.scale.x
    bpy.ops.object.select_all(action="SELECT")
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    for action in bpy.data.actions:
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        if curve.data_path.endswith(".location"):
                            for key in curve.keyframe_points:
                                key.co.y *= factor
                                key.handle_left.y *= factor
                                key.handle_right.y *= factor


def lift(arm, dz):
    """Every clip moves its root bones `dz` up, so the body stands where it should in every frame. Baked into the
    clips rather than left on the rig's node: a loader that reads a node's offset under a skin as well as over it
    would count it twice."""
    import mathutils as _m
    up = _m.Vector((0, 0, dz / arm.scale.z))
    for bone in [b for b in arm.data.bones if b.parent is None]:
        local = bone.matrix_local.to_3x3().inverted() @ up
        path = 'pose.bones["%s"].location' % bone.name
        for action in bpy.data.actions:
            for layer in action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for i in range(3):
                            curve = bag.fcurves.find(path, index=i)
                            if curve is None:
                                curve = bag.fcurves.new(path, index=i, group_name=bone.name)
                                curve.keyframe_points.insert(action.frame_range[0], 0.0)
                            for key in curve.keyframe_points:
                                key.co.y += local[i]
                                key.handle_left.y += local[i]
                                key.handle_right.y += local[i]


# Each one's height in metres as the game first draws it (a block's ModelScale then says how big), and its turn
# about the up axis to face -Y.
SPEC = {"golem": (golem, 2.6, 0), "minotaur": (minotaur, 2.4, 0), "troll": (troll, 2.8, 0),
        "bandit": (bandit, 1.8, 0), "spider": (spider, 0.8, 0), "zombie": (zombie, 1.8, 0)}

AFTER = []
clear()
build, height, turn = SPEC[WHO][0], SPEC[WHO][1], SPEC[WHO][2]
build()
arm = armature()
for obj in bpy.data.objects:
    obj.hide_set(False)
    obj.hide_viewport = False
for empty in [o for o in bpy.data.objects if o.type == "EMPTY"]:
    bpy.data.objects.remove(empty, do_unlink=True)
if arm.animation_data:
    arm.animation_data.action = None
    for track in list(arm.animation_data.nla_tracks):
        arm.animation_data.nla_tracks.remove(track)
# An FBX take keys the rig's own node too (where it stands, its turn, its size -- the troll falls by turning it):
# that motion is folded into its root bones, frame by frame, under one still node, and the node's keys dropped.
def object_curves(action):
    return [(bag, c) for layer in action.layers for strip in layer.strips for bag in strip.channelbags
            for c in bag.fcurves if not c.data_path.startswith("pose.")]


idle = bpy.data.actions.get("Idle")
if idle is not None and object_curves(idle):
    play(arm, idle)
    bpy.context.scene.frame_set(int(idle.frame_range[0]))
    rx, ry = arm.rotation_euler.x, arm.rotation_euler.y
    still = mathutils.Matrix.LocRotScale(None, mathutils.Euler((rx, ry, 0)), arm.scale.copy())
    roots = [pb for pb in arm.pose.bones if pb.parent is None]
    for action in [a for a in bpy.data.actions if object_curves(a)]:
        play(arm, action)
        frames = range(int(action.frame_range[0]), int(action.frame_range[1]) + 1)
        wanted = {}
        for frame in frames:
            bpy.context.scene.frame_set(frame)
            moving = arm.matrix_world.copy()
            wanted[frame] = {pb.name: still.inverted() @ moving @ pb.matrix for pb in roots}
        for bag, curve in object_curves(action):
            bag.fcurves.remove(curve)
        arm.matrix_world = still
        for frame in frames:
            bpy.context.scene.frame_set(frame)
            arm.matrix_world = still
            for pb in roots:
                pb.rotation_mode = "QUATERNION"
                pb.matrix = wanted[frame][pb.name]
                bpy.context.view_layer.update()
                pb.keyframe_insert("location", frame=frame)
                pb.keyframe_insert("rotation_quaternion", frame=frame)
                pb.keyframe_insert("scale", frame=frame)
    play(arm, None)
    arm.matrix_world = still
for action in bpy.data.actions:
    for bag, curve in object_curves(action):
        bag.fcurves.remove(curve)
arm.location = (0, 0, 0)
arm.rotation_euler.z = 0
# What hangs on nothing (an importer's Icosphere, a stray cube) is dropped.
drop([o for o in meshes() if o.parent is None and not any(md.type == "ARMATURE" for md in o.modifiers)])
decimate(meshes())
if turn:
    arm.rotation_euler.z += math.radians(turn)
    bpy.context.view_layer.update()
flatten(arm, height)
low, high = extent(meshes(), arm, bpy.data.actions.get("Idle"))
lift(arm, -low)
bpy.context.view_layer.update()
for step in AFTER:
    step()
low, high = extent(meshes(), arm, bpy.data.actions["Idle"])
lift(arm, -low)
bpy.context.view_layer.update()
shrink_pictures()
for action in bpy.data.actions:
    if action.name.startswith("source."):
        bpy.data.actions.remove(action)

# Its blow lands when its stand-in's does, within a tenth of a second (a block's PreAttackFrames serves both looks):
# a long wind-up is cut short, a short one held a moment on its first pose. Seconds, later is positive.
RETIME = {"zombie": -0.75, "spider": -0.85, "golem": -0.37, "minotaur": 0.12, "troll": -0.15}


def retime(action, seconds):
    shift = seconds * bpy.context.scene.render.fps / bpy.context.scene.render.fps_base
    start = action.frame_range[0]
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    first = curve.evaluate(start - min(shift, 0))
                    keys = [(k.co.x + shift, k.co.y) for k in curve.keyframe_points if k.co.x + shift >= start]
                    keys = [(start, first)] + [k for k in keys if k[0] > start]
                    while len(curve.keyframe_points):
                        curve.keyframe_points.remove(curve.keyframe_points[-1])
                    curve.keyframe_points.add(len(keys))
                    for point, (x, y) in zip(curve.keyframe_points, keys):
                        point.co = (x, y)
                        point.interpolation = "LINEAR"
    action.frame_range = (start, max(start + 1, action.frame_range[1] + shift))


if WHO in RETIME:
    retime(bpy.data.actions["Attack"], RETIME[WHO])

# The hands it strikes with and the feet it stands on, under the names the game's measure of a clip looks for
# (MonsterClipFitTest: a hand.l, a foot, a leg's tip, a head that bites).
RENAMES = {
    "troll": {"Left Hand": "hand.l", "Right Hand": "hand.r"},
    "zombie": {"mixamorig:LeftHand": "hand.l", "mixamorig:RightHand": "hand.r"},
    "minotaur": {"CATRigLArmPalm": "hand.l", "CATRigRArmPalm": "hand.r", "CATRigLLegAnkle": "foot.l",
                 "CATRigRLegAnkle": "foot.r"},
    "spider": {"fang_L": "head", **{"leg%d_%s" % (n, side): "%s_leg.%s_end" % ("abcd"[n - 1], side.lower())
                                    for n in (1, 2, 3, 4) for side in "LR"}},
}
for old, new in RENAMES.get(WHO, {}).items():
    rename_bone(arm, old, new)
low, high = extent(meshes(), arm, bpy.data.actions["Idle"])
tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in meshes())
bpy.ops.object.select_all(action="SELECT")
path = os.path.join(OUT, WHO + ".glb")
bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                          export_apply=False, export_cameras=False, export_lights=False, export_extras=False,
                          export_morph=False, export_def_bones=False, export_skins=True,
                          export_animations=True, export_animation_mode="ACTIONS", export_force_sampling=True,
                          export_anim_slide_to_zero=True, export_image_format="AUTO", export_materials="EXPORT")
print("EXPORTED %s tris=%d foot=%.3f height=%.3f clips=%s bytes=%d"
      % (WHO, tris, low, high - low, sorted(a.name for a in bpy.data.actions), os.path.getsize(path)))
