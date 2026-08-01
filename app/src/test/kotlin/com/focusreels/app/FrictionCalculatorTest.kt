package com.focusreels.app

import com.focusreels.app.domain.FrictionCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests unitaires JVM purs : exécutables sur PC via `gradlew test`, sans émulateur ni appareil. */
class FrictionCalculatorTest {

    @Test
    fun `premiere tentative utilise le delai de base`() {
        assertEquals(5, FrictionCalculator.delayForAttempt(baseDelaySeconds = 5, incrementSeconds = 5, attemptsAlreadyDoneToday = 0))
    }

    @Test
    fun `delai croit de l increment a chaque tentative`() {
        assertEquals(10, FrictionCalculator.delayForAttempt(5, 5, attemptsAlreadyDoneToday = 1))
        assertEquals(15, FrictionCalculator.delayForAttempt(5, 5, attemptsAlreadyDoneToday = 2))
    }

    @Test
    fun `compteur reinitialise si la date stockee differe d aujourd hui`() {
        assertEquals(0, FrictionCalculator.attemptsForToday(storedAttempts = 4, storedDate = "2026-07-31", today = "2026-08-01"))
    }

    @Test
    fun `compteur conserve si meme jour`() {
        assertEquals(4, FrictionCalculator.attemptsForToday(storedAttempts = 4, storedDate = "2026-08-01", today = "2026-08-01"))
    }
}
