package com.focusreels.app.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Configuration

/**
 * Au redémarrage du téléphone, WorkManager reprogramme automatiquement ses tâches persistées ;
 * ce receiver existe pour un futur usage (ex: revalidation d'état) sans logique réseau ni tierce partie.
 */
class RelockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // WorkManager restaure seul les OneTimeWorkRequest persistés après reboot.
        // Aucune action supplémentaire requise pour la V1.
    }
}
