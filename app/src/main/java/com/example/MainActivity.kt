package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalPrefsStore
import com.example.data.local.ThemePreference
import com.example.data.repository.StudyRepository
import com.example.notifications.StudyNotificationHelper
import com.example.service.StudyShieldAccessibilityService
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.TwoFactorVerificationScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.BlockedAppInterceptDialog
import com.example.ui.components.ShieldExitPasswordDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExamsScreen
import com.example.ui.screens.FocusTimerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.StudyShieldTheme
import com.example.ui.viewmodel.StudyViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class StudyTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
) {
  HOME("Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_home"),
  FOCUS("Focus", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_focus"),
  EXAMS("Exams", Icons.Filled.Event, Icons.Outlined.Event, "nav_exams"),
  ANALYTICS("Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_analytics"),
  PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile"),
}

class MainActivity : ComponentActivity() {
  private var blockedAppIntentState by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    StudyNotificationHelper.ensureChannels(this)
    blockedAppIntentState = intent?.getStringExtra(StudyShieldAccessibilityService.EXTRA_BLOCKED_PACKAGE)

    setContent {
      val prefsStore = remember { LocalPrefsStore(applicationContext) }
      val themePref by
          prefsStore.themePreference.collectAsStateWithLifecycle(
              initialValue = ThemePreference.SYSTEM
          )
      val darkTheme =
          when (themePref) {
            ThemePreference.SYSTEM -> isSystemInDarkTheme()
            ThemePreference.DARK -> true
            ThemePreference.LIGHT -> false
          }

      StudyShieldTheme(darkTheme = darkTheme) {
        AppRoot(
            blockedAppFromIntent = blockedAppIntentState,
            onClearBlockedIntent = { blockedAppIntentState = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val pkg = intent.getStringExtra(StudyShieldAccessibilityService.EXTRA_BLOCKED_PACKAGE)
    if (!pkg.isNullOrBlank()) {
      blockedAppIntentState = pkg
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppRoot(
    blockedAppFromIntent: String?,
    onClearBlockedIntent: () -> Unit,
) {
  val auth = remember { Firebase.auth }
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      AuthenticatedStudyApp(
          currentUserId = user.uid,
          userEmail = user.email ?: "",
          userDisplayName = user.displayName ?: "Scholar",
          blockedAppFromIntent = blockedAppFromIntent,
          onClearBlockedIntent = onClearBlockedIntent,
      )
    }
  }
}

@Composable
fun AuthenticatedStudyApp(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    blockedAppFromIntent: String?,
    onClearBlockedIntent: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val viewModel: StudyViewModel =
      viewModel(
          key = currentUserId,
          factory =
              viewModelFactory {
                initializer {
                  val app =
                      checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                      }
                  val databaseId = app.getString(R.string.firestore_database_id)
                  val db = FirebaseFirestore.getInstance(databaseId)
                  val repository = StudyRepository(db, Firebase.auth)
                  val prefsStore = LocalPrefsStore(app.applicationContext)
                  StudyViewModel(
                      repository = repository,
                      prefsStore = prefsStore,
                      appContext = app.applicationContext,
                      currentUserId = currentUserId,
                      initialEmail = userEmail,
                      initialDisplayName = userDisplayName,
                  )
                }
              },
      )

  val profileState by viewModel.profileState.collectAsStateWithLifecycle()
  val securityPiiState by viewModel.securityPiiState.collectAsStateWithLifecycle()
  val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
  val examsState by viewModel.examsState.collectAsStateWithLifecycle()
  val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
  val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
  val shieldState by viewModel.shieldRuntimeState.collectAsStateWithLifecycle()
  val pomodoroState by viewModel.pomodoroState.collectAsStateWithLifecycle()
  val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
  val twoFactorVerifiedUid by viewModel.twoFactorVerifiedUid.collectAsStateWithLifecycle()
  val actionStatus by viewModel.actionStatus.collectAsStateWithLifecycle()

  val profile = (profileState as? UiState.Success)?.data
  val pii = (securityPiiState as? UiState.Success)?.data

  val needsTwoFactor =
      profile?.twoFactorEnabled == true &&
          !pii?.totpSecret.isNullOrBlank() &&
          twoFactorVerifiedUid != currentUserId

  if (needsTwoFactor) {
    TwoFactorVerificationScreen(
        email = pii?.email ?: userEmail,
        phoneNumber = pii?.phoneNumber ?: "",
        totpSecret = pii?.totpSecret ?: "",
        onVerified = { viewModel.markTwoFactorVerifiedForSession() },
        onSignOut = {
          signOutUser(credentialManager, scope)
        },
    )
    return
  }

  var activeTab by remember { mutableStateOf(StudyTab.HOME) }
  var showExitPasswordDialog by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(actionStatus) {
    val msg = actionStatus
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  if (activeTab != StudyTab.HOME) {
    BackHandler { activeTab = StudyTab.HOME }
  }

  val activeBlockedApp = blockedAppFromIntent ?: shieldState.lastBlockedAppPackage
  if (!activeBlockedApp.isNullOrBlank() && shieldState.isShieldActive) {
    BlockedAppInterceptDialog(
        blockedPackage = activeBlockedApp,
        activeSubject = shieldState.activeSubject,
        onDismissToStudy = {
          onClearBlockedIntent()
          viewModel.clearBlockedAttemptBanner()
          activeTab = StudyTab.FOCUS
        },
        onRequestPasswordExit = {
          onClearBlockedIntent()
          viewModel.clearBlockedAttemptBanner()
          showExitPasswordDialog = true
        },
    )
  }

  if (showExitPasswordDialog) {
    ShieldExitPasswordDialog(
        onDismiss = { showExitPasswordDialog = false },
        onVerifyPassword = { pw -> viewModel.verifyPasswordAndExitShield(pw) },
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
          if (!useNavigationRail) {
            NavigationBar {
              StudyTab.entries.forEach { tab ->
                val selected = activeTab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { activeTab = tab },
                    icon = {
                      Icon(
                          imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                          contentDescription = tab.label,
                      )
                    },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag(tab.tag),
                )
              }
            }
          }
        },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail) {
          NavigationRail {
            StudyTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationRailItem(
                  selected = selected,
                  onClick = { activeTab = tab },
                  icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                    )
                  },
                  label = { Text(tab.label) },
                  modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          when (activeTab) {
            StudyTab.HOME ->
                DashboardScreen(
                    profileState = profileState,
                    sessionsState = sessionsState,
                    examsState = examsState,
                    shieldState = shieldState,
                    weeklyReport = weeklyReport,
                    onNavigateToFocus = { activeTab = StudyTab.FOCUS },
                    onNavigateToExams = { activeTab = StudyTab.EXAMS },
                    onNavigateToAnalytics = { activeTab = StudyTab.ANALYTICS },
                    onQuickLogSession = { subj, mins, shield, notes ->
                      viewModel.logManualStudySession(subj, mins, shield, notes)
                    },
                    onOpenShieldUnlock = { showExitPasswordDialog = true },
                )
            StudyTab.FOCUS ->
                FocusTimerScreen(
                    pomodoroState = pomodoroState,
                    shieldState = shieldState,
                    profile = profile,
                    onSelectPhase = { phase, customMins ->
                      viewModel.selectPomodoroPhase(phase, customMins)
                    },
                    onSelectSubject = { subj -> viewModel.setPomodoroSubject(subj) },
                    onToggleShieldLock = { locked -> viewModel.setPomodoroShieldLocked(locked) },
                    onStartOrResume = { viewModel.startOrResumeTimer() },
                    onPauseWithoutShield = { viewModel.pauseTimerWithoutShield() },
                    onCompleteNow = { viewModel.completeTimerSession(naturalFinish = false) },
                    onRequestPasswordExit = { showExitPasswordDialog = true },
                    onSetShieldPassword = { pw -> viewModel.setShieldPasswordQuick(pw) },
                    onToggleBlockedPackage = { pkg -> viewModel.toggleBlockedPackage(pkg) },
                    onAddCustomPackage = { pkg -> viewModel.addCustomBlockedPackage(pkg) },
                )
            StudyTab.EXAMS ->
                ExamsScreen(
                    examsState = examsState,
                    onCreateExam = { title, subj, millis, targetHrs, remMins, prio ->
                      viewModel.createExamReminder(title, subj, millis, targetHrs, remMins, prio)
                    },
                    onAddStudyHour = { exam -> viewModel.addExamStudyHour(exam) },
                    onToggleCompleted = { exam -> viewModel.toggleExamCompleted(exam) },
                    onDeleteExam = { examId -> viewModel.deleteExam(examId) },
                    onTriggerNotificationPreview = { exam ->
                      viewModel.triggerExamNotificationPreview(exam)
                    },
                )
            StudyTab.ANALYTICS ->
                AnalyticsScreen(
                    currentUserId = currentUserId,
                    sessionsState = sessionsState,
                    leaderboardState = leaderboardState,
                    weeklyReport = weeklyReport,
                    onReportCopied = {
                      scope.launch {
                        snackbarHostState.showSnackbar("Weekly report copied to clipboard!")
                      }
                    },
                )
            StudyTab.PROFILE ->
                ProfileScreen(
                    profileState = profileState,
                    securityPiiState = securityPiiState,
                    themePreference = themePref,
                    userEmailFallback = userEmail,
                    onChangeTheme = { pref -> viewModel.setThemePreference(pref) },
                    onSavePreferences = { name, avatar, goal, focus, breakM, pkgs, share ->
                      viewModel.saveProfilePreferences(name, avatar, goal, focus, breakM, pkgs, share)
                    },
                    onSaveSecurity = { email, phone, twoFa, secret, newPw ->
                      viewModel.saveSecuritySettings(email, phone, twoFa, secret, newPw)
                    },
                    onSignOut = {
                      signOutUser(credentialManager, scope)
                    },
                )
          }
        }
      }
    }
  }
}
