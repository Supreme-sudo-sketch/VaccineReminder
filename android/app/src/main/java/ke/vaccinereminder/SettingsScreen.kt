package ke.vaccinereminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    api: Api,
    prefs: Prefs,
    secure: SecureStore,
    s: Strings,
    lang: Lang,
    onLang: (Lang) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    session: Session?,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(prefs.baseUrl) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var savedCount by remember { mutableIntStateOf(secure.loadLookups().size) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notify = LocalNotify.current
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    fun saveAndTest() {
        val address = url.trim().trimEnd('/')
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            urlError = s.errUrl
            return
        }
        prefs.baseUrl = address
        url = address
        scope.launch {
            testing = true
            testResult = null
            testResult = try {
                val info = api.health(address)
                true to s.connected(formatDate(info.today, s.lang), info.smsMode)
            } catch (e: ApiException) {
                false to s.error(e)
            }
            testing = false
        }
    }

    AppScaffold(s.settings, s, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(s.settingsServer) {
                FormField(
                    value = url,
                    onValueChange = {
                        url = it
                        urlError = null
                        testResult = null
                    },
                    label = s.serverAddress,
                    icon = Icons.Filled.Dns,
                    error = urlError,
                    supporting = s.serverHint,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    onImeAction = ::saveAndTest,
                )
                LoadingButton(s.testConnection, testing, ::saveAndTest, Modifier.fillMaxWidth(), icon = Icons.Filled.NetworkCheck, outlined = true)
                testResult?.let { (ok, message) -> MessageBanner(message, kind = if (ok) BannerKind.SUCCESS else BannerKind.ERROR) }
            }

            SettingsCard(s.language) {
                LanguageChoice(lang, s, onLang)
            }

            SettingsCard(s.appearance) {
                ThemeChoice(theme, s, onTheme)
            }

            if (session != null) {
                SettingsCard(s.account) {
                    AccountRow(Icons.Filled.Person, session.worker.name)
                    AccountRow(Icons.Filled.Phone, Phone.display(session.worker.phone))
                    if (session.worker.facility.isNotBlank()) AccountRow(Icons.Filled.LocalHospital, session.worker.facility)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { changingPassword = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.changePassword)
                    }
                    LoadingButton(
                        s.logout,
                        signingOut,
                        { confirmLogout = true },
                        Modifier.fillMaxWidth(),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        outlined = true,
                    )
                }
            }

            SettingsCard(s.thisPhone) {
                Text(
                    if (savedCount == 0) s.noSaved else s.savedChildren(savedCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (savedCount > 0) {
                    TextButton(
                        onClick = {
                            secure.saveLookups(emptyList())
                            VaccineAlerts.sync(context, false)
                            savedCount = 0
                            notify(s.forgotten)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(s.forgetAll) }
                }
            }

            SettingsCard(s.about) {
                Text("${s.appName} · ${s.version(version)}", style = MaterialTheme.typography.bodyMedium)
                Text(s.aboutBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (changingPassword) {
        ChangePasswordDialog(api, s, onDismiss = { changingPassword = false }) {
            changingPassword = false
            notify(s.passwordChanged)
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = s.logoutConfirm,
            body = session?.worker?.name ?: "",
            confirmLabel = s.logout,
            s = s,
            icon = Icons.AutoMirrored.Filled.Logout,
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                scope.launch {
                    signingOut = true
                    // End the session on the server too; sign out locally even if that fails.
                    try {
                        api.logout()
                    } catch (e: ApiException) {
                        // offline or already expired
                    }
                    onSignedOut()
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun AccountRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChangePasswordDialog(api: Api, s: Strings, onDismiss: () -> Unit, onDone: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val e = mutableMapOf<String, String>()
        if (current.isEmpty()) e["currentPassword"] = s.errPasswordRequired
        if (passwordStrength(new) == 0) e["newPassword"] = s.passwordHint
        if (confirm != new) e["confirm"] = s.errPasswordMatch
        errors = e
        if (e.isNotEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            try {
                api.changePassword(current, new)
                onDone()
            } catch (ex: ApiException) {
                val field = ex.field
                if (field != null) errors = errors + (field to s.error(ex)) else error = s.error(ex)
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(s.changePassword) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PasswordField(current, {
                    current = it
                    errors = errors - "currentPassword"
                }, s.currentPassword, s, error = errors["currentPassword"])
                PasswordField(new, {
                    new = it
                    errors = errors - "newPassword"
                }, s.newPassword, s, error = errors["newPassword"], supporting = s.passwordHint)
                PasswordField(confirm, {
                    confirm = it
                    errors = errors - "confirm"
                }, s.confirmPassword, s, error = errors["confirm"], imeAction = ImeAction.Done, onImeAction = ::submit)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = !busy) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(s.cancel) } },
    )
}
