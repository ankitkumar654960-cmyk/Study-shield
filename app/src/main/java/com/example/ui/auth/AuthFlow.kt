package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.LocalPrefsStore
import com.example.util.SecurityUtils
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private fun resolveDefaultWebClientId(context: Context): String? {
  val resId =
      context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
  if (resId == 0) return null
  return try {
    context.getString(resId).takeIf { it.isNotBlank() }
  } catch (_: Exception) {
    null
  }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId =
      resolveDefaultWebClientId(context)
          ?: run {
            onUnauthenticated()
            return
          }

  val googleIdOption =
      GetGoogleIdOption.Builder()
          .setFilterByAuthorizedAccounts(true)
          .setServerClientId(clientId)
          .setAutoSelectEnabled(true)
          .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {},
) {
  val clientId =
      resolveDefaultWebClientId(context)
          ?: run {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
          }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val activityContext = context as? Activity ?: context
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type returned.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
    }
  }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.message}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefsStore = remember { LocalPrefsStore(context) }
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = onAuthSuccess,
        onUnauthenticated = {},
        scope = scope,
    )
  }

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(
                  Brush.verticalGradient(
                      listOf(
                          MaterialTheme.colorScheme.background,
                          MaterialTheme.colorScheme.surface,
                      )
                  )
              )
              .safeDrawingPadding(),
      contentAlignment = Alignment.Center,
  ) {
    Column(
        modifier =
            Modifier.widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(24.dp),
                    )
        ) {
          Image(
              painter = painterResource(id = R.drawable.img_study_sanctuary),
              contentDescription = "StudyShield calm study sanctuary",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
          )
          Box(
              modifier =
                  Modifier.fillMaxSize()
                      .background(
                          Brush.verticalGradient(
                              listOf(
                                  Color.Transparent,
                                  MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                              )
                          )
                      )
          )
          Row(
              modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                  text = "StudyShield",
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                  text = "Deep Focus • Distraction Vault • Exam Prep",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Guard your focus.\nMaster every exam.",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                "Sync study sessions, weekly reports, XP leaderboards, and two-factor protected focus rules across all your devices.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          FeatureBullet(
              icon = Icons.Filled.Timer,
              title = "Pomodoro & Deep Focus",
              subtitle = "Earn XP while blocking Instagram & YouTube",
          )
          FeatureBullet(
              icon = Icons.Filled.Lock,
              title = "Password-Protected Exit & 2FA",
              subtitle = "Prevent impulsive exits and secure your profile",
          )
          FeatureBullet(
              icon = Icons.Filled.NotificationsActive,
              title = "Exam Reminders & Weekly Reports",
              subtitle = "Local alerts, offline logging, and cloud sync",
          )
          FeatureBullet(
              icon = Icons.Filled.CloudSync,
              title = "Cross-Device Cloud Sync",
              subtitle = "Seamless access on phone and tablet, online or offline",
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        var showEmailLogin by remember { mutableStateOf(false) }
        var showMobileLogin by remember { mutableStateOf(false) }
        var emailInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var mobileInput by remember { mutableStateOf("") }

        if (errorMessage != null) {
          Card(
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer
                  ),
              modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          ) {
            Text(
                text = errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
            )
          }
        }

        if (showMobileLogin) {
          OutlinedTextField(
              value = mobileInput,
              onValueChange = { mobileInput = it.filter { ch -> ch.isDigit() }.take(15); errorMessage = null },
              label = { Text("Mobile Number (10+ digits)") },
              placeholder = { Text("9876543210") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              modifier = Modifier.fillMaxWidth().testTag("mobile_input"),
              shape = RoundedCornerShape(14.dp),
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
              onClick = {
                errorMessage = null
                isLoading = true
                val cleanPhone = mobileInput.trim()
                if (cleanPhone.length < 10) {
                  isLoading = false
                  errorMessage = "Please enter a valid 10-digit mobile number."
                  return@Button
                }
                val syntheticEmail = "$cleanPhone@studyshield.mobile"
                val syntheticPassword = "shield_${cleanPhone}_secure"
                scope.launch {
                  try {
                    try {
                      Firebase.auth.signInWithEmailAndPassword(syntheticEmail, syntheticPassword).await()
                    } catch (_: Exception) {
                      Firebase.auth.createUserWithEmailAndPassword(syntheticEmail, syntheticPassword).await()
                    }
                    isLoading = false
                    onAuthSuccess()
                  } catch (e: Exception) {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: "Mobile sign-in failed. Please try again."
                  }
                }
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("mobile_sign_in_button"),
              shape = RoundedCornerShape(16.dp),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
              )
            } else {
              Text("Sign In with Mobile Number", style = MaterialTheme.typography.labelLarge)
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          TextButton(onClick = { showMobileLogin = false }) {
            Text("Back to Google / Email Sign-In")
          }
        } else if (showEmailLogin) {
          OutlinedTextField(
              value = emailInput,
              onValueChange = { emailInput = it; errorMessage = null },
              label = { Text("Email Address") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              modifier = Modifier.fillMaxWidth().testTag("email_input"),
              shape = RoundedCornerShape(14.dp),
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it; errorMessage = null },
              label = { Text("Password (Min 6 chars)") },
              singleLine = true,
              visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              modifier = Modifier.fillMaxWidth().testTag("password_input"),
              shape = RoundedCornerShape(14.dp),
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
              onClick = {
                errorMessage = null
                isLoading = true
                val email = emailInput.trim()
                val password = passwordInput.trim()
                if (email.isBlank() || password.length < 6) {
                  isLoading = false
                  errorMessage = "Please enter a valid email and password (min 6 characters)."
                  return@Button
                }
                scope.launch {
                  try {
                    try {
                      Firebase.auth.signInWithEmailAndPassword(email, password).await()
                    } catch (_: Exception) {
                      Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                    }
                    isLoading = false
                    onAuthSuccess()
                  } catch (e: Exception) {
                    isLoading = false
                    errorMessage = e.localizedMessage ?: "Authentication failed. Please check credentials."
                  }
                }
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("email_sign_in_button"),
              shape = RoundedCornerShape(16.dp),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
              )
            } else {
              Text("Sign In or Register with Email", style = MaterialTheme.typography.labelLarge)
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          TextButton(onClick = { showEmailLogin = false }) {
            Text("Prefer Google Sign-In")
          }
        } else {
          Button(
              onClick = {
                errorMessage = null
                isLoading = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {
                      isLoading = false
                      onAuthSuccess()
                    },
                    onAuthError = { msg ->
                      isLoading = false
                      errorMessage = msg
                    },
                    scope = scope,
                    onAuthCancelled = {
                      isLoading = false
                      errorMessage = "Google Sign-In was cancelled. Try Mobile or Email below."
                    },
                )
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
              shape = RoundedCornerShape(16.dp),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
              )
            } else {
              Icon(
                  imageVector = Icons.Filled.Security,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                  text = "Continue with Google",
                  style = MaterialTheme.typography.labelLarge,
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            TextButton(
                onClick = { showMobileLogin = true },
                modifier = Modifier.testTag("toggle_mobile_login_button"),
            ) {
              Text("Sign In with Mobile")
            }
            TextButton(
                onClick = { showEmailLogin = true },
                modifier = Modifier.testTag("toggle_email_login_button"),
            ) {
              Text("Sign In with Email")
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
              scope.launch {
                prefsStore.setGuestMode(true)
                onAuthSuccess()
              }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("guest_mode_button"),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
          Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(10.dp))
          Text("Skip Login & Continue as Guest", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                "Link your contact email, phone number & 2FA authenticator inside Profile Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun FeatureBullet(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
  Card(
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
          ),
      shape = RoundedCornerShape(16.dp),
      modifier =
          Modifier.fillMaxWidth()
              .border(
                  1.dp,
                  MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                  RoundedCornerShape(16.dp),
              ),
  ) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
              imageVector = icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
          )
        }
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun TwoFactorVerificationScreen(
    email: String,
    phoneNumber: String,
    totpSecret: String,
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
) {
  var codeInput by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showAuthenticatorHint by remember { mutableStateOf(false) }
  var currentOtp by remember { mutableStateOf(SecurityUtils.generateTotpCode(totpSecret)) }
  var secondsLeft by remember { mutableIntStateOf(SecurityUtils.secondsRemainingInTotpWindow()) }

  LaunchedEffect(totpSecret) {
    while (true) {
      currentOtp = SecurityUtils.generateTotpCode(totpSecret)
      secondsLeft = SecurityUtils.secondsRemainingInTotpWindow()
      delay(1000L)
    }
  }

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .safeDrawingPadding()
              .padding(24.dp),
      contentAlignment = Alignment.Center,
  ) {
    Card(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = "Two-Factor Verification",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Two-Factor Verification",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val targetInfo =
            buildString {
              if (email.isNotBlank()) append(email)
              if (phoneNumber.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(phoneNumber)
              }
            }
                .ifBlank { "Protected Account" }
        Text(
            text = "Enter the 6-digit TOTP authenticator code for $targetInfo to unlock StudyShield.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = codeInput,
            onValueChange = { raw ->
              val digits = raw.filter { it.isDigit() }.take(6)
              codeInput = digits
              errorText = null
            },
            label = { Text("6-Digit Verification Code") },
            placeholder = { Text("000000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("two_factor_code_input"),
            textStyle =
                MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
        )

        if (errorText != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
              text = errorText ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
              if (SecurityUtils.verifyTotpCode(totpSecret, codeInput)) {
                onVerified()
              } else {
                errorText = "Invalid verification code. Please check your 6-digit TOTP code."
              }
            },
            modifier =
                Modifier.fillMaxWidth().height(50.dp).testTag("verify_two_factor_button"),
            shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Filled.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Verify & Unlock")
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = { showAuthenticatorHint = !showAuthenticatorHint },
            modifier = Modifier.testTag("toggle_totp_preview_button"),
        ) {
          Text(
              if (showAuthenticatorHint) "Hide On-Device Authenticator Preview"
              else "View On-Device TOTP Authenticator Code"
          )
        }

        if (showAuthenticatorHint) {
          Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  text = currentOtp,
                  style = MaterialTheme.typography.headlineLarge,
                  color = MaterialTheme.colorScheme.primary,
              )
              Text(
                  text = "Refreshes in ${secondsLeft}s",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().testTag("two_factor_sign_out_button"),
            shape = RoundedCornerShape(14.dp),
        ) {
          Text("Sign Out")
        }
      }
    }
  }
}
