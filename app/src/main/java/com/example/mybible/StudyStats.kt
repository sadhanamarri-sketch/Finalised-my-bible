package com.example.mybible

import java.time.LocalDate

/**
 * Study tracking utilities used by the native Kotlin app.
 */
data class StudySummary(
    val totalVerses: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val longestStreak: Int
)

object StudyStats {
    fun streak(dates: Collection<String>, today: LocalDate = LocalDate.now()): Int {
        val days = dates.mapNotNull { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }.toSet()
        if (days.isEmpty()) return 0
        var cursor = today
        if (!days.contains(cursor)) {
            cursor = cursor.minusDays(1)
            if (!days.contains(cursor)) return 0
        }
        var count = 0
        while (days.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun longestStreak(dates: Collection<String>): Int {
        val days = dates.mapNotNull { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            .distinct().sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var current = 1
        for (i in 1 until days.size) {
            current = if (days[i] == days[i - 1].plusDays(1)) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }

    fun summary(dates: Collection<String>, today: LocalDate = LocalDate.now()): StudySummary =
        StudySummary(
            totalVerses = dates.size,
            activeDays = dates.mapNotNull { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }.distinct().size,
            currentStreak = streak(dates, today),
            longestStreak = longestStreak(dates)
        )
}
