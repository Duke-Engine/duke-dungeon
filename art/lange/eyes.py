"""Lange's eyes, painted into his skin picture where his sunglasses were.

The download's face has no eyes under the glasses: it was never meant to be
seen without them. So the glasses go -- frame and lenses; the earring in the
same mesh stays -- and the place each lens covered is found on his face (a ray
from the front, through the lens's middle) and read back as a spot on his
skin picture, with which way across and up runs there. Each eye is then
painted at that spot, in the face's own soft painted style: an almond of
white, an elf-green iris darkening to its rim, a pupil, a highlight and a dark
upper lid line. Painted at the picture's full size, before it is downsized,
so the downsizing smooths every edge.
"""

import math

import bmesh
import mathutils
import numpy as np

# The eye, in metres on his face: width, height of the opening.
WIDTH, HEIGHT = 0.030, 0.0145
# The eye's middle, from the lens's middle: the lenses sat a little low and to the outside.
NUDGE = (0.0, 0.004)
IRIS, PUPIL, SHINE = 0.0058, 0.0026, 0.0012
WHITE = (0.93, 0.90, 0.86)
IRIS_IN, IRIS_RIM = (0.36, 0.62, 0.30), (0.12, 0.28, 0.12)
DARK = (0.05, 0.035, 0.03)
LID = (0.16, 0.09, 0.07)


def _parts(bm):
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
    return parts


def _uv_at(face, point):
    """Where on the face's picture the world point in front of it lands: (uv, material name), or None."""
    to_local = face.matrix_world.inverted()
    hit, at, _, index = face.ray_cast(to_local @ (point + mathutils.Vector((0, -0.3, 0))),
                                      to_local.to_3x3() @ mathutils.Vector((0, 1, 0)))
    if not hit:
        return None
    mesh = face.data
    uvs = mesh.uv_layers.active.data
    for tri in mesh.loop_triangles:
        if tri.polygon_index != index:
            continue
        corners = [mesh.vertices[v].co for v in tri.vertices]
        flat = [mathutils.Vector((*uvs[l].uv, 0)) for l in tri.loops]
        uv = mathutils.geometry.barycentric_transform(at, *corners, *flat)
        inside = mathutils.geometry.intersect_point_tri(at, *corners)
        if inside is not None or len(mesh.polygons[index].vertices) == 3:
            return mathutils.Vector(uv[:2]), face.material_slots[mesh.polygons[index].material_index].name
    return None


def take_off_glasses(glasses, body, depsgraph):
    """The glasses' frame and lenses deleted, the earring kept; returns where each eye goes on the picture."""
    face = body.evaluated_get(depsgraph)
    face.data.calc_loop_triangles()
    bm = bmesh.new()
    bm.from_mesh(glasses.data)
    bm.transform(glasses.matrix_world)
    steel = [p for p in _parts(bm)
             if glasses.data.materials[p[0].material_index].name.startswith("hair skin steel")]
    frame = max(steel, key=len)
    eyes = []
    for lens in [p for p in steel if p is not frame]:
        verts = {v for f in lens for v in f.verts}
        middle = sum((v.co for v in verts), mathutils.Vector()) / len(verts)
        middle += mathutils.Vector((NUDGE[0], 0, NUDGE[1]))
        here = _uv_at(face, middle)
        across = _uv_at(face, middle + mathutils.Vector((0.004, 0, 0)))
        up = _uv_at(face, middle + mathutils.Vector((0, 0, 0.004)))
        if not (here and across and up):
            raise SystemExit(f"EYES: no face behind the lens at {middle}")
        # UV per metre: columns are across (+X) and up (+Z).
        d_across, d_up = (across[0] - here[0]) / 0.004, (up[0] - here[0]) / 0.004
        jacobian = mathutils.Matrix(((d_across.x, d_up.x), (d_across.y, d_up.y)))
        eyes.append((here[0], jacobian, middle.x))
        print(f"EYES eye at x {middle.x:+.3f} z {middle.z:.3f} -> uv {tuple(round(c, 4) for c in here[0])} "
              f"on {here[1]}")
    bmesh.ops.delete(bm, geom=[f for part in steel for f in part], context="FACES")
    bm.transform(glasses.matrix_world.inverted())
    bm.to_mesh(glasses.data)
    bm.free()
    glasses.name = "earring"
    return eyes


def _mix(px, mask, colour):
    for c in range(3):
        px[..., c] = px[..., c] * (1 - mask) + colour[c] * mask


def _soft(distance, width):
    """1 inside, 0 outside, a smooth edge `width` wide around distance 0 (negative is inside)."""
    return np.clip(0.5 - distance / width, 0.0, 1.0)


def paint(image, eyes):
    """Each eye painted onto the picture (Blender image, any size), in place."""
    w, h = image.size
    px = np.empty(w * h * 4, dtype=np.float32)
    image.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)
    for uv, jacobian, _ in eyes:
        to_face = jacobian.inverted()  # uv offset -> metres (across, up)
        reach = max(abs(jacobian[0][0]), abs(jacobian[0][1]), abs(jacobian[1][0]), abs(jacobian[1][1])) * WIDTH
        x0, x1 = int((uv.x - reach) * w), int((uv.x + reach) * w) + 1
        y0, y1 = int((uv.y - reach) * h), int((uv.y + reach) * h) + 1
        ys, xs = np.mgrid[y0:y1, x0:x1]
        du, dv = (xs + 0.5) / w - uv.x, (ys + 0.5) / h - uv.y
        a = to_face[0][0] * du + to_face[0][1] * dv  # metres across
        b = to_face[1][0] * du + to_face[1][1] * dv  # metres up
        edge = 1.5 / (math.hypot(jacobian[0][0], jacobian[1][0]) * w)  # about a pixel and a half, in metres
        s = np.clip(1 - (2 * a / WIDTH) ** 2, 0, None)
        top, bottom = HEIGHT / 2 * s ** 0.7, -HEIGHT / 2 * 0.75 * s ** 0.9
        opening = np.minimum(_soft(b - top, edge), _soft(bottom - b, edge)) * (np.abs(a) < WIDTH / 2)
        region = px[y0:y1, x0:x1]
        _mix(region, opening, WHITE)
        r = np.hypot(a, b - HEIGHT * 0.06)
        iris = _soft(r - IRIS, edge) * opening
        t = np.clip(r / IRIS, 0, 1)[..., None]
        ring = np.array(IRIS_IN) * (1 - t ** 2) + np.array(IRIS_RIM) * t ** 2
        for c in range(3):
            region[..., c] = region[..., c] * (1 - iris) + ring[..., c] * iris
        _mix(region, _soft(r - PUPIL, edge) * opening, DARK)
        shine = np.hypot(a + IRIS * 0.35, b - HEIGHT * 0.06 - IRIS * 0.35)
        _mix(region, _soft(shine - SHINE, edge) * opening, (1, 1, 1))
        # The upper lid: a dark line along the top of the opening, heavier toward the outer corner.
        lid = HEIGHT * (0.10 + 0.06 * np.clip(np.abs(a) / (WIDTH / 2), 0, 1))
        line = _soft(np.abs(b - top - lid * 0.3) - lid / 2, edge) * (np.abs(a) < WIDTH / 2 * 1.08)
        _mix(region, line * 0.9, LID)
        px[y0:y1, x0:x1] = region
    image.pixels.foreach_set(px.ravel())
    image.update()
