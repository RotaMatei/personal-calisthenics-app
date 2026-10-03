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
- [ ] M1 `:core` models + complete seed data (exercises, steps, tempos, highlights, cues, DO/DON'T, ladders)
- [ ] M2 `:core` logic: day rotation, deload, session planner/sequencer, superset mode, smart rest
- [ ] M3 `:core` timers: TimelineTimer, isometric get-ready, tempo, rest, flow; cue to beep/haptic mapping
- [ ] M4 `:core` analytics: recovery window, weekly volume guard (+15%), streak, heatmap, joint advice, progression gating
- [ ] M5 `:core` 3D body rig (IK, projections, highlights) + PNG preview tool, poses for every exercise
- [ ] M6 `:core` unit tests for M1-M5 (run locally via shim runner)
- [ ] M7 `:app` data layer (Room entities, DAOs, repository, seeding of user state)
- [ ] M8 `:app` feedback (audio ducking beeps, haptics) + foreground service + wake lock + session controller
- [ ] M9 `:app` theme + navigation + Tab 1 Dashboard
- [ ] M10 `:app` Tab 2 Workout Player (flow, isometric, tempo, rest, set logging, cold mode, finish + joint log)
- [ ] M11 `:app` Tab 3 Guide (rig canvas, media override, DO/DON'T, progression matrix)
- [ ] M12 `:app` Tab 4 Stats (heatmap, safety guard, charts, joint history)
- [ ] M13 Spec audit against `docs/SPEC.md`, fix gaps, README with build/install steps
- [ ] M14 CI green (needs GitHub push access) and debug APK artifact produced

## Notes / decisions
- Exercise catalog is code (`SeedData.kt`), user data is Room. Packages: `com.personal.calisthenics.core` (pure) and
  `com.personal.calisthenicsguide` (Android).
- Deload: weeks 1-5 accumulation, week 6 deload (sets in Phase 1 and 2 halved using largest-remainder, min 1 per
  exercise). Warm-up and decompression stay full.
- Pinned versions: AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Compose BOM 2024.12.01, Room 2.6.1, Coil 2.7.0.
