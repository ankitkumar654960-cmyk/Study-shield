package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.local.LocalPrefsStore
import com.example.data.local.ShieldStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StudyShieldAccessibilityService : AccessibilityService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var prefsStore: LocalPrefsStore

  override fun onServiceConnected() {
    super.onServiceConnected()
    prefsStore = LocalPrefsStore(applicationContext)
    serviceScope.launch {
      prefsStore.shieldRuntimeState.collect { /* keeps ShieldStateHolder hydrated */ }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return
    if (pkg == packageName) return

    val runtimeState = ShieldStateHolder.state.value
    if (!runtimeState.isShieldActive) return

    // If session timer expired naturally, auto-release shield
    if (runtimeState.sessionEndEpochMs > 0L && System.currentTimeMillis() > runtimeState.sessionEndEpochMs) {
      serviceScope.launch { prefsStore.setShieldActive(false) }
      return
    }

    // Only intercept explicitly blocked distracting apps (e.g., Instagram, YouTube);
    // all other system apps (Phone, Messages, Settings, Browser, Calculator) continue working normally.
    if (pkg in runtimeState.blockedPackages) {
      serviceScope.launch { prefsStore.recordBlockedAttempt(pkg) }
      val blockIntent =
          Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(EXTRA_BLOCKED_PACKAGE, pkg)
          }
      startActivity(blockIntent)
    }
  }

  override fun onInterrupt() {}

  override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
  }

  companion object {
    const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"

    fun isServiceEnabled(context: Context): Boolean {
      val expectedComponent =
          ComponentName(context, StudyShieldAccessibilityService::class.java).flattenToString()
      val enabledServices =
          Settings.Secure.getString(
              context.contentResolver,
              Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
          ) ?: return false
      return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
      return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
      } catch (_: Exception) {
        false
      }
    }
  }
}
