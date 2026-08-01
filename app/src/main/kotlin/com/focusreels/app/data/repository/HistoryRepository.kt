package com.focusreels.app.data.repository

import com.focusreels.app.data.db.AppDatabase
import com.focusreels.app.data.db.BlockAttemptEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val db: AppDatabase) {

    suspend fun recordAttempt(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        db.blockAttemptDao().insert(BlockAttemptEntity(packageName = packageName, timestampMillis = timestampMillis))
    }

    fun observeHistory(packageName: String): Flow<List<BlockAttemptEntity>> =
        db.blockAttemptDao().observeHistory(packageName)
}
