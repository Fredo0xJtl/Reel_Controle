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
    entities = [BlockedAppEntity::class, BlockAttemptEntity::class, UnlockEventEntity::class],
    // v1 -> v2 : ajout de unlock_events (compteur/graphique de déblocages, cf. HomeScreen /
    // HistoryScreen). Couverte par fallbackToDestructiveMigration ci-dessous, pas de migration
    // explicite écrite : perte de l'historique local acceptable (aucune synchronisation,
    // contenu recréable, cf. commentaire sur le builder).
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockAttemptDao(): BlockAttemptDao
    abstract fun unlockEventDao(): UnlockEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_reels.db"
                )
                    // Filet de sécurité tant qu'aucune migration explicite n'existe : sans cela,
                    // un futur changement de schéma (version 2+) fait planter l'app au démarrage
                    // au lieu de recréer la base. Perte de données locale acceptable ici (aucune
                    // synchronisation, contenu recréable) ; à retirer le jour où une vraie
                    // migration est écrite.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
