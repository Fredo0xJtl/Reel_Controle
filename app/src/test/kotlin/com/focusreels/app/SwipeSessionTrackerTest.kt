package com.focusreels.app

import com.focusreels.app.domain.SwipeSessionTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verrouille le comportement voulu pour les Reels reçus en DM (§3.2) : ils doivent rester
 * visibles, et n'être bloqués qu'une fois le seuil de swipes tolérés dépassé.
 *
 * Ces règles ont régressé plusieurs fois en test terrain (fenêtre DM fermée dès le premier clic,
 * ou au contraire jamais bloquée quel que soit le nombre de swipes). Elles sont testables hors
 * appareil : ces tests évitent d'avoir à rejouer le scénario à la main sur le téléphone.
 */
class SwipeSessionTrackerTest {

    private fun tracker(tolerated: Int = 2) = SwipeSessionTracker(toleratedSwipes = tolerated)

    @Test
    fun `hors contexte DM, aucune tolerance ne s'applique`() {
        val tracker = tracker()
        assertFalse(tracker.isWithinDmTolerance())
        assertFalse(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `un Reels DM tout juste ouvert est dans la fenetre de tolerance`() {
        val tracker = tracker()
        tracker.onDmReelsOpened()
        assertTrue(tracker.isWithinDmTolerance())
        assertFalse(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `la tolerance survit a des swipes sous le seuil`() {
        val tracker = tracker(tolerated = 2)
        tracker.onDmReelsOpened()
        tracker.onSwipeDetected()
        tracker.onSwipeDetected()
        assertTrue(tracker.isWithinDmTolerance())
        assertFalse(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `depasser le seuil de swipes bascule en flux general a bloquer`() {
        val tracker = tracker(tolerated = 2)
        tracker.onDmReelsOpened()
        repeat(3) { tracker.onSwipeDetected() }
        assertFalse(tracker.isWithinDmTolerance())
        assertTrue(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `onDmReelsOpened repete ne remet pas le compteur a zero`() {
        // Régression corrigée : la méthode est appelée à chaque événement d'accessibilité tant que
        // le lecteur DM reste affiché. Réinitialiser à chaque appel rendait le seuil inatteignable.
        val tracker = tracker(tolerated = 1)
        tracker.onDmReelsOpened()
        tracker.onSwipeDetected()
        tracker.onDmReelsOpened()
        tracker.onSwipeDetected()
        tracker.onDmReelsOpened()
        assertTrue(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `les swipes hors contexte DM ne sont pas comptes`() {
        val tracker = tracker(tolerated = 1)
        repeat(5) { tracker.onSwipeDetected() }
        tracker.onDmReelsOpened()
        assertTrue(tracker.isWithinDmTolerance())
    }

    @Test
    fun `reset ramene a l'etat hors DM`() {
        val tracker = tracker(tolerated = 1)
        tracker.onDmReelsOpened()
        repeat(3) { tracker.onSwipeDetected() }
        tracker.reset()
        assertFalse(tracker.isWithinDmTolerance())
        assertFalse(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `une nouvelle session DM apres reset repart avec la tolerance pleine`() {
        val tracker = tracker(tolerated = 2)
        tracker.onDmReelsOpened()
        repeat(5) { tracker.onSwipeDetected() }
        tracker.reset()

        tracker.onDmReelsOpened()
        assertTrue(tracker.isWithinDmTolerance())
        assertFalse(tracker.shouldTreatAsGeneralFeed())
    }

    @Test
    fun `un seuil a zero bloque des le premier swipe`() {
        val tracker = tracker(tolerated = 0)
        tracker.onDmReelsOpened()
        assertTrue(tracker.isWithinDmTolerance())

        tracker.onSwipeDetected()
        assertTrue(tracker.shouldTreatAsGeneralFeed())
    }
}
