"""Makes phucpham1076's "Lange - Half-Elf Knight" into the knight the game draws.

    ./gradlew exportLange -PlangeSource="C:/Users/me/Downloads/lange-half-elf-knight"
    blender -b --factory-startup -P art/lange/export_lange.py -- <posing.fbx> <idle.fbx> <walk.fbx> <textures> <out folder>

The download (Sketchfab, CC BY 4.0 -- see CREDITS.md) is three FBX files in
source/KnightElf.zip -- him in a pose, him idling, him walking, all on one
91-bone rig -- and a folder of 4K PBR pictures in three sets (clothes_leather,
hair_skin_steel, weapons). The game wants one glTF with his clips inside, so
this opens the posing file, fixes him up, borrows the two clips from the other
two files and writes <out>/lange.glb, with the pictures it used, downsized,
in <out>/textures. What ships is that .glb, which is committed; the source is
not in the repository and Blender is not a build dependency.

On the way:

THE GLASSES GO. The owner's word: he wears none. They were sunglasses over a
face with no eyes modelled under them, so the frame is deleted and the two dark
lenses are shrunk into his eye sockets (EYE) to be his eyes. The earring, which
is in the same mesh, stays.

THE SWORD IS HIS. It arrives as a loose object with an animation of its own in
each file, following his LEFT hand (he is left-handed: the right hand hangs at
his side in every clip and the left holds the blade up). It becomes a skinned
part of him on a bone of its own, `sword`, a child of L.forearm, and its
animation in each file is baked onto that bone frame by frame -- so the walk,
which carries the blade differently from the idle, keeps its own grip.

EVERYTHING HANGS OFF THE RIG BY WEIGHTS. The shield (on his back, on spine3),
the book and its holder (on his right thigh) were parented to bones; they are
skinned to those bones instead, at full weight, which every glTF reader agrees
on. Every mesh's own transform is baked into its vertices.

THE PICTURES ARE WIRED. BaseColor, Normal, Metallic, Roughness and AO (and the
weapons' Emissive) on a Principled BSDF; the exporter packs metallic and
roughness into glTF's one texture and writes AO as occlusion. Downsized to
SIZE first. The game itself draws the base colour only (the client re-lights
every model with plain lighting, see DukeRtsApp.dressModel); the rest is there
for whatever else reads the file.

THE CLIPS ARE THE GAME'S NAMES. Idle and Walk from their files. The download has
nothing else, so Attack, Hurt and Death are STAND-INS: slices of the idle, short
enough that his swing fits between blows (DungeonMonsterArtTest). Replace them
here, under the same names, when real ones arrive, and no data file changes.

METRES, FEET AT THE ORIGIN, FACING +Z. The rig stands at the origin with his
feet on it and faces Blender's -Y, which the exporter's Z-up to Y-up turn makes
glTF's +Z -- as the KayKit people face, so his block says Facing = 90 as theirs
do. His size in the game is his block's ModelScale (12 units over the height
printed here), not a scale baked in: scaling an armature does not scale the
location keys of its clips.
"""

import json
import os
import struct
import sys

import bmesh
import bpy
import mathutils

args = sys.argv[sys.argv.index("--") + 1:]
if len(args) != 5:
    raise SystemExit("usage: blender -b -P export_lange.py -- <posing.fbx> <idle.fbx> <walk.fbx> <textures> <out>")
POSING, IDLE, WALK, TEXTURES, OUT = args

# Pixels a side each set's pictures are written at.
SIZE = {"clothes_leather": 1024, "hair_skin_steel": 1024, "weapons": 1024}
FPS = 24
# Stand-ins, as (name, first frame, last frame) of the idle: one second, half a second, one second.
STAND_INS = [("Attack", 2, 26), ("Hurt", 2, 14), ("Death", 2, 26)]
SWORD_BONE = "sword"
HAND = "L.forearm"  # the rig has no hand bone: the fingers start at the forearm's end
# What a sunglasses lens is shrunk to, across and up, to be an eye.
EYE = (0.42, 0.34)


def new_objects(before):
    return [o for o in bpy.data.objects if o.name not in before]


