@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

fun verificationIcon(status: String): ImageVector = when (status) {
    "pending" -> Icons.Filled.HourglassTop
    "verified" -> Icons.Filled.Verified
    "rejected" -> Icons.Filled.Cancel
    else -> Icons.Filled.RadioButtonUnchecked
}

// Grey "Not submitted", orange "Pending verification", green "Verified" or red "Rejected".
@Composable
fun VerificationPill(status: String, s: Strings, modifier: Modifier = Modifier) {
    val c = verificationColors(status)
    Pill(s.verificationStatus(status), c.container, c.content, modifier, icon = verificationIcon(status))
}

// A picture shown full screen: pinch to zoom, drag to move, double-tap to zoom in or back out.
@Composable
fun ZoomImageDialog(image: ImageBitmap, title: String, s: Strings, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Image(
                bitmap = image,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Filled.Close, contentDescription = s.back)
                }
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

// A read-only field that opens a list to choose from.
@Composable
private fun <T> ChoiceField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    icon: ImageVector,
    enabled: Boolean = true,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Caregiver: send evidence and follow its status
// ---------------------------------------------------------------------------------------------

// A compressed photo ready to upload. ref is set once the server has it, so a retry after a failure
// only sends what is still missing.
private class PickedPhoto(val bytes: ByteArray, val preview: ImageBitmap) {
    var progress by mutableFloatStateOf(0f)
    var ref by mutableStateOf<String?>(null)
}

