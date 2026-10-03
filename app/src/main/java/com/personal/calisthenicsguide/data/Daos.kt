package com.personal.calisthenicsguide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endedAtMs IS NOT NULL ORDER BY endedAtMs DESC")
    fun completed(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions WHERE endedAtMs IS NULL AND id NOT IN (SELECT DISTINCT sessionId FROM set_logs)")
    suspend fun deleteEmptyUnfinished()
}

@Dao
interface SetLogDao {
    @Insert
    suspend fun insert(set: SetLogEntity): Long

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM set_logs WHERE sessionId IN (SELECT id FROM sessions WHERE endedAtMs IS NOT NULL) ORDER BY epochMs")
    fun allCompleted(): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY epochMs")
    suspend fun forSession(sessionId: Long): List<SetLogEntity>

    @Query(
        "SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND sessionId IN " +
            "(SELECT id FROM sessions WHERE endedAtMs IS NOT NULL) ORDER BY epochMs DESC LIMIT :limit",
    )
    suspend fun recentForExercise(exerciseId: String, limit: Int): List<SetLogEntity>
}

@Dao
interface ProgressionDao {
    @Query("SELECT * FROM progression")
    fun all(): Flow<List<ProgressionEntity>>

    @Query("SELECT * FROM progression WHERE exerciseId = :exerciseId")
    suspend fun get(exerciseId: String): ProgressionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressionEntity)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun all(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist WHERE date = :date")
    fun forDate(date: String): Flow<List<ChecklistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ChecklistEntity)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_overrides")
    fun all(): Flow<List<MediaOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: MediaOverrideEntity)

    @Query("DELETE FROM media_overrides WHERE exerciseId = :exerciseId")
    suspend fun delete(exerciseId: String)
}
