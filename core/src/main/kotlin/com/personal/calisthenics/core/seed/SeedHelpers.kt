package com.personal.calisthenics.core.seed

import com.personal.calisthenics.core.model.BodyRegion
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.ProgressionLevel

/** Red: primary target muscle. */
internal fun muscle(region: BodyRegion, note: String = "") = Highlight(region, HighlightKind.MUSCLE, note)

/** Blue: tendon under high mechanical tension. */
internal fun tendon(region: BodyRegion, note: String = "") = Highlight(region, HighlightKind.TENDON, note)

/** Yellow: joint under high mechanical tension. */
internal fun joint(region: BodyRegion, note: String = "") = Highlight(region, HighlightKind.JOINT, note)

internal fun level(name: String, description: String) = ProgressionLevel(name, description)
