package com.aroundus.app.utils

import com.aroundus.app.models.Event
import com.aroundus.app.models.EventEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object EventDateUtils {

    /**
     * Determines whether an event's scheduled date and time has already passed.
     * Returns true if the event has ended, false if it is upcoming or active.
     */
    fun isEventPassed(dateStr: String?, createdAtStr: String? = null): Boolean {
        val raw = dateStr?.trim().orEmpty()
        if (raw.isBlank() || raw.contains("TBA", ignoreCase = true)) {
            return false // Undetermined / TBA events are not marked as passed
        }

        val lower = raw.lowercase(Locale.US)
        if (lower.contains("tomorrow")) {
            return false
        }
        if (lower.contains("yesterday")) {
            return true
        }

        val eventCal = parseEventDateTime(raw, createdAtStr) ?: return false
        val nowCal = Calendar.getInstance()

        // If event is scheduled for today:
        val isSameDay = eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                eventCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            // Allow a grace period of 3 hours after scheduled start time (or until end of day)
            // so active events today stay accessible to neighbors
            val bufferMillis = 3L * 60 * 60 * 1000 // 3 hours
            return (eventCal.timeInMillis + bufferMillis) < nowCal.timeInMillis
        }

        // If event was scheduled on a day strictly before today:
        return eventCal.before(nowCal)
    }

    /**
     * Attempts to parse diverse event date formats into a Calendar instance.
     */
    fun parseEventDateTime(rawDateStr: String?, createdAtStr: String? = null): Calendar? {
        val raw = rawDateStr?.trim().orEmpty()
        if (raw.isBlank() || raw.contains("TBA", ignoreCase = true)) return null

        val now = Calendar.getInstance()
        val lower = raw.lowercase(Locale.US)

        // Handle "Today" / "Tonight"
        if (lower.contains("today") || lower.contains("tonight")) {
            val cal = Calendar.getInstance()
            parseTimeIntoCalendar(raw, cal)
            return cal
        }

        // Handle "Tomorrow"
        if (lower.contains("tomorrow")) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            parseTimeIntoCalendar(raw, cal)
            return cal
        }

        // Clean up string
        var cleaned = raw
            .replace("·", " ")
            .replace("•", " ")
            .replace(",", ", ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Strip end time range if present (e.g. "9:00 AM – 2:00 PM")
        if (cleaned.contains("–")) {
            val beforeDash = cleaned.substringBefore("–").trim()
            val afterDash = cleaned.substringAfter("–").trim()
            if ((afterDash.endsWith("AM", true) || afterDash.endsWith("PM", true)) &&
                !beforeDash.endsWith("AM", true) && !beforeDash.endsWith("PM", true)
            ) {
                val amPm = afterDash.takeLast(2)
                cleaned = "$beforeDash $amPm"
            } else {
                cleaned = beforeDash
            }
        } else if (cleaned.contains(" - ") && (cleaned.contains("AM", true) || cleaned.contains("PM", true))) {
            cleaned = cleaned.substringBefore(" - ").trim()
        }

        // Extract year from createdAtStr if available
        var fallbackYear = now.get(Calendar.YEAR)
        if (!createdAtStr.isNullOrBlank()) {
            val yearMatch = Regex("\\b(20\\d\\d)\\b").find(createdAtStr)
            if (yearMatch != null) {
                fallbackYear = yearMatch.value.toIntOrNull() ?: fallbackYear
            }
        }

        val yearInString = Regex("\\b(20\\d\\d)\\b").find(cleaned)?.value?.toIntOrNull()

        // 1. Try standard date formats with year
        val formatsWithYear = listOf(
            "EEE, MMM d, yyyy h:mm a",
            "EEE, MMM d, yyyy hh:mm a",
            "EEE, MMM d, yyyy",
            "MMM d, yyyy h:mm a",
            "MMM d, yyyy hh:mm a",
            "MMM d, yyyy",
            "d MMM yyyy h:mm a",
            "d MMM yyyy",
            "d MMMM yyyy",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "MM/dd/yyyy"
        )

        for (pattern in formatsWithYear) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
                val parsedDate = sdf.parse(cleaned)
                if (parsedDate != null) {
                    val cal = Calendar.getInstance().apply { time = parsedDate }
                    if (!pattern.contains("h") && !pattern.contains("H")) {
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                    }
                    return cal
                }
            } catch (_: Exception) {}
        }

        // 2. Try formats without year (e.g. "Sat, Aug 15 9:00 AM", "Aug 15")
        val formatsWithoutYear = listOf(
            "EEE, MMM d h:mm a",
            "EEE, MMM d hh:mm a",
            "EEE, MMM d",
            "MMM d h:mm a",
            "MMM d hh:mm a",
            "MMM d",
            "d MMM h:mm a",
            "d MMM"
        )

        for (pattern in formatsWithoutYear) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
                val parsedDate = sdf.parse(cleaned)
                if (parsedDate != null) {
                    val tempCal = Calendar.getInstance().apply { time = parsedDate }
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, yearInString ?: fallbackYear)
                        set(Calendar.MONTH, tempCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, tempCal.get(Calendar.DAY_OF_MONTH))
                        if (pattern.contains("h") || pattern.contains("H")) {
                            set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                        } else {
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                        }
                    }
                    return cal
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: Keyword token matching for Month and Day
        return parseFallbackTokens(cleaned, yearInString ?: fallbackYear)
    }

    private fun parseTimeIntoCalendar(source: String, target: Calendar) {
        val timeRegex = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?")
        val match = timeRegex.find(source)
        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 12
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues.getOrNull(3)?.uppercase(Locale.US)
            if (amPm == "PM" && hour < 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)
            target.set(Calendar.SECOND, 0)
        } else {
            target.set(Calendar.HOUR_OF_DAY, 23)
            target.set(Calendar.MINUTE, 59)
            target.set(Calendar.SECOND, 59)
        }
    }

    private fun parseFallbackTokens(text: String, fallbackYear: Int): Calendar? {
        val monthMap = mapOf(
            "JAN" to Calendar.JANUARY, "FEB" to Calendar.FEBRUARY, "MAR" to Calendar.MARCH,
            "APR" to Calendar.APRIL, "MAY" to Calendar.MAY, "JUN" to Calendar.JUNE,
            "JUL" to Calendar.JULY, "AUG" to Calendar.AUGUST, "SEP" to Calendar.SEPTEMBER,
            "OCT" to Calendar.OCTOBER, "NOV" to Calendar.NOVEMBER, "DEC" to Calendar.DECEMBER
        )

        val tokens = text.split(" ", "-", "/", ",", "·").filter { it.isNotBlank() }
        var detectedMonth: Int? = null
        var detectedDay: Int? = null

        for (token in tokens) {
            val upper = token.uppercase(Locale.US)
            val monthEntry = monthMap.entries.firstOrNull { upper.startsWith(it.key) }
            if (monthEntry != null && detectedMonth == null) {
                detectedMonth = monthEntry.value
                continue
            }
            val num = token.toIntOrNull()
            if (num != null && num in 1..31 && detectedDay == null) {
                detectedDay = num
            }
        }

        if (detectedMonth != null && detectedDay != null) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, fallbackYear)
                set(Calendar.MONTH, detectedMonth)
                set(Calendar.DAY_OF_MONTH, detectedDay)
            }
            parseTimeIntoCalendar(text, cal)
            return cal
        }

        return null
    }
}

fun Event.isPassed(): Boolean = EventDateUtils.isEventPassed(this.time, this.createdAt)
fun EventEntity.isPassed(): Boolean = EventDateUtils.isEventPassed(this.date, this.createdAt)
