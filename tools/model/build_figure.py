#!/usr/bin/env python3
"""
Bereitet das 3D-Mannequin für die App auf.

Eingabe:  Xbot.glb (Mixamo „X Bot“, aus dem three.js-Repository:
          https://github.com/mrdoob/three.js/blob/dev/examples/models/gltf/Xbot.glb)
Ausgabe:  app/src/main/assets/figure3d/figure.glb   – Figur ohne Animationen, Muskelregionen als eigene Materialien
          app/src/main/assets/figure3d/box.glb      – Einheitswürfel für Requisiten
          app/src/main/assets/figure3d/cylinder.glb – Einheitszylinder für Requisiten

Die Figur wird nicht mit fertigen Clips animiert: Die App steuert das Skelett pro Frame direkt aus
den Keyframe-Posen der Übungen (siehe app/.../figure3d/Rig3D.kt).

Aufruf:  python3 tools/model/build_figure.py pfad/zu/Xbot.glb
Benötigt: pip install pygltflib numpy
"""
import sys
import os
import numpy as np
from pygltflib import (GLTF2, Accessor, Asset, Buffer, BufferView, Material, Mesh, Node, PbrMetallicRoughness,
                       Primitive, Attributes, Scene)

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "assets", "figure3d")

COMPONENT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def read(g, blob, index):
    acc = g.accessors[index]
    bv = g.bufferViews[acc.bufferView]
    dtype = np.dtype(COMPONENT[acc.componentType])
    width = WIDTH[acc.type]
    start = (bv.byteOffset or 0) + (acc.byteOffset or 0)
    stride = bv.byteStride or dtype.itemsize * width
    if stride == dtype.itemsize * width:
        data = np.frombuffer(blob, dtype=dtype, count=acc.count * width, offset=start)
    else:
        data = np.concatenate([np.frombuffer(blob, dtype=dtype, count=width, offset=start + i * stride) for i in range(acc.count)])
    return data.reshape(acc.count, width).copy()


class Writer:
    """Baut ein GLB mit genau den Daten, die gebraucht werden."""

    def __init__(self):
        self.g = GLTF2(asset=Asset(version="2.0", generator="FitStreak build_figure.py"))
        self.blob = bytearray()

    def add(self, array, kind, target=None, minmax=False):
        array = np.ascontiguousarray(array)
        while len(self.blob) % 4:
            self.blob.append(0)
        offset = len(self.blob)
        self.blob.extend(array.tobytes())
        self.g.bufferViews.append(BufferView(buffer=0, byteOffset=offset, byteLength=array.nbytes, target=target))
        ctype = {v: k for k, v in COMPONENT.items()}[array.dtype.type]
        acc = Accessor(bufferView=len(self.g.bufferViews) - 1, componentType=ctype, count=len(array), type=kind)
        if minmax:
            acc.min = array.min(axis=0).tolist()
            acc.max = array.max(axis=0).tolist()
        self.g.accessors.append(acc)
        return len(self.g.accessors) - 1

    def material(self, name, color, metallic=0.0, roughness=0.75):
        self.g.materials.append(Material(name=name, pbrMetallicRoughness=PbrMetallicRoughness(
            baseColorFactor=list(color), metallicFactor=metallic, roughnessFactor=roughness)))
        return len(self.g.materials) - 1

    def save(self, name):
        while len(self.blob) % 4:
            self.blob.append(0)
        self.g.buffers = [Buffer(byteLength=len(self.blob))]
        self.g.set_binary_blob(bytes(self.blob))
        path = os.path.join(OUT, name)
        self.g.save_binary(path)
        print(f"{name}: {os.path.getsize(path) / 1024:.0f} kB")


