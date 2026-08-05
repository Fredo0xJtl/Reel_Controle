package com.focusreels.app.data.repository

import com.focusreels.app.data.db.AppDatabase
import com.focusreels.app.data.db.BlockAttemptEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class HistoryRepository(private val db: AppDatabase) {

    suspend fun recordAttempt(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        db.blockAttemptDao().insert(BlockAttemptEntity(packageName = packageName, timestampMillis = timestampMillis))
    }

    fun observeHistory(packageName: String): Flow<List<BlockAttemptEntity>> =
        db.blockAttemptDao().observeHistory(packageName)

    fun observeCountToday(packageName: String): Flow<Int> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return db.blockAttemptDao().observeCountToday(packageName, todayStart)
    }
}
