package ke.vaccinereminder

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        val secure = SecureStore(applicationContext)
        VaccineAlerts.sync(applicationContext, secure.loadLookups().isNotEmpty())
        val openCaregiver = intent?.getStringExtra(VaccineAlerts.EXTRA_OPEN) == VaccineAlerts.OPEN_CAREGIVER
        setContent { App(prefs, secure, openCaregiver, onDarkTheme = ::styleSystemBars) }
    }

    override fun onRestart() {
        super.onRestart()
        // Back from the background: collect any notification a health worker sent meanwhile.
        // (A fresh start does this through VaccineAlerts.sync in onCreate.)
        if (SecureStore(applicationContext).loadLookups().isNotEmpty()) VaccineAlerts.checkMessagesNow(applicationContext)
    }

    // Dark icons on the light theme and light icons on the dark one, even when the theme chosen in
    // Settings differs from the phone's own dark mode setting.
    private fun styleSystemBars(dark: Boolean) {
        val scrimLight = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val scrimDark = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(scrimDark) else SystemBarStyle.light(scrimLight, scrimDark),
        )
    }
}

sealed class Route(val key: String) {
    data object Welcome : Route("welcome")
    data object Caregiver : Route("caregiver")
    data object Nearby : Route("nearby")
    data object Login : Route("login")
    data object SignUp : Route("signup")
    data object WorkerHome : Route("worker")
    // fromId > 0 registers a twin (twin = true) or a brother or sister of that child, with the
    // parent's details filled in.
    data class RegisterChild(val fromId: Int = 0, val twin: Boolean = false) :
        Route(if (fromId == 0) "register" else if (twin) "register/$fromId/twin" else "register/$fromId/sibling")
    data object Settings : Route("settings")
    data class ChildDetail(val id: Int) : Route("child/$id")
    data class EditChild(val id: Int) : Route("edit/$id")

    // The caregiver's vaccination evidence screen, opened from one child's card (0: no child chosen).
    // With a dose code it goes straight to the form for that vaccine.
    data class Verify(val childId: Int, val doseCode: String = "") : Route("verify/$childId/$doseCode")
    data object VerificationRequests : Route("verifications")
    data class VerificationDetail(val id: Int) : Route("verification/$id")

    // Screens only a signed-in health worker may see.
    val needsWorker: Boolean
        get() = this is WorkerHome || this is RegisterChild || this is ChildDetail || this is EditChild ||
            this is VerificationRequests || this is VerificationDetail

    companion object {
        fun parse(key: String): Route = when {
            key.startsWith("child/") -> key.removePrefix("child/").toIntOrNull()?.let { ChildDetail(it) } ?: Welcome
            key.startsWith("edit/") -> key.removePrefix("edit/").toIntOrNull()?.let { EditChild(it) } ?: Welcome
            key.startsWith("register/") -> key.removePrefix("register/").split("/").let { parts ->
                parts[0].toIntOrNull()?.let { RegisterChild(it, parts.getOrNull(1) == "twin") } ?: Welcome
            }
            key.startsWith("verify/") -> key.removePrefix("verify/").split("/").let { parts ->
                parts[0].toIntOrNull()?.let { Verify(it, parts.getOrElse(1) { "" }) } ?: Welcome
            }
            key.startsWith("verification/") -> key.removePrefix("verification/").toIntOrNull()?.let { VerificationDetail(it) } ?: Welcome
            else -> listOf(Welcome, Caregiver, Nearby, Login, SignUp, WorkerHome, RegisterChild(), Settings, VerificationRequests)
                .firstOrNull { it.key == key } ?: Welcome
        }
    }
}

private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.key } },
    restore = { keys -> keys.map { Route.parse(it) }.toMutableStateList() },
)

