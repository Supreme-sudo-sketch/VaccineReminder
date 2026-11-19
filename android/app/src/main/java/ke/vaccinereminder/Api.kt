package ke.vaccinereminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class ApiException(
    val kind: Kind,
    message: String,
    val status: Int = 0,
    val code: String? = null,
    val field: String? = null,
    val address: String? = null,
) : Exception(message) {
    enum class Kind { NETWORK, BAD_ADDRESS, BAD_REPLY, SERVER }
}

data class Worker(val id: Int, val name: String, val phone: String, val facility: String)

data class Dose(
    val code: String,
    val name: String,
    val milestone: String,
    val dueDate: String,
    val givenDate: String?,
    val status: String,
    // Evidence a caregiver sent for this dose, if any.
    val verification: DoseVerification? = null,
)

// status is "pending", "verified" or "rejected"; no evidence at all means "not submitted".
data class DoseVerification(
    val id: Int,
    val status: String,
    val rejectionReason: String?,
    val vaccinationDate: String = "",
    val hospital: String = "",
    val submittedAt: String = "",
    val hasCard: Boolean = false,
    val hasNote: Boolean = false,
    // Only sent to health workers: the pictures, shown under the dose in the child's record.
    val documents: List<EvidenceDocument> = emptyList(),
)

// A caregiver's evidence that a dose was given elsewhere. The fields after hasNote are only filled
// in for health workers.
data class Verification(
    val id: Int,
    val childId: Int,
    val childName: String,
    val doseCode: String,
    val doseName: String,
    val vaccinationDate: String,
    val hospital: String,
    val doctorName: String,
    val status: String,
    val submittedAt: String,
    val submissionCount: Int,
    val reviewedAt: String?,
    val rejectionReason: String?,
    val hasCard: Boolean,
    val hasNote: Boolean,
    val milestone: String = "",
    val reviewedBy: String? = null,
    val documentCount: Int = 0,
    val child: VerificationChild? = null,
    val dose: DoseState? = null,
    val documents: List<EvidenceDocument> = emptyList(),
    val history: List<VerificationEvent> = emptyList(),
    val warnings: List<VerificationWarning> = emptyList(),
)

data class VerificationChild(val id: Int, val childName: String, val dob: String, val caregiverName: String, val phone: String, val facility: String)

// The dose as the record shows it now, next to what the evidence claims.
data class DoseState(val dueDate: String, val givenDate: String?, val status: String)

data class EvidenceDocument(val id: Int, val kind: String, val mimeType: String, val size: Int)

data class VerificationEvent(val action: String, val at: String, val by: String?, val detail: String?)

data class VerificationWarning(val code: String, val text: String)

data class VerificationList(val items: List<Verification>, val pending: Int, val reviewed: Int)

data class CaregiverEvidence(val children: List<Child>, val verifications: List<Verification>)

// What the caregiver typed for one request; card and note are the refs of uploaded pictures.
data class EvidenceInput(
    val childId: Int,
    val doseCode: String,
    val vaccinationDate: String,
    val hospital: String,
    val doctorName: String,
    val cardDocument: String?,
    val noteDocument: String?,
)

data class SmsEntry(val kind: String, val status: String, val message: String, val sentAt: String)

// A notification a health worker sent to the caregiver's app. deliveredAt is when the app collected it.
data class AppNotification(val message: String, val sentAt: String, val deliveredAt: String?, val sentBy: String?)

data class NotifyResult(val notification: AppNotification, val appSeenAt: String?)

// A notification the caregiver's app collects from the server. from is the clinic that sent it.
data class IncomingNotification(val id: Long, val childId: Int, val childName: String, val message: String, val from: String?)

data class Child(
    val id: Int,
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val dob: String,
    val language: String,
    val facility: String,
    val consent: Boolean,
    val nextDueDate: String?,
    // The hospital nearest the family's home, named in SMS ("" if not recorded).
    val nearestHospital: String = "",
    val nextVaccines: List<String>,
    val overdueCount: Int,
    val givenCount: Int,
    val totalCount: Int,
    val doses: List<Dose>,
    val smsHistory: List<SmsEntry>,
    val savedArea: SavedArea? = null,
    val appNotifications: List<AppNotification> = emptyList(),
    // When a caregiver's app last checked in for this child; null if none ever has.
    val appSeenAt: String? = null,
    // Other children with the same parent's phone number (health workers only).
    val siblings: List<Sibling> = emptyList(),
)

