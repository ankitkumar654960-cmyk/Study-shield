package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Scholar",
    val firstName: String = "",
    val lastName: String = "",
    val dateOfBirth: String = "",
    val studentClass: String = "JEE / NEET Aspirant",
    val avatarPreset: String = "owl",
    val weeklyGoalMinutes: Long = 600L,
    val pomodoroFocusMinutes: Long = 25L,
    val pomodoroBreakMinutes: Long = 5L,
    val totalXp: Long = 0L,
    val weeklyXp: Long = 0L,
    val streakDays: Long = 0L,
    val blockedPackages: List<String> = DEFAULT_BLOCKED_PACKAGES,
    val shareOnLeaderboard: Boolean = true,
    val twoFactorEnabled: Boolean = false,
    val hasShieldPassword: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    val DEFAULT_BLOCKED_PACKAGES =
        listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.android.chrome",
        )

    fun subjectsForClass(studentClass: String): List<String> {
      return when {
        studentClass.contains("JEE") -> listOf("Physics", "Chemistry", "Mathematics")
        studentClass.contains("NEET") -> listOf("Physics", "Chemistry", "Biology")
        studentClass.contains("1st") || studentClass.contains("5th") || studentClass.contains("10th") -> listOf("Mathematics", "Science", "English", "Social Science", "Hindi")
        else -> listOf("Physics", "Chemistry", "Mathematics", "Biology", "English")
      }
    }
  }
}

@IgnoreExtraProperties
data class UserSecurityPii(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val totpSecret: String = "",
    val shieldPasswordHash: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val durationMinutes: Long = 25L,
    val xpEarned: Long = 250L,
    val mode: String = MODE_POMODORO,
    val shieldEnabled: Boolean = true,
    val notes: String = "",
    val sessionDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val MODE_POMODORO = "pomodoro"
    const val MODE_DEEP_FOCUS = "deep_focus"
    const val MODE_MANUAL = "manual"
  }
}

@IgnoreExtraProperties
data class ExamReminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val chapter: String = "",
    val lectureName: String = "",
    val lectureUrl: String = "",
    val examDateMillis: Long = 0L,
    val targetHours: Long = 10L,
    val completedHours: Double = 0.0,
    val reminderMinutesBefore: Long = 120L,
    val priority: String = PRIORITY_HIGH,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  companion object {
    const val PRIORITY_HIGH = "high"
    const val PRIORITY_MEDIUM = "medium"
    const val PRIORITY_LOW = "low"
  }
}

@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Scholar",
    val avatarPreset: String = "owl",
    val totalXp: Long = 0L,
    val weeklyXp: Long = 0L,
    val weeklyStudyMinutes: Long = 0L,
    val streakDays: Long = 0L,
    val level: Long = 1L,
    val isPublic: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class DistractingAppCatalogItem(
    val packageName: String,
    val label: String,
    val category: String,
)

object DistractingAppCatalog {
  val knownApps =
      listOf(
          DistractingAppCatalogItem("com.instagram.android", "Instagram", "Social"),
          DistractingAppCatalogItem("com.google.android.youtube", "YouTube", "Video"),
          DistractingAppCatalogItem("com.android.chrome", "Google Chrome", "Browser"),
          DistractingAppCatalogItem("com.zhiliaoapp.musically", "TikTok / Shorts", "Short Video"),
          DistractingAppCatalogItem("com.snapchat.android", "Snapchat", "Messaging"),
          DistractingAppCatalogItem("com.whatsapp", "WhatsApp", "Messaging"),
          DistractingAppCatalogItem("org.telegram.messenger", "Telegram", "Messaging"),
          DistractingAppCatalogItem("com.twitter.android", "X (Twitter)", "Social"),
          DistractingAppCatalogItem("com.reddit.frontpage", "Reddit", "Forums"),
          DistractingAppCatalogItem("com.facebook.katana", "Facebook", "Social"),
          DistractingAppCatalogItem("in.mohalla.sharechat", "ShareChat", "Social"),
          DistractingAppCatalogItem("com.mxtech.videoplayer.ad", "MX Player", "Streaming"),
          DistractingAppCatalogItem("com.netflix.mediaclient", "Netflix", "Streaming"),
          DistractingAppCatalogItem("tv.twitch.android.app", "Twitch", "Streaming"),
          DistractingAppCatalogItem("com.dts.freefireth", "Free Fire", "Gaming"),
          DistractingAppCatalogItem("com.pubg.imnewd", "BGMI / PUBG", "Gaming"),
          DistractingAppCatalogItem("com.roblox.client", "Roblox", "Gaming"),
      )

  fun labelForPackage(packageName: String): String {
    return knownApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
  }
}

