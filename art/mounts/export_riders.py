"""Writes the horse and each hero's riding clips -- docs/plan/2026-10-15-mounts.md.

    blender -b --factory-startup -P art/mounts/export_riders.py -- <horse.glb> <resources folder> <out folder>         [<shots folder> [<icon .png>]]

The horse is Quaternius' "Horse" from his Ultimate Animated Animals (CC0; poly.pizza qvTrSG9pZF, as poly.pizza
serves it), written to <out>/horse.glb with a bone of its own, SADDLE: a child of its middle back bone standing
straight up at the top of its back, where a rider's seat goes. The engine hangs a rider there (E88,
GameObject.rideOn): the rider's look under that bone, so the horse's gallop carries him while he plays his own clips.
Standing straight up with no roll, the bone's frame is the horse model's own, so a rider hung on it faces the way the
horse does with no turn of his own (his riding look's Facing is 0) and is drawn in the horse's units (his riding
look's ModelScale is his own over the horse's HORSE_SCALE).

Each hero's riding clips go to <out>/riding_<hero>.glb, an animation library on his own skeleton (a three-cornered
mesh to carry its skin): Ride_Idle, sat with his thighs forward and apart, his shins back and his hands forward at the
reins, and Ride_Gallop, the same rising and falling with the horse's stride, as long as its Gallop. In both his seat
-- the lowest of him between his hips -- is lowered onto his feet's origin, so the saddle bone is where he sits.

With a shots folder, each hero is rendered riding at a gallop there, as the game hangs him; with an icon path, the
horse's head on clear, for the whistle's picture in the bag (art/icons/items/painted/blender_horse.png).
"""

import math
import os
import sys

import bpy
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index("--") + 1:]
HORSE, RESOURCES, OUT = args[0], args[1], args[2]
SHOTS = args[3] if len(args) > 3 else None
ICON = args[4] if len(args) > 4 else None

# Game units at the horse's withers (its back bone's middle): a hero is 12 tall, a horse's back about 0.85 of a man.
WITHERS = 10.0
# The horse is drawn at this ModelScale (data/world/mounts.duke): it stands WITHERS at the withers under it.
HORSE_SCALE = 4.0
CLIPS = {"Idle": "Idle", "Walk": "Walk", "Gallop": "Gallop", "Eating": "Eating", "Death": "Death",
         "Hurt": "Idle_HitReact_Left"}
BACK_BONE = "Torso2"
SADDLE = "Saddle"
SWATCH = 8

# Each hero: his file, where his idle comes from, his look's ModelScale, and his thigh, shin and upper-arm bones,
# left then right.
HEROES = {
    "knight": dict(model="models/heroes/lange/lange.glb", library=None, idle="Idle", scale=6.82,
                   thighs=("L.thigh", "R.thigh"), shins=("L.leg", "R.leg"), arms=("L.shoulder", "R.shoulder")),
    "rogue": dict(model="models/heroes/huntress.glb", library="animations/characters/huntress.glb",
                  idle="Ranged_Bow_Idle", scale=5.50,
                  thighs=("QuickRigCharacter_LeftUpLeg_04", "QuickRigCharacter_RightUpLeg_08"),
                  shins=("QuickRigCharacter_LeftLeg_05", "QuickRigCharacter_RightLeg_09"),
                  arms=("QuickRigCharacter_LeftArm_016", "QuickRigCharacter_RightArm_039")),
    "mage": dict(model="models/heroes/mage.glb", library="animations/characters/general.glb", idle="Idle_A",
                 scale=4.52, thighs=("upperleg.l", "upperleg.r"), shins=("lowerleg.l", "lowerleg.r"),
                 arms=("upperarm.l", "upperarm.r")),
}
# The seat, in degrees about the world's axes (the hero faces -Y): thighs forward and apart, shins back down.
THIGH_FORWARD, THIGH_APART, SHIN_BACK, ARM_FORWARD = -55.0, 38.0, 50.0, -20.0
# At a gallop: how far he rises and falls in the saddle, a share of his height, and his hands working the reins.
BOB, REINS = 0.012, 8.0


