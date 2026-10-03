# Stress map: what the highlight colours mean

Every exercise clip colours the body parts that matter for that exact movement:

| Colour | Meaning |
|---|---|
| Red | Muscle doing the work |
| Blue | Tendon under tension |
| Yellow | Joint under tension |

**How strong the colour is** = *load* x *where you are in the rep*.

- **Load**: Primary (1.0) is the main target, Secondary (0.7) assists or stabilises, Minor (0.4) is a light contribution.
- **Peak**: each highlight peaks at **Position A** (start), **Position B** (end of range) or stays **steady** all the way
  through. Away from its peak a highlight fades to 40% of its strength, so the colours pulse with the clip. In a still picture
  (a DO/DON'T card) every highlight is shown at its peak.

> **Caveat.** This is a general biomechanics / rehab-literature mapping of typical bodyweight loading. It is not measured
> data for you and it is not medical advice. If a joint or tendon hurts, stop and see a professional.

The data lives in `SeedData` (`Highlight.load`, `Highlight.peak`); `StressMapTest` keeps the rules honest.


## l_sit
A = knees tucked, B = legs straight (full L).

Isometric compression: the hip flexors and abs work against a lever that is longest at B. Triceps lock the elbows, lower traps and serratus keep the shoulders pushed down; the locked elbows and shoulder capsule carry a steady load.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Hip Flexors - Hold the legs up against gravity | Red | Primary | B (end) |
| Abs - Compress the trunk | Red | Primary | B (end) |
| Triceps - Lockout | Red | Secondary | Steady |
| Lower Traps - Scapular depression | Red | Primary | Steady |
| Medial Elbow - Locked-elbow loading | Blue | Secondary | Steady |
| Shoulder Joint | Yellow | Secondary | Steady |

## planche_lean
A = upright plank, B = leaned forward.

Leaning moves bodyweight ahead of the wrists, so shoulder-flexion torque (front delt, serratus, pecs), wrist extension and the stretched biceps tendon all rise toward B.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Front Delt | Red | Primary | B (end) |
| Serratus | Red | Primary | B (end) |
| Pecs | Red | Secondary | B (end) |
| Distal Biceps Tendon - Straight-arm lengthened load | Blue | Primary | B (end) |
| Wrist - Extension under load | Yellow | Primary | B (end) |
| Shoulder Joint | Yellow | Secondary | B (end) |

## strict_pullups
A = dead hang, B = chin over the bar.

Lats and biceps contract hardest as you pull toward B. The distal biceps tendon is most stretched and loaded at the bottom (A) where the elbow is straight under full bodyweight. Grip keeps the forearm flexors and their medial-elbow origin busy the whole rep.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Lats | Red | Primary | B (end) |
| Biceps | Red | Primary | B (end) |
| Lower Traps | Red | Secondary | Steady |
| Forearm Flexors - Grip | Red | Secondary | Steady |
| Distal Biceps Tendon - Peak load at the bottom | Blue | Primary | A (start) |
| Medial Elbow - Flexor origin under grip load | Blue | Secondary | Steady |
| Shoulder Joint | Yellow | Secondary | A (start) |

## parallel_bar_dips
A = top support, B = bottom.

Pecs, front delts and the shoulder capsule are loaded most at depth (B); triceps work through the whole press; elbow tendons see their highest force at the bottom.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Pecs | Red | Primary | B (end) |
| Triceps | Red | Primary | Steady |
| Front Delt | Red | Secondary | B (end) |
| Medial Elbow - Elbow tendons under tension | Blue | Secondary | B (end) |
| Shoulder Joint - Anterior capsule at the bottom | Yellow | Primary | B (end) |

## straight_bar_dips
A = top support, B = bottom.

Same pressing muscles as parallel dips. The straight bar sits at the base of the palm, so the wrist carries a steady extension load.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Pecs | Red | Primary | B (end) |
| Triceps | Red | Primary | Steady |
| Front Delt | Red | Secondary | B (end) |
| Wrist - Bar sits at the base of the palm | Yellow | Secondary | Steady |
| Shoulder Joint | Yellow | Secondary | B (end) |

## australian_pullups
A = arms straight under the bar, B = chest to bar.

Mid-back, lats and rear delts peak when the shoulder blades are pulled together at B. The bar is lower than a pull-up, so load on the biceps and its tendon is moderate; the tendon is most stretched at A.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Mid Back | Red | Primary | B (end) |
| Lats | Red | Primary | B (end) |
| Rear Delt | Red | Secondary | B (end) |
| Biceps | Red | Secondary | B (end) |
| Distal Biceps Tendon | Blue | Secondary | A (start) |
| Shoulder Joint | Yellow | Minor | Steady |

## pike_pushups
A = hips high, B = head toward the floor.

Front delts work hardest as the head lowers; the shoulder joint is most exposed at the bottom, especially if the elbows flare. Wrists carry weight throughout.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Front Delt | Red | Primary | B (end) |
| Triceps | Red | Secondary | Steady |
| Serratus | Red | Secondary | A (start) |
| Shoulder Joint - Impingement risk if elbows flare | Yellow | Primary | B (end) |
| Wrist | Yellow | Secondary | Steady |

## pseudo_planche_pushups
A = leaned top, B = bottom.

Front delts and pecs peak at the bottom; the lean keeps a steady heavy load on the wrists and lengthens the biceps tendon at the top.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Front Delt | Red | Primary | B (end) |
| Pecs | Red | Primary | B (end) |
| Triceps | Red | Secondary | Steady |
| Distal Biceps Tendon - Lengthened under lean | Blue | Secondary | A (start) |
| Wrist | Yellow | Primary | Steady |
| Shoulder Joint | Yellow | Secondary | B (end) |

## pistol_squat
A = standing on one leg, B = deepest squat.

Quads, glutes, the knee and the patellar tendon peak at the bottom, especially during the slow lowering. The Achilles and ankle load rises with ankle dorsiflexion at depth.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Quads | Red | Primary | B (end) |
| Glutes | Red | Primary | B (end) |
| Patellar Tendon - Loaded eccentrically, peak at the bottom | Blue | Primary | B (end) |
| Achilles | Blue | Secondary | B (end) |
| Knee Joint | Yellow | Primary | B (end) |
| Ankle Joint | Yellow | Secondary | B (end) |

## nordic_curl
A = upright kneeling, B = lowered to the floor.

The hamstring lever grows as the body tilts forward, so hamstrings and their tendons are loaded most near B (eccentric work).

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Hamstrings | Red | Primary | B (end) |
| Glutes | Red | Secondary | B (end) |
| Hamstring Tendon - Eccentric tendon loading | Blue | Primary | B (end) |
| Knee Joint | Yellow | Secondary | B (end) |

## hanging_leg_raises
A = straight hang, B = legs raised.

Abs and hip flexors work to lift the legs and peak at B. Lats and grip hold the hang the whole time.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Abs | Red | Primary | B (end) |
| Hip Flexors | Red | Primary | B (end) |
| Lats - Stabilizes the hang | Red | Secondary | Steady |
| Forearm Flexors - Grip | Red | Secondary | Steady |
| Medial Elbow - Grip load | Blue | Secondary | Steady |
| Shoulder Joint | Yellow | Secondary | Steady |

## german_hang
A = shallow, B = deep.

Passive stretch of the front of the shoulder (front delt, pec minor, biceps tendon) and shoulder extension peak at the deepest position.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Front Delt - Anterior deltoid stretch | Red | Primary | B (end) |
| Pec Minor - Lengthened | Red | Secondary | B (end) |
| Distal Biceps Tendon - Lengthened under gentle load | Blue | Secondary | B (end) |
| Shoulder Joint - End-range extension | Yellow | Primary | B (end) |

## forearm_flexor_extensor_stretch
A = fingers pointing down, B = fingers pointing up.

Fingers down lengthens the wrist extensors (lateral elbow origin); fingers up lengthens the flexors (medial elbow origin). The wrist is moved gently both ways.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Forearm Extensors - Stretched while the fingers point down | Red | Primary | A (start) |
| Forearm Flexors - Stretched while the fingers point up | Red | Primary | B (end) |
| Lateral Elbow - Extensor tendon origin | Blue | Secondary | A (start) |
| Medial Elbow - Flexor tendon origin | Blue | Secondary | B (end) |
| Wrist | Yellow | Secondary | Steady |

## prying_deep_squat
A = standing, B = deep squat.

Mostly ankle and hip range of motion at B; muscles and tendons are only lightly loaded because the bottom position is held low-force.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Glutes | Red | Secondary | B (end) |
| Quads | Red | Secondary | B (end) |
| Achilles - Ankle dorsiflexion stretch | Blue | Secondary | B (end) |
| Patellar Tendon - Low-force lengthened loading | Blue | Minor | B (end) |
| Ankle Joint | Yellow | Primary | B (end) |
| Hip Joint | Yellow | Primary | B (end) |

## jefferson_curl
A = upright, B = fully rounded.

Hamstrings, back extensors and thoracolumbar fascia are lengthened (not strongly contracted) and peak at the deepest curl.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Hamstrings | Red | Primary | B (end) |
| Erectors - Lengthened, not loaded | Red | Primary | B (end) |
| Hamstring Tendon - Lengthened under bodyweight | Blue | Secondary | B (end) |
| Thoracolumbar - Fascial tension | Blue | Secondary | B (end) |

## jumping_jacks
A = feet together, B = arms overhead and feet wide.

Calves and Achilles do springy work through every rep; the shoulder joint reaches full overhead range at B.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Calves - Springy ankle work | Red | Secondary | Steady |
| Shoulder Joint - Full overhead range | Yellow | Secondary | B (end) |
| Achilles - Light elastic loading | Blue | Minor | Steady |

## joint_circles
A/B = opposite sides of the circle.

Gentle capsule mobilisation; nothing is loaded heavily, so shoulder, hip and ankle are the main targets and muscles are minor.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Rear Delt - Gentle rotator-cuff activation | Red | Minor | Steady |
| Shoulder Joint - Capsule lubrication | Yellow | Secondary | Steady |
| Hip Joint - Hip capsule | Yellow | Secondary | Steady |
| Ankle Joint - Ankle mobility | Yellow | Secondary | Steady |
| Medial Elbow - Elbow circles prime the flexor origin | Blue | Minor | Steady |

## banded_dislocates
A = band low, B = band overhead.

The shoulder capsule is the main target; rear delts and lower traps work to guide the band overhead.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Shoulder Joint - Capsule mobility | Yellow | Primary | Steady |
| Rear Delt | Red | Secondary | B (end) |
| Lower Traps | Red | Secondary | B (end) |

## band_pull_aparts
A = arms in front, B = arms wide.

Rear delts and mid-back (rhomboids, mid-traps) work hardest at full spread.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Rear Delt | Red | Primary | B (end) |
| Mid Back | Red | Primary | B (end) |
| Lower Traps | Red | Secondary | B (end) |
| Shoulder Joint | Yellow | Minor | Steady |

## first_knuckle_raises
A = flat hand, B = heel of hand lifted onto the finger pads.

Finger flexors and the wrist capsule work most at B; the common flexor origin at the medial elbow takes a light, steady load.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Forearm Flexors - Finger flexors do the lifting | Red | Primary | B (end) |
| Medial Elbow - Common flexor origin | Blue | Secondary | Steady |
| Wrist - Capsule conditioning | Yellow | Secondary | B (end) |

## wrist_extensor_leans
A = upright, B = leaned forward over flat palms.

Leaning forward extends the wrist further: the joint is the target at B and the flexors are lengthened. The extensors mostly stabilise.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Forearm Extensors - Stabilise the wrist | Red | Secondary | Steady |
| Forearm Flexors - Lengthened as you lean | Red | Secondary | B (end) |
| Lateral Elbow - Common extensor origin | Blue | Secondary | Steady |
| Wrist - Wrist extension range | Yellow | Primary | B (end) |

## palms_back_flexor_stretch
A = hands set, B = sat back.

Sitting back stretches the finger and wrist flexors and their medial-elbow origin.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Forearm Flexors | Red | Primary | B (end) |
| Medial Elbow - Golfer's elbow origin | Blue | Secondary | B (end) |
| Wrist | Yellow | Primary | B (end) |

## dorsal_wrist_pushups
A = hands set, B = leaned in.

Back of the hand on the floor flexes the wrist and lengthens the extensors and dorsal ligaments, peaking at B.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Forearm Extensors | Red | Primary | B (end) |
| Lateral Elbow - Extensor origin | Blue | Secondary | B (end) |
| Wrist - Dorsal ligaments | Yellow | Primary | B (end) |

## passive_dead_hang
Isometric, no real A/B difference.

Relaxed hang: lats are in a long stretch, grip flexors and the medial elbow origin carry bodyweight, the shoulder capsule is traction-loaded steadily.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Lats - Long stretch | Red | Primary | Steady |
| Forearm Flexors - Grip endurance | Red | Primary | Steady |
| Medial Elbow - Grip load on the flexor origin | Blue | Secondary | Steady |
| Shoulder Joint - Capsule decompression | Yellow | Secondary | Steady |

## scapular_pullups
A = relaxed hang, B = shoulder blades pulled down.

Lower traps lead the shoulder-blade depression and peak at B; lats and serratus assist.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Lower Traps | Red | Primary | B (end) |
| Lats | Red | Secondary | B (end) |
| Serratus | Red | Secondary | B (end) |
| Shoulder Joint - Learns to stay packed | Yellow | Secondary | B (end) |

## scapular_dips
A = shoulders up, B = shoulders pushed down.

Lower traps and serratus drive the depression; triceps and elbows stay locked, so their load is steady.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Lower Traps | Red | Primary | B (end) |
| Serratus | Red | Primary | B (end) |
| Triceps - Isometric lockout | Red | Secondary | Steady |
| Medial Elbow - Locked-elbow loading | Blue | Secondary | Steady |
| Shoulder Joint | Yellow | Secondary | Steady |

## scapular_pushups
A = blades together, B = blades spread apart.

Serratus anterior and pec minor peak when the shoulder blades protract (B); lower traps control the return.

| Region | Colour | Load | Peaks |
|---|---|---|---|
| Serratus - Protraction | Red | Primary | B (end) |
| Pec Minor | Red | Secondary | B (end) |
| Lower Traps | Red | Secondary | A (start) |
| Shoulder Joint | Yellow | Minor | Steady |
