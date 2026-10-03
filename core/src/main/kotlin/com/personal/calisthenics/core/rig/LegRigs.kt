package com.personal.calisthenics.core.rig

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Leg and hip work plus the standing stretches: pistol squat, nordic curl, deep squat, Jefferson curl, forearm stretch. */
internal object LegRigs {

    private val floor = RigScene()
    private val nordicScene = RigScene(
        equipment = listOf(
            Cylinder(Vec3(-34f, 12.5f, -43f), Vec3(34f, 12.5f, -43f), 2.2f),
            Box(Vec3(-38f, 0f, -46f), Vec3(-32f, 12f, -40f)),
            Box(Vec3(32f, 0f, -46f), Vec3(38f, 12f, -40f)),
        ),
    )
    private val benchScene = RigScene(equipment = listOf(Box(Vec3(-34f, 0f, -24f), Vec3(34f, 40f, 30f))))

    private fun kf(p: Pose, hold: Long = 500, move: Long = 1200) = Keyframe(p, hold, move)

    fun all(): Map<String, ExerciseRig> = mapOf(
        "pistol_squat" to pistolSquat(),
        "nordic_curl" to nordicCurl(),
        "prying_deep_squat" to pryingDeepSquat(),
        "jefferson_curl" to jeffersonCurl(),
        "forearm_flexor_extensor_stretch" to forearmStretch(),
    )

    // ------------------------------------------------------------------ pistol squat

    private fun pistol(
        hipY: Float,
        hipZ: Float,
        lean: Float,
        head: Float,
        ankleY: Float = 8f,
        footPitch: Float = 0f,
        kneePole: Vec3 = Vec3(-0.1f, 0f, 1f),
        armsUp: Float = 0f,
    ): Pose {
        val hip = Vec3(0f, hipY, hipZ)
        val freeFoot = Vec3(10f, hipY - 6f, hipZ + 86.2f)
        val supportFoot = Limb(Vec3(-10f, ankleY, 0f), kneePole, footPitch, contact = ankleY < 9f)
        val free = Limb(freeFoot, Vec3(0f, 1f, 0f), -35f)
        val shoulderY = hipY + 54f * cos(rad(lean))
        val shoulderZ = hipZ + 54f * sin(rad(lean))
        val handsY = shoulderY - 6f + armsUp
        return Kit.standing(
            hip = hip, lean = lean, head = head,
            hands = sym(15f, handsY, shoulderZ + 44f, Poles.DOWN, 0f),
            feet = supportFoot to free,
        )
    }

