package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ShieldRuntimeState
import com.example.data.model.ExamReminder
import com.example.data.model.StudySession
import com.example.data.model.UserProfile
import com.example.ui.components.QuickLogStudyDialog
import com.example.ui.components.WeeklyBarChart
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WeeklyStudyReport
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    sessionsState: UiState<List<StudySession>>,
    examsState: UiState<List<ExamReminder>>,
    shieldState: ShieldRuntimeState,
    weeklyReport: WeeklyStudyReport,
    onNavigateToFocus: () -> Unit,
    onNavigateToExams: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onQuickLogSession: (subject: String, minutes: Long, shieldUsed: Boolean, notes: String) -> Unit,
    onOpenShieldUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var showQuickLog by remember { mutableStateOf(false) }

  val profile = (profileState as? UiState.Success)?.data
  val sessions = (sessionsState as? UiState.Success)?.data ?: emptyList()
  val exams = (examsState as? UiState.Success)?.data ?: emptyList()

  val level = (((profile?.totalXp ?: 0L) / 500L) + 1L).coerceAtLeast(1L)
  val goalMinutes = (profile?.weeklyGoalMinutes ?: 600L).coerceAtLeast(30L)
  val progressFraction =
      (weeklyReport.totalMinutesThisWeek.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)

  if (showQuickLog) {
    QuickLogStudyDialog(
        defaultSubject = sessions.firstOrNull()?.subject ?: "Mathematics",
        onDismiss = { showQuickLog = false },
        onLogSession = onQuickLogSession,
    )
  }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Header Card with Level, Streak, and Cloud Sync Pill
    item {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = "Welcome back, ${profile?.displayName ?: "Scholar"}",
              style = MaterialTheme.typography.headlineMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
              Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Lv.$level • ${profile?.totalXp ?: 0L} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
              Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${profile?.streakDays ?: 1L}d Streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
                imageVector = Icons.Filled.CloudDone,
                contentDescription = "Cloud Synced",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Synced", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    // Active Shield Banner OR Quick Focus Launch Card
    item {
      if (shieldState.isShieldActive) {
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                  imageVector = Icons.Filled.Shield,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(26.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Distraction Shield ACTIVE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text =
                        "Studying ${shieldState.activeSubject} • Instagram & ${shieldState.blockedPackages.size} apps blocked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
              }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                  onClick = onNavigateToFocus,
                  modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Timer")
              }
              FilledTonalButton(
                  onClick = onOpenShieldUnlock,
                  modifier = Modifier.weight(1f).testTag("dashboard_unlock_shield_button"),
              ) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exit Mode")
              }
            }
          }
        }
      } else {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier =
                Modifier.fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(24.dp),
                    ),
        ) {
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .background(
                          Brush.horizontalGradient(
                              listOf(
                                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                  MaterialTheme.colorScheme.surface,
                              )
                          )
                      )
                      .padding(18.dp)
          ) {
            Column {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Weekly Study Goal", style = MaterialTheme.typography.titleLarge)
                  Text(
                      text =
                          "${weeklyReport.totalMinutesThisWeek / 60}h ${weeklyReport.totalMinutesThisWeek % 60}m of ${goalMinutes / 60}h target",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(modifier = Modifier.height(10.dp))
              LinearProgressIndicator(
                  progress = { progressFraction },
                  modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)),
              )
              Spacer(modifier = Modifier.height(14.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onNavigateToFocus,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("dashboard_start_focus_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Start Pomodoro")
                }
                FilledTonalButton(
                    onClick = { showQuickLog = true },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("dashboard_quick_log_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Log Study")
                }
              }
            }
          }
        }
      }
    }

    // 7-Day Progress Chart
    item {
      Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier =
              Modifier.fillMaxWidth()
                  .border(
                      1.dp,
                      MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                      RoundedCornerShape(22.dp),
                  ),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("7-Day Study Progress", style = MaterialTheme.typography.titleLarge)
              Text(
                  text =
                      "${weeklyReport.sessionsCount} sessions • +${weeklyReport.xpEarnedThisWeek} XP this week",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = onNavigateToAnalytics) { Text("Full Report") }
          }
          Spacer(modifier = Modifier.height(12.dp))
          WeeklyBarChart(
              points = weeklyReport.dailyPoints,
              dailyGoalMinutes = (goalMinutes / 7L).coerceAtLeast(30L),
          )
        }
      }
    }

    // Upcoming Exam Countdowns Strip
    item {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Upcoming Exams", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onNavigateToExams) { Text("Manage All") }
      }

      val activeExams = exams.filter { !it.completed }.take(5)
      if (activeExams.isEmpty()) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                  imageVector = Icons.Filled.CalendarMonth,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                  text = "No upcoming exams yet. Add deadlines to schedule local reminders.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = onNavigateToExams) { Text("Add") }
          }
        }
      } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          items(activeExams, key = { it.id }) { exam ->
            val daysLeft =
                TimeUnit.MILLISECONDS.toDays(
                    (exam.examDateMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                )
            Card(
                onClick = onNavigateToExams,
                shape = RoundedCornerShape(18.dp),
                colors =
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier.width(210.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(18.dp),
                        ),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Surface(
                    color =
                        if (daysLeft <= 3) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                  Text(
                      text = if (daysLeft == 0L) "Today" else "${daysLeft}d left",
                      style = MaterialTheme.typography.labelMedium,
                      color =
                          if (daysLeft <= 3) MaterialTheme.colorScheme.onErrorContainer
                          else MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = exam.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exam.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                      (exam.completedHours.toFloat() / exam.targetHours.coerceAtLeast(1L).toFloat())
                          .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                )
              }
            }
          }
        }
      }
    }

    // Recent Study Sessions
    item {
      Text("Recent Focus Logs", style = MaterialTheme.typography.titleLarge)
    }

    if (sessionsState is UiState.Loading) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    } else if (sessions.isEmpty()) {
      item {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
              modifier = Modifier.padding(20.dp).fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("No study sessions logged yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Start a Pomodoro timer or tap Log Study to earn your first XP!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(sessions.take(6), key = { it.id }) { session ->
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                  modifier =
                      Modifier.size(40.dp)
                          .clip(CircleShape)
                          .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center,
              ) {
                Icon(
                    imageVector =
                        if (session.shieldEnabled) Icons.Filled.Shield else Icons.Filled.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                    text = session.subject,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        "${session.durationMinutes} min • ${session.sessionDate}" +
                            if (session.shieldEnabled) " • Shielded" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                  text = "+${session.xpEarned} XP",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
    }
  }
}
