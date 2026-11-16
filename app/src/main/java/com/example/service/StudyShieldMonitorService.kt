package com.example.service

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.LocalPrefsStore
import com.example.data.local.ShieldStateHolder
import com.example.notifications.StudyNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StudyShieldMonitorService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var prefsStore: LocalPrefsStore
  private var monitorJob: Job? = null
  private var lastInterceptedPkg: String? = null
  private var lastInterceptedAtMs: Long = 0L

  override fun onCreate() {
    super.onCreate()
    prefsStore = LocalPrefsStore(applicationContext)
    serviceScope.launch {
      prefsStore.shieldRuntimeState.collect { /* keeps ShieldStateHolder hydrated */ }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }

    ensureChannel()
    val notification = buildOngoingNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    startMonitorLoop()
    return START_STICKY
  }

  private fun startMonitorLoop() {
    if (monitorJob?.isActive == true) return
    monitorJob =
        serviceScope.launch {
          val usageStatsManager =
              getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
          while (isActive) {
            val runtimeState = ShieldStateHolder.state.value
            if (!runtimeState.isShieldActive) {
              stopSelf()
              break
            }

            if (runtimeState.sessionEndEpochMs > 0L &&
                System.currentTimeMillis() > runtimeState.sessionEndEpochMs) {
              prefsStore.setShieldActive(false)
              stopSelf()
              break
            }

            if (usageStatsManager != null && isUsageAccessGranted(applicationContext)) {
              val foregroundPkg = queryLatestForegroundPackage(usageStatsManager)
              val now = System.currentTimeMillis()
              if (!foregroundPkg.isNullOrBlank() &&
                  foregroundPkg != packageName &&
                  foregroundPkg in runtimeState.blockedPackages &&
                  (foregroundPkg != lastInterceptedPkg || now - lastInterceptedAtMs > 2500L)) {
                lastInterceptedPkg = foregroundPkg
                lastInterceptedAtMs = now
                prefsStore.recordBlockedAttempt(foregroundPkg)
                triggerDistractionIntercept(foregroundPkg, runtimeState.activeSubject)
              }
            }

            delay(POLL_INTERVAL_MS)
          }
        }
  }

  private fun queryLatestForegroundPackage(usageStatsManager: UsageStatsManager): String? {
    val end = System.currentTimeMillis()
    val start = end - 4000L
    val events = usageStatsManager.queryEvents(start, end) ?: return null
    val event = UsageEvents.Event()
    var latestPkg: String? = null
    var latestTime = 0L
    val resumedEventType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          UsageEvents.Event.ACTIVITY_RESUMED
        } else {
          @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      if (event.eventType == resumedEventType && event.timeStamp >= latestTime) {
        latestTime = event.timeStamp
        latestPkg = event.packageName
      }
    }
    return latestPkg
  }

  private fun triggerDistractionIntercept(blockedPkg: String, subject: String) {
    val blockIntent =
        Intent(this, MainActivity::class.java).apply {
          addFlags(
              Intent.FLAG_ACTIVITY_NEW_TASK or
                  Intent.FLAG_ACTIVITY_SINGLE_TOP or
                  Intent.FLAG_ACTIVITY_CLEAR_TOP
          )
          putExtra(EXTRA_BLOCKED_PACKAGE, blockedPkg)
        }

    try {
      startActivity(blockIntent)
    } catch (e: Exception) {
      Log.w(TAG, "Background launch throttled; notifying user instead.", e)
    }

    StudyNotificationHelper.showLocalNotification(
        context = applicationContext,
        channelId = StudyNotificationHelper.CHANNEL_TIMER,
        notificationId = INTERCEPT_NOTIFICATION_ID,
        title = "Focus Shield Active",
        body = "Distracting app blocked during your $subject session. Tap to return to StudyShield.",
    )
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel =
          NotificationChannel(
              CHANNEL_SHIELD_MONITOR,
              "Focus Shield Monitor",
              NotificationManager.IMPORTANCE_LOW,
          )
      manager.createNotificationChannel(channel)
    }
  }

  private fun buildOngoingNotification(): Notification {
    val openIntent =
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    val pendingIntent =
        PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    return NotificationCompat.Builder(this, CHANNEL_SHIELD_MONITOR)
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setContentTitle("StudyShield Focus Active")
        .setContentText("Guarding against distracting apps while your study timer runs.")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(pendingIntent)
        .build()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    super.onDestroy()
    monitorJob?.cancel()
    serviceScope.cancel()
  }

  companion object {
    private const val TAG = "StudyShieldMonitor"
    private const val CHANNEL_SHIELD_MONITOR = "studyshield_monitor"
    private const val NOTIFICATION_ID = 4001
    private const val INTERCEPT_NOTIFICATION_ID = 4002
    private const val POLL_INTERVAL_MS = 850L
    private const val ACTION_STOP = "com.example.service.ACTION_STOP_SHIELD"
    const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"

    fun start(context: Context) {
      val intent = Intent(context, StudyShieldMonitorService::class.java)
      try {
        ContextCompat.startForegroundService(context, intent)
      } catch (e: Exception) {
        Log.w(TAG, "Unable to start monitor service", e)
      }
    }

    fun stop(context: Context) {
      val intent =
          Intent(context, StudyShieldMonitorService::class.java).apply { action = ACTION_STOP }
      try {
        context.stopService(intent)
      } catch (_: Exception) {}
    }

    fun isUsageAccessGranted(context: Context): Boolean {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
      val mode =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
          } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
          }
      return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
      val pkgIntent =
          Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
          }
      try {
        context.startActivity(pkgIntent)
      } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
      }
    }
  }
}
