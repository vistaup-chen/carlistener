package com.chen.carlistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 调试用 — 接收 notifier 模拟的短信广播，走和真实短信相同的匹配+响铃流程
 */
class DebugSmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DebugSmsReceiver"
        const val ACTION_DEBUG_SMS = "com.chen.carlistener.DEBUG_SMS"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_SMS) return

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
        val messageBody = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        Log.d(TAG, "收到模拟短信 — 发件人: $sender, 内容: $messageBody")

        // 和 SmsReceiver 相同的匹配逻辑
        val senderMatch = matchesSenderNumber(context, sender)
        val keywordMatch = KeywordMatcher.containsKeyword(context, messageBody)

        if (senderMatch || keywordMatch) {
            val reason = when {
                senderMatch && keywordMatch -> "号码+关键字"
                senderMatch -> "号码"
                else -> "关键字"
            }
            Log.d(TAG, "命中[$reason]，触发响铃")
            triggerRingtone(context, "模拟短信[$reason]: $messageBody")
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
        val serviceIntent = Intent(context, RingtoneService::class.java)
        serviceIntent.putExtra("action", "ring")
        serviceIntent.putExtra("message", message)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动响铃服务失败，直接启动 AlarmActivity: ${e.message}")
            try {
                val alarmIntent = Intent(context, AlarmActivity::class.java)
                alarmIntent.putExtra(AlarmActivity.EXTRA_MESSAGE, message)
                alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(alarmIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "兜底也失败: ${e2.message}")
            }
        }
    }
}