// A brother, sister or twin (twin: same date of birth).
data class Sibling(val id: Int, val childName: String, val dob: String, val twin: Boolean)

data class SavedArea(val label: String, val lat: Double, val lon: Double, val expiresOn: String)

data class Facility(
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double,
    val phone: String?,
    val openingHours: String?,
)

data class Place(val label: String, val name: String, val lat: Double, val lon: Double)

// A facility to pick when registering. type is "hospital", "health_centre", "clinic", or "in_use"
// for a name that is only known because someone in this system already entered it.
data class FacilityChoice(val name: String, val type: String, val county: String?)

data class FacilityChoices(val facilities: List<FacilityChoice>, val more: Boolean)

data class NearbyResult(val kind: String, val maxSearchKm: Int, val facilities: List<Facility>)

data class ChildInput(
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val dob: String,
    val language: String,
    val facility: String,
    val consent: Boolean,
    val nearestHospital: String = "",
)

data class MissedChild(
    val id: Int,
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val consent: Boolean,
    val daysOverdue: Int,
    val missedCount: Int,
    val vaccines: String,
    val lastSmsAt: String?,
)

data class DueItem(
    val id: Int,
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val dueDate: String,
    val vaccines: String,
)

data class Stats(
    val children: Int,
    val overdueChildren: Int,
    val dueToday: Int,
    val dueThisWeek: Int,
    val givenLast30Days: Int,
    val smsLast7Days: Int,
    val smsMode: String,
    val pendingVerifications: Int = 0,
    // False when SMS only go out when a health worker sends them.
    val autoReminders: Boolean = true,
)

data class ServerInfo(val today: String, val smsMode: String)

data class ReminderResult(val kind: String, val smsStatus: String)

// processed counts every reminder; sent only those that really went out by SMS (not demo or test ones).
// waiting: handed to the SMS phone, which had not confirmed sending them yet.
data class RunResult(val processed: Int, val sent: Int, val smsMode: String, val waiting: Int = 0, val failed: Int = 0)

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { block(getJSONObject(it)) }

fun parseWorker(j: JSONObject) = Worker(
    id = j.optInt("id"),
    name = j.optString("name"),
    phone = j.optString("phone"),
    facility = j.optString("facility"),
)

