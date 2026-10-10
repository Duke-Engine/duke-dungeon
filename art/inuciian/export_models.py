"""Cuts Inuciian's "Baba Yaga's hut" into the models the game draws.

    ./gradlew exportInuciian                          # Blender found as convertAnimations finds it
    blender -b --factory-startup -P art/inuciian/export_models.py -- <the .blend> <its textures folder> <out folder>

The scene is one .blend from Sketchfab (Inuciian, CC BY 4.0 -- see CREDITS.md):
a hut on chicken legs, the old woman stirring her cauldron, a girl in a cage,
an imp, a forest round them. The game wants it as pieces -- one glTF per kind
-- so this opens the scene once per piece, keeps that piece, and writes it.
The source is not in the repository (it is 70 MB of someone else's file); what
ships is the .glb this writes, which is committed, and Blender is not a build
dependency.

What happens to every piece on the way:

TEXTURES ARE WIRED AGAIN. The file was made in Blender 2.79, whose materials
held their pictures in texture slots that today's Blender drops on opening: every
material comes in as plain grey. Each is wired again by name -- `bench_mat` is
`bench_albedo.png` -- or, for a material that had a colour of its own rather
than a picture (wood, rock, metal, the autumn grass), given that colour. A piece
with no UVs at all gets its picture's average colour. The pictures go inside the
.glb, so a model is one file.

ONE SIZE A KIND. Each piece is written at the size it stands in the game at a
scale of 1, in the game's units (a cell is 10, a hero 12 tall). People and the
things they handle keep the scene's own proportions, at PEOPLE units a metre: the
old woman comes out a head shorter than the hero, the cauldron a cell across.
The hut is written smaller than true (HUT units a metre) so the top-down camera
sees past it, and each piece of the forest at the height it is given below, so a
pine stands like the KayKit pines it grows among.

Y UP, ORIGIN AT THE FOOT, FACING THE GAME'S WAY. The exporter turns Blender's Z up
into glTF's Y up; each piece is moved so the middle of its foot is at the origin;
and the people and the hut are turned to face glTF's +Z, as the KayKit people do
(so a block says Facing = 90 for them as for those).

THE DARK WOOD. The forest's foliage is autumn gold. The Qorong'i o'rmon wants it
dark, so the pine, the bushes and the grass are written twice: as made, and as a
`_dark` copy whose foliage is turned to dark green and blue-grey (its bark and
stone left alone) -- see darken().

PEOPLE MOVE. The old woman and the imp are rigged, and the girl's cage swings;
each is written with its scene animation as one clip called `Idle`, which is what
their Monster blocks name.
"""

import math
import os
import re
import sys

import bpy
import mathutils
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, TEXTURES, OUT = args[0], args[1], args[2]

# Game units a metre, for the people and what they handle; and for the hut, drawn smaller than true.
PEOPLE = 7.5
HUT = 4.5

# Materials that had a picture, by a name that is not the picture's own.
ALIAS = {"hillhock": "hillock", "hourglas": "hourglass", "leafs": "leaves", "shelf_tex": "shelf"}
# Never drawn: the smoke and fog sheets, the candle flames, an invisible VR collider.
SKIP_MATERIALS = {"Smoke_mat", "fire_mat", "Transparent", "water_mat", "terrain_mat"}

TEXTURE_FILES = {f.lower(): f for f in os.listdir(TEXTURES)}


def stem(material_name):
    name = re.sub(r"\.\d+$", "", material_name).lower()
    name = re.sub(r"(_| )(mat|material|tex)$", "", name)
    return ALIAS.get(name, name)


def picture_for(material):
    """The picture a material wore in 2.79, or None for one that was a colour."""
    if tuple(round(c, 2) for c in material.diffuse_color[:3]) != (0.8, 0.8, 0.8):
        return None  # a colour of its own: it had no picture
    s = stem(material.name)
    for candidate in (s + "_albedo.png", s + ".png"):
        if candidate in TEXTURE_FILES:
            return os.path.join(TEXTURES, TEXTURE_FILES[candidate])
    return None


def new_material(name, colour=None, image=None):
    material = bpy.data.materials.new(name)  # never the 2.79 one of the same name, whose nodes are not wired
    material.use_nodes = True
    tree = material.node_tree
    bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    for link in list(bsdf.inputs["Base Color"].links):
        tree.links.remove(link)
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    if image is not None:
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        node.interpolation = "Closest"  # a palette atlas: no blending between swatches
        tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
    else:
        bsdf.inputs["Base Color"].default_value = (*colour[:3], 1.0)
    return material


