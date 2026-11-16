package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.ExamReminder
import com.example.notifications.StudyNotificationHelper
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ExamsScreen(
    examsState: UiState<List<ExamReminder>>,
    onCreateExam: (
        title: String,
        subject: String,
        examDateMillis: Long,
        targetHours: Long,
        reminderMinutesBefore: Long,
        priority: String,
    ) -> Unit,
    onAddStudyHour: (ExamReminder) -> Unit,
    onToggleCompleted: (ExamReminder) -> Unit,
    onDeleteExam: (String) -> Unit,
    onTriggerNotificationPreview: (ExamReminder) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var hasNotifPermission by
      remember { mutableStateOf(StudyNotificationHelper.hasNotificationPermission(context)) }
  val notifLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifPermission = granted
      }

  var showAddDialog by remember { mutableStateOf(false) }

  if (showAddDialog) {
    AddExamDialog(
        onDismiss = { showAddDialog = false },
        onSave = { title, subject, daysOut, targetHours, reminderMins, priority ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          val examMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysOut.toLong())
          onCreateExam(title, subject, examMillis, targetHours, reminderMins, priority)
          showAddDialog = false
        },
    )
  }

  Box(modifier = modifier.fillMaxSize()) {
    val exams = (examsState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text("Exam Deadlines & Alerts", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Local notifications remind you before every exam.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        item {
          Card(
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.secondaryContainer
                  ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Enable notifications to receive exam countdown alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
              }
              OutlinedButton(
                  onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
              ) {
                Text("Allow")
              }
            }
          }
        }
      }

      when (examsState) {
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
                  text = examsState.message,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          if (exams.isEmpty()) {
            item {
              Card(
                  shape = RoundedCornerShape(22.dp),
                  colors =
                      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  Icon(
                      imageVector = Icons.Filled.Event,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(40.dp),
                  )
                  Spacer(modifier = Modifier.height(10.dp))
                  Text("No exams scheduled yet", style = MaterialTheme.typography.titleLarge)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "Tap + Add Exam to set target study hours and local reminders.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          } else {
            items(exams, key = { it.id }) { exam ->
              ExamCard(
                  exam = exam,
                  onAddHour = { onAddStudyHour(exam) },
                  onToggleCompleted = { onToggleCompleted(exam) },
                  onDelete = { onDeleteExam(exam.id) },
                  onPreviewAlert = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                      notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      onTriggerNotificationPreview(exam)
                    }
                  },
              )
            }
          }
        }
      }
    }

    FloatingActionButton(
        onClick = { showAddDialog = true },
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("add_exam_fab"),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
      Icon(Icons.Filled.Add, contentDescription = "Add Exam Reminder")
    }
  }
}

@Composable
private fun ExamCard(
    exam: ExamReminder,
    onAddHour: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onPreviewAlert: () -> Unit,
) {
  val dateStr =
      remember(exam.examDateMillis) {
        SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(exam.examDateMillis))
      }
  val daysLeft =
      TimeUnit.MILLISECONDS.toDays((exam.examDateMillis - System.currentTimeMillis()).coerceAtLeast(0L))
  val progress =
      (exam.completedHours.toFloat() / exam.targetHours.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

  Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier =
          Modifier.fillMaxWidth()
              .border(
                  1.dp,
                  MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                  RoundedCornerShape(20.dp),
              ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          IconButton(onClick = onToggleCompleted) {
            Icon(
                imageVector =
                    if (exam.completed) Icons.Filled.CheckCircle
                    else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Toggle Exam Completed",
                tint =
                    if (exam.completed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Column {
            Text(
                text = exam.title,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        textDecoration =
                            if (exam.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
            )
            Text(
                text = "${exam.subject} • $dateStr (${daysLeft}d left)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Surface(
            color =
                when (exam.priority) {
                  ExamReminder.PRIORITY_HIGH -> MaterialTheme.colorScheme.errorContainer
                  ExamReminder.PRIORITY_MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
                  else -> MaterialTheme.colorScheme.tertiaryContainer
                },
            shape = RoundedCornerShape(8.dp),
        ) {
          Text(
              text = exam.priority.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text =
                  "Prep: ${String.format(Locale.US, "%.1f", exam.completedHours)}h / ${exam.targetHours}h",
              style = MaterialTheme.typography.labelMedium,
          )
          Text(
              text = "Alert ${exam.reminderMinutesBefore}m prior",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(onClick = onAddHour, enabled = !exam.completed) {
          Text("+1h Studied")
        }
        Row {
          IconButton(onClick = onPreviewAlert) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = "Test Exam Reminder Notification",
                tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete Exam",
                tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AddExamDialog(
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        subject: String,
        daysOut: Int,
        targetHours: Long,
        reminderMins: Long,
        priority: String,
    ) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var subject by remember { mutableStateOf("") }
  var daysOutSlider by remember { mutableFloatStateOf(7f) }
  var targetHoursSlider by remember { mutableFloatStateOf(12f) }
  var reminderMinutes by remember { mutableLongStateOf(120L) }
  var priority by remember { mutableStateOf(ExamReminder.PRIORITY_HIGH) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("New Exam Reminder") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
              value = title,
              onValueChange = { title = it },
              label = { Text("Exam Title") },
              placeholder = { Text("e.g., Linear Algebra Midterm") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("exam_title_input"),
          )
          OutlinedTextField(
              value = subject,
              onValueChange = { subject = it },
              label = { Text("Subject") },
              placeholder = { Text("e.g., Mathematics") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("exam_subject_input"),
          )
          Column {
            Text("Exam in ${daysOutSlider.toInt()} days", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = daysOutSlider,
                onValueChange = { daysOutSlider = it },
                valueRange = 1f..60f,
            )
          }
          Column {
            Text(
                "Target Prep: ${targetHoursSlider.toInt()} hours",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = targetHoursSlider,
                onValueChange = { targetHoursSlider = it },
                valueRange = 2f..60f,
            )
          }
          Text("Priority", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                    ExamReminder.PRIORITY_HIGH to "High",
                    ExamReminder.PRIORITY_MEDIUM to "Medium",
                    ExamReminder.PRIORITY_LOW to "Low",
                )
                .forEach { (key, label) ->
                  FilterChip(
                      selected = priority == key,
                      onClick = { priority = key },
                      label = { Text(label) },
                  )
                }
          }
          Text("Reminder Lead Time", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30L to "30m", 120L to "2h", 1440L to "1d").forEach { (mins, label) ->
              FilterChip(
                  selected = reminderMinutes == mins,
                  onClick = { reminderMinutes = mins },
                  label = { Text(label) },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onSave(
                  title.ifBlank { "Upcoming Exam" },
                  subject.ifBlank { "General" },
                  daysOutSlider.toInt(),
                  targetHoursSlider.toLong(),
                  reminderMinutes,
                  priority,
              )
            },
            modifier = Modifier.testTag("save_exam_button"),
        ) {
          Text("Schedule")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
