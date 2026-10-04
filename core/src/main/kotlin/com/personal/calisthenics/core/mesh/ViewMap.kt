package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.rig.Bounds
import kotlin.math.min

/**
 * Maps rig view centimetres (y up) into a picture of [w] x [h] pixels: the [bounds] rectangle is centred and scaled
 * uniformly to fit inside the padding. Same mapping as the Compose `ViewFit`, so labels line up with the mesh picture.
 */
class ViewMap(val bounds: Bounds, val w: Float, val h: Float, pad: Float = 0f) {
    val scale: Float = min((w - 2 * pad) / bounds.width, (h - 2 * pad) / bounds.height)
    val ox: Float = (w - bounds.width * scale) / 2f
    val oy: Float = (h - bounds.height * scale) / 2f

    fun x(v: Float) = ox + (v - bounds.minX) * scale
    fun y(v: Float) = oy + (bounds.maxY - v) * scale
    fun r(v: Float) = v * scale
}
