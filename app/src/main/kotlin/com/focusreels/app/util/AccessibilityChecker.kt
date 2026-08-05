package com.focusreels.app.util

import android.content.Context
import android.provider.Settings
import com.focusreels.app.accessibility.ReelsAccessibilityService

object AccessibilityChecker {

    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        // Comparaison par nom pleinement qualifié ("package/package.Classe", format standard de
        // ENABLED_ACCESSIBILITY_SERVICES) plutôt que par simpleName seul : un simple `contains`
        // sur le nom court risquait de matcher un service homonyme d'une autre application.
        val qualifiedName = "${context.packageName}/${ReelsAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { it.equals(qualifiedName, ignoreCase = true) }
    }
}
