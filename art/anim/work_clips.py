"""Writes animations/characters/work.glb: the KayKit heroes' work clips, under the game's names.

    blender -b --factory-startup -P art/anim/work_clips.py -- \
        "<KayKit_Character_Animations_1.1>/Animations/gltf/Rig_Medium" src/main/resources/animations/characters/work.glb

The food state loops a hero's Chop, Gather or Cook by its exact name while he works (E67, data/world/food.duke).
The knight and the Huntress carry their own (art/lange, art/huntress); a hero on KayKit's own rig -- the mage --
takes them from this file, which his Humanoid set lists. KayKit's Character Animations (CC0) are the source:

  Chop    Tools' Chopping: both hands on the haft, up over the shoulder and down into a trunk in front, looped;
          sped from 1.333s to 1.2s. (KayKit's own clip named Chop is a 4.3s one-off with a wind-in, which would
          loop badly -- hence a file of our own rather than the Tools file itself.) On a chibi's short arms it
          reads; on a grown body it does not, which is why the knight and the Huntress have a posed one.
  Gather  General's Interact: a reach forward and back.
  Cook    Tools' Working_C: both hands working at chest height.

Bones and clips only, as magic.glb is: the meshes are dropped.
"""

import sys

import bpy

# (the game's name, KayKit file, KayKit clip, how much faster)
WORK = [
    ("Chop", "Rig_Medium_Tools.glb", "Chopping", 1.333 / 1.2),
    ("Gather", "Rig_Medium_General.glb", "Interact", 1.0),
    ("Cook", "Rig_Medium_Tools.glb", "Working_C", 1.0),
    # Carried across a traversal link (E81): MovementBasic's airborne loop, under the knight's and Huntress' name.
    ("Jump", "Rig_Medium_MovementBasic.glb", "Jump_Idle", 1.0),
]

args = sys.argv[sys.argv.index("--") + 1:]
FOLDER, OUT = args
bpy.ops.wm.read_factory_settings(use_empty=True)

rig = None
taken = {}
for file in dict.fromkeys(f for _, f, _, _ in WORK):
    before_objects, before_actions = set(bpy.data.objects), set(bpy.data.actions)
    bpy.ops.import_scene.gltf(filepath=f"{FOLDER}/{file}")
    added = [o for o in bpy.data.objects if o not in before_objects]
    for action in set(bpy.data.actions) - before_actions:
        taken[(file, action.name.split(".")[0])] = action
    for obj in added:
        if obj.type == "ARMATURE" and rig is None:
            rig = obj
        elif obj.type == "MESH" or obj.type == "ARMATURE":
            bpy.data.objects.remove(obj, do_unlink=True)

ad = rig.animation_data or rig.animation_data_create()
ad.action = None
for track in list(ad.nla_tracks):  # the importer lays every clip of the file on its own track: only ours go out
    ad.nla_tracks.remove(track)
for name, file, clip, faster in WORK:
    action = taken.get((file, clip))
    if action is None:
        raise SystemExit(f"{file} holds no {clip}")
    # And the rig itself keyed still, start and end: jME hangs a clip that moves only joints on the mesh skinned
    # to them, and there is none here, so with nothing else moving the file loads with no clips at all
    # (magic.glb works for the same reason: its source keyed the rig).
    ad.action = action
    if hasattr(ad, "action_slot") and action.slots:
        ad.action_slot = action.slots[0]
    for frame in action.frame_range:
        rig.keyframe_insert("location", frame=frame)
    ad.action = None
    track = ad.nla_tracks.new()
    track.name = name
    strip = track.strips.new(name, int(action.frame_range[0]), action)
    if hasattr(strip, "action_slot") and action.slots:
        strip.action_slot = action.slots[0]
    strip.scale = 1.0 / faster

for obj in list(bpy.data.objects):
    if obj.type == "MESH":
        bpy.data.objects.remove(obj, do_unlink=True)
for obj in bpy.data.objects:
    obj.select_set(obj == rig or obj.parent == rig)
bpy.context.view_layer.objects.active = rig
bpy.ops.export_scene.gltf(filepath=OUT, export_format="GLB", use_selection=True, export_skins=True,
                          export_animations=True, export_animation_mode="NLA_TRACKS", export_materials="NONE",
                          export_optimize_animation_keep_anim_object=True, export_optimize_animation_size=False)
print("WROTE", OUT, [w[0] for w in WORK])
