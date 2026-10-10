"""Cuts six castle kits from Sketchfab (all CC BY 4.0, see CREDITS.md) into the pieces a map is built from.

    ./gradlew exportCastles -PcastlesSource="C:/Users/me/Downloads/castles"
    blender -b --factory-startup -P art/castles/export_castles.py -- <the downloads, unzipped> <out folder> [kit ...]

The source folder holds each download unzipped into a folder named as its zip (modular-fortress-free/,
medieval-fortress-kit/, ...); see KITS. What ships is what this writes, under resources/models/castles/<kit>/,
which is committed; neither the downloads nor Blender are build dependencies.

ONE GRID. A cell is 10 game units and the hero 12 tall. Each kit is scaled by one factor of its own (KITS'
`factor`, game units per source unit) chosen so that its module -- the length of its wall between two towers --
is a whole number of cells, and so that its walls stand one and a half to three heroes tall:

    fort    (ayarady's medieval fortress)   2.0      a wall 3 cells, 26 tall; towers 30 to 45 tall
    rampart (FelikinRuslan's fortress)      9.09     towers every 5 cells, the wall between them 3 cells, 16 tall
    town    (bukkbeek's city wall)          6.0      long wall 6 cells, short wall 3.5, 21 tall
    hall    (pedramashoori's interior)      0.05     a wall 3 cells, 25 tall; floors 3 x 3.5 cells
    desert  (nkilstrup's desert walls)      2.5      a wall 3 cells (4 for the blocks), 20 to 28 tall
    room    (santyfrow's castle room)       3.195    a wall 2 cells between column centres, 20 tall

After scaling, a wall's length and a floor's sides are stretched to the nearest half cell when they
are within 8% of one (never the height), so pieces placed on whole and half cells meet.

PIVOTS. Y up; every piece stands on the ground its kit was built on (y = 0). Then, by kind:
  wall   -- runs along +X from the origin, its thickness centred on the line: placed on a grid line at its
            start, a facing of 0 runs it east, 90 north (up the map), 180 west, 270 south.
  tower  -- centred on the origin: placed on the grid point where walls meet.
  floor  -- its corner at the origin, covering +X and +Z (east and down the map): placed on a grid point, it
            covers the cells east and south of it.
  prop   -- centred on the origin, at the height the kit stood it (a banner keeps its place up a wall).
A piece may be measured from another's frame (`frame`): the city's gate is written in its gatehouse's frame, so
the two placed at one point and one facing stand one in the other.

PICTURES. Base colour only -- what the game draws -- as JPEG, at most 1024 across and 512 where that is plenty
(a palette of a few pixels stays PNG). One folder of pictures a kit (textures/), shared by its pieces: each
piece is a .gltf with its own .bin beside it, which is why these are .gltf and not .glb -- a .glb would carry a
copy of every picture in every piece. Normal, roughness and occlusion maps are left out of the repository.

TRIANGLES. A piece over 8000 triangles is collapsed to about 5000; the rest are as made.

THE QUEEN'S CASTLE. The fort kit is written twice: as made, and as a `_dark` copy of every piece -- the same
.bin, its pictures recoloured darker, colder and a little red (darken()) -- for the queen's dark castle. The
game cannot yet hand a scenery piece another picture or write a tint into a map (engine requests E34, E35), so
the copies are what a map names.
"""

import json
import math
import os
import re
import sys
import zipfile

import bpy
import mathutils
import numpy as np

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, OUT = os.path.abspath(args[0]), os.path.abspath(args[1])
ONLY = set(args[2:])
CELL = 10.0
SNAP = CELL / 2
SNAP_WITHIN = 0.08
MOST_TRIANGLES, COLLAPSE_TO = 8000, 5000


def path(*parts):
    """A path joined with '/', and never Blender's '//', which Windows reads as a share."""
    return "/".join(p.replace("\\", "/").rstrip("/") for p in parts)


# ---- the kits ----

def piece(name, objects=(), kind="prop", region=None, span=None, frame=None, tex=None, ground=None):
    """One piece: the source objects joined (or the loose parts of them whose middles lie in region, x0 y0 x1 y1),
    measured from the first (or from frame, another piece's first object), a wall from span[0] to span[1] along
    its length."""
    return dict(name=name, objects=list(objects), kind=kind, region=region, span=span, frame=frame, tex=tex,
                ground=ground)


