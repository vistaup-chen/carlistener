package com.chen.carlistener

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        private const val FALLBACK_CHANNEL_ID = "sms_fallback_channel"
        private const val FALLBACK_NOTIFICATION_ID = 9999
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "收到短信广播")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) {
            Log.d(TAG, "没有短信内容")
            return
        }

        val fullMessage = StringBuilder()
        for (message in messages) {
            fullMessage.append(message.messageBody)
        }

        val messageBody = fullMessage.toString()
        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: ""
        Log.d(TAG, "发件人: $sender, 短信内容: $messageBody")

        val senderMatch = matchesSenderNumber(context, sender)
        val keywordMatch = KeywordMatcher.containsKeyword(context, messageBody)

        if (senderMatch || keywordMatch) {
            val reason = when {
                senderMatch && keywordMatch -> "号码+关键字"
                senderMatch -> "号码"
                else -> "关键字"
            }
            Log.d(TAG, "命中[$reason]，触发响铃")
            triggerRingtone(context, "短信[$reason]: $messageBody")
        } else {
            Log.d(TAG, "号码和关键字均不匹配")
        }
    }

    private fun matchesSenderNumber(context: Context, sender: String): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val numbers = prefs.getString(MainActivity.KEY_SENDER_NUMBERS, MainActivity.DEFAULT_SENDER_NUMBERS)
        if (numbers.isNullOrEmpty()) return false

        return numbers.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { sender.contains(it, ignoreCase = true) }
    }

    private fun triggerRingtone(context: Context, message: String) {
        val intent = Intent(context, RingtoneService::class.java)
        intent.putExtra("action", "ring")
        intent.putExtra("message", message)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Android 12+ 后台启动 FGS 限制，或厂商省电策略拦截
            // 兜底：发高优先级通知 + 直接启动 AlarmActivity
            Log.e(TAG, "启动响铃服务失败，使用兜底方案: ${e.message}")
            showFallbackNotification(context, message)
            launchAlarmDirectly(context, message)
        }
    }

    /**
     * 兜底：发高优先级通知提醒用户
     */
    private fun showFallbackNotification(context: Context, message: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    FALLBACK_CHANNEL_ID, "短信提醒兜底",
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(channel)
            }

            val alarmIntent = Intent(context, AlarmActivity::class.java)
            alarmIntent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                context, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, FALLBACK_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }
            builder.setContentTitle("⚠️ 车辆监听提醒")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
            nm.notify(FALLBACK_NOTIFICATION_ID, builder.build())
        } catch (e2: Exception) {
            Log.e(TAG, "兜底通知也失败: ${e2.message}")
        }
    }

    /**
     * 兜底：直接启动 AlarmActivity（不依赖 FGS）
     */
    private fun launchAlarmDirectly(context: Context, message: String) {
        try {
            val alarmIntent = Intent(context, AlarmActivity::class.java)
            alarmIntent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e(TAG, "直接启动 AlarmActivity 也失败: ${e.message}")
        }
    }
}