def mean_colour(image):
    pixels = np.array(image.pixels[:]).reshape(-1, 4)
    return tuple(pixels[:, :3].mean(axis=0))


def wire(objects):
    """Every material of these objects drawn again: its picture, its colour, or its picture's average."""
    made = {}
    for obj in objects:
        has_uv = len(obj.data.uv_layers) > 0
        for slot in obj.material_slots:
            old = slot.material
            if old is None:
                continue
            path = picture_for(old)
            key = (path or old.name, has_uv)
            if key not in made:
                if path is None:
                    made[key] = new_material("C_" + stem(old.name), colour=old.diffuse_color)
                else:
                    image = bpy.data.images.load(path, check_existing=True)
                    if has_uv:
                        made[key] = new_material(os.path.splitext(os.path.basename(path))[0], image=image)
                    else:
                        made[key] = new_material("A_" + stem(old.name), colour=mean_colour(image))
            slot.material = made[key]


# ---- the dark wood ----

def darken_rgb(rgb, hue):
    """Foliage -- saturated yellows, oranges, reds -- turned to a dark {hue}; bark and stone, unsaturated, left."""
    rgb = np.asarray(rgb, dtype=np.float64)
    mx = rgb.max(axis=-1)
    mn = rgb.min(axis=-1)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    foliage = sat > 0.45
    # Keep each swatch's place in its own range of light, so the atlas keeps its shading.
    value = mx * 0.42
    s = np.clip(sat * 0.55, 0, 1)
    h = (hue + (rgb[..., 0] - rgb[..., 1]) * 20.0) % 360 / 60.0
    i = np.floor(h).astype(int) % 6
    f = h - np.floor(h)
    p = value * (1 - s)
    q = value * (1 - s * f)
    t = value * (1 - s * (1 - f))
    table = [(value, t, p), (q, value, p), (p, value, t), (p, q, value), (t, p, value), (value, p, q)]
    out = np.zeros_like(rgb)
    for k, (r, g, b) in enumerate(table):
        mask = i == k
        out[..., 0] = np.where(mask, r, out[..., 0])
        out[..., 1] = np.where(mask, g, out[..., 1])
        out[..., 2] = np.where(mask, b, out[..., 2])
    return np.where(foliage[..., None], out, rgb)


def darken(objects, hue):
    """Every material of these objects in the dark wood's colours: a copy of each picture, or of each colour."""
    for obj in objects:
        for slot in obj.material_slots:
            material = slot.material
            if material is None or material.name.endswith("_dark"):
                continue
            dark = material.copy()
            dark.name = material.name + "_dark"
            tree = dark.node_tree
            bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
            pictures = [n for n in tree.nodes if n.type == "TEX_IMAGE"]
            if pictures:
                source = pictures[0].image
                w, h = source.size
                pixels = np.array(source.pixels[:]).reshape(h, w, 4)
                pixels[..., :3] = darken_rgb(pixels[..., :3], hue)
                image = bpy.data.images.new(os.path.splitext(source.name)[0] + "_dark", w, h, alpha=True)
                image.pixels[:] = pixels.ravel()
                image.pack()
                pictures[0].image = image
            else:
                colour = bsdf.inputs["Base Color"].default_value
                bsdf.inputs["Base Color"].default_value = (*darken_rgb(colour[:3], hue), 1.0)
            slot.material = dark


# ---- one piece ----

def open_scene():
    bpy.ops.wm.open_mainfile(filepath=SOURCE)
    # The rigs are in a collection the file keeps out of the view layer, and what is not in it is not written.
    def include(layer):
        layer.exclude = False
        layer.hide_viewport = False
        layer.collection.hide_viewport = False
        layer.collection.hide_render = False
        for child in layer.children:
            include(child)
    include(bpy.context.view_layer.layer_collection)
    for obj in bpy.data.objects:
        obj.hide_viewport = False
        obj.hide_render = False
        obj.hide_set(False)


def world_box(objects):
    depsgraph = bpy.context.evaluated_depsgraph_get()
    points = []
    for obj in objects:
        if obj.type != "MESH":
            continue
        evaluated = obj.evaluated_get(depsgraph)
        points += [evaluated.matrix_world @ mathutils.Vector(c) for c in evaluated.bound_box]
    lo = mathutils.Vector([min(p[i] for p in points) for i in range(3)])
    hi = mathutils.Vector([max(p[i] for p in points) for i in range(3)])
    return lo, hi


