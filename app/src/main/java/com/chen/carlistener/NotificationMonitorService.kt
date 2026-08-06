package com.chen.carlistener

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationMonitor"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val packageName = sbn.packageName
        Log.d(TAG, "收到通知，包名: $packageName")

        // 获取配置的监听包名（支持多个，逗号分隔）
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val targetPackages = prefs.getString(MainActivity.KEY_NOTIFICATION_PACKAGE, MainActivity.DEFAULT_NOTIFICATION_PACKAGE) ?: ""

        // 检查是否是目标应用的通知（始终包含 notifier 测试 app）
        val isTargetApp = packageName == MainActivity.NOTIFIER_PACKAGE
                || targetPackages.split(",").any { it.trim() == packageName }
        if (isTargetApp) {
            Log.d(TAG, "匹配到目标应用: $packageName")

            // 获取通知内容
            val notification = sbn.notification
            val extras = notification.extras
            
            if (extras != null) {
                val title = extras.getString(Notification.EXTRA_TITLE, "")
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
                
                val fullContent = "$title $text $bigText"
                Log.d(TAG, "通知内容: $fullContent")

                // 检查是否包含关键字
                if (KeywordMatcher.containsKeyword(this, fullContent)) {
                    Log.d(TAG, "通知包含关键字，触发响铃")
                    triggerRingtone("通知: $fullContent")
                } else {
                    Log.d(TAG, "通知不包含关键字")
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 不需要处理通知移除
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.e(TAG, "NotificationListenerService 被系统断开！尝试重连...")
        // Android 7+ 可以请求重连
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, NotificationMonitorService::class.java))
            Log.d(TAG, "已请求重连 NotificationListenerService")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService 已连接 ✓")
    }

    private fun triggerRingtone(message: String) {
        val intent = android.content.Intent(this, RingtoneService::class.java)
        intent.putExtra("action", "ring")
        intent.putExtra("message", message)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动响铃服务失败: ${e.message}")
        }
    }
}
