package com.focusreels.app.domain

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Planifie le reverrouillage automatique après le délai configuré (cahier des charges §3.4). */
object RelockScheduler {

    private const val WORK_NAME_PREFIX = "relock_"

    fun scheduleRelock(context: Context, packageName: String, delayMinutes: Int) {
        val request = OneTimeWorkRequestBuilder<RelockWorker>()
            .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(RelockWorker.KEY_PACKAGE_NAME to packageName))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME_PREFIX + packageName, androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelRelock(context: Context, packageName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + packageName)
    }
}
