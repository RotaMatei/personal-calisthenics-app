package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.rig.Body
import com.personal.calisthenics.core.rig.FigureBuilder
import com.personal.calisthenics.core.rig.HandFrame
import com.personal.calisthenics.core.rig.HandRig
import com.personal.calisthenics.core.rig.HandSetup
import com.personal.calisthenics.core.rig.Limb
import com.personal.calisthenics.core.rig.Pose
import com.personal.calisthenics.core.rig.RigScene
import com.personal.calisthenics.core.rig.Skeleton
import com.personal.calisthenics.core.rig.Vec3
import com.personal.calisthenics.core.rig.lerp
import com.personal.calisthenics.core.rig.pitchDir
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turns a solved [Skeleton] into skinning transforms for the [HumanMesh].
 *
 * Every skin bone has a rest frame (taken from the mesh's own joint landmarks) and a posed frame (taken from the rig),
 * and the vertex transform is "go to the bone's local axes, stretch the bone to the rig's segment length, come back
 * out in the posed frame". Limbs get their bend plane from the IK pole, so elbows and knees fold the right way even
 * when the limb is straight; the forearm twist is split between the forearm and the hand.
 */
internal class HumanRig(private val mesh: HumanMesh) {

    private val index = mesh.boneByName
    private val z0 = Vec3(0f, 0f, 1f)
    private val up0 = Vec3(0f, 1f, 0f)

    // ------------------------------------------------------------------ rest landmarks
    private val hipC = mesh.landmark("hipC")
    private val shoulderC = mesh.landmark("shoulderC")
    private val headPivot = mesh.landmark("headPivot")
    private val headTop = mesh.landmark("headTop")
    private val headC = mesh.landmark("headC")
    private val midC = lerp(hipC, shoulderC, Body.TORSO_LOWER / (Body.TORSO_LOWER + Body.TORSO_UPPER))
    private val spineRest = Rot3.fromYZ(shoulderC - hipC, z0)
    private val neckRest = Rot3.fromYZ(headPivot - shoulderC, z0)
    private val headRest = Rot3.fromYZ(headTop - headPivot, z0)

    /** Where the head's centre sits along its own axis, measured from the pivot (cm). */
    private val headCentreOffset = (headC - headPivot).dot(headRest.y)

    /** Rest data of one body side (P = +x, N = -x). */
    private inner class Side(val tag: String) {
        val shoulder = mesh.landmark("shoulder$tag")
        val elbow = mesh.landmark("elbow$tag")
        val wrist = mesh.landmark("wrist$tag")
        val knuckle = mesh.landmark("knuckle$tag")
        val hip = mesh.landmark("hip$tag")
        val knee = mesh.landmark("knee$tag")
        val ankle = mesh.landmark("ankle$tag")
        val toeBall = mesh.landmark("toeBall$tag")
        val palmN = mesh.landmark("palmN$tag")

        val armApex: Vec3 = run {
            val dir = (wrist - shoulder).normalized()
            val off = elbow - shoulder
            off - dir * off.dot(dir)
        }
        val legApex = z0

        val handRest = Rot3.fromYZ(knuckle - wrist, palmN)
        val footRest: Rot3 = (toeBall - ankle).let { f -> Rot3.fromYZ(up0, Vec3(f.x, 0f, f.z)) }
        val finger: List<List<Vec3>> = (1..5).map { k -> (1..4).map { j -> mesh.landmark("f$k$j$tag") } }
    }

    private val sideP = Side("P")
    private val sideN = Side("N")

    private fun bone(name: String): Int = index[name] ?: error("Unknown skin bone $name")

    // ------------------------------------------------------------------ posing

    /** Skinning transforms for every skin bone, indexed like [HumanMesh.boneNames]. */
    fun bones(pose: Pose, sk: Skeleton, scene: RigScene): Array<BoneXf> {
        val identity = BoneXf.between(Vec3.ZERO, Rot3.IDENTITY, Vec3.ZERO, Rot3.IDENTITY)
        val out = Array(mesh.boneNames.size) { identity }

        // Torso. The pelvis is as wide as the rig's hips, the chest as wide as its shoulders.
        val hipHalf = mesh.landmark("hipP").x
        val shoulderHalf = mesh.landmark("shoulderP").x
        val lowLen = (shoulderC - hipC).length() * Body.TORSO_LOWER / (Body.TORSO_LOWER + Body.TORSO_UPPER)
        val upLen = (shoulderC - hipC).length() - lowLen
        out[bone("lower")] = BoneXf.between(
            hipC, spineRest, sk.hip, Rot3.fromYZ(sk.lowUp, sk.lowFront),
            Body.HIP_HALF / hipHalf, Body.TORSO_LOWER / lowLen, 1f,
        )
        out[bone("upper")] = BoneXf.between(
            midC, spineRest, sk.mid, Rot3.fromYZ(sk.up, sk.front),
            Body.SHOULDER_HALF / shoulderHalf, Body.TORSO_UPPER / upLen, 1f,
        )

        // Neck and head. The head keeps its natural size; the neck takes up the difference to the rig's proportions.
        val headPivotPosed = sk.headCenter - sk.headUp * headCentreOffset
        val neckVec = headPivotPosed - sk.shoulder
        val neckFront = (sk.front + sk.faceDir).normalized()
        out[bone("neck")] = BoneXf.between(
            shoulderC, neckRest, sk.shoulder, Rot3.fromYZ(neckVec, neckFront),
            1f, neckVec.length() / (headPivot - shoulderC).length(), 1f,
        )
        out[bone("head")] = BoneXf.between(headPivot, headRest, headPivotPosed, Rot3.fromYZ(sk.headUp, sk.faceDir))

        limbs(out, sideP, "P", pose.handR, pose.footR, right = true, sk = sk, scene = scene)
        limbs(out, sideN, "N", pose.handL, pose.footL, right = false, sk = sk, scene = scene)
        return out
    }

    private fun limbs(out: Array<BoneXf>, s: Side, tag: String, handLimb: Limb, footLimb: Limb, right: Boolean, sk: Skeleton, scene: RigScene) {
        val shoulderJ = if (right) sk.shoulderR else sk.shoulderL
        val elbowJ = if (right) sk.elbowR else sk.elbowL
        val wristJ = if (right) sk.wristR else sk.wristL
        val hipJ = if (right) sk.hipR else sk.hipL
        val kneeJ = if (right) sk.kneeR else sk.kneeL
        val ankleJ = if (right) sk.ankleR else sk.ankleL
        val armBend = if (right) sk.armBendR else sk.armBendL
        val legBend = if (right) sk.legBendR else sk.legBendL

        // Clavicle: from the shoulder centre out to the shoulder joint (shrug moves it up).
        val clavVec = shoulderJ - sk.shoulder
        val clavRestVec = s.shoulder - shoulderC
        out[bone("clav$tag")] = BoneXf.between(
            shoulderC, Rot3.fromYZ(clavRestVec, z0), sk.shoulder, Rot3.fromYZ(clavVec, sk.front),
            1f, clavVec.length() / clavRestVec.length(), 1f,
        )

        // Arm: upper arm and forearm share the bend-plane normal.
        val (armUpRest, foreRest) = limbFrames(s.shoulder, s.elbow, s.wrist, s.armApex)
        val (armUpPosed, forePosed) = limbFrames(shoulderJ, elbowJ, wristJ, armBend)
        val upperArmLen = (s.elbow - s.shoulder).length()
        val foreLen = (s.wrist - s.elbow).length()
        out[bone("armUp$tag")] = BoneXf.between(
            s.shoulder, armUpRest, shoulderJ, armUpPosed, 1f, Body.UPPER_ARM / upperArmLen, 1f,
        )
        val foreScale = Body.FOREARM / foreLen
        out[bone("fore$tag")] = BoneXf.between(s.elbow, foreRest, elbowJ, forePosed, 1f, foreScale, 1f)

        // Hand: orientation from the rig's hand frame; the forearm twist is shared half and half.
        val setup = FigureBuilder.handSetup(handLimb, wristJ, right, scene)
        val handPosed = Rot3.fromYZ(setup.frame.d, setup.frame.n)
        val twistRest = foreRest.slerp(s.handRest, 0.5f)
        val twistPosed = forePosed.slerp(handPosed, 0.5f)
        out[bone("twist$tag")] = BoneXf.between(
            lerp(s.elbow, s.wrist, 0.5f), twistRest, lerp(elbowJ, wristJ, 0.5f), twistPosed, 1f, foreScale, 1f,
        )
        val handScale = HAND_SCALE
        out[bone("hand$tag")] = BoneXf.between(s.wrist, s.handRest, wristJ, handPosed, handScale, handScale, handScale)
        fingers(out, s, tag, handPosed, setup, wristJ, handScale)

        // Leg.
        val (thighRest, shinRest) = limbFrames(s.hip, s.knee, s.ankle, s.legApex)
        val (thighPosed, shinPosed) = limbFrames(hipJ, kneeJ, ankleJ, legBend)
        out[bone("thigh$tag")] = BoneXf.between(
            s.hip, thighRest, hipJ, thighPosed, 1f, Body.THIGH / (s.knee - s.hip).length(), 1f,
        )
        out[bone("shin$tag")] = BoneXf.between(
            s.knee, shinRest, kneeJ, shinPosed, 1f, Body.SHIN / (s.ankle - s.knee).length(), 1f,
        )

        // Foot: pitch (toes up / down) and yaw (toes out) come from the pose; it stands on the floor, not on the ankle.
        val sign = if (right) 1f else -1f
        val yaw = footLimb.yaw * sign
        val f = rotY(pitchDir(footLimb.pitch), yaw)
        val up = rotY(pitchDir(footLimb.pitch - 90f), yaw)
        val footPosed = Rot3(up.cross(f).normalized(), up, f)
        val drop = (Body.ANKLE_HEIGHT - s.ankle.y).coerceAtLeast(0f)
        out[bone("foot$tag")] = BoneXf.between(s.ankle, s.footRest, ankleJ - up * drop, footPosed)
    }

    /** Hand frame helper: right-handed hand frames are built from the rig's finger direction and palm normal. */
    private fun fingers(out: Array<BoneXf>, s: Side, tag: String, handPosed: Rot3, setup: HandSetup, wristJ: Vec3, hs: Float) {
        val handM = BoneXf.between(s.wrist, s.handRest, wristJ, handPosed, hs, hs, hs)
        val a = handPosed * s.handRest.transposed() // pure rotation: rest hand -> posed hand
        val f: HandFrame = setup.frame
        val curlAxis = f.d.cross(f.n).normalized()
        for (k in 1..5) {
            val joints = s.finger[k - 1]
            val flex = if (k == 1) HandRig.thumbFlex(setup.shape, setup.bar != null) else HandRig.fingerFlex(f, setup.shape, setup.bar, k - 2)
            var posed = handM.apply(joints[0])
            var cum = 0f
            for (j in 0 until 3) {
                cum += flex[j]
                val r = Rot3.about(curlAxis, cum) * a
                val m = BoneXf.matrix(r, hs)
                out[bone("f$k${j + 1}$tag")] = BoneXf.fromMatrix(m, joints[j], posed)
                val seg = joints[j + 1] - joints[j]
                posed += Vec3(
                    m[0] * seg.x + m[1] * seg.y + m[2] * seg.z,
                    m[3] * seg.x + m[4] * seg.y + m[5] * seg.z,
                    m[6] * seg.x + m[7] * seg.y + m[8] * seg.z,
                )
            }
        }
    }

    private fun BoneXf.apply(p: Vec3) = Vec3(
        m[0] * p.x + m[1] * p.y + m[2] * p.z + t[0],
        m[3] * p.x + m[4] * p.y + m[5] * p.z + t[1],
        m[6] * p.x + m[7] * p.y + m[8] * p.z + t[2],
    )

    /**
     * Upper and lower bone frames of a two-bone limb: y runs along the bone, x is the normal of the bend plane (shared
     * by both bones) and z completes the frame, so the bend direction is the same in the rest and the posed limb.
     */
    private fun limbFrames(root: Vec3, mid: Vec3, end: Vec3, apex: Vec3): Pair<Rot3, Rot3> {
        val dir = (end - root).normalized()
        val perp = (apex - dir * apex.dot(dir)).normalized()
        val normal = dir.cross(perp).normalized()
        fun frame(y: Vec3): Rot3 {
            val yn = y.normalized()
            val zn = normal.cross(yn).normalized()
            return Rot3(normal, yn, zn)
        }
        return frame(mid - root) to frame(end - mid)
    }

    private fun rotY(v: Vec3, deg: Float): Vec3 {
        val a = Math.toRadians(deg.toDouble()).toFloat()
        return Vec3(v.x * cos(a) + v.z * sin(a), v.y, -v.x * sin(a) + v.z * cos(a))
    }

    companion object {
        /** Hands are scaled to match the rig's grip geometry (the mesh hand is larger than the rig's). */
        const val HAND_SCALE = 0.86f
    }
}
