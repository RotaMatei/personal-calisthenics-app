package com.personal.calisthenics.core.plan

enum class ChecklistGroup(val title: String, val subtitle: String) {
    NUTRITION("Tendon Nutrition Protocol", "About 45 minutes before training"),
    GEAR("Park Gear Check", "Pack before you leave"),
}

data class ChecklistItem(val id: String, val group: ChecklistGroup, val title: String, val detail: String)

/** The daily pre-workout toggles of the dashboard (stored per date in Room). */
object PreWorkoutChecklist {
    val items: List<ChecklistItem> = listOf(
        ChecklistItem(
            "collagen", ChecklistGroup.NUTRITION, "Hydrolyzed collagen",
            "10-15 g, taken about 45 minutes before the session",
        ),
        ChecklistItem(
            "vitamin_c", ChecklistGroup.NUTRITION, "Vitamin C",
            "50-100 mg alongside the collagen to support collagen synthesis",
        ),
        ChecklistItem(
            "hydration", ChecklistGroup.NUTRITION, "Hydration / creatine",
            "Water bottle filled; creatine taken if it is part of your routine",
        ),
        ChecklistItem(
            "band", ChecklistGroup.GEAR, "Light resistance band",
            "For shoulder dislocates and band pull-aparts in the warm-up",
        ),
        ChecklistItem(
            "chalk", ChecklistGroup.GEAR, "Chalk (liquid or block)",
            "Dry, secure grip without gripping harder than needed",
        ),
        ChecklistItem(
            "wraps", ChecklistGroup.GEAR, "Wrist wraps / elbow sleeves / long sleeves",
            "Neoprene wraps and sleeves; long sleeves for cold weather",
        ),
    )

    fun byGroup(group: ChecklistGroup): List<ChecklistItem> = items.filter { it.group == group }
}
