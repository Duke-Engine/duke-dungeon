"""Copies the realistic Act I models from the workspace (WorkSpaces/duke-games/models/<name>/out) into the game's
resources, with their textures cut to a size the game can hold in memory, and cuts act1_props.glb into one file per piece
(the game loads a file per model).

    py -3 art/models/realistic_models.py [WORKSPACE_MODELS_DIR]

Every texture is kept (colour, normal, occlusion-roughness-metal, emissive): ModelPicturesTest allows them for these
paths only. The size is the reason for the cut, not the maps: a 2048 picture decodes to 21 MB with its mips, and a person
has three to five of them. People, skeletons and props go to 1024; the hero keeps his 2048.
"""
import io
import json
import shutil
import struct
import sys
from pathlib import Path

from PIL import Image

PIXELS = 1024
GLB_MAGIC = 0x46546C67
JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942

HUMANS = ["soldier", "commander", "militia", "enemy_commander", "dark_lord", "blacksmith", "old_woman", "merchant",
          "archer", "healer", "villager", "farmer", "traveller", "pedlar", "tower_keeper", "stablemaster"]
DEAD = ["skeleton", "runner", "brute", "skeleton_mage", "revenant", "grave_raiser", "skeleton_healer", "skeleton_summoner", "stalker", "necromancer", "reaper", "champion", "warden"]
PIECES = ["grave", "pillar", "pillar_decorated", "barrel", "well", "statue"]

CHARACTERS = Path("src/main/resources/models/characters/realistic")
PROPS = Path("src/main/resources/models/props/realistic")
HERO = Path("src/main/resources/models/characters/heroes/hero_lange.glb")


def read(path):
    data = Path(path).read_bytes()
    magic, _, _ = struct.unpack_from("<III", data, 0)
    assert magic == GLB_MAGIC, path
    length, kind = struct.unpack_from("<II", data, 12)
    assert kind == JSON_CHUNK
    document = json.loads(data[20:20 + length])
    at = 20 + length
    binary = b""
    if at < len(data):
        blength, bkind = struct.unpack_from("<II", data, at)
        assert bkind == BIN_CHUNK
        binary = data[at + 8:at + 8 + blength]
    return document, binary


def write(path, document, binary):
    text = json.dumps(document, separators=(",", ":")).encode()
    text += b" " * (-len(text) % 4)
    binary += b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(text) + 8 + len(binary)
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as out:
        out.write(struct.pack("<III", GLB_MAGIC, 2, total))
        out.write(struct.pack("<II", len(text), JSON_CHUNK) + text)
        out.write(struct.pack("<II", len(binary), BIN_CHUNK) + binary)


def shrink(data, mime, pixels):
    """The picture cut to at most pixels on a side; the same bytes if it is already that small."""
    picture = Image.open(io.BytesIO(data))
    if max(picture.size) <= pixels:
        return data
    picture = picture.resize((pixels, pixels) if picture.width == picture.height
                             else (min(pixels, picture.width), min(pixels, picture.height)), Image.LANCZOS)
    out = io.BytesIO()
    if mime == "image/png":
        picture.save(out, "PNG", optimize=True)
    else:
        picture.convert("RGB").save(out, "JPEG", quality=90, optimize=True)
    return out.getvalue()


def rebuild(document, binary, pixels):
    """The same document with its pictures cut to `pixels` and its buffer views packed again."""
    views = document["bufferViews"]
    picture_of = {image["bufferView"]: image for image in document.get("images", [])}
    packed = bytearray()
    for index, view in enumerate(views):
        chunk = binary[view.get("byteOffset", 0):view.get("byteOffset", 0) + view["byteLength"]]
        if index in picture_of and pixels:
            chunk = shrink(chunk, picture_of[index].get("mimeType", "image/jpeg"), pixels)
        packed += b"\0" * (-len(packed) % 4)
        view["byteOffset"] = len(packed)
        view["byteLength"] = len(chunk)
        packed += chunk
    document["buffers"] = [{"byteLength": len(packed)}]
    return bytes(packed)


