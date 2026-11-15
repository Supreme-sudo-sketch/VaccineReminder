@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val FORM_FIELDS = setOf("childName", "dob", "caregiverName", "phone", "facility", "consent")

// Registers a new child (childId == null) or edits an existing one.
@Composable
fun ChildFormScreen(api: Api, s: Strings, worker: Worker, childId: Int?, onBack: () -> Unit, onSaved: (Child) -> Unit) {
    val editing = childId != null
    var loaded by remember { mutableStateOf(!editing) }
    var loadError by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    var childName by rememberSaveable { mutableStateOf("") }
    var dob by rememberSaveable { mutableStateOf("") }
    var caregiverName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var facility by rememberSaveable { mutableStateOf(if (editing) "" else worker.facility) }
    var language by rememberSaveable { mutableStateOf("en") }
    var consent by rememberSaveable { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    val today = todayNairobi()

    LaunchedEffect(childId, reload) {
        if (childId == null) return@LaunchedEffect
        try {
            val c = api.getChild(childId)
            // After a rotation the typed values are restored, so do not overwrite them.
            if (!prefilled) {
                childName = c.childName
                dob = c.dob
                caregiverName = c.caregiverName
                phone = Phone.display(c.phone)
                facility = c.facility
                language = c.language
                consent = c.consent
                prefilled = true
            }
            loaded = true
            loadError = null
        } catch (e: ApiException) {
            loadError = e
        }
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (childName.isBlank()) e["childName"] = s.errName
        if (dob.isBlank()) e["dob"] = s.errDob
        if (caregiverName.isBlank()) e["caregiverName"] = s.errName
        if (Phone.normalize(phone) == null) e["phone"] = s.errPhone
        if (!editing && !consent) e["consent"] = s.errConsent
        errors = e
        return e.isEmpty()
    }

    fun submit() {
        if (busy || !validate()) return
        val input = ChildInput(
            childName = childName.trim(),
            caregiverName = caregiverName.trim(),
            phone = Phone.normalize(phone)!!,
            dob = dob,
            language = language,
            facility = facility.trim(),
            consent = consent,
        )
        scope.launch {
            busy = true
            error = null
            try {
                val child = if (childId == null) api.register(input) else api.update(childId, input)
                notify(if (editing) s.changesSaved else s.registered)
                onSaved(child)
            } catch (e: ApiException) {
                val field = e.field
                if (field != null && field in FORM_FIELDS) errors = errors + (field to s.error(e)) else error = e
            } finally {
                busy = false
            }
        }
    }

    AppScaffold(if (editing) s.editTitle else s.registerTitle, s, onBack = onBack) { padding ->
        val failed = loadError
        if (!loaded) {
            Box(Modifier.padding(padding).padding(16.dp)) {
                if (failed != null) MessageBanner(s.error(failed), action = s.retry, onAction = { reload++ }) else LoadingBlock()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionHeader(s.sectionChild)
                FormField(
                    value = childName,
                    onValueChange = {
                        childName = it
                        errors = errors - "childName"
                    },
                    label = s.childName,
                    icon = Icons.Filled.ChildCare,
                    error = errors["childName"],
                    capitalization = KeyboardCapitalization.Words,
                )
                DateField(
                    value = dob,
                    label = s.childDob,
                    s = s,
                    onChange = {
                        dob = it
                        errors = errors - "dob"
                    },
                    error = errors["dob"],
                    supporting = when {
                        editing -> listOf(ageText(dob, s), s.dobChangeWarning).filter { it.isNotBlank() }.joinToString(". ")
                        dob.isNotBlank() -> ageText(dob, s)
                        else -> null
                    },
                    min = today.minusYears(5),
                    max = today,
                )
                SectionHeader(s.sectionCaregiver)
                FormField(
                    value = caregiverName,
                    onValueChange = {
                        caregiverName = it
                        errors = errors - "caregiverName"
                    },
                    label = s.caregiverName,
                    icon = Icons.Filled.Person,
                    error = errors["caregiverName"],
                    capitalization = KeyboardCapitalization.Words,
                )
                FormField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errors = errors - "phone"
                    },
                    label = s.phone,
                    icon = Icons.Filled.Phone,
                    error = errors["phone"],
                    placeholder = s.phoneHint,
                    keyboardType = KeyboardType.Phone,
                )
                SectionHeader(s.sectionClinic)
                FormField(
                    value = facility,
                    onValueChange = {
                        facility = it
                        errors = errors - "facility"
                    },
                    label = s.facility,
                    icon = Icons.Filled.LocalHospital,
                    error = errors["facility"],
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                )
                Text(s.smsLanguage, style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(listOf("en" to s.english, "sw" to s.kiswahili), language, { language = it })
                Spacer(Modifier.height(6.dp))
                ConsentCard(
                    editing = editing,
                    consent = consent,
                    onChange = {
                        consent = it
                        errors = errors - "consent"
                    },
                    error = errors["consent"],
                    s = s,
                )
                error?.let { MessageBanner(s.error(it), Modifier.padding(top = 6.dp)) }
                LoadingButton(
                    if (editing) s.saveChanges else s.registerChild,
                    busy,
                    ::submit,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    icon = if (editing) Icons.Filled.Save else Icons.Filled.PersonAdd,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConsentCard(editing: Boolean, consent: Boolean, onChange: (Boolean) -> Unit, error: String?, s: Strings) {
    Surface(
        color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = consent, role = if (editing) Role.Switch else Role.Checkbox, onValueChange = onChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Text(s.smsReminders, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = consent, onCheckedChange = null)
                } else {
                    Checkbox(checked = consent, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.consent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            Text(
                s.consentDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun ChildDetailScreen(api: Api, s: Strings, id: Int, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    var child by remember { mutableStateOf<Child?>(null) }
    var loadError by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busyCode by remember { mutableStateOf<String?>(null) }
    var reminding by remember { mutableStateOf(false) }
    var recordDose by remember { mutableStateOf<Dose?>(null) }
    var undoDose by remember { mutableStateOf<Dose?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    val context = LocalContext.current

    LaunchedEffect(id, reload) {
        try {
            child = api.getChild(id)
            loadError = null
        } catch (e: ApiException) {
            loadError = e
        }
    }

    // Runs a change to the doses and shows the updated record, or the error.
    fun change(code: String, action: suspend () -> Child) {
        if (busyCode != null) return
        scope.launch {
            busyCode = code
            try {
                child = action()
            } catch (e: ApiException) {
                notify(s.error(e))
            }
            busyCode = null
        }
    }

    val c = child
    AppScaffold(
        title = c?.childName ?: "",
        s = s,
        onBack = onBack,
        actions = {
            if (c != null) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = s.edit) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = s.more) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(s.deleteRecord) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val failed = loadError
        if (c == null) {
            Box(Modifier.padding(padding).padding(16.dp)) {
                if (failed != null) MessageBanner(s.error(failed), action = s.retry, onAction = { reload++ }) else LoadingBlock()
            }
        } else {
            val overdue = c.doses.filter { it.status == "overdue" }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.childName, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.childName, style = MaterialTheme.typography.headlineSmall)
                        Text(ageText(c.dob, s), style = MaterialTheme.typography.bodyLarge)
                        Text(s.bornOn(formatDate(c.dob, s.lang)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        InfoRow(Icons.Filled.Person, s.sectionCaregiver, c.caregiverName)
                        InfoRow(Icons.Filled.Phone, s.phone, Phone.display(c.phone)) {
                            FilledTonalIconButton(onClick = { dial(context, c.phone) }) {
                                Icon(Icons.Filled.Call, contentDescription = s.call)
                            }
                        }
                        if (c.facility.isNotBlank()) InfoRow(Icons.Filled.LocalHospital, s.facility, c.facility)
                        InfoRow(
                            if (c.consent) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            s.remindersTitle,
                            (if (c.consent) s.smsOn else s.smsOff) + " · " + (if (c.language == "sw") s.kiswahili else s.english),
                        )
                    }
                }
                NextVisitCard(c, s)
                ProgressLine(c.givenCount, c.totalCount, s)
                if (!c.consent) {
                    MessageBanner(s.consentOffNote, kind = BannerKind.INFO)
                } else if (c.nextDueDate != null) {
                    LoadingButton(
                        s.sendReminder,
                        reminding,
                        onClick = {
                            scope.launch {
                                reminding = true
                                try {
                                    val r = api.remind(c.id)
                                    notify(if (r.smsStatus == "sent") s.reminderSent else s.reminderTest)
                                    reload++
                                } catch (e: ApiException) {
                                    notify(s.error(e))
                                }
                                reminding = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Sms,
                        outlined = true,
                    )
                }
                if (overdue.isNotEmpty()) {
                    MessageBanner(
                        s.pastDueBody,
                        kind = BannerKind.WARNING,
                        title = s.pastDueTitle(overdue.size),
                        action = s.recordPast,
                        onAction = { showPast = true },
                    )
                }
                SectionHeader(s.schedule)
                DoseTimeline(c.doses, s) { dose ->
                    when {
                        busyCode == dose.code || (busyCode == ALL && dose.status == "overdue") ->
                            CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp), strokeWidth = 2.dp)
                        dose.status == "given" -> TextButton(onClick = { undoDose = dose }, enabled = busyCode == null) { Text(s.undo) }
                        else -> TextButton(onClick = { recordDose = dose }, enabled = busyCode == null) { Text(s.record) }
                    }
                }
                if (c.smsHistory.isNotEmpty()) {
                    SectionHeader(s.smsHistory)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column {
                            c.smsHistory.forEachIndexed { i, entry ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SmsHistoryRow(entry, s)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            recordDose?.let { dose ->
                RecordDoseDialog(dose, c.dob, s, onDismiss = { recordDose = null }) { date ->
                    recordDose = null
                    change(dose.code) { api.markGiven(c.id, dose.code, date) }
                }
            }
            undoDose?.let { dose ->
                ConfirmDialog(
                    title = s.undoTitle,
                    body = s.undoBody(dose.name),
                    confirmLabel = s.undo,
                    s = s,
                    icon = Icons.AutoMirrored.Filled.Undo,
                    onDismiss = { undoDose = null },
                    onConfirm = {
                        undoDose = null
                        change(dose.code) { api.undo(c.id, dose.code) }
                    },
                )
            }
            if (showPast) {
                RecordPastDialog(overdue, c.dob, s, onDismiss = { showPast = false }) { given ->
                    showPast = false
                    change(ALL) { api.recordDoses(c.id, given) }
                }
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = s.deleteTitle,
                    body = s.deleteBody(c.childName),
                    confirmLabel = s.delete,
                    s = s,
                    destructive = true,
                    icon = Icons.Filled.Delete,
                    onDismiss = { confirmDelete = false },
                    onConfirm = {
                        confirmDelete = false
                        scope.launch {
                            try {
                                api.deleteChild(c.id)
                                notify(s.deleted)
                                onDeleted()
                            } catch (e: ApiException) {
                                notify(s.error(e))
                            }
                        }
                    },
                )
            }
        }
    }
}

private const val ALL = "*"

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SmsHistoryRow(entry: SmsEntry, s: Strings) {
    val colors = statusColors(
        when (entry.status) {
            "sent" -> "given"
            "failed" -> "overdue"
            else -> "upcoming"
        },
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.smsKind(entry.kind), style = MaterialTheme.typography.titleSmall)
                Text(formatTimestamp(entry.sentAt, s.lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(s.smsStatus(entry.status), colors.container, colors.content)
        }
        Text(entry.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RecordDoseDialog(dose: Dose, dob: String, s: Strings, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val today = todayNairobi()
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.recordTitle(dose.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.dueOn(formatDate(dose.dueDate, s.lang)), style = MaterialTheme.typography.bodyMedium)
                DateField(date, s.dateGiven, s, { date = it }, min = parseIsoDate(dob), max = today)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date) }) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

// Records several overdue doses at once, for a child whose immunisation card shows earlier vaccines.
@Composable
private fun RecordPastDialog(doses: List<Dose>, dob: String, s: Strings, onDismiss: () -> Unit, onConfirm: (Map<String, String>) -> Unit) {
    val today = todayNairobi()
    var selected by remember { mutableStateOf(doses.map { it.code }.toSet()) }
    var mode by remember { mutableStateOf("due") }
    var oneDate by remember { mutableStateOf(today.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.recordPast) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                doses.forEach { d ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = d.code in selected, role = Role.Checkbox) {
                                selected = if (it) selected + d.code else selected - d.code
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = d.code in selected, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text(s.dueOn(formatDate(d.dueDate, s.lang)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(s.whenGiven, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SegmentedChoice(listOf("due" to s.onDueDates, "one" to s.onOneDate), mode, { mode = it })
                if (mode == "one") {
                    Spacer(Modifier.height(8.dp))
                    DateField(oneDate, s.dateGiven, s, { oneDate = it }, min = parseIsoDate(dob), max = today)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val todayIso = today.toString()
                    onConfirm(
                        doses.filter { it.code in selected }.associate {
                            it.code to if (mode == "due") minOf(it.dueDate, todayIso) else oneDate
                        },
                    )
                },
            ) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
