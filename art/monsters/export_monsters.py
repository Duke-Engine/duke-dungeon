"""Writes the wilds' monsters -- Quaternius's animated creatures (CC0) -- as the game draws them.

    blender -b --factory-startup -P art/monsters/export_monsters.py -- <downloads folder> <out folder>

The downloads folder holds each model as poly.pizza serves it (one .glb a model, in a folder of its own, its name
starting with the model's -- see SOURCES below and CREDITS.md). Nothing from it ships but what this writes.

What happens to each on the way:

FOUR CLIPS, GAME NAMES. Each creature keeps only what a Monster block names -- Idle, Walk, Attack, Death -- taken from
its own clips (a flyer's Walk is its Fast_Flying, a beast's Attack its Headbutt) and renamed so every block reads the
same. Dances, waves, yes and no are dropped.

ONE PICTURE, ONE MATERIAL. Most of these creatures are coloured by flat materials, four to seven a model. Each colour
is laid as an 8-pixel swatch on one small palette picture, every face's UVs put on its colour's swatch, and the model
drawn by one material: one draw call a creature, and a skin the art test can find. The few that came with a picture
of their own (the Enemy and Animal packs' Atlas) keep it.

NATIVE SIZE. Nothing is scaled here: each block says its ModelScale, as the KayKit skeletons' do. The height the
model stands at is printed, which is what each block's ModelScale is worked out from.
"""

import glob
import os
import sys

import bpy

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, OUT = args[0], args[1]

# Out name -> (the download's file name starts with, {game clip: the model's own clip}).
FLYER = {"Idle": "Flying_Idle", "Walk": "Fast_Flying", "Attack": "Punch", "Death": "Death"}
BLOB = {"Idle": "Idle", "Walk": "Walk", "Attack": "Bite_Front", "Death": "Death"}
BIPED = {"Idle": "Idle", "Walk": "Walk", "Attack": "Punch", "Death": "Death"}
ENEMY = {"Idle": "Idle", "Walk": "Walk", "Attack": "Attack", "Death": "Death"}
MODELS = {
    # forest
    "wolf": ("Wolf_", {"Idle": "Idle", "Walk": "Run", "Attack": "Headbutt", "Death": "Death"}),
    "spider": ("Spider_", {"Idle": "Spider_Idle", "Walk": "Spider_Walk", "Attack": "Spider_Attack",
                           "Death": "Spider_Death"}),
    "sporeling": ("Mushnub_Evolved_", BLOB),
    "goblin": ("Goblin_", ENEMY),
    # desert
    "cactoro": ("Cactoro_", BIPED),
    "dino": ("Dino_", BIPED),
    "demon": ("Demon_IkB63XOPvr", ENEMY),
    "dragon": ("Dragon_Evolved_", dict(FLYER, Attack="Headbutt")),
    # highlands
    "goleling": ("Goleling_71gomWolax", FLYER),
    "tribal": ("Tribal_", FLYER),
    "giant": ("Giant_", ENEMY),
    # marsh
    "slime": ("Slime_", {"Idle": "Slime_Idle", "Walk": "Slime_Walk", "Attack": "Slime_Attack",
                         "Death": "Slime_Death"}),
    "frog": ("Frog_", BIPED),
    "ghost": ("Ghost_Iip30bDHmu", FLYER),
    "glub": ("Glub_Evolved_", dict(FLYER, Attack="Headbutt")),
}

SWATCH = 8


def source_of(prefix):
    found = sorted(glob.glob(os.path.join(SOURCE, "*", prefix + "*.glb")))
    if not found:
        raise SystemExit("no download starts with " + prefix)
    return found[0]


