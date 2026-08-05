package com.focusreels.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historique illimité des déblocages (désactivations volontaires du blocage), symétrique à
 * [BlockAttemptEntity]. Un déblocage est enregistré une fois le compte à rebours de friction
 * écoulé (cf. `UnlockFrictionScreen`), pas au moment où l'utilisateur décoche l'interrupteur —
 * décocher lance l'écran de friction, le déblocage n'est acquis qu'à son terme.
 */
@Entity(tableName = "unlock_events")
data class UnlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampMillis: Long
)
