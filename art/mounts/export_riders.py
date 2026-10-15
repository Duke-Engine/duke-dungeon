"""Writes the horse and the three heroes riding it -- docs/plan/2026-10-15-mounts.md.

    blender -b --factory-startup -P art/mounts/export_riders.py -- <horse.glb> <resources folder> <out folder>         [<shots folder> [<icon .png>]]

The horse is Quaternius' "Horse" from his Ultimate Animated Animals (CC0; poly.pizza qvTrSG9pZF, as poly.pizza
serves it). The engine draws no unit on another's bone yet (engine request E82), so a hero riding is ONE model: the
horse's rig and clips, with the hero's own mesh -- his idle's first frame, his legs swung into a seat -- baked still
and skinned whole to the horse's middle back bone, so the horse's gallop carries him. One such model a hero
(<out>/<hero>_riding.glb), and the horse alone (<out>/horse.glb) for when he is off it.

Every file is sized for its hero: his look's ModelScale is the riding look's too, so the horse is scaled here to stand
WITHERS game units at the withers whatever the hero's own size (the mage's chibi rig is larger in its own units).
The horse alone stands at the same height under HORSE_SCALE. Its flat colours go onto one palette picture, as the
food animals' do (art/food/export_food.py, whose helpers these are); the heroes keep their own pictures.

The heroes ride with nothing in their hands that is a Held of theirs (the rogue's bow and dagger, the mage's staff
and book): those hang on bones this rig has not got. The knight's sword is part of his mesh, so he keeps it.
With a shots folder, a picture of each rider at a gallop is rendered there; with an icon path, the horse's head on
clear, for the whistle's picture in the bag (art/icons/items/painted/blender_horse.png, framed by ItemIcons).
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
# The horse alone is drawn at this ModelScale (data/world/mounts.duke): it stands WITHERS at the withers under it.
HORSE_SCALE = 4.0
CLIPS = {"Idle": "Idle", "Walk": "Walk", "Gallop": "Gallop", "Eating": "Eating", "Death": "Death",
         "Hurt": "Idle_HitReact_Left"}
SADDLE_BONE = "Torso2"
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
    armature.scale = armature.scale * factor
    bpy.ops.object.select_all(action="DESELECT")
    for obj in bpy.data.objects:  # only the horse is in the scene yet
        obj.select_set(True)
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    whole = factor * 1.0
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
    back = (rig.matrix_world @ rig.data.bones[SADDLE_BONE].head_local).z
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


def rider(hero):
    """The hero's meshes, posed in the seat and baked still; returns them and where his seat is (between his thighs)."""
    before = set(bpy.data.objects)
    before_actions = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=os.path.join(RESOURCES, hero["model"]))
    mine = [o for o in bpy.data.objects if o not in before]
    for stray in [o for o in mine if o.type == "MESH" and o.parent is None
                  and not any(m.type == "ARMATURE" for m in o.modifiers)]:
        mine.remove(stray)
        bpy.data.objects.remove(stray, do_unlink=True)
    for empty in [o for o in mine if o.type == "EMPTY"]:
        mine.remove(empty)
        bpy.data.objects.remove(empty, do_unlink=True)
    rig = next(o for o in mine if o.type == "ARMATURE")
    meshes = [o for o in mine if o.type == "MESH"]
    idle = next((a for a in bpy.data.actions if a not in before_actions and named(a, hero["idle"])), None)
    if hero["library"]:
        held = set(bpy.data.objects)
        bpy.ops.import_scene.gltf(filepath=os.path.join(RESOURCES, hero["library"]))
        idle = next(a for a in bpy.data.actions if a not in before_actions and named(a, hero["idle"]))
        for obj in [o for o in bpy.data.objects if o not in held]:
            bpy.data.objects.remove(obj, do_unlink=True)
    play(rig, idle, int(idle.frame_range[0]))
    for side, (thigh, shin, arm) in enumerate(zip(hero["thighs"], hero["shins"], hero["arms"])):
        apart = THIGH_APART if side == 0 else -THIGH_APART
        turn(rig, thigh, "X", THIGH_FORWARD)
        turn(rig, thigh, "Y", -apart)
        turn(rig, shin, "X", SHIN_BACK)
        turn(rig, arm, "X", ARM_FORWARD)
    thighs = [rig.matrix_world @ rig.pose.bones[name].head for name in hero["thighs"]]
    seat = (thighs[0] + thighs[1]) / 2
    depsgraph = bpy.context.evaluated_depsgraph_get()
    baked = []
    for obj in meshes:
        still = bpy.data.meshes.new_from_object(obj.evaluated_get(depsgraph), depsgraph=depsgraph)
        world = obj.matrix_world.copy()
        copy = bpy.data.objects.new(obj.name + "_riding", still)
        bpy.context.collection.objects.link(copy)
        copy.matrix_world = world
        baked.append(copy)
    for obj in meshes + [rig]:
        bpy.data.objects.remove(obj, do_unlink=True)
    for action in [a for a in bpy.data.actions if a not in before_actions]:
        bpy.data.actions.remove(action)
    for obj in baked:
        obj.vertex_groups.clear()
        if obj.data.shape_keys:
            obj.shape_key_clear()
    # What he sits on: the lowest of him between his hip joints, a hand's breadth either side of them.
    tall = 12.0 / hero["scale"]
    bottom = seat.z
    for obj in baked:
        for v in obj.data.vertices:
            at = obj.matrix_world @ v.co
            if abs(at.x - seat.x) < 0.05 * tall and abs(at.y - seat.y) < 0.07 * tall:
                bottom = min(bottom, at.z)
    return baked, Vector((seat.x, seat.y, bottom))


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
# The horse alone, drawn at HORSE_SCALE.
rig, skin = horse(WITHERS / HORSE_SCALE)
export(os.path.join(OUT, "horse.glb"), rig, skin)
print("EXPORTED horse bytes=%d" % os.path.getsize(os.path.join(OUT, "horse.glb")))
if SHOTS:
    shoot("horse", rig, skin)

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
    rig, skin = horse(WITHERS / hero["scale"])
    play(rig, None, 1)
    saddle_y = (rig.matrix_world @ rig.data.bones[SADDLE_BONE].head_local).y
    top = surface_over(skin, saddle_y)
    baked, seat = rider(hero)
    # What he sits on, on the horse's back, a little into it, as a man sits down into a saddle.
    move = Vector((0.0, saddle_y, top)) - seat
    move.z -= 0.01 * 12.0 / hero["scale"]
    for obj in baked:
        obj.location += move
    bpy.context.view_layer.update()
    for obj in baked:
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        group = obj.vertex_groups.new(name=SADDLE_BONE)
        group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
    bpy.ops.object.select_all(action="DESELECT")
    for obj in baked:
        obj.select_set(True)
    skin.select_set(True)
    bpy.context.view_layer.objects.active = skin
    bpy.ops.object.join()
    path = os.path.join(OUT, name + "_riding.glb")
    export(path, rig, skin)
    tris = sum(len(p.vertices) - 2 for p in skin.data.polygons)
    gallop = bpy.data.actions["Gallop"].frame_range
    print("EXPORTED %s tris=%d gallop=%s seconds bytes=%d" % (
        name, tris, (gallop[1] - gallop[0]) / bpy.context.scene.render.fps, os.path.getsize(path)))
    if SHOTS:
        shoot(name + "_riding", rig, skin)
        shoot(name + "_riding_front", rig, skin, (-0.25, -1.0, 0.3))