def fortress_picture(material, textures):
    number = int(re.search(r"#(\d+)", material.name).group(1))
    for f in os.listdir(textures):
        if "BaseColor" in f and int(f.split("_")[0]) == number:
            return path(textures, f)
    return None


def interior_picture(material, textures):
    for node in material.node_tree.nodes if material.use_nodes else []:
        if node.type == "TEX_IMAGE" and node.image is not None:
            stem = os.path.splitext(re.split(r"[\\/]", node.image.filepath)[-1])[0]
            for ext in (".jpeg", ".jpg", ".png"):
                if os.path.exists(path(textures, stem + ext)):
                    return path(textures, stem + ext)
    return None


def desert_picture(material, textures):
    stem = re.sub(r"^(DesertTemple_|Structure_Source_)", "", material.name)
    return path(textures, stem + "_Albedo.png")


KITS = {
    "fort": dict(
        folder="medieval-fortress-kit", fbx="source/fortress.fbx", ascii=True, factor=2.0, tex=1024,
        picture=fortress_picture,
        pieces=[
            piece("wall", ["9"], "wall"),
            piece("wall_ruined", ["4"], "wall"),
            piece("wall_gable", ["7"], "wall", tex=512),
            piece("wall_gable_turret", ["8"], "wall"),
            piece("wall_spire_tower", ["11"], "wall"),
            piece("wall_pier", ["6"], "wall", tex=512),
            piece("gate", ["2"], "wall", tex=512),
            piece("gatehouse", ["14"], "wall"),
            piece("house_front", ["13"], "wall", tex=512),
            piece("house_front_narrow", ["10"], "wall", tex=512),
            piece("roof", ["19"], "prop", tex=512),
            piece("railing", ["18"], "wall", tex=512),
            piece("tower_open", ["1"], "tower"),
            piece("tower_cone", ["3"], "tower"),
            piece("tower_stepped", ["5"], "tower"),
            piece("turret", ["12"], "tower", tex=512),
            piece("keep", ["15"], "tower"),
            piece("ramp", ["16"], "prop"),
        ]),
    "rampart": dict(
        folder="modular-fortress-free", fbx="source/Tower.fbx", factor=50 / 5.5, tex=1024, loose=True, ground=0.0,
        picture=lambda m, t: path(t, ("metal" if m.name.startswith("metal") else "rock") + "_color.png"),
        sizes={"metal_color": 512},
        pieces=[
            piece("tower_tall", kind="tower", region=(-1.3, -1.3, 1.3, 1.3)),
            piece("tower", kind="tower", region=(9.7, -1.3, 12.3, 1.3)),
            piece("tower_octagon", kind="tower", region=(4.2, -1.3, 6.8, 1.3)),
            piece("wall", kind="wall", region=(1.0, -1.0, 4.5, 1.0, 1.9)),
            piece("wall_arcade", kind="wall", region=(4.3, 0.2, 6.7, 3.8, 1.9)),
            piece("wall_gate", kind="wall", region=(-1.0, 3.7, 1.0, 7.3, 1.9)),
        ]),
    "town": dict(
        folder="medieval-city-wall-assets-low-poly-modular", fbx="source/TownHall.fbx", factor=6.0, tex=64,
        picture=lambda m, t: path(t, "Colors_1.png"),  # each on its own foot: the kit sinks its towers unevenly
        pieces=[
            piece("wall_long", ["Wall.long"], "wall"),
            piece("wall_short", ["Wall.short"], "wall"),
            piece("wall_tower", ["Wall.tower"], "wall"),
            piece("wall_archers", ["Wall_ArcheryTower"], "wall"),
            piece("gatehouse", ["Gate_wall"], "wall"),
            piece("gate", ["Gate"], "wall", frame="gatehouse"),
            piece("tower_round", ["Tower.tall"], "tower"),
            piece("tower_round_low", ["Tower.short"], "tower"),
            piece("tower_slim", ["Tower"], "tower"),
            piece("palace_tower", ["Palace.tower"], "tower"),
            piece("palace_tower_mid", ["Palace.tower2"], "tower"),
            piece("palace_tower_low", ["Palace.tower1"], "tower"),
            piece("house", ["Small house"], "tower"),
            piece("banner_post", ["Cylinder"], "prop"),
            piece("bridge", ["Bridge"], "prop"),
            piece("stepping_stones", ["Rock_Corridor.001"], "prop"),
        ]),
    "hall": dict(
        folder="medieval-castle-modular-kits-interior", fbx="source/mud mud cas.fbx", factor=0.05, tex=1024,
        picture=interior_picture, ground=0.0,
        pieces=[
            piece("wall", ["Plane004"], "wall"),
            piece("wall_thick", ["Plane002"], "wall"),
            piece("corner_round", ["Plane007"], "floor"),
            piece("floor", ["Plane003"], "floor"),
            piece("skeleton_plaque", ["Box003"], "prop", tex=512),
            piece("torch", ["Cylinder002"], "prop", tex=512),
        ]),
    "desert": dict(
        folder="desert-fortress-walls", fbx="source/Desert_Fortress_Walls.fbx", factor=2.5, tex=1024,
        picture=desert_picture, ground=0.0,
        pieces=[
            piece("block_long", ["DesertTemple_WallBlock_01"], "wall"),
            piece("block_long_b", ["DesertTemple_WallBlock_03"], "wall"),
            piece("block_short", ["DesertTemple_WallBlock_02"], "wall"),
            piece("wall_sloped", ["DesertTemple_Wall_Angled_01"], "wall"),
            piece("wall_sloped_b", ["DesertTemple_Wall_Angled_02"], "wall"),
            piece("wall_tall", ["DesertTemple_Wall_Tall_01"], "wall"),
            piece("pillar_tall", ["DesertTemple_Wall_Tall_02"], "tower"),
            piece("buttress", ["DesertTemple_Wall_Angled_03"], "prop", tex=512),
            piece("buttress_tall", ["DesertTemple_Wall_Angled_04"], "prop", tex=512),
        ]),
    "room": dict(
        folder="stylized-medieval-castle-room", fbx="source/dungeon_UV.fbx", factor=20 / 6.26, tex=512,
        picture=lambda m, t: path(t, "dungeon_UV_" + re.sub(r"\.\d+$", "", m.name) + "_BaseColor.png"),
        sizes={"dungeon_UV_walls_BaseColor": 1024, "dungeon_UV_base_BaseColor": 1024,
               "dungeon_UV_columns_BaseColor": 1024, "dungeon_UV_stone_decorations_BaseColor": 1024},
        ground=-0.09,
        pieces=[
            piece("wall", ["Cube.004", "Cube.037", "Cube.038", "Cube.039"], "wall", span=(-3.65, 2.6)),
            piece("wall_arch", ["Cube.005", "Cube.011"], "wall", span=(-9.91, -3.65)),
            piece("wall_window", ["Cube.014", "Cube.016", "Cube", "Cube.044", "Cube.045"], "wall",
                  span=(-12.49, -6.24)),
            piece("cornice", ["Cube.003"], "wall", span=(-9.91, -3.65)),
            piece("column", ["Cube.001"], "tower"),
            piece("column_half", ["Cube.029"], "tower"),
            piece("column_stump", ["Cube.028"], "tower"),
            piece("floor", ["Plane"], "floor"),
            piece("basin", ["Cube.022", "Sphere", "Sphere.001"], "prop", region=(-9.4, -2.4, -8.0, -0.9)),
            piece("pedestal", ["Cube.022"], "prop", region=(-6.0, -2.4, -4.2, -0.9)),
            piece("banners", ["Cylinder"], "prop"),
            piece("banner", ["Cylinder.001"], "prop"),
            piece("sconce", ["Cylinder.002", "Cylinder.006", "Cylinder.007", "Cylinder.015"], "prop"),
            piece("shelf", ["Cube.019", "Cube.021", "Cylinder.019", "Cylinder.020"], "prop"),
            piece("barrel", ["barrel"], "prop"),
            piece("crate", ["box"], "prop"),
            piece("chest", ["chestBottom", "chestHinges", "chestTop"], "prop"),
            piece("table", ["tebleLarge"], "prop"),
            piece("table_small", ["tableSmall"], "prop"),
            piece("stool", ["Cube.047"], "prop"),
            piece("sword", ["Cylinder.024"], "prop"),
            piece("sword_leaning", ["Cylinder.022"], "prop"),
        ]),
}


