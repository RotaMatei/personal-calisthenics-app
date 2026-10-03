package com.personal.calisthenics.core.rig

/** Body points a DO/DON'T label can point at. "_C" landmarks use the limb that is on the floor / bar. */
enum class Landmark {
    HEAD, NECK, SHOULDERS, UPPER_BACK, LOW_BACK, CHEST, BELLY, SPINE, HIPS,
    ELBOW_L, ELBOW_R, WRIST_L, WRIST_R, KNEE_L, ANKLE_L, HEEL_L, FOOT_L,
    KNEE_C, HEEL_C, ANKLE_C,
}

/** Where a label sits relative to the point it names (view directions; the figure faces right in the side view). */
enum class Place { LEFT, RIGHT, UP, DOWN }

enum class LabelAlign { START, END, CENTER }

/**
 * One short callout on a DO/DON'T picture: [text] names what is [good] or bad at [landmark]. The label is placed
 * [distance] cm from the point toward [place] (plus [lift] cm upward) and joined to it by an arrow.
 */
data class Annotation(
    val landmark: Landmark,
    val text: String,
    val good: Boolean,
    val place: Place = Place.RIGHT,
    val distance: Float = 30f,
    val lift: Float = 0f,
)

/** An annotation resolved to view coordinates (cm, y up) for one camera. */
data class AnnotationDraw(
    val text: String,
    val good: Boolean,
    /** The body point the arrow ends on. */
    val anchor: Vec2,
    /** Where the text is drawn: its left edge for START, right edge for END, centre for CENTER (baseline middle). */
    val label: Vec2,
    val align: LabelAlign,
    /** Text height in cm. */
    val size: Float,
) {
    /** Approximate width of the text in cm (the Compose / Java2D renderers measure exactly; core only frames). */
    val width: Float get() = text.length * size * 0.64f

    /** Rectangle the label occupies, including a little padding. */
    fun box(): Bounds {
        val w = width + 2f
        val minX = when (align) {
            LabelAlign.START -> label.x - 1f
            LabelAlign.END -> label.x - w + 1f
            LabelAlign.CENTER -> label.x - w / 2f
        }
        return Bounds(minX, label.y - size * 0.8f, minX + w, label.y + size * 1.2f)
    }
}

object Annotations {
    const val TEXT_SIZE = 4.4f

    /** Labels for DO/DON'T still [key] (empty if it has none). */
    fun forStill(key: String): List<Annotation> = table[key].orEmpty()

    fun point(landmark: Landmark, pose: Pose, sk: Skeleton): Vec3 {
        val spine = lerp(sk.hip, sk.shoulder, 0.5f)
        fun contactLeft() = pose.footL.contact || !pose.footR.contact
        return when (landmark) {
            Landmark.HEAD -> sk.headCenter
            Landmark.NECK -> sk.shoulder + sk.up * 7f
            Landmark.SHOULDERS -> sk.shoulder
            Landmark.UPPER_BACK -> lerp(sk.hip, sk.shoulder, 0.78f) - sk.front * 9f
            Landmark.LOW_BACK -> lerp(sk.hip, sk.shoulder, 0.25f) - sk.lowFront * 9f
            Landmark.CHEST -> lerp(sk.hip, sk.shoulder, 0.8f) + sk.front * 9f
            Landmark.BELLY -> lerp(sk.hip, sk.shoulder, 0.4f) + sk.front * 9f
            Landmark.SPINE -> spine - sk.front * 8f
            Landmark.HIPS -> sk.hip
            Landmark.ELBOW_L -> sk.elbowL
            Landmark.ELBOW_R -> sk.elbowR
            Landmark.WRIST_L -> sk.wristL
            Landmark.WRIST_R -> sk.wristR
            Landmark.KNEE_L -> sk.kneeL
            Landmark.ANKLE_L -> sk.ankleL
            Landmark.HEEL_L -> sk.heelL
            Landmark.FOOT_L -> sk.toeL
            Landmark.KNEE_C -> if (contactLeft()) sk.kneeL else sk.kneeR
            Landmark.HEEL_C -> if (contactLeft()) sk.heelL else sk.heelR
            Landmark.ANKLE_C -> if (contactLeft()) sk.ankleL else sk.ankleR
        }
    }

