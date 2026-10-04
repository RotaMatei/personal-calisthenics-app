#!/usr/bin/env python3
"""Builds core/src/main/resources/mesh/human.bin: the skinned human mesh the 3D clips and stills render.

Source: MakeHuman 1.x base mesh and targets (CC0), https://github.com/makehumancommunity/makehuman
        (sparse-checkout: makehuman/data/{3dobjs,rigs,targets/macrodetails,targets/torso,targets/armslegs}).

What this does
  1. applies the macro targets for a lean, muscular young male (plus a few muscle targets) to the base mesh;
  2. keeps the "body" group (the skin, no helper geometry), triangulated, in centimetres, feet on y = 0;
  3. collapses MakeHuman's 139 weighted bones onto the ~60 segments our rig can pose (torso, neck, head, clavicle,
     arm, forearm + twist, hand, 15 finger phalanges, thigh, shin, foot, per side);
  4. exports rest-pose landmarks (joint positions, palm normal, ...) from which the Kotlin side builds rest frames.

Usage: build_human.py <makehuman checkout dir> [out.bin]
File layout (big endian):  magic 'HUM1', nVerts, nTris, nBones, bone names (UTF), nLandmarks, landmarks (UTF + 3 floats),
vertices (float x3), triangles (uint16 x3), skin (4 x [bone uint8, weight uint8]) per vertex.
"""
import os
import struct
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from makehuman_body import Joints, load_body  # noqa: E402

TARGET_HEIGHT_CM = 177.0

# Extra muscle targets on top of the macro "male, muscular, lean" look: name -> weight.
EXTRA_TARGETS = {
    "torso/torso-muscle-dorsi-incr.target": 0.55,
    "torso/torso-muscle-pectoral-incr.target": 0.55,
    "torso/torso-vshape-incr.target": 0.45,
    "armslegs/l-upperarm-muscle-incr.target": 0.6,
    "armslegs/r-upperarm-muscle-incr.target": 0.6,
    "armslegs/l-upperarm-shoulder-muscle-incr.target": 0.6,
    "armslegs/r-upperarm-shoulder-muscle-incr.target": 0.6,
    "armslegs/l-lowerarm-muscle-incr.target": 0.4,
    "armslegs/r-lowerarm-muscle-incr.target": 0.4,
    "armslegs/l-upperleg-muscle-incr.target": 0.5,
    "armslegs/r-upperleg-muscle-incr.target": 0.5,
    "armslegs/l-lowerleg-muscle-incr.target": 0.4,
    "armslegs/r-lowerleg-muscle-incr.target": 0.4,
}

SIDES = ("P", "N")  # P = +x side (MakeHuman ".L" bones; the rig's "R"), N = -x side
BONES = ["lower", "upper", "neck", "head"]
for s in SIDES:
    BONES += [f"clav{s}", f"armUp{s}", f"fore{s}", f"twist{s}", f"hand{s}"]
    BONES += [f"f{k}{j}{s}" for k in range(1, 6) for j in range(1, 4)]
    BONES += [f"thigh{s}", f"shin{s}", f"foot{s}"]
BONE_INDEX = {n: i for i, n in enumerate(BONES)}

HEAD_PREFIXES = ("head", "jaw", "eye", "tongue", "levator", "oculi", "orbicularis", "oris", "risorius", "temporalis", "special")


