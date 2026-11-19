package ke.vaccinereminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// The conversions between a child's record and the rows saved on the phone (LocalDb.kt).
class ChildCacheTest {
    private val savedOn = LocalDate.of(2026, 11, 19)

    private fun dose(code: String, due: String, given: String? = null, status: String = "upcoming") =
        Dose(code, code.uppercase(), "6 weeks", due, given, status)

    private val amani = Child(
        id = 7,
        childName = "Amani Kamau",
        caregiverName = "Grace Kamau",
        phone = "+254712345678",
        dob = "2026-10-01",
        language = "sw",
        facility = "Kangemi Health Centre",
        consent = true,
        nextDueDate = "2026-11-12",
        nearestHospital = "Mbagathi County Hospital",
        nextVaccines = listOf("OPV1", "PENTA1"),
        overdueCount = 2,
        givenCount = 1,
        totalCount = 4,
        doses = listOf(
            dose("bcg", "2026-10-01", given = "2026-10-01", status = "given"),
            dose("opv1", "2026-11-12", status = "overdue"),
            dose("penta1", "2026-11-12", status = "overdue"),
            dose("mr1", "2027-07-01"),
        ),
        smsHistory = listOf(SmsEntry("manual", "sent", "Hi", "2026-11-18 09:00:00")),
    )

    private fun saved(child: Child, owner: String = WORKER_OWNER) =
        ChildWithDoses(child.toEntity(owner, savedAt = 1_000L), child.doseEntities(owner).reversed())

    @Test
    fun aSavedRecordReadsBackTheSame() {
        val back = saved(amani).toChild(savedOn)
        assertEquals(amani.copy(smsHistory = emptyList()), back)
        assertEquals(listOf("bcg", "opv1", "penta1", "mr1"), back.doses.map { it.code })
    }

    @Test
    fun doseStatusesFollowTheDateTheRecordIsOpened() {
        // Saved when MR1 was still months away; opened offline the day it falls due, then after.
        assertEquals("due", saved(amani).toChild(LocalDate.of(2027, 7, 1)).doses.last().status)
        val later = saved(amani).toChild(LocalDate.of(2027, 7, 2))
        assertEquals("overdue", later.doses.last().status)
        assertEquals(3, later.overdueCount)
        assertNull(saved(amani.copy(doses = amani.doses.map { it.copy(givenDate = "2026-11-19") })).toChild(savedOn).nextDueDate)

        assertEquals("given", doseStatus("2026-11-01", "2026-11-02", savedOn))
        assertEquals("overdue", doseStatus("2026-11-18", null, savedOn))
        assertEquals("due", doseStatus("2026-11-19", null, savedOn))
        assertEquals("upcoming", doseStatus("2026-11-20", null, savedOn))
    }

    @Test
    fun theNextVisitIsWorkedOutFromTheSavedDoses() {
        val back = saved(amani).toChild(savedOn)
        assertEquals("2026-11-12", back.nextDueDate)
        assertEquals(listOf("OPV1", "PENTA1"), back.nextVaccines)
        assertEquals(1, back.givenCount)
        assertEquals(4, back.totalCount)
    }

    @Test
    fun aListRowSavedWithoutDosesKeepsTheServersCounts() {
        val row = amani.copy(doses = emptyList())
        val back = ChildWithDoses(row.toEntity(WORKER_OWNER, 1L), emptyList()).toChild(savedOn)
        assertEquals("2026-11-12", back.nextDueDate)
        assertEquals(2, back.overdueCount)
        assertEquals(1, back.givenCount)
        assertEquals(4, back.totalCount)
    }

    @Test
    fun eachOwnerHasItsOwnCopy() {
        val caregiver = caregiverOwner(SavedLookup("+254712345678", "2026-10-01", " Amani "))
        assertEquals("caregiver:+254712345678|2026-10-01|amani", caregiver)
        assertNotEquals(amani.toEntity(WORKER_OWNER, 1L).cacheKey, amani.toEntity(caregiver, 1L).cacheKey)
        assertTrue(amani.doseEntities(caregiver).all { it.childKey == cacheKey(caregiver, 7) })
    }

    @Test
    fun phoneSearchesMatchTheStoredDigits() {
        assertEquals("712345", phoneDigits("0712 345"))
        assertEquals("712345678", phoneDigits("+254 712 345 678"))
        assertEquals("", phoneDigits("Amani"))
        assertEquals("", phoneDigits(""))
    }
}
