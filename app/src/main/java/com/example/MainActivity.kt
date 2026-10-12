package com.example

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import com.example.data.remote.SpotifyMusicService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.NetworkConnectivityObserver
import com.example.data.local.UltronLocalDatabase
import com.example.data.model.UserProfile
import com.example.data.repository.UltronRepository
import com.example.ui.UltronViewModel
import com.example.ui.screens.AchievementsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthLandingScreen
import com.example.ui.screens.CollabNotebooksAndPortalsScreen
import com.example.ui.screens.FocusModeScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ProfileAndOnboardingScreen
import com.example.ui.screens.SpotifyMiniPlayerBar
import com.example.ui.screens.StudySectionScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var ttsEngine: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase App Check for AI Logic & Backend Security
        try {
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } catch (_: Exception) {
            // Ignore in local JVM / Robolectric unit test environments if AppCheck is already initialized
        }

        var ttsInitialized = false
        val createdTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInitialized = true
                ttsEngine?.language = Locale.US
            }
        }
        ttsEngine = createdTts
        if (ttsInitialized) {
            createdTts.language = Locale.US
        }

        enableEdgeToEdge()
        SpotifyMusicService.initialize(this)
        SpotifyMusicService.handleAuthorizationCallback(intent?.data)
        setContent {
            UltronRootApp(tts = ttsEngine)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SpotifyMusicService.handleAuthorizationCallback(intent.data)
    }

    override fun onDestroy() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        super.onDestroy()
    }
}

