package com.example.freedom.domain.query

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic Temporal Resolution Layer.
 *
 * Converts relative human temporal phrases, explicit dates, and explicit date ranges
 * into exact epoch millisecond ranges `[startEpochMs, endEpochMs]` (inclusive).
 *
 * TIMEZONE SPECIFICATION:
 * - Uses the device's local timezone ([TimeZone.getDefault]), or an explicitly provided [TimeZone].
 * - Standard operational time is evaluated in local calendar days.
 * - Relative period bounds:
 *   - TODAY: 00:00:00.000 to 23:59:59.999 of current date.
 *   - YESTERDAY: 00:00:00.000 to 23:59:59.999 of previous date.
 *   - THIS_WEEK: Monday 00:00:00.000 to current timestamp + 24 hours.
 *   - LAST_WEEK: Monday 00:00:00.000 to Sunday 23:59:59.999 of preceding week.
 *   - THIS_MONTH: Day 1 00:00:00.000 to end of month 23:59:59.999.
 *   - LAST_MONTH: Day 1 to last day of preceding month.
 *   - SINCE_MONDAY: Monday 00:00:00.000 of current week to now.
 *   - LAST_SUNDAY: Sunday 00:00:00.000 to 23:59:59.999.
 * - Explicit dates:
 *   - Formats accepted: `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`.
 *   - Evaluated from start of day (00:00:00.000) to end of day (23:59:59.999).
 */
object TemporalResolver {

    private val SUPPORTED_DATE_FORMATS = listOf(
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "yyyy/MM/dd"
    )

    fun parseRelativePeriodToken(token: String): RelativePeriod? {
        val norm = token.trim().uppercase().replace(" ", "_")
        return try {
            RelativePeriod.valueOf(norm)
        } catch (_: Exception) {
            when {
                norm.contains("TODAY") -> RelativePeriod.TODAY
                norm.contains("YESTERDAY") -> RelativePeriod.YESTERDAY
                norm.contains("THIS_WEEK") || norm.contains("WEEKLY") -> RelativePeriod.THIS_WEEK
                norm.contains("LAST_WEEK") || norm.contains("PREVIOUS_WEEK") -> RelativePeriod.LAST_WEEK
                norm.contains("THIS_MONTH") || norm.contains("MONTHLY") -> RelativePeriod.THIS_MONTH
                norm.contains("LAST_MONTH") || norm.contains("PREVIOUS_MONTH") -> RelativePeriod.LAST_MONTH
                norm.contains("SINCE_MONDAY") || norm.contains("FROM_MONDAY") -> RelativePeriod.SINCE_MONDAY
                norm.contains("SUNDAY") -> RelativePeriod.LAST_SUNDAY
                else -> null
            }
        }
    }

    /**
     * Resolves a [TemporalConstraint] into an epoch millisecond range `Pair(startEpochMs, endEpochMs)`.
     */
    fun resolveConstraint(
        constraint: TemporalConstraint,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long>? {
        return when (constraint) {
            is TemporalConstraint.Relative -> resolveRelative(constraint.period, nowMillis, timeZone)
            is TemporalConstraint.ExplicitDate -> resolveExplicitDate(constraint.dateString, timeZone)
            is TemporalConstraint.ExplicitRange -> resolveExplicitRange(constraint.startDate, constraint.endDate, timeZone)
        }
    }

    /**
     * Resolves a [RelativePeriod] into an epoch millisecond range.
     */
    fun resolveRelative(
        period: RelativePeriod,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long> {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
        }

        return when (period) {
            RelativePeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            RelativePeriod.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            RelativePeriod.THIS_WEEK -> {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = (dayOfWeek + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, nowMillis + 86400000L)
            }

            RelativePeriod.LAST_WEEK -> {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = (dayOfWeek + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday - 7)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.add(Calendar.DAY_OF_YEAR, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            RelativePeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, nowMillis + 86400000L)
            }

            RelativePeriod.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }

            RelativePeriod.SINCE_MONDAY -> {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val daysFromMonday = (dayOfWeek + 5) % 7
                cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, nowMillis + 86400000L)
            }

            RelativePeriod.LAST_SUNDAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                }
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
        }
    }

    /**
     * Resolves an explicit calendar date string into start-of-day and end-of-day epoch ms.
     */
    fun resolveExplicitDate(dateString: String, timeZone: TimeZone = TimeZone.getDefault()): Pair<Long, Long>? {
        val trimmed = dateString.trim()
        for (pattern in SUPPORTED_DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    this.timeZone = timeZone
                    isLenient = false
                }
                val parsedDate = sdf.parse(trimmed) ?: continue
                val cal = Calendar.getInstance(timeZone).apply {
                    time = parsedDate
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                return Pair(start, end)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Resolves an explicit date range into start-of-first-day and end-of-last-day epoch ms.
     */
    fun resolveExplicitRange(
        startDate: String,
        endDate: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long>? {
        val startPair = resolveExplicitDate(startDate, timeZone) ?: return null
        val endPair = resolveExplicitDate(endDate, timeZone) ?: return null
        return Pair(startPair.first, endPair.second)
    }

    /**
     * Extracts a [TemporalConstraint] from natural language if possible.
     */
    fun extractConstraintFromPrompt(prompt: String): TemporalConstraint? {
        val lower = prompt.lowercase()

        // Check for explicit date patterns e.g. 2026-09-28 or 28/09/2026
        val dateMatch = Regex("(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4})").find(prompt)
        if (dateMatch != null) {
            return TemporalConstraint.ExplicitDate(dateMatch.value)
        }

        // Relative periods
        val period = when {
            lower.contains("last sunday") || lower.contains("on sunday") || lower.contains("past sunday") -> RelativePeriod.LAST_SUNDAY
            lower.contains("since monday") || lower.contains("from monday") -> RelativePeriod.SINCE_MONDAY
            lower.contains("this week") || lower.contains("weekly") -> RelativePeriod.THIS_WEEK
            lower.contains("last week") || lower.contains("previous week") -> RelativePeriod.LAST_WEEK
            lower.contains("this month") || lower.contains("monthly") -> RelativePeriod.THIS_MONTH
            lower.contains("last month") || lower.contains("previous month") -> RelativePeriod.LAST_MONTH
            lower.contains("yesterday") -> RelativePeriod.YESTERDAY
            lower.contains("today") -> RelativePeriod.TODAY
            else -> null
        }
        return period?.let { TemporalConstraint.Relative(it) }
    }
}
