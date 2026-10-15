"""Makes phucpham1076's "Lange - Half-Elf Knight" into the knight the game draws.

    ./gradlew exportLange -PlangeSource=".../lange-half-elf-knight" \
        -PualSource=".../UAL1_Standard.glb" -PkaykitAnimations=".../Animations/gltf/Rig_Medium"
    blender -b --factory-startup -P art/lange/export_lange.py -- <posing.fbx> <idle.fbx> \
        <textures> <UAL1_Standard.glb> <KayKit Rig_Medium folder> <out folder>

The download (Sketchfab, CC BY 4.0 -- see CREDITS.md) is three FBX files in
source/KnightElf.zip -- him in a pose, him idling, him walking, all on one
91-bone rig -- and a folder of 4K PBR pictures in three sets (clothes_leather,
hair_skin_steel, weapons). The game wants one glTF with his clips inside, so
this opens the posing file, fixes him up, borrows the two clips from the other
two files and the rest from two CC0 animation libraries, and writes
<out>/lange.glb, with the pictures it used, downsized, in <out>/textures. What
ships is that .glb, which is committed; the sources are not in the repository
and Blender is not a build dependency.

On the way:

THE GLASSES GO, AND HE HAS EYES. The owner's word: he wears none. They were
sunglasses over a face with no eyes modelled under them, so the glasses are
deleted and eyes are painted into his skin picture where the lenses were --
see eyes.py. The earring, which is in the same mesh, stays.

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

THE CLIPS ARE THE GAME'S NAMES. Idle is his own, from its file (his own walk
is not used: it plants his feet at a quarter of his speed). Walk (a jog),
Attack, Hurt, Death and his three casts (Cast_Wave, Cast_Charge, Cast_Pull) are
borrowed from Quaternius' Universal Animation Library and KayKit's Character
Animations (both CC0) and put onto his rig by retarget.py -- see BORROWED for
which is which, and why.

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

import bpy
import mathutils

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eyes  # noqa: E402
import retarget  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
if len(args) != 6:
    raise SystemExit("usage: blender -b -P export_lange.py -- <posing.fbx> <idle.fbx> <textures> "
                     "<UAL1_Standard.glb> <KayKit Rig_Medium folder> <out>")
POSING, IDLE, TEXTURES, UAL_FILE, KAYKIT_DIR, OUT = args

# Pixels a side each set's pictures are written at.
SIZE = {"clothes_leather": 1024, "hair_skin_steel": 1024, "weapons": 1024}
FPS = 24
# What he borrows: (his clip, which library, its file, its clip, mirrored, how much faster, seconds cut off its
# start). Mirrored is a right-handed swing made left-handed, for his sword hand. See retarget.py.
BORROWED = [
    # His run. He moves 21 a second and stands 12 tall, so 3.1 m/s for a man 1.76 m high. His own download's walk
    # plants its feet at 0.8 m/s and skated four times over; Quaternius' walk plants them at 1 m/s and his jog at
    # 5, so this is the two half and half, both stretched to a second (24 frames, both start on the left foot):
    # a long-striding run between them. The game paces it to his speed (WalkStride in units/knight.duke, measured
    # by HeroClipFitTest), so whatever it measures his feet stay where they land.
    ("Walk", "ual", None, ("Walk_Loop", "Jog_Fwd_Loop", 0.5, 24), False, 1.0, 0.0),
    # A lunging one-handed slash, made left-handed. The client starts it on the frame the blow LANDS, so it
    # is cut in at the top of its wind-up and the blade's fastest frame -- the impact -- comes a tenth of a
    # second after the blow instead of three tenths. Faster too, so it is over before his next blow
    # (ReloadFrames 34 = 1.133s, DungeonMonsterArtTest); the impact is checked by HeroClipFitTest.
    ("Attack", "ual", None, "Sword_Attack", True, 1.3, 0.24),
    ("Hurt", "ual", None, "Hit_Chest", False, 1.0, 0.0),
    # Onto his back, twice as fast: 1.208s.
    ("Death", "ual", None, "Death01", False, 2.0, 0.0),
    # Q, the wave: the same slash at its own pace, the stronger for being slower.
    ("Cast_Wave", "ual", None, "Sword_Attack", True, 1.0, 0.0),
    # W, the charge: leaning in and driving, which is what the charge does to what it meets. KayKit's
    # Melee_Blocking was tried first: its chibi arms block with the upper arm straight out, which on a
    # man's proportions is a scarecrow, not a guard.
    ("Cast_Charge", "ual", None, "Push_Loop", False, 1.0, 0.0),
    # R, the pull: one hand raised to call the room in.
    ("Cast_Pull", "kaykit", "Rig_Medium_CombatRanged.glb", "Ranged_Magic_Raise", False, 1.0, 0.0),
    # Work, for the food state: the engine loops one by its exact name while he works (E67, GameObject.busy). Chop
    # is his slash at its own pace, Gather Quaternius' Interact, Cook Quaternius' Fixing_Kneeling: the Huntress has the same
    # three names.
    ("Chop", "ual", None, "Sword_Attack", True, 1.0, 0.0),
    ("Gather", "ual", None, "Interact", False, 1.0, 0.0),
    ("Cook", "ual", None, "Fixing_Kneeling", False, 1.0, 0.0),
]
SWORD_BONE = "sword"
HAND = "L.forearm"  # the rig has no hand bone: the fingers start at the forearm's end


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

EYES = eyes.take_off_glasses(next(o for o in objs if o.name.startswith("glasses")),
                             next(o for o in objs if o.name == "body"), bpy.context.evaluated_depsgraph_get())

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
clips = [idle]
# His fingers as his own idle closes them on the sword (and lets the other hand hang), for every borrowed clip.
ad = arm.animation_data
ad.action = idle
ad.action_slot = idle.slots[0]
scene.frame_set(2)
fingers = {pb.name: pb.matrix_basis.copy() for pb in arm.pose.bones
           if pb.name.split(".", 1)[-1].startswith(retarget.FINGERS)}
ad.action = None
for pb in arm.pose.bones:
    pb.matrix_basis = mathutils.Matrix.Identity(4)
bpy.context.view_layer.update()
target = retarget.Target(arm, fingers)


def source_clip(by_name, clip):
    """A clip of the library by name, or (first, second, weight, frames): a blend of two -- see retarget.Blend."""
    if isinstance(clip, tuple):
        first, second, weight, frames = clip
        return retarget.Blend(by_name[first], by_name[second], weight, frames)
    return by_name[clip]


def import_gltf(path):
    before = {o.name for o in bpy.data.objects}
    actions_before = set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=path)
    objs = new_objects(before)
    return objs, next(o for o in objs if o.type == "ARMATURE"), set(bpy.data.actions) - actions_before


TABLES = {"kaykit": retarget.KAYKIT, "ual": retarget.UAL}
# Clips whose sword is not steered by their hand but held as he holds it at rest, low and trailing: on the run,
# Quaternius' fist stands a blade straight up, which from above is a pin, not a sword.
SWORD_AT_REST = {"Walk"}


def table_for(lib, clip):
    table = TABLES[lib]
    return {k: v for k, v in table.items() if k != "sword"} if clip in SWORD_AT_REST else table


for path in sorted({(lib, file) for _, lib, file, _, _, _, _ in BORROWED}, key=str):
    lib, file = path
    source_objs, source_arm, source_actions = import_gltf(UAL_FILE if lib == "ual" else os.path.join(KAYKIT_DIR, file))
    by_name = {a.name: a for a in source_actions}
    for name, l, f, clip, mirror, speed, start in BORROWED:
        if (l, f) == path:
            clips.append(retarget.retarget(scene, target, source_arm, source_clip(by_name, clip), table_for(lib, name),
                                           mirror, name, FPS,
                                           speed, start))
    remove(source_objs)
    for action in source_actions:
        bpy.data.actions.remove(action)

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
    if (set_name, kind) == ("hair_skin_steel", "BaseColor"):
        eyes.paint(image, EYES)
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

for track in arm.animation_data.nla_tracks:  # measured at rest, not in whatever pose the clips left
    track.mute = True
for pb in arm.pose.bones:
    pb.matrix_basis = mathutils.Matrix.Identity(4)
bpy.context.view_layer.update()
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
for track in arm.animation_data.nla_tracks:
    track.mute = False

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
