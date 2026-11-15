package ke.vaccinereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        setContent { App(prefs, secure, openCaregiver) }
    }
}

sealed class Route(val key: String) {
    data object Welcome : Route("welcome")
    data object Caregiver : Route("caregiver")
    data object Nearby : Route("nearby")
    data object Login : Route("login")
    data object SignUp : Route("signup")
    data object WorkerHome : Route("worker")
    data object RegisterChild : Route("register")
    data object Settings : Route("settings")
    data class ChildDetail(val id: Int) : Route("child/$id")
    data class EditChild(val id: Int) : Route("edit/$id")

    // Screens only a signed-in health worker may see.
    val needsWorker: Boolean
        get() = this is WorkerHome || this is RegisterChild || this is ChildDetail || this is EditChild

    companion object {
        fun parse(key: String): Route = when {
            key.startsWith("child/") -> key.removePrefix("child/").toIntOrNull()?.let { ChildDetail(it) } ?: Welcome
            key.startsWith("edit/") -> key.removePrefix("edit/").toIntOrNull()?.let { EditChild(it) } ?: Welcome
            else -> listOf(Welcome, Caregiver, Nearby, Login, SignUp, WorkerHome, RegisterChild, Settings).firstOrNull { it.key == key } ?: Welcome
        }
    }
}

private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.key } },
    restore = { keys -> keys.map { Route.parse(it) }.toMutableStateList() },
)

@Composable
fun App(prefs: Prefs, secure: SecureStore, openCaregiver: Boolean = false) {
    var lang by rememberSaveable { mutableStateOf(Lang.of(prefs.language)) }
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

    val setLang: (Lang) -> Unit = {
        lang = it
        prefs.language = it.code
    }

    val current = stack.last()
    LaunchedEffect(current, session) {
        if (current.needsWorker && session == null) reset(Route.Welcome, Route.Login)
    }

    AppTheme {
        CompositionLocalProvider(LocalSnackbar provides snackbar, LocalNotify provides notify) {
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
                                onBack = ::pop,
                            )
                            Route.Nearby -> NearbyScreen(api, s, caregiverLookups.value, onBack = ::pop)
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
                                    onRegister = { push(Route.RegisterChild) },
                                    onSettings = { push(Route.Settings) },
                                )
                            }
                            Route.RegisterChild -> if (worker != null) {
                                ChildFormScreen(api, s, worker, childId = null, onBack = ::pop, onSaved = { replaceTop(Route.ChildDetail(it.id)) })
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
                            )
                            Route.Settings -> SettingsScreen(
                                api = api,
                                prefs = prefs,
                                secure = secure,
                                s = s,
                                lang = lang,
                                onLang = setLang,
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