# ---- the source ----

def fbx_of(kit):
    fbx = path(SOURCE, kit["folder"], kit["fbx"])
    if not os.path.exists(fbx):  # the desert kit ships its FBX inside a zip of its own
        inner = [f for f in os.listdir(os.path.dirname(fbx)) if f.endswith(".zip")]
        with zipfile.ZipFile(path(os.path.dirname(fbx), inner[0])) as z:
            z.extract(os.path.basename(fbx), os.path.dirname(fbx))
    return fbx


def open_kit(kit):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    if kit.get("ascii"):
        bpy.ops.wm.fbx_import(filepath=fbx_of(kit))  # the C++ importer: the Python one refuses ASCII FBX
    else:
        bpy.ops.import_scene.fbx(filepath=fbx_of(kit))
    for obj in list(bpy.data.objects):
        if obj.type != "MESH":
            continue
        world = obj.matrix_world.copy()
        obj.parent = None
        obj.matrix_world = world
    if kit.get("loose"):
        meshes = [o for o in bpy.data.objects if o.type == "MESH"]
        select(meshes)
        bpy.ops.object.mode_set(mode="EDIT")
        bpy.ops.mesh.separate(type="LOOSE")
        bpy.ops.object.mode_set(mode="OBJECT")


def select(objects, active=None):
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objects:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = active or objects[0]


