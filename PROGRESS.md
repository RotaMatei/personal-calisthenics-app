# Progress checklist (read me first when resuming)

Project: **Calisthenics Park & Tendon Guide**, personal-use Android app (Kotlin, Compose, Room).
Source spec: see `docs/SPEC.md`. Workspace limits: no Gradle/Maven/Google downloads (egress policy),
so `:app` cannot be compiled in the Claude workspace. `:core` is pure Kotlin and is compiled and tested with
`tools/run-core-tests.sh` (downloads kotlinc from GitHub into a scratch dir). CI (`.github/workflows/android.yml`)
builds everything once pushes work.

## How to resume
1. Attach the repo (`add_repo` RotaMatei/personal-calisthenics-app) and clone it.
2. Pick the first unchecked item below. Do the work, run `tools/run-core-tests.sh`, commit, push.
3. If CI results are available (`gh run list`, `gh run view --log-failed`), fix failures first.
4. When every box is checked and CI is green, stop and report.

## Milestones
- [x] M0 Gradle scaffold, version catalog, CI workflow
- [x] M1 `:core` models + complete seed data (exercises, steps, tempos, highlights, cues, DO/DON'T, ladders)
- [x] M2 `:core` logic: day rotation, deload, session planner/sequencer, superset mode, smart rest
- [x] M3 `:core` timers: TimelineTimer, isometric get-ready, tempo, rest, flow; cue to beep/haptic mapping
- [x] M4 `:core` analytics: recovery window, weekly volume guard (+15%), streak, heatmap, joint advice, progression gating
- [x] M5 `:core` 3D body rig (IK, projections, highlights) + PNG preview tool (`tools/render-rig.sh`), poses for every
      exercise (all 27 animations and every DO/DON'T still, basic capsule-mannequin fidelity; fidelity upgrades are M5b-M5d)
- [x] M5b **User requirement (added 2026-10-03): exercise visualizations are short looping 3D-rendered clips of a
      humanoid doing the exercise, not static pictures.** Plan: perspective camera with yaw/pitch (default 3/4 view,
      slow auto-orbit, drag to rotate, tap to switch to the spec's side/front views) over the existing 3D-authored rig;
      shaded capsule/sphere "impostor" bodies with depth-correct painter's sorting; the clip plays the full rep
      (keyframes already loop) at the exercise's real tempo. Implemented in `:core` (projection + shading math,
      testable) and drawn by the Compose Canvas in M11. The local GIF/MP4 override from the spec stays as an option.
- [x] M5c **User requirement (added 2026-10-03): tension-point colouring must match the real stress of that exact
      exercise.** Keep the spec colours (muscle red, tendon blue, joint yellow) but add per-highlight load level
      (primary / secondary / minor, drawn as opacity) and the rep phase where the load peaks (e.g. distal biceps tendon
      peaks in the bottom hang of a pull-up, patellar tendon at the bottom of a pistol squat), so the colours pulse with
      the clip. Audit every exercise's highlights against its real biomechanics and record the rationale per exercise in
      `docs/STRESS_MAP.md`. DONE 2026-10-03: `Highlight.load` (Primary/Secondary/Minor) + `Highlight.peak` (Steady/A/B),
      `RigAnimation.progressAt`, soft-edged patches, all 27 exercises audited, `StressMapTest`. Caveat to state in the app: this is a general biomechanics / rehab-literature mapping, not
      measured data for this user, and not medical advice.
- [x] M5d **User requirement (added 2026-10-03): the 3D models must be more detailed for every exercise.** Example
      from the user: the hand-on-bar grip close-up is not understandable (cannot tell which parts are fingers). The current
      figure is a simple capsule mannequin and the grip art is a crude schematic. To do: (1) a detailed humanoid for the
      clips: tapered torso with chest/pelvis/abdomen shapes, neck, jaw/face, shoulders with deltoid bulge, upper arm and
      forearm taper, articulated hands with four separate fingers (3 segments each) plus a thumb, feet with heel/arch/toes,
      knees and elbows as visible joints; (2) per-exercise detail views where the contact matters (grip close-ups for the
      hook grip vs palm-crease grip, wrist angle for the wrist drills, hand turn-out for planche work, foot position for
      squats), drawn as a proper 3D hand with labelled callouts (fingers, thumb, bar, palm crease); (3) labels and arrows on
      the DO/DON'T art so each picture can be read without guessing; (4) review every exercise sheet by eye for clarity.
- [x] M6 `:core` unit tests for M1-M5 (73 tests passing via tools/run-core-tests.sh; add tests for M5b-M5d as they land)
- [x] M7 `:app` data layer (Room entities, DAOs, repository, seeding of user state)
- [x] M8 `:app` feedback (audio ducking beeps, haptics) + foreground service + wake lock + session controller
      (pure `SessionEngine` state machine in `:core` with 8 tests; `CueFeedback`, `SessionRunner`, `WorkoutService` in `:app`.
      Notes for M9/M10: request POST_NOTIFICATIONS at runtime before `SessionRunner.start`; set FLAG_KEEP_SCREEN_ON on the
      workout screen; observe `app.sessionRunner.state`.)
- [x] M9 `:app` theme + navigation + Tab 1 Dashboard (written 2026-10-03; verify the CI compile before relying on it)
- [x] M10 `:app` Tab 2 Workout Player (flow, isometric, tempo, rest, set logging, cold mode, finish + joint log) (written 2026-10-03; CI compile pending)
- [x] M11 `:app` Tab 3 Guide (3D clip canvas from M5b with M5c stress colouring, media override, DO/DON'T, progression matrix) (written 2026-10-03; CI compile pending)
- [x] M12 `:app` Tab 4 Stats (heatmap, safety guard, charts, joint history) (written 2026-10-03; CI compile pending)
- [x] M13 Spec audit against `docs/SPEC.md`, fix gaps, README with build/install steps (audit 2026-10-03: added beep/vibration
      toggles, weekday label, keep-screen-on for the whole session, abandoned-session cleanup; all spec sections covered)
- [x] M14 CI green (needs GitHub push access) and debug APK artifact produced (green on 2026-10-03: run 37161308156, artifact calisthenics-debug-apk)

## Round 2: user feedback after testing the first APK (2026-10-04) - BINDING requirements
The user tested the app on a phone: it runs and moves smoothly, but it needs these improvements. Work them in order.
Claim rule: an interactive session started on these at 2026-10-04 09:35 (Europe/Bucharest). A scheduled run should skip
milestones marked `(claimed)` unless `git log origin/main -1` is more than 2 hours old, then take over the first unchecked one.

- [x] M15 **Rig animation fixes** (DONE 2026-10-04: pull-up pole fixed; Pose has headRoll/headTurn/sideLean/sideFlex; joint_circles is a 30 s clip of five 6 s captioned parts; `RigMotionTest` guards bend-direction flips, elbow flexion, pull-up elbow below hand, all five captions; the clip caption is drawn by `ClipView`; session screens must drive the clip from drill time so it matches the 5 sub-labels, see M19). (a) Elbows do not bend in the pull-up clip, and the same is true for every
      exercise where the elbows should flex (pull-ups, chin-ups, Australian pull-ups, dips, push-up variants, pike and
      pseudo-planche push-ups, ...). Audit every exercise's keyframes, make the elbow angle really change between Position A
      and B, and add a test that fails when a bending-elbow exercise has no elbow flexion. (b) Warm-up/drill clips that cover
      several rotations (joint circles: neck, shoulders, elbows, wrists, ...) currently show only one of them. The clip must
      play every rotation in turn, with the current joint named on screen.
- [x] M16 **Visual quality of the 3D figure** (DONE 2026-10-04: tension is now `WashPrim` soft rose/teal/sand gradients clipped to the body part outline, no blobs, far-side tension shown as a faint see-through wash; form shading = per-primitive light/shadow gradients (`Shading`), torso shaded as one volume; new face, hair, muscle-definition washes (`Anatomy`), tapered limbs; `FigureLookTest`; Compose `RigDrawing` + preview tool implement it, Android path verified by CI only). (a) Targeted joints and muscles must NOT be painted as yellow or red
      blobs. Show the tension as a soft hue wash over the model's body surface instead (graded intensity, still pulsing with
      the movement phase, still matched to the real stress of that exact exercise per M5c). USER DECISION (2026-10-04): three
      muted hues as a gentle wash over the body, soft rose = muscle, soft teal = tendon, soft sand = joint (no saturated
      red/yellow/blue, no blobs on joints). (b) Make the figure more realistic:
      better shading and proportions, muscle definition, clothing, face and hair. Limit: it is rendered by our own Canvas
      painter (no 3D engine, no downloadable models in this workspace), so "realistic" means as far as that allows.
- [ ] M17 (claimed) **UI redesign.** The UI feels cluttered and badly spaced. Redo all four tabs with a consistent spacing scale,
      fewer simultaneous cards, clearer hierarchy. Use a calmer, less contrasty dark palette: the saturated yellow and blue hurt
      the user's eyes, so use muted tones (soft, desaturated accents) everywhere, including charts and the heatmap.
- [ ] M18 (claimed) **Workout details page.** Pressing a workout must NOT start it. It opens a details page that lists every
      exercise with the exact number of sets and reps (or hold seconds) and the rest in minutes. Each row shows the picture of
      the correct position; pressing it extends a drawer-like panel that shows the looping 3D video instead, with the reps
      and the description under it. The Start button lives on this page.
- [ ] M19 (claimed) **Rep-exercise player screen.** For every exercise counted in reps (no duration), the whole screen shows the
      3D video and a Next button sits at the bottom. USER DECISIONS (2026-10-04): this applies to strength sets (Phase 2)
      only; warm-up/decompression drills keep their timers and 10 s transitions. Tempo beeps and vibration KEEP running on
      this screen (the clip loops at the set's tempo), Next ends the set and leads to a one-tap pre-filled reps/RIR log, then rest.
- [ ] M20 (claimed) **New logo.** A better, more suitable app icon (adaptive launcher icon plus the notification icon) that
      fits calisthenics and tendon health. Keep it simple and readable at small sizes.
- Root causes found 2026-10-04 (for M15, all fixed; the 'snap' numbers below were partly fast real motion, the test now checks the bend direction instead): (1) pull-up elbow flips: the fixed elbow "pole" (BACK_DOWN) throws the elbow up
  and behind the head during the ascent (elbow above the wrist at t~1.3-1.6 s) and then snaps below it; need a per-pose,
  body-relative elbow direction that cannot flip, plus a continuity test (`tools` probe: max per-10 ms jump, elbows of
  strict_pullups 7.1 cm, joint_circles 11.7 cm, jumping_jacks 7.5 cm, banded_dislocates 3.1 cm). (2) joint_circles is a
  single arm-swing loop; the Pose model has no neck roll/turn, hip circle or ankle circle, so those must be added and the
  clip sequenced through all five rotations (neck half circles, shoulders, elbows, hips, ankles) with a caption.
- [ ] M22 (claimed) **Real 3D human figure (added 2026-10-04 10:26, BINDING, supersedes the capsule-built body of M5d/M16).**
      The user: "please use real 3D assets of humans. They don't have to be coloured even, just detailed." Replace the
      procedurally built figure with a real, detailed human mesh. Asset: the MakeHuman base mesh with its default skeleton and
      skin weights (CC0 per `LICENSE.ASSETS.md` of github.com/makehumancommunity/makehuman, sparse clone of
      `makehuman/data/3dobjs/base.obj`, `makehuman/data/rigs/default.mhskel`, `default_weights.mhw`; the workspace cannot reach
      raw.githubusercontent.com, only normal git clones from github.com). Plan: (1) offline Python tool in `tools/mesh/` that
      keeps the skin ("body") group, triangulates, decimates to a phone-friendly size, collapses the MakeHuman bone weights onto
      our ~16 rig segments and writes one compact binary under `core/src/main/resources/`; (2) `:core` loads it, skins it with
      the `Skeleton` that `RigSolver` already solves (so every existing animation, pole vector, bar and prop keeps working),
      and shades it as plain matte clay/marble (colour is not needed, detail is); (3) a CPU z-buffer rasteriser in `:core`
      draws mesh + props into one image that Compose and the preview tool both show, so occlusion is correct; (4) tension is
      still the soft muted hue wash, now projected over the mesh regions; (5) tests: mesh loads, weights sum to 1, posed mesh
      stays attached to the skeleton, no stretched triangles in the 20 exercise poses; preview renders checked by eye;
      (6) credit the asset in the README. Keep `RigRenderer` (capsule figure) as a fallback until the mesh path is verified.
- [ ] M21 Re-audit against these requirements and `docs/SPEC.md`, update tests and README, CI green, new debug APK artifact.

## Auto-resume
- Scheduled task "Calisthenics app auto-resume" (trig_01NAmLe2tPTDAtFJZ7zbnBqD) starts a fresh session hourly at :48 and
  follows this file. GitHub pushes work (App access confirmed 2026-10-03); CI "Core unit tests" passes under real Gradle,
  "Build debug APK" fails until the :app sources exist. CI logs cannot be downloaded (egress); use
  `gh run view <id> --json jobs` and `gh api repos/RotaMatei/personal-calisthenics-app/check-runs/<job id>/annotations`.

## Status of M5b / M5d (2026-10-03) - DONE; contact sheets reviewed by eye 2026-10-03 (pull-up, pistol, planche, L-sit OK; labelled DO/DON'T present). M7 data layer compiled green in CI.
- Done: 3D rig pipeline (orbit camera, painter, ellipsoid body, 4-finger hands with bar wrap, grip close-ups), bar/floor hand
  contact re-authored, smooth torso, graded highlights.
- Still open: eyeball every exercise from 3/4, side and front once more (pull-up WRONG poses were authored for the old bar
  geometry), labels/arrows on DO/DON'T art, auto-orbit helper + stable framing + tests for M5b, then the `:app` milestones.

## Design for M5b/M5d (detailed 3D figure) - in progress
- Pose gets real hand/foot frames (finger direction, palm normal, toe-out yaw) and hand shapes (relaxed, flat, fist,
  fingertips, bar hook). Figure = ellipsoid torso/head/shoe blobs + tapered limb tubes + articulated hands (4 fingers x 3
  segments + thumb). Camera = yaw/pitch orbit with optional perspective; SIDE and FRONT stay as presets.
- Highlights become depth-sorted blobs on the body (hidden when behind it) plus a faint x-ray pass, intensity = load x phase.
- Bars: wrist target is derived from the bar centre and the hook-grip geometry (bar ~10 cm past the wrist), so all hanging
  and support poses are re-authored from the bar position.

## Notes / decisions
- Exercise catalog is code (`SeedData.kt`), user data is Room. Packages: `com.personal.calisthenics.core` (pure) and
  `com.personal.calisthenicsguide` (Android).
- Deload: weeks 1-5 accumulation, week 6 deload (sets in Phase 1 and 2 halved using largest-remainder, min 1 per
  exercise). Warm-up and decompression stay full.
- Pinned versions: AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, Coil 2.7.0.
