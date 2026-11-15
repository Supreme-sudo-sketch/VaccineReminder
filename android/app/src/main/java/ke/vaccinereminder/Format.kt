package ke.vaccinereminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

fun todayNairobi(): LocalDate = LocalDate.now(NAIROBI)

object Phone {
    // Same rules as the server: Kenyan mobile numbers starting 07 or 01, in any common format.
    fun normalize(raw: String): String? {
        val s = raw.replace(Regex("[\\s\\-()]"), "")
        return when {
            Regex("^\\+254[17]\\d{8}$").matches(s) -> s
            Regex("^254[17]\\d{8}$").matches(s) -> "+$s"
            Regex("^0[17]\\d{8}$").matches(s) -> "+254" + s.substring(1)
            Regex("^[17]\\d{8}$").matches(s) -> "+254$s"
            else -> null
        }
    }

    // "+254712345678" -> "0712 345 678", the way people in Kenya write their number.
    fun display(phone: String): String {
        val n = normalize(phone) ?: return phone
        val local = "0" + n.removePrefix("+254")
        return "${local.substring(0, 4)} ${local.substring(4, 7)} ${local.substring(7)}"
    }
}

fun parseIsoDate(s: String?): LocalDate? = try {
    if (s.isNullOrBlank()) null else LocalDate.parse(s)
} catch (e: DateTimeParseException) {
    null
}

private fun locale(lang: Lang): Locale = if (lang == Lang.SW) Locale.forLanguageTag("sw-KE") else Locale.UK

fun formatDate(iso: String?, lang: Lang): String {
    val d = parseIsoDate(iso) ?: return iso ?: ""
    return d.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(lang)))
}

// Server timestamps are SQLite CURRENT_TIMESTAMP values in UTC, e.g. "2026-10-25 05:01:02".
fun formatTimestamp(utc: String?, lang: Lang): String {
    if (utc.isNullOrBlank()) return ""
    return try {
        val t = LocalDateTime.parse(utc.replace(' ', 'T')).atOffset(ZoneOffset.UTC).atZoneSameInstant(NAIROBI)
        t.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale(lang)))
    } catch (e: DateTimeParseException) {
        utc
    }
}

fun daysFromToday(iso: String?, today: LocalDate = todayNairobi()): Long? =
    parseIsoDate(iso)?.let { ChronoUnit.DAYS.between(today, it) }

fun relativeDate(iso: String?, s: Strings, today: LocalDate = todayNairobi()): String {
    val days = daysFromToday(iso, today) ?: return ""
    return s.relativeDays(days)
}

fun ageText(dob: String, s: Strings, today: LocalDate = todayNairobi()): String {
    val birth = parseIsoDate(dob) ?: return ""
    if (birth.isAfter(today)) return ""
    val days = ChronoUnit.DAYS.between(birth, today)
    val p = Period.between(birth, today)
    return when {
        days < 14 -> s.ageDays(days.toInt())
        days < 13 * 7 -> s.ageWeeks((days / 7).toInt())
        p.years < 2 -> s.ageMonths(p.years * 12 + p.months)
        else -> s.ageYears(p.years, p.months)
    }
}

// 0 = too weak for the server, 1 = acceptable, 2 = strong.
fun passwordStrength(password: String): Int {
    val ok = password.length >= 8 && password.any { it.isLetter() } && password.any { it.isDigit() }
    if (!ok) return 0
    val strong = password.length >= 12 || password.any { !it.isLetterOrDigit() }
    return if (strong) 2 else 1
}

// Doses grouped into the visits they belong to ("At birth", "6 weeks", ...), in schedule order.
data class VisitGroup(val milestone: String, val doses: List<Dose>) {
    val status: String
        get() = when {
            doses.all { it.status == "given" } -> "given"
            doses.any { it.status == "overdue" } -> "overdue"
            doses.any { it.status == "due" } -> "due"
            else -> "upcoming"
        }

    val pendingDate: String? get() = doses.filter { it.status != "given" }.minOfOrNull { it.dueDate }
    val lastGivenDate: String? get() = doses.mapNotNull { it.givenDate }.maxOrNull()
}

fun groupVisits(doses: List<Dose>): List<VisitGroup> =
    doses.groupBy { it.milestone.ifBlank { it.dueDate } }.map { (milestone, list) -> VisitGroup(milestone, list) }

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
