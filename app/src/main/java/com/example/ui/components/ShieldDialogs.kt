package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.DistractingAppCatalog

@Composable
fun ShieldExitPasswordDialog(
    onDismiss: () -> Unit,
    onVerifyPassword: (String) -> Boolean,
) {
  var password by remember { mutableStateOf("") }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  AlertDialog(
      onDismissRequest = onDismiss,
      icon = {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text("Password-Protected Exit Mode") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
              text =
                  "Study Shield is blocking distracting apps. Enter your Exit Password to end this study session and restore Instagram & YouTube access.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
              value = password,
              onValueChange = {
                password = it
                errorMsg = null
              },
              label = { Text("Shield Exit Password") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              isError = errorMsg != null,
              modifier = Modifier.fillMaxWidth().testTag("shield_exit_password_input"),
          )
          if (errorMsg != null) {
            Text(
                text = errorMsg ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              if (onVerifyPassword(password)) {
                onDismiss()
              } else {
                errorMsg = "Incorrect exit password. Stay focused!"
              }
            },
            colors =
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("confirm_shield_exit_button"),
        ) {
          Text("Unlock & Exit")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Keep Studying") }
      },
  )
}

@Composable
fun CreateShieldPasswordDialog(
    onDismiss: () -> Unit,
    onPasswordCreated: (String) -> Unit,
) {
  var pw by remember { mutableStateOf("") }
  var confirmPw by remember { mutableStateOf("") }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  AlertDialog(
      onDismissRequest = onDismiss,
      icon = {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
      },
      title = { Text("Set Shield Exit Password") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
              text =
                  "To prevent easy bypassing of Instagram & YouTube blocking, set an Exit Password (at least 4 characters) before locking your session.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
              value = pw,
              onValueChange = {
                pw = it
                errorMsg = null
              },
              label = { Text("New Exit Password") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("new_shield_password_input"),
          )
          OutlinedTextField(
              value = confirmPw,
              onValueChange = {
                confirmPw = it
                errorMsg = null
              },
              label = { Text("Confirm Password") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("confirm_new_shield_password_input"),
          )
          if (errorMsg != null) {
            Text(
                text = errorMsg ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              when {
                pw.length < 4 -> errorMsg = "Password must be at least 4 characters."
                pw != confirmPw -> errorMsg = "Passwords do not match."
                else -> {
                  onPasswordCreated(pw)
                  onDismiss()
                }
              }
            },
            modifier = Modifier.testTag("save_new_shield_password_button"),
        ) {
          Text("Save & Lock")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      },
  )
}

@Composable
fun BlockedAppInterceptDialog(
    blockedPackage: String,
    activeSubject: String,
    onDismissToStudy: () -> Unit,
    onRequestPasswordExit: () -> Unit,
) {
  val appLabel = DistractingAppCatalog.labelForPackage(blockedPackage)
  AlertDialog(
      onDismissRequest = onDismissToStudy,
      icon = {
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text("$appLabel Blocked by StudyShield") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              text =
                  "$appLabel is restricted during your active $activeSubject study session. All essential system apps remain available.",
              style = MaterialTheme.typography.bodyMedium,
          )
          Text(
              text = "To restore $appLabel immediately, use Password-Protected Exit Mode.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(onClick = onDismissToStudy) { Text("Back to Study") }
      },
      dismissButton = {
        TextButton(onClick = onRequestPasswordExit) { Text("Exit with Password") }
      },
  )
}

@Composable
fun QuickLogStudyDialog(
    defaultSubject: String,
    onDismiss: () -> Unit,
    onLogSession: (subject: String, minutes: Long, shieldEnabled: Boolean, notes: String) -> Unit,
) {
  var subject by remember { mutableStateOf(defaultSubject) }
  var durationSlider by remember { mutableFloatStateOf(45f) }
  var shieldUsed by remember { mutableStateOf(true) }
  var notes by remember { mutableStateOf("") }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Log Study Session") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
              value = subject,
              onValueChange = { subject = it },
              label = { Text("Subject / Course") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("quick_log_subject_input"),
          )
          Column {
            Text(
                text = "Duration: ${durationSlider.toInt()} minutes (+${durationSlider.toInt() * 10} XP)",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = durationSlider,
                onValueChange = { durationSlider = it },
                valueRange = 10f..180f,
                steps = 16,
            )
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("Distraction Shield Used", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = shieldUsed, onCheckedChange = { shieldUsed = it })
          }
          OutlinedTextField(
              value = notes,
              onValueChange = { notes = it },
              label = { Text("Topic Notes (optional)") },
              maxLines = 2,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onLogSession(subject, durationSlider.toLong(), shieldUsed, notes)
              onDismiss()
            },
            modifier = Modifier.testTag("confirm_quick_log_button"),
        ) {
          Text("Save & Earn XP")
        }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
      },
  )
}