    /** Resolves [list] for [pose] as seen from [camera]. */
    fun layout(list: List<Annotation>, pose: Pose, camera: Camera): List<AnnotationDraw> {
        val sk = RigSolver.solve(pose)
        return list.map { a ->
            val p = camera.project(point(a.landmark, pose, sk))
            val anchor = Vec2(p.x, p.y)
            val (dx, dy, align) = when (a.place) {
                Place.LEFT -> Triple(-a.distance, 0f, LabelAlign.END)
                Place.RIGHT -> Triple(a.distance, 0f, LabelAlign.START)
                Place.UP -> Triple(0f, a.distance, LabelAlign.CENTER)
                Place.DOWN -> Triple(0f, -a.distance, LabelAlign.CENTER)
            }
            AnnotationDraw(a.text, a.good, anchor, Vec2(anchor.x + dx, anchor.y + dy + a.lift), align, TEXT_SIZE)
        }
    }

    private fun bad(l: Landmark, text: String, place: Place = Place.RIGHT, distance: Float = 30f, lift: Float = 0f) =
        Annotation(l, text, false, place, distance, lift)

    private fun good(l: Landmark, text: String, place: Place = Place.RIGHT, distance: Float = 30f, lift: Float = 0f) =
        Annotation(l, text, true, place, distance, lift)

