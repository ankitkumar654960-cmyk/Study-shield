package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExamReminder
import com.example.data.model.StudySession
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserInitialized_and_logStudySession_syncsProfileAndLeaderboard() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore, auth)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserInitialized("Alice", ALICE_EMAIL).getOrThrow()
    }

    val sessionId =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.logStudySession(
                  subject = "Quantum Mechanics",
                  durationMinutes = 25L,
                  mode = StudySession.MODE_POMODORO,
                  shieldEnabled = true,
                  notes = "Wavefunction problems",
                  sessionDate = "2026-11-16",
                  currentTotalXp = 0L,
                  currentWeeklyXp = 0L,
                  currentWeeklyMinutes = 0L,
                  streakDays = 1L,
              )
              .getOrThrow()
        }
    assertTrue(sessionId.isNotBlank())

    val sessions =
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeStudySessions(uid).first { list -> list.any { it.id == sessionId } }
        }
    assertTrue(sessions.any { it.subject == "Quantum Mechanics" && it.xpEarned == 350L })

    val leaderboard =
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeLeaderboard().first { list -> list.any { it.userId == uid } }
        }
    val aliceEntry = leaderboard.first { it.userId == uid }
    assertTrue(aliceEntry.weeklyXp >= 350L)
  }

  @Test
  fun createAndObserveExam_ownerSucceeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudyRepository(firestore, auth)

    val examId =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          repo.createExam(
                  title = "Algorithms Final",
                  subject = "Computer Science",
                  examDateMillis = System.currentTimeMillis() + 86_400_000L,
                  targetHours = 15L,
                  reminderMinutesBefore = 60L,
                  priority = ExamReminder.PRIORITY_HIGH,
              )
              .getOrThrow()
        }
    assertTrue(examId.isNotBlank())

    val exams =
        withTimeout(FLOW_TIMEOUT_MS) {
          repo.observeExams(uid).first { list -> list.any { it.id == examId } }
        }
    assertNotNull(exams.firstOrNull { it.id == examId })
  }

  @Test
  fun crossUserRead_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = StudyRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureUserInitialized("Alice", ALICE_EMAIL).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = StudyRepository(firestore, auth)

    var captured: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeUserProfile(aliceUid).first() }
    } catch (e: Exception) {
      captured = e.unwrapFirestoreException()
    }
    assertNotNull(captured)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, captured?.code)
  }

  @Test
  fun unauthenticatedObserver_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = StudyRepository(firestore, auth)

    var captured: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeStudySessions("some_uid").first() }
    } catch (e: Exception) {
      captured = e.unwrapFirestoreException()
    }
    assertNotNull(captured)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, captured?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_study@test.com"
    const val BOB_EMAIL = "bob_study@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