def to_srgb(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def base_colour(material):
    if material is None or not material.use_nodes:
        return (0.8, 0.8, 0.8)
    for node in material.node_tree.nodes:
        if node.type == "BSDF_PRINCIPLED":
            socket = node.inputs["Base Color"]
            if socket.links:
                return None  # a picture drives it
            return tuple(socket.default_value[:3])
    return (0.8, 0.8, 0.8)


def has_picture(material):
    return material is not None and material.use_nodes and any(
        n.type == "TEX_IMAGE" and n.image is not None for n in material.node_tree.nodes)


def palette(name, meshes):
    """Every flat colour on one picture; every face on its colour's swatch; one material."""
    colours = []
    for obj in meshes:
        for slot in obj.material_slots:
            colour = base_colour(slot.material)
            if colour is not None and colour not in colours:
                colours.append(colour)
        if not obj.material_slots and (0.8, 0.8, 0.8) not in colours:
            colours.append((0.8, 0.8, 0.8))
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

    for obj in meshes:
        mesh = obj.data
        if not mesh.uv_layers:
            mesh.uv_layers.new(name="UVMap")
        while len(mesh.uv_layers) > 1:
            mesh.uv_layers.remove(mesh.uv_layers[1])
        uv = mesh.uv_layers[0].data
        slots = [base_colour(s.material) for s in obj.material_slots] or [(0.8, 0.8, 0.8)]
        for poly in mesh.polygons:
            colour = slots[min(poly.material_index, len(slots) - 1)]
            i = colours.index(colour)
            u = ((i % across) * SWATCH + SWATCH / 2) / size
            v = ((i // across) * SWATCH + SWATCH / 2) / size
            for loop in poly.loop_indices:
                uv[loop].uv = (u, v)
            poly.material_index = 0
        mesh.materials.clear()
        mesh.materials.append(material)
    return len(colours)


def height(meshes):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    low, high = 1e9, -1e9
    for obj in meshes:
        evaluated = obj.evaluated_get(depsgraph)
        mesh = evaluated.to_mesh()
        for v in mesh.vertices:
            z = (evaluated.matrix_world @ v.co).z
            low, high = min(low, z), max(high, z)
        evaluated.to_mesh_clear()
    return low, high


def flatten(armature):
    """The conversion hangs every rig at a scale of 100 over a skin in hundredths, which a loader's bounds read wrong:
    the scale and turn are applied, so every node is 1, and every clip's moves are scaled to the bones they move."""
    factor = armature.scale.x
    bpy.ops.object.select_all(action="SELECT")
    bpy.context.view_layer.objects.active = armature
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


for out, (prefix, clips) in MODELS.items():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=source_of(prefix))
    # poly.pizza's conversion leaves a stray two-unit Icosphere at the origin, bound to nothing: dropped.
    for stray in [o for o in bpy.data.objects if o.type == "MESH" and o.parent is None
                  and not any(m.type == "ARMATURE" for m in o.modifiers)]:
        bpy.data.objects.remove(stray, do_unlink=True)
    # And the leaf "_end" empties hung on the bones, which no clip moves and which a loader counts in the bounds.
    for empty in [o for o in bpy.data.objects if o.type == "EMPTY" and o.name.endswith("_end")]:
        bpy.data.objects.remove(empty, do_unlink=True)
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")

    wanted = {}
    for game, own in clips.items():
        action = next((a for a in bpy.data.actions if a.name.split("|")[-1] == own), None)
        if action is None:
            raise SystemExit(out + " has no clip " + own + ": " + str([a.name for a in bpy.data.actions]))
        wanted[action.name] = game
    for action in list(bpy.data.actions):
        if action.name not in wanted:
            bpy.data.actions.remove(action)
    for action in list(bpy.data.actions):
        action.name = wanted[action.name]
    if armature.animation_data:
        armature.animation_data.action = None
        for track in list(armature.animation_data.nla_tracks):
            armature.animation_data.nla_tracks.remove(track)

    flatten(armature)
    pictured = any(has_picture(s.material) for o in meshes for s in o.material_slots)
    swatches = 0 if pictured else palette(out, meshes)

    # Stood at rest for the measure: the height the block's ModelScale is worked out from.
    idle = bpy.data.actions["Idle"]
    ad = armature.animation_data or armature.animation_data_create()
    ad.action = idle
    if idle.slots:
        ad.action_slot = idle.slots[0]
    bpy.context.scene.frame_set(1)
    low, high = height(meshes)
    ad.action = None
    tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in meshes)

    bpy.ops.object.select_all(action="SELECT")
    path = os.path.join(OUT, out + ".glb")
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                              export_apply=False, export_cameras=False, export_lights=False, export_extras=False,
                              export_morph=False, export_def_bones=False, export_skins=True,
                              export_animations=True, export_animation_mode="ACTIONS",
                              export_image_format="AUTO", export_materials="EXPORT")
    print("EXPORTED %s tris=%d swatches=%d foot=%.3f height=%.3f clips=%s bytes=%d"
          % (out, tris, swatches, low, high - low, sorted(a.name for a in bpy.data.actions), os.path.getsize(path)))
