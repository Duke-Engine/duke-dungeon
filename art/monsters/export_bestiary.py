"""Writes the bestiary's free monsters -- Quaternius's CC0 and CC-BY packs -- as the game draws them.

    blender -b --factory-startup -P art/monsters/export_bestiary.py -- <downloads folder> <out folder> [name ...]

The downloads folder holds each model as poly.pizza serves it, one .glb a model in a folder named <Model>_<id> (see
MODELS below, CREDITS.md and data/units/bestiary_*.duke). Nothing from it ships but what this writes, into
src/main/resources/models/bestiary/. Then art/models/base_colour_only.js is run over every file written.

As art/monsters/export_monsters.py, and on top of it:

SIX CLIPS, GAME NAMES. Idle, Walk, Run, Attack, Hit and Death where the model has them, taken from its own clips by
the first name in PREFS (or the model's own choice in MODELS) and renamed. poly.pizza cuts a clip's name at 63
characters, so a cut name is matched by its leading part (the pirates' "...|Sword|Cha"). A model with no walk walks by
its idle; one with no death (the kraken's tentacle) is given one: its root shrinks into the ground over a second.

ONE PICTURE OR A PALETTE. Flat colours are baked into one palette picture (one material); a model that came with a
picture of its own keeps it, at 1024 px at most.

FEET ON THE GROUND. poly.pizza's conversion leaves a stray two-unit Icosphere at the origin, which is dropped, and
some rigs hang from the hips with the feet a metre below the origin (the orc, the mushroom king) or the whole body
five metres down (an adventurer). Each is moved so the lowest point of its Idle stands on the origin -- a flyer's so
it hovers a quarter of its height above it. The rig is then flattened to scale 1.

The height each stands at, in the file's units, is printed: a block's ModelScale is the height it is drawn at in
units over this.
"""

import os
import sys

import bpy

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from palette import has_picture, palette  # noqa: E402

args = sys.argv[sys.argv.index("--") + 1:]
SOURCE, OUT = args[0], args[1]
ONLY = args[2:]

PREFS = {
    "Idle": ["Idle", "Flying_Idle", "Rat_Idle", "Snake_Idle", "Tentacle_Idle", "Bat_Flying", "Dragon_Flying",
             "Wasp_Flying", "Flying"],
    "Walk": ["Walk", "Walking", "Fast_Flying", "Rat_Walk", "Bat_Flying", "Dragon_Flying", "Wasp_Flying", "Flying"],
    "Run": ["Run", "Gallop", "Rat_Run"],
    "Attack": ["Attack", "Attack_Headbutt", "Bite_Front", "Headbutt", "Punch", "Rat_Attack", "Bat_Attack",
               "Dragon_Attack", "Wasp_Attack", "Tentacle_Attack"],
    "Hit": ["HitReact", "HitRecieve", "RecieveHit", "Bat_Hit", "Dragon_Hit"],
    "Death": ["Death", "Rat_Death", "Bat_Death", "Dragon_Death", "Wasp_Death"],
}

