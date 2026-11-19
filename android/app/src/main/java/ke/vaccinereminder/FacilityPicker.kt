package ke.vaccinereminder

import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

// The health facility field: tapping it opens a search of Kenyan facilities to pick from, so names
// are spelt the same way everywhere. A facility missing from the list can still be typed in.
@Composable
fun FacilityField(value: String, onChange: (String) -> Unit, api: Api, s: Strings, error: String? = null, label: String = s.facility) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.LocalHospital, contentDescription = null) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            isError = error != null,
            supportingText = (error ?: s.chooseFromList.takeIf { value.isBlank() })?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only text field ignores taps, so this transparent layer opens the list.
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClickLabel = s.chooseFacility, role = Role.Button) { open = true },
        )
    }
    if (open) {
        FacilityPickerDialog(
            api = api,
            s = s,
            initialQuery = value,
            onDismiss = { open = false },
            onSelect = {
                onChange(it)
                open = false
            },
        )
    }
}

@Composable
private fun FacilityPickerDialog(api: Api, s: Strings, initialQuery: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var choices by remember { mutableStateOf<FacilityChoices?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val typed = query.trim().replace(Regex("\\s+"), " ")

    LaunchedEffect(typed) {
        error = null
        if (typed.length < 2) {
            choices = null
            loading = false
            return@LaunchedEffect
        }
        delay(250) // wait until typing pauses
        loading = true
        try {
            choices = api.facilityChoices(typed)
        } catch (e: ApiException) {
            choices = null
            error = s.error(e)
        }
        loading = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The list covers the whole screen, so no grey dimming. It would only show behind the status bar,
        // and Android draws white status bar icons over a dimmed area, which are unreadable on the light
        // theme. Without it the app's own window keeps setting the icon colour to suit the theme.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = s.cancel) }
                    Text(s.chooseFacility, style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(s.searchFacility) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = s.clear) } }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(focus),
                )
                Box(Modifier.height(8.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    val list = choices?.facilities.orEmpty()
                    when {
                        typed.length < 2 -> item { Hint(s.facilitySearchHint) }
                        error != null -> item { MessageBanner(error!!, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                        choices != null && list.isEmpty() -> item { Hint(s.noFacilityFound) }
                    }
                    items(list, key = { "${it.name}|${it.county}" }) { f ->
                        ListItem(
                            headlineContent = { Text(f.name, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(
                                    if (f.type == "in_use") s.facilityInUse else listOfNotNull(s.facilityType(f.type), f.county?.let(s::county)).joinToString(" · "),
                                )
                            },
                            leadingContent = {
                                Icon(
                                    when (f.type) {
                                        "hospital" -> Icons.Filled.LocalHospital
                                        "in_use" -> Icons.Filled.History
                                        else -> Icons.Filled.MedicalServices
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable { onSelect(f.name) },
                        )
                    }
                    if (choices?.more == true) item { Hint(s.keepTyping) }
                    // Lets a facility OpenStreetMap is missing still be entered, and keeps the form usable offline.
                    if (typed.length >= 2 && list.none { it.name.equals(typed, ignoreCase = true) }) {
                        item {
                            if (list.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            ListItem(
                                headlineContent = { Text(s.useTypedFacility(typed), color = MaterialTheme.colorScheme.primary) },
                                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable { onSelect(typed) },
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.facilityListCredit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
