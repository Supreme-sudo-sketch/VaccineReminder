package ke.vaccinereminder

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

// A result and where it came from: the server (savedAt null), or the copy saved on this phone at
// savedAt (milliseconds since 1970) because the server could not be reached.
data class Loaded<T>(val data: T, val savedAt: Long? = null)

// Children's records for the screens. Asks the server first and saves what it sends through the
// DAO; when the server cannot be reached, answers from that saved copy instead. Any other error
// (signed out, not found, refused) is passed on as usual.
class ChildRepository(
    private val api: Api,
    private val dao: ChildDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = ::todayNairobi,
) {
    // For saving in the background without holding up the screen.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ----- Health worker -----

    suspend fun listChildren(query: String, filter: String): Loaded<List<Child>> =
        try {
            val rows = api.listChildren(query, filter)
            val saved = now()
            dao.saveSummaries(rows.map { it.toEntity(WORKER_OWNER, saved) })
            // The full list, unfiltered: anything else saved was removed on the server.
            if (query.isBlank() && filter == "all" && rows.size < 200) dao.deleteOwnerExcept(WORKER_OWNER, rows.map { it.id })
            Loaded(rows)
        } catch (e: ApiException) {
            if (!e.offline() || dao.count(WORKER_OWNER) == 0) throw e
            val day = today()
            val found = dao.search(WORKER_OWNER, query, phoneDigits(query), filter, day.toString(), day.plusDays(7).toString())
            Loaded(found.map { it.toChild(day) }, found.minOfOrNull { it.child.savedAt } ?: now())
        }

    suspend fun getChild(id: Int): Loaded<Child> =
        try {
            Loaded(api.getChild(id).also { saveWorkerChild(it) })
        } catch (e: ApiException) {
            // A list row saved without its schedule is not enough to show the record.
            val saved = (if (e.offline()) dao.withDoses(cacheKey(WORKER_OWNER, id))?.takeIf { it.doses.isNotEmpty() } else null) ?: throw e
            Loaded(saved.toChild(today()), saved.child.savedAt)
        }

    // After the health worker registered, edited or recorded doses: the server's reply is the new copy.
    fun saved(child: Child) {
        scope.launch { saveWorkerChild(child) }
    }

    fun deleted(id: Int) {
        scope.launch { dao.delete(cacheKey(WORKER_OWNER, id)) }
    }

    // On sign-out: the next health worker on this phone must not see these children.
    fun forgetWorker() {
        scope.launch { dao.deleteOwner(WORKER_OWNER) }
    }

    private suspend fun saveWorkerChild(child: Child) {
        dao.saveFull(child.toEntity(WORKER_OWNER, now()), child.doseEntities(WORKER_OWNER))
    }

    // ----- Caregiver -----

    // save: keep a copy only for sign-ins the caregiver chose to remember on this phone.
    suspend fun lookup(who: SavedLookup, save: Boolean): Loaded<List<Child>> {
        val owner = caregiverOwner(who)
        return try {
            val children = api.lookup(who)
            if (save) {
                val saved = now()
                dao.replaceOwner(owner, children.map { it.toEntity(owner, saved) to it.doseEntities(owner) })
            }
            Loaded(children)
        } catch (e: ApiException) {
            // Not found: the clinic removed or corrected the record, so the copy goes too.
            if (e.status == 404) dao.deleteOwner(owner)
            val saved = (if (e.offline() && save) dao.byOwner(owner).takeIf { it.isNotEmpty() } else null) ?: throw e
            Loaded(saved.map { it.toChild(today()) }, saved.minOf { it.child.savedAt })
        }
    }

    // Keeps only the copies for these remembered sign-ins.
    fun keepCaregivers(remembered: List<SavedLookup>) {
        scope.launch { dao.deleteCaregiversExcept(remembered.map(::caregiverOwner)) }
    }

    private fun ApiException.offline() = kind == ApiException.Kind.NETWORK
}

val LocalChildren = staticCompositionLocalOf<ChildRepository> { error("ChildRepository not provided") }

// ----- Between the app's Child and the saved rows -----

fun Child.toEntity(owner: String, savedAt: Long) = ChildEntity(
    cacheKey = cacheKey(owner, id),
    owner = owner,
    id = id,
    childName = childName,
    caregiverName = caregiverName,
    phone = phone,
    dob = dob,
    language = language,
    facility = facility,
    nearestHospital = nearestHospital,
    consent = consent,
    nextDueDate = nextDueDate,
    overdueCount = overdueCount,
    givenCount = givenCount,
    totalCount = totalCount,
    savedAt = savedAt,
)

fun Child.doseEntities(owner: String): List<DoseEntity> =
    doses.mapIndexed { i, d -> DoseEntity(cacheKey(owner, id), d.code, i, d.name, d.milestone, d.dueDate, d.givenDate) }

// The same rule as the server: given, overdue once the date has passed, due on the day, else upcoming.
fun doseStatus(dueDate: String, givenDate: String?, today: LocalDate): String {
    if (givenDate != null) return "given"
    val due = parseIsoDate(dueDate) ?: return "upcoming"
    return when {
        due.isBefore(today) -> "overdue"
        due.isEqual(today) -> "due"
        else -> "upcoming"
    }
}

// A saved record as the screens show it, worked out for `today`. What only the server knows (SMS
// sent, messages, proof sent, brothers and sisters) is left empty.
fun ChildWithDoses.toChild(today: LocalDate): Child {
    val c = child
    val doses = doses.sortedBy { it.position }.map { d ->
        Dose(d.code, d.name, d.milestone, d.dueDate, d.givenDate, doseStatus(d.dueDate, d.givenDate, today))
    }
    val pending = doses.filter { it.status != "given" }
    val nextDue = if (doses.isEmpty()) c.nextDueDate else pending.minOfOrNull { it.dueDate }
    return Child(
        id = c.id,
        childName = c.childName,
        caregiverName = c.caregiverName,
        phone = c.phone,
        dob = c.dob,
        language = c.language,
        facility = c.facility,
        consent = c.consent,
        nextDueDate = nextDue,
        nearestHospital = c.nearestHospital,
        nextVaccines = pending.filter { it.dueDate == nextDue }.map { it.name },
        overdueCount = if (doses.isEmpty()) c.overdueCount else doses.count { it.status == "overdue" },
        givenCount = if (doses.isEmpty()) c.givenCount else doses.size - pending.size,
        totalCount = if (doses.isEmpty()) c.totalCount else doses.size,
        doses = doses,
        smsHistory = emptyList(),
    )
}

// "0712 345 678" or "+254 712..." as the digits stored after +254, for searching by phone.
fun phoneDigits(query: String): String {
    val digits = query.replace(Regex("[\\s\\-()+]"), "")
    return if (digits.isNotEmpty() && digits.all { it.isDigit() }) digits.replace(Regex("^(254|0)"), "") else ""
}
