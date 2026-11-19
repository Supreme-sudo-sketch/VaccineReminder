@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkerHome(
    api: Api,
    s: Strings,
    worker: Worker,
    onOpenChild: (Int) -> Unit,
    onRegister: () -> Unit,
    onSettings: () -> Unit,
    onVerifications: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var childFilter by rememberSaveable { mutableStateOf("all") }
    var overdue by remember { mutableIntStateOf(0) }

    AppScaffold(
        title = when (tab) {
            0 -> s.appName
            1 -> s.children
            else -> s.followUp
        },
        s = s,
        actions = {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = s.settings) }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(s.home) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    label = { Text(s.children) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        BadgedBox(badge = { if (overdue > 0) Badge { Text(if (overdue > 99) "99+" else "$overdue") } }) {
                            Icon(Icons.Filled.NotificationImportant, contentDescription = null)
                        }
                    },
                    label = { Text(s.followUp) },
                )
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = onRegister,
                    icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    text = { Text(s.registerChild) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            when (tab) {
                0 -> DashboardTab(
                    api = api,
                    s = s,
                    worker = worker,
                    onOpenChild = onOpenChild,
                    onShowChildren = {
                        childFilter = it
                        tab = 1
                    },
                    onShowFollowUp = { tab = 2 },
                    onOverdue = { overdue = it },
                    onVerifications = onVerifications,
                )
                1 -> ChildrenTab(api, s, childFilter, onFilter = { childFilter = it }, onOpenChild = onOpenChild, onRegister = onRegister)
                else -> FollowUpTab(api, s, onOpenChild = onOpenChild, onCount = { overdue = it })
            }
        }
    }
}

