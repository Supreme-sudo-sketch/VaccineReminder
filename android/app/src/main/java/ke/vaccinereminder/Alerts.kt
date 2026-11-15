package ke.vaccinereminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object VaccineAlerts {
    const val EXTRA_OPEN = "open"
    const val OPEN_CAREGIVER = "caregiver"
    private const val PERIODIC = "vaccine-alerts"
    private const val NOW = "vaccine-alerts-now"
    private const val CHANNEL = "vaccine_alerts"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        work.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<VaccineCheckWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
        )
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VaccineCheckWorker>().setConstraints(online).build(),
        )
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    fun show(context: Context, id: Int, title: String, text: String, channelName: String): Boolean {
        if (!canNotify(context)) return false
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(channelName).build(),
        )
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_CAREGIVER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val open = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vaccine)
            .setColor(0xFF0B6E4F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // canNotify() checked the permission above, but the user can revoke it at any moment.
        return try {
            manager.notify(id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

class VaccineCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val lookups = SecureStore(applicationContext).loadLookups()
        if (lookups.isEmpty()) return Result.success()
        val prefs = Prefs(applicationContext)
        val s = Strings(Lang.of(prefs.language))
        val api = Api({ prefs.baseUrl }, { null }, {})
        val children = mutableListOf<Child>()
        var offline = false
        for (lookup in lookups) {
            try {
                children += api.lookup(lookup.phone, lookup.dob)
            } catch (e: ApiException) {
                if (e.kind == ApiException.Kind.NETWORK) offline = true
            }
        }
        if (children.isEmpty() && offline) return Result.retry()
        val today = todayNairobi().toString()
        for (child in children.distinctBy { it.id }) alertIfDue(child, api, s, prefs, today)
        return Result.success()
    }

    private suspend fun alertIfDue(child: Child, api: Api, s: Strings, prefs: Prefs, today: String) {
        val due = child.doses.filter { it.status != "given" && (daysFromToday(it.dueDate) ?: Long.MAX_VALUE) <= 3 }
        if (due.isEmpty()) return
        val keys = due.map { "${child.id}|${it.code}|$today" }
        if (prefs.notifiedOn(today).containsAll(keys)) return
        val area = child.savedArea
        val clinic = if (area == null) {
            null
        } else {
            try {
                api.nearbyFacilities(area.lat, area.lon).facilities.firstOrNull()
            } catch (e: ApiException) {
                null
            }
        }
        val first = due.minOf { it.dueDate }
        val parts = mutableListOf(
            due.joinToString(", ") { it.name } + " · " + relativeDate(first, s) + " (" + formatDate(first, s.lang) + ").",
        )
        if (clinic != null) {
            parts += s.nearestFacility(clinic.type, clinic.name, clinic.distanceKm, clinic.phone)
            parts += s.callToConfirm
        } else if (child.facility.isNotBlank()) {
            parts += s.registeredClinic(child.facility)
        }
        val title = s.alertTitle(child.childName, due.any { it.status == "overdue" })
        if (VaccineAlerts.show(applicationContext, child.id, title, parts.joinToString(" "), s.alertsChannel)) {
            prefs.markNotified(today, keys)
        }
    }
}
