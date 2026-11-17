package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object StudyNotificationHelper {
  const val CHANNEL_EXAMS = "studyshield_exams"
  const val CHANNEL_TIMER = "studyshield_timer"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val examChannel =
          NotificationChannel(
                  CHANNEL_EXAMS,
                  context.getString(R.string.notification_channel_exams_name),
                  NotificationManager.IMPORTANCE_HIGH,
              )
              .apply {
                description = context.getString(R.string.notification_channel_exams_desc)
              }
      val timerChannel =
          NotificationChannel(
                  CHANNEL_TIMER,
                  context.getString(R.string.notification_channel_timer_name),
                  NotificationManager.IMPORTANCE_HIGH,
              )
              .apply {
                description = context.getString(R.string.notification_channel_timer_desc)
                enableVibration(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
              }
      manager.createNotificationChannel(examChannel)
      manager.createNotificationChannel(timerChannel)
    }
  }

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun showLocalNotification(
      context: Context,
      channelId: String,
      notificationId: Int,
      title: String,
      body: String,
  ) {
    ensureChannels(context)
    if (!hasNotificationPermission(context)) return

    val launchIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    val notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

    NotificationManagerCompat.from(context).notify(notificationId, notification)
  }

  fun scheduleExamReminder(
      context: Context,
      examId: String,
      title: String,
      subject: String,
      triggerAtMillis: Long,
  ) {
    ensureChannels(context)
    if (triggerAtMillis <= System.currentTimeMillis()) return

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent =
        Intent(context, ExamReminderReceiver::class.java).apply {
          putExtra(ExamReminderReceiver.EXTRA_EXAM_TITLE, title)
          putExtra(ExamReminderReceiver.EXTRA_EXAM_SUBJECT, subject)
          putExtra(ExamReminderReceiver.EXTRA_NOTIFICATION_ID, examId.hashCode())
        }
    val pendingIntent =
        PendingIntent.getBroadcast(
            context,
            examId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
  }

  fun cancelExamReminder(context: Context, examId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, ExamReminderReceiver::class.java)
    val pendingIntent =
        PendingIntent.getBroadcast(
            context,
            examId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    alarmManager.cancel(pendingIntent)
  }
}

class ExamReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val title = intent.getStringExtra(EXTRA_EXAM_TITLE) ?: "Upcoming Exam"
    val subject = intent.getStringExtra(EXTRA_EXAM_SUBJECT) ?: "Study Session"
    val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 4001)
    StudyNotificationHelper.showLocalNotification(
        context = context,
        channelId = StudyNotificationHelper.CHANNEL_EXAMS,
        notificationId = notificationId,
        title = "Exam Reminder: $title",
        body = "Your $subject deadline is approaching. Start a StudyShield focus block to review!",
    )
  }

  companion object {
    const val EXTRA_EXAM_TITLE = "extra_exam_title"
    const val EXTRA_EXAM_SUBJECT = "extra_exam_subject"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
  }
}
