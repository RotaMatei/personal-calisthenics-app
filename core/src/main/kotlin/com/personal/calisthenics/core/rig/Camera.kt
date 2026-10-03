package com.personal.calisthenics.core.rig

import kotlin.math.cos
import kotlin.math.sin

/** Result of projecting one world point: view-plane position (cm, y up), scale factor and nearness (bigger = nearer). */
data class Projected(val x: Float, val y: Float, val scale: Float, val near: Float)

/**
 * Orbit camera looking at [target]. [yaw] 0 is the front view (camera in front of the figure), -90 the side view with
 * the figure facing right, +90 the opposite side, 180 the back view. [pitch] raises the camera above the horizon.
 * [perspective] blends orthographic (0) with full perspective (1).
 */
data class Camera(
    val yaw: Float,
    val pitch: Float = 10f,
    val target: Vec3 = Vec3(0f, 90f, 0f),
    val distance: Float = 420f,
    val perspective: Float = 0.35f,
) {
    /** Unit vector from the target toward the camera. */
    val toCamera: Vec3 = Vec3(sin(rad(yaw)) * cos(rad(pitch)), sin(rad(pitch)), cos(rad(yaw)) * cos(rad(pitch)))
    val forward: Vec3 = -toCamera
    val right: Vec3 = forward.cross(Vec3(0f, 1f, 0f)).normalized()
    val up: Vec3 = right.cross(forward)
    val position: Vec3 = target + toCamera * distance

    fun project(p: Vec3): Projected {
        val rel = p - target
        val oz = rel.dot(forward) // positive = farther from the camera than the target
        val sc = distance / (distance + perspective * oz).coerceAtLeast(distance * 0.2f)
        return Projected(rel.dot(right) * sc, rel.dot(up) * sc, sc, -oz)
    }

    fun withYaw(newYaw: Float) = copy(yaw = newYaw)

    companion object {
        /** Side view: the figure faces the right edge of the picture. */
        val SIDE = Camera(yaw = -90f, pitch = 0f, perspective = 0f)
        val FRONT = Camera(yaw = 0f, pitch = 0f, perspective = 0f)

        /** Default 3/4 view used by the looping clips. */
        val THREE_QUARTER = Camera(yaw = -38f, pitch = 12f, perspective = 0.3f)
    }
}

/** The two classic views from the spec plus the 3/4 clip view. */
enum class ViewKind(val title: String, val camera: Camera) {
    SIDE("Side view", Camera.SIDE),
    FRONT("Front view", Camera.FRONT),
    THREE_QUARTER("3/4 view", Camera.THREE_QUARTER),
}