def keep_only(names):
    keep = set(names)
    for obj in list(bpy.data.objects):
        if obj.name not in keep:
            bpy.data.objects.remove(obj, do_unlink=True)
    return [bpy.data.objects[n] for n in names]


def write(path, animated):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.export_scene.gltf(
        filepath=path, export_format="GLB", use_selection=True, export_yup=True, export_apply=True,
        export_texcoords=True, export_normals=True, export_materials="EXPORT", export_image_format="AUTO",
        export_cameras=False, export_lights=False, export_extras=False,
        export_animations=animated, export_animation_mode="SCENE", export_anim_scene_split_object=False,
        export_force_sampling=True,
        export_morph=False, export_skins=animated)


def static_piece(out, names, factor=None, height=None, turn=0.0, foot=None, dark_hue=None, upright=False,
                 along_x=False):
    """{names} joined into one mesh, its foot at the origin, at {factor} units a metre or {height} units tall."""
    open_scene()
    objects = keep_only(names)
    meshes = [o for o in objects if o.type == "MESH"]
    for obj in meshes:
        if obj.data.shape_keys:
            obj.shape_key_clear()
    bpy.context.scene.frame_set(0)
    for obj in meshes:
        world = obj.matrix_world.copy()
        obj.animation_data_clear()  # standing still: a keyed place would put it back where it was
        if upright:
            world = mathutils.Matrix.Translation(world.to_translation()) @ mathutils.Matrix.Diagonal(
                (*world.to_scale(), 1.0))  # stood up: the scene lays this one on its side
        obj.parent = None
        obj.matrix_world = world
        obj.data = obj.data.copy()  # its own mesh: one shared with a copy elsewhere cannot take a modifier
    wire(meshes)
    if dark_hue is not None:
        darken(meshes, dark_hue)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in meshes:
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        # The 2.79 modifiers (edge split, a mirror) made real, so the join keeps them.
        for modifier in list(obj.modifiers):
            bpy.ops.object.modifier_apply(modifier=modifier.name)
    for obj in meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    if len(meshes) > 1:
        bpy.ops.object.join()
    joined = bpy.context.view_layer.objects.active
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if along_x:  # a length of fence laid along glTF's X, so a map turns it along the line it is part of
        xy = np.array([(v.co.x, v.co.y) for v in joined.data.vertices])
        xy -= xy.mean(axis=0)
        eigen = np.linalg.eigh(np.cov(xy.T))[1][:, -1]
        turn -= math.atan2(eigen[1], eigen[0])
        joined.data.transform(mathutils.Matrix.Rotation(turn, 4, "Z"))
        turn = 0.0
    lo, hi = world_box([joined])
    middle = foot if foot is not None else ((lo.x + hi.x) / 2, (lo.y + hi.y) / 2)
    if factor is None:
        factor = height / (hi.z - lo.z)
    move = (mathutils.Matrix.Rotation(turn, 4, "Z") @ mathutils.Matrix.Scale(factor, 4)
            @ mathutils.Matrix.Translation((-middle[0], -middle[1], -lo.z)))
    joined.data.transform(move)
    joined.name = os.path.splitext(os.path.basename(out))[0]
    joined.data.name = joined.name
    write(out, animated=False)
    report(out, joined, factor, False)


def animated_piece(out, names, factor, turn=0.0, foot=None):
    """{names} as they are, rigs and swinging cages kept, sized, turned and with their foot at the origin."""
    open_scene()
    objects = keep_only(names)
    meshes = [o for o in objects if o.type == "MESH"]
    for obj in meshes:
        if obj.data.shape_keys:
            obj.shape_key_clear()
    wire(meshes)
    scene = bpy.context.scene
    scene.frame_start, scene.frame_end = 0, 68
    scene.frame_set(0)
    rigs = [o for o in objects if o.type == "ARMATURE"]
    if rigs:  # measured as it is bound to its rig, which is how a loader measures it, not in its keyed pose
        points = [m.matrix_world @ v.co for m in meshes for v in m.data.vertices]
        lo = mathutils.Vector([min(p[i] for p in points) for i in range(3)])
        hi = mathutils.Vector([max(p[i] for p in points) for i in range(3)])
    else:
        lo, hi = world_box(meshes)
    middle = foot if foot is not None else ((lo.x + hi.x) / 2, (lo.y + hi.y) / 2)
    if rigs:
        bake_into_rig(rigs[0], meshes, factor, turn, middle, lo.z)
    else:
        under_a_root(objects, names, factor, turn, middle, lo.z)
    scene.name = "Idle"  # the scene's animation is written as one clip, named after the scene
    write(out, animated=True)
    report(out, meshes[0], factor, True, meshes)


