package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Language
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
import com.example.ui.components.InAppStudyBrowserDialog
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
        chapter: String,
        lectureName: String,
        lectureUrl: String,
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
  var activeBrowserUrl by remember { mutableStateOf<String?>(null) }
  var activeBrowserTitle by remember { mutableStateOf("") }

  if (activeBrowserUrl != null) {
    InAppStudyBrowserDialog(
        url = activeBrowserUrl ?: "",
        title = activeBrowserTitle,
        onDismiss = { activeBrowserUrl = null },
    )
  }

  if (showAddDialog) {
    AddTaskDialog(
        onDismiss = { showAddDialog = false },
        onSave = { title, subject, chapter, lectureName, lectureUrl, daysOut, targetHours, reminderMins, priority ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          val examMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysOut.toLong())
          onCreateExam(title, subject, chapter, lectureName, lectureUrl, examMillis, targetHours, reminderMins, priority)
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
            Text("Study Tasks & Lectures", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Open YouTube & Chrome links directly inside StudyShield safely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                  Text("No study tasks added yet", style = MaterialTheme.typography.titleLarge)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "Tap + Add task to attach lecture URLs and study notes.",
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
                  onOpenUrl = { url, title ->
                    activeBrowserUrl = url
                    activeBrowserTitle = title
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
      Icon(Icons.Filled.Add, contentDescription = "Add Task")
    }
  }
}

@Composable
private fun ExamCard(
    exam: ExamReminder,
    onAddHour: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String, String) -> Unit,
) {
  val progress =
      (exam.completedHours.toFloat() / exam.targetHours.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

  val subtitleFormat = buildString {
    if (exam.subject.isNotBlank()) append(exam.subject)
    if (exam.chapter.isNotBlank()) {
      if (isNotEmpty()) append(" · ")
      append(exam.chapter)
    }
    if (exam.lectureName.isNotBlank()) {
      if (isNotEmpty()) append(" · ")
      append(exam.lectureName)
    }
    if (isEmpty()) {
      append("General Study")
    }
  }

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
                contentDescription = "Toggle Task Completed",
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
                text = subtitleFormat,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
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
          if (exam.lectureUrl.isNotBlank()) {
            TextButton(
                onClick = { onOpenUrl(exam.lectureUrl, exam.title) },
                contentPadding = PaddingValues(0.dp),
            ) {
              Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open In-App Link")
            }
          }
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
        IconButton(onClick = onDelete) {
          Icon(
              Icons.Filled.Delete,
              contentDescription = "Delete Task",
              tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        subject: String,
        chapter: String,
        lectureName: String,
        lectureUrl: String,
        daysOut: Int,
        targetHours: Long,
        reminderMins: Long,
        priority: String,
    ) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var selectedSubject by remember { mutableStateOf("Physics") }
  var chapter by remember { mutableStateOf("") }
  var lectureName by remember { mutableStateOf("") }
  var lectureUrl by remember { mutableStateOf("") }
  var targetHoursSlider by remember { mutableFloatStateOf(10f) }

  val subjects = listOf("Physics", "Mathematics", "Chemistry", "Biology", "Computer Science", "Literature")

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Add task + optional lecture link") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
              value = title,
              onValueChange = { title = it },
              label = { Text("Task title") },
              placeholder = { Text("e.g. Watch Physics lecture 04") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("task_title_input"),
          )

          Text("Subject", style = MaterialTheme.typography.labelLarge)
          FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            subjects.forEach { subj ->
              FilterChip(
                  selected = selectedSubject == subj,
                  onClick = { selectedSubject = subj },
                  label = { Text(subj) },
              )
            }
          }

          OutlinedTextField(
              value = chapter,
              onValueChange = { chapter = it },
              label = { Text("Chapter") },
              placeholder = { Text("e.g. Motion in a Straight Line") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
              value = lectureName,
              onValueChange = { lectureName = it },
              label = { Text("Lecture number / name") },
              placeholder = { Text("e.g. Lecture 04") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          OutlinedTextField(
              value = lectureUrl,
              onValueChange = { lectureUrl = it },
              label = { Text("Direct lecture URL (optional)") },
              placeholder = { Text("https://youtube.com/... or https://...") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )

          Column {
            Text(
                "Target Prep: ${targetHoursSlider.toInt()} hours",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = targetHoursSlider,
                onValueChange = { targetHoursSlider = it },
                valueRange = 1f..50f,
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onSave(
                  title.ifBlank { "Watch Lecture" },
                  selectedSubject,
                  chapter.ifBlank { "General Chapter" },
                  lectureName.ifBlank { "Lecture 01" },
                  lectureUrl.trim(),
                  14,
                  targetHoursSlider.toLong(),
                  120L,
                  ExamReminder.PRIORITY_HIGH,
              )
            },
            modifier = Modifier.testTag("save_exam_button"),
        ) {
          Text("+ Add task")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