    private fun pistolSquat(): ExerciseRig {
        val stand = pistol(Kit.STAND_HIP_Y, 0f, lean = 4f, head = 0f)
        val bottom = pistol(32f, -18f, lean = 42f, head = -14f)
        val wrong = pistol(32f, -18f, lean = 42f, head = -14f, ankleY = 18f, footPitch = 48f, kneePole = Vec3(0.38f, 0f, 1f))
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(stand, 500, 1500), kf(bottom, 600, 1200))),
            mapOf(
                "pistol_squat.wrong_valgus" to RigStill(floor, wrong),
                "pistol_squat.right_tracking" to RigStill(floor, bottom),
            ),
        )
    }

    // ------------------------------------------------------------------ nordic curl

    private fun nordic(thighDeg: Float, torsoLean: Float, handsDown: Boolean, head: Float = 0f, look: Float = 0f): Pose {
        val knee = Vec3(0f, Kit.KNEE_FLOOR_Y, 0f)
        val hip = Vec3(0f, knee.y + Body.THIGH * cos(rad(thighDeg)), Body.THIGH * sin(rad(thighDeg)))
        val shoulder = Kit.shoulderOf(hip, torsoLean)
        val hands = if (handsDown) {
            sym(19f, Kit.WRIST_FLOOR + 1f, shoulder.z + 8f, Poles.OUT_BACK, 0f, false)
        } else {
            sym(17f, shoulder.y - 12f, shoulder.z + 26f, Poles.DOWN, 0f)
        }
        return Kit.standing(
            hip = hip, lean = torsoLean, head = head, look = look, hands = hands,
            feet = Kit.feet(Vec3(0f, Kit.KNEE_FLOOR_Y + 0.6f, -43f), 9f, Poles.DOWN, 175f, contact = false),
        )
    }

    private fun nordicCurl(): ExerciseRig {
        val up = nordic(0f, 0f, handsDown = false)
        val down = nordic(70f, 70f, handsDown = true, head = 4f)
        val wrong = nordic(0f, 72f, handsDown = false, head = 4f)
        val right = nordic(55f, 55f, handsDown = false, head = 0f)
        return ExerciseRig(
            RigAnimation(nordicScene, listOf(kf(up, 500, 2200), kf(down, 600, 1200))),
            mapOf(
                "nordic_curl.wrong_hip_pike" to RigStill(nordicScene, wrong),
                "nordic_curl.right_straight_line" to RigStill(nordicScene, right),
            ),
        )
    }

    // ------------------------------------------------------------------ prying deep squat

    private fun deepSquat(
        hipY: Float,
        hipZ: Float,
        lean: Float,
        ankleY: Float = 8f,
        footPitch: Float = 0f,
        kneePole: PoleSpec = Poles.FWD_OUT,
        head: Float = -8f,
    ): Pose {
        val shoulderY = hipY + 54f * cos(rad(lean))
        val shoulderZ = hipZ + 54f * sin(rad(lean))
        return Kit.standing(
            hip = Vec3(0f, hipY, hipZ), lean = lean, head = head,
            hands = sym(4f, shoulderY - 16f, shoulderZ + 22f, Poles.OUT_DOWN, -20f),
            feet = sym(25f, ankleY, 0f, kneePole, footPitch, contact = ankleY < 9f),
        )
    }

    private fun pryingDeepSquat(): ExerciseRig {
        val stand = Kit.standing(
            hands = sym(4f, 132f, 22f, Poles.OUT_DOWN, -20f),
            feet = sym(22f, 8f, 0f, Poles.FWD_OUT, 0f, true),
            hip = Vec3(0f, 93.8f, 0f),
        )
        val bottom = deepSquat(30f, -14f, 28f)
        val wrong = deepSquat(32f, -14f, 28f, ankleY = 18f, footPitch = 50f, kneePole = PoleSpec(-0.35f, 0f, 1f))
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(stand, 400, 1800), kf(bottom, 1500, 1800))),
            mapOf(
                "prying_deep_squat.wrong_heels_up" to RigStill(floor, wrong),
                "prying_deep_squat.right_heels_down" to RigStill(floor, bottom),
            ),
        )
    }

    // ------------------------------------------------------------------ jefferson curl

    private fun jefferson(hipZ: Float, lean: Float, flex: Float, head: Float, handsOut: Float = 0f): Pose {
        val ankle = Vec3(0f, 48f, 20f)
        val hipY = ankle.y + sqrt(Kit.LEG * Kit.LEG - (hipZ - ankle.z) * (hipZ - ankle.z))
        val hip = Vec3(0f, hipY, hipZ)
        val shoulder = Kit.shoulderOf(hip, lean, flex)
        return Kit.standing(
            hip = hip, lean = lean, flex = flex, head = head,
            hands = sym(22f, shoulder.y - 52f, shoulder.z + 4f + handsOut, Poles.BACK, 90f),
            feet = Kit.feet(ankle, 9f, Poles.FWD, 0f, contact = true),
        )
    }

    private fun jeffersonCurl(): ExerciseRig {
        val tall = jefferson(hipZ = 18f, lean = 0f, flex = 0f, head = 0f)
        val curled = jefferson(hipZ = 6f, lean = 78f, flex = 62f, head = 22f)
        val wrong = jefferson(hipZ = 14f, lean = 52f, flex = 98f, head = 24f, handsOut = 6f)
        return ExerciseRig(
            RigAnimation(benchScene, listOf(kf(tall, 500, 2600), kf(curled, 1200, 2600))),
            mapOf(
                "jefferson_curl.wrong_fast_bounce" to RigStill(benchScene, wrong),
                "jefferson_curl.right_segmental" to RigStill(benchScene, curled),
            ),
        )
    }

    // ------------------------------------------------------------------ forearm stretch

    private fun stretch(fingersDeg: Float, elbowBent: Boolean, pull: Float): Pose {
        val shoulderJoint = Vec3(-19f, Kit.STAND_HIP_Y + 54f, 0f)
        val wrist = if (elbowBent) Vec3(-19f, shoulderJoint.y - 14f, 30f) else Vec3(-17f, shoulderJoint.y - 0.5f, 54.5f)
        val tip = wrist + Vec3(0f, -sin(rad(fingersDeg)), cos(rad(fingersDeg))) * Body.HAND
        val helper = Vec3(-12f, tip.y + (if (fingersDeg < 0f) 1f else -1f) * 2f, tip.z + 6f + pull)
        return Kit.standing(
            hands = Limb(wrist, Vec3(0f, -1f, 0f), fingersDeg, false) to Limb(Vec3(-helper.x, helper.y, helper.z), Vec3(0.3f, -1f, 0f), 0f),
            head = 4f,
        ).let { it }
    }

    private fun forearmStretch(): ExerciseRig {
        val flexor = stretch(-72f, elbowBent = false, pull = 0f)
        val extensor = stretch(72f, elbowBent = false, pull = 0f)
        val wrong = stretch(-85f, elbowBent = true, pull = 6f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(flexor, 1500, 1500), kf(extensor, 1500, 1500))),
            mapOf(
                "forearm_flexor_extensor_stretch.wrong_yank" to RigStill(floor, wrong),
                "forearm_flexor_extensor_stretch.right_gentle" to RigStill(floor, flexor),
            ),
        )
    }
}