def to_srgb(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def base_colour(material):
    if material is None or not material.use_nodes:
        return (0.8, 0.8, 0.8)
    for node in material.node_tree.nodes:
        if node.type == "BSDF_PRINCIPLED":
            socket = node.inputs["Base Color"]
            if socket.links and socket.links[0].from_node.type == "MIX":
                for put in socket.links[0].from_node.inputs:
                    if put.type == "RGBA" and not put.is_linked:
                        return tuple(put.default_value[:3])
            return tuple(socket.default_value[:3])
    return (0.8, 0.8, 0.8)


def palette(name, obj):
    """Every flat colour of the horse on one picture, every face on its colour's swatch, one material."""
    for attribute in list(obj.data.color_attributes):
        obj.data.color_attributes.remove(attribute)
    colours = []
    for slot in obj.material_slots:
        colour = base_colour(slot.material)
        if colour not in colours:
            colours.append(colour)
    across = 1
    while across * across < len(colours):
        across *= 2
    size = across * SWATCH
    image = bpy.data.images.new(name + "_palette", size, size, alpha=False)
    pixels = [0.0] * (size * size * 4)
    for i, colour in enumerate(colours):
        cx, cy = (i % across) * SWATCH, (i // across) * SWATCH
        srgb = [to_srgb(c) for c in colour]
        for y in range(cy, cy + SWATCH):
            for x in range(cx, cx + SWATCH):
                at = (y * size + x) * 4
                pixels[at:at + 4] = [*srgb, 1.0]
    image.pixels = pixels
    path = os.path.join(OUT, name + "_palette.png")
    image.filepath_raw = path
    image.file_format = "PNG"
    image.save()
    image.pack()
    os.remove(path)
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    tree = material.node_tree
    bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    node = tree.nodes.new("ShaderNodeTexImage")
    node.image = image
    node.interpolation = "Closest"
    tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
    mesh = obj.data
    if not mesh.uv_layers:
        mesh.uv_layers.new(name="UVMap")
    while len(mesh.uv_layers) > 1:
        mesh.uv_layers.remove(mesh.uv_layers[1])
    uv = mesh.uv_layers[0].data
    slots = [base_colour(s.material) for s in obj.material_slots]
    for poly in mesh.polygons:
        i = colours.index(slots[min(poly.material_index, len(slots) - 1)])
        u = ((i % across) * SWATCH + SWATCH / 2) / size
        v = ((i // across) * SWATCH + SWATCH / 2) / size
        for loop in poly.loop_indices:
            uv[loop].uv = (u, v)
        poly.material_index = 0
    mesh.materials.clear()
    mesh.materials.append(material)


def strays():
    """poly.pizza's and the importer's leftovers: an Icosphere bound to nothing, and the bones' "_end" empties."""
    for stray in [o for o in bpy.data.objects if o.type == "MESH" and o.parent is None
                  and not any(m.type == "ARMATURE" for m in o.modifiers)]:
        bpy.data.objects.remove(stray, do_unlink=True)
    for empty in [o for o in bpy.data.objects if o.type == "EMPTY"]:
        bpy.data.objects.remove(empty, do_unlink=True)


def scale_rig(armature, factor):
    """The rig and its skin scaled by {@code factor} and applied, so every node is 1, every clip's moves scaled too."""
    whole = armature.scale.x * factor  # the download's 100, the first time: every move keyed in its hundredths
    armature.scale = armature.scale * factor
    bpy.ops.object.select_all(action="DESELECT")
    for obj in bpy.data.objects:  # only the horse is in the scene yet
        obj.select_set(True)
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    for action in bpy.data.actions:
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        if curve.data_path.endswith(".location"):
                            for key in curve.keyframe_points:
                                key.co.y *= whole
                                key.handle_left.y *= whole
                                key.handle_right.y *= whole


def play(armature, action, frame):
    ad = armature.animation_data or armature.animation_data_create()
    ad.action = action
    if action is not None and action.slots:
        ad.action_slot = action.slots[0]
    bpy.context.scene.frame_set(frame)
    bpy.context.view_layer.update()


def horse(withers):
    """The horse imported, its clips kept and named, standing {@code withers} at the withers, one palette material."""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=HORSE)
    strays()
    rig = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    skin = next(o for o in bpy.data.objects if o.type == "MESH")
    keep = {}
    for game, own in CLIPS.items():
        # The download has every clip twice, once under the rig's name: the plain one is kept.
        action = bpy.data.actions.get(own) or next(a for a in bpy.data.actions if a.name.split("|")[-1] == own)
        keep[action.name] = game
    for action in list(bpy.data.actions):
        if action.name not in keep:
            bpy.data.actions.remove(action)
    for action in list(bpy.data.actions):
        action.name = keep[action.name]
    if rig.animation_data:
        rig.animation_data.action = None
        for track in list(rig.animation_data.nla_tracks):
            rig.animation_data.nla_tracks.remove(track)
    scale_rig(rig, 1.0)  # the download's 100 applied
    play(rig, None, 1)
    back = (rig.matrix_world @ rig.data.bones[BACK_BONE].head_local).z
    scale_rig(rig, withers / back)
    palette("horse", skin)
    return rig, skin


def surface_over(skin, y, x_half=0.08, y_half=0.12):
    """The top of the horse's skin over the line x = 0 at {@code y}, at rest: where a seat goes."""
    top = -1e9
    for v in skin.data.vertices:
        at = skin.matrix_world @ v.co
        if abs(at.x) < x_half and abs(at.y - y) < y_half:
            top = max(top, at.z)
    return top


def named(action, name):
    """Whether it is that clip: the horse's own Idle came first, so the hero's may be Idle.001."""
    return action.name == name or action.name.rsplit(".", 1)[0] == name


def turn(armature, bone, axis, degrees):
    """A pose bone turned about a world axis through its own head."""
    pb = armature.pose.bones[bone]
    world = armature.matrix_world @ pb.matrix
    head = world.to_translation()
    spin = Matrix.Translation(head) @ Matrix.Rotation(math.radians(degrees), 4, axis) @ Matrix.Translation(-head)
    pb.matrix = armature.matrix_world.inverted() @ spin @ world
    bpy.context.view_layer.update()


def saddle(rig, skin):
    """SADDLE: straight up from the top of the horse's back over BACK_BONE, a child of it, its frame the model's."""
    play(rig, None, 1)
    y = (rig.matrix_world @ rig.data.bones[BACK_BONE].head_local).y
    top = surface_over(skin, y)
    bpy.ops.object.select_all(action="DESELECT")
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    inverse = rig.matrix_world.inverted()
    bone = rig.data.edit_bones.new(SADDLE)
    bone.head = inverse @ Vector((0.0, y, top))
    bone.tail = inverse @ Vector((0.0, y, top + 0.25))
    bone.roll = 0.0
    bone.parent = rig.data.edit_bones[BACK_BONE]
    bone.use_connect = False
    bone.use_deform = False
    bpy.ops.object.mode_set(mode="OBJECT")


def hero_in(hero):
    """The hero imported beside what is there: his rig, his meshes and his idle."""
    before = set(bpy.data.objects)
    before_actions = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=os.path.join(RESOURCES, hero["model"]))
    mine = [o for o in bpy.data.objects if o not in before]
    for stray in [o for o in mine if o.type == "EMPTY" or o.type == "MESH" and o.parent is None
                  and not any(m.type == "ARMATURE" for m in o.modifiers)]:
        mine.remove(stray)
        bpy.data.objects.remove(stray, do_unlink=True)
    rig = next(o for o in mine if o.type == "ARMATURE")
    meshes = [o for o in mine if o.type == "MESH"]
    if hero["library"]:
        held = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(RESOURCES, hero["library"]))
        for obj in [o for o in bpy.data.objects if o not in held]:
            bpy.data.objects.remove(obj, do_unlink=True)
    idle = next(a for a in bpy.data.actions if a not in before_actions and named(a, hero["idle"]))
    return rig, meshes, idle


def seated(rig, meshes, hero, idle, lift, reins):
    """His idle's first frame swung into the seat, his seat lowered onto his origin and {@code lift} over it."""
    play(rig, idle, int(idle.frame_range[0]))
    for side, (thigh, shin, arm) in enumerate(zip(hero["thighs"], hero["shins"], hero["arms"])):
        apart = THIGH_APART if side == 0 else -THIGH_APART
        turn(rig, thigh, "X", THIGH_FORWARD)
        turn(rig, thigh, "Y", -apart)
        turn(rig, shin, "X", SHIN_BACK)
        turn(rig, arm, "X", ARM_FORWARD + reins)
    thighs = [rig.matrix_world @ rig.pose.bones[name].head for name in hero["thighs"]]
    middle = (thighs[0] + thighs[1]) / 2
    # What he sits on: the lowest of him between his hip joints, a hand's breadth either side of them.
    tall = 12.0 / hero["scale"]
    bottom = middle.z
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for obj in meshes:
        mesh = obj.evaluated_get(depsgraph).to_mesh()
        for v in mesh.vertices:
            at = obj.matrix_world @ v.co
            if abs(at.x - middle.x) < 0.05 * tall and abs(at.y - middle.y) < 0.07 * tall:
                bottom = min(bottom, at.z)
        obj.evaluated_get(depsgraph).to_mesh_clear()
    move = Matrix.Translation(Vector((-middle.x, -middle.y, lift * tall - bottom)))
    for pb in rig.pose.bones:
        if pb.parent is None:
            pb.matrix = rig.matrix_world.inverted() @ move @ rig.matrix_world @ pb.matrix
    bpy.context.view_layer.update()
    return {pb.name: pb.matrix_basis.copy() for pb in rig.pose.bones}


def clip(rig, name, keys):
    """A clip of {@code keys}, (frame, every bone's pose) each, keyed on every bone."""
    action = bpy.data.actions.new(name)
    ad = rig.animation_data or rig.animation_data_create()
    ad.action = action
    for frame, pose in keys:
        for pb in rig.pose.bones:
            pb.rotation_mode = "QUATERNION"
            pb.matrix_basis = pose[pb.name]
            pb.keyframe_insert("location", frame=frame)
            pb.keyframe_insert("rotation_quaternion", frame=frame)
            pb.keyframe_insert("scale", frame=frame)
    return action


def riding_clips(hero, gallop):
    """His Ride_Idle and Ride_Gallop, the gallop {@code gallop} frames long, on his rig; returns it and his meshes."""
    rig, meshes, idle = hero_in(hero)
    still = seated(rig, meshes, hero, idle, 0.0, 0.0)
    strides = []
    for step in range(5):
        wave = math.sin(step / 4 * 2 * math.pi)
        strides.append((1 + round(step * (gallop - 1) / 4), seated(rig, meshes, hero, idle, BOB * wave, REINS * wave)))
    clip(rig, "Ride_Idle", [(1, still), (30, still)])
    clip(rig, "Ride_Gallop", strides)
    return rig, meshes


def library(path, rig):
    """His riding clips alone on his skeleton, a three-cornered mesh to carry its skin."""
    for action in [a for a in bpy.data.actions if not a.name.startswith("Ride_")]:
        bpy.data.actions.remove(action)
    root = next(pb.name for pb in rig.pose.bones if pb.parent is None)
    mesh = bpy.data.meshes.new("seat")
    mesh.from_pydata([(0, 0, 0), (0.01, 0, 0), (0, 0.01, 0)], [], [(0, 1, 2)])
    tri = bpy.data.objects.new("seat", mesh)
    bpy.context.collection.objects.link(tri)
    tri.parent = rig
    tri.modifiers.new("Armature", "ARMATURE").object = rig
    tri.vertex_groups.new(name=root).add([0, 1, 2], 1.0, "REPLACE")
    export(path, rig, tri)


def shoot_riding(name, horse_rig, horse_skin, rig, hero):
    """Him riding at a gallop, hung as the engine hangs him: on SADDLE in the horse's frame, at his scale over its."""
    gallop = bpy.data.actions["Gallop"]
    play(horse_rig, gallop, int(sum(gallop.frame_range) / 2))
    play(rig, bpy.data.actions["Ride_Gallop"], 3)
    bone = horse_rig.pose.bones[SADDLE]
    frame = bone.matrix @ bone.bone.matrix_local.to_3x3().inverted().to_4x4()
    rig.matrix_world = horse_rig.matrix_world @ frame @ Matrix.Scale(hero["scale"] / HORSE_SCALE, 4)
    bpy.context.view_layer.update()
    shoot(name, horse_rig, horse_skin)


def export(path, rig, skin):
    bpy.ops.object.select_all(action="DESELECT")
    rig.select_set(True)
    skin.select_set(True)
    bpy.context.view_layer.objects.active = rig
    if rig.animation_data:
        rig.animation_data.action = None
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                              export_apply=False, export_cameras=False, export_lights=False, export_extras=False,
                              export_morph=False, export_def_bones=False, export_skins=True,
                              export_animations=True, export_animation_mode="ACTIONS",
                              export_image_format="AUTO", export_materials="EXPORT")


