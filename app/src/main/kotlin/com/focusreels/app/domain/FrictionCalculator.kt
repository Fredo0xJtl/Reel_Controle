package com.focusreels.app.domain

import java.time.LocalDate
import java.time.ZoneId

/**
 * Logique de friction progressive (cahier des charges §3.3).
 * Pure Kotlin, sans dépendance Android : testable unitairement sur PC (JVM) sans émulateur.
 */
object FrictionCalculator {

    /** Délai (en secondes) avant que le déblocage ne prenne effet, pour la N-ième tentative du jour. */
    fun delayForAttempt(baseDelaySeconds: Int, incrementSeconds: Int, attemptsAlreadyDoneToday: Int): Int =
        baseDelaySeconds + incrementSeconds * attemptsAlreadyDoneToday

    /** Réinitialisation quotidienne à 00h00 (§3.3) : renvoie le nombre de tentatives à utiliser pour "aujourd'hui". */
    fun attemptsForToday(storedAttempts: Int, storedDate: String, today: String = todayString()): Int =
        if (storedDate == today) storedAttempts else 0

    fun todayString(zoneId: ZoneId = ZoneId.systemDefault()): String =
        LocalDate.now(zoneId).toString()
}
