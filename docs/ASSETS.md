# Third-party assets

## Human body mesh (MakeHuman, CC0)

The 3D figure in the clips and pictures is a real, detailed human mesh, not geometry drawn by this project.

- **Source:** the MakeHuman 1.x community project, <https://github.com/makehumancommunity/makehuman>
  (commit `a8bc2d54ff0ac92e78ff71431b1023eda42bf482`).
- **Licence of the assets:** Creative Commons CC0 1.0 Universal (public-domain dedication), see `LICENSE.ASSETS.md` in that
  repository. No attribution is required; it is given here anyway.
- **Files used:** `makehuman/data/3dobjs/base.obj` (base mesh), `makehuman/data/targets/macrodetails/*` and a few
  `targets/torso/*` and `targets/armslegs/*` muscle targets (the lean, muscular male shape), and
  `makehuman/data/rigs/default.mhskel` plus `default_weights.mhw` (skeleton and skin weights).
- **What this project did with them:** `tools/mesh/build_human.py` applies the targets, scales the body to 177 cm, collapses
  the 139 MakeHuman bones onto the 50 skin bones of this app's rig (torso, neck, head, clavicles, arms with forearm twist,
  hands, 15 finger bones per hand, legs, feet) and writes one compact binary,
  `core/src/main/resources/mesh/human.bin` (about 420 KB, 13,380 vertices, 26,756 triangles). The app opens it as the asset `human.bin`.
- **How it is drawn:** `core/.../mesh/` skins the mesh with the pose the rig solves for every frame (so every exercise, bar
  and prop keeps working), shades it as uncoloured clay and tints it with the soft muscle / tendon / joint hues. If the
  mesh cannot be loaded the app falls back to the older capsule figure.
- **Regenerating it:**

      git clone --depth 1 --filter=blob:none --sparse https://github.com/makehumancommunity/makehuman mh
      git -C mh sparse-checkout set makehuman/data/3dobjs makehuman/data/rigs makehuman/data/targets/macrodetails \
          makehuman/data/targets/torso makehuman/data/targets/armslegs
      python3 tools/mesh/build_human.py mh      # needs numpy

- **Look:** a nude clay mannequin with a smooth crotch (no genitals), no hair, no eyes. Hidden stress patches (the old
  "x-ray" view) are not drawn on the mesh; orbit the camera to see them.
