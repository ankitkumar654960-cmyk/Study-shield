package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalPrefsStore
import com.example.data.local.ShieldRuntimeState
import com.example.data.local.ThemePreference
import com.example.data.model.ExamReminder
import com.example.data.model.LeaderboardEntry
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.data.model.UserSecurityPii
import com.example.data.repository.StudyRepository
import com.example.notifications.StudyNotificationHelper
import com.example.util.SecurityUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class PomodoroPhase(val label: String) {
  FOCUS("Focus Session"),
  SHORT_BREAK("Short Break"),
  LONG_BREAK("Long Break"),
}

data class PomodoroTimerUiState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val subject: String = "Mathematics",
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val shieldLocked: Boolean = true,
    val completedPomodorosToday: Int = 0,
    val lastSessionFeedback: String? = null,
)

data class DayStudyPoint(
    val isoDate: String,
    val shortLabel: String,
    val minutes: Long,
)

data class SubjectDistribution(
    val subject: String,
    val minutes: Long,
    val percentage: Float,
)

data class WeeklyStudyReport(
    val totalMinutesThisWeek: Long,
    val previousWeekMinutes: Long,
    val deltaPercentage: Int,
    val sessionsCount: Int,
    val shieldSessionsCount: Int,
    val xpEarnedThisWeek: Long,
    val averageDailyMinutes: Long,
    val topSubject: String,
    val dailyPoints: List<DayStudyPoint>,
    val subjectBreakdown: List<SubjectDistribution>,
    val shareableText: String,
)

