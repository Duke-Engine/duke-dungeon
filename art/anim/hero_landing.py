"""Adds the heroes' landing clip, HeroLanding, to a .glb that already carries a kneel and an idle on the same rig.

    py art/anim/hero_landing.py <file.glb> <kneel clip> <idle clip> [<seconds into the kneel>]
    py art/anim/hero_landing.py src/main/resources/models/heroes/lange/lange.glb Cook Idle 1.2
    py art/anim/hero_landing.py src/main/resources/animations/characters/huntress.glb Cook Ranged_Bow_Idle

The seamless campaign's intro (docs/plan/2026-10-15-seamless-campaign.md, section 4.2): he comes down in the
meteor and is found kneeling in the smoke -- the "superhero landing", one knee down, a hand to the ground, the
head bowed -- then rises to his stance. The kneel is his own Cook (Quaternius' Fixing_Kneeling on his rig) at the
deepest of its frames (where its hips are lowest, or the seconds given), the stance his idle's first frame; both are his already, so nothing is retargeted. Keys:
knelt from the first frame, held to KNEEL seconds, risen by RISE, held to the end (LENGTH, 70 game frames).

Written straight into the glTF: one new animation of its own samplers and accessors appended to the binary,
everything else in the file -- its mesh, pictures and clips -- left byte for byte as it was. Run again, it
replaces its own clip. Plain Python, no Blender.
"""

import json
import struct
import sys

KNEEL = 1.2
RISE = 2.0
LENGTH = 70 / 30
NAME = "HeroLanding"


def read(path):
    data = open(path, "rb").read()
    length = struct.unpack("<I", data[12:16])[0]
    gltf = json.loads(data[20:20 + length])
    rest = data[20 + length:]
    bin_length = struct.unpack("<I", rest[0:4])[0]
    return gltf, bytearray(rest[8:8 + bin_length])


def write(path, gltf, blob):
    text = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    text += b" " * (-len(text) % 4)
    blob = bytes(blob) + b"\0" * (-len(blob) % 4)
    total = 12 + 8 + len(text) + 8 + len(blob)
    with open(path, "wb") as out:
        out.write(struct.pack("<4sII", b"glTF", 2, total))
        out.write(struct.pack("<I4s", len(text), b"JSON") + text)
        out.write(struct.pack("<I4s", len(blob), b"BIN\0") + blob)


SIZES = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}


def values(gltf, blob, index):
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    n = SIZES[acc["type"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = view.get("byteStride", 4 * n)
    assert acc["componentType"] == 5126, "float accessors only"
    return [struct.unpack_from("<%df" % n, blob, start + i * stride) for i in range(acc["count"])]


def at(times, keys, t, rotation):
    """The channel's value at time t, linear between keys (normalised slerp-ish for a rotation)."""
    if t <= times[0][0]:
        return list(keys[0])
    for i in range(1, len(times)):
        if t <= times[i][0]:
            a, b = times[i - 1][0], times[i][0]
            u = 0.0 if b == a else (t - a) / (b - a)
            p, q = keys[i - 1], keys[i]
            if rotation and sum(x * y for x, y in zip(p, q)) < 0:
                q = [-x for x in q]
            v = [x + (y - x) * u for x, y in zip(p, q)]
            if rotation:
                norm = sum(x * x for x in v) ** 0.5
                v = [x / norm for x in v]
            return v
    return list(keys[-1])


def channels(gltf, blob, animation):
    """(node, path) -> (times, keys) of an animation."""
    out = {}
    for ch in animation["channels"]:
        sampler = animation["samplers"][ch["sampler"]]
        out[(ch["target"]["node"], ch["target"]["path"])] = (values(gltf, blob, sampler["input"]),
                                                            values(gltf, blob, sampler["output"]))
    return out


def add(gltf, blob, floats, kind):
    """A new accessor over `floats` (rows of SIZES[kind]) appended to the binary; its index."""
    while len(blob) % 4:
        blob.append(0)
    offset = len(blob)
    flat = [x for row in floats for x in row]
    blob.extend(struct.pack("<%df" % len(flat), *flat))
    gltf["bufferViews"].append({"buffer": 0, "byteOffset": offset, "byteLength": 4 * len(flat)})
    acc = {"bufferView": len(gltf["bufferViews"]) - 1, "componentType": 5126, "count": len(floats), "type": kind}
    if kind == "SCALAR":
        acc["min"] = [min(r[0] for r in floats)]
        acc["max"] = [max(r[0] for r in floats)]
    gltf["accessors"].append(acc)
    return len(gltf["accessors"]) - 1


def main(path, kneel_name, idle_name, when=None):
    gltf, blob = read(path)
    gltf["animations"] = [a for a in gltf["animations"] if a["name"] != NAME]
    by_name = {a["name"]: a for a in gltf["animations"]}
    kneel = channels(gltf, blob, by_name[kneel_name])
    idle = channels(gltf, blob, by_name[idle_name])
    # The deepest of the kneel: when the hips -- the moving bone that stands highest -- are lowest (glTF is y-up).
    moved = [keys for (node, kind), keys in kneel.items() if kind == "translation"]
    hips = max(moved, key=lambda tk: sum(k[1] for k in tk[1]) / len(tk[1]))
    low = float(when) if when is not None else min((t[0] for t in hips[0]), key=lambda t: at(*hips, t, False)[1])
    rows = [[0.0], [KNEEL], [RISE], [LENGTH]]
    animation = {"name": NAME, "channels": [], "samplers": []}
    input_ = add(gltf, blob, rows, "SCALAR")
    for key in sorted(set(kneel) | set(idle)):
        node, kind = key
        if kind not in ("rotation", "translation", "scale"):
            continue
        rotation = kind == "rotation"
        rest = gltf["nodes"][node].get(kind, [0, 0, 0, 1] if rotation else [1, 1, 1] if kind == "scale"
                                        else [0, 0, 0])
        down = at(*kneel[key], low, rotation) if key in kneel else list(rest)
        up = at(*idle[key], 0.0, rotation) if key in idle else list(rest)
        if rotation and sum(x * y for x, y in zip(down, up)) < 0:
            up = [-x for x in up]
        output = add(gltf, blob, [down, down, up, up], "VEC4" if rotation else "VEC3")
        animation["samplers"].append({"input": input_, "output": output, "interpolation": "LINEAR"})
        animation["channels"].append({"sampler": len(animation["samplers"]) - 1,
                                      "target": {"node": node, "path": kind}})
    gltf["animations"].append(animation)
    gltf["buffers"][0]["byteLength"] = len(blob) + (-len(blob) % 4)
    write(path, gltf, blob)
    print(f"{path}: {NAME} from {kneel_name} at {low:.2f}s and {idle_name}, {len(animation['channels'])} channels")


if __name__ == "__main__":
    main(*sys.argv[1:5])
