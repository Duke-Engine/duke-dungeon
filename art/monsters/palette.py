"""Flat colours baked onto one palette picture, so a model is drawn by one material with a skin the game can find --
shared by art/monsters/export_bestiary.py and export_licensed_monsters.py (as art/monsters/export_monsters.py does it)."""

import os

import bpy

SWATCH = 8


def to_srgb(c):
    return 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055


def base_colour(material):
    if material is None or not material.use_nodes:
        return (0.8, 0.8, 0.8)
    for node in material.node_tree.nodes:
        if node.type == "BSDF_PRINCIPLED":
            socket = node.inputs["Base Color"]
            if socket.links:
                return None
            return tuple(round(v, 4) for v in socket.default_value[:3])
    return (0.8, 0.8, 0.8)


def has_picture(material):
    return material is not None and material.use_nodes and any(
        n.type == "TEX_IMAGE" and n.image is not None for n in material.node_tree.nodes)


def palette(name, meshes, out):
    """Every flat colour on one picture of 8-pixel swatches; every face on its colour's swatch; one material."""
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
    path = os.path.join(out, name + "_palette.png")
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
            i = colours.index(slots[min(poly.material_index, len(slots) - 1)])
            u = ((i % across) * SWATCH + SWATCH / 2) / size
            v = ((i // across) * SWATCH + SWATCH / 2) / size
            for loop in poly.loop_indices:
                uv[loop].uv = (u, v)
            poly.material_index = 0
        mesh.materials.clear()
        mesh.materials.append(material)
