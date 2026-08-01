package com.focusreels.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une ligne par application gérée (cahier des charges §3.5 : architecture modulaire,
 * même si seule Instagram existe en V1).
 */
@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val blockingEnabled: Boolean = false,
    val baseDelaySeconds: Int,
    val incrementSeconds: Int,
    val relockDelayMinutes: Int,
    val toleratedSwipesAfterDm: Int,
    /** Horodatage epoch millis du dernier déblocage effectif, pour le reverrouillage auto (§3.4). */
    val lastUnlockAtMillis: Long? = null,
    /** Nombre de tentatives de déblocage depuis la dernière réinitialisation à 00h00 (§3.3). */
    val unlockAttemptsToday: Int = 0,
    /** Date (yyyy-MM-dd) du compteur ci-dessus, pour détecter le changement de jour. */
    val unlockAttemptsDate: String = ""
)
