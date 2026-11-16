package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.LeaderboardEntry
import com.example.data.model.StudySession
import com.example.ui.components.SubjectDonutChart
import com.example.ui.components.TrendAreaChart
import com.example.ui.components.WeeklyBarChart
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WeeklyStudyReport

@Composable
fun AnalyticsScreen(
    currentUserId: String,
    sessionsState: UiState<List<StudySession>>,
    leaderboardState: UiState<List<LeaderboardEntry>>,
    weeklyReport: WeeklyStudyReport,
    onReportCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  var selectedSegment by remember { mutableIntStateOf(0) } // 0 = Analytics & Weekly Report, 1 = Leaderboard

  val sessions = (sessionsState as? UiState.Success)?.data ?: emptyList()
  val allTimeMinutes = sessions.sumOf { it.durationMinutes }
  val shieldRatio =
      if (sessions.isNotEmpty()) (sessions.count { it.shieldEnabled } * 100) / sessions.size
      else 100

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text("Analytics & Leaderboard", style = MaterialTheme.typography.headlineMedium)
      Spacer(modifier = Modifier.height(10.dp))
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedSegment == 0,
            onClick = { selectedSegment = 0 },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.testTag("analytics_tab_reports"),
        ) {
          Text("Weekly Report & Charts")
        }
        SegmentedButton(
            selected = selectedSegment == 1,
            onClick = { selectedSegment = 1 },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.testTag("analytics_tab_leaderboard"),
        ) {
          Text("XP Leaderboard")
        }
      }
    }

    if (selectedSegment == 0) {
      // KPI Cards Row
      item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiStatCard(
              title = "All-Time Study",
              value = "${allTimeMinutes / 60}h ${allTimeMinutes % 60}m",
              subtitle = "${sessions.size} total sessions",
              modifier = Modifier.weight(1f),
          )
          KpiStatCard(
              title = "Shield Focus Rate",
              value = "$shieldRatio%",
              subtitle = "${weeklyReport.averageDailyMinutes}m/day avg",
              modifier = Modifier.weight(1f),
          )
        }
      }

      // Weekly Study Duration Report Generator Card
      item {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier =
                Modifier.fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(22.dp),
                    ),
        ) {
          Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text("Weekly Study Duration Report", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Generated live from synced sessions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Surface(
                  color =
                      if (weeklyReport.deltaPercentage >= 0)
                          MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.errorContainer,
                  shape = RoundedCornerShape(10.dp),
              ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                      Icons.Filled.TrendingUp,
                      contentDescription = null,
                      modifier = Modifier.size(14.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                      text =
                          if (weeklyReport.deltaPercentage >= 0) "+${weeklyReport.deltaPercentage}%"
                          else "${weeklyReport.deltaPercentage}%",
                      style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              ReportMetric("This Week", "${weeklyReport.totalMinutesThisWeek} min")
              ReportMetric("Top Subject", weeklyReport.topSubject)
              ReportMetric("XP Gained", "+${weeklyReport.xpEarnedThisWeek}")
            }

            WeeklyBarChart(points = weeklyReport.dailyPoints)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                  onClick = {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(Intent.EXTRA_SUBJECT, "StudyShield Weekly Report")
                          putExtra(Intent.EXTRA_TEXT, weeklyReport.shareableText)
                        }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share Weekly Study Report")
                    )
                  },
                  modifier = Modifier.weight(1f).testTag("share_weekly_report_button"),
                  shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Report")
              }
              OutlinedButton(
                  onClick = {
                    clipboardManager.setText(AnnotatedString(weeklyReport.shareableText))
                    onReportCopied()
                  },
                  modifier = Modifier.weight(1f).testTag("copy_weekly_report_button"),
                  shape = RoundedCornerShape(14.dp),
              ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Summary")
              }
            }
          }
        }
      }

      // Trend Area Chart
      item {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text("Consistency Curve", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Daily study duration momentum over the last 7 days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TrendAreaChart(points = weeklyReport.dailyPoints)
          }
        }
      }

      // Subject Distribution Donut
      item {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Text("Subject Breakdown", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            SubjectDonutChart(breakdown = weeklyReport.subjectBreakdown)
          }
        }
      }
    } else {
      // XP Leaderboard Segment
      when (leaderboardState) {
        is UiState.Loading -> {
          item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        is UiState.Error -> {
          item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                  text = leaderboardState.message,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          val entries = leaderboardState.data
          if (entries.isEmpty()) {
            item {
              Card(
                  shape = RoundedCornerShape(20.dp),
                  colors =
                      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                      Icons.Filled.EmojiEvents,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(38.dp),
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text("Leaderboard is waiting for scholars!", style = MaterialTheme.typography.titleMedium)
                  Text(
                      text = "Complete a focus session to claim #1 on this week's board.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            itemsIndexed(entries, key = { _, item -> item.userId }) { index, entry ->
              val isSelf = entry.userId == currentUserId
              Card(
                  shape = RoundedCornerShape(18.dp),
                  colors =
                      CardDefaults.cardColors(
                          containerColor =
                              if (isSelf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                              else MaterialTheme.colorScheme.surface
                      ),
                  modifier =
                      Modifier.fillMaxWidth()
                          .border(
                              1.dp,
                              if (isSelf) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                              RoundedCornerShape(18.dp),
                          ),
              ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier =
                            Modifier.size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    when (index) {
                                      0 -> MaterialTheme.colorScheme.secondary
                                      1 -> MaterialTheme.colorScheme.tertiary
                                      2 -> MaterialTheme.colorScheme.primary
                                      else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                      Text(
                          text = "#${index + 1}",
                          style = MaterialTheme.typography.labelLarge,
                          color =
                              if (index < 3) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text(
                          text = if (isSelf) "${entry.displayName} (You)" else entry.displayName,
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                      )
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lv.${entry.level} • ${entry.weeklyStudyMinutes}m studied",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${entry.streakDays}d",
                            style = MaterialTheme.typography.labelSmall,
                        )
                      }
                    }
                  }
                  Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(10.dp),
                  ) {
                    Text(
                        text = "${entry.weeklyXp} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
  Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
          text = value,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.primary,
      )
      Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReportMetric(label: String, value: String) {
  Column {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = value, style = MaterialTheme.typography.titleMedium)
  }
}
