package com.focusreels.app.domain

/**
 * Distingue "Reels reçu en DM" du "flux Reels général" via un compteur de swipes (§3.2, §4.2).
 * État en mémoire uniquement (pas de persistance nécessaire : contexte volatile de navigation).
 */
class SwipeSessionTracker(@Volatile var toleratedSwipes: Int) {

    private var inDmReelsContext = false
    private var swipeCount = 0

    /**
     * Ne réinitialise le compteur que lors de l'entrée dans le contexte DM. Cette méthode est
     * appelée à chaque événement d'accessibilité tant que le lecteur DM reste affiché (plusieurs
     * fois par seconde) : remettre le compteur à zéro à chaque appel empêchait tout dépassement
     * du seuil de swipes tolérés, quel que soit le nombre réel de swipes effectués.
     */
    fun onDmReelsOpened() {
        if (!inDmReelsContext) {
            inDmReelsContext = true
            swipeCount = 0
        }
    }

    fun onSwipeDetected() {
        if (!inDmReelsContext) return
        swipeCount++
    }

    /** True si le nombre de swipes tolérés est dépassé : il faut alors traiter comme le flux Reels général. */
    fun shouldTreatAsGeneralFeed(): Boolean = inDmReelsContext && swipeCount > toleratedSwipes

    /**
     * True tant qu'on est dans la fenêtre de grâce DM (Reels reçu en DM, swipes encore sous le
     * seuil toléré). Utilisé pour ne pas déclencher le blocage "lecteur plein écran" générique
     * (cf. [com.focusreels.app.accessibility.InstagramUiDetector.isImmersiveReelsViewerOpen])
     * pendant cette fenêtre : le lecteur plein écran DM et celui ouvert depuis Explorer/le profil
     * partagent la même UI, donc sans cette distinction le premier Reels reçu en DM serait
     * bloqué immédiatement au lieu de bénéficier de la tolérance de swipes voulue.
     */
    fun isWithinDmTolerance(): Boolean = inDmReelsContext && swipeCount <= toleratedSwipes

    fun reset() {
        inDmReelsContext = false
        swipeCount = 0
    }
}
