package ke.vaccinereminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    private val en = Strings(Lang.EN)
    private val sw = Strings(Lang.SW)
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun phoneNumbersMatchTheServerRules() {
        assertEquals("+254712345678", Phone.normalize("0712 345 678"))
        assertEquals("+254112345678", Phone.normalize("254112345678"))
        assertEquals("+254712345678", Phone.normalize("+254 (712) 345-678"))
        assertEquals("+254712345678", Phone.normalize("712345678"))
        assertNull(Phone.normalize("12345"))
        assertNull(Phone.normalize("0812345678"))
        assertEquals("0712 345 678", Phone.display("+254712345678"))
        assertEquals("not a phone", Phone.display("not a phone"))
    }

    @Test
    fun passwordStrengthFollowsTheServerMinimum() {
        assertEquals(0, passwordStrength("short1"))
        assertEquals(0, passwordStrength("onlyletters"))
        assertEquals(0, passwordStrength("12345678"))
        assertEquals(1, passwordStrength("secret123"))
        assertEquals(2, passwordStrength("secret123!"))
        assertEquals(2, passwordStrength("averylongpassword1"))
    }

    @Test
    fun relativeDatesInBothLanguages() {
        assertEquals("Today", relativeDate("2026-10-25", en, today))
        assertEquals("Tomorrow", relativeDate("2026-10-26", en, today))
        assertEquals("In 3 days", relativeDate("2026-10-28", en, today))
        assertEquals("5 days ago", relativeDate("2026-10-20", en, today))
        assertEquals("In 4 weeks", relativeDate("2026-11-22", en, today))
        assertEquals("Baada ya siku 3", relativeDate("2026-10-28", sw, today))
        assertEquals("Jana", relativeDate("2026-10-24", sw, today))
        assertEquals("", relativeDate("not a date", en, today))
    }

    @Test
    fun ageOfAChild() {
        assertEquals("6 days old", ageText("2026-10-19", en, today))
        assertEquals("6 weeks old", ageText("2026-09-13", en, today))
        assertEquals("9 months old", ageText("2026-01-20", en, today))
        assertEquals("2 years, 1 month", ageText("2024-09-25", en, today))
        assertEquals("Wiki 6", ageText("2026-09-13", sw, today))
        assertEquals("", ageText("2026-12-01", en, today))
    }

    @Test
    fun milestoneLabels() {
        assertEquals("At birth", en.milestone("birth"))
        assertEquals("14 weeks", en.milestone("14w"))
        assertEquals("Miezi 9", sw.milestone("9m"))
    }

    @Test
    fun dosesAreGroupedIntoVisitsInScheduleOrder() {
        val doses = listOf(
            Dose("BCG", "BCG", "birth", "2026-09-13", "2026-09-13", "given"),
            Dose("OPV0", "Polio (OPV 0)", "birth", "2026-09-13", null, "overdue"),
            Dose("OPV1", "Polio (OPV 1)", "6w", "2026-10-25", null, "due"),
            Dose("PENTA1", "Pentavalent 1", "6w", "2026-10-25", "2026-10-25", "given"),
            Dose("MR1", "Measles-Rubella 1", "9m", "2027-06-13", null, "upcoming"),
        )
        val groups = groupVisits(doses)
        assertEquals(listOf("birth", "6w", "9m"), groups.map { it.milestone })
        assertEquals(listOf("overdue", "due", "upcoming"), groups.map { it.status })
        assertEquals("2026-09-13", groups[0].pendingDate)
        assertEquals("given", groupVisits(doses.take(1)).single().status)
        assertEquals("2026-09-13", groupVisits(doses.take(1)).single().lastGivenDate)
    }

    @Test
    fun initialsForAvatars() {
        assertEquals("AW", initials("Amani Wanjiru Otieno"))
        assertEquals("A", initials("  amani "))
        assertEquals("", initials(""))
    }
}
