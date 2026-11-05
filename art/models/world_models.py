"""Copies the world kit (WorkSpaces/duke-games/models/world_kit/out/world_kit.glb) into the game's resources: a glb a piece, no
pictures inside, all of them naming one folder of pictures beside them, models/world_kit/textures.

    py -3 art/models/world_models.py [WORKSPACE_MODELS_DIR]

A wood is a dozen models over one foliage atlas: embedded, each would decode its own 21 MB copy of it; named by uri, jME's
asset cache holds it once. Only POSITION, NORMAL, TEXCOORD_0 and COLOR_0 are kept, the shade the client's scenery reads.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from realistic_models import read, write  # noqa: E402

OUT = Path("src/main/resources/models/world_kit")
KEEP = ("POSITION", "NORMAL", "TEXCOORD_0", "COLOR_0")


def cut(document, binary, node):
    """A document of one node: its mesh's primitives, its accessors and their views packed, its images named by uri."""
    source = document["meshes"][node["mesh"]]
    out = {"asset": document["asset"], "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": node["name"], "mesh": 0}],
           "meshes": [], "accessors": [], "bufferViews": [], "materials": [], "textures": [], "images": []}
    packed = bytearray()
    accessors, materials = {}, {}

    def accessor(index):
        if index not in accessors:
            a = dict(document["accessors"][index])
            view = document["bufferViews"][a.pop("bufferView")]
            at = view.get("byteOffset", 0)
            packed.extend(b"\0" * (-len(packed) % 4))
            out["bufferViews"].append({"buffer": 0, "byteOffset": len(packed), "byteLength": view["byteLength"]})
            packed.extend(binary[at:at + view["byteLength"]])
            a["bufferView"] = len(out["bufferViews"]) - 1
            accessors[index] = len(out["accessors"])
            out["accessors"].append(a)
        return accessors[index]

    def material(index):
        if index not in materials:
            m = json.loads(json.dumps(document["materials"][index]))
            pbr = m["pbrMetallicRoughness"]
            pbr.pop("metallicRoughnessTexture", None)
            suffix = "png" if m["name"] == "foliage" else "jpg"
            out["images"].append({"uri": f"textures/{m['name']}.{suffix}"})
            out["textures"].append({"source": len(out["images"]) - 1})
            pbr["baseColorTexture"] = {"index": len(out["textures"]) - 1}
            materials[index] = len(out["materials"])
            out["materials"].append(m)
        return materials[index]

    primitives = []
    for p in source["primitives"]:
        primitives.append({"attributes": {k: accessor(v) for k, v in p["attributes"].items() if k in KEEP},
                           "indices": accessor(p["indices"]), "material": material(p["material"]), "mode": 4})
    out["meshes"].append({"name": node["name"], "primitives": primitives})
    out["buffers"] = [{"byteLength": len(packed)}]
    return out, bytes(packed)


def main():
    models = Path(sys.argv[1] if len(sys.argv) > 1 else "../../WorkSpaces/duke-games/models")
    document, binary = read(models / "world_kit" / "out" / "world_kit.glb")
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "textures").mkdir(exist_ok=True)
    root = next(n for n in document["nodes"] if n.get("name") == "world_kit")
    for child in root["children"]:
        node = document["nodes"][child]
        scene, packed = cut(document, binary, node)
        write(OUT / f"{node['name']}.glb", scene, packed)
    # The pictures, each once: the kit's source files, not cut, as textures.py wrote them.
    for m in document["materials"]:
        suffix = "png" if m["name"] == "foliage" else "jpg"
        picture = models / "world_kit" / "source" / f"{m['name']}.{suffix}"
        (OUT / "textures" / picture.name).write_bytes(picture.read_bytes())
    print("wrote", len(root["children"]), "pieces to", OUT)


if __name__ == "__main__":
    main()
