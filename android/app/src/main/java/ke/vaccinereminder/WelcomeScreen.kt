package ke.vaccinereminder

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
fun WelcomeScreen(
    s: Strings,
    lang: Lang,
    onLang: (Lang) -> Unit,
    worker: Worker?,
    onCaregiver: () -> Unit,
    onWorker: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val heroTop = if (dark) scheme.primaryContainer else scheme.primary
    val heroBottom = if (dark) scheme.surfaceContainerLow else Color(0xFF138A64)
    val heroText = if (dark) scheme.onPrimaryContainer else scheme.onPrimary

    // Light status bar icons over the dark green header; restored when leaving the screen.
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(heroTop, heroBottom)))
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 52.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = heroText.copy(alpha = 0.16f), contentColor = heroText) {
                    Icon(Icons.Filled.Vaccines, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = s.settings, tint = heroText)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(s.appName, style = MaterialTheme.typography.headlineMedium, color = heroText)
            Spacer(Modifier.height(6.dp))
            Text(s.tagline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = heroText.copy(alpha = 0.9f))
        }

        Column(
            Modifier
                .offset(y = (-28).dp)
                .background(scheme.background, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RoleCard(
                icon = Icons.Filled.ChildCare,
                title = s.caregiverTitle,
                subtitle = s.caregiverSubtitle,
                container = scheme.primaryContainer,
                content = scheme.onPrimaryContainer,
                onClick = onCaregiver,
            )
            RoleCard(
                icon = Icons.Filled.MedicalServices,
                title = s.workerTitle,
                subtitle = worker?.let { s.continueAs(it.name) } ?: s.workerSubtitle,
                container = scheme.secondaryContainer,
                content = scheme.onSecondaryContainer,
                onClick = onWorker,
            )
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(s.welcomeNote, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Text(s.language, style = MaterialTheme.typography.labelLarge, color = scheme.onBackground, modifier = Modifier.padding(top = 4.dp))
            LanguageChoice(lang, s, onLang)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(14.dp).size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
