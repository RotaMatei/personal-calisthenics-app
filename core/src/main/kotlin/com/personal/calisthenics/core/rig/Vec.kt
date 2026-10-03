package com.personal.calisthenics.core.rig

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
}

/**
 * World axes (centimetres): +x = the figure's right, +y = up, +z = the direction the figure faces.
 * The floor is the plane y = 0.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) Vec3(0f, 0f, 0f) else Vec3(x / l, y / l, z / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun lerp(a: Vec3, b: Vec3, t: Float): Vec3 = Vec3(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t))

internal fun lerp(a: Vec2, b: Vec2, t: Float): Vec2 = Vec2(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

internal fun rad(deg: Float): Float = deg * (PI.toFloat() / 180f)

/** Unit vector for a sagittal-plane angle measured from straight up, positive toward the facing direction. */
internal fun upDir(deg: Float): Vec3 = Vec3(0f, cos(rad(deg)), sin(rad(deg)))

/** Unit vector perpendicular to [upDir] that points out of the chest (the "front" of the torso). */
internal fun frontDir(deg: Float): Vec3 = Vec3(0f, -sin(rad(deg)), cos(rad(deg)))

/** Direction for hand / foot pitch: 0 = forward, 90 = pointing down, -90 = pointing up, 180 = backward. */
internal fun pitchDir(deg: Float): Vec3 = Vec3(0f, -sin(rad(deg)), cos(rad(deg)))
