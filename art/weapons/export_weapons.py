"""Two weapon packs made into the game's hand-held models.

    ./gradlew exportWeapons -PelvenSource=".../free-elven-assassin-weapon-set-bow-dagger" \
        -PmedievalSource=".../low-poly-medieval-weapons-free-pack"
    blender -b --factory-startup -P art/weapons/export_weapons.py -- \
        <KatanaBow.fbx> <its Textures folder> <melee.FBX> <textureFlat.png> <out folder>

ELVEN (nodgerty, "FREE - Elven Assassin Weapon Set Bow & Dagger", CC BY 4.0): a recurve
bow and a curved dagger, lying at an angle in their scene. Each is found by its own shape
(principal axes) and laid out the way the KayKit weapons are, so a Held block hangs it
with the numbers it hung KayKit's with:

- the BOW runs along glTF Z (Blender Y), 1.98 long like the KayKit bow, its grip on the
  origin and bulging toward +X, its string the straight line on -X -- so Roll = 180 in
  rogue.duke still puts the string toward her draw hand
  (DungeonMonsterArtTest.theBowIsTurnedToSuitWhichSideItsStringIsOn reads exactly this);
- the DAGGER runs along glTF +Y (Blender +Z), blade outward from the grip on the origin,
  the flat of the blade across X, 0.55 long -- a quarter of the Huntress.

Collapsed to about 3,500 and 1,500 triangles; base colour only (the game reads a held
thing's first picture as its colour, DukeRtsApp.hang), 1024 for the bow, 512 for the dagger.

MEDIEVAL (panarth, "Low poly medieval weapons free pack", CC BY 4.0): nine weapons laid
flat side by side on one colour atlas. Each is written as a glTF of its own, named for what
it is, standing up glTF +Y with its head or blade up and its grip on the origin (the bow,
quiver, arrow and shields on their middle), all at one size -- the sword as long as the
KayKit one-handed sword, 1.78 -- and the atlas inside, as a PNG.
"""

import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index("--") + 1:]
ELVEN, ELVEN_PICTURES, MEDIEVAL, ATLAS, OUT = args[:5]

BOW_LENGTH, DAGGER_LENGTH = 1.98, 0.55
BOW_TRIANGLES, DAGGER_TRIANGLES = 3500, 1500
KAYKIT_SWORD = 1.78  # sword_1handed.gltf, pommel to tip

# The medieval scene's names, and what each is called here.
MEDIEVAL_NAMES = {"sword": "sword", "ax": "axe", "warhammer": "warhammer", "knife": "dagger",
                  "lshield": "shield_round", "hshield": "shield_heater", "bow": "bow", "quiver": "quiver",
                  "arrow": "arrow"}
# Held by a grip: the share of its length from the handle's end the hand closes on.
GRIPPED = {"sword": 0.15, "axe": 0.12, "warhammer": 0.12, "dagger": 0.2}


def fresh(path):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=path)
    return [o for o in bpy.data.objects if o.type == "MESH"]


def tris(obj):
    return sum(len(p.vertices) - 2 for p in obj.data.polygons)


def only(obj):
    """This mesh alone, its transform in its vertices, nothing else in the file."""
    for other in list(bpy.data.objects):
        if other != obj:
            bpy.data.objects.remove(other, do_unlink=True)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)


def points(obj):
    return np.array([list(v.co) for v in obj.data.vertices])


def place(obj, origin, axes, scale):
    """Vertices moved so `origin` is the origin and `axes` (columns: new X, Y, Z) the axes, then sized."""
    turn = Matrix([list(axes[:, i]) for i in range(3)])  # rows: the new axes, so it takes world into them
    obj.data.transform(Matrix.Scale(scale, 4) @ turn.to_4x4() @ Matrix.Translation(-Vector(origin)))


def collapse(obj, triangles):
    if tris(obj) <= triangles:
        return
    mod = obj.modifiers.new("collapse", "DECIMATE")
    mod.ratio = triangles / tris(obj)
    bpy.ops.object.modifier_apply(modifier=mod.name)


def one_picture(obj, path, size):
    """One material: the picture as its base colour, downsized, nothing else."""
    image = bpy.data.images.load(path)
    if max(image.size) > size:
        image.scale(size, size)
    material = bpy.data.materials.new(os.path.splitext(os.path.basename(path))[0])
    tree = material.node_tree
    bsdf = tree.nodes.get("Principled BSDF")
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Roughness"].default_value = 1.0
    tex = tree.nodes.new("ShaderNodeTexImage")
    tex.image = image
    tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    obj.data.materials.clear()
    obj.data.materials.append(material)