def region_of(joint, c, n):
    """Ordnet ein Dreieck (dominantes Gelenk, Schwerpunkt, Normale in der T-Pose) einer Muskelregion zu."""
    x, y, z = c
    front, back = n[2] > 0.25, n[2] < -0.25
    if joint == "Spine2":
        return "CHEST" if front else "UPPER_BACK"
    if joint in ("Spine1", "Spine"):
        if n[2] > 0.35:
            return "ABS"
        return "LOWER_BACK" if n[2] < -0.35 else "OBLIQUES"
    if joint == "Hips":
        if back and y < 1.08:
            return "GLUTES"
        return "ABS" if front and y > 1.0 else "BODY"
    if joint in ("LeftShoulder", "RightShoulder"):
        if abs(x) > 0.11:
            return "SHOULDER"
        return "UPPER_BACK" if n[2] < 0 else "CHEST"
    if joint in ("LeftArm", "RightArm"):
        t = (abs(x) - 0.152) / 0.278
        if t < 0.34:
            return "SHOULDER"
        # nach Lage relativ zum Knochen (nicht nach Normale) – ergibt zwei saubere, symmetrische Hälften
        return "BICEPS" if z >= -0.047 else "TRICEPS"
    if joint in ("LeftUpLeg", "RightUpLeg"):
        if y > 0.86 and z < -0.005:
            return "GLUTES"
        return "QUADS" if z >= -0.008 else "HAMSTRINGS"
    if joint in ("LeftLeg", "RightLeg"):
        return "CALVES" if z < -0.012 and y > 0.24 else "BODY"
    return "BODY"


def build_figure(src):
    g = GLTF2().load(src)
    blob = g.binary_blob()
    skin = g.skins[0]
    joint_names = [g.nodes[j].name.replace("mixamorig:", "") for j in skin.joints]

    w = Writer()
    # Knoten und Skin unverändert übernehmen (ohne Animationen)
    w.g.nodes = g.nodes
    w.g.scenes = g.scenes
    w.g.scene = g.scene
    ibm = w.add(read(g, blob, skin.inverseBindMatrices).astype(np.float32), "MAT4")
    skin.inverseBindMatrices = ibm
    w.g.skins = [skin]

    counts = {}
    for mesh_index, mesh in enumerate(g.meshes):
        prim = mesh.primitives[0]
        prefix = "j" if "Joints" in g.materials[prim.material].name else "s"
        base_color = (0.20, 0.20, 0.22, 1.0) if prefix == "j" else (0.80, 0.80, 0.82, 1.0)
        pos = read(g, blob, prim.attributes.POSITION).astype(np.float32)
        nor = read(g, blob, prim.attributes.NORMAL).astype(np.float32)
        joints = read(g, blob, prim.attributes.JOINTS_0)
        weights = read(g, blob, prim.attributes.WEIGHTS_0).astype(np.float32)
        idx = read(g, blob, prim.indices).reshape(-1, 3).astype(np.uint32)

        # dominantes Gelenk je Dreieck
        per_vertex = np.zeros((len(pos), len(joint_names)), dtype=np.float32)
        for k in range(4):
            np.add.at(per_vertex, (np.arange(len(pos)), joints[:, k]), weights[:, k])
        tri_weights = per_vertex[idx[:, 0]] + per_vertex[idx[:, 1]] + per_vertex[idx[:, 2]]
        dominant = tri_weights.argmax(axis=1)
        centroid = pos[idx].mean(axis=1)
        normal = nor[idx].mean(axis=1)
        normal /= np.maximum(np.linalg.norm(normal, axis=1, keepdims=True), 1e-6)

        regions = {}
        for t in range(len(idx)):
            r = region_of(joint_names[dominant[t]], centroid[t], normal[t])
            regions.setdefault(r, []).append(idx[t])

        attributes = Attributes(
            POSITION=w.add(pos, "VEC3", target=34962, minmax=True),
            NORMAL=w.add(nor, "VEC3", target=34962),
            JOINTS_0=w.add(joints.astype(np.uint8 if joints.max() < 256 else np.uint16), "VEC4", target=34962),
            WEIGHTS_0=w.add(weights, "VEC4", target=34962),
        )
        primitives = []
        for region, tris in sorted(regions.items()):
            tri = np.array(tris, dtype=np.uint32).reshape(-1)
            index_type = np.uint16 if len(pos) < 65536 else np.uint32
            material = w.material(f"{prefix}_{region}", base_color, metallic=0.0, roughness=0.6 if prefix == "j" else 0.72)
            primitives.append(Primitive(attributes=attributes, indices=w.add(tri.astype(index_type).reshape(-1, 1), "SCALAR", target=34963), material=material))
            counts[f"{prefix}_{region}"] = len(tris)
        w.g.meshes.append(Mesh(name=mesh.name, primitives=primitives))
    print("Dreiecke je Region:", counts)
    w.save("figure.glb")


