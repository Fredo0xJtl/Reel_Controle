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
        return enabledServices.contains(ReelsAccessibilityService::class.java.simpleName)
    }
}
