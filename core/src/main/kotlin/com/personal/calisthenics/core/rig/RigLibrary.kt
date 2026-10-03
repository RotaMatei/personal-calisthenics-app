package com.personal.calisthenics.core.rig

/**
 * Registry of every exercise demonstration and DO/DON'T still. Keys match [com.personal.calisthenics.core.seed.SeedData]:
 * an animation per exercise id, and stills named `<exerciseId>.<name>` (plus `detail.*` close-ups handled by [DetailArt]).
 */
object RigLibrary {

    private val rigs: Map<String, ExerciseRig> by lazy {
        buildMap {
            putAll(PrepRigs.all())
            putAll(FloorRigs.all())
            putAll(BarRigs.all())
            putAll(LegRigs.all())
        }
    }

    private val stills: Map<String, RigStill> by lazy {
        buildMap { rigs.values.forEach { putAll(it.stills) } }
    }

    val animationIds: Set<String> get() = rigs.keys

    val stillKeys: Set<String> get() = stills.keys

    fun animationOrNull(exerciseId: String): RigAnimation? = rigs[exerciseId]?.animation

    fun animation(exerciseId: String): RigAnimation =
        animationOrNull(exerciseId) ?: error("No rig animation for exercise '$exerciseId'")

    fun still(key: String): RigStill? = stills[key]

    /** True when [key] (a DO/DON'T pose key) can be drawn, either as a body still or as a close-up. */
    fun hasArt(key: String): Boolean = key in stills || key in DetailArt.keys
}

/** Checks that contact limbs (hands on bars / floor, feet on the floor) actually reach their targets. */
object RigValidation {
    const val TOLERANCE_CM = 1.5f

    /** Contact limbs whose IK end is farther than [TOLERANCE_CM] from the requested target. */
    fun contactErrors(pose: Pose): Map<LimbId, Float> {
        val sk = RigSolver.solve(pose)
        val contacts = mapOf(
            LimbId.HAND_L to pose.handL.contact,
            LimbId.HAND_R to pose.handR.contact,
            LimbId.FOOT_L to pose.footL.contact,
            LimbId.FOOT_R to pose.footR.contact,
        )
        return sk.reachError.filter { (id, err) -> contacts.getValue(id) && err > TOLERANCE_CM }
    }

    /** Lowest point of the body below the floor plane (positive number = penetration in cm). */
    fun floorPenetration(pose: Pose, floorY: Float = 0f): Float {
        val sk = RigSolver.solve(pose)
        val points = listOf(
            sk.kneeL to 6.2f, sk.kneeR to 6.2f, sk.toeL to 3.2f, sk.toeR to 3.2f, sk.heelL to 4f, sk.heelR to 4f,
            sk.handTipL to 3f, sk.handTipR to 3f, sk.wristL to 3.6f, sk.wristR to 3.6f, sk.headCenter to 10f,
            sk.hip to 10f, sk.shoulder to 12f, sk.elbowL to 4.4f, sk.elbowR to 4.4f,
        )
        return points.maxOf { (p, r) -> floorY - (p.y - r) }.coerceAtLeast(0f)
    }
}