def fcurves(action):
    return [f for layer in action.layers for strip in layer.strips for bag in strip.channelbags for f in bag.fcurves]


def bake_into_rig(rig, meshes, factor, turn, middle, floor):
    """A rig sized, turned and centred in its own bones and mesh, so it is written with no transform over it.

    Not a root node over it, as the cage has: jME applies a skinned model's root transform to its joints and to
    its mesh both, and the old woman came out ninety units tall.
    """
    for obj in meshes:
        obj.animation_data_clear()  # the rig moves them; a mesh's own keyed drift is dropped
    move = (mathutils.Matrix.Rotation(turn, 4, "Z") @ mathutils.Matrix.Scale(factor, 4)
            @ mathutils.Matrix.Translation((-middle[0], -middle[1], -floor)))
    rig.matrix_world = move @ rig.matrix_world
    bpy.context.view_layer.update()
    bpy.ops.object.select_all(action="DESELECT")
    for obj in [rig] + meshes:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    # A bone's keyed place is in its own space, which was sized with the rig.
    for curve in fcurves(rig.animation_data.action):
        if curve.data_path.endswith(".location"):
            for key in curve.keyframe_points:
                key.co.y *= factor
                key.handle_left.y *= factor
                key.handle_right.y *= factor


def under_a_root(objects, names, factor, turn, middle, floor):
    """Things that move as whole objects -- a swinging cage -- under a root that sizes, turns and centres them."""
    scene = bpy.context.scene
    root = bpy.data.objects.new("root", None)
    scene.collection.objects.link(root)
    offset = bpy.data.objects.new("offset", None)
    scene.collection.objects.link(offset)
    offset.parent = root
    offset.location = (-middle[0], -middle[1], -floor)
    root.scale = (factor, factor, factor)
    root.rotation_euler = (0, 0, turn)
    for obj in objects:
        if obj.parent is None or obj.parent.name not in names:
            # Its own place becomes its place under the root: moved to the foot, then sized and turned.
            obj.parent = offset
            obj.matrix_parent_inverse = mathutils.Matrix.Identity(4)


LOG = []


def report(out, obj, factor, animated, meshes=None):
    tris = sum(sum(len(p.vertices) - 2 for p in m.data.polygons) for m in (meshes or [obj]))
    LOG.append(f"EXPORTED {os.path.relpath(out, OUT)} tris={tris} factor={factor:.2f} animated={animated}")
    print(LOG[-1])


# ---- what is cut out ----

def names_where(test):
    open_scene()
    return [o.name for o in bpy.data.objects if test(o)]


def centre(obj):
    lo, hi = world_box([obj])
    return (lo + hi) / 2


def hut_parts():
    """The hut whole: the legs, walls, roof, chimney, door, both ladders and what stands inside."""
    def inside(o):
        if o.type != "MESH" or any(s.material and s.material.name in SKIP_MATERIALS for s in o.material_slots):
            return False
        if o.name.startswith(("hut_", "chimney", "ladder_1")):
            return True
        c = centre(o)
        return 0.0 <= c.x <= 7.0 and -5.6 <= c.y <= 1.6 and c.z > 1.4 and o.name != "smoke"
    return names_where(inside)


