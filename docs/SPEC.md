# SYSTEM PROMPT: Build "Calisthenics Park & Tendon Guide" Android App (Personal Use)

## 1. Context, Language & Core Objective
Build a native or cross-platform Android mobile application (offline-first, no authentication, no external backend, 100% local storage using SQLite / Room / MMKV) designed strictly for personal use.
* **Language Requirement:** All UI elements, menus, timers, audio cues, database seed data, and biomechanical instructions MUST be in **English**.
* **User Profile & Goal:** The user has 3.5 years of traditional gym weightlifting experience (strong muscle bellies) but is a beginner in Calisthenics (tendons, ligaments, and scapular stabilizers are still adapting). The app must serve both as a **Smart Workout Tracker & Timer System for outdoor park workouts** and as a comprehensive **Interactive Biomechanical Guide** focused on joint injury prevention and bodyweight progressive overload.

## 2. UI/UX Design & Outdoor Park Ergonomics
* **High-Contrast Dark Mode:** Deep dark theme with high-contrast typography and accent colors for clear visibility under direct outdoor sunlight.
* **One-Hand / Chalk-Friendly Controls:** Large touch targets positioned in the lower half of the screen so they can be easily tapped with chalked hands or workout gloves.
* **Audio & Haptic Feedback:** Short audio beeps (using audio ducking so background music is never paused) and distinct vibration patterns for timers (get-ready buffer, isometric start/stop, tempo cadence, and rest completion).

---

## 3. Functional Architecture & Main Screens (4 Tabs)

### TAB 1: DASHBOARD & RECOVERY STATUS (Home)
1. **Tendon Recovery Timer:** Displays elapsed time since the last completed workout, highlighting the optimal **48–72 hour collagen synthesis window**.
2. **Workout Day Rotation (A / B / C):** Automatically selects the next session in the 3x/week Full Body cycle to rotate tendon stress angles and prevent overuse injuries:
   * **Day A (Monday):** Pronated Pull Focus (Overhand Pull-ups) + Vertical Push (Pike Push-ups).
   * **Day B (Wednesday):** Neutral Grip Pull Focus + Straight/Bent-Arm Push (Pseudo Planche Push-ups).
   * **Day C (Friday):** Supinated Pull Focus (Chin-ups) + Straight Bar Dips.
3. **Deload Week Tracker:** Progress bar tracking a 5-week accumulation block. On Week 6, the app automatically switches to **"Deload Mode"** (reducing total sets by 50% to allow connective tissue super-compensation).
4. **Pre-Workout Checklist (45 mins prior):** Quick daily toggles for:
   * *Tendon Nutrition Protocol:* 10–15g Hydrolyzed Collagen + 50–100mg Vitamin C + Hydration / Creatine.
   * *Park Gear Check:* Light resistance band, Chalk (liquid/block), Neoprene wrist wraps / elbow sleeves / long sleeves (for cold weather).

---

### TAB 2: WORKOUT PLAYER & SMART TIMERS (Active Session)
The active workout screen runs through 4 sequential phases. Include a global top toggle: **"Cold Weather / Antagonist Supersets Mode"** (which automatically pairs Exercise 3 with 4, and Exercise 5 with 6, reducing rest to 90s between paired exercises to keep body temperature elevated outdoors).

#### A. Integrated Timer Engine:
1. **Flow Timer (for Warm-up & Stretching):** Automatically transitions from one drill to the next with a 10-second setup transition.
2. **Isometric Hold Timer (with "Get Ready" Buffer):** Tapping "Start" on L-Sit or Planche Lean triggers a **5-second countdown** (*"Get into position & lock scapula"*), followed immediately by the 15–20 second active hold timer.
3. **Tempo Metronome (Visual & Audio):** Animated pacing bar guiding each repetition's cadence (e.g., 3s eccentric lowering -> 1s bottom pause -> 1s concentric drive).
4. **Smart Rest Timer:** Auto-starts immediately when a set is logged (90s for isometrics/core, 120–180s for heavy compound lifts).

#### B. Pre-Loaded Full Body Workout Structure (~70–75 min):

**PHASE 0: "Bulletproof Joints" Warm-up (12–15 min)**
* *Cardio & General Mobility (3 min):* Jumping Jacks, neck/shoulder/elbow/hip/ankle circles, 20x Banded Shoulder Dislocates & Band Pull-Aparts.
* *Floor Wrist Prep – Elbows Locked (4 min):* 
  1. First Knuckle Raises / Palm Pulses (15 controlled reps)
  2. Forward Wrist Extensor Leans (10 reps x 2s hold)
  3. Palms-Back Flexor Stretch leaning toward heels (10 slow pulses)
  4. Dorsal (Back-of-Hand) Wrist Push-ups at 15% bodyweight (10 fist-clenching reps).
* *Scapular & Elbow Activation (5 min):* Passive Dead Hang (15s) -> Scapular Pull-ups (10 reps x 2s pause at top), Parallel Bar Support Hold + Scapular Dips (10 reps with locked elbows), Scapular Push-ups focusing on full protraction at the top (10 reps).

**PHASE 1: Isometrics & Straight-Arm Joint Strength (10 min)**
1. **Parallel Bar L-Sit / Tuck L-Sit:** 3 sets x 15–20s | Rest: 90s | *Cue: Lock elbows completely, push shoulders down away from ears (scapular depression).*
2. **Planche Lean:** 3 sets x 15–20s | Rest: 90s | *Cue: Straight arms, Posterior Pelvic Tilt (tuck tailbone), protract scapula, lean shoulders past wrists.*