def skin_bones(mh_name):
    """MakeHuman bone name -> list of (skin bone, share)."""
    base, side = (mh_name[:-2], mh_name[-1]) if mh_name.endswith((".L", ".R")) else (mh_name, None)
    s = {"L": "P", "R": "N"}.get(side, "")
    if base in ("root", "pelvis", "spine05", "spine04"):
        return [("lower", 1.0)]
    if base == "spine03":
        return [("lower", 0.5), ("upper", 0.5)]
    if base in ("spine02", "spine01", "breast"):
        return [("upper", 1.0)]
    if base.startswith("neck"):
        return [("neck", 1.0)]
    if base.startswith(HEAD_PREFIXES):
        return [("head", 1.0)]
    if base == "clavicle":
        return [("clav" + s, 1.0)]
    if base in ("shoulder01", "upperarm01", "upperarm02"):
        return [("armUp" + s, 1.0)]
    if base == "lowerarm01":
        return [("fore" + s, 1.0)]
    if base == "lowerarm02":
        return [("twist" + s, 1.0)]
    if base == "wrist" or base.startswith("metacarpal"):
        return [("hand" + s, 1.0)]
    if base.startswith("finger"):
        k, j = base[6:].split("-")
        return [(f"f{k}{j}{s}", 1.0)]
    if base in ("upperleg01", "upperleg02"):
        return [("thigh" + s, 1.0)]
    if base in ("lowerleg01", "lowerleg02"):
        return [("shin" + s, 1.0)]
    if base == "foot" or base.startswith("toe"):
        return [("foot" + s, 1.0)]
    raise ValueError("unmapped bone " + mh_name)


def collapse_weights(weights_json, n_verts):
    dense = np.zeros((n_verts, len(BONES)), dtype=np.float64)
    for mh_name, entries in weights_json["weights"].items():
        targets = skin_bones(mh_name)
        for v, w in entries:
            if v >= n_verts:
                continue
            for name, share in targets:
                dense[v, BONE_INDEX[name]] += w * share
    return dense


def top4(dense):
    n = dense.shape[0]
    idx = np.argsort(-dense, axis=1)[:, :4]
    w = np.take_along_axis(dense, idx, axis=1)
    sums = w.sum(axis=1, keepdims=True)
    sums[sums == 0] = 1.0
    w = w / sums
    q = np.floor(w * 255.0 + 0.5).astype(np.int64)
    # Make every vertex sum to exactly 255 by fixing the strongest influence.
    q[:, 0] += 255 - q.sum(axis=1)
    return idx.astype(np.uint8), q.astype(np.uint8)


def landmarks(V, J, skin_w, bone_w_threshold=0.5):
    """Rest-pose landmark dictionary (cm)."""
    L = {}

    def pt(name, p):
        L[name] = np.asarray(p, dtype=np.float64)

    pt("hipC", (J.head("upperleg01.L") + J.head("upperleg01.R")) / 2)
    pt("shoulderC", (J.head("upperarm01.L") + J.head("upperarm01.R")) / 2)
    pt("headPivot", J.head("head"))
    head_ids = np.where(skin_w[:, BONE_INDEX["head"]] > bone_w_threshold)[0]
    hv = V[head_ids]
    pt("headTop", (0.0, hv[:, 1].max(), hv[hv[:, 1] > hv[:, 1].max() - 1.0][:, 2].mean()))
    pt("chin", (0.0, hv[:, 1].min(), hv[hv[:, 1] < hv[:, 1].min() + 1.0][:, 2].mean()))
    pt("headC", (hv[:, 0].min() / 2 + hv[:, 0].max() / 2, (hv[:, 1].min() + hv[:, 1].max()) / 2, (hv[:, 2].min() + hv[:, 2].max()) / 2))
    for mh, s in (("L", "P"), ("R", "N")):
        sk = J.skel["bones"]
        pt(f"shoulder{s}", J.head(f"upperarm01.{mh}"))
        pt(f"elbow{s}", J.head(f"lowerarm01.{mh}"))
        pt(f"wrist{s}", J.head(f"wrist.{mh}"))
        pt(f"knuckle{s}", J.head(f"finger3-1.{mh}"))
        pt(f"hip{s}", J.head(f"upperleg01.{mh}"))
        pt(f"knee{s}", J.head(f"lowerleg01.{mh}"))
        pt(f"ankle{s}", J.head(f"foot.{mh}"))
        pt(f"toeBall{s}", J.head(f"toe3-1.{mh}"))
        for k in range(1, 6):
            for j in range(1, 4):
                pt(f"f{k}{j}{s}", J.head(f"finger{k}-{j}.{mh}"))
            pt(f"f{k}4{s}", J.tail(f"finger{k}-3.{mh}"))
        foot_ids = np.where(skin_w[:, BONE_INDEX["foot" + s]] > bone_w_threshold)[0]
        fv = V[foot_ids]
        pt(f"heel{s}", (fv[fv[:, 2] < fv[:, 2].min() + 1.0][:, 0].mean(), fv[:, 1].min(), fv[:, 2].min()))
        pt(f"toeTip{s}", (fv[fv[:, 2] > fv[:, 2].max() - 1.0][:, 0].mean(), fv[:, 1].min(), fv[:, 2].max()))
        # Palm normal: the side the middle finger curls toward, made perpendicular to the hand direction.
        d = L[f"knuckle{s}"] - L[f"wrist{s}"]
        d /= np.linalg.norm(d)
        curl = (L[f"f33{s}"] - L[f"f31{s}"])
        curl -= d * curl.dot(d)
        # Use the whole hand's finger curl, which is steadier than one finger.
        c2 = sum((L[f"f{k}3{s}"] - L[f"f{k}1{s}"]) for k in (2, 3, 4, 5))
        c2 -= d * c2.dot(d)
        n = c2 / np.linalg.norm(c2)
        pt(f"palmN{s}", n)
    return L


