@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@Composable
fun CaregiverScreen(
    api: Api,
    secure: SecureStore,
    s: Strings,
    lookupsState: MutableState<List<SavedLookup>>,
    onNearby: () -> Unit,
    onBack: () -> Unit,
) {
    // `persisted` is what the caregiver asked us to remember; `lookups` also holds this visit's searches.
    var persisted by remember { mutableStateOf(secure.loadLookups()) }
    var lookups by lookupsState
    val context = LocalContext.current
    val notify = LocalNotify.current
    var canNotify by remember { mutableStateOf(VaccineAlerts.canNotify(context)) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = VaccineAlerts.canNotify(context)
        askedNotifications = true
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canNotify = VaccineAlerts.canNotify(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun askNotifications() {
        val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRuntime && !askedNotifications) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
    }
    var found by remember { mutableStateOf<List<Pair<SavedLookup, Child>>>(emptyList()) }
    var loading by remember { mutableStateOf(lookups.isNotEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<ApiException?>(null) }
    var showForm by rememberSaveable { mutableStateOf(persisted.isEmpty()) }
    var reload by remember { mutableIntStateOf(0) }

    var phone by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var rememberLookup by rememberSaveable { mutableStateOf(true) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var dobError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        val results = mutableListOf<Pair<SavedLookup, Child>>()
        val keep = mutableListOf<SavedLookup>()
        var failure: ApiException? = null
        for (l in lookups) {
            try {
                api.lookup(l.phone, l.dob).forEach { results += l to it }
                keep += l
            } catch (e: ApiException) {
                // A 404 means the clinic removed or corrected the record, so stop looking it up.
                if (e.status != 404) {
                    failure = e
                    keep += l
                }
            }
        }
        if (keep.size != lookups.size) {
            lookups = keep
            persisted = persisted.filter { it in keep }
            secure.saveLookups(persisted)
        }
        found = results.distinctBy { it.second.id }
        loadError = failure
        loading = false
        refreshing = false
        if (lookups.isEmpty()) showForm = true
    }

    fun find() {
        val normalized = Phone.normalize(phone)
        phoneError = if (normalized == null) s.errPhone else null
        dobError = if (dob.isBlank()) s.errDob else null
        if (normalized == null || dob.isBlank() || busy) return
        scope.launch {
            busy = true
            formError = null
            try {
                val lookup = SavedLookup(normalized, dob)
                val children = api.lookup(normalized, dob)
                lookups = (lookups + lookup).distinct()
                if (rememberLookup) {
                    persisted = (persisted + lookup).distinct()
                    secure.saveLookups(persisted)
                    VaccineAlerts.sync(context, true)
                    if (!canNotify) askNotifications()
                }
                found = (found + children.map { lookup to it }).distinctBy { it.second.id }
                showForm = false
                phone = ""
                dob = ""
            } catch (e: ApiException) {
                formError = if (e.status == 404) s.lookupNotFound else s.error(e)
            } finally {
                busy = false
            }
        }
    }

    fun forget(lookup: SavedLookup) {
        lookups = lookups - lookup
        persisted = persisted - lookup
        secure.saveLookups(persisted)
        VaccineAlerts.sync(context, persisted.isNotEmpty())
        found = found.filter { it.first != lookup }
        if (found.isEmpty()) showForm = true
    }

    AppScaffold(
        s.myChildTitle,
        s,
        onBack = onBack,
        actions = { IconButton(onClick = onNearby) { Icon(Icons.Filled.LocalHospital, contentDescription = s.nearbyTitle) } },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reload++
            },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (loading) item { LoadingBlock() }
                loadError?.let { e ->
                    item {
                        MessageBanner(s.error(e), action = s.retry, onAction = {
                            loading = true
                            reload++
                        })
                    }
                }
                items(found, key = { it.second.id }) { (lookup, child) ->
                    CaregiverChildCard(child, s, onNearby = onNearby, onForget = if (lookup in persisted) ({ forget(lookup) }) else null)
                }
                if (persisted.isNotEmpty() && !loading) {
                    item {
                        AlertsCard(s, canNotify, onAllow = ::askNotifications) {
                            VaccineAlerts.checkNow(context)
                            notify(s.checkStarted)
                        }
                    }
                }
                item {
                    if (showForm) {
                        LookupForm(
                            s = s,
                            phone = phone,
                            onPhone = {
                                phone = it
                                phoneError = null
                            },
                            phoneError = phoneError,
                            dob = dob,
                            onDob = {
                                dob = it
                                dobError = null
                            },
                            dobError = dobError,
                            rememberLookup = rememberLookup,
                            onRemember = { rememberLookup = it },
                            error = formError,
                            busy = busy,
                            onFind = ::find,
                        )
                    } else if (!loading) {
                        OutlinedButton(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.addAnother)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupForm(
    s: Strings,
    phone: String,
    onPhone: (String) -> Unit,
    phoneError: String?,
    dob: String,
    onDob: (String) -> Unit,
    dobError: String?,
    rememberLookup: Boolean,
    onRemember: (Boolean) -> Unit,
    error: String?,
    busy: Boolean,
    onFind: () -> Unit,
) {
    val today = todayNairobi()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.lookupIntro, style = MaterialTheme.typography.bodyMedium)
            FormField(
                value = phone,
                onValueChange = onPhone,
                label = s.phone,
                icon = Icons.Filled.Phone,
                error = phoneError,
                placeholder = s.phoneHint,
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            )
            DateField(dob, s.childDob, s, onDob, error = dobError, min = today.minusYears(6), max = today)
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = rememberLookup, role = Role.Checkbox, onValueChange = onRemember),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberLookup, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(s.rememberMe, style = MaterialTheme.typography.bodyMedium)
            }
            error?.let { MessageBanner(it) }
            LoadingButton(s.find, busy, onFind, Modifier.fillMaxWidth(), icon = Icons.Filled.Search)
        }
    }
}

@Composable
private fun AlertsCard(s: Strings, canNotify: Boolean, onAllow: () -> Unit, onCheckNow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(s.alertsTitle, style = MaterialTheme.typography.titleSmall)
            }
            Text(s.alertsBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canNotify) {
                OutlinedButton(onClick = onCheckNow) { Text(s.checkNow) }
            } else {
                Text(s.alertsNeedPermission, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Button(onClick = onAllow) { Text(s.allowNotifications) }
            }
        }
    }
}

@Composable
private fun CaregiverChildCard(child: Child, s: Strings, onNearby: () -> Unit, onForget: (() -> Unit)?) {
    var expanded by rememberSaveable(child.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(child.childName, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(child.childName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        ageText(child.dob, s) + " · " + s.bornOn(formatDate(child.dob, s.lang)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (child.facility.isNotBlank()) {
                        Text(child.facility, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onForget != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = s.more) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(s.forgetChild) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onForget()
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            NextVisitCard(child, s)
            Spacer(Modifier.height(16.dp))
            ProgressLine(child.givenCount, child.totalCount, s)
            Spacer(Modifier.height(12.dp))
            val sms = statusColors(if (child.consent) "given" else "upcoming")
            Pill(
                if (child.consent) s.smsOn else s.smsOff,
                sms.container,
                sms.content,
                icon = if (child.consent) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
            )
            child.savedArea?.let { area ->
                Text(
                    s.areaOnCard(area.label.ifBlank { s.myLocation }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            OutlinedButton(onClick = onNearby, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.LocalHospital, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.clinicsNearMe)
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (expanded) s.hideSchedule else s.fullSchedule)
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(expanded) {
                DoseTimeline(child.doses, s, Modifier.padding(top = 4.dp))
            }
        }
    }
}
