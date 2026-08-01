package com.focusreels.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données 100 % locale (SQLite via Room). Aucune synchronisation réseau :
 * exigence non négociable du cahier des charges (§4.4).
 */
@Database(
    entities = [BlockedAppEntity::class, BlockAttemptEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockAttemptDao(): BlockAttemptDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_reels.db"
                ).build().also { instance = it }
            }
    }
}