def main():
    mh_dir = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "..", "core", "src", "main", "resources", "mesh", "human.bin")
    V, groups, skel, wts = load_body(mh_dir, extra_targets=EXTRA_TARGETS, gender=1.0, muscle=0.75, weight=0.35)
    quads = np.array(groups["body"], dtype=np.int64)
    n_verts = int(quads.max()) + 1
    assert set(np.unique(quads)) == set(range(n_verts)), "body vertices are not contiguous"

    dense = collapse_weights(wts, n_verts)
    J = Joints(V, skel)
    assert J.head("upperarm01.L")[0] > 0, "left bones are expected on +x"

    # Scale to the rig's height and stand the feet on y = 0, centred on the hips in x.
    body = V[:n_verts]
    height = body[:, 1].max() - body[:, 1].min()
    g = TARGET_HEIGHT_CM / height
    print(f"morphed height {height:.1f} cm -> scale {g:.4f}")
    V = V * g
    J = Joints(V, skel)
    body = V[:n_verts]
    shift = np.array([0.0, -body[:, 1].min(), 0.0])
    V = V + shift
    J = Joints(V, skel)
    body = V[:n_verts]

    lm = landmarks(body, J, dense)
    for k in ("hipC", "shoulderC", "headPivot", "headTop", "chin", "headC", "shoulderP", "elbowP", "wristP", "knuckleP",
              "hipP", "kneeP", "ankleP", "toeBallP", "heelP", "toeTipP", "palmNP"):
        print(f"  {k:10s} {np.round(lm[k], 2)}")

    tris = np.concatenate([quads[:, [0, 1, 2]], quads[:, [0, 2, 3]]])
    bone_idx, bone_w = top4(dense)

    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(struct.pack(">4s", b"HUM1"))
        f.write(struct.pack(">iii", n_verts, len(tris), len(BONES)))
        for name in BONES:
            b = name.encode("utf-8")
            f.write(struct.pack(">H", len(b)) + b)
        f.write(struct.pack(">i", len(lm)))
        for name, p in lm.items():
            b = name.encode("utf-8")
            f.write(struct.pack(">H", len(b)) + b)
            f.write(struct.pack(">fff", *[float(x) for x in p]))
        f.write(body.astype(">f4").tobytes())
        f.write(tris.astype(">u2").tobytes())
        inter = np.empty((n_verts, 8), dtype=np.uint8)
        inter[:, 0::2] = bone_idx
        inter[:, 1::2] = bone_w
        f.write(inter.tobytes())
    print(f"wrote {out_path}: {os.path.getsize(out_path) / 1024:.0f} KiB, {n_verts} vertices, {len(tris)} triangles, {len(BONES)} bones")


if __name__ == "__main__":
    main()
