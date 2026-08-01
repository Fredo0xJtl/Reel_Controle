package com.focusreels.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Historique illimité des tentatives d'accès bloquées (cahier des charges §3.6). */
@Entity(tableName = "block_attempts")
data class BlockAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestampMillis: Long
)