def tris(obj):
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


def box(objects):
    points = np.array([tuple(o.matrix_world @ v.co) for o in objects for v in o.data.vertices])
    return points.min(axis=0), points.max(axis=0)


def middle(obj):
    lo, hi = box([obj])
    return (lo + hi) / 2


def sources(spec):
    """The source objects a piece is made of: by name, or every loose part whose middle is in its region."""
    meshes = [o for o in bpy.data.objects if o.type == "MESH" and not o.name.startswith("piece_")]
    if spec["objects"]:
        return [bpy.data.objects[n] for n in spec["objects"]]
    x0, y0, x1, y1, *top = spec["region"]  # and, for a wall, the height its parts sit below
    found = [o for o in meshes if x0 <= middle(o)[0] <= x1 and y0 <= middle(o)[1] <= y1
             and (not top or middle(o)[2] < top[0])]
    found.sort(key=lambda o: -np.prod(box([o])[1] - box([o])[0] + 1e-3))
    return found


# ---- pictures ----

def picture_size(kit, source):
    stem = os.path.splitext(os.path.basename(source))[0]
    return kit.get("sizes", {}).get(stem)


def written_picture(kit, prefix, source, size):
    """The source picture as the game's: downsized, JPEG (or PNG for a palette), in the kit's textures/."""
    stem = re.sub(r"[^a-z0-9]+", "_", os.path.splitext(os.path.basename(source))[0].lower()).strip("_")
    stem = re.sub(r"_?(basecolor|albedo|color)(_?\d{4})?$", "", stem)
    stem = re.sub(r"^dungeon_uv_|_lambert1", "", stem)
    image = bpy.data.images.load(source, check_existing=True)
    tiny = max(image.size) <= 64
    out = path(OUT, prefix, "textures", f"{prefix}_{stem}" + (".png" if tiny else ".jpg"))
    if out in WRITTEN:
        return WRITTEN[out]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    image = image.copy()
    w, h = image.size
    if max(w, h) > size:
        image.scale(max(1, round(w * size / max(w, h))), max(1, round(h * size / max(w, h))))
    image.filepath_raw = out
    image.file_format = "PNG" if tiny else "JPEG"
    image.save()
    saved = bpy.data.images.load(out, check_existing=True)
    WRITTEN[out] = saved
    return saved


WRITTEN = {}
MATERIALS = {}


