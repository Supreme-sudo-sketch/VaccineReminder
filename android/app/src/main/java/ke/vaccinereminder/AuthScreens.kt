package ke.vaccinereminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
private fun AuthLayout(
    title: String,
    s: Strings,
    icon: ImageVector,
    heading: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppScaffold(title, s, onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp).size(32.dp))
            }
            Text(heading, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun LoginScreen(
    api: Api,
    s: Strings,
    initialPhone: String,
    onBack: () -> Unit,
    onSignedIn: (Session) -> Unit,
    onSignUp: () -> Unit,
    onSettings: () -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf(if (initialPhone.isBlank()) "" else Phone.display(initialPhone)) }
    var password by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val normalized = Phone.normalize(phone)
        phoneError = if (normalized == null) s.errPhone else null
        passwordError = if (password.isEmpty()) s.errPasswordRequired else null
        if (normalized == null || password.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            try {
                onSignedIn(api.login(normalized, password))
            } catch (e: ApiException) {
                if (e.field == "phone") phoneError = s.error(e) else error = e
            } finally {
                busy = false
            }
        }
    }

    AuthLayout(s.signInTitle, s, Icons.Filled.MedicalServices, s.signInHeading, s.signInSubtitle, onBack) {
        FormField(
            value = phone,
            onValueChange = {
                phone = it
                phoneError = null
            },
            label = s.yourPhone,
            icon = Icons.Filled.Phone,
            error = phoneError,
            placeholder = s.phoneHint,
            keyboardType = KeyboardType.Phone,
        )
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = s.password,
            s = s,
            error = passwordError,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
        error?.let { e ->
            MessageBanner(
                s.error(e),
                action = if (e.kind != ApiException.Kind.SERVER) s.serverSettings else null,
                onAction = onSettings,
            )
        }
        LoadingButton(s.signIn, busy, ::submit, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.Login)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(
            s.noAccount,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(s.createAccount)
        }
    }
}

@Composable
fun SignUpScreen(api: Api, s: Strings, onBack: () -> Unit, onSignedIn: (Session) -> Unit, onSignIn: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var facility by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<ApiException?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun validate(): Boolean {
        val e = mutableMapOf<String, String>()
        if (name.trim().length < 2) e["name"] = s.errName
        if (Phone.normalize(phone) == null) e["phone"] = s.errPhone
        if (facility.isBlank()) e["facility"] = s.errFacility
        if (passwordStrength(password) == 0) e["password"] = s.passwordHint
        if (confirm != password) e["confirm"] = s.errPasswordMatch
        if (code.isBlank()) e["registrationCode"] = s.errCode
        errors = e
        return e.isEmpty()
    }

    fun submit() {
        if (busy || !validate()) return
        scope.launch {
            busy = true
            error = null
            try {
                onSignedIn(api.registerWorker(name.trim(), Phone.normalize(phone)!!, facility.trim(), password, code.trim()))
            } catch (e: ApiException) {
                val field = e.field
                if (field != null && field in setOf("name", "phone", "facility", "password", "registrationCode")) {
                    errors = errors + (field to s.error(e))
                } else {
                    error = e
                }
            } finally {
                busy = false
            }
        }
    }

    AuthLayout(s.signUpTitle, s, Icons.Filled.PersonAdd, s.signUpHeading, s.signUpSubtitle, onBack) {
        FormField(
            value = name,
            onValueChange = {
                name = it
                errors = errors - "name"
            },
            label = s.fullName,
            icon = Icons.Filled.Person,
            error = errors["name"],
            capitalization = KeyboardCapitalization.Words,
        )
        FormField(
            value = phone,
            onValueChange = {
                phone = it
                errors = errors - "phone"
            },
            label = s.yourPhone,
            icon = Icons.Filled.Phone,
            error = errors["phone"],
            placeholder = s.phoneHint,
            keyboardType = KeyboardType.Phone,
        )
        FacilityField(
            value = facility,
            onChange = {
                facility = it
                errors = errors - "facility"
            },
            api = api,
            s = s,
            error = errors["facility"],
        )
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                errors = errors - "password"
            },
            label = s.password,
            s = s,
            error = errors["password"],
            supporting = s.passwordHint,
        )
        if (password.isNotEmpty()) PasswordStrengthBar(password, s)
        PasswordField(
            value = confirm,
            onValueChange = {
                confirm = it
                errors = errors - "confirm"
            },
            label = s.confirmPassword,
            s = s,
            error = errors["confirm"],
        )
        FormField(
            value = code,
            onValueChange = {
                code = it
                errors = errors - "registrationCode"
            },
            label = s.registrationCode,
            icon = Icons.Filled.Key,
            error = errors["registrationCode"],
            supporting = s.registrationCodeHint,
            imeAction = ImeAction.Done,
            onImeAction = ::submit,
        )
        error?.let { MessageBanner(s.error(it)) }
        LoadingButton(s.signUpTitle, busy, ::submit, Modifier.fillMaxWidth(), icon = Icons.Filled.Badge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(s.haveAccount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSignIn) { Text(s.signIn) }
        }
    }
}

@Composable
private fun PasswordStrengthBar(password: String, s: Strings) {
    val level = passwordStrength(password)
    val color = when (level) {
        0 -> MaterialTheme.colorScheme.error
        1 -> statusColors("due").content
        else -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        if (i <= level) color else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(50),
                    ),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(s.passwordStrengthLabel(level), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