def shoot(name, rig, skin, way=(-0.9, -0.75, 0.45)):
    """A picture of it at a gallop, from in front and to its left, for the owner."""
    play(rig, bpy.data.actions["Gallop"], int(sum(bpy.data.actions["Gallop"].frame_range) / 2))
    scene = bpy.context.scene
    low = Vector((1e9, 1e9, 1e9))
    high = -low
    depsgraph = bpy.context.evaluated_depsgraph_get()
    mesh = skin.evaluated_get(depsgraph).to_mesh()
    for v in mesh.vertices:
        at = skin.matrix_world @ v.co
        low = Vector(map(min, low, at))
        high = Vector(map(max, high, at))
    skin.evaluated_get(depsgraph).to_mesh_clear()
    middle = (low + high) / 2
    reach = (high - low).length
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    scene.collection.objects.link(camera)
    camera.location = middle + Vector(way).normalized() * reach * 1.25
    camera.rotation_euler = (middle - camera.location).to_track_quat("-Z", "Y").to_euler()
    camera.data.lens = 50
    scene.camera = camera
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    scene.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
    sun.data.energy = 3.5
    world = bpy.data.worlds.new("sky")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.62, 0.7, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.8
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 900
    scene.render.resolution_y = 700
    scene.render.filepath = os.path.join(SHOTS, name + ".png")
    bpy.ops.render.render(write_still=True)
    for obj in (camera, sun):
        bpy.data.objects.remove(obj, do_unlink=True)


