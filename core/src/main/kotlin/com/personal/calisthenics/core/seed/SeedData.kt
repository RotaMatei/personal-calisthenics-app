package com.personal.calisthenics.core.seed

import com.personal.calisthenics.core.model.Exercise
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.WorkoutStep

/**
 * Single entry point for all pre-loaded content: 27 encyclopedia exercises and the full 4-phase session.
 * Everything is English and immutable; user data (logs, progression state, settings) lives in Room.
 */
object SeedData {
    val exercises: List<Exercise> = ExerciseSeedPrep.all + ExerciseSeedMain.all
    val steps: List<WorkoutStep> = WorkoutSeed.all

    private val exerciseById: Map<String, Exercise> = exercises.associateBy { it.id }
    private val stepById: Map<String, WorkoutStep> = steps.associateBy { it.id }

    fun exercise(id: String): Exercise = exerciseById[id] ?: error("Unknown exercise id: $id")
    fun exerciseOrNull(id: String): Exercise? = exerciseById[id]
    fun step(id: String): WorkoutStep = stepById[id] ?: error("Unknown step id: $id")
    fun stepsIn(phase: Phase): List<WorkoutStep> = steps.filter { it.phase == phase }
}
