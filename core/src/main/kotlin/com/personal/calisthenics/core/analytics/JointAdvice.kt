package com.personal.calisthenics.core.analytics

import com.personal.calisthenics.core.model.Grip

data class NextSessionAdvice(
    /** True when the pull-up slot of the next session must use a neutral grip. */
    val forceNeutralGrip: Boolean,
    val messages: List<String>,
) {
    /** Grip to use for the next session given the grip its day would normally use. */
    fun gripFor(normal: Grip): Grip = if (forceNeutralGrip) Grip.NEUTRAL else normal
}

/** Reads the post-workout joint log and decides how the next session should adapt. */
object JointAdvice {
    /** Ratings above this value (that is, 3/5 and up) count as stiffness. */
    const val STIFFNESS_THRESHOLD = 2

    fun evaluate(last: JointRating?): NextSessionAdvice {
        if (last == null) return NextSessionAdvice(false, emptyList())
        val messages = mutableListOf<String>()
        var neutral = false
        if (last.elbows > STIFFNESS_THRESHOLD) {
            neutral = true
            messages += "Inner-elbow stiffness logged at ${last.elbows}/5: next session switches pull-ups to a Neutral Grip " +
                "to unload the medial elbow."
        }
        if (last.wrists > STIFFNESS_THRESHOLD) {
            messages += "Wrist stiffness at ${last.wrists}/5: spend an extra round on the floor wrist prep and keep leans light."
        }
        if (last.shoulders > STIFFNESS_THRESHOLD) {
            messages += "Shoulder soreness at ${last.shoulders}/5: keep pike push-ups and dips shallow and stop at parallel."
        }
        return NextSessionAdvice(neutral, messages)
    }
}