# Out name -> (the download's folder, whether it flies, {game clip: its own clip} where PREFS would pick wrong).
MODELS = {
    # forest grove
    "armabee": ("Armabee_42djT5zJnx", True, {}),
    "armabee_evolved": ("Armabee_Evolved_GcttdvsqsQ", True, {}),
    "wasp": ("Wasp_3aQgc75sUR", True, {}),
    "stag": ("Stag_tQdzbZ1Cmw", False, {}),
    "fox": ("Fox_Bc97C66HKi", False, {}),
    "mushroom_king": ("Mushroom_King_grnFTziU8u", False, {"Attack": "Weapon"}),
    # roads
    "bandit": ("Hooded_Adventurer_y9KWOVG21R", False, {"Attack": "Sword_Slash"}),
    "brigand": ("Adventurer_ZwF0K7WBmu", False, {"Attack": "Sword_Slash"}),
    "brigand_2": ("Adventurer_5EGWBMpuXq", False, {"Attack": "Sword_Slash"}),  # faces away: its block turns it (Facing)
    "ninja_master": ("Ninja_xGYmeDpfTu", False, {"Attack": "Weapon"}),
    "wolf_grey": ("Wolf_P1gU3Qkr9r", False, {}),
    "bull": ("Bull_a8PIIYwF7r", False, {"Attack": "Attack_Headbutt"}),
    # orcs
    "orc_grunt": ("Orc_Enemy_Q3z8ZX4kUy", False, {}),
    "orc_warrior": ("Orc_5vO2YJsPEf", False, {"Attack": "Weapon"}),
    "monkroose": ("Monkroose_j4rVPvxyLg", False, {"Attack": "Weapon"}),
    # crypt
    "zombie": ("Zombie_VlXjG0N8Eg", False, {"Attack": "Punch"}),
    "zombie_ghoul": ("Zombie_22K0aSZkHV", False, {"Attack": "Punch"}),
    "zombie_brute": ("Big_arm_KaVJET0WHx", False, {"Attack": "Punch"}),
    "zombie_crawler": ("Zombie_half_Htcsn9OrXJ", False, {"Idle": "Crawl", "Walk": "Crawl", "Attack": "Crawl"}),
    "ghost_skull": ("Ghost_Skull_TX8r9WBXpe", True, {}),
    "skull_crawler": ("Skull_Enemy_EHHwZ17lWD", False, {}),
    "bat": ("Bat_hNO9XvjlKa", True, {}),
    "rat": ("Rat_iltq5bVNaV", False, {}),
    "fallen_king": ("King_I1gTjmuK2m", False, {"Attack": "Sword_Slash"}),
    "witch": ("Witch_QBEOV9ZUT8", False, {"Attack": "Sword_Slash"}),
    "cult_mage": ("Animated_Wizard_kttbFvCl2C", False, {"Attack": "Spell1"}),
    # coast and lake
    "crab": ("Crab_Enemy_Gs3yfsV5lB", False, {}),
    "sharkman": ("Sharky_iPV5YmAoIv", False, {"Attack": "Sword"}),
    "pirate_anne": ("Anne_tZYaOQ4l94", False, {"Attack": "Sword"}),
    "pirate_mako": ("Mako_2urczqZ9Xf", False, {"Attack": "Sword"}),
    "pirate_captain": ("Pirate_Captain_sN18LyyHAU", False, {"Attack": "Sword"}),
    "skeleton_pirate": ("Skeleton_1XZD9GK6Kj", False, {"Attack": "Sword"}),
    "kraken_tentacle": ("Tentacle_BR1vpIvvvv", False, {"Attack": "Tentacle_Attack"}),
    # snow
    "yeti": ("Yeti_ceRHrn8HHE", False, {"Attack": "Weapon"}),
    "yeti_cub": ("Yeti_S1E7idPFhe", False, {}),
    "hywirl": ("Hywirl_zjr7CmWGER", True, {}),
    "wyvern": ("Dragon_VBvzjFIYws", True, {}),
    # the demons by the queen's castle
    "demon_brute": ("Demon_LnfIziKv4o", True, {"Attack": "Weapon"}),
    "blue_demon": ("Blue_Demon_S7jYW6Amye", False, {"Attack": "Weapon"}),
    "imp": ("Demon_Mo2ky6vkf8", True, {}),
    # ruins
    "crowned_golem": ("Goleling_Evolved_iHEuXiH6Aj", True, {}),
}


def source_of(folder):
    path = os.path.join(SOURCE, folder, folder + ".glb")
    if not os.path.exists(path):
        raise SystemExit("no download " + path)
    return path


def find_action(own):
    for action in bpy.data.actions:
        if action.name == own or own in action.name.split("|"):
            return action
    for action in bpy.data.actions:  # a name cut at 63 characters: its cut last part starts the clip's name
        last = action.name.split("|")[-1]
        if len(action.name) == 63 and len(last) >= 2 and own.startswith(last) and len(own) - len(last) <= 3:
            return action
    return None


def shrink_pictures(limit=1024):
    for image in bpy.data.images:
        if image.size[0] > limit or image.size[1] > limit:
            f = limit / max(image.size)
            image.scale(int(image.size[0] * f), int(image.size[1] * f))


def play(armature, action):
    ad = armature.animation_data or armature.animation_data_create()
    ad.action = action
    if action is not None and action.slots:
        ad.action_slot = action.slots[0]