class Api(
    private val baseUrl: () -> String,
    private val token: () -> String?,
    private val onUnauthorized: () -> Unit,
) {

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        base: String? = null,
        readTimeoutMs: Int = 20000,
    ): JSONObject = onIo(auth) { send(method, path, body, auth, base ?: baseUrl(), readTimeoutMs) }

    private suspend fun <T> onIo(auth: Boolean, block: () -> T): T {
        try {
            return withContext(Dispatchers.IO) { block() }
        } catch (e: ApiException) {
            // Back on the caller's thread here, so the app can safely drop the session and show the login screen.
            if (auth && e.status == 401) onUnauthorized()
            throw e
        }
    }

    private fun send(method: String, path: String, body: JSONObject?, auth: Boolean, base: String, readTimeoutMs: Int): JSONObject =
        exchange(path, base, configure = { conn ->
            // HttpURLConnection cannot send PATCH; the server accepts POST with an override header.
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            val t = token()
            if (auth && t != null) conn.setRequestProperty("Authorization", "Bearer $t")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }) { conn -> readJson(conn) }

    private fun readJson(conn: HttpURLConnection): JSONObject {
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    // Opens a connection to base + path, lets configure() set it up and send any body, and reads a
    // successful reply with read(). Failures become ApiExceptions the screens know how to show.
    private fun <T> exchange(path: String, base: String, configure: (HttpURLConnection) -> Unit, read: (HttpURLConnection) -> T): T {
        val trimmed = base.trim().trimEnd('/')
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw ApiException(ApiException.Kind.BAD_ADDRESS, "Server address must start with http:// or https://")
        }
        val conn = try {
            URL(trimmed + path).openConnection() as HttpURLConnection
        } catch (e: MalformedURLException) {
            throw ApiException(ApiException.Kind.BAD_ADDRESS, "Invalid server address")
        } catch (e: IOException) {
            throw ApiException(ApiException.Kind.BAD_ADDRESS, "Invalid server address")
        }
        try {
            conn.connectTimeout = 10000
            configure(conn)
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val json = if (text.isBlank()) JSONObject() else JSONObject(text)
                throw ApiException(
                    kind = ApiException.Kind.SERVER,
                    message = json.optString("error").ifBlank { "Error $code" },
                    status = code,
                    code = json.stringOrNull("code"),
                    field = json.stringOrNull("field"),
                )
            }
            return read(conn)
        } catch (e: ApiException) {
            throw e
        } catch (e: JSONException) {
            throw ApiException(ApiException.Kind.BAD_REPLY, "Unexpected reply from the server")
        } catch (e: IOException) {
            throw ApiException(ApiException.Kind.NETWORK, "Cannot reach the server at $trimmed", address = trimmed)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseChild(j: JSONObject): Child {
        val doses = j.optJSONArray("doses").mapObjects { d ->
            Dose(
                code = d.getString("code"),
                name = d.getString("name"),
                milestone = d.optString("milestone"),
                dueDate = d.getString("dueDate"),
                givenDate = d.stringOrNull("givenDate"),
                status = d.getString("status"),
                verification = d.optJSONObject("verification")?.let { v ->
                    DoseVerification(
                        id = v.getInt("id"),
                        status = v.getString("status"),
                        rejectionReason = v.stringOrNull("rejectionReason"),
                        vaccinationDate = v.optString("vaccinationDate"),
                        hospital = v.optString("hospital"),
                        submittedAt = v.optString("submittedAt"),
                        hasCard = v.optBoolean("hasCard"),
                        hasNote = v.optBoolean("hasNote"),
                        documents = v.optJSONArray("documents").mapObjects { doc -> EvidenceDocument(doc.getInt("id"), doc.getString("kind"), "", 0) },
                    )
                },
            )
        }
        val history = j.optJSONArray("smsHistory").mapObjects { h ->
            SmsEntry(h.optString("kind"), h.optString("status"), h.optString("message"), h.optString("sentAt"))
        }
        val next = j.optJSONArray("nextVaccines")
        return Child(
            id = j.getInt("id"),
            childName = j.getString("childName"),
            caregiverName = j.getString("caregiverName"),
            phone = j.getString("phone"),
            dob = j.getString("dob"),
            language = j.optString("language", "en"),
            facility = j.optString("facility", ""),
            nearestHospital = j.optString("nearestHospital", ""),
            consent = j.optBoolean("consent", true),
            nextDueDate = j.stringOrNull("nextDueDate"),
            nextVaccines = if (next == null) emptyList() else (0 until next.length()).map { next.getString(it) },
            overdueCount = j.optInt("overdueCount", 0),
            givenCount = j.optInt("givenCount", 0),
            totalCount = j.optInt("totalCount", doses.size),
            doses = doses,
            smsHistory = history,
            savedArea = j.optJSONObject("savedArea")?.let(::parseArea),
            appNotifications = j.optJSONArray("appNotifications").mapObjects(::parseAppNotification),
            appSeenAt = j.stringOrNull("appSeenAt"),
            siblings = j.optJSONArray("siblings").mapObjects { Sibling(it.getInt("id"), it.optString("childName"), it.optString("dob"), it.optBoolean("twin")) },
        )
    }

    private fun parseAppNotification(n: JSONObject) =
        AppNotification(n.optString("message"), n.optString("sentAt"), n.stringOrNull("deliveredAt"), n.stringOrNull("sentBy"))

    private fun parseArea(a: JSONObject) =
        SavedArea(a.optString("label"), a.getDouble("lat"), a.getDouble("lon"), a.optString("expiresOn"))

    private fun coord(value: Double) = String.format(Locale.US, "%.5f", value)

    suspend fun nearbyFacilities(lat: Double, lon: Double): NearbyResult {
        val j = request("GET", "/facilities/nearby?lat=${coord(lat)}&lon=${coord(lon)}", auth = false, readTimeoutMs = 120000)
        val facilities = j.getJSONArray("facilities")
            .mapObjects { f ->
                Facility(
                    name = f.getString("name"),
                    type = f.optString("type", "clinic"),
                    lat = f.getDouble("lat"),
                    lon = f.getDouble("lon"),
                    distanceKm = f.optDouble("distanceKm", 0.0),
                    phone = f.stringOrNull("phone"),
                    openingHours = f.stringOrNull("openingHours"),
                )
            }
        return NearbyResult(j.optString("kind", "clinic"), j.optInt("maxSearchKm", 50), facilities)
    }

    suspend fun geocode(query: String): List<Place> =
        request("GET", "/geocode?q=" + URLEncoder.encode(query, "UTF-8"), auth = false, readTimeoutMs = 30000)
            .getJSONArray("results")
            .mapObjects { p -> Place(p.getString("label"), p.optString("name"), p.getDouble("lat"), p.getDouble("lon")) }

    suspend fun facilityChoices(query: String): FacilityChoices {
        val j = request("GET", "/facilities/list?q=" + URLEncoder.encode(query, "UTF-8"), auth = false)
        return FacilityChoices(
            facilities = j.optJSONArray("facilities").mapObjects { FacilityChoice(it.getString("name"), it.optString("type"), it.stringOrNull("county")) },
            more = j.optBoolean("more"),
        )
    }

    // What every caregiver request carries to show who is asking.
    private fun SavedLookup.json() = JSONObject().put("phone", phone).put("childDob", dob).put("childName", childName)

    suspend fun saveArea(who: SavedLookup, lat: Double?, lon: Double?, label: String?): SavedArea? {
        val body = who.json()
        if (lat == null || lon == null) {
            body.put("lat", JSONObject.NULL)
        } else {
            body.put("lat", lat).put("lon", lon).put("label", label ?: "")
        }
        return request("POST", "/caregiver/location", body, auth = false).optJSONObject("savedArea")?.let(::parseArea)
    }

    private fun parseAuth(j: JSONObject) = Session(j.getString("token"), parseWorker(j.getJSONObject("worker")))

    suspend fun health(base: String? = null): ServerInfo {
        val j = request("GET", "/health", auth = false, base = base)
        return ServerInfo(j.optString("today"), j.optString("smsMode", "test"))
    }

    suspend fun login(phone: String, password: String): Session {
        val body = JSONObject().put("phone", phone).put("password", password)
        return parseAuth(request("POST", "/auth/login", body, auth = false))
    }

    suspend fun registerWorker(name: String, phone: String, facility: String, password: String, code: String): Session {
        val body = JSONObject()
            .put("name", name)
            .put("phone", phone)
            .put("facility", facility)
            .put("password", password)
            .put("registrationCode", code)
        return parseAuth(request("POST", "/auth/register", body, auth = false))
    }

    suspend fun me(): Worker = parseWorker(request("GET", "/auth/me").getJSONObject("worker"))

    suspend fun logout() {
        request("POST", "/auth/logout")
    }

    suspend fun changePassword(current: String, new: String) {
        request("POST", "/auth/password", JSONObject().put("currentPassword", current).put("newPassword", new))
    }

    suspend fun lookup(who: SavedLookup): List<Child> {
        val body = who.json()
        return request("POST", "/caregiver/lookup", body, auth = false).getJSONArray("children").mapObjects(::parseChild)
    }

    suspend fun stats(): Stats {
        val j = request("GET", "/stats")
        return Stats(
            children = j.optInt("children"),
            overdueChildren = j.optInt("overdueChildren"),
            dueToday = j.optInt("dueToday"),
            dueThisWeek = j.optInt("dueThisWeek"),
            givenLast30Days = j.optInt("givenLast30Days"),
            smsLast7Days = j.optInt("smsLast7Days"),
            smsMode = j.optString("smsMode", "test"),
            pendingVerifications = j.optInt("pendingVerifications", 0),
            autoReminders = j.optBoolean("autoReminders", true),
        )
    }

    suspend fun listChildren(query: String, filter: String): List<Child> {
        val path = "/children?q=" + URLEncoder.encode(query, "UTF-8") + "&filter=" + filter
        return request("GET", path).getJSONArray("children").mapObjects(::parseChild)
    }

    suspend fun getChild(id: Int): Child = parseChild(request("GET", "/children/$id"))

    private fun ChildInput.toJson() = JSONObject()
        .put("childName", childName)
        .put("caregiverName", caregiverName)
        .put("phone", phone)
        .put("dob", dob)
        .put("language", language)
        .put("facility", facility)
        .put("consent", consent)
        .put("nearestHospital", nearestHospital)

    suspend fun register(input: ChildInput): Child = parseChild(request("POST", "/children", input.toJson()))

    suspend fun update(id: Int, input: ChildInput): Child = parseChild(request("PATCH", "/children/$id", input.toJson()))

    suspend fun deleteChild(id: Int) {
        request("DELETE", "/children/$id")
    }

    suspend fun markGiven(id: Int, code: String, date: String): Child =
        parseChild(request("POST", "/children/$id/doses/$code/given", JSONObject().put("date", date)))

    // `given` maps dose code to the date it was given.
    suspend fun recordDoses(id: Int, given: Map<String, String>): Child {
        val arr = JSONArray()
        given.forEach { (code, date) -> arr.put(JSONObject().put("code", code).put("date", date)) }
        return parseChild(request("POST", "/children/$id/doses", JSONObject().put("given", arr)))
    }

    suspend fun undo(id: Int, code: String): Child =
        parseChild(request("POST", "/children/$id/doses/$code/undo"))

    suspend fun missed(): List<MissedChild> =
        request("GET", "/missed").getJSONArray("children").mapObjects { j ->
            MissedChild(
                id = j.getInt("id"),
                childName = j.getString("childName"),
                caregiverName = j.getString("caregiverName"),
                phone = j.getString("phone"),
                consent = j.optBoolean("consent", true),
                daysOverdue = j.getInt("daysOverdue"),
                missedCount = j.getInt("missedCount"),
                vaccines = j.getString("vaccines"),
                lastSmsAt = j.stringOrNull("lastSmsAt"),
            )
        }

    suspend fun due(days: Int): List<DueItem> =
        request("GET", "/due?days=$days").getJSONArray("items").mapObjects { j ->
            DueItem(
                id = j.getInt("id"),
                childName = j.getString("childName"),
                caregiverName = j.getString("caregiverName"),
                phone = j.getString("phone"),
                dueDate = j.getString("dueDate"),
                vaccines = j.getString("vaccines"),
            )
        }

    // An empty message sends the usual vaccine reminder.
    suspend fun sendNotification(childId: Int, message: String): NotifyResult {
        val res = request("POST", "/children/$childId/notify", JSONObject().put("message", message))
        return NotifyResult(parseAppNotification(res.getJSONObject("notification")), res.stringOrNull("appSeenAt"))
    }

    suspend fun caregiverNotifications(who: SavedLookup, after: Long): List<IncomingNotification> {
        val body = who.json().put("after", after)
        return request("POST", "/caregiver/notifications", body, auth = false).optJSONArray("notifications").mapObjects { n ->
            IncomingNotification(n.getLong("id"), n.optInt("childId"), n.optString("childName"), n.getString("message"), n.stringOrNull("from"))
        }
    }

    suspend fun remind(id: Int): ReminderResult {
        // The server waits a few seconds for the SMS phone to confirm it sent the message.
        val res = request("POST", "/children/$id/remind", readTimeoutMs = 60000)
        return ReminderResult(res.getString("kind"), res.getString("smsStatus"))
    }

    suspend fun runReminders(): RunResult {
        val res = request("POST", "/reminders/run", readTimeoutMs = 180000)
        val all = res.getJSONArray("sent")
        fun count(status: String) = (0 until all.length()).count { all.getJSONObject(it).optString("status") == status }
        return RunResult(all.length(), count("sent"), res.optString("smsMode", "test"), count("queued"), count("failed"))
    }

    // ----- Vaccination verification -----

    private fun parseVerification(j: JSONObject): Verification = Verification(
        id = j.getInt("id"),
        childId = j.getInt("childId"),
        childName = j.optString("childName"),
        doseCode = j.getString("doseCode"),
        doseName = j.optString("doseName"),
        vaccinationDate = j.optString("vaccinationDate"),
        hospital = j.optString("hospital"),
        doctorName = j.optString("doctorName"),
        status = j.getString("status"),
        submittedAt = j.optString("submittedAt"),
        submissionCount = j.optInt("submissionCount", 1),
        reviewedAt = j.stringOrNull("reviewedAt"),
        rejectionReason = j.stringOrNull("rejectionReason"),
        hasCard = j.optBoolean("hasCard"),
        hasNote = j.optBoolean("hasNote"),
        milestone = j.optString("milestone"),
        reviewedBy = j.stringOrNull("reviewedBy"),
        documentCount = j.optInt("documentCount"),
        child = j.optJSONObject("child")?.let { c ->
            VerificationChild(
                c.getInt("id"), c.optString("childName"), c.optString("dob"), c.optString("caregiverName"),
                c.optString("phone"), c.optString("facility"),
            )
        },
        dose = j.optJSONObject("dose")?.let { d -> DoseState(d.optString("dueDate"), d.stringOrNull("givenDate"), d.optString("status")) },
        documents = j.optJSONArray("documents").mapObjects { d ->
            EvidenceDocument(d.getInt("id"), d.getString("kind"), d.optString("mimeType"), d.optInt("size"))
        },
        history = j.optJSONArray("history").mapObjects { e ->
            VerificationEvent(e.getString("action"), e.optString("at"), e.stringOrNull("by"), e.stringOrNull("detail"))
        },
        warnings = j.optJSONArray("warnings").mapObjects { w -> VerificationWarning(w.optString("code"), w.optString("text")) },
    )

    // Sends one picture as raw JPEG bytes, reporting progress from 0 to 1 as it goes. Returns the ref
    // to send with the request.
    suspend fun uploadEvidence(who: SavedLookup, childId: Int, kind: String, bytes: ByteArray, onProgress: (Float) -> Unit): String =
        onIo(auth = false) {
            exchange("/caregiver/verification-documents?childId=$childId&kind=$kind", baseUrl(), configure = { conn ->
                conn.requestMethod = "POST"
                conn.readTimeout = 60000
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "image/jpeg")
                conn.setRequestProperty("X-Caregiver-Phone", who.phone)
                conn.setRequestProperty("X-Child-Dob", who.dob)
                // Header values must be plain ASCII, so the name is URL-encoded.
                conn.setRequestProperty("X-Child-Name", android.net.Uri.encode(who.childName))
                conn.outputStream.use { out ->
                    var sent = 0
                    while (sent < bytes.size) {
                        val n = minOf(16 * 1024, bytes.size - sent)
                        out.write(bytes, sent, n)
                        sent += n
                        onProgress(sent.toFloat() / bytes.size)
                    }
                }
            }) { conn -> readJson(conn).getJSONObject("document").getString("ref") }
        }

    private fun EvidenceInput.toJson(who: SavedLookup) = who.json()
        .put("childId", childId)
        .put("doseCode", doseCode)
        .put("vaccinationDate", vaccinationDate)
        .put("hospital", hospital)
        .put("doctorName", doctorName)
        .put("cardDocument", cardDocument ?: JSONObject.NULL)
        .put("noteDocument", noteDocument ?: JSONObject.NULL)

    suspend fun submitEvidence(who: SavedLookup, input: EvidenceInput): Verification =
        parseVerification(request("POST", "/caregiver/verifications", input.toJson(who), auth = false).getJSONObject("verification"))

    suspend fun resubmitEvidence(id: Int, who: SavedLookup, input: EvidenceInput): Verification =
        parseVerification(
            request("POST", "/caregiver/verifications/$id/resubmit", input.toJson(who), auth = false).getJSONObject("verification"),
        )

    suspend fun caregiverEvidence(who: SavedLookup): CaregiverEvidence {
        val body = who.json()
        val j = request("POST", "/caregiver/verifications/status", body, auth = false)
        return CaregiverEvidence(j.optJSONArray("children").mapObjects(::parseChild), j.optJSONArray("verifications").mapObjects(::parseVerification))
    }

    // status is "pending" or "reviewed".
    suspend fun verifications(status: String): VerificationList {
        val j = request("GET", "/verifications?status=$status")
        val counts = j.optJSONObject("counts")
        return VerificationList(
            j.optJSONArray("verifications").mapObjects(::parseVerification),
            counts?.optInt("pending") ?: 0,
            counts?.optInt("reviewed") ?: 0,
        )
    }

    suspend fun verification(id: Int): Verification = parseVerification(request("GET", "/verifications/$id").getJSONObject("verification"))

    // The picture itself. Only a signed-in health worker can fetch it.
    suspend fun evidenceDocument(verificationId: Int, documentId: Int): ByteArray = onIo(auth = true) {
        exchange("/verifications/$verificationId/documents/$documentId", baseUrl(), configure = { conn ->
            conn.requestMethod = "GET"
            conn.readTimeout = 60000
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        }) { conn -> conn.inputStream.use { it.readBytes() } }
    }

    suspend fun approveVerification(id: Int): Verification =
        parseVerification(request("POST", "/verifications/$id/approve", JSONObject()).getJSONObject("verification"))

    suspend fun rejectVerification(id: Int, reason: String): Verification =
        parseVerification(request("POST", "/verifications/$id/reject", JSONObject().put("reason", reason)).getJSONObject("verification"))
}
