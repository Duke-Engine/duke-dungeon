"""Puts the Ritual clip (ritual.py) into an exported ritualist.glb, without the downloads export_licensed.py needs.

    blender -b --factory-startup -P art/licensed/add_ritual.py -- <ritualist.glb in> <ritualist.glb out>

The licensed file is written into licensed-assets/ (git-ignored), never the repository. Its other clips, its mesh
and its picture are kept as they were read.
"""

import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ritual  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, OUT = args
FPS = 24

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.fps = FPS
bpy.ops.import_scene.gltf(filepath=SOURCE)
arm = next(o for o in bpy.data.objects if o.type == "ARMATURE")
old = bpy.data.actions.get("Ritual")
if old is not None:
    bpy.data.actions.remove(old)
idle = next(a for a in bpy.data.actions if a.name == "Idle" or a.name.startswith("Idle"))
made = ritual.add(arm, scene, FPS, idle)
for action in bpy.data.actions:
    action.use_fake_user = True
print("RITUAL posed:", tuple(made.frame_range), "clips", [a.name for a in bpy.data.actions])
bpy.ops.object.select_all(action="SELECT")
bpy.ops.export_scene.gltf(
    filepath=OUT,
    export_format="GLB",
    export_image_format="JPEG",
    export_jpeg_quality=88,
    export_animations=True,
    export_animation_mode="ACTIONS",
    export_anim_slide_to_zero=True,
    export_force_sampling=True,
    export_skins=True,
    export_apply=False,
    export_yup=True,
)
print(f"EXPORTED {OUT} {os.path.getsize(OUT) / 1e6:.2f} MB")
