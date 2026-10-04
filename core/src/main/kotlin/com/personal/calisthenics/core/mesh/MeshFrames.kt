package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.toDraws

/** Draws the clips and DO/DON'T stills of the rig library with the human mesh instead of the capsule figure. */
object MeshFrames {

    /** One frame of [player]'s clip at [timeMs] seen from [camera] into [out] (see [MeshRenderer.render]). */
    fun clip(
        renderer: MeshRenderer,
        player: ClipPlayer,
        timeMs: Long,
        camera: Camera,
        view: ViewMap,
        out: IntArray,
        options: MeshOptions = MeshOptions(),
    ) {
        val animation = player.animation
        val draws = player.highlights.toDraws(animation.progressAt(timeMs))
        renderer.render(animation.scene, animation.poseAt(timeMs), camera, draws, view, out, options)
    }

    /** The DO/DON'T still [key] (every highlight at its peak) into [out]; false when there is no such still. */
    fun still(
        renderer: MeshRenderer,
        key: String,
        camera: Camera,
        highlights: List<Highlight>,
        view: ViewMap,
        out: IntArray,
        options: MeshOptions = MeshOptions(),
    ): Boolean {
        val still = RigLibrary.still(key) ?: return false
        renderer.render(still.scene, still.pose, camera, highlights.toDraws(), view, out, options)
        return true
    }
}
