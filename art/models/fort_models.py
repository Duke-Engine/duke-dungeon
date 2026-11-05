"""Copies the fort kit's pieces Act I uses from the workspace (WorkSpaces/duke-games/models/fort_kit/out) into the game's
resources, one glb a piece, textures cut to 1024 like realistic_models.py.

    py -3 art/models/fort_models.py [WORKSPACE_MODELS_DIR]

What it does that realistic_models.piece does not:

* A piece is a node and everything under it (a gatehouse and the portcullis it carries); the kit's animation `Open` and
  `Close` are one clip for the whole file, so each piece gets only the channels that move its own nodes.
* The kit's nodes rest in their OPEN pose (the last frame of `Open`); the game shows a gate shut until it opens, so the
  animated nodes are written at frame 1 of `Open`, closed, and `Open` plays them out.
* A wall runs along +x from its origin with its outside on glTF +z. The game turns a piece by `facing` about the
  vertical and a ring of walls is laid clockwise with the outside on the -y side of its facing 0 (north wall), which is
  glTF -z: each run piece is turned half a circle about its own middle (x -> L - x, z -> -z) so it still runs along +x
  from its origin and its outside is on -z. A GATE is a game object standing in its passage (the sim's blocker and
  the swinging leaves in one), so its origin is the middle of the passage: x -> c - x.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from realistic_models import PIXELS, read, rebuild, write  # noqa: E402

MODELS = Path("src/main/resources/models/buildings")
# Scenery is drawn in its base colour by the client (KitTiles replaces the material), so a wall carries that picture
# alone; the gates are objects drawn with their maps (RealisticModels), so they keep theirs.
WALLS = MODELS / "fort_kit"
GATES_FOLDER = MODELS / "realistic"

# name: (kit node, length along the wall in metres or None, how it is turned, children to leave out)
#   "run": turned about the middle of its length, origin at the wall's start
#   "gate": turned about the middle of its passage, origin there (a game object)
#   None: round, centred on its origin, not turned
STATIC = {
    "town_wall_long": ("town_wall_long", 9.0, "run", ()),
    "town_wall_tower": ("town_wall_tower", 3.0, "run", ()),
    "town_gatehouse": ("town_gatehouse", 6.0, "run", ()),
    "town_tower_round": ("town_tower_round", None, None, ()),
    "town_tower_round_low": ("town_tower_round_low", None, None, ()),
    "fort_wall": ("fort_wall", 4.5, "run", ()),
    "fort_wall_gable": ("fort_wall_gable", 1.5, "run", ()),
    "fort_gatehouse": ("fort_gatehouse", 3.0, "run", ("Portcullis",)),
    "fort_tower_cone": ("fort_tower_cone", None, None, ()),
    "fort_turret": ("fort_turret", None, None, ()),
    "fort_keep": ("fort_keep", None, None, ()),
}
# the moving parts, as objects: name: (kit node, middle of the passage in metres, nodes to keep, name of the new root)
GATES = {
    "town_gate": ("town_gate", 3.0, None),
    "fort_portcullis": ("fort_gatehouse", 1.5, ("Portcullis",)),
}
DARK = ["fort_wall", "fort_wall_gable", "fort_gatehouse", "fort_portcullis", "fort_tower_cone", "fort_turret",
        "fort_keep"]
HALF_TURN = [0.0, 1.0, 0.0, 0.0]


def accessor_values(document, binary, index):
    import struct
    a = document["accessors"][index]
    view = document["bufferViews"][a["bufferView"]]
    at = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[a["type"]]
    return struct.unpack_from("<%df" % (a["count"] * width), binary, at)[:width]


def first_frame(document, binary, node_index):
    """{'translation': ..., 'rotation': ...} of the node at the first frame of `Open`, for the channels that move it."""
    opening = next(a for a in document["animations"] if a["name"] == "Open")
    out = {}
    for channel in opening["channels"]:
        if channel["target"]["node"] == node_index:
            sampler = opening["samplers"][channel["sampler"]]
            out[channel["target"]["path"]] = [round(v, 6) for v in accessor_values(document, binary, sampler["output"])]
    return out


def cut(document, binary, root_name, name, length, turn, leave_out=(), keep_only=None, middle=None, colour_only=False):
    """A glb document and buffer holding the kit node `root_name` (and its children), as the piece `name`."""
    nodes = document["nodes"]
    by_name = {n.get("name"): i for i, n in enumerate(nodes)}
    root = by_name[root_name]
    views, accessors, materials, textures, images, samplers = {}, {}, {}, {}, {}, {}
    out = {"nodes": [], "meshes": [], "accessors": [], "bufferViews": [], "materials": [], "textures": [],
           "images": [], "samplers": []}
    chunks = []

    def view_of(index):
        if index not in views:
            raw = document["bufferViews"][index]
            at = raw.get("byteOffset", 0)
            views[index] = len(out["bufferViews"])
            chunks.append(binary[at:at + raw["byteLength"]])
            out["bufferViews"].append({k: v for k, v in raw.items() if k != "byteOffset"})
        return views[index]

    def accessor(index):
        if index not in accessors:
            source = dict(document["accessors"][index])
            source["bufferView"] = view_of(source["bufferView"])
            accessors[index] = len(out["accessors"])
            out["accessors"].append(source)
        return accessors[index]

    def texture(index):
        if index not in textures:
            source = dict(document["textures"][index])
            image = dict(document["images"][source["source"]])
            image["bufferView"] = view_of(image["bufferView"])
            source["source"] = len(out["images"])
            out["images"].append(image)
            if "sampler" in source:
                if source["sampler"] not in samplers:
                    samplers[source["sampler"]] = len(out["samplers"])
                    out["samplers"].append(document["samplers"][source["sampler"]])
                source["sampler"] = samplers[source["sampler"]]
            textures[index] = len(out["textures"])
            out["textures"].append(source)
        return textures[index]

    def material(index):
        if index not in materials:
            source = json.loads(json.dumps(document["materials"][index]))
            if colour_only:
                for holder in (source, source.get("pbrMetallicRoughness", {})):
                    for key in ("normalTexture", "occlusionTexture", "emissiveTexture", "metallicRoughnessTexture"):
                        holder.pop(key, None)
            for holder in (source, source.get("pbrMetallicRoughness", {})):
                for key, value in holder.items():
                    if isinstance(value, dict) and "index" in value:
                        value["index"] = texture(value["index"])
            materials[index] = len(out["materials"])
            out["materials"].append(source)
        return materials[index]

    def mesh(index):
        source = document["meshes"][index]
        primitives = []
        for primitive in source["primitives"]:
            copy = dict(primitive)
            copy["attributes"] = {k: accessor(v) for k, v in primitive["attributes"].items()}
            if "indices" in primitive:
                copy["indices"] = accessor(primitive["indices"])
            if "material" in primitive:
                copy["material"] = material(primitive["material"])
            primitives.append(copy)
        out["meshes"].append({"name": source["name"], "primitives": primitives})
        return len(out["meshes"]) - 1

    moved = {}  # source node index -> new index, for the animation channels

    def node(index, is_root=False):
        source = nodes[index]
        copy = {"name": name if is_root else source["name"]}
        if "mesh" in source:
            copy["mesh"] = mesh(source["mesh"])
        for key in ("translation", "rotation", "scale"):
            if key in source:
                copy[key] = source[key]
        copy.update(first_frame(document, binary, index))  # closed, where it is one of the moving parts
        slot = len(out["nodes"])
        out["nodes"].append(copy)
        moved[index] = slot
        kids = [c for c in source.get("children", []) if nodes[c]["name"] not in leave_out
                and (keep_only is None or is_root and nodes[c]["name"] in keep_only)]
        if kids:
            copy["children"] = [node(c) for c in kids]
        return slot

    node(root, True)
    top = out["nodes"][0]
    if turn:
        # The root of a gate carries no mesh of its own in the kit, so the turn sits on it; a wall's mesh is on it.
        shift = length if turn == "run" else middle
        top["rotation"] = HALF_TURN
        top["translation"] = [shift, 0.0, 0.0]
        top.pop("scale", None)
    animations = []
    for clip in document.get("animations", []):
        channels, clip_samplers = [], []
        for channel in clip["channels"]:
            target = channel["target"]["node"]
            if target in moved:
                sampler = clip["samplers"][channel["sampler"]]
                clip_samplers.append({"input": accessor(sampler["input"]), "output": accessor(sampler["output"]),
                                      "interpolation": sampler.get("interpolation", "LINEAR")})
                channels.append({"sampler": len(clip_samplers) - 1,
                                 "target": {"node": moved[target], "path": channel["target"]["path"]}})
        if channels:
            animations.append({"name": clip["name"], "channels": channels, "samplers": clip_samplers})
    packed = bytearray()
    for view, chunk in zip(out["bufferViews"], chunks):
        packed += b"\0" * (-len(packed) % 4)
        view["buffer"] = 0
        view["byteOffset"] = len(packed)
        packed += chunk
    scene = {"asset": document["asset"], "scene": 0, "scenes": [{"nodes": [0]}], "buffers": [{"byteLength": len(packed)}]}
    for key in ("nodes", "meshes", "accessors", "bufferViews", "materials", "textures", "images", "samplers"):
        if out[key]:
            scene[key] = out[key]
    if animations:
        scene["animations"] = animations
    return scene, bytes(packed)


def main():
    models = Path(sys.argv[1] if len(sys.argv) > 1 else "../../WorkSpaces/duke-games/models")
    for kit, walls, gates, only in (("fort_kit.glb", WALLS, GATES_FOLDER, None),
                                    ("fort_kit_dark.glb", WALLS / "dark", GATES_FOLDER / "dark", DARK)):
        document, binary = read(models / "fort_kit" / "out" / kit)
        for name, (node, length, turn, leave_out) in STATIC.items():
            if only is not None and name not in only:
                continue
            scene, packed = cut(json.loads(json.dumps(document)), binary, node, name, length, turn, leave_out,
                                colour_only=True)
            write(walls / f"{name}.glb", scene, rebuild(scene, packed, PIXELS))
        for name, (node, middle, keep_only) in GATES.items():
            if only is not None and name not in only:
                continue
            scene, packed = cut(json.loads(json.dumps(document)), binary, node, name, None, "gate", (), keep_only,
                                middle)
            write(gates / f"{name}.glb", scene, rebuild(scene, packed, PIXELS))


if __name__ == "__main__":
    main()