def game_material(kit, prefix, source_material, size):
    """A material with the source's base colour picture and nothing else; a colour when it has no picture."""
    source = kit["picture"](source_material, path(SOURCE, kit["folder"], "textures"))
    if source is not None and not os.path.exists(source):
        source = None
    if source is not None:
        size = picture_size(kit, source) or size
    key = (prefix, source or source_material.name, size)
    if key in MATERIALS:
        return MATERIALS[key]
    material = bpy.data.materials.new(f"{prefix}_" + (os.path.splitext(os.path.basename(source))[0] if source
                                                       else re.sub(r"\W+", "_", source_material.name)))
    material.use_nodes = True
    tree = material.node_tree
    bsdf = next(n for n in tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    if source is not None:
        node = tree.nodes.new("ShaderNodeTexImage")
        node.image = written_picture(kit, prefix, source, size)
        if max(node.image.size) <= 64:
            node.interpolation = "Closest"  # a palette: no blending between its swatches
        tree.links.new(node.outputs["Color"], bsdf.inputs["Base Color"])
    else:
        # A part whose picture is missing from the download (the skeleton's body): a bone colour.
        bsdf.inputs["Base Color"].default_value = (0.78, 0.74, 0.62, 1.0)
    MATERIALS[key] = material
    return material


# ---- one piece ----

def frame_of(kit, spec):
    """The source object a piece is measured from, and its rotation onto +X for a wall that runs along Y."""
    if spec["frame"]:
        other = next(p for p in kit["pieces"] if p["name"] == spec["frame"])
        return frame_of(kit, other)
    found = sources(spec)
    first = found[0]
    lo, hi = box(found if spec["region"] else [first])
    along_y = spec["kind"] == "wall" and (hi[1] - lo[1]) > (hi[0] - lo[0])
    return first, along_y, spec["span"]


def snapped(length):
    whole = max(SNAP, round(length / SNAP) * SNAP)
    return whole if abs(whole - length) <= SNAP_WITHIN * length else length


def make(kit, prefix, spec):
    parts = sources(spec)
    copies = []
    for obj in parts:
        copy = obj.copy()
        copy.data = obj.data.copy()
        copy.name = "piece_" + obj.name
        copy.animation_data_clear()  # the FBX importer keys its place; kept, the exporter puts it back
        bpy.context.scene.collection.objects.link(copy)
        copies.append(copy)
    size = spec["tex"] or kit["tex"]
    for copy in copies:
        for slot in copy.material_slots:
            if slot.material is not None:
                slot.material = game_material(kit, prefix, slot.material, size)
    select(copies)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if len(copies) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    if spec["region"] and spec["objects"]:  # named objects cut down to the loose parts in the region
        obj = cut_to_region(obj, spec["region"])
    first_uv_tile(obj)

    # Measured in the source: the piece itself when cut from a region, else its frame (its first object, or
    # another piece's), or the wall's span; standing on the kit's ground.
    frame, along_y, span = frame_of(kit, spec)
    lo, hi = box([obj]) if spec["region"] else box([frame])
    ground = kit.get("ground") if spec["ground"] is None else spec["ground"]
    floor = box([obj])[0][2] if ground is None else ground
    turn = mathutils.Matrix.Identity(4)
    if along_y:  # turned a quarter clockwise, (x, y) -> (y, -x), so its length runs along +X
        turn = mathutils.Matrix.Rotation(-math.pi / 2, 4, "Z")
        lo, hi = np.array(turn @ mathutils.Vector(lo)), np.array(turn @ mathutils.Vector(hi))
        lo, hi = np.minimum(lo, hi), np.maximum(lo, hi)
    kind = spec["kind"]
    if kind == "wall":
        start, end = sorted(span) if span else (lo[0], hi[0])  # a span along Y is the same numbers along X
        origin = (start, (lo[1] + hi[1]) / 2)
        lengths = ((end - start), None)
    elif kind == "floor":
        origin = (lo[0], hi[1])
        lengths = (hi[0] - lo[0], hi[1] - lo[1])
    else:
        origin = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2)
        lengths = (None, None)  # centred on the point walls meet at, a tower is as wide as it was made
    f = kit["factor"]
    stretch = [1.0, 1.0]
    for axis, length in enumerate(lengths):
        if length:
            stretch[axis] = snapped(length * f) / (length * f)
    move = (mathutils.Matrix.Diagonal((f * stretch[0], f * stretch[1], f, 1.0))
            @ mathutils.Matrix.Translation((-origin[0], -origin[1], -floor)) @ turn)
    obj.data.transform(move)
    obj.matrix_world = mathutils.Matrix.Identity(4)
    before = tris(obj)
    if before > MOST_TRIANGLES:
        mod = obj.modifiers.new("collapse", "DECIMATE")
        mod.ratio = COLLAPSE_TO / before
        mod.use_collapse_triangulate = True
        select([obj])
        bpy.ops.object.modifier_apply(modifier=mod.name)
    name = f"{prefix}_{spec['name']}"
    obj.name = name
    obj.data.name = name
    out = path(OUT, prefix, name + ".gltf")
    write(obj, out)
    lo, hi = box([obj])
    LOG.append(f"PIECE {prefix}/{name} kind={kind} tris={before}->{tris(obj)} "
               f"size={hi[0] - lo[0]:.1f}x{hi[1] - lo[1]:.1f}x{hi[2] - lo[2]:.1f} stretch={stretch[0]:.3f},"
               f"{stretch[1]:.3f}")
    bpy.data.objects.remove(obj, do_unlink=True)