@Composable
fun CaregiverVerificationScreen(
    api: Api,
    s: Strings,
    lookups: List<SavedLookup>,
    initialChildId: Int,
    initialDoseCode: String,
    onBack: () -> Unit,
) {
    var children by remember { mutableStateOf<List<Pair<SavedLookup, Child>>>(emptyList()) }
    var requests by remember { mutableStateOf<List<Verification>>(emptyList()) }
    var loading by remember { mutableStateOf(lookups.isNotEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var resubmitId by rememberSaveable { mutableStateOf<Int?>(null) }
    var formChildId by rememberSaveable { mutableIntStateOf(initialChildId) }
    var formDoseCode by rememberSaveable { mutableStateOf("") }
    // Opened from a vaccine in the schedule: go straight to its form once the data is in.
    var openedForDose by rememberSaveable { mutableStateOf(initialDoseCode.isBlank()) }

    LaunchedEffect(reload) {
        val kids = mutableListOf<Pair<SavedLookup, Child>>()
        val all = mutableListOf<Verification>()
        var failure: ApiException? = null
        for (l in lookups) {
            try {
                val found = api.caregiverEvidence(l)
                found.children.forEach { kids += l to it }
                all += found.verifications
            } catch (e: ApiException) {
                if (e.status != 404) failure = e
            }
        }
        children = kids.distinctBy { it.second.id }
        requests = all.distinctBy { it.id }.sortedByDescending { it.submittedAt }
        loadError = failure
        loading = false
        refreshing = false
        if (!openedForDose && children.isNotEmpty()) {
            openedForDose = true
            resubmitId = requests.firstOrNull { it.childId == initialChildId && it.doseCode == initialDoseCode && it.status == "rejected" }?.id
            formChildId = initialChildId
            formDoseCode = initialDoseCode
            formOpen = true
        }
    }

    BackHandler(enabled = formOpen) { formOpen = false }

    if (formOpen && children.isNotEmpty()) {
        EvidenceForm(
            api = api,
            s = s,
            children = children,
            initialChildId = formChildId,
            initialDoseCode = formDoseCode,
            resubmit = requests.firstOrNull { it.id == resubmitId && it.status == "rejected" },
            onClose = { formOpen = false },
            onSent = {
                formOpen = false
                reload++
            },
        )
        return
    }

    AppScaffold(s.verificationTitle, s, onBack = onBack) { padding ->
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
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { MessageBanner(s.verificationIntro, kind = BannerKind.INFO) }
                if (loading) item { LoadingBlock() }
                loadError?.let { e ->
                    item {
                        MessageBanner(s.error(e), action = s.retry, onAction = {
                            loading = true
                            reload++
                        })
                    }
                }
                if (!loading && children.isEmpty() && loadError == null) {
                    item { EmptyState(Icons.Filled.Search, s.findChildFirst, s.findChildFirstBody) }
                }
                if (!loading && children.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                resubmitId = null
                                formChildId = initialChildId
                                formDoseCode = ""
                                formOpen = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.newEvidence)
                        }
                    }
                    item { SectionHeader(s.myRequests) }
                    if (requests.isEmpty()) item { EmptyState(Icons.Filled.TaskAlt, s.noRequestsTitle, s.noRequestsBody) }
                    items(requests, key = { it.id }) { r ->
                        CaregiverRequestCard(r, s) {
                            resubmitId = r.id
                            formChildId = r.childId
                            formDoseCode = r.doseCode
                            formOpen = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaregiverRequestCard(r: Verification, s: Strings, onResubmit: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.doseName, style = MaterialTheme.typography.titleMedium)
                Text(r.childName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            VerificationPill(r.status, s)
        }
        Text(s.givenAt(formatDate(r.vaccinationDate, s.lang), r.hospital), style = MaterialTheme.typography.bodyMedium)
        Text(
            s.byDoctor(r.doctorName) + " · " + s.sentOn(formatTimestamp(r.submittedAt, s.lang)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (r.status) {
            "pending" -> Text(s.pendingNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            "verified" -> MessageBanner(s.verifiedNote, kind = BannerKind.SUCCESS)
            "rejected" -> MessageBanner(
                s.reasonLine(r.rejectionReason.orEmpty()),
                kind = BannerKind.ERROR,
                title = s.notAccepted,
                action = s.sendCorrected,
                onAction = onResubmit,
            )
        }
    }
}

@Composable
private fun EvidenceForm(
    api: Api,
    s: Strings,
    children: List<Pair<SavedLookup, Child>>,
    initialChildId: Int,
    initialDoseCode: String,
    resubmit: Verification?,
    onClose: () -> Unit,
    onSent: () -> Unit,
) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var childId by rememberSaveable {
        mutableIntStateOf(resubmit?.childId ?: children.firstOrNull { it.second.id == initialChildId }?.second?.id ?: children.first().second.id)
    }
    val (lookup, child) = children.firstOrNull { it.second.id == childId } ?: children.first()
    var doseCode by rememberSaveable { mutableStateOf(resubmit?.doseCode ?: initialDoseCode) }
    var date by rememberSaveable { mutableStateOf(resubmit?.vaccinationDate ?: "") }
    var hospital by rememberSaveable { mutableStateOf(resubmit?.hospital ?: "") }
    var doctor by rememberSaveable { mutableStateOf(resubmit?.doctorName ?: "") }
    var card by remember { mutableStateOf<PickedPhoto?>(null) }
    var note by remember { mutableStateOf<PickedPhoto?>(null) }
    var preparing by remember { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf("card") }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var enlarge by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }

    // Doses that still need evidence: not recorded as given and not already waiting for review.
    val choices = child.doses.filter { it.status != "given" && it.verification?.status != "pending" }
    fun doseLabel(d: Dose) = d.name + (if (d.milestone.isNotBlank()) " · " + s.milestone(d.milestone) else "") +
        (if (d.verification?.status == "rejected") " · " + s.verificationStatus("rejected") else "")

    fun accept(slot: String, uri: Uri, fromCamera: Boolean) {
        scope.launch {
            preparing = slot
            try {
                val bytes = withContext(Dispatchers.Default) { EvidenceImages.prepare(context, uri) }
                val preview = withContext(Dispatchers.Default) { EvidenceImages.decode(bytes) }
                    ?: throw EvidenceImages.BadImage(EvidenceImages.Problem.NOT_A_PICTURE)
                val photo = PickedPhoto(bytes, preview)
                if (slot == "card") card = photo else note = photo
                errors = errors - "documents"
                sendError = null
            } catch (e: EvidenceImages.BadImage) {
                notify(s.badImage(e.problem))
            } catch (e: IOException) {
                notify(s.badImage(EvidenceImages.Problem.NOT_A_PICTURE))
            } catch (e: SecurityException) {
                notify(s.badImage(EvidenceImages.Problem.NOT_A_PICTURE))
            } finally {
                preparing = null
                // The full-size camera photo is not kept once the compressed copy exists.
                if (fromCamera) EvidenceImages.clearCaptures(context)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri?.let(Uri::parse)
        captureUri = null
        if (ok && uri != null) accept(target, uri, fromCamera = true) else EvidenceImages.clearCaptures(context)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) accept(target, uri, fromCamera = false)
    }

    fun openCamera(slot: String) {
        target = slot
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.evidence", EvidenceImages.captureFile(context))
        captureUri = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            captureUri = null
            notify(s.noCameraApp)
        }
    }

    fun openGallery(slot: String) {
        target = slot
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (choices.none { it.code == doseCode }) e["doseCode"] = s.errDose
        if (date.isBlank()) e["vaccinationDate"] = s.errVaccinationDate
        if (hospital.trim().length < 2) e["hospital"] = s.errHospital
        if (doctor.trim().length < 2) e["doctorName"] = s.errDoctor
        if (card == null && note == null) e["documents"] = s.errNoDocument
        errors = e
        return e.isEmpty()
    }

    // Uploads the photos the server does not have yet, then sends the request. Safe to call again
    // after a failure: finished uploads are not repeated.
    fun send() {
        if (sending) return
        scope.launch {
            sending = true
            sendError = null
            try {
                for ((kind, photo) in listOf("card" to card, "note" to note)) {
                    if (photo == null || photo.ref != null) continue
                    stage = kind
                    photo.progress = 0f
                    photo.ref = api.uploadEvidence(lookup, child.id, kind, photo.bytes) { photo.progress = it }
                }
                stage = "submit"
                val input = EvidenceInput(child.id, doseCode, date, hospital.trim(), doctor.trim(), card?.ref, note?.ref)
                if (resubmit != null) {
                    api.resubmitEvidence(resubmit.id, lookup, input)
                } else {
                    api.submitEvidence(lookup, input)
                }
                notify(s.evidenceSent)
                onSent()
            } catch (e: ApiException) {
                // The server forgets photos not sent within a day; upload them again on the next try.
                if (e.code == "bad_document") {
                    card?.ref = null
                    note?.ref = null
                }
                val field = e.field
                if (e.kind == ApiException.Kind.SERVER && field in listOf("doseCode", "vaccinationDate", "hospital", "doctorName")) {
                    errors = errors + (field!! to s.error(e))
                }
                sendError = s.error(e)
            } finally {
                sending = false
                stage = null
            }
        }
    }

    AppScaffold(if (resubmit != null) s.resubmitTitle else s.newEvidenceTitle, s, onBack = onClose) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            resubmit?.rejectionReason?.let { MessageBanner(s.reasonLine(it), kind = BannerKind.WARNING, title = s.notAccepted) }
            ChoiceField(
                label = s.childLabel,
                value = child.childName,
                options = children.map { it.second.id to it.second.childName },
                onSelect = { id ->
                    if (id != childId) {
                        childId = id
                        doseCode = ""
                        // Uploaded photos belong to the child they were sent for.
                        card?.ref = null
                        note?.ref = null
                    }
                },
                icon = Icons.Filled.Person,
                enabled = resubmit == null && !sending,
            )
            if (choices.isEmpty()) {
                MessageBanner(s.noDosesLeft, kind = BannerKind.INFO)
            } else {
                ChoiceField(
                    label = s.vaccineDose,
                    value = choices.firstOrNull { it.code == doseCode }?.let(::doseLabel) ?: "",
                    options = choices.map { it.code to doseLabel(it) },
                    onSelect = {
                        doseCode = it
                        errors = errors - "doseCode"
                    },
                    icon = Icons.Filled.Vaccines,
                    enabled = resubmit == null && !sending,
                    error = errors["doseCode"],
                )
                // Where this dose stands now: grey "Not submitted", or red "Rejected" when correcting.
                choices.firstOrNull { it.code == doseCode }?.let { d ->
                    VerificationPill(d.verification?.status ?: "not_submitted", s)
                }
            }
            DateField(
                date,
                s.vaccinationDate,
                s,
                onChange = {
                    date = it
                    errors = errors - "vaccinationDate"
                },
                error = errors["vaccinationDate"],
                min = parseIsoDate(child.dob),
                max = todayNairobi(),
            )
            FacilityField(
                hospital,
                onChange = {
                    hospital = it
                    errors = errors - "hospital"
                },
                api = api,
                s = s,
                error = errors["hospital"],
                label = s.hospitalLabel,
            )
            FormField(
                value = doctor,
                onValueChange = {
                    doctor = it.take(80)
                    errors = errors - "doctorName"
                },
                label = s.doctorLabel,
                icon = Icons.Filled.MedicalServices,
                error = errors["doctorName"],
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            )
            SectionHeader(s.evidenceHeader)
            Text(
                errors["documents"] ?: s.evidenceHint,
                style = MaterialTheme.typography.bodySmall,
                color = if (errors["documents"] != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhotoSlot(
                title = s.cardPhoto,
                photo = card,
                preparing = preparing == "card",
                uploading = sending && stage == "card",
                enabled = !sending && preparing == null,
                s = s,
                onCamera = { openCamera("card") },
                onGallery = { openGallery("card") },
                onRemove = { card = null },
                onOpen = { enlarge = s.cardPhoto to it },
            )
            PhotoSlot(
                title = s.notePhoto,
                photo = note,
                preparing = preparing == "note",
                uploading = sending && stage == "note",
                enabled = !sending && preparing == null,
                s = s,
                onCamera = { openCamera("note") },
                onGallery = { openGallery("note") },
                onRemove = { note = null },
                onOpen = { enlarge = s.notePhoto to it },
            )
            sendError?.let {
                MessageBanner(it, title = s.uploadFailed, action = s.retry, onAction = { if (validate()) send() })
            }
            LoadingButton(
                s.submitEvidence,
                loading = sending,
                onClick = { if (validate()) confirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = choices.isNotEmpty() && preparing == null,
                icon = Icons.AutoMirrored.Filled.Send,
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirm) {
        val doseName = choices.firstOrNull { it.code == doseCode }?.name ?: ""
        ConfirmDialog(
            title = s.confirmSendTitle,
            body = s.confirmSendBody(child.childName, doseName),
            confirmLabel = s.send,
            s = s,
            onConfirm = {
                confirm = false
                send()
            },
            onDismiss = { confirm = false },
            icon = Icons.Filled.Verified,
        )
    }
    enlarge?.let { (title, image) -> ZoomImageDialog(image, title, s) { enlarge = null } }
}

@Composable
private fun PhotoSlot(
    title: String,
    photo: PickedPhoto?,
    preparing: Boolean,
    uploading: Boolean,
    enabled: Boolean,
    s: Strings,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    onOpen: (ImageBitmap) -> Unit,
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (photo?.ref != null && !uploading) {
                val c = statusColors("given")
                Pill(s.uploaded, c.container, c.content, icon = Icons.Filled.TaskAlt)
            }
        }
        when {
            preparing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(s.preparingPhoto, style = MaterialTheme.typography.bodyMedium)
            }
            photo != null -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = s.tapToEnlarge) { onOpen(photo.preview) },
                ) {
                    Image(photo.preview, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    ZoomHint(s, Modifier.align(Alignment.BottomEnd))
                }
                Text(EvidenceImages.sizeLabel(photo.bytes.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (uploading) {
                    LinearProgressIndicator(progress = { photo.progress }, modifier = Modifier.fillMaxWidth())
                    Text(s.uploading((photo.progress * 100).toInt()), style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = enabled) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.replace)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(s.takePhoto) },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onCamera()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(s.gallery) },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onGallery()
                                },
                            )
                        }
                    }
                    TextButton(
                        onClick = onRemove,
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.remove)
                    }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCamera, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.takePhoto, maxLines = 1)
                }
                OutlinedButton(onClick = onGallery, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.gallery, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ZoomHint(s: Strings, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = modifier.padding(8.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ZoomIn, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(s.tapToEnlarge, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Health worker: review requests
// ---------------------------------------------------------------------------------------------

// The card on the health worker's dashboard that leads to the requests.
@Composable
fun VerificationRequestsCard(pending: Int, s: Strings, onClick: () -> Unit) {
    val c = verificationColors(if (pending > 0) "pending" else "verified")
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = c.container, contentColor = c.content) {
                Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.verificationRequests, style = MaterialTheme.typography.titleSmall)
                Text(s.waitingReview(pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pending > 0) {
                Badge { Text(if (pending > 99) "99+" else "$pending") }
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
fun VerificationRequestsScreen(api: Api, s: Strings, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("pending") }
    var list by remember { mutableStateOf<VerificationList?>(null) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reload) {
        try {
            list = api.verifications(tab)
            error = null
        } catch (e: ApiException) {
            error = e
        }
        loading = false
        refreshing = false
    }

    AppScaffold(s.verificationRequestsShort, s, onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            val counts = list
            SegmentedChoice(
                options = listOf(
                    "pending" to s.pendingTab + (counts?.let { " (${it.pending})" } ?: ""),
                    "reviewed" to s.reviewedTab + (counts?.let { " (${it.reviewed})" } ?: ""),
                ),
                selected = tab,
                onSelect = {
                    if (it != tab) {
                        tab = it
                        list = null
                        loading = true
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { e -> item { MessageBanner(s.error(e), action = s.retry, onAction = { reload++ }) } }
                    if (loading) item { LoadingBlock() }
                    val items = list?.items.orEmpty()
                    if (!loading && error == null && items.isEmpty()) {
                        item {
                            if (tab == "pending") {
                                EmptyState(Icons.Filled.TaskAlt, s.noPendingTitle, s.noPendingBody)
                            } else {
                                EmptyState(Icons.Filled.History, s.noReviewedTitle, s.noReviewedBody)
                            }
                        }
                    }
                    items(items, key = { it.id }) { v -> RequestRow(v, s) { onOpen(v.id) } }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(v: Verification, s: Strings, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp)) {
            Avatar(v.childName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(v.childName, style = MaterialTheme.typography.titleMedium)
                Text(
                    v.doseName + (if (v.milestone.isNotBlank()) " · " + s.milestone(v.milestone) else ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    s.givenAt(formatDate(v.vaccinationDate, s.lang), v.hospital),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    VerificationPill(v.status, s)
                    Text(s.photoCount(v.documentCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (v.status == "pending") {
                        s.sentOn(formatTimestamp(v.submittedAt, s.lang)) + (if (v.submissionCount > 1) " · " + s.sentTimes(v.submissionCount) else "")
                    } else {
                        s.reviewedBy(v.reviewedBy ?: "-", formatTimestamp(v.reviewedAt, s.lang))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VerificationDetailScreen(api: Api, s: Strings, id: Int, onBack: () -> Unit, onOpenChild: (Int) -> Unit) {
    var request by remember { mutableStateOf<Verification?>(null) }
    var loadError by remember { mutableStateOf<ApiException?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmApprove by remember { mutableStateOf(false) }
    var rejecting by rememberSaveable { mutableStateOf(false) }
    var enlarge by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current

    LaunchedEffect(reload) {
        try {
            request = api.verification(id)
            loadError = null
        } catch (e: ApiException) {
            loadError = e
        }
    }

    fun approve() {
        scope.launch {
            busy = "approve"
            try {
                request = api.approveVerification(id)
                notify(s.approvedDone)
            } catch (e: ApiException) {
                notify(s.error(e))
                // Someone else may have reviewed it or recorded the dose meanwhile: show the current state.
                if (e.status == 409) reload++
            } finally {
                busy = null
            }
        }
    }

    fun reject(reason: String) {
        scope.launch {
            busy = "reject"
            try {
                request = api.rejectVerification(id, reason)
                rejecting = false
                notify(s.rejectedDone)
            } catch (e: ApiException) {
                notify(s.error(e))
                if (e.status == 409) {
                    rejecting = false
                    reload++
                }
            } finally {
                busy = null
            }
        }
    }

    AppScaffold(s.verificationTitle, s, onBack = onBack) { padding ->
        val v = request
        val error = loadError
        if (v == null) {
            Box(
                Modifier
                    .padding(padding)
                    .padding(16.dp),
            ) {
                if (error != null) MessageBanner(s.error(error), action = s.retry, onAction = { reload++ }) else LoadingBlock()
            }
            return@AppScaffold
        }
        Column(
            Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard {
                VerificationPill(v.status, s)
                Text(v.doseName, style = MaterialTheme.typography.headlineSmall)
                if (v.milestone.isNotBlank()) {
                    Text(s.milestone(v.milestone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    s.sentOn(formatTimestamp(v.submittedAt, s.lang)) + (if (v.submissionCount > 1) " · " + s.sentTimes(v.submissionCount) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (v.status) {
                "verified" -> MessageBanner(s.verifiedBy(v.reviewedBy ?: "-", formatTimestamp(v.reviewedAt, s.lang)), kind = BannerKind.SUCCESS)
                "rejected" -> MessageBanner(
                    s.reasonLine(v.rejectionReason.orEmpty()),
                    kind = BannerKind.ERROR,
                    title = s.rejectedBy(v.reviewedBy ?: "-", formatTimestamp(v.reviewedAt, s.lang)),
                )
            }
            v.warnings.forEach { MessageBanner(s.warning(it), kind = BannerKind.WARNING) }

            v.child?.let { c ->
                SectionHeader(s.sectionChild)
                InfoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.childName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.childName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                ageText(c.dob, s) + " · " + s.bornOn(formatDate(c.dob, s.lang)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LabeledValue(s.sectionCaregiver, "${c.caregiverName} · ${Phone.display(c.phone)}")
                    if (c.facility.isNotBlank()) LabeledValue(s.facility, c.facility)
                    TextButton(onClick = { onOpenChild(c.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(s.openChildRecord)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            SectionHeader(s.sectionVaccination)
            InfoCard {
                LabeledValue(s.vaccineDose, v.doseName)
                LabeledValue(s.dateGivenLabel, formatDate(v.vaccinationDate, s.lang))
                LabeledValue(s.hospitalLabel, v.hospital)
                LabeledValue(s.doctorLabel, v.doctorName)
                v.dose?.let { d ->
                    LabeledValue(
                        s.inRecordNow,
                        if (d.givenDate != null) s.recordGiven(formatDate(d.givenDate, s.lang)) else s.recordNotGiven(formatDate(d.dueDate, s.lang)),
                    )
                }
            }

            SectionHeader(s.sectionEvidence)
            if (v.status == "pending") MessageBanner(s.verifyAdvice, kind = BannerKind.INFO)
            v.documents.forEach { doc ->
                EvidenceThumb(api, s, v.id, doc) { title, image -> enlarge = title to image }
            }

            if (v.history.isNotEmpty()) {
                SectionHeader(s.sectionHistory)
                InfoCard {
                    v.history.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        HistoryRow(e, s)
                    }
                }
            }

            if (v.status == "pending" && v.warnings.none { it.code == "own_child" }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                    LoadingButton(
                        s.reject,
                        loading = busy == "reject",
                        onClick = { rejecting = true },
                        modifier = Modifier.weight(1f),
                        enabled = busy == null,
                        icon = Icons.Filled.Close,
                        outlined = true,
                    )
                    LoadingButton(
                        s.approve,
                        loading = busy == "approve",
                        onClick = { confirmApprove = true },
                        modifier = Modifier.weight(1f),
                        enabled = busy == null,
                        icon = Icons.Filled.Verified,
                    )
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }

        if (confirmApprove) {
            ConfirmDialog(
                title = s.approveTitle,
                body = s.approveBody(v.doseName, formatDate(v.vaccinationDate, s.lang), v.childName),
                confirmLabel = s.approve,
                s = s,
                onConfirm = {
                    confirmApprove = false
                    approve()
                },
                onDismiss = { confirmApprove = false },
                icon = Icons.Filled.Verified,
            )
        }
        if (rejecting) RejectDialog(s, busy = busy == "reject", onDismiss = { rejecting = false }, onReject = ::reject)
    }
    enlarge?.let { (title, image) -> ZoomImageDialog(image, title, s) { enlarge = null } }
}

// Shown under a vaccine in the schedule: where its proof stands. Caregivers see what they sent and a
// button to send or correct it; health workers (with `api`) also see the pictures themselves.
@Composable
fun DoseEvidence(
    dose: Dose,
    s: Strings,
    api: Api? = null,
    onSend: (() -> Unit)? = null,
    onOpen: ((Int) -> Unit)? = null,
) {
    val v = dose.verification
    // A dose the clinic recorded itself needs no proof, and nobody sends proof for a vaccine not yet due.
    if (v == null && (dose.status == "given" || dose.status == "upcoming")) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.proofLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            VerificationPill(v?.status ?: "not_submitted", s)
        }
        if (v == null) {
            Text(if (onOpen != null) s.noProofWorker else s.noProofYet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val sent = listOfNotNull(s.cardShort.takeIf { v.hasCard }, s.noteShort.takeIf { v.hasNote }).joinToString(" · ")
            if (sent.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(sent, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (v.hospital.isNotBlank() && v.vaccinationDate.isNotBlank()) {
                Text(
                    s.givenAt(formatDate(v.vaccinationDate, s.lang), v.hospital),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (v.status == "rejected" && v.rejectionReason != null) {
                Text(s.reasonLine(v.rejectionReason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (api != null && v.documents.isNotEmpty()) EvidenceThumbRow(api, s, v.id, v.documents)
        }
        val button = when {
            onOpen != null && v != null -> (if (v.status == "pending") s.review else s.viewProof) to { onOpen(v.id) }
            onSend != null && v == null -> s.sendProof to onSend
            onSend != null && v?.status == "rejected" -> s.sendAgain to onSend
            else -> null
        }
        button?.let { (label, click) ->
            OutlinedButton(onClick = click, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                Icon(
                    if (onOpen != null) Icons.Filled.Verified else Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(label)
            }
        }
    }
}

// Small pictures of a request's documents; tap one to see it full screen.
@Composable
private fun EvidenceThumbRow(api: Api, s: Strings, verificationId: Int, documents: List<EvidenceDocument>) {
    var enlarge by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        documents.forEach { doc ->
            val title = if (doc.kind == "card") s.cardShort else s.noteShort
            var image by remember(verificationId, doc.id) { mutableStateOf<ImageBitmap?>(null) }
            var failed by remember(verificationId, doc.id) { mutableStateOf(false) }
            var attempt by remember(verificationId, doc.id) { mutableIntStateOf(0) }
            LaunchedEffect(verificationId, doc.id, attempt) {
                failed = false
                try {
                    val bytes = api.evidenceDocument(verificationId, doc.id)
                    image = withContext(Dispatchers.Default) { EvidenceImages.decode(bytes) }
                    failed = image == null
                } catch (e: ApiException) {
                    failed = true
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val loaded = image
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClickLabel = if (failed) s.retry else s.tapToEnlarge) {
                            if (loaded != null) enlarge = title to loaded else if (failed) attempt++
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loaded != null -> Image(loaded, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        failed -> Icon(Icons.Filled.Autorenew, contentDescription = s.retry)
                        else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    enlarge?.let { (title, image) -> ZoomImageDialog(image, title, s) { enlarge = null } }
}

// One uploaded picture, fetched with the health worker's session. Tap to see it full screen.
@Composable
private fun EvidenceThumb(api: Api, s: Strings, verificationId: Int, doc: EvidenceDocument, onOpen: (String, ImageBitmap) -> Unit) {
    var image by remember(doc.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(doc.id) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(doc.id, attempt) {
        failed = null
        try {
            val bytes = api.evidenceDocument(verificationId, doc.id)
            image = withContext(Dispatchers.Default) { EvidenceImages.decode(bytes) }
            if (image == null) failed = s.imageLoadFailed
        } catch (e: ApiException) {
            failed = s.error(e)
        }
    }
    val title = if (doc.kind == "card") s.cardPhoto else s.notePhoto
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        val loaded = image
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(enabled = loaded != null, onClickLabel = s.tapToEnlarge) { if (loaded != null) onOpen(title, loaded) },
            contentAlignment = Alignment.Center,
        ) {
            val problem = failed
            when {
                loaded != null -> {
                    Image(loaded, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    ZoomHint(s, Modifier.align(Alignment.BottomEnd))
                }
                problem != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Text(problem, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { attempt++ }) { Text(s.retry) }
                }
                else -> CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun HistoryRow(e: VerificationEvent, s: Strings) {
    val (icon, status) = when (e.action) {
        "approved" -> Icons.Filled.Verified to "verified"
        "rejected" -> Icons.Filled.Cancel to "rejected"
        else -> Icons.AutoMirrored.Filled.Send to "pending"
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = verificationColors(status).content, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(s.eventLabel(e.action) + (e.by?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            Text(formatTimestamp(e.at, s.lang), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.action == "rejected" && e.detail != null) Text(s.reasonLine(e.detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RejectDialog(s: Strings, busy: Boolean, onDismiss: () -> Unit, onReject: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.Cancel, contentDescription = null) },
        title = { Text(s.rejectTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.rejectBody)
                listOf(s.reasonUnclear, s.reasonMismatch, s.reasonUnconfirmed).forEach { r ->
                    SuggestionChip(
                        onClick = {
                            reason = r
                            error = null
                        },
                        label = { Text(r) },
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it.take(300)
                        error = null
                    },
                    label = { Text(s.rejectReason) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (reason.trim().length < 5) error = s.errReason else onReject(reason.trim()) },
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(s.reject)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(s.cancel) } },
    )
}
