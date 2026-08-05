package com.focusreels.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

object ThemePreferences {
    private val USE_DARK_MODE = booleanPreferencesKey("use_dark_mode")

    // `USE_DARK_MODE` absent a deux causes possibles, qu'il faut distinguer : première
    // installation (aucun choix fait, doit démarrer en sombre) ou choix explicite de l'option
    // "SYSTÈME" dans les réglages (doit suivre le thème de l'appareil). Sans ce second indicateur,
    // les deux cas sont indiscernables — imposer le sombre par défaut aurait alors aussi écrasé le
    // choix explicite "SYSTÈME" à chaque démarrage de l'app.
    private val THEME_INITIALIZED = booleanPreferencesKey("theme_initialized")

    fun observeDarkMode(context: Context): Flow<Boolean?> =
        context.themeDataStore.data.map { it[USE_DARK_MODE] }

    suspend fun setDarkMode(context: Context, isDark: Boolean) {
        context.themeDataStore.edit {
            it[USE_DARK_MODE] = isDark
            it[THEME_INITIALIZED] = true
        }
    }

    /** Revient au suivi du thème système (option "SYSTÈME" du sélecteur de thème). */
    suspend fun clearDarkMode(context: Context) {
        context.themeDataStore.edit {
            it.remove(USE_DARK_MODE)
            it[THEME_INITIALIZED] = true
        }
    }

    /**
     * À appeler une fois au démarrage de l'app : impose le mode sombre par défaut au tout premier
     * lancement (aucun choix de thème jamais fait), sans jamais écraser un choix déjà fait par
     * l'utilisateur (y compris "SYSTÈME").
     */
    suspend fun ensureDarkDefaultOnFirstLaunch(context: Context) {
        context.themeDataStore.edit {
            if (it[THEME_INITIALIZED] != true) {
                it[USE_DARK_MODE] = true
                it[THEME_INITIALIZED] = true
            }
        }
    }
}