**PHASE 2: Full Body Strength & Hypertrophy (~40–45 min)**
*(Log per set: Reps completed, RIR [1–2 recommended], and current Progression Level)*
3. **Strict Pull-ups (Overhand / Neutral / Chin-ups based on Day A/B/C):** 4 sets x 6–10 reps | Tempo: 3-1-X-0 | Rest: 150–180s.
4. **Parallel Bar Dips (or Straight Bar Dips on Day C):** 4 sets x 8–12 reps | Tempo: 3-0-1-0 | Rest: 120–150s.
5. **Elevated Australian Pull-ups (Bodyweight Rows):** 3 sets x 8–12 reps | Tempo: 2-0-1-1 (1s chest-to-bar squeeze) | Rest: 120s.
6. **Pike Push-ups (Days A/C) or Pseudo Planche Push-ups (Day B):** 3 sets x 6–10 reps | Tempo: 3-1-1-0 | Rest: 120s.
7. **Pistol Squat Progression / Bulgarian Split Squats:** 3 sets x 6–8 reps/leg | Tempo: 3-1-1-0 | Rest: 60s between legs.
8. **Assisted Nordic Hamstring Curls / Single-Leg Hip Thrust:** 3 sets x 8–10 reps | Tempo: 4s eccentric lowering | Rest: 90s.
9. **Hanging Leg Raises / Toes-to-Bar:** 3 sets x 8–12 reps | Tempo: 3s strict lowering, zero momentum | Rest: 90s.

**PHASE 3: Loaded Stretching & Tendon Decompression (10 min)**
1. **German Hang Progression (Feet-Assisted Skin the Cat on low bar):** 2 sets x 30s (targets distal biceps tendon, anterior deltoid, and pec minor).
2. **Passive Dead Hang with Parasympathetic Breathing (4s slow exhale):** 2 sets x 45s (spinal and shoulder decompression).
3. **Locked-Elbow Forearm Flexor & Extensor Stretch:** 30s per side/direction (direct Golfer's Elbow prevention).
4. **Prying Deep Squat Hold:** 90s continuous ankle dorsiflexion and Achilles/patellar tendon mobilization.
5. **Bodyweight Jefferson Curl (off a park bench, vertebra by vertebra):** 2 sets x 30s (hamstring tendons and thoracolumbar fascia).

---

### TAB 3: HUMAN DEMONSTRATIONS & BIOMECHANICAL ENCYCLOPEDIA (Guide)
Every exercise in the database features an interactive guide card containing:
1. **Human Visual Demonstration (2D Articulated Vector Rig / SVG / Canvas Animation):**
   * An articulated human mannequin (side and front views) smoothly looping between Position A (Start) and Position B (End Range).
   * Color-coded anatomical highlights: **Red** for primary target muscles, **Blue/Yellow** for tendons and joints under high mechanical tension (e.g., highlighting the distal biceps tendon during Planche Leans and German Hangs).
   * Ability for the user to attach/override with a local GIF or MP4 video from device storage.
2. **Visual "DO vs. DON'T" Biomechanical Cards:**
   * *Example 1 (Pike Push-ups):* Flaring elbows and dropping the head between the hands (WRONG - shoulder impingement) vs. leaning forward so the head and hands form an equilateral tripod triangle (CORRECT).
   * *Example 2 (Bar Grip):* Gripping deep in the palm crease (WRONG - pinches skin and rips calluses) vs. **Active Hook Grip** at the base of the fingers (CORRECT).
3. **4-Stage Bodyweight Progression Matrix (Progressive Overload Gatekeeper):**
   * Checkboxes the user must complete before the app prompts them to unlock a harder leverage variation:
     * `[ ] Stage 1: Volume Mastery` (Hit 3–4 sets x 12 clean reps with RIR 1–2)
     * `[ ] Stage 2: Eccentric Tempo` (Master a 4-second controlled lowering phase)
     * `[ ] Stage 3: Isometric Pause` (Hold a 2-second pause at the hardest mechanical point of the rep)
     * `[ ] Stage 4: Leverage Unlock` (Advance to the next mechanical progression, e.g., Ground Pike Push-ups -> Elevated Pike Push-ups).

---

### TAB 4: TRACKING & ESSENTIAL ANALYTICS (Stats)
1. **Consistency Heatmap & Streak:** Monthly GitHub-style contribution grid displaying completed workouts (Days A, B, C), rest days, and Deload weeks.
2. **Tendon Safety Guard (Anti-Tendonitis Volume Monitor):**
   * Calculates total weekly Pull reps and Push reps.
   * If weekly Pull volume increases by **more than 15%** compared to the previous week, trigger a prominent warning banner: *"Medial Epicondylitis (Golfer's Elbow) Risk: Weekly pulling volume spiked by >15%. Keep volume steady to allow tendon adaptation."*
3. **Core Strength Progression Charts:**
   * Clean Line Charts tracking: Max Isometric Hold Time (L-Sit & Planche Lean in seconds) and Total Clean Reps per workout for Pull-ups and Dips.
4. **Joint Health Post-Workout Log:**
   * Quick 1–5 soreness/stiffness rating slider after each session for **Wrists, Elbows, and Shoulders**. If the user logs inner-elbow stiffness (>2/5), the app automatically suggests switching to Neutral Grip for the next session.

## 4. Technical Implementation Directives
* Deliver clean, modular, production-ready code with zero `// TODO: add exercises here` placeholders.
* Pre-populate the entire `seedData` file with all exercises, phases, timers, tempos, anatomical highlights, and biomechanical cues listed above in English.
* Implement an Android `Foreground Service` and screen `WakeLock` during active workouts so isometric timers and rest countdowns never get killed by OS battery optimization when the phone is locked or in a pocket.