def write(obj, path, image_format):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                              export_materials="EXPORT", export_image_format=image_format,
                              export_jpeg_quality=85, export_animations=False, export_skins=False,
                              export_cameras=False, export_lights=False, export_extras=False)
    lo, hi = points(obj).min(0), points(obj).max(0)
    print(f"WEAPON {os.path.relpath(path, OUT)} tris={tris(obj)} blender-box={np.round(lo, 3)}..{np.round(hi, 3)}"
          f" bytes={os.path.getsize(path)}")


# ---- elven ----

def principal(p):
    middle = p.mean(0)
    _, _, rows = np.linalg.svd(p - middle, full_matrices=False)
    return middle, rows  # rows[0] the longest way, rows[2] the thinnest


def bow():
    obj = next(o for o in fresh(ELVEN) if o.name.startswith("BowLP"))
    only(obj)
    p = points(obj)
    middle, axes = principal(p)
    along = axes[0]
    t = (p - middle) @ along
    tip_a, tip_b = p[t.argmin()], p[t.argmax()]
    # The string runs tip to tip; the grip is what stands out of that line in the middle.
    line = (tip_b - tip_a) / np.linalg.norm(tip_b - tip_a)
    centre = (t - (t.min() + t.max()) / 2)
    mid = p[np.abs(centre) < 0.06 * (t.max() - t.min())]
    off = (mid - tip_a) - np.outer((mid - tip_a) @ line, line)
    far = np.linalg.norm(off, axis=1)
    grip_side = off[far > 0.5 * far.max()]
    out = grip_side.mean(0)
    out /= np.linalg.norm(out)
    grip = mid[far > 0.3 * far.max()].mean(0)
    x = out - line * (out @ line)
    x /= np.linalg.norm(x)
    y = line
    z = np.cross(x, y)
    place(obj, grip, np.stack([x, y, z], axis=1), BOW_LENGTH / (t.max() - t.min()))
    collapse(obj, BOW_TRIANGLES)
    one_picture(obj, os.path.join(ELVEN_PICTURES, "Bow_Base_color.png"), 1024)
    write(obj, os.path.join(OUT, "elven", "bow.glb"), "JPEG")


def dagger():
    obj = next(o for o in fresh(ELVEN) if o.name.startswith("KnifeLP"))
    only(obj)
    p = points(obj)
    middle, axes = principal(p)
    along = axes[0]
    t = (p - middle) @ along
    length = t.max() - t.min()

    def flatness(segment):  # how much wider one way than the other
        q = p[segment] - p[segment].mean(0)
        w, h = np.abs(q @ axes[1]).max(), np.abs(q @ axes[2]).max()
        return max(w, h) / max(min(w, h), 1e-6)

    low, high = t < t.min() + 0.35 * length, t > t.max() - 0.35 * length
    # The blade is the flat end, wide and thin; the grip, a hooked bar, is the rounder one.
    if flatness(high) > flatness(low):  # the blade is toward +along: turn the grip there instead
        along, t = -along, -t
    # Now the grip is at +along and the blade toward -along; the hand closes a fifth of the way in.
    grip_at = t.max() - GRIPPED["dagger"] * length
    grip = p[np.abs(t - grip_at) < 0.05 * length].mean(0)
    y = -along  # the blade, outward from the grip
    x = axes[1] - y * (axes[1] @ y)
    x /= np.linalg.norm(x)
    z = np.cross(x, y)
    # Blender: the blade up +Z, its flat across X -- which glTF's Y up makes +Y.
    place(obj, grip, np.stack([x, -z, y], axis=1), DAGGER_LENGTH / length)
    collapse(obj, DAGGER_TRIANGLES)
    one_picture(obj, os.path.join(ELVEN_PICTURES, "DefaultMaterial_Base_color.png"), 512)
    write(obj, os.path.join(OUT, "elven", "dagger.glb"), "JPEG")


# ---- medieval ----

def medieval():
    names = [o.name for o in fresh(MEDIEVAL)]
    for name in names:
        obj = next(o for o in fresh(MEDIEVAL) if o.name == name)
        only(obj)
        called = MEDIEVAL_NAMES.get(name, name)
        # Laid flat, head or blade toward -Y: stood up, that is +Z (Blender), +Y (glTF).
        obj.data.transform(Matrix.Rotation(math.pi / 2, 4, "X"))
        p = points(obj)
        lo, hi = p.min(0), p.max(0)
        if called in GRIPPED:
            origin = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2] + GRIPPED[called] * (hi[2] - lo[2]))
        else:
            origin = (lo + hi) / 2
        obj.data.transform(Matrix.Scale(SCALE, 4) @ Matrix.Translation(-Vector(origin)))
        one_picture(obj, ATLAS, 256)
        write(obj, os.path.join(OUT, "medieval", called + ".glb"), "AUTO")


def medieval_scale():
    sword = next(o for o in fresh(MEDIEVAL) if o.name == "sword")
    return KAYKIT_SWORD / max(sword.dimensions)


bow()
dagger()
SCALE = medieval_scale()
medieval()
