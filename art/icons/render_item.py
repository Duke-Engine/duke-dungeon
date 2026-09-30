"""The picture a bag draws a thing with, rendered from the thing's own model -- and the game's copy of that model,
set on its own origin and lying on it.

    "C:/Program Files/Blender Foundation/Blender 5.2/blender.exe" -b --factory-startup \
        -P art/icons/render_item.py -- art/models/key_crown.glb \
        src/main/resources/models/props/key/key_crown.glb src/main/resources/icons/items/key_crown.png

Run by hand when an item's model arrives, as art/anim/fbx_to_glb.py is, and the build does not depend on it: what
ships is what it wrote, which is committed. Kept here rather than done in Blender's window for the reason the icon
cutter is: a picture nobody can make again is a picture nobody dares change.

THE MODEL IS SET ON ITS ORIGIN. The owner's key was exported where it lay in his scene, two units off the origin; the
game draws a thing at its origin, so as it came the key would lie two cells from where it fell. Its middle is moved
over the origin and its underside onto it, and nothing else in it changes.

THE PICTURE IS THE SIZE OF THE OTHERS. 64 by 64, as every picture in icons/stats is, and clear round the thing, so a
bag's slot shows its own stone there. Looked at from straight above and turned an eighth, so a long thing runs corner
to corner; lit by a sun and by a pale sky it shines in; rendered by Cycles on the processor, so a machine with no
graphics card makes the same picture.
"""

import math
import os
import sys

import bpy
from mathutils import Vector

SIZE = 64


def bounds(meshes):
    """The box round every mesh, in the scene's own units: its low corner and its high one."""
    corners = [thing.matrix_world @ Vector(corner) for thing in meshes for corner in thing.bound_box]
    low = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    high = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    return low, high


def render(source, model, picture):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=source)
    scene = bpy.context.scene
    meshes = [thing for thing in scene.objects if thing.type == "MESH"]
    if not meshes:
        raise SystemExit(f"{source}: no mesh in it to draw")

    # On its own origin, lying on it.
    low, high = bounds(meshes)
    shift = Vector(((low.x + high.x) / 2, (low.y + high.y) / 2, low.z))
    for thing in scene.objects:
        if thing.parent is None:
            thing.location -= shift
    bpy.context.view_layer.update()
    bpy.ops.export_scene.gltf(filepath=model, export_format="GLB")

    # From straight above, turned an eighth: a rectangle across by down fills (across + down) / sqrt 2 each way.
    low, high = bounds(meshes)
    across = high.x - low.x
    down = high.y - low.y
    camera = bpy.data.objects.new("Camera", bpy.data.cameras.new("Camera"))
    camera.data.type = "ORTHO"
    camera.data.ortho_scale = (across + down) / math.sqrt(2) * 1.1
    camera.location = (0, 0, high.z + 10)
    camera.rotation_euler = (0, 0, math.radians(45))
    scene.collection.objects.link(camera)
    scene.camera = camera

    sun = bpy.data.objects.new("Sun", bpy.data.lights.new("Sun", "SUN"))
    sun.data.energy = 4
    sun.rotation_euler = (math.radians(40), 0, math.radians(30))
    scene.collection.objects.link(sun)
    # The sky is what silver shows: a world's colour is its Background node's, not World.color.
    sky = bpy.data.worlds.new("Sky")
    background = sky.node_tree.nodes["Background"]
    background.inputs["Color"].default_value = (0.8, 0.8, 0.85, 1.0)
    background.inputs["Strength"].default_value = 1.0
    scene.world = sky

    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 64
    scene.render.resolution_x = SIZE
    scene.render.resolution_y = SIZE
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.view_settings.view_transform = "Standard"
    scene.render.filepath = picture
    bpy.ops.render.render(write_still=True)
    print(f"RENDERED {picture}, and {model} set on its origin, from {source}")


argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
if len(argv) != 3:
    raise SystemExit("usage: blender -b --factory-startup -P render_item.py -- model.glb out.glb out.png")
# Whole paths: Blender reads a relative render path against the .blend it has not got, and wrote to C:\out.
render(*(os.path.abspath(path) for path in argv))
