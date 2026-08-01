package com.focusreels.app.domain

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository

/** Réactive le blocage à l'issue du délai de reverrouillage (§3.4). */
class RelockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE_NAME) ?: return Result.failure()
        val app = applicationContext as FocusReelsApplication
        val repository = BlockedAppRepository(app.database)

        val entity = repository.get(packageName) ?: return Result.success()
        if (!entity.blockingEnabled) {
            repository.save(entity.copy(blockingEnabled = true))
        }
        return Result.success()
    }

    companion object {
        const val KEY_PACKAGE_NAME = "package_name"
    }
}
