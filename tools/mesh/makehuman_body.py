"""Loads the MakeHuman 1.x base mesh (CC0), applies the macro targets for a lean athletic male and exposes the
skeleton / skin weights the rest of the converter needs.

Everything here is plain numpy; no MakeHuman code is used, only its CC0 data files:
  makehuman/data/3dobjs/base.obj                      quad mesh with named groups (the "body" group is the skin)
  makehuman/data/targets/macrodetails/*.target        sparse vertex offsets ("index dx dy dz" per line)
  makehuman/data/rigs/default.mhskel                  bones (head / tail = mean of vertex groups)
  makehuman/data/rigs/default_weights.mhw             vertex weights of every bone

MakeHuman units are decimetres; the loader returns centimetres.
"""
from __future__ import annotations

import collections
import json
import os

import numpy as np

DM_TO_CM = 10.0


def read_obj(path):
    verts = []
    groups = collections.OrderedDict()
    cur = None
    with open(path) as fh:
        for line in fh:
            p = line.split()
            if not p:
                continue
            if p[0] == "v":
                verts.append((float(p[1]), float(p[2]), float(p[3])))
            elif p[0] == "g":
                cur = p[1]
                groups.setdefault(cur, [])
            elif p[0] == "f":
                groups[cur].append([int(x.split("/")[0]) - 1 for x in p[1:]])
    return np.array(verts, dtype=np.float64), groups


def read_target(path):
    idx, delta = [], []
    with open(path) as fh:
        for line in fh:
            if not line.strip() or line.startswith("#"):
                continue
            p = line.split()
            idx.append(int(p[0]))
            delta.append((float(p[1]), float(p[2]), float(p[3])))
    return np.array(idx, dtype=np.int64), np.array(delta, dtype=np.float64).reshape(-1, 3)


def macro_weights(gender=1.0, age_young=1.0, muscle=0.75, weight=0.35, ethnic=(1 / 3, 1 / 3, 1 / 3)):
    """Weights of the macro target files for the given slider values (0..1), following MakeHuman's own formulas."""

    def three(v):
        lo = max(0.0, 1.0 - 2.0 * v)
        hi = max(0.0, 2.0 * v - 1.0)
        return {"min": lo, "average": 1.0 - lo - hi, "max": hi}

    out = {}
    mus, wei = three(muscle), three(weight)
    sexes = {"male": gender, "female": 1.0 - gender}
    for sex, ws in sexes.items():
        if ws <= 0:
            continue
        for m, wm in mus.items():
            for w, ww in wei.items():
                k = ws * age_young * wm * ww
                if k > 0:
                    out[f"universal-{sex}-young-{m}muscle-{w}weight"] = k
        for eth, we in zip(("african", "asian", "caucasian"), ethnic):
            k = we * ws * age_young
            if k > 0:
                out[f"{eth}-{sex}-young"] = k
    return out


def load_body(mh_dir, extra_targets=None, **macro):
    """Returns (vertices_cm [19158,3], groups, skeleton json, weights json)."""
    data = os.path.join(mh_dir, "makehuman", "data")
    verts, groups = read_obj(os.path.join(data, "3dobjs", "base.obj"))
    for name, k in macro_weights(**macro).items():
        i, d = read_target(os.path.join(data, "targets", "macrodetails", name + ".target"))
        verts[i] += d * k
    for rel, k in (extra_targets or {}).items():
        i, d = read_target(os.path.join(data, "targets", rel))
        verts[i] += d * k
    verts *= DM_TO_CM
    skel = json.load(open(os.path.join(data, "rigs", "default.mhskel")))
    weights = json.load(open(os.path.join(data, "rigs", "default_weights.mhw")))
    return verts, groups, skel, weights


class Joints:
    """Joint lookups of the default skeleton on a (possibly morphed) vertex array."""

    def __init__(self, verts, skel):
        self.v = verts
        self.skel = skel

    def point(self, key):
        return self.v[self.skel["joints"][key]].mean(axis=0)

    def head(self, bone):
        return self.point(self.skel["bones"][bone]["head"])

    def tail(self, bone):
        return self.point(self.skel["bones"][bone]["tail"])
