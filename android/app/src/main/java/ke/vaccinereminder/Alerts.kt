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
    private const val MESSAGES_PERIODIC = "clinic-messages"
    private const val MESSAGES_NOW = "clinic-messages-now"
    private const val MESSAGES_CHANNEL = "clinic_messages"
    private const val MESSAGE_TAG = "clinic-message"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    // Runs while a caregiver has children saved on this phone: vaccine checks about every 12 hours,
    // and checks for health worker notifications every 15 minutes (the shortest Android allows).
    fun sync(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(MESSAGES_PERIODIC)
            return
        }
        work.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<VaccineCheckWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
        )
        work.enqueueUniquePeriodicWork(
            MESSAGES_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ClinicMessageWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
        )
        checkMessagesNow(context)
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VaccineCheckWorker>().setConstraints(online).build(),
        )
        checkMessagesNow(context)
    }

    // Collects health worker notifications straight away, for example when the app is opened.
    fun checkMessagesNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MESSAGES_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ClinicMessageWorker>().setConstraints(online).build(),
        )
    }

    fun showMessage(context: Context, n: IncomingNotification, s: Strings): Boolean =
        post(
            context,
            channel = MESSAGES_CHANNEL,
            channelName = s.messagesChannel,
            // High importance so it pops up on screen, as a message a person sent should.
            importance = NotificationManagerCompat.IMPORTANCE_HIGH,
            tag = MESSAGE_TAG,
            id = n.id.toInt(),
            title = n.from?.let(s::messageFrom) ?: s.messageFromClinic,
            text = n.message,
            subText = n.childName,
        )

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )

    fun show(context: Context, id: Int, title: String, text: String, channelName: String): Boolean =
        post(context, CHANNEL, channelName, NotificationManagerCompat.IMPORTANCE_DEFAULT, tag = null, id = id, title = title, text = text)

    private fun post(
        context: Context,
        channel: String,
        channelName: String,
        importance: Int,
        tag: String?,
        id: Int,
        title: String,
        text: String,
        subText: String? = null,
    ): Boolean {
        if (!canNotify(context)) return false
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(NotificationChannelCompat.Builder(channel, importance).setName(channelName).build())
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_CAREGIVER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // Request codes differ per channel so one notification's tap action does not replace another's.
        val open = PendingIntent.getActivity(context, 31 * id + channel.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_vaccine)
            .setColor(0xFF0B6E4F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (importance >= NotificationManagerCompat.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // canNotify() checked the permission above, but the user can revoke it at any moment.
        return try {
            manager.notify(tag, id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

// Collects notifications health workers sent for the children saved on this phone and shows them.
// The server marks them delivered when collected, so it only asks when they can actually be shown.
class ClinicMessageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val lookups = SecureStore(applicationContext).loadLookups()
        if (lookups.isEmpty() || !VaccineAlerts.canNotify(applicationContext)) return Result.success()
        val prefs = Prefs(applicationContext)
        val s = Strings(Lang.of(prefs.language))
        val api = Api({ prefs.baseUrl }, { null }, {})
        val after = prefs.lastNotificationId
        val found = mutableListOf<IncomingNotification>()
        var offline = false
        for (lookup in lookups) {
            try {
                found += api.caregiverNotifications(lookup, after)
            } catch (e: ApiException) {
                if (e.kind == ApiException.Kind.NETWORK) offline = true
            }
        }
        val fresh = found.distinctBy { it.id }.sortedBy { it.id }
        fresh.forEach { VaccineAlerts.showMessage(applicationContext, it, s) }
        fresh.lastOrNull()?.let { prefs.lastNotificationId = it.id }
        return if (fresh.isEmpty() && offline) Result.retry() else Result.success()
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
                children += api.lookup(lookup)
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