def piece(document, binary, name):
    """A glb holding the node `name` alone, with what only it uses."""
    node = next(n for n in document["nodes"] if n.get("name") == name)
    mesh = document["meshes"][node["mesh"]]
    accessors, views, materials, textures, images, samplers = {}, {}, {}, {}, {}, {}

    def take(table, key):
        return table.setdefault(key, len(table))

    out_meshes = [{"name": mesh["name"], "primitives": []}]
    out_accessors, out_views, out_materials, out_textures, out_images, out_samplers = [], [], [], [], [], []
    chunks = []

    def accessor(index):
        if index in accessors:
            return accessors[index]
        source = dict(document["accessors"][index])
        view = source["bufferView"]
        if view not in views:
            raw = document["bufferViews"][view]
            at = raw.get("byteOffset", 0)
            views[view] = len(out_views)
            chunks.append(binary[at:at + raw["byteLength"]])
            copy = {key: value for key, value in raw.items() if key != "byteOffset"}
            out_views.append(copy)
        source["bufferView"] = views[view]
        accessors[index] = len(out_accessors)
        out_accessors.append(source)
        return accessors[index]

    def texture(index):
        if index in textures:
            return textures[index]
        source = dict(document["textures"][index])
        image = document["images"][source["source"]]
        view = image["bufferView"]
        if view not in views:
            raw = document["bufferViews"][view]
            at = raw.get("byteOffset", 0)
            views[view] = len(out_views)
            chunks.append(binary[at:at + raw["byteLength"]])
            out_views.append({"buffer": 0, "byteLength": raw["byteLength"]})
        copy = dict(image)
        copy["bufferView"] = views[view]
        source["source"] = len(out_images)
        out_images.append(copy)
        if "sampler" in source:
            if source["sampler"] not in samplers:
                samplers[source["sampler"]] = len(out_samplers)
                out_samplers.append(document["samplers"][source["sampler"]])
            source["sampler"] = samplers[source["sampler"]]
        textures[index] = len(out_textures)
        out_textures.append(source)
        return textures[index]

    def material(index):
        if index in materials:
            return materials[index]
        source = json.loads(json.dumps(document["materials"][index]))
        for holder in (source, source.get("pbrMetallicRoughness", {})):
            for key, value in holder.items():
                if isinstance(value, dict) and "index" in value:
                    value["index"] = texture(value["index"])
        materials[index] = len(out_materials)
        out_materials.append(source)
        return materials[index]

    for primitive in mesh["primitives"]:
        copy = dict(primitive)
        copy["attributes"] = {key: accessor(value) for key, value in primitive["attributes"].items()}
        if "indices" in primitive:
            copy["indices"] = accessor(primitive["indices"])
        if "material" in primitive:
            copy["material"] = material(primitive["material"])
        out_meshes[0]["primitives"].append(copy)

    # Re-point the views at one packed buffer.
    packed = bytearray()
    for view, chunk in zip(out_views, chunks):
        packed += b"\0" * (-len(packed) % 4)
        view["buffer"] = 0
        view["byteOffset"] = len(packed)
        packed += chunk
    scene = {
        "asset": document["asset"],
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"name": name, "mesh": 0}],
        "meshes": out_meshes,
        "accessors": out_accessors,
        "bufferViews": out_views,
        "buffers": [{"byteLength": len(packed)}],
    }
    for key, value in (("materials", out_materials), ("textures", out_textures), ("images", out_images),
                       ("samplers", out_samplers)):
        if value:
            scene[key] = value
    return scene, bytes(packed)


def main():
    models = Path(sys.argv[1] if len(sys.argv) > 1 else "../../WorkSpaces/duke-games/models")
    for name in HUMANS + DEAD:
        document, binary = read(models / name / "out" / f"{name}.glb")
        binary = rebuild(document, binary, PIXELS)
        write(CHARACTERS / f"{name}.glb", document, binary)
    for name in ("rat", "bat"):
        document, binary = read(models / "act1_props" / "out" / f"{name}.glb")
        write(CHARACTERS / f"{name}.glb", document, rebuild(document, binary, PIXELS))
    # Lange keeps his 2048: he is the one person always on screen.
    shutil.copyfile(models / "hero_lange" / "out" / "hero_lange.glb", HERO)
    document, binary = read(models / "act1_props" / "out" / "act1_props.glb")
    for name in PIECES:
        scene, bin_ = piece(json.loads(json.dumps(document)), binary, name)
        scene_binary = rebuild(scene, bin_, PIXELS)
        write(PROPS / f"{name}.glb", scene, scene_binary)


if __name__ == "__main__":
    main()
