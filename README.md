# Calisthenics Park & Tendon Guide

Personal-use Android app (Kotlin, Jetpack Compose, Room, 100% offline) for outdoor calisthenics sessions: a smart
workout timer plus a biomechanical guide focused on tendon and joint health. Full spec: [`docs/SPEC.md`](docs/SPEC.md).
Stress-colouring rationale per exercise: [`docs/STRESS_MAP.md`](docs/STRESS_MAP.md).

## Tabs

- **Home**: today's workout card, tendon recovery clock (48-72 h window), A/B/C day rotation, 6-week deload tracker;
  pre-workout checklist and beep/vibration settings are collapsed until you open them.
- **Workout**: pressing a workout opens a details page first: every exercise with its exact sets x reps (or hold
  seconds) and the rest in minutes, the picture of position A on each row, and a drawer on tap that plays the looping 3D
  clip with the reps and description under it. Day choice and the cold-weather superset switch live there, and so does
  the Start button. During a session: warm-up flow (10 s transitions), isometric holds with a 5 s get-ready buffer,
  smart rest timer that starts when a set is logged, reps/RIR logging, joint rating at the end. Strength sets counted in
  reps show the 3D clip full screen at the set's tempo (beeps and vibration keep running) with a Next button that ends
  the set and leads to a one-tap, pre-filled reps/RIR log. Runs in a foreground service with a wake lock; the screen
  stays on during a session.
- **Guide**: every exercise with a looping clip of a real 3D human mesh (orbit, side, front) tinted with soft
  stress-matched muscle/tendon/joint hues, optional own GIF/MP4, labelled DO/DON'T pictures, and the 4-stage progression matrix.
- **Stats**: consistency heatmap and streak, tendon safety guard (pull volume +15% warning), strength charts, joint log.

## Build and install

Requirements: JDK 17 and the Android SDK (platform 35). From the repo root:

    ./gradlew :core:test            # pure-Kotlin logic tests
    ./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk

Or download the `calisthenics-debug-apk` artifact from the latest successful run of the *Android CI* workflow on GitHub,
copy `app-debug.apk` to the phone and open it (allow "install unknown apps" for your file manager or browser once).
Release builds are signed with the debug key so they also install directly.

On Android 13+ the app asks for notification permission when you start a workout; it only shows the "workout running"
notification that keeps timers alive with the screen off.

## Layout

- `core/`: pure Kotlin (no Android): models and seed data, session planning, deload, timers and cue catalog, the
  `SessionEngine` state machine, analytics, the 3D body rig (poses, solver, capsule figure) and the human mesh pipeline
  (`core/.../mesh/`: skinning, rasteriser, floor and props) that draws the figure into a bitmap.
- `app/`: Compose UI, Room data layer, audio/haptic feedback, foreground service.
- `tools/run-core-tests.sh`: compiles and runs the `:core` tests without Gradle (needs only a JDK and downloads kotlinc).
  `tools/render-rig.sh` renders PNG contact sheets of the rig and `tools/render-mesh.sh` renders the human mesh for visual
  review. `tools/mesh/` converts the MakeHuman body into `core/src/main/resources/mesh/human.bin`.

## Credits

The human body mesh comes from the MakeHuman project and is CC0 (public domain); details, files used and how to regenerate
it are in [`docs/ASSETS.md`](docs/ASSETS.md).

## Notes

- The rest times in the spec add up to roughly 85-100 minutes of training, not the 70-75 minutes it states, so the app
  shows a computed time range instead of a fixed promise.
- Stress colouring is a general biomechanics / rehab-literature mapping, not measured data for you and not medical advice.
