package com.focusreels.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnlockEventDao {

    @Insert
    suspend fun insert(entity: UnlockEventEntity)

    @Query("SELECT * FROM unlock_events WHERE packageName = :packageName ORDER BY timestampMillis DESC")
    fun observeHistory(packageName: String): Flow<List<UnlockEventEntity>>

    @Query("SELECT COUNT(*) FROM unlock_events WHERE packageName = :packageName AND timestampMillis >= :todayStartMillis")
    fun observeCountToday(packageName: String, todayStartMillis: Long): Flow<Int>
}
