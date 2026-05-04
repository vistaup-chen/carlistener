package com.chen.carlistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "收到短信广播")

        // 获取短信内容
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages == null || messages.isEmpty()) {
            Log.d(TAG, "没有短信内容")
            return
        }

        // 合并多部分短信
        val fullMessage = StringBuilder()
        for (message in messages) {
            fullMessage.append(message.messageBody)
        }

        val messageBody = fullMessage.toString()
        Log.d(TAG, "短信内容: $messageBody")

        // 检查是否包含关键字
        if (containsKeyword(context, messageBody)) {
            Log.d(TAG, "短信包含关键字，触发响铃")
            triggerRingtone(context, "短信: $messageBody")
        } else {
            Log.d(TAG, "短信不包含关键字")
        }
    }

    private fun containsKeyword(context: Context, message: String): Boolean {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val keywords = prefs.getString(MainActivity.KEY_KEYWORDS, "")
        
        if (keywords.isNullOrEmpty()) {
            return false
        }

        val keywordList = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (keyword in keywordList) {
            if (message.contains(keyword, ignoreCase = true)) {
                Log.d(TAG, "匹配到关键字: $keyword")
                return true
            }
        }

        return false
    }

    private fun triggerRingtone(context: Context, message: String) {
        val intent = Intent(context, RingtoneService::class.java)
        intent.putExtra("action", "ring")
        intent.putExtra("message", message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