def build_primitive(name, pos, nor, idx):
    w = Writer()
    attributes = Attributes(POSITION=w.add(pos.astype(np.float32), "VEC3", target=34962, minmax=True),
                            NORMAL=w.add(nor.astype(np.float32), "VEC3", target=34962))
    material = w.material("prop", (0.45, 0.45, 0.48, 1.0), metallic=0.0, roughness=0.6)
    w.g.meshes = [Mesh(name=name, primitives=[Primitive(attributes=attributes, indices=w.add(idx.astype(np.uint16).reshape(-1, 1), "SCALAR", target=34963), material=material)])]
    w.g.nodes = [Node(name=name, mesh=0)]
    w.g.scenes = [Scene(nodes=[0])]
    w.g.scene = 0
    w.save(f"{name}.glb")


def build_box():
    """Einheitswürfel: x, z von -0,5 bis 0,5, y von 0 bis 1 (steht auf dem Boden)."""
    faces = [((0, 0, 1), (1, 0, 0), (0, 1, 0)), ((0, 0, -1), (-1, 0, 0), (0, 1, 0)), ((1, 0, 0), (0, 0, -1), (0, 1, 0)),
             ((-1, 0, 0), (0, 0, 1), (0, 1, 0)), ((0, 1, 0), (1, 0, 0), (0, 0, -1)), ((0, -1, 0), (1, 0, 0), (0, 0, 1))]
    pos, nor, idx = [], [], []
    for n, u, v in faces:
        n, u, v = np.array(n, float), np.array(u, float), np.array(v, float)
        base = len(pos)
        for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            p = (n + a * u + b * v) * 0.5
            pos.append([p[0], p[1] + 0.5, p[2]])
            nor.append(n)
        idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    build_primitive("box", np.array(pos), np.array(nor), np.array(idx))


def build_cylinder(segments=24):
    """Einheitszylinder: Achse = y von -0,5 bis 0,5, Radius 0,5."""
    pos, nor, idx = [], [], []
    for i in range(segments + 1):
        a = 2 * np.pi * i / segments
        c, s = np.cos(a), np.sin(a)
        pos += [[0.5 * c, -0.5, 0.5 * s], [0.5 * c, 0.5, 0.5 * s]]
        nor += [[c, 0, s], [c, 0, s]]
    for i in range(segments):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 2, 2 * i + 3
        idx += [a, b, c, c, b, d]
    for y, ny in ((-0.5, -1), (0.5, 1)):
        center = len(pos)
        pos.append([0, y, 0]); nor.append([0, ny, 0])
        ring = len(pos)
        for i in range(segments + 1):
            a = 2 * np.pi * i / segments
            pos.append([0.5 * np.cos(a), y, 0.5 * np.sin(a)]); nor.append([0, ny, 0])
        for i in range(segments):
            idx += [center, ring + i + 1, ring + i] if ny > 0 else [center, ring + i, ring + i + 1]
    build_primitive("cylinder", np.array(pos), np.array(nor), np.array(idx))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    build_figure(sys.argv[1])
    build_box()
    build_cylinder()