def extent(meshes, armature, action):
    """The lowest and highest point the meshes reach over the clip, sampled every few frames."""
    play(armature, action)
    low, high = 1e9, -1e9
    start, end = (int(action.frame_range[0]), int(action.frame_range[1])) if action else (1, 1)
    for frame in range(start, end + 1, max(1, (end - start) // 6)):
        bpy.context.scene.frame_set(frame)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        for obj in meshes:
            evaluated = obj.evaluated_get(depsgraph)
            mesh = evaluated.to_mesh()
            for v in mesh.vertices:
                z = (evaluated.matrix_world @ v.co).z
                low, high = min(low, z), max(high, z)
            evaluated.to_mesh_clear()
    play(armature, None)
    return low, high


def flatten(armature):
    factor = armature.scale.x
    bpy.ops.object.select_all(action="SELECT")
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    if abs(factor - 1) < 1e-6:
        return
    for action in bpy.data.actions:
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        if curve.data_path.endswith(".location"):
                            for key in curve.keyframe_points:
                                key.co.y *= factor
                                key.handle_left.y *= factor
                                key.handle_right.y *= factor


def lift(arm, dz):
    """Every clip moves its root bones `dz` up, so the body stands where it should in every frame. Baked into the
    clips rather than left on the rig's node: a loader that reads a node's offset under a skin as well as over it
    would count it twice."""
    import mathutils as _m
    up = _m.Vector((0, 0, dz / arm.scale.z))
    for bone in [b for b in arm.data.bones if b.parent is None]:
        local = bone.matrix_local.to_3x3().inverted() @ up
        path = 'pose.bones["%s"].location' % bone.name
        for action in bpy.data.actions:
            for layer in action.layers:
                for strip in layer.strips:
                    for bag in strip.channelbags:
                        for i in range(3):
                            curve = bag.fcurves.find(path, index=i)
                            if curve is None:
                                curve = bag.fcurves.new(path, index=i, group_name=bone.name)
                                curve.keyframe_points.insert(action.frame_range[0], 0.0)
                            for key in curve.keyframe_points:
                                key.co.y += local[i]
                                key.handle_left.y += local[i]
                                key.handle_right.y += local[i]


def sink(armature, idle):
    """A death for one that has none: its idle's first pose, its root shrinking to nothing over a second."""
    death = idle.copy()
    death.name = "Death"
    root = next(b for b in armature.pose.bones if b.parent is None)
    play(armature, death)
    start = int(idle.frame_range[0])
    for frame, size in ((start, 1.0), (start + 8, 0.85), (start + 24, 0.02)):
        root.scale = (size, size, size)
        root.keyframe_insert("scale", frame=frame)
    # Only the first pose of the idle, so it does not go on swaying as it goes.
    for layer in death.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    if curve.data_path.endswith(".scale") and root.name in curve.data_path:
                        continue
                    first = curve.evaluate(start)
                    while len(curve.keyframe_points) > 1:
                        curve.keyframe_points.remove(curve.keyframe_points[-1])
                    curve.keyframe_points[0].co = (start, first)
    play(armature, None)
    root.scale = (1.0, 1.0, 1.0)
    return death


for out, (folder, flies, own) in MODELS.items():
    if ONLY and out not in ONLY:
        continue
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=source_of(folder))
    for stray in [o for o in bpy.data.objects if o.type == "MESH" and (o.name.startswith("Icosphere") or (
            o.parent is None and not any(m.type == "ARMATURE" for m in o.modifiers)))]:
        bpy.data.objects.remove(stray, do_unlink=True)
    for empty in [o for o in bpy.data.objects if o.type == "EMPTY" and o.name.endswith("_end")]:
        bpy.data.objects.remove(empty, do_unlink=True)
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    armature = next(o for o in bpy.data.objects if o.type == "ARMATURE")

    # Meshes hung on a bone (a stag's antlers, a bat's eyes) are skinned to it at rest, so they follow it.
    armature.data.pose_position = "REST"
    bpy.context.view_layer.update()
    for obj in meshes:
        if obj.parent_type == "BONE" and obj.parent_bone:
            world = obj.matrix_world.copy()
            bone = obj.parent_bone
            obj.parent_type = "OBJECT"
            obj.parent_bone = ""
            obj.parent = armature
            obj.matrix_world = world
            bpy.context.view_layer.update()
            group = obj.vertex_groups.new(name=bone)
            group.add(range(len(obj.data.vertices)), 1.0, "REPLACE")
            modifier = obj.modifiers.new("Armature", "ARMATURE")
            modifier.object = armature
    armature.data.pose_position = "POSE"
    bpy.context.view_layer.update()

    chosen = {}
    for game, prefs in PREFS.items():
        for name in ([own[game]] if game in own else []) + prefs:
            action = find_action(name)
            if action is not None:
                chosen[game] = action
                break
    if "Idle" not in chosen or "Attack" not in chosen:
        raise SystemExit(out + " has no idle or attack: " + str([a.name for a in bpy.data.actions]))
    used, seen = {}, set()
    for game, action in chosen.items():
        if action.name in seen:
            action = action.copy()
        seen.add(action.name)
        used[action] = game
    for action in list(bpy.data.actions):
        if action not in used:
            bpy.data.actions.remove(action)
    for action, game in used.items():
        action.name = "__" + game
    for action, game in used.items():
        action.name = game
    if armature.animation_data:
        armature.animation_data.action = None
        for track in list(armature.animation_data.nla_tracks):
            armature.animation_data.nla_tracks.remove(track)
    idle = bpy.data.actions["Idle"]
    if "Walk" not in chosen:
        idle.copy().name = "Walk"
    if "Death" not in chosen:
        sink(armature, idle)

    # The lowest point of its idle on the origin; a flyer a quarter of its height above.
    flatten(armature)
    low, high = extent(meshes, armature, idle)
    lift(armature, ((high - low) * 0.25 if flies else 0.0) - low)
    if not any(has_picture(s.material) for o in meshes for s in o.material_slots):
        palette(out, meshes, OUT)
    shrink_pictures()

    low, high = extent(meshes, armature, idle)
    tris = sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in meshes)
    bpy.ops.object.select_all(action="SELECT")
    path = os.path.join(OUT, out + ".glb")
    bpy.ops.export_scene.gltf(filepath=path, export_format="GLB", use_selection=True, export_yup=True,
                              export_apply=False, export_cameras=False, export_lights=False, export_extras=False,
                              export_morph=False, export_def_bones=False, export_skins=True,
                              export_animations=True, export_animation_mode="ACTIONS",
                              export_image_format="AUTO", export_materials="EXPORT")
    print("EXPORTED %s tris=%d foot=%.3f height=%.3f clips=%s bytes=%d"
          % (out, tris, low, high - low, sorted(a.name for a in bpy.data.actions), os.path.getsize(path)))
