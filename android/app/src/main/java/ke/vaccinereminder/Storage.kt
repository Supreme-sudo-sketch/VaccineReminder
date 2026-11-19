package ke.vaccinereminder

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vaccine_reminder", Context.MODE_PRIVATE)

    init {
        // Version 1 kept a shared health worker key here in plain text. Accounts replaced it.
        if (sp.contains("apiKey")) sp.edit { remove("apiKey") }
    }

    var baseUrl: String
        get() = sp.getString("baseUrl", DEFAULT_URL) ?: DEFAULT_URL
        set(value) = sp.edit { putString("baseUrl", value.trim().trimEnd('/')) }

    var language: String
        get() = sp.getString("language", "en") ?: "en"
        set(value) = sp.edit { putString("language", value) }

    // "system", "light" or "dark". See ThemeMode.
    var theme: String
        get() = sp.getString("theme", ThemeMode.SYSTEM.code) ?: ThemeMode.SYSTEM.code
        set(value) = sp.edit { putString("theme", value) }

    // Pre-fills the phone field on the login screen. Not secret: it is the worker's own number.
    var lastWorkerPhone: String
        get() = sp.getString("lastWorkerPhone", "") ?: ""
        set(value) = sp.edit { putString("lastWorkerPhone", value) }

    // Highest health worker notification already shown, kept per server since each counts from 1.
    var lastNotificationId: Long
        get() = sp.getLong("lastNotificationId|$baseUrl", 0L)
        set(value) = sp.edit { putLong("lastNotificationId|$baseUrl", value) }

    fun notifiedOn(day: String): Set<String> =
        (sp.getStringSet("notified", emptySet()) ?: emptySet()).filter { it.endsWith("|$day") }.toSet()

    fun markNotified(day: String, keys: Collection<String>) {
        sp.edit { putStringSet("notified", notifiedOn(day) + keys) }
    }

    companion object {
        val DEFAULT_URL: String
            get() = if (isEmulator()) "http://10.0.2.2:3000" else "http://127.0.0.1:3000"

        private fun isEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.HARDWARE in setOf("goldfish", "ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for")
    }
}

data class Session(val token: String, val worker: Worker)

// How a caregiver signs in for one child: the phone number, the child's name and date of birth, as
// the clinic registered them.
data class SavedLookup(val phone: String, val dob: String, val childName: String)

// Values encrypted with an AES key that never leaves the Android Keystore. If the key is lost
// (for example after a backup restore) reading returns null and the user simply logs in again.
class SecureStore(context: Context) {
    private val sp = context.getSharedPreferences("vaccine_reminder_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun put(name: String, value: String?) {
        if (value == null) {
            sp.edit { remove(name) }
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            sp.edit { putString(name, Base64.encodeToString(out, Base64.NO_WRAP)) }
        } catch (e: Exception) {
            sp.edit { remove(name) }
        }
    }

    private fun get(name: String): String? {
        val stored = sp.getString(name, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_SIZE))
            String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun loadSession(): Session? {
        val j = get("session")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val w = j.optJSONObject("worker") ?: return null
        return Session(j.optString("token"), parseWorker(w)).takeIf { it.token.isNotBlank() }
    }

    fun saveSession(session: Session?) {
        put(
            "session",
            session?.let {
                JSONObject()
                    .put("token", it.token)
                    .put(
                        "worker",
                        JSONObject()
                            .put("id", it.worker.id)
                            .put("name", it.worker.name)
                            .put("phone", it.worker.phone)
                            .put("facility", it.worker.facility),
                    )
                    .toString()
            },
        )
    }

    // Sign-ins a caregiver chose to remember, so the app opens on their children. Ones saved before the
    // child's name was asked for are dropped, so the caregiver signs in once more.
    fun loadLookups(): List<SavedLookup> {
        val arr = get("lookups")?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { SavedLookup(it.optString("phone"), it.optString("dob"), it.optString("childName")) }
                ?.takeIf { it.childName.isNotBlank() }
        }
    }

    fun saveLookups(list: List<SavedLookup>) {
        if (list.isEmpty()) {
            put("lookups", null)
            return
        }
        val arr = JSONArray()
        list.distinct().take(MAX_LOOKUPS).forEach { arr.put(JSONObject().put("phone", it.phone).put("dob", it.dob).put("childName", it.childName)) }
        put("lookups", arr.toString())
    }

    companion object {
        private const val ALIAS = "vaccine_reminder_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val MAX_LOOKUPS = 6
    }
}
