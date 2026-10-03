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
- [ ] M13 Spec audit against `docs/SPEC.md`, fix gaps, README with build/install steps
- [ ] M14 CI green (needs GitHub push access) and debug APK artifact produced

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