@Composable
private fun DashboardTab(
    api: Api,
    s: Strings,
    worker: Worker,
    onOpenChild: (Int) -> Unit,
    onShowChildren: (String) -> Unit,
    onShowFollowUp: () -> Unit,
    onOverdue: (Int) -> Unit,
    onVerifications: () -> Unit,
) {
    var stats by remember { mutableStateOf<Stats?>(null) }
    var due by remember { mutableStateOf<List<DueItem>>(emptyList()) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current

    LaunchedEffect(reload) {
        try {
            val (st, d) = coroutineScope {
                val a = async { api.stats() }
                val b = async { api.due(7) }
                a.await() to b.await()
            }
            stats = st
            due = d
            error = null
            onOverdue(st.overdueChildren)
        } catch (e: ApiException) {
            error = e
        }
        loading = false
        refreshing = false
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            reload++
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(s.hello(worker.name.substringBefore(' ')), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOf(worker.facility, formatDate(todayNairobi().toString(), s.lang)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error?.let { e -> item { MessageBanner(s.error(e), action = s.retry, onAction = { reload++ }) } }
            if (loading) item { LoadingBlock() }
            stats?.let { st ->
                item {
                    val scheme = MaterialTheme.colorScheme
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(
                                s.statChildren,
                                "${st.children}",
                                Icons.Filled.Groups,
                                StatusColors(scheme.primaryContainer, scheme.onPrimaryContainer),
                                Modifier.weight(1f),
                                onClick = { onShowChildren("all") },
                            )
                            StatTile(s.statOverdue, "${st.overdueChildren}", Icons.Filled.WarningAmber, statusColors("overdue"), Modifier.weight(1f), onClick = onShowFollowUp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(s.statDueWeek, "${st.dueThisWeek}", Icons.Filled.Event, statusColors("due"), Modifier.weight(1f), onClick = { onShowChildren("due") })
                            StatTile(s.statSms, "${st.smsLast7Days}", Icons.Filled.Sms, statusColors("given"), Modifier.weight(1f))
                        }
                    }
                }
                item { VerificationRequestsCard(st.pendingVerifications, s, onClick = onVerifications) }
                item {
                    var confirmRun by remember { mutableStateOf(false) }
                    RemindersCard(st.smsMode, st.autoReminders, running, s) { confirmRun = true }
                    // Each SMS costs airtime, so sending always asks first.
                    if (confirmRun) {
                        ConfirmDialog(
                            title = s.confirmRunTitle,
                            body = s.confirmRunBody,
                            confirmLabel = s.send,
                            s = s,
                            icon = Icons.Filled.Sms,
                            onDismiss = { confirmRun = false },
                            onConfirm = {
                                confirmRun = false
                                scope.launch {
                                    running = true
                                    try {
                                        val run = api.runReminders()
                                        notify(s.remindersProcessed(run.processed, run.sent, run.waiting, run.failed))
                                        reload++
                                    } catch (e: ApiException) {
                                        notify(s.error(e))
                                    }
                                    running = false
                                }
                            },
                        )
                    }
                }
            }
            if (!loading && error == null) {
                item { SectionHeader(s.dueThisWeek) }
                if (due.isEmpty()) {
                    item {
                        Text(s.noneDueThisWeek, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(due, key = { "${it.id}-${it.dueDate}" }) { item -> DueRow(item, s) { onOpenChild(item.id) } }
            }
        }
    }
}

@Composable
private fun RemindersCard(smsMode: String, auto: Boolean, running: Boolean, s: Strings, onRun: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(s.remindersTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val c = statusColors(if (smsMode == "live") "given" else "due")
                Pill(s.smsModeName(smsMode), c.container, c.content)
            }
            Text(if (auto) s.remindersAuto else s.remindersManual, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (smsMode == "live") s.smsModeLive else s.smsModeTest,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LoadingButton(s.runReminders, running, onRun, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Send, outlined = true)
        }
    }
}

@Composable
private fun DueRow(item: DueItem, s: Strings, onClick: () -> Unit) {
    val today = daysFromToday(item.dueDate) == 0L
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.childName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.childName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.vaccines,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    relativeDate(item.dueDate, s),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (today) statusColors("due").content else MaterialTheme.colorScheme.primary,
                )
                Text(formatDate(item.dueDate, s.lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChildrenTab(
    api: Api,
    s: Strings,
    filter: String,
    onFilter: (String) -> Unit,
    onOpenChild: (Int) -> Unit,
    onRegister: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<Child>>(emptyList()) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadedOnce by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    // When the rows came from the copy saved on this phone.
    var savedAt by remember { mutableStateOf<Long?>(null) }
    val repo = LocalChildren.current
    val focus = LocalFocusManager.current

    LaunchedEffect(query, filter, reload) {
        // Wait for typing to pause before searching.
        if (query.isNotBlank() && !refreshing) delay(300)
        loading = true
        try {
            val loaded = repo.listChildren(query.trim(), filter)
            rows = loaded.data
            savedAt = loaded.savedAt
            error = null
        } catch (e: ApiException) {
            error = e
        }
        loading = false
        loadedOnce = true
        refreshing = false
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(s.search) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = s.clear) }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("all" to s.filterAll, "overdue" to s.filterOverdue, "due" to s.filterDue).forEach { (value, label) ->
                    FilterChip(
                        selected = filter == value,
                        onClick = { onFilter(value) },
                        label = { Text(label) },
                        leadingIcon = if (filter == value) {
                            { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (loading && loadedOnce) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                reload++
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { e -> item { MessageBanner(s.error(e), action = s.retry, onAction = { reload++ }) } }
                savedAt?.let { t -> item { OfflineBanner(t, s) { reload++ } } }
                if (!loadedOnce) item { LoadingBlock() }
                if (loadedOnce && !loading && error == null && rows.isEmpty()) {
                    item {
                        if (query.isBlank() && filter == "all") {
                            EmptyState(Icons.Filled.ChildCare, s.noChildren, s.noChildrenBody, action = s.registerChild, onAction = onRegister)
                        } else {
                            EmptyState(Icons.Filled.SearchOff, s.noMatches, s.noMatchesBody)
                        }
                    }
                }
                items(rows, key = { it.id }) { child -> ChildRow(child, s) { onOpenChild(child.id) } }
            }
        }
    }
}

@Composable
private fun ChildRow(child: Child, s: Strings, onClick: () -> Unit) {
    val fraction = if (child.totalCount == 0) 0f else child.givenCount.toFloat() / child.totalCount
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(child.childName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        child.childName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!child.consent) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = s.smsOff,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    s.parentLine(child.caregiverName, Phone.display(child.phone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    child.nextDueDate?.let { "${s.next}: ${relativeDate(it, s)} · ${formatDate(it, s.lang)}" } ?: s.allDone,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                )
            }
            if (child.overdueCount > 0) {
                Spacer(Modifier.width(10.dp))
                StatusChip("overdue", s.missedCount(child.overdueCount))
            }
        }
    }
}

@Composable
private fun FollowUpTab(api: Api, s: Strings, onOpenChild: (Int) -> Unit, onCount: (Int) -> Unit) {
    var rows by remember { mutableStateOf<List<MissedChild>>(emptyList()) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    val context = LocalContext.current

    LaunchedEffect(reload) {
        try {
            rows = api.missed()
            error = null
            onCount(rows.size)
        } catch (e: ApiException) {
            error = e
        }
        loading = false
        refreshing = false
    }

    // The child whose SMS is waiting for the health worker to confirm.
    var confirmFor by remember { mutableStateOf<MissedChild?>(null) }
    fun remind(item: MissedChild) {
        if (item.id in sending) return
        scope.launch {
            sending = sending + item.id
            try {
                val r = api.remind(item.id)
                notify(s.reminderOutcome(r.smsStatus))
                reload++
            } catch (e: ApiException) {
                notify(s.error(e))
            }
            sending = sending - item.id
        }
    }

    confirmFor?.let { item ->
        ConfirmDialog(
            title = s.confirmSmsTitle,
            body = s.confirmSmsBody(item.caregiverName, Phone.display(item.phone)),
            confirmLabel = s.send,
            s = s,
            icon = Icons.Filled.Sms,
            onDismiss = { confirmFor = null },
            onConfirm = {
                confirmFor = null
                remind(item)
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            reload++
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(s.followUpIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { e -> item { MessageBanner(s.error(e), action = s.retry, onAction = { reload++ }) } }
            if (loading) item { LoadingBlock() }
            if (!loading && error == null && rows.isEmpty()) {
                item { EmptyState(Icons.Filled.TaskAlt, s.noMissed, s.noMissedBody) }
            }
            items(rows, key = { it.id }) { item ->
                MissedCard(
                    item = item,
                    s = s,
                    sending = item.id in sending,
                    onCall = { dial(context, item.phone) },
                    onSms = { confirmFor = item },
                    onOpen = { onOpenChild(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MissedCard(item: MissedChild, s: Strings, sending: Boolean, onCall: () -> Unit, onSms: () -> Unit, onOpen: () -> Unit) {
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(item.childName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.childName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.parentLine(item.caregiverName, Phone.display(item.phone)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip("overdue", s.daysOverdue(item.daysOverdue))
            }
            Spacer(Modifier.height(10.dp))
            Text(item.vaccines, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.lastSmsAt?.let { s.lastSms(formatTimestamp(it, s.lang)) } ?: s.noSmsYet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onCall) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.call)
                }
                if (item.consent) {
                    FilledTonalButton(onClick = onSms, enabled = !sending) {
                        if (sending) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(s.sendSms)
                    }
                } else {
                    val c = statusColors("upcoming")
                    Pill(s.noConsent, c.container, c.content, icon = Icons.Filled.NotificationsOff)
                }
            }
        }
    }
}
