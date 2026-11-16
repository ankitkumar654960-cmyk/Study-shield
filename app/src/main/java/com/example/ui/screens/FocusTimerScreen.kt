package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ShieldRuntimeState
import com.example.data.model.DistractingAppCatalog
import com.example.data.model.UserProfile
import com.example.service.StudyShieldAccessibilityService
import com.example.ui.components.CreateShieldPasswordDialog
import com.example.ui.viewmodel.PomodoroPhase
import com.example.ui.viewmodel.PomodoroTimerUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusTimerScreen(
    pomodoroState: PomodoroTimerUiState,
    shieldState: ShieldRuntimeState,
    profile: UserProfile?,
    onSelectPhase: (PomodoroPhase, Int?) -> Unit,
    onSelectSubject: (String) -> Unit,
    onToggleShieldLock: (Boolean) -> Unit,
    onStartOrResume: () -> Unit,
    onPauseWithoutShield: () -> Unit,
    onCompleteNow: () -> Unit,
    onRequestPasswordExit: () -> Unit,
    onSetShieldPassword: (String) -> Unit,
    onToggleBlockedPackage: (String) -> Unit,
    onAddCustomPackage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isAccessibilityEnabled =
      remember(shieldState.isShieldActive) {
        StudyShieldAccessibilityService.isServiceEnabled(context)
      }
  var showCreatePwDialog by remember { mutableStateOf(false) }
  var customPkgInput by remember { mutableStateOf("") }

  if (showCreatePwDialog) {
    CreateShieldPasswordDialog(
        onDismiss = { showCreatePwDialog = false },
        onPasswordCreated = { newPw ->
          onSetShieldPassword(newPw)
          onStartOrResume()
        },
    )
  }

  val subjects =
      listOf("Mathematics", "Computer Science", "Physics", "Chemistry", "Biology", "Literature")
  val blockedList = profile?.blockedPackages ?: shieldState.blockedPackages.toList()
  val hasPasswordConfigured =
      (profile?.hasShieldPassword == true) || shieldState.cachedPasswordHash.isNotBlank()

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Mode Chips
    item {
      Text("Pomodoro & Focus Shield", style = MaterialTheme.typography.headlineMedium)
      Spacer(modifier = Modifier.height(10.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
            selected = pomodoroState.phase == PomodoroPhase.FOCUS && pomodoroState.totalSeconds == 25 * 60,
            onClick = { onSelectPhase(PomodoroPhase.FOCUS, 25) },
            label = { Text("Pomodoro 25m") },
            enabled = !pomodoroState.isRunning,
        )
        FilterChip(
            selected = pomodoroState.phase == PomodoroPhase.FOCUS && pomodoroState.totalSeconds == 50 * 60,
            onClick = { onSelectPhase(PomodoroPhase.FOCUS, 50) },
            label = { Text("Deep 50m") },
            enabled = !pomodoroState.isRunning,
        )
        FilterChip(
            selected = pomodoroState.phase == PomodoroPhase.SHORT_BREAK,
            onClick = { onSelectPhase(PomodoroPhase.SHORT_BREAK, null) },
            label = { Text("Break 5m") },
            enabled = !pomodoroState.isRunning,
        )
      }
    }

    // Circular Countdown Ring Card
    item {
      val progress =
          if (pomodoroState.totalSeconds > 0) {
            pomodoroState.remainingSeconds.toFloat() / pomodoroState.totalSeconds.toFloat()
          } else 0f
      val mins = pomodoroState.remainingSeconds / 60
      val secs = pomodoroState.remainingSeconds % 60
      val timeText = String.format(Locale.US, "%02d:%02d", mins, secs)

      Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier =
              Modifier.fillMaxWidth()
                  .border(
                      1.dp,
                      if (shieldState.isShieldActive) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                      RoundedCornerShape(26.dp),
                  ),
      ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val ringPrimary = MaterialTheme.colorScheme.primary
            val ringSecondary = MaterialTheme.colorScheme.secondary
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
              val stroke = 14.dp.toPx()
              drawArc(
                  color = trackColor,
                  startAngle = 0f,
                  sweepAngle = 360f,
                  useCenter = false,
                  style = Stroke(width = stroke),
              )
              drawArc(
                  brush = Brush.sweepGradient(listOf(ringPrimary, ringSecondary, ringPrimary)),
                  startAngle = -90f,
                  sweepAngle = 360f * progress,
                  useCenter = false,
                  style = Stroke(width = stroke, cap = StrokeCap.Round),
              )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  text = timeText,
                  style = MaterialTheme.typography.displayLarge,
                  color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                  text = pomodoroState.subject,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
              )
              if (shieldState.isShieldActive) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                  Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHIELD LOCKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                  }
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          if (pomodoroState.lastSessionFeedback != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
              Text(
                  text = pomodoroState.lastSessionFeedback,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(10.dp),
              )
            }
          }

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (!pomodoroState.isRunning) {
              Button(
                  onClick = {
                    if (pomodoroState.phase == PomodoroPhase.FOCUS &&
                        pomodoroState.shieldLocked &&
                        !hasPasswordConfigured) {
                      showCreatePwDialog = true
                    } else {
                      onStartOrResume()
                    }
                  },
                  modifier =
                      Modifier.weight(1f).height(52.dp).testTag("focus_start_button"),
                  shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (pomodoroState.shieldLocked && pomodoroState.phase == PomodoroPhase.FOCUS)
                        "Start Shielded Focus"
                    else "Start Timer"
                )
              }
            } else if (shieldState.isShieldActive) {
              Button(
                  onClick = onRequestPasswordExit,
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.error
                      ),
                  modifier =
                      Modifier.weight(1f).height(52.dp).testTag("focus_exit_shield_button"),
                  shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exit Mode (Password)")
              }
              OutlinedButton(
                  onClick = onCompleteNow,
                  modifier = Modifier.height(52.dp).testTag("focus_finish_button"),
                  shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Finish & Log")
              }
            } else {
              Button(
                  onClick = onPauseWithoutShield,
                  modifier = Modifier.weight(1f).height(52.dp),
                  shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.Pause, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pause")
              }
              OutlinedButton(
                  onClick = onCompleteNow,
                  modifier = Modifier.height(52.dp),
                  shape = RoundedCornerShape(16.dp),
              ) {
                Icon(Icons.Filled.Stop, contentDescription = "Finish & Save")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log")
              }
            }
          }
        }
      }
    }

    // Subject Selector
    item {
      Text("Study Subject", style = MaterialTheme.typography.titleMedium)
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        subjects.forEach { subj ->
          FilterChip(
              selected = pomodoroState.subject == subj,
              onClick = { onSelectSubject(subj) },
              label = { Text(subj) },
              enabled = !pomodoroState.isRunning,
          )
        }
      }
    }

    // Distraction Shield Configuration Card
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.img_shield_active),
                contentDescription = "Distraction Shield emblem",
                modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("App Distraction Blocker", style = MaterialTheme.typography.titleLarge)
              Text(
                  text = "All system apps stay working. Blocked apps redirect back here.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
                checked = pomodoroState.shieldLocked,
                onCheckedChange = onToggleShieldLock,
                enabled = !shieldState.isShieldActive,
            )
          }

          Surface(
              color =
                  if (isAccessibilityEnabled) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector =
                        if (isAccessibilityEnabled) Icons.Filled.CheckCircle
                        else Icons.Filled.Warning,
                    contentDescription = null,
                    tint =
                        if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (isAccessibilityEnabled)
                            "Android Accessibility Shield is ACTIVE"
                        else "Enable StudyShield Accessibility Service to block Instagram & YouTube system-wide",
                    style = MaterialTheme.typography.bodyMedium,
                )
              }
              if (!isAccessibilityEnabled) {
                OutlinedButton(
                    onClick = {
                      context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.testTag("enable_accessibility_button"),
                ) {
                  Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Enable")
                }
              }
            }
          }

          Text(
              text = "Blocked During Study Session",
              style = MaterialTheme.typography.titleMedium,
          )
          FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            DistractingAppCatalog.knownApps.forEach { app ->
              val isBlocked = app.packageName in blockedList
              FilterChip(
                  selected = isBlocked,
                  onClick = { if (!shieldState.isShieldActive) onToggleBlockedPackage(app.packageName) },
                  label = { Text(app.label) },
                  leadingIcon =
                      if (isBlocked) {
                        {
                          Icon(
                              Icons.Filled.Shield,
                              contentDescription = null,
                              modifier = Modifier.size(16.dp),
                          )
                        }
                      } else null,
              )
            }
          }

          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
                value = customPkgInput,
                onValueChange = { customPkgInput = it },
                label = { Text("Add Custom Package ID") },
                placeholder = { Text("com.example.distraction") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                  onAddCustomPackage(customPkgInput)
                  customPkgInput = ""
                },
                enabled = !shieldState.isShieldActive,
            ) {
              Icon(Icons.Filled.Add, contentDescription = "Add blocked package")
            }
          }
        }
      }
    }
  }
}