class StudyViewModel(
    private val repository: StudyRepository,
    private val prefsStore: LocalPrefsStore,
    private val appContext: Context,
    private val currentUserId: String,
    private val initialEmail: String,
    private val initialDisplayName: String,
) : ViewModel() {

  private var timerJob: Job? = null

  private val _pomodoroState = MutableStateFlow(PomodoroTimerUiState())
  val pomodoroState: StateFlow<PomodoroTimerUiState> = _pomodoroState.asStateFlow()

  private val _actionStatus = MutableStateFlow<String?>(null)
  val actionStatus: StateFlow<String?> = _actionStatus.asStateFlow()

  val themePreference: StateFlow<ThemePreference> =
      prefsStore.themePreference.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
          initialValue = ThemePreference.SYSTEM,
      )

  val shieldRuntimeState: StateFlow<ShieldRuntimeState> =
      prefsStore.shieldRuntimeState.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
          initialValue = ShieldRuntimeState(),
      )

  val twoFactorVerifiedUid: StateFlow<String?> =
      prefsStore.twoFactorVerifiedUid.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
          initialValue = null,
      )

  val profileState: StateFlow<UiState<UserProfile?>> =
      repository
          .observeUserProfile(currentUserId)
          .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
          .catch { error -> emit(UiState.Error(unwrapMessage(error, "Failed to load profile"))) }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = UiState.Loading,
          )

  val securityPiiState: StateFlow<UiState<UserSecurityPii?>> =
      repository
          .observeSecurityPii(currentUserId)
          .map<UserSecurityPii?, UiState<UserSecurityPii?>> { UiState.Success(it) }
          .catch { error ->
            emit(UiState.Error(unwrapMessage(error, "Failed to load security settings")))
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = UiState.Loading,
          )

  val sessionsState: StateFlow<UiState<List<StudySession>>> =
      repository
          .observeStudySessions(currentUserId)
          .map<List<StudySession>, UiState<List<StudySession>>> { UiState.Success(it) }
          .catch { error ->
            emit(UiState.Error(unwrapMessage(error, "Failed to load study sessions")))
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = UiState.Loading,
          )

  val examsState: StateFlow<UiState<List<ExamReminder>>> =
      repository
          .observeExams(currentUserId)
          .map<List<ExamReminder>, UiState<List<ExamReminder>>> { UiState.Success(it) }
          .catch { error -> emit(UiState.Error(unwrapMessage(error, "Failed to load exams"))) }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = UiState.Loading,
          )

  val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
      repository
          .observeLeaderboard()
          .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
          .catch { error ->
            emit(UiState.Error(unwrapMessage(error, "Failed to load leaderboard")))
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = UiState.Loading,
          )

  val weeklyReport: StateFlow<WeeklyStudyReport> =
      combine(sessionsState, profileState) { sessState, profState ->
            val sessions = (sessState as? UiState.Success)?.data ?: emptyList()
            val profile = (profState as? UiState.Success)?.data
            buildWeeklyReport(sessions, profile)
          }
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
              initialValue = buildWeeklyReport(emptyList(), null),
          )

  init {
    viewModelScope.launch {
      repository.ensureUserInitialized(initialDisplayName, initialEmail)
    }
    // Sync offline security cache & blocked packages whenever cloud profile/PII updates
    viewModelScope.launch {
      profileState.collect { state ->
        val profile = (state as? UiState.Success)?.data ?: return@collect
        prefsStore.setBlockedPackages(profile.blockedPackages.toSet())
        if (!_pomodoroState.value.isRunning && _pomodoroState.value.phase == PomodoroPhase.FOCUS) {
          val targetSec = (profile.pomodoroFocusMinutes.toInt().coerceIn(5, 120)) * 60
          if (_pomodoroState.value.totalSeconds != targetSec &&
              _pomodoroState.value.remainingSeconds == _pomodoroState.value.totalSeconds) {
            _pomodoroState.update { it.copy(totalSeconds = targetSec, remainingSeconds = targetSec) }
          }
        }
      }
    }
    viewModelScope.launch {
      securityPiiState.collect { state ->
        val pii = (state as? UiState.Success)?.data ?: return@collect
        if (pii.shieldPasswordHash.isNotBlank()) {
          prefsStore.cacheSecurityCredentials(currentUserId, pii.shieldPasswordHash)
        }
      }
    }
  }

  private fun unwrapMessage(error: Throwable, fallback: String): String {
    val firestoreError =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, fallback, rootError)
    return rootError.localizedMessage ?: fallback
  }

  fun clearStatusMessage() {
    _actionStatus.value = null
  }

  fun setThemePreference(theme: ThemePreference) {
    viewModelScope.launch { prefsStore.setThemePreference(theme) }
  }

  fun markTwoFactorVerifiedForSession() {
    viewModelScope.launch { prefsStore.setTwoFactorVerifiedUid(currentUserId) }
  }

  fun clearBlockedAttemptBanner() {
    viewModelScope.launch { prefsStore.recordBlockedAttempt(null) }
  }

  fun selectPomodoroPhase(phase: PomodoroPhase, customFocusMinutes: Int? = null) {
    if (_pomodoroState.value.isRunning) return
    val profile = (profileState.value as? UiState.Success)?.data
    val mins =
        when (phase) {
          PomodoroPhase.FOCUS ->
              customFocusMinutes ?: (profile?.pomodoroFocusMinutes?.toInt() ?: 25)
          PomodoroPhase.SHORT_BREAK -> profile?.pomodoroBreakMinutes?.toInt() ?: 5
          PomodoroPhase.LONG_BREAK -> 15
        }
    val secs = mins.coerceIn(1, 120) * 60
    _pomodoroState.update {
      it.copy(
          phase = phase,
          totalSeconds = secs,
          remainingSeconds = secs,
          lastSessionFeedback = null,
      )
    }
  }

  fun setPomodoroSubject(subject: String) {
    _pomodoroState.update { it.copy(subject = subject) }
  }

  fun setPomodoroShieldLocked(locked: Boolean) {
    if (_pomodoroState.value.isRunning && shieldRuntimeState.value.isShieldActive) return
    _pomodoroState.update { it.copy(shieldLocked = locked) }
  }

  fun startOrResumeTimer() {
    if (_pomodoroState.value.isRunning) return
    val current = _pomodoroState.value
    val endEpochMs = System.currentTimeMillis() + current.remainingSeconds * 1000L

    if (current.phase == PomodoroPhase.FOCUS && current.shieldLocked) {
      viewModelScope.launch {
        prefsStore.setShieldActive(
            active = true,
            subject = current.subject,
            endEpochMs = endEpochMs,
        )
      }
    }

    _pomodoroState.update { it.copy(isRunning = true, lastSessionFeedback = null) }
    timerJob?.cancel()
    timerJob =
        viewModelScope.launch {
          while (_pomodoroState.value.isRunning && _pomodoroState.value.remainingSeconds > 0) {
            delay(1000L)
            _pomodoroState.update { state ->
              val next = (state.remainingSeconds - 1).coerceAtLeast(0)
              state.copy(remainingSeconds = next)
            }
          }
          if (_pomodoroState.value.remainingSeconds == 0 && _pomodoroState.value.isRunning) {
            completeTimerSession(naturalFinish = true)
          }
        }
  }

  fun pauseTimerWithoutShield() {
    timerJob?.cancel()
    _pomodoroState.update { it.copy(isRunning = false) }
  }

  fun verifyPasswordAndExitShield(passwordInput: String): Boolean {
    val storedCloudHash =
        (securityPiiState.value as? UiState.Success)?.data?.shieldPasswordHash ?: ""
    val offlineHash = shieldRuntimeState.value.cachedPasswordHash
    val activeHash = storedCloudHash.ifBlank { offlineHash }

    if (activeHash.isBlank()) {
      // No password set yet -> allow exit
      releaseShieldAndResetTimer()
      return true
    }

    return if (SecurityUtils.verifyShieldPassword(passwordInput, currentUserId, activeHash)) {
      releaseShieldAndResetTimer()
      _actionStatus.value = "Study Shield unlocked. Standard app access restored."
      true
    } else {
      false
    }
  }

  fun releaseShieldAndResetTimer() {
    timerJob?.cancel()
    viewModelScope.launch { prefsStore.setShieldActive(false) }
    val total = _pomodoroState.value.totalSeconds
    _pomodoroState.update {
      it.copy(
          isRunning = false,
          remainingSeconds = total,
      )
    }
  }

  fun completeTimerSession(naturalFinish: Boolean) {
    timerJob?.cancel()
    val snapshot = _pomodoroState.value
    val elapsedSeconds = (snapshot.totalSeconds - snapshot.remainingSeconds).coerceAtLeast(60)
    val loggedMinutes =
        if (naturalFinish) (snapshot.totalSeconds / 60L).coerceAtLeast(1L)
        else (elapsedSeconds / 60L).coerceAtLeast(1L)

    viewModelScope.launch {
      prefsStore.setShieldActive(false)
      if (snapshot.phase == PomodoroPhase.FOCUS) {
        val profile = (profileState.value as? UiState.Success)?.data
        val report = weeklyReport.value
        val result =
            repository.logStudySession(
                subject = snapshot.subject,
                durationMinutes = loggedMinutes,
                mode = StudySession.MODE_POMODORO,
                shieldEnabled = snapshot.shieldLocked,
                notes =
                    if (naturalFinish) "Completed Pomodoro session"
                    else "Completed early focus block",
                sessionDate = todayIsoDate(),
                currentTotalXp = profile?.totalXp ?: 0L,
                currentWeeklyXp = profile?.weeklyXp ?: 0L,
                currentWeeklyMinutes = report.totalMinutesThisWeek,
                streakDays = (profile?.streakDays ?: 0L).coerceAtLeast(1L),
            )
        val xpGained =
            loggedMinutes * 10L +
                (if (snapshot.shieldLocked) loggedMinutes * 2L else 0L) +
                (if (loggedMinutes >= 25L) 50L else 0L)

        if (result.isSuccess) {
          StudyNotificationHelper.showLocalNotification(
              context = appContext,
              channelId = StudyNotificationHelper.CHANNEL_TIMER,
              notificationId = 3001,
              title = "Focus Complete: +$xpGained XP!",
              body = "Great work on ${snapshot.subject} ($loggedMinutes min). Time for a short break.",
          )
          _pomodoroState.update {
            it.copy(
                isRunning = false,
                remainingSeconds = it.totalSeconds,
                completedPomodorosToday = it.completedPomodorosToday + 1,
                lastSessionFeedback = "Logged $loggedMinutes min of ${snapshot.subject} (+$xpGained XP)",
            )
          }
        } else {
          _pomodoroState.update {
            it.copy(
                isRunning = false,
                remainingSeconds = it.totalSeconds,
                lastSessionFeedback = "Session saved offline; will sync when connected.",
            )
          }
        }
      } else {
        StudyNotificationHelper.showLocalNotification(
            context = appContext,
            channelId = StudyNotificationHelper.CHANNEL_TIMER,
            notificationId = 3002,
            title = "Break Finished",
            body = "Ready to jump back into your next StudyShield focus block?",
        )
        _pomodoroState.update {
          it.copy(
              isRunning = false,
              phase = PomodoroPhase.FOCUS,
              totalSeconds = 25 * 60,
              remainingSeconds = 25 * 60,
              lastSessionFeedback = "Break complete! Ready to focus.",
          )
        }
      }
    }
  }

  fun logManualStudySession(
      subject: String,
      durationMinutes: Long,
      shieldEnabled: Boolean,
      notes: String,
  ) {
    viewModelScope.launch {
      val profile = (profileState.value as? UiState.Success)?.data
      val report = weeklyReport.value
      val result =
          repository.logStudySession(
              subject = subject,
              durationMinutes = durationMinutes,
              mode = StudySession.MODE_MANUAL,
              shieldEnabled = shieldEnabled,
              notes = notes,
              sessionDate = todayIsoDate(),
              currentTotalXp = profile?.totalXp ?: 0L,
              currentWeeklyXp = profile?.weeklyXp ?: 0L,
              currentWeeklyMinutes = report.totalMinutesThisWeek,
              streakDays = (profile?.streakDays ?: 0L).coerceAtLeast(1L),
          )
      _actionStatus.value =
          if (result.isSuccess) "Study session logged & synced (+XP)!"
          else "Failed to log session: ${result.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun createExamReminder(
      title: String,
      subject: String,
      examDateMillis: Long,
      targetHours: Long,
      reminderMinutesBefore: Long,
      priority: String,
  ) {
    viewModelScope.launch {
      val result =
          repository.createExam(
              title = title,
              subject = subject,
              examDateMillis = examDateMillis,
              targetHours = targetHours,
              reminderMinutesBefore = reminderMinutesBefore,
              priority = priority,
          )
      result.onSuccess { examId ->
        val triggerAt = examDateMillis - reminderMinutesBefore * 60_000L
        StudyNotificationHelper.scheduleExamReminder(
            context = appContext,
            examId = examId,
            title = title,
            subject = subject,
            triggerAtMillis = triggerAt,
        )
        _actionStatus.value = "Exam reminder scheduled!"
      }
      result.onFailure { err ->
        _actionStatus.value = "Could not save exam: ${err.localizedMessage}"
      }
    }
  }

  fun addExamStudyHour(exam: ExamReminder, deltaHours: Double = 1.0) {
    viewModelScope.launch {
      val nextHours = (exam.completedHours + deltaHours).coerceIn(0.0, exam.targetHours.toDouble())
      val isNowDone = nextHours >= exam.targetHours.toDouble()
      repository.updateExamProgress(exam.id, nextHours, isNowDone)
    }
  }

  fun toggleExamCompleted(exam: ExamReminder) {
    viewModelScope.launch {
      repository.updateExamProgress(exam.id, exam.completedHours, !exam.completed)
    }
  }

  fun deleteExam(examId: String) {
    viewModelScope.launch {
      StudyNotificationHelper.cancelExamReminder(appContext, examId)
      repository.deleteExam(examId)
    }
  }

  fun triggerExamNotificationPreview(exam: ExamReminder) {
    StudyNotificationHelper.showLocalNotification(
        context = appContext,
        channelId = StudyNotificationHelper.CHANNEL_EXAMS,
        notificationId = exam.id.hashCode(),
        title = "Exam Reminder: ${exam.title}",
        body =
            "${exam.subject} • Target ${exam.targetHours}h (${String.format(Locale.US, "%.1f", exam.completedHours)}h done).",
    )
    _actionStatus.value = "Local notification preview sent."
  }

  fun toggleBlockedPackage(packageName: String) {
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    val current = profile.blockedPackages.toMutableList()
    if (packageName in current) {
      current.remove(packageName)
    } else {
      current.add(packageName)
    }
    saveProfilePreferences(
        displayName = profile.displayName,
        avatarPreset = profile.avatarPreset,
        weeklyGoalMinutes = profile.weeklyGoalMinutes,
        pomodoroFocusMinutes = profile.pomodoroFocusMinutes,
        pomodoroBreakMinutes = profile.pomodoroBreakMinutes,
        blockedPackages = current,
        shareOnLeaderboard = profile.shareOnLeaderboard,
    )
  }

  fun addCustomBlockedPackage(packageName: String) {
    val clean = packageName.trim()
    if (clean.isBlank() || !clean.contains('.')) {
      _actionStatus.value = "Enter a valid Android package ID (e.g., com.instagram.android)"
      return
    }
    val profile = (profileState.value as? UiState.Success)?.data ?: return
    val updated = (profile.blockedPackages + clean).distinct().take(30)
    saveProfilePreferences(
        displayName = profile.displayName,
        avatarPreset = profile.avatarPreset,
        weeklyGoalMinutes = profile.weeklyGoalMinutes,
        pomodoroFocusMinutes = profile.pomodoroFocusMinutes,
        pomodoroBreakMinutes = profile.pomodoroBreakMinutes,
        blockedPackages = updated,
        shareOnLeaderboard = profile.shareOnLeaderboard,
    )
    _actionStatus.value = "Added $clean to Distraction Shield."
  }

  fun saveProfilePreferences(
      displayName: String,
      avatarPreset: String,
      weeklyGoalMinutes: Long,
      pomodoroFocusMinutes: Long,
      pomodoroBreakMinutes: Long,
      blockedPackages: List<String>,
      shareOnLeaderboard: Boolean,
  ) {
    viewModelScope.launch {
      prefsStore.setBlockedPackages(blockedPackages.toSet())
      val result =
          repository.updateProfilePreferences(
              displayName = displayName,
              avatarPreset = avatarPreset,
              weeklyGoalMinutes = weeklyGoalMinutes,
              pomodoroFocusMinutes = pomodoroFocusMinutes,
              pomodoroBreakMinutes = pomodoroBreakMinutes,
              blockedPackages = blockedPackages,
              shareOnLeaderboard = shareOnLeaderboard,
          )
      _actionStatus.value =
          if (result.isSuccess) "Profile preferences saved & synced."
          else "Save failed: ${result.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun saveSecuritySettings(
      email: String,
      phoneNumber: String,
      twoFactorEnabled: Boolean,
      totpSecret: String,
      newShieldPassword: String?,
  ) {
    viewModelScope.launch {
      val existingPii = (securityPiiState.value as? UiState.Success)?.data
      val finalSecret =
          if (twoFactorEnabled && totpSecret.isBlank()) {
            existingPii?.totpSecret?.ifBlank { SecurityUtils.generateTotpSecret() }
                ?: SecurityUtils.generateTotpSecret()
          } else {
            totpSecret
          }
      val finalPwHash =
          if (!newShieldPassword.isNullOrBlank()) {
            SecurityUtils.hashShieldPassword(newShieldPassword.trim(), currentUserId)
          } else {
            existingPii?.shieldPasswordHash ?: ""
          }
      val hasPw = finalPwHash.isNotBlank()
      if (hasPw) {
        prefsStore.cacheSecurityCredentials(currentUserId, finalPwHash)
      }
      if (twoFactorEnabled) {
        prefsStore.setTwoFactorVerifiedUid(currentUserId)
      }
      val result =
          repository.updateContactAndSecurity(
              email = email,
              phoneNumber = phoneNumber,
              twoFactorEnabled = twoFactorEnabled,
              totpSecret = finalSecret,
              hasShieldPassword = hasPw,
              shieldPasswordHash = finalPwHash,
          )
      _actionStatus.value =
          if (result.isSuccess) "Security, contact & 2FA settings updated."
          else "Update failed: ${result.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun setShieldPasswordQuick(password: String) {
    val pii = (securityPiiState.value as? UiState.Success)?.data
    saveSecuritySettings(
        email = pii?.email ?: initialEmail,
        phoneNumber = pii?.phoneNumber ?: "",
        twoFactorEnabled = (profileState.value as? UiState.Success)?.data?.twoFactorEnabled ?: false,
        totpSecret = pii?.totpSecret ?: "",
        newShieldPassword = password,
    )
  }

  private fun buildWeeklyReport(
      sessions: List<StudySession>,
      profile: UserProfile?,
  ): WeeklyStudyReport {
    val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val shortFmt = SimpleDateFormat("EEE", Locale.US)
    val cal = Calendar.getInstance()

    val thisWeekDates = LinkedHashMap<String, String>()
    for (daysAgo in 6 downTo 0) {
      val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
      thisWeekDates[isoFmt.format(c.time)] = shortFmt.format(c.time)
    }

    val prevWeekDates = HashSet<String>()
    for (daysAgo in 13 downTo 7) {
      val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
      prevWeekDates.add(isoFmt.format(c.time))
    }

    val thisWeekSessions = sessions.filter { it.sessionDate in thisWeekDates.keys }
    val prevWeekSessions = sessions.filter { it.sessionDate in prevWeekDates }

    val totalThisWeek = thisWeekSessions.sumOf { it.durationMinutes }
    val totalPrevWeek = prevWeekSessions.sumOf { it.durationMinutes }
    val deltaPct =
        when {
          totalPrevWeek == 0L && totalThisWeek > 0L -> 100
          totalPrevWeek == 0L -> 0
          else -> (((totalThisWeek - totalPrevWeek) * 100L) / totalPrevWeek).toInt()
        }

    val dailyPoints =
        thisWeekDates.entries.map { (iso, label) ->
          val mins = thisWeekSessions.filter { it.sessionDate == iso }.sumOf { it.durationMinutes }
          DayStudyPoint(isoDate = iso, shortLabel = label, minutes = mins)
        }

    val subjectGroups =
        thisWeekSessions.groupBy { it.subject.ifBlank { "General" } }.mapValues { (_, list) ->
          list.sumOf { it.durationMinutes }
        }
    val denom = totalThisWeek.coerceAtLeast(1L).toFloat()
    val subjectBreakdown =
        subjectGroups.entries
            .sortedByDescending { it.value }
            .map { (subj, mins) ->
              SubjectDistribution(
                  subject = subj,
                  minutes = mins,
                  percentage = (mins.toFloat() / denom) * 100f,
              )
            }

    val topSubject = subjectBreakdown.firstOrNull()?.subject ?: "None yet"
    val xpThisWeek = thisWeekSessions.sumOf { it.xpEarned }
    val shieldCount = thisWeekSessions.count { it.shieldEnabled }
    val goalMinutes = profile?.weeklyGoalMinutes ?: 600L

    val shareText = buildString {
      appendLine("StudyShield Weekly Study Report")
      appendLine("Scholar: ${profile?.displayName ?: initialDisplayName}")
      appendLine("Total Study Duration: ${totalThisWeek / 60}h ${totalThisWeek % 60}m (Goal: ${goalMinutes / 60}h)")
      appendLine("Sessions Completed: ${thisWeekSessions.size} (${shieldCount} with Distraction Shield)")
      appendLine("XP Earned This Week: +$xpThisWeek XP")
      appendLine("Top Subject: $topSubject")
      appendLine("Daily Breakdown:")
      dailyPoints.forEach { pt -> appendLine("  • ${pt.shortLabel} (${pt.isoDate}): ${pt.minutes} min") }
    }

    return WeeklyStudyReport(
        totalMinutesThisWeek = totalThisWeek,
        previousWeekMinutes = totalPrevWeek,
        deltaPercentage = deltaPct,
        sessionsCount = thisWeekSessions.size,
        shieldSessionsCount = shieldCount,
        xpEarnedThisWeek = xpThisWeek,
        averageDailyMinutes = totalThisWeek / 7L,
        topSubject = topSubject,
        dailyPoints = dailyPoints,
        subjectBreakdown = subjectBreakdown,
        shareableText = shareText,
    )
  }

  private fun todayIsoDate(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  }

  private companion object {
    const val TAG = "StudyViewModel"
    const val STOP_TIMEOUT_MS = 5000L
  }
}
