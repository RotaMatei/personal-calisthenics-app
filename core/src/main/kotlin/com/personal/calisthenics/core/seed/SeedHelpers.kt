package com.personal.calisthenics.core.seed

import com.personal.calisthenics.core.model.BodyRegion
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.model.Load
import com.personal.calisthenics.core.model.PeakAt
import com.personal.calisthenics.core.model.ProgressionLevel

/** Red: a muscle that works in the exercise ([load] says how hard, [peak] where in the rep it works hardest). */
internal fun muscle(region: BodyRegion, load: Load = Load.PRIMARY, peak: PeakAt = PeakAt.STEADY, note: String = "") =
    Highlight(region, HighlightKind.MUSCLE, note, load, peak)

/** Blue: a tendon under mechanical tension. */
internal fun tendon(region: BodyRegion, load: Load = Load.PRIMARY, peak: PeakAt = PeakAt.STEADY, note: String = "") =
    Highlight(region, HighlightKind.TENDON, note, load, peak)

/** Yellow: a joint under mechanical tension. */
internal fun joint(region: BodyRegion, load: Load = Load.PRIMARY, peak: PeakAt = PeakAt.STEADY, note: String = "") =
    Highlight(region, HighlightKind.JOINT, note, load, peak)

internal fun level(name: String, description: String) = ProgressionLevel(name, description)
