package com.focusreels.app.data.repository

import com.focusreels.app.data.db.AppDatabase
import com.focusreels.app.data.db.BlockAttemptEntity
import com.focusreels.app.data.db.UnlockEventEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class HistoryRepository(private val db: AppDatabase) {

    suspend fun recordAttempt(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        db.blockAttemptDao().insert(BlockAttemptEntity(packageName = packageName, timestampMillis = timestampMillis))
    }

    fun observeHistory(packageName: String): Flow<List<BlockAttemptEntity>> =
        db.blockAttemptDao().observeHistory(packageName)

    fun observeCountToday(packageName: String): Flow<Int> =
        db.blockAttemptDao().observeCountToday(packageName, todayStartMillis())

    /** Symétrique de [recordAttempt] côté déblocages (cf. [UnlockEventEntity]). */
    suspend fun recordUnlock(packageName: String, timestampMillis: Long = System.currentTimeMillis()) {
        db.unlockEventDao().insert(UnlockEventEntity(packageName = packageName, timestampMillis = timestampMillis))
    }

    fun observeUnlockHistory(packageName: String): Flow<List<UnlockEventEntity>> =
        db.unlockEventDao().observeHistory(packageName)

    fun observeUnlockCountToday(packageName: String): Flow<Int> =
        db.unlockEventDao().observeCountToday(packageName, todayStartMillis())

    private fun todayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
