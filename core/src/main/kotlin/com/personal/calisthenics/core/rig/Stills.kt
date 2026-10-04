package com.personal.calisthenics.core.rig

import com.personal.calisthenics.core.model.Highlight

/** A DO/DON'T picture ready to paint: the body and props (back to front) plus the arrow labels drawn on top. */
class StillFrame(val prims: List<Prim>, val overlay: List<AnnotationDraw>, val camera: Camera)

/** Renders the labelled DO/DON'T stills of [RigLibrary]. */
object Stills {

    /** The still [key] from [camera], or null if there is no such still (detail close-ups use [DetailArt]). */
    fun render(
        key: String,
        camera: Camera = Camera.SIDE,
        highlights: List<Highlight> = emptyList(),
        options: RenderOptions = RenderOptions(),
    ): StillFrame? {
        val still = RigLibrary.still(key) ?: return null
        val prims = RigRenderer.render(still.scene, still.pose, camera, highlights.toDraws(), options)
        val overlay = Annotations.layout(Annotations.forStill(key), still.pose, camera)
        return StillFrame(prims, overlay, camera)
    }

    /** Only the labels of the still [key] (cheap), for views that draw the figure some other way; null if there is no such still. */
    fun overlay(key: String, camera: Camera = Camera.SIDE): List<AnnotationDraw>? {
        val still = RigLibrary.still(key) ?: return null
        return Annotations.layout(Annotations.forStill(key), still.pose, camera)
    }

    /**
     * One view rectangle that fits every still in [keys] (body, props and label boxes) from [camera], so a wrong/right
     * pair is drawn at the same scale.
     */
    fun bounds(keys: List<String>, camera: Camera, margin: Float = 10f): Bounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (key in keys) {
            val still = RigLibrary.still(key) ?: continue
            val body = RigFraming.bounds(RigAnimation(still.scene, listOf(Keyframe(still.pose, 0, 1), Keyframe(still.pose, 0, 1))), camera, 0f)
            minX = minOf(minX, body.minX); minY = minOf(minY, body.minY)
            maxX = maxOf(maxX, body.maxX); maxY = maxOf(maxY, body.maxY)
            for (label in Annotations.layout(Annotations.forStill(key), still.pose, camera)) {
                val b = label.box()
                minX = minOf(minX, b.minX); minY = minOf(minY, b.minY)
                maxX = maxOf(maxX, b.maxX); maxY = maxOf(maxY, b.maxY)
            }
        }
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }
}