@Composable
fun App(prefs: Prefs, secure: SecureStore, openCaregiver: Boolean = false, onDarkTheme: (Boolean) -> Unit = {}) {
    var lang by rememberSaveable { mutableStateOf(Lang.of(prefs.language)) }
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.of(prefs.theme)) }
    val dark = themeMode.isDark()
    LaunchedEffect(dark) { onDarkTheme(dark) }
    val s = remember(lang) { Strings(lang) }
    val currentStrings by rememberUpdatedState(s)
    var session by remember { mutableStateOf(secure.loadSession()) }
    val caregiverLookups = remember { mutableStateOf(secure.loadLookups()) }
    val stack = rememberSaveable(saver = RouteStackSaver) {
        when {
            openCaregiver -> mutableStateListOf(Route.Welcome, Route.Caregiver)
            session != null -> mutableStateListOf<Route>(Route.WorkerHome)
            else -> mutableStateListOf<Route>(Route.Welcome)
        }
    }
    // Keeps each screen's saved state (scroll position, typed text, selected tab) while it is in the back stack.
    val screenState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = remember {
        { message ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }

    fun push(route: Route) {
        if (stack.last() != route) stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) screenState.removeState(stack.removeAt(stack.lastIndex).key)
    }

    fun replaceTop(route: Route) {
        screenState.removeState(stack.removeAt(stack.lastIndex).key)
        stack.add(route)
    }

    fun reset(vararg routes: Route) {
        stack.forEach { screenState.removeState(it.key) }
        stack.clear()
        stack.addAll(routes)
    }

    fun signIn(newSession: Session) {
        secure.saveSession(newSession)
        prefs.lastWorkerPhone = newSession.worker.phone
        session = newSession
        reset(Route.WorkerHome)
        notify(s.welcomeUser(newSession.worker.name.substringBefore(' ')))
    }

    fun signOut() {
        secure.saveSession(null)
        session = null
        reset(Route.Welcome)
        notify(s.loggedOut)
    }

    val api = remember {
        Api(
            baseUrl = { prefs.baseUrl },
            token = { session?.token },
            onUnauthorized = {
                if (session != null) {
                    secure.saveSession(null)
                    session = null
                    reset(Route.Welcome, Route.Login)
                    notify(currentStrings.sessionExpired)
                }
            },
        )
    }

    // Children's records, from the server or, when it cannot be reached, from the copy saved on this phone.
    val context = LocalContext.current
    val childRepo = remember { ChildRepository(api, VaccineDatabase.get(context).childDao()) }
    LaunchedEffect(session) {
        // Signed out or the session expired: the next health worker on this phone must not see them.
        if (session == null) childRepo.forgetWorker()
    }

    val setLang: (Lang) -> Unit = {
        lang = it
        prefs.language = it.code
    }

    val setTheme: (ThemeMode) -> Unit = {
        themeMode = it
        prefs.theme = it.code
    }

    val current = stack.last()
    LaunchedEffect(current, session) {
        if (current.needsWorker && session == null) reset(Route.Welcome, Route.Login)
    }

    AppTheme(dark = dark) {
        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalNotify provides notify, LocalChildren provides childRepo) {
            BackHandler(enabled = stack.size > 1) { pop() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }) togetherWith fadeOut(tween(150))
                    },
                    label = "screen",
                ) { route ->
                    screenState.SaveableStateProvider(route.key) {
                        val worker = session?.worker
                        when (route) {
                            Route.Welcome -> WelcomeScreen(
                                s = s,
                                lang = lang,
                                onLang = setLang,
                                worker = worker,
                                onCaregiver = { push(Route.Caregiver) },
                                onWorker = { push(if (session != null) Route.WorkerHome else Route.Login) },
                                onSettings = { push(Route.Settings) },
                            )
                            Route.Caregiver -> CaregiverScreen(
                                api = api,
                                secure = secure,
                                s = s,
                                lookupsState = caregiverLookups,
                                onNearby = { push(Route.Nearby) },
                                onVerify = { childId, doseCode -> push(Route.Verify(childId, doseCode)) },
                                onBack = ::pop,
                            )
                            Route.Nearby -> NearbyScreen(api, s, caregiverLookups.value, onBack = ::pop)
                            is Route.Verify -> CaregiverVerificationScreen(api, s, caregiverLookups.value, route.childId, route.doseCode, onBack = ::pop)
                            Route.Login -> LoginScreen(
                                api = api,
                                s = s,
                                initialPhone = prefs.lastWorkerPhone,
                                onBack = ::pop,
                                onSignedIn = ::signIn,
                                onSignUp = { replaceTop(Route.SignUp) },
                                onSettings = { push(Route.Settings) },
                            )
                            Route.SignUp -> SignUpScreen(api, s, onBack = ::pop, onSignedIn = ::signIn, onSignIn = { replaceTop(Route.Login) })
                            Route.WorkerHome -> if (worker != null) {
                                WorkerHome(
                                    api = api,
                                    s = s,
                                    worker = worker,
                                    onOpenChild = { push(Route.ChildDetail(it)) },
                                    onRegister = { push(Route.RegisterChild()) },
                                    onSettings = { push(Route.Settings) },
                                    onVerifications = { push(Route.VerificationRequests) },
                                )
                            }
                            Route.VerificationRequests -> if (worker != null) {
                                VerificationRequestsScreen(api, s, onBack = ::pop, onOpen = { push(Route.VerificationDetail(it)) })
                            }
                            is Route.VerificationDetail -> if (worker != null) {
                                VerificationDetailScreen(api, s, route.id, onBack = ::pop, onOpenChild = { push(Route.ChildDetail(it)) })
                            }
                            is Route.RegisterChild -> if (worker != null) {
                                ChildFormScreen(
                                    api,
                                    s,
                                    worker,
                                    childId = null,
                                    onBack = ::pop,
                                    onSaved = { replaceTop(Route.ChildDetail(it.id)) },
                                    fromChildId = route.fromId.takeIf { it > 0 },
                                    twin = route.twin,
                                )
                            }
                            is Route.EditChild -> if (worker != null) {
                                ChildFormScreen(api, s, worker, childId = route.id, onBack = ::pop, onSaved = { pop() })
                            }
                            is Route.ChildDetail -> ChildDetailScreen(
                                api = api,
                                s = s,
                                id = route.id,
                                onBack = ::pop,
                                onEdit = { push(Route.EditChild(route.id)) },
                                onDeleted = ::pop,
                                onOpenVerification = { push(Route.VerificationDetail(it)) },
                                onOpenChild = { push(Route.ChildDetail(it)) },
                                onAddSibling = { twin -> push(Route.RegisterChild(route.id, twin)) },
                            )
                            Route.Settings -> SettingsScreen(
                                api = api,
                                prefs = prefs,
                                secure = secure,
                                s = s,
                                lang = lang,
                                onLang = setLang,
                                theme = themeMode,
                                onTheme = setTheme,
                                session = session,
                                onBack = ::pop,
                                onSignedOut = ::signOut,
                            )
                        }
                    }
                }
                SnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = if (current == Route.WorkerHome) 80.dp else 8.dp),
                )
            }
        }
    }
}
