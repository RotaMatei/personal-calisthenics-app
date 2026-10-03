package com.personal.calisthenicsguide.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One workout session. [endedAtMs] stays null until the session is finished, so abandoned sessions never count. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    /** WorkoutDay label: "A", "B" or "C". */
    val day: String,
    val deload: Boolean,
    val coldMode: Boolean,
    /** Post-workout joint ratings 1..5, null until logged. */
    val wrists: Int? = null,
    val elbows: Int? = null,
    val shoulders: Int? = null,
)

/** One logged set (or one leg of a set). */
@Entity(
    tableName = "set_logs",
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val epochMs: Long,
    val exerciseId: String,
    /** MovementCategory name. */
    val category: String,
    val stepId: String,
    val setNumber: Int,
    /** Side name for per-leg exercises, otherwise null. */
    val side: String?,
    val reps: Int?,
    val holdSeconds: Int?,
    val rir: Int?,
    val levelIndex: Int,
)

/** Progression ladder level and the three stage checkboxes of one exercise. */
@Entity(tableName = "progression")
data class ProgressionEntity(
    @PrimaryKey val exerciseId: String,
    val levelIndex: Int,
    val stageMask: Int,
)

/** Small key/value store for settings (block start date, cold mode, last chosen day...). */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Pre-workout checklist toggles, one row per day and item. */
@Entity(tableName = "checklist", primaryKeys = ["date", "itemId"])
data class ChecklistEntity(
    /** ISO date, e.g. 2026-10-03. */
    val date: String,
    val itemId: String,
    val done: Boolean,
)

/** A GIF / MP4 the user attached to an exercise to replace the built-in 3D clip. */
@Entity(tableName = "media_overrides")
data class MediaOverrideEntity(
    @PrimaryKey val exerciseId: String,
    val uri: String,
    val mimeType: String,
)