os.makedirs(OUT, exist_ok=True)
rig, skin = horse(WITHERS / HORSE_SCALE)
saddle(rig, skin)
export(os.path.join(OUT, "horse.glb"), rig, skin)
print("EXPORTED horse bytes=%d" % os.path.getsize(os.path.join(OUT, "horse.glb")))

if ICON:
    # Its head, from its left and a little in front, on clear: the neck and body run out of the picture.
    play(rig, bpy.data.actions["Idle"], 1)
    head = rig.matrix_world @ rig.pose.bones["Head"].head
    neck = rig.matrix_world @ rig.pose.bones["Neck1"].head
    middle = head + (neck - head) * 0.25
    size = (head - neck).length * 1.9
    scene = bpy.context.scene
    camera = bpy.data.objects.new("camera", bpy.data.cameras.new("camera"))
    scene.collection.objects.link(camera)
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = size
    camera.location = middle + Vector((-1.0, -0.8, 0.15)).normalized() * size * 4
    camera.rotation_euler = (middle - camera.location).to_track_quat("-Z", "Y").to_euler()
    scene.camera = camera
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    scene.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(40), math.radians(15), math.radians(-60))
    sun.data.energy = 4.0
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.film_transparent = True
    scene.render.resolution_x = scene.render.resolution_y = 256
    scene.render.filepath = ICON
    bpy.ops.render.render(write_still=True)
    scene.render.film_transparent = False
    for obj in (camera, sun):
        bpy.data.objects.remove(obj, do_unlink=True)

for name, hero in HEROES.items():
    rig, skin = horse(WITHERS / HORSE_SCALE)
    saddle(rig, skin)
    span = tuple(bpy.data.actions["Gallop"].frame_range)
    rider, meshes = riding_clips(hero, int(round(span[1] - span[0])) + 1)
    if SHOTS:
        shoot_riding(name + "_riding", rig, skin, rider, hero)
    path = os.path.join(OUT, "riding_" + name + ".glb")
    library(path, rider)
    print("EXPORTED riding_%s gallop=%.2fs bytes=%d" % (
        name, (span[1] - span[0]) / bpy.context.scene.render.fps, os.path.getsize(path)))
