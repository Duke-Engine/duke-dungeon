"""The knight's Q: a flat half-moon of white steel, the body the SwordWave projectile flies as.

Own-made (CC0). Built and exported headless with Blender, then a preview drawn from above:

    blender -b -P art/effects/knight_wave.py -- <out dir>

Writes <out dir>/knight_wave.glb (copy into src/main/resources/models/effects/) and knight_wave_top.png.

The shape: 14 tip to tip -- the skill's HitWidth, the lane the game draws -- and bulging 4.5 the way it
flies, which is +X (the client's forward; Facing = 0 in sword_wave.duke). It lies flat in the ground plane.
Its picture is light on black, brightest at the leading edge and gone at the tips and the inner edge, and the
material is marked unlit (KHR_materials_unlit) and ADDITIVE (extras.blend, read by the client's ModelBlends),
so it glows over the floor rather than being lit by it.
"""
import json
import math
import os
import struct
import sys

import bpy
import numpy as np

OUT = sys.argv[sys.argv.index('--') + 1] if '--' in sys.argv else os.getcwd()
os.makedirs(OUT, exist_ok=True)
GLB = os.path.join(OUT, 'knight_wave.glb')

HALF_SPAN, BULGE, INNER = 7.0, 4.5, 0.4  # inner edge bulges INNER of the outer: the moon's hollow
SEGMENTS = 32

bpy.ops.wm.read_factory_settings(use_empty=True)

# ---- the mesh: a strip between the outer and the inner edge, tips meeting ----
verts, faces, uvs = [], [], []
for k in range(SEGMENTS + 1):
    t = -1.0 + 2.0 * k / SEGMENTS
    bow = 1.0 - t * t
    y = HALF_SPAN * t
    outer = BULGE * bow - BULGE / 2
    inner = BULGE * INNER * bow - BULGE / 2
    verts += [(inner, y, 0.0), (outer, y, 0.0)]
for k in range(SEGMENTS):
    i0, o0, i1, o1 = 2 * k, 2 * k + 1, 2 * k + 2, 2 * k + 3
    faces.append((i0, o0, o1, i1))  # counter-clockwise from above: its face is up
mesh = bpy.data.meshes.new('knight_wave')
mesh.from_pydata(verts, [], faces)
uv = mesh.uv_layers.new(name='UVMap')
for poly in mesh.polygons:
    for loop in poly.loop_indices:
        v = mesh.loops[loop].vertex_index
        k, outer = divmod(v, 2)
        uv.data[loop].uv = (float(outer), k / SEGMENTS)  # u across (inner 0 -> outer 1), v along
mesh.update()
obj = bpy.data.objects.new('knight_wave', mesh)
bpy.context.scene.collection.objects.link(obj)

# ---- the picture: across 64, along 256 ----
W, H = 64, 256
u = (np.arange(W) + 0.5) / W
v = (np.arange(H) + 0.5) / H
U, V = np.meshgrid(u, v)
along = np.abs(V * 2 - 1)
tips = np.clip(1 - along ** 2.5, 0, 1)                          # gone at the tips
body = np.clip(U, 0, 1) ** 1.2                                   # faint at the hollow, strong forward
edge = np.exp(-((U - 0.86) / 0.09) ** 2)                         # the white-hot leading edge
front = 1 - np.clip((U - 0.93) / 0.07, 0, 1)                     # soft at the very rim
glow = np.clip((0.55 * body + 0.9 * edge) * front * tips, 0, 1)
core = np.clip(edge * front * tips, 0, 1)
rgb = np.stack([glow * 0.80 + core * 0.20, glow * 0.88 + core * 0.12, glow], axis=-1)
rgba = np.concatenate([np.clip(rgb, 0, 1), glow[..., None]], axis=-1).astype(np.float32)
image = bpy.data.images.new('knight_wave', W, H, alpha=True)
image.pixels.foreach_set(rgba.ravel())
image.file_format = 'PNG'
image.pack()

material = bpy.data.materials.new('knight_wave')
material.use_nodes = True
nodes = material.node_tree.nodes
bsdf = nodes.get('Principled BSDF')
tex = nodes.new('ShaderNodeTexImage')
tex.image = image
material.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
material.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Emission Color'])
bsdf.inputs['Emission Strength'].default_value = 1.0
mesh.materials.append(material)

bpy.ops.export_scene.gltf(filepath=GLB, export_format='GLB', use_selection=False, export_yup=True)


# ---- unlit and additive: what glTF's exporter has no switch for, written into the file's JSON ----
def patch(path):
    with open(path, 'rb') as f:
        data = f.read()
    json_len = struct.unpack_from('<I', data, 12)[0]
    doc = json.loads(data[20:20 + json_len])
    rest = data[20 + json_len:]
    for mat in doc['materials']:
        mat.setdefault('extensions', {})['KHR_materials_unlit'] = {}
        mat['extras'] = {'blend': 'ADDITIVE'}
        mat.pop('emissiveFactor', None)
        mat.pop('emissiveTexture', None)
        mat['extensions'].pop('KHR_materials_emissive_strength', None)
        mat['doubleSided'] = True
    used = set(doc.get('extensionsUsed', [])) - {'KHR_materials_emissive_strength'}
    doc['extensionsUsed'] = sorted(used | {'KHR_materials_unlit'})
    body = json.dumps(doc, separators=(',', ':')).encode()
    body += b' ' * (-len(body) % 4)
    chunk = struct.pack('<II', len(body), 0x4E4F534A) + body
    with open(path, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, 12 + len(chunk) + len(rest)) + chunk + rest)


patch(GLB)

# ---- a look from above, on a dark floor: what the player's camera sees ----
scene = bpy.context.scene
scene.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in
        bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'CYCLES'
scene.render.resolution_x, scene.render.resolution_y = 640, 480
scene.render.film_transparent = False
world = bpy.data.worlds.new('dark')
world.use_nodes = True
world.node_tree.nodes['Background'].inputs['Color'].default_value = (0.03, 0.03, 0.035, 1)
scene.world = world
# Shown as it glows: emission straight from the picture, no light needed.
material.node_tree.nodes.remove(bsdf)
emit = nodes.new('ShaderNodeEmission')
emit.inputs['Strength'].default_value = 2.0
material.node_tree.links.new(tex.outputs['Color'], emit.inputs['Color'])
# Added to the floor, as the client draws it: black is nothing, light is light.
add = nodes.new('ShaderNodeAddShader')
material.node_tree.links.new(emit.outputs['Emission'], add.inputs[0])
material.node_tree.links.new(nodes.new('ShaderNodeBsdfTransparent').outputs['BSDF'], add.inputs[1])
material.node_tree.links.new(add.outputs['Shader'], nodes['Material Output'].inputs['Surface'])
material.surface_render_method = 'BLENDED'
floor = bpy.data.objects.new('floor', bpy.data.meshes.new('floor'))
floor.data.from_pydata([(-40, -40, -0.1), (40, -40, -0.1), (40, 40, -0.1), (-40, 40, -0.1)], [], [(0, 1, 2, 3)])
stone = bpy.data.materials.new('stone')
stone.diffuse_color = (0.06, 0.055, 0.05, 1)
floor.data.materials.append(stone)
scene.collection.objects.link(floor)
cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
scene.collection.objects.link(cam)
cam.location = (-14, 0, 24)  # behind and above, the way it is thrown away from the knight
cam.rotation_euler = (math.radians(30), 0, math.radians(-90))
scene.camera = cam
scene.render.filepath = os.path.join(OUT, 'knight_wave_top.png')
bpy.ops.render.render(write_still=True)
print('wrote', GLB)
