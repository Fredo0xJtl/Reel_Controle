package com.focusreels.app.domain

/**
 * Distingue "Reels reçu en DM" du "flux Reels général" via un compteur de swipes (§3.2, §4.2).
 * État en mémoire uniquement (pas de persistance nécessaire : contexte volatile de navigation).
 */
class SwipeSessionTracker(private val toleratedSwipes: Int) {

    private var inDmReelsContext = false
    private var swipeCount = 0

    fun onDmReelsOpened() {
        inDmReelsContext = true
        swipeCount = 0
    }

    fun onSwipeDetected() {
        if (!inDmReelsContext) return
        swipeCount++
    }

    /** True si le nombre de swipes tolérés est dépassé : il faut alors traiter comme le flux Reels général. */
    fun shouldTreatAsGeneralFeed(): Boolean = inDmReelsContext && swipeCount > toleratedSwipes

    fun reset() {
        inDmReelsContext = false
        swipeCount = 0
    }
}
