// A .glb cut down to what the game draws: each material's base colour, nothing else.
//
//     node art/models/base_colour_only.js <in.glb> [out.glb]      (out defaults to in)
//
// The engine re-dresses every model in plain lighting over ONE picture of each piece -- the first texture jME's
// material hands it, and jME hands them in its own order, not the file's: NormalMap before BaseColorMap. So a piece
// that also carries a normal map was drawn in its normal map, pale lavender (Lange, 1.0.41), and the swords in their
// near-black emissive map. Until the engine asks for the base colour by name (E62), the game ships only that:
// normal, occlusion, metal/roughness and emissive maps are dropped, and the pictures and bytes nothing uses with them.
const fs = require('fs');

const [input, output = input] = process.argv.slice(2);
const glb = fs.readFileSync(input);
const jsonLength = glb.readUInt32LE(12);
const gltf = JSON.parse(glb.subarray(20, 20 + jsonLength).toString('utf8'));
const binChunk = 20 + jsonLength;
const bin = glb.subarray(binChunk + 8, binChunk + 8 + glb.readUInt32LE(binChunk));

for (const material of gltf.materials || []) {
    delete material.normalTexture;
    delete material.occlusionTexture;
    delete material.emissiveTexture;
    delete material.emissiveFactor;
    if (material.pbrMetallicRoughness) delete material.pbrMetallicRoughness.metallicRoughnessTexture;
}

// Textures, then images, still referred to; each list renumbered.
const keep = (list, used) => {
    const map = new Map();
    const kept = [];
    (list || []).forEach((item, at) => { if (used.has(at)) { map.set(at, kept.length); kept.push(item); } });
    return [kept, map];
};
const usedTextures = new Set((gltf.materials || [])
    .map(m => m.pbrMetallicRoughness && m.pbrMetallicRoughness.baseColorTexture)
    .filter(Boolean).map(t => t.index));
const [textures, textureAt] = keep(gltf.textures, usedTextures);
for (const m of gltf.materials || []) {
    const base = m.pbrMetallicRoughness && m.pbrMetallicRoughness.baseColorTexture;
    if (base) base.index = textureAt.get(base.index);
}
const [images, imageAt] = keep(gltf.images, new Set(textures.map(t => t.source)));
textures.forEach(t => { t.source = imageAt.get(t.source); });
gltf.textures = textures;
gltf.images = images;

// Buffer views still referred to, their bytes packed again at four-byte steps.
const viewRefs = [];
for (const a of gltf.accessors || []) {
    if (a.bufferView !== undefined) viewRefs.push([a, 'bufferView']);
    if (a.sparse) viewRefs.push([a.sparse.indices, 'bufferView'], [a.sparse.values, 'bufferView']);
}
for (const i of images) if (i.bufferView !== undefined) viewRefs.push([i, 'bufferView']);
const [views, viewAt] = keep(gltf.bufferViews, new Set(viewRefs.map(([o, k]) => o[k])));
const parts = [];
let offset = 0;
for (const view of views) {
    const bytes = bin.subarray(view.byteOffset || 0, (view.byteOffset || 0) + view.byteLength);
    view.byteOffset = offset;
    parts.push(bytes);
    offset += bytes.length;
    const pad = (4 - offset % 4) % 4;
    parts.push(Buffer.alloc(pad));
    offset += pad;
}
viewRefs.forEach(([o, k]) => { o[k] = viewAt.get(o[k]); });
gltf.bufferViews = views;
gltf.buffers = [{byteLength: offset}];

let json = Buffer.from(JSON.stringify(gltf), 'utf8');
json = Buffer.concat([json, Buffer.alloc((4 - json.length % 4) % 4, 0x20)]);
const newBin = Buffer.concat(parts);
const header = Buffer.alloc(12);
header.writeUInt32LE(0x46546C67, 0);
header.writeUInt32LE(2, 4);
header.writeUInt32LE(12 + 8 + json.length + 8 + newBin.length, 8);
const chunk = (type, data) => {
    const head = Buffer.alloc(8);
    head.writeUInt32LE(data.length, 0);
    head.writeUInt32LE(type, 4);
    return Buffer.concat([head, data]);
};
fs.writeFileSync(output, Buffer.concat([header, chunk(0x4E4F534A, json), chunk(0x004E4942, newBin)]));
console.log(`${output}: ${glb.length} -> ${12 + 16 + json.length + newBin.length} bytes, ${images.length} pictures`);