private data class NavDest(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltronRootApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    var currentFirebaseUser by remember {
        mutableStateOf(
            try {
                Firebase.auth.currentUser
            } catch (_: Exception) {
                null
            }
        )
    }

    DisposableEffect(Unit) {
        val auth = try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentFirebaseUser = firebaseAuth.currentUser
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
        }
    }

    if (currentFirebaseUser == null) {
        MyApplicationTheme(themePreference = "DARK") {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AuthLandingScreen(
                        onAuthSuccess = {
                            currentFirebaseUser = Firebase.auth.currentUser
                        }
                    )
                }
            }
        }
        return
    }

    val user = currentFirebaseUser!!
    val viewModel: UltronViewModel = viewModel(
        key = "ultron_vm_${user.uid}",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = UltronRepository(context)
                val db = UltronLocalDatabase.getInstance(context)
                val connectivity = NetworkConnectivityObserver.observeIsOnline(context)
                return UltronViewModel(
                    repository = repo,
                    localDao = db.cachedStudyDao(),
                    connectivityFlow = connectivity,
                    currentUserId = user.uid,
                    defaultUserName = user.displayName ?: "Ultron Scholar",
                    defaultUserEmail = user.email ?: ""
                ) as T
            }
        }
    )

    val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeTurns by viewModel.activeChatTurns.collectAsStateWithLifecycle()
    val flashcardDecks by viewModel.flashcardDecks.collectAsStateWithLifecycle()
    val quizzes by viewModel.quizzes.collectAsStateWithLifecycle()
    val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val homeworkSessions by viewModel.homeworkSessions.collectAsStateWithLifecycle()
    val paymentRequests by viewModel.paymentRequests.collectAsStateWithLifecycle()
    val adminAllPayments by viewModel.adminAllPayments.collectAsStateWithLifecycle()
    val adminAllUsers by viewModel.adminAllUsers.collectAsStateWithLifecycle()
    val sharedNotebooks by viewModel.sharedNotebooks.collectAsStateWithLifecycle()
    val schoolPortals by viewModel.schoolPortals.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val cachedItems by viewModel.cachedItems.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val newlyUnlockedBadge by viewModel.newlyUnlockedBadge.collectAsStateWithLifecycle()
    val generatedBitmap by viewModel.generatedDiagramBitmap.collectAsStateWithLifecycle()
    val generatedMediaInfo by viewModel.generatedMediaInfo.collectAsStateWithLifecycle()
    val timerSecondsLeft by viewModel.timerSecondsLeft.collectAsStateWithLifecycle()
    val timerRunning by viewModel.timerRunning.collectAsStateWithLifecycle()
    val timerIsBreak by viewModel.timerIsBreak.collectAsStateWithLifecycle()
    val focusDurationMin by viewModel.focusDurationMinutes.collectAsStateWithLifecycle()
    val breakDurationMin by viewModel.breakDurationMinutes.collectAsStateWithLifecycle()

    val effectiveProfile = profileState ?: UserProfile(
        id = user.uid,
        fullName = user.displayName ?: "Ultron Scholar",
        email = user.email ?: "",
        onboardingCompleted = true
    )

    var currentRoute by remember { mutableStateOf("HOME") }
    var studyInitialTab by remember { mutableIntStateOf(0) }

    BackHandler(enabled = currentRoute != "HOME") {
        currentRoute = "HOME"
    }

    val navDestinations = remember {
        listOf(
            NavDest("HOME", "Home", Icons.Default.Home, "nav_home"),
            NavDest("STUDY", "Study", Icons.AutoMirrored.Filled.MenuBook, "nav_study"),
            NavDest("FOCUS", "Focus", Icons.Default.Timer, "nav_focus"),
            NavDest("TASKS", "Tasks", Icons.Default.TaskAlt, "nav_tasks"),
            NavDest("ACHIEVEMENTS", "Awards", Icons.Default.EmojiEvents, "nav_achievements"),
            NavDest("SUBSCRIPTION", "Plans", Icons.Default.AccountBalanceWallet, "nav_subscription"),
            NavDest("PROFILE", "Profile", Icons.Default.Person, "nav_profile")
        )
    }

    MyApplicationTheme(themePreference = effectiveProfile.themePreference) {
        if (profileState != null && !effectiveProfile.onboardingCompleted) {
            Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    ProfileAndOnboardingScreen(
                        profile = effectiveProfile,
                        isOnboarding = true,
                        onSaveProfile = { fullName, studentClass, section, schoolName, dob, age, role, lang, theme ->
                            viewModel.saveUserProfile(
                                fullName = fullName,
                                studentClass = studentClass,
                                section = section,
                                schoolName = schoolName,
                                dob = dob,
                                age = age,
                                role = role,
                                language = lang,
                                themePreference = theme,
                                onboardingCompleted = true
                            )
                        },
                        onNavigateToSubscription = { currentRoute = "SUBSCRIPTION" },
                        onNavigateToAdmin = { currentRoute = "ADMIN" },
                        onSignOutSuccess = { currentFirebaseUser = null }
                    )
                }
            }
            return@MyApplicationTheme
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 700.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.clickable { currentRoute = "HOME" }
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_ultron_official_logo_1791619996735),
                                    contentDescription = "Ultron Official Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(9.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${effectiveProfile.effectivePlan} • Lvl ${effectiveProfile.level}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        actions = {
                            // Online / Offline Status Pill
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isOnline) {
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                        contentDescription = if (isOnline) "Online" else "Offline",
                                        tint = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isOnline) "Synced" else "Offline",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isOnline) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // 1-Click Collab Notebooks & School Portals button
                            IconButton(
                                onClick = { currentRoute = "COLLAB" },
                                modifier = Modifier.testTag("top_collab_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "Collaborative Notebooks & School Portals",
                                    tint = if (currentRoute == "COLLAB") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Official Admin Dashboard button (only shown to verified Ultron admins)
                            if (effectiveProfile.isAdminUser) {
                                IconButton(
                                    onClick = { currentRoute = "ADMIN" },
                                    modifier = Modifier.testTag("top_admin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Admin Control Center",
                                        tint = if (currentRoute == "ADMIN") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Quick Dark / Light Theme Toggle
                            IconButton(
                                onClick = {
                                    val nextTheme = if (effectiveProfile.themePreference == "LIGHT") "DARK" else "LIGHT"
                                    viewModel.saveUserProfile(
                                        fullName = effectiveProfile.fullName,
                                        studentClass = effectiveProfile.studentClass,
                                        section = effectiveProfile.section,
                                        schoolName = effectiveProfile.schoolName,
                                        dob = effectiveProfile.dob,
                                        age = effectiveProfile.age,
                                        role = effectiveProfile.role,
                                        language = effectiveProfile.language,
                                        themePreference = nextTheme
                                    )
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (effectiveProfile.themePreference == "LIGHT") Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Toggle Dark or Light Mode"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar {
                            navDestinations.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { currentRoute = dest.route },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    if (isWideScreen) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            navDestinations.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { currentRoute = dest.route },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag("rail_${dest.tag}")
                                )
                            }
                            NavigationRailItem(
                                selected = currentRoute == "COLLAB",
                                onClick = { currentRoute = "COLLAB" },
                                icon = { Icon(Icons.Default.Groups, contentDescription = "Collab") },
                                label = { Text("Collab") },
                                modifier = Modifier.testTag("rail_nav_collab")
                            )
                            if (effectiveProfile.isAdminUser) {
                                NavigationRailItem(
                                    selected = currentRoute == "ADMIN",
                                    onClick = { currentRoute = "ADMIN" },
                                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                                    label = { Text("Admin") },
                                    modifier = Modifier.testTag("rail_nav_admin")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (isBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        // Status / Error Banner
                        val activeBanner = errorBanner ?: statusBanner
                        if (!activeBanner.isNullOrBlank()) {
                            val isError = errorBanner != null
                            Surface(
                                color = if (isError) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeBanner,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearBanners() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss notification",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Persistent Spotify Study Music Mini-Player Bar when audio is active
                        SpotifyMiniPlayerBar(
                            onOpenMusicSection = {
                                currentRoute = "FOCUS"
                            }
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (currentRoute) {
                                "HOME" -> HomeDashboardScreen(
                                    profile = effectiveProfile,
                                    tasks = tasks,
                                    conversations = conversations,
                                    quizzes = quizzes,
                                    isOnline = isOnline,
                                    cachedItems = cachedItems,
                                    newlyUnlockedBadge = newlyUnlockedBadge,
                                    onDismissBadgeDialog = { viewModel.dismissUnlockedBadge() },
                                    onQuickAction = { action ->
                                        when (action) {
                                            "STUDY_CHAT" -> {
                                                studyInitialTab = 0
                                                currentRoute = "STUDY"
                                            }
                                            "FLASHCARDS" -> {
                                                studyInitialTab = 1
                                                currentRoute = "STUDY"
                                            }
                                            "QUIZ" -> {
                                                studyInitialTab = 2
                                                currentRoute = "STUDY"
                                            }
                                            "HOMEWORK" -> {
                                                studyInitialTab = 3
                                                currentRoute = "STUDY"
                                            }
                                            "SCIENCE_LAB", "SCIENCE_MEDIA" -> {
                                                studyInitialTab = 4
                                                currentRoute = "STUDY"
                                            }
                                            "FOCUS" -> currentRoute = "FOCUS"
                                            "TASKS" -> currentRoute = "TASKS"
                                            "ACHIEVEMENTS" -> currentRoute = "ACHIEVEMENTS"
                                            "SUBSCRIPTION" -> currentRoute = "SUBSCRIPTION"
                                            "COLLAB" -> currentRoute = "COLLAB"
                                            "ADMIN" -> currentRoute = "ADMIN"
                                        }
                                    },
                                    onToggleTask = { task -> viewModel.toggleTaskCompletion(task) }
                                )

                                "STUDY" -> StudySectionScreen(
                                    viewModel = viewModel,
                                    profile = effectiveProfile,
                                    conversations = conversations,
                                    activeTurns = activeTurns,
                                    flashcardDecks = flashcardDecks,
                                    quizzes = quizzes,
                                    homeworkSessions = homeworkSessions,
                                    generatedBitmap = generatedBitmap,
                                    generatedMediaInfo = generatedMediaInfo,
                                    isBusy = isBusy,
                                    initialTab = studyInitialTab,
                                    tts = tts
                                )

                                "FOCUS" -> FocusModeScreen(
                                    viewModel = viewModel,
                                    profile = effectiveProfile,
                                    focusSessions = focusSessions,
                                    timerSecondsLeft = timerSecondsLeft,
                                    timerRunning = timerRunning,
                                    timerIsBreak = timerIsBreak,
                                    focusDurationMin = focusDurationMin,
                                    breakDurationMin = breakDurationMin
                                )

                                "TASKS" -> TasksScreen(
                                    viewModel = viewModel,
                                    tasks = tasks
                                )

                                "ACHIEVEMENTS" -> AchievementsScreen(
                                    profile = effectiveProfile
                                )

                                "SUBSCRIPTION" -> SubscriptionScreen(
                                    viewModel = viewModel,
                                    profile = effectiveProfile,
                                    paymentHistory = paymentRequests
                                )

                                "COLLAB" -> CollabNotebooksAndPortalsScreen(
                                    viewModel = viewModel,
                                    profile = effectiveProfile,
                                    notebooks = sharedNotebooks,
                                    portals = schoolPortals,
                                    tts = tts
                                )

                                "ADMIN" -> AdminDashboardScreen(
                                    viewModel = viewModel,
                                    profile = effectiveProfile,
                                    allPayments = adminAllPayments,
                                    allUsers = adminAllUsers
                                )

                                "PROFILE" -> ProfileAndOnboardingScreen(
                                    profile = effectiveProfile,
                                    isOnboarding = false,
                                    onSaveProfile = { fullName, studentClass, section, schoolName, dob, age, role, lang, theme ->
                                        viewModel.saveUserProfile(
                                            fullName = fullName,
                                            studentClass = studentClass,
                                            section = section,
                                            schoolName = schoolName,
                                            dob = dob,
                                            age = age,
                                            role = role,
                                            language = lang,
                                            themePreference = theme,
                                            onboardingCompleted = true
                                        )
                                    },
                                    onNavigateToSubscription = { currentRoute = "SUBSCRIPTION" },
                                    onNavigateToAdmin = { currentRoute = "ADMIN" },
                                    onSignOutSuccess = { currentFirebaseUser = null }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