def main():
    people = os.path.join(OUT, "people")
    props = os.path.join(OUT, "props")
    nature = os.path.join(OUT, "nature")
    half = math.pi

    # People: she faces her cauldron (+Y in the scene), turned to face glTF's +Z as the KayKit people do.
    animated_piece(os.path.join(people, "baba_yaga.glb"), ["Armature_Baba_Yaga", "Baba_Yaga"], PEOPLE, turn=half)
    animated_piece(os.path.join(people, "imp.glb"), ["Armature_imp.001", "Imp.001"], PEOPLE, turn=half)
    static_piece(os.path.join(people, "damsel.glb"), ["lady"], PEOPLE, turn=half)
    # In her cage on its gibbet, the cage swinging; her cell is under the cage.
    cage = ["lady", "cage", "Cylinder.026", "Cube.000", "Cube.001", "Cube.008"]
    open_scene()
    lo, hi = world_box([bpy.data.objects["cage"]])
    animated_piece(os.path.join(people, "damsel_caged.glb"), cage, PEOPLE, turn=half,
                   foot=((lo.x + hi.x) / 2, (lo.y + hi.y) / 2))

    # The hut, one model: its door faces +Y in the scene, turned to glTF's +Z; its foot the legs' middle.
    parts = hut_parts()
    open_scene()
    legs_lo, legs_hi = world_box([bpy.data.objects["hut_stand"]])
    static_piece(os.path.join(OUT, "hut.glb"), parts, HUT, turn=half,
                 foot=((legs_lo.x + legs_hi.x) / 2, (legs_lo.y + legs_hi.y) / 2))

    # What stands round it, at the people's scale.
    firewood = ["Cylinder.010", "Cylinder.029", "Cylinder.030", "Cylinder.031", "Cylinder.033", "Cylinder.034",
                "Cylinder.037"]
    static_piece(os.path.join(props, "cauldron.glb"), ["cauldron", "soup_ladle"] + firewood, PEOPLE)
    static_piece(os.path.join(props, "bench.glb"), ["bench"], PEOPLE)
    static_piece(os.path.join(props, "stool.glb"), ["Cube.088"], PEOPLE)
    static_piece(os.path.join(props, "bucket.glb"), ["bucket"], PEOPLE)
    static_piece(os.path.join(props, "broom.glb"), ["broom"], PEOPLE)
    static_piece(os.path.join(props, "fence.glb"), ["fence_1"], PEOPLE, along_x=True)
    static_piece(os.path.join(props, "fence_gate.glb"), ["fence_2"], PEOPLE, along_x=True)
    static_piece(os.path.join(props, "scarecrow.glb"), ["scarecrow.001", "scarecrow_target", "scarecrow_circles"], PEOPLE)
    static_piece(os.path.join(props, "torch.glb"), ["torch"], PEOPLE)
    static_piece(os.path.join(props, "well.glb"), ["Cylinder.108", "Cylinder.109"], PEOPLE)
    static_piece(os.path.join(props, "boat.glb"), ["boat", "oar", "oar.001"], PEOPLE)
    static_piece(os.path.join(props, "fishing_rod.glb"), ["fishing_rod", "fishing_rod_hook"], PEOPLE)
    static_piece(os.path.join(props, "rat.glb"), ["rat"], PEOPLE)
    static_piece(os.path.join(props, "candles.glb"), ["candle.005", "candle.006", "candle.007"], PEOPLE)

    # The forest, each at the height it stands among the KayKit pieces (a KayKit pine is 11 to 17 tall).
    for dark, hue in ((False, None), (True, 140.0)):
        tail = "_dark" if dark else ""
        static_piece(os.path.join(nature, f"pine{tail}.glb"), ["Tree_1"], height=19.0, dark_hue=hue)
        static_piece(os.path.join(nature, f"bush_large{tail}.glb"), ["bush_1"], height=6.0, dark_hue=hue)
        static_piece(os.path.join(nature, f"tree_leafy{tail}.glb"), ["tree_2.001"], height=15.0, dark_hue=hue)
    for dark, hue in ((False, None), (True, 175.0)):
        tail = "_dark" if dark else ""
        static_piece(os.path.join(nature, f"grass_tall{tail}.glb"), ["grass_2"], height=2.6, dark_hue=hue)
        static_piece(os.path.join(nature, f"grass_short{tail}.glb"), ["grass_1"], height=1.8, dark_hue=hue)
    static_piece(os.path.join(nature, "dead_tree.glb"), ["Cylinder.003"], height=16.0)
    static_piece(os.path.join(nature, "dead_tree_bent.glb"), ["Cylinder.005"], height=13.0)
    static_piece(os.path.join(nature, "dead_tree_tall.glb"), ["tree_3"], height=15.0)
    static_piece(os.path.join(nature, "bush_bare.glb"), ["bush_1.001"], height=4.0)
    static_piece(os.path.join(nature, "rock.glb"), ["rock_1"], height=4.0)
    static_piece(os.path.join(nature, "rock_small.glb"), ["rock_2"], height=1.2)
    static_piece(os.path.join(nature, "mushroom_red.glb"), ["mushroom_1"], PEOPLE, upright=True)
    static_piece(os.path.join(nature, "mushroom_brown.glb"), ["mushroom_2"], PEOPLE, upright=True)
    cattails = [f"cattail_plant.0{n}" for n in range(39, 48)]
    static_piece(os.path.join(nature, "cattails.glb"), cattails, height=6.0)
    static_piece(os.path.join(nature, "mound.glb"), ["Hillhock"], height=5.0)
    static_piece(os.path.join(nature, "stump.glb"), ["stump"], PEOPLE)
    static_piece(os.path.join(nature, "leaves.glb"), ["leafs"], PEOPLE)

    print("\n".join(LOG))


main()
