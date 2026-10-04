package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.rig.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A rotation (or any orthonormal frame) given by its three column axes. */
internal class Rot3(val x: Vec3, val y: Vec3, val z: Vec3) {

    fun apply(v: Vec3) = Vec3(
        x.x * v.x + y.x * v.y + z.x * v.z,
        x.y * v.x + y.y * v.y + z.y * v.z,
        x.z * v.x + y.z * v.y + z.z * v.z,
    )

    operator fun times(o: Rot3) = Rot3(apply(o.x), apply(o.y), apply(o.z))

    fun transposed() = Rot3(Vec3(x.x, y.x, z.x), Vec3(x.y, y.y, z.y), Vec3(x.z, y.z, z.z))

    /** Rotation that is [t] of the way from this frame to [o] (spherical interpolation). */
    fun slerp(o: Rot3, t: Float): Rot3 {
        val a = toQuat()
        var b = o.toQuat()
        var dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        if (dot < 0f) {
            b = floatArrayOf(-b[0], -b[1], -b[2], -b[3])
            dot = -dot
        }
        val q = if (dot > 0.9995f) {
            FloatArray(4) { a[it] + (b[it] - a[it]) * t }
        } else {
            val th = acos(dot.coerceAtMost(1f))
            val s = sin(th)
            val wa = sin((1f - t) * th) / s
            val wb = sin(t * th) / s
            FloatArray(4) { a[it] * wa + b[it] * wb }
        }
        return fromQuat(q)
    }

    private fun toQuat(): FloatArray {
        // Rotation matrix rows: m[r][c]; columns are x, y, z.
        val m00 = x.x; val m01 = y.x; val m02 = z.x
        val m10 = x.y; val m11 = y.y; val m12 = z.y
        val m20 = x.z; val m21 = y.z; val m22 = z.z
        val tr = m00 + m11 + m22
        val q = FloatArray(4) // w, x, y, z
        if (tr > 0f) {
            val s = sqrt(tr + 1f) * 2f
            q[0] = 0.25f * s; q[1] = (m21 - m12) / s; q[2] = (m02 - m20) / s; q[3] = (m10 - m01) / s
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            q[0] = (m21 - m12) / s; q[1] = 0.25f * s; q[2] = (m01 + m10) / s; q[3] = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            q[0] = (m02 - m20) / s; q[1] = (m01 + m10) / s; q[2] = 0.25f * s; q[3] = (m12 + m21) / s
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            q[0] = (m10 - m01) / s; q[1] = (m02 + m20) / s; q[2] = (m12 + m21) / s; q[3] = 0.25f * s
        }
        return q
    }

    companion object {
        val IDENTITY = Rot3(Vec3(1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f))

        private fun fromQuat(q0: FloatArray): Rot3 {
            val n = sqrt(q0[0] * q0[0] + q0[1] * q0[1] + q0[2] * q0[2] + q0[3] * q0[3])
            val w = q0[0] / n; val qx = q0[1] / n; val qy = q0[2] / n; val qz = q0[3] / n
            return Rot3(
                Vec3(1f - 2f * (qy * qy + qz * qz), 2f * (qx * qy + qz * w), 2f * (qx * qz - qy * w)),
                Vec3(2f * (qx * qy - qz * w), 1f - 2f * (qx * qx + qz * qz), 2f * (qy * qz + qx * w)),
                Vec3(2f * (qx * qz + qy * w), 2f * (qy * qz - qx * w), 1f - 2f * (qx * qx + qy * qy)),
            )
        }

        /** Frame whose y axis is [y] and whose z axis is [zHint] made perpendicular to it; x = y cross z. */
        fun fromYZ(y: Vec3, zHint: Vec3): Rot3 {
            val yn = y.normalized()
            var z = zHint - yn * zHint.dot(yn)
            if (z.length() < 1e-4f) {
                val alt = if (abs(yn.z) < 0.9f) Vec3(0f, 0f, 1f) else Vec3(1f, 0f, 0f)
                z = alt - yn * alt.dot(yn)
            }
            val zn = z.normalized()
            return Rot3(yn.cross(zn), yn, zn)
        }

        /** Frame with the given y and x axes; z = x cross y. */
        fun fromYX(y: Vec3, x: Vec3): Rot3 {
            val yn = y.normalized()
            val xn = (x - yn * x.dot(yn)).normalized()
            return Rot3(xn, yn, xn.cross(yn))
        }

        /** Rotation by [deg] degrees about the unit [axis] (right-handed). */
        fun about(axis: Vec3, deg: Float): Rot3 {
            val k = axis.normalized()
            val a = Math.toRadians(deg.toDouble()).toFloat()
            val c = cos(a)
            val s = sin(a)
            fun rot(v: Vec3) = v * c + k.cross(v) * s + k * (k.dot(v) * (1f - c))
            return Rot3(rot(Vec3(1f, 0f, 0f)), rot(Vec3(0f, 1f, 0f)), rot(Vec3(0f, 0f, 1f)))
        }
    }
}

/** One skinning transform: a point v maps to m * v + t (m is row-major 3x3). */
internal class BoneXf(val m: FloatArray, val t: FloatArray) {
    companion object {
        /** Maps the rest frame ([restO], [restR]) onto the posed one, scaling the frame's axes by [sx], [sy], [sz]. */
        fun between(restO: Vec3, restR: Rot3, posedO: Vec3, posedR: Rot3, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): BoneXf {
            val m = FloatArray(9)
            fun add(p: Vec3, r: Vec3, s: Float) {
                m[0] += s * p.x * r.x; m[1] += s * p.x * r.y; m[2] += s * p.x * r.z
                m[3] += s * p.y * r.x; m[4] += s * p.y * r.y; m[5] += s * p.y * r.z
                m[6] += s * p.z * r.x; m[7] += s * p.z * r.y; m[8] += s * p.z * r.z
            }
            add(posedR.x, restR.x, sx)
            add(posedR.y, restR.y, sy)
            add(posedR.z, restR.z, sz)
            return withOrigins(m, restO, posedO)
        }

        /** Rigid-with-scale transform given directly as a matrix: v -> posedO + m * (v - restO). */
        fun fromMatrix(m: FloatArray, restO: Vec3, posedO: Vec3): BoneXf = withOrigins(m.copyOf(), restO, posedO)

        private fun withOrigins(m: FloatArray, restO: Vec3, posedO: Vec3): BoneXf {
            val t = floatArrayOf(
                posedO.x - (m[0] * restO.x + m[1] * restO.y + m[2] * restO.z),
                posedO.y - (m[3] * restO.x + m[4] * restO.y + m[5] * restO.z),
                posedO.z - (m[6] * restO.x + m[7] * restO.y + m[8] * restO.z),
            )
            return BoneXf(m, t)
        }

        /** Matrix of a rotation [r] followed by a uniform scale [s]. */
        fun matrix(r: Rot3, s: Float = 1f): FloatArray = floatArrayOf(
            r.x.x * s, r.y.x * s, r.z.x * s,
            r.x.y * s, r.y.y * s, r.z.y * s,
            r.x.z * s, r.y.z * s, r.z.z * s,
        )
    }
}
