package com.focusreels.app.data.repository

import com.focusreels.app.data.db.AppDatabase
import com.focusreels.app.data.db.BlockedAppEntity
import com.focusreels.app.util.AppIds
import com.focusreels.app.util.Defaults
import kotlinx.coroutines.flow.Flow

class BlockedAppRepository(private val db: AppDatabase) {

    suspend fun ensureSeeded() {
        if (db.blockedAppDao().get(AppIds.INSTAGRAM) == null) {
            db.blockedAppDao().insert(
                BlockedAppEntity(
                    packageName = AppIds.INSTAGRAM,
                    displayName = "Instagram",
                    blockingEnabled = false,
                    baseDelaySeconds = Defaults.BASE_DELAY_SECONDS,
                    incrementSeconds = Defaults.INCREMENT_SECONDS,
                    relockDelayMinutes = Defaults.RELOCK_DELAY_MINUTES,
                    toleratedSwipesAfterDm = Defaults.TOLERATED_SWIPES_AFTER_DM
                )
            )
        }
    }

    fun observe(packageName: String): Flow<BlockedAppEntity?> = db.blockedAppDao().observe(packageName)

    fun observeAll(): Flow<List<BlockedAppEntity>> = db.blockedAppDao().observeAll()

    suspend fun get(packageName: String): BlockedAppEntity? = db.blockedAppDao().get(packageName)

    suspend fun save(entity: BlockedAppEntity) = db.blockedAppDao().update(entity)
}
