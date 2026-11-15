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
)

data class SmsEntry(val kind: String, val status: String, val message: String, val sentAt: String)

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
    val nextVaccines: List<String>,
    val overdueCount: Int,
    val givenCount: Int,
    val totalCount: Int,
    val doses: List<Dose>,
    val smsHistory: List<SmsEntry>,
    val savedArea: SavedArea? = null,
)

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

data class NearbyResult(val kind: String, val maxSearchKm: Int, val facilities: List<Facility>)

data class ChildInput(
    val childName: String,
    val caregiverName: String,
    val phone: String,
    val dob: String,
    val language: String,
    val facility: String,
    val consent: Boolean,
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
)

data class ServerInfo(val today: String, val smsMode: String)

data class ReminderResult(val kind: String, val smsStatus: String)

data class RunResult(val processed: Int, val smsMode: String)

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
    ): JSONObject {
        try {
            return withContext(Dispatchers.IO) { send(method, path, body, auth, base ?: baseUrl(), readTimeoutMs) }
        } catch (e: ApiException) {
            // Back on the caller's thread here, so the app can safely drop the session and show the login screen.
            if (auth && e.status == 401) onUnauthorized()
            throw e
        }
    }

    private fun send(method: String, path: String, body: JSONObject?, auth: Boolean, base: String, readTimeoutMs: Int): JSONObject {
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
            // HttpURLConnection cannot send PATCH; the server accepts POST with an override header.
            if (method == "PATCH") {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                conn.requestMethod = method
            }
            conn.connectTimeout = 10000
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            val t = token()
            if (auth && t != null) conn.setRequestProperty("Authorization", "Bearer $t")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw ApiException(
                    kind = ApiException.Kind.SERVER,
                    message = json.optString("error").ifBlank { "Error $code" },
                    status = code,
                    code = json.stringOrNull("code"),
                    field = json.stringOrNull("field"),
                )
            }
            return json
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
            consent = j.optBoolean("consent", true),
            nextDueDate = j.stringOrNull("nextDueDate"),
            nextVaccines = if (next == null) emptyList() else (0 until next.length()).map { next.getString(it) },
            overdueCount = j.optInt("overdueCount", 0),
            givenCount = j.optInt("givenCount", 0),
            totalCount = j.optInt("totalCount", doses.size),
            doses = doses,
            smsHistory = history,
            savedArea = j.optJSONObject("savedArea")?.let(::parseArea),
        )
    }

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

    suspend fun saveArea(phone: String, childDob: String, lat: Double?, lon: Double?, label: String?): SavedArea? {
        val body = JSONObject().put("phone", phone).put("childDob", childDob)
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

    suspend fun lookup(phone: String, childDob: String): List<Child> {
        val body = JSONObject().put("phone", phone).put("childDob", childDob)
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

    suspend fun remind(id: Int): ReminderResult {
        val res = request("POST", "/children/$id/remind")
        return ReminderResult(res.getString("kind"), res.getString("smsStatus"))
    }

    suspend fun runReminders(): RunResult {
        val res = request("POST", "/reminders/run")
        return RunResult(res.getJSONArray("sent").length(), res.optString("smsMode", "test"))
    }
}
