# Calisthenics Park & Tendon Guide

Personal-use Android app (Kotlin, Jetpack Compose, Room, 100% offline) for outdoor calisthenics sessions: a smart
workout timer plus a biomechanical guide focused on tendon and joint health. Full spec: [`docs/SPEC.md`](docs/SPEC.md).
Stress-colouring rationale per exercise: [`docs/STRESS_MAP.md`](docs/STRESS_MAP.md).

## Tabs

- **Home**: tendon recovery clock (48-72 h window), A/B/C day rotation, 6-week deload tracker, pre-workout checklist,
  cold-weather superset toggle, beep/vibration settings, start button.
- **Workout**: warm-up flow (10 s transitions), isometric holds with a 5 s get-ready buffer, tempo metronome, smart rest
  timer that starts when a set is logged, reps/RIR logging, joint rating at the end. Runs in a foreground service with a
  wake lock; the screen stays on during a session.
- **Guide**: every exercise with a looping 3D humanoid clip (orbit, side, front), stress-matched muscle/tendon/joint
  colouring, optional own GIF/MP4, labelled DO/DON'T pictures, and the 4-stage progression matrix.
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
  `SessionEngine` state machine, analytics, and the 3D body rig that produces drawing primitives.
- `app/`: Compose UI, Room data layer, audio/haptic feedback, foreground service.
- `tools/run-core-tests.sh`: compiles and runs the `:core` tests without Gradle (needs only a JDK and downloads kotlinc).
  `tools/render-rig.sh` renders PNG contact sheets of the rig for visual review.

## Notes

- The rest times in the spec add up to roughly 85-100 minutes of training, not the 70-75 minutes it states, so the app
  shows a computed time range instead of a fixed promise.
- Stress colouring is a general biomechanics / rehab-literature mapping, not measured data for you and not medical advice.
