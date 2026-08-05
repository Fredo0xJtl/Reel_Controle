package com.focusreels.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockAttemptDao {

    @Insert
    suspend fun insert(entity: BlockAttemptEntity)

    @Query("SELECT * FROM block_attempts WHERE packageName = :packageName ORDER BY timestampMillis DESC")
    fun observeHistory(packageName: String): Flow<List<BlockAttemptEntity>>

    @Query("SELECT COUNT(*) FROM block_attempts WHERE packageName = :packageName")
    fun observeCount(packageName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM block_attempts WHERE packageName = :packageName AND timestampMillis >= :todayStartMillis")
    fun observeCountToday(packageName: String, todayStartMillis: Long): Flow<Int>
}