def cut_to_region(obj, region):
    x0, y0, x1, y1 = region
    select([obj])
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.separate(type="LOOSE")
    bpy.ops.object.mode_set(mode="OBJECT")
    loose = list(bpy.context.selected_objects)
    keep = [o for o in loose if x0 <= middle(o)[0] <= x1 and y0 <= middle(o)[1] <= y1]
    for o in loose:
        if o not in keep:
            bpy.data.objects.remove(o, do_unlink=True)
    select(keep)
    if len(keep) > 1:
        bpy.ops.object.join()
    return bpy.context.view_layer.objects.active


def first_uv_tile(obj):
    """UVs moved into the first tile: the fort kit's pictures are UDIM tiles (1002 is u in 1..2)."""
    for layer in obj.data.uv_layers:
        uv = np.zeros(len(layer.data) * 2)
        layer.data.foreach_get("uv", uv)
        uv = uv.reshape(-1, 2)
        if len(uv):
            uv -= np.floor(uv.min(axis=0) + 1e-4)
            layer.data.foreach_set("uv", uv.ravel())


def write(obj, out):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    select([obj])
    bpy.ops.export_scene.gltf(
        filepath=out, export_format="GLTF_SEPARATE", use_selection=True, export_yup=True, export_apply=True,
        export_keep_originals=True, export_texcoords=True, export_normals=True, export_materials="EXPORT",
        export_cameras=False, export_lights=False, export_extras=False, export_animations=False,
        export_skins=False, export_morph=False)


# ---- the queen's castle ----

def darken(rgb):
    """Darker, colder, a little red: the stone's own shading kept, its colour drained toward a cold violet."""
    grey = rgb @ np.array([0.30, 0.59, 0.11])
    drained = rgb * 0.35 + grey[..., None] * 0.65
    return np.clip(drained * 0.6 * np.array([1.0, 0.80, 1.06]) + np.array([0.025, 0.0, 0.01]), 0, 1)


def dark_copies(prefix):
    folder = path(OUT, prefix)
    for f in sorted(os.listdir(path(folder, "textures"))):
        if f.endswith("_dark.jpg") or not f.endswith(".jpg"):
            continue
        image = bpy.data.images.load(path(folder, "textures", f))
        w, h = image.size
        pixels = np.array(image.pixels[:]).reshape(h, w, 4)
        pixels[..., :3] = darken(pixels[..., :3])
        dark = bpy.data.images.new(f[:-4] + "_dark", w, h)
        dark.pixels[:] = pixels.ravel()
        dark.filepath_raw = path(folder, "textures", f[:-4] + "_dark.jpg")
        dark.file_format = "JPEG"
        dark.save()
    for f in sorted(os.listdir(folder)):
        if not f.endswith(".gltf") or f.endswith("_dark.gltf"):
            continue
        with open(path(folder, f), encoding="utf-8") as stream:
            gltf = json.load(stream)
        for image in gltf.get("images", []):
            image["uri"] = re.sub(r"\.jpg$", "_dark.jpg", image["uri"])
            image["name"] = image.get("name", "") + "_dark"
        for material in gltf.get("materials", []):
            material["name"] = material.get("name", "") + "_dark"
        with open(path(folder, f[:-5] + "_dark.gltf"), "w", encoding="utf-8") as stream:
            json.dump(gltf, stream, indent=1)
        LOG.append(f"DARK {prefix}/{f[:-5]}_dark")


LOG = []


def main():
    for prefix, kit in KITS.items():
        if ONLY and prefix not in ONLY:
            continue
        WRITTEN.clear()
        MATERIALS.clear()
        open_kit(kit)
        for spec in kit["pieces"]:
            make(kit, prefix, spec)
        if prefix == "fort":
            dark_copies(prefix)
    print("\n".join(LOG))


main()