    private val table: Map<String, List<Annotation>> = mapOf(
        "l_sit.wrong_shrugged" to listOf(bad(Landmark.SHOULDERS, "Shoulders by ears", Place.LEFT), bad(Landmark.ELBOW_L, "Elbows bent", Place.RIGHT, 34f, 6f)),
        "l_sit.right_depressed" to listOf(good(Landmark.SHOULDERS, "Shoulders pushed down", Place.LEFT), good(Landmark.ELBOW_L, "Elbows locked", Place.RIGHT, 34f, 6f)),
        "planche_lean.wrong_arched" to listOf(bad(Landmark.LOW_BACK, "Low back arched", Place.UP, 24f), bad(Landmark.UPPER_BACK, "Blades pinched", Place.UP, 34f, 8f)),
        "planche_lean.right_tucked_protracted" to listOf(good(Landmark.HIPS, "Tailbone tucked", Place.UP, 26f), good(Landmark.SHOULDERS, "Shoulders past wrists", Place.UP, 30f)),
        "strict_pullups.wrong_shrug_chin_poke" to listOf(bad(Landmark.HEAD, "Chin pokes over bar", Place.RIGHT, 28f), bad(Landmark.SHOULDERS, "Shrugged", Place.LEFT, 28f)),
        "strict_pullups.right_chest_to_bar" to listOf(good(Landmark.CHEST, "Chest to bar", Place.RIGHT, 28f), good(Landmark.SHOULDERS, "Shoulders down", Place.LEFT, 28f)),
        "parallel_bar_dips.wrong_deep_flared" to listOf(bad(Landmark.SHOULDERS, "Too deep, shoulders rounded", Place.LEFT, 24f), bad(Landmark.ELBOW_L, "Elbows flare", Place.RIGHT, 30f)),
        "parallel_bar_dips.right_parallel" to listOf(good(Landmark.ELBOW_L, "Elbows 45 deg back", Place.LEFT, 28f), good(Landmark.CHEST, "Chest forward", Place.RIGHT, 28f)),
        "straight_bar_dips.wrong_upright" to listOf(bad(Landmark.CHEST, "Chest behind bar", Place.LEFT, 30f), bad(Landmark.ELBOW_L, "Forearms angled back", Place.RIGHT, 30f)),
        "straight_bar_dips.right_chest_over" to listOf(good(Landmark.CHEST, "Chest over bar", Place.RIGHT, 30f), good(Landmark.ELBOW_L, "Forearms vertical", Place.LEFT, 30f)),
        "australian_pullups.wrong_sagging" to listOf(bad(Landmark.HIPS, "Hips sag", Place.DOWN, 24f), bad(Landmark.HEAD, "Neck reaches", Place.UP, 22f)),
        "australian_pullups.right_plank_line" to listOf(good(Landmark.SPINE, "Rigid plank line", Place.DOWN, 28f), good(Landmark.CHEST, "Chest to bar", Place.UP, 28f)),
        "pike_pushups.wrong_flare_head_between" to listOf(bad(Landmark.HEAD, "Head between hands", Place.RIGHT, 30f), bad(Landmark.ELBOW_L, "Elbows flare", Place.LEFT, 30f)),
        "pike_pushups.right_tripod" to listOf(good(Landmark.HEAD, "Head ahead of hands", Place.RIGHT, 30f), good(Landmark.HIPS, "Hips high", Place.UP, 24f)),
        "pseudo_planche_pushups.wrong_no_lean" to listOf(bad(Landmark.SHOULDERS, "Shoulders over hands", Place.UP, 26f), bad(Landmark.WRIST_L, "Hands under chest", Place.RIGHT, 30f)),
        "pseudo_planche_pushups.right_leaning" to listOf(good(Landmark.SHOULDERS, "Shoulders past hands", Place.UP, 26f), good(Landmark.WRIST_L, "Hands at the waist", Place.RIGHT, 30f)),
        "pistol_squat.wrong_valgus" to listOf(bad(Landmark.KNEE_C, "Knee caves in", Place.RIGHT, 34f), bad(Landmark.HEEL_C, "Heel lifts", Place.LEFT, 30f)),
        "pistol_squat.right_tracking" to listOf(good(Landmark.KNEE_C, "Knee over middle toes", Place.RIGHT, 34f), good(Landmark.HEEL_C, "Heel stays down", Place.LEFT, 30f)),
        "nordic_curl.wrong_hip_pike" to listOf(bad(Landmark.HIPS, "Hips fold back", Place.UP, 28f), bad(Landmark.CHEST, "Torso drops", Place.RIGHT, 28f)),
        "nordic_curl.right_straight_line" to listOf(good(Landmark.SPINE, "Straight line knees to head", Place.UP, 30f)),
        "hanging_leg_raises.wrong_swing" to listOf(bad(Landmark.LOW_BACK, "Back arched", Place.LEFT, 28f), bad(Landmark.KNEE_L, "Swinging", Place.RIGHT, 28f)),
        "hanging_leg_raises.right_strict" to listOf(good(Landmark.HIPS, "Pelvis tucked", Place.LEFT, 28f), good(Landmark.KNEE_L, "Controlled lift", Place.RIGHT, 28f)),
        "german_hang.wrong_passive_drop" to listOf(bad(Landmark.SHOULDERS, "Shrugged and passive", Place.RIGHT, 32f), bad(Landmark.HIPS, "Dropped to end range", Place.RIGHT, 40f, -10f)),
        "german_hang.right_feet_assisted" to listOf(good(Landmark.SHOULDERS, "Shoulders active", Place.RIGHT, 32f), good(Landmark.ANKLE_L, "Feet share the load", Place.RIGHT, 30f)),
        "forearm_flexor_extensor_stretch.wrong_yank" to listOf(bad(Landmark.ELBOW_L, "Elbow bent", Place.LEFT, 26f), bad(Landmark.WRIST_R, "Hard yank", Place.RIGHT, 26f)),
        "forearm_flexor_extensor_stretch.right_gentle" to listOf(good(Landmark.ELBOW_L, "Elbow locked", Place.LEFT, 26f), good(Landmark.WRIST_R, "Gentle pressure", Place.RIGHT, 26f)),
        "prying_deep_squat.wrong_heels_up" to listOf(bad(Landmark.HEEL_L, "Heels lift", Place.LEFT, 28f), bad(Landmark.KNEE_L, "Knees cave", Place.RIGHT, 30f)),
        "prying_deep_squat.right_heels_down" to listOf(good(Landmark.HEEL_L, "Heels flat", Place.LEFT, 28f), good(Landmark.KNEE_L, "Knees track out", Place.RIGHT, 30f)),
        "jefferson_curl.wrong_fast_bounce" to listOf(bad(Landmark.UPPER_BACK, "Fast and bouncing", Place.LEFT, 30f)),
        "jefferson_curl.right_segmental" to listOf(good(Landmark.SPINE, "One vertebra at a time", Place.LEFT, 30f), good(Landmark.HEAD, "Head leads down", Place.RIGHT, 30f)),
        "jumping_jacks.wrong_stiff_landing" to listOf(bad(Landmark.KNEE_L, "Locked knees", Place.RIGHT, 30f), bad(Landmark.HEEL_L, "Heels slam", Place.LEFT, 28f)),
        "jumping_jacks.right_soft_landing" to listOf(good(Landmark.KNEE_L, "Soft knees", Place.RIGHT, 30f), good(Landmark.ANKLE_L, "Balls of the feet", Place.LEFT, 28f)),
        "joint_circles.wrong_neck_hyperextend" to listOf(bad(Landmark.NECK, "Neck hyperextends", Place.LEFT, 30f)),
        "joint_circles.right_neck_neutral" to listOf(good(Landmark.HEAD, "Half circle, long neck", Place.RIGHT, 30f)),
        "banded_dislocates.wrong_ribs_flared" to listOf(bad(Landmark.BELLY, "Ribs flared", Place.RIGHT, 32f), bad(Landmark.ELBOW_L, "Elbows bent", Place.LEFT, 28f)),
        "banded_dislocates.right_ribs_down" to listOf(good(Landmark.BELLY, "Ribs down", Place.RIGHT, 32f), good(Landmark.ELBOW_L, "Arms straight", Place.LEFT, 28f)),
        "band_pull_aparts.wrong_shrug" to listOf(bad(Landmark.SHOULDERS, "Shrugged", Place.LEFT, 30f), bad(Landmark.LOW_BACK, "Back arched", Place.LEFT, 30f)),
        "band_pull_aparts.right_depressed" to listOf(good(Landmark.SHOULDERS, "Shoulders down", Place.LEFT, 30f), good(Landmark.CHEST, "Chest tall", Place.RIGHT, 30f)),
        "first_knuckle_raises.wrong_bent_elbows" to listOf(bad(Landmark.ELBOW_L, "Elbows bend", Place.LEFT, 26f), bad(Landmark.HIPS, "Body rocks", Place.LEFT, 30f)),
        "first_knuckle_raises.right_locked" to listOf(good(Landmark.ELBOW_L, "Elbows locked", Place.LEFT, 26f), good(Landmark.WRIST_L, "Fingers and wrists only", Place.RIGHT, 24f)),
        "wrist_extensor_leans.wrong_palm_lifting" to listOf(bad(Landmark.WRIST_L, "Heel of hand lifts", Place.RIGHT, 30f), bad(Landmark.ELBOW_L, "Elbows soft", Place.LEFT, 26f)),
        "wrist_extensor_leans.right_flat_palm" to listOf(good(Landmark.WRIST_L, "Whole palm flat", Place.RIGHT, 30f), good(Landmark.ELBOW_L, "Elbows locked", Place.LEFT, 26f)),
        "palms_back_flexor_stretch.wrong_forced" to listOf(bad(Landmark.ELBOW_L, "Elbows bent", Place.LEFT, 26f), bad(Landmark.HIPS, "Hips dropped hard", Place.UP, 26f)),
        "palms_back_flexor_stretch.right_slow_pulses" to listOf(good(Landmark.ELBOW_L, "Elbows locked", Place.LEFT, 26f), good(Landmark.WRIST_L, "Mild stretch only", Place.RIGHT, 28f)),
        "dorsal_wrist_pushups.wrong_heavy_load" to listOf(bad(Landmark.WRIST_L, "All weight on knuckles", Place.RIGHT, 26f), bad(Landmark.SHOULDERS, "Leaning too far", Place.UP, 26f)),
        "dorsal_wrist_pushups.right_light_load" to listOf(good(Landmark.WRIST_L, "About 15% bodyweight", Place.RIGHT, 26f), good(Landmark.KNEE_L, "Weight stays in knees", Place.LEFT, 30f, 22f)),
        "scapular_pullups.wrong_bent_elbows" to listOf(bad(Landmark.ELBOW_L, "Elbows bend", Place.RIGHT, 28f), bad(Landmark.HEAD, "Chin rises", Place.LEFT, 28f)),
        "scapular_pullups.right_straight_arm" to listOf(good(Landmark.ELBOW_L, "Elbows locked", Place.RIGHT, 28f), good(Landmark.SHOULDERS, "Shoulders down", Place.LEFT, 28f)),
        "scapular_dips.wrong_sagging" to listOf(bad(Landmark.ELBOW_L, "Elbows bend", Place.RIGHT, 28f), bad(Landmark.SHOULDERS, "Shrugged to ears", Place.LEFT, 28f)),
        "scapular_dips.right_depressed" to listOf(good(Landmark.ELBOW_L, "Elbows locked", Place.RIGHT, 28f), good(Landmark.SHOULDERS, "Pushed down", Place.LEFT, 28f)),
        "scapular_pushups.wrong_elbow_bend" to listOf(bad(Landmark.ELBOW_L, "Elbows bend", Place.DOWN, 22f), bad(Landmark.UPPER_BACK, "Blades stuck", Place.LEFT, 28f)),
        "scapular_pushups.right_protracted" to listOf(good(Landmark.ELBOW_L, "Elbows locked", Place.UP, 26f), good(Landmark.UPPER_BACK, "Blades spread wide", Place.LEFT, 28f)),
    )
}
