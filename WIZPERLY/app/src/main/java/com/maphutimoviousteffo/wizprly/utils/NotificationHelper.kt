package com.maphutimoviousteffo.wizprly.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maphutimoviousteffo.wizprly.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "wizprly_notifications"
    private const val CHANNEL_NAME = "WizPrly Notifications"
    private const val CHANNEL_DESC = "Notifications for AI responses"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(
        context: Context, 
        title: String, 
        message: String, 
        timeInMillis: Long, 
        chatId: String? = null,
        reminderType: String = "TEXT"
    ) {
        createNotificationChannel(context)
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("reminderType", reminderType)
            if (chatId != null) putExtra("chatId", chatId)
        }
        val requestCode = (timeInMillis xor (chatId?.hashCode()?.toLong() ?: 0L)).toInt() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        try {
            val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } catch (e2: Exception) {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        }
    }

    fun showNotification(
        context: Context, 
        title: String, 
        message: String, 
        chatId: String? = null, 
        totalUnread: Int = 0,
        autoStartCall: Boolean = false
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (chatId != null) putExtra("chatId", chatId)
            if (autoStartCall) putExtra("autoStartCall", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, chatId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // MARK AS READ ACTION
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "MARK_READ"
            if (chatId != null) putExtra("chatId", chatId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, chatId?.hashCode() ?: 0, markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.maphutimoviousteffo.wizprly.R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Mark as Read", markReadPendingIntent)

        if (autoStartCall) {
            builder.addAction(0, "📞 Answer Call", pendingIntent)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(chatId?.hashCode() ?: NOTIFICATION_ID, builder.build())
    }
}

class NotificationActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val chatId = intent?.getStringExtra("chatId")
        if (intent?.action == "MARK_READ" && chatId != null) {
            // Here we'd ideally trigger a ViewModel or DB update. 
            // For now, cancel the specific notification.
            val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(chatId.hashCode())
        }
    }
}
