package com.focusreels.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): BlockedAppEntity?

    @Query("SELECT * FROM blocked_apps WHERE packageName = :packageName")
    fun observe(packageName: String): Flow<BlockedAppEntity?>

    @Query("SELECT * FROM blocked_apps")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockedAppEntity)

    @Update
    suspend fun update(entity: BlockedAppEntity)
}