def import_fbx(path):
    before = {o.name for o in bpy.data.objects}
    bpy.ops.import_scene.fbx(filepath=path)
    objs = new_objects(before)
    arm = next(o for o in objs if o.type == "ARMATURE")
    sword = next(o for o in objs if o.name.startswith("sword low"))
    return objs, arm, sword


def remove(objs):
    for o in objs:
        data = o.data
        bpy.data.objects.remove(o, do_unlink=True)
        if isinstance(data, bpy.types.Mesh) and data.users == 0:
            bpy.data.meshes.remove(data)


def skin_to(obj, arm, bone):
    """A mesh moved by one bone at full weight, its vertices in the rig's rest space."""
    group = obj.vertex_groups.new(name=bone)
    group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
    obj.parent = arm
    obj.parent_type = "OBJECT"
    obj.matrix_parent_inverse = mathutils.Matrix.Identity(4)
    obj.matrix_world = mathutils.Matrix.Identity(4)
    mod = obj.modifiers.new("Armature", "ARMATURE")
    mod.object = arm


# ---- him, from the posing file ----

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS
objs, arm, sword = import_fbx(POSING)
arm.name = "Armature"
scene.frame_set(2)
bpy.context.view_layer.update()

def eyes_instead_of_glasses(glasses, body):
    """The sunglasses' frame goes; their two lenses, shrunk, become his eyes; the earring stays."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    face = body.evaluated_get(depsgraph)
    to_face = face.matrix_world.inverted()
    bm = bmesh.new()
    bm.from_mesh(glasses.data)
    bm.transform(glasses.matrix_world)  # world space, as the pose shows it
    seen, parts = set(), []
    for start in bm.faces:
        if start.index in seen:
            continue
        part, stack = [], [start]
        seen.add(start.index)
        while stack:
            f = stack.pop()
            part.append(f)
            for e in f.edges:
                for g in e.link_faces:
                    if g.index not in seen:
                        seen.add(g.index)
                        stack.append(g)
        parts.append(part)
    steel = [p for p in parts if glasses.data.materials[p[0].material_index].name.startswith("hair skin steel")]
    frame = max(steel, key=len)
    lenses = [p for p in steel if p is not frame]
    for lens in lenses:
        verts = {v for f in lens for v in f.verts}
        centre = sum((v.co for v in verts), mathutils.Vector()) / len(verts)
        # Where his face is behind the lens, found by looking at it from the front (-Y).
        hit, at, _, _ = face.ray_cast(to_face @ (centre + mathutils.Vector((0, -0.3, 0))),
                                      to_face.to_3x3() @ mathutils.Vector((0, 1, 0)))
        surface = face.matrix_world @ at if hit else centre
        for v in verts:
            d = v.co - centre
            v.co = mathutils.Vector((centre.x + d.x * EYE[0], surface.y - 0.002 + d.y * 0.2,
                                     centre.z + d.z * EYE[1]))
    bmesh.ops.delete(bm, geom=frame, context="FACES")
    bm.transform(glasses.matrix_world.inverted())
    bm.to_mesh(glasses.data)
    bm.free()
    glasses.name = "eyes and earring"
    print(f"EYES {len(lenses)} lenses made eyes, the frame's {len(frame)} faces gone")


eyes_instead_of_glasses(next(o for o in objs if o.name.startswith("glasses")),
                        next(o for o in objs if o.name == "body"))

world_of = lambda pb: arm.matrix_world @ pb.matrix

# Bone-parented pieces: their vertices where the pose shows them, carried back to the rest of their bone.
for o in [o for o in objs if o.type == "MESH" and o.parent_type == "BONE"]:
    bone = o.parent_bone
    to_rest = arm.data.bones[bone].matrix_local @ world_of(arm.pose.bones[bone]).inverted() @ o.matrix_world
    o.parent = None
    o.data.transform(to_rest)
    skin_to(o, arm, bone)

# The sword's bone: its rest is where the sword sits on the resting forearm, in the posing file's grip.
grip = world_of(arm.pose.bones[HAND]).inverted() @ sword.matrix_world
rest = arm.data.bones[HAND].matrix_local @ grip
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode="EDIT")
eb = arm.data.edit_bones.new(SWORD_BONE)
eb.head = (0, 0, 0)
eb.tail = (0, 0.3, 0)
eb.matrix = rest
eb.length = 0.3
eb.parent = arm.data.edit_bones[HAND]
eb.use_deform = True
# The IK targets and pole bones drive nothing once out of Blender: FBX carries no constraints, so
# the legs' and arms' movement is already on their own bones. Left in, they are parentless joints
# that turn in the clips -- which the game reads as turning him (DungeonMonsterArtTest).
for control in [b for b in arm.data.edit_bones if b.name.startswith(("IK_", "PV_"))]:
    arm.data.edit_bones.remove(control)
bpy.ops.object.mode_set(mode="OBJECT")
rest = arm.data.bones[SWORD_BONE].matrix_local  # as Blender rounded it
sword.animation_data_clear()
sword.data.transform(rest)
skin_to(sword, arm, SWORD_BONE)
sword.name = "sword"

# Skinned pieces: their own transform baked in, so each is in the rig's space as it stands.
for o in [o for o in arm.children if o.type == "MESH" and o is not sword]:
    if o.matrix_world != mathutils.Matrix.Identity(4):
        o.data.transform(arm.matrix_world.inverted() @ o.matrix_world)
        o.matrix_parent_inverse = mathutils.Matrix.Identity(4)
        o.matrix_basis = mathutils.Matrix.Identity(4)

# The posing file's own action is a held pose, not a clip: dropped, and the rig put back to rest.
arm.animation_data_clear()
for pb in arm.pose.bones:
    pb.matrix_basis = mathutils.Matrix.Identity(4)
for action in list(bpy.data.actions):
    bpy.data.actions.remove(action)
bpy.context.view_layer.update()


# ---- the clips ----

def borrow(path, name):
    """The other file's clip on this rig, with the sword's own movement baked onto its bone."""
    others, other_arm, other_sword = import_fbx(path)
    action = other_arm.animation_data.action
    first, last = (int(f) for f in action.frame_range)
    action.name = name
    ad = arm.animation_data or arm.animation_data_create()
    ad.action = action
    if action.slots:
        ad.action_slot = action.slots[0]
    pb = arm.pose.bones[SWORD_BONE]
    pb.rotation_mode = "QUATERNION"
    for frame in range(first, last + 1):
        scene.frame_set(frame)
        pb.matrix = arm.matrix_world.inverted() @ other_sword.matrix_world
        bpy.context.view_layer.update()
        pb.keyframe_insert("location", frame=frame, group=SWORD_BONE)
        pb.keyframe_insert("rotation_quaternion", frame=frame, group=SWORD_BONE)
    for stray in [a for a in bpy.data.actions if a is not action and a.name.startswith("sword low")]:
        bpy.data.actions.remove(stray)
    remove(others)
    action.use_frame_range = True
    action.frame_start, action.frame_end = first, last
    action.use_fake_user = True
    print(f"CLIP {name} frames {first}-{last} = {(last - first) / FPS:.3f}s")
    return action


idle = borrow(IDLE, "Idle")
walk = borrow(WALK, "Walk")
clips = [idle, walk]
for name, first, last in STAND_INS:
    slice_ = idle.copy()
    slice_.name = name
    slice_.frame_start, slice_.frame_end = first, last
    slice_.use_fake_user = True
    clips.append(slice_)
    print(f"CLIP {name} (stand-in: Idle {first}-{last}) = {(last - first) / FPS:.3f}s")

ad = arm.animation_data
ad.action = None
for action in clips:
    track = ad.nla_tracks.new()
    track.name = action.name
    strip = track.strips.new(action.name, int(action.frame_start), action)
for pb in arm.pose.bones:
    pb.matrix_basis = mathutils.Matrix.Identity(4)
scene.frame_set(1)


# ---- the pictures ----

os.makedirs(os.path.join(OUT, "textures"), exist_ok=True)


def picture(set_name, kind, colour=False):
    source = os.path.join(TEXTURES, f"elf_low_{set_name}_{kind}.png")
    if not os.path.exists(source):
        return None
    image = bpy.data.images.load(source)
    size = SIZE[set_name]
    if image.size[0] != size:
        image.scale(size, size)
    image.filepath_raw = os.path.join(OUT, "textures", f"lange_{set_name}_{kind}.png")
    image.file_format = "PNG"
    image.save()
    image.colorspace_settings.name = "sRGB" if colour else "Non-Color"
    return image


def gltf_output_group():
    group = bpy.data.node_groups.get("glTF Material Output")
    if group is None:
        group = bpy.data.node_groups.new("glTF Material Output", "ShaderNodeTree")
        group.interface.new_socket("Occlusion", in_out="INPUT", socket_type="NodeSocketFloat")
        group.interface.new_socket("Thickness", in_out="INPUT", socket_type="NodeSocketFloat")
    return group


def wire(material, set_name):
    material.use_nodes = True
    nt = material.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])

    def tex(kind, colour=False):
        image = picture(set_name, kind, colour)
        if image is None:
            return None
        node = nt.nodes.new("ShaderNodeTexImage")
        node.image = image
        return node

    nt.links.new(tex("BaseColor", True).outputs["Color"], bsdf.inputs["Base Color"])
    normal = nt.nodes.new("ShaderNodeNormalMap")
    nt.links.new(tex("Normal").outputs["Color"], normal.inputs["Color"])
    nt.links.new(normal.outputs["Normal"], bsdf.inputs["Normal"])
    for kind, socket in (("Metallic", bsdf.inputs["Metallic"]), ("Roughness", bsdf.inputs["Roughness"])):
        split = nt.nodes.new("ShaderNodeSeparateColor")
        nt.links.new(tex(kind).outputs["Color"], split.inputs["Color"])
        nt.links.new(split.outputs["Red"], socket)
    ao = nt.nodes.new("ShaderNodeGroup")
    ao.node_tree = gltf_output_group()
    split = nt.nodes.new("ShaderNodeSeparateColor")
    nt.links.new(tex("ao").outputs["Color"], split.inputs["Color"])
    nt.links.new(split.outputs["Red"], ao.inputs["Occlusion"])
    emissive = tex("Emissive", True)
    if emissive is not None:
        nt.links.new(emissive.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = 1.0


for material in {m for o in arm.children for m in o.data.materials if m}:
    wire(material, material.name.split(".")[0].replace(" ", "_"))


# ---- his size, and the file ----

low, high = [1e9] * 3, [-1e9] * 3
tris = 0
for o in arm.children:
    tris += sum(len(p.vertices) - 2 for p in o.data.polygons)
    if o is sword:
        continue
    for corner in o.bound_box:
        point = o.matrix_world @ mathutils.Vector(corner)
        low = [min(a, b) for a, b in zip(low, point)]
        high = [max(a, b) for a, b in zip(high, point)]
print(f"SIZE rest box {[round(v, 3) for v in low]} .. {[round(v, 3) for v in high]}; "
      f"height {high[2]:.3f} m, {tris} triangles, {len(arm.data.bones)} bones")

target = os.path.join(OUT, "lange.glb")
bpy.ops.export_scene.gltf(
    filepath=target,
    export_format="GLB",
    export_image_format="JPEG",
    export_jpeg_quality=90,
    export_animations=True,
    export_animation_mode="ACTIONS",
    export_anim_slide_to_zero=True,
    export_force_sampling=True,
    export_skins=True,
    export_apply=False,
    export_yup=True,
)

# What was written, read back: its clips and how long each runs.
with open(target, "rb") as f:
    data = f.read()
length = struct.unpack_from("<I", data, 12)[0]
gltf = json.loads(data[20:20 + length])
for anim in gltf.get("animations", []):
    times = max(gltf["accessors"][s["input"]]["max"][0] for s in anim["samplers"])
    print(f"EXPORTED clip {anim['name']} {times:.3f}s")
print(f"EXPORTED {target} {os.path.getsize(target) / 1e6:.2f} MB, "
      f"{len(gltf['skins'][0]['joints'])} joints, {len(gltf.get('images', []))} images")
