package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ExamReminder
import com.example.data.model.LeaderboardEntry
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.data.model.UserSecurityPii
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
      context: Context
  ) : this(
      db =
          FirebaseFirestore.getInstance(
              context.applicationContext.getString(R.string.firestore_database_id)
          ),
      auth = Firebase.auth,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid ?: "guest_local_scholar"
  }

  private val isGuest: Boolean
    get() = auth.currentUser == null || requireUserId() == "guest_local_scholar"

  private fun unwrapAndLog(error: Throwable, op: OperationType, path: String, uid: String): Nothing {
    val firestoreError =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    val currentUid = auth.currentUser?.uid ?: "guest_local_scholar"
    if (exceptionToLog != null && currentUid == uid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
    throw error
  }

  fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
    if (userId == "guest_local_scholar" || auth.currentUser == null) {
      emitAll(localGuestProfile)
      return@flow
    }
    val path = "users/$userId"
    emitAll(
        db.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
              if (snapshot.exists()) {
                snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
              } else {
                null
              }
            }
            .catch { error -> unwrapAndLog(error, OperationType.GET, path, userId) }
    )
  }

  fun observeSecurityPii(userId: String): Flow<UserSecurityPii?> = flow {
    if (userId == "guest_local_scholar" || auth.currentUser == null) {
      emit(
          UserSecurityPii(
              userId = userId,
              email = "guest@studyshield.local",
              phoneNumber = "",
          )
      )
      return@flow
    }
    val path = "users/$userId/private/security"
    emitAll(
        db.collection("users")
            .document(userId)
            .collection("private")
            .document("security")
            .snapshots()
            .map { snapshot ->
              if (snapshot.exists()) {
                snapshot.toObject(UserSecurityPii::class.java, ServerTimestampBehavior.ESTIMATE)
              } else {
                null
              }
            }
            .catch { error -> unwrapAndLog(error, OperationType.GET, path, userId) }
    )
  }

  fun observeStudySessions(userId: String): Flow<List<StudySession>> = flow {
    if (userId == "guest_local_scholar" || auth.currentUser == null) {
      emitAll(localGuestSessions)
      return@flow
    }
    val path = "users/$userId/study_sessions"
    emitAll(
        db.collection("users")
            .document(userId)
            .collection("study_sessions")
            .snapshots()
            .map { snapshot ->
              val remote = snapshot.toObjects(StudySession::class.java, ServerTimestampBehavior.ESTIMATE)
              (remote + localGuestSessions.value).distinctBy { it.id }.sortedByDescending { it.createdAt?.seconds ?: System.currentTimeMillis() }
            }
            .catch { error ->
              emit(localGuestSessions.value)
            }
    )
  }

  fun observeExams(userId: String): Flow<List<ExamReminder>> = flow {
    if (userId == "guest_local_scholar" || auth.currentUser == null) {
      emit(emptyList())
      return@flow
    }
    val path = "users/$userId/exams"
    emitAll(
        db.collection("users")
            .document(userId)
            .collection("exams")
            .snapshots()
            .map { snapshot ->
              snapshot
                  .toObjects(ExamReminder::class.java, ServerTimestampBehavior.ESTIMATE)
                  .sortedBy { it.examDateMillis }
            }
            .catch { error -> unwrapAndLog(error, OperationType.LIST, path, userId) }
    )
  }

  fun observeLeaderboard(): Flow<List<LeaderboardEntry>> = flow {
    if (isGuest || auth.currentUser == null) {
      emit(emptyList())
      return@flow
    }
    val uid = requireUserId()
    val path = "leaderboard"
    emitAll(
        db.collection("leaderboard")
            .whereEqualTo("isPublic", true)
            .limit(100)
            .snapshots()
            .map { snapshot ->
              snapshot
                  .toObjects(LeaderboardEntry::class.java, ServerTimestampBehavior.ESTIMATE)
                  .sortedByDescending { it.weeklyXp }
            }
            .catch { error -> unwrapAndLog(error, OperationType.LIST, path, uid) }
    )
  }

  suspend fun ensureUserInitialized(
      displayName: String,
      email: String,
  ): Result<Unit> {
    if (isGuest) return Result.success(Unit)
    val uid = requireUserId()
    val safeName = displayName.trim().ifBlank { "Scholar" }.take(50)
    val safeEmail = email.trim().take(120)
    val profileRef = db.collection("users").document(uid)
    val piiRef = profileRef.collection("private").document("security")
    val leaderboardRef = db.collection("leaderboard").document(uid)

    return try {
      val profileSnap = profileRef.get().await()
      if (!profileSnap.exists()) {
        val profilePayload =
            mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "avatarPreset" to "owl",
                "weeklyGoalMinutes" to 600L,
                "pomodoroFocusMinutes" to 25L,
                "pomodoroBreakMinutes" to 5L,
                "totalXp" to 0L,
                "weeklyXp" to 0L,
                "streakDays" to 1L,
                "blockedPackages" to UserProfile.DEFAULT_BLOCKED_PACKAGES,
                "shareOnLeaderboard" to true,
                "twoFactorEnabled" to false,
                "hasShieldPassword" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        profileRef.set(profilePayload).await()
      }

      val piiSnap = piiRef.get().await()
      if (!piiSnap.exists()) {
        val piiPayload =
            mapOf(
                "userId" to uid,
                "email" to safeEmail,
                "phoneNumber" to "",
                "totpSecret" to "",
                "shieldPasswordHash" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        piiRef.set(piiPayload).await()
      }

      val lbSnap = leaderboardRef.get().await()
      if (!lbSnap.exists()) {
        val lbPayload =
            mapOf(
                "userId" to uid,
                "displayName" to safeName,
                "avatarPreset" to "owl",
                "totalXp" to 0L,
                "weeklyXp" to 0L,
                "weeklyStudyMinutes" to 0L,
                "streakDays" to 1L,
                "level" to 1L,
                "isPublic" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        leaderboardRef.set(lbPayload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, profileRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateProfilePreferences(
      displayName: String,
      firstName: String,
      lastName: String,
      dateOfBirth: String,
      studentClass: String,
      avatarPreset: String,
      weeklyGoalMinutes: Long,
      pomodoroFocusMinutes: Long,
      pomodoroBreakMinutes: Long,
      blockedPackages: List<String>,
      shareOnLeaderboard: Boolean,
  ): Result<Unit> {
    val safeName = displayName.trim().ifBlank { "Scholar" }.take(50)
    val safeFirstName = firstName.trim().take(30)
    val safeLastName = lastName.trim().take(30)
    val safeDob = dateOfBirth.trim().take(20)
    val safeClass = studentClass.trim().take(40)
    val safeAvatar = avatarPreset.trim().ifBlank { "owl" }.take(30)
    val safePackages = blockedPackages.distinct().take(30)

    if (isGuest) {
      localGuestProfile.update {
        it.copy(
            displayName = safeName,
            firstName = safeFirstName,
            lastName = safeLastName,
            dateOfBirth = safeDob,
            studentClass = safeClass,
            avatarPreset = safeAvatar,
            weeklyGoalMinutes = weeklyGoalMinutes.coerceIn(30L, 5000L),
            pomodoroFocusMinutes = pomodoroFocusMinutes.coerceIn(5L, 120L),
            pomodoroBreakMinutes = pomodoroBreakMinutes.coerceIn(1L, 30L),
            blockedPackages = safePackages,
            shareOnLeaderboard = shareOnLeaderboard,
        )
      }
      return Result.success(Unit)
    }

    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    val leaderboardRef = db.collection("leaderboard").document(uid)

    return try {
      profileRef
          .update(
              mapOf(
                  "displayName" to safeName,
                  "firstName" to safeFirstName,
                  "lastName" to safeLastName,
                  "dateOfBirth" to safeDob,
                  "studentClass" to safeClass,
                  "avatarPreset" to safeAvatar,
                  "weeklyGoalMinutes" to weeklyGoalMinutes.coerceIn(30L, 5000L),
                  "pomodoroFocusMinutes" to pomodoroFocusMinutes.coerceIn(5L, 120L),
                  "pomodoroBreakMinutes" to pomodoroBreakMinutes.coerceIn(1L, 30L),
                  "blockedPackages" to safePackages,
                  "shareOnLeaderboard" to shareOnLeaderboard,
                  "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
          .await()

      val lbSnap = leaderboardRef.get().await()
      if (lbSnap.exists()) {
        leaderboardRef
            .update(
                mapOf(
                    "displayName" to safeName,
                    "avatarPreset" to safeAvatar,
                    "isPublic" to shareOnLeaderboard,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      localGuestProfile.update {
        it.copy(
            displayName = safeName,
            firstName = safeFirstName,
            lastName = safeLastName,
            dateOfBirth = safeDob,
            studentClass = safeClass,
            avatarPreset = safeAvatar,
            weeklyGoalMinutes = weeklyGoalMinutes.coerceIn(30L, 5000L),
            pomodoroFocusMinutes = pomodoroFocusMinutes.coerceIn(5L, 120L),
            pomodoroBreakMinutes = pomodoroBreakMinutes.coerceIn(1L, 30L),
            blockedPackages = safePackages,
            shareOnLeaderboard = shareOnLeaderboard,
        )
      }
      Result.success(Unit)
    }
  }

  suspend fun updateContactAndSecurity(
      email: String,
      phoneNumber: String,
      twoFactorEnabled: Boolean,
      totpSecret: String,
      hasShieldPassword: Boolean,
      shieldPasswordHash: String,
  ): Result<Unit> {
    if (isGuest) return Result.success(Unit)
    val uid = requireUserId()
    val profileRef = db.collection("users").document(uid)
    val piiRef = profileRef.collection("private").document("security")

    return try {
      piiRef
          .update(
              mapOf(
                  "email" to email.trim().take(120),
                  "phoneNumber" to phoneNumber.trim().take(30),
                  "totpSecret" to totpSecret.trim().take(64),
                  "shieldPasswordHash" to shieldPasswordHash.trim().take(128),
                  "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
          .await()

      profileRef
          .update(
              mapOf(
                  "twoFactorEnabled" to twoFactorEnabled,
                  "hasShieldPassword" to hasShieldPassword,
                  "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
          .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, piiRef.path)
      Result.failure(e)
    }
  }

  suspend fun logStudySession(
      subject: String,
      durationMinutes: Long,
      mode: String,
      shieldEnabled: Boolean,
      notes: String,
      sessionDate: String,
      currentTotalXp: Long,
      currentWeeklyXp: Long,
      currentWeeklyMinutes: Long,
      streakDays: Long,
  ): Result<String> {
    val uid = requireUserId()
    val safeMinutes = durationMinutes.coerceIn(1L, 720L)
    val baseXp = safeMinutes * 10L
    val shieldBonus = if (shieldEnabled) safeMinutes * 2L else 0L
    val completionBonus =
        if (mode == StudySession.MODE_POMODORO && safeMinutes >= 25L) 50L else 0L
    val xpEarned = baseXp + shieldBonus + completionBonus

    val localId = "session_${System.currentTimeMillis()}"
    val localSession = StudySession(
        id = localId,
        userId = uid,
        subject = subject.trim().ifBlank { "General Study" }.take(60),
        durationMinutes = safeMinutes,
        xpEarned = xpEarned,
        mode = mode,
        shieldEnabled = shieldEnabled,
        notes = notes.trim().take(300),
        sessionDate = sessionDate.take(10),
    )
    localGuestSessions.update { listOf(localSession) + it }

    if (isGuest || auth.currentUser == null) {
      return Result.success(localId)
    }

    val sessionRef = db.collection("users").document(uid).collection("study_sessions").document()
    val profileRef = db.collection("users").document(uid)
    val leaderboardRef = db.collection("leaderboard").document(uid)

    val payload =
        mapOf(
            "userId" to uid,
            "subject" to localSession.subject,
            "durationMinutes" to safeMinutes,
            "xpEarned" to xpEarned,
            "mode" to mode,
            "shieldEnabled" to shieldEnabled,
            "notes" to localSession.notes,
            "sessionDate" to localSession.sessionDate,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

    return try {
      sessionRef.set(payload).await()

      try {
        val newTotalXp = currentTotalXp + xpEarned
        val newWeeklyXp = currentWeeklyXp + xpEarned
        val newWeeklyMinutes = (currentWeeklyMinutes + safeMinutes).coerceAtMost(10080L)
        val newLevel = ((newTotalXp / 500L) + 1L).coerceIn(1L, 100L)

        val profileSnap = profileRef.get().await()
        if (profileSnap.exists()) {
          profileRef
              .update(
                  mapOf(
                      "totalXp" to newTotalXp,
                      "weeklyXp" to newWeeklyXp,
                      "streakDays" to streakDays.coerceAtLeast(1L),
                      "updatedAt" to FieldValue.serverTimestamp(),
                  )
              )
              .await()
        }

        val lbSnap = leaderboardRef.get().await()
        if (lbSnap.exists()) {
          leaderboardRef
              .update(
                  mapOf(
                      "totalXp" to newTotalXp,
                      "weeklyXp" to newWeeklyXp,
                      "weeklyStudyMinutes" to newWeeklyMinutes,
                      "streakDays" to streakDays.coerceAtLeast(1L),
                      "level" to newLevel,
                      "updatedAt" to FieldValue.serverTimestamp(),
                  )
              )
              .await()
        }
      } catch (_: Exception) {
        // Non-fatal profile/leaderboard update catch
      }

      Result.success(sessionRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w("StudyRepository", "Remote session sync failed, session kept locally: ${e.message}")
      Result.success(localId)
    }
  }

  suspend fun createExam(
      title: String,
      subject: String,
      chapter: String,
      lectureName: String,
      lectureUrl: String,
      examDateMillis: Long,
      targetHours: Long,
      reminderMinutesBefore: Long,
      priority: String,
  ): Result<String> {
    val uid = requireUserId()
    val localId = "exam_${System.currentTimeMillis()}"
    if (isGuest || auth.currentUser == null) {
      return Result.success(localId)
    }
    val examRef = db.collection("users").document(uid).collection("exams").document()
    val payload =
        mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Upcoming Exam" }.take(80),
            "subject" to subject.trim().ifBlank { "General" }.take(60),
            "chapter" to chapter.trim().take(80),
            "lectureName" to lectureName.trim().take(80),
            "lectureUrl" to lectureUrl.trim().take(300),
            "examDateMillis" to examDateMillis.coerceAtLeast(1L),
            "targetHours" to targetHours.coerceIn(1L, 500L),
            "completedHours" to 0.0,
            "reminderMinutesBefore" to reminderMinutesBefore.coerceIn(0L, 10080L),
            "priority" to priority,
            "completed" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    return try {
      examRef.set(payload).await()
      Result.success(examRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w("StudyRepository", "Remote exam save failed, keeping locally: ${e.message}")
      Result.success(localId)
    }
  }

  suspend fun updateExamProgress(
      examId: String,
      completedHours: Double,
      completed: Boolean,
  ): Result<Unit> {
    if (isGuest) return Result.success(Unit)
    val uid = requireUserId()
    require(examId.isNotBlank()) { "Exam ID must not be blank" }
    val examRef = db.collection("users").document(uid).collection("exams").document(examId)
    return try {
      examRef
          .update(
              mapOf(
                  "completedHours" to completedHours.coerceIn(0.0, 500.0),
                  "completed" to completed,
                  "updatedAt" to FieldValue.serverTimestamp(),
              )
          )
          .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, examRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteExam(examId: String): Result<Unit> {
    if (isGuest) return Result.success(Unit)
    val uid = requireUserId()
    require(examId.isNotBlank()) { "Exam ID must not be blank" }
    val examRef = db.collection("users").document(uid).collection("exams").document(examId)
    return try {
      examRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, examRef.path)
      Result.failure(e)
    }
  }

  suspend fun getSessionById(sessionId: String): Result<StudySession?> {
    if (isGuest) return Result.success(null)
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("study_sessions").document(sessionId)
    return try {
      val snap = docRef.get().await()
      val obj =
          if (snap.exists()) {
            snap.toObject(StudySession::class.java, ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
      Result.success(obj)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  companion object {
    val localGuestSessions = MutableStateFlow<List<StudySession>>(emptyList())
    val localGuestProfile =
        MutableStateFlow(
            UserProfile(
                userId = "guest_local_scholar",
                displayName = "Scholar",
                firstName = "",
                lastName = "",
                dateOfBirth = "2008-05-15",
                studentClass = "JEE Aspirant",
                avatarPreset = "owl",
                weeklyGoalMinutes = 600L,
                pomodoroFocusMinutes = 25L,
                pomodoroBreakMinutes = 5L,
            )
        )
  }
}

