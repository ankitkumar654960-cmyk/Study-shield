package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.ThemePreference
import com.example.data.model.UserProfile
import com.example.data.model.UserSecurityPii
import com.example.ui.viewmodel.UiState
import com.example.util.SecurityUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profileState: UiState<UserProfile?>,
    securityPiiState: UiState<UserSecurityPii?>,
    themePreference: ThemePreference,
    userEmailFallback: String,
    onChangeTheme: (ThemePreference) -> Unit,
    onSavePreferences: (
        displayName: String,
        avatarPreset: String,
        weeklyGoalMinutes: Long,
        pomodoroFocusMinutes: Long,
        pomodoroBreakMinutes: Long,
        blockedPackages: List<String>,
        shareOnLeaderboard: Boolean,
    ) -> Unit,
    onSaveSecurity: (
        email: String,
        phoneNumber: String,
        twoFactorEnabled: Boolean,
        totpSecret: String,
        newShieldPassword: String?,
    ) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val profile = (profileState as? UiState.Success)?.data
  val pii = (securityPiiState as? UiState.Success)?.data

  var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "Scholar") }
  var avatarPreset by remember(profile?.avatarPreset) { mutableStateOf(profile?.avatarPreset ?: "owl") }
  var weeklyGoalHours by
      remember(profile?.weeklyGoalMinutes) {
        mutableFloatStateOf(((profile?.weeklyGoalMinutes ?: 600L) / 60f).coerceIn(1f, 60f))
      }
  var focusMinutes by
      remember(profile?.pomodoroFocusMinutes) {
        mutableFloatStateOf((profile?.pomodoroFocusMinutes ?: 25L).toFloat().coerceIn(10f, 90f))
      }
  var breakMinutes by
      remember(profile?.pomodoroBreakMinutes) {
        mutableFloatStateOf((profile?.pomodoroBreakMinutes ?: 5L).toFloat().coerceIn(3f, 25f))
      }
  var shareLeaderboard by
      remember(profile?.shareOnLeaderboard) { mutableStateOf(profile?.shareOnLeaderboard ?: true) }

  var contactEmail by remember(pii?.email) { mutableStateOf(pii?.email?.ifBlank { userEmailFallback } ?: userEmailFallback) }
  var phoneNumber by remember(pii?.phoneNumber) { mutableStateOf(pii?.phoneNumber ?: "") }
  var twoFactorEnabled by
      remember(profile?.twoFactorEnabled) { mutableStateOf(profile?.twoFactorEnabled ?: false) }
  var totpSecret by
      remember(pii?.totpSecret) {
        mutableStateOf(pii?.totpSecret?.ifBlank { SecurityUtils.generateTotpSecret() } ?: SecurityUtils.generateTotpSecret())
      }
  var newShieldPassword by remember { mutableStateOf("") }

  var liveOtp by remember(totpSecret) { mutableStateOf(SecurityUtils.generateTotpCode(totpSecret)) }
  var otpCountdown by remember { mutableIntStateOf(SecurityUtils.secondsRemainingInTotpWindow()) }

  LaunchedEffect(totpSecret, twoFactorEnabled) {
    if (twoFactorEnabled) {
      while (true) {
        liveOtp = SecurityUtils.generateTotpCode(totpSecret)
        otpCountdown = SecurityUtils.secondsRemainingInTotpWindow()
        delay(1000L)
      }
    }
  }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Profile & Security", style = MaterialTheme.typography.headlineMedium)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(999.dp),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Multi-Device Sync", style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }

    // Appearance / Dark Mode Toggle
    item {
      Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Theme & Dark Mode", style = MaterialTheme.typography.titleMedium)
          }
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemePreference.entries.forEachIndexed { idx, pref ->
              SegmentedButton(
                  selected = themePreference == pref,
                  onClick = { onChangeTheme(pref) },
                  shape = SegmentedButtonDefaults.itemShape(index = idx, count = ThemePreference.entries.size),
                  modifier = Modifier.testTag("theme_option_${pref.key}"),
              ) {
                Text(
                    text =
                        when (pref) {
                          ThemePreference.SYSTEM -> "System"
                          ThemePreference.DARK -> "Dark"
                          ThemePreference.LIGHT -> "Light"
                        }
                )
              }
            }
          }
        }
      }
    }

    // Profile & Study Goals Card
    item {
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scholar Profile & Goals", style = MaterialTheme.typography.titleLarge)
          }

          OutlinedTextField(
              value = displayName,
              onValueChange = { displayName = it },
              label = { Text("Display Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
          )

          Text("Scholar Badge", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("owl" to "Owl", "fox" to "Fox", "phoenix" to "Phoenix", "compass" to "Compass")
                .forEach { (id, label) ->
                  FilterChip(
                      selected = avatarPreset == id,
                      onClick = { avatarPreset = id },
                      label = { Text(label) },
                  )
                }
          }

          Column {
            Text(
                text = "Weekly Study Goal: ${weeklyGoalHours.toInt()} hours",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = weeklyGoalHours,
                onValueChange = { weeklyGoalHours = it },
                valueRange = 2f..60f,
            )
          }

          Column {
            Text(
                text = "Default Pomodoro: ${focusMinutes.toInt()}m focus / ${breakMinutes.toInt()}m break",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = focusMinutes,
                onValueChange = { focusMinutes = it },
                valueRange = 15f..90f,
            )
          }

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Show on Public XP Leaderboard", style = MaterialTheme.typography.titleMedium)
              Text(
                  text = "Shares only display name, XP, and streak (never email/phone)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = shareLeaderboard, onCheckedChange = { shareLeaderboard = it })
          }

          Button(
              onClick = {
                onSavePreferences(
                    displayName,
                    avatarPreset,
                    (weeklyGoalHours.toLong() * 60L),
                    focusMinutes.toLong(),
                    breakMinutes.toLong(),
                    profile?.blockedPackages ?: UserProfile.DEFAULT_BLOCKED_PACKAGES,
                    shareLeaderboard,
                )
              },
              modifier = Modifier.fillMaxWidth().testTag("save_profile_button"),
              shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Profile Preferences")
          }
        }
      }
    }

    // Contact Info, 2FA & Shield Exit Password Card
    item {
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Contact, 2FA & Shield Lock", style = MaterialTheme.typography.titleLarge)
          }

          OutlinedTextField(
              value = contactEmail,
              onValueChange = { contactEmail = it },
              label = { Text("Account / Recovery Email") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier = Modifier.fillMaxWidth().testTag("profile_email_input"),
          )

          OutlinedTextField(
              value = phoneNumber,
              onValueChange = { phoneNumber = it },
              label = { Text("Phone Number (for Profile & Recovery)") },
              placeholder = { Text("+1 555 010 9988") },
              leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              modifier = Modifier.fillMaxWidth().testTag("profile_phone_input"),
          )

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Two-Factor Verification (TOTP 2FA)", style = MaterialTheme.typography.titleMedium)
              Text(
                  text = "Require a 6-digit authenticator code after sign-in",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
                checked = twoFactorEnabled,
                onCheckedChange = { enabled ->
                  twoFactorEnabled = enabled
                  if (enabled && totpSecret.isBlank()) {
                    totpSecret = SecurityUtils.generateTotpSecret()
                  }
                },
                modifier = Modifier.testTag("toggle_2fa_switch"),
            )
          }

          if (twoFactorEnabled) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Authenticator Secret Key (Base32)", style = MaterialTheme.typography.labelSmall)
                Text(totpSecret, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Current 6-Digit Code: $liveOtp (${otpCountdown}s)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          OutlinedTextField(
              value = newShieldPassword,
              onValueChange = { newShieldPassword = it },
              label = {
                Text(
                    if (profile?.hasShieldPassword == true) "Change Shield Exit Password"
                    else "Set Shield Exit Password"
                )
              },
              placeholder = { Text("Minimum 4 characters") },
              leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth().testTag("profile_shield_password_input"),
          )

          Button(
              onClick = {
                onSaveSecurity(
                    contactEmail,
                    phoneNumber,
                    twoFactorEnabled,
                    totpSecret,
                    newShieldPassword.takeIf { it.isNotBlank() },
                )
                newShieldPassword = ""
              },
              modifier = Modifier.fillMaxWidth().testTag("save_security_button"),
              shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Filled.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Security & 2FA")
          }
        }
      }
    }

    // Sign Out Card
    item {
      OutlinedButton(
          onClick = onSignOut,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
          shape = RoundedCornerShape(14.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign Out")
      }
    }
  }
}
