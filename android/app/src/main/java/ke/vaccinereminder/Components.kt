@file:OptIn(ExperimentalMaterial3Api::class)

package ke.vaccinereminder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// One snackbar host for the whole app, so a message survives moving to another screen.
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("No snackbar host") }
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun AppScaffold(
    title: String,
    s: Strings,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back) }
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    error: String? = null,
    supporting: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailing,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = {
            when {
                onImeAction != null -> onImeAction()
                imeAction == ImeAction.Next -> focus.moveFocus(FocusDirection.Down)
                else -> focus.clearFocus()
            }
        }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    s: Strings,
    error: String? = null,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        icon = Icons.Filled.Lock,
        error = error,
        supporting = supporting,
        keyboardType = KeyboardType.Password,
        imeAction = imeAction,
        onImeAction = onImeAction,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailing = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) s.hidePassword else s.showPassword,
                )
            }
        },
    )
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

// A date chosen from a calendar. `value` is an ISO date (yyyy-MM-dd) or blank; dates outside min..max cannot be picked.
@Composable
fun DateField(
    value: String,
    label: String,
    s: Strings,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    min: LocalDate? = null,
    max: LocalDate? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else formatDate(value, s.lang),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            isError = error != null,
            supportingText = (error ?: supporting)?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only text field ignores taps, so this transparent layer opens the calendar.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = s.choose, role = Role.Button) { open = true },
        )
    }
    if (open) {
        val initial = parseIsoDate(value)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial?.toUtcMillis(),
            initialDisplayedMonthMillis = (initial ?: max)?.toUtcMillis(),
            yearRange = (min?.year ?: 1900)..(max?.year ?: 2100),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
                }

                override fun isSelectableYear(year: Int) =
                    (min == null || year >= min.year) && (max == null || year <= max.year)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let {
                            onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        open = false
                    },
                ) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(s.cancel) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun LoadingButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    outlined: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
    val m = modifier.heightIn(min = 52.dp)
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = m, enabled = enabled && !loading, content = content)
    } else {
        Button(onClick = onClick, modifier = m, enabled = enabled && !loading, content = content)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

fun statusIcon(status: String): ImageVector = when (status) {
    "given" -> Icons.Filled.Check
    "overdue" -> Icons.Filled.PriorityHigh
    "due" -> Icons.Filled.Today
    else -> Icons.Filled.Schedule
}

@Composable
fun StatusChip(status: String, label: String, modifier: Modifier = Modifier) {
    val c = statusColors(status)
    Pill(label, c.container, c.content, modifier, icon = statusIcon(status))
}

enum class BannerKind { ERROR, WARNING, INFO, SUCCESS }

@Composable
fun MessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.ERROR,
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (container, content, icon) = when (kind) {
        BannerKind.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Filled.ErrorOutline)
        BannerKind.WARNING -> statusColors("due").let { Triple(it.container, it.content, Icons.Filled.WarningAmber) }
        BannerKind.INFO -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Filled.Info)
        BannerKind.SUCCESS -> statusColors("given").let { Triple(it.container, it.content, Icons.Filled.CheckCircle) }
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (action != null) 4.dp else 12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (action != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = content),
                    ) { Text(action) }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(18.dp).size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    val choices = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (bg, fg) = choices[(name.hashCode() and 0x7fffffff) % choices.size]
    Surface(shape = CircleShape, color = bg, contentColor = fg, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials(name).ifEmpty { "?" },
                style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ProgressLine(given: Int, total: Int, s: Strings, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else given.toFloat() / total
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(s.vaccinesGiven(given, total), style = MaterialTheme.typography.labelMedium)
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, colors: StatusColors, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(16.dp),
        ) {
            Surface(shape = CircleShape, color = colors.container, contentColor = colors.content) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun SegmentedChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label) }
        }
    }
}

@Composable
fun LanguageChoice(lang: Lang, s: Strings, onSelect: (Lang) -> Unit, modifier: Modifier = Modifier) {
    SegmentedChoice(
        options = listOf(Lang.EN.code to s.english, Lang.SW.code to s.kiswahili),
        selected = lang.code,
        onSelect = { onSelect(Lang.of(it)) },
        modifier = modifier,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    s: Strings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
fun DoseTimeline(doses: List<Dose>, s: Strings, modifier: Modifier = Modifier, action: (@Composable (Dose) -> Unit)? = null) {
    val groups = remember(doses) { groupVisits(doses) }
    Column(modifier) {
        groups.forEachIndexed { index, group ->
            TimelineItem(group, s, isFirst = index == 0, isLast = index == groups.lastIndex, action = action)
        }
    }
}

@Composable
private fun TimelineItem(group: VisitGroup, s: Strings, isFirst: Boolean, isLast: Boolean, action: (@Composable (Dose) -> Unit)?) {
    val colors = statusColors(group.status)
    val line = MaterialTheme.colorScheme.outlineVariant
    val pending = group.pendingDate
    val dateLine = if (pending != null) {
        s.dueOn(formatDate(pending, s.lang)) + " · " + relativeDate(pending, s)
    } else {
        group.lastGivenDate?.let { s.givenOn(formatDate(it, s.lang)) } ?: ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(10.dp).background(if (isFirst) Color.Transparent else line))
            Surface(shape = CircleShape, color = colors.container, contentColor = colors.content, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(statusIcon(group.status), contentDescription = s.status(group.status), modifier = Modifier.size(16.dp))
                }
            }
            Box(Modifier.width(2.dp).weight(1f).background(if (isLast) Color.Transparent else line))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, top = 10.dp, bottom = 16.dp),
        ) {
            Text(s.milestone(group.milestone), style = MaterialTheme.typography.titleMedium)
            if (dateLine.isNotBlank()) {
                Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column {
                    group.doses.forEachIndexed { i, dose ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DoseLine(dose, s, action)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseLine(dose: Dose, s: Strings, action: (@Composable (Dose) -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dose.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                if (dose.givenDate != null) s.givenOn(formatDate(dose.givenDate, s.lang)) else s.dueOn(formatDate(dose.dueDate, s.lang)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusChip(dose.status, s.status(dose.status))
            if (action != null) action(dose)
        }
    }
}

// The most important thing for a caregiver: when to go back, and for what.
@Composable
fun NextVisitCard(child: Child, s: Strings, modifier: Modifier = Modifier) {
    val next = child.nextDueDate
    val days = daysFromToday(next)
    val status = when {
        next == null -> "given"
        days != null && days < 0 -> "overdue"
        days == 0L -> "due"
        else -> "upcoming"
    }
    val container = if (status == "upcoming") MaterialTheme.colorScheme.primaryContainer else statusColors(status).container
    val content = if (status == "upcoming") MaterialTheme.colorScheme.onPrimaryContainer else statusColors(status).content
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp)) {
            Icon(
                when (status) {
                    "given" -> Icons.Filled.Verified
                    "overdue" -> Icons.Filled.WarningAmber
                    else -> Icons.Filled.EventAvailable
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (next == null) {
                    Text(s.allDone, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(s.nextVisit, style = MaterialTheme.typography.labelLarge)
                    Text(relativeDate(next, s), style = MaterialTheme.typography.headlineSmall)
                    Text(formatDate(next, s.lang), style = MaterialTheme.typography.bodyMedium)
                    if (child.nextVaccines.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(child.nextVaccines.joinToString(", "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (status) {
                            "overdue" -> s.visitMissed
                            "due" -> s.visitToday
                            else -> s.bringCard
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

fun dial(context: Context, phone: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
    } catch (e: ActivityNotFoundException) {
        // No phone app (for example a tablet); nothing to do.
    }
}